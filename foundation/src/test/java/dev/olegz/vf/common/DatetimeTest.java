package dev.olegz.vf.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatetimeTest {

    @Test
    void dayOffsetsMoveInExpectedDirection() {
        Datetime now = Datetime.now();

        assertTrue(Datetime.nowPlusDays(1).after(now));
        assertTrue(Datetime.nowMinusDays(1).before(now));
    }
}
