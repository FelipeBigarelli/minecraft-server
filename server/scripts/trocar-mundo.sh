#!/usr/bin/env bash
# =============================================================
#  trocar-mundo.sh — Troca qual pasta de mundo o servidor carrega.
#
#  Serve para visitar um mundo antigo e copiar construções dele
#  (//copy + //schem save) para depois colar no mundo atual.
#
#  Uso:
#      bash scripts/trocar-mundo.sh              # lista e mostra o atual
#      bash scripts/trocar-mundo.sh world        # carrega 'world'
#      bash scripts/trocar-mundo.sh world-antes-ilhas-2026-11-04_143611
#
#  NÃO apaga nem move mundo nenhum: só reescreve 'level-name'
#  no server.properties. Troca é sempre reversível.
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$SCRIPT_DIR/server.env" ] && . "$SCRIPT_DIR/server.env"
SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
PROPS="$SERVER_DIR/server.properties"

fail() { echo "[trocar-mundo] ERRO: $*" >&2; exit 1; }

[ -f "$PROPS" ] || fail "não achei $PROPS"

# -------------------------------------------------------------
#  Liveness pela PORTA e pelo lock do start.sh, nunca por 'ps'.
#  A linha de comando de um processo Java pode conter credenciais
#  (o launcher do cliente passa as dele por argumento), e qualquer
#  leitura por 'ps' as despeja na saída do terminal e nos logs.
# -------------------------------------------------------------
servidor_rodando() {
    if ss -ltn 2>/dev/null | grep -qE '(^|[^0-9:]):25565\b'; then
        return 0
    fi
    # Se o start.sh está com o lock, flock -n falha.
    if [ -f "$SERVER_DIR/.servidor.lock" ]; then
        if ! flock -n "$SERVER_DIR/.servidor.lock" true 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

ATUAL=$(sed -n 's/^level-name=//p' "$PROPS" | head -1)

listar() {
    echo "[trocar-mundo] Mundo carregado agora: $ATUAL"
    echo "[trocar-mundo] Pastas de mundo disponíveis em $SERVER_DIR:"
    local achou=0
    for d in "$SERVER_DIR"/*/; do
        d="${d%/}"
        [ -f "$d/level.dat" ] || continue
        achou=1
        local nome tam marca dp
        nome=$(basename "$d")
        tam=$(du -sh "$d" 2>/dev/null | cut -f1)
        marca="   "
        [ "$nome" = "$ATUAL" ] && marca=" * "
        dp=""
        if [ -d "$d/datapacks" ] && [ -n "$(ls -A "$d/datapacks" 2>/dev/null)" ]; then
            dp="  [datapacks: $(ls -A "$d/datapacks" | tr '\n' ' ')]"
        fi
        printf '%s%-45s %6s%s\n' "$marca" "$nome" "$tam" "$dp"
    done
    [ "$achou" = 1 ] || echo "    (nenhuma pasta com level.dat)"
}

DESTINO="${1:-}"

if [ -z "$DESTINO" ]; then
    listar
    echo
    echo "[trocar-mundo] Para trocar:  bash scripts/trocar-mundo.sh <nome-da-pasta>"
    exit 0
fi

DESTINO="${DESTINO%/}"
DESTINO="$(basename "$DESTINO")"

servidor_rodando && fail "o servidor está no ar. Dê 'stop' no console antes de trocar de mundo."

[ -d "$SERVER_DIR/$DESTINO" ] || fail "pasta não existe: $SERVER_DIR/$DESTINO"
[ -f "$SERVER_DIR/$DESTINO/level.dat" ] || fail "'$DESTINO' não tem level.dat — não é um mundo."

if [ "$DESTINO" = "$ATUAL" ]; then
    echo "[trocar-mundo] '$DESTINO' já é o mundo carregado. Nada a fazer."
    exit 0
fi

STAMP=$(date +%Y-%m-%d_%H%M%S)
cp -p "$PROPS" "$PROPS.bak-trocar-$STAMP"

# Reescreve a linha inteira. Usar um arquivo temporário e mover por cima
# mantém o server.properties sempre íntegro, mesmo se der erro no meio.
TMP=$(mktemp)
awk -v novo="$DESTINO" '
    /^level-name=/ { print "level-name=" novo; feito = 1; next }
    { print }
    END { if (!feito) print "level-name=" novo }
' "$PROPS" > "$TMP"
mv "$TMP" "$PROPS"
chmod --reference="$PROPS.bak-trocar-$STAMP" "$PROPS" 2>/dev/null || true

echo "[trocar-mundo] level-name: $ATUAL  ->  $DESTINO"
echo "[trocar-mundo] backup do properties: $PROPS.bak-trocar-$STAMP"
echo
echo "[trocar-mundo] Lembretes:"
echo "  - Datapack é por mundo. '$DESTINO' só usa o que estiver em $DESTINO/datapacks/."
echo "  - Homes, regiões e plots são guardados por NOME de mundo. Visitando"
echo "    um mundo antigo você entra como se fosse um mundo novo para os plugins;"
echo "    use /tp e o WorldEdit para achar e copiar as construções."
echo "  - Suba o servidor você mesmo:  bash scripts/start.sh"
