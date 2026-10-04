package dev.emerald.core.utility;

import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UtilityScorerTest {
    @Test
    void restedFedAdultWorksByDay() {
        assertEquals(Need.WORK, UtilityScorer.pick(new UtilityInputs(10, 90, false, false, Role.FARMER, 50)));
    }

    @Test
    void starvingCitizenEats() {
        assertEquals(Need.EAT, UtilityScorer.pick(new UtilityInputs(90, 90, false, false, Role.BUILDER, 50)));
    }

    @Test
    void nightMeansSleepExceptForGuards() {
        assertEquals(Need.SLEEP, UtilityScorer.pick(new UtilityInputs(10, 80, false, true, Role.FARMER, 50)));
        assertEquals(Need.WORK, UtilityScorer.pick(new UtilityInputs(10, 80, false, true, Role.GUARD, 50)));
    }

    @Test
    void threatBeatsEverythingForCautiousCitizens() {
        assertEquals(Need.FLEE, UtilityScorer.pick(new UtilityInputs(50, 50, true, false, Role.RESEARCHER, 80)));
    }

    @Test
    void childrenStudyByDayButLessThanAdultsWork() {
        double child = UtilityScorer.score(new UtilityInputs(0, 100, false, false, Role.CHILD, 50)).get(Need.WORK);
        double adult = UtilityScorer.score(new UtilityInputs(0, 100, false, false, Role.FARMER, 50)).get(Need.WORK);
        assertTrue(child > 0 && child < adult);
        assertEquals(0.0, UtilityScorer.score(new UtilityInputs(0, 100, false, true, Role.CHILD, 50)).get(Need.WORK));
    }

    @Test
    void guardsDefendInsteadOfFleeing() {
        assertEquals(Need.WORK, UtilityScorer.pick(new UtilityInputs(20, 80, true, false, Role.GUARD, 90)));
    }

    @Test
    void needsDriftWithElapsedTimeAndSleepRestores() {
        CitizenRecord c = new CitizenRecord(UUID.randomUUID(), UUID.randomUUID(), "Ivo", Role.FARMER);
        c.setHunger(0);
        c.setEnergy(50);
        NeedsModel.advance(c, 0, 24000, false);
        assertEquals(20, c.hunger());
        assertEquals(10, c.energy());
        NeedsModel.advance(c, 24000, 24000 + 6000, true);
        assertEquals(50, c.energy());
    }

    @Test
    void eatingConsumesARealItem() {
        CitizenRecord c = new CitizenRecord(UUID.randomUUID(), UUID.randomUUID(), "Juno", Role.FARMER);
        c.setHunger(60);
        c.carried().insert("minecraft:bread", 1);
        assertEquals("minecraft:bread", NeedsModel.eatFromCarried(c));
        assertEquals(20, c.hunger());
        assertEquals(0, c.carried().count("minecraft:bread"));
        assertNull(NeedsModel.eatFromCarried(c));
    }
}
