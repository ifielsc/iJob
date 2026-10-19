package com.ifielsc.ijob.infra.mapper.coordination;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClusterStateMapper {
    String selectValue(@Param("stateKey") String stateKey);

    Long selectTimeoutSeconds(@Param("stateKey") String stateKey);

    int upsert(
            @Param("stateKey") String stateKey,
            @Param("stateValue") String stateValue,
            @Param("timeoutSeconds") long timeoutSeconds);

    int updateValue(@Param("stateKey") String stateKey, @Param("stateValue") String stateValue);

    int updateTimeout(
            @Param("stateKey") String stateKey, @Param("timeoutSeconds") long timeoutSeconds);

    int delete(@Param("stateKey") String stateKey);

    List<String> searchKeys(
            @Param("prefix") String prefix,
            @Param("keyword") String keyword,
            @Param("start") int start,
            @Param("size") int size,
            @Param("ascending") boolean ascending);

    int insertIfAbsent(@Param("stateKey") String stateKey, @Param("stateValue") String stateValue);

    int deleteExpired(@Param("limit") int limit);
}
