package dev.emerald.core.citizen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Rolls the six founding citizens of a village. Deterministic for a given seed. */
public final class FounderFactory {
    public static final List<Role> FOUNDING_ROLES =
            List.of(Role.FARMER, Role.BUILDER, Role.RESEARCHER, Role.GUARD, Role.GENERAL, Role.CHILD);

    private static final String[] NAMES = {
            "Ada", "Bram", "Cyra", "Doran", "Elin", "Fenn", "Gale", "Hesper", "Ivo", "Juno",
            "Kestrel", "Lio", "Mira", "Nils", "Orla", "Pell", "Quill", "Rhea", "Soren", "Tamsin"
    };

    private FounderFactory() {
    }

    public static List<CitizenRecord> founders(UUID villageId, long seed) {
        Random rng = new Random(seed ^ villageId.getMostSignificantBits());
        List<String> pool = new ArrayList<>(List.of(NAMES));
        List<CitizenRecord> out = new ArrayList<>();
        for (Role role : FOUNDING_ROLES) {
            String name = pool.remove(rng.nextInt(pool.size()));
            UUID id = new UUID(rng.nextLong(), rng.nextLong());
            CitizenRecord c = new CitizenRecord(id, villageId, name, role);
            c.setAgeDays(role == Role.CHILD ? 4 + rng.nextInt(6) : 20 + rng.nextInt(40));
            c.setHunger(10 + rng.nextInt(20));
            c.setEnergy(70 + rng.nextInt(30));
            int curiosityBias = role == Role.RESEARCHER ? 30 : 0;
            c.setCuriosity(Math.min(100, 20 + rng.nextInt(60) + curiosityBias));
            int cautionBias = role == Role.GUARD ? -20 : 0;
            c.setCaution(Math.max(0, 25 + rng.nextInt(60) + cautionBias));
            out.add(c);
        }
        return out;
    }
}
