package dev.olegz.vf.core.dao.mapper;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import org.apache.ibatis.annotations.Param;

public interface LambdaAssignmentMapper {
    void insertLambdaAssignment(LambdaAssignment lambdaAssignment);
    boolean updateLambdaAssignment(LambdaAssignment lambdaAssignment);
    boolean markLambdaAssignmentDeleted(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("deletedAt") Datetime deletedAt);
    List<LambdaAssignment> selectConcurrentLambdaAssignments(LambdaAssignment lambdaAssignment);
    LambdaAssignment selectLambdaAssignment(int lambdaAssignmentId);
    List<Integer> selectLambdaAssignmentIdsByLambdaId(int lambdaId);
    List<Integer> selectLambdaAssignmentIdsByLocationId(int locationId);
    List<LambdaAssignment> selectLambdaAssignments(
        @Param("lambdaId") Integer lambdaId,
        @Param("locationId") Integer locationId,
        @Param("testing") boolean testingOnly);
    void insertLambdaAssignmentRun(LambdaRun run);
    void updateLambdaAssignmentRunsReset(int lambdaAssignmentId);
}
