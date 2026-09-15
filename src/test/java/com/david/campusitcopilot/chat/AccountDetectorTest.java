package com.david.campusitcopilot.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountDetectorTest {

    @Test
    void testDetectCunySystems() {
        assertEquals("cuny", AccountDetector.detect("I have an MFA error on CUNYfirst"));
        assertEquals("cuny", AccountDetector.detect("Brightspace login is asking for a code"));
        assertEquals("cuny", AccountDetector.detect("How to set up MFA for Lehman 360?"));
        assertEquals("cuny", AccountDetector.detect("Zoom requires authenticator"));
        assertEquals("cuny", AccountDetector.detect("CUNYbuy login issue"));
        assertEquals("cuny", AccountDetector.detect("CUNY Login MFA setup"));
        assertEquals("cuny", AccountDetector.detect("ePAF form login"));
        assertEquals("cuny", AccountDetector.detect("ideclare"));
        assertEquals("cuny", AccountDetector.detect("cuny"));
        assertEquals("cuny", AccountDetector.detect("1"));
        assertEquals("cuny", AccountDetector.detect("first"));
        assertEquals("cuny", AccountDetector.detect("the first one"));
    }

    @Test
    void testDetectMicrosoftSystems() {
        assertEquals("microsoft365", AccountDetector.detect("Can't set up authenticator for Outlook"));
        assertEquals("microsoft365", AccountDetector.detect("Teams is asking for verification"));
        assertEquals("microsoft365", AccountDetector.detect("Student email MFA prompt"));
        assertEquals("microsoft365", AccountDetector.detect("Microsoft 365 MFA"));
        assertEquals("microsoft365", AccountDetector.detect("M365 login"));
        assertEquals("microsoft365", AccountDetector.detect("Office 365"));
        assertEquals("microsoft365", AccountDetector.detect("2"));
        assertEquals("microsoft365", AccountDetector.detect("second"));
        assertEquals("microsoft365", AccountDetector.detect("the second one"));
    }

    @Test
    void testDetectLehmanSystems() {
        assertEquals("lehman", AccountDetector.detect("Lehman login account"));
        assertEquals("lehman", AccountDetector.detect("Campus computer login"));
        assertEquals("lehman", AccountDetector.detect("Lab computer in library"));
    }

    @Test
    void testDetectNullOrAmbiguous() {
        assertNull(AccountDetector.detect(null));
        assertNull(AccountDetector.detect(""));
        assertNull(AccountDetector.detect("MFA isn't working"));
        assertNull(AccountDetector.detect("I cannot log in"));
        assertNull(AccountDetector.detect("hello"));
    }
}
