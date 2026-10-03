package dev.emerald.core.citizen;

/** First-slice roles. Deliberately small; more professions arrive only after the egg proof works. */
public enum Role {
    FARMER,
    BUILDER,
    RESEARCHER,
    GUARD,
    GENERAL,
    CHILD;

    public boolean isAdult() {
        return this != CHILD;
    }
}
