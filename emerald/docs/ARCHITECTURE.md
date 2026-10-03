# Emerald architecture

## Source-of-truth rules

1. **CitizenRecord is the person; CivVillager is only the loaded body.** Records live in `VillageSavedData` (`data/emerald_villages.dat` in the overworld). The entity stores only the UUID of the record it embodies. Unloading a body unbinds it; killing a body marks the record dead and keeps it.
2. **The Minecraft server is authoritative for physical facts.** Job routines act through `WorldPort`, and every item transfer is a real container change.
3. **Facts come only from the ObservationBus.** `ObservationBus.record` is called only by server-side adapters (`ObservationAdapter`, and routines after the world confirmed an action). `KnowledgeBook.witness` rejects observations that are not on the bus. `TESTED_TRUE`/`TESTED_FALSE` can only be set with an `ExperimentOutcome`, and its factories require bus evidence.
4. **SmartBrainLib executes body behaviour only.** `WalkToTaskTarget` and `FleeThreat` read a walk goal or flee flag that the job layer wrote. Utility scoring, jobs, research and strategy live in `simulation-core`.
5. **The LLM is proposal-only.** The `ai` module can return exactly one action, `PROPOSE_HYPOTHESIS`. Its output is parsed strictly, validated, and turned into a `Hypothesis`. Pass or fail is still decided by the experiment engine reading the bus. With AI off (the default), the hand-authored `FallbackHypotheses` table drives the same loop.
6. **Designs belong to the village.** `DesignRegistry` sits on `VillageState`, so a design survives its inventor's death. Personal understanding stays on citizens and can be lost.
7. **Saves are versioned.** Every persisted root carries `schemaVersion` (`EmeraldConstants.SCHEMA_VERSION`). `MigrationRegistry` upgrades step by step and refuses saves from a newer build. An unreadable save is preserved untouched and the simulation pauses rather than overwriting it.

## Layers

```
Minecraft world ──events──▶ ObservationAdapter ──▶ ObservationBus ──▶ ExperimentEngine / KnowledgeBook (witness)
      ▲                                                  │
      │ WorldPort (real block/inventory changes)         ▼
Job routines ◀── CitizenScheduler ◀── UtilityScorer   EggWasteDetector / VillageDirector ──▶ ProblemBoard
      │                                                                     │
      ▼                                                                     ▼
CivVillager body (SmartBrainLib: look, move, wander, flee)        HypothesisSource (table or validated AI)
```

- **Utility** (`utility/`): scores EAT, SLEEP, WORK and FLEE from hunger, energy, threat, night, role and caution. It runs every `utilityIntervalTicks`, never every tick.
- **Jobs** (`job/`): fixed, deterministic routines (`FarmerRoutine`, `CourierRoutine`, `BuilderRoutine`, `ExperimentSetupRoutine`, `WatchExperimentRoutine`, `EatRoutine`, `SleepRoutine`). They hold transient state only and are re-chosen after a reload. There is no GOAP yet.
- **Requests** (`request/`): OPEN → CLAIMED → DELIVERED, plus BLOCKED and CANCELLED. The v1 resolver chain is: requester already holds it → cancel; warehouse has unreserved stock → stays claimable; otherwise → blocked. A claim reserves stock. There is no recursive crafting.
- **Construction** (`construction/`): `Blueprint` (from `data/emerald/structure/hut.nbt` when present, else the built-in 5x5 hut), a per-step world diff, material requests in chunks, and one real item consumed per block placed.
- **Science** (`research/`, `knowledge/`, `technology/`, `education/`): EGGS_WASTED → fallback (or AI) hypothesis → researcher requests a real hopper and places it under the pen → an egg spawning in the pen counts as an opportunity → a `HOPPER_PULLED` caused by that egg means PASS, and the window closing after an opportunity means FAIL. Both results are kept, and a pass creates chicken_collector Mk I on the village.
- **Ledger** (`event/`): an append-only, bounded record of important events with causation, correlation and provenance.

## Module boundaries

`simulation-core` and `ai` import no Minecraft or NeoForge classes; the build enforces this because both compile without Minecraft on the classpath. The NeoForge module converts at the edges: `Positions` (BlockPos ↔ Pos), `NbtBridge` (CompoundTag ↔ neutral maps), `ContainerItemStore` (Container ↔ ItemStore), `MinecraftWorldPort` and `CivBodyPort`.

## Known gaps (deliberate, for later milestones)

- REDSTONE_SIGNAL and item movement on water are not observed yet, so WATER_PUSHES_ITEM cannot be witnessed.
- The warehouse is a single container block. A double chest only exposes the clicked half.
- Unloaded citizens do not drift needs or progress work. Offline simulation is M9.
- Builders do not yet place an adopted design (proof 11). That needs `hopper_pen.nbt`.
