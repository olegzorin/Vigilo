package dev.olegz.vf.messaging;

import dev.olegz.vf.common.ApplicationFailureException;

/**
 * Unchecked exception for failures originating in the messaging layer (configuration, producing, consuming).
 * <p>
 * Subclasses {@link ApplicationFailureException} while giving callers and broker implementations a single
 * messaging-specific type to throw and catch regardless of the underlying broker.
 */
public class MessagingException extends ApplicationFailureException {
    public MessagingException(String message) {
        super(message);
    }

    public MessagingException(Throwable cause) {
        super(cause);
    }

    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
