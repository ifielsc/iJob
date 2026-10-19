// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.auth;

import java.util.Set;

public record RoleDTO(AccountRole roleCode, Set<AccountPermission> permissions) {}
