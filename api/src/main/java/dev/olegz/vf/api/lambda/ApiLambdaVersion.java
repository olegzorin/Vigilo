package dev.olegz.vf.api.lambda;

import java.util.List;
import java.util.Map;

import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.core.domain.lambdabuild.InvocationLaneSettings;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;

public class ApiLambdaVersion {
    public final int lambdaVersionId;
    public final String version;
    public final int trigger;
    public final Map<String, String> schedules;

    public final LambdaVersionStatus status;
    public String statusDate;
    public long statusDateMs;
    public String createdAt;
    public long createdAtMs;

    public List<ApiDataStream> dataStreams;

    public Function function;
    public Function asyncFunction;

    public static class Function {
        public final int memory;
        public final int timeout;
        public final String name;
        public String startDate;
        public Long startDateMs;

        private Function(LambdaVersion v, InvocationLane lane) {
            if (lane == InvocationLane.ASYNC) {
                InvocationLaneSettings settings = InvocationLaneSettings.forLane(lane);
                this.memory = settings.memorySize();
                this.timeout = settings.timeout();
            } else {
                memory = v.memory;
                timeout = v.timeout;
            }
            this.name = v.getFunctionName(lane);
            if (v.functionStartDate != null) {
                startDateMs = v.functionStartDate.getTime();
                startDate = DateFormatUtils.printDateTime(startDateMs);
            }
        }
    }

    public static class ApiDataStream {
        public final String address;

        private ApiDataStream(String address) {
            this.address = address;
        }
    }

    public ApiLambdaVersion(LambdaVersion v) {
        lambdaVersionId = v.lambdaVersionId;
        version = v.version;
        trigger = v.trigger;
        status = v.status;
        schedules = v.schedule;
        if (v.createdAt != null) {
            createdAtMs = v.createdAt.getTime();
            createdAt = DateFormatUtils.printDateTime(createdAtMs);
        }
        if (v.statusDate != null) {
            statusDateMs = v.statusDate.getTime();
            statusDate = DateFormatUtils.printDateTime(statusDateMs);
        }
        dataStreams = CollectionOps.map(v.addresses, ApiDataStream::new);
        if (v.getFunctionName(InvocationLane.DEFAULT) != null) {
            function = new Function(v, InvocationLane.DEFAULT);
            asyncFunction = new Function(v, InvocationLane.ASYNC);
        }
    }
}
