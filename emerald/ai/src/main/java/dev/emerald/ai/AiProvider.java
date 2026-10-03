package dev.emerald.ai;

import java.util.concurrent.CompletableFuture;

/**
 * A bounded, asynchronous model call that returns raw JSON text. Implementations must never block
 * the server thread; the game only polls finished futures. The returned text is untrusted until
 * {@link ProposalParser} and {@link ProposalValidator} accept it.
 */
public interface AiProvider {
    CompletableFuture<String> completeJson(String jsonSchema, String systemPrompt, String userPrompt);

    String name();
}
