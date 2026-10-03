package dev.emerald.core.event;

/** Who or what authored a ledger event. Only SYSTEM_OBSERVED and EXPERIMENT_ENGINE can carry facts. */
public enum Provenance {
    SYSTEM_OBSERVED,
    EXPERIMENT_ENGINE,
    VILLAGE_DIRECTOR,
    JOB_SYSTEM,
    TEACHING,
    PLAYER_COMMAND,
    AI_PROPOSAL;

    public boolean canCarryFact() {
        return this == SYSTEM_OBSERVED || this == EXPERIMENT_ENGINE;
    }
}
