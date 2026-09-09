package dev.olegz.vf.core.dao;

import dev.olegz.vf.core.domain.alert.LambdaAlert;

public interface LambdaAlertDao {
    void insertAlert(LambdaAlert alert);

    LambdaAlert getAlert(String idempotencyKey, int lambdaAssignmentId);
}
