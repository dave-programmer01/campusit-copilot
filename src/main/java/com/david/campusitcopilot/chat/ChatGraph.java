package com.david.campusitcopilot.chat;

import com.david.campusitcopilot.search.FilterSpec;
import com.david.campusitcopilot.search.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stateful conversation workflow built with LangGraph4j.
 * <p>
 * Manages deterministic stage transitions (triage, device check, diagnostic fork,
 * retrieval & walk, fallback), replacing prompt-based state guessing with a graph state machine.
 */
@Slf4j
@Service
public class ChatGraph {

    public static final String DEVICE_CHECK_PROMPT =
            "What device are you using? (MacBook, Windows 10, Windows 11, iPhone, or Android)";

    public static final String DEVICE_CHECK_ACKNOWLEDGED_PROMPT =
            "Got it, login's fine — which device are you trying to connect? (MacBook, Windows 10, Windows 11, iPhone, or Android)";

    public static final String DIAGNOSTIC_PROMPT =
            "quick check — when you try to sign into your Lehman email or the portal, does that password work?";

    public static final String GREETING_PROMPT =
            "hey! how can i help you today? let me know what tech issue you're running into (wifi, lehman login, passwords, etc.).";

    public static final String FALLBACK_PROMPT =
            "ok, this one's past what i can walk you through quick. here's your move: hop on LehmanQ at lehman.edu/q to grab a spot without standing in line, or swing by Carman Hall 108. want me to tell you which is faster right now?";

    private static final String DEVICE_UNKNOWN =
            "unknown → Not yet known. If this is a wifi issue, your FIRST job is to ask which device "
                    + "they're on: MacBook, Windows, iPhone, or Android. Do not give steps until you know.";

    private static final String EMPTY_CONTEXT =
            "(No verified steps loaded yet. Ask what device they're on (for wifi), run the diagnostic check (for password/login), or route to the desk — do not invent steps.)";

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final IntentRouter intentRouter;
    private final String systemTemplate;
    private final BaseCheckpointSaver checkpointSaver;
    private final CompiledGraph<ConversationState> compiledGraph;

    @Autowired
    public ChatGraph(ChatClient.Builder chatClientBuilder,
                     RetrievalService retrievalService,
                     IntentRouter intentRouter,
                     @Value("classpath:/prompts/system-prompt.md") Resource systemPromptResource,
                     BaseCheckpointSaver checkpointSaver) {
        this(chatClientBuilder.build(), retrievalService, intentRouter, readResource(systemPromptResource), checkpointSaver);
    }

    public ChatGraph(ChatClient chatClient,
                     RetrievalService retrievalService,
                     IntentRouter intentRouter,
                     String systemTemplate,
                     BaseCheckpointSaver checkpointSaver) {
        this.chatClient = chatClient;
        this.retrievalService = retrievalService;
        this.intentRouter = intentRouter;
        this.systemTemplate = systemTemplate;
        this.checkpointSaver = checkpointSaver;
        this.compiledGraph = buildAndCompileGraph();
    }

    public ChatGraph(ChatClient chatClient,
                     RetrievalService retrievalService,
                     IntentRouter intentRouter,
                     String systemTemplate) {
        this(chatClient, retrievalService, intentRouter, systemTemplate, new MemorySaver());
    }

    private CompiledGraph<ConversationState> buildAndCompileGraph() {
        try {
            StateGraph<ConversationState> workflow = new StateGraph<>(ConversationState.SCHEMA, ConversationState::new);

            // Transition and routing nodes
            workflow.addNode("router", node_async(this::routerNode));
            workflow.addNode("triage_intent", node_async(this::triageNode));
            workflow.addNode("handle_device", node_async(this::handleDeviceNode));
            workflow.addNode("handle_diagnostic", node_async(this::handleDiagnosticNode));

            // Terminal response nodes
            workflow.addNode("device_check", node_async(this::deviceCheckNode));
            workflow.addNode("device_ack_check", node_async(this::deviceAckCheckNode));
            workflow.addNode("diagnostic", node_async(this::diagnosticNode));
            workflow.addNode("retrieve_respond", node_async(this::retrieveRespondNode));
            workflow.addNode("greeting", node_async(this::greetingNode));
            workflow.addNode("aside", node_async(this::asideNode));
            workflow.addNode("fallback", node_async(this::fallbackNode));

            // Connect START to router
            workflow.addEdge(START, "router");

            // Conditional routing from entry router based on state/stage
            workflow.addConditionalEdges(
                    "router",
                    edge_async(this::routeFromRouter),
                    Map.of(
                            "triage_intent", "triage_intent",
                            "handle_device", "handle_device",
                            "handle_diagnostic", "handle_diagnostic",
                            "diagnostic", "diagnostic",
                            "retrieve_respond", "retrieve_respond",
                            "aside", "aside",
                            "fallback", "fallback"
                    )
            );

            // Conditional routing after triage
            workflow.addConditionalEdges(
                    "triage_intent",
                    edge_async(this::routeAfterTriage),
                    Map.of(
                            "device_check", "device_check",
                            "diagnostic", "diagnostic",
                            "retrieve_respond", "retrieve_respond",
                            "greeting", "greeting",
                            "fallback", "fallback"
                    )
            );

            // Conditional routing after device handling
            workflow.addConditionalEdges(
                    "handle_device",
                    edge_async(this::routeAfterDevice),
                    Map.of(
                            "device_check", "device_check",
                            "device_ack_check", "device_ack_check",
                            "retrieve_respond", "retrieve_respond",
                            "fallback", "fallback"
                    )
            );

            // Conditional routing after diagnostic handling
            workflow.addConditionalEdges(
                    "handle_diagnostic",
                    edge_async(this::routeAfterDiagnostic),
                    Map.of(
                            "device_check", "device_check",
                            "retrieve_respond", "retrieve_respond",
                            "fallback", "fallback"
                    )
            );

            // Terminal edges to END
            workflow.addEdge("device_check", END);
            workflow.addEdge("device_ack_check", END);
            workflow.addEdge("diagnostic", END);
            workflow.addEdge("retrieve_respond", END);
            workflow.addEdge("greeting", END);
            workflow.addEdge("aside", END);
            workflow.addEdge("fallback", END);

            CompileConfig compileConfig = CompileConfig.builder()
                    .checkpointSaver(this.checkpointSaver)
                    .build();

            return workflow.compile(compileConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to construct and compile ChatGraph", e);
        }
    }

    public Map<String, Object> routerNode(ConversationState state) {
        log.info("Router node: evaluating stage={}, topic={}, device={}, subtopic={}",
                state.getStage(), state.getTopic(), state.getDevice(), state.getSubtopic());
        return Map.of();
    }

    /**
     * Triage node — runs IntentRouter only when topic is unset or needs re-classification; writes topic to state.
     */
    public Map<String, Object> triageNode(ConversationState state) {
        if (state.topic().isPresent() && StringUtils.hasText(state.getTopic())
                && !"unknown".equalsIgnoreCase(state.getTopic())
                && !"greeting".equalsIgnoreCase(state.getTopic())
                && state.getStage() != Stage.FALLBACK) {
            log.info("Triage node: topic already set to '{}', skipping IntentRouter.", state.getTopic());
            return Map.of();
        }

        Intent intent = intentRouter.route(state.messages());
        if (intent == null) {
            intent = Intent.unknown();
        }
        log.info("Triage node: IntentRouter classified topic={}, subtopic={}", intent.topic(), intent.subtopic());

        Map<String, Object> updates = new HashMap<>();
        if (state.getStage() == Stage.FALLBACK) {
            updates.put("deviceRetryCount", 0);
        }
        if (intent.isWifi()) {
            updates.put("topic", "wifi");
            if (state.getDevice() == null) {
                String detected = DeviceDetector.detect(state.latestUserMessage());
                if (detected != null) {
                    updates.put("device", detected);
                }
            }
        } else if (intent.isLogin()) {
            updates.put("topic", "login");
            if (intent.subtopic() != null) {
                updates.put("subtopic", intent.subtopic());
            }
        } else if (intent.isGreeting()) {
            updates.put("topic", "greeting");
        } else {
            updates.put("topic", "unknown");
        }

        return updates;
    }

    public Map<String, Object> handleDeviceNode(ConversationState state) {
        String msg = state.latestUserMessage();
        Map<String, Object> updates = new HashMap<>();

        if (DiagnosticClassifier.isOutOfBand(msg)) {
            log.info("Handle-device node: out-of-band input detected, routing to fallback");
            updates.put("stage", Stage.FALLBACK);
            updates.put("deviceRetryCount", 0);
            return updates;
        }
        String detected = DeviceDetector.detect(msg);
        if (detected != null) {
            log.info("Handle-device node: detected device='{}'", detected);
            updates.put("topic", "wifi");
            updates.put("device", detected);
            updates.put("stage", Stage.IN_WIFI_WALK);
            updates.put("deviceRetryCount", 0);
            return updates;
        }

        DiagnosticClassifier.Answer answer = DiagnosticClassifier.classify(msg);
        log.info("Handle-device node: classified non-device input as diagnostic answer={}", answer);

        if (answer == DiagnosticClassifier.Answer.PASSWORD_REJECTED) {
            log.info("Handle-device node: student reported password rejected/forgotten -> routing to reset");
            updates.put("topic", "login");
            updates.put("subtopic", "reset");
            updates.put("stage", Stage.IN_RESET);
            updates.put("deviceRetryCount", 0);
            return updates;
        }

        if (answer == DiagnosticClassifier.Answer.NEVER_ACTIVATED) {
            log.info("Handle-device node: student reported never activated / new student -> routing to activation");
            updates.put("topic", "login");
            updates.put("subtopic", "activation");
            updates.put("stage", Stage.IN_ACTIVATION);
            updates.put("deviceRetryCount", 0);
            return updates;
        }

        if (answer == DiagnosticClassifier.Answer.WORKS_ELSEWHERE) {
            log.info("Handle-device node: student reported login works elsewhere");
            if (StringUtils.hasText(state.getDevice())) {
                updates.put("topic", "wifi");
                updates.put("subtopic", "");
                updates.put("stage", Stage.IN_WIFI_WALK);
                updates.put("deviceRetryCount", 0);
                return updates;
            }
            int currentCount = state.getDeviceRetryCount();
            int newCount = currentCount + 1;
            if (newCount >= 2) {
                log.info("Handle-device node: device retry limit reached ({}) -> routing to fallback", newCount);
                updates.put("stage", Stage.FALLBACK);
                updates.put("deviceRetryCount", newCount);
                return updates;
            } else {
                log.info("Handle-device node: works elsewhere but device still unknown (attempt {}) -> re-asking with acknowledgment", newCount);
                updates.put("topic", "wifi");
                updates.put("subtopic", "");
                updates.put("stage", Stage.AWAITING_DEVICE);
                updates.put("deviceRetryCount", newCount);
                return updates;
            }
        }

        int currentCount = state.getDeviceRetryCount();
        int newCount = currentCount + 1;
        if (newCount >= 2) {
            log.info("Handle-device node: failed device attempts reached {} -> routing to fallback", newCount);
            updates.put("stage", Stage.FALLBACK);
            updates.put("deviceRetryCount", newCount);
            return updates;
        } else {
            log.info("Handle-device node: device could not be detected from '{}' (attempt {}) -> re-asking", msg, newCount);
            updates.put("stage", Stage.AWAITING_DEVICE);
            updates.put("deviceRetryCount", newCount);
            return updates;
        }
    }

    public Map<String, Object> handleDiagnosticNode(ConversationState state) {
        String msg = state.latestUserMessage();
        DiagnosticClassifier.Answer answer = DiagnosticClassifier.classify(msg);
        log.info("Handle-diagnostic node: classified answer={}", answer);

        Map<String, Object> updates = new HashMap<>();
        String detected = DeviceDetector.detect(msg);
        if (detected != null) {
            updates.put("device", detected);
        }

        if (answer == DiagnosticClassifier.Answer.WORKS_ELSEWHERE) {
            updates.put("topic", "wifi");
            updates.put("subtopic", "");
            String device = detected != null ? detected : state.getDevice();
            if (StringUtils.hasText(device)) {
                updates.put("stage", Stage.IN_WIFI_WALK);
            } else {
                updates.put("stage", Stage.AWAITING_DEVICE);
            }
        } else if (answer == DiagnosticClassifier.Answer.PASSWORD_REJECTED) {
            updates.put("topic", "login");
            updates.put("subtopic", "reset");
            updates.put("stage", Stage.IN_RESET);
        } else if (answer == DiagnosticClassifier.Answer.NEVER_ACTIVATED) {
            updates.put("topic", "login");
            updates.put("subtopic", "activation");
            updates.put("stage", Stage.IN_ACTIVATION);
        } else { // OUT_OF_BAND or UNKNOWN
            updates.put("stage", Stage.FALLBACK);
        }
        return updates;
    }

    /**
     * Device-check node — if topic=wifi and device null, ask for device; set stage=AWAITING_DEVICE.
     */
    public Map<String, Object> deviceCheckNode(ConversationState state) {
        log.info("Device-check node: asking for device and setting stage=AWAITING_DEVICE");
        ChatMessage assistantMsg = new ChatMessage("assistant", DEVICE_CHECK_PROMPT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", Stage.AWAITING_DEVICE);
        updates.put("messages", List.of(assistantMsg));
        return updates;
    }

    /**
     * Device-ack-check node — acknowledges that login works, but re-prompts specifically for the device.
     */
    public Map<String, Object> deviceAckCheckNode(ConversationState state) {
        log.info("Device-ack-check node: acknowledging login works and re-asking device (stage=AWAITING_DEVICE)");
        ChatMessage assistantMsg = new ChatMessage("assistant", DEVICE_CHECK_ACKNOWLEDGED_PROMPT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", Stage.AWAITING_DEVICE);
        updates.put("messages", List.of(assistantMsg));
        return updates;
    }

    /**
     * Diagnostic node — ask the login-check question; set stage=AWAITING_DIAGNOSTIC.
     */
    public Map<String, Object> diagnosticNode(ConversationState state) {
        log.info("Diagnostic node: asking login-check question and setting stage=AWAITING_DIAGNOSTIC");
        ChatMessage assistantMsg = new ChatMessage("assistant", DIAGNOSTIC_PROMPT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", Stage.AWAITING_DIAGNOSTIC);
        updates.put("messages", List.of(assistantMsg));
        return updates;
    }

    /**
     * Retrieve-respond node — build FilterSpec from state, retrieve, generate the walk.
     */
    public Map<String, Object> retrieveRespondNode(ConversationState state) {
        log.info("Retrieve-respond node: resolving filter from state (topic={}, device={}, subtopic={}, stage={})",
                state.getTopic(), state.getDevice(), state.getSubtopic(), state.getStage());
        String topic = state.getTopic();
        String device = state.getDevice();
        String subtopic = state.getSubtopic();
        Stage currentStage = state.getStage();
        Stage targetStage = currentStage;

        if (currentStage == Stage.TRIAGE || currentStage == Stage.FALLBACK) {
            if ("wifi".equalsIgnoreCase(topic)) {
                targetStage = Stage.IN_WIFI_WALK;
            } else if ("login".equalsIgnoreCase(topic) && "reset".equalsIgnoreCase(subtopic)) {
                targetStage = Stage.IN_RESET;
            } else if ("login".equalsIgnoreCase(topic) && "activation".equalsIgnoreCase(subtopic)) {
                targetStage = Stage.IN_ACTIVATION;
            } else {
                targetStage = Stage.IN_WIFI_WALK;
            }
        }

        FilterSpec filterSpec;
        if ("wifi".equalsIgnoreCase(topic)) {
            filterSpec = FilterSpec.wifi(device);
        } else if ("login".equalsIgnoreCase(topic)) {
            filterSpec = FilterSpec.login(subtopic);
        } else {
            filterSpec = new FilterSpec(topic, device, subtopic);
        }

        String searchQuery = StringUtils.hasText(state.latestUserMessage())
                ? state.latestUserMessage()
                : (topic != null ? topic : "help");
        List<Document> hits = retrievalService.search(searchQuery, filterSpec, 1, 0.0);

        String contextSlot;
        if (hits.isEmpty() || hits.getFirst().getText() == null || !StringUtils.hasText(hits.getFirst().getText())) {
            contextSlot = EMPTY_CONTEXT;
        } else {
            contextSlot = "```\n" + hits.getFirst().getText().trim() + "\n```";
        }

        String deviceSlot;
        if (StringUtils.hasText(device)) {
            deviceSlot = "known → The student is on: " + device.trim().toLowerCase(Locale.ROOT);
        } else {
            deviceSlot = DEVICE_UNKNOWN;
        }

        String systemText = systemTemplate
                .replace("{{DEVICE}}", deviceSlot)
                .replace("{{CONTEXT}}", contextSlot);

        String reply = chatClient.prompt()
                .system(systemText)
                .messages(toMessages(state.messages()))
                .call()
                .content();

        Map<String, Object> updates = new HashMap<>();
        if (targetStage != null) {
            updates.put("stage", targetStage);
        }
        updates.put("messages", List.of(new ChatMessage("assistant", reply)));

        return updates;
    }

    /**
     * Aside node — handles out-of-band / off-topic campus questions mid-flow without losing troubleshooting stage.
     */
    public Map<String, Object> asideNode(ConversationState state) {
        log.info("Aside node: answering out-of-band aside while preserving stage={}", state.getStage());

        String device = state.getDevice();
        String deviceSlot;
        if (StringUtils.hasText(device)) {
            deviceSlot = "known → The student is on: " + device.trim().toLowerCase(Locale.ROOT);
        } else {
            deviceSlot = DEVICE_UNKNOWN;
        }

        String asideContext = "(The student asked an aside or off-topic campus question mid-conversation. "
                + "Answer their question briefly if campus-related (e.g., Carman Hall 108 is the IT Help Desk), "
                + "redirect back to their tech issue, and hold their place in the troubleshooting flow.)";

        String systemText = systemTemplate
                .replace("{{DEVICE}}", deviceSlot)
                .replace("{{CONTEXT}}", asideContext);

        String reply = chatClient.prompt()
                .system(systemText)
                .messages(toMessages(state.messages()))
                .call()
                .content();

        Map<String, Object> updates = new HashMap<>();
        updates.put("messages", List.of(new ChatMessage("assistant", reply)));
        return updates;
    }

    /**
     * Greeting node — provides a warm re-prompt for greetings and stays at TRIAGE stage.
     */
    public Map<String, Object> greetingNode(ConversationState state) {
        log.info("Greeting node: sending warm greeting re-prompt and staying at TRIAGE stage");
        ChatMessage assistantMsg = new ChatMessage("assistant", GREETING_PROMPT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", Stage.TRIAGE);
        updates.put("topic", "");
        updates.put("messages", List.of(assistantMsg));
        return updates;
    }

    /**
     * Fallback node — the LehmanQ/Carman route.
     */
    public Map<String, Object> fallbackNode(ConversationState state) {
        log.info("Fallback node: routing student to LehmanQ / Carman Hall 108");
        ChatMessage assistantMsg = new ChatMessage("assistant", FALLBACK_PROMPT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("stage", Stage.FALLBACK);
        updates.put("messages", List.of(assistantMsg));
        return updates;
    }

    public String routeFromRouter(ConversationState state) {
        Stage stage = state.getStage();
        String topic = state.getTopic();
        String latestUserMsg = state.latestUserMessage();

        log.info("Route from router: stage={}, topic={}, msg='{}'", stage, topic, latestUserMsg);

        if (stage == Stage.TRIAGE || stage == Stage.FALLBACK || !StringUtils.hasText(topic)) {
            return "triage_intent";
        }
        if (DiagnosticClassifier.isOutOfBand(latestUserMsg)) {
            return "aside";
        }
        if (stage == Stage.AWAITING_DEVICE) {
            return "handle_device";
        }
        if (stage == Stage.AWAITING_DIAGNOSTIC) {
            return "handle_diagnostic";
        }
        if (stage == Stage.IN_WIFI_WALK) {
            if (DiagnosticClassifier.isLoginOrPasswordIssue(latestUserMsg)) {
                return "diagnostic";
            }
            return "retrieve_respond";
        }
        if (stage == Stage.IN_RESET || stage == Stage.IN_ACTIVATION) {
            return "retrieve_respond";
        }
        return "triage_intent";
    }

    public String routeAfterTriage(ConversationState state) {
        String topic = state.getTopic();
        String subtopic = state.getSubtopic();
        String device = state.getDevice();

        log.info("Route after triage: topic={}, device={}, subtopic={}", topic, device, subtopic);

        if ("greeting".equalsIgnoreCase(topic)) {
            return "greeting";
        } else if ("wifi".equalsIgnoreCase(topic)) {
            if (StringUtils.hasText(device)) {
                return "retrieve_respond";
            }
            return "device_check";
        } else if ("login".equalsIgnoreCase(topic)) {
            if ("reset".equalsIgnoreCase(subtopic) || "activation".equalsIgnoreCase(subtopic)) {
                return "retrieve_respond";
            }
            return "diagnostic";
        } else {
            return "fallback";
        }
    }

    public String routeAfterDevice(ConversationState state) {
        log.info("Route after device: stage={}, device={}", state.getStage(), state.getDevice());
        if (state.getStage() == Stage.FALLBACK) {
            return "fallback";
        }
        if (state.getStage() == Stage.IN_RESET || state.getStage() == Stage.IN_ACTIVATION
                || state.getStage() == Stage.IN_WIFI_WALK || StringUtils.hasText(state.getDevice())) {
            return "retrieve_respond";
        }
        if (DiagnosticClassifier.classify(state.latestUserMessage()) == DiagnosticClassifier.Answer.WORKS_ELSEWHERE) {
            return "device_ack_check";
        }
        return "device_check";
    }

    public String routeAfterDiagnostic(ConversationState state) {
        log.info("Route after diagnostic: stage={}, topic={}, device={}",
                state.getStage(), state.getTopic(), state.getDevice());
        if (state.getStage() == Stage.AWAITING_DEVICE) {
            return "device_check";
        }
        if (state.getStage() == Stage.FALLBACK) {
            return "fallback";
        }
        return "retrieve_respond";
    }

    public ConversationState execute(String conversationId, List<ChatMessage> messages, String device) {
        Map<String, Object> inputs = new HashMap<>();
        if (messages != null && !messages.isEmpty()) {
            inputs.put("messages", messages);
        }
        if (StringUtils.hasText(device)) {
            inputs.put("device", device.trim().toLowerCase(Locale.ROOT));
        }

        String threadId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();

        Optional<ConversationState> result = compiledGraph.invoke(inputs, config);
        return result.orElseThrow(() -> new IllegalStateException("Graph execution produced no state for thread: " + threadId));
    }

    public ConversationState execute(String conversationId, ChatMessage userMessage, String device) {
        return execute(conversationId, userMessage != null ? List.of(userMessage) : List.of(), device);
    }

    public Optional<ConversationState> getState(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();
        return compiledGraph.stateOf(config).map(StateSnapshot::state);
    }

    public CompiledGraph<ConversationState> getCompiledGraph() {
        return this.compiledGraph;
    }

    public BaseCheckpointSaver getCheckpointSaver() {
        return this.checkpointSaver;
    }

    private static List<Message> toMessages(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        if (history == null) {
            return messages;
        }
        for (ChatMessage message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(message.role())) {
                messages.add(new AssistantMessage(message.content()));
            } else {
                messages.add(new UserMessage(message.content()));
            }
        }
        return messages;
    }

    private static String readResource(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read system prompt template", e);
        }
    }
}
