package dev.olegz.vf.api.lambda.management;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.lambda.ApiLambda;
import dev.olegz.vf.api.lambda.ApiLambdaVersion;
import dev.olegz.vf.api.web.support.ActionContext;
import dev.olegz.vf.api.web.support.ActionResponse;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdaoperation.LambdaOperationRequest;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import dev.olegz.vf.core.domain.lambdaversion.VersionBump;
import dev.olegz.vf.core.messaging.MessageDispatcher;
import dev.olegz.vf.core.service.lambda.LambdaManagementService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LambdaManagementAction {
    private static final Logger logger = LoggerFactory.getLogger(LambdaManagementAction.class);

    private final LambdaManagementService lambdaManagementService;

    public LambdaManagementAction(LambdaManagementService lambdaManagementService) {
        this.lambdaManagementService = lambdaManagementService;
    }

    public Response postLambda(ActionContext ctx, LambdaCreateRequest request) {
        logger.debug(">postLambda() {}", request);

        int lambdaId = lambdaManagementService.createLambda(ctx.userId(), request.lambdaName, request.devTeamId, request.description, request.metadata);

        logger.debug("<postLambda() id={}", lambdaId);
        return new Response();
    }

    public Response putLambda(ActionContext ctx, LambdaUpdateRequest request) {
        logger.debug(">putLambda() {}", request);

        lambdaManagementService.updateLambda(ctx.userId(), request.lambdaId, request.description, request.metadata);

        logger.debug("<putLambda()");
        return new Response();
    }

    public Response getLambdas(ActionContext ctx, Integer devTeamId) {
        Response response = new Response();
        response.apps = CollectionOps.map(lambdaManagementService.getLambdas(ctx.userId(), devTeamId), ApiLambda::new);
        return response;
    }

    public static class PutVersionRequest {
        public @NotNull(message = "bump") VersionBump bump;
        public String whatsnew;
        public Map<String, String> schedules;
        public int trigger;
        private LambdaConfig toLambdaConfig() {
            LambdaConfig lambdaConfig = new LambdaConfig();
            lambdaConfig.bump = bump;
            lambdaConfig.trigger = trigger;
            lambdaConfig.whatsnew = whatsnew;
            lambdaConfig.schedule = schedules;
            return lambdaConfig;
        }
    }

    public Response putLambdaConfiguration(ActionContext ctx, int lambdaId, PutVersionRequest request) {
        logger.debug(">putLambdaConfiguration() {}", lambdaId);

        LambdaConfig lambdaConfig = request.toLambdaConfig();
        LambdaVersion lambdaVersion = lambdaManagementService.setLambdaConfiguration(ctx.userId(), lambdaId, lambdaConfig);

        Response response = new Response();
        response.lambdaVersionId = lambdaVersion.lambdaVersionId;
        response.version = lambdaVersion.version;

        logger.debug("<putLambdaConfiguration() {}", lambdaId);
        return response;
    }

    public Response promoteTestVersionToProduction(ActionContext ctx, int lambdaId) {
        lambdaManagementService.promoteTestVersionToProduction(ctx.userId(), lambdaId);
        return new Response();
    }

    public Response discardTestVersion(ActionContext ctx, int lambdaId) {
        lambdaManagementService.discardTestVersion(ctx.userId(), lambdaId);
        return new Response();
    }

    public Response rollbackProductionVersion(ActionContext ctx, int lambdaId) {
        lambdaManagementService.rollbackProductionVersion(ctx.userId(), lambdaId);
        return new Response();
    }

    public Response getLambdaVersions(ActionContext ctx, int lambdaId, List<LambdaVersionStatus> statuses, String version) {
        Response response = new Response();
        response.versions = CollectionOps.map(
            lambdaManagementService.getLambdaVersions(ctx.userId(), lambdaId,
                statuses == null ? null : statuses.toArray(LambdaVersionStatus[]::new), version),
            ApiLambdaVersion::new);
        response.versionsCount = response.versions == null ? 0 : response.versions.size();
        return response;
    }


    public Response deleteActiveLambdaVersions(ActionContext ctx, int lambdaId) {
        if (logger.isDebugEnabled()) {
            logger.debug(">deleteLambdaVersions() lambdaId=" + lambdaId);
        }
        Lambda lambda = lambdaManagementService.deleteLambdaActiveVersions(ctx.userId(), lambdaId);
        if (lambda != null) MessageDispatcher.sendLambdaOperation(LambdaOperationRequest.deleteLambdaAssignments(lambda.lambdaId));

        logger.debug("<deleteLambdaVersions()");
        return new Response();
    }

    public static class LambdaCreateRequest {
        public @NotNull(message = "lambdaName") String lambdaName;
        public @Positive(message = "devTeamId") int devTeamId;
        public String description;
        public Map<String, Object> metadata;
    }

    public static class LambdaUpdateRequest {
        public @Positive(message = "lambdaId") int lambdaId;
        public String description;
        public Map<String, Object> metadata;
    }

    public static class Response extends ActionResponse {
        public List<ApiLambda> apps;
        public Integer versionsCount;
        public List<ApiLambdaVersion> versions;
        public Integer lambdaVersionId;
        public String version;
    }
}
