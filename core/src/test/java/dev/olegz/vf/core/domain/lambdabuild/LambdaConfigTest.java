package dev.olegz.vf.core.domain.lambdabuild;

import dev.olegz.vf.common.exception.WrongParameterValueException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaConfigTest {
    @Test
    void rejectsRemovedDataRequestTriggerBit() {
        LambdaConfig config = new LambdaConfig();
        config.trigger = 1 << 3;

        assertThrows(WrongParameterValueException.class, config::checkTriggers);
    }
}
