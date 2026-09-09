package dev.olegz.vf.aws.iam;

import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.common.ApplicationFailureException;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;

/**
 * Generic AWS IAM lookups (execution-role ARNs and the AWS account ID). Results are cached
 * for the JVM lifetime since they are effectively immutable.
 */
public final class IamSupport {
    private IamSupport() {
    }

    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private static IamClient iamClient() {
        return AwsClients.iamClient();
    }

    public static String getRoleArn(String roleName) {
        return cache.computeIfAbsent("role:" + roleName, k -> {
            try (var iamClient = iamClient()) {
                return iamClient.getRole(req -> req.roleName(roleName)).role().arn();
            } catch (NoSuchEntityException e) {
                throw new ApplicationFailureException("Role " + roleName + " does not exist in AWS IAM");
            } catch (Exception e) {
                throw new ApplicationFailureException("Exception in getting execution role", e);
            }
        });
    }

    public static String getAccountId() {
        return cache.computeIfAbsent("accountId", k -> {
            try (var iamClient = iamClient()) {
                return iamClient.getUser().user().arn().split(":")[4];
            } catch (Exception e) {
                throw new ApplicationFailureException("Exception in getting AWS account ID", e);
            }
        });
    }
}
