package com.ifelio.ijob.infra.mapper.notify;

import org.apache.ibatis.annotations.Param;

public interface NotifyRateLimitMapper {
    int insertIfAbsent(@Param("key") String key);

    Long selectDeadline(@Param("key") String key);

    int acquire(@Param("key") String key, @Param("now") long now, @Param("deadline") long deadline);

    int cooldown(@Param("key") String key, @Param("deadline") long deadline);
}
