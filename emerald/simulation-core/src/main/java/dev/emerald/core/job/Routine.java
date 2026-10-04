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

    /** The personal skill this work trains, or null. */
    default dev.emerald.core.citizen.SkillType skill() {
        return null;
    }

    /** Village procedure id for success metrics (skill library). */
    default String procedureId() {
        return task().name().toLowerCase(java.util.Locale.ROOT) + "_v1";
    }

    /** Why the routine failed, if it did. */
    default String failureReason() {
        return "";
    }
}
