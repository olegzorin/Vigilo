package dev.olegz.vf.worker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import dev.olegz.vf.core.dao.LambdaRunDao;
import dev.olegz.vf.core.dao.LambdaVariableDao;
import dev.olegz.vf.registry.dao.retry.DataAccessRetry;
import dev.olegz.vf.core.domain.lambdarun.LambdaEventReceipt;
import dev.olegz.vf.core.domain.lambdarun.LambdaRun;
import dev.olegz.vf.core.domain.lambdarun.LambdaRuntimeAssignment;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;
import dev.olegz.vf.core.event.ResetEvent;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.event.TriggerEvent;
import dev.olegz.vf.core.service.lambda.LambdaEventReceiptService;
import dev.olegz.vf.core.service.lambda.TriggerEventDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("lambdaTriggeringService")
public class LambdaTriggeringService {
    private static final Logger logger = LoggerFactory.getLogger(LambdaTriggeringService.class);

    private final LambdaRunDao lambdaRunDao;
    private final LambdaRunService lambdaRunService;
    private final LambdaVariableDao lambdaVariableDao;
    private final LambdaEventReceiptService eventReceiptService;
    private final TriggerEventDataService triggerEventDataService;

    public LambdaTriggeringService(
        LambdaRunDao lambdaRunDao,
        LambdaRunService lambdaRunService,
        LambdaVariableDao lambdaVariableDao,
        LambdaEventReceiptService eventReceiptService,
        TriggerEventDataService triggerEventDataService)
    {
        this.lambdaRunDao = lambdaRunDao;
        this.lambdaRunService = lambdaRunService;
        this.lambdaVariableDao = lambdaVariableDao;
        this.eventReceiptService = eventReceiptService;
        this.triggerEventDataService = triggerEventDataService;
    }

    public void process(TriggerEvent event, BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer) {
        logger.debug(">process() {}", event);

        DataAccessRetry.repeat(
            () -> callLocationLambdas(event, execConsumer),
            () -> "processLambdas for " + event,
            logger);

        logger.debug("<process()");
    }

    public void process(ResetEvent event, BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer) {
        logger.debug(">process() {}", event);

        DataAccessRetry.repeat(
            () -> callAssignedLambda(event, execConsumer),
            () -> "processLambdaReset for " + event,
            logger);

        logger.debug("<process()");
    }

    public void processDurable(String eventId, TriggerEvent event) {
        DataAccessRetry.repeat(
            () -> processDurableTrigger(eventId, event),
            () -> "process durable lambdas for " + event,
            logger);
    }

    public void processDurable(String eventId, ResetEvent event) {
        DataAccessRetry.repeat(
            () -> processDurableReset(eventId, event),
            () -> "process durable lambda reset for " + event,
            logger);
    }

    public void processDurable(String eventId, ScheduledEvent event) {
        DataAccessRetry.repeat(
            () -> processDurableScheduled(eventId, event),
            () -> "process durable scheduled lambda for " + event,
            logger);
    }

    private List<LambdaRuntimeAssignment> getRuntimeAssignmentsForTrigger(int locationId, int trigger) {
        List<LambdaRuntimeAssignment> lambdaAssignments = lambdaRunDao.getRuntimeAssignmentsForTrigger(locationId, trigger);
        if (lambdaAssignments.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("| no lambdas for locationId=" + locationId + ", trigger=" + trigger);
            }
            return null;
        }

        lambdaAssignments = cleanLambdaAssignments(lambdaAssignments);

        if (logger.isDebugEnabled()) {
            logger.debug("| " + lambdaAssignments.size() + " lambdas for locationId=" + locationId + ", trigger=" + trigger);
        }
        return lambdaAssignments;
    }

    /**
     * Select one assignment when the same lambda exists more than once without mutating the source.
     * @param lambdaAssignments lambda assignments to test
     * @return deduplicated assignments
     */
    static List<LambdaRuntimeAssignment> cleanLambdaAssignments(List<LambdaRuntimeAssignment> lambdaAssignments) {
        if ((lambdaAssignments == null) || (lambdaAssignments.size() <= 1)) return lambdaAssignments;

        List<LambdaRuntimeAssignment> result = new ArrayList<>(lambdaAssignments.size());
        for (LambdaRuntimeAssignment candidate : lambdaAssignments) {
            if (candidate == null) continue;

            int duplicateIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                LambdaRuntimeAssignment existing = result.get(i);
                if ((candidate.lambdaId == existing.lambdaId) && (candidate.locationId == existing.locationId)) {
                    duplicateIndex = i;
                    break;
                }
            }

            if (duplicateIndex < 0) {
                result.add(candidate);
                continue;
            }

            LambdaRuntimeAssignment existing = result.get(duplicateIndex);
            if (candidate.lambdaAssignmentId == existing.lambdaAssignmentId) {
                logger.error("Duplicate lambda assignments:\n" + candidate + '\n' + existing);
            }
            if ((candidate.lambdaAssignmentId < existing.lambdaAssignmentId) ||
                ((candidate.lambdaAssignmentId == existing.lambdaAssignmentId) &&
                    (candidate.version.lambdaVersionId > existing.version.lambdaVersionId)))
            {
                result.set(duplicateIndex, candidate);
            }
        }
        return result;
    }

    private void callLocationLambdas(TriggerEvent event, BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer) {
        logger.debug("|>callLocationLambdas() {}", event);

        List<LambdaRuntimeAssignment> triggeredLambdas = getRuntimeAssignmentsForTrigger(event.locationId, event.trigger);
        if ((triggeredLambdas == null) || triggeredLambdas.isEmpty()) {
            logger.debug("|<callLambdasByLocationEvent() no lambdas {}", event);
            return;
        }

        TriggerEventData eventData = triggerEventDataService.hydrate(event);
        dispatchSharedEventData(
            triggeredLambdas,
            eventData,
            (lambdaAssignment, sharedEventData) ->
                lambdaRunService.submitLambdaInput(lambdaAssignment, event, sharedEventData, execConsumer));

        logger.debug("|<callLocationLambdas() {}", event);
    }

    private void processDurableTrigger(String eventId, TriggerEvent event) {
        List<Integer> assignmentIds = eventReceiptService.initialize(eventId, () -> {
            List<LambdaRuntimeAssignment> assignments = getRuntimeAssignmentsForTrigger(event.locationId, event.trigger);
            return assignmentIds(assignments);
        });
        TriggerEventData eventData = assignmentIds.isEmpty() ? null : triggerEventDataService.hydrate(event);

        RuntimeException failure = null;
        for (int lambdaAssignmentId : assignmentIds) {
            try {
                LambdaEventReceipt receipt = eventReceiptService.admitAssignment(eventId, lambdaAssignmentId, () -> {
                    LambdaRuntimeAssignment assignment = getActiveAssignment(lambdaAssignmentId, event.locationId);
                    return assignment == null ? 0L : lambdaRunService.admitDurableLambdaInput(assignment, event, eventData);
                });
                publishAdmittedTrigger(eventId, event, eventData, receipt);
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
                logger.warn("Failed lambda trigger assignment; remaining assignments will still be processed: " +
                    "eventId=" + eventId + ", lambdaAssignmentId=" + lambdaAssignmentId, e);
            }
        }
        if (failure != null) throw failure;
        eventReceiptService.completeEvent(eventId);
    }

    private void processDurableReset(String eventId, ResetEvent event) {
        List<Integer> assignmentIds = eventReceiptService.initialize(eventId, () -> {
            LambdaRuntimeAssignment assignment = getActiveAssignment(event.lambdaAssignmentId, event.locationId);
            return assignment == null ? List.of() : List.of(assignment.lambdaAssignmentId);
        });
        TriggerEventData eventData = assignmentIds.isEmpty() ? null : triggerEventDataService.hydrate(event);

        for (int lambdaAssignmentId : assignmentIds) {
            LambdaEventReceipt receipt = eventReceiptService.admitAssignment(eventId, lambdaAssignmentId, () -> {
                LambdaRuntimeAssignment assignment = getActiveAssignment(lambdaAssignmentId, event.locationId);
                if (assignment == null) return 0L;
                lambdaVariableDao.advanceLambdaAssignmentVariableGeneration(
                    event.lambdaAssignmentId, event.variableGeneration());
                return lambdaRunService.admitDurableLambdaInput(assignment, event, eventData);
            });
            publishAdmittedReset(eventId, event, eventData, receipt);
        }
        eventReceiptService.completeEvent(eventId);
    }

    private void processDurableScheduled(String eventId, ScheduledEvent event) {
        List<Integer> assignmentIds = eventReceiptService.initialize(eventId, () -> {
            LambdaRuntimeAssignment assignment = getActiveAssignment(event.lambdaAssignmentId, event.locationId);
            return assignment == null ? List.of() : List.of(assignment.lambdaAssignmentId);
        });
        TriggerEventData eventData = assignmentIds.isEmpty() ? null : triggerEventDataService.hydrate(event);

        for (int lambdaAssignmentId : assignmentIds) {
            LambdaEventReceipt receipt = eventReceiptService.admitAssignment(eventId, lambdaAssignmentId, () -> {
                LambdaRuntimeAssignment assignment = getActiveAssignment(lambdaAssignmentId, event.locationId);
                return assignment == null ? 0L :
                    lambdaRunService.admitDurableLambdaInput(assignment, event, eventData);
            });
            publishAdmittedScheduled(eventId, event, eventData, receipt);
        }
        eventReceiptService.completeEvent(eventId);
    }

    private void publishAdmittedTrigger(
        String eventId,
        TriggerEvent event,
        TriggerEventData eventData,
        LambdaEventReceipt receipt)
    {
        if (receipt.completed()) return;
        LambdaRuntimeAssignment assignment = getActiveAssignment(receipt.lambdaAssignmentId, event.locationId);
        if (assignment != null) lambdaRunService.publishAdmittedLambdaInput(assignment, event, eventData, receipt.runId);
        eventReceiptService.completeAssignment(eventId, receipt.lambdaAssignmentId, receipt.runId);
    }

    private void publishAdmittedReset(
        String eventId,
        ResetEvent event,
        TriggerEventData eventData,
        LambdaEventReceipt receipt)
    {
        if (receipt.completed()) return;
        LambdaRuntimeAssignment assignment = getActiveAssignment(receipt.lambdaAssignmentId, event.locationId);
        if (assignment != null) lambdaRunService.publishAdmittedLambdaInput(assignment, event, eventData, receipt.runId);
        eventReceiptService.completeAssignment(eventId, receipt.lambdaAssignmentId, receipt.runId);
    }

    private void publishAdmittedScheduled(
        String eventId,
        ScheduledEvent event,
        TriggerEventData eventData,
        LambdaEventReceipt receipt)
    {
        if (receipt.completed()) return;
        LambdaRuntimeAssignment assignment = getActiveAssignment(receipt.lambdaAssignmentId, event.locationId);
        if (assignment != null) {
            lambdaRunService.publishAdmittedLambdaInput(assignment, event, eventData, receipt.runId);
        }
        eventReceiptService.completeAssignment(eventId, receipt.lambdaAssignmentId, receipt.runId);
    }

    private void callAssignedLambda(ResetEvent event, BiConsumer<LambdaRun, LambdaRuntimeAssignment> execConsumer) {
        logger.debug("|>callAssignedLambda() {}", event);

        LambdaRuntimeAssignment lambdaAssignment = getActiveAssignment(event.lambdaAssignmentId, event.locationId);
        if (lambdaAssignment == null) {
            logger.debug("|<callAssignedLambda() no active assignment {}", event);
            return;
        }

        lambdaVariableDao.advanceLambdaAssignmentVariableGeneration(
            event.lambdaAssignmentId, event.variableGeneration());
        TriggerEventData eventData = triggerEventDataService.hydrate(event);
        lambdaRunService.submitLambdaInput(lambdaAssignment, event, eventData, execConsumer);

        logger.debug("|<callAssignedLambda() {}", event);
    }

    private LambdaRuntimeAssignment getActiveAssignment(int lambdaAssignmentId, int locationId) {
        LambdaRuntimeAssignment lambdaAssignment = lambdaRunDao.getActiveLambdaRuntimeAssignment(lambdaAssignmentId);
        return lambdaAssignment != null && lambdaAssignment.locationId == locationId ? lambdaAssignment : null;
    }

    static void dispatchSharedEventData(
        List<LambdaRuntimeAssignment> triggeredLambdas,
        TriggerEventData eventData,
        BiConsumer<LambdaRuntimeAssignment, TriggerEventData> submitter)
    {
        for (LambdaRuntimeAssignment lambdaAssignment : triggeredLambdas) {
            if (lambdaAssignment == null) continue;
            submitter.accept(lambdaAssignment, eventData);
        }
    }

    private static List<Integer> assignmentIds(List<LambdaRuntimeAssignment> assignments) {
        if ((assignments == null) || assignments.isEmpty()) return List.of();
        List<Integer> ids = new ArrayList<>(assignments.size());
        for (LambdaRuntimeAssignment assignment : assignments) {
            if (assignment != null) ids.add(assignment.lambdaAssignmentId);
        }
        return ids;
    }
}
