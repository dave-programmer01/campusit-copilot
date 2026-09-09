package com.david.campusitcopilot.chat;

import com.david.campusitcopilot.search.FilterSpec;
import com.david.campusitcopilot.search.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


@Slf4j
@Service
public class ChatService {


    private static final String DEVICE_UNKNOWN =
            "unknown → Not yet known. If this is a wifi issue, your FIRST job is to ask which device "
                    + "they're on: MacBook, Windows, iPhone, or Android. Do not give steps until you know.";


    private static final String EMPTY_CONTEXT =
            "(No verified steps loaded yet. Ask what device they're on (for wifi), run the diagnostic check (for password/login), or route to the desk — do not invent steps.)";


    private static final int RETRIEVAL_TOP_K = 1;

    private static final double RETRIEVAL_THRESHOLD = 0.0;

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final IntentRouter intentRouter;
    private final String systemTemplate;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       RetrievalService retrievalService,
                       IntentRouter intentRouter,
                       @Value("classpath:/prompts/system-prompt.md") Resource systemPromptResource) {
        this.chatClient = chatClientBuilder.build();
        this.retrievalService = retrievalService;
        this.intentRouter = intentRouter;
        this.systemTemplate = readResource(systemPromptResource);
    }


    public String reply(List<ChatMessage> history, String device) {
        String latestUserMessage = latestUserMessage(history);
        Intent intent = intentRouter.route(history);

        String deviceSlot = deviceSlot(device);
        String contextSlot = contextSlot(intent, device, latestUserMessage);

        String systemText = systemTemplate
                .replace("{{DEVICE}}", deviceSlot)
                .replace("{{CONTEXT}}", contextSlot);

        return chatClient.prompt()
                .system(systemText)
                .messages(toMessages(history))
                .call()
                .content();
    }

    private String deviceSlot(String device) {
        if (StringUtils.hasText(device)) {
            return "known → The student is on: " + device.trim().toLowerCase(Locale.ROOT);
        }
        return DEVICE_UNKNOWN;
    }


    private String contextSlot(Intent intent, String device, String latestUserMessage) {
        if (intent == null || !StringUtils.hasText(latestUserMessage)) {
            return EMPTY_CONTEXT;
        }

        FilterSpec filterSpec;
        if (intent.isWifi()) {
            if (!StringUtils.hasText(device)) {
                return EMPTY_CONTEXT;
            }
            filterSpec = FilterSpec.wifi(device);
        } else if (intent.isLogin()) {
            filterSpec = FilterSpec.login(intent.subtopic());
        } else {
            return EMPTY_CONTEXT;
        }

        List<Document> hits = retrievalService.search(
                latestUserMessage, filterSpec, RETRIEVAL_TOP_K, RETRIEVAL_THRESHOLD);

        if (hits.isEmpty()) {
            return EMPTY_CONTEXT;
        }

        String steps = hits.get(0).getText();
        if (!StringUtils.hasText(steps)) {
            return EMPTY_CONTEXT;
        }

        // Fence the retrieved steps so the model treats them as the only source.
        return "```\n" + steps.trim() + "\n```";
    }

    private static String latestUserMessage(List<ChatMessage> history) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            if (message != null && "user".equalsIgnoreCase(message.role())
                    && StringUtils.hasText(message.content())) {
                return message.content();
            }
        }
        return "";
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
