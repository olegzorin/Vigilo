package dev.olegz.vf.core.dao.metric;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.olegz.vf.common.props.PropertyStore;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LambdaWorkloadMetric {
    boolean ENABLED = PropertyStore.getBoolean("vf.lambdaWorkloadMetrics.enabled", true);

    enum Operation {

        workflowPutNewRun("workflow.putNewRun"),
        workflowPutNextRun("workflow.putNextRun"),
        workflowMarkLambdaRunNotStartedIfUnchanged("workflow.markLambdaRunNotStartedIfUnchanged"),
        workflowUpdateLambdaRunRetry("workflow.updateLambdaRunRetry"),

        sqlGetActiveLambdaRuntimeAssignmentById("sql.getActiveLambdaRuntimeAssignmentById"),
        sqlUpdateLambdaPendingInput("sql.updateLambdaPendingInput"),
        sqlInsertLambdaPendingInput("sql.insertLambdaPendingInput"),
        sqlSelectLambdaPendingInputs("sql.selectLambdaPendingInputs"),
        sqlSelectLambdaRunForUpdate("sql.selectLambdaRunForUpdate"),
        sqlSelectLambdaRun("sql.selectLambdaRun"),
        sqlUpdateLambdaRunPendingCount("sql.updateLambdaRunPendingCount"),
        sqlUpdateLambdaRunAsStarted("sql.updateLambdaRunAsStarted"),
        sqlUpdateLambdaRunSubmitAsync("sql.updateLambdaRunSubmitAsync"),
        sqlCompleteLambdaRun("sql.completeLambdaRun"),
        sqlInsertLambdaRunsInfo("sql.insertLambdaRunsInfo"),
        sqlSelectLambdaRunsRequiringCompletion("sql.selectLambdaRunsRequiringCompletion"),
        sqlSelectAsyncLambdaRunsRequiringCompletion("sql.selectAsyncLambdaRunsRequiringCompletion"),
        sqlSelectScheduledLambdaAssignments("sql.selectScheduledLambdaAssignments"),
        sqlSelectRuntimeAssignmentsForLocation("sql.selectRuntimeAssignmentsForLocation"),
        sqlSelectTriggerLocationMetadata("sql.selectTriggerLocationMetadata"),
        sqlSelectTriggerLocationDeviceMetadata("sql.selectTriggerLocationDeviceMetadata"),
        sqlSelectTriggerLocationHydration("sql.selectTriggerLocationHydration"),
        sqlSelectLambdaVariables("sql.selectLambdaVariables"),
        sqlUpdateLambdaVariable("sql.updateLambdaVariable"),
        sqlInsertLambdaVariable("sql.insertLambdaVariable"),
        sqlDeleteLambdaVariable("sql.deleteLambdaVariable");

        private final String metricName;

        Operation(String metricName) {
            this.metricName = metricName;
        }

        String metricName() {
            return metricName;
        }
    }

    Operation operation();
}
