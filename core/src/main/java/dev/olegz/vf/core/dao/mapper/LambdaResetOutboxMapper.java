package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;
import org.apache.ibatis.annotations.Param;

public interface LambdaResetOutboxMapper {
    void insert(LambdaResetOutboxEntry entry);
    LambdaResetOutboxEntry claimNextAvailable(
        @Param("now") Timestamp now,
        @Param("claimId") String claimId,
        @Param("claimUntil") Timestamp claimUntil);
    boolean deleteClaimed(LambdaResetOutboxEntry entry);
    boolean releaseClaim(LambdaResetOutboxEntry entry);
}
