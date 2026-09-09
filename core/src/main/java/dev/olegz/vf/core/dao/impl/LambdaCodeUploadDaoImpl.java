package dev.olegz.vf.core.dao.impl;

import java.util.List;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.core.dao.LambdaCodeUploadDao;
import dev.olegz.vf.core.dao.mapper.LambdaCodeUploadMapper;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

@Repository
public class LambdaCodeUploadDaoImpl implements LambdaCodeUploadDao {
    private final LambdaCodeUploadMapper mapper;

    public LambdaCodeUploadDaoImpl(LambdaCodeUploadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload) {
        mapper.insertLambdaCodeUpload(lambdaCodeUpload);
    }

    @Override
    public void updateLambdaCodeUpload(LambdaCodeUpload lambdaCodeUpload) {
        lambdaCodeUpload.message = StringUtils.truncate(StringUtils.trimToNull(lambdaCodeUpload.message), 10_000);
        mapper.updateLambdaCodeUpload(lambdaCodeUpload);
    }

    @Override
    public boolean updateInProgressLambdaCodeUploadFunctions(LambdaCodeUpload lambdaCodeUpload) {
        return mapper.updateInProgressLambdaCodeUploadFunctions(lambdaCodeUpload) == 1;
    }

    @Override
    public void setLambdaCodeUploadFunctionDeletionDate(int uploadId, Datetime date, String info) {
        mapper.updateLambdaCodeUploadFunctionDeletionDate(uploadId, date, info);
    }

    @Override
    public LambdaCodeUpload getLambdaCodeUpload(int uploadId) {
        return mapper.selectCodeUpload(uploadId);
    }

    @Override
    public LambdaCodeUpload getLambdaCodeUpload(int lambdaId, int uploadId) {
        return mapper.selectCodeUploadByLambdaId(lambdaId, uploadId);
    }

    @Override
    public List<LambdaCodeUpload> getExpiredLambdaCodeUploads(Datetime expiredBefore, int limit) {
        return mapper.selectExpiredLambdaCodeUploads(expiredBefore, limit);
    }

    @Override
    public List<LambdaCodeUpload> getLambdaCodeUploadsForCleanup() {
        return mapper.selectInactiveCodeUploads();
    }
}
