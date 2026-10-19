package com.ifielsc.ijob.api.runtime;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.contract.runtime.InstanceDTO;
import com.ifielsc.ijob.contract.runtime.TriggerDTO;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Adds presentation text without rewriting historical diagnostic messages. */
@Component
public class RuntimeMessageLocalizer {
    private final MessageSource messages;

    public RuntimeMessageLocalizer(MessageSource messages) {
        this.messages = messages;
    }

    public InstanceDTO localize(InstanceDTO value) {
        if (value != null) {
            value.setDisplayMessage(
                    render(value.getMessageKey(), value.getMessageArgs(), value.getMessage()));
        }
        return value;
    }

    public TriggerDTO localize(TriggerDTO value) {
        if (value != null) {
            value.setDisplayMessage(
                    render(value.getMessageKey(), value.getMessageArgs(), value.getMessage()));
        }
        return value;
    }

    private String render(String key, String jsonArgs, String original) {
        if (key == null || key.isBlank()) {
            return original;
        }
        // Generic status metadata cannot faithfully translate a legacy diagnostic.
        if (key.startsWith("runtime.execution.") && original != null && !original.isBlank()) {
            return original;
        }
        try {
            Object[] args =
                    jsonArgs == null ? new Object[0] : JSONUtil.parseArray(jsonArgs).toArray();
            return messages.getMessage(key, args, original, LocaleContextHolder.getLocale());
        } catch (IllegalArgumentException exception) {
            return original;
        } catch (cn.hutool.json.JSONException exception) {
            return original;
        }
    }
}
