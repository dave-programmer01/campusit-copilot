package com.david.campusitcopilot.chat;

import com.david.campusitcopilot.search.FilterSpec;
import com.david.campusitcopilot.search.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private RetrievalService retrievalService;
    private IntentRouter intentRouter;
    private ChatService chatService;

    private static final String TEMPLATE = "DEVICE: {{DEVICE}}\nCONTEXT: {{CONTEXT}}";

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        retrievalService = mock(RetrievalService.class);
        intentRouter = mock(IntentRouter.class);

        chatService = new ChatService(
                chatClientBuilder,
                retrievalService,
                intentRouter,
                new ByteArrayResource(TEMPLATE.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void testWifiIssueWithKnownDeviceRetrievesWifiDoc() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document doc = new Document("Step 1: Open Wi-Fi settings", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(eq("how to connect"), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("ok so first open your wifi settings");

        List<ChatMessage> history = List.of(new ChatMessage("user", "how to connect"));
        String response = chatService.reply(history, "macbook");

        assertEquals("ok so first open your wifi settings", response);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("known → The student is on: macbook"));
        assertTrue(systemText.contains("Step 1: Open Wi-Fi settings"));
    }

    @Test
    void testWifiIssueWithUnknownDeviceReturnsEmptyContext() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        when(responseSpec.content()).thenReturn("what device are you on?");

        List<ChatMessage> history = List.of(new ChatMessage("user", "wifi is not working"));
        String response = chatService.reply(history, null);

        assertEquals("what device are you on?", response);
        verify(retrievalService, never()).search(anyString(), any(FilterSpec.class), anyInt(), anyDouble());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("unknown → Not yet known"));
        assertTrue(systemText.contains("No verified steps loaded yet"));
    }

    @Test
    void testLoginResetIssueRetrievesPasswordResetDoc() {
        when(intentRouter.route(any())).thenReturn(Intent.login("reset"));
        Document doc = new Document("Password reset guide steps", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(eq("forgot my password"), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("head to managelogin.lehman.edu and click forgot password");

        List<ChatMessage> history = List.of(new ChatMessage("user", "forgot my password"));
        String response = chatService.reply(history, null);

        assertEquals("head to managelogin.lehman.edu and click forgot password", response);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("Password reset guide steps"));
    }

    @Test
    void testLoginActivationIssueRetrievesActivationDoc() {
        when(intentRouter.route(any())).thenReturn(Intent.login("activation"));
        Document doc = new Document("Account activation guide steps", Map.of("topic", "login", "subtopic", "activation"));
        when(retrievalService.search(eq("never activated my account"), eq(FilterSpec.login("activation")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("head to managelogin.lehman.edu to activate");

        List<ChatMessage> history = List.of(new ChatMessage("user", "never activated my account"));
        String response = chatService.reply(history, null);

        assertEquals("head to managelogin.lehman.edu to activate", response);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("Account activation guide steps"));
    }

    @Test
    void testUnknownIntentReturnsEmptyContext() {
        when(intentRouter.route(any())).thenReturn(Intent.unknown());
        when(responseSpec.content()).thenReturn("hey! how can i help?");

        List<ChatMessage> history = List.of(new ChatMessage("user", "hello"));
        String response = chatService.reply(history, null);

        assertEquals("hey! how can i help?", response);
        verify(retrievalService, never()).search(anyString(), any(FilterSpec.class), anyInt(), anyDouble());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("No verified steps loaded yet"));
    }

    @Test
    void testColdLoginEntryAmbiguousQueriesLoginWithoutSubtopicFilter() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        Document doc = new Document("General login info", Map.of("topic", "login"));
        when(retrievalService.search(eq("my password won't work"), eq(FilterSpec.login(null)), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("quick check — when you try to sign into your Lehman email or the portal, does that password work?");

        List<ChatMessage> history = List.of(new ChatMessage("user", "my password won't work"));
        String response = chatService.reply(history, null);

        assertEquals("quick check — when you try to sign into your Lehman email or the portal, does that password work?", response);
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("General login info"));
    }

    @Test
    void testInterruptionMidForkFlapsToUnknownAndCollapsesContextSlot() {
        when(intentRouter.route(any())).thenReturn(Intent.unknown());
        when(responseSpec.content()).thenReturn("Carman Hall 108 is located near the campus quad.");

        List<ChatMessage> history = List.of(
                new ChatMessage("user", "my password won't work"),
                new ChatMessage("assistant", "quick check — when you try to sign into your Lehman email or the portal, does that password work?"),
                new ChatMessage("user", "wait where is Carman Hall?")
        );
        String response = chatService.reply(history, null);

        assertEquals("Carman Hall 108 is located near the campus quad.", response);
        verify(retrievalService, never()).search(anyString(), any(FilterSpec.class), anyInt(), anyDouble());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        String systemText = systemCaptor.getValue();
        assertTrue(systemText.contains("No verified steps loaded yet"));
    }
}
