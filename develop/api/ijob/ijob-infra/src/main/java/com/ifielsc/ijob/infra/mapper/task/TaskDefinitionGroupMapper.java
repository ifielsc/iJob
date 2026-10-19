package com.ifielsc.ijob.infra.mapper.task;

import com.ifielsc.ijob.infra.mapper.group.DefinitionGroupRow;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskDefinitionGroupMapper {
    int insert(
            @Param("definitionId") Long definitionId,
            @Param("groupCode") String groupCode,
            @Param("createTime") LocalDateTime createTime);

    List<DefinitionGroupRow> selectByDefinitionIds(
            @Param("definitionIds") List<Long> definitionIds);
}
