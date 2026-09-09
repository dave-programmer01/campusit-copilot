package com.david.campusitcopilot.chat;

import com.david.campusitcopilot.search.FilterSpec;
import com.david.campusitcopilot.search.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatControllerTest {

    private ChatGraph chatGraph;
    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatGraph = mock(ChatGraph.class);
        chatController = new ChatController(chatGraph);
    }

    @Test
    void testChatWithConversationIdReturnsReplyAndId() {
        ConversationState mockState = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "messages", List.of(
                        new ChatMessage("user", "my password is not working"),
                        new ChatMessage("assistant", ChatGraph.DIAGNOSTIC_PROMPT)
                )
        ));

        when(chatGraph.execute(eq("conv-123"), anyList(), eq(null))).thenReturn(mockState);

        ChatController.ChatRequest request = new ChatController.ChatRequest(
                new ChatMessage("user", "my password is not working"),
                null,
                "conv-123"
        );

        ChatController.ChatResponse response = chatController.chat(request);

        assertNotNull(response);
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, response.reply());
        assertEquals("conv-123", response.conversationId());
        verify(chatGraph).execute(eq("conv-123"), eq(List.of(new ChatMessage("user", "my password is not working"))), eq(null));
    }

    @Test
    void testChatWithoutConversationIdGeneratesId() {
        ConversationState mockState = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "messages", List.of(
                        new ChatMessage("user", "how to connect wifi"),
                        new ChatMessage("assistant", ChatGraph.DEVICE_CHECK_PROMPT)
                )
        ));

        when(chatGraph.execute(anyString(), anyList(), eq("macbook"))).thenReturn(mockState);

        ChatController.ChatRequest request = new ChatController.ChatRequest(
                new ChatMessage("user", "how to connect wifi"),
                "macbook"
        );

        ChatController.ChatResponse response = chatController.chat(request);

        assertNotNull(response);
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, response.reply());
        assertNotNull(response.conversationId());
        assertFalse(response.conversationId().isBlank());
        verify(chatGraph).execute(anyString(), eq(List.of(new ChatMessage("user", "how to connect wifi"))), eq("macbook"));
    }

    @Test
    void testChatExtractsLatestAssistantReply() {
        ConversationState mockState = new ConversationState(Map.of(
                "messages", List.of(
                        new ChatMessage("user", "turn 1"),
                        new ChatMessage("assistant", "reply 1"),
                        new ChatMessage("user", "turn 2"),
                        new ChatMessage("assistant", "latest reply")
                )
        ));

        when(chatGraph.execute(eq("thread-1"), anyList(), eq(null))).thenReturn(mockState);

        ChatController.ChatRequest request = new ChatController.ChatRequest(
                new ChatMessage("user", "turn 2"),
                null,
                "thread-1"
        );

        ChatController.ChatResponse response = chatController.chat(request);

        assertEquals("latest reply", response.reply());
        assertEquals("thread-1", response.conversationId());
        verify(chatGraph).execute(eq("thread-1"), eq(List.of(new ChatMessage("user", "turn 2"))), eq(null));
    }

    @Test
    void testChatNullMessageDefaultsToEmptyList() {
        ConversationState mockState = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("assistant", "hello"))
        ));

        when(chatGraph.execute(anyString(), eq(List.of()), eq(null))).thenReturn(mockState);

        ChatController.ChatRequest request = new ChatController.ChatRequest(null, null, "thread-null");
        ChatController.ChatResponse response = chatController.chat(request);

        assertEquals("hello", response.reply());
        assertEquals("thread-null", response.conversationId());
        verify(chatGraph).execute(eq("thread-null"), eq(List.of()), eq(null));
    }

    // =========================================================================
    // Multi-turn controller tests with live ChatGraph instance
    // =========================================================================

    @Test
    void testMultiTurnDialogueThroughControllerPreservesState() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Here are the reset steps for Lehman 360 login.");

        RetrievalService retrievalService = mock(RetrievalService.class);
        IntentRouter intentRouter = mock(IntentRouter.class);
        when(intentRouter.route(any())).thenReturn(Intent.login(null));

        Document resetDoc =
                new Document("Reset steps", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(resetDoc));

        ChatGraph liveGraph = new ChatGraph(chatClient, retrievalService, intentRouter, "DEVICE: {{DEVICE}}\nCONTEXT: {{CONTEXT}}");
        ChatController liveController = new ChatController(liveGraph);

        String convId = "multi-turn-controller-session";

        // Turn 1: Cold login entry
        ChatController.ChatRequest req1 = new ChatController.ChatRequest(
                new ChatMessage("user", "my password won't work"),
                null,
                convId
        );
        ChatController.ChatResponse res1 = liveController.chat(req1);
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, res1.reply());
        assertEquals(convId, res1.conversationId());

        // Turn 2: Terse reply ("no") using the SAME conversationId
        ChatController.ChatRequest req2 = new ChatController.ChatRequest(
                new ChatMessage("user", "no"),
                null,
                convId
        );
        ChatController.ChatResponse res2 = liveController.chat(req2);
        assertEquals("Here are the reset steps for Lehman 360 login.", res2.reply());
        assertEquals(convId, res2.conversationId());

        // Confirm state in liveGraph after turn 2 is IN_RESET
        ConversationState savedState = liveGraph.getState(convId).orElseThrow();
        assertEquals(Stage.IN_RESET, savedState.getStage());
        assertEquals("login", savedState.getTopic());
        assertEquals("reset", savedState.getSubtopic());
    }
}
