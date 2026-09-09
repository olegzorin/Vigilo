package dev.olegz.vf.core.domain.lambdaversion;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LambdaVersionTest {

    @Test
    void runnableVersionRequiresExplicitAsyncFunctionName() {
        LambdaVersion version = new LambdaVersion();
        version.status = LambdaVersionStatus.TESTING;
        version.functionName = "lambda:1";
        version.functionStartDate = new Timestamp(System.currentTimeMillis());
        version.memory = 1024;
        version.timeout = 30;

        assertEquals("async function name is missing", version.checkRunnable());

        version.asyncFunctionName = "lambda-async:1";
        assertNull(version.checkRunnable());
    }
}
