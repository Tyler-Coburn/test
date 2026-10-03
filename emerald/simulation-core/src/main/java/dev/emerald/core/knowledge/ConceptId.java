package dev.emerald.core.knowledge;

/** Closed v1 concept catalogue. Open-ended invention comes after these nine are real. */
public enum ConceptId {
    CHICKEN_LAYING,
    ITEM_ENTITY_SPAWN,
    HOPPER_PULLS_ITEM,
    CHEST_STORES_ITEM,
    CROP_GROWTH,
    CROP_HARVEST,
    WATER_PUSHES_ITEM,
    PISTON_MOVES_BLOCK,
    REDSTONE_SIGNAL;

    public static boolean isKnownName(String name) {
        for (ConceptId c : values()) {
            if (c.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
