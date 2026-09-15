package com.david.campusitcopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IntentRouterTest {

    private ChatClient chatClient;
    private IntentRouter intentRouter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        intentRouter = new IntentRouter(chatClient, new ObjectMapper());
    }

    @Test
    void testParseResponseWifi() {
        String json = "{\"topic\": \"wifi\", \"subtopic\": null}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isWifi());
        assertNull(intent.subtopic());
    }

    @Test
    void testParseResponseLoginReset() {
        String json = "{\"topic\": \"login\", \"subtopic\": \"reset\"}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isLogin());
        assertEquals("reset", intent.subtopic());
    }

    @Test
    void testParseResponseLoginActivation() {
        String json = "{\"topic\": \"login\", \"subtopic\": \"activation\"}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isLogin());
        assertEquals("activation", intent.subtopic());
    }

    @Test
    void testParseResponseLoginGeneral() {
        String json = "{\"topic\": \"login\", \"subtopic\": null}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isLogin());
        assertNull(intent.subtopic());
    }

    @Test
    void testParseResponseGreeting() {
        String json = "{\"topic\": \"greeting\", \"subtopic\": null}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isGreeting());
        assertNull(intent.subtopic());
    }

    @Test
    void testParseResponseUnknown() {
        String json = "{\"topic\": \"unknown\", \"subtopic\": null}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isUnknown());
        assertNull(intent.subtopic());
    }

    @Test
    void testParseResponseWithMarkdownCodeFence() {
        String raw = "```json\n{\"topic\": \"login\", \"subtopic\": \"reset\"}\n```";
        Intent intent = intentRouter.parseResponse(raw);
        assertTrue(intent.isLogin());
        assertEquals("reset", intent.subtopic());
    }

    @Test
    void testParseResponseMfaCuny() {
        String json = "{\"topic\": \"mfa\", \"subtopic\": null, \"account\": \"cuny\"}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isMfa());
        assertNull(intent.subtopic());
        assertEquals("cuny", intent.account());
    }

    @Test
    void testParseResponseMfaMicrosoft() {
        String json = "{\"topic\": \"mfa\", \"subtopic\": null, \"account\": \"microsoft365\"}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isMfa());
        assertNull(intent.subtopic());
        assertEquals("microsoft365", intent.account());
    }

    @Test
    void testParseResponseMfaAmbiguous() {
        String json = "{\"topic\": \"mfa\", \"subtopic\": null, \"account\": null}";
        Intent intent = intentRouter.parseResponse(json);
        assertTrue(intent.isMfa());
        assertNull(intent.subtopic());
        assertNull(intent.account());
    }

    @Test
    void testParseResponseFallbackFromText() {
        String raw = "The student wants to reset their password";
        Intent intent = intentRouter.parseResponse(raw);
        assertTrue(intent.isLogin());
        assertEquals("reset", intent.subtopic());
    }

    @Test
    void testParseResponseFallbackFromTextMfa() {
        String raw = "I am having an MFA error on Brightspace";
        Intent intent = intentRouter.parseResponse(raw);
        assertTrue(intent.isMfa());
        assertEquals("cuny", intent.account());
    }

    @Test
    void testRouteEmptyHistoryReturnsUnknown() {
        Intent intent = intentRouter.route(List.of());
        assertTrue(intent.isUnknown());
    }

    @Test
    void testRouteSuccessfulLlmCall() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"login\", \"subtopic\": \"reset\"}");

        List<ChatMessage> history = List.of(new ChatMessage("user", "forgot my lehman password"));
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isLogin());
        assertEquals("reset", intent.subtopic());
    }

    @Test
    void testRouteGreeting() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"greeting\", \"subtopic\": null}");

        List<ChatMessage> history = List.of(new ChatMessage("user", "hi"));
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isGreeting());
        assertNull(intent.subtopic());
    }

    @Test
    void testRouteGreetingFallbackHeuristic() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("LLM down"));

        List<ChatMessage> history = List.of(new ChatMessage("user", "hello there"));
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isGreeting());
        assertNull(intent.subtopic());
    }

    @Test
    void testRouteColdLoginEntryAmbiguous() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"login\", \"subtopic\": null}");

        List<ChatMessage> history = List.of(new ChatMessage("user", "my password won't work"));
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isLogin());
        assertNull(intent.subtopic());
    }

    @Test
    void testRouteMultiTurnTerseAnswerNoPasswordReset() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"login\", \"subtopic\": \"reset\"}");

        List<ChatMessage> history = List.of(
                new ChatMessage("user", "my password won't work"),
                new ChatMessage("assistant", "quick check — when you try to sign into your Lehman email or the portal, does that password work?"),
                new ChatMessage("user", "no")
        );
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isLogin());
        assertEquals("reset", intent.subtopic());
    }

    @Test
    void testRouteMultiTurnTerseAnswerWorksFineWifi() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"wifi\", \"subtopic\": null}");

        List<ChatMessage> history = List.of(
                new ChatMessage("user", "wifi won't connect"),
                new ChatMessage("assistant", "quick check — when you try to sign into your Lehman email or the portal, does that password work?"),
                new ChatMessage("user", "works fine")
        );
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isWifi());
        assertNull(intent.subtopic());
    }

    @Test
    void testRouteMultiTurnBrandNewStudentActivation() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"login\", \"subtopic\": \"activation\"}");

        List<ChatMessage> history = List.of(
                new ChatMessage("user", "can't log into campus wifi or 360"),
                new ChatMessage("assistant", "quick check — when you try to sign into your Lehman email or the portal, does that password work?"),
                new ChatMessage("user", "first semester here, never set it up")
        );
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isLogin());
        assertEquals("activation", intent.subtopic());
    }

    @Test
    void testRouteMultiTurnOutOfBandInterruptionFlapsToUnknown() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"topic\": \"unknown\", \"subtopic\": null}");

        List<ChatMessage> history = List.of(
                new ChatMessage("user", "my password won't work"),
                new ChatMessage("assistant", "quick check — when you try to sign into your Lehman email or the portal, does that password work?"),
                new ChatMessage("user", "wait where is Carman Hall?")
        );
        Intent intent = intentRouter.route(history);

        assertTrue(intent.isUnknown());
        assertNull(intent.subtopic());
    }
}
