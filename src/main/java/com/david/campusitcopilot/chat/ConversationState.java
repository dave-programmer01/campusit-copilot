package com.david.campusitcopilot.chat;

import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State of a conversation within the LangGraph workflow.
 * <p>
 * Extends {@link MessagesState} to maintain an append-only list of {@link ChatMessage}s,
 * while providing replace-semantics scalar channels for topic, device, subtopic, and stage.
 */
public class ConversationState extends MessagesState<ChatMessage> {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "messages", Channels.appenderWithDuplicate(ArrayList::new),
            "topic", Channels.base((oldVal, newVal) -> newVal),
            "device", Channels.base((oldVal, newVal) -> newVal),
            "subtopic", Channels.base((oldVal, newVal) -> newVal),
            "stage", Channels.base((oldVal, newVal) -> newVal, () -> Stage.TRIAGE),
            "deviceRetryCount", Channels.base((oldVal, newVal) -> newVal, () -> 0)
    );

    public ConversationState(Map<String, Object> data) {
        super(data);
    }

    public Optional<String> topic() {
        return value("topic");
    }

    public String getTopic() {
        String top = topic().orElse(null);
        return StringUtils.hasText(top) ? top : null;
    }

    public Optional<String> device() {
        return value("device");
    }

    public String getDevice() {
        String dev = device().orElse(null);
        return StringUtils.hasText(dev) ? dev : null;
    }

    public Optional<String> subtopic() {
        return value("subtopic");
    }

    public String getSubtopic() {
        String sub = subtopic().orElse(null);
        return StringUtils.hasText(sub) ? sub : null;
    }

    public Optional<Stage> stage() {
        return value("stage");
    }

    public Stage getStage() {
        return stage().orElse(Stage.TRIAGE);
    }

    public Optional<Integer> deviceRetryCount() {
        return value("deviceRetryCount");
    }

    public int getDeviceRetryCount() {
        return deviceRetryCount().orElse(0);
    }

    @Override
    public List<ChatMessage> messages() {
        return this.<List<ChatMessage>>value("messages").orElseGet(List::of);
    }

    /**
     * Extracts the most recent user message from the messages channel.
     */
    public String latestUserMessage() {
        List<ChatMessage> msgs = messages();
        if (msgs == null) {
            return "";
        }
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage msg = msgs.get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.role()) && StringUtils.hasText(msg.content())) {
                return msg.content();
            }
        }
        return "";
    }
}
