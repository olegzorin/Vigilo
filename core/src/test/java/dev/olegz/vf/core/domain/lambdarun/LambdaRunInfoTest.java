package dev.olegz.vf.core.domain.lambdarun;

import java.sql.Timestamp;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;


class LambdaRunInfoTest {

    @Test
    void prepareFields_compressesResultMessageWithLz4() {
        LambdaRunInfo runInfo = new LambdaRunInfo(1, 2, InvocationLane.ASYNC, 128);
        runInfo.requestDate = new Timestamp(System.currentTimeMillis());
        runInfo.resultMessage = "Lambda run failed after processing payload abc123";

        runInfo.prepareFields(LoggerFactory.getLogger(LambdaRunInfoTest.class));

        LambdaRunInfo loaded = new LambdaRunInfo(1, 2, InvocationLane.ASYNC, 128);
        loaded.resultMessageCompressed = runInfo.resultMessageCompressed;
        loaded.uncompress(LoggerFactory.getLogger(LambdaRunInfoTest.class));

        assertEquals(runInfo.resultMessage, loaded.resultMessage);
    }

    @Test
    void prepareFields_truncatesOversizedCompressedResultMessage() {
        LambdaRunInfo runInfo = new LambdaRunInfo(1, 2, InvocationLane.ASYNC, 128);
        runInfo.requestDate = new Timestamp(System.currentTimeMillis());
        runInfo.resultMessage = randomAscii(100_000);

        runInfo.prepareFields(LoggerFactory.getLogger(LambdaRunInfoTest.class));

        assertNotNull(runInfo.resultMessageCompressed);
        assertTrue(runInfo.resultMessageCompressed.length <= 32_000);
    }

    private static String randomAscii(int length) {
        Random random = new Random(42);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }
}
