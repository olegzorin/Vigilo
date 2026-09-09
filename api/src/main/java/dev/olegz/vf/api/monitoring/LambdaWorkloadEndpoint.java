package dev.olegz.vf.api.monitoring;

import java.util.List;

import dev.olegz.vf.core.dao.metric.LambdaWorkloadMetricSnapshot;
import dev.olegz.vf.core.dao.metric.LambdaWorkloadMetrics;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "lambdaworkload")
public class LambdaWorkloadEndpoint {

    /** Exposes the in-process API's selected lambda workload. */
    @ReadOperation
    public List<LambdaWorkloadMetricSnapshot> snapshot() {
        return LambdaWorkloadMetrics.snapshot();
    }
}
