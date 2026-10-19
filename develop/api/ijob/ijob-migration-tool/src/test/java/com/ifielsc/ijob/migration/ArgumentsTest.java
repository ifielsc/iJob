// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArgumentsTest {

    @Test
    void releaseCommandsRequireTheirOwnAcknowledgements() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Arguments.parse(
                                new String[] {
                                    "release-import",
                                    "--config=db",
                                    "--bundle=data",
                                    "--ack-empty-target=IJOB_V27_EMPTY_TARGET"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Arguments.parse(
                                new String[] {
                                    "release-prepare", "--config=db", "--ack-offline=IJOB_STOPPED"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> Arguments.parse(new String[] {"prepare", "--config=db"}));
        var prepare =
                Arguments.parse(
                        new String[] {
                            "release-prepare",
                            "--config=db",
                            "--ack-offline=IJOB_STOPPED",
                            "--ack-replica=IJOB_ISOLATED_REPLICA"
                        });
        assertTrue(prepare.release());
        assertTrue(prepare.replica());
        assertEquals(Arguments.Command.PREPARE, prepare.command());
        var init =
                Arguments.parse(
                        new String[] {
                            "release-init",
                            "--config=db",
                            "--ack-empty-target=IJOB_RELEASE_V1_EMPTY_TARGET"
                        });
        assertEquals(Arguments.Command.INIT, init.command());
    }

    @Test
    void parsesReadOnlyCommandsWithoutWriteAcknowledgement() {
        Arguments preflight = Arguments.parse(new String[] {"preflight", "--config=db.properties"});
        Arguments verify =
                Arguments.parse(
                        new String[] {"verify", "--config=db.properties", "--bundle=export"});

        assertEquals(Arguments.Command.PREFLIGHT, preflight.command());
        assertEquals(Arguments.Command.VERIFY, verify.command());
    }

    @Test
    void exportRequiresExactOfflineAcknowledgement() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                Arguments.parse(
                                        new String[] {
                                            "export",
                                            "--config=db.properties",
                                            "--bundle=export",
                                            "--ack-offline=yes"
                                        }));

        assertTrue(exception.getMessage().contains("IJOB_STOPPED"));
    }

    @Test
    void importRequiresExactEmptyTargetAcknowledgement() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                Arguments.parse(
                                        new String[] {
                                            "import", "--config=db.properties", "--bundle=export"
                                        }));

        assertTrue(exception.getMessage().contains("IJOB_V27_EMPTY_TARGET"));
    }

    @Test
    void rejectsUnknownAndDuplicateOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Arguments.parse(new String[] {"preflight", "--config=a", "--secret=x"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> Arguments.parse(new String[] {"preflight", "--config=a", "--config=b"}));
    }
}
