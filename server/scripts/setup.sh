#!/usr/bin/env bash
# =============================================================
#  setup.sh — Prepara um servidor Spigot 26.2 do zero (Linux/VPS)
#  Roda uma única vez. Depois use start.sh no dia a dia.
# =============================================================
set -euo pipefail

MC_VERSION="${MC_VERSION:-26.2}"
SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"
BUILD_DIR="$SERVER_DIR/buildtools"

log()  { printf '\033[1;36m[setup]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[aviso]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[erro]\033[0m %s\n' "$*" >&2; exit 1; }

# -------------------------------------------------------------
# 1. Dependências do sistema
# -------------------------------------------------------------
log "Instalando dependências do sistema..."
if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y git curl wget screen tar
elif command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y git curl wget screen tar
else
    warn "Gerenciador de pacotes não reconhecido. Instale manualmente: git curl wget screen tar"
fi

# -------------------------------------------------------------
# 2. Java 25
#    O Minecraft 26.2 é compilado com JDK 25. Não use versões
#    anteriores — o servidor simplesmente não sobe.
# -------------------------------------------------------------
need_java() {
    command -v java >/dev/null 2>&1 || return 0
    local v
    v=$(java -version 2>&1 | head -1 | grep -oP '(?<=version ")[0-9]+' || echo 0)
    [ "$v" -lt 25 ]
}

if need_java; then
    log "Instalando OpenJDK 25..."
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get install -y openjdk-25-jdk || {
            warn "openjdk-25-jdk não está nos repositórios desta distro."
            warn "Instale manualmente pelo Adoptium: https://adoptium.net/temurin/releases/?version=25"
            die  "Java 25 é obrigatório para o Minecraft 26.2."
        }
    else
        die "Instale o JDK 25 manualmente e rode este script de novo."
    fi
else
    log "Java 25+ já presente: $(java -version 2>&1 | head -1)"
fi

# -------------------------------------------------------------
# 3. Compilar o Spigot com o BuildTools
#    A Spigot NÃO distribui .jar pronto por razões de licença —
#    você precisa compilar localmente. Leva de 5 a 15 min.
# -------------------------------------------------------------
mkdir -p "$SERVER_DIR" "$BUILD_DIR" "$SERVER_DIR/plugins"

if [ -f "$SERVER_DIR/spigot-$MC_VERSION.jar" ]; then
    log "spigot-$MC_VERSION.jar já existe — pulando compilação."
else
    log "Baixando BuildTools..."
    cd "$BUILD_DIR"
    wget -q -O BuildTools.jar \
        "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"

    log "Compilando Spigot $MC_VERSION (isso demora, vai tomar um café ☕)..."
    java -Xmx2G -jar BuildTools.jar --rev "$MC_VERSION"

    cp "spigot-$MC_VERSION.jar" "$SERVER_DIR/spigot-$MC_VERSION.jar"
    log "Jar compilado e copiado."
fi

# -------------------------------------------------------------
# 4. EULA
# -------------------------------------------------------------
cd "$SERVER_DIR"
if ! grep -q "eula=true" eula.txt 2>/dev/null; then
    log "Aceitando a EULA da Mojang (https://aka.ms/MinecraftEULA)..."
    echo "eula=true" > eula.txt
fi

# -------------------------------------------------------------
# 5. Pronto
# -------------------------------------------------------------
log "Setup concluído."
echo
echo "  Diretório do servidor : $SERVER_DIR"
echo "  Jar                   : spigot-$MC_VERSION.jar"
echo
echo "  Próximos passos:"
echo "    1. cp config/server.properties $SERVER_DIR/"
echo "    2. bash scripts/start.sh"
echo
