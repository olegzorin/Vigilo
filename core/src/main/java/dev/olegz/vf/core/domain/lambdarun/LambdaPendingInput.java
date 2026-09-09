package dev.olegz.vf.core.domain.lambdarun;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.olegz.vf.core.domain.lambdarun.input.TriggerEventData;

public class LambdaPendingInput {
    @JsonIgnore
    public long inputNumber;
    public TriggerEventData eventData;
    public int trigger;

    public LambdaPendingInput() {
    }

    public LambdaPendingInput(LambdaRun lambdaRun) {
        this.eventData = lambdaRun.inputs[0];
        this.trigger = lambdaRun.trigger;
    }

    @Override
    public String toString() {
        return "{eventData=" + eventData +
            ", trigger=" + trigger +
            '}';
    }
}
