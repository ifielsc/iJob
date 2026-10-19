package com.ifielsc.ijob.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class JobNotifyBindingCleanupTest {
    @Test
    void activeFacadeShouldExposeVersionedCrudOnly() {
        assertNotNull(JobService.class.getAnnotation(Service.class));
        Set<String> methodNames =
                Arrays.stream(JobService.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(Method::getName)
                        .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "queryPage",
                        "getDetail",
                        "getVersionDetail",
                        "create",
                        "update",
                        "delete",
                        "deleteBatch"),
                methodNames);
    }
}
