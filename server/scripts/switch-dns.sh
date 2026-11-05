#!/usr/bin/env bash
# Faz o Nintendo Switch entrar no servidor do Biga sem digitar IP.
#
# Como funciona: o Switch so deixa entrar nos "servidores em destaque"
# (Hive, CubeCraft, Lifeboat...). Este script liga um DNS na rede local que
# responde esses enderecos com o IP deste PC. Ai, clicando em "The Hive" no
# Switch, ele cai no nosso servidor pelo Geyser.
#
# Nada fica instalado nem ligado depois: e Ctrl+C e acabou.

set -euo pipefail

PORTA_BEDROCK=19132

IFACE=$(ip -4 route show default | awk '/^default/{print $5; exit}')
if [[ -z "${IFACE:-}" ]]; then
  echo "Nao achei a placa de rede com saida pra internet." >&2
  exit 1
fi
IP=$(ip -4 -o addr show dev "$IFACE" | awk '{print $4}' | cut -d/ -f1 | head -1)
ROTEADOR=$(ip -4 route show default | awk '/^default/{print $3; exit}')

echo "Placa de rede : $IFACE"
echo "IP deste PC   : $IP"
echo "Roteador      : $ROTEADOR"
echo

if ! ss -lun | grep -q ":$PORTA_BEDROCK\b"; then
  echo "AVISO: nada escutando na porta $PORTA_BEDROCK/UDP."
  echo "       O Geyser precisa estar rodando — ligue o servidor primeiro."
  echo
fi

CONF="$(dirname "$0")/switch-dns.conf"

{
  echo "port=53"
  echo "listen-address=$IP"
  echo "bind-interfaces"
  echo "no-resolv"
  echo "no-hosts"
  echo "user=nobody"
  echo "log-queries"
  echo "server=$ROTEADOR"
  echo "server=1.1.1.1"
  # Enderecos dos servidores em destaque do Minecraft Bedrock -> este PC.
  for d in hivebedrock.network geo.hivebedrock.network \
           mco.lbsg.net play.lbsg.net \
           mco.cubecraft.net \
           play.inpvp.net \
           play.galaxite.net; do
    echo "address=/$d/$IP"
  done
} > "$CONF"

if [[ "${1:-}" == "--testar" ]]; then
  echo "Config gerada em: $CONF"
  echo
  sed 's/^/  /' "$CONF"
  echo
  /usr/sbin/dnsmasq --test --conf-file="$CONF"
  exit 0
fi

cat <<AJUDA
No Switch, uma vez so:
  Configuracoes do console > Internet > Configuracoes da Internet
  > sua rede Wi-Fi > Alterar configuracoes > Configuracao de DNS > Manual
  > DNS primario:   $IP
  > DNS secundario: $ROTEADOR
  Salvar e conectar.

Depois, no Minecraft do Switch:
  aba Servidores > clique em "The Hive" > entra no nosso servidor.

Pra voltar o Switch ao normal: mesma tela, Configuracao de DNS > Automatico.

Rodando o DNS agora (Ctrl+C pra parar):
AJUDA

exec sudo /usr/sbin/dnsmasq --no-daemon --keep-in-foreground \
  --log-facility=- --pid-file= --conf-file="$CONF"
