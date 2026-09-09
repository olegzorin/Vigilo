package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.DurationProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.dao.LambdaAssignmentCleanupDao;
import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.dao.LambdaVariableDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaAssignmentCleanupServiceImpl implements LambdaAssignmentCleanupService {
    private final LambdaAssignmentCleanupDao cleanupDao;
    private final LambdaVariableDao lambdaVariableDao;
    private final LambdaRunDao lambdaRunDao;

    public LambdaAssignmentCleanupServiceImpl(
        LambdaAssignmentCleanupDao cleanupDao,
        LambdaVariableDao lambdaVariableDao,
        LambdaRunDao lambdaRunDao)
    {
        this.cleanupDao = cleanupDao;
        this.lambdaVariableDao = lambdaVariableDao;
        this.lambdaRunDao = lambdaRunDao;
    }

    @Override
    @Transactional
    public void enqueueLambdaAssignmentCleanup(int lambdaAssignmentId) {
        long cleanupTime = System.currentTimeMillis() +
            PropertyStore.getDuration(DurationProp.LAMBDA_ASSIGNMENT_DATA_CLEANUP_DELAY).toMillis();
        cleanupDao.enqueueLambdaAssignmentCleanup(lambdaAssignmentId, new Datetime(cleanupTime));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer cleanupNextLambdaAssignment() {
        Integer lambdaAssignmentId = cleanupDao.takeNextLambdaAssignmentForCleanup(Datetime.now());
        if (lambdaAssignmentId == null) return null;

        lambdaVariableDao.deleteLambdaAssignmentVariables(lambdaAssignmentId);
        lambdaRunDao.deleteLambdaAssignmentRunData(lambdaAssignmentId);
        return lambdaAssignmentId;
    }
}
