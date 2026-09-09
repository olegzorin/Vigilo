package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaAsyncSubmissionOutboxEntry;
import org.apache.ibatis.annotations.Param;

public interface LambdaAsyncSubmissionOutboxMapper {
    boolean exists(LambdaAsyncSubmissionOutboxEntry entry);
    void insert(LambdaAsyncSubmissionOutboxEntry entry);
    LambdaAsyncSubmissionOutboxEntry claimNextDue(
        @Param("now") Timestamp now,
        @Param("claimId") String claimId,
        @Param("claimUntil") Timestamp claimUntil);
    boolean deleteClaimed(LambdaAsyncSubmissionOutboxEntry entry);
    boolean rescheduleClaimed(LambdaAsyncSubmissionOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
