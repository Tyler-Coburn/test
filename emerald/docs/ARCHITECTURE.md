# Emerald architecture

## Source-of-truth rules

1. **CitizenRecord is the person; CivVillager is only the body.** Records live in `VillageSavedData` (`data/emerald_villages.dat` in the overworld). The record's `bodyUuid` names its one canonical body.
   - Unloading keeps that link.
   - A body with a different UUID is a stale duplicate and is discarded when it joins.
   - Killing the canonical body marks the record dead; the record is kept.
2. **The Minecraft server is authoritative for physical facts.** Job routines act through `WorldPort`, and every item transfer is a real container change. Offline results are abstract until the `Materializer` applies them, and reality wins every conflict.
3. **Facts come only from the ObservationBus.** Server-side adapters write to the bus.
   - `KnowledgeBook.witness` rejects observations that are not on the bus.
   - `TESTED_TRUE`/`TESTED_FALSE` need an `ExperimentOutcome`, which requires bus evidence.
   - A library `Writing` can only be produced from a citizen's own tested knowledge, and it cites the proving observation.
4. **SmartBrainLib executes body behaviour only.** `WalkToTaskTarget`, `FleeThreat` and `DefendVillage` read what the job layer decided.
5. **The LLM is proposal-only.** `PROPOSE_HYPOTHESIS` is the single allowed action. Output is parsed strictly and validated, and the deterministic table is always the fallback. AI is off by default.
6. **Designs and books belong to the village.** Personal understanding stays on citizens and can die with them.
7. **Saves are versioned.**
   - `schemaVersion` is on every root, and `MigrationRegistry` upgrades step by step.
   - Saves from a newer build are refused, and unreadable saves are preserved untouched.
   - A committed golden schema-1 save (`simulation-core/src/test/resources/golden/`) must keep loading.

## The loop

```
problem (predicate over state + bus) -> hypothesis (table or validated AI) -> experiment (real apparatus via requests)
  -> PASS / FAIL / ABORT from bus evidence (all kept) -> concept TESTED_* on the researcher
  -> design revision on the village -> builder installs it (no understanding needed)
  -> still wasting? engineering prototype (Mk N) -> field trial from bus evidence -> adopt or reject
  -> books (cite evidence) / teaching (OBSERVED only) / study (documented state) / death (lost unless written or adopted)
```

## Systems

| Package | What it does |
|---|---|
| `utility` | Scores EAT, SLEEP, WORK and FLEE every `utilityIntervalTicks`. Children study by day; for guards under threat, defending is their work. |
| `job` | Fixed deterministic routines: farmer, courier, builder, experiment setup and watch, teach, write book, study, patrol, defend, eat, sleep. `NOTHING` means "no work" and triggers a short idle back-off, so idle jobs don't rescan the world. Finished routines grant skill XP and feed procedure metrics. |
| `request` | OPEN → CLAIMED → DELIVERED, plus BLOCKED and CANCELLED. The resolver order is: already held → in stock (a claim reserves it) → blocked. There is no recursive crafting. |
| `construction` | Blueprints come from `.nbt` or code (5×5 hut, collector Mk I, generated full-floor collector). Each step re-diffs against the world; builders dig soft natural blocks and consume one real item per block. |
| `observe`, `event` | A bounded observation bus with causation, and an append-only bounded ledger with provenance. |
| `knowledge`, `education` | Closed concept catalogue and state rules. Teaching gives at most OBSERVED; reading a book gives the documented state. |
| `research` | EGGS_WASTED detector, experiments (setup → running → pass/fail/abort), problem board. |
| `technology` | Design revisions (prototype → adopted or failed), procedure metrics, capabilities derived from living knowledge, books and designs. |
| `director` | Slow strategy, every `directorIntervalTicks`. It opens and resolves FOOD_LOW and HOMELESS, and finds a building site to start huts. It moves a general to the farm during FOOD_LOW (only if there is farmland). It deploys adopted designs, builds and trials prototypes, hands problems to researchers, and admits newcomers (spare beds, food at 2× reserve, cooldown, population cap, role from demand and village bias). |
| `simulation` | Offline LOD in deterministic steps (seeded by village id and step). Rates come only from what was measured while loaded: counted crop plots, measured collector output, counted stock. Output is pending deltas and queued blocks, which the `Materializer` reconciles on load. |
| `economy` | Production and consumption totals, daily net, measured production rates. |
| `sandbox` | `SandboxWorld` (a fake server) and `FirstSliceScenario` (end-to-end proofs). |

## Module boundaries

`simulation-core` and `ai` import no Minecraft or NeoForge classes; the build enforces this because both compile without Minecraft. The NeoForge module adapts at the edges:
- `Positions` (BlockPos ↔ Pos)
- `NbtBridge` (CompoundTag ↔ neutral maps)
- `ContainerItemStore` (Container ↔ ItemStore)
- `MinecraftWorldPort`
- `CivBodyPort`
- `ObservationAdapter`

## Server ticking

| Every | What |
|---|---|
| 10 ticks | Each loaded citizen with a body: threat sensing, scheduler step, body instructions |
| 20 ticks | Each active village: storage polling, request resolution, experiment timeouts, materialisation, director (at its own cadence), newcomer bodies |
| 200 ticks | Each inactive village: offline simulation |

A village is *active* while its centre is loaded and either a player is within 128 blocks or the chunk is force-loaded.

## Known gaps (deliberate, for later milestones)

- Item movement on water is not observed yet, so WATER_PUSHES_ITEM cannot be witnessed. REDSTONE_SIGNAL is observed from signal-source block updates (rate-limited per position).
- The warehouse is one container block; a double chest exposes only the registered half.
- No crafting: a shortage stays BLOCKED until someone (a player) stocks the warehouse.
- One village per world is driven by the commands. The data model already holds several.
- Families, government, trade and war are out of scope for the first slice, per the spec.
