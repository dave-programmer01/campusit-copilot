package com.david.campusitcopilot.chat;

import java.util.Locale;

public record Intent(Topic topic, String subtopic) {

    public enum Topic {
        WIFI,
        LOGIN,
        GREETING,
        UNKNOWN;

        public static Topic fromString(String val) {
            if (val == null) {
                return UNKNOWN;
            }
            return switch (val.trim().toLowerCase(Locale.ROOT)) {
                case "wifi", "wi-fi", "wireless" -> WIFI;
                case "login", "account", "password" -> LOGIN;
                case "greeting", "greet", "hello", "hi" -> GREETING;
                default -> UNKNOWN;
            };
        }
    }

    public static Intent wifi() {
        return new Intent(Topic.WIFI, null);
    }

    public static Intent login(String subtopic) {
        return new Intent(Topic.LOGIN, subtopic);
    }

    public static Intent greeting() {
        return new Intent(Topic.GREETING, null);
    }

    public static Intent unknown() {
        return new Intent(Topic.UNKNOWN, null);
    }

    public boolean isWifi() {
        return topic == Topic.WIFI;
    }

    public boolean isLogin() {
        return topic == Topic.LOGIN;
    }

    public boolean isGreeting() {
        return topic == Topic.GREETING;
    }

    public boolean isUnknown() {
        return topic == Topic.UNKNOWN;
    }
}
