package com.ifielsc.ijob.infra.mapper.auth;

import com.ifielsc.ijob.infra.entity.auth.OperationAudit;

import org.apache.ibatis.annotations.Param;

public interface OperationAuditMapper {
    int insert(OperationAudit audit);

    OperationAudit selectById(@Param("id") Long id);

    java.util.List<OperationAudit> selectPage(com.ifielsc.ijob.contract.auth.AuditPageQuery query);

    java.util.List<Long> selectExpired(@Param("cutoff") java.time.LocalDateTime cutoff);

    int deleteExpired(
            @Param("ids") java.util.List<Long> ids,
            @Param("cutoff") java.time.LocalDateTime cutoff);
}
