// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

public final class SqliteLockHolderMain {
    private SqliteLockHolderMain() {}

    public static void main(String[] args) throws Exception {
        SqliteSingletonLockStrategy lock = new SqliteSingletonLockStrategy(args[0]);
        lock.acquire("ijob:standalone");
        System.out.println("LOCKED");
        System.out.flush();
        System.in.read();
        lock.release("ijob:standalone");
    }
}
