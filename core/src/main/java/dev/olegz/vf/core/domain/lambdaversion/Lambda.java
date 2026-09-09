package dev.olegz.vf.core.domain.lambdaversion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.core.domain.lambdabuild.LambdaCodeUpload;

public class Lambda {
    public static final String S3_BUCKET = PropertyStore.getString("vf.aws.s3.sourceCodeBucket");
    public static final String EXECUTION_ROLE = "dev-botlab-execution-role";
    public static final String SQS_RESULT_QUEUE = "dev-botlab-lambda-results";
    public static final String SQS_RESULT_DEAD_LETTER_QUEUE = SQS_RESULT_QUEUE + "-dlq";
    public static final String SNS_BUILD_ERROR_TOPIC = "dev-botlab-lambda-build-errors";
    public static final String SNS_RUNTIME_ERROR_TOPIC = "dev-botlab-lambda-runtime-errors";

    public int lambdaId;
    public String lambdaName;
    public String description;
    public Map<String, Object> metadata;
    public int devTeamId;
    public Datetime createdAt;

    /**
     * Highest version number ever published for this lambda — a monotonic high-water mark that is the
     * basis for the next development version's number. Advanced only when a version reaches
     * {@code PRODUCTION}, so it never regresses on rollback and survives version deletion.
     * {@code null} until the lambda's first version is published.
     */
    public String maxVersion;

    /**
     * Monotonic per-lambda counter that hands out the next {@link LambdaVersion#sequenceNumber}. Unlike
     * {@link #maxVersion} it advances every time a new version row is created (not only on publish),
     * so it gives each version a stable, gap-free internal creation ordinal. Starts at 0; the first
     * created version gets sequence number 1. This is an internal ordering key and is never exposed
     * by the API.
     */
    public int maxSequence;

    public List<LambdaVersion> lambdaVersions;
    public Integer codeUploadId;
    public LambdaCodeUpload codeUpload;

    @Override
    public String toString() {
        return "{lambdaId=" + lambdaId + ", " + lambdaName + ", metadata=" + metadata + '}';
    }


    @Override
    public boolean equals(Object obj) {
        return (this == obj) ||
            (obj instanceof Lambda that) &&
                (this.lambdaId == that.lambdaId) &&
                this.lambdaName.equals(that.lambdaName) &&
                Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        throw new ApplicationFailureException("hashCode not designed for " + this.getClass().getName());
    }

    public LambdaVersion getDevelopmentLambdaVersion() {
        return CollectionOps.findAny(lambdaVersions, v -> v.status.isDevelopment());
    }

    public LambdaVersion getPublicLambdaVersion() {
        return CollectionOps.findAny(lambdaVersions, v -> (v.status == LambdaVersionStatus.PRODUCTION));
    }

    // Version visible to developer
    public LambdaVersion getLastLambdaVersion() {
        LambdaVersion devVersion = getDevelopmentLambdaVersion();
        return devVersion != null ? devVersion : getPublicLambdaVersion();
    }

    public LambdaVersion getLatestRunnableLambdaVersion() {
        LambdaVersion devVersion = getDevelopmentLambdaVersion();
        return (devVersion != null) && devVersion.isRunnable() ? devVersion : getPublicLambdaVersion();
    }

}
