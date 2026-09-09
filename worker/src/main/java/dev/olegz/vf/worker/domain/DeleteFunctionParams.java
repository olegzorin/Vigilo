package dev.olegz.vf.worker.domain;


public class DeleteFunctionParams {
    // "In" params
    public final String functionName;
    public final String versionQualifier;

    // "Out" params
    public boolean versionDeleted;    // only specific version deleted
    public boolean functionDeleted;   // entire function deleted

    public DeleteFunctionParams(String functionRef) {
        String[] split = functionRef.split(":", 2);
        functionName = split[0];
        versionQualifier = split.length == 2 ? split[1] : null;
    }

    @Override
    public String toString() {
        return "functionName=" + functionName + (versionQualifier != null ? ", versionQualifier=" + versionQualifier : "");
    }
}
