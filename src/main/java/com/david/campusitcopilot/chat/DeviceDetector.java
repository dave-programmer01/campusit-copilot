package com.david.campusitcopilot.chat;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Utility for recognizing device names from user input or conversation text.
 */
public final class DeviceDetector {

    private DeviceDetector() {
    }

    public static String detect(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("windows 11") || lower.contains("windows-11") || lower.contains("windows11")
                || lower.contains("win 11") || lower.contains("win11")) {
            return "windows-11";
        }
        if (lower.contains("windows 10") || lower.contains("windows-10") || lower.contains("windows10")
                || lower.contains("win 10") || lower.contains("win10")) {
            return "windows-10";
        }
        if (lower.contains("macbook") || lower.contains("macos") || lower.contains("osx") || lower.contains("mac") || lower.contains("apple laptop")) {
            return "macbook";
        }
        if (lower.contains("iphone") || lower.contains("ios") || lower.contains("ipad") || lower.contains("apple phone")) {
            return "iphone";
        }
        if (lower.contains("android") || lower.contains("samsung") || lower.contains("pixel") || lower.contains("galaxy")) {
            return "android";
        }
        if (lower.contains("windows") || lower.contains("pc")) {
            return "windows-11";
        }
        return null;
    }
}
