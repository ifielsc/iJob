package com.ifelio.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class ProcessResourceTest {
    @Test
    void interruptedCleanupShouldAwaitTerminationAndRestoreInterrupt() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process =
                new ProcessBuilder(
                                java,
                                "-cp",
                                System.getProperty("java.class.path"),
                                Sleeper.class.getName())
                        .start();
        try {
            assertEquals(
                    "READY",
                    new BufferedReader(new InputStreamReader(process.getInputStream())).readLine());
            Thread.currentThread().interrupt();
            new ProcessResource(process).close();
            assertFalse(process.isAlive());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    public static class Sleeper {
        public static void main(String[] args) throws InterruptedException {
            System.out.println("READY");
            Thread.sleep(30000);
        }
    }
}
