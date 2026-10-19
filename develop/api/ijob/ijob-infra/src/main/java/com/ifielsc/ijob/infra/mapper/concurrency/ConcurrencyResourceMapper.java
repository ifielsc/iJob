package com.ifielsc.ijob.infra.mapper.concurrency;

import com.ifielsc.ijob.infra.entity.concurrency.ConcurrencyResource;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ConcurrencyResourceMapper {
    List<ConcurrencyResource> selectPage(
            com.ifielsc.ijob.contract.concurrency.ConcurrencyResourcePageQuery query);

    ConcurrencyResource selectById(@Param("id") Long id);

    ConcurrencyResource selectByCode(@Param("code") String code);

    List<ConcurrencyResource> selectByIds(@Param("ids") List<Long> ids);

    int insert(ConcurrencyResource resource);

    int update(ConcurrencyResource resource);

    int deleteById(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    int countTaskReferences(@Param("id") Long id);
}
