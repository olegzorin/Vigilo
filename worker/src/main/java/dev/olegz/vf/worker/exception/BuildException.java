package dev.olegz.vf.worker.exception;

/**
 * This exception indicates a problem in the lambda code
 * and developer team should be notified of it.
 */
public class BuildException extends RuntimeException {

    public BuildException(String message) {
        super(message);
    }
}
