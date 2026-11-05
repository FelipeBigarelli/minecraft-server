# Jogar no servidor pelo Nintendo Switch (e qualquer Bedrock de console)

O servidor é Java. O Switch roda Bedrock. A ponte é o **Geyser**, que já está
instalado em `plugins/Geyser-Spigot/` e escuta em `19132/UDP`.

O problema não é o Geyser — é a interface do console. No Switch o Minecraft
**não tem campo pra digitar IP** e, na conta do Felipe, também não aparece a
seção "Jogos em LAN". Sobra a aba **Servidores**, que só lista os "servidores em
destaque" (Hive, CubeCraft, Lifeboat, InPvP, Galaxite).

A saída é sequestrar o DNS de um desses endereços na rede de casa: o Switch
pergunta "onde fica o Hive?", o nosso DNS responde "no PC do Felipe", e clicar
em **The Hive** cai no nosso servidor.

## Usar

Com o servidor ligado, num terminal:

```sh
~/minecraft/scripts/switch-dns.sh
```

Ele detecta a placa de rede, o IP do PC e o roteador, gera
`switch-dns.conf` ao lado dele e sobe o `dnsmasq` em primeiro plano. Pede a
senha do sudo porque a porta 53 é privilegiada. **Ctrl+C** encerra e não sobra
nada ligado nem instalado.

Pra só conferir a configuração, sem subir nada e sem sudo:

```sh
~/minecraft/scripts/switch-dns.sh --testar
```

### No Switch, uma vez só

Configurações do console → Internet → Configurações da Internet → sua rede →
Alterar configurações → **Configuração de DNS → Manual**

- DNS primário: o IP do PC (o script mostra; hoje `192.168.1.49`)
- DNS secundário: o roteador (`192.168.1.1`)

Salvar e conectar. Depois, no Minecraft: aba **Servidores** → **The Hive**.

Pra voltar ao normal: mesma tela → Configuração de DNS → **Automático**.

## Pré-requisitos

- Switch e PC na **mesma rede** (o DNS responde um IP `192.168.1.x`, que só
  vale de dentro de casa).
- `dnsmasq-base` instalado (já está; o pacote `dnsmasq` com serviço **não**
  está, de propósito — assim nada briga com o `systemd-resolved`, que só ocupa
  `127.0.0.53`).
- Servidor ligado, com o Geyser escutando em `19132/UDP`. O script avisa se não
  estiver.

## O nome do jogador

`java.auth-type` no `config.yml` do Geyser está em **`offline`**, combinando com
`online-mode=false` do servidor. Nesse modo o Geyser usa a **gamertag do Xbox**
como nome Java.

Isso importa: a conta de Java dele é `zBigaBiga`, cujo UUID offline é
`aec5be46-16f3-3c00-af72-a03d30e673ca`. Se a gamertag do Switch for diferente
(mesmo só na caixa das letras), o Geyser entra como **outro jogador** — sem
dinheiro, sem homes, sem terreno, sem permissões.

Dois jeitos de resolver, quando souber a gamertag:

1. Migrar os dados (EssentialsX, LuckPerms, WorldGuard) para o UUID offline da
   gamertag nova.
2. Instalar o **Floodgate**, trocar `auth-type` para `floodgate` e usar
   `/linkaccount` pra amarrar a conta Bedrock à Java. Mais limpo, um plugin a
   mais.

## Se a aba Servidores não resolver

`downloads/bedrockconnect/BedrockConnect.jar` (1.72.1, SHA-256 conferido contra
o digest publicado pelo GitHub) sobe um **menu** no lugar do servidor em
destaque, onde dá pra cadastrar IPs à mão. Ele não traz DNS próprio — usa o
mesmo `switch-dns.sh` — mas precisa da porta `19132`, então o Geyser teria que
mudar pra `19133` e entrar como entrada da lista. Só vale a pena com mais de um
servidor pra escolher.

## Mudanças feitas

- `plugins/Geyser-Spigot/config.yml`: `java.auth-type: online` → `offline`
  (backup em `config.yml.bak-20261105`). **Exige reiniciar o servidor.**
- `server/scripts/switch-dns.sh`: novo.
