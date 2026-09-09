package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;

public interface LambdaEventReceiptService {
    List<Integer> initialize(String eventId, Supplier<List<Integer>> lambdaAssignmentIds);
    LambdaEventReceipt admitAssignment(String eventId, int lambdaAssignmentId, LongSupplier admitter);
    void completeAssignment(String eventId, int lambdaAssignmentId, long runId);
    void completeEvent(String eventId);
    int deleteCompletedBefore(Timestamp cutoff, int limit);
}
