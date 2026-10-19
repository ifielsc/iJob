package com.ifielsc.ijob.core.auth;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountAccessRequest;
import com.ifielsc.ijob.contract.auth.AccountCreateRequest;
import com.ifielsc.ijob.contract.auth.AccountDTO;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.auth.AccountProfileRequest;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.ApproverDTO;
import com.ifielsc.ijob.contract.auth.ApproverResolutionDTO;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.PasswordChangeRequest;
import com.ifielsc.ijob.contract.auth.PasswordResetRequest;
import com.ifielsc.ijob.core.query.PageResults;
import com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard;
import com.ifielsc.ijob.infra.coordination.id.IdGenerator;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.entity.auth.AuthUser;
import com.ifielsc.ijob.infra.mapper.auth.AuthUserMapper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class AccountService {
    private final AppProperties properties;
    private final AuthUserMapper mapper;
    private final RuntimeMutationGuard guard;
    private final IdGenerator ids;
    private final PasswordEncoder encoder;
    private final AuthSession sessions;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public AccountService(
            AppProperties properties,
            AuthUserMapper mapper,
            RuntimeMutationGuard guard,
            IdGenerator ids,
            PasswordEncoder encoder,
            AuthSession sessions,
            AuditService audit,
            PlatformTransactionManager manager) {
        this.properties = properties;
        this.mapper = mapper;
        this.guard = guard;
        this.ids = ids;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
        tx = new TransactionTemplate(manager);
    }

    public void initialize() {
        if (properties.getAuth().getMode() != AppProperties.AuthMode.DATABASE) return;
        tx.executeWithoutResult(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    if (mapper.countAll() == 0 && properties.getAuth().isBootstrapEnabled()) {
                        String username = properties.getAuth().getUsername();
                        String hash = properties.getAuth().getPasswordHash();
                        if (username == null
                                || username.isBlank()
                                || username.length() > 128
                                || username.codePoints().anyMatch(Character::isISOControl)
                                || hash == null
                                || !hash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$")) {
                            throw new IllegalStateException(
                                    "Database bootstrap requires an explicit username and BCrypt"
                                            + " hash");
                        }
                        long id = ids.nextId(IdNamespace.AUTH_USER);
                        var event =
                                audit.prepare(
                                        null,
                                        "ACCOUNT_BOOTSTRAP",
                                        "ACCOUNT",
                                        Long.toString(id),
                                        "username,roleCode");
                        event.setActorLoginId("SYSTEM");
                        AuthUser user =
                                base(
                                        id,
                                        username,
                                        username,
                                        username,
                                        AccountRole.ADMIN,
                                        true,
                                        "SYSTEM");
                        user.setPasswordHash(hash);
                        changed(mapper.insert(user));
                        AuthUser stored = mapper.selectById(id);
                        if (stored == null
                                || !username.equals(stored.getLoginId())
                                || !username.equals(stored.getUsername())) {
                            throw new IllegalStateException(
                                    "Bootstrap identity cannot be represented exactly by this"
                                            + " database");
                        }
                        audit.append(event, "SUCCESS", null);
                    }
                    if (mapper.countEnabledAdministrators() < 1)
                        throw new IllegalStateException(
                                "Database authentication requires an enabled administrator;"
                                        + " explicitly bootstrap an empty account table");
                });
    }

    public PageResult<AccountDTO> query(int pageNum, int pageSize) {
        return query(pageNum, pageSize, null, null, null);
    }

    public PageResult<AccountDTO> query(
            int pageNum, int pageSize, String name, AccountRole role, Boolean enabled) {
        requireDatabase();
        AuditService.page(pageNum, pageSize);
        String keyword = name == null ? "" : name.trim();
        if (keyword.length() > 128 || keyword.codePoints().anyMatch(Character::isISOControl))
            throw new BusinessException(SecurityError.INPUT);
        String pattern =
                keyword.isEmpty()
                        ? null
                        : "%"
                                + keyword.replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                        .replace("[", "![")
                                + "%";
        try (var ignored = PageHelper.startPage(pageNum, pageSize)) {
            PageResult<AuthUser> rows =
                    PageResults.from(
                            pattern == null && role == null && enabled == null
                                    ? mapper.selectPage()
                                    : mapper.selectFiltered(pattern, role, enabled));
            PageResult<AccountDTO> out = new PageResult<>();
            out.setList(rows.getList().stream().map(AccountService::dto).toList());
            out.setTotal(rows.getTotal());
            out.setPageNum(rows.getPageNum());
            out.setPageSize(rows.getPageSize());
            out.setPages(rows.getPages());
            return out;
        }
    }

    public List<ApproverDTO> approvers(int pageNum, int pageSize) {
        return approvers(pageNum, pageSize, null);
    }

    public List<ApproverDTO> approvers(int pageNum, int pageSize, String name) {
        requireDatabase();
        String keyword = name == null ? "" : name.trim();
        if (keyword.length() > 128 || keyword.codePoints().anyMatch(Character::isISOControl))
            throw new BusinessException(SecurityError.INPUT);
        String pattern =
                keyword.isEmpty()
                        ? null
                        : "%"
                                + keyword.replace("!", "!!")
                                        .replace("%", "!%")
                                        .replace("_", "!_")
                                        .replace("[", "![")
                                + "%";
        AuditService.page(pageNum, pageSize);
        try (var ignored = PageHelper.startPage(pageNum, pageSize, false)) {
            return mapper.selectApprovers(pattern).stream()
                    .map(u -> new ApproverDTO(u.getLoginId(), u.getUsername(), u.getDisplayName()))
                    .toList();
        }
    }

    public List<ApproverResolutionDTO> resolveApprovers(List<String> loginIds) {
        requireDatabase();
        if (loginIds == null
                || loginIds.isEmpty()
                || loginIds.size() > 100
                || loginIds.stream()
                        .anyMatch(
                                id ->
                                        id == null
                                                || id.isBlank()
                                                || id.length() > 128
                                                || id.codePoints()
                                                        .anyMatch(Character::isISOControl)))
            throw new BusinessException(SecurityError.INPUT);
        // Compare exact Java strings as well: some database collations are case-insensitive.
        var rows =
                mapper.selectApprovalAccounts(loginIds).stream()
                        .collect(java.util.stream.Collectors.toMap(AuthUser::getLoginId, u -> u));
        return loginIds.stream()
                .distinct()
                .map(
                        id -> {
                            var user = rows.get(id);
                            String status =
                                    user == null
                                            ? "MISSING"
                                            : !Boolean.TRUE.equals(user.getEnabled())
                                                    ? "DISABLED"
                                                    : !AccountRolePolicy.permissions(
                                                                            user.getRoleCode())
                                                                    .contains(
                                                                            AccountPermission
                                                                                    .APPROVAL_DECIDE)
                                                            ? "FORBIDDEN"
                                                            : "AVAILABLE";
                            return new ApproverResolutionDTO(
                                    id,
                                    user == null ? null : user.getUsername(),
                                    user == null ? null : user.getDisplayName(),
                                    status);
                        })
                .toList();
    }

    public void validateApprovers(List<String> loginIds) {
        if (loginIds == null || loginIds.isEmpty())
            throw new BusinessException(SecurityError.APPROVER_INVALID);
        for (int offset = 0; offset < loginIds.size(); offset += 100) {
            if (resolveApprovers(loginIds.subList(offset, Math.min(offset + 100, loginIds.size())))
                    .stream()
                    .anyMatch(row -> !"AVAILABLE".equals(row.status())))
                throw new BusinessException(SecurityError.APPROVER_INVALID);
        }
    }

    public AccountDTO create(AccountCreateRequest request, CurrentUserResponse actor) {
        requireDatabase();
        if (request == null) throw new BusinessException(SecurityError.INPUT);
        String username = AccountRules.username(request.getUsername());
        AccountRules.password(request.getPassword());
        String display = AccountRules.displayName(request.getDisplayName(), username);
        String hash = encoder.encode(request.getPassword());
        long id = ids.nextId(IdNamespace.AUTH_USER);
        return tx.execute(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    if (mapper.selectByUsername(username) != null)
                        throw new BusinessException(SecurityError.CONFLICT);
                    AuthUser user =
                            base(
                                    id,
                                    "user:" + UUID.randomUUID(),
                                    username,
                                    display,
                                    request.getRoleCode() == null
                                            ? AccountRole.VIEWER
                                            : request.getRoleCode(),
                                    request.getEnabled() == null || request.getEnabled(),
                                    actor.getLoginId());
                    user.setPasswordHash(hash);
                    changed(mapper.insert(user));
                    return dto(user);
                });
    }

    public AccountDTO profile(Long id, AccountProfileRequest request) {
        requireDatabase();
        if (request == null) throw new BusinessException(SecurityError.INPUT);
        long revision = AccountRules.revision(request.getExpectedRevision());
        return tx.execute(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    AuthUser user = require(id);
                    user.setDisplayName(
                            AccountRules.displayName(request.getDisplayName(), user.getUsername()));
                    user.setUpdateTime(AuditService.now());
                    changed(mapper.updateProfile(user, revision));
                    return dto(require(id));
                });
    }

    public AccountDTO access(Long id, AccountAccessRequest request, CurrentUserResponse actor) {
        requireDatabase();
        if (request == null) throw new BusinessException(SecurityError.INPUT);
        long revision = AccountRules.revision(request.getExpectedRevision());
        return tx.execute(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    AuthUser user = require(id);
                    boolean enabled =
                            request.getEnabled() == null
                                    ? Boolean.TRUE.equals(user.getEnabled())
                                    : request.getEnabled();
                    AccountRole role =
                            request.getRoleCode() == null
                                    ? user.getRoleCode()
                                    : request.getRoleCode();
                    if (!enabled && user.getLoginId().equals(actor.getLoginId()))
                        throw new BusinessException(SecurityError.SELF_DISABLE);
                    if (Boolean.TRUE.equals(user.getEnabled())
                            && user.getRoleCode() == AccountRole.ADMIN
                            && (!enabled || role != AccountRole.ADMIN)
                            && mapper.countEnabledAdministrators() <= 1)
                        throw new BusinessException(SecurityError.LAST_ADMIN);
                    user.setEnabled(enabled);
                    user.setRoleCode(role);
                    user.setUpdateTime(AuditService.now());
                    changed(mapper.updateAccess(user, revision));
                    revokeAfterCommit(user.getLoginId());
                    return dto(require(id));
                });
    }

    public void delete(Long id, Long expectedRevision, CurrentUserResponse actor) {
        requireDatabase();
        if (id == null || id <= 0) throw new BusinessException(SecurityError.INPUT);
        long revision = AccountRules.revision(expectedRevision);
        tx.executeWithoutResult(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    AuthUser user = require(id);
                    if (user.getLoginId().equals(actor.getLoginId()))
                        throw new BusinessException(SecurityError.SELF_DELETE);
                    if (Boolean.TRUE.equals(user.getEnabled())
                            && user.getRoleCode() == AccountRole.ADMIN
                            && mapper.countEnabledAdministrators() <= 1)
                        throw new BusinessException(SecurityError.LAST_ADMIN);
                    changed(mapper.deleteByRevision(id, revision));
                    revokeAfterCommit(user.getLoginId());
                });
    }

    public void reset(Long id, PasswordResetRequest request) {
        requireDatabase();
        if (request == null) throw new BusinessException(SecurityError.INPUT);
        long revision = AccountRules.revision(request.getExpectedRevision());
        AccountRules.password(request.getNewPassword());
        String hash = encoder.encode(request.getNewPassword());
        tx.executeWithoutResult(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    AuthUser user = require(id);
                    user.setPasswordHash(hash);
                    user.setMustChangePassword(true);
                    user.setUpdateTime(AuditService.now());
                    changed(mapper.updatePassword(user, revision));
                    revokeAfterCommit(user.getLoginId());
                });
    }

    public void changePassword(PasswordChangeRequest request, CurrentUserResponse actor) {
        requireDatabase();
        if (request == null
                || request.getCurrentPassword() == null
                || request.getCurrentPassword()
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                .length
                        > 72) throw new BusinessException(SecurityError.INPUT);
        AccountRules.password(request.getNewPassword());
        AuthUser before = mapper.selectByLoginId(actor.getLoginId());
        if (before == null
                || !encoder.matches(request.getCurrentPassword(), before.getPasswordHash()))
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        if (encoder.matches(request.getNewPassword(), before.getPasswordHash()))
            throw new BusinessException(SecurityError.PASSWORD);
        String hash = encoder.encode(request.getNewPassword());
        tx.executeWithoutResult(
                status -> {
                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                    AuthUser user = require(before.getId());
                    user.setPasswordHash(hash);
                    user.setMustChangePassword(false);
                    user.setUpdateTime(AuditService.now());
                    changed(mapper.updatePassword(user, before.getRevision()));
                    revokeAfterCommit(user.getLoginId());
                });
    }

    private void revokeAfterCommit(String loginId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            sessions.revoke(loginId);
                        } catch (RuntimeException ignored) {
                            log.error(
                                    "SECURITY_SESSION_REVOKE_FAILED; database security version"
                                            + " remains enforced");
                        }
                    }
                });
    }

    public AccountDTO getDetail(Long id) {
        requireDatabase();
        if (id == null || id <= 0) throw new BusinessException(SecurityError.NOT_FOUND);
        return dto(require(id));
    }

    private AuthUser require(Long id) {
        AuthUser user = mapper.selectById(id);
        if (user == null) throw new BusinessException(SecurityError.NOT_FOUND);
        return user;
    }

    private void requireDatabase() {
        if (properties.getAuth().getMode() != AppProperties.AuthMode.DATABASE)
            throw new BusinessException(SecurityError.DATABASE_REQUIRED);
    }

    private static void changed(int count) {
        if (count != 1) throw new BusinessException(SecurityError.CONFLICT);
    }

    private static AuthUser base(
            long id,
            String loginId,
            String username,
            String display,
            AccountRole role,
            boolean enabled,
            String creator) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setLoginId(loginId);
        user.setUsername(username);
        user.setDisplayName(display);
        user.setRoleCode(role);
        user.setEnabled(enabled);
        user.setMustChangePassword(true);
        user.setSecurityVersion(0L);
        user.setRevision(0L);
        user.setCreatedBy(creator);
        user.setCreateTime(AuditService.now());
        user.setUpdateTime(user.getCreateTime());
        return user;
    }

    private static AccountDTO dto(AuthUser user) {
        return new AccountDTO(
                user.getId().toString(),
                user.getLoginId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getRevision().toString(),
                user.getCreateTime(),
                user.getUpdateTime());
    }
}
