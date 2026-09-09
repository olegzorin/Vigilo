package dev.olegz.vf.core.domain.lambdarun;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An independent sequential lane of lambda invocations. Invocations within one lane execute
 * sequentially, while different lanes for the same lambda assignment may execute concurrently.
 */
public enum InvocationLane {
    DEFAULT((byte) 0),
    ASYNC((byte) 1);

    private final byte code;

    InvocationLane(byte code) {
        this.code = code;
    }

    @JsonValue
    public byte code() {
        return code;
    }

    @JsonCreator
    public static InvocationLane fromCode(byte code) {
        return switch (code) {
            case 0 -> DEFAULT;
            case 1 -> ASYNC;
            default -> throw new IllegalArgumentException("Unknown invocation lane code: " + code);
        };
    }
}
