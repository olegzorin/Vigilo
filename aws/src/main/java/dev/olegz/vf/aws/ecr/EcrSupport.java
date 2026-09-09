package dev.olegz.vf.aws.ecr;

import java.util.Collection;
import java.util.List;

import dev.olegz.vf.aws.AwsResourceNames;
import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.aws.iam.IamSupport;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.util.CollectionOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;

/**
 * ECR private-registry operations: repository resolution and image checks/deletion.
 * <p>
 * Thin static wrapper matching the module's other AWS helpers ({@link dev.olegz.vf.aws.s3.S3Support},
 * {@link dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport}). The shared {@link EcrClient} is built
 * lazily on first use and held for the JVM lifetime; it comes from {@link AwsClients#ecrClient}, so it is the
 * real ECR client or the local one per {@code vf.aws.local}, and is released by {@link #shutdown()} on
 * context shutdown. The underlying HTTP client is shared across AWS clients and owned by {@link AwsClients}.
 */
public final class EcrSupport {
    private EcrSupport() {
    }

    private static final Logger logger = LoggerFactory.getLogger(EcrSupport.class);

    /**
     * The shared {@link EcrClient}, built lazily on first use and held for the JVM lifetime;
     * rebuilt if {@link #shutdown()} closed it (e.g. between test contexts).
     */
    private static final AwsClients.LazyClient<EcrClient> CLIENT = AwsClients.lazyClient("ECR", AwsClients::ecrClient);

    private static EcrClient client() {
        return CLIENT.get();
    }

    /** Idempotent: closes the shared client and its connection pool; safe to call more than once. */
    public static void shutdown() {
        CLIENT.shutdown();
    }

    /**
     * Returns the URI of the repository {@code repoName} in this account's private registry, creating it
     * first if it does not exist. New repositories are created with immutable image tags, scan-on-push
     * disabled and AES256 encryption. Callers that resolve the same repository repeatedly should cache the
     * result, as this performs an ECR call each time.
     */
    public static String getOrCreateRepository(String repoName) {
        String resourceName = AwsResourceNames.prefixed(repoName);
        String accountId = IamSupport.getAccountId();
        try {
            return client().describeRepositories(
                req -> req.registryId(accountId).repositoryNames(resourceName)
            ).repositories().getFirst().repositoryUri();
        } catch (RepositoryNotFoundException x) {
            try {
                return client().createRepository(
                    r -> r.registryId(accountId).repositoryName(resourceName)
                        .imageTagMutability(ImageTagMutability.IMMUTABLE)
                        .imageScanningConfiguration(c -> c.scanOnPush(false))
                        .encryptionConfiguration(c -> c.encryptionType(EncryptionType.AES256))
                ).repository().repositoryUri();
            } catch (Exception e) {
                throw new ApplicationFailureException("Exception in creation of repoName=" + resourceName, e);
            }
        } catch (Exception e) {
            throw new ApplicationFailureException("Exception while searching ECR for repoName=" + resourceName, e);
        }
    }

    /** Whether an image tagged {@code imageTag} exists in the ECR repository {@code repoName}. */
    public static boolean imageExists(String repoName, String imageTag) {
        String resourceName = AwsResourceNames.prefixed(repoName);
        ImageIdentifier imageId = imageTagToId(imageTag);
        DescribeImagesResponse resp;
        try {
            resp = client().describeImages(req -> req.repositoryName(resourceName).imageIds(imageId));
        } catch (ImageNotFoundException e) {
            return false;
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception in checking image details in ECR, repoName=" + resourceName + ", imageTag=" + imageTag);
        }
        return resp.hasImageDetails() && !resp.imageDetails().isEmpty();
    }

    /**
     * Deletes the images tagged {@code imageTags} from the ECR repository {@code repoName}. Tags that fail
     * to delete for any reason other than already being absent are added to {@code errorsCollector}; if the
     * whole batch call fails, every tag is added.
     */
    public static void deleteImages(String repoName, List<String> imageTags, Collection<String> errorsCollector) {
        String resourceName = AwsResourceNames.prefixed(repoName);
        try {
            var imageIds = CollectionOps.map(imageTags, EcrSupport::imageTagToId);
            var response = client().batchDeleteImage(req -> req.repositoryName(resourceName).imageIds(imageIds));
            if (response.hasFailures() && !response.failures().isEmpty()) {
                response.failures().forEach(failure -> {
                    if (failure.failureCode() != ImageFailureCode.IMAGE_NOT_FOUND) {
                        errorsCollector.add(failure.imageId().imageTag());
                    }
                });
            }
        } catch (Exception e) {
            AwsExceptions.logAwsExceptionAsError(logger, e, "Exception in deleting images, repoName=" + resourceName);
            errorsCollector.addAll(imageTags);
        }
    }

    private static ImageIdentifier imageTagToId(String imageTag) {
        return ImageIdentifier.builder().imageTag(imageTag).build();
    }
}
