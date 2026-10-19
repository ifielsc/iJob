// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.runtime;

import org.apache.ibatis.annotations.Param;

public interface EtlFileStateMapper {
    int acquire(
            @Param("taskId") Long taskId,
            @Param("key") String key,
            @Param("instanceId") Long instanceId,
            @Param("token") String token);

    int guard(@Param("taskId") Long taskId, @Param("key") String key, @Param("token") String token);

    int release(
            @Param("taskId") Long taskId, @Param("key") String key, @Param("token") String token);

    String digest(
            @Param("taskId") Long taskId,
            @Param("key") String key,
            @Param("pathHash") String pathHash);

    int deleteRecord(
            @Param("taskId") Long taskId,
            @Param("key") String key,
            @Param("pathHash") String pathHash);

    int insertRecord(
            @Param("taskId") Long taskId,
            @Param("key") String key,
            @Param("pathHash") String pathHash,
            @Param("digest") String digest,
            @Param("marker") String marker,
            @Param("instanceId") Long instanceId);
}
