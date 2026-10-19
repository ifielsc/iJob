package com.ifielsc.ijob.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.auth.AccountCreateRequest;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.auth.AccountRole;

import org.junit.jupiter.api.Test;

import java.util.Set;

class AccountRolePolicyTest {
    @Test
    void viewerCanOnlyReadSafeDefinitionsAndChangeOwnPassword() {
        assertEquals(
                Set.of(
                        AccountPermission.SUMMARY_READ,
                        AccountPermission.DEFINITION_READ,
                        AccountPermission.SELF_PASSWORD_CHANGE),
                AccountRolePolicy.permissions(AccountRole.VIEWER));
    }

    @Test
    void operatorCannotEditDefinitionsOrManageUsersOrPublishEvents() {
        for (AccountPermission denied :
                Set.of(
                        AccountPermission.CONFIGURATION_WRITE,
                        AccountPermission.SENSITIVE_READ,
                        AccountPermission.USER_MANAGE,
                        AccountPermission.AUDIT_READ,
                        AccountPermission.EVENT_PUBLISH)) {
            assertFalse(AccountRolePolicy.allows(AccountRole.OPERATOR, denied));
        }
        assertTrue(
                AccountRolePolicy.allows(
                        AccountRole.OPERATOR, AccountPermission.EXECUTION_CONTROL));
        assertTrue(
                AccountRolePolicy.allows(AccountRole.OPERATOR, AccountPermission.APPROVAL_DECIDE));
    }

    @Test
    void administratorHasAllExplicitlyDefinedPermissions() {
        assertEquals(
                AccountPermission.values().length,
                AccountRolePolicy.permissions(AccountRole.ADMIN).size());
    }

    @Test
    void missingRoleOrPermissionNeverGrantsAccess() {
        assertTrue(AccountRolePolicy.permissions(null).isEmpty());
        assertFalse(AccountRolePolicy.allows(null, AccountPermission.SUMMARY_READ));
        assertFalse(AccountRolePolicy.allows(AccountRole.ADMIN, null));
    }

    @Test
    void callersCannotModifySharedRolePolicy() {
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        AccountRolePolicy.permissions(AccountRole.VIEWER)
                                .add(AccountPermission.USER_MANAGE));
    }

    @Test
    void newAccountDefaultsAreSafeAndPasswordDoesNotLeakThroughToString() {
        AccountCreateRequest request = new AccountCreateRequest();
        assertEquals(AccountRole.VIEWER, request.getRoleCode());
        assertTrue(request.getEnabled());
        request.setPassword("test-only-secret");
        assertFalse(request.toString().contains("test-only-secret"));
    }
}
