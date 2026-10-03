package dev.emerald.ai;

import java.time.Duration;

/**
 * @param enabled       false = never call a model; the deterministic fallback runs
 * @param baseUrl       Ollama base URL, local only for structured outputs
 * @param cooldownTicks minimum game ticks between model calls for one village
 */
public record AiConfig(boolean enabled, String baseUrl, String model, Duration timeout, long cooldownTicks) {
    public static final AiConfig DISABLED = new AiConfig(false, "http://localhost:11434", "qwen3", Duration.ofSeconds(20), 1200);
}
