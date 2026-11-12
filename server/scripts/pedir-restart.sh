#!/bin/sh
# =============================================================
#  pedir-restart.sh — o que o /restart do console dispara
#
#  POR QUE ISTO EXISTE
#  -------------------
#  /restart e /stop terminam o servidor do mesmo jeito: código 0.
#  Sem nada para diferenciar, um supervisor que sobe o servidor de
#  novo faria o /stop virar um servidor que se recusa a desligar.
#
#  Então o restart-script do spigot.yml aponta para cá, e este
#  arquivo não reinicia nada: ele só deixa um recado. Quem lê o
#  recado é o laço do scripts/start.sh, que é o dono do terminal e
#  do lock do runtime.
#
#  POSIX sh de propósito: o Paper chama o restart-script com `sh`.
#  Nada de array, nada de BASH_SOURCE aqui dentro.
# =============================================================
set -eu

DIR_SCRIPT=$(dirname "$0")
SERVER_DIR=$(cd "$DIR_SCRIPT/.." && pwd)

: > "$SERVER_DIR/.reiniciar"
echo "[restart] Pedido registrado. O start.sh sobe o servidor de novo."
