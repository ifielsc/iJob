package com.ifielsc.ijob.contract.monitor.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessMatchPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"1", "2147483647", "001"})
    void acceptsExistingPidContract(String value) {
        assertDoesNotThrow(() -> ProcessMatchPolicy.validate(ProcessMatchType.PID, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2147483648", " 1", "1 ", "1.0", "pid"})
    void rejectsInvalidPids(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMatchPolicy.validate(ProcessMatchType.PID, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[", "(a+)+", "(?:a|aa)+", "(?=a)", "(?i)java", "(a)\\1"})
    void rejectsExistingUnsupportedRegexStructures(String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMatchPolicy.validate(ProcessMatchType.REGEX, value));
    }

    @Test
    void retainsRegexLengthAndSupportedSyntax() {
        for (String value : new String[] {"java.*", "[a-z]+", "(?:ab)+", "a".repeat(256)}) {
            assertDoesNotThrow(() -> ProcessMatchPolicy.validate(ProcessMatchType.REGEX, value));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ProcessMatchPolicy.validate(ProcessMatchType.REGEX, "a".repeat(257)));
    }
}
