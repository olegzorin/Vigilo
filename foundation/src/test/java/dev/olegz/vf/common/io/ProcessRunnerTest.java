package dev.olegz.vf.common.io;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessRunnerTest {

    @Test
    void reportsTruncatedMergedLog() {
        ProcessBuilder processBuilder = shell("printf 123456789").redirectErrorStream(true);

        ProcessRunner.CompletedProcess process = ProcessRunner.run(processBuilder, null, 5_000, 5);

        assertEquals("56789", process.getLog());
        assertTrue(process.isLogTruncated());
        assertNull(process.getOut());
        assertFalse(process.isOutTruncated());
    }

    @Test
    void reportsSeparatedStreamsWithoutTruncation() {
        ProcessBuilder processBuilder = shell("printf output; printf error >&2");

        ProcessRunner.CompletedProcess process = ProcessRunner.run(processBuilder, null, 5_000, 32);

        assertEquals("output", process.getOut());
        assertEquals("error", process.getLog());
        assertFalse(process.isOutTruncated());
        assertFalse(process.isLogTruncated());
    }

    private static ProcessBuilder shell(String command) {
        return new ProcessBuilder("/bin/sh", "-c", command);
    }
}
