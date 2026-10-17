package com.ifelio.ijob.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.TriggerDTO;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

class RuntimeMessageLocalizerTest {
    @Test
    void rendersBothLanguagesWithoutMutatingStoredDiagnostic() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        var localizer = new RuntimeMessageLocalizer(source);
        var dto = new InstanceDTO();
        dto.setMessage("api invoke failed status:503");
        dto.setMessageKey("result.plugin.api-status");
        dto.setMessageArgs("[503]");
        Locale previous = LocaleContextHolder.getLocale();
        try {
            LocaleContextHolder.setLocale(Locale.CHINESE);
            localizer.localize(dto);
            assertEquals("API 调用失败，状态码：503", dto.getDisplayMessage());
            assertEquals("api invoke failed status:503", dto.getMessage());
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            localizer.localize(dto);
            assertEquals(dto.getMessage(), dto.getDisplayMessage());
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    @Test
    void historicalOrCorruptMetadataFallsBackSafely() {
        var localizer =
                new RuntimeMessageLocalizer(
                        new org.springframework.context.support.StaticMessageSource());
        var dto = new InstanceDTO();
        dto.setMessage("original diagnostic");
        dto.setMessageKey("obsolete.key");
        dto.setMessageArgs("invalid JSON");
        assertEquals(dto.getMessage(), localizer.localize(dto).getDisplayMessage());
        dto.setMessageArgs("[]");
        assertEquals(dto.getMessage(), localizer.localize(dto).getDisplayMessage());
    }

    @Test
    void systemReasonsTranslateInstancesAndTriggersWithoutGuessingHistoricalText() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        var localizer = new RuntimeMessageLocalizer(source);
        String[][] cases = {
            {"task-timeout", "Task timeout", "任务执行超时"},
            {"job-timeout", "Job timeout", "作业执行超时"},
            {"dag-failed", "Job DAG contains failed or timed out node", "作业中存在失败或超时的节点"}
        };
        Locale previous = LocaleContextHolder.getLocale();
        try {
            for (String[] item : cases) {
                var instance = new InstanceDTO();
                instance.setMessage(item[1]);
                instance.setMessageKey("runtime.reason." + item[0]);
                instance.setMessageArgs("[]");
                var trigger = new TriggerDTO();
                trigger.setMessage(item[1]);
                trigger.setMessageKey(instance.getMessageKey());
                trigger.setMessageArgs("[]");
                LocaleContextHolder.setLocale(Locale.CHINESE);
                assertEquals(item[2], localizer.localize(instance).getDisplayMessage());
                assertEquals(item[2], localizer.localize(trigger).getDisplayMessage());
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                assertEquals(item[1], localizer.localize(instance).getDisplayMessage());
                assertEquals(item[1], localizer.localize(trigger).getDisplayMessage());
                assertEquals(item[1], instance.getMessage());
                assertEquals(item[1], trigger.getMessage());
                LocaleContextHolder.setLocale(Locale.CHINESE);
                instance.setMessageKey("runtime.execution.failed");
                assertEquals(item[1], localizer.localize(instance).getDisplayMessage());
                trigger.setMessageArgs("invalid JSON");
                assertEquals(item[1], localizer.localize(trigger).getDisplayMessage());
            }
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }
}
