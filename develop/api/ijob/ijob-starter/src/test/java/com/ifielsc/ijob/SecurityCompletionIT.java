package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.auth.AccountAccessRequest;
import com.ifielsc.ijob.contract.auth.AccountCreateRequest;
import com.ifielsc.ijob.contract.auth.AccountDTO;
import com.ifielsc.ijob.contract.auth.AccountProfileRequest;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.AuditPageQuery;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginRequest;
import com.ifielsc.ijob.contract.auth.LoginResponse;
import com.ifielsc.ijob.contract.auth.PasswordChangeRequest;
import com.ifielsc.ijob.contract.auth.PasswordResetRequest;
import com.ifielsc.ijob.core.auth.AccountService;
import com.ifielsc.ijob.core.auth.AuditService;
import com.ifielsc.ijob.core.auth.AuthSession;
import com.ifielsc.ijob.core.auth.DatabaseAuthentication;
import com.ifielsc.ijob.infra.mapper.auth.AuthUserMapper;
import com.ifielsc.ijob.infra.mapper.auth.OperationAuditMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityCompletionIT {
    private final SecurityFoundationV27IT fixture = new SecurityFoundationV27IT();
    private final AppProperties properties = new AppProperties();
    private final AtomicLong ids = new AtomicLong(1900000);
    private final FakeSession session = new FakeSession();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AccountService accounts;
    private AuditService audit;
    private DatabaseAuthentication authentication;
    private AuthUserMapper users;
    private CurrentUserResponse admin;
    private static final String PASSWORD = "Completion-test-password-123";

    @BeforeAll
    void setup() throws Exception {
        fixture.targetPrefix("sc27");
        fixture.provisionAndUpgrade();
        properties.getAuth().setMode(AppProperties.AuthMode.DATABASE);
        properties.getAuth().setUsername("legacy-admin");
        properties.getAuth().setPasswordHash(encoder.encode(PASSWORD));
        properties.getAuth().setBootstrapEnabled(true);
        users = fixture.sessions().getMapper(AuthUserMapper.class);
        var manager = new DataSourceTransactionManager(fixture.dataSource());
        audit =
                new AuditService(
                        fixture.sessions().getMapper(OperationAuditMapper.class),
                        namespace -> ids.incrementAndGet(),
                        manager);
        accounts =
                new AccountService(
                        properties,
                        users,
                        fixture.guard(),
                        namespace -> ids.incrementAndGet(),
                        encoder,
                        session,
                        audit,
                        manager);
        authentication = new DatabaseAuthentication(properties, users, encoder, session, audit);
    }

    @Test
    @Order(1)
    void bootstrapIsOnceUnderConcurrentNodesAndPreservesLegacyPrincipal() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results =
                    pool.invokeAll(
                            List.of(
                                    () -> {
                                        accounts.initialize();
                                        return true;
                                    },
                                    () -> {
                                        accounts.initialize();
                                        return true;
                                    }));
            for (var result : results) assertEquals(true, result.get());
        }
        assertEquals(1, users.countAll());
        assertEquals(1, users.countEnabledAdministrators());
        authentication.login(new LoginRequest("legacy-admin", PASSWORD));
        admin = authentication.currentUser();
        assertEquals("legacy-admin", admin.getLoginId());
        assertTrue(admin.isMustChangePassword());
        String hash = users.selectByUsername("legacy-admin").getPasswordHash();
        properties.getAuth().setPasswordHash(encoder.encode("never-overwrite-existing-password"));
        accounts.initialize();
        assertEquals(hash, users.selectByUsername("legacy-admin").getPasswordHash());
        AuditPageQuery q = new AuditPageQuery();
        q.setActionCode("ACCOUNT_BOOTSTRAP");
        assertEquals(1, audit.query(q).getTotal());
    }

    @Test
    @Order(2)
    void defaultsIdentityCasAndApproverSelection() {
        AccountDTO created = create("safe-viewer", null);
        assertEquals(AccountRole.VIEWER, created.roleCode());
        assertTrue(created.enabled());
        assertTrue(created.mustChangePassword());
        assertTrue(created.loginId().startsWith("user:"));
        assertEquals("safe-viewer", created.displayName());
        AccountProfileRequest profile = new AccountProfileRequest();
        profile.setExpectedRevision(0L);
        profile.setDisplayName("管理员 ✓");
        AccountDTO renamed = accounts.profile(Long.valueOf(created.id()), profile);
        assertEquals(created.loginId(), renamed.loginId());
        assertEquals(created.username(), renamed.username());
        assertThrows(
                BusinessException.class,
                () -> accounts.profile(Long.valueOf(created.id()), profile));
        assertFalse(
                accounts.approvers(1, 100).stream()
                        .anyMatch(a -> a.loginId().equals(created.loginId())));
        assertThrows(BusinessException.class, () -> create("safe-viewer", null));
    }

    @Test
    @Order(3)
    void concurrentDemotionsCannotRemoveLastAdministrator() throws Exception {
        var second = create("second-admin", AccountRole.ADMIN);
        var first = users.selectByUsername("legacy-admin");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results =
                    pool.invokeAll(
                            List.of(
                                    () -> demote(first.getId(), first.getRevision()),
                                    () ->
                                            demote(
                                                    Long.valueOf(second.id()),
                                                    Long.valueOf(second.revision()))));
            int success = 0;
            for (var result : results) if (Boolean.TRUE.equals(result.get())) success++;
            assertEquals(1, success);
            assertEquals(1, users.countEnabledAdministrators());
        }
        var current = users.selectByUsername("legacy-admin");
        AccountAccessRequest restore = new AccountAccessRequest();
        restore.setExpectedRevision(current.getRevision());
        restore.setRoleCode(AccountRole.ADMIN);
        accounts.access(current.getId(), restore, admin);
        AccountAccessRequest disableSelf = new AccountAccessRequest();
        disableSelf.setExpectedRevision(users.selectById(current.getId()).getRevision());
        disableSelf.setEnabled(false);
        assertThrows(
                BusinessException.class,
                () -> accounts.access(current.getId(), disableSelf, admin));
    }

    @Test
    @Order(4)
    void databaseRereadRevokesOldVersionsEvenWhenSharedSessionRevocationFails() {
        AccountDTO user = create("session-operator", AccountRole.OPERATOR);
        authentication.login(new LoginRequest(user.username(), PASSWORD));
        CurrentUserResponse actor = authentication.currentUser();
        assertEquals(AccountRole.OPERATOR, actor.getRoleCode());
        session.failRevocation = true;
        AccountAccessRequest access = new AccountAccessRequest();
        access.setExpectedRevision(0L);
        access.setRoleCode(AccountRole.VIEWER);
        accounts.access(Long.valueOf(user.id()), access, admin);
        assertThrows(BusinessException.class, authentication::currentUser);
        authentication.login(new LoginRequest(user.username(), PASSWORD));
        assertEquals(AccountRole.VIEWER, authentication.currentUser().getRoleCode());
        access.setExpectedRevision(1L);
        access.setEnabled(false);
        accounts.access(Long.valueOf(user.id()), access, admin);
        assertThrows(BusinessException.class, authentication::currentUser);
        assertThrows(
                BusinessException.class,
                () -> authentication.login(new LoginRequest(user.username(), PASSWORD)));
        session.failRevocation = false;
    }

    @Test
    @Order(5)
    void passwordChangeResetAndUnknownCredentials() {
        AccountDTO user = create("password-user", AccountRole.VIEWER);
        authentication.login(new LoginRequest(user.username(), PASSWORD));
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setCurrentPassword(PASSWORD);
        change.setNewPassword("new-completion-password-123");
        accounts.changePassword(change, authentication.currentUser());
        assertThrows(
                BusinessException.class,
                () -> authentication.login(new LoginRequest(user.username(), PASSWORD)));
        authentication.login(new LoginRequest(user.username(), change.getNewPassword()));
        assertFalse(authentication.currentUser().isMustChangePassword());
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setExpectedRevision(1L);
        reset.setNewPassword("reset-completion-password-123");
        accounts.reset(Long.valueOf(user.id()), reset);
        authentication.login(new LoginRequest(user.username(), reset.getNewPassword()));
        assertTrue(authentication.currentUser().isMustChangePassword());
        var unknown =
                assertThrows(
                        BusinessException.class,
                        () -> authentication.login(new LoginRequest("unknown-user", PASSWORD)));
        var invalid =
                assertThrows(
                        BusinessException.class,
                        () -> authentication.login(new LoginRequest(user.username(), "wrong")));
        assertEquals(unknown.getMessage(), invalid.getMessage());
    }

    @Test
    @Order(6)
    void auditQueriesAreBoundedAndRetentionPreservesBoundary() {
        var event = audit.prepare(admin, "RETENTION_TEST", "ACCOUNT", "1", "displayName");
        var cutoff = AuditService.now().minusDays(90);
        event.setOccurredAt(cutoff.minusSeconds(1));
        audit.independent(event, "SUCCESS", null);
        var boundary = audit.prepare(admin, "RETENTION_TEST", "ACCOUNT", "2", "displayName");
        boundary.setOccurredAt(cutoff);
        audit.independent(boundary, "SUCCESS", null);
        assertEquals(1, audit.cleanup(cutoff, 20, true));
        assertEquals(1, audit.cleanup(cutoff, 20, false));
        assertNotNull(audit.get(boundary.getId()));
        assertThrows(BusinessException.class, () -> audit.get(event.getId()));
        AuditPageQuery query = new AuditPageQuery();
        query.setFrom(AuditService.now().minusDays(100));
        assertThrows(BusinessException.class, () -> audit.query(query));
        query.setFrom(null);
        query.setPageSize(501);
        assertThrows(BusinessException.class, () -> audit.query(query));
    }

    @Test
    @Order(7)
    void passwordsNeverEnterDtosOrAudit() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String serialized = mapper.writeValueAsString(accounts.query(1, 100));
        String records = mapper.writeValueAsString(audit.query(new AuditPageQuery()));
        assertFalse(serialized.contains("passwordHash"));
        assertFalse(serialized.contains(PASSWORD));
        assertFalse(records.contains(PASSWORD));
        assertFalse(records.contains("$2a$"));
    }

    @Test
    @Order(8)
    void deletionGuardsRevisionAndRevokesAccessWithoutErasingHistory() {
        AccountDTO user = create("delete-user", AccountRole.VIEWER);
        long id = Long.parseLong(user.id());
        authentication.login(new LoginRequest(user.username(), PASSWORD));
        var actor = authentication.currentUser();
        assertThrows(BusinessException.class, () -> accounts.delete(id, 0L, actor));
        assertThrows(BusinessException.class, () -> accounts.delete(id, 1L, admin));
        assertNotNull(users.selectById(id));
        // Earlier concurrent demotion may leave both administrators after restoration.
        for (var candidate : users.selectPage()) {
            if (candidate.getRoleCode() == AccountRole.ADMIN
                    && Boolean.TRUE.equals(candidate.getEnabled())
                    && users.countEnabledAdministrators() > 1) {
                assertTrue(demote(candidate.getId(), candidate.getRevision()));
            }
        }
        var lastAdmin =
                users.selectPage().stream()
                        .filter(
                                u ->
                                        u.getRoleCode() == AccountRole.ADMIN
                                                && Boolean.TRUE.equals(u.getEnabled()))
                        .findFirst()
                        .orElseThrow();
        assertThrows(
                BusinessException.class,
                () -> accounts.delete(lastAdmin.getId(), lastAdmin.getRevision(), actor));
        var history =
                audit.prepare(actor, "DELETE_HISTORY_TEST", "ACCOUNT", user.id(), "displayName");
        audit.independent(history, "SUCCESS", null);
        session.failRevocation = true;
        try {
            accounts.delete(id, 0L, admin);
            assertEquals(null, users.selectById(id));
            assertThrows(BusinessException.class, () -> authentication.currentUser());
            assertThrows(
                    BusinessException.class,
                    () -> authentication.login(new LoginRequest(user.username(), PASSWORD)));
            assertNotNull(audit.get(history.getId()));
        } finally {
            session.failRevocation = false;
        }
        AccountDTO recreated = create("delete-user", AccountRole.VIEWER);
        assertFalse(user.loginId().equals(recreated.loginId()));
    }

    @Test
    @Order(9)
    void accountFiltersApplyBeforePaginationAndTreatWildcardsLiterally() {
        for (int index = 0; index < 3; index++) {
            AccountCreateRequest request = new AccountCreateRequest();
            request.setUsername("filter-member-" + index);
            request.setPassword(PASSWORD);
            request.setDisplayName(index < 2 ? "Filter_Team%" : "FilterXTeamY");
            request.setRoleCode(index < 2 ? AccountRole.VIEWER : AccountRole.OPERATOR);
            request.setEnabled(index != 2);
            accounts.create(request, admin);
        }
        var first = accounts.query(1, 1, "filter_team%", AccountRole.VIEWER, true);
        var second = accounts.query(2, 1, "filter_team%", AccountRole.VIEWER, true);
        assertEquals(2, first.getTotal());
        assertEquals(1, first.getList().size());
        assertFalse(first.getList().getFirst().id().equals(second.getList().getFirst().id()));
        assertEquals(
                1, accounts.query(1, 20, "filter-member", AccountRole.OPERATOR, false).getTotal());
        assertEquals(
                0, accounts.query(1, 20, "filter_team%", AccountRole.OPERATOR, null).getTotal());
        assertThrows(
                BusinessException.class, () -> accounts.query(1, 20, "a".repeat(129), null, null));
    }

    @Test
    @Order(10)
    void auditTimeSortIsStableAcrossPagesAndRejectsUnknownFieldsAndDirections() {
        var time = AuditService.now().minusHours(1);
        var expected = new java.util.ArrayList<String>();
        // IDs and timestamps deliberately have different order, with ties across page boundaries.
        for (int offset : new int[] {2, 0, 1, 0}) {
            var event = audit.prepare(admin, "SORT_TEST", "ACCOUNT", "sort-target", "displayName");
            event.setOccurredAt(time.plusSeconds(offset));
            audit.independent(event, "SUCCESS", null);
            expected.add(event.getId().toString());
        }
        var ascending = List.of(expected.get(1), expected.get(3), expected.get(2), expected.get(0));
        AuditPageQuery query = new AuditPageQuery();
        query.setFrom(time.minusSeconds(1));
        query.setTo(time.plusSeconds(3));
        query.setActionCode("SORT_TEST");
        query.setActorLoginId(admin.getLoginId());
        query.setTargetType("ACCOUNT");
        query.setTargetId("sort-target");
        query.setResultCode("SUCCESS");
        query.setPageSize(1);
        for (String direction : List.of("asc", "desc")) {
            query.setSortDirection(direction);
            for (int page = 1; page <= 4; page++) {
                query.setPageNum(page);
                var result = audit.query(query);
                assertEquals(4, result.getTotal());
                assertEquals(1, result.getList().size());
                assertEquals(
                        ascending.get("asc".equals(direction) ? page - 1 : 4 - page),
                        result.getList().getFirst().id());
            }
        }
        query.setPageNum(1);
        query.setSortDirection("desc");
        query.setTargetId("no-match");
        assertEquals(0, audit.query(query).getTotal());
        query.setTargetId("sort-target");
        for (String field : List.of("id", "actorName", "occurredAt DESC", "")) {
            query.setSortBy(field);
            assertThrows(BusinessException.class, () -> audit.query(query));
        }
        query.setSortBy("occurredAt");
        for (String direction : List.of("ASC", "desc,id", "")) {
            query.setSortDirection(direction);
            assertThrows(BusinessException.class, () -> audit.query(query));
        }
        query.setSortDirection(null);
        assertThrows(BusinessException.class, () -> audit.query(query));
        query.setSortDirection("desc");
        query.setSortBy(null);
        assertThrows(BusinessException.class, () -> audit.query(query));
    }

    private AccountDTO create(String username, AccountRole role) {
        AccountCreateRequest request = new AccountCreateRequest();
        request.setUsername(username);
        request.setPassword(PASSWORD);
        if (role != null) request.setRoleCode(role);
        return accounts.create(request, admin);
    }

    private boolean demote(Long id, Long revision) {
        AccountAccessRequest request = new AccountAccessRequest();
        request.setExpectedRevision(revision);
        request.setRoleCode(AccountRole.OPERATOR);
        try {
            accounts.access(id, request, admin);
            return true;
        } catch (BusinessException expected) {
            return false;
        }
    }

    private static class FakeSession implements AuthSession {
        private String principal;
        private String username;
        private Long version;
        volatile boolean failRevocation;

        public LoginResponse login(String username) {
            throw new UnsupportedOperationException();
        }

        public LoginResponse loginAccount(String principal, String username, long version) {
            this.principal = principal;
            this.username = username;
            this.version = version;
            return new LoginResponse("ijob-token", "synthetic-test-token");
        }

        public CurrentUserResponse currentUser() {
            return new CurrentUserResponse(principal, username);
        }

        public Long securityVersion() {
            return version;
        }

        public void logout() {
            principal = null;
            version = null;
        }

        public void revoke(String loginId) {
            if (failRevocation) throw new IllegalStateException("Injected revocation outage");
        }
    }
}
