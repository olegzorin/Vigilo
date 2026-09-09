package dev.olegz.vf.core.dao.impl;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.core.dao.LambdaEventReceiptDao;
import dev.olegz.vf.core.dao.mapper.LambdaEventReceiptMapper;
import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaEventReceiptDaoImpl implements LambdaEventReceiptDao {
    private final LambdaEventReceiptMapper mapper;

    public LambdaEventReceiptDaoImpl(LambdaEventReceiptMapper mapper) {
        this.mapper = mapper;
    }

    @Override public boolean insertMarker(String eventId) { return mapper.insertMarker(eventId); }
    @Override public void insertAssignments(String eventId, List<Integer> ids) { mapper.insertAssignments(eventId, ids); }
    @Override public List<Integer> getAssignmentIds(String eventId) { return mapper.selectAssignmentIds(eventId); }
    @Override public LambdaEventReceipt getForUpdate(String eventId, int id) {
        return mapper.selectForUpdate(eventId, id);
    }
    @Override public void markAdmitted(String eventId, int id, long runId) {
        mapper.updateAssignmentAdmitted(eventId, id, runId);
    }
    @Override public void completeAssignment(String eventId, int id) { mapper.updateAssignmentCompleted(eventId, id); }
    @Override public int countPendingAssignments(String eventId) { return mapper.countPendingAssignments(eventId); }
    @Override public void completeMarker(String eventId) { mapper.updateMarkerCompleted(eventId); }
    @Override public List<String> getCompletedEventIdsBefore(Timestamp cutoff, int limit) {
        return mapper.selectCompletedEventIdsBefore(cutoff, limit);
    }
    @Override public void deleteEvents(List<String> eventIds) { mapper.deleteEvents(eventIds); }
}
