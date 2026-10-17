package com.ifelio.ijob.core.auth;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.auth.AuditDTO;
import com.ifelio.ijob.contract.auth.AuditPageQuery;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.coordination.id.IdGenerator;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.entity.auth.OperationAudit;
import com.ifelio.ijob.infra.mapper.auth.OperationAuditMapper;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@lombok.extern.slf4j.Slf4j
public class AuditService {
    private final OperationAuditMapper mapper;
    private final IdGenerator ids;
    private final TransactionTemplate independent;
    private final TransactionTemplate maintenance;

    public AuditService(
            OperationAuditMapper mapper, IdGenerator ids, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.ids = ids;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Cleanup must share the maintenance lease transaction and its SQLite write lock.
        maintenance = new TransactionTemplate(manager);
    }

    /** Prepare audit identity and metadata; SQLite joins any active transaction without caching. */
    public OperationAudit prepare(
            CurrentUserResponse actor, String action, String type, String target, String fields) {
        OperationAudit event = new OperationAudit();
        event.setId(ids.nextId(IdNamespace.OPERATION_AUDIT));
        event.setOccurredAt(now());
        event.setActorLoginId(actor == null ? "ANONYMOUS" : actor.getLoginId());
        event.setActorName(actor == null ? null : actor.getDisplayName());
        event.setActionCode(code(action, 64));
        event.setTargetType(code(type, 32));
        event.setTargetId(identifier(target, 128));
        event.setChangedFields(fields == null || fields.isEmpty() ? null : code(fields, 1024));
        String trace = MDC.get("traceId");
        event.setTraceId(trace != null && trace.matches("[a-fA-F0-9]{16,32}") ? trace : null);
        return event;
    }

    public void append(OperationAudit event, String result, String requestId) {
        event.setResultCode(code(result, 32));
        event.setRequestId(identifier(requestId, 128));
        if (mapper.insert(event) != 1) throw new IllegalStateException("Audit append failed");
    }

    public void independent(OperationAudit event, String result, String requestId) {
        independent.executeWithoutResult(status -> append(event, result, requestId));
    }

    public void bestEffort(OperationAudit event, String result) {
        try {
            independent(event, result, null);
        } catch (RuntimeException ignored) {
            log.error("SECURITY_AUDIT_WRITE_FAILED action={}", event.getActionCode());
        }
    }

    public PageResult<AuditDTO> query(AuditPageQuery query) {
        AuditPageQuery q = query == null ? new AuditPageQuery() : query;
        page(q.getPageNum(), q.getPageSize());
        if (!"occurredAt".equals(q.getSortBy())
                || !("asc".equals(q.getSortDirection()) || "desc".equals(q.getSortDirection())))
            throw new BusinessException(SecurityError.INPUT);
        LocalDateTime end = q.getTo() == null ? now() : q.getTo();
        LocalDateTime start = q.getFrom() == null ? end.minusDays(7) : q.getFrom();
        if (start.isAfter(end) || start.plusDays(90).isBefore(end))
            throw new BusinessException(SecurityError.INPUT);
        q.setFrom(start);
        q.setTo(end);
        identifier(q.getActorLoginId(), 128);
        identifier(q.getActionCode(), 64);
        identifier(q.getTargetType(), 64);
        identifier(q.getTargetId(), 128);
        identifier(q.getResultCode(), 32);
        try (var ignored = PageHelper.startPage(q.getPageNum(), q.getPageSize())) {
            PageResult<OperationAudit> rows = PageResults.from(mapper.selectPage(q));
            return map(rows);
        }
    }

    public AuditDTO get(Long id) {
        OperationAudit row = mapper.selectById(id);
        if (row == null) throw new BusinessException(SecurityError.NOT_FOUND);
        return dto(row);
    }

    public int cleanup(LocalDateTime cutoff, int limit, boolean dryRun) {
        page(1, limit);
        return maintenance.execute(
                status -> {
                    List<Long> expired;
                    try (var ignored = PageHelper.startPage(1, limit, false)) {
                        expired = mapper.selectExpired(cutoff);
                    }
                    if (expired.isEmpty() || dryRun) return expired.size();
                    return mapper.deleteExpired(expired, cutoff);
                });
    }

    public static void page(int number, int size) {
        if (number < 1 || number > 100000 || size < 1 || size > 500)
            throw new BusinessException(SecurityError.INPUT);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static String code(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9_.,:-]+"))
            throw new IllegalArgumentException("Invalid audit code");
        return value;
    }

    private static String identifier(String value, int max) {
        if (value != null
                && (value.length() > max || value.codePoints().anyMatch(Character::isISOControl)))
            throw new BusinessException(SecurityError.INPUT);
        return value;
    }

    private static AuditDTO dto(OperationAudit row) {
        return new AuditDTO(
                row.getId().toString(),
                row.getOccurredAt(),
                row.getActorLoginId(),
                row.getActorName(),
                row.getActionCode(),
                row.getTargetType(),
                row.getTargetId(),
                row.getResultCode(),
                row.getTraceId(),
                row.getRequestId(),
                row.getChangedFields());
    }

    private static PageResult<AuditDTO> map(PageResult<OperationAudit> rows) {
        PageResult<AuditDTO> out = new PageResult<>();
        out.setList(rows.getList().stream().map(AuditService::dto).toList());
        out.setTotal(rows.getTotal());
        out.setPageNum(rows.getPageNum());
        out.setPageSize(rows.getPageSize());
        out.setPages(rows.getPages());
        return out;
    }
}
