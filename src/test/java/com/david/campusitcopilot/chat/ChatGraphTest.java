package com.david.campusitcopilot.chat;

import com.david.campusitcopilot.search.FilterSpec;
import com.david.campusitcopilot.search.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

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

class ChatGraphTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec responseSpec;
    private RetrievalService retrievalService;
    private IntentRouter intentRouter;
    private ChatGraph chatGraph;

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
        when(responseSpec.content()).thenReturn("Mocked LLM reply");

        retrievalService = mock(RetrievalService.class);
        intentRouter = mock(IntentRouter.class);

        chatGraph = new ChatGraph(chatClient, retrievalService, intentRouter, TEMPLATE);
    }

    // ==========================================
    // 1. NODE TESTS
    // ==========================================

    @Test
    void testTriageNodeUnsetWifi() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        ConversationState state = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("user", "wifi on my macbook"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals("macbook", updates.get("device"));
        assertNull(updates.get("subtopic"));
    }

    @Test
    void testTriageNodeUnsetLogin() {
        when(intentRouter.route(any())).thenReturn(Intent.login("reset"));
        ConversationState state = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("user", "forgot password"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("login", updates.get("topic"));
        assertEquals("reset", updates.get("subtopic"));
    }

    @Test
    void testTriageNodeUnsetGreeting() {
        when(intentRouter.route(any())).thenReturn(Intent.greeting());
        ConversationState state = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("user", "hello"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("greeting", updates.get("topic"));
        assertNull(updates.get("subtopic"));
    }

    @Test
    void testTriageNodeFallbackReClassifies() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.FALLBACK,
                "topic", "unknown",
                "deviceRetryCount", 2,
                "messages", List.of(new ChatMessage("user", "how do i connect wifi"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testTriageNodeAlreadySetDoesNothing() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "wifi",
                "messages", List.of(new ChatMessage("user", "next step"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertTrue(updates.isEmpty());
        verify(intentRouter, never()).route(any());
    }

    @Test
    void testDeviceCheckNode() {
        ConversationState state = new ConversationState(Map.of());
        Map<String, Object> updates = chatGraph.deviceCheckNode(state);

        assertEquals(Stage.AWAITING_DEVICE, updates.get("stage"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertNotNull(msgs);
        assertEquals(1, msgs.size());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, msgs.get(0).content());
    }

    @Test
    void testDiagnosticNode() {
        ConversationState state = new ConversationState(Map.of());
        Map<String, Object> updates = chatGraph.diagnosticNode(state);

        assertEquals(Stage.AWAITING_DIAGNOSTIC, updates.get("stage"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertNotNull(msgs);
        assertEquals(1, msgs.size());
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, msgs.get(0).content());
    }

    @Test
    void testRetrieveRespondNodeWifi() {
        Document doc = new Document("MacBook wifi steps", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(eq("how to connect"), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Here are the MacBook steps");

        ConversationState state = new ConversationState(Map.of(
                "topic", "wifi",
                "device", "macbook",
                "stage", Stage.TRIAGE,
                "messages", List.of(new ChatMessage("user", "how to connect"))
        ));

        Map<String, Object> updates = chatGraph.retrieveRespondNode(state);
        assertEquals(Stage.IN_WIFI_WALK, updates.get("stage"));

        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertEquals("Here are the MacBook steps", msgs.get(0).content());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        assertTrue(systemCaptor.getValue().contains("MacBook wifi steps"));
        assertTrue(systemCaptor.getValue().contains("known → The student is on: macbook"));
    }

    @Test
    void testHandleDeviceNodeDetected() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "messages", List.of(new ChatMessage("user", "MacBook Pro"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals("macbook", updates.get("device"));
        assertEquals(Stage.IN_WIFI_WALK, updates.get("stage"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeDiagnosticPasswordRejected() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "messages", List.of(new ChatMessage("user", "no it doesn't work on email either"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals("login", updates.get("topic"));
        assertEquals("reset", updates.get("subtopic"));
        assertEquals(Stage.IN_RESET, updates.get("stage"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeDiagnosticNeverActivated() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "messages", List.of(new ChatMessage("user", "first semester here, never set it up"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals("login", updates.get("topic"));
        assertEquals("activation", updates.get("subtopic"));
        assertEquals(Stage.IN_ACTIVATION, updates.get("stage"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeDiagnosticWorksElsewhereKnownDevice() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "device", "macbook",
                "messages", List.of(new ChatMessage("user", "works fine"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals(Stage.IN_WIFI_WALK, updates.get("stage"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeDiagnosticWorksElsewhereUnknownDeviceFirstAttempt() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "deviceRetryCount", 0,
                "messages", List.of(new ChatMessage("user", "works fine"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals("", updates.get("subtopic"));
        assertEquals(Stage.AWAITING_DEVICE, updates.get("stage"));
        assertEquals(1, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeUnclearFirstAttemptIncrementsRetry() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "deviceRetryCount", 0,
                "messages", List.of(new ChatMessage("user", "banana"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals(Stage.AWAITING_DEVICE, updates.get("stage"));
        assertEquals(1, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeUnclearSecondAttemptTriggersFallback() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "deviceRetryCount", 1,
                "messages", List.of(new ChatMessage("user", "pineapple"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals(Stage.FALLBACK, updates.get("stage"));
        assertEquals(2, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDeviceNodeOutOfBand() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "messages", List.of(new ChatMessage("user", "where is Carman Hall?"))
        ));

        Map<String, Object> updates = chatGraph.handleDeviceNode(state);
        assertEquals(Stage.FALLBACK, updates.get("stage"));
        assertEquals(0, updates.get("deviceRetryCount"));
    }

    @Test
    void testHandleDiagnosticNodeWorksElsewhereKnownDevice() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "device", "macbook",
                "messages", List.of(new ChatMessage("user", "works fine on my phone"))
        ));

        Map<String, Object> updates = chatGraph.handleDiagnosticNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals("", updates.get("subtopic"));
        assertEquals(Stage.IN_WIFI_WALK, updates.get("stage"));
    }

    @Test
    void testHandleDiagnosticNodeWorksElsewhereUnknownDevice() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "messages", List.of(new ChatMessage("user", "yes"))
        ));

        Map<String, Object> updates = chatGraph.handleDiagnosticNode(state);
        assertEquals("wifi", updates.get("topic"));
        assertEquals("", updates.get("subtopic"));
        assertEquals(Stage.AWAITING_DEVICE, updates.get("stage"));
    }

    @Test
    void testHandleDiagnosticNodePasswordRejected() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "messages", List.of(new ChatMessage("user", "no"))
        ));

        Map<String, Object> updates = chatGraph.handleDiagnosticNode(state);
        assertEquals("login", updates.get("topic"));
        assertEquals("reset", updates.get("subtopic"));
        assertEquals(Stage.IN_RESET, updates.get("stage"));
    }

    @Test
    void testHandleDiagnosticNodeNeverActivated() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "messages", List.of(new ChatMessage("user", "first semester here, never set it up"))
        ));

        Map<String, Object> updates = chatGraph.handleDiagnosticNode(state);
        assertEquals("login", updates.get("topic"));
        assertEquals("activation", updates.get("subtopic"));
        assertEquals(Stage.IN_ACTIVATION, updates.get("stage"));
    }

    @Test
    void testHandleDiagnosticNodeOutOfBand() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "messages", List.of(new ChatMessage("user", "where is the library?"))
        ));

        Map<String, Object> updates = chatGraph.handleDiagnosticNode(state);
        assertEquals(Stage.FALLBACK, updates.get("stage"));
    }

    @Test
    void testDeviceAckCheckNode() {
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "topic", "wifi"
        ));
        Map<String, Object> updates = chatGraph.deviceAckCheckNode(state);
        assertEquals(Stage.AWAITING_DEVICE, updates.get("stage"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertEquals(1, msgs.size());
        assertEquals(ChatGraph.DEVICE_CHECK_ACKNOWLEDGED_PROMPT, msgs.getFirst().content());
    }

    @Test
    void testAsideNode() {
        when(responseSpec.content()).thenReturn("Carman Hall 108 is the IT Help Desk. Ready to resume whenever you are.");
        ConversationState state = new ConversationState(Map.of(
                "stage", Stage.IN_WIFI_WALK,
                "topic", "wifi",
                "device", "macbook",
                "messages", List.of(
                        new ChatMessage("user", "wifi broken"),
                        new ChatMessage("assistant", "Step 1..."),
                        new ChatMessage("user", "where is Carman Hall?")
                )
        ));

        Map<String, Object> updates = chatGraph.asideNode(state);
        assertNull(updates.get("stage")); // preserves stage by not overriding
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertEquals(1, msgs.size());
        assertEquals("Carman Hall 108 is the IT Help Desk. Ready to resume whenever you are.", msgs.getFirst().content());
        verify(retrievalService, never()).search(anyString(), any(FilterSpec.class), anyInt(), anyDouble());
    }

    @Test
    void testGreetingNode() {
        ConversationState state = new ConversationState(Map.of());
        Map<String, Object> updates = chatGraph.greetingNode(state);

        assertEquals(Stage.TRIAGE, updates.get("stage"));
        assertEquals("", updates.get("topic"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertEquals(ChatGraph.GREETING_PROMPT, msgs.get(0).content());
    }

    @Test
    void testFallbackNode() {
        ConversationState state = new ConversationState(Map.of());
        Map<String, Object> updates = chatGraph.fallbackNode(state);

        assertEquals(Stage.FALLBACK, updates.get("stage"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertEquals(ChatGraph.FALLBACK_PROMPT, msgs.get(0).content());
    }

    // ==========================================
    // 2. CONDITIONAL EDGE ROUTING TESTS
    // ==========================================

    @Test
    void testRouteFromRouter() {
        // Initial/Unset stage -> triage_intent
        ConversationState s1 = new ConversationState(Map.of("stage", Stage.TRIAGE));
        assertEquals("triage_intent", chatGraph.routeFromRouter(s1));

        // Awaiting device -> handle_device
        ConversationState s2 = new ConversationState(Map.of("stage", Stage.AWAITING_DEVICE, "topic", "wifi"));
        assertEquals("handle_device", chatGraph.routeFromRouter(s2));

        // Awaiting device with out-of-band aside -> aside
        ConversationState s2b = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DEVICE,
                "topic", "wifi",
                "messages", List.of(new ChatMessage("user", "where is Carman Hall?"))
        ));
        assertEquals("aside", chatGraph.routeFromRouter(s2b));

        // Awaiting diagnostic -> handle_diagnostic
        ConversationState s3 = new ConversationState(Map.of("stage", Stage.AWAITING_DIAGNOSTIC, "topic", "login"));
        assertEquals("handle_diagnostic", chatGraph.routeFromRouter(s3));

        // Awaiting diagnostic with out-of-band aside -> aside
        ConversationState s3b = new ConversationState(Map.of(
                "stage", Stage.AWAITING_DIAGNOSTIC,
                "topic", "login",
                "messages", List.of(new ChatMessage("user", "where is the library?"))
        ));
        assertEquals("aside", chatGraph.routeFromRouter(s3b));

        // In wifi walk password issue -> diagnostic
        ConversationState s4 = new ConversationState(Map.of(
                "stage", Stage.IN_WIFI_WALK,
                "topic", "wifi",
                "device", "macbook",
                "messages", List.of(new ChatMessage("user", "entered password and it says invalid password"))
        ));
        assertEquals("diagnostic", chatGraph.routeFromRouter(s4));

        // In wifi walk out of band aside -> aside (does NOT drop stage)
        ConversationState s5 = new ConversationState(Map.of(
                "stage", Stage.IN_WIFI_WALK,
                "topic", "wifi",
                "device", "macbook",
                "messages", List.of(new ChatMessage("user", "where is Carman Hall?"))
        ));
        assertEquals("aside", chatGraph.routeFromRouter(s5));

        // In wifi walk normal continuation -> retrieve_respond
        ConversationState s6 = new ConversationState(Map.of(
                "stage", Stage.IN_WIFI_WALK,
                "topic", "wifi",
                "device", "macbook",
                "messages", List.of(new ChatMessage("user", "connected, what next?"))
        ));
        assertEquals("retrieve_respond", chatGraph.routeFromRouter(s6));

        // In reset normal continuation -> retrieve_respond
        ConversationState s7 = new ConversationState(Map.of(
                "stage", Stage.IN_RESET,
                "topic", "login",
                "subtopic", "reset",
                "messages", List.of(new ChatMessage("user", "reset done, what next?"))
        ));
        assertEquals("retrieve_respond", chatGraph.routeFromRouter(s7));

        // In reset out of band aside -> aside (does NOT drop stage)
        ConversationState s7b = new ConversationState(Map.of(
                "stage", Stage.IN_RESET,
                "topic", "login",
                "subtopic", "reset",
                "messages", List.of(new ChatMessage("user", "where is Carman Hall?"))
        ));
        assertEquals("aside", chatGraph.routeFromRouter(s7b));

        // In activation out of band aside -> aside (does NOT drop stage)
        ConversationState s7c = new ConversationState(Map.of(
                "stage", Stage.IN_ACTIVATION,
                "topic", "login",
                "subtopic", "activation",
                "messages", List.of(new ChatMessage("user", "where is the library?"))
        ));
        assertEquals("aside", chatGraph.routeFromRouter(s7c));

        // In fallback -> triage_intent (re-triages new message instead of dead-ending)
        ConversationState s8 = new ConversationState(Map.of("stage", Stage.FALLBACK, "topic", "unknown"));
        assertEquals("triage_intent", chatGraph.routeFromRouter(s8));
    }

    @Test
    void testRouteAfterTriage() {
        // Greeting -> greeting
        ConversationState s0 = new ConversationState(Map.of("topic", "greeting"));
        assertEquals("greeting", chatGraph.routeAfterTriage(s0));

        // Wifi with known device -> retrieve_respond
        ConversationState s1 = new ConversationState(Map.of("topic", "wifi", "device", "macbook"));
        assertEquals("retrieve_respond", chatGraph.routeAfterTriage(s1));

        // Wifi without device -> device_check
        ConversationState s2 = new ConversationState(Map.of("topic", "wifi"));
        assertEquals("device_check", chatGraph.routeAfterTriage(s2));

        // Login ambiguous (no subtopic) -> diagnostic
        ConversationState s3 = new ConversationState(Map.of("topic", "login"));
        assertEquals("diagnostic", chatGraph.routeAfterTriage(s3));

        // Login with reset -> retrieve_respond
        ConversationState s4 = new ConversationState(Map.of("topic", "login", "subtopic", "reset"));
        assertEquals("retrieve_respond", chatGraph.routeAfterTriage(s4));

        // Login with activation -> retrieve_respond
        ConversationState s5 = new ConversationState(Map.of("topic", "login", "subtopic", "activation"));
        assertEquals("retrieve_respond", chatGraph.routeAfterTriage(s5));

        // Unknown -> fallback
        ConversationState s6 = new ConversationState(Map.of("topic", "unknown"));
        assertEquals("fallback", chatGraph.routeAfterTriage(s6));
    }

    @Test
    void testRouteAfterDevice() {
        // Device detected -> retrieve_respond
        ConversationState s1 = new ConversationState(Map.of("stage", Stage.IN_WIFI_WALK, "device", "iphone"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDevice(s1));

        // Device not detected & unclear -> device_check
        ConversationState s2 = new ConversationState(Map.of("stage", Stage.AWAITING_DEVICE, "messages", List.of(new ChatMessage("user", "banana"))));
        assertEquals("device_check", chatGraph.routeAfterDevice(s2));

        // Device not detected & works fine -> device_ack_check
        ConversationState s2b = new ConversationState(Map.of("stage", Stage.AWAITING_DEVICE, "messages", List.of(new ChatMessage("user", "works fine"))));
        assertEquals("device_ack_check", chatGraph.routeAfterDevice(s2b));

        // Fallback stage -> fallback
        ConversationState s3 = new ConversationState(Map.of("stage", Stage.FALLBACK));
        assertEquals("fallback", chatGraph.routeAfterDevice(s3));

        // Diagnostic answer routed to reset (device is null) -> retrieve_respond
        ConversationState s4 = new ConversationState(Map.of("stage", Stage.IN_RESET, "topic", "login", "subtopic", "reset"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDevice(s4));

        // Diagnostic answer routed to activation (device is null) -> retrieve_respond
        ConversationState s5 = new ConversationState(Map.of("stage", Stage.IN_ACTIVATION, "topic", "login", "subtopic", "activation"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDevice(s5));
    }

    @Test
    void testRouteAfterDiagnostic() {
        // Awaiting device -> device_check
        ConversationState s1 = new ConversationState(Map.of("stage", Stage.AWAITING_DEVICE, "topic", "wifi"));
        assertEquals("device_check", chatGraph.routeAfterDiagnostic(s1));

        // In reset -> retrieve_respond
        ConversationState s2 = new ConversationState(Map.of("stage", Stage.IN_RESET, "topic", "login", "subtopic", "reset"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDiagnostic(s2));

        // In activation -> retrieve_respond
        ConversationState s3 = new ConversationState(Map.of("stage", Stage.IN_ACTIVATION, "topic", "login", "subtopic", "activation"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDiagnostic(s3));

        // In wifi walk -> retrieve_respond
        ConversationState s4 = new ConversationState(Map.of("stage", Stage.IN_WIFI_WALK, "topic", "wifi", "device", "macbook"));
        assertEquals("retrieve_respond", chatGraph.routeAfterDiagnostic(s4));

        // Fallback -> fallback
        ConversationState s5 = new ConversationState(Map.of("stage", Stage.FALLBACK));
        assertEquals("fallback", chatGraph.routeAfterDiagnostic(s5));
    }

    // ==========================================
    // 3. END-TO-END MULTI-TURN WORKFLOW TESTS
    // ==========================================

    @Test
    void testEndToEndColdLoginToPasswordReset() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        Document resetDoc = new Document("Reset steps", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(resetDoc));
        when(responseSpec.content())
                .thenReturn("Follow these reset steps at managelogin.lehman.edu");

        String convId = "conv-login-reset-1";

        // Turn 1: Cold login entry ("my password won't work")
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "my password won't work"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state1.getStage());
        assertEquals("login", state1.getTopic());
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Terse reply ("no") -> routes deterministically to reset guide
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "no"), null);
        assertEquals(Stage.IN_RESET, state2.getStage());
        assertEquals("login", state2.getTopic());
        assertEquals("reset", state2.getSubtopic());
        assertEquals("Follow these reset steps at managelogin.lehman.edu", state2.lastMessage().get().content());

        // Verify IntentRouter was only called ONCE during triage, not on turn 2
        verify(intentRouter, times(1)).route(any());
    }

    @Test
    void testEndToEndColdLoginToAccountActivation() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        Document actDoc = new Document("Activation steps", Map.of("topic", "login", "subtopic", "activation"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("activation")), eq(1), eq(0.0)))
                .thenReturn(List.of(actDoc));
        when(responseSpec.content())
                .thenReturn("Follow these activation steps (2 business days after CUNYfirst)");

        String convId = "conv-login-act-1";

        // Turn 1: Cold login entry
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "can't log into anything"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state1.getStage());

        // Turn 2: Brand new student
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "first semester here, never set it up"), null);
        assertEquals(Stage.IN_ACTIVATION, state2.getStage());
        assertEquals("login", state2.getTopic());
        assertEquals("activation", state2.getSubtopic());
        assertEquals("Follow these activation steps (2 business days after CUNYfirst)", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndWifiWalkWithMidWalkPasswordDiagnosticFork() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document wifiDoc = new Document("MacBook wifi guide", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(wifiDoc));
        when(responseSpec.content())
                .thenReturn("Select Lehman-WiFi and enter credentials")
                .thenReturn("Since your login works elsewhere, check your username format without @lehman.cuny.edu");

        String convId = "conv-wifi-fork-1";

        // Turn 1: Wifi complaint without device
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi won't connect"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student replies "macbook"
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "macbook"), null);
        assertEquals(Stage.IN_WIFI_WALK, state2.getStage());
        assertEquals("macbook", state2.getDevice());
        assertEquals("Select Lehman-WiFi and enter credentials", state2.lastMessage().get().content());

        // Turn 3: Password rejected at wifi login prompt -> triggers diagnostic
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "it says invalid password"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state3.getStage());
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, state3.lastMessage().get().content());

        // Turn 4: Student says "works fine on my phone" (works elsewhere) -> resumes wifi walk with macbook
        ConversationState state4 = chatGraph.execute(convId, new ChatMessage("user", "works fine on my phone"), null);
        assertEquals(Stage.IN_WIFI_WALK, state4.getStage());
        assertEquals("macbook", state4.getDevice());
        assertEquals("Since your login works elsewhere, check your username format without @lehman.cuny.edu", state4.lastMessage().get().content());
    }

    @Test
    void testEndToEndColdLoginToWorksElsewhereToDeviceCheckToWifiSteps() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        Document win11Doc = new Document("Windows 11 Wi-Fi steps", Map.of("topic", "wifi", "device", "windows-11"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("windows-11")), eq(1), eq(0.0)))
                .thenReturn(List.of(win11Doc));
        when(responseSpec.content()).thenReturn("Select Lehman-WiFi in Windows 11 settings and connect");

        String convId = "conv-login-to-wifi-1";

        // Turn 1: Cold ambiguous login complaint
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "my login is failing"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state1.getStage());
        assertEquals("login", state1.getTopic());
        assertEquals(ChatGraph.DIAGNOSTIC_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student says it works fine on email -> routes to device_check (since device is unknown)
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "works fine on my phone"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals("wifi", state2.getTopic());
        assertNull(state2.getSubtopic());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state2.lastMessage().get().content());

        // Turn 3: Student specifies device -> routes to retrieve_respond with wifi topic and windows-11 device
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "Windows 11 laptop"), null);
        assertEquals(Stage.IN_WIFI_WALK, state3.getStage());
        assertEquals("wifi", state3.getTopic());
        assertEquals("windows-11", state3.getDevice());
        assertEquals("Select Lehman-WiFi in Windows 11 settings and connect", state3.lastMessage().get().content());
    }

    @Test
    void testEndToEndOutOfBandInterruptionDoesNotCorruptState() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        when(responseSpec.content()).thenReturn("Carman Hall 108 is where the IT help desk is. Coming back to your password: does it work on email?");
        String convId = "conv-oob-1";

        // Turn 1: Cold login entry -> diagnostic
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "my password won't work"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state1.getStage());

        // Turn 2: Out of band aside interruption -> aside node answers and preserves AWAITING_DIAGNOSTIC
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "wait where is Carman Hall?"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state2.getStage());
        assertEquals("Carman Hall 108 is where the IT help desk is. Coming back to your password: does it work on email?", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndAwaitingDeviceToDiagnosticAnswerPasswordRejected() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document resetDoc = new Document("Password reset guide", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(resetDoc));
        when(responseSpec.content()).thenReturn("Here are the reset steps for Lehman 360 login.");

        String convId = "conv-await-dev-to-reset";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "can't connect to wifi"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student gives diagnostic answer instead of device ("no it says password rejected")
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "no it says password rejected"), null);
        assertEquals(Stage.IN_RESET, state2.getStage());
        assertEquals("login", state2.getTopic());
        assertEquals("reset", state2.getSubtopic());
        assertEquals("Here are the reset steps for Lehman 360 login.", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndAwaitingDeviceToDiagnosticAnswerNeverActivated() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document actDoc = new Document("Activation guide", Map.of("topic", "login", "subtopic", "activation"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("activation")), eq(1), eq(0.0)))
                .thenReturn(List.of(actDoc));
        when(responseSpec.content()).thenReturn("Here are the activation steps for new students.");

        String convId = "conv-await-dev-to-act";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi isn't working"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());

        // Turn 2: Student says brand new student / never activated
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "first semester here, never set it up"), null);
        assertEquals(Stage.IN_ACTIVATION, state2.getStage());
        assertEquals("login", state2.getTopic());
        assertEquals("activation", state2.getSubtopic());
        assertEquals("Here are the activation steps for new students.", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndAwaitingDeviceLoopsAtMostTwiceBeforeFallback() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        String convId = "conv-retry-fallback";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE (attempt 0)
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi issue"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: First unclear input ("huh?") -> attempt 1 -> re-asks device
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "huh?"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals(1, state2.getDeviceRetryCount());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state2.lastMessage().get().content());

        // Turn 3: Second unclear input ("what?") -> attempt 2 -> routes to FALLBACK
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "what?"), null);
        assertEquals(Stage.FALLBACK, state3.getStage());
        assertEquals(2, state3.getDeviceRetryCount());
        assertEquals(ChatGraph.FALLBACK_PROMPT, state3.lastMessage().get().content());
    }

    @Test
    void testEndToEndAwaitingDeviceRecoversOnSecondTry() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document iphoneDoc = new Document("iPhone Wi-Fi guide", Map.of("topic", "wifi", "device", "iphone"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("iphone")), eq(1), eq(0.0)))
                .thenReturn(List.of(iphoneDoc));
        when(responseSpec.content()).thenReturn("Here are the iPhone Wi-Fi steps.");

        String convId = "conv-retry-recover";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi problem"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());

        // Turn 2: Unclear response ("what do you mean") -> attempt 1
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "what do you mean"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals(1, state2.getDeviceRetryCount());

        // Turn 3: User answers with valid device ("iPhone") -> successfully enters IN_WIFI_WALK
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "iPhone"), null);
        assertEquals(Stage.IN_WIFI_WALK, state3.getStage());
        assertEquals("iphone", state3.getDevice());
        assertEquals(0, state3.getDeviceRetryCount());
        assertEquals("Here are the iPhone Wi-Fi steps.", state3.lastMessage().get().content());
    }

    @Test
    void testEndToEndMidResetOutOfBandAsidePreservesFlow() {
        when(intentRouter.route(any())).thenReturn(Intent.login(null));
        Document resetDoc = new Document("Password reset steps", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(resetDoc));
        when(responseSpec.content())
                .thenReturn("Step 1: Go to managelogin.lehman.edu")
                .thenReturn("Carman Hall is on the west side of campus. Returning to your reset: now enter your email.")
                .thenReturn("Step 2: Enter your verification code.");

        String convId = "conv-mid-reset-aside";

        // Turn 1: Cold login
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "password rejected"), null);
        assertEquals(Stage.AWAITING_DIAGNOSTIC, state1.getStage());

        // Turn 2: Password rejected across the board -> IN_RESET
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "no doesn't work"), null);
        assertEquals(Stage.IN_RESET, state2.getStage());
        assertEquals("Step 1: Go to managelogin.lehman.edu", state2.lastMessage().get().content());

        // Turn 3: Out-of-band aside mid-reset ("where is Carman Hall?") -> LLM handles aside, stage remains IN_RESET
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "where is Carman Hall?"), null);
        assertEquals(Stage.IN_RESET, state3.getStage());
        assertEquals("Carman Hall is on the west side of campus. Returning to your reset: now enter your email.", state3.lastMessage().get().content());

        // Turn 4: Continuation of reset walk -> still in IN_RESET
        ConversationState state4 = chatGraph.execute(convId, new ChatMessage("user", "done, what next?"), null);
        assertEquals(Stage.IN_RESET, state4.getStage());
        assertEquals("Step 2: Enter your verification code.", state4.lastMessage().get().content());

        // Document retrieval must only be called for the 2 reset turns, NOT during the aside
        verify(retrievalService, times(2)).search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0));
    }

    @Test
    void testEndToEndMidWifiWalkOutOfBandAsidePreservesFlow() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document wifiDoc = new Document("MacBook wifi steps", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(wifiDoc));
        when(responseSpec.content())
                .thenReturn("Step 1: Open Wi-Fi settings on your MacBook.")
                .thenReturn("Carman Hall 108 is the IT help desk. Continuing Wi-Fi setup: select Lehman-WiFi.")
                .thenReturn("Step 2: Enter your Lehman login credentials.");

        String convId = "conv-mid-wifi-aside";

        // Turn 1: Wifi on macbook -> IN_WIFI_WALK
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "how to connect wifi on macbook"), "macbook");
        assertEquals(Stage.IN_WIFI_WALK, state1.getStage());
        assertEquals("Step 1: Open Wi-Fi settings on your MacBook.", state1.lastMessage().get().content());

        // Turn 2: Out-of-band aside ("where is Carman Hall 108?") -> LLM handles aside, stage remains IN_WIFI_WALK
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "where is Carman Hall 108?"), null);
        assertEquals(Stage.IN_WIFI_WALK, state2.getStage());
        assertEquals("Carman Hall 108 is the IT help desk. Continuing Wi-Fi setup: select Lehman-WiFi.", state2.lastMessage().get().content());

        // Turn 3: Continuation of wifi walk -> still in IN_WIFI_WALK
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "ok what next?"), null);
        assertEquals(Stage.IN_WIFI_WALK, state3.getStage());
        assertEquals("Step 2: Enter your Lehman login credentials.", state3.lastMessage().get().content());

        // Document retrieval must only be called for the 2 wifi walk turns, NOT during the aside
        verify(retrievalService, times(2)).search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0));
    }

    @Test
    void testEndToEndAwaitingDeviceWithAsidePreservesAwaitingDevice() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document winDoc = new Document("Windows 11 Wi-Fi steps", Map.of("topic", "wifi", "device", "windows-11"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("windows-11")), eq(1), eq(0.0)))
                .thenReturn(List.of(winDoc));
        when(responseSpec.content())
                .thenReturn("Carman Hall is on campus, room 108 is the desk. What device are you on?")
                .thenReturn("Step 1: Open network settings on Windows 11.");

        String convId = "conv-await-dev-aside";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi broken"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());

        // Turn 2: Out-of-band aside while awaiting device -> aside node answers, stage remains AWAITING_DEVICE
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "where is Carman Hall?"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals("Carman Hall is on campus, room 108 is the desk. What device are you on?", state2.lastMessage().get().content());

        // Turn 3: User answers device -> transitions to IN_WIFI_WALK
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "windows 11"), null);
        assertEquals(Stage.IN_WIFI_WALK, state3.getStage());
        assertEquals("windows-11", state3.getDevice());
        assertEquals("Step 1: Open network settings on Windows 11.", state3.lastMessage().get().content());

        // Search was only called on turn 3
        verify(retrievalService, times(1)).search(anyString(), eq(FilterSpec.wifi("windows-11")), eq(1), eq(0.0));
    }

    @Test
    void testEndToEndMultiTurnMessageAccumulationNoDuplicates() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document macDoc = new Document("Mac guide", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(macDoc));
        when(responseSpec.content())
                .thenReturn("Step 1: Turn Wi-Fi off and on.")
                .thenReturn("Step 2: Select Lehman-WiFi.");

        String convId = "conv-messages-accum";

        // Turn 1: User sends message 1 -> state has exactly 2 messages (user, assistant)
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi help macbook"), "macbook");
        assertEquals(2, state1.messages().size());
        assertEquals("user", state1.messages().get(0).role());
        assertEquals("wifi help macbook", state1.messages().get(0).content());
        assertEquals("assistant", state1.messages().get(1).role());
        assertEquals("Step 1: Turn Wi-Fi off and on.", state1.messages().get(1).content());

        // Turn 2: User sends message 2 -> state has exactly 4 messages
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "done"), null);
        assertEquals(4, state2.messages().size());
        assertEquals("user", state2.messages().get(0).role());
        assertEquals("wifi help macbook", state2.messages().get(0).content());
        assertEquals("assistant", state2.messages().get(1).role());
        assertEquals("Step 1: Turn Wi-Fi off and on.", state2.messages().get(1).content());
        assertEquals("user", state2.messages().get(2).role());
        assertEquals("done", state2.messages().get(2).content());
        assertEquals("assistant", state2.messages().get(3).role());
        assertEquals("Step 2: Select Lehman-WiFi.", state2.messages().get(3).content());
    }

    @Test
    void testEndToEndAwaitingDeviceWorksFineAcknowledgesAndProceeds() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        Document macDoc = new Document("MacBook Wi-Fi steps", Map.of("topic", "wifi", "device", "macbook"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0)))
                .thenReturn(List.of(macDoc));
        when(responseSpec.content()).thenReturn("Step 1: Open Wi-Fi settings on your MacBook.");

        String convId = "conv-await-dev-works-fine-proceed";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi broken"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student says "works fine" (answering diagnostic instead of device) -> Acknowledges and re-prompts for device
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "works fine"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals(1, state2.getDeviceRetryCount());
        assertEquals("wifi", state2.getTopic());
        assertNull(state2.getSubtopic());
        assertEquals(ChatGraph.DEVICE_CHECK_ACKNOWLEDGED_PROMPT, state2.lastMessage().get().content());

        // Turn 3: Student specifies device ("MacBook") -> proceeds to IN_WIFI_WALK
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "MacBook"), null);
        assertEquals(Stage.IN_WIFI_WALK, state3.getStage());
        assertEquals("macbook", state3.getDevice());
        assertEquals(0, state3.getDeviceRetryCount());
        assertEquals("Step 1: Open Wi-Fi settings on your MacBook.", state3.lastMessage().get().content());

        // Verification: Search was called once for macbook on Turn 3
        verify(retrievalService, times(1)).search(anyString(), eq(FilterSpec.wifi("macbook")), eq(1), eq(0.0));
    }

    @Test
    void testEndToEndAwaitingDeviceWorksFineTwiceTriggersFallback() {
        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        String convId = "conv-await-dev-works-fine-fallback";

        // Turn 1: Wifi complaint without device -> AWAITING_DEVICE
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "wifi broken"), null);
        assertEquals(Stage.AWAITING_DEVICE, state1.getStage());
        assertEquals(ChatGraph.DEVICE_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student says "works fine" -> Acknowledges and re-prompts for device (retry 1)
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "works fine"), null);
        assertEquals(Stage.AWAITING_DEVICE, state2.getStage());
        assertEquals(1, state2.getDeviceRetryCount());
        assertEquals(ChatGraph.DEVICE_CHECK_ACKNOWLEDGED_PROMPT, state2.lastMessage().get().content());

        // Turn 3: Student says "works fine" again -> triggers fallback (retry 2)
        ConversationState state3 = chatGraph.execute(convId, new ChatMessage("user", "works fine"), null);
        assertEquals(Stage.FALLBACK, state3.getStage());
        assertEquals(2, state3.getDeviceRetryCount());
        assertEquals(ChatGraph.FALLBACK_PROMPT, state3.lastMessage().get().content());
    }

    @Test
    void testEndToEndGreetingWarmRepromptThenWifiWalk() {
        when(intentRouter.route(any()))
                .thenReturn(Intent.greeting())
                .thenReturn(Intent.wifi());
        Document win11Doc = new Document("Windows 11 Wi-Fi steps", Map.of("topic", "wifi", "device", "windows-11"));
        when(retrievalService.search(anyString(), eq(FilterSpec.wifi("windows-11")), eq(1), eq(0.0)))
                .thenReturn(List.of(win11Doc));
        when(responseSpec.content()).thenReturn("Select Lehman-WiFi in Windows 11 settings and connect");

        String convId = "conv-greeting-then-wifi";

        // Turn 1: Student says "hi" -> warm re-prompt, stage stays TRIAGE (never enters FALLBACK)
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "hi"), null);
        assertEquals(Stage.TRIAGE, state1.getStage());
        assertEquals(ChatGraph.GREETING_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student asks real question -> routes cleanly to Wi-Fi walk
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "how to connect wifi on windows 11"), "windows-11");
        assertEquals(Stage.IN_WIFI_WALK, state2.getStage());
        assertEquals("windows-11", state2.getDevice());
        assertEquals("Select Lehman-WiFi in Windows 11 settings and connect", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndFallbackRecoveryToPasswordReset() {
        when(intentRouter.route(any()))
                .thenReturn(Intent.unknown())
                .thenReturn(Intent.login("reset"));
        Document resetDoc = new Document("Reset steps", Map.of("topic", "login", "subtopic", "reset"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset")), eq(1), eq(0.0)))
                .thenReturn(List.of(resetDoc));
        when(responseSpec.content()).thenReturn("Follow these reset steps at managelogin.lehman.edu");

        String convId = "conv-fallback-recover-reset";

        // Turn 1: Unknown question -> routes to FALLBACK
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "what time does the cafeteria close?"), null);
        assertEquals(Stage.FALLBACK, state1.getStage());
        assertEquals(ChatGraph.FALLBACK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Student now types a valid tech question -> recovers from FALLBACK to IN_RESET
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "how do i reset my lehman password?"), null);
        assertEquals(Stage.IN_RESET, state2.getStage());
        assertEquals("login", state2.getTopic());
        assertEquals("reset", state2.getSubtopic());
        assertEquals("Follow these reset steps at managelogin.lehman.edu", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndFallbackPersistsIfStillUnknown() {
        when(intentRouter.route(any()))
                .thenReturn(Intent.unknown())
                .thenReturn(Intent.unknown());

        String convId = "conv-fallback-persist";

        // Turn 1: Unknown question -> routes to FALLBACK
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "what is the weather today?"), null);
        assertEquals(Stage.FALLBACK, state1.getStage());
        assertEquals(ChatGraph.FALLBACK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: Another unknown question -> re-triages, still unknown, falls back again
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "who is the mayor?"), null);
        assertEquals(Stage.FALLBACK, state2.getStage());
        assertEquals(ChatGraph.FALLBACK_PROMPT, state2.lastMessage().get().content());
    }

    @Test
    void testConstructorInjectionWithCustomCheckpointSaver() {
        org.bsc.langgraph4j.checkpoint.MemorySaver customSaver = new org.bsc.langgraph4j.checkpoint.MemorySaver();
        ChatGraph graphWithCustomSaver = new ChatGraph(chatClient, retrievalService, intentRouter, TEMPLATE, customSaver);

        assertSame(customSaver, graphWithCustomSaver.getCheckpointSaver());

        when(intentRouter.route(any())).thenReturn(Intent.wifi());
        ConversationState result = graphWithCustomSaver.execute("custom-saver-thread", new ChatMessage("user", "wifi broken"), null);
        assertNotNull(result);
        assertEquals(Stage.AWAITING_DEVICE, result.getStage());
    }

    // ==========================================
    // MFA & ACCOUNT DISAMBIGUATION TESTS
    // ==========================================

    @Test
    void testTriageNodeMfaWithAccount() {
        when(intentRouter.route(any())).thenReturn(Intent.mfa("cuny"));
        ConversationState state = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("user", "MFA error on Brightspace"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("mfa", updates.get("topic"));
        assertEquals("cuny", updates.get("account"));
    }

    @Test
    void testTriageNodeMfaWithoutAccount() {
        when(intentRouter.route(any())).thenReturn(new Intent(Intent.Topic.MFA, null, null));
        ConversationState state = new ConversationState(Map.of(
                "messages", List.of(new ChatMessage("user", "MFA isn't working"))
        ));

        Map<String, Object> updates = chatGraph.triageNode(state);
        assertEquals("mfa", updates.get("topic"));
        assertNull(updates.get("account"));
    }

    @Test
    void testAccountCheckNode() {
        ConversationState state = new ConversationState(Map.of());
        Map<String, Object> updates = chatGraph.accountCheckNode(state);

        assertEquals(Stage.AWAITING_ACCOUNT, updates.get("stage"));
        @SuppressWarnings("unchecked")
        List<ChatMessage> msgs = (List<ChatMessage>) updates.get("messages");
        assertNotNull(msgs);
        assertEquals(1, msgs.size());
        assertEquals(ChatGraph.ACCOUNT_CHECK_PROMPT, msgs.get(0).content());
    }

    @Test
    void testHandleAccountNodeCuny() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "stage", Stage.AWAITING_ACCOUNT,
                "messages", List.of(new ChatMessage("user", "CUNYfirst"))
        ));

        Map<String, Object> updates = chatGraph.handleAccountNode(state);
        assertEquals("cuny", updates.get("account"));
        assertEquals(Stage.IN_MFA, updates.get("stage"));
        assertEquals(0, updates.get("accountRetryCount"));
    }

    @Test
    void testHandleAccountNodeMicrosoft() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "stage", Stage.AWAITING_ACCOUNT,
                "messages", List.of(new ChatMessage("user", "Outlook"))
        ));

        Map<String, Object> updates = chatGraph.handleAccountNode(state);
        assertEquals("microsoft365", updates.get("account"));
        assertEquals(Stage.IN_MFA, updates.get("stage"));
        assertEquals(0, updates.get("accountRetryCount"));
    }

    @Test
    void testHandleAccountNodeRetryClarification() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "stage", Stage.AWAITING_ACCOUNT,
                "accountRetryCount", 0,
                "messages", List.of(new ChatMessage("user", "i don't know"))
        ));

        Map<String, Object> updates = chatGraph.handleAccountNode(state);
        assertNull(updates.get("account"));
        assertEquals(Stage.AWAITING_ACCOUNT, updates.get("stage"));
        assertEquals(1, updates.get("accountRetryCount"));
    }

    @Test
    void testHandleAccountNodeFallbackAfterTwoAttempts() {
        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "stage", Stage.AWAITING_ACCOUNT,
                "accountRetryCount", 1,
                "messages", List.of(new ChatMessage("user", "still unsure"))
        ));

        Map<String, Object> updates = chatGraph.handleAccountNode(state);
        assertEquals(Stage.FALLBACK, updates.get("stage"));
        assertEquals(2, updates.get("accountRetryCount"));
    }

    @Test
    void testRetrieveRespondNodeMfaCuny() {
        Document doc = new Document("CUNY Login MFA steps", Map.of("topic", "mfa", "account", "cuny"));
        when(retrievalService.search(eq("MFA error"), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Go to ssologin.cuny.edu/oaa/rui");

        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "account", "cuny",
                "stage", Stage.AWAITING_ACCOUNT,
                "messages", List.of(new ChatMessage("user", "MFA error"))
        ));

        Map<String, Object> updates = chatGraph.retrieveRespondNode(state);
        assertEquals(Stage.IN_MFA, updates.get("stage"));
        verify(retrievalService).search(eq("MFA error"), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0));
    }

    @Test
    void testRetrieveRespondNodeMfaMicrosoft() {
        Document doc = new Document("M365 MFA steps", Map.of("topic", "mfa", "account", "microsoft365"));
        when(retrievalService.search(eq("Outlook authenticator"), eq(FilterSpec.mfa("microsoft365")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Follow Microsoft MFA prompts");

        ConversationState state = new ConversationState(Map.of(
                "topic", "mfa",
                "account", "microsoft365",
                "stage", Stage.TRIAGE,
                "messages", List.of(new ChatMessage("user", "Outlook authenticator"))
        ));

        Map<String, Object> updates = chatGraph.retrieveRespondNode(state);
        assertEquals(Stage.IN_MFA, updates.get("stage"));
        verify(retrievalService).search(eq("Outlook authenticator"), eq(FilterSpec.mfa("microsoft365")), eq(1), eq(0.0));
    }

    @Test
    void testEndToEndMfaBrightspaceDirectWalk() {
        when(intentRouter.route(any())).thenReturn(Intent.mfa("cuny"));
        Document doc = new Document("CUNY MFA steps", Map.of("topic", "mfa", "account", "cuny"));
        when(retrievalService.search(anyString(), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Go to the CUNY Self-Service portal at ssologin.cuny.edu/oaa/rui");

        ConversationState result = chatGraph.execute("conv-mfa-brightspace", new ChatMessage("user", "MFA error on Brightspace"), null);
        assertEquals(Stage.IN_MFA, result.getStage());
        assertEquals("mfa", result.getTopic());
        assertEquals("cuny", result.getAccount());
        assertEquals("Go to the CUNY Self-Service portal at ssologin.cuny.edu/oaa/rui", result.lastMessage().get().content());
    }

    @Test
    void testEndToEndMfaOutlookDirectWalk() {
        when(intentRouter.route(any())).thenReturn(Intent.mfa("microsoft365"));
        Document doc = new Document("M365 MFA steps", Map.of("topic", "mfa", "account", "microsoft365"));
        when(retrievalService.search(anyString(), eq(FilterSpec.mfa("microsoft365")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Open Microsoft Authenticator for Outlook");

        ConversationState result = chatGraph.execute("conv-mfa-outlook", new ChatMessage("user", "can't set up authenticator for Outlook"), null);
        assertEquals(Stage.IN_MFA, result.getStage());
        assertEquals("mfa", result.getTopic());
        assertEquals("microsoft365", result.getAccount());
        assertEquals("Open Microsoft Authenticator for Outlook", result.lastMessage().get().content());
    }

    @Test
    void testEndToEndMfaAmbiguousDisambiguationFlow() {
        when(intentRouter.route(any())).thenReturn(new Intent(Intent.Topic.MFA, null, null));
        Document doc = new Document("CUNY MFA steps", Map.of("topic", "mfa", "account", "cuny"));
        when(retrievalService.search(anyString(), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Here are the CUNY MFA steps");

        String convId = "conv-mfa-ambiguous";

        // Turn 1: "MFA isn't working" -> asks CUNY or Microsoft
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "MFA isn't working"), null);
        assertEquals(Stage.AWAITING_ACCOUNT, state1.getStage());
        assertEquals(ChatGraph.ACCOUNT_CHECK_PROMPT, state1.lastMessage().get().content());

        // Turn 2: "CUNYfirst" -> routes right to CUNY MFA doc
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "CUNYfirst"), null);
        assertEquals(Stage.IN_MFA, state2.getStage());
        assertEquals("mfa", state2.getTopic());
        assertEquals("cuny", state2.getAccount());
        assertEquals("Here are the CUNY MFA steps", state2.lastMessage().get().content());
    }

    @Test
    void testEndToEndMfaMidWalkContinuation() {
        when(intentRouter.route(any())).thenReturn(Intent.mfa("cuny"));
        Document doc = new Document("CUNY MFA steps", Map.of("topic", "mfa", "account", "cuny"));
        when(retrievalService.search(anyString(), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content())
                .thenReturn("Step 1: Navigate to ssologin.cuny.edu/oaa/rui")
                .thenReturn("Step 2: Scan the QR code with your authenticator app");

        String convId = "conv-mfa-midwalk";

        // Turn 1: Start MFA walk
        ConversationState state1 = chatGraph.execute(convId, new ChatMessage("user", "MFA setup for CUNYfirst"), null);
        assertEquals(Stage.IN_MFA, state1.getStage());
        assertEquals("cuny", state1.getAccount());
        assertEquals("Step 1: Navigate to ssologin.cuny.edu/oaa/rui", state1.lastMessage().get().content());

        // Turn 2: Follow-up mid-walk
        ConversationState state2 = chatGraph.execute(convId, new ChatMessage("user", "ok did that what is next"), null);
        assertEquals(Stage.IN_MFA, state2.getStage());
        assertEquals("cuny", state2.getAccount());
        assertEquals("Step 2: Scan the QR code with your authenticator app", state2.lastMessage().get().content());
        verify(retrievalService, times(2)).search(anyString(), eq(FilterSpec.mfa("cuny")), eq(1), eq(0.0));
    }

    @Test
    void testLoginResetWithAccountLehmanRegression() {
        when(intentRouter.route(any())).thenReturn(Intent.login("reset", "lehman"));
        Document doc = new Document("Lehman password reset steps", Map.of("topic", "login", "subtopic", "reset", "account", "lehman"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("reset", "lehman")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Visit the Lehman password reset portal");

        ConversationState result = chatGraph.execute("conv-login-reset-regression", new ChatMessage("user", "I forgot my password"), null);
        assertEquals(Stage.IN_RESET, result.getStage());
        assertEquals("login", result.getTopic());
        assertEquals("reset", result.getSubtopic());
        assertEquals("lehman", result.getAccount());
        assertEquals("Visit the Lehman password reset portal", result.lastMessage().get().content());
        verify(retrievalService).search(anyString(), eq(FilterSpec.login("reset", "lehman")), eq(1), eq(0.0));
    }

    @Test
    void testLoginActivationWithAccountLehmanRegression() {
        when(intentRouter.route(any())).thenReturn(Intent.login("activation", "lehman"));
        Document doc = new Document("Lehman account activation steps", Map.of("topic", "login", "subtopic", "activation", "account", "lehman"));
        when(retrievalService.search(anyString(), eq(FilterSpec.login("activation", "lehman")), eq(1), eq(0.0)))
                .thenReturn(List.of(doc));
        when(responseSpec.content()).thenReturn("Follow Lehman first-time account activation");

        ConversationState result = chatGraph.execute("conv-login-act-regression", new ChatMessage("user", "new student activating my account"), null);
        assertEquals(Stage.IN_ACTIVATION, result.getStage());
        assertEquals("login", result.getTopic());
        assertEquals("activation", result.getSubtopic());
        assertEquals("lehman", result.getAccount());
        assertEquals("Follow Lehman first-time account activation", result.lastMessage().get().content());
        verify(retrievalService).search(anyString(), eq(FilterSpec.login("activation", "lehman")), eq(1), eq(0.0));
    }
}
