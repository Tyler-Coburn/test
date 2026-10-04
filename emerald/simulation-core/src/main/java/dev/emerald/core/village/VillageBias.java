package dev.emerald.core.village;

import dev.emerald.core.citizen.Role;

import java.util.Random;

/**
 * Rolled once at founding. Weights which newcomers arrive and how eagerly knowledge is spread.
 * It never locks a tech tree: any village can still invent anything its problems call for.
 */
public enum VillageBias {
    AGRICULTURAL(Role.FARMER),
    INDUSTRIAL(Role.BUILDER),
    MILITARY(Role.GUARD),
    COMMERCIAL(Role.GENERAL),
    ACADEMIC(Role.RESEARCHER),
    MINING(Role.GENERAL);

    private final Role favouredRole;

    VillageBias(Role favouredRole) {
        this.favouredRole = favouredRole;
    }

    public Role favouredRole() {
        return favouredRole;
    }

    public static VillageBias roll(long seed) {
        VillageBias[] all = values();
        return all[new Random(seed).nextInt(all.length)];
    }
}
