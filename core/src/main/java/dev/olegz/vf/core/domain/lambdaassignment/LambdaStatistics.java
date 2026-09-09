package dev.olegz.vf.core.domain.lambdaassignment;

import java.util.List;

import dev.olegz.vf.core.domain.lambdarun.LambdaRunInfo;

public class LambdaStatistics {
    public int lambdaVersionId;
    public String version;
    public int totalRuns;
    public int successfulRuns;
    public long totalExecutionTime;
    public List<LambdaRunInfo> runsInfo;
}
