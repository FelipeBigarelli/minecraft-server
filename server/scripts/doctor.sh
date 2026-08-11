#!/usr/bin/env bash
# =============================================================
#  doctor.sh — Diagnóstico sem iniciar o servidor.
#
#  Útil depois do setup/restore em um PC novo. Confere Java,
#  Maven, Paper, BigaCore, configs básicas e presença do mundo.
#  Não modifica arquivos e NÃO sobe o Minecraft.
# =============================================================
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi

SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
MC_VERSION="${MC_VERSION:-${DEFAULT_MC_VERSION:-26.2}}"
PAPER_BUILD="${PAPER_BUILD:-${DEFAULT_PAPER_BUILD:-92}}"
EXPECTED_JAR="paper-$MC_VERSION-$PAPER_BUILD.jar"

ERROS=0
AVISOS=0

ok()   { printf '\033[1;32m[ OK ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[AVISO]\033[0m %s\n' "$*"; AVISOS=$((AVISOS + 1)); }
err()  { printf '\033[1;31m[ERRO]\033[0m %s\n' "$*"; ERROS=$((ERROS + 1)); }

java_major() {
    java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

echo "Minecraft Server Doctor"
echo "Servidor esperado: $SERVER_DIR"
echo "Paper esperado   : $EXPECTED_JAR"
echo

if command -v java >/dev/null 2>&1; then
    MAJOR=$(java_major)
    if [[ "$MAJOR" =~ ^[0-9]+$ ]] && [ "$MAJOR" -ge 25 ]; then
        ok "Java $MAJOR disponível."
    else
        err "Java 25+ é obrigatório; encontrado: ${MAJOR:-desconhecido}."
    fi
else
    err "java não encontrado no PATH."
fi

if command -v mvn >/dev/null 2>&1; then
    MVN_JAVA=$(mvn -version 2>/dev/null | sed -nE 's/.*Java version: ([0-9]+).*/\1/p' | head -1)
    if [ "${MVN_JAVA:-0}" -ge 25 ] 2>/dev/null; then
        ok "Maven disponível e usando Java $MVN_JAVA."
    else
        warn "Maven existe, mas não consegui confirmar Java 25. Rode: mvn -version"
    fi
else
    err "Maven não encontrado."
fi

if [ -d "$SERVER_DIR" ]; then
    ok "Diretório do servidor existe."
else
    err "Diretório do servidor não existe: $SERVER_DIR"
fi

if [ -f "$SERVER_DIR/$EXPECTED_JAR" ]; then
    ok "$EXPECTED_JAR encontrado."
else
    err "$EXPECTED_JAR não encontrado em $SERVER_DIR. Rode setup.sh novamente."
fi

PLUGIN_JAR=$(find "$SERVER_DIR/plugins" -maxdepth 1 -type f -name 'bigacore-*.jar' 2>/dev/null | head -1)
if [ -n "$PLUGIN_JAR" ]; then
    ok "BigaCore encontrado: $(basename "$PLUGIN_JAR")"
else
    err "BigaCore não encontrado em $SERVER_DIR/plugins/."
fi

if grep -q '^eula=true$' "$SERVER_DIR/eula.txt" 2>/dev/null; then
    ok "EULA marcada como aceita pelo setup."
else
    err "eula.txt não contém eula=true."
fi

if grep -q '^online-mode=true$' "$SERVER_DIR/server.properties" 2>/dev/null; then
    ok "online-mode=true."
else
    err "online-mode=true não foi encontrado em server.properties."
fi

if grep -q '^enable-rcon=true$' "$SERVER_DIR/server.properties" 2>/dev/null; then
    warn "RCON está habilitado. Confirme firewall e nunca exponha 25575 diretamente à internet."
else
    ok "RCON não está habilitado."
fi

MUNDOS=()
for mundo in world world_nether world_the_end; do
    [ -d "$SERVER_DIR/$mundo" ] && MUNDOS+=("$mundo")
done

if [ ${#MUNDOS[@]} -gt 0 ]; then
    ok "Mundo encontrado: ${MUNDOS[*]}"
else
    warn "Nenhum mundo existe ainda. Isso é normal em instalação nova; o primeiro boot gera um mundo se você não restaurar um snapshot."
fi

PROCESSOS=$(ps -eo pid=,comm=,args= 2>/dev/null | awk '$2 == "java" && /(paper|spigot)-[^ ]*\.jar/ {print}')
if [ -n "$PROCESSOS" ]; then
    warn "Há um servidor Minecraft rodando agora. Não inicie uma segunda instância."
    echo "$PROCESSOS"
else
    ok "Nenhum processo Paper/Spigot detectado."
fi

echo
if [ "$ERROS" -eq 0 ]; then
    printf '\033[1;32mDiagnóstico aprovado.\033[0m %d aviso(s).\n' "$AVISOS"
    echo "Para iniciar manualmente: cd \"$SERVER_DIR\" && bash scripts/start.sh"
    exit 0
fi

printf '\033[1;31mDiagnóstico encontrou %d erro(s).\033[0m Corrija antes do primeiro boot.\n' "$ERROS"
exit 1
