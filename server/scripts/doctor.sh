#!/usr/bin/env bash
# =============================================================
#  doctor.sh — Diagnóstico sem iniciar o servidor.
#
#  Útil depois do setup/restore em um PC novo. Confere Java,
#  Maven, Paper, BigaCore, economia, configs básicas e mundo.
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

CHESTSHOP_JAR="ChestShop-3.13-pre-1.jar"
VAULT_JAR="VaultUnlocked-2.20.2.jar"
ETERNAL_JAR="EternalEconomy-v1.0.1.jar"

ERROS=0
AVISOS=0

ok()   { printf '\033[1;32m[ OK ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[AVISO]\033[0m %s\n' "$*"; AVISOS=$((AVISOS + 1)); }
err()  { printf '\033[1;31m[ERRO]\033[0m %s\n' "$*"; ERROS=$((ERROS + 1)); }

java_major() {
    java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

verificar_jar_plugin() {
    local arquivo="$1" descricao="$2"
    if [ -f "$SERVER_DIR/plugins/$arquivo" ]; then
        ok "$descricao encontrado: $arquivo"
    else
        err "$descricao não encontrado: $SERVER_DIR/plugins/$arquivo"
    fi
}

verificar_duplicatas_plugin() {
    local padrao="$1" descricao="$2"
    local quantidade
    quantidade=$(find "$SERVER_DIR/plugins" -maxdepth 1 -type f -iname "$padrao" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$quantidade" -gt 1 ]; then
        err "$descricao tem $quantidade JARs no diretório plugins/. Remova versões antigas antes de iniciar."
    fi
}

echo "Minecraft Server Doctor"
echo "Servidor esperado: $SERVER_DIR"
echo "Paper esperado   : $EXPECTED_JAR"
echo "Economia         : ChestShop 3.13-pre-1 + VaultUnlocked 2.20.2 + EternalEconomy 1.0.1"
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
    if jar tf "$PLUGIN_JAR" 2>/dev/null | grep -qx 'economy.yml'; then
        ok "BigaCore contém economy.yml."
    else
        err "O JAR do BigaCore não contém economy.yml. Recompile pelo setup.sh."
    fi
else
    err "BigaCore não encontrado em $SERVER_DIR/plugins/."
fi

verificar_duplicatas_plugin '*bigacore-*.jar' 'BigaCore'
verificar_jar_plugin "$VAULT_JAR" "VaultUnlocked"
verificar_jar_plugin "$ETERNAL_JAR" "EternalEconomy"
verificar_jar_plugin "$CHESTSHOP_JAR" "ChestShop"
verificar_duplicatas_plugin '*Vault*.jar' 'Vault/VaultUnlocked'
verificar_duplicatas_plugin '*EternalEconomy*.jar' 'EternalEconomy'
verificar_duplicatas_plugin '*ChestShop*.jar' 'ChestShop'

CHESTSHOP_CONFIG="$SERVER_DIR/plugins/ChestShop/config.yml"
if [ -f "$CHESTSHOP_CONFIG" ]; then
    if grep -Eq '^TAX_AMOUNT:[[:space:]]*4([[:space:]]|$)' "$CHESTSHOP_CONFIG"; then
        ok "ChestShop: taxa P2P = 4%."
    else
        err "ChestShop: TAX_AMOUNT não está em 4."
    fi

    if grep -Eq '^SHOP_CREATION_PRICE:[[:space:]]*50([[:space:]]|$)' "$CHESTSHOP_CONFIG"; then
        ok "ChestShop: criação de loja = 50 B$."
    else
        err "ChestShop: SHOP_CREATION_PRICE não está em 50."
    fi

    if grep -Eq '^ALLOW_PARTIAL_TRANSACTIONS:[[:space:]]*false([[:space:]]|$)' "$CHESTSHOP_CONFIG"; then
        ok "ChestShop: transações parciais desativadas."
    else
        err "ChestShop: ALLOW_PARTIAL_TRANSACTIONS deve ser false."
    fi
else
    err "Config do ChestShop não encontrado: $CHESTSHOP_CONFIG"
fi

ETERNAL_CONFIG="$SERVER_DIR/plugins/EternalEconomy/config.yml"
if [ -f "$ETERNAL_CONFIG" ]; then
    # As aspas são obrigatórias no padrão: ao gravar o próprio config no
    # primeiro boot, o EternalEconomy normaliza o número para string e escreve
    # defaultBalance: '250'. Sem aceitar isso, o doctor reprovava um servidor
    # perfeitamente configurado e bloqueava o boot.
    if grep -Eq "^defaultBalance:[[:space:]]*['\"]?250(\.0+)?['\"]?[[:space:]]*$" "$ETERNAL_CONFIG"; then
        ok "EternalEconomy: saldo inicial = 250 B$."
    else
        err "EternalEconomy: defaultBalance não está em 250."
    fi
else
    err "Config do EternalEconomy não encontrado: $ETERNAL_CONFIG"
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
