package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;

public interface LambdaCodeUploadDao {
    void insertLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload);
    void updateLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload);
    boolean updateInProgressLambdaCodeUploadFunctions(LambdaCodeUpload lambdaCodeUpload);
    void setLambdaCodeUploadFunctionDeletionDate(int uploadId, Datetime date, String info);
    LambdaCodeUpload getLambdaCodeUpload(int uploadId);
    LambdaCodeUpload getLambdaCodeUpload(int lambdaId, int uploadId);
    List<LambdaCodeUpload> getExpiredLambdaCodeUploads(Datetime expiredBefore, int limit);
    List<LambdaCodeUpload> getLambdaCodeUploadsForCleanup();
}
