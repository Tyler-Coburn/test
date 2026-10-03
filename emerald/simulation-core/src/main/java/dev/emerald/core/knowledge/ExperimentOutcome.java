package dev.emerald.core.knowledge;

import dev.emerald.core.observe.ObservationBus;
import dev.emerald.core.observe.WorldObservation;

import java.util.UUID;

/**
 * The only token that can move a concept to TESTED_TRUE or TESTED_FALSE.
 *
 * <p>Both factories demand an observation that is authentically on the {@link ObservationBus}:
 * a pass cites the expected observation; a fail cites the opportunity observation (e.g. the egg that
 * spawned in the pen) that proves the test actually ran yet produced no expected evidence.
 * Model text, dialogue and player claims cannot construct one.
 */
public final class ExperimentOutcome {
    private final UUID experimentId;
    private final ConceptId concept;
    private final boolean passed;
    private final WorldObservation evidence;
    private final long gameTime;

    private ExperimentOutcome(UUID experimentId, ConceptId concept, boolean passed,
                              WorldObservation evidence, long gameTime) {
        this.experimentId = experimentId;
        this.concept = concept;
        this.passed = passed;
        this.evidence = evidence;
        this.gameTime = gameTime;
    }

    public static ExperimentOutcome passed(ObservationBus bus, UUID experimentId, ConceptId concept,
                                           WorldObservation expectedEvidence) {
        requireAuthentic(bus, expectedEvidence);
        return new ExperimentOutcome(experimentId, concept, true, expectedEvidence, expectedEvidence.gameTime());
    }

    public static ExperimentOutcome failed(ObservationBus bus, UUID experimentId, ConceptId concept,
                                           WorldObservation opportunity, long gameTime) {
        requireAuthentic(bus, opportunity);
        return new ExperimentOutcome(experimentId, concept, false, opportunity, gameTime);
    }

    private static void requireAuthentic(ObservationBus bus, WorldObservation obs) {
        if (bus == null || !bus.isAuthentic(obs)) {
            throw new IllegalArgumentException("Experiment outcomes require evidence recorded on the ObservationBus");
        }
    }

    public UUID experimentId() {
        return experimentId;
    }

    public ConceptId concept() {
        return concept;
    }

    public boolean passed() {
        return passed;
    }

    public WorldObservation evidence() {
        return evidence;
    }

    public long gameTime() {
        return gameTime;
    }
}
