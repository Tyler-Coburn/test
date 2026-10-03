package dev.emerald.core.job;

/**
 * A fixed, deterministic job procedure, stepped a few times a second. Holds only transient state:
 * after a reload the scheduler simply chooses a fresh routine from the persisted records.
 */
public interface Routine {
    RoutineStatus step(RoutineContext ctx);

    TaskType task();

    /** Human-readable current step, shown by /emerald inspect. */
    String describe();

    /** Why the routine failed, if it did. */
    default String failureReason() {
        return "";
    }
}
