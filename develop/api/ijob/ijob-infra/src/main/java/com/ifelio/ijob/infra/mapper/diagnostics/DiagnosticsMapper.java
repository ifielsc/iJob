package com.ifelio.ijob.infra.mapper.diagnostics;

import com.ifelio.ijob.contract.diagnostics.DiagnosticCategory;
import com.ifelio.ijob.contract.diagnostics.DiagnosticItemDTO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiagnosticsMapper {
    List<DiagnosticItemDTO> selectItems(
            @Param("category") DiagnosticCategory category,
            @Param("now") LocalDateTime now,
            @Param("queueCutoff") LocalDateTime queueCutoff);
}
