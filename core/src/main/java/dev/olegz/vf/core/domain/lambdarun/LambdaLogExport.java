package dev.olegz.vf.core.domain.lambdarun;

import java.net.URL;
import java.util.List;

public class LambdaLogExport {
    public int lambdaAssignmentId;
    public String lambdaName;
    public String startDate;
    public String endDate;
    public String taskId;
    public String statusCode;
    public List<URL> files;

    @Override
    public String toString() {
        return "lambdaAssignmentId=" + lambdaAssignmentId + ", lambdaName=" + lambdaName + ", startDate=" + startDate +
            ", endDate=" + endDate + ", taskId=" + taskId + ", statusCode=" + statusCode +
            (files == null ? "" : ", files=" + files);
    }
}
