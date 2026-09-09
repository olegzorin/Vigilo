package dev.olegz.vf.aws.ecr;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.exception.WrongParameterValueException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EcrPublicSupportTest {

    @AfterEach
    void clearCatalog() {
        EcrPublicSupport.clearPythonImageTags();
    }

    @Test
    void refreshesImageTagsFromOnePaginatedScan() {
        var pageFetcher = new StubTagPageFetcher(
            List.of(
                "3.11-arm64",
                "3.9-arm64",
                "3.11.2024.09.05.16-arm64",
                "3.12-x86_64"
            ),
            List.of(
                "3.10-arm64",
                "3.11-arm64",
                "3.10-x86_64",
                "3.13.1-arm64",
                "latest-arm64"
            )
        );

        EcrPublicSupport.refreshPythonImageTags(pageFetcher);

        List<String> imageTags = EcrPublicSupport.getPythonImageTags();
        assertTrue(imageTags.containsAll(EcrPublicSupport.getPythonRuntimeImageTags()));
        assertTrue(imageTags.contains("3.9-arm64"));
        assertTrue(imageTags.contains("3.10-x86_64"));
        assertTrue(imageTags.contains("3.12-x86_64"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> EcrPublicSupport.getPythonImageTags().add("3.15-arm64")
        );
        assertEquals(2, pageFetcher.requests.size());
        assertEquals(
            "https://public.ecr.aws/v2/lambda/python/tags/list?n=1000",
            pageFetcher.requests.getFirst().toString()
        );
        assertEquals("https://public.ecr.aws/page-2", pageFetcher.requests.get(1).toString());
    }

    @Test
    void keepsPreviousCatalogWhenRefreshFails() {
        EcrPublicSupport.refreshPythonImageTags(
            new StubTagPageFetcher(List.of("3.11-arm64", "3.12-x86_64"))
        );

        assertThrows(
            IllegalStateException.class,
            () -> EcrPublicSupport.refreshPythonImageTags(pageUri -> {
                throw new IllegalStateException("refresh failed");
            })
        );
        assertEquals(
            EcrPublicSupport.getPythonRuntimeImageTags(),
            EcrPublicSupport.getPythonImageTags()
        );
    }

    @Test
    void prefillsCatalogFromLambdaRuntimesBeforeInitialRefresh() {
        assertThrows(
            IllegalStateException.class,
            () -> EcrPublicSupport.refreshPythonImageTags(pageUri -> {
                throw new IllegalStateException("refresh failed");
            })
        );

        List<String> imageTags = EcrPublicSupport.getPythonImageTags();
        assertTrue(imageTags.contains("3.10-arm64"));
        assertTrue(imageTags.contains("3.10-x86_64"));
        assertTrue(imageTags.contains("3.14-arm64"));
        assertTrue(imageTags.contains("3.14-x86_64"));
    }

    @Test
    void getsPythonImageUri() {
        EcrPublicSupport.refreshPythonImageTags(
            new StubTagPageFetcher(List.of("3.11-arm64", "3.12-x86_64"))
        );

        assertEquals(
            "public.ecr.aws/lambda/python:3.11-arm64",
            EcrPublicSupport.getPythonImageUri("3.11-arm64")
        );
        assertEquals(
            "public.ecr.aws/lambda/python:3.12-x86_64",
            EcrPublicSupport.getPythonImageUri("3.12-x86_64")
        );
    }

    @Test
    void rejectsInvalidImageUriArguments() {
        assertThrows(
            WrongParameterValueException.class,
            () -> EcrPublicSupport.getPythonImageUri("python3.11-arm64")
        );
        assertThrows(
            WrongParameterValueException.class,
            () -> EcrPublicSupport.getPythonImageUri("3.11-mips64")
        );
        assertThrows(
            WrongParameterValueException.class,
            () -> EcrPublicSupport.getPythonImageUri("99.99-arm64")
        );
    }

    private static final class StubTagPageFetcher implements EcrPublicSupport.TagPageFetcher {
        private final List<List<String>> pages;
        private final List<URI> requests = new ArrayList<>();

        @SafeVarargs
        private StubTagPageFetcher(List<String>... pages) {
            this.pages = List.of(pages);
        }

        @Override
        public EcrPublicSupport.TagPage fetch(URI pageUri) {
            requests.add(pageUri);
            int pageIndex = requests.size() - 1;
            URI nextPage = pageIndex + 1 < pages.size()
                ? URI.create("https://public.ecr.aws/page-" + (pageIndex + 2))
                : null;
            return new EcrPublicSupport.TagPage(pages.get(pageIndex), nextPage);
        }
    }
}
