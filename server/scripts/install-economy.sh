#!/usr/bin/env bash
# =============================================================
#  install-economy.sh — Instala a stack econômica do BigaCore.
#
#  Stack validada para Paper/Minecraft 26.2:
#    - VaultUnlocked 2.20.2
#    - EternalEconomy 1.0.1
#    - ChestShop 3.13-pre-1
#
#  Os JARs NÃO são versionados no Git. Este script baixa versões
#  fixas de fontes oficiais e valida o que for possível antes de
#  colocar em plugins/.
# =============================================================
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
PLUGIN_DIR="$SERVER_DIR/plugins"
FORCE_PLUGINS="${FORCE_PLUGINS:-0}"

CHESTSHOP_VERSION="3.13-pre-1"
CHESTSHOP_URL="https://github.com/ChestShop-authors/ChestShop-3/releases/download/${CHESTSHOP_VERSION}/ChestShop.jar"
CHESTSHOP_SHA256="3b833a4357aa2b86b87cded143ea4e3fbe908f0c687c09d178450eb871fd0244"

ETERNAL_VERSION="1.0.1"
ETERNAL_URL="https://github.com/EternalCodeTeam/EternalEconomy/releases/download/v${ETERNAL_VERSION}/EternalEconomy-v${ETERNAL_VERSION}.jar"
ETERNAL_SHA256="12960f158930116688566386e81096057f836c222979f148b19bcaa6a2013f1c"

# VaultUnlocked 2.20.2 é publicado oficialmente no Modrinth Maven.
# O version ID é imutável e fixa exatamente a release que queremos.
VAULT_VERSION="2.20.2"
VAULT_MODRINTH_MODULE="ayRaM8J7"
VAULT_MODRINTH_VERSION_ID="cLNipSgw"

log()  { printf '\033[1;36m[economia]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[     ok ]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[   erro ]\033[0m %s\n' "$*" >&2; exit 1; }

mkdir -p "$PLUGIN_DIR"

sha256_file() {
    sha256sum "$1" | cut -d' ' -f1
}

instalar_url_fixa() {
    local nome="$1" destino="$2" url="$3" esperado="$4"

    if [ -f "$destino" ] && [ "$FORCE_PLUGINS" != "1" ]; then
        local atual
        atual="$(sha256_file "$destino")"
        if [ "$atual" = "$esperado" ]; then
            ok "$nome já instalado e checksum confere."
            return 0
        fi
        die "$nome já existe, mas o SHA256 não é o esperado. Remova/revise o arquivo ou use FORCE_PLUGINS=1 conscientemente."
    fi

    local tmp="${destino}.parcial"
    rm -f "$tmp"
    log "Baixando $nome..."
    curl -fL# --max-time 300 -o "$tmp" "$url" || die "Falha ao baixar $nome."

    local obtido
    obtido="$(sha256_file "$tmp")"
    if [ "$obtido" != "$esperado" ]; then
        rm -f "$tmp"
        die "SHA256 inválido para $nome. Esperado=$esperado obtido=$obtido"
    fi

    mv "$tmp" "$destino"
    ok "$nome instalado e verificado."
}

verificar_plugin_yml() {
    local jar_file="$1" nome_esperado="$2" versao_esperada="$3"
    local tmp_dir versao_regex
    tmp_dir="$(mktemp -d)"

    if ! (cd "$tmp_dir" && jar xf "$jar_file" plugin.yml >/dev/null 2>&1); then
        rm -rf "$tmp_dir"
        die "Não consegui ler plugin.yml de $(basename "$jar_file")."
    fi

    grep -Eq "^name:[[:space:]]*${nome_esperado}[[:space:]]*$" "$tmp_dir/plugin.yml" \
        || { rm -rf "$tmp_dir"; die "Plugin inesperado em $(basename "$jar_file")."; }

    # Algumas releases oficiais (ChestShop, por exemplo) acrescentam uma
    # descrição de build depois da versão semântica. O SHA256 continua sendo
    # a verificação forte do binário; aqui confirmamos que a versão declarada
    # começa exatamente na release que fixamos.
    versao_regex="${versao_esperada//./\\.}"
    grep -Eq "^version:[[:space:]]*['\"]?${versao_regex}([[:space:]]|['\"]|$)" "$tmp_dir/plugin.yml" \
        || { rm -rf "$tmp_dir"; die "Versão inesperada em $(basename "$jar_file")."; }

    rm -rf "$tmp_dir"
}

instalar_vault_modrinth() {
    local destino="$PLUGIN_DIR/VaultUnlocked-${VAULT_VERSION}.jar"

    if [ -f "$destino" ] && [ "$FORCE_PLUGINS" != "1" ]; then
        verificar_plugin_yml "$destino" "Vault" "$VAULT_VERSION"
        ok "VaultUnlocked $VAULT_VERSION já instalado e metadata confere."
        return 0
    fi

    log "Baixando VaultUnlocked $VAULT_VERSION pelo Modrinth Maven..."
    mvn -q dependency:get \
        -Dartifact="maven.modrinth:${VAULT_MODRINTH_MODULE}:${VAULT_MODRINTH_VERSION_ID}" \
        -DremoteRepositories="modrinth::default::https://api.modrinth.com/maven" \
        -Dtransitive=false \
        || die "Falha ao baixar VaultUnlocked pelo Modrinth Maven."

    local origem="$HOME/.m2/repository/maven/modrinth/${VAULT_MODRINTH_MODULE}/${VAULT_MODRINTH_VERSION_ID}/${VAULT_MODRINTH_MODULE}-${VAULT_MODRINTH_VERSION_ID}.jar"
    [ -f "$origem" ] || die "Maven concluiu, mas o JAR do VaultUnlocked não foi encontrado em $origem."

    cp "$origem" "$destino"
    verificar_plugin_yml "$destino" "Vault" "$VAULT_VERSION"
    ok "VaultUnlocked $VAULT_VERSION instalado. SHA256=$(sha256_file "$destino")"
}

instalar_url_fixa \
    "ChestShop $CHESTSHOP_VERSION" \
    "$PLUGIN_DIR/ChestShop-${CHESTSHOP_VERSION}.jar" \
    "$CHESTSHOP_URL" \
    "$CHESTSHOP_SHA256"
verificar_plugin_yml "$PLUGIN_DIR/ChestShop-${CHESTSHOP_VERSION}.jar" "ChestShop" "$CHESTSHOP_VERSION"

instalar_vault_modrinth

instalar_url_fixa \
    "EternalEconomy $ETERNAL_VERSION" \
    "$PLUGIN_DIR/EternalEconomy-v${ETERNAL_VERSION}.jar" \
    "$ETERNAL_URL" \
    "$ETERNAL_SHA256"

# Templates econômicos são copiados apenas quando ainda não existem.
# FORCE_CONFIG=1 continua sendo a forma explícita de reaplicá-los.
copiar_template() {
    local origem="$1" destino="$2"
    mkdir -p "$(dirname "$destino")"
    if [ -f "$destino" ] && [ "${FORCE_CONFIG:-0}" != "1" ]; then
        printf '         %-34s (já existe, preservado)\n' "${destino#$SERVER_DIR/}"
        return 0
    fi
    cp "$origem" "$destino"
    printf '         %-34s copiado\n' "${destino#$SERVER_DIR/}"
}

copiar_template \
    "$PROJECT_DIR/server/config/plugins/ChestShop/config.yml" \
    "$PLUGIN_DIR/ChestShop/config.yml"
copiar_template \
    "$PROJECT_DIR/server/config/plugins/EternalEconomy/config.yml" \
    "$PLUGIN_DIR/EternalEconomy/config.yml"

ok "Stack econômica instalada. Nenhum servidor foi iniciado."
