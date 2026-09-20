package com.david.campusitcopilot.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticClassifierTest {

    @Test
    void testClassifyWorksElsewhere() {
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("yes"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("yeah it works fine"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("yeah it's fine on email"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("works fine"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("it works on my email"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("i can log in to portal fine"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("lets me in on CUNYfirst"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("works on my phone"));
        assertEquals(DiagnosticClassifier.Answer.WORKS_ELSEWHERE, DiagnosticClassifier.classify("email works"));
    }

    @Test
    void testClassifyPasswordRejected() {
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("no"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("nope"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("nope not really"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("it doesn't work"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("it doesn't work on email either"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("no it says wrong password"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("fails on email too"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("forgot my password"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("can't get in anywhere"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("it's not taking it"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("rejected everywhere"));
        assertEquals(DiagnosticClassifier.Answer.PASSWORD_REJECTED, DiagnosticClassifier.classify("locked out of my account"));
    }

    @Test
    void testClassifyNeverActivated() {
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("first semester here, never set it up"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("brand new student"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("never activated my account"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("i am a freshman"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("yeah I never set it up"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("just started this semester"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("incoming transfer student"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("I have to activate Lehman login"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("need to activate"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("not activated"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("haven't activated"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("activate my login"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("activate my account"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("gotta activate"));
        assertEquals(DiagnosticClassifier.Answer.NEVER_ACTIVATED, DiagnosticClassifier.classify("it doesn't work, I have to activate Lehman login"));
    }

    @Test
    void testClassifyOutOfBand() {
        assertEquals(DiagnosticClassifier.Answer.OUT_OF_BAND, DiagnosticClassifier.classify("wait where is Carman Hall?"));
        assertEquals(DiagnosticClassifier.Answer.OUT_OF_BAND, DiagnosticClassifier.classify("where is the library?"));
        assertEquals(DiagnosticClassifier.Answer.OUT_OF_BAND, DiagnosticClassifier.classify("campus map"));
    }

    @Test
    void testClassifyUnknown() {
        assertEquals(DiagnosticClassifier.Answer.UNKNOWN, DiagnosticClassifier.classify("maybe"));
        assertEquals(DiagnosticClassifier.Answer.UNKNOWN, DiagnosticClassifier.classify(""));
        assertEquals(DiagnosticClassifier.Answer.UNKNOWN, DiagnosticClassifier.classify(null));
    }

    @Test
    void testIsGreeting() {
        assertTrue(DiagnosticClassifier.isGreeting("hi"));
        assertTrue(DiagnosticClassifier.isGreeting("hello"));
        assertTrue(DiagnosticClassifier.isGreeting("Hey!"));
        assertTrue(DiagnosticClassifier.isGreeting("good morning"));
        assertTrue(DiagnosticClassifier.isGreeting("howdy"));
        assertTrue(DiagnosticClassifier.isGreeting("sup"));
        assertTrue(DiagnosticClassifier.isGreeting("yo"));
        assertTrue(DiagnosticClassifier.isGreeting("hello there"));
        assertFalse(DiagnosticClassifier.isGreeting("hi my wifi is broken"));
        assertFalse(DiagnosticClassifier.isGreeting("where is Carman Hall?"));
        assertFalse(DiagnosticClassifier.isGreeting(""));
        assertFalse(DiagnosticClassifier.isGreeting(null));
    }
}
