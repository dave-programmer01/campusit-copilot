package com.david.campusitcopilot.chat;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The student-facing conversation endpoint.
 * <p>
 * {@code POST /chat} takes a single new message plus an optional {@code device} and {@code conversationId}.
 * Executes through the stateful {@link ChatGraph} preserving conversation state across turns.
 */
@RestController
public class ChatController {

    private final ChatGraph chatGraph;

    public ChatController(ChatGraph chatGraph) {
        this.chatGraph = chatGraph;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        List<ChatMessage> messages = request.message() == null ? List.of() : List.of(request.message());
        String conversationId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();

        ConversationState state = chatGraph.execute(conversationId, messages, request.device());
        String reply = extractReply(state);
        return new ChatResponse(reply, conversationId);
    }

    private String extractReply(ConversationState state) {
        List<ChatMessage> msgs = state.messages();
        if (msgs != null) {
            for (int i = msgs.size() - 1; i >= 0; i--) {
                ChatMessage msg = msgs.get(i);
                if (msg != null && "assistant".equalsIgnoreCase(msg.role()) && StringUtils.hasText(msg.content())) {
                    return msg.content();
                }
            }
        }
        return "";
    }

    /**
     * @param message        the latest message from the student
     * @param device         the student's device once known (optional; absent keeps the "ask first" flow)
     * @param conversationId stable session / thread identifier (optional; auto-generated if absent)
     */
    public record ChatRequest(ChatMessage message, String device, String conversationId) {
        public ChatRequest(ChatMessage message, String device) {
            this(message, device, null);
        }
    }

    public record ChatResponse(String reply, String conversationId) {
        public ChatResponse(String reply) {
            this(reply, null);
        }
    }
}
