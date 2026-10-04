package dev.emerald.minecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.education.Teaching;
import dev.emerald.core.event.LedgerEvent;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.knowledge.ConceptId;
import dev.emerald.core.knowledge.KnowledgeBook;
import dev.emerald.core.request.ResourceRequest;
import dev.emerald.core.research.Experiment;
import dev.emerald.core.research.ResearchProblem;
import dev.emerald.core.technology.DesignRevision;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.minecraft.server.EmeraldServer;
import dev.emerald.minecraft.world.MinecraftWorldPort;
import dev.emerald.minecraft.world.Positions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/** {@code /emerald ...}: founding, inspection and first-slice debug controls. Ops only (level 2). */
public final class EmeraldCommands {
    private EmeraldCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("emerald").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").executes(EmeraldCommands::spawn))
                .then(Commands.literal("bodies").executes(EmeraldCommands::summonMissingBodies))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx, null))
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(ctx -> inspect(ctx, StringArgumentType.getString(ctx, "citizen")))))
                .then(Commands.literal("village").executes(EmeraldCommands::village))
                .then(Commands.literal("warehouse").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(EmeraldCommands::setWarehouse)))
                .then(Commands.literal("pen").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(EmeraldCommands::setPen))))
                .then(Commands.literal("request")
                        .executes(EmeraldCommands::listRequests)
                        .then(Commands.literal("planks").executes(EmeraldCommands::builderAsksForPlanks)))
                .then(Commands.literal("build").then(Commands.literal("hut")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(EmeraldCommands::buildHut))))
                .then(Commands.literal("knowledge")
                        .executes(ctx -> knowledge(ctx, null))
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(ctx -> knowledge(ctx, StringArgumentType.getString(ctx, "citizen")))))
                .then(Commands.literal("problems").executes(EmeraldCommands::problems))
                .then(Commands.literal("plan")
                        .executes(ctx -> plan(ctx, null))
                        .then(Commands.argument("citizen", StringArgumentType.word())
                                .executes(ctx -> plan(ctx, StringArgumentType.getString(ctx, "citizen")))))
                .then(Commands.literal("library")
                        .executes(EmeraldCommands::library)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(EmeraldCommands::setLibrary)))
                .then(Commands.literal("skills").executes(EmeraldCommands::skills))
                .then(Commands.literal("economy").executes(EmeraldCommands::economy))
                .then(Commands.literal("offline").executes(EmeraldCommands::offline))
                .then(Commands.literal("experiment").executes(EmeraldCommands::experiments))
                .then(Commands.literal("design").executes(EmeraldCommands::designs))
                .then(Commands.literal("ledger").executes(EmeraldCommands::ledger))
                .then(Commands.literal("teach")
                        .then(Commands.argument("teacher", StringArgumentType.word())
                                .then(Commands.argument("student", StringArgumentType.word())
                                        .then(Commands.argument("concept", StringArgumentType.word())
                                                .executes(EmeraldCommands::teach))))));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void say(CommandContext<CommandSourceStack> ctx, String line) {
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String line) {
        ctx.getSource().sendFailure(Component.literal(line));
        return 0;
    }

    private static Optional<VillageState> village(CommandContext<CommandSourceStack> ctx) {
        Optional<VillageState> v = EmeraldServer.world().flatMap(VillageWorld::primary);
        if (v.isEmpty()) {
            String paused = EmeraldServer.pausedReason();
            fail(ctx, paused != null ? "Emerald is paused: " + paused : "No village yet. Run /emerald spawn.");
        }
        return v;
    }

    private static int withVillage(CommandContext<CommandSourceStack> ctx, Function<VillageState, Integer> action) {
        return village(ctx).map(v -> {
            int result = action.apply(v);
            EmeraldServer.markDirty();
            return result;
        }).orElse(0);
    }

    private static Optional<CitizenRecord> citizen(CommandContext<CommandSourceStack> ctx, VillageState v, String query) {
        Optional<CitizenRecord> c = v.citizens().find(query);
        if (c.isEmpty()) {
            fail(ctx, "No single citizen matches '" + query + "' (use a name or UUID prefix).");
        }
        return c;
    }

    // --- M1 / M2 ------------------------------------------------------------------------------

    private static int spawn(CommandContext<CommandSourceStack> ctx) {
        Optional<VillageWorld> world = EmeraldServer.world();
        if (world.isEmpty()) {
            return fail(ctx, "Emerald is paused: " + EmeraldServer.pausedReason());
        }
        if (world.get().primary().isPresent()) {
            return fail(ctx, "A village already exists. Use /emerald inspect, or /emerald bodies to re-summon missing bodies.");
        }
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        VillageState v = world.get().found("Emerald Hollow", level.dimension().location().toString(),
                Positions.toPos(at), level.getGameTime(), level.getSeed());
        dev.emerald.core.village.VillageSimulator.wire(v);
        EmeraldServer.markDirty();
        say(ctx, "Founded " + v.name() + " with " + v.citizens().size() + " citizen records.");
        return 1 + summonMissing(ctx, v);
    }

    private static int summonMissingBodies(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            EmeraldServer.forgetMissingBodies(v);
            return summonMissing(ctx, v);
        });
    }

    private static int summonMissing(CommandContext<CommandSourceStack> ctx, VillageState v) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        int i = 0;
        int summoned = 0;
        for (CitizenRecord c : v.citizens().alive()) {
            i++;
            if (EmeraldServer.body(c.id()).isPresent()) {
                continue;
            }
            BlockPos spot = at.offset((i % 3) - 1, 0, (i / 3) - 1);
            if (EmeraldServer.summonBody(level, c, spot).isPresent()) {
                summoned++;
            }
        }
        say(ctx, "Summoned " + summoned + " citizen bodies.");
        return summoned;
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx, String query) {
        return withVillage(ctx, v -> {
            List<CitizenRecord> list = query == null ? List.copyOf(v.citizens().all())
                    : citizen(ctx, v, query).map(List::of).orElse(List.of());
            for (CitizenRecord c : list) {
                boolean loaded = EmeraldServer.body(c.id()).isPresent();
                say(ctx, String.format(Locale.ROOT,
                        "%s [%s] %s age=%dd hunger=%d energy=%d curiosity=%d caution=%d %s body=%s need=%s task=%s %s carried=%s known=%d",
                        c.name(), c.shortId(), c.role(), c.ageDays(), c.hunger(), c.energy(), c.curiosity(), c.caution(),
                        c.alive() ? "alive" : "DEAD(" + c.deathCause() + ")",
                        loaded ? "loaded" : (c.hasBody() ? "unloaded" : "none"),
                        c.currentNeed(), c.currentTask(), c.taskDetail().isEmpty() ? "" : "(" + c.taskDetail() + ")",
                        c.carried().contents(), c.knowledge().known().size()));
            }
            return list.size();
        });
    }

    // --- M3-M5 --------------------------------------------------------------------------------

    private static int village(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            say(ctx, v.name() + " centre=" + v.center() + " warehouse=" + v.warehouse()
                    + " pen=" + (v.pen() == null ? "none" : v.pen().min() + " .. " + v.pen().max()));
            say(ctx, "citizens alive=" + v.citizens().alive().size() + "/" + v.citizens().size()
                    + " buildings=" + v.construction().buildings().size() + " beds=" + v.construction().totalBeds()
                    + " activeRequests=" + v.requests().active().size()
                    + " observations=" + v.observations().size() + "/" + v.observations().totalRecorded()
                    + " ledger=" + v.ledger().size());
            EmeraldServer.levelOf(v).map(l -> new MinecraftWorldPort(l, v).warehouse())
                    .ifPresent(store -> say(ctx, "warehouse stock: " + store.contents()));
            v.construction().projects().forEach(p -> say(ctx, "project " + p.blueprintId() + " @" + p.origin()
                    + " " + p.state() + " placed=" + p.placed()));
            return 1;
        });
    }

    private static int setWarehouse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (MinecraftWorldPort.containerAt(ctx.getSource().getLevel(), pos) == null) {
            return fail(ctx, "No container at " + pos.toShortString() + ". Place a chest or barrel first.");
        }
        return withVillage(ctx, v -> {
            v.setWarehouse(Positions.toPos(pos));
            v.log("WAREHOUSE_SET", ctx.getSource().getLevel().getGameTime(), Positions.toPos(pos), null, null, null,
                    null, Provenance.PLAYER_COMMAND);
            say(ctx, "Warehouse registered at " + pos.toShortString());
            return 1;
        });
    }

    private static int setPen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        BlockPos b = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        return withVillage(ctx, v -> {
            v.setPen(Box.of(Positions.toPos(a), Positions.toPos(b)));
            say(ctx, "Pen registered: the air space " + a.toShortString() + " .. " + b.toShortString()
                    + ". The experiment hopper goes in the floor below its centre.");
            return 1;
        });
    }

    private static int listRequests(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            if (v.requests().all().isEmpty()) {
                say(ctx, "No requests.");
            }
            for (ResourceRequest r : v.requests().all()) {
                String who = v.citizens().get(r.requester()).map(CitizenRecord::name).orElse("?");
                say(ctx, who + ": " + r.summary());
            }
            return v.requests().all().size();
        });
    }

    private static int builderAsksForPlanks(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            Optional<CitizenRecord> builder = v.citizens().firstAlive(Role.BUILDER);
            if (builder.isEmpty()) {
                return fail(ctx, "No living builder.");
            }
            long now = ctx.getSource().getLevel().getGameTime();
            int count = EmeraldServer.world().map(w -> w.config().plankRequestCount()).orElse(16);
            ResourceRequest r = v.requests().open(builder.get().id(), ItemIds.OAK_PLANKS, count, null, null, now);
            v.log("REQUEST_OPENED", now, null, builder.get().id(), null, null, r.id(), Provenance.PLAYER_COMMAND,
                    "item", ItemIds.OAK_PLANKS, "count", String.valueOf(count));
            say(ctx, builder.get().name() + " asks for " + count + " oak planks: " + r.summary());
            return 1;
        });
    }

    private static int buildHut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        return withVillage(ctx, v -> {
            Optional<CitizenRecord> builder = v.citizens().firstAlive(Role.BUILDER);
            if (builder.isEmpty()) {
                return fail(ctx, "No living builder.");
            }
            if (v.construction().activeFor(builder.get().id()).isPresent()) {
                return fail(ctx, builder.get().name() + " is already building something.");
            }
            ConstructionProject p = v.construction().start(Blueprints.HUT, Positions.toPos(pos), builder.get().id(),
                    ctx.getSource().getLevel().getGameTime());
            int blocks = EmeraldServer.blueprints().get(Blueprints.HUT).map(b -> b.blocks().size()).orElse(0);
            say(ctx, "Hut project " + p.id().toString().substring(0, 8) + " at " + pos.toShortString() + " (" + blocks
                    + " blocks, source: " + EmeraldServer.blueprints().source(Blueprints.HUT) + ")");
            return 1;
        });
    }

    // --- M6 / M7 ------------------------------------------------------------------------------

    private static int knowledge(CommandContext<CommandSourceStack> ctx, String query) {
        return withVillage(ctx, v -> {
            List<CitizenRecord> list = query == null ? List.copyOf(v.citizens().all())
                    : citizen(ctx, v, query).map(List::of).orElse(List.of());
            for (CitizenRecord c : list) {
                String entries = c.knowledge().entries().isEmpty() ? "nothing yet"
                        : String.join("; ", c.knowledge().entries().stream().map(KnowledgeBook::describe).toList());
                say(ctx, c.name() + ": " + entries);
            }
            return list.size();
        });
    }

    private static int problems(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            if (v.problems().all().isEmpty()) {
                say(ctx, "No problems recorded.");
            }
            for (ResearchProblem p : v.problems().all()) {
                say(ctx, p.type() + " " + p.status() + " opened t=" + p.openedAt()
                        + (p.evidence() != null ? " evidence=" + p.evidence().toString().substring(0, 8) : "")
                        + (p.note().isEmpty() ? "" : " (" + p.note() + ")"));
            }
            return v.problems().all().size();
        });
    }

    private static int experiments(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            if (v.experiments().all().isEmpty()) {
                say(ctx, "No experiments yet.");
            }
            for (Experiment e : v.experiments().all()) {
                say(ctx, e.summary());
                say(ctx, "  hypothesis (" + e.hypothesis().origin() + "): " + e.hypothesis().statement());
            }
            return v.experiments().all().size();
        });
    }

    private static int designs(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            if (v.designs().all().isEmpty()) {
                say(ctx, "The village owns no designs yet.");
            }
            for (DesignRevision d : v.designs().all()) {
                String inventor = v.citizens().get(d.inventor())
                        .map(c -> c.name() + (c.alive() ? "" : " (dead)")).orElse("?");
                say(ctx, d.label() + " cost=" + d.cost() + " output=" + d.measuredOutput() + " inventor=" + inventor
                        + " requires=" + d.requires());
            }
            return v.designs().all().size();
        });
    }

    private static int ledger(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            List<LedgerEvent> recent = v.ledger().recent(15);
            recent.forEach(e -> say(ctx, e.summary()));
            return recent.size();
        });
    }

    private static int plan(CommandContext<CommandSourceStack> ctx, String query) {
        return withVillage(ctx, v -> {
            List<CitizenRecord> list = query == null ? v.citizens().alive()
                    : citizen(ctx, v, query).map(List::of).orElse(List.of());
            boolean night = EmeraldServer.levelOf(v).map(l -> new MinecraftWorldPort(l, v).isNight()).orElse(false);
            for (CitizenRecord c : list) {
                boolean threatened = EmeraldServer.body(c.id()).map(b -> b.nearestMonster(8) != null).orElse(false);
                var scores = dev.emerald.core.utility.UtilityScorer.score(new dev.emerald.core.utility.UtilityInputs(
                        c.hunger(), c.energy(), threatened, night, c.effectiveRole(), c.caution()));
                String routine = EmeraldServer.scheduler().current(c.id())
                        .map(r -> r.getClass().getSimpleName() + ": " + r.describe()).orElse("none");
                say(ctx, c.name() + " job=" + c.effectiveRole() + (c.jobOverride() != null ? " (assigned)" : "")
                        + " scores=" + scores + " -> " + c.currentNeed() + " routine=" + routine);
            }
            return list.size();
        });
    }

    private static int library(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            say(ctx, "Library at " + v.libraryPos() + ": " + v.library().all().size() + " writing(s)");
            v.library().all().forEach(w -> say(ctx, "  " + w.concept() + " " + w.state() + " by "
                    + v.citizens().get(w.author()).map(CitizenRecord::name).orElse("?") + " citing "
                    + w.sourceObservation().toString().substring(0, 8)));
            return v.library().all().size();
        });
    }

    private static int setLibrary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        return withVillage(ctx, v -> {
            v.setLibraryPos(Positions.toPos(pos));
            say(ctx, "Library registered at " + pos.toShortString() + " (a lectern or bookshelf works well).");
            return 1;
        });
    }

    private static int skills(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            v.citizens().alive().forEach(c -> say(ctx, c.name() + " " + c.skills()));
            v.skills().all().forEach(s -> say(ctx, String.format(Locale.ROOT, "  %s: %d/%d ok, avg %d ticks%s",
                    s.id(), s.successes(), s.attempts(), s.averageTicks(), s.reliable() ? " [reliable]" : "")));
            return 1;
        });
    }

    private static int economy(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            long now = ctx.getSource().getLevel().getGameTime();
            say(ctx, "produced " + v.economy().producedTotals());
            say(ctx, "consumed " + v.economy().consumedTotals());
            say(ctx, String.format(Locale.ROOT, "net/day wheat=%.1f bread=%.1f eggs=%.1f; measured egg output %.1f/day",
                    v.economy().netPerDay(ItemIds.WHEAT, now), v.economy().netPerDay(ItemIds.BREAD, now),
                    v.economy().netPerDay(ItemIds.EGG, now), v.economy().producedPerDay(ItemIds.EGG)));
            return 1;
        });
    }

    private static int offline(CommandContext<CommandSourceStack> ctx) {
        return withVillage(ctx, v -> {
            say(ctx, (v.loaded() ? "loaded" : "UNLOADED") + "; last simulated t=" + v.lastSimulationTime()
                    + "; snapshot t=" + v.snapshot().takenAt() + " plots=" + v.snapshot().cropPlots()
                    + " chickens=" + v.snapshot().chickens());
            say(ctx, "pending deltas " + v.pending().deltas() + "; queued blocks " + v.pending().blocks().size());
            v.ledger().ofType("OFFLINE_CATCHUP").stream().skip(Math.max(0, v.ledger().ofType("OFFLINE_CATCHUP").size() - 3))
                    .forEach(e -> say(ctx, "  " + e.summary()));
            return 1;
        });
    }

    private static int teach(CommandContext<CommandSourceStack> ctx) {
        String conceptName = StringArgumentType.getString(ctx, "concept").toUpperCase(Locale.ROOT);
        if (!ConceptId.isKnownName(conceptName)) {
            return fail(ctx, "Unknown concept " + conceptName);
        }
        return withVillage(ctx, v -> {
            Optional<CitizenRecord> teacher = citizen(ctx, v, StringArgumentType.getString(ctx, "teacher"));
            Optional<CitizenRecord> student = citizen(ctx, v, StringArgumentType.getString(ctx, "student"));
            if (teacher.isEmpty() || student.isEmpty()) {
                return 0;
            }
            ConceptId concept = ConceptId.valueOf(conceptName);
            Teaching.Result r = Teaching.teach(v, teacher.get(), student.get(), concept,
                    ctx.getSource().getLevel().getGameTime());
            say(ctx, teacher.get().name() + " -> " + student.get().name() + " " + concept + ": " + r
                    + " (student now " + student.get().knowledge().state(concept) + ")");
            return r == Teaching.Result.TAUGHT ? 1 : 0;
        });
    }
}
