package dev.olegz.vf.messaging;

/** Signals that the current broker record must remain uncommitted and be delivered again. */
public class RetryableMessageException extends RuntimeException {
    public RetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
