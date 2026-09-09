package dev.olegz.vf.core.dao.mapper;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import org.apache.ibatis.annotations.Param;

public interface LambdaEventReceiptMapper {
    boolean insertMarker(@Param("eventId") String eventId);
    void insertAssignments(
        @Param("eventId") String eventId,
        @Param("lambdaAssignmentIds") List<Integer> lambdaAssignmentIds);
    List<Integer> selectAssignmentIds(String eventId);
    LambdaEventReceipt selectForUpdate(
        @Param("eventId") String eventId,
        @Param("lambdaAssignmentId") int lambdaAssignmentId);
    void updateAssignmentAdmitted(
        @Param("eventId") String eventId,
        @Param("lambdaAssignmentId") int lambdaAssignmentId,
        @Param("runId") long runId);
    void updateAssignmentCompleted(
        @Param("eventId") String eventId,
        @Param("lambdaAssignmentId") int lambdaAssignmentId);
    int countPendingAssignments(String eventId);
    void updateMarkerCompleted(String eventId);
    List<String> selectCompletedEventIdsBefore(
        @Param("cutoff") Timestamp cutoff,
        @Param("limit") int limit);
    void deleteEvents(List<String> eventIds);
}
