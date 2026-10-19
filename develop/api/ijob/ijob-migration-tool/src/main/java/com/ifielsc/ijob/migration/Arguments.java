// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.migration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

record Arguments(
        Command command,
        Path config,
        Path bundle,
        boolean offlineAcknowledged,
        boolean emptyTargetAcknowledged,
        boolean release,
        boolean replica) {

    enum Command {
        PREFLIGHT,
        EXPORT,
        IMPORT,
        VERIFY,
        INIT,
        PREPARE
    }

    static Arguments parse(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(usage());
        }
        boolean release = args[0].startsWith("release-");
        Command command;
        try {
            command =
                    Command.valueOf(
                            (release ? args[0].substring(8) : args[0])
                                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown command: " + args[0] + System.lineSeparator() + usage());
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String argument = args[i];
            int equals = argument.indexOf('=');
            if (!argument.startsWith("--") || equals < 3) {
                throw new IllegalArgumentException("Expected --key=value, got: " + argument);
            }
            String previous =
                    values.put(argument.substring(2, equals), argument.substring(equals + 1));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate option: " + argument.substring(2, equals));
            }
        }
        rejectUnknown(values);
        if (!release && (command == Command.INIT || command == Command.PREPARE))
            throw new IllegalArgumentException("Schema preparation requires a release command");
        Path config = Path.of(required(values, "config")).toAbsolutePath().normalize();
        Path bundle =
                command == Command.PREFLIGHT
                                || command == Command.INIT
                                || command == Command.PREPARE
                        ? null
                        : Path.of(required(values, "bundle")).toAbsolutePath().normalize();
        boolean offline = "IJOB_STOPPED".equals(values.get("ack-offline"));
        boolean empty =
                (release ? "IJOB_RELEASE_V1_EMPTY_TARGET" : "IJOB_V27_EMPTY_TARGET")
                        .equals(values.get("ack-empty-target"));
        boolean replica = "IJOB_ISOLATED_REPLICA".equals(values.get("ack-replica"));
        if (values.containsKey("ack-replica")
                && (!release
                        || (command != Command.EXPORT && command != Command.PREPARE)
                        || !replica))
            throw new IllegalArgumentException(
                    "Replica acknowledgement is only valid for release-export/prepare");
        if (command == Command.PREPARE && !replica)
            throw new IllegalArgumentException(
                    "release-prepare requires --ack-replica=IJOB_ISOLATED_REPLICA");
        if ((command == Command.EXPORT || command == Command.PREPARE) && !offline) {
            throw new IllegalArgumentException("export requires --ack-offline=IJOB_STOPPED");
        }
        if ((command == Command.IMPORT || command == Command.INIT) && !empty) {
            throw new IllegalArgumentException(
                    "import requires --ack-empty-target="
                            + (release ? "IJOB_RELEASE_V1_EMPTY_TARGET" : "IJOB_V27_EMPTY_TARGET"));
        }
        return new Arguments(command, config, bundle, offline, empty, release, replica);
    }

    private static void rejectUnknown(Map<String, String> values) {
        for (String key : values.keySet()) {
            if (!java.util.Set.of(
                            "config", "bundle", "ack-offline", "ack-empty-target", "ack-replica")
                    .contains(key)) {
                throw new IllegalArgumentException("Unknown option: --" + key);
            }
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing --" + key + " option");
        }
        return value;
    }

    static String usage() {
        return "Release conversion: prefix commands with release-; export requires frozen V38"
                + " history; import requires --ack-empty-target=IJOB_RELEASE_V1_EMPTY_TARGET and"
                + " leaves startup blocked for review.\n"
                + "  release-init --config=<properties>"
                + " --ack-empty-target=IJOB_RELEASE_V1_EMPTY_TARGET\n"
                + "  release-prepare --config=<isolated-replica-properties>"
                + " --ack-offline=IJOB_STOPPED --ack-replica=IJOB_ISOLATED_REPLICA (V35 to V38"
                + " only)\n"
                + "Usage:\n"
                + "  preflight --config=<properties>\n"
                + "  export --config=<properties> --bundle=<new-directory>"
                + " --ack-offline=IJOB_STOPPED\n"
                + "  import --config=<properties> --bundle=<directory>"
                + " --ack-empty-target=IJOB_V27_EMPTY_TARGET\n"
                + "  verify --config=<properties> --bundle=<directory>";
    }
}
