package com.david.campusitcopilot.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceDetectorTest {

    @Test
    void testDetectMacBook() {
        assertEquals("macbook", DeviceDetector.detect("I am on a MacBook Air"));
        assertEquals("macbook", DeviceDetector.detect("macos monterey"));
        assertEquals("macbook", DeviceDetector.detect("using mac"));
        assertEquals("macbook", DeviceDetector.detect("apple laptop"));
    }

    @Test
    void testDetectIPhone() {
        assertEquals("iphone", DeviceDetector.detect("connecting from my iPhone 14"));
        assertEquals("iphone", DeviceDetector.detect("ios device"));
        assertEquals("iphone", DeviceDetector.detect("my ipad"));
    }

    @Test
    void testDetectAndroid() {
        assertEquals("android", DeviceDetector.detect("I have an Android phone"));
        assertEquals("android", DeviceDetector.detect("Samsung Galaxy S22"));
        assertEquals("android", DeviceDetector.detect("Google Pixel"));
    }

    @Test
    void testDetectWindows11() {
        assertEquals("windows-11", DeviceDetector.detect("I'm using Windows 11"));
        assertEquals("windows-11", DeviceDetector.detect("win 11 laptop"));
        assertEquals("windows-11", DeviceDetector.detect("windows11 pro"));
    }

    @Test
    void testDetectWindows10() {
        assertEquals("windows-10", DeviceDetector.detect("Running Windows 10"));
        assertEquals("windows-10", DeviceDetector.detect("win 10 desktop"));
        assertEquals("windows-10", DeviceDetector.detect("windows10"));
    }

    @Test
    void testDetectGenericWindows() {
        assertEquals("windows-11", DeviceDetector.detect("my windows laptop"));
        assertEquals("windows-11", DeviceDetector.detect("on a pc"));
    }

    @Test
    void testDetectUnknownOrEmpty() {
        assertNull(DeviceDetector.detect(null));
        assertNull(DeviceDetector.detect(""));
        assertNull(DeviceDetector.detect("hello"));
        assertNull(DeviceDetector.detect("wifi not working"));
    }
}
