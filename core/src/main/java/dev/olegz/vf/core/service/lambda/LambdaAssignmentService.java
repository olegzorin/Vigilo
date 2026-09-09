package dev.olegz.vf.core.service.lambda;

import java.util.List;

import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;
import dev.olegz.vf.core.domain.lambdarun.LambdaKeyInput;

public interface LambdaAssignmentService {
    int createLambdaAssignment(User user, int lambdaId, int locationId, boolean testing);

    LambdaAssignment getLambdaAssignment(User user, int lambdaAssignmentId);

    LambdaKeyInput generateLambdaApiKey(User user, int lambdaAssignmentId);

    List<LambdaAssignment> getLambdaAssignmentsForLambda(User user, int lambdaId);

    List<LambdaAssignment> getLambdaAssignmentsForLocation(User user, int locationId);

    void setLambdaAssignmentEnabled(User user, int lambdaAssignmentId, boolean enabled);

    void cancelLambdaAssignment(User user, int lambdaAssignmentId);

    void cancelAssignmentsForLocation(User user, int locationId);

    void deleteAssignmentsForLocation(int locationId);

    void cancelAssignmentsForLambda(User user, int appId);

    /*
    void deleteLambdaAssignmentWorkData(int lambdaAssignmentId);

    LambdaAssignment getLambdaAssignmentForLogExport(User user, int lambdaAssignmentId);

    void putVariable(int lambdaAssignmentId, int locationId, boolean shared, String name, byte[] value);

    void putVariables(int lambdaAssignmentId, String name, byte[] value);

    byte[] getVariable(int lambdaAssignmentId, int locationId, boolean shared, String name);

    void deleteVariable(int lambdaAssignmentId, int locationId, boolean shared, String name);

    void deleteInactiveVariables();

    void deleteExecutionErrors(int appVersionId);

     */
}
