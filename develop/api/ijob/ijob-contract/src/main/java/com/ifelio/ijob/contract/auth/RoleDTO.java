package com.ifelio.ijob.contract.auth;

import java.util.Set;

public record RoleDTO(AccountRole roleCode, Set<AccountPermission> permissions) {}
