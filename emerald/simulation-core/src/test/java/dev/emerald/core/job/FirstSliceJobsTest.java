package dev.emerald.core.job;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprint;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.testing.FakeWorld;
import dev.emerald.core.testing.Fixtures;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirstSliceJobsTest {

    /** farmer_deposits_wheat */
    @Test
    void farmerHarvestsReplantsAndDepositsWheat() {
        VillageState v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        CitizenRecord farmer = Fixtures.role(v, Role.FARMER);
        w.spawnBody(farmer, Fixtures.CENTER);
        List<Pos> field = List.of(new Pos(5, 64, 5), new Pos(6, 64, 5), new Pos(7, 64, 5));
        field.forEach(w::matureWheat);

        for (int i = 0; i < 80 && w.warehouse.count(ItemIds.WHEAT) < 3; i++) {
            w.step(v, null);
        }

        assertEquals(3, w.warehouse.count(ItemIds.WHEAT), "all harvested wheat reaches the warehouse");
        field.forEach(p -> assertEquals(0, w.crops.get(p), "crop replanted at " + p));
        List<WorldObservation> harvests = v.observations().matching(o -> o.type() == ObservationType.CROP_HARVESTED);
        assertEquals(3, harvests.size());
        WorldObservation stored = v.observations().matching(o -> o.type() == ObservationType.ITEM_STORED).get(0);
        assertEquals(harvests.get(2).id(), stored.causedBy(), "storage cites the harvest that caused it");
        assertEquals(KnowState.OBSERVED, farmer.knowledge().state(ConceptId.CROP_HARVEST), "the farmer witnessed it");
        assertEquals(KnowState.OBSERVED, farmer.knowledge().state(ConceptId.CHEST_STORES_ITEM));
    }

    @Test
    void farmerWithoutWarehouseFailsAndLogsWhy() {
        VillageState v = Fixtures.village();
        FakeWorld w = new FakeWorld();
        CitizenRecord farmer = Fixtures.role(v, Role.FARMER);
        w.spawnBody(farmer, Fixtures.CENTER);
        w.matureWheat(new Pos(2, 64, 2));
        for (int i = 0; i < 20; i++) {
            w.step(v, null);
        }
        assertTrue(v.ledger().ofType("TASK_FAILED").stream()
                .anyMatch(e -> e.payload().get("reason").contains("no warehouse")));
    }

    /** courier_delivers_planks */
    @Test
    void courierDeliversSixteenPlanksToTheBuilder() {
        VillageState v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.OAK_PLANKS, 32);
        CitizenRecord builder = Fixtures.role(v, Role.BUILDER);
        CitizenRecord general = Fixtures.role(v, Role.GENERAL);
        w.spawnBody(builder, new Pos(-6, 64, 4));
        w.spawnBody(general, Fixtures.CENTER);

        ResourceRequest r = v.requests().open(builder.id(), ItemIds.OAK_PLANKS, 16, null, null, w.time);
        for (int i = 0; i < 80 && r.state() != ResourceRequest.State.DELIVERED; i++) {
            w.step(v, null);
        }

        assertEquals(ResourceRequest.State.DELIVERED, r.state());
        assertEquals(general.id(), r.carrier());
        assertEquals(16, builder.carried().count(ItemIds.OAK_PLANKS), "planks physically moved to the builder");
        assertEquals(0, general.carried().count(ItemIds.OAK_PLANKS));
        assertEquals(16, w.warehouse.count(ItemIds.OAK_PLANKS), "and physically left the warehouse");
        assertEquals(1, v.ledger().ofType("REQUEST_DELIVERED").size());
    }

    @Test
    void requestBlocksWhenWarehouseIsShort() {
        VillageState v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.OAK_PLANKS, 4);
        CitizenRecord builder = Fixtures.role(v, Role.BUILDER);
        w.spawnBody(Fixtures.role(v, Role.GENERAL), Fixtures.CENTER);
        ResourceRequest r = v.requests().open(builder.id(), ItemIds.OAK_PLANKS, 16, null, null, w.time);
        for (int i = 0; i < 10; i++) {
            w.step(v, null);
        }
        assertEquals(ResourceRequest.State.BLOCKED, r.state());
        assertEquals(4, w.warehouse.count(ItemIds.OAK_PLANKS));
    }

    /** builder_places_hut */
    @Test
    void builderRequestsMaterialsAndPlacesTheHutFromRealItems() {
        VillageState v = Fixtures.village();
        v.setWarehouse(Fixtures.WAREHOUSE);
        FakeWorld w = new FakeWorld();
        w.warehouse = new ItemCounter();
        w.warehouse.insert(ItemIds.OAK_PLANKS, 64);
        CitizenRecord builder = Fixtures.role(v, Role.BUILDER);
        CitizenRecord general = Fixtures.role(v, Role.GENERAL);
        w.spawnBody(builder, Fixtures.CENTER);
        w.spawnBody(general, Fixtures.CENTER);
        Pos origin = new Pos(-10, 64, -10);
        ConstructionProject p = v.construction().start(Blueprints.HUT, origin, builder.id(), w.time);

        for (int i = 0; i < 2000 && p.state() == ConstructionProject.State.ACTIVE; i++) {
            w.step(v, null);
        }

        assertEquals(ConstructionProject.State.COMPLETE, p.state());
        Blueprint hut = Blueprints.fallbackHut();
        for (BlueprintBlock b : hut.blocks()) {
            assertEquals(b.blockId(), w.blocks.get(origin.offset(b.rel())));
        }
        assertEquals(55, p.placed());
        assertEquals(64 - 55, w.warehouse.count(ItemIds.OAK_PLANKS) + builder.carried().count(ItemIds.OAK_PLANKS),
                "every placed block consumed one real plank");
        assertEquals(1, v.construction().buildings().size());
        assertEquals(55, v.observations().matching(o -> o.type() == ObservationType.BLOCK_PLACED).size());
        assertTrue(v.requests().all().stream().allMatch(r -> r.state() == ResourceRequest.State.DELIVERED),
                "all material came through the request board");
        assertEquals(1, v.ledger().ofType("BUILDING_COMPLETED").size());
    }
}
