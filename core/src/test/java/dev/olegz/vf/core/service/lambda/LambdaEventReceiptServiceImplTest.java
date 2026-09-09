package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.*;

import dev.olegz.vf.core.dao.LambdaEventReceiptDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LambdaEventReceiptServiceImplTest {
    @Test
    void freezesFanOutAndSkipsCompletedAssignmentsOnReplay() {
        RecordingDao dao = new RecordingDao();
        LambdaEventReceiptServiceImpl service = new LambdaEventReceiptServiceImpl(dao);
        int[] selections = new int[1];

        assertEquals(List.of(2, 1), service.initialize("event-1", () -> {
            selections[0]++;
            return Arrays.asList(2, 1, 2, null, 0);
        }));
        assertEquals(List.of(2, 1), service.initialize("event-1", () -> {
            throw new AssertionError("replay must use the frozen assignment set");
        }));
        assertEquals(1, selections[0]);

        int[] calls = new int[1];
        LambdaEventReceipt admitted = service.admitAssignment("event-1", 1, () -> {
            calls[0]++;
            return 101L;
        });
        assertEquals(LambdaEventReceipt.ADMITTED, admitted.status);
        assertEquals(101L, service.admitAssignment("event-1", 1, () -> {
            throw new AssertionError("admitted assignment must not be admitted again");
        }).runId);
        assertEquals(1, calls[0]);

        assertThrows(IllegalStateException.class, () -> service.completeEvent("event-1"));
        service.completeAssignment("event-1", 1, 101L);
        assertTrue(service.admitAssignment("event-1", 2, () -> 0L).completed());
        service.completeEvent("event-1");
        assertEquals(LambdaEventReceipt.COMPLETED, dao.receipts.get("event-1:0").status);
    }

    private static final class RecordingDao implements LambdaEventReceiptDao {
        private final Map<String, LambdaEventReceipt> receipts = new HashMap<>();
        private final Map<String, List<Integer>> assignments = new HashMap<>();

        @Override
        public boolean insertMarker(String eventId) {
            if (assignments.containsKey(eventId)) return false;
            assignments.put(eventId, new ArrayList<>());
            receipts.put(eventId + ":0", receipt(eventId, 0, LambdaEventReceipt.OPEN));
            return true;
        }

        @Override
        public void insertAssignments(String eventId, List<Integer> ids) {
            assignments.get(eventId).addAll(ids);
            for (int id : ids) receipts.put(eventId + ':' + id, receipt(eventId, id, LambdaEventReceipt.PENDING));
        }

        @Override public List<Integer> getAssignmentIds(String eventId) {
            return List.copyOf(assignments.get(eventId));
        }
        @Override public LambdaEventReceipt getForUpdate(String eventId, int id) {
            return receipts.get(eventId + ':' + id);
        }
        @Override public void markAdmitted(String eventId, int id, long runId) {
            LambdaEventReceipt receipt = receipts.get(eventId + ':' + id);
            receipt.status = LambdaEventReceipt.ADMITTED;
            receipt.runId = runId;
        }
        @Override public void completeAssignment(String eventId, int id) {
            receipts.get(eventId + ':' + id).status = LambdaEventReceipt.COMPLETED;
        }
        @Override public int countPendingAssignments(String eventId) {
            int count = 0;
            for (int id : assignments.get(eventId)) {
                if (!receipts.get(eventId + ':' + id).completed()) count++;
            }
            return count;
        }
        @Override public void completeMarker(String eventId) {
            receipts.get(eventId + ":0").status = LambdaEventReceipt.COMPLETED;
        }
        @Override public List<String> getCompletedEventIdsBefore(Timestamp cutoff, int limit) { return List.of(); }
        @Override public void deleteEvents(List<String> eventIds) { }

        private static LambdaEventReceipt receipt(String eventId, int assignmentId, String status) {
            LambdaEventReceipt receipt = new LambdaEventReceipt();
            receipt.eventId = eventId;
            receipt.lambdaAssignmentId = assignmentId;
            receipt.status = status;
            return receipt;
        }
    }
}
