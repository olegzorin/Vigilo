package dev.olegz.vf.core.service.lambda;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import dev.olegz.vf.core.dao.LambdaEventReceiptDao;
import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LambdaEventReceiptServiceImpl implements LambdaEventReceiptService {
    private final LambdaEventReceiptDao receiptDao;

    public LambdaEventReceiptServiceImpl(LambdaEventReceiptDao receiptDao) {
        this.receiptDao = receiptDao;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Integer> initialize(String eventId, Supplier<List<Integer>> lambdaAssignmentIds) {
        if (!receiptDao.insertMarker(eventId)) return receiptDao.getAssignmentIds(eventId);

        List<Integer> ids = normalize(lambdaAssignmentIds.get());
        if (!ids.isEmpty()) receiptDao.insertAssignments(eventId, ids);
        return ids;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LambdaEventReceipt admitAssignment(String eventId, int lambdaAssignmentId, LongSupplier admitter) {
        LambdaEventReceipt receipt = receiptDao.getForUpdate(eventId, lambdaAssignmentId);
        if (receipt == null) {
            throw new IllegalStateException(
                "Missing lambda event receipt: eventId=" + eventId + ", lambdaAssignmentId=" + lambdaAssignmentId);
        }
        if (receipt.completed() || LambdaEventReceipt.ADMITTED.equals(receipt.status)) return receipt;
        if (!LambdaEventReceipt.PENDING.equals(receipt.status)) {
            throw new IllegalStateException("Unexpected lambda event receipt status=" + receipt.status +
                ", eventId=" + eventId + ", lambdaAssignmentId=" + lambdaAssignmentId);
        }

        long runId = admitter.getAsLong();
        if (runId > 0L) {
            receiptDao.markAdmitted(eventId, lambdaAssignmentId, runId);
            receipt.status = LambdaEventReceipt.ADMITTED;
            receipt.runId = runId;
        } else {
            receiptDao.completeAssignment(eventId, lambdaAssignmentId);
            receipt.status = LambdaEventReceipt.COMPLETED;
        }
        return receipt;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAssignment(String eventId, int lambdaAssignmentId, long runId) {
        LambdaEventReceipt receipt = receiptDao.getForUpdate(eventId, lambdaAssignmentId);
        if (receipt == null) {
            throw new IllegalStateException(
                "Missing lambda event receipt: eventId=" + eventId + ", lambdaAssignmentId=" + lambdaAssignmentId);
        }
        if (receipt.completed()) return;
        if (!LambdaEventReceipt.ADMITTED.equals(receipt.status) || (receipt.runId == null) || (receipt.runId != runId)) {
            throw new IllegalStateException("Lambda event receipt admission changed: eventId=" + eventId +
                ", lambdaAssignmentId=" + lambdaAssignmentId + ", runId=" + runId);
        }
        receiptDao.completeAssignment(eventId, lambdaAssignmentId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeEvent(String eventId) {
        if (receiptDao.countPendingAssignments(eventId) != 0) {
            throw new IllegalStateException("Lambda event still has pending assignments: eventId=" + eventId);
        }
        receiptDao.completeMarker(eventId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteCompletedBefore(Timestamp cutoff, int limit) {
        List<String> eventIds = receiptDao.getCompletedEventIdsBefore(cutoff, limit);
        if (eventIds.isEmpty()) return 0;
        receiptDao.deleteEvents(eventIds);
        return eventIds.size();
    }

    private static List<Integer> normalize(List<Integer> ids) {
        if ((ids == null) || ids.isEmpty()) return List.of();
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(ids.size());
        for (Integer id : ids) {
            if ((id != null) && (id > 0)) unique.add(id);
        }
        return new ArrayList<>(unique);
    }
}
