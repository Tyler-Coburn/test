# Emerald

A Minecraft civilization mod. Villagers are persistent citizens who observe the world, open problems, run physical experiments, keep designs and teach each other.

- Minecraft Java 1.21.1, NeoForge 21.1.x, Java 21
- Mod id: `emerald`
- Source of truth for the design: *Emerald — AI Villager Civilization Mod Master System & Design Spec*. The rules that matter most are summarized in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Layout

| Module | What it is | Depends on Minecraft? |
|---|---|---|
| `simulation-core/` | Citizens, utility, jobs, requests, construction, observation bus, ledger, knowledge, experiments, designs, teaching, director | No |
| `ai/` | `AiProvider`, Ollama client, strict proposal parser and validator, fallback hypothesis source | No (Gson is supplied by Minecraft at runtime) |
| `minecraft-neoforge/` | The mod: `CivVillager` body (SmartBrainLib), SavedData, world/observation adapters, commands, renderer | Yes |

The mod jar compiles the two pure modules' sources into itself, so there is one jar and no jar-in-jar.

## Build

```bash
./gradlew build               # everything, including the mod jar (needs the NeoForge/Mojang/SmartBrainLib mavens)
./gradlew runClient           # dev client
./gradlew runGameTestServer   # GameTests (none registered yet, see docs/MANUAL_STEPS.md)
./gradlew -Pemerald.coreOnly=true test   # pure modules only: no Minecraft download needed
```

The first full build downloads Minecraft and NeoForge and can take a while.

## In game (ops)

| Command | Does |
|---|---|
| `/emerald spawn` | Founds the village here: six citizen records (farmer, builder, researcher, guard, general, child) and their bodies |
| `/emerald bodies` | Re-summons bodies for living citizens whose body is gone |
| `/emerald inspect [name]` | Persisted record: needs, personality, alive, body loaded/unloaded, need, task, carried, knowledge count |
| `/emerald village` | Centre, warehouse stock, pen, buildings, projects |
| `/emerald warehouse <pos>` | Registers a chest/barrel as the village warehouse |
| `/emerald request` / `request planks` | Lists requests / builder asks for 16 oak planks |
| `/emerald build hut <pos>` | Starts the 5x5 hut project |
| `/emerald pen <from> <to>` | Registers the chicken pen's air space |
| `/emerald problems`, `experiment`, `knowledge [name]`, `design`, `ledger` | Science-loop introspection |
| `/emerald teach <teacher> <student> <concept>` | Lossy teaching (student reaches OBSERVED at most) |

See [docs/MANUAL_STEPS.md](docs/MANUAL_STEPS.md) for the step-by-step verification script and the one asset you still need to author.
