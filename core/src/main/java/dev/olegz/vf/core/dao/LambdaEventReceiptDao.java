package dev.olegz.vf.core.dao;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;

public interface LambdaEventReceiptDao {
    boolean insertMarker(String eventId);
    void insertAssignments(String eventId, List<Integer> lambdaAssignmentIds);
    List<Integer> getAssignmentIds(String eventId);
    LambdaEventReceipt getForUpdate(String eventId, int lambdaAssignmentId);
    void markAdmitted(String eventId, int lambdaAssignmentId, long runId);
    void completeAssignment(String eventId, int lambdaAssignmentId);
    int countPendingAssignments(String eventId);
    void completeMarker(String eventId);
    List<String> getCompletedEventIdsBefore(Timestamp cutoff, int limit);
    void deleteEvents(List<String> eventIds);
}
