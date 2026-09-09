package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.core.domain.lambdarun.LambdaAssignmentLog;

public interface LambdaLogService {
    String functionLogGroupNamePrefix = "/aws/lambda/";

    void createFunctionLogGroup(String functionName);

    void deleteFunctionLogGroup(String functionName);

    void writeLambdaAssignmentLog(LambdaAssignmentLog log);
}
