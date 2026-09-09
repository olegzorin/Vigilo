package dev.olegz.vf.core.domain.lambdaassignment;

public class LambdaVariable {
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_SMALL_VALUE_SIZE = 64_000;

    public int originalSize;
    public byte[] value;
    public byte[] largeValue;
}
