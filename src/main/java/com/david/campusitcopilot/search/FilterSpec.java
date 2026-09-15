package com.david.campusitcopilot.search;

import org.springframework.util.StringUtils;

/**
 * Filter specification for document retrieval.
 * Holds optional criteria: topic (e.g. "wifi", "login", "mfa"), device (e.g. "macbook", "windows-11"),
 * subtopic (e.g. "activation", "reset"), and account (e.g. "cuny", "microsoft365", "lehman").
 */
public record FilterSpec(String topic, String device, String subtopic, String account) {

    public FilterSpec {
        topic = StringUtils.hasText(topic) ? topic.trim() : null;
        device = StringUtils.hasText(device) ? device.trim() : null;
        subtopic = StringUtils.hasText(subtopic) ? subtopic.trim() : null;
        account = StringUtils.hasText(account) ? account.trim() : null;
    }

    public FilterSpec(String topic, String device, String subtopic) {
        this(topic, device, subtopic, null);
    }

    public static FilterSpec wifi(String device) {
        return new FilterSpec("wifi", device, null, null);
    }

    public static FilterSpec login(String subtopic) {
        return new FilterSpec("login", null, subtopic, "lehman");
    }

    public static FilterSpec login(String subtopic, String account) {
        return new FilterSpec("login", null, subtopic, account != null ? account : "lehman");
    }

    public static FilterSpec mfa(String account) {
        return new FilterSpec("mfa", null, null, account);
    }

    public static FilterSpec of(String topic, String device, String subtopic) {
        return new FilterSpec(topic, device, subtopic, null);
    }

    public static FilterSpec of(String topic, String device, String subtopic, String account) {
        return new FilterSpec(topic, device, subtopic, account);
    }
}
