package dev.olegz.vf.core.dao.mapper;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import org.apache.ibatis.annotations.Param;

public interface LambdaCodeUploadMapper {
    void insertLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload);
    void updateLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload);
    int updateInProgressLambdaCodeUploadFunctions(LambdaCodeUpload lambdaCodeUpload);
    void updateLambdaCodeUploadFunctionDeletionDate(
        @Param("uploadId") int uploadId,
        @Param("deletionDate") Datetime deletionDate,
        @Param("deletionInfo") String deletionInfo);
    LambdaCodeUpload selectCodeUploadById(int uploadId);
    LambdaCodeUpload selectCodeUpload(int uploadId);
    LambdaCodeUpload selectCodeUploadByLambdaId(
        @Param("lambdaId") int lambdaId,
        @Param("uploadId") int uploadId);
    List<LambdaCodeUpload> selectExpiredLambdaCodeUploads(
        @Param("expiredBefore") Datetime expiredBefore,
        @Param("limit") int limit);
    List<LambdaCodeUpload> selectInactiveCodeUploads();
}
