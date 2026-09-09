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
            - "login": Issues regarding Lehman login account, account activation (new/first-time student, CUNYfirst 2-day record requirement), password reset, forgotten password, password expired, password not working across Lehman systems (Lehman 360, student email, portal, campus computers, recovery email, MFA/Authenticator).
            - "greeting": Greetings, opening pleasantries (e.g. "hi", "hello", "hey", "good morning", "yo") without an explicit tech issue yet.
            - "unknown": General chit-chat, questions unrelated to Lehman campus tech, or unclear inquiries.

            If the topic is "login", also identify the subtopic:
            - "activation": Brand-new student, first-time account activation, never set up Lehman account before.
            - "reset": Forgot password, password rejected/expired, needs password reset via recovery email.
            - null: General login question or ambiguous between activation and reset.

            If the topic is NOT "login", subtopic must be null.

            Respond with a JSON object:
            {"topic": "wifi" | "login" | "greeting" | "unknown", "subtopic": "activation" | "reset" | null}
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

            log.info("Classified intent: topic={}, subtopic={}", topic, subtopic);
            return new Intent(topic, subtopic);
        } catch (Exception e) {
            log.warn("Could not parse intent classification JSON '{}', falling back: {}", raw, e.getMessage());
            return parseHeuristicFromText(raw);
        }
    }

    private Intent parseHeuristicFromText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
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
            return Intent.login(subtopic);
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
}
