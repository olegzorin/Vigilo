package dev.olegz.vf.aws.ecr;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.error.AwsExceptions;
import dev.olegz.vf.aws.local.LocalAws;
import dev.olegz.vf.common.exception.ExternalConnectionException;
import dev.olegz.vf.common.exception.ExternalException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecrpublic.EcrPublicClient;
import software.amazon.awssdk.services.ecrpublic.model.AuthorizationData;
import software.amazon.awssdk.services.lambda.model.Runtime;

/** Read-only searches of the AWS-managed Lambda image repositories in ECR Public. */
public final class EcrPublicSupport {
    private EcrPublicSupport() {
    }

    private static final Logger logger = LoggerFactory.getLogger(EcrPublicSupport.class);

    private static final String PYTHON_REPOSITORY = "lambda/python";
    private static final String PYTHON_REPOSITORY_URI = "public.ecr.aws/" + PYTHON_REPOSITORY;
    private static final URI FIRST_TAGS_PAGE = URI.create(
        "https://public.ecr.aws/v2/" + PYTHON_REPOSITORY + "/tags/list?n=1000"
    );
    private static final Pattern NEXT_PAGE_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"?next\"?");
    private static final Pattern PYTHON_IMAGE_TAG = Pattern.compile(
        "^(\\d+)\\.(\\d+)-(arm64|x86_64)$"
    );
    private static final String PYTHON_RUNTIME_PREFIX = "python";

    private static final long HTTP_CONNECTION_TIMEOUT =
        PropertyStore.getLong("vf.aws.httpClient.connectionTimeout", 5_000L);
    private static final long HTTP_REQUEST_TIMEOUT =
        PropertyStore.getLong("vf.aws.httpClient.socketTimeout", 10_000L);

    private static final class ClientHolder {
        private static final AwsClients.LazyClient<EcrPublicClient> CLIENT =
            AwsClients.lazyClient("ECR Public", AwsClients::ecrPublicClient);
    }

    private static EcrPublicClient client() {
        return ClientHolder.CLIENT.get();
    }

    private static volatile HttpClient registryHttpClient;
    private static final Object registryHttpClientLock = new Object();
    private static volatile List<String> pythonImageTags;
    private static final Object pythonImageTagsRefreshLock = new Object();

    private static HttpClient registryHttpClient() {
        HttpClient httpClient = registryHttpClient;
        if (httpClient != null) return httpClient;
        synchronized (registryHttpClientLock) {
            httpClient = registryHttpClient;
            if (httpClient == null) {
                registryHttpClient = httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(HTTP_CONNECTION_TIMEOUT))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            }
            return httpClient;
        }
    }

    /** Idempotent: closes the shared SDK and registry HTTP clients; safe to call more than once. */
    public static void shutdown() {
        synchronized (pythonImageTagsRefreshLock) {
            ClientHolder.CLIENT.shutdown();
            synchronized (registryHttpClientLock) {
                if (registryHttpClient != null) {
                    try {
                        registryHttpClient.close();
                    } catch (Exception e) {
                        logger.warn("Error closing ECR Public registry HTTP client", e);
                    }
                    registryHttpClient = null;
                }
            }
            pythonImageTags = null;
        }
    }

    /**
     * Returns the AWS Lambda Python base-image tags in the form
     * {@code <major>.<minor>-<architecture>}. Tags are deduplicated and returned in ascending
     * numeric version and architecture order.
     */
    public static List<String> getPythonImageTags() {
        List<String> tags = pythonImageTags;
        if (tags == null) {
            synchronized (pythonImageTagsRefreshLock) {
                tags = pythonImageTags;
                if (tags == null) {
                    refreshPythonImageTagsLocked();
                    tags = pythonImageTags;
                }
            }
        }
        return tags;
    }

    /**
     * Returns the canonical AWS Lambda Python base-image URI for the specified image tag, for
     * example {@code public.ecr.aws/lambda/python:3.11-arm64}.
     */
    public static String getPythonImageUri(String imageTag) {
        validateImageTag(imageTag);
        return PYTHON_REPOSITORY_URI + ':' + imageTag;
    }

    /**
     * Rebuilds the complete Python image catalog and atomically publishes it when successful.
     * Readers continue using the previous immutable snapshot while this method is running or if it
     * fails.
     */
    public static void refreshPythonImageTags() {
        synchronized (pythonImageTagsRefreshLock) {
            refreshPythonImageTagsLocked();
        }
    }

    private static void refreshPythonImageTagsLocked() {
        prefillPythonImageTagsLocked();
        if (LocalAws.ENABLED) {
            return;
        }

        logger.info("Refreshing ECR Public Python image tags");
        String authorizationToken = getAuthorizationToken();
        List<String> refreshedTags = loadPythonImageTags(
            pageUri -> fetchTagPage(pageUri, authorizationToken)
        );
        pythonImageTags = refreshedTags;
        logger.info("Refreshed ECR Public Python image tags: count={}", refreshedTags.size());
    }

    static void refreshPythonImageTags(TagPageFetcher pageFetcher) {
        synchronized (pythonImageTagsRefreshLock) {
            prefillPythonImageTagsLocked();
            pythonImageTags = loadPythonImageTags(pageFetcher);
        }
    }

    static List<String> loadPythonImageTags(TagPageFetcher pageFetcher) {
        Set<String> matchingTags = new HashSet<>(getPythonRuntimeImageTags());

        URI pageUri = FIRST_TAGS_PAGE;
        while (pageUri != null) {
            TagPage page = pageFetcher.fetch(pageUri);
            addMatchingTags(page.tags(), matchingTags);
            pageUri = page.nextPage();
        }

        return sortImageTags(matchingTags);
    }

    private static void prefillPythonImageTagsLocked() {
        if (pythonImageTags == null) {
            pythonImageTags = getPythonRuntimeImageTags();
        }
    }

    static List<String> getPythonRuntimeImageTags() {
        Set<String> tags = new HashSet<>();
        for (Runtime runtime : Runtime.knownValues()) {
            String runtimeName = runtime.toString();
            if (!runtimeName.startsWith(PYTHON_RUNTIME_PREFIX)) continue;

            String version = runtimeName.substring(PYTHON_RUNTIME_PREFIX.length());
            tags.add(version + "-arm64");
            tags.add(version + "-x86_64");
        }
        return sortImageTags(tags);
    }

    private static List<String> sortImageTags(Set<String> tags) {
        List<String> result = new ArrayList<>(tags);
        result.sort(EcrPublicSupport::compareImageTags);
        return List.copyOf(result);
    }

    static void clearPythonImageTags() {
        pythonImageTags = null;
    }

    private static String getAuthorizationToken() {
        try {
            AuthorizationData authorizationData = client().getAuthorizationToken(_ -> {}).authorizationData();
            String token = authorizationData == null ? null : authorizationData.authorizationToken();
            if (token == null || token.isBlank()) {
                throw new ExternalException("ECR Public returned an empty authorization token");
            }
            return token;
        } catch (ExternalException e) {
            throw e;
        } catch (Exception e) {
            throw AwsExceptions.wrapAwsException(e, "Exception while getting ECR Public authorization token");
        }
    }

    private static TagPage fetchTagPage(URI pageUri, String authorizationToken) {
        HttpRequest request = HttpRequest.newBuilder(pageUri)
            .timeout(Duration.ofMillis(HTTP_REQUEST_TIMEOUT))
            .header("Authorization", "Bearer " + authorizationToken)
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = registryHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectionException("Interrupted while listing ECR Public Python image tags");
        } catch (IOException e) {
            throw new ExternalConnectionException("Exception while listing ECR Public Python image tags: " + e.getMessage());
        }

        if (response.statusCode() != 200) {
            throw new ExternalException(
                "ECR Public registry returned HTTP " + response.statusCode() + " while listing Python image tags"
            );
        }

        TagsList tagsList = StringMapper.readValue(response.body(), TagsList.class);
        List<String> tags = tagsList == null || tagsList.tags() == null ? List.of() : tagsList.tags();
        URI nextPage = response.headers().firstValue("Link")
            .map(EcrPublicSupport::parseNextPage)
            .orElse(null);
        return new TagPage(tags, nextPage);
    }

    private static URI parseNextPage(String linkHeader) {
        Matcher matcher = NEXT_PAGE_LINK.matcher(linkHeader);
        if (!matcher.find()) return null;
        return FIRST_TAGS_PAGE.resolve(matcher.group(1));
    }

    private static void addMatchingTags(List<String> tags, Set<String> matchingTags) {
        for (String imageTag : tags) {
            if (imageTag == null) continue;
            if (PYTHON_IMAGE_TAG.matcher(imageTag).matches()) matchingTags.add(imageTag);
        }
    }

    private static int compareImageTags(String first, String second) {
        Matcher firstTag = PYTHON_IMAGE_TAG.matcher(first);
        Matcher secondTag = PYTHON_IMAGE_TAG.matcher(second);
        if (!firstTag.matches() || !secondTag.matches()) return first.compareTo(second);

        int majorComparison = Integer.compare(
            Integer.parseInt(firstTag.group(1)), Integer.parseInt(secondTag.group(1))
        );
        if (majorComparison != 0) return majorComparison;
        int minorComparison = Integer.compare(
            Integer.parseInt(firstTag.group(2)), Integer.parseInt(secondTag.group(2))
        );
        if (minorComparison != 0) return minorComparison;
        return firstTag.group(3).compareTo(secondTag.group(3));
    }

    private static void validateImageTag(String imageTag) {
        if (imageTag == null || !PYTHON_IMAGE_TAG.matcher(imageTag).matches()) {
            throw new WrongParameterValueException("Invalid Python image tag: " + imageTag + " - valid tag is like '3.12-arm64'");
        }
        if (pythonImageTags == null || !pythonImageTags.contains(imageTag)) {
            throw new WrongParameterValueException("Non-supported Python image tag");
        }
    }

    @FunctionalInterface
    interface TagPageFetcher {
        TagPage fetch(URI pageUri);
    }

    record TagPage(List<String> tags, URI nextPage) {
    }

    private record TagsList(String name, List<String> tags) {
    }
}
