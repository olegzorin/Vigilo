package dev.olegz.vf.core.dao;

import java.util.List;

import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.core.TestConfiguration;
import dev.olegz.vf.core.domain.DateRange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link SystemDao}.
 * <p>
 * The partition-read methods are exercised only against the deterministic
 * "table is not partitioned" path, using {@code users}.
 * <p>
 * Not covered:
 * <ul>
 *   <li>{@code addTablePartition} / {@code truncateTablePartition} - {@code ALTER TABLE} DDL that
 *       auto-commits and so cannot be isolated by {@code @Rollback}, and would mutate the schema.
 *   <li>The positive partition-read path - date-range partitions are added at runtime by the
 *       scheduler and are absent from a freshly built schema, so it is environment-dependent.
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
@Transactional
@Rollback
class SystemDaoTest {

    @BeforeAll
    static void setUp() {
        VigiloEnvironment.getHomeDir();
    }

    private final SystemDao systemDao;

    @Autowired
    SystemDaoTest(SystemDao systemDao) {
        this.systemDao = systemDao;
    }

    @Test
    void getTablePartitionDates_nonPartitionedTable_returnsEmpty() {
        List<DateRange> ranges = systemDao.getTablePartitionDates("users");
        assertNotNull(ranges);
        assertTrue(ranges.isEmpty());
    }

    @Test
    void getLatestTablePartitionTime_nonPartitionedTable_returnsNull() {
        assertNull(systemDao.getLatestTablePartitionTime("users"));
    }

    @Test
    void getMaxTablePartitionTime_nonPartitionedTable_returnsZero() {
        assertEquals(0L, systemDao.getMaxTablePartitionTime("users"));
    }
}
