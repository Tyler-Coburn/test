package dev.emerald.core.job;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.village.VillageState;

/** Everything a routine step may touch. */
public record RoutineContext(
        VillageState village,
        CitizenRecord citizen,
        BodyPort body,
        WorldPort world,
        SimulationConfig config,
        BlueprintLibrary blueprints
) {
    public long now() {
        return world.gameTime();
    }
}
