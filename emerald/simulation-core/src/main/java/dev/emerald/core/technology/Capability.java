package dev.emerald.core.technology;

/**
 * What a village can do, independent of which blocks or mods provide it. Research reasons over
 * capabilities; mod adapters (later) map their blocks onto these after observing them work.
 */
public enum Capability {
    STORE_ITEM,
    MOVE_ITEM,
    PUSH_BLOCK,
    DETECT_CHANGE,
    HARVEST_CROP,
    COLLECT_DROPS
}
