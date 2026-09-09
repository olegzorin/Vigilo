package dev.olegz.vf.core.service.lambda;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import dev.olegz.vf.core.domain.lambdarun.LambdaError;

public interface LambdaWorkerService {

    LambdaCodeUpload getUploadForProcessing(int lambdaId, int uploadId);

    boolean commitUpload(LambdaCodeUpload upload);

    LambdaCodeUpload failExpiredUpload(int lambdaId, int uploadId, Datetime expiredBefore);

    void registerLambdaError(LambdaError lambdaError);
}
