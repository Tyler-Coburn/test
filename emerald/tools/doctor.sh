#!/usr/bin/env bash
# Emerald setup doctor: checks Java, network access to every host the build needs, and disk space,
# then optionally runs each build stage. Usage:
#   tools/doctor.sh            # checks only
#   tools/doctor.sh --build    # checks, then pure tests, sandbox, mod jar, GameTests
set -u
cd "$(dirname "$0")/.."

ok=0; warn=0; bad=0
pass() { printf '  \033[32mOK\033[0m    %s\n' "$1"; ok=$((ok+1)); }
note() { printf '  \033[33mWARN\033[0m  %s\n' "$1"; warn=$((warn+1)); }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; bad=$((bad+1)); }

echo "Java"
if command -v java >/dev/null 2>&1; then
  v=$(java -version 2>&1 | grep -oE 'version "[0-9]+' | grep -oE '[0-9]+$')
  if [ "${v:-0}" = "21" ]; then pass "Java 21 found ($(command -v java))";
  elif [ "${v:-0}" -gt 21 ]; then note "Java $v found; the build pins a Java 21 toolchain, install JDK 21 too (https://adoptium.net/temurin/releases/?version=21)";
  else fail "Java ${v:-?} found; install JDK 21 (https://adoptium.net/temurin/releases/?version=21)"; fi
else
  fail "java not on PATH; install JDK 21 (https://adoptium.net/temurin/releases/?version=21)"
fi

echo "Network (hosts the build and dev runs download from)"
check_host() { # url, purpose, required(1)/optional(0)
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 15 "$1" 2>/dev/null)
  if [ "$code" != "000" ] && [ -n "$code" ]; then pass "$2  ($1 -> $code)";
  elif [ "$3" = "1" ]; then fail "$2 unreachable: $1"; else note "$2 unreachable: $1"; fi
}
check_host https://services.gradle.org/distributions/ "Gradle wrapper distribution" 1
check_host https://plugins.gradle.org/m2/ "Gradle plugin portal (ModDevGradle)" 1
check_host https://repo.maven.apache.org/maven2/ "Maven Central (JUnit, Gson, plugin deps)" 1
check_host https://maven.neoforged.net/releases/ "NeoForge maven (NeoForge, NeoForm)" 1
check_host https://piston-meta.mojang.com/mc/game/version_manifest_v2.json "Mojang version manifest" 1
check_host https://piston-data.mojang.com/ "Mojang client/server jars" 1
check_host https://libraries.minecraft.net/ "Minecraft libraries" 1
check_host https://maven.parchmentmc.org/ "Parchment mappings" 1
check_host https://dl.cloudsmith.io/public/tslat/sbl/maven/ "SmartBrainLib maven" 1
check_host https://resources.download.minecraft.net/ "Minecraft assets (runClient only)" 0
check_host http://localhost:11434/api/tags "Local Ollama (optional AI hypotheses)" 0

echo "Disk"
free_kb=$(df -Pk . | awk 'NR==2 {print $4}')
if [ "${free_kb:-0}" -gt 4000000 ]; then pass "$((free_kb/1024/1024)) GiB free";
else note "only $((free_kb/1024)) MiB free; the first NeoForge setup needs about 3-4 GiB"; fi

echo
echo "Summary: $ok ok, $warn warnings, $bad failures"
if [ "$bad" -gt 0 ]; then
  echo "Without the NeoForge/Mojang/Parchment/SmartBrainLib hosts you can still run:"
  echo "  ./gradlew -Pemerald.coreOnly=true test :simulation-core:sandbox"
fi

if [ "${1:-}" = "--build" ]; then
  set -e
  echo; echo "== Pure tests and sandbox =="; ./gradlew -Pemerald.coreOnly=true test :simulation-core:sandbox
  if [ "$bad" -eq 0 ]; then
    echo; echo "== Mod jar =="; ./gradlew build
    echo; echo "== GameTests =="; ./gradlew runGameTestServer
    echo; echo "Jar: $(ls minecraft-neoforge/build/libs/*.jar)"
  else
    echo "Skipping the mod build: required hosts are unreachable."; exit 1
  fi
fi
[ "$bad" -eq 0 ]
