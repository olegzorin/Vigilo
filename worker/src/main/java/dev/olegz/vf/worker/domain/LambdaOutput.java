package dev.olegz.vf.worker.domain;

import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaLogEvent;
import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;

public class LambdaOutput {
    public Byte startCode;
    public long startTime;
    public long endTime;
    public List<LambdaLogEvent> logEvents;
    // This message can be generated either by the lambda code or by the Lambda engine in case of run error
    public String errorMessage;

    public long duration() {
        return (startTime == 0) || (endTime == 0) ? 0 : (endTime - startTime);
    }

    public byte resultCode() {
        return (startCode != null) && (startCode != 0) ? LambdaRunInfo.DUPLICATE_REQUEST :
            errorMessage == null || errorMessage.isBlank() ? LambdaRunInfo.SUCCESS :
                errorMessage.contains("We currently do not have sufficient capacity") ? LambdaRunInfo.AWS_SERVER_CAPACITY_ERROR :
                    LambdaRunInfo.LAMBDA_ERROR;
    }
}
