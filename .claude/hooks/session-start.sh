#!/bin/bash
# Prepares Claude Code cloud sessions for Emerald (emerald/): downloads the Gradle wrapper and the
# pure-module dependencies so tests run immediately, then reports which Minecraft hosts are reachable.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(pwd)}/emerald"

# Compile the pure modules and their tests (fills the Gradle cache; container state is cached afterwards).
./gradlew -Pemerald.coreOnly=true --no-daemon -q classes testClasses

# Report network access for the full NeoForge build; never fail the session over it.
if ! tools/doctor.sh > /tmp/emerald-doctor.txt 2>&1; then
  echo "Emerald: NeoForge/Mojang/SmartBrainLib hosts are blocked here; use -Pemerald.coreOnly=true (details: /tmp/emerald-doctor.txt)"
fi

echo 'export EMERALD_CORE_ONLY_HINT="./gradlew -Pemerald.coreOnly=true test"' >> "${CLAUDE_ENV_FILE:-/dev/null}"
