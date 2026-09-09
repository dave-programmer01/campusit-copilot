package com.david.campusitcopilot.chat;

/**
 * Stages of the IT support conversation graph.
 */
public enum Stage {
    TRIAGE,
    AWAITING_DEVICE,
    AWAITING_DIAGNOSTIC,
    IN_WIFI_WALK,
    IN_RESET,
    IN_ACTIVATION,
    FALLBACK,
    DONE
}
