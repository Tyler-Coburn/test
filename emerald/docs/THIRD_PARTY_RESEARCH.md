# Third-party research log

Default policy: copy no code, textures, sounds, schematics or prompts from GPL or mixed-license projects. Reimplement ideas independently. Recheck exact branch licenses before any reuse.

This log was compiled from the research packs supplied with the spec (`civ-source-exhaustion-pack.md`, `civ-extracted-patterns*.md`, `civ-scrape-pack.md`). The build sandbox could not reach GitHub, so no reference repository was opened directly in this session.

| Project | URL / branch | License (as recorded) | Studied | Ideas taken | Code copied? |
|---|---|---|---|---|---|
| MineColonies | github.com/ldtteam/minecolonies `version/1.21` | GPL-3.0 | Package and class names; request-system wiki | Citizen record separate from entity; warehouse + delivery resolver order; BLOCKED requests visible to the colony | No |
| MCA Reborn | github.com/Luke100000/minecraft-comes-alive | GPL-3.0 | Feature list only | Families, traits and homes are deferred to later milestones | No |
| VillAIgence | github.com/True-Ruslan/villAIgence `1.21.1` docs/livingworld | Not recorded; docs only | MEMORY_2, EVENTS, CONTEXT, ACTIONS, SEMANTIC_INGESTION | FACT only from server-observed events; action whitelist rechecked server-side; bounded context (8 observations); bounded journals | No |
| SmartBrainLib | github.com/Tslat/SmartBrainLib wiki | MPL-2.0 | Getting started, entity, behaviours, sensors | Used as a **dependency** (not shaded): `SmartBrainOwner`, core/idle activity groups, `ExtendedBehaviour` | No (dependency only) |
| JavaGOAP | github.com/ph1387/JavaGOAP | MIT | README | Not used yet; fixed routines first | No |
| Millénaire (rewrite) | github.com/gblfxt/Millenaire-rewrite-1.21.1 | MIT (lineage uncertain) | Notes only | Population and resource gates for growth (future director) | No |
| Create | github.com/Creators-of-Create/Create `mc1.21.1/dev` | Code MIT, assets all rights reserved | `BlockStressValues` notes | Later capability adapter; not on the classpath | No |
| Ollama docs | docs.ollama.com structured outputs | Docs | `/api/chat` with `format` JSON schema | Constrained decoding and enum re-validation after parse | No |
| NeoForge docs | docs.neoforged.net 1.21.1 | Docs | SavedData, attachments, GameTest, registries | `SavedData.Factory`, `DeferredRegister`, `EntityAttributeCreationEvent` usage | No |

The vanilla villager model and texture are referenced through `ModelLayers.VILLAGER` and `minecraft:textures/entity/villager/villager.png`. They are loaded from the game at runtime and are not copied into this repository.
