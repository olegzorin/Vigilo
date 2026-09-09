package dev.olegz.vf.common;

/**
 * Common Runtime Exception class
 * @author Dmitry Shirkalin
 */
public class ApplicationFailureException extends RuntimeException {
	public final boolean logStackTrace;

	/**
     * A constructor from a string error message
     * @param message error message
     */
    public ApplicationFailureException(String message) {
        super(message);
        logStackTrace = false;
    }
    
    /**
     * A constructor from a cause
     * @param e exception cause
     */
    public ApplicationFailureException(Throwable e) {
        super(e);
        logStackTrace = true;
    }

    /**
     * A constructor from a string error message and a cause
     * @param message error message
     * @param e exception cause
     */
    public ApplicationFailureException(String message, Throwable e) {
        super(message, e);
        logStackTrace = true;
    }

    @Override
    public String toString() {
        Throwable cause = getCause();
        if (cause == null) return super.toString();

        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());

        do {
            sb.append("\nCaused by: ").append(cause);
            cause = cause.getCause();
        } while (cause != null);

        return sb.toString();
    }
}
