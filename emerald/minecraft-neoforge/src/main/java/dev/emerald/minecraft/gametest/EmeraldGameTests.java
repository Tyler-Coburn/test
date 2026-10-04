package dev.emerald.minecraft.gametest;

import dev.emerald.ai.AiProposal;
import dev.emerald.ai.HypothesisContext;
import dev.emerald.ai.ProposalRejected;
import dev.emerald.ai.ProposalValidator;
import dev.emerald.core.EmeraldConstants;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.education.Teaching;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.ExperimentOutcome;
import dev.emerald.core.knowledge.KnowState;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.EggWasteDetector;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.research.FallbackHypotheses;
import dev.emerald.core.research.Hypothesis;
import dev.emerald.core.research.ProblemType;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.technology.DesignRegistry;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.minecraft.entity.CivVillager;
import dev.emerald.minecraft.saved.NbtBridge;
import dev.emerald.minecraft.server.EmeraldServer;
import dev.emerald.minecraft.world.ContainerItemStore;
import dev.emerald.minecraft.world.MinecraftWorldPort;
import dev.emerald.minecraft.world.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-game proofs, driven by the real server loop (EmeraldServer ticks the village, SmartBrainLib moves
 * the bodies, vanilla hoppers pull real eggs). Each test uses its own batch so villages never overlap.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(EmeraldConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmeraldGameTests {
    private static final String PLATFORM = "gametest_platform";

    private EmeraldGameTests() {
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Fresh village centred on the platform. Every citizen starts "away" (no body summoned). */
    private static VillageState found(GameTestHelper h) {
        EmeraldServer.resetForTests();
        ServerLevel level = h.getLevel();
        VillageWorld world = EmeraldServer.world().orElseThrow(() -> new GameTestAssertException("Emerald paused"));
        VillageState v = world.found("Testvale", level.dimension().location().toString(),
                Positions.toPos(h.absolutePos(new BlockPos(6, 1, 6))), level.getGameTime(), 1L);
        VillageSimulator.wire(v);
        for (CitizenRecord c : v.citizens().all()) {
            c.bindBody(UUID.randomUUID());   // placeholder link: these citizens are elsewhere
            c.setHunger(0);
            c.setEnergy(100);
        }
        return v;
    }

    private static CitizenRecord citizen(VillageState v, Role role) {
        return v.citizens().firstAlive(role).orElseThrow();
    }

    private static CivVillager summon(GameTestHelper h, VillageState v, Role role, BlockPos rel) {
        CitizenRecord c = citizen(v, role);
        c.unbindBody();
        return EmeraldServer.summonBody(h.getLevel(), c, h.absolutePos(rel))
                .orElseThrow(() -> new GameTestAssertException("could not summon " + role));
    }

    private static ContainerItemStore chest(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, Blocks.CHEST);
        ContainerItemStore store = MinecraftWorldPort.containerAt(h.getLevel(), h.absolutePos(rel));
        check(store != null, "chest has no container");
        return store;
    }

    private static void egg(GameTestHelper h, double x, double y, double z) {
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        ItemEntity egg = new ItemEntity(h.getLevel(), base.getX() + x, base.getY() + y, base.getZ() + z,
                new ItemStack(Items.EGG));
        h.getLevel().addFreshEntity(egg);
    }

    // --- M1 -----------------------------------------------------------------------------------

    @GameTest(template = PLATFORM, batch = "citizen_record_reloads")
    public static void citizen_record_reloads(GameTestHelper h) {
        VillageState v = found(h);
        CompoundTag tag = NbtBridge.toTag(EmeraldServer.world().orElseThrow().toMap());
        VillageWorld back = VillageWorld.fromMap(NbtBridge.toMap(tag), EmeraldServer.world().orElseThrow().config());
        VillageState reloaded = back.get(v.id()).orElseThrow(() -> new GameTestAssertException("village lost"));
        check(reloaded.citizens().size() == 6, "six citizens expected");
        for (CitizenRecord c : v.citizens().all()) {
            CitizenRecord r = reloaded.citizens().get(c.id()).orElseThrow(() -> new GameTestAssertException("lost " + c.name()));
            check(r.role() == c.role() && r.name().equals(c.name()) && r.curiosity() == c.curiosity(), "changed " + c.name());
        }
        h.succeed();
    }

    // --- M3 / M4 / M5 -------------------------------------------------------------------------

    @GameTest(template = PLATFORM, batch = "farmer_deposits_wheat", timeoutTicks = 1200)
    public static void farmer_deposits_wheat(GameTestHelper h) {
        VillageState v = found(h);
        ContainerItemStore wh = chest(h, new BlockPos(1, 1, 1));
        v.setWarehouse(Positions.toPos(h.absolutePos(new BlockPos(1, 1, 1))));
        h.setBlock(new BlockPos(9, 1, 9), Blocks.FARMLAND);
        h.setBlock(new BlockPos(9, 2, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        summon(h, v, Role.FARMER, new BlockPos(5, 1, 5));
        h.succeedWhen(() -> {
            check(wh.count(ItemIds.WHEAT) >= 1, "no wheat deposited yet");
            check(!v.observations().matching(o -> o.type() == ObservationType.CROP_HARVESTED).isEmpty(), "no harvest observed");
            check(!v.observations().matching(o -> o.type() == ObservationType.ITEM_STORED).isEmpty(), "no storage observed");
        });
    }

    @GameTest(template = PLATFORM, batch = "courier_delivers_planks", timeoutTicks = 1200)
    public static void courier_delivers_planks(GameTestHelper h) {
        VillageState v = found(h);
        ContainerItemStore wh = chest(h, new BlockPos(1, 1, 1));
        wh.insert(ItemIds.OAK_PLANKS, 32);
        v.setWarehouse(Positions.toPos(h.absolutePos(new BlockPos(1, 1, 1))));
        summon(h, v, Role.BUILDER, new BlockPos(10, 1, 10));
        summon(h, v, Role.GENERAL, new BlockPos(5, 1, 5));
        CitizenRecord builder = citizen(v, Role.BUILDER);
        ResourceRequest r = v.requests().open(builder.id(), ItemIds.OAK_PLANKS, 16, null, null, h.getLevel().getGameTime());
        h.succeedWhen(() -> {
            check(r.state() == ResourceRequest.State.DELIVERED, "request is " + r.state());
            check(builder.carried().count(ItemIds.OAK_PLANKS) == 16, "builder does not hold the planks");
            check(wh.count(ItemIds.OAK_PLANKS) == 16, "planks did not leave the chest");
        });
    }

    @GameTest(template = PLATFORM, batch = "builder_places_hut", timeoutTicks = 6000)
    public static void builder_places_hut(GameTestHelper h) {
        VillageState v = found(h);
        ContainerItemStore wh = chest(h, new BlockPos(1, 1, 1));
        wh.insert(ItemIds.OAK_PLANKS, 64);
        v.setWarehouse(Positions.toPos(h.absolutePos(new BlockPos(1, 1, 1))));
        summon(h, v, Role.BUILDER, new BlockPos(3, 1, 3));
        summon(h, v, Role.GENERAL, new BlockPos(2, 1, 4));
        ConstructionProject p = v.construction().start(Blueprints.HUT,
                Positions.toPos(h.absolutePos(new BlockPos(5, 1, 5))), citizen(v, Role.BUILDER).id(), h.getLevel().getGameTime());
        h.succeedWhen(() -> {
            check(p.state() == ConstructionProject.State.COMPLETE, "hut " + p.placed() + "/55 placed");
            check(wh.count(ItemIds.OAK_PLANKS) + citizen(v, Role.BUILDER).carried().count(ItemIds.OAK_PLANKS) == 9,
                    "planks were not consumed one per block");
        });
    }

    // --- M6 ------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM, batch = "wasted_egg_opens_problem", timeoutTicks = 200)
    public static void wasted_egg_opens_problem(GameTestHelper h) {
        VillageState v = found(h);
        v.setPen(Box.of(Positions.toPos(h.absolutePos(new BlockPos(2, 1, 8))), Positions.toPos(h.absolutePos(new BlockPos(3, 1, 9)))));
        h.spawn(EntityType.CHICKEN, new BlockPos(2, 1, 8));
        egg(h, 2.5, 1.2, 8.5);
        h.succeedWhen(() -> {
            List<WorldObservation> eggs = v.observations().matching(o -> o.type() == ObservationType.ITEM_SPAWNED && o.isItem(ItemIds.EGG));
            check(!eggs.isEmpty(), "egg spawn not observed");
            EggWasteDetector.evaluate(v, eggs.get(0).gameTime() + 2400, 2400);
            check(v.problems().active(ProblemType.EGGS_WASTED).isPresent(), "EGGS_WASTED not opened");
        });
    }

    /** Pen cell at (6,2,6) over a real hopper at (6,1,6) feeding a chest at (6,0,6). */
    private static Experiment runningExperiment(GameTestHelper h, VillageState v, boolean withHopper) {
        v.setPen(Box.of(Positions.toPos(h.absolutePos(new BlockPos(6, 2, 6))), Positions.toPos(h.absolutePos(new BlockPos(6, 2, 6)))));
        if (withHopper) {
            h.setBlock(new BlockPos(6, 0, 6), Blocks.CHEST);
            h.setBlock(new BlockPos(6, 1, 6), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        }
        h.spawn(EntityType.CHICKEN, new BlockPos(7, 1, 6));
        CitizenRecord researcher = citizen(v, Role.RESEARCHER);
        long now = h.getLevel().getGameTime();
        ResearchProblem problem = v.problems().open(ProblemType.EGGS_WASTED, null, now);
        Hypothesis hyp = FallbackHypotheses.forProblem(problem, researcher.id(), now).orElseThrow();
        Experiment e = ExperimentEngine.propose(v, hyp, researcher, Positions.toPos(h.absolutePos(new BlockPos(6, 1, 6))), now);
        ExperimentEngine.apparatusReady(v, e, now, EmeraldServer.world().orElseThrow().config());
        return e;
    }

    @GameTest(template = PLATFORM, batch = "hopper_experiment_passes", timeoutTicks = 400)
    public static void hopper_experiment_passes(GameTestHelper h) {
        VillageState v = found(h);
        Experiment e = runningExperiment(h, v, true);
        h.runAfterDelay(5, () -> egg(h, 6.5, 2.2, 6.5));
        h.succeedWhen(() -> {
            check(e.phase() == Experiment.Phase.PASSED, "experiment " + e.phase());
            check(citizen(v, Role.RESEARCHER).knowledge().state(ConceptId.HOPPER_PULLS_ITEM) == KnowState.TESTED_TRUE,
                    "HOPPER_PULLS_ITEM not TESTED_TRUE");
            check(v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent(), "no design stored");
        });
    }

    @GameTest(template = PLATFORM, batch = "hopper_experiment_fails_without_hopper", timeoutTicks = 400)
    public static void hopper_experiment_fails_without_hopper(GameTestHelper h) {
        VillageState v = found(h);
        Experiment e = runningExperiment(h, v, false);
        h.runAfterDelay(5, () -> egg(h, 6.5, 2.2, 6.5));
        h.succeedWhen(() -> {
            check(!e.opportunities().isEmpty(), "egg not yet observed in the pen");
            ExperimentEngine.tick(v, e.timeoutAt());
            check(e.phase() == Experiment.Phase.FAILED, "experiment " + e.phase());
            check(citizen(v, Role.RESEARCHER).knowledge().state(ConceptId.HOPPER_PULLS_ITEM) == KnowState.TESTED_FALSE,
                    "expected TESTED_FALSE");
        });
    }

    // --- M7 ------------------------------------------------------------------------------------

    @GameTest(template = PLATFORM, batch = "teaching_copies_observed_not_tested_true")
    public static void teaching_copies_observed_not_tested_true(GameTestHelper h) {
        VillageState v = found(h);
        CitizenRecord teacher = citizen(v, Role.RESEARCHER);
        CitizenRecord child = citizen(v, Role.CHILD);
        WorldObservation pull = v.observations().record(ObservationType.HOPPER_PULLED, v.dimension(), v.center(),
                ItemIds.EGG, teacher.id(), h.getLevel().getGameTime(), null);
        teacher.knowledge().applyOutcome(ExperimentOutcome.passed(v.observations(), UUID.randomUUID(),
                ConceptId.HOPPER_PULLS_ITEM, pull));
        check(Teaching.teach(v, teacher, child, ConceptId.HOPPER_PULLS_ITEM, h.getLevel().getGameTime()) == Teaching.Result.TAUGHT,
                "teaching refused");
        check(child.knowledge().state(ConceptId.HOPPER_PULLS_ITEM) == KnowState.OBSERVED, "child should be OBSERVED");
        h.succeed();
    }

    @GameTest(template = PLATFORM, batch = "design_survives_researcher_death", timeoutTicks = 200)
    public static void design_survives_researcher_death(GameTestHelper h) {
        VillageState v = found(h);
        CivVillager body = summon(h, v, Role.RESEARCHER, new BlockPos(6, 1, 6));
        CitizenRecord researcher = citizen(v, Role.RESEARCHER);
        v.designs().record(DesignRegistry.CHICKEN_COLLECTOR, Map.of(), 1, 0, true, researcher.id(), null,
                ExperimentEngine.COLLECTOR_REQUIRES, Blueprints.COLLECTOR, h.getLevel().getGameTime());
        h.runAfterDelay(5, body::kill);
        h.succeedWhen(() -> {
            check(!researcher.alive(), "researcher record should be dead");
            check(v.citizens().get(researcher.id()).isPresent(), "record must remain");
            check(v.designs().latestAdopted(DesignRegistry.CHICKEN_COLLECTOR).isPresent(), "design lost with inventor");
        });
    }

    // --- M8 ------------------------------------------------------------------------------------

    private static HypothesisContext aiContext(VillageState v) {
        CitizenRecord researcher = citizen(v, Role.RESEARCHER);
        WorldObservation egg = v.observations().record(ObservationType.ITEM_SPAWNED, v.dimension(), v.center(),
                ItemIds.EGG, researcher.id(), 1, null);
        researcher.knowledge().witness(v.observations(), egg);
        ResearchProblem p = v.problems().open(ProblemType.EGGS_WASTED, egg.id(), 2);
        return HypothesisContext.of(v, p, researcher, Map.of(ItemIds.HOPPER, 1));
    }

    @GameTest(template = PLATFORM, batch = "llm_reject_unknown_concept")
    public static void llm_reject_unknown_concept(GameTestHelper h) {
        VillageState v = found(h);
        HypothesisContext ctx = aiContext(v);
        try {
            ProposalValidator.validate(new AiProposal(AiProposal.PROPOSE_HYPOTHESIS, "Magnets attract eggs.",
                    List.of("CHICKEN_LAYING", "EGG_MAGNETISM"), "HOPPER_PULLED"), ctx, null, 3);
            throw new GameTestAssertException("unknown concept accepted");
        } catch (ProposalRejected expected) {
            h.succeed();
        }
    }

    @GameTest(template = PLATFORM, batch = "llm_reject_non_hypothesis_action")
    public static void llm_reject_non_hypothesis_action(GameTestHelper h) {
        VillageState v = found(h);
        HypothesisContext ctx = aiContext(v);
        try {
            ProposalValidator.validate(new AiProposal("PLACE_BLOCK", "Place a hopper.",
                    List.of("CHICKEN_LAYING"), "HOPPER_PULLED"), ctx, null, 3);
            throw new GameTestAssertException("non-hypothesis action accepted");
        } catch (ProposalRejected expected) {
            h.succeed();
        }
    }
}
