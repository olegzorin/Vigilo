package dev.olegz.vf.core.domain.lambdarun;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Source object for AWS CloudWatchLogs InputLogEvent
 */
public class LambdaLogEvent {
    /*
     * Log event size (message size in UTF-8 plus 26 bytes) can be no larger than 256 KB (262,144 bytes) for CloudWatchLogs.
     * We use a smaller limit to ensure that the above condition is met.
     */
    private static final int MAX_MESSAGE_SIZE = 64_000;

    public int seqno;
    public byte[] text;
    public long timestamp;

    @JsonCreator
    public LambdaLogEvent(
        @JsonProperty("message") String message,
        @JsonProperty("timestamp") long timestamp)
    {
        this.timestamp = timestamp;
        if (message != null && !message.isBlank()) {
            this.text = StringUtils.truncate(message, MAX_MESSAGE_SIZE).getBytes(UTF_8);
        }
    }

    public String message() {
        return text == null ? null : new String(text, UTF_8);
    }

    @Override
    public String toString() {
        return "{timestamp=" + timestamp + ", message=" + message() + '}';
    }
}
