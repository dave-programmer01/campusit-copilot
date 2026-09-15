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
        assertTrue(state.account().isEmpty());
        assertNull(state.getAccount());
        assertEquals(Stage.TRIAGE, state.getStage());
        assertEquals(0, state.getDeviceRetryCount());
        assertTrue(state.deviceRetryCount().isEmpty());
        assertEquals(0, state.getAccountRetryCount());
        assertTrue(state.accountRetryCount().isEmpty());
        assertTrue(state.messages().isEmpty());
        assertEquals("", state.latestUserMessage());
    }

    @Test
    void testScalarChannelsReplaceNotAppend() {
        Map<String, Object> initial = Map.of(
                "topic", "wifi",
                "device", "macbook",
                "subtopic", "activation",
                "account", "lehman",
                "stage", Stage.AWAITING_DEVICE,
                "deviceRetryCount", 1,
                "accountRetryCount", 0,
                "messages", List.of(new ChatMessage("user", "first message"))
        );

        ConversationState state1 = new ConversationState(initial);
        assertEquals("wifi", state1.getTopic());
        assertEquals("macbook", state1.getDevice());
        assertEquals("activation", state1.getSubtopic());
        assertEquals("lehman", state1.getAccount());
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(1, state1.getDeviceRetryCount());
        assertEquals(0, state1.getAccountRetryCount());
        assertEquals(1, state1.messages().size());

        // Update with new values
        Map<String, Object> updates = Map.of(
                "topic", "mfa",
                "device", "iphone",
                "subtopic", "reset",
                "account", "cuny",
                "stage", Stage.IN_MFA,
                "deviceRetryCount", 0,
                "accountRetryCount", 1,
                "messages", List.of(new ChatMessage("assistant", "second message"))
        );

        Map<String, Object> updatedData = AgentState.updateState(state1, updates, ConversationState.SCHEMA);
        ConversationState state2 = new ConversationState(updatedData);

        // Scalars must be replaced, not appended or converted to list
        assertEquals("mfa", state2.getTopic());
        assertEquals("iphone", state2.getDevice());
        assertEquals("reset", state2.getSubtopic());
        assertEquals("cuny", state2.getAccount());
        assertEquals(Stage.IN_MFA, state2.getStage());
        assertEquals(0, state2.getDeviceRetryCount());
        assertEquals(1, state2.getAccountRetryCount());

        // Messages list MUST append
        assertEquals(2, state2.messages().size());
        assertEquals("first message", state2.messages().get(0).content());
        assertEquals("second message", state2.messages().get(1).content());
        assertEquals("first message", state2.latestUserMessage());
    }

    @Test
    void testTypedGettersAndLatestUserMessage() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "account", "microsoft365",
                "stage", Stage.IN_MFA,
                "messages", List.of(
                        new ChatMessage("user", "help with mfa on outlook"),
                        new ChatMessage("assistant", "here are steps")
                )
        ));

        assertTrue(state.topic().isPresent());
        assertEquals("mfa", state.topic().get());
        assertEquals("mfa", state.getTopic());

        assertTrue(state.account().isPresent());
        assertEquals("microsoft365", state.account().get());
        assertEquals("microsoft365", state.getAccount());

        assertTrue(state.subtopic().isEmpty());
        assertNull(state.getSubtopic());

        assertTrue(state.stage().isPresent());
        assertEquals(Stage.IN_MFA, state.stage().get());
        assertEquals(Stage.IN_MFA, state.getStage());

        assertEquals("help with mfa on outlook", state.latestUserMessage());
    }

    @Test
    void testSchemaHasExactChannelsWithoutDuplication() {
        assertEquals(8, ConversationState.SCHEMA.size());
        assertTrue(ConversationState.SCHEMA.containsKey("messages"));
        assertTrue(ConversationState.SCHEMA.containsKey("topic"));
        assertTrue(ConversationState.SCHEMA.containsKey("device"));
        assertTrue(ConversationState.SCHEMA.containsKey("subtopic"));
        assertTrue(ConversationState.SCHEMA.containsKey("account"));
        assertTrue(ConversationState.SCHEMA.containsKey("stage"));
        assertTrue(ConversationState.SCHEMA.containsKey("deviceRetryCount"));
        assertTrue(ConversationState.SCHEMA.containsKey("accountRetryCount"));
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
