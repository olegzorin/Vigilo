package dev.olegz.vf.worker.domain;

import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public class UpdateFunctionParams {

    public final String functionName;
    public final String functionDesc;
    public final byte arch;
    public final String imageUri;
    public final String versionDesc;
    public final int memory;
    public final int timeout;
    public final InvocationLane lane;

    public UpdateFunctionParams(InvocationLane lane, String functionName, String functionDesc, byte arch, String imageUri,
                                String versionDesc, InvocationLaneSettings settings) {
        this.lane = lane;
        this.functionName = functionName;
        this.functionDesc = functionDesc;
        this.arch = arch;
        this.imageUri = imageUri;
        this.versionDesc = versionDesc;
        this.memory = settings.memorySize();
        this.timeout = settings.timeout();
    }

    public static UpdateFunctionParams create(LambdaCodeUpload upload, InvocationLane lane, String imageUri) {
        String functionDesc = upload.lambda.lambdaName + ", ver." + upload.lambdaVersion.version +
            (lane == InvocationLane.ASYNC ? " (async)" : "");
        InvocationLaneSettings settings = InvocationLaneSettings.forLane(lane);
        return new UpdateFunctionParams(lane, upload.getBaseFunctionName(lane), functionDesc, upload.arch, imageUri,
            functionDesc + "; upload ID=" + upload.uploadId,
            settings);
    }

    @Override
    public String toString() {
        return "lane=" + lane +
            ", functionName=" + functionName +
            ", functionDesc=" + functionDesc +
            ", arch=" + arch +
            ", imageUri=" + imageUri +
            ", versionDesc=" + versionDesc +
            ", memory=" + memory +
            ", timeout=" + timeout;
    }
}
