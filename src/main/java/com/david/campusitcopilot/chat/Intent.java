package com.david.campusitcopilot.chat;

import java.util.Locale;

public record Intent(Topic topic, String subtopic, String account, Action action, String diagnosticAnswer) {

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

    public enum Action {
        CONTINUE,
        SWITCH,
        ASIDE,
        DIAGNOSTIC_ANSWER;

        public static Action fromString(String val) {
            if (val == null) {
                return CONTINUE;
            }
            return switch (val.trim().toUpperCase(Locale.ROOT)) {
                case "SWITCH" -> SWITCH;
                case "ASIDE" -> ASIDE;
                case "DIAGNOSTIC_ANSWER", "DIAGNOSTIC" -> DIAGNOSTIC_ANSWER;
                default -> CONTINUE;
            };
        }
    }

    public Intent(Topic topic, String subtopic) {
        this(topic, subtopic, null, Action.CONTINUE, null);
    }

    public Intent(Topic topic, String subtopic, String account) {
        this(topic, subtopic, account, Action.CONTINUE, null);
    }

    public static Intent wifi() {
        return new Intent(Topic.WIFI, null, null, Action.CONTINUE, null);
    }

    public static Intent login(String subtopic) {
        return new Intent(Topic.LOGIN, subtopic, "lehman", Action.CONTINUE, null);
    }

    public static Intent login(String subtopic, String account) {
        return new Intent(Topic.LOGIN, subtopic, account, Action.CONTINUE, null);
    }

    public static Intent mfa(String account) {
        return new Intent(Topic.MFA, null, account, Action.CONTINUE, null);
    }

    public static Intent greeting() {
        return new Intent(Topic.GREETING, null, null, Action.CONTINUE, null);
    }

    public static Intent unknown() {
        return new Intent(Topic.UNKNOWN, null, null, Action.CONTINUE, null);
    }

    public static Intent flowContinue(Topic topic, String subtopic, String account) {
        return new Intent(topic, subtopic, account, Action.CONTINUE, null);
    }

    public static Intent flowSwitch(Topic topic, String subtopic, String account) {
        return new Intent(topic, subtopic, account, Action.SWITCH, null);
    }

    public static Intent flowAside() {
        return new Intent(null, null, null, Action.ASIDE, null);
    }

    public static Intent diagnosticAnswer(String diagnosticAnswer) {
        return new Intent(Topic.LOGIN, null, "lehman", Action.DIAGNOSTIC_ANSWER, diagnosticAnswer);
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

    public boolean isContinue() {
        return action == Action.CONTINUE;
    }

    public boolean isSwitch() {
        return action == Action.SWITCH;
    }

    public boolean isAside() {
        return action == Action.ASIDE;
    }

    public boolean isDiagnosticAnswer() {
        return action == Action.DIAGNOSTIC_ANSWER;
    }
}
