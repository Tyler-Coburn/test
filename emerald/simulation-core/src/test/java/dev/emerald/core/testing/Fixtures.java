package dev.emerald.core.testing;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

/** Common village setups. */
public final class Fixtures {
    public static final Pos CENTER = new Pos(0, 64, 0);
    public static final Pos WAREHOUSE = new Pos(3, 64, 0);
    /** 3x1x3 air space where chickens stand; floor is y=63. */
    public static final Box PEN = Box.of(new Pos(10, 64, 10), new Pos(12, 64, 12));

    private Fixtures() {
    }

    public static VillageWorld world() {
        return new VillageWorld(SimulationConfig.DEFAULT);
    }

    public static VillageState village() {
        VillageState v = world().found("Testford", FakeWorld.DIM, CENTER, 1000, 42L);
        VillageSimulator.wire(v);
        return v;
    }

    public static CitizenRecord role(VillageState v, Role role) {
        CitizenRecord c = v.citizens().firstAlive(role).orElseThrow();
        c.setHunger(0);
        c.setEnergy(100);
        return c;
    }
}
