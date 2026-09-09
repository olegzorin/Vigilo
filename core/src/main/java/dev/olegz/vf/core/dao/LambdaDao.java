package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;

public interface LambdaDao {
    // Lambdas
    void insertLambda(Lambda lambda);
    void updateLambda(Lambda lambda);
    void updateLambdaUploadId(int lambdaId, Integer uploadId);
    void updateLambdaMaxVersion(int lambdaId, String maxVersion);
    void updateLambdaMaxSequence(int lambdaId, int maxSequence);

    Lambda getLambda(int lambdaId);
    Lambda getLambdaByIdAndUser(int lambdaId, int userId);
    List<Lambda> getLambdasByDeveloper(int userId, Integer devTeamId);
    Lambda getLambdaForUpdate(int lambdaId);
    Lambda getLambdaForUpdate(int lambdaId, int userId);

    // Lambda versions
    List<LambdaVersion> getLambdaVersions(int lambdaId, String version, LambdaVersionStatus[] statuses);
    List<LambdaVersion> getLambdaVersionsForUpdate(int lambdaId);
    void insertLambdaVersionConfig(LambdaVersion lambdaVersion);
    void updateLambdaVersions(int lambdaId, List<LambdaVersion> lambdaVersions);
    void deleteLambdaActiveVersions(int lambdaId);

    // For unit tests
    void deleteLambda(String lambdaName, int userId, int devTeamId);
}
