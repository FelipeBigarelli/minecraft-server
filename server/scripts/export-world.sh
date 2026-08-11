#!/usr/bin/env bash
# =============================================================
#  export-world.sh — Snapshot PUBLICÁVEL somente dos mundos.
#
#  Diferente de backup.sh, este arquivo NÃO inclui plugins,
#  configs, ops.json, whitelist, bancos ou qualquer credencial.
#  É o script certo para gerar assets de GitHub Releases.
#
#  Exige o servidor desligado para evitar publicar um mundo
#  capturado no meio de uma escrita de chunk.
# =============================================================
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"
EXPORT_DIR="${EXPORT_DIR:-$HOME/minecraft-exports}"
STAMP=$(date +%Y-%m-%d_%H%M%S)

mkdir -p "$EXPORT_DIR"
cd "$SERVER_DIR"

servidor_rodando() {
    ps -eo comm= -o args= 2>/dev/null \
        | awk '$1 == "java" && /(paper|spigot)-[^ ]*\.jar/ { encontrado = 1 }
               END { exit !encontrado }'
}

if servidor_rodando; then
    echo "[export] ERRO: o servidor está rodando." >&2
    echo "[export] Pare com 'stop' no console e rode o export novamente." >&2
    exit 1
fi

CANDIDATOS=(world world_nether world_the_end)
MUNDOS=()
for item in "${CANDIDATOS[@]}"; do
    [ -e "$item" ] && MUNDOS+=("$item")
done

if [ ${#MUNDOS[@]} -eq 0 ]; then
    echo "[export] ERRO: nenhum mundo encontrado em $SERVER_DIR." >&2
    exit 1
fi

ARCHIVE="$EXPORT_DIR/mc-world-$STAMP.tar.gz"

echo "[export] Criando snapshot público: $ARCHIVE"
echo "[export] Incluindo SOMENTE: ${MUNDOS[*]}"

tar czf "$ARCHIVE" "${MUNDOS[@]}"

echo "[export] Verificando integridade..."
gzip -t "$ARCHIVE"

SIZE=$(du -h "$ARCHIVE" | cut -f1)
SHA=$(sha256sum "$ARCHIVE" | cut -d' ' -f1)

echo "[export] OK — $SIZE"
echo "[export] SHA256: $SHA"
echo "[export] Seguro para GitHub Release: não contém plugins, configs, ops, whitelist ou credenciais."
echo "$ARCHIVE"
