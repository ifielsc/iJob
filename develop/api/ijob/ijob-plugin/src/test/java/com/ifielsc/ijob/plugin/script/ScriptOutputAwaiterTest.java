package com.ifielsc.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class ScriptOutputAwaiterTest {
    @Test
    void completedOutputShouldBeReturned() throws Exception {
        String output =
                ScriptOutputAwaiter.await(
                        CompletableFuture.completedFuture("accepted"), 1, TimeUnit.SECONDS);

        assertEquals("accepted", output);
    }

    @Test
    void tailWaitShouldUseDedicatedFailureInsteadOfExecutionTimeout() throws Exception {
        CompletableFuture<String> pending = new CompletableFuture<>();

        ScriptOutputCollectionException exception =
                assertThrows(
                        ScriptOutputCollectionException.class,
                        () -> ScriptOutputAwaiter.await(pending, 1, TimeUnit.MILLISECONDS));

        assertEquals("Script output collection did not finish", exception.getMessage());
    }
}
