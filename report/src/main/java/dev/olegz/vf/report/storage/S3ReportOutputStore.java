package dev.olegz.vf.report.storage;

import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.PropertyStore;
import org.springframework.stereotype.Component;

/** Stores oversized report ZIPs in the configured reports S3 bucket. */
@Component
public class S3ReportOutputStore implements ReportOutputStore {
    private static final String REPORTS_BUCKET_PROPERTY = "vf.aws.s3.reportsBucket";
    private static final String CONTENT_TYPE_ZIP = "application/zip";

    @Override
    public byte[] get(String objectId) {
        String bucket = reportsBucket(false);
        return bucket == null ? null : S3Support.getData(bucket, objectId);
    }

    @Override
    public void put(String objectId, byte[] data) {
        S3Support.createObject(reportsBucket(true), objectId, data, CONTENT_TYPE_ZIP, false);
    }

    private static String reportsBucket(boolean required) {
        String bucket = PropertyStore.getString(REPORTS_BUCKET_PROPERTY, true);
        if ((bucket == null) || "?".equals(bucket)) {
            if (required) {
                throw new ApplicationFailureException(REPORTS_BUCKET_PROPERTY + " is not configured");
            }
            return null;
        }
        return bucket;
    }
}
