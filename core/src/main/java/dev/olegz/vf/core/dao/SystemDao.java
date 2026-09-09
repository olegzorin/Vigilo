package dev.olegz.vf.core.dao;

import java.time.ZonedDateTime;
import java.util.List;

import dev.olegz.vf.core.domain.DateRange;

public interface SystemDao {
    List<DateRange> getTablePartitionDates(String tableName);

    long getMaxTablePartitionTime(String tableName);

    ZonedDateTime getLatestTablePartitionTime(String tableName);

    void addTablePartition(String tableName, String newPartName, String newPartDate);

    void truncateTablePartition(String tableName, String partName);

}
