package com.ifielsc.ijob.core.auth;

import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.auth.AccountRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable coarse permissions. Object/state constraints must still be checked by services. */
public final class AccountRolePolicy {
    private static final Set<AccountPermission> VIEWER =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            AccountPermission.SUMMARY_READ,
                            AccountPermission.DEFINITION_READ,
                            AccountPermission.SELF_PASSWORD_CHANGE));
    private static final Set<AccountPermission> OPERATOR =
            Collections.unmodifiableSet(
                    EnumSet.of(
                            AccountPermission.SUMMARY_READ,
                            AccountPermission.DEFINITION_READ,
                            AccountPermission.SELF_PASSWORD_CHANGE,
                            AccountPermission.EXECUTION_CONTROL,
                            AccountPermission.APPROVAL_DECIDE));
    private static final Set<AccountPermission> ADMIN =
            Collections.unmodifiableSet(EnumSet.allOf(AccountPermission.class));

    private AccountRolePolicy() {}

    public static Set<AccountPermission> permissions(AccountRole role) {
        if (role == null) {
            return Set.of();
        }
        return switch (role) {
            case ADMIN -> ADMIN;
            case OPERATOR -> OPERATOR;
            case VIEWER -> VIEWER;
        };
    }

    public static boolean allows(AccountRole role, AccountPermission permission) {
        return permission != null && permissions(role).contains(permission);
    }
}
