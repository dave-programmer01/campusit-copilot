package com.david.campusitcopilot.chat;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Utility for recognizing system/account names from user input or conversation text.
 * Classifies mentions into "cuny", "microsoft365", or "lehman".
 */
public final class AccountDetector {

    private AccountDetector() {
    }

    public static String detect(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT).trim();

        // 1. Check CUNY systems
        if (lower.contains("cunyfirst") || lower.contains("cuny first")
                || lower.contains("brightspace") || lower.contains("blackboard")
                || lower.contains("cunybuy") || lower.contains("cuny buy")
                || lower.contains("lehman 360") || lower.contains("lehman360")
                || lower.contains("zoom")
                || lower.contains("ideclare") || lower.contains("epaf") || lower.contains("eprf")
                || lower.contains("cuny login") || lower.contains("cunylogin")
                || lower.contains("cuny vpn") || lower.contains("cuny mfa")
                || lower.contains("cuny") || lower.contains("ssologin.cuny.edu")) {
            return "cuny";
        }

        // 2. Check Microsoft / Email systems
        if (lower.contains("microsoft 365") || lower.contains("microsoft365")
                || lower.contains("m365") || lower.contains("microsoft")
                || lower.contains("outlook") || lower.contains("teams")
                || lower.contains("office 365") || lower.contains("office365")
                || lower.contains("student email") || lower.contains("lehman email") || lower.contains("cuny email")
                || lower.contains("school email") || lower.contains("my email") || lower.contains("email login")
                || lower.contains("email") || lower.contains("ms 365") || lower.contains("ms365")) {
            return "microsoft365";
        }

        // 3. Check Lehman campus systems
        if (lower.contains("campus computer") || lower.contains("campus computers")
                || lower.contains("lab computer") || lower.contains("lab computers")
                || lower.contains("lehman login") || lower.contains("lehman account")
                || lower.contains("lehman")) {
            return "lehman";
        }

        // 4. Quick answers when prompted "is this for your CUNY login (1) or Microsoft/email login (2)?"
        if (lower.matches("^(1|first|the first|the first one|option 1|cuny login)$")) {
            return "cuny";
        }
        if (lower.matches("^(2|second|the second|the second one|option 2|microsoft|email|outlook)$")) {
            return "microsoft365";
        }

        return null;
    }
}
