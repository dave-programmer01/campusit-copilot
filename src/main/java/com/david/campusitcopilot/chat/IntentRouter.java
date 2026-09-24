package com.david.campusitcopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class IntentRouter {

    private static final String ROUTER_SYSTEM_PROMPT = """
            You are an intent classification engine for a college campus IT help desk assistant (Lehman College).
            Your task is to classify the student's inquiry / conversation into exactly one topic:
            - "wifi": Issues regarding connecting to campus Wi-Fi / wireless network, eduroam, Lehman-WiFi, network configuration on devices (MacBook, Windows, iPhone, Android), Wi-Fi connection errors.
            - "login": Issues regarding Lehman login account, account activation (new/first-time student, CUNYfirst 2-day record requirement), password reset, forgotten password, password expired, password not working across Lehman systems (Lehman 360, student email, portal, campus computers, recovery email).
            - "mfa": Authenticator app issues, verification codes, 6-digit codes, "MFA error", "can't get past the login prompt", "asked to set up MFA", Microsoft Authenticator, mobile authenticator TOTP, QR code scanning, "more information required" prompt.
            - "greeting": Greetings, opening pleasantries (e.g. "hi", "hello", "hey", "good morning", "yo") without an explicit tech issue yet.
            - "unknown": General chit-chat, questions unrelated to Lehman campus tech, or unclear inquiries.

            If the topic is "login", also identify the subtopic:
            - "activation": Brand-new student, first-time account activation, never set up Lehman account before.
            - "reset": Forgot password, password rejected/expired, needs password reset via recovery email.
            - null: General login question or ambiguous between activation and reset.

            Account detection: when the message names a system, capture the account:
            - "cuny": CUNY systems including CUNYfirst, Brightspace, Lehman 360, Zoom, CUNYbuy, Campus VPN, Lehman Electronic Forms (ePAF, ePRF, iDeclare).
            - "microsoft365": Microsoft systems including Outlook, Teams, Lehman student email, M365 apps, Office 365, "more information required" email login prompt.
            - "lehman": Lehman campus accounts including Lehman login, wifi, campus computers, Lehman 360 login.
            - null: System/account is unknown or ambiguous (e.g., student just says "MFA isn't working" or "can't log in" without naming the system).

            If the topic is NOT "login", subtopic must be null.

            Respond with a JSON object:
            {"topic": "wifi" | "login" | "mfa" | "greeting" | "unknown", "subtopic": "activation" | "reset" | null, "account": "cuny" | "microsoft365" | "lehman" | null}
            """;

    private static final String FLOW_ROUTER_SYSTEM_PROMPT = """
            You are a conversation flow and intent classification engine for a college campus IT help desk assistant (Lehman College).

            The student is currently in an ongoing troubleshooting flow with this state:
            - Current Stage: {{STAGE}}
            - Current Topic: {{TOPIC}}
            - Current Subtopic: {{SUBTOPIC}}
            - Current Account: {{ACCOUNT}}
            - Current Device: {{DEVICE}}

            Given the conversation history and the student's latest message, choose exactly one action:

            1. "CONTINUE":
            The student is continuing within their current flow:
            - Acknowledging steps ("ok", "done", "got it", "did that", "next", "what next", "still stuck", "it worked", "it failed", "I don't see that button").
            - Terse answers to questions asked during the walk ("yes", "no", "sure", "ok").
            - Asking for clarification on current steps.
            CRITICAL BIAS: Default to CONTINUE. Only choose SWITCH if the student makes an explicit, unambiguous request for a completely different IT topic. Terse answers ("yes", "no", "ok", "done", "next") in an active walk MUST ALWAYS be CONTINUE — with the one exception below.

            EXCEPTION — ACTIVATION HEADS-UP: At the Wi-Fi credential step the assistant warns that the step only works if the student's Lehman login is already activated, and promises to switch to activation first if it isn't. When the assistant's most recent message carries that heads-up (it says the login needs to be activated), a negative reply is the student taking up that offer, not a terse walk answer. It MUST be SWITCH with topic "login", subtopic "activation", account "lehman". This includes "no", "nope", "it doesn't", "it isn't", "not activated", "I never did", "I don't think so", "not yet", "it's not working". Affirmative or neutral replies ("yes", "it is", "ok", "done", "next") stay CONTINUE.

            2. "SWITCH":
            The student clearly and explicitly requests help with a different IT problem (e.g., they were in Wi-Fi troubleshooting and say "I need to reset my password", "I have to activate Lehman login", "actually I need help with Brightspace MFA"), or answers the activation heads-up negatively (see the exception above).
            When action is "SWITCH", identify the new:
            - topic: "wifi" | "login" | "mfa" | "greeting" | "unknown"
            - subtopic: (for "login") "activation" | "reset" | null
            - account: "cuny" (CUNYfirst, Brightspace, Lehman 360, Zoom, CUNYbuy) | "microsoft365" (Outlook, Teams, Lehman email, M365) | "lehman" (campus computers, wifi, Lehman login) | null

            3. "ASIDE":
            The student asks an off-topic question unrelated to technical troubleshooting (e.g., "where is Carman Hall?", "where is the library?", "what time does the cafeteria close?", "who is the registrar?").

            4. "DIAGNOSTIC_ANSWER":
            Only when the current stage is AWAITING_DIAGNOSTIC (the assistant asked if their password works on email or portal):
            Classify their answer into diagnosticAnswer:
            - "works-elsewhere": Password works on email/portal/other systems ("yes", "yeah", "it works fine", "works on my phone", "I can sign in to email").
            - "password-rejected": Password rejected / fails / forgot / locked out ("no", "nope", "doesn't work", "fails", "forgot my password", "invalid password").
            - "never-activated": Brand new student, never activated account, first semester, freshman ("never set it up", "first semester", "I'm a new student", "have to activate").

            Respond with a JSON object in this exact format:
            {"action": "CONTINUE" | "SWITCH" | "ASIDE" | "DIAGNOSTIC_ANSWER", "topic": "wifi" | "login" | "mfa" | "greeting" | "unknown" | null, "subtopic": "activation" | "reset" | null, "account": "cuny" | "microsoft365" | "lehman" | null, "diagnosticAnswer": "works-elsewhere" | "password-rejected" | "never-activated" | null}
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public IntentRouter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    public IntentRouter(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public Intent route(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return Intent.unknown();
        }

        String conversation = formatHistory(history);
        if (!StringUtils.hasText(conversation)) {
            return Intent.unknown();
        }

        try {
            String response = chatClient.prompt()
                    .system(ROUTER_SYSTEM_PROMPT)
                    .user(conversation)
                    .call()
                    .content();

            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Intent classification call failed, falling back to heuristic parsing: {}", e.getMessage());
            return fallbackHeuristic(history);
        }
    }

    public Intent route(List<ChatMessage> history, ConversationState state) {
        if (history == null || history.isEmpty()) {
            return Intent.unknown();
        }
        if (state == null || state.getStage() == Stage.TRIAGE || state.getStage() == Stage.FALLBACK
                || !StringUtils.hasText(state.getTopic())) {
            return route(history);
        }

        String conversation = formatHistory(history);
        if (!StringUtils.hasText(conversation)) {
            return Intent.unknown();
        }

        try {
            String stageStr = state.getStage() != null ? state.getStage().name() : "UNKNOWN";
            String topicStr = state.getTopic() != null ? state.getTopic() : "none";
            String subtopicStr = state.getSubtopic() != null ? state.getSubtopic() : "none";
            String accountStr = state.getAccount() != null ? state.getAccount() : "none";
            String deviceStr = state.getDevice() != null ? state.getDevice() : "none";

            String prompt = FLOW_ROUTER_SYSTEM_PROMPT
                    .replace("{{STAGE}}", stageStr)
                    .replace("{{TOPIC}}", topicStr)
                    .replace("{{SUBTOPIC}}", subtopicStr)
                    .replace("{{ACCOUNT}}", accountStr)
                    .replace("{{DEVICE}}", deviceStr);

            String response = chatClient.prompt()
                    .system(prompt)
                    .user(conversation)
                    .call()
                    .content();

            return parseFlowResponse(response, state);
        } catch (Exception e) {
            log.warn("Flow intent classification call failed, falling back to heuristic parsing: {}", e.getMessage());
            return fallbackFlowHeuristic(history, state);
        }
    }

    private String formatHistory(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (msg != null && StringUtils.hasText(msg.content())) {
                sb.append(msg.role()).append(": ").append(msg.content().trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    Intent parseResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Intent.unknown();
        }

        try {
            String cleaned = raw.trim();
            if (cleaned.contains("{") && cleaned.contains("}")) {
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonNode node = objectMapper.readTree(cleaned);
            String topicStr = node.has("topic") ? node.get("topic").asText() : null;
            String subtopicStr = (node.has("subtopic") && !node.get("subtopic").isNull())
                    ? node.get("subtopic").asText()
                    : null;
            String accountStr = (node.has("account") && !node.get("account").isNull())
                    ? node.get("account").asText()
                    : null;

            Intent.Topic topic = Intent.Topic.fromString(topicStr);

            String subtopic = null;
            if (topic == Intent.Topic.LOGIN && StringUtils.hasText(subtopicStr)) {
                String subLower = subtopicStr.toLowerCase(Locale.ROOT);
                if (subLower.contains("activation")) {
                    subtopic = "activation";
                } else if (subLower.contains("reset") || subLower.contains("password")) {
                    subtopic = "reset";
                }
            }

            String account = normalizeAccount(accountStr);

            log.info("Classified intent: topic={}, subtopic={}, account={}", topic, subtopic, account);
            return new Intent(topic, subtopic, account);
        } catch (Exception e) {
            log.warn("Could not parse intent classification JSON '{}', falling back: {}", raw, e.getMessage());
            return parseHeuristicFromText(raw);
        }
    }

    Intent parseFlowResponse(String raw, ConversationState state) {
        if (!StringUtils.hasText(raw)) {
            return fallbackFlowHeuristic(state != null ? state.messages() : List.of(), state);
        }

        try {
            String cleaned = raw.trim();
            if (cleaned.contains("{") && cleaned.contains("}")) {
                int start = cleaned.indexOf('{');
                int end = cleaned.lastIndexOf('}');
                cleaned = cleaned.substring(start, end + 1);
            }

            JsonNode node = objectMapper.readTree(cleaned);
            String actionStr = node.has("action") && !node.get("action").isNull() ? node.get("action").asText() : "CONTINUE";
            Intent.Action action = Intent.Action.fromString(actionStr);

            String topicStr = node.has("topic") && !node.get("topic").isNull() ? node.get("topic").asText() : null;
            String subtopicStr = (node.has("subtopic") && !node.get("subtopic").isNull()) ? node.get("subtopic").asText() : null;
            String accountStr = (node.has("account") && !node.get("account").isNull()) ? node.get("account").asText() : null;
            String diagStr = node.has("diagnosticAnswer") && !node.get("diagnosticAnswer").isNull() ? node.get("diagnosticAnswer").asText() : null;

            if (action == Intent.Action.ASIDE) {
                return Intent.flowAside();
            }

            if (action == Intent.Action.DIAGNOSTIC_ANSWER) {
                String normalizedDiag = normalizeDiagnosticAnswer(diagStr);
                return Intent.diagnosticAnswer(normalizedDiag);
            }

            if (action == Intent.Action.SWITCH) {
                Intent.Topic topic = Intent.Topic.fromString(topicStr);
                String subtopic = null;
                if (topic == Intent.Topic.LOGIN && StringUtils.hasText(subtopicStr)) {
                    String subLower = subtopicStr.toLowerCase(Locale.ROOT);
                    if (subLower.contains("activation")) {
                        subtopic = "activation";
                    } else if (subLower.contains("reset") || subLower.contains("password")) {
                        subtopic = "reset";
                    }
                }
                String account = normalizeAccount(accountStr);
                if (topic == Intent.Topic.LOGIN && account == null) {
                    account = "lehman";
                }
                log.info("Flow SWITCH intent: topic={}, subtopic={}, account={}", topic, subtopic, account);
                return Intent.flowSwitch(topic, subtopic, account);
            }

            // Default: CONTINUE
            Intent.Topic currentTopic = state != null && state.getTopic() != null
                    ? Intent.Topic.fromString(state.getTopic())
                    : (topicStr != null ? Intent.Topic.fromString(topicStr) : Intent.Topic.UNKNOWN);
            String currentSubtopic = state != null && state.getSubtopic() != null ? state.getSubtopic() : subtopicStr;
            String currentAccount = state != null && state.getAccount() != null ? state.getAccount() : normalizeAccount(accountStr);
            log.info("Flow CONTINUE intent: topic={}, subtopic={}, account={}", currentTopic, currentSubtopic, currentAccount);
            return Intent.flowContinue(currentTopic, currentSubtopic, currentAccount);
        } catch (Exception e) {
            log.warn("Could not parse flow classification JSON '{}', falling back: {}", raw, e.getMessage());
            return fallbackFlowHeuristic(state != null ? state.messages() : List.of(), state);
        }
    }

    private static String normalizeDiagnosticAnswer(String val) {
        if (!StringUtils.hasText(val)) {
            return "unknown";
        }
        String lower = val.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("works") || lower.contains("elsewhere") || lower.contains("affirmative") || lower.contains("yes")) {
            return "works-elsewhere";
        }
        if (lower.contains("reject") || lower.contains("fail") || lower.contains("reset") || lower.contains("no")
                || lower.contains("wrong") || lower.contains("invalid") || lower.contains("locked")) {
            return "password-rejected";
        }
        if (lower.contains("activat") || lower.contains("never") || lower.contains("new") || lower.contains("freshman")) {
            return "never-activated";
        }
        return "unknown";
    }

    private static String normalizeAccount(String val) {
        if (!StringUtils.hasText(val)) {
            return null;
        }
        String lower = val.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("cuny")) {
            return "cuny";
        }
        if (lower.contains("microsoft") || lower.contains("365") || lower.contains("outlook") || lower.contains("teams") || lower.contains("email")) {
            return "microsoft365";
        }
        if (lower.contains("lehman")) {
            return "lehman";
        }
        return null;
    }

    private Intent parseHeuristicFromText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String detectedAccount = AccountDetector.detect(text);

        if (lower.contains("mfa") || lower.contains("authenticator") || lower.contains("verification code")
                || lower.contains("2fa") || lower.contains("two-factor") || lower.contains("totp") || lower.contains("qr code")) {
            return Intent.mfa(detectedAccount);
        }
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("wireless")) {
            return Intent.wifi();
        }
        if (lower.contains("login") || lower.contains("password") || lower.contains("account") || lower.contains("activation") || lower.contains("reset")) {
            String subtopic = null;
            if (lower.contains("activation")) {
                subtopic = "activation";
            } else if (lower.contains("reset")) {
                subtopic = "reset";
            }
            String account = detectedAccount != null ? detectedAccount : "lehman";
            return Intent.login(subtopic, account);
        }
        if (DiagnosticClassifier.isGreeting(text)) {
            return Intent.greeting();
        }
        return Intent.unknown();
    }

    private Intent fallbackHeuristic(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg != null && StringUtils.hasText(msg.content())) {
                Intent parsed = parseHeuristicFromText(msg.content());
                if (!parsed.isUnknown()) {
                    return parsed;
                }
            }
        }
        return Intent.unknown();
    }

    private Intent fallbackFlowHeuristic(List<ChatMessage> history, ConversationState state) {
        if (history == null || history.isEmpty()) {
            return Intent.flowContinue(
                    state != null && state.getTopic() != null ? Intent.Topic.fromString(state.getTopic()) : Intent.Topic.UNKNOWN,
                    state != null ? state.getSubtopic() : null,
                    state != null ? state.getAccount() : null
            );
        }
        String latest = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.role()) && StringUtils.hasText(msg.content())) {
                latest = msg.content().trim();
                break;
            }
        }
        if (latest == null) {
            ChatMessage last = history.get(history.size() - 1);
            latest = last != null && last.content() != null ? last.content().trim() : "";
        }

        if (DiagnosticClassifier.isOutOfBand(latest)) {
            return Intent.flowAside();
        }

        Stage stage = state != null ? state.getStage() : null;

        if (stage == Stage.AWAITING_DIAGNOSTIC) {
            DiagnosticClassifier.Answer answer = DiagnosticClassifier.classify(latest);
            return switch (answer) {
                case WORKS_ELSEWHERE -> Intent.diagnosticAnswer("works-elsewhere");
                case PASSWORD_REJECTED -> Intent.diagnosticAnswer("password-rejected");
                case NEVER_ACTIVATED -> Intent.diagnosticAnswer("never-activated");
                case OUT_OF_BAND -> Intent.flowAside();
                default -> Intent.diagnosticAnswer("unknown");
            };
        }

        // The credential step's activation heads-up promises a switch; a "no" to it is that switch.
        if (stage == Stage.IN_WIFI_WALK && lastAssistantMentionsActivation(history)
                && DiagnosticClassifier.isActivationDenial(latest)) {
            return Intent.flowSwitch(Intent.Topic.LOGIN, "activation", "lehman");
        }

        String lower = latest.toLowerCase(Locale.ROOT);
        String detectedAccount = AccountDetector.detect(latest);

        if (lower.contains("mfa") || lower.contains("2fa") || lower.contains("authenticator")
                || lower.contains("verification code") || lower.contains("two-factor")) {
            if (state == null || !"mfa".equalsIgnoreCase(state.getTopic())) {
                return Intent.flowSwitch(Intent.Topic.MFA, null, detectedAccount);
            }
        }

        if (lower.contains("reset password") || lower.contains("forgot password")
                || lower.contains("forgot my password") || lower.contains("change password")
                || lower.contains("reset my password") || lower.contains("need to reset")) {
            if (state == null || !"login".equalsIgnoreCase(state.getTopic()) || !"reset".equalsIgnoreCase(state.getSubtopic())) {
                return Intent.flowSwitch(Intent.Topic.LOGIN, "reset", detectedAccount != null ? detectedAccount : "lehman");
            }
        }

        if (lower.contains("activate") || lower.contains("activation")
                || lower.contains("new student") || lower.contains("first semester")
                || lower.contains("never activated") || lower.contains("have to activate")) {
            if (state == null || !"login".equalsIgnoreCase(state.getTopic()) || !"activation".equalsIgnoreCase(state.getSubtopic())) {
                return Intent.flowSwitch(Intent.Topic.LOGIN, "activation", detectedAccount != null ? detectedAccount : "lehman");
            }
        }

        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("wireless")) {
            if (state == null || !"wifi".equalsIgnoreCase(state.getTopic())) {
                return Intent.flowSwitch(Intent.Topic.WIFI, null, null);
            }
        }

        Intent.Topic currentTopic = state != null && state.getTopic() != null
                ? Intent.Topic.fromString(state.getTopic())
                : Intent.Topic.UNKNOWN;
        String subtopic = state != null ? state.getSubtopic() : null;
        String account = state != null ? state.getAccount() : null;
        return Intent.flowContinue(currentTopic, subtopic, account);
    }

    private static boolean lastAssistantMentionsActivation(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg != null && "assistant".equalsIgnoreCase(msg.role()) && msg.content() != null) {
                return msg.content().toLowerCase(Locale.ROOT).contains("activat");
            }
        }
        return false;
    }
}
