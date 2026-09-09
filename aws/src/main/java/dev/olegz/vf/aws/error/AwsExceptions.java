package dev.olegz.vf.aws.error;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.ExternalException;
import org.slf4j.Logger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;

/**
 * Translation and logging of AWS SDK exceptions into the application's exception types.
 * Pure and stateless — safe to call from anywhere.
 */
public final class AwsExceptions {
    private AwsExceptions() {
    }

    private static String printSdkException(SdkException e, String message) {
        StringBuilder sb = new StringBuilder(message).append('\n')
            .append(e.getClass().getSimpleName());

        if (e instanceof SdkServiceException sse) {
            sb.append(" [statusCode=").append(sse.statusCode());
            Integer attempts = sse.numAttempts();
            if (attempts != null) sb.append(", attempt=").append(attempts);
            sb.append(']');
        }

        String em = e.getMessage();
        if (em != null) sb.append('\n').append(em);

        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            sb.append("\n\tCaused by ").append(t.getClass().getSimpleName());
            String tm = t.getMessage();
            if (tm != null) sb.append(": ").append(tm);
        }

        return sb.toString();
    }

    public static RuntimeException wrapAwsException(Throwable e, String message) {
        return
            e instanceof SdkServiceException sse ? new ExternalException(printSdkException(sse, message)) :
            e instanceof SdkException sde ? new ApplicationFailureException(printSdkException(sde, message)) :
            new ApplicationFailureException(message, e);
    }

    public static void logAwsExceptionAsError(Logger logger, Throwable e, String message) {
        if (e instanceof SdkException se) logger.error(printSdkException(se, message));
        else logger.error(message, e);
    }

    public static void logAwsExceptionAsWarning(Logger logger, Throwable e, String message) {
        if (e instanceof SdkException se) logger.warn(printSdkException(se, message));
        else logger.warn(message, e);
    }
}
