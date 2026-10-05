# Setting up Emerald

Everything needed to build, test and play Emerald, on your own machine, on a server, in Claude Code cloud sessions and in GitHub Actions.

## What each task needs

| Task | Needs | Command |
|---|---|---|
| Simulation tests, sandbox run, replay page | JDK 21 + Maven Central | `./gradlew -Pemerald.coreOnly=true test :simulation-core:sandbox` |
| Build the mod jar | JDK 21 + NeoForge, Mojang, Parchment and SmartBrainLib mavens | `./gradlew build` |
| In-game GameTests | same as the jar | `./gradlew runGameTestServer` |
| Dev client | same + Minecraft assets | `./gradlew runClient` |
| AI hypotheses (optional) | Ollama on `localhost:11434` | see section 6 |

`tools/doctor.sh` (macOS/Linux) or `tools\doctor.ps1` (Windows) checks Java, every host below and disk space. Add `--build` (or `-Build`) to run every stage.

## 1. Versions

| Component | Version | Source |
|---|---|---|
| Minecraft | 1.21.1 | target in the spec |
| Java | 21 (Temurin recommended) | NeoForge 1.21.1 requirement |
| NeoForge | 21.1.252 | official `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` template, checked 2026-10-05 |
| ModDevGradle | 2.0.148 | same template; resolved from the Gradle plugin portal |
| Parchment | 2024.11.17 for 1.21.1 | same template and SmartBrainLib's own build |
| SmartBrainLib | 1.16.11 (`SmartBrainLib-neoforge-1.21.1`) | SmartBrainLib `1.21` branch `gradle.properties`; built against NeoForge 21.1.191, allows 21.1.0+ |
| Gradle | 8.14.3 (wrapper) | ModDevGradle 2.0.x supports Gradle 8.8+ |

To move to a newer NeoForge 21.1.x build, change `neo_version` in `gradle.properties`. If Gradle cannot find SmartBrainLib 1.16.11, open https://dl.cloudsmith.io/public/tslat/sbl/maven/net/tslat/smartbrainlib/SmartBrainLib-neoforge-1.21.1/maven-metadata.xml and set `sbl_version` to a listed version.

## 2. Hosts the build downloads from

| Host | Used for |
|---|---|
| `services.gradle.org` | Gradle wrapper distribution |
| `plugins.gradle.org`, `repo.maven.apache.org` | ModDevGradle and its dependencies, JUnit, Gson |
| `maven.neoforged.net` | NeoForge, NeoForm, the NeoForm runtime |
| `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net` | Minecraft version manifest, game jars, libraries |
| `resources.download.minecraft.net` | game assets (only `runClient` needs them) |
| `maven.parchmentmc.org` | Parchment parameter names |
| `dl.cloudsmith.io` (and `maven.cloudsmith.io`) | SmartBrainLib |

A corporate proxy or firewall must allow all of them for `./gradlew build`.

## 3. Your own computer

1. **Install JDK 21.** Get it from https://adoptium.net/temurin/releases/?version=21 and check with `java -version`.
2. **Get the code.** Run `git clone https://github.com/Tyler-Coburn/test.git`, then `cd test/emerald`, then `git checkout ccr-41f8dfb4-81uvah` (until it is merged).
3. **Check the machine.** `tools/doctor.sh` should show no failures.
4. **Build.** `./gradlew build`. The first run downloads and decompiles Minecraft, which takes several minutes and 3–4 GB of disk. The jar lands in `minecraft-neoforge/build/libs/emerald-0.1.0.jar`.
5. **Test in game.** `./gradlew runGameTestServer` runs the 11 GameTests and exits non-zero if any fail.
6. **Play in the dev client.** `./gradlew runClient`, create a creative superflat world, then follow `docs/MANUAL_STEPS.md` section 3.

### IDE

- **IntelliJ IDEA:** File → Open → choose the `emerald` folder (the one containing `settings.gradle`). Set Gradle JVM to 21 in Settings → Build Tools → Gradle. After the sync, the Gradle tool window lists `runClient`, `runServer` and `runGameTestServer` under `minecraft-neoforge` → `mod development`. Install the "Minecraft Development" plugin if you like; it is optional.
- **VS Code:** install "Extension Pack for Java" and "Gradle for Java", open `emerald/`, and run the same tasks from the Gradle view.

## 4. Playing with the built jar

Emerald needs NeoForge 21.1.x and SmartBrainLib on the client and the server.

**With Prism Launcher** (https://prismlauncher.org):
1. Add Instance → Minecraft 1.21.1 → Mod loader: NeoForge 21.1.252 (or newer 21.1.x).
2. Edit → Mods → Download mods → search "SmartBrainLib" → pick the 1.21.1 NeoForge file.
3. Edit → Mods → Add file → `emerald-0.1.0.jar`.
4. Launch and open a world with cheats on: `/emerald spawn`.

**With the official launcher:** run the NeoForge installer from https://neoforged.net (`neoforge-21.1.252-installer.jar` → Install client). Put `emerald-0.1.0.jar` and the SmartBrainLib 1.21.1 NeoForge jar into `.minecraft/mods`, then pick the NeoForge profile.

SmartBrainLib downloads: https://www.curseforge.com/minecraft/mc-mods/smartbrainlib or https://modrinth.com/mod/smartbrainlib.

## 5. Dedicated server

```bash
java -jar neoforge-21.1.252-installer.jar --installServer
echo "eula=true" > eula.txt          # after reading https://aka.ms/MinecraftEULA
cp emerald-0.1.0.jar SmartBrainLib-neoforge-1.21.1-*.jar mods/
./run.sh nogui                         # Windows: run.bat
```

Settings live in `world/serverconfig/emerald-server.toml`: waste window, experiment timeout, farm radius, population cap, newcomer cooldown, offline limit, patrol radius and AI. The village save is `world/data/emerald_villages.dat`. Use `/emerald` commands as an operator (level 2).

## 6. Optional: local AI hypotheses (Ollama)

1. Install Ollama from https://ollama.com/download, then run `ollama pull qwen3` (any model with structured-output support works).
2. In `emerald-server.toml`, set `ai.enabled = true`, `ai.model = "qwen3"`, and `ai.ollamaUrl = "http://localhost:11434"`.
3. Restart. `/emerald ledger` shows `AI_REQUESTED` and `AI_PROPOSAL_ACCEPTED`/`AI_PROPOSAL_REJECTED`.

The model may only propose hypotheses; the village keeps working with Ollama off. Structured outputs are a local Ollama feature, so do not point this at a hosted endpoint that ignores `format`.

## 7. Claude Code cloud sessions

The repository's `.claude/hooks/session-start.sh` runs at the start of each cloud session. It downloads the Gradle wrapper and the simulation dependencies so `./gradlew -Pemerald.coreOnly=true test` works immediately, then writes a host report to `/tmp/emerald-doctor.txt`. It takes effect once merged into the default branch.

To let a cloud session also compile the mod and run GameTests:
1. Open the environment menu in the session's title bar → Edit → Network access → Custom.
2. Keep the default package-manager list.
3. Add these allowed domains: `maven.neoforged.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `maven.parchmentmc.org`, `dl.cloudsmith.io`, `maven.cloudsmith.io`.

Docs: https://code.claude.com/docs/en/cloud-environments#network-access

## 8. GitHub Actions

`.github/workflows/emerald.yml` runs on every push that touches `emerald/`, and on demand (Actions → Emerald → Run workflow). It has two jobs:
- **Simulation tests and sandbox:** unit tests, the end-to-end sandbox and the replay page, uploaded as `simulation-reports`.
- **Mod build and GameTests:** `./gradlew build`, then `runGameTestServer`, uploading the jar and server logs as `emerald-mod`.

The first run (run 1, 2026-10-05) did not start. GitHub's annotation said *"The job was not started because your account is locked due to a billing issue."* Fix it at GitHub → Settings → Billing and plans (payment method or spending limit). Public repositories get Actions minutes free once the account is unlocked. Then re-run the workflow.

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Could not resolve net.neoforged:...` / 403 / timeout | A host from section 2 is blocked. Run `tools/doctor.sh`. |
| `Unsupported class file major version` or toolchain errors | Gradle is running on a JDK other than 21. Set `JAVA_HOME` to JDK 21 and stop old daemons with `./gradlew --stop`. |
| `Could not find net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1:1.16.11` | Pick a listed version (section 1) and set `sbl_version`. |
| Compile errors in `minecraft-neoforge` | The vanilla signatures listed in `docs/MANUAL_STEPS.md` section 0 are the unverified ones. Everything else was checked against NeoForge and SmartBrainLib source. |
| `runGameTestServer` says no tests found | The run passes `-Dneoforge.enabledGameTestNamespaces=emerald`. Make sure `data/emerald/structure/gametest_platform.nbt` is on the classpath (`python3 tools/make_gametest_platform.py <path>` regenerates it). |
| Mod loads but citizens never move | Bodies are summoned by `/emerald spawn` or `/emerald bodies`. A village only runs physically with a player within 128 blocks of its centre or with the centre chunk force-loaded. |
| Out of memory during setup | Raise `org.gradle.jvmargs` in `gradle.properties` (default `-Xmx3G`). |
