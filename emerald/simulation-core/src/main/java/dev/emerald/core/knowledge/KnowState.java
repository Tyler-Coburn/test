package dev.emerald.core.knowledge;

/** How far a citizen (or village) has taken a concept. Failed tests are retained as TESTED_FALSE. */
public enum KnowState {
    UNKNOWN,
    OBSERVED,
    HYPOTHESIS,
    TESTED_TRUE,
    TESTED_FALSE,
    ADOPTED;

    public boolean isTested() {
        return this == TESTED_TRUE || this == TESTED_FALSE || this == ADOPTED;
    }

    public boolean isConfirmedTrue() {
        return this == TESTED_TRUE || this == ADOPTED;
    }
}
