package dev.olegz.vf.api.lambda.assignment;

import java.time.ZoneId;

import dev.olegz.vf.api.lambda.ApiLambda;
import dev.olegz.vf.api.lambda.ApiLambdaVersion;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.domain.lambdaassignment.LambdaAssignment;

public class ApiLambdaAssignment {
    public final int lambdaAssignmentId;
    public final int lambdaId;
    public final boolean active;
    public final boolean testing;
    public final int locationId;
    public String assignmentDate;
    public long assignmentDateMs;
    public String endDate;
    public Long endDateMs;
    public ApiLambdaVersion version;
    public ApiLambda lambda;

    public ApiLambdaAssignment(LambdaAssignment core, ZoneId zoneId) {
        lambdaAssignmentId = core.lambdaAssignmentId;
        lambdaId = core.lambdaId;
        if (core.assignmentDate != null) {
            assignmentDateMs = core.assignmentDate.getTime();
            assignmentDate = DateFormatUtils.printDateTime(assignmentDateMs, zoneId);
        }
        if (core.endDate != null) {
            endDateMs = core.endDate.getTime();
            endDate = DateFormatUtils.printDateTime(endDateMs, zoneId);
        }
        testing = core.testing;
        active = core.checkActive();
        locationId = core.locationId;

        if (core.lambdaVersion != null) {
            version = new ApiLambdaVersion(core.lambdaVersion);
        }
        if (core.lambda != null) {
            lambda = new ApiLambda(core.lambda);
        }
    }
}
