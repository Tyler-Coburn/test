package dev.emerald.core.knowledge;

/** How a knowledge entry was obtained. */
public enum KnowledgeSource {
    WITNESSED,
    TAUGHT,
    HYPOTHESIZED,
    EXPERIMENT,
    /** Read from a library writing that cites the proving observation. */
    DOCUMENT,
    VILLAGE_ADOPTION
}
