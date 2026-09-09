package dev.olegz.vf.aws.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.common.io.ProcessRunner;
import dev.olegz.vf.common.props.PropertyStore;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;

/**
 * Local implementation of {@link EcrClient} backed by the local Docker image store rather than a
 * real registry. Repositories are synthetic: {@code describeRepositories}/{@code createRepository}
 * hand back a {@code repositoryUri} of {@code <repoPrefix>/<repoName>} (prefix from
 * {@code vf.aws.local.ecr.repoPrefix}, default {@code vf-local}), so the build command can tag the
 * image straight into the local store with no push. A small in-memory set tracks which repos have
 * been "created" so the describe-then-create-on-miss flow in
 * {@code dev.olegz.vf.worker.LambdaAwsResources#makeEcrRepositoryUri} sees AWS
 * semantics (a miss throws {@link RepositoryNotFoundException}).
 * <p>
 * Image operations shell out to the docker CLI (via {@link LocalDocker}): {@code describeImages}
 * runs {@code docker image inspect} (a miss throws {@link ImageNotFoundException}) and
 * {@code batchDeleteImage} runs {@code docker image rm}, reporting a missing image as an
 * {@link ImageFailureCode#IMAGE_NOT_FOUND} failure like AWS.
 */
public final class LocalEcrClient implements EcrClient {

    private static final long INSPECT_TIMEOUT_MS = 30_000L;
    private static final long DELETE_TIMEOUT_MS = 60_000L;

    /** Outcome of removing a local image, mapped onto ECR's failure semantics. */
    enum RemoveResult { REMOVED, NOT_FOUND, FAILED }

    /** Docker image operations, overridable in tests where there is no daemon. */
    interface Images {
        boolean exists(String imageRef);

        RemoveResult remove(String imageRef);
    }

    private final Images images;
    private final String repoPrefix;
    private final Set<String> knownRepos = ConcurrentHashMap.newKeySet();

    public LocalEcrClient() {
        this(productionImages());
    }

    LocalEcrClient(Images images) {
        this.images = images;
        this.repoPrefix = PropertyStore.getString("vf.aws.local.ecr.repoPrefix", "vf-local");
    }

    private String repositoryUri(String repoName) {
        return repoPrefix + '/' + repoName;
    }

    @Override
    public DescribeRepositoriesResponse describeRepositories(DescribeRepositoriesRequest request) {
        List<Repository> repositories = new ArrayList<>();
        List<String> names = request.repositoryNames();
        if (names == null || names.isEmpty()) {
            // Whole-registry listing: report every known repository.
            for (String name : knownRepos) {
                repositories.add(toRepository(name));
            }
        } else {
            for (String name : names) {
                if (!knownRepos.contains(name)) {
                    throw RepositoryNotFoundException.builder()
                        .message("Repository does not exist: " + name).build();
                }
                repositories.add(toRepository(name));
            }
        }
        return DescribeRepositoriesResponse.builder().repositories(repositories).build();
    }

    @Override
    public CreateRepositoryResponse createRepository(CreateRepositoryRequest request) {
        String name = request.repositoryName();
        knownRepos.add(name);
        return CreateRepositoryResponse.builder().repository(toRepository(name)).build();
    }

    private Repository toRepository(String name) {
        return Repository.builder()
            .repositoryName(name)
            .registryId(LocalAws.ACCOUNT_ID)
            .repositoryArn("arn:aws:ecr:local:" + LocalAws.ACCOUNT_ID + ":repository/" + name)
            .repositoryUri(repositoryUri(name))
            .build();
    }

    @Override
    public DescribeImagesResponse describeImages(DescribeImagesRequest request) {
        String repoName = request.repositoryName();
        String uri = repositoryUri(repoName);
        List<ImageDetail> details = new ArrayList<>();
        for (ImageIdentifier imageId : request.imageIds()) {
            String imageRef = uri + ':' + imageId.imageTag();
            if (!images.exists(imageRef)) {
                throw ImageNotFoundException.builder()
                    .message("Image does not exist: " + imageRef).build();
            }
            details.add(ImageDetail.builder()
                .registryId(LocalAws.ACCOUNT_ID)
                .repositoryName(repoName)
                .imageTags(imageId.imageTag())
                .imagePushedAt(Instant.now())
                .build());
        }
        return DescribeImagesResponse.builder().imageDetails(details).build();
    }

    @Override
    public BatchDeleteImageResponse batchDeleteImage(BatchDeleteImageRequest request) {
        String uri = repositoryUri(request.repositoryName());
        List<ImageFailure> failures = new ArrayList<>();
        for (ImageIdentifier imageId : request.imageIds()) {
            RemoveResult result = images.remove(uri + ':' + imageId.imageTag());
            if (result == RemoveResult.NOT_FOUND) {
                failures.add(ImageFailure.builder()
                    .imageId(imageId).failureCode(ImageFailureCode.IMAGE_NOT_FOUND)
                    .failureReason("Requested image not found").build());
            } else if (result == RemoveResult.FAILED) {
                failures.add(ImageFailure.builder()
                    .imageId(imageId).failureCode(ImageFailureCode.INVALID_IMAGE_TAG)
                    .failureReason("Failed to remove local image").build());
            }
        }
        return BatchDeleteImageResponse.builder().failures(failures).build();
    }

    private static Images productionImages() {
        return new Images() {
            @Override
            public boolean exists(String imageRef) {
                return LocalDocker.run(List.of("image", "inspect", imageRef), INSPECT_TIMEOUT_MS).isOk();
            }

            @Override
            public RemoveResult remove(String imageRef) {
                ProcessRunner.CompletedProcess cp = LocalDocker.run(List.of("image", "rm", imageRef), DELETE_TIMEOUT_MS);
                if (cp.isOk()) return RemoveResult.REMOVED;
                String log = cp.getLog();
                return log.contains("No such image") ? RemoveResult.NOT_FOUND : RemoveResult.FAILED;
            }
        };
    }

    @Override
    public String serviceName() {
        return EcrClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
