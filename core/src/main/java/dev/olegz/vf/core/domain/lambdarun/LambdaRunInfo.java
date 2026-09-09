package dev.olegz.vf.core.domain.lambdarun;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import dev.olegz.vf.common.props.IntProp;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.codec.Lz4Codec;
import dev.olegz.vf.core.domain.ResultList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class LambdaRunInfo implements ResultList.Item {
    public static final byte SUCCESS = 0;
    public static final byte LAMBDA_ERROR = 1;  // Lambda code error
    public static final byte LAMBDA_TIMEOUT = 2; // Lambda run was timed out on AWS Lambda

    /* AWS SdkServiceException has occurred */
    //public static final byte AWS_SERVICE_ERROR = -1;
    /* AWS SdkClientException has occurred */
    public static final byte AWS_CLIENT_ERROR = -2;
    /* Response from AWS Lambda hasn't been received in time */
    public static final byte REQUEST_TIMEOUT = -3;
    /* AWS reply was “We currently do not have sufficient capacity in the region you requested.
     * Our system will be working on provisioning additional capacity. You can avoid getting
     * this error by temporarily reducing your request rate.” */
    public static final byte AWS_SERVER_CAPACITY_ERROR = -4;
    /* Lambda has called the /vf/start API twice with the same key */
    public static final byte DUPLICATE_REQUEST = -5;
    /* The call to /vf/start API has not been received from lambda */
    public static final byte LAMBDA_NOT_STARTED = -6;
    /* A new run of the lambda assignment was started before the current run completed */
    public static final byte OTHER_RUN_STARTED = -7;
    /* Run exception on the client side */
    public static final byte CLIENT_FAILURE = -8;

    public static boolean isLambdaError(byte resultCode) {
        return (resultCode == LAMBDA_ERROR) || (resultCode == LAMBDA_TIMEOUT);
    }

    public static int partitionsCount() {
        return PropertyStore.getInt(IntProp.LAMBDA_RUN_RESULTS_PARTITIONS_COUNT); // cannot be less than 2
    }

    public static int partitionIndex(long time) {
        int partitionsShift = PropertyStore.getInt(IntProp.LAMBDA_RUN_RESULTS_PARTITIONS_SHIFT);
        return days(time) % partitionsCount() + partitionsShift;
    }

    public static int[] partitions(long startTime, long endTime) {
        int start = partitionIndex(startTime);
        endTime -= 1000L;

        if (endTime <= startTime) return new int[]{start};

        int end = partitionIndex(endTime);
        int partitionsCount = partitionsCount();

        int[] res = new int[end + 1 - (start <= end ? start : start - partitionsCount)];
        for (int i = 0; i < res.length; i++) res[i] = (byte) ((i + start) % partitionsCount);

        return res;
    }

    private static final int FIRST_YEAR = 2026;

    private static int days(long time) {
        ZonedDateTime zdt = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC);

        if (zdt.getYear() < FIRST_YEAR) {
            throw new IllegalArgumentException("Unsupported date");
        }

        int days = zdt.getDayOfYear();

        while (zdt.getYear() > FIRST_YEAR) {
            zdt = zdt.plusYears(-1);
            // for leap years
            days += Year.from(zdt).length();
        }

        return days;
    }


    public int part;
    public int lambdaAssignmentId;
    public int lambdaId;
    public Integer locationId;
    public String lambdaName;
    public int lambdaVersionId;
    public String version;
    public int memory;
    public String functionName;
    public Timestamp functionStartDate;
    public Timestamp requestDate;
    public byte resultCode;
    public InvocationLane lane;
    public int triggers;
    public long executionTime;
    public long processingTime;
    public int eventsCount;
    public Timestamp eventDate;
    public String resultMessage;
    public byte[] resultMessageCompressed;
    public String server;
    public int retries;

    @Override
    public int id() {
        return lambdaAssignmentId;
    }

    @Override
    public Timestamp ts() {
        return requestDate;
    }

    public LambdaRunInfo(int lambdaId, int lambdaAssignmentId, InvocationLane lane, int memory) {
        this.lambdaId = lambdaId;
        this.lambdaAssignmentId = lambdaAssignmentId;
        this.lane = lane;
        this.memory = memory;
    }

    public void prepareFields(Logger logger) {
        part = partitionIndex(requestDate.getTime());
        server = StringUtils.truncate(server, 3);

        if (resultMessage != null && !resultMessage.isBlank()) {
            resultMessageCompressed = compress(logger);
        }
    }

    private static final int MAX_COMPRESSED_MESSAGE_SIZE = 32_000;
    private static final int MAX_UNCOMPRESSED_MESSAGE_SIZE = MAX_COMPRESSED_MESSAGE_SIZE * 100;

    private byte[] compress(Logger logger) {
        try {
            byte[] res = Lz4Codec.lz4CompressWithLength(resultMessage.getBytes(StandardCharsets.UTF_8));
            if (res.length <= MAX_COMPRESSED_MESSAGE_SIZE) return res;

            for (int i = 0; i < 10; i++) {
                int newSize = resultMessage.length() / res.length;
                if (newSize == 0) {
                    newSize = MAX_COMPRESSED_MESSAGE_SIZE - 100;
                } else {
                    newSize *= MAX_COMPRESSED_MESSAGE_SIZE;
                    newSize -= 100;
                }

                if (newSize >= resultMessage.length()) newSize = resultMessage.length() / 2;

                resultMessage = resultMessage.substring(0, newSize);
                res = Lz4Codec.lz4CompressWithLength(resultMessage.getBytes(StandardCharsets.UTF_8));
                if (res.length <= MAX_COMPRESSED_MESSAGE_SIZE) return res;
            }
        } catch (Exception e) {
            logger.error("Exception in compressing resultMessage\n" + resultMessage, e);
        }
        return null;
    }

    public void uncompress(Logger logger) {
        if (resultMessageCompressed != null) {
            try {
                byte[] bytes = Lz4Codec.lz4UncompressWithLength(resultMessageCompressed, MAX_UNCOMPRESSED_MESSAGE_SIZE);
                if (bytes != null) resultMessage = new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error("Exception in uncompressing resultMessage\n" + this, e);
            }
        }
    }

    @Override
    public String toString() {
        return "{lambdaId=" + lambdaId +
            ", lambdaAssignmentId=" + lambdaAssignmentId +
            ", lambdaVersionId=" + lambdaVersionId +
            ", lane=" + lane +
            ", resultCode=" + resultCode +
            ", requestDate=" + requestDate +
            ", executionTime=" + executionTime +
            ", triggers=" + triggers +
            ", eventsCount=" + eventsCount +
            ", memory=" + memory +
            '}';
    }
}
