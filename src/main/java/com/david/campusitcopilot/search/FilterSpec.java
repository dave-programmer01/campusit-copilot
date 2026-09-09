package com.david.campusitcopilot.search;

import org.springframework.util.StringUtils;

/**
 * Filter specification for document retrieval.
 * Holds optional criteria: topic (e.g. "wifi", "login"), device (e.g. "macbook", "windows-11"),
 * and subtopic (e.g. "activation", "reset").
 */
public record FilterSpec(String topic, String device, String subtopic) {

    public FilterSpec {
        topic = StringUtils.hasText(topic) ? topic.trim() : null;
        device = StringUtils.hasText(device) ? device.trim() : null;
        subtopic = StringUtils.hasText(subtopic) ? subtopic.trim() : null;
    }

    public static FilterSpec wifi(String device) {
        return new FilterSpec("wifi", device, null);
    }

    public static FilterSpec login(String subtopic) {
        return new FilterSpec("login", null, subtopic);
    }

    public static FilterSpec of(String topic, String device, String subtopic) {
        return new FilterSpec(topic, device, subtopic);
    }
}
