package dev.olegz.vf.core.domain.lambdarun;

/**
 * Version fields required to invoke a lambda at runtime.
 */
public class LambdaRuntimeVersion {
    public int lambdaVersionId;
    public int memory;
    public int timeout; // in seconds
    public String functionName;
    public String asyncFunctionName;
    public boolean published;
    public int trigger;

    public String getFunctionName(InvocationLane lane) {
        return lane == InvocationLane.ASYNC ? asyncFunctionName : functionName;
    }

    @Override
    public String toString() {
        return "{lambdaVersionId=" + lambdaVersionId +
            (published ? "" : ", notPublic") +
            ", memory=" + memory +
            ", timeout=" + timeout +
            ", functionName=" + functionName +
            ", asyncFunctionName=" + asyncFunctionName +
            '}';
    }
}
