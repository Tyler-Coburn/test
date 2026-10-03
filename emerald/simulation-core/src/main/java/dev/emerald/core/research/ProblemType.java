package dev.emerald.core.research;

/** Problems are predicates over village state and the observation log, never invented by a model. */
public enum ProblemType {
    /** Eggs laid in the pen with no automated collection evidence in the window. Researchable. */
    EGGS_WASTED(true),
    /** Stored staple food below citizens x reserve. Handled by farming, not research. */
    FOOD_LOW(false),
    /** Living adults exceed beds. Handled by building a hut, not research. */
    HOMELESS(false);

    private final boolean researchable;

    ProblemType(boolean researchable) {
        this.researchable = researchable;
    }

    public boolean researchable() {
        return researchable;
    }
}
