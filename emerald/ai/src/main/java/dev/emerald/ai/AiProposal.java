package dev.emerald.ai;

import java.util.List;

/** The only shape a model may answer with. Parsed strictly; any extra field is a rejection. */
public record AiProposal(String action, String statement, List<String> conceptIds, String expectedObservation) {
    public static final String PROPOSE_HYPOTHESIS = "PROPOSE_HYPOTHESIS";

    public AiProposal {
        conceptIds = conceptIds == null ? List.of() : List.copyOf(conceptIds);
    }
}
