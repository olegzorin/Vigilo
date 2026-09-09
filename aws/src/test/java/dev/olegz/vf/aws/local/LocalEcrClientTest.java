package dev.olegz.vf.aws.local;

import java.util.HashSet;
import java.util.Set;

import dev.olegz.vf.aws.LocalAwsTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecr.model.ImageFailureCode;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ImageNotFoundException;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalEcrClient}, stubbing the docker image operations so no daemon is needed.
 */
class LocalEcrClientTest extends LocalAwsTestSupport {

    /** In-memory stand-in for the docker image store. */
    private static final class StubImages implements LocalEcrClient.Images {
        final Set<String> present = new HashSet<>();

        @Override
        public boolean exists(String imageRef) {
            return present.contains(imageRef);
        }

        @Override
        public LocalEcrClient.RemoveResult remove(String imageRef) {
            return present.remove(imageRef)
                ? LocalEcrClient.RemoveResult.REMOVED
                : LocalEcrClient.RemoveResult.NOT_FOUND;
        }
    }

    @Test
    void describeMissingRepositoryThrows() {
        LocalEcrClient ecr = new LocalEcrClient(new StubImages());
        assertThrows(RepositoryNotFoundException.class,
            () -> ecr.describeRepositories(r -> r.repositoryNames("missing-repo")));
    }

    @Test
    void createThenDescribeReturnsSyntheticUri() {
        LocalEcrClient ecr = new LocalEcrClient(new StubImages());
        String created = ecr.createRepository(r -> r.repositoryName("bundle-1"))
            .repository().repositoryUri();
        assertEquals("vf-local/bundle-1", created);

        String described = ecr.describeRepositories(r -> r.repositoryNames("bundle-1"))
            .repositories().getFirst().repositoryUri();
        assertEquals("vf-local/bundle-1", described);
    }

    @Test
    void describeImagesPresentAndMissing() {
        StubImages images = new StubImages();
        images.present.add("vf-local/bundle-1:tag-1");
        LocalEcrClient ecr = new LocalEcrClient(images);
        ecr.createRepository(r -> r.repositoryName("bundle-1"));

        ImageIdentifier present = ImageIdentifier.builder().imageTag("tag-1").build();
        var resp = ecr.describeImages(r -> r.repositoryName("bundle-1").imageIds(present));
        assertTrue(resp.hasImageDetails() && !resp.imageDetails().isEmpty());
        assertEquals("tag-1", resp.imageDetails().getFirst().imageTags().getFirst());

        ImageIdentifier missing = ImageIdentifier.builder().imageTag("tag-2").build();
        assertThrows(ImageNotFoundException.class,
            () -> ecr.describeImages(r -> r.repositoryName("bundle-1").imageIds(missing)));
    }

    @Test
    void batchDeleteReportsMissingAsImageNotFound() {
        StubImages images = new StubImages();
        images.present.add("vf-local/bundle-1:tag-1");
        LocalEcrClient ecr = new LocalEcrClient(images);

        ImageIdentifier deleted = ImageIdentifier.builder().imageTag("tag-1").build();
        ImageIdentifier missing = ImageIdentifier.builder().imageTag("tag-9").build();
        var resp = ecr.batchDeleteImage(r -> r.repositoryName("bundle-1").imageIds(deleted, missing));

        // The present image removed cleanly (no failure); the missing one is an IMAGE_NOT_FOUND failure.
        assertEquals(1, resp.failures().size());
        assertEquals(ImageFailureCode.IMAGE_NOT_FOUND, resp.failures().getFirst().failureCode());
        assertEquals("tag-9", resp.failures().getFirst().imageId().imageTag());
        assertFalse(images.present.contains("vf-local/bundle-1:tag-1"), "deleted image should be gone");
    }
}
