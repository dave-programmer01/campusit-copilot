package com.david.campusitcopilot.chat;

import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationStateTest {

    @Test
    void testInitialStateDefaults() {
        ConversationState state = new ConversationState(Map.of());

        assertTrue(state.topic().isEmpty());
        assertNull(state.getTopic());
        assertTrue(state.device().isEmpty());
        assertNull(state.getDevice());
        assertTrue(state.subtopic().isEmpty());
        assertNull(state.getSubtopic());
        assertEquals(Stage.TRIAGE, state.getStage());
        assertEquals(0, state.getDeviceRetryCount());
        assertTrue(state.deviceRetryCount().isEmpty());
        assertTrue(state.messages().isEmpty());
        assertEquals("", state.latestUserMessage());
    }

    @Test
    void testScalarChannelsReplaceNotAppend() {
        Map<String, Object> initial = Map.of(
                "topic", "wifi",
                "device", "macbook",
                "subtopic", "activation",
                "stage", Stage.AWAITING_DEVICE,
                "deviceRetryCount", 1,
                "messages", List.of(new ChatMessage("user", "first message"))
        );

        ConversationState state1 = new ConversationState(initial);
        assertEquals("wifi", state1.getTopic());
        assertEquals("macbook", state1.getDevice());
        assertEquals("activation", state1.getSubtopic());
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(1, state1.getDeviceRetryCount());
        assertEquals(1, state1.messages().size());

        // Update with new values
        Map<String, Object> updates = Map.of(
                "topic", "login",
                "device", "iphone",
                "subtopic", "reset",
                "stage", Stage.IN_RESET,
                "deviceRetryCount", 0,
                "messages", List.of(new ChatMessage("assistant", "second message"))
        );

        Map<String, Object> updatedData = AgentState.updateState(state1, updates, ConversationState.SCHEMA);
        ConversationState state2 = new ConversationState(updatedData);

        // Scalars must be replaced, not appended or converted to list
        assertEquals("login", state2.getTopic());
        assertEquals("iphone", state2.getDevice());
        assertEquals("reset", state2.getSubtopic());
        assertEquals(Stage.IN_RESET, state2.getStage());
        assertEquals(0, state2.getDeviceRetryCount());

        // Messages list MUST append
        assertEquals(2, state2.messages().size());
        assertEquals("first message", state2.messages().get(0).content());
        assertEquals("second message", state2.messages().get(1).content());
        assertEquals("first message", state2.latestUserMessage());
    }

    @Test
    void testTypedGettersAndLatestUserMessage() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "wifi",
                "device", "windows-11",
                "stage", Stage.IN_WIFI_WALK,
                "messages", List.of(
                        new ChatMessage("user", "help with wifi"),
                        new ChatMessage("assistant", "what device?"),
                        new ChatMessage("user", "windows 11"),
                        new ChatMessage("assistant", "here are steps")
                )
        ));

        assertTrue(state.topic().isPresent());
        assertEquals("wifi", state.topic().get());
        assertEquals("wifi", state.getTopic());

        assertTrue(state.device().isPresent());
        assertEquals("windows-11", state.device().get());
        assertEquals("windows-11", state.getDevice());

        assertTrue(state.subtopic().isEmpty());
        assertNull(state.getSubtopic());

        assertTrue(state.stage().isPresent());
        assertEquals(Stage.IN_WIFI_WALK, state.stage().get());
        assertEquals(Stage.IN_WIFI_WALK, state.getStage());

        assertEquals("windows 11", state.latestUserMessage());
    }

    @Test
    void testSchemaHasExactChannelsWithoutDuplication() {
        assertEquals(6, ConversationState.SCHEMA.size());
        assertTrue(ConversationState.SCHEMA.containsKey("messages"));
        assertTrue(ConversationState.SCHEMA.containsKey("topic"));
        assertTrue(ConversationState.SCHEMA.containsKey("device"));
        assertTrue(ConversationState.SCHEMA.containsKey("subtopic"));
        assertTrue(ConversationState.SCHEMA.containsKey("stage"));
        assertTrue(ConversationState.SCHEMA.containsKey("deviceRetryCount"));
    }

    @Test
    void testClearingSubtopicWithNull() {
        ConversationState initial = new ConversationState(Map.of(
                "topic", "login",
                "subtopic", "reset"
        ));
        assertEquals("reset", initial.getSubtopic());

        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
        updates.put("topic", "wifi");
        updates.put("subtopic", null);

        Map<String, Object> updatedData = AgentState.updateState(initial, updates, ConversationState.SCHEMA);
        ConversationState updatedState = new ConversationState(updatedData);

        assertEquals("wifi", updatedState.getTopic());
        assertNull(updatedState.getSubtopic());
        assertTrue(updatedState.subtopic().isEmpty());
    }

    @Test
    void testClearingSubtopicWithEmptyString() {
        ConversationState initial = new ConversationState(Map.of(
                "topic", "login",
                "subtopic", "reset"
        ));
        assertEquals("reset", initial.getSubtopic());

        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
        updates.put("topic", "wifi");
        updates.put("subtopic", "");

        Map<String, Object> updatedData = AgentState.updateState(initial, updates, ConversationState.SCHEMA);
        ConversationState updatedState = new ConversationState(updatedData);

        assertEquals("wifi", updatedState.getTopic());
        assertNull(updatedState.getSubtopic());
        assertTrue(updatedState.subtopic().isPresent());
        assertEquals("", updatedState.subtopic().get());
    }
}
