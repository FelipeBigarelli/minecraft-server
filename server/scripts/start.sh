#!/usr/bin/env bash
# =============================================================
#  start.sh — Sobe o servidor Spigot com GC otimizado
# =============================================================
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.2}"
SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"

# RAM dedicada ao servidor. Regra prática:
#   deixe pelo menos 1-2 GB livres para o SO.
#   VPS de 4 GB  -> 3G
#   VPS de 8 GB  -> 6G
#   VPS de 16 GB -> 12G
RAM="${RAM:-4G}"

cd "$SERVER_DIR"

JAR="spigot-$MC_VERSION.jar"
[ -f "$JAR" ] || { echo "[erro] $JAR não encontrado em $SERVER_DIR. Rode setup.sh primeiro."; exit 1; }

# -------------------------------------------------------------
#  Flags de GC (as "Aikar's flags"): configuram o G1 para
#  favorecer pausas curtas e previsíveis em vez de throughput.
#  Em servidor de Minecraft isso é o que importa — um GC pause
#  longo aparece como lag spike para os jogadores.
# -------------------------------------------------------------
JAVA_FLAGS=(
  -Xms"$RAM" -Xmx"$RAM"
  -XX:+UseG1GC
  -XX:+ParallelRefProcEnabled
  -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions
  -XX:+DisableExplicitGC
  -XX:+AlwaysPreTouch
  -XX:G1NewSizePercent=30
  -XX:G1MaxNewSizePercent=40
  -XX:G1HeapRegionSize=8M
  -XX:G1ReservePercent=20
  -XX:G1HeapWastePercent=5
  -XX:G1MixedGCCountTarget=4
  -XX:InitiatingHeapOccupancyPercent=15
  -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem
  -XX:MaxTenuringThreshold=1
  -Dusing.aikars.flags=https://mcflags.emc.gs
  -Daikars.new.flags=true
)

echo "[start] Subindo Spigot $MC_VERSION com ${RAM} de RAM..."
exec java "${JAVA_FLAGS[@]}" -jar "$JAR" nogui
