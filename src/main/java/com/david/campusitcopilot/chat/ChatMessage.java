package com.david.campusitcopilot.chat;

import java.io.Serializable;

/**
 * One turn of the conversation.
 *
 * @param role    {@code "user"} or {@code "assistant"} (anything non-assistant is treated as user)
 * @param content the message text
 */
public record ChatMessage(String role, String content) implements Serializable {
}
