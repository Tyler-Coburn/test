package dev.emerald.core.job;

/** RUNNING, DONE (did real work), FAILED (with a reason), NOTHING (no work available; not a completion). */
public enum RoutineStatus { RUNNING, DONE, FAILED, NOTHING }
