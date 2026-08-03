#!/usr/bin/env bash
# =============================================================
#  backup.sh — Backup compactado dos mundos + configs
#
#  Agende no cron (todo dia às 4h):
#    crontab -e
#    0 4 * * * /bin/bash $HOME/minecraft/scripts/backup.sh >> $HOME/minecraft/backup.log 2>&1
# =============================================================
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/minecraft}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/minecraft-backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"

STAMP=$(date +%Y-%m-%d_%H%M%S)
ARCHIVE="$BACKUP_DIR/mc-backup-$STAMP.tar.gz"

mkdir -p "$BACKUP_DIR"
cd "$SERVER_DIR"

echo "[backup] Criando $ARCHIVE ..."

# Compacta apenas o que importa. Excluir cache e logs economiza
# muito espaço — nada aqui é necessário para restaurar.
tar czf "$ARCHIVE" \
    --exclude='./cache' \
    --exclude='./logs' \
    --exclude='./buildtools' \
    --exclude='*.jar' \
    world world_nether world_the_end \
    plugins server.properties bukkit.yml spigot.yml \
    ops.json whitelist.json banned-players.json banned-ips.json \
    2>/dev/null || true

SIZE=$(du -h "$ARCHIVE" | cut -f1)
echo "[backup] OK — $SIZE"

echo "[backup] Removendo backups com mais de $KEEP_DAYS dias..."
find "$BACKUP_DIR" -name 'mc-backup-*.tar.gz' -type f -mtime "+$KEEP_DAYS" -delete

echo "[backup] Backups atuais:"
ls -1sh "$BACKUP_DIR" | tail -n +2
