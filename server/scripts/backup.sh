#!/usr/bin/env bash
# =============================================================
#  backup.sh — Backup PRIVADO compactado dos mundos + configs.
#
#  ⚠️  ESTE ARQUIVO NÃO É PARA GITHUB RELEASE PÚBLICA.
#  Ele inclui plugins, configs, ops/whitelist e pode passar a
#  conter senhas, bancos ou tokens do runtime no futuro.
#
#  Para gerar um snapshot compartilhável só do mundo, use:
#      bash scripts/export-world.sh
#
#  Agende no cron (todo dia às 4h):
#    crontab -e
#    0 4 * * * /bin/bash $HOME/minecraft/scripts/backup.sh >> $HOME/minecraft/backup.log 2>&1
# =============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/server.env" ]; then
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/server.env"
fi

SERVER_DIR="${SERVER_DIR:-${DEFAULT_SERVER_DIR:-$HOME/minecraft}}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/minecraft-backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"

STAMP=$(date +%Y-%m-%d_%H%M%S)

mkdir -p "$BACKUP_DIR"
cd "$SERVER_DIR"

# -------------------------------------------------------------
#  Backup com o servidor no ar é backup "quente".
#
#  O servidor pode estar gravando um chunk no exato momento em que
#  o tar o lê, e o resultado é um .mca truncado — que só aparece
#  na hora de restaurar, o pior momento possível. Não bloqueamos
#  (o cron roda com o servidor de pé, e um backup quente é melhor
#  que nenhum), mas marcamos o nome do arquivo para que a diferença
#  seja óbvia quando você for escolher qual restaurar.
# -------------------------------------------------------------
#  Detecção via 'ps' filtrando pelo EXECUTÁVEL ser java, e não via
#  'pgrep -f' num padrão solto: o pgrep -f casa contra a linha de
#  comando inteira de qualquer processo, então um shell que apenas
#  MENCIONE "paper" vira falso positivo.
servidor_rodando() {
    ps -eo comm= -o args= 2>/dev/null \
        | awk '$1 == "java" && /(paper|spigot)-[^ ]*\.jar/ { encontrado = 1 }
               END { exit !encontrado }'
}

if servidor_rodando; then
    SUFIXO="-quente"
    echo "[backup] ⚠️  O servidor está RODANDO. Este backup vai sair marcado como '-quente'."
    echo "[backup]    Para um backup consistente, no console do servidor:"
    echo "[backup]        save-off"
    echo "[backup]        save-all flush"
    echo "[backup]    ...rode este script, e depois:  save-on"
else
    SUFIXO=""
fi

ARCHIVE="$BACKUP_DIR/mc-backup-$STAMP$SUFIXO.tar.gz"

# -------------------------------------------------------------
#  O que entra no backup.
# -------------------------------------------------------------
CANDIDATOS=(
    world world_nether world_the_end
    plugins
    config
    server.properties bukkit.yml spigot.yml commands.yml
    ops.json whitelist.json banned-players.json banned-ips.json
    usercache.json
    scripts/server.env
)

ALVOS=()
for item in "${CANDIDATOS[@]}"; do
    [ -e "$item" ] && ALVOS+=("$item")
done

if [ ${#ALVOS[@]} -eq 0 ]; then
    echo "[backup] ERRO: nada para salvar em $SERVER_DIR. Diretório errado?" >&2
    exit 1
fi

echo "[backup] Criando $ARCHIVE ..."
echo "[backup] Incluindo: ${ALVOS[*]}"
echo "[backup] 🔒 PRIVADO: não publique este arquivo em GitHub Release pública."

tar czf "$ARCHIVE" \
    --exclude='./cache' \
    --exclude='./logs' \
    --exclude='./buildtools' \
    --exclude='./bundler' \
    --exclude='*.jar' \
    "${ALVOS[@]}"

echo "[backup] Verificando integridade..."
gzip -t "$ARCHIVE"

ENTRADAS=$(tar tzf "$ARCHIVE" | wc -l)
SIZE=$(du -h "$ARCHIVE" | cut -f1)
echo "[backup] OK — $SIZE, $ENTRADAS entradas, íntegro."

echo "[backup] Removendo backups com mais de $KEEP_DAYS dias..."
find "$BACKUP_DIR" -name 'mc-backup-*.tar.gz' -type f -mtime "+$KEEP_DAYS" -delete

echo "[backup] Backups atuais:"
ls -1sh "$BACKUP_DIR" | tail -n +2
