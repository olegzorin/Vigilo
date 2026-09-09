package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;

public interface LambdaAssignmentDao {
    void insertLambdaAssignment(LambdaAssignment lambdaAssignment);
    boolean updateLambdaAssignment(LambdaAssignment lambdaAssignment);
    boolean markLambdaAssignmentDeleted(int lambdaAssignmentId);
    LambdaAssignment getLambdaAssignment(int lambdaAssignmentId);
    List<Integer> getLambdaAssignmentIds(int lambdaId);
    List<Integer> getLambdaAssignmentIdsForLocation(int locationId);
    List<LambdaAssignment> getLambdaAssignments(Integer lambdaId, Integer locationId, boolean testingOnly);
    void resetLambdaAssignmentRunData(int lambdaAssignmentId);
}
