package dev.emerald.minecraft.config;

import dev.emerald.ai.AiConfig;
import dev.emerald.core.SimulationConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.Duration;

/** Server config (world/serverconfig/emerald-server.toml). AI is off by default. */
public final class EmeraldConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue UTILITY_INTERVAL = BUILDER
            .comment("Ticks between need re-scoring for each loaded citizen.")
            .defineInRange("simulation.utilityIntervalTicks", SimulationConfig.DEFAULT.utilityIntervalTicks(), 10, 1200);
    public static final ModConfigSpec.IntValue EGG_WINDOW = BUILDER
            .comment("An egg laid in the pen with no collection evidence after this many ticks opens EGGS_WASTED.")
            .defineInRange("simulation.eggWasteWindowTicks", SimulationConfig.DEFAULT.eggWasteWindowTicks(), 200, 72000);
    public static final ModConfigSpec.IntValue EXPERIMENT_TIMEOUT = BUILDER
            .comment("Ticks an experiment watches for its expected observation.")
            .defineInRange("simulation.experimentTimeoutTicks", SimulationConfig.DEFAULT.experimentTimeoutTicks(), 200, 168000);
    public static final ModConfigSpec.IntValue FARM_RADIUS = BUILDER
            .defineInRange("simulation.farmRadius", SimulationConfig.DEFAULT.farmRadius(), 4, 48);

    public static final ModConfigSpec.BooleanValue AI_ENABLED = BUILDER
            .comment("Let a local Ollama model propose hypotheses. The deterministic table is always the fallback.")
            .define("ai.enabled", false);
    public static final ModConfigSpec.ConfigValue<String> AI_URL = BUILDER
            .define("ai.ollamaUrl", "http://localhost:11434");
    public static final ModConfigSpec.ConfigValue<String> AI_MODEL = BUILDER
            .define("ai.model", "qwen3");
    public static final ModConfigSpec.IntValue AI_TIMEOUT_SECONDS = BUILDER
            .defineInRange("ai.timeoutSeconds", 20, 2, 120);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EmeraldConfig() {
    }

    public static SimulationConfig simulation() {
        if (!SPEC.isLoaded()) {
            return SimulationConfig.DEFAULT;
        }
        SimulationConfig d = SimulationConfig.DEFAULT;
        return new SimulationConfig(UTILITY_INTERVAL.get(), EGG_WINDOW.get(), EXPERIMENT_TIMEOUT.get(),
                d.observationCap(), d.ledgerCap(), FARM_RADIUS.get(), d.plankRequestCount());
    }

    public static AiConfig ai() {
        if (!SPEC.isLoaded()) {
            return AiConfig.DISABLED;
        }
        return new AiConfig(AI_ENABLED.get(), AI_URL.get(), AI_MODEL.get(),
                Duration.ofSeconds(AI_TIMEOUT_SECONDS.get()), AiConfig.DISABLED.cooldownTicks());
    }
}
