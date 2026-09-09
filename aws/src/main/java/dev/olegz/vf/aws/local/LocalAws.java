package dev.olegz.vf.aws.local;

import java.io.File;
import java.nio.file.Path;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.props.PropertyStore;

/**
 * Runtime switch and shared configuration for the local (non-AWS) client implementations.
 * <p>
 * When {@code vf.aws.local=true}, {@link AwsClients} hands out local clients backed by the
 * local file system instead of real AWS SDK clients. Local data is stored under {@code <home>/aws}
 * (see {@link VigiloEnvironment#getHomeDir()}), partitioned by region.
 * <p>
 * This class reads only {@link PropertyStore} and the region from {@link AwsClients#REGION}. It must
 * never reference {@code AwsCredentials}, whose static initializer fails when AWS is not configured -
 * which is exactly the situation in local mode; {@link AwsClients#REGION} is safe here because it
 * short-circuits to a default region in local mode.
 */
public final class LocalAws {
    private LocalAws() {
    }

    /** Whether local (non-AWS) client implementations are in effect. */
    public static final boolean ENABLED = PropertyStore.getBoolean("vf.aws.local", false);

    /** A synthetic AWS account id used in generated ARNs. */
    public static final String ACCOUNT_ID = "000000000000";

    private static final String ROOT = PropertyStore.getString(
        "vf.aws.local.root",
        Path.of(VigiloEnvironment.getHomeDir(), "aws").toString()
    );

    /** File backing an S3 object: {@code <root>/s3/<region>/<bucket>/<key>}. Object keys may contain '/'. */
    public static File s3File(String bucket, String key) {
        if (bucket == null || key == null) return null;
        return Path.of(ROOT, "s3", AwsClients.REGION.id(), bucket, key).toFile();
    }

    /** Root directory for local CloudWatch logs: {@code <root>/<region>/logs}. */
    public static File logsRoot() {
        return Path.of(ROOT, AwsClients.REGION.id(), "logs").toFile();
    }
}
