package com.david.campusitcopilot.chat;

import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A real {@link IntentRouter} whose LLM call always fails, so every decision comes from the
 * router's own heuristic fallback.
 * <p>
 * Tests use this as the default answer for the two-arg {@code route(history, state)} call that
 * {@code routerNode} and the {@code handle_*} nodes make on every turn: it keeps multi-turn
 * routing deterministic and message-sensitive without stubbing each turn by hand. Tests that care
 * about one specific decision override it with an explicit {@link Intent}.
 */
final class OfflineIntentRouter {

    private OfflineIntentRouter() {
    }

    static IntentRouter create() {
        ChatClient failing = mock(ChatClient.class);
        when(failing.prompt()).thenThrow(new IllegalStateException("no LLM in tests"));
        return new IntentRouter(failing, null);
    }
}
