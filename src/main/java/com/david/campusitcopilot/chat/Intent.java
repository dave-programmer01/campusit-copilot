package com.david.campusitcopilot.chat;

import java.util.Locale;

public record Intent(Topic topic, String subtopic, String account) {

    public enum Topic {
        WIFI,
        LOGIN,
        MFA,
        GREETING,
        UNKNOWN;

        public static Topic fromString(String val) {
            if (val == null) {
                return UNKNOWN;
            }
            return switch (val.trim().toLowerCase(Locale.ROOT)) {
                case "wifi", "wi-fi", "wireless" -> WIFI;
                case "login", "password" -> LOGIN;
                case "mfa", "authenticator", "2fa", "two-factor", "verification code", "verify" -> MFA;
                case "greeting", "greet", "hello", "hi" -> GREETING;
                default -> UNKNOWN;
            };
        }
    }

    public Intent(Topic topic, String subtopic) {
        this(topic, subtopic, null);
    }

    public static Intent wifi() {
        return new Intent(Topic.WIFI, null, null);
    }

    public static Intent login(String subtopic) {
        return new Intent(Topic.LOGIN, subtopic, "lehman");
    }

    public static Intent login(String subtopic, String account) {
        return new Intent(Topic.LOGIN, subtopic, account);
    }

    public static Intent mfa(String account) {
        return new Intent(Topic.MFA, null, account);
    }

    public static Intent greeting() {
        return new Intent(Topic.GREETING, null, null);
    }

    public static Intent unknown() {
        return new Intent(Topic.UNKNOWN, null, null);
    }

    public boolean isWifi() {
        return topic == Topic.WIFI;
    }

    public boolean isLogin() {
        return topic == Topic.LOGIN;
    }

    public boolean isMfa() {
        return topic == Topic.MFA;
    }

    public boolean isGreeting() {
        return topic == Topic.GREETING;
    }

    public boolean isUnknown() {
        return topic == Topic.UNKNOWN;
    }
}
