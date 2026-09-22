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
Edition”. Entre na conta Microsoft e confira as permissões de multiplayer. O
caminho pelos servidores online requer Nintendo Switch Online, conforme a
[página oficial da Nintendo](https://www.nintendo.com/us/store/products/minecraft-switch/).

No PC, obtenha o IPv4 da rede doméstica:

```bash
hostname -I
```

Não use `127.0.0.1` ou `localhost` no Switch: esses endereços apontam para o
próprio console. Ignore IPs de Docker/VPN; use o IP da interface Wi-Fi/Ethernet
que compartilha a rede do Switch. Cabo no PC e Wi-Fi no console funcionam se
estiverem na mesma LAN e sem isolamento de clientes/rede de convidados.

O Switch não oferece a entrada livre de IP como o Minecraft do PC/celular.
O método documentado é **BedrockConnect**, um serviço de terceiros:

1. No Switch: Configurações do console → Internet → Configurações de Internet →
   sua rede → Alterar configurações → DNS → Manual.
2. Use como DNS primário um endereço atual da lista indicada no
   [guia oficial do Geyser para consoles](https://geysermc.org/wiki/geyser/using-geyser-with-consoles/).
   O guia indica Google/Cloudflare como alternativas para o DNS secundário.
3. Abra Minecraft → Jogar → Servidores. O redirecionamento do BedrockConnect
   oferece a tela para conectar a outro servidor. Informe **o IPv4 local do PC**
   e porta **19132**, ou a porta configurada em `bedrock.port`.

Não foi fixado um DNS público neste projeto: endereços podem mudar ou ser
bloqueados. Um DNS de terceiros interfere na resolução de nomes do console;
use uma instância confiável e volte para **DNS automático** se houver problemas.
BedrockConnect não é uma solução oficial da Nintendo/Mojang nem do GeyserMC.
Não há garantia de que toda combinação de rede/firmware funcione sem ajustes.

Para a mesma LAN, não abra portas no roteador. Se o firewall do PC bloquear,
libere somente **UDP na porta Bedrock para a sua sub-rede local**. Confirme a
sub-rede real antes de criar uma regra; não desative todo o firewall. Amigos
fora de casa exigem uma avaliação separada de NAT/CGNAT e acesso público.

Você continua entrando no PC Java com `localhost:25565` no próprio PC do servidor.

## Validação manual que falta no equipamento

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
