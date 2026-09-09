package dev.olegz.vf.api.lambda;

import java.sql.Timestamp;

import dev.olegz.vf.common.Datetime;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersion;
import dev.olegz.vf.core.domain.lambdaversion.LambdaVersionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiLambdaVersionTest {
    @BeforeAll
    static void startPropertyStore() {
        PropertyStore.start();
    }

    @Test
    void exposesGeneralAsyncFunctionWithoutMlField() {
        LambdaVersion version = new LambdaVersion();
        version.status = LambdaVersionStatus.TESTING;
        version.functionName = "lambda:1";
        version.asyncFunctionName = "lambda-async:1";
        version.functionStartDate = new Timestamp(1_000L);
        version.createdAt = new Datetime(2_000L);
        version.memory = 1024;
        version.timeout = 30;

        String json = StringMapper.toString(new ApiLambdaVersion(version));

        assertTrue(json.contains("\"asyncFunction\""));
        assertTrue(json.contains("\"name\":\"lambda-async:1\""));
        assertTrue(json.contains("\"createdAt\""));
        assertTrue(json.contains("\"createdAtMs\":2000"));
        assertFalse(json.contains("\"mlFunction\""));
    }
}
