package dev.olegz.vf.core.dao.impl;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import dev.olegz.vf.common.util.DateFormatUtils;
import dev.olegz.vf.registry.cache.CacheNames;
import dev.olegz.vf.core.cache.MethodCacheKeyGenerator;
import dev.olegz.vf.core.dao.SystemDao;
import dev.olegz.vf.core.dao.mapper.SystemMapper;
import dev.olegz.vf.core.domain.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository("systemDao")
public class SystemDaoImpl implements SystemDao {
    private static final Logger logger = LoggerFactory.getLogger(SystemDaoImpl.class);

    private final SystemMapper mapper;

    public SystemDaoImpl(SystemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Cacheable(cacheNames = CacheNames.CONSTANT_DICTIONARY, keyGenerator = MethodCacheKeyGenerator.ID)
    public List<DateRange> getTablePartitionDates(String tableName) {
        return DateRange.fromTablePartitions(mapper.selectPartitionDescriptions(tableName));
    }

    @Override
    @Cacheable(cacheNames = CacheNames.CONSTANT_DICTIONARY, keyGenerator = MethodCacheKeyGenerator.ID)
    public long getMaxTablePartitionTime(String tableName) {
        ZonedDateTime time = getLatestTablePartitionTime(tableName);
        return time != null ? time.toEpochSecond() * 1000L : 0L;
    }

    @Override
    public ZonedDateTime getLatestTablePartitionTime(String tableName) {
        String desc = mapper.selectLatestPartitionDesc(tableName);
        if (desc == null) return null;

        try {
            return DateFormatUtils.parseDateZoned(desc.replace("'", ""));
        } catch (DateTimeParseException e) {
            logger.error("ParseException in getting latest table partition time for tableName=" + tableName + ", partitionDesc=" + desc + " : " + e);
        }

        return null;
    }

    @Override
    public void addTablePartition(String tableName, String newPartName, String newPartDate) {
        String previousPartDate = mapper.selectLatestPartitionDesc(tableName);
        if (previousPartDate == null) {
            throw new IllegalStateException("Cannot add a range partition without an existing lower bound: " + tableName);
        }
        mapper.addPartition(tableName, newPartName, quoteLiteral(previousPartDate), newPartDate);
    }

    @Override
    public void truncateTablePartition(String tableName, String partName) {
        mapper.truncateTablePartition(tableName, partName);
    }

    private static String quoteLiteral(String value) {
        return '\'' + value.replace("'", "''") + '\'';
    }

}
