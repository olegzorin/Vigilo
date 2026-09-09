package dev.olegz.vf.core.dao.impl;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.core.dao.LambdaAlertDao;
import dev.olegz.vf.core.dao.mapper.LambdaAlertMapper;
import dev.olegz.vf.core.domain.alert.LambdaAlert;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LambdaAlertDaoImpl implements LambdaAlertDao {
    private final LambdaAlertMapper mapper;

    public LambdaAlertDaoImpl(LambdaAlertMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertAlert(LambdaAlert alert) {
        if (mapper.insertAlert(alert) != 1) {
            throw new ApplicationFailureException(
                "Cannot resolve location for lambda alert " + alert.alertId);
        }
    }

    @Override
    public LambdaAlert getAlert(String idempotencyKey, int lambdaAssignmentId) {
        return mapper.selectAlert(idempotencyKey, lambdaAssignmentId);
    }
}
