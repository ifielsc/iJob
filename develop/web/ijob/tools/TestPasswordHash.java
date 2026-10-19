// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import org.springframework.security.crypto.bcrypt.BCrypt;

// Integration fixture only. Password enters through the environment, never command arguments.
class TestPasswordHash {
    public static void main(String[] args) {
        String value = System.getenv("IJOB_TEST_PASSWORD");
        if (value == null || value.length() < 12) throw new IllegalArgumentException("Missing test password");
        System.out.print(BCrypt.hashpw(value, BCrypt.gensalt(4)));
    }
}
