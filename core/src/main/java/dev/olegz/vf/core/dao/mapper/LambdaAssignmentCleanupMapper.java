package dev.olegz.vf.core.dao.mapper;

import dev.olegz.vf.common.Datetime;
import org.apache.ibatis.annotations.Param;

public interface LambdaAssignmentCleanupMapper {
    void insertLambdaAssignmentCleanup(
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("cleanupAfter") Datetime cleanupAfter);

    Integer takeNextLambdaAssignmentForCleanup(Datetime date);
}
