package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;

public interface LambdaVariableDao {
    byte[] getLambdaAssignmentVariable(int lambdaAssignmentId, long generation, String name);
    byte[] getLambdaLocationVariable(int locationId, String name);
    void deleteLambdaAssignmentVariable(int lambdaAssignmentId, long generation, String name);
    void deleteLambdaAssignmentVariables(int lambdaAssignmentId);
    void deleteLambdaLocationVariable(int locationId, String name);
    void putLambdaAssignmentVariable(int lambdaAssignmentId, long generation, String name, byte[] value);
    void putLambdaLocationVariable(int locationId, String name, byte[] value);
    long getLambdaAssignmentVariableGeneration(int lambdaAssignmentId);
    void advanceLambdaAssignmentVariableGeneration(int lambdaAssignmentId, long generation);
    List<Integer> getInactiveAssignmentVariables(Datetime date, int maxCount);
    List<Integer> getInactiveLocationVariables(Datetime date, int maxCount);
}
