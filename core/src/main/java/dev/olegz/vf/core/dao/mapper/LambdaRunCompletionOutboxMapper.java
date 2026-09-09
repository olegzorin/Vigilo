package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaRunCompletionOutboxEntry;
import org.apache.ibatis.annotations.Param;

public interface LambdaRunCompletionOutboxMapper {
    void insert(LambdaRunCompletionOutboxEntry entry);
    LambdaRunCompletionOutboxEntry claimNextAvailable(
        @Param("now") Timestamp now,
        @Param("claimId") String claimId,
        @Param("claimUntil") Timestamp claimUntil);
    boolean renewClaim(LambdaRunCompletionOutboxEntry entry);
    boolean deleteClaimed(LambdaRunCompletionOutboxEntry entry);
    boolean releaseClaim(LambdaRunCompletionOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
