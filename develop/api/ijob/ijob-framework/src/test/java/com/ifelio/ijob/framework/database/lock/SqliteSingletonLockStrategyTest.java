package com.ifelio.ijob.framework.database.lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class SqliteSingletonLockStrategyTest {
    @TempDir Path temporaryDirectory;

    @Test
    void secondOwnerShouldFailUntilFirstOwnerReleasesTheSidecar() {
        String url = "jdbc:sqlite:" + temporaryDirectory.resolve("platform.db");
        SqliteSingletonLockStrategy first = new SqliteSingletonLockStrategy(url);
        SqliteSingletonLockStrategy second = new SqliteSingletonLockStrategy(url);

        first.acquire("ijob:standalone");
        assertTrue(first.isHeld("ijob:standalone", 1));
        assertTrue(java.nio.file.Files.exists(first.lockPath()));
        assertThrows(IllegalStateException.class, () -> second.acquire("ijob:standalone"));

        first.release("ijob:standalone");
        assertFalse(first.isHeld("ijob:standalone", 1));
        second.acquire("ijob:standalone");
        assertTrue(second.isHeld("ijob:standalone", 1));
        second.release("ijob:standalone");
    }

    @Test
    @Timeout(20)
    void secondJvmShouldBeRejectedByTheOperatingSystemFileLock() throws Exception {
        String url = "jdbc:sqlite:" + temporaryDirectory.resolve("cross-process.db");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        Process process =
                new ProcessBuilder(
                                java, "-cp", classpath, SqliteLockHolderMain.class.getName(), url)
                        .redirectErrorStream(true)
                        .start();
        try {
            String ready =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            return process.inputReader().readLine();
                                        } catch (java.io.IOException exception) {
                                            throw new IllegalStateException(exception);
                                        }
                                    })
                            .get(10, TimeUnit.SECONDS);
            assertTrue("LOCKED".equals(ready), () -> "Unexpected helper output: " + ready);

            SqliteSingletonLockStrategy contender = new SqliteSingletonLockStrategy(url);
            assertThrows(IllegalStateException.class, () -> contender.acquire("ijob:standalone"));

            process.outputWriter().write("release\n");
            process.outputWriter().flush();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertTrue(process.exitValue() == 0);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
