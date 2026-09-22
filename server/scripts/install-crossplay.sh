#!/usr/bin/env bash
# Instala ou confere apenas o crossplay. Não compila BigaCore nem inicia o mundo.
# Uso: bash server/scripts/install-crossplay.sh [--check]
# CROSSPLAY_REPLACE_JARS=1 autoriza substituir SOMENTE os dois JARs fixados.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi
SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
if [ -f "$SERVER_DIR/scripts/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SERVER_DIR/scripts/server.env"
fi
MODE=install
if [ "${1:-}" = "--check" ] && [ "$#" -eq 1 ]; then
    MODE=check
elif [ "$#" -ne 0 ]; then
    echo 'Uso: install-crossplay.sh [--check]' >&2
    exit 2
fi
command -v python3 >/dev/null || { echo 'Instale python3 antes de continuar.' >&2; exit 1; }
python3 -c 'import yaml' 2>/dev/null || {
    echo 'Dependência ausente: PyYAML. Ubuntu/Debian: sudo apt-get install python3-yaml' >&2
    exit 1
}
CONFIG_ROOT="$SCRIPT_DIR/../config"
if [ ! -f "$CONFIG_ROOT/crossplay.lock.json" ]; then
    CONFIG_ROOT="$SCRIPT_DIR/crossplay-config"
fi
ARGS=("$MODE" --server-dir "$SERVER_DIR" --config-root "$CONFIG_ROOT"
      --minecraft "${MC_VERSION:-${DEFAULT_MC_VERSION:-26.2}}")
case "${CROSSPLAY_REPLACE_JARS:-0}" in
    0) ;;
    1) ARGS+=(--replace-jars) ;;
    *) echo 'CROSSPLAY_REPLACE_JARS deve ser 0 ou 1.' >&2; exit 2 ;;
esac
exec python3 "$SCRIPT_DIR/crossplay.py" "${ARGS[@]}"
