package dev.olegz.vf.core.dao;

import java.sql.Timestamp;

import dev.olegz.vf.core.domain.lambdarun.LambdaResetOutboxEntry;

public interface LambdaResetOutboxDao {
    void insert(LambdaResetOutboxEntry entry);
    LambdaResetOutboxEntry claimNextAvailable(Timestamp now, String claimId, Timestamp claimUntil);
    boolean deleteClaimed(LambdaResetOutboxEntry entry);
    boolean releaseClaim(LambdaResetOutboxEntry entry);
}
