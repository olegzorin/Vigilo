package dev.olegz.vf.core.dao.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface SystemMapper {
    String selectLatestPartitionDesc(String tableName);

    List<String> selectPartitionDescriptions(String tableName);

    void addPartition(
        @Param("tableName") String tableName,
        @Param("newPartName") String newPartName,
        @Param("previousPartDate") String previousPartDate,
        @Param("newPartDate") String newPartDate);

    void truncateTablePartition(
        @Param("tableName") String tableName,
        @Param("partName") String partName);

}
