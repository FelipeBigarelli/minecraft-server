#!/usr/bin/env bash
# =============================================================
#  start.sh — Sobe o servidor Minecraft e o mantém de pé
#
#  Uso normal (Paper):
#    bash scripts/start.sh
#
#  O script NÃO sai quando o servidor para: ele decide o que
#  fazer pelo motivo da parada.
#
#    /restart no console  -> sobe de novo, no mesmo terminal
#    /stop no console     -> encerra de vez
#    Ctrl+C               -> encerra de vez
#    queda / crash        -> sobe de novo (até 3 vezes em 10 min)
#
#  Rollback para Spigot, sem editar nada:
#    SERVER_FLAVOR=spigot bash scripts/start.sh
#
#  O setup grava defaults persistentes em scripts/server.env.
#  Variáveis passadas na execução continuam tendo prioridade.
# =============================================================
# O Paper executa o restart-script com `sh scripts/start.sh`, e em
# Ubuntu `sh` é o dash. Este arquivo usa array e BASH_SOURCE, que o
# dash não tem: sem esta linha ele morre na primeira linha útil, e
# como o Paper não lê a saída do processo filho, o erro não aparece
# em lugar nenhum. O servidor simplesmente não voltava.
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

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

MC_VERSION="${MC_VERSION:-${DEFAULT_MC_VERSION:-26.3}}"
SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"

# Qual servidor subir: 'paper' (padrão desde 03/08/2026) ou 'spigot'.
# O jar do Spigot continua no disco de propósito, como rota de volta.
SERVER_FLAVOR="${SERVER_FLAVOR:-${DEFAULT_SERVER_FLAVOR:-paper}}"

# O Paper versiona por build dentro da mesma versão do Minecraft.
# Manter o número aqui e no nome do jar torna óbvio, em qualquer log
# ou 'ps aux', qual build está no ar — informação que some se o
# arquivo se chamar só 'paper-26.3.jar'.
PAPER_BUILD="${PAPER_BUILD:-${DEFAULT_PAPER_BUILD:-167}}"

# RAM dedicada ao servidor. Regra prática:
#   deixe pelo menos 1-2 GB livres para o SO.
#   VPS de 4 GB  -> 3G
#   VPS de 8 GB  -> 6G
#   VPS de 16 GB -> 12G
RAM="${RAM:-${DEFAULT_RAM:-4G}}"

cd "$SERVER_DIR"

# O descritor 9 permanece aberto dentro do Java pelo tempo de vida do servidor.
# Impede uma segunda instância iniciada por este script e instalações em cima
# de um runtime no ar.
command -v flock >/dev/null 2>&1 || { echo "[erro] Instale util-linux (flock)." >&2; exit 1; }
[ ! -L .servidor.lock ] || { echo "[erro] Lock não pode ser link simbólico." >&2; exit 1; }
exec 9<>.servidor.lock
flock -n 9 || { echo "[erro] Servidor/instalador já está usando este runtime." >&2; exit 1; }

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

# Console colorido: ativa sozinho se log4j2.xml existir no diretório do servidor.
# Afeta apenas a saída no terminal; logs/latest.log continua sem cor.
if [ -f "$SERVER_DIR/log4j2.xml" ]; then
    JAVA_FLAGS+=(-Dlog4j.configurationFile="$SERVER_DIR/log4j2.xml")
fi

# -------------------------------------------------------------
#  Laço de supervisão
#
#  Antes daqui havia um `exec java`: o script VIRAVA o servidor, e
#  quando o Java saía não sobrava shell nenhum para subir de novo.
#  Era por isso que /restart derrubava o servidor e deixava o
#  terminal no prompt.
#
#  O Paper também não consegue fazer isso sozinho. O restart dele
#  dispara o script de dentro de um shutdown hook, ou seja, com a
#  JVM velha ainda viva — e o flock lá em cima, que existe para
#  impedir duas instâncias, recusaria a nova na hora. Em vez de
#  afrouxar essa trava, quem reinicia é este laço, que já é dono
#  do lock e não disputa com ninguém.
#
#  COMO ELE SABE A DIFERENÇA entre /restart e /stop: os dois saem
#  com código 0. Então o /restart passa a chamar
#  scripts/pedir-restart.sh, que só encosta num arquivo de sinal.
#  Sinal presente = reinicia. Sinal ausente = era /stop, e parar é
#  o que o dono pediu.
# -------------------------------------------------------------
SINAL_RESTART="$SERVER_DIR/.reiniciar"
rm -f "$SINAL_RESTART"

# Pausas do laço, em segundos. Existem como variável porque o teste
# de supervisão precisa rodar os três cenários em menos de um
# segundo — e porque quem tem disco lento às vezes quer mais folga
# entre a queda e a nova tentativa.
ESPERA_RESTART="${ESPERA_RESTART:-3}"
ESPERA_QUEDA="${ESPERA_QUEDA:-10}"

# Ctrl+C precisa ENCERRAR, e não virar mais uma volta do laço. O
# sinal chega ao Java e ao bash ao mesmo tempo; como o bash está
# esperando o filho, a trap só roda depois que o servidor salvou e
# saiu, que é exatamente a hora certa de decidir.
PARAR=0
trap 'PARAR=1' INT TERM

QUEDAS=0
JANELA=$(date +%s)

while true; do
    echo "[start] Subindo $JAR com ${RAM} de RAM..."

    set +e
    java "${JAVA_FLAGS[@]}" -jar "$JAR" nogui
    CODIGO=$?
    set -e

    AGORA=$(date +%s)

    if [ "$PARAR" -eq 1 ]; then
        echo "[start] Interrompido no terminal. Servidor encerrado."
        exit 0
    fi

    if [ -f "$SINAL_RESTART" ]; then
        rm -f "$SINAL_RESTART"
        echo "[start] Reinício pedido pelo console. Subindo de novo em 3s..."
        sleep "$ESPERA_RESTART"
        continue
    fi

    # 130 = Ctrl+C, 143 = kill padrão. Nenhum dos dois é queda.
    if [ "$CODIGO" -eq 0 ] || [ "$CODIGO" -eq 130 ] || [ "$CODIGO" -eq 143 ]; then
        echo "[start] Servidor desligado (código $CODIGO). Até a próxima."
        exit 0
    fi

    # Daqui para baixo é queda. Subir de novo é o certo — mas subir
    # para sempre esconde o problema e enche o disco de log. Três
    # tentativas em dez minutos e o script para, deixando o erro na
    # tela em vez de girar em silêncio.
    if [ $((AGORA - JANELA)) -gt 600 ]; then
        QUEDAS=0
        JANELA=$AGORA
    fi
    QUEDAS=$((QUEDAS + 1))

    if [ "$QUEDAS" -ge 3 ]; then
        echo "[erro] O servidor caiu $QUEDAS vezes em menos de 10 minutos (código $CODIGO)." >&2
        echo "       Não vou subir de novo. Veja logs/latest.log." >&2
        exit "$CODIGO"
    fi

    echo "[start] Queda (código $CODIGO). Tentativa $QUEDAS de 3 — subindo em ${ESPERA_QUEDA}s, Ctrl+C cancela."
    sleep "$ESPERA_QUEDA"
done
