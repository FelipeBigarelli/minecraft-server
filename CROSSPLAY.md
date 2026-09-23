# PC Java + Nintendo Switch no mesmo servidor

Perfil do repositório conferido em **21/09/2026**. O servidor continua Paper
26.2 build 92 com BigaCore, VaultUnlocked, EternalEconomy e ChestShop. Não há
conversão de mundo nem mudança de banco, UUID ou inventário dos jogadores Java.

## O que foi integrado

| Componente | Versão fixada | Papel |
|---|---|---|
| Geyser-Spigot | 2.11.3, build 1245 | Traduz Bedrock para o Paper Java |
| Floodgate-Spigot | 2.2.5, build 141 | Autentica o jogador Bedrock sem exigir compra do Java |
| Java | 25565/TCP por padrão | Conexão atual do PC |
| Bedrock | 19132/UDP por padrão | Entrada adicional do Switch |

Versões, builds e SHA256 estão em `server/config/crossplay.lock.json`. Os hashes
foram conferidos contra os binários da API oficial do GeyserMC, não inventados.
O perfil usa RakNet, schema Geyser 8 e `java.auth-type: floodgate`. Não use
exemplos antigos com `remote.auth-type`. NetherNet não foi habilitado: exige
uma revisão própria de portas/sinalização quando este perfil for atualizado.

## Aplicar em um servidor já instalado

Primeiro digite **`stop` no console do Minecraft** e aguarde o processo sair.
Não execute os instaladores com o servidor em execução.

Faça o backup privado com o servidor desligado:

```bash
bash ~/minecraft/scripts/backup.sh
```

Depois, **na pasta local do clone `minecraft-server`**, execute:

```bash
git pull --ff-only
bash server/scripts/setup.sh
bash ~/minecraft/scripts/doctor.sh
```

O setup instala as dependências e ambos os plugins. Não use `FORCE_CONFIG=1`
para esta atualização: a intenção é preservar as configurações existentes.
Se o runtime não é `~/minecraft`, passe o mesmo `SERVER_DIR` utilizado na
instalação anterior e ajuste os comandos de backup/diagnóstico.

Somente após o diagnóstico aprovado:

```bash
cd ~/minecraft
bash scripts/start.sh
```

No primeiro boot o Floodgate cria `plugins/floodgate/key.pem`. O Geyser, no
mesmo Paper, obtém a chave automaticamente. Nunca copie essa chave para o Git.

Para instalar **somente crossplay**, sem recompilar BigaCore/economia, há:

```bash
# Ubuntu/Debian, somente se as dependências ainda não estiverem instaladas:
sudo apt-get install -y python3 python3-yaml curl
# Na raiz do clone, com o servidor parado:
bash server/scripts/install-crossplay.sh
bash server/scripts/install-crossplay.sh --check
```

A opção focada instala seu próprio helper e templates no runtime, mas não
atualiza o `start.sh`/`doctor.sh` antigo. O setup completo é o caminho recomendado
para receber também o bloqueio de instalação concorrente e o diagnóstico geral.

`INSTALL_CROSSPLAY=0 bash server/scripts/setup.sh` omite a etapa em uma instalação
Java-only. Isso **não desinstala** plugins já existentes nem reverte configs.

## Segurança e preservação

`online-mode=true` permanece obrigatório para Java. A validação das contas
Bedrock também permanece habilitada. O instalador altera apenas
`enforce-secure-profile=false` no `server.properties` do runtime para permitir o
chat Bedrock. Isso remove a exigência de perfil/chat assinado Java; não desliga
a autenticação de conta. Jogadores Java com certos mods podem enviar mensagens
não reportáveis. Veja a [explicação do Geyser](https://geysermc.org/wiki/geyser/secure-chat/).

O instalador valida os dois downloads antes de substituir arquivos, recusa JARs
alternativos/duplicados, detecta processos e locks, porta UDP ocupada, conflitos
com Query, YAML duplicado e configurações incompatíveis. Não converte
configurações antigas silenciosamente. `FORCE_CONFIG` não força a sobrescrita
dos configs Geyser/Floodgate.

Arquivos alterados ganham cópia em `~/minecraft/.crossplay-backups/`, com acesso
restrito ao dono. Essa pasta pode conter segredos de `server.properties` e é
**privada**. Falhas normais durante a aplicação tentam restaurar os arquivos já
substituídos; isso não substitui um backup completo nem garante recuperação de
queda de energia/disco. Reinstalações idênticas não baixam nem criam novas cópias.

O instalador não muda firewall, DNS do Switch, roteador, whitelist ou permissões.
O listener padrão `0.0.0.0` aceita conexões nas interfaces do PC: em máquina com
IP público, revise o firewall. A whitelist atualmente desativada é sinalizada no
diagnóstico; não exponha o servidor à internet sem definir quem pode entrar.

## Entrar pelo Switch na mesma rede

Use **Minecraft (Bedrock)**, não a edição antiga chamada “Nintendo Switch
Edition”. Entre na conta Microsoft e confira as permissões de multiplayer.

No PC, obtenha o IPv4 da rede doméstica:

```bash
hostname -I
```

Não use `127.0.0.1` ou `localhost` no Switch: apontam para o próprio console.
Ignore IPs de Docker/VPN; use o IP da interface que compartilha a rede do
console. Cabo no PC e Wi-Fi no Switch funcionam na mesma LAN.

O Switch **não tem campo para digitar endereço de servidor**. A aba Servidores
lista apenas os parceiros da Mojang. Por isso todo método aqui depende de fazer
o console resolver o nome de um desses parceiros para o seu servidor.

### O caminho validado: DNS local no próprio PC

Confirmado em 22/09/2026, com Switch em `192.168.18.236` e servidor em
`192.168.18.3`. Não depende de serviço de terceiros e conecta direto, sem menu
intermediário.

**O detalhe que faz ou quebra:** o The Hive usa **dois domínios**. O
`geo.hivebedrock.network` responde ao ping que desenha o MOTD na lista; o
`geo.hivebedrock.cloud` é o que o jogo resolve **na hora de conectar**.
Interceptar só o primeiro produz um sintoma que engana: a entrada do Hive passa
a exibir o MOTD do seu servidor, mas entrar nela cai no Hive verdadeiro, porque
a conexão saiu pelo domínio que escapou. Intercepte os dois.

No PC, com `dnsmasq` instalado e o servidor parado ou não:

```bash
sudo dnsmasq --port=53 --listen-address=SEU_IP --bind-interfaces \
  --address=/hivebedrock.network/SEU_IP \
  --address=/hivebedrock.cloud/SEU_IP \
  --server=1.1.1.1 --server=8.8.8.8 --no-resolv --no-hosts \
  --pid-file=/run/minecraft-switch-dns.pid
```

Apenas esses dois domínios são desviados; todo o resto é encaminhado e a
internet do console segue normal. Para deixar permanente há um modelo de unidade
systemd em [`server/config/minecraft-switch-dns.service`](server/config/minecraft-switch-dns.service);
troque `SEU_IP` antes de instalar.

No Switch: Configurações do console → Internet → Configurações de Internet →
sua rede → Alterar configurações → DNS → Manual.

| Campo | Valor | Por quê |
|---|---|---|
| Primário | IP do PC | responde pelos domínios do Hive |
| Secundário | `1.1.1.1` | mantém a internet do console com o PC desligado |

Deixar o secundário como DNS público é deliberado: com o PC desligado o primário
não responde, o console cai no secundário e o The Hive volta a ser o Hive real —
o comportamento correto quando não há servidor no ar. Apontar os dois campos
para o PC deixa o console sem resolver nome nenhum quando a máquina está
desligada.

Feche o Minecraft pelo HOME antes de abrir de novo. O console guarda a resolução
anterior, e sem reiniciar o jogo ele reusa o endereço do Hive real. Depois é
**Jogar → Servidores → The Hive**, que passa a levar ao seu mundo.

O IP do PC precisa ser estável. Em DHCP, faça reserva no roteador ou fixe o
endereço; se ele mudar, o console procura no lugar errado.

### BedrockConnect, e por que ficou como alternativa

É o método que a documentação do Geyser indica, via instâncias públicas de
terceiros. Aqui ele **não funcionou**: com o DNS de uma instância pública
configurado e o console reiniciado por completo, clicar nos servidores em
destaque continuou abrindo os servidores reais. O próprio projeto avisa que o IP
principal costuma ser bloqueado em consoles.

Vale saber também que cada instância intercepta uma lista própria de nomes.
Medindo contra `104.238.130.180`, os nomes `geo.hivebedrock.network`,
`play.inpvp.net` e `mco.mineplex.com` eram desviados, enquanto
`mco.cubecraft.net`, `mco.lbsg.net` e `play.galaxite.net` não — clicar no
CubeCraft levava ao CubeCraft. Se for tentar esse caminho, clique num servidor
que a instância realmente intercepta.

Fica registrado como alternativa, não como recomendação: o DNS local não depende
de terceiro, não pode ser bloqueado e conecta direto.

### Descoberta de LAN não resolveu

O Geyser responde ao ping de broadcast — um cliente na mesma interface o
encontra. Ainda assim o servidor não apareceu em “Jogos na LAN” no console: o
roteador não repassou o broadcast do Wi-Fi para o cabo. Se o seu repassar, esse
é o caminho mais simples e dispensa DNS; não conte com ele.

### Rede

Para a mesma LAN, não abra portas no roteador. Se o firewall do PC bloquear,
libere somente **UDP na porta Bedrock para a sua sub-rede local**. Confirme a
sub-rede real antes de criar a regra; não desative todo o firewall.

Amigos fora de casa exigem avaliação separada de NAT/CGNAT. Console não instala
VPN, então uma malha tipo Tailscale resolve apenas para quem joga em PC/celular;
para um console remoto sobra encaminhamento de porta, que só deve ser feito com
whitelist definida e firewall revisado antes.

Você continua entrando no PC Java com `localhost:25565` no próprio PC.

## Validação manual que falta no equipamento

**Login real já confirmado em 22/09/2026.** Um Switch entrou pelo caminho de DNS
local acima, e o servidor registrou:

```
[Geyser-Spigot] Player connected with username I LoB I Biga (2193)
[Geyser-Spigot] ... has connected to the Java server
.I_LoB_I_Biga[/192.168.18.236:0] logged in ... ([minecraft:overworld]176.5, 64.0, 68.5)
[BigaCore] [saldo-inicial] .I_LoB_I_Biga recebeu 250 (saldo era 0, alvo 250).
```

Mundo existente carregado, saldo inicial creditado, nenhum erro no log. O que
segue pendente é a jogabilidade em si.

O CI comprova boot e resposta de protocolo, **não um login real no Switch**.
Depois de conectar os dois jogadores, confira: presença no mesmo mundo,
movimentação, inventário, chat, reentrada, `/biga eco saldo` e uma compra/venda
pequena no ChestShop. Confirme saldo, itens e teto de recompra após a operação.

Bedrock tem diferenças de interface, combate e exibição. A presença do plugin
no boot não garante equivalência visual de displays/lojas no console.
Por padrão, nomes Bedrock recebem `.` na frente e espaços viram `_`. São
identidades diferentes das contas Java sem vinculação explícita. Não remova o
prefixo nem tente unir saldos/inventários automaticamente.

## Diagnóstico e manutenção

```bash
# Checagem sem iniciar e sem alterar configs:
bash ~/minecraft/scripts/install-crossplay.sh --check
# No console do servidor em execução:
# geyser version
# version floodgate
# No terminal, com o servidor ligado:
ss -lunp | grep ':19132'
```

`--check` valida arquivos, hashes e parâmetros, não a passagem pelo firewall ou
a conta Microsoft. Chave ausente antes do primeiro boot é um aviso esperado.

O Switch pode atualizar o Minecraft automaticamente. Se aparecer versão
incompatível, confira `geyser version` e a versão exibida no console. Atualize
os pins do lock **somente depois de verificar a API oficial, schemas e CI**.
O instalador nunca troca versões por `latest` silenciosamente. Para substituir
um JAR existente por outro pin já revisado, use, com o servidor desligado:

```bash
CROSSPLAY_REPLACE_JARS=1 bash server/scripts/install-crossplay.sh
```

Para voltar a Java-only, faça backup e pare o servidor. Mova os dois JARs e as
pastas de configuração Geyser/Floodgate para um diretório privado fora de
`plugins/`; mova também `scripts/crossplay-config` para que o doctor não detecte
uma instalação parcial. Guarde a chave. Reponha o valor anterior de
`enforce-secure-profile` a partir do backup, preservando `online-mode=true`.
Não restaure todo o mundo para simplesmente retirar os plugins.

## Armadilhas de ambiente

Encontradas ao aplicar num Ubuntu 20.04 real; nenhuma aparece no CI, que roda em
`ubuntu-latest`.

**Python 3.10+ não existe pelo apt no Ubuntu 20.04.** O `setup.sh` exige 3.10+ e
aborta, porque a distribuição só oferece 3.8. Os caminhos de uso diário —
`doctor.sh` e `install-crossplay.sh --check` — não têm esse portão e funcionam
em 3.8; a suíte `test_crossplay.py` passa inteira nas duas versões. Para rodar o
`setup.sh` nessa distro, use um interpretador 3.10+ já disponível na máquina,
por exemplo `PYENV_VERSION=3.11.9 bash server/scripts/setup.sh`, com PyYAML
instalado nele.

**`backup.sh` poda backups antigos.** O padrão é `KEEP_DAYS=7`, e a poda roda ao
final de cada execução. Num servidor parado por semanas, o primeiro backup novo
apaga todos os anteriores. Para preservar o histórico, rode com
`KEEP_DAYS=36500 bash scripts/backup.sh`.

**A chave do Floodgate nasce legível por outros.** O plugin gera
`plugins/floodgate/key.pem` no primeiro boot com o umask padrão do sistema, que
no Ubuntu resulta em `664`. Considere `chmod 600` nela.

## Testes e fontes

`server/tests/test_crossplay.py`: regressões offline com JARs sintéticos e
hashes exclusivos do teste, falhas simuladas, preservação e idempotência.
`Crossplay smoke`: setup completo em runner descartável, Paper + economia +
Geyser/Floodgate, ping RakNet UDP real, bloqueio concorrente, `stop` limpo e
reinstalação preservando mundo/chave/configs. O artefato contém somente log.

Referências: [Geyser setup](https://geysermc.org/wiki/geyser/setup/),
[Floodgate setup](https://geysermc.org/wiki/floodgate/setup/),
[consoles](https://geysermc.org/wiki/geyser/using-geyser-with-consoles/) e
[chat assinado](https://geysermc.org/wiki/geyser/secure-chat/).
