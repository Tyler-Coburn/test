package dev.emerald.core.job;

/** What a citizen's body is currently doing. Written by the job layer, executed by body behaviours. */
public enum TaskType {
    IDLE,
    FLEE,
    EAT,
    SLEEP,
    HARVEST_CROPS,
    DELIVER_REQUEST,
    BUILD,
    SETUP_EXPERIMENT,
    WATCH_EXPERIMENT,
    PATROL,
    DEFEND,
    TEACH,
    WRITE_BOOK,
    STUDY
}
