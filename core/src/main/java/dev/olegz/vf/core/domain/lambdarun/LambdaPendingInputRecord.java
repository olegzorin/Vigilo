package dev.olegz.vf.core.domain.lambdarun;

import java.time.Duration;

import dev.olegz.vf.common.objectmap.BytesMapper;

public class LambdaPendingInputRecord {
    public static final int MAX_SHORT_SIZE = 2048 * 31;
    private static final int MIN_SHORT_SIZE = 2048 * 4;
    private static final long ONE_HOUR_MILLIS = Duration.ofHours(1).toMillis();

    public long inputNumber;
    public int inputSize;
    public byte[] data;

    private LambdaPendingInputRecord() {}

    public LambdaPendingInputRecord(long inputNumber, byte[] data) {
        this.inputNumber = inputNumber;
        this.data = data;
        inputSize = data.length;
    }

    public static LambdaPendingInputRecord fromInput(long inputNumber, LambdaPendingInput input) {
        return new LambdaPendingInputRecord(inputNumber, BytesMapper.writeValue(input));
    }

    public LambdaPendingInput toInput() {
        if ((data == null) || (inputSize == 0) || (data.length == 0)) {
            return placeholderInput();
        }
        int len = Math.min(inputSize, data.length);
        LambdaPendingInput input = BytesMapper.readValue(data, len, LambdaPendingInput.class);
        input.inputNumber = inputNumber;
        return input;
    }

    public LambdaPendingInput placeholderInput() {
        LambdaPendingInput input = new LambdaPendingInput();
        input.inputNumber = inputNumber;
        return input;
    }

    @Override
    public String toString() {
        return "{inputNumber=" + inputNumber + ", inputSize=" + inputSize + '}';
    }

    // DAO helpers

    public static boolean isLarge(int inputSize) {
        return inputSize > MAX_SHORT_SIZE;
    }

    public static int paddedSizeOf(int inputSize) {
        return inputSize > MAX_SHORT_SIZE ? inputSize
            : inputSize <= MIN_SHORT_SIZE ? MIN_SHORT_SIZE
              : (inputSize + 2047) & (-2048);
    }


    // Partitions

    private static final String[] readPartitions = { "2,3,0", "3,0,1", "0,1,2", "1,2,3" };
    public static int getInsertPart() {
        return (int) ((System.currentTimeMillis() / ONE_HOUR_MILLIS) % 4);
    }

    public static String getReadPartitions() {
        return readPartitions[getInsertPart()];
    }

    public static String getIdlePartition() {
        int p = getInsertPart();
        return "p" + (p == 3 ? 0 : p + 1);
    }
}
