package dev.olegz.vf.core.dao.mapper;

import java.util.List;

import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.apache.ibatis.annotations.Param;

public interface LambdaVersionMapper {
    void insertLambda(Lambda lambda);
    boolean updateLambda(Lambda lambda);
    void updateLambdaUploadId(
        @Param("lambdaId") int lambdaId,
        @Param("uploadId") Integer uploadId);
    void updateLambdaMaxVersion(
        @Param("lambdaId") int lambdaId,
        @Param("maxVersion") String maxVersion);
    void updateLambdaMaxSequence(
        @Param("lambdaId") int lambdaId,
        @Param("maxSequence") int maxSequence);
    Lambda selectLambdaById(int lambdaId);
    Lambda selectLambdaByIdAndUser(
        @Param("lambdaId") int lambdaId,
        @Param("userId") int userId);
    List<Lambda> selectLambdasByDeveloper(
        @Param("userId") int userId,
        @Param("teamId") Integer devTeamId);
    Lambda selectLambdaForUpdate(int lambdaId);
    Lambda selectLambdaForUpdateByIdAndUser(
        @Param("lambdaId") int lambdaId,
        @Param("userId") int userId);

    List<LambdaVersion> selectLambdaVersions(
        @Param("lambdaId") int lambdaId,
        @Param("statuses") LambdaVersionStatus[] statuses,
        @Param("version") String version);
    void insertLambdaVersion(LambdaVersion lambdaVersion);
    void updateLambdaVersion(LambdaVersion lambdaVersion);
    void deleteLambdaVersions(int lambdaId);
    void deleteLambdaActiveVersions(int lambdaId);
    void insertLambdaActiveVersion(LambdaVersion lambdaVersion);
    void clearNonPublicLambdaVersionActive(int lambdaId);

    // Test cleanup
    List<Integer> selectLambdaAssignmentIdsByLambdaId(int lambdaId);
    void deleteLambdaAssignmentVariable(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("name") String name);
    int deleteLambdaPendingInputs(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("limit") int limit);
    void deleteLambdaAssignmentRun(int lambdaAssignmentId);
    void deleteLambdaAssignment(int lambdaAssignmentId);
    void deleteLambdaCodeUploads(int lambdaId);
    void deleteLambda(int lambdaId);
}
