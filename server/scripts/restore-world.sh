#!/usr/bin/env bash
# =============================================================
#  restore-world.sh — Restaura SOMENTE os mundos de um .tar.gz.
#
#  Aceita tanto os snapshots novos (mc-world-*.tar.gz) quanto os
#  backups legados (mc-backup-*.tar.gz), mas extrai somente:
#      world/
#      world_nether/
#      world_the_end/
#
#  Plugins, configs, ops, whitelist e credenciais do arquivo de
#  origem NUNCA são restaurados por este script.
#
#  Uso:
#      bash scripts/restore-world.sh ~/Downloads/mc-world-....tar.gz
#
#  Se já houver um mundo no destino, o script para. Para substituir
#  conscientemente, use FORCE=1; o mundo anterior é movido para uma
#  pasta de segurança antes da restauração.
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi

SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
FORCE="${FORCE:-0}"
ARCHIVE="${1:-}"
STAMP=$(date +%Y-%m-%d_%H%M%S)

fail() {
    echo "[restore] ERRO: $*" >&2
    exit 1
}

servidor_rodando() {
    ps -eo comm= -o args= 2>/dev/null \
        | awk '$1 == "java" && /(paper|spigot)-[^ ]*\.jar/ { encontrado = 1 }
               END { exit !encontrado }'
}

[ -n "$ARCHIVE" ] || fail "informe o caminho do .tar.gz. Ex.: bash scripts/restore-world.sh ~/Downloads/mc-world-AAAA-MM-DD_HHMMSS.tar.gz"
[ -f "$ARCHIVE" ] || fail "arquivo não encontrado: $ARCHIVE"
[ -d "$SERVER_DIR" ] || fail "diretório do servidor não existe: $SERVER_DIR. Rode setup.sh primeiro."

if servidor_rodando; then
    fail "o servidor está rodando. Pare com 'stop' no console antes de restaurar."
fi

echo "[restore] Verificando gzip..."
gzip -t "$ARCHIVE" || fail "arquivo gzip inválido ou truncado."

TMP_DIR=$(mktemp -d)
LIST_FILE="$TMP_DIR/world-files.txt"
trap 'rm -rf "$TMP_DIR"' EXIT

# Primeiro validamos todos os nomes do tar. Mesmo quando a origem é nossa,
# nunca extraímos caminho absoluto nem componentes '..'.
while IFS= read -r entry; do
    clean="${entry#./}"

    case "$entry" in
        /*) fail "arquivo contém caminho absoluto inseguro: $entry" ;;
    esac

    case "/$clean/" in
        */../*) fail "arquivo contém caminho relativo inseguro: $entry" ;;
    esac

    case "$clean" in
        world|world/*|world_nether|world_nether/*|world_the_end|world_the_end/*)
            printf '%s\n' "$entry" >> "$LIST_FILE"
            ;;
    esac
done < <(tar tzf "$ARCHIVE")

[ -s "$LIST_FILE" ] || fail "nenhuma pasta de mundo reconhecida foi encontrada no arquivo."

echo "[restore] Extraindo somente arquivos de mundo em área temporária..."
# --no-recursion é importante quando a lista contém tanto diretórios quanto
# seus filhos. Sem ele, o tar extrai o diretório recursivamente e depois tenta
# localizar os mesmos filhos outra vez, podendo falhar com "Not found in archive".
tar xzf "$ARCHIVE" --no-recursion -C "$TMP_DIR" -T "$LIST_FILE"

MUNDOS=()
for mundo in world world_nether world_the_end; do
    if [ -d "$TMP_DIR/$mundo" ]; then
        MUNDOS+=("$mundo")
    fi
done

[ ${#MUNDOS[@]} -gt 0 ] || fail "o arquivo listava mundos, mas nenhuma pasta válida foi extraída."

EXISTENTES=()
for mundo in "${MUNDOS[@]}"; do
    [ -e "$SERVER_DIR/$mundo" ] && EXISTENTES+=("$mundo")
done

if [ ${#EXISTENTES[@]} -gt 0 ] && [ "$FORCE" != "1" ]; then
    echo "[restore] Já existe mundo no destino: ${EXISTENTES[*]}" >&2
    echo "[restore] Nenhum arquivo foi alterado." >&2
    echo "[restore] Se você REALMENTE quer substituir, rode: FORCE=1 bash scripts/restore-world.sh \"$ARCHIVE\"" >&2
    exit 1
fi

if [ ${#EXISTENTES[@]} -gt 0 ]; then
    SAFETY_DIR="$SERVER_DIR/restore-safety-$STAMP"
    mkdir -p "$SAFETY_DIR"
    echo "[restore] Salvando mundo atual em: $SAFETY_DIR"
    for mundo in "${EXISTENTES[@]}"; do
        mv "$SERVER_DIR/$mundo" "$SAFETY_DIR/"
    done
fi

for mundo in "${MUNDOS[@]}"; do
    mv "$TMP_DIR/$mundo" "$SERVER_DIR/$mundo"
done

echo "[restore] OK — mundos restaurados em $SERVER_DIR: ${MUNDOS[*]}"
echo "[restore] Nenhum plugin, config, ops, whitelist ou credencial do arquivo foi restaurado."
echo "[restore] Agora rode: bash $SERVER_DIR/scripts/doctor.sh"
