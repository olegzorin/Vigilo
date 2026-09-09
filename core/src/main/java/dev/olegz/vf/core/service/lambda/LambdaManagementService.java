package dev.olegz.vf.core.service.lambda;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.LambdaConfig;
import dev.olegz.vf.core.domain.lambdabuild.BuildConfig;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;

public interface LambdaManagementService {

    int createLambda(int userId, String lambdaName, int devTeamId, String description, Map<String, Object> metadata);

    void updateLambda(int userId, int lambdaId, String description, Map<String, Object> metadata);

    List<Lambda> getLambdas(int userId, Integer devTeamId);

    LambdaVersion setLambdaConfiguration(int userId, int lambdaId, LambdaConfig lambdaConfig);

    List<LambdaVersion> getLambdaVersions(int userId, int lambdaId, LambdaVersionStatus[] statuses, String version);

    void promoteTestVersionToProduction(int userId, int lambdaId);

    void discardTestVersion(int userId, int lambdaId);

    void rollbackProductionVersion(int userId, int lambdaId);

    Lambda deleteLambdaActiveVersions(int userId, int lambdaId);

    LambdaCodeUpload uploadLambdaCode(int userId, int lambdaId, BuildConfig buildConfig);

    void failLambdaCodeUpload(LambdaCodeUpload upload, String message);

    LambdaCodeUpload getLambdaCodeUpload(int userId, int lambdaId, int uploadId);
}
