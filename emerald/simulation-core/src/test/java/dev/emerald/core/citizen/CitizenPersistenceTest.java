package dev.emerald.core.citizen;

import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.data.DataException;
import dev.emerald.core.data.MigrationRegistry;
import dev.emerald.core.job.TaskType;
import dev.emerald.core.testing.TestData;
import dev.emerald.core.utility.Need;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenPersistenceTest {

    @Test
    void citizenRecordRoundTripsThroughNbtShapedData() {
        CitizenRecord c = new CitizenRecord(UUID.randomUUID(), UUID.randomUUID(), "Ada", Role.RESEARCHER);
        c.setAgeDays(31);
        c.setHunger(42);
        c.setEnergy(77);
        c.setCuriosity(90);
        c.setCaution(15);
        c.setHome(new Pos(1, 64, -3));
        c.bindBody(UUID.randomUUID());
        c.setNeed(Need.EAT);
        c.setTask(TaskType.EAT, "going to warehouse");
        c.carried().insert(ItemIds.WHEAT, 5);

        CitizenRecord back = CitizenRecord.fromMap(TestData.reserialize(c.toMap()));

        assertEquals(c.id(), back.id());
        assertEquals(c.villageId(), back.villageId());
        assertEquals("Ada", back.name());
        assertEquals(Role.RESEARCHER, back.role());
        assertEquals(31, back.ageDays());
        assertEquals(42, back.hunger());
        assertEquals(77, back.energy());
        assertEquals(90, back.curiosity());
        assertEquals(15, back.caution());
        assertTrue(back.alive());
        assertEquals(c.bodyUuid(), back.bodyUuid());
        assertEquals(c.home(), back.home());
        assertEquals(Need.EAT, back.currentNeed());
        assertEquals(TaskType.EAT, back.currentTask());
        assertEquals(5, back.carried().count(ItemIds.WHEAT));
    }

    @Test
    void needsAreClampedTo0To100() {
        CitizenRecord c = new CitizenRecord(UUID.randomUUID(), UUID.randomUUID(), "Bram", Role.FARMER);
        c.setHunger(150);
        c.setEnergy(-5);
        assertEquals(100, c.hunger());
        assertEquals(0, c.energy());
    }

    /** citizen_record_reloads: six founders keep UUIDs, roles and state across save/load. */
    @Test
    void sixFoundersSurviveSaveAndReload() {
        VillageWorld world = new VillageWorld(SimulationConfig.DEFAULT);
        VillageState v = world.found("Testford", "minecraft:overworld", new Pos(0, 64, 0), 500, 7L);
        assertEquals(6, v.citizens().size());
        assertEquals(FounderFactory.FOUNDING_ROLES, v.citizens().all().stream().map(CitizenRecord::role).toList());

        Map<String, Object> saved = TestData.reserialize(world.toMap());
        assertEquals(EmeraldConstants.SCHEMA_VERSION, ((Number) saved.get(MigrationRegistry.SCHEMA_KEY)).intValue());
        VillageWorld reloaded = VillageWorld.fromMap(saved, SimulationConfig.DEFAULT);

        VillageState back = reloaded.primary().orElseThrow();
        assertEquals(v.id(), back.id());
        List<CitizenRecord> before = List.copyOf(v.citizens().all());
        List<CitizenRecord> after = List.copyOf(back.citizens().all());
        assertEquals(6, after.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(before.get(i).id(), after.get(i).id());
            assertEquals(before.get(i).role(), after.get(i).role());
            assertEquals(before.get(i).name(), after.get(i).name());
            assertEquals(before.get(i).hunger(), after.get(i).hunger());
            assertEquals(before.get(i).curiosity(), after.get(i).curiosity());
        }
        assertEquals(v.ledger().size(), back.ledger().size());
    }

    @Test
    void refusesSavesFromANewerSchema() {
        VillageWorld world = new VillageWorld(SimulationConfig.DEFAULT);
        world.found("Testford", "minecraft:overworld", new Pos(0, 64, 0), 0, 1L);
        Map<String, Object> saved = TestData.reserialize(world.toMap());
        saved.put(MigrationRegistry.SCHEMA_KEY, EmeraldConstants.SCHEMA_VERSION + 1);
        assertThrows(DataException.class, () -> VillageWorld.fromMap(saved, SimulationConfig.DEFAULT));
    }

    @Test
    void founderRollIsDeterministicPerSeed() {
        UUID village = UUID.randomUUID();
        List<CitizenRecord> a = FounderFactory.founders(village, 99L);
        List<CitizenRecord> b = FounderFactory.founders(village, 99L);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).id(), b.get(i).id());
            assertEquals(a.get(i).name(), b.get(i).name());
        }
    }
}
