package com.ifielsc.ijob.core.notify.template;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.InstanceStatus;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Background notifications use an explicit locale, never the ambient HTTP or machine locale. */
@Component
public class NotificationMessages {
    private final ResourceBundleMessageSource source;
    private final Locale locale;
    private final ZoneId businessZone;

    public NotificationMessages(AppProperties properties) {
        businessZone = ZoneId.of(properties.getSchedule().getZoneId());
        AppProperties.NotificationLocale configured =
                Objects.requireNonNull(
                        properties.getNotification().getLocale(),
                        "app.notification.locale is required");
        locale =
                configured == AppProperties.NotificationLocale.ZH ? Locale.CHINESE : Locale.ENGLISH;
        source = new ResourceBundleMessageSource();
        source.setBasenames("notifications", "messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
    }

    public String text(String key, Object... arguments) {
        return source.getMessage(key, arguments, locale);
    }

    public ZoneId businessZone() {
        return businessZone;
    }

    public String status(InstanceStatus status) {
        return status == null ? text("status.unknown") : text("status." + status.name());
    }

    public String render(String key, Object[] arguments, String fallback) {
        return source.getMessage(key, arguments, fallback == null ? "" : fallback, locale);
    }
}
