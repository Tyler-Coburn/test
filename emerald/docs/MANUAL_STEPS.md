# Manual steps and in-game verification

## 0. Build where the NeoForge mavens are reachable

The build sandbox used to write this code blocked `maven.neoforged.net`, Mojang's servers, `maven.parchmentmc.org` and `dl.cloudsmith.io`. The NeoForge module has therefore **not been compiled yet**. Its Gradle configuration does load: MDG 2.0.148 resolves and the `runClient`, `runServer` and `runGameTestServer` tasks exist. Only `simulation-core` and `ai` were compiled and tested.

On a normal machine:

```bash
cd emerald
./gradlew build
```

If it fails:
- **NeoForge version**: `neo_version=21.1.191` in `gradle.properties`. Any 21.1.x listed at https://projects.neoforged.net/neoforged/neoforge works.
- **SmartBrainLib**: `sbl_version=1.16.11`. If Cloudsmith does not have it, use the newest `SmartBrainLib-neoforge-1.21.1` version listed at https://dl.cloudsmith.io/public/tslat/sbl/maven/net/tslat/smartbrainlib/.
- **SBL API names** (most likely compile errors, since they were written from the wiki without a jar): `CivVillager`, `WalkToTaskTarget`, `FleeThreat`. Check the `ExtendedBehaviour` method names (`start`, `tick`, `stop`, `shouldKeepRunning`, `getMemoryRequirements`) against the jar.

## 1. Author `hut.nbt` (optional; the code has a fallback)

Until this file exists, `/emerald build hut` uses the built-in 5x5 plank hut (55 planks). To ship a designed hut instead:

1. Make a superflat creative world with the dev client: `./gradlew runClient`.
2. Build the hut within a 5x5 footprint. Lowest wall layer = the first layer saved. Use only blocks a builder can place from items: planks, logs, glass, doors, torches.
3. `/give @s structure_block`, then place it at the hut's north-west corner, one block below the floor. Set the mode to **Save**, the name to `emerald:hut`, relative position `0 1 0`, size `5 4 5`, and turn **Include entities** off. Press **Save**.
4. Copy `runs/client/saves/<world>/generated/emerald/structures/hut.nbt` to `minecraft-neoforge/src/main/resources/data/emerald/structure/hut.nbt`.
5. Restart. The log prints `loaded blueprint emerald:hut`, and `/emerald build hut` reports `source: emerald:structure/hut.nbt`.

Do the same later for `hopper_pen.nbt` (the chicken_collector Mk I blueprint: a pen floor with a hopper feeding a chest) when proof 11 is implemented.

## 2. Verification script

Run each step in a dev world as op.

**M1, persistent citizens**
1. `/emerald spawn` → "Founded … with 6 citizen records", then six bodies are summoned.
2. `/emerald inspect`: note the UUID prefixes, roles, hunger and curiosity.
3. Save and quit, then reload. `/emerald inspect` shows the same UUIDs, roles and values.

**M2, bodies**
4. Bodies wander and look around. `/emerald inspect` shows `body=loaded` and a need/task.
5. Spawn a zombie nearby. The citizens' need becomes `FLEE` and they run.
6. Kill one body with `/kill @e[type=emerald:civ_villager,limit=1]`. The record stays, now `DEAD(...)`.

**M3, farmer and warehouse**
7. Place a chest. `/emerald warehouse <chest pos>`.
8. Plant wheat on farmland near the village centre and bonemeal it to maturity.
9. The farmer walks over, harvests, replants and deposits. Wheat appears in the chest. `/emerald ledger` and `/emerald knowledge <farmer>` show CROP_HARVEST and CHEST_STORES_ITEM observed.

**M4, request and delivery**
10. Put 32 oak planks in the chest. `/emerald request planks`.
11. The general claims the request, walks to the chest, takes 16 planks, walks to the builder and hands them over. `/emerald request` shows `DELIVERED`.

**M5, hut**
12. Put 64 planks in the chest. `/emerald build hut <pos>`.
13. The builder opens 16-plank requests, the general delivers them, and the builder places blocks one at a time. Chest stock drops by one per block. When the hut is finished, `/emerald village` lists a building.

**M6, science**
14. Fence a small pen (a 1x1 or 3x3 air space is fastest) and put chickens in it. `/emerald pen <from> <to>` with the **air** corners. The pen floor is the layer below.
15. Wait for an egg. After the waste window (2400 ticks by default; lower it in `emerald-server.toml`), `/emerald problems` shows `EGGS_WASTED`.
16. Put a hopper in the warehouse. The researcher requests it, the general delivers it, and the researcher places it in the floor under the pen's centre (`/emerald experiment` shows the position).
17. When a chicken lays an egg over the hopper, the experiment PASSES: `/emerald knowledge <researcher>` shows `HOPPER_PULLS_ITEM=TESTED_TRUE`, and `/emerald design` shows `chicken_collector Mk I [adopted]`.
    - Failure path: make the pen large, or block the hopper, so that eggs are laid but none is pulled. At timeout the result is `TESTED_FALSE` and a failed revision is stored.

**M7, teaching and survival**
18. `/emerald teach <researcher> <child> HOPPER_PULLS_ITEM`. The child gets `OBSERVED`, not `TESTED_TRUE`.
19. Kill the researcher's body. `/emerald design` still lists Mk I, with the inventor marked `(dead)`.

**M8, AI (optional)**
20. Run Ollama locally (`ollama run qwen3`). In `world/serverconfig/emerald-server.toml`, set `ai.enabled = true`, then restart. When the next EGGS_WASTED opens, `/emerald ledger` shows `AI_REQUESTED`, followed by either `AI_PROPOSAL_ACCEPTED` or `AI_PROPOSAL_REJECTED` with a fallback. Stop Ollama: the loop continues using the table.

## 3. Not yet done

- NeoForge GameTests: the pure-Java equivalents of the 11 listed scenarios pass in `simulation-core` and `ai`, but none are registered as NeoForge `@GameTest`s yet.
