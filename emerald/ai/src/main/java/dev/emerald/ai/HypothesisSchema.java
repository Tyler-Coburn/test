package dev.emerald.ai;

import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.observe.ObservationType;

import java.util.Arrays;
import java.util.stream.Collectors;

/** JSON schema sent as Ollama's {@code format} for constrained decoding. Enums are re-validated after parse. */
public final class HypothesisSchema {
    public static final int MAX_STATEMENT = 240;
    public static final int MAX_CONCEPTS = 6;

    private HypothesisSchema() {
    }

    public static String json() {
        return """
                {"type":"object","additionalProperties":false,
                 "required":["action","statement","conceptIds","expectedObservation"],
                 "properties":{
                  "action":{"type":"string","enum":["PROPOSE_HYPOTHESIS"]},
                  "statement":{"type":"string","maxLength":%d},
                  "conceptIds":{"type":"array","maxItems":%d,"items":{"type":"string","enum":[%s]}},
                  "expectedObservation":{"type":"string","enum":[%s]}}}"""
                .formatted(MAX_STATEMENT, MAX_CONCEPTS, quoted(ConceptId.values()), quoted(ObservationType.values()));
    }

    private static String quoted(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> "\"" + v.name() + "\"").collect(Collectors.joining(","));
    }
}
