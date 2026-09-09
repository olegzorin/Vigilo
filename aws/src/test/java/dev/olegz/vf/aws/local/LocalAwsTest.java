package dev.olegz.vf.aws.local;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import dev.olegz.vf.aws.LocalAwsTestSupport;
import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport;
import dev.olegz.vf.aws.ecr.EcrPublicSupport;
import dev.olegz.vf.aws.iam.IamSupport;
import dev.olegz.vf.aws.s3.S3Presigning;
import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.aws.sqs.SqsSupport;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.props.PropertyStore;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the local (non-AWS) client implementations and the {@link AwsClients} switch.
 * <p>
 * The static block enables local mode <em>before</em> any local class is loaded, and crucially
 * <b>no AWS credentials or region are configured</b> - so a green run also proves the factory's
 * local branch never touches {@code AwsCredentials} (whose static initializer
 * would otherwise fail).
 */
class LocalAwsTest extends LocalAwsTestSupport {

    @Test
    void factoryReturnsLocalClients() {
        assertTrue(LocalAws.ENABLED, "local mode must be enabled");
        assertInstanceOf(LocalS3Client.class, AwsClients.s3Client());
        assertInstanceOf(LocalCloudWatchLogsClient.class, AwsClients.cloudWatchLogsClient());
        assertInstanceOf(LocalSqsClient.class, AwsClients.sqsClient());
        assertInstanceOf(LocalEc2Client.class, AwsClients.ec2Client());
        assertInstanceOf(LocalIamClient.class, AwsClients.iamClient());
        assertInstanceOf(LocalEcrClient.class, AwsClients.ecrClient());
        assertInstanceOf(LocalLambdaClient.class, AwsClients.lambdaClient());
    }

    @Test
    void localStorageUsesConfiguredRoot() {
        Path expected = Path.of(
            PropertyStore.getString("vf.aws.local.root"),
            "s3",
            AwsClients.REGION.id(),
            "bucket",
            "key"
        );

        assertEquals(expected.toFile(), LocalAws.s3File("bucket", "key"));
    }

    @Test
    void ecrPublicCatalogUsesLambdaRuntimeDefaults() {
        EcrPublicSupport.shutdown();
        EcrPublicSupport.refreshPythonImageTags();

        HashSet<String> expectedTags = new HashSet<>();
        for (Runtime runtime : Runtime.knownValues()) {
            String runtimeName = runtime.toString();
            if (!runtimeName.startsWith("python")) continue;

            String version = runtimeName.substring("python".length());
            expectedTags.add(version + "-arm64");
            expectedTags.add(version + "-x86_64");
        }
        assertEquals(expectedTags, new HashSet<>(EcrPublicSupport.getPythonImageTags()));
    }

    @Test
    void s3RoundTripThroughSupport() {
        String bucket = "test-bucket";
        String key = "dir/object-1";
        byte[] data = "hello local s3".getBytes(StandardCharsets.UTF_8);

        S3Support.createObject(bucket, key, data, "text/plain", false);
        assertArrayEquals(data, S3Support.getData(bucket, key));

        // A missing key is reported as not-found (NoSuchKey), surfaced as null.
        assertNull(S3Support.getData(bucket, "no/such/key"));

        HashSet<String> errors = new HashSet<>();
        S3Support.deleteObjects(bucket, List.of(key), errors);
        assertTrue(errors.isEmpty(), "no delete errors expected");
        assertNull(S3Support.getData(bucket, key), "object should be gone after delete");
    }

    @Test
    void cloudWatchLogsThroughSupport() {
        String group = "/vf/test/group";
        String stream = "stream-1";

        CloudWatchLogsSupport.createLogGroup(group, 14);
        assertEquals(14, CloudWatchLogsSupport.getLogGroupRetentionDays(group));
        assertNotNull(CloudWatchLogsSupport.findLogGroup(group));

        CloudWatchLogsSupport.createLogStream(group, stream, "initial message");
        Long lastTs = CloudWatchLogsSupport.getLastEventTimestamp(group, stream);
        assertNotNull(lastTs, "stream should have a last-event timestamp after the init message");

        List<String> warnings = new ArrayList<>();
        List<InputLogEvent> events = List.of(
            InputLogEvent.builder().timestamp(System.currentTimeMillis()).message("event a").build());
        CloudWatchLogsSupport.writeLogEvents(group, stream, events, warnings);

        CloudWatchLogsSupport.deleteLogGroup(group);
        assertNull(CloudWatchLogsSupport.findLogGroup(group), "group should be gone after delete");
    }

    @Test
    void sqsProvisioningReturnsArn() {
        String arn = SqsSupport.makeSqsQueue("test-queue", 0, 3600, 10);
        assertEquals("arn:aws:sqs:local:" + LocalAws.ACCOUNT_ID + ":dev-botlab-test-queue", arn);
    }

    @Test
    void sqsProvisioningReconcilesAttributesAndRedrivePolicy() {
        String suffix = UUID.randomUUID().toString();
        String deadLetterQueue = "result-dlq-" + suffix;
        String resultQueue = "result-" + suffix;
        String deadLetterQueueArn = SqsSupport.makeSqsQueue(deadLetterQueue, 0, 1_209_600, 60);

        SqsSupport.makeSqsQueue(resultQueue, 0, 3_600, 10, deadLetterQueueArn, 3);
        SqsSupport.makeSqsQueue(resultQueue, 0, 345_600, 60, deadLetterQueueArn, 10);

        var client = AwsClients.sqsClient();
        String resourceName = "dev-botlab-" + resultQueue;
        String queueUrl = client.getQueueUrl(r -> r.queueName(resourceName)).queueUrl();
        Map<QueueAttributeName, String> attributes = client.getQueueAttributes(r -> r
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.ALL)).attributes();

        assertEquals("345600", attributes.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD));
        assertEquals("60", attributes.get(QueueAttributeName.VISIBILITY_TIMEOUT));
        Map<String, String> redrivePolicy = StringMapper.readStringMap(
            attributes.get(QueueAttributeName.REDRIVE_POLICY));
        assertEquals(deadLetterQueueArn, redrivePolicy.get("deadLetterTargetArn"));
        assertEquals("10", redrivePolicy.get("maxReceiveCount"));
    }

    @Test
    void iamSupportYieldsSyntheticArns() {
        assertEquals(LocalAws.ACCOUNT_ID, IamSupport.getAccountId());
        assertEquals("arn:aws:iam::" + LocalAws.ACCOUNT_ID + ":role/my-role", IamSupport.getRoleArn("my-role"));
    }

    @Test
    void presignedUrlIsLocalHttpUrl() {
        // Phase 2: the local presign is an http:// URL served by LocalS3HttpServer (reachable from a
        // build container via host.docker.internal), replacing the Phase 1 file:// stopgap.
        URL url = S3Presigning.makeS3ReadPresignedUrl("test-bucket", "dir/obj", 60_000L, null);
        assertEquals("http", url.getProtocol());
        assertEquals("host.docker.internal", url.getHost());
        assertTrue(url.getPath().endsWith("/test-bucket/dir/obj"), "unexpected URL: " + url);
    }
}
