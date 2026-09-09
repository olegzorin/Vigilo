package dev.olegz.vf.aws.local;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/**
 * Local in-memory implementation of {@link SqsClient}, covering the queue provisioning and
 * minimal message-transport calls used by {@code SqsSupport}.
 */
public final class LocalSqsClient implements SqsClient {

    private static final ConcurrentHashMap<String, String> queueUrls = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<LocalMessage>> queues =
        new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<QueueAttributeName, String>> queueAttributes =
        new ConcurrentHashMap<>();
    private static final AtomicLong messageIds = new AtomicLong();

    @Override
    public GetQueueUrlResponse getQueueUrl(GetQueueUrlRequest request) {
        String url = queueUrls.get(request.queueName());
        if (url == null) {
            throw QueueDoesNotExistException.builder().message("Queue does not exist: " + request.queueName()).build();
        }
        return GetQueueUrlResponse.builder().queueUrl(url).build();
    }

    @Override
    public CreateQueueResponse createQueue(CreateQueueRequest request) {
        String url = queueUrls.computeIfAbsent(request.queueName(), LocalSqsClient::queueUrl);
        queues.computeIfAbsent(request.queueName(), _ -> new ConcurrentLinkedQueue<>());
        queueAttributes.computeIfAbsent(request.queueName(), _ -> new ConcurrentHashMap<>())
            .putAll(request.attributes());
        return CreateQueueResponse.builder().queueUrl(url).build();
    }

    @Override
    public GetQueueAttributesResponse getQueueAttributes(GetQueueAttributesRequest request) {
        String name = queueName(request.queueUrl());
        queue(name);
        String arn = "arn:aws:sqs:local:" + LocalAws.ACCOUNT_ID + ':' + name;
        Map<QueueAttributeName, String> allAttributes = new HashMap<>(
            queueAttributes.computeIfAbsent(name, _ -> new ConcurrentHashMap<>()));
        allAttributes.put(QueueAttributeName.QUEUE_ARN, arn);

        if (request.attributeNames().contains(QueueAttributeName.ALL)) {
            return GetQueueAttributesResponse.builder().attributes(allAttributes).build();
        }

        Map<QueueAttributeName, String> requestedAttributes = new HashMap<>();
        for (QueueAttributeName attribute : request.attributeNames()) {
            String value = allAttributes.get(attribute);
            if (value != null) requestedAttributes.put(attribute, value);
        }
        return GetQueueAttributesResponse.builder().attributes(requestedAttributes).build();
    }

    @Override
    public SetQueueAttributesResponse setQueueAttributes(SetQueueAttributesRequest request) {
        String name = queueName(request.queueUrl());
        queue(name);
        queueAttributes.computeIfAbsent(name, _ -> new ConcurrentHashMap<>()).putAll(request.attributes());
        return SetQueueAttributesResponse.builder().build();
    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        String queueName = queueName(request.queueUrl());
        ConcurrentLinkedQueue<LocalMessage> queue = queue(queueName);
        String messageId = Long.toString(messageIds.incrementAndGet());
        queue.add(new LocalMessage(messageId, request.messageBody(), System.currentTimeMillis()));
        return SendMessageResponse.builder().messageId(messageId).build();
    }

    @Override
    public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest request) {
        String queueName = queueName(request.queueUrl());
        ConcurrentLinkedQueue<LocalMessage> queue = queue(queueName);
        int max = request.maxNumberOfMessages() == null ? 1 : request.maxNumberOfMessages();
        List<Message> messages = new ArrayList<>(max);
        LocalMessage m;
        while (messages.size() < max && (m = queue.poll()) != null) {
            messages.add(Message.builder()
                .messageId(m.messageId())
                .receiptHandle(m.messageId())
                .body(m.body())
                .attributes(Map.of(MessageSystemAttributeName.SENT_TIMESTAMP, Long.toString(m.sentTimestamp())))
                .build());
        }
        return ReceiveMessageResponse.builder().messages(messages).build();
    }

    @Override
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest request) {
        return DeleteMessageResponse.builder().build();
    }

    private static String queueUrl(String queueName) {
        return "https://sqs.local/" + LocalAws.ACCOUNT_ID + '/' + queueName;
    }

    private static String queueName(String queueUrl) {
        int slash = queueUrl.lastIndexOf('/');
        return slash < 0 ? queueUrl : queueUrl.substring(slash + 1);
    }

    private ConcurrentLinkedQueue<LocalMessage> queue(String queueName) {
        if (!queueUrls.containsKey(queueName)) {
            throw QueueDoesNotExistException.builder().message("Queue does not exist: " + queueName).build();
        }
        return queues.computeIfAbsent(queueName, _ -> new ConcurrentLinkedQueue<>());
    }

    private record LocalMessage(String messageId, String body, long sentTimestamp) {
    }

    @Override
    public String serviceName() {
        return SqsClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
