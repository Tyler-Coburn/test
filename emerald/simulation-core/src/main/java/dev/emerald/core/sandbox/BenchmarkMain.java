package dev.emerald.core.sandbox;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.UUID;

/**
 * Simulation-layer cost per population size: one in-game day of jobs, utility, requests, director
 * and observation for 10..250 citizens, without Minecraft pathfinding or rendering. Use spark in game
 * for the full picture. {@code ./gradlew -Pemerald.coreOnly=true :simulation-core:benchmark}
 */
public final class BenchmarkMain {
    private BenchmarkMain() {
    }

    public static void main(String[] args) {
        int[] sizes = {10, 25, 50, 100, 250};
        System.out.println("citizens | ms per in-game day | ms per server second (20 ticks) | budget used of 50 ms tick");
        for (int n : sizes) {
            double msPerDay = run(n);
            double msPerSecond = msPerDay / (24000 / 20.0);
            System.out.printf("%8d | %18.1f | %31.3f | %5.2f%%%n", n, msPerDay, msPerSecond, msPerSecond / 20 / 50 * 100);
        }
    }

    static double run(int citizens) {
        SimulationConfig cfg = SimulationConfig.DEFAULT;
        VillageWorld world = new VillageWorld(cfg);
        SandboxWorld w = new SandboxWorld(1, 1000, 64, cfg);
        VillageState v = world.found("Bench", SandboxWorld.DIM, new Pos(0, 64, 0), w.time, 1);
        w.bind(v);
        Role[] roles = Role.values();
        for (int i = v.citizens().size(); i < citizens; i++) {
            CitizenRecord c = new CitizenRecord(new UUID(i, citizens), v.id(), "C" + i, roles[i % roles.length]);
            c.setHome(v.center());
            v.citizens().add(c);
        }
        ItemCounter chest = w.chestAt(new Pos(3, 64, 0));
        chest.insert(ItemIds.BREAD, 64 * 20);
        chest.insert(ItemIds.OAK_PLANKS, 64 * 10);
        v.setWarehouse(new Pos(3, 64, 0));
        w.plantField(new Pos(6, 64, -12), 10, 10, 3);
        w.summonMissingBodies(v.center());
        w.runTicks(2400); // warm-up
        long start = System.nanoTime();
        w.runTicks(24000);
        return (System.nanoTime() - start) / 1e6;
    }
}
