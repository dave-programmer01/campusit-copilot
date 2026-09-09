package com.david.campusitcopilot.chat;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Utility for classifying answers to the diagnostic question and spotting tech issues or out-of-band queries.
 */
public final class DiagnosticClassifier {

    private DiagnosticClassifier() {
    }

    public enum Answer {
        WORKS_ELSEWHERE,
        PASSWORD_REJECTED,
        NEVER_ACTIVATED,
        OUT_OF_BAND,
        UNKNOWN
    }

    public static Answer classify(String text) {
        if (!StringUtils.hasText(text)) {
            return Answer.UNKNOWN;
        }
        String lower = text.toLowerCase(Locale.ROOT).trim();

        if (isOutOfBand(lower)) {
            return Answer.OUT_OF_BAND;
        }

        // 2. Check never activated / brand-new student (checked BEFORE affirmative so "yeah I never set it up" routes to NEVER_ACTIVATED)
        if (lower.contains("first semester") || lower.contains("brand new") || lower.contains("new student")
                || lower.contains("never activated") || lower.contains("never set") || lower.contains("never had")
                || lower.contains("first time") || lower.contains("freshman") || lower.contains("haven't set")
                || lower.contains("haven't activated") || lower.contains("have not set") || lower.contains("have not activated")
                || lower.contains("never used") || lower.contains("just enrolled") || lower.contains("just registered")
                || lower.contains("new here") || lower.contains("transfer student") || lower.contains("incoming")
                || lower.contains("haven't created") || lower.contains("never created") || lower.contains("first year")
                || lower.contains("just started") || lower.contains("haven't set up") || lower.contains("never set up")
                || lower.contains("haven't made") || lower.contains("haven't claimed") || lower.contains("never claimed")) {
            return Answer.NEVER_ACTIVATED;
        }

        // 3. Check password rejected / reset (negatives checked before affirmative)
        if (lower.matches("^(no|nope|nah|not really|negative|it doesn't|doesn't|does not|fails|failed|rejected|forgot|can't|cannot|cant|broken|won't|wont|not working|locked|invalid|incorrect|denied).*")
                || lower.contains("doesn't work") || lower.contains("does not work") || lower.contains("no it doesn't")
                || lower.contains("not working") || lower.contains("rejected") || lower.contains("forgot")
                || lower.contains("fails") || lower.contains("failed") || lower.contains("can't log")
                || lower.contains("cannot log") || lower.contains("cant log") || lower.contains("can't sign")
                || lower.contains("cannot sign") || lower.contains("cant sign") || lower.contains("can't get in")
                || lower.contains("cannot get in") || lower.contains("cant get in") || lower.contains("can't login")
                || lower.contains("cannot login") || lower.contains("cant login")
                || lower.contains("not taking") || lower.contains("won't take") || lower.contains("wont take")
                || lower.contains("won't accept") || lower.contains("wont accept") || lower.contains("doesn't accept")
                || lower.contains("does not accept") || lower.contains("neither") || lower.contains("both fail")
                || lower.contains("fails on both") || lower.contains("fails on email") || lower.contains("fails everywhere")
                || lower.contains("fails on portal") || lower.contains("email fails") || lower.contains("portal fails")
                || lower.contains("wrong password") || lower.contains("invalid password") || lower.contains("incorrect password")
                || lower.contains("broken") || lower.contains("locked out") || lower.contains("account locked")
                || lower.contains("account disabled") || lower.contains("doesn't let me") || lower.contains("won't let me")
                || lower.contains("wont let me") || lower.contains("not on email either") || lower.contains("not on portal either")) {
            return Answer.PASSWORD_REJECTED;
        }

        // 4. Check works elsewhere (affirmative)
        if (lower.matches("^(yes|yeah|yep|yup|it does|works|works fine|it works|fine|good|i can|can log in|can sign in|works everywhere|definitely|sure|sure does|always does|works on)(\\b|\\s|!|\\.).*")
                || lower.equals("it does") || lower.equals("works") || lower.equals("yes") || lower.equals("yeah")
                || lower.equals("yep") || lower.equals("yup") || lower.equals("fine") || lower.equals("good") || lower.equals("sure")
                || lower.contains("works fine") || lower.contains("it works") || lower.contains("works on email")
                || lower.contains("works on portal") || lower.contains("works on phone") || lower.contains("works on laptop")
                || lower.contains("works on my phone") || lower.contains("works on my laptop") || lower.contains("works on my email")
                || lower.contains("works on cunyfirst") || lower.contains("works elsewhere") || lower.contains("works everywhere")
                || lower.contains("works on other devices")
                || lower.contains("i can sign in") || lower.contains("i can log in") || lower.contains("can sign in")
                || lower.contains("can log in") || lower.contains("signs in fine") || lower.contains("signs in")
                || lower.contains("logs in fine") || lower.contains("logs in") || lower.contains("logged in fine")
                || lower.contains("email works") || lower.contains("portal works") || lower.contains("cunyfirst works")
                || lower.contains("lets me in") || lower.contains("let me in") || lower.contains("able to sign in")
                || lower.contains("able to log in")) {
            return Answer.WORKS_ELSEWHERE;
        }

        return Answer.UNKNOWN;
    }

    public static boolean isGreeting(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String cleaned = text.toLowerCase(Locale.ROOT).trim().replaceAll("[!.,?]+$", "").trim();
        return cleaned.matches("^(hi|hello|hey|hey there|hi there|hello there|good morning|good afternoon|good evening|howdy|sup|yo|greetings|what's up|whats up)$");
    }

    public static boolean isOutOfBand(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("carman") || lower.contains("where is") || lower.contains("library")
                || lower.contains("cafeteria") || lower.contains("bookstore") || lower.contains("financial aid")
                || lower.contains("bursar") || lower.contains("registrar") || lower.contains("advising")
                || lower.contains("campus map");
    }

    public static boolean isLoginOrPasswordIssue(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("wrong password") || lower.contains("invalid password")
                || lower.contains("can't log in") || lower.contains("cannot log in") || lower.contains("login failed")
                || lower.contains("credential") || lower.contains("360 login") || lower.contains("account locked");
    }
}
