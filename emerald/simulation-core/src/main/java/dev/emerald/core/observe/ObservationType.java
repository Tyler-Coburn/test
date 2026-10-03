package dev.emerald.core.observe;

/**
 * Closed catalogue of physical facts the server can report in the first slice.
 * Anything outside this list cannot be evidence, which is what makes AI validation possible.
 */
public enum ObservationType {
    ITEM_SPAWNED,
    HOPPER_PULLED,
    ITEM_STORED,
    CROP_GREW,
    CROP_HARVESTED,
    PISTON_MOVED,
    REDSTONE_SIGNAL,
    BLOCK_PLACED
}
