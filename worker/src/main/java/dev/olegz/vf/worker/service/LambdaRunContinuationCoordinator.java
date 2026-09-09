package dev.olegz.vf.worker.service;

import java.util.function.Consumer;

import dev.olegz.vf.registry.dao.retry.RetryOnConcurrencyFailure;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunContext;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.service.lambda.LambdaRunStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Advances a completed lane only when Kafka confirms publication of its pending continuation. */
@Service
public class LambdaRunContinuationCoordinator {
    private final LambdaRunStateService stateService;

    public LambdaRunContinuationCoordinator(LambdaRunStateService stateService) {
        this.stateService = stateService;
    }

    @RetryOnConcurrencyFailure("")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaRun completeAndAdvance(
        LambdaRunContext completed,
        LambdaRuntimeAssignment lambdaAssignment,
        Consumer<LambdaRun> publisher)
    {
        if (stateService.checkRunFinal(
            completed.lambdaAssignmentId, completed.lane, completed.requestId))
        {
            return null;
        }

        LambdaRun run = stateService.putNextRunTransactional(
            completed.lambdaAssignmentId, completed.lane, lambdaAssignment, completed.requestId);
        if (run != null) publisher.accept(run);
        return run;
    }
}
