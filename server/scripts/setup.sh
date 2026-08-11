#!/usr/bin/env bash
# =============================================================
#  setup.sh — Instala o servidor Paper 26.2 do zero.
#
#  Idempotente: pode rodar de novo com segurança. Nada que já
#  exista é sobrescrito (jar, mundo, configs editados).
#
#  Uso, a partir da raiz do projeto clonado:
#      bash server/scripts/setup.sh
#
#  Variáveis opcionais:
#      MC_OP=SeuNick      vira operador automaticamente
#      RAM=8G             RAM padrão persistida para o start.sh
#      SERVER_DIR=~/mc    outro diretório de instalação
#      FORCE_CONFIG=1     sobrescreve configs/defaults existentes
# =============================================================
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.2}"
PAPER_BUILD="${PAPER_BUILD:-92}"
RAM="${RAM:-4G}"
SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"
FORCE_CONFIG="${FORCE_CONFIG:-0}"

PAPER_SHA256_DEFAULT="059d00bbce0fa1707739618b3276f5c80b9655dc0f964306fa799a9c7cb01cc2"
PAPER_API="https://fill.papermc.io/v3/projects/paper"
UA="biga-mc-server/1.0"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

log()  { printf '\033[1;36m[setup]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[  ok ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[aviso]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[erro]\033[0m %s\n' "$*" >&2; exit 1; }

log "Projeto  : $PROJECT_DIR"
log "Servidor : $SERVER_DIR"
log "RAM      : $RAM"
echo

# -------------------------------------------------------------
# 1. Dependências do sistema
# -------------------------------------------------------------
log "Verificando dependências..."
FALTANDO=()
for cmd in curl tar screen; do
    command -v "$cmd" >/dev/null 2>&1 || FALTANDO+=("$cmd")
done
command -v mvn >/dev/null 2>&1 || FALTANDO+=("maven")

if [ ${#FALTANDO[@]} -gt 0 ]; then
    log "Instalando: ${FALTANDO[*]}"
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update -qq
        sudo apt-get install -y "${FALTANDO[@]}"
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y "${FALTANDO[@]}"
    elif command -v pacman >/dev/null 2>&1; then
        sudo pacman -S --noconfirm "${FALTANDO[@]}"
    else
        die "Gerenciador de pacotes não reconhecido. Instale manualmente: ${FALTANDO[*]}"
    fi
fi
ok "Dependências presentes."

# -------------------------------------------------------------
# 2. Java 25
# -------------------------------------------------------------
versao_java() {
    "$1" -version 2>&1 | head -1 | grep -oP '(?<=version ")[0-9]+' || echo 0
}

if ! command -v java >/dev/null 2>&1 || [ "$(versao_java java)" -lt 25 ]; then
    log "Instalando OpenJDK 25..."
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get install -y openjdk-25-jdk || {
            warn "openjdk-25-jdk não está nos repositórios desta distro."
            warn "Instale pelo Adoptium: https://adoptium.net/temurin/releases/?version=25"
            die  "Java 25 é obrigatório para o Minecraft $MC_VERSION."
        }
    else
        die "Instale o JDK 25 manualmente e rode este script de novo."
    fi
fi
ok "Java $(versao_java java) ativo."

JDK25_PATH="$(dirname "$(dirname "$(readlink -f "$(command -v javac || command -v java)")")")"
if [ -z "${JAVA_HOME:-}" ] || [ "$(versao_java "$JAVA_HOME/bin/java")" -lt 25 ] 2>/dev/null; then
    export JAVA_HOME="$JDK25_PATH"
    warn "JAVA_HOME não apontava para o JDK 25. Ajustado nesta sessão para:"
    warn "  $JAVA_HOME"
    warn "Para tornar permanente, adicione ao seu ~/.bashrc ou ~/.zshrc:"
    warn "  export JAVA_HOME=$JAVA_HOME"
fi
ok "Maven usando Java $(mvn -version 2>/dev/null | grep -oP '(?<=Java version: )[0-9]+' || echo '?')."

# -------------------------------------------------------------
# 3. Estrutura de diretórios
# -------------------------------------------------------------
mkdir -p "$SERVER_DIR"/{plugins,scripts,config}
ok "Diretórios criados."

# -------------------------------------------------------------
# 4. Baixar o Paper
# -------------------------------------------------------------
JAR="paper-$MC_VERSION-$PAPER_BUILD.jar"

if [ -f "$SERVER_DIR/$JAR" ]; then
    ok "$JAR já existe — pulando download."
else
    if [ "$PAPER_BUILD" = "92" ]; then
        SHA="$PAPER_SHA256_DEFAULT"
        URL="https://fill-data.papermc.io/v1/objects/$SHA/$JAR"
    else
        log "Consultando a API do PaperMC para a build $PAPER_BUILD..."
        command -v jq >/dev/null 2>&1 || die "jq é necessário para builds != 92. Instale-o ou use PAPER_BUILD=92."
        RESP=$(curl -fsS --max-time 30 -H "User-Agent: $UA" \
               "$PAPER_API/versions/$MC_VERSION/builds/$PAPER_BUILD") \
               || die "Build $PAPER_BUILD não encontrada para o Minecraft $MC_VERSION."
        SHA=$(echo "$RESP" | jq -r '.downloads."server:default".checksums.sha256')
        URL=$(echo "$RESP" | jq -r '.downloads."server:default".url')
        [ "$SHA" != "null" ] || die "Resposta inesperada da API do PaperMC."
    fi

    log "Baixando $JAR (~59 MB)..."
    curl -fL# --max-time 600 -H "User-Agent: $UA" -o "$SERVER_DIR/$JAR.parcial" "$URL" \
        || die "Falha no download do Paper."

    log "Verificando SHA256..."
    OBTIDO=$(sha256sum "$SERVER_DIR/$JAR.parcial" | cut -d' ' -f1)
    if [ "$OBTIDO" != "$SHA" ]; then
        rm -f "$SERVER_DIR/$JAR.parcial"
        die "Checksum não confere. Download corrompido ou adulterado — arquivo descartado."
    fi
    mv "$SERVER_DIR/$JAR.parcial" "$SERVER_DIR/$JAR"
    ok "$JAR baixado e verificado."
fi

# -------------------------------------------------------------
# 5. EULA da Mojang
# -------------------------------------------------------------
if ! grep -q "eula=true" "$SERVER_DIR/eula.txt" 2>/dev/null; then
    log "Aceitando a EULA da Mojang (https://aka.ms/MinecraftEULA)..."
    echo "eula=true" > "$SERVER_DIR/eula.txt"
fi
ok "EULA aceita."

# -------------------------------------------------------------
# 6. Configs
# -------------------------------------------------------------
copiar_config() {
    local origem="$1" destino="$2"
    [ -f "$origem" ] || return 0
    if [ -f "$destino" ] && [ "$FORCE_CONFIG" != "1" ]; then
        printf '         %-34s (já existe, preservado)\n' "$(basename "$destino")"
    else
        cp "$origem" "$destino"
        printf '         %-34s copiado\n' "$(basename "$destino")"
    fi
}

log "Instalando configs..."
copiar_config "$PROJECT_DIR/server/config/server.properties" "$SERVER_DIR/server.properties"
copiar_config "$PROJECT_DIR/server/config/bukkit.yml"        "$SERVER_DIR/bukkit.yml"
copiar_config "$PROJECT_DIR/server/config/spigot.yml"        "$SERVER_DIR/spigot.yml"
copiar_config "$PROJECT_DIR/server/config/commands.yml"      "$SERVER_DIR/commands.yml"
copiar_config "$PROJECT_DIR/server/config/paper/paper-global.yml" \
              "$SERVER_DIR/config/paper-global.yml"
copiar_config "$PROJECT_DIR/server/config/paper/paper-world-defaults.yml" \
              "$SERVER_DIR/config/paper-world-defaults.yml"
ok "Configs instalados."

# -------------------------------------------------------------
# 7. Scripts operacionais + defaults persistentes
# -------------------------------------------------------------
cp "$PROJECT_DIR"/server/scripts/{start.sh,backup.sh,export-world.sh,restore-world.sh,doctor.sh,setup.sh} "$SERVER_DIR/scripts/"
cp "$PROJECT_DIR"/server/scripts/minecraft.service "$SERVER_DIR/scripts/" 2>/dev/null || true
chmod +x "$SERVER_DIR"/scripts/*.sh

RUNTIME_ENV="$SERVER_DIR/scripts/server.env"
if [ -f "$RUNTIME_ENV" ] && [ "$FORCE_CONFIG" != "1" ]; then
    printf '         %-34s (já existe, preservado)\n' "server.env"
else
    {
        printf 'DEFAULT_SERVER_DIR=%q\n' "$SERVER_DIR"
        printf 'DEFAULT_MC_VERSION=%q\n' "$MC_VERSION"
        printf 'DEFAULT_PAPER_BUILD=%q\n' "$PAPER_BUILD"
        printf 'DEFAULT_RAM=%q\n' "$RAM"
        printf 'DEFAULT_SERVER_FLAVOR=%q\n' "paper"
    } > "$RUNTIME_ENV"
    printf '         %-34s criado\n' "server.env"
fi
ok "Scripts e defaults instalados em $SERVER_DIR/scripts/."

# -------------------------------------------------------------
# 8. Compilar e instalar o BigaCore
# -------------------------------------------------------------
log "Compilando o plugin..."
( cd "$PROJECT_DIR/plugin" && mvn -q clean package ) || die "Falha ao compilar o BigaCore."

PLUGIN_JAR=$(ls -1 "$PROJECT_DIR"/plugin/target/bigacore-*.jar 2>/dev/null \
             | grep -v original | head -1)
[ -n "$PLUGIN_JAR" ] || die "Jar do plugin não foi gerado."
cp "$PLUGIN_JAR" "$SERVER_DIR/plugins/"
ok "BigaCore instalado: $(basename "$PLUGIN_JAR")"

# -------------------------------------------------------------
# 9. Operador (opcional)
# -------------------------------------------------------------
if [ -n "${MC_OP:-}" ]; then
    if [ -s "$SERVER_DIR/ops.json" ] && grep -q '"name"' "$SERVER_DIR/ops.json" 2>/dev/null; then
        ok "ops.json já tem entradas — preservado."
    else
        log "Buscando o UUID de '$MC_OP' na Mojang..."
        PERFIL=$(curl -fsS --max-time 15 \
                 "https://api.mojang.com/users/profiles/minecraft/$MC_OP" 2>/dev/null || true)
        UUID_RAW=$(echo "$PERFIL" | grep -oP '(?<="id"\s:\s")[0-9a-f]{32}|(?<="id":")[0-9a-f]{32}' | head -1)
        if [ -n "$UUID_RAW" ]; then
            UUID="${UUID_RAW:0:8}-${UUID_RAW:8:4}-${UUID_RAW:12:4}-${UUID_RAW:16:4}-${UUID_RAW:20:12}"
            cat > "$SERVER_DIR/ops.json" <<EOF
[
  {
    "uuid": "$UUID",
    "name": "$MC_OP",
    "level": 4,
    "bypassesPlayerLimit": false
  }
]
EOF
            ok "$MC_OP definido como operador (nível 4)."
        else
            warn "Não consegui resolver o UUID de '$MC_OP'."
            warn "Depois de subir, digite no console:  op $MC_OP"
        fi
    fi
fi

# -------------------------------------------------------------
# 10. Pronto
# -------------------------------------------------------------
echo
printf '\033[1;32m═══════════════════════════════════════════════════\033[0m\n'
printf '\033[1;32m  Setup concluído.\033[0m\n'
printf '\033[1;32m═══════════════════════════════════════════════════\033[0m\n'
echo
echo "  Servidor : $SERVER_DIR"
echo "  Jar      : $JAR"
echo "  Plugin   : $(basename "$PLUGIN_JAR")"
echo "  RAM      : $RAM (persistida em scripts/server.env)"
echo
echo "  Antes do primeiro boot neste PC, valide:"
echo "      cd $SERVER_DIR && bash scripts/doctor.sh"
echo
echo "  Restaurar mundo de um snapshot/backup:"
echo "      bash $SERVER_DIR/scripts/restore-world.sh /caminho/arquivo.tar.gz"
echo
echo "  Para subir:"
echo "      cd $SERVER_DIR && bash scripts/start.sh"
echo
echo "  Backup privado completo:"
echo "      cd $SERVER_DIR && bash scripts/backup.sh"
echo
echo "  Snapshot compartilhável do mundo:"
echo "      cd $SERVER_DIR && bash scripts/export-world.sh"
echo
echo "  Para desligar: digite 'stop' no console. Nunca Ctrl+C."
echo
if [ -z "${MC_OP:-}" ]; then
echo "  Dica: para virar admin, digite no console:  op SeuNick"
echo "        (ou rode este script com MC_OP=SeuNick)"
echo
fi
