package dev.emerald.core.utility;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.world.ItemIds;

import java.util.Map;

/** Deterministic hunger/energy drift and food values. Works on elapsed game time, so it also serves offline catch-up. */
public final class NeedsModel {
    /** Hunger rises one point per this many ticks (20 per Minecraft day). */
    public static final int HUNGER_PERIOD = 1200;
    /** Energy changes one step per this many ticks. */
    public static final int ENERGY_PERIOD = 600;

    public static final Map<String, Integer> FOOD_VALUES = Map.of(
            ItemIds.BREAD, 40,
            ItemIds.WHEAT, 12,
            "minecraft:carrot", 15,
            "minecraft:potato", 12,
            "minecraft:baked_potato", 35);

    private NeedsModel() {
    }

    /** Applies drift for the window (fromTick, toTick]; sleeping restores energy instead of draining it. */
    public static void advance(CitizenRecord c, long fromTick, long toTick, boolean sleeping) {
        if (!c.alive() || toTick <= fromTick) {
            return;
        }
        long hungerSteps = toTick / HUNGER_PERIOD - fromTick / HUNGER_PERIOD;
        long energySteps = toTick / ENERGY_PERIOD - fromTick / ENERGY_PERIOD;
        c.setHunger((int) Math.min(100, c.hunger() + hungerSteps));
        if (sleeping) {
            c.setEnergy((int) Math.min(100, c.energy() + energySteps * 4));
        } else {
            c.setEnergy((int) Math.max(0, c.energy() - energySteps));
        }
    }

    /** Eats one unit of the best food the citizen carries. Returns the item eaten, or null. */
    public static String eatFromCarried(CitizenRecord c) {
        String best = null;
        int bestValue = 0;
        for (Map.Entry<String, Integer> e : FOOD_VALUES.entrySet()) {
            if (c.carried().count(e.getKey()) > 0 && e.getValue() > bestValue) {
                best = e.getKey();
                bestValue = e.getValue();
            }
        }
        if (best != null && c.carried().extract(best, 1) == 1) {
            c.setHunger(c.hunger() - bestValue);
            return best;
        }
        return null;
    }

    public static boolean isFood(String itemId) {
        return FOOD_VALUES.containsKey(itemId);
    }
}
