package dev.emerald.core.data;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.technology.Capabilities;
import dev.emerald.core.technology.Capability;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A committed schema-1 save (a sandbox village after the full first slice). Every future build must
 * still open it, through migrations once the schema changes. Never regenerate it to make a test pass.
 */
class GoldenSaveTest {
    static VillageWorld load() throws IOException {
        try (InputStream in = GoldenSaveTest.class.getResourceAsStream("/golden/schema-v1-village.json")) {
            assertNotNull(in, "golden fixture missing");
            return VillageWorld.fromMap(Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)),
                    SimulationConfig.DEFAULT);
        }
    }

    @Test
    void schemaOneSaveStillLoadsWithItsInvariants() throws IOException {
        VillageState v = load().primary().orElseThrow();
        assertTrue(v.citizens().size() >= 6);
        assertTrue(v.citizens().all().stream().anyMatch(c -> !c.alive()), "the dead inventor's record is kept");
        assertTrue(v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent());
        assertTrue(v.construction().buildings().stream().anyMatch(b -> Blueprints.isCollector(b.type())));
        assertFalse(v.library().all().isEmpty());
        assertTrue(v.ledger().size() > 0 && v.ledger().totalAppended() >= v.ledger().size());
        for (CitizenRecord c : v.citizens().all()) {
            assertTrue(c.hunger() >= 0 && c.hunger() <= 100 && c.energy() >= 0 && c.energy() <= 100);
        }
    }

    @Test
    void capabilitiesComeFromLivingKnowledgeBooksAndDesigns() throws IOException {
        VillageState v = load().primary().orElseThrow();
        assertTrue(Capabilities.of(v).contains(Capability.COLLECT_DROPS));
        assertTrue(Capabilities.of(v).contains(Capability.MOVE_ITEM));
        assertTrue(v.library().find(ConceptId.HOPPER_PULLS_ITEM).isPresent());
    }

    @Test
    void reSavingIsStable() throws IOException {
        VillageWorld w = load();
        String once = Json.write(w.toMap());
        String twice = Json.write(VillageWorld.fromMap(Json.parseObject(once), SimulationConfig.DEFAULT).toMap());
        assertEquals(once, twice);
    }
}
