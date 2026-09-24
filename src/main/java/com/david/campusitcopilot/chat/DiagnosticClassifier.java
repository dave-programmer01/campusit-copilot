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

        // Never activated / brand-new student
        if (lower.contains("first semester") || lower.contains("brand new") || lower.contains("new student")
                || lower.contains("never activated") || lower.contains("never set") || lower.contains("freshman")
                || lower.contains("haven't set") || lower.contains("haven't activated")
                || lower.contains("transfer student") || lower.contains("incoming")
                || lower.matches(".*\\b(just )?(started|starting|enrolled|joined)\\b.*\\b(this|last) (semester|term|fall|spring)\\b.*")
                || lower.contains("activate") || lower.contains("activation")) {
            return Answer.NEVER_ACTIVATED;
        }

        // Password rejected / reset
        if (lower.matches("^(no|nope|nah|not really|negative|it doesn't|doesn't|does not|fails|failed|rejected|forgot|can't|cannot|cant|broken|won't|wont|locked|invalid|incorrect|denied).*")
                || lower.contains("doesn't work") || lower.contains("does not work") || lower.contains("no it doesn't")
                || lower.contains("not working") || lower.contains("rejected") || lower.contains("forgot")
                || lower.contains("fails") || lower.contains("failed") || lower.contains("wrong password")
                || lower.contains("invalid password") || lower.contains("incorrect password")
                || lower.contains("locked out") || lower.contains("account locked")
                || lower.contains("account disabled")
                || lower.contains("not taking") || lower.contains("won't take") || lower.contains("wont take")
                || lower.contains("not accepting") || lower.contains("doesn't accept") || lower.contains("does not accept")
                || lower.contains("won't let me in") || lower.contains("wont let me in")
                || lower.contains("not letting me in") || lower.contains("keeps rejecting")) {
            return Answer.PASSWORD_REJECTED;
        }

        // Works elsewhere (affirmative)
        if (lower.matches("^(yes|yeah|yep|yup|it does|works|fine|good|i can|can log in|can sign in|sure).*")
                || lower.equals("it does") || lower.equals("works") || lower.equals("yes") || lower.equals("yeah")
                || lower.equals("yep") || lower.equals("yup") || lower.equals("fine") || lower.equals("good") || lower.equals("sure")
                || lower.contains("works fine") || lower.contains("it works") || lower.contains("works on")
                || lower.contains("works elsewhere") || lower.contains("works everywhere")
                || lower.contains("i can sign in") || lower.contains("i can log in") || lower.contains("can sign in")
                || lower.contains("can log in") || lower.contains("lets me in") || lower.contains("able to log in")
                || lower.matches("^(my )?(email|portal|outlook|teams|cunyfirst|brightspace|zoom|lehman 360)\\b.*\\b(works|working|fine)\\b.*")) {
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

    /**
     * True when the message reports a credential being refused, as opposed to merely mentioning one.
     * <p>
     * Used to fork a walk to the diagnostic question: a student who hits "invalid password" while
     * entering Wi-Fi credentials may have a dead Lehman account rather than a network problem.
     * Both halves are required so that "ok, I typed my password, now what?" stays in the walk.
     */
    public static boolean isLoginOrPasswordIssue(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean mentionsCredential = lower.contains("password") || lower.contains("credential")
                || lower.contains("log in") || lower.contains("login") || lower.contains("sign in")
                || lower.contains("signin") || lower.contains("username");
        if (!mentionsCredential) {
            return false;
        }
        return lower.contains("wrong") || lower.contains("invalid") || lower.contains("incorrect")
                || lower.contains("rejected") || lower.contains("denied") || lower.contains("expired")
                || lower.contains("failed") || lower.contains("fails") || lower.contains("locked")
                || lower.contains("doesn't work") || lower.contains("does not work") || lower.contains("not working")
                || lower.contains("won't take") || lower.contains("wont take") || lower.contains("not taking")
                || lower.contains("not accepting") || lower.contains("won't accept")
                || lower.contains("can't") || lower.contains("cannot") || lower.contains("cant");
    }

    /**
     * True when the message is a "no" to the Wi-Fi credential step's activation heads-up
     * ("this only works if your lehman login is already activated").
     * <p>
     * Word-bounded on purpose: {@link #classify} treats any "no..." prefix as a rejection, which
     * would turn "now what?" at the credential step into a switch to activation.
     */
    public static boolean isActivationDenial(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT).trim().replace('’', '\'');
        return lower.matches("^(no|nope|nah|not yet|not really|never|negative|i don't think so|i dont think so"
                        + "|it doesn't|it doesnt|it does not|doesn't|doesnt|it isn't|it isnt|it's not|its not|it is not"
                        + "|i never|i haven't|i havent|i didn't|i didnt)\\b.*")
                || lower.contains("not activated") || lower.contains("isn't activated")
                || lower.contains("never activated") || lower.contains("not working")
                || lower.contains("doesn't work") || lower.contains("does not work");
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
}
