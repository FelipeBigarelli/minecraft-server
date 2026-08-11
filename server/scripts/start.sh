#!/usr/bin/env bash
# =============================================================
#  start.sh — Sobe o servidor Minecraft com GC otimizado
#
#  Uso normal (Paper):
#    bash scripts/start.sh
#
#  Rollback para Spigot, sem editar nada:
#    SERVER_FLAVOR=spigot bash scripts/start.sh
#
#  O setup grava defaults persistentes em scripts/server.env.
#  Variáveis passadas na execução continuam tendo prioridade.
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Defaults persistentes gerados pelo setup.sh. Este arquivo contém
# somente parâmetros operacionais (diretório, versão, build e RAM),
# nunca segredos. Variáveis de ambiente passadas na execução têm
# prioridade sobre esses defaults.
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi

MC_VERSION="${MC_VERSION:-${DEFAULT_MC_VERSION:-26.2}}"
SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"

# Qual servidor subir: 'paper' (padrão desde 03/08/2026) ou 'spigot'.
# O jar do Spigot continua no disco de propósito, como rota de volta.
SERVER_FLAVOR="${SERVER_FLAVOR:-${DEFAULT_SERVER_FLAVOR:-paper}}"

# O Paper versiona por build dentro da mesma versão do Minecraft.
# Manter o número aqui e no nome do jar torna óbvio, em qualquer log
# ou 'ps aux', qual build está no ar — informação que some se o
# arquivo se chamar só 'paper-26.2.jar'.
PAPER_BUILD="${PAPER_BUILD:-${DEFAULT_PAPER_BUILD:-92}}"

# RAM dedicada ao servidor. Regra prática:
#   deixe pelo menos 1-2 GB livres para o SO.
#   VPS de 4 GB  -> 3G
#   VPS de 8 GB  -> 6G
#   VPS de 16 GB -> 12G
RAM="${RAM:-${DEFAULT_RAM:-4G}}"

cd "$SERVER_DIR"

case "$SERVER_FLAVOR" in
    paper)  JAR="paper-$MC_VERSION-$PAPER_BUILD.jar" ;;
    spigot) JAR="spigot-$MC_VERSION.jar" ;;
    *)      echo "[erro] SERVER_FLAVOR inválido: '$SERVER_FLAVOR' (use 'paper' ou 'spigot')"; exit 1 ;;
esac

if [ ! -f "$JAR" ]; then
    echo "[erro] $JAR não encontrado em $SERVER_DIR."
    echo "       Jars disponíveis:"
    ls -1 ./*.jar 2>/dev/null | sed 's/^/         /' || echo "         (nenhum)"
    exit 1
fi

# -------------------------------------------------------------
#  Flags de GC (as "Aikar's flags"): configuram o G1 para
#  favorecer pausas curtas e previsíveis em vez de throughput.
#  Em servidor de Minecraft isso é o que importa — um GC pause
#  longo aparece como lag spike para os jogadores.
#
#  Continuam válidas no Paper: foram escritas pensando nele.
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

echo "[start] Subindo $JAR com ${RAM} de RAM..."
exec java "${JAVA_FLAGS[@]}" -jar "$JAR" nogui
