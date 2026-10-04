# Emerald

A Minecraft civilization mod. Villagers are persistent citizens who observe the world, open problems, run physical experiments, keep and improve designs, write books, teach each other, and keep developing while you are away.

- Minecraft Java 1.21.1, NeoForge 21.1.x, Java 21, SmartBrainLib 1.16.x
- Mod id: `emerald`
- Design source of truth: *Emerald — AI Villager Civilization Mod Master System & Design Spec*. The rules that matter most are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Layout

| Module | What it is | Depends on Minecraft? |
|---|---|---|
| `simulation-core/` | Citizens, utility, jobs, requests, construction, observation bus, ledger, knowledge, experiments, designs and prototypes, library, teaching, director, offline simulation, economy, sandbox | No |
| `ai/` | `AiProvider`, Ollama client, strict proposal parser and validator, fallback hypothesis source | No (Gson is supplied by Minecraft at runtime) |
| `minecraft-neoforge/` | The mod: `CivVillager` body (SmartBrainLib), SavedData, world and observation adapters, commands, renderer, GameTests | Yes |

The mod jar compiles the pure modules' sources into itself. The sandbox package is excluded from the jar.

## Try it without Minecraft

```bash
./gradlew -Pemerald.coreOnly=true test                     # 83 unit/scenario tests
./gradlew -Pemerald.coreOnly=true :simulation-core:sandbox  # whole first slice end to end -> simulation-core/build/sandbox-report.md
./gradlew -Pemerald.coreOnly=true :simulation-core:sandbox -Pseed=42
./gradlew -Pemerald.coreOnly=true :simulation-core:benchmark
./gradlew -Pemerald.coreOnly=true :simulation-core:replay && python3 tools/build_replay_page.py   # build/emerald-replay.html: watch a run
```

The sandbox stands in for the server. It has a flat world with growing wheat, laying chickens, real hopper and chest mechanics, monsters, and walking bodies. In it, a village founds itself, farms, shares a chest, builds huts, notices wasted eggs, fails and then passes a hopper experiment, writes the result down and teaches it, and installs and then improves a chicken collector. It also survives three unloaded days, a night attack, a save/reload, and the inventor's death. The run reports 18 checks.

## Build and run the mod

```bash
./gradlew build               # mod jar (needs the NeoForge, Mojang and SmartBrainLib mavens)
./gradlew runClient           # dev client
./gradlew runGameTestServer   # 11 in-game GameTests
```

## In game (ops)

| Command | Does |
|---|---|
| `/emerald spawn` | Founds the village here: six citizen records (farmer, builder, researcher, guard, general, child) and their bodies |
| `/emerald bodies` | Re-summons bodies for living citizens whose body is not loaded |
| `/emerald inspect [name]` | Record: needs, personality, alive, body loaded/unloaded, need, task, carried, knowledge |
| `/emerald plan [name]` | Utility scores, assigned job, current routine |
| `/emerald village` | Centre, bias, capabilities, warehouse stock, pen, buildings, projects |
| `/emerald warehouse <pos>` / `library <pos>` / `pen <from> <to>` | Register the warehouse container, the library spot, and the chicken pen's air space |
| `/emerald request` / `request planks` | List requests / builder asks for 16 oak planks |
| `/emerald build hut <pos>` | Order a 5x5 hut (the director also starts huts itself when people are homeless) |
| `/emerald problems`, `experiment`, `design`, `knowledge [name]`, `library`, `ledger` | Science and knowledge introspection |
| `/emerald skills`, `economy`, `offline` | Skill levels and procedure metrics, production/consumption, offline simulation state |
| `/emerald teach <teacher> <student> <concept>` | Manual lossy teaching (the student reaches OBSERVED at most) |

[docs/MANUAL_STEPS.md](docs/MANUAL_STEPS.md) has the in-game verification script and the remaining manual steps.
