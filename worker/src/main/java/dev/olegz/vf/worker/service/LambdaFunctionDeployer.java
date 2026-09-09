package dev.olegz.vf.worker.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.aws.iam.IamSupport;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.ExternalException;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.worker.domain.DeleteFunctionParams;
import dev.olegz.vf.worker.domain.UpdateFunctionParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;

@Service("lambdaFunctionDeployer")
public class LambdaFunctionDeployer {
    private static final Logger logger = LoggerFactory.getLogger(LambdaFunctionDeployer.class);

    private static DestinationConfig getDestination() {
        String sqsQueueArn = LambdaResultQueueProvisioner.provision();
        return DestinationConfig.builder()
            .onSuccess(c -> c.destination(sqsQueueArn))
            .onFailure(c -> c.destination(sqsQueueArn))
            .build();
    }

    private static final SystemLogLevel SYS_LOG_LEVEL = SystemLogLevel.INFO;
    private static final ApplicationLogLevel lambda_LOG_LEVEL = ApplicationLogLevel.WARN;

    private static LoggingConfig getLoggingConfig() {
        String logLevel = PropertyStore.getString("vf.lambda.systemLogLevel", SYS_LOG_LEVEL.name());
        SystemLogLevel sysLogLevel = SystemLogLevel.fromValue(logLevel.toUpperCase());
        if (sysLogLevel == null) {
            logger.warn("Wrong system log level '" + logLevel + "', defaulting to " + SYS_LOG_LEVEL);
            sysLogLevel = SYS_LOG_LEVEL;
        }

        logLevel = PropertyStore.getString("vf.lambda.applicationLogLevel", lambda_LOG_LEVEL.name());
        ApplicationLogLevel appLogLevel = ApplicationLogLevel.fromValue(logLevel.toUpperCase());
        if (appLogLevel == null) {
            logger.warn("Wrong application log level '" + logLevel + "', defaulting to " + lambda_LOG_LEVEL);
            appLogLevel = lambda_LOG_LEVEL;
        }

        return LoggingConfig.builder()
            .logFormat(LogFormat.JSON)
            .systemLogLevel(sysLogLevel)
            .applicationLogLevel(appLogLevel)
            .build();
    }

    private static final Class<?>[] noRetryApiExceptions = {
        InvalidParameterValueException.class,
        InvalidRequestContentException.class,
        CodeStorageExceededException.class,
        TooManyRequestsException.class,
        RequestTooLargeException.class,
        ResourceConflictException.class,
        ResourceNotFoundException.class,
        LambdaException.class,
        software.amazon.awssdk.services.iam.model.NoSuchEntityException.class
    };

    private static boolean isRetryableApiException(Throwable e) {
        for (var cls : noRetryApiExceptions) {
            if (cls.isInstance(e)) return false;
        }
        return true;
    }

    private LambdaClient makeUpdateClient() {
        return AwsClients.lambdaClient(builder -> builder.overrideConfiguration(
            conf -> conf.retryStrategy(AwsClients.standardRetryStrategy(
                retryStrategyBuilder -> retryStrategyBuilder.retryOnException(LambdaFunctionDeployer::isRetryableApiException)))
        ));
    }

    EnumMap<InvocationLane, String> updateLambdaFunctions(
        EnumMap<InvocationLane, UpdateFunctionParams> paramsByLane, long endTime,
        BiConsumer<InvocationLane, String> publishedFunctionConsumer)
    {
        logger.debug(">updateLambdaFunctions() paramsByLane={}", paramsByLane);

        if (endTime <= Instant.now().getEpochSecond() + 5) {
            // the whole update definitely can't be done faster than 5 seconds!
            throw new ApplicationFailureException("There is not enough time left to deploy lambda function");
        }

        String roleArn = IamSupport.getRoleArn(Lambda.EXECUTION_ROLE);
        LoggingConfig loggingConfig = getLoggingConfig();

        try (LambdaClient client = makeUpdateClient()) {
            EnumMap<InvocationLane, String> versions = new EnumMap<>(InvocationLane.class);
            for (InvocationLane lane : InvocationLane.values()) {
                UpdateFunctionParams params = paramsByLane.get(lane);
                versions.put(lane, deployFunction(client, params, roleArn, loggingConfig, endTime,
                    functionRef -> publishedFunctionConsumer.accept(lane, functionRef)));
            }

            UpdateFunctionParams asyncParams = paramsByLane.get(InvocationLane.ASYNC);
            client.putFunctionEventInvokeConfig(
                r -> r.functionName(asyncParams.functionName)
                    .qualifier(versions.get(InvocationLane.ASYNC))
                    .maximumRetryAttempts(0)
                    .destinationConfig(getDestination())
            );

            EnumMap<InvocationLane, String> functionRefs = new EnumMap<>(InvocationLane.class);
            for (InvocationLane lane : InvocationLane.values()) {
                functionRefs.put(lane, paramsByLane.get(lane).functionName + ':' + versions.get(lane));
            }
            logger.debug("<updateLambdaFunctions() {}", functionRefs);
            return functionRefs;
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while updating functions " + paramsByLane.values());
        }
    }

    String deployFunction(LambdaClient client, UpdateFunctionParams params, String roleArn,
                          LoggingConfig loggingConfig, long endTime,
                          Consumer<String> publishedFunctionConsumer) {
        Architecture architecture = params.arch == BuildConfig.ARCH_X86 ? Architecture.X86_64 : Architecture.ARM64;
        try {
            client.updateFunctionCode(
                r -> r.functionName(params.functionName)
                    .architectures(architecture)
                    .imageUri(params.imageUri)
            );
            waitUntilFunctionUpdated(client, params.functionName, endTime);

            client.updateFunctionConfiguration(
                r -> r.functionName(params.functionName)
                    .loggingConfig(loggingConfig)
                    .memorySize(params.memory)
                    .timeout(params.timeout)
                    .role(roleArn)
            );
        } catch (ResourceNotFoundException e) {
            client.createFunction(
                r -> r.functionName(params.functionName)
                    .description(params.functionDesc)
                    .architectures(architecture)
                    .packageType(PackageType.IMAGE)
                    .code(c -> c.imageUri(params.imageUri))
//                    .vpcConfig(LambdaAwsResources.getVpcConfig())
                    .loggingConfig(loggingConfig)
                    .memorySize(params.memory)
                    .timeout(params.timeout)
                    .role(roleArn)
            );
        }

        waitUntilFunctionUpdated(client, params.functionName, endTime);
        String version = client.publishVersion(
            r -> r.functionName(params.functionName).description(params.versionDesc)
        ).version();
        publishedFunctionConsumer.accept(params.functionName + ':' + version);
        waitUntilPublishedVersionActive(client, params.functionName, version, endTime);
        return version;
    }

    private static void waitUntilFunctionUpdated(LambdaClient client, String functionName, long endTime) {
        long maxWaitTime = endTime - Instant.now().getEpochSecond();
        if (maxWaitTime <= 0) {
            throw new ApplicationFailureException("There is no time left to wait for the function to update");
        }

        ResponseOrException<GetFunctionConfigurationResponse> waiterResult = client.waiter()
            .waitUntilFunctionUpdated(
                r -> r.functionName(functionName),
                c -> c.waitTimeout(Duration.ofSeconds(maxWaitTime))
            ).matched();

        // On error
        waiterResult.exception().ifPresent(e -> {
            throw new ApplicationFailureException("Exception while updating function", e);
        });
        // On success
        waiterResult.response().ifPresentOrElse(
            response -> {
                switch (response.lastUpdateStatus()) {
                    case LastUpdateStatus.FAILED -> throw new ExternalException("Function update failed");
                    case LastUpdateStatus.IN_PROGRESS -> throw new ExternalException("Function update timed out, maxWaitTime=" + maxWaitTime);
                    case LastUpdateStatus.UNKNOWN_TO_SDK_VERSION ->
                        throw new ExternalException("Unknown update status of function: " + response.lastUpdateStatusAsString());
                }
            },
            () -> {
                throw new ExternalException("No response from waiting for function to update");
            }
        );
    }

    private void waitUntilPublishedVersionActive(LambdaClient client, String functionName, String version, long endTime) {
        long maxWaitTime = endTime - Instant.now().getEpochSecond();
        if (maxWaitTime <= 0) {
            throw new ApplicationFailureException("There is no time left to wait for the published version to activate, functionVersion=" + version);
        }

        ResponseOrException<GetFunctionConfigurationResponse> waiterResult = client.waiter()
            .waitUntilPublishedVersionActive(
                r -> r.functionName(functionName).qualifier(version),
                c -> c.waitTimeout(Duration.ofSeconds(maxWaitTime))
            ).matched();

        // On error
        waiterResult.exception().ifPresent(e -> {
            throw new ApplicationFailureException("Exception while activation, functionVersion=" + version, e);
        });
        // On success
        waiterResult.response().ifPresentOrElse(
            response -> {
                switch (response.state()) {
                    case State.FAILED -> throw new ExternalException("Function activation failed, functionVersion=" + version);
                    case State.PENDING, State.INACTIVE -> throw new ExternalException("Function activation timed out, functionVersion=" + version);
                    case State.UNKNOWN_TO_SDK_VERSION ->
                        throw new ExternalException("Unknown function activation state, functionVersion=" + version + ", state=" + response.stateAsString());
                }
            },
            () -> {
                throw new ExternalException("No response from waiting for activation to complete, functionVersion=" + version);
            }
        );
    }

    /**
     * Delete the entire function or just a specific version.
     * If after deleting the specified version there are no versions
     * left except $LATEST, the entire function is deleted.
     */
    void deleteFunction(DeleteFunctionParams params) {
        logger.debug(">deleteFunction() {}", params);

        try (LambdaClient client = makeUpdateClient()) {
            if (params.versionQualifier != null) {
                var versions = client.listVersionsByFunction(
                    r -> r.functionName(params.functionName)
                ).versions();
                boolean otherVersionExists = CollectionOps.anyMatch(versions,
                    v -> !v.version().equals(params.versionQualifier) && !v.version().equals("$LATEST")
                );
                if (otherVersionExists) {
                    // just delete specified version and return
                    client.deleteFunction(
                        r -> r.functionName(params.functionName).qualifier(params.versionQualifier)
                    );
                    params.versionDeleted = true;
                    logger.debug("<deleteFunction() version {}", params.versionQualifier);
                    return;
                }
            }
            // delete entire function
            client.deleteFunction(
                r -> r.functionName(params.functionName)
            );
            params.functionDeleted = true;
            logger.debug("<deleteFunction() function on the whole");
        } catch (ResourceNotFoundException ignore) {
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception in function deletion, " + params);
        }
    }

}
