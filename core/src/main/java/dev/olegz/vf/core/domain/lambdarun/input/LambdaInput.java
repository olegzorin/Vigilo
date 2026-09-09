package dev.olegz.vf.core.domain.lambdarun.input;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.olegz.vf.core.domain.lambdarun.InvocationLane;

public class LambdaInput {
    public int id;                  // lambda assignment ID
    @JsonProperty("flow")
    public InvocationLane lane = InvocationLane.DEFAULT;
    public int lambdaId;
    public int lambdaVersionId;
    public long runId;
    public String apiKey;           // lambda API key
    public Long apiKeyExpiry;       // lambda API key expiry
    public List<String> apiHosts;       // API hosts for cloud-running lambdas
    public Integer count;           // counter of cloud-running lambda triggering
    public TriggerEventData[] inputs;
    public Long timer;              // last timer trigger time
    public Long invocationToken;    // unique token for this invocation attempt
    public Long prevTriggerTime;    // previous launch time for cloud-running lambda
    public Boolean logEvents;       // whether lambda should include the current execution log events in the output

    public LambdaInput() {
    }

    @Override
    public String toString() {
        return "{id=" + id + ", lambdaId=" + lambdaId + ", lambdaVersionId=" + lambdaVersionId + ", runId=" + runId +
            ", apiHosts=" + apiHosts + ", inputs=" + Arrays.toString(inputs) +
            '}';
    }

    public void sortInputs() {
        if ((inputs != null) && (inputs.length > 1)) {
            Arrays.sort(inputs, Comparator.comparingLong(i -> i.time));
        }
    }

    public int triggers() {
        int triggers = 0;
        for (TriggerEventData input : inputs) triggers |= input.trigger;
        return triggers;
    }

}
