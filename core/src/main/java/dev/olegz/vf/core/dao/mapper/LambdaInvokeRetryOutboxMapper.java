package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaInvokeRetryOutboxEntry;
import org.apache.ibatis.annotations.Param;

public interface LambdaInvokeRetryOutboxMapper {
    boolean exists(LambdaInvokeRetryOutboxEntry entry);
    void insert(LambdaInvokeRetryOutboxEntry entry);
    LambdaInvokeRetryOutboxEntry claimNextDue(
        @Param("now") Timestamp now,
        @Param("claimId") String claimId,
        @Param("claimUntil") Timestamp claimUntil);
    boolean renewClaim(LambdaInvokeRetryOutboxEntry entry);
    boolean deleteClaimed(LambdaInvokeRetryOutboxEntry entry);
    boolean releaseClaim(LambdaInvokeRetryOutboxEntry entry);
    void deleteForLambdaAssignment(int lambdaAssignmentId);
}
