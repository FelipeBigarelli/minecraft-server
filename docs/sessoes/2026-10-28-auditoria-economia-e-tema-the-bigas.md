# 2026-10-28 — Auditoria da economia e tema visual The Bigas

Sessão longa, com dois eixos: identidade visual aplicada a todos os plugins e um
levantamento do estado **real** da economia, que diverge do que o `ECONOMIA.md`
descreve.

O segundo eixo é o motivo deste documento existir. Antes de desenhar qualquer
loja de compra e venda, é preciso saber que **a economia nunca rodou**.

---

## 🔴 Auditoria da economia — leia isto primeiro

### A economia está tecnicamente ligada e praticamente parada

A cadeia está correta e sem nenhum erro no log:

```text
EssentialsX Economy  →  Vault  →  ChestShop + BigaCore
```

```text
[ChestShop] Using EssentialsX Economy as the Economy provider now.
[Essentials] Essentials found a compatible payment resolution method: Vault Compatibility Layer (v2.20.2)!
[BigaCore] BigaCore habilitado — economia Vault conectada.
```

E mesmo assim:

| Evidência | Valor |
|---|---|
| Saldo de `zBigaBiga` | **0** |
| Saldo de `bigarellimatheus` | **0** |
| Transações no `ChestShop.log`, desde sempre | **2** |
| Data da última transação | **2026-08-12** |

O log inteiro do ChestShop tem duas linhas:

```text
2026/08/12 08:02:59 zBigaBiga bought 32 Grass Block for 48,00 from Loja do Servidor at [world] 183, 65, 21
2026/08/12 08:04:45 An Admin Shop was removed by zBigaBiga - 48Bife Assado - B 96 - at [world] 204, 65, 34
```

Uma compra e uma remoção, no mesmo dia, há dois meses e meio. Não existe série
histórica para calibrar preço: qualquer balanceamento agora é a partir do zero.

### A causa: o provedor de economia trocou e o saldo inicial ficou órfão

O `ECONOMIA.md` descreve a economia sobre **EternalEconomy**, com
`defaultBalance: 250`. Esse plugin **não está mais instalado** — foi movido para
`~/minecraft/plugins-desativados/EternalEconomy-v1.0.1.jar`.

Quem responde pela economia hoje é o EssentialsX, e ele tem:

```yaml
# ~/minecraft/plugins/Essentials/config.yml:855
starting-balance: 0
```

Somado a isso, o BigaCore já considera o saldo inicial **entregue** para os três
UUIDs conhecidos:

```yaml
# ~/minecraft/plugins/BigaCore/saldo-inicial.yml
recebido:
  261ba52f-3832-4f89-b0d9-104acf1ea58f: true   # zBigaBiga
  00000000-0000-0000-0009-01f0e234fe0c: true   # conta Bedrock/Floodgate
  ea045815-648c-47a0-b581-a4ec7b0f7b0d: true   # bigarellimatheus
```

Ou seja: o flag de "já recebeu" foi gravado na era do EternalEconomy, o dinheiro
nunca chegou na carteira do EssentialsX, e **o BigaCore não vai tentar de novo**.
Ninguém jamais teve saldo. É por isso que a loja nunca girou.

### Divergências entre `ECONOMIA.md` e o servidor ao vivo

| Item | `ECONOMIA.md` diz | Servidor ao vivo |
|---|---|---|
| Provedor de economia | EternalEconomy | **EssentialsX Economy** |
| Saldo inicial | 250 BC$ | **0** |
| Símbolo da moeda | `BC$` | **`$`** |
| Taxa ChestShop P2P | 4% | 4% ✅ |
| Criar loja | 50 | 50 ✅ |
| Reembolso ao remover | 10 | 10 ✅ |
| Transações parciais | desativadas | `false` ✅ |
| Imposto sai de circulação | sim | `SERVER_ECONOMY_ACCOUNT: ""` ✅ |

As regras do ChestShop conferem. O que não confere é **de onde vem o dinheiro** —
e é justamente a parte que define se a economia existe.

### Divergência de template: qual é o modelo de Admin Shop?

`server/config/plugins/ChestShop/config.yml` (neste repo) e
`~/minecraft/plugins/ChestShop/config.yml` (ao vivo) discordam sobre o desenho da
loja do servidor:

- **Template do repo**: Biga Market com Admin Shops de **estoque infinito**
  gerados pelo BigaCore a partir de `economy.yml`, placas que vendem **e**
  compram, e o `ChestShopCapGuard` cancelando venda acima do teto diário.
- **Config ao vivo**: *"Admin Shops continuam disponíveis, mas NÃO serão
  ilimitados por padrão. A criação dos poucos Admin Shops autorizados será
  manual e seguirá `server/economy/catalog.yml`."*

Esse `server/economy/catalog.yml` **não existe** no repo. São dois modelos
econômicos diferentes convivendo, e decidir entre eles é pré-requisito para o
balanceamento.

### Insumo que já existe

`~/minecraft/plugins/BigaCore/economy.yml` tem **175 itens precificados** e a
seção `admin-shop:`. É a base de preços para qualquer calibragem — não precisa
começar do papel em branco.

### Perguntas que o balanceamento precisa responder

1. **De onde entra dinheiro?** Hoje: de lugar nenhum. Saldo inicial, Admin Shop
   comprando dos jogadores, ou recompensa por atividade — escolher pelo menos uma,
   ou a economia continua parada.
2. **Admin Shop infinito ou catálogo curado?** Os dois modelos estão escritos em
   lugares diferentes do projeto.
3. **Reemitir o saldo inicial?** Exige limpar `saldo-inicial.yml` ou um
   `/eco give` manual. São 2 jogadores, dá para fazer na mão.
4. **`BC$` ou `$`?** O símbolo documentado nunca foi aplicado.

---

## 🎨 Tema visual The Bigas

Paleta derivada da MOTD de `server.properties`:

| Papel | Cor |
|---|---|
| Destaque, sucesso, marca | `#FFD700` ouro |
| Valores, ações | `#FF6A00` laranja |
| Info | `#FFB800` |
| Texto corrido | `#AAAAAA` |
| Erro | `#E03131` |
| Bordas, fundo | `#2E2E33` grafite |

O mapeamento preserva o **papel** de cada cor original em vez de achatar tudo numa
só: verde continua significando "deu certo", vermelho continua significando
"erro", agora nas cores da marca. A saída é sempre a forma `&x&R&R&G&G&B&B`, que
o `translateAlternateColorCodes` do Bukkit entende em qualquer plugin, sem
depender do parser de hex de cada um.

### Aplicado em

| Arquivo (em `~/minecraft/plugins/`) | O quê |
|---|---|
| `Essentials/config.yml` | `message-colors.primary/secondary` — repinta **1.180 ocorrências** de uma vez |
| `Essentials/motd.txt` | refeito com a barra de gradiente e o título "THE BIGAS" |
| `ChestShop/languages/lang.pt_BR.yml` | 44 linhas |
| `AdvancedPortals/lang/en_GB.lang` | 47 linhas |
| `CreativePlots/config.yml` | 18 linhas + `Title-Bar-Text` com gradiente real |
| `BigaCore/config.yml` | boas-vindas em MiniMessage |
| `WorldGuard/config.yml` | mensagem de bloqueio |
| `GravesX/config/grave.yml` | 35 linhas + holograma |

O EssentialsX 2.22 tem um tema central (`message-colors`), então duas linhas
resolveram o plugin inteiro. Esse é o ponto de alavanca — vale procurar o
equivalente antes de sair repintando arquivo por arquivo.

FancyHolograms e WorldEdit não têm código de cor em config. LuckPerms só fala
com admin.

Todos os arquivos têm backup `.bak-paleta-*` ao lado.

---

## 🪦 GravesX instalado

`plugins/GravesX-2026.4.9.2.jar`, build de 2026-09-19 com suporte declarado a
**26.3** — a maioria dos plugins de túmulo só vai até 1.21.x e não serve aqui.

Config em `plugins/GravesX/config/`, com estes desvios do padrão:

| Opção | Padrão | Agora | Porquê |
|---|---|---|---|
| `grave.time` | 10800 | **-1** | 3h é pouco se a morte foi longe |
| `protection.enabled` | false | **true** | ninguém abre túmulo alheio |
| `protection.time` | 300 | **-1** | trancado para sempre |
| `open.killer` | true | **false** | quem matou não saqueia |
| `block.head.type` | 1 | **0** | ver abaixo |
| `message.prefix` | `&c☠` | ouro `#FFD700` | paleta |

O `head.type` é a pegadinha: o padrão do GravesX **não é a cabeça do jogador**,
é uma textura fixa de baú em base64. Com `0` ele usa a cabeça real de quem
morreu, que era o pedido.

---

## 🏘️ Mundo de plots com o tema

`CreativePlots` só expõe o bloco `Materials`, mas dá para contar a história da
marca com ele:

| Peça | Antes | Agora |
|---|---|---|
| Rua | `DIRT_PATH` | `POLISHED_DEEPSLATE` |
| Moldura do lote | `STONE` | `POLISHED_BLACKSTONE` |
| Laje do lote **livre** | `STONE_SLAB` | `SMOOTH_SANDSTONE_SLAB` |
| Laje do lote **tomado** | `BRICK_SLAB` | `WAXED_CUT_COPPER_SLAB` |
| Chão do lote | `GRASS_BLOCK` | mantido |

Moldura e rua em grafite; a laje faz o gradiente da marca — **ouro = livre,
laranja = ocupado**. De cima dá para ler o loteamento inteiro de relance.

O `WAXED_` é deliberado: cobre comum oxida e fica verde, o que destruiria o tema
em algumas semanas de servidor.

Os cinco nomes foram conferidos contra
`libraries/io/papermc/paper/paper-api/26.3.build.167-beta/paper-api-26.3.build.167-beta.jar`,
não contra memória — nome de material errado quebra a geração no meio.

O mundo **ainda não foi gerado**, então o tema já nasce assim. Gera com:

```text
/cp generate plots 200 16 4
```

### Limite de plots não precisa de config

Lendo o bytecode do `PlotCommands`: se o jogador é **op** ou tem `*`, o limite
vira `Integer.MAX_VALUE`. Senão ele varre `plot.claim.1`..`plot.claim.100` e usa
o primeiro que bater. Só então cai no `Max-Plots-Per-Player` do config.

Ou seja: op já tem plots ilimitados. Para o `bigarellimatheus`:

```text
/lp user bigarellimatheus permission set plot.claim.100 true
```

---

## 🐉 End e dragão

- `previously_killed` em
  `world/dimensions/minecraft/the_end/data/minecraft/ender_dragon_fight.dat`
  virou `0`, com backup `.bak-ovo-*`. O jogo só coloca o ovo quando o dragão
  morre **e** essa flag está falsa; com ela zerada a próxima morte gera ovo novo
  e dá os 12.000 XP da primeira vez, em vez de 500.
- O portal da Fortaleza (**2393–2397 / y=-26 / 66–70**) foi resetado por comando:
  9 blocos de portal removidos e as 12 molduras voltaram a `eye=false`. Foram 4
  `/fill` separados, um por `facing` — um `/fill` só reorientaria todas as
  molduras para o norte e o portal nunca mais acenderia.

### Nota técnica: o formato de palette mudou em 26.3

O leitor de região precisou ser reescrito. Em `DataVersion 5023` uma entrada de
palette aparece em **três** formas, e as três convivem no mesmo mundo:

```text
"minecraft:bedrock"                                     → string pura
{"": "minecraft:air"}                                   → composto com string sem nome
{"id": "minecraft:pointed_dripstone", "properties": {…}} → id + properties (minúsculo)
```

Não é mais o `{"Name": …, "Properties": …}` de sempre. Além disso `TAG_Byte` é
**com sinal** — ler `Y` como byte sem sinal dava seção 251 em vez de -5.

---

## 🔧 O CI quebrou no merge e levou a dois achados reais

Os dois smoke tests ficaram vermelhos ao subir o `/pedreiro`, com a mesma
mensagem: `Evidência ausente no log: BigaCore habilitado`. Consertar isso
destravou um efeito dominó que vale registrar, porque dois degraus afetam o
servidor de verdade, não só o CI.

### 1. O projeto tinha ficado para trás do servidor

O commit `a2330c4` subiu o `pom.xml` para a API 26.3 e deixou o `setup.sh`
instalando Paper **26.2**. O CI passou a compilar o plugin contra uma API e
bootar noutra, e o plugin não habilitava.

Detalhe de diagnóstico: o passo roda com `set -e`, e o `grep -q` da evidência
falha **sem imprimir nada**. O log do GitHub vinha vazio, o que dá a impressão
errada de infraestrutura quebrada. A pista estava no `smoke_crossplay.py`, que
usa `raise` com mensagem em vez de `grep`.

Corrigido subindo o projeto inteiro para Paper 26.3 build 167, com o sha256
vindo da API do PaperMC — que confere com o jar que já está no ar.

### 2. ⚠️ O crossplay do Switch quebraria em silêncio na próxima atualização

Este é o achado que importa fora do CI.

O Geyser **2.12** mudou o padrão de conexão de RakNet para **NetherNet** e, no
caminho, removeu o valor `none` de `bedrock.signaling.mode`. O template do repo
usava exatamente esse valor:

```
SerializationException: [bedrock, signaling, mode] ... Invalid enum constant
provided, expected a value of enum, got none
```

Uma chave inválida derruba a leitura do arquivo **inteiro**. O Geyser cai para
o default, sobe `Built-in signaling` em vez de `Started Geyser on`, e **nunca
chega a ler o `transport: raknet`** que o template pedia. O servidor liga
normal; só para de atender na porta 19132.

O Switch usa RakNet. Quem atualizasse o Geyser com esse template no disco
perderia o crossplay sem nenhuma mensagem de erro aparente.

Corrigido removendo o bloco `signaling` (com `transport: raknet` ele não é
usado) e subindo o `config-version` esperado de 8 para 11, que é o que a 2.12.1
escreve.

Validado com boot real, não por changelog:

```text
[Geyser-Spigot] Started Geyser on UDP port 19132
ping UDP responde 0x1c com "MCPE;" no payload
```

O lock foi atualizado para Geyser **2.12.1 build 1276** (Floodgate seguiu em
2.2.5 b141, mesmo sha de antes), com a verificação feita do jeito que o lock
exige: baixar pelo sha256 da API do GeyserMC e bootar num Paper 26.3 limpo.

### Estado final

Os três workflows verdes em `28f4e0c`: CI, Economy Smoke e Crossplay smoke.

---

## 🎨 Correção: cada plugin fala um dialeto de cor diferente

A primeira aplicação do tema usou `&x&R&R&G&G&B&B` em todos os arquivos. Isso
renderizou lixo literal (`&x` visível, as seis letras do hex virando cores
soltas) em parte dos plugins. Não existe formato universal — foi preciso ler o
bytecode de cada jar:

| Plugin | Formato que o parser aceita | Evidência no jar |
|---|---|---|
| EssentialsX 2.22 `motd.txt` | `&#RRGGBB` | regex `(&)?&#([0-9a-fA-F]{6})` |
| EssentialsX `message-colors` | `#RRGGBB` puro | `message-colors.primary` |
| BigaCore | MiniMessage | plugin próprio |
| GravesX | `&#RRGGBB` | `&#[A-Fa-f0-9]{6}` + `convertLegacyToMiniMessage` |
| AdvancedPortals | `&#RRGGBB` ou `&x…` | regex próprio, documentado no lang |
| ChestShop | `&x&R&R&G&G&B&B` | chama `translateAlternateColorCodes` |
| CreativePlots | `&x…` + `<gradient:#a:#b>` | `ChatColor` + `GRADIENT_PATTERN` |
| WorldGuard | legado simples, **sem hex** | nenhum parser de hex no jar |

O `ALL_CODES` do Bukkit (`0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx`) inclui `Xx`,
então `&x` só funciona onde o plugin delega ao `translateAlternateColorCodes`.
Onde o plugin tem parser próprio, `&x` é texto.

Como `&#RRGGBB` não tem tag de gradiente, o degradê do MOTD do EssentialsX é
feito caractere a caractere, com o hex interpolado por `▬` e por letra.

---

## 🌐 Tradução das mensagens para português

| Fonte | Como |
|---|---|
| EssentialsX | `locale: pt_BR` — locale embutido, 1620 de 1634 chaves, e usa `<primary>`/`<secondary>` em 488 lugares, então o tema sobrevive |
| ChestShop | já estava em `pt_BR`; só o prefixo virou `[Loja]` |
| AdvancedPortals | 150 strings traduzidas à mão em `en_GB.lang` |
| GravesX | 166 strings: mensagens, holograma, obituário, cabeça, GUI e as 43 causas de morte |
| CreativePlots | 17 mensagens de `Message-Settings` |
| `spigot.yml` | as 6 mensagens de kick/whitelist/comando desconhecido |
| `config/paper-global.yml` | `connection-throttle` e `no-permission` (o resto é `<lang:…>`, traduzido pelo cliente) |
| BigaCore | já estava em português |

Backups ao lado de cada arquivo em `.bak-ingles` e `.bak-formato-*`.

---

## 🔌 Crossplay com Switch: removido

O serviço `minecraft-switch-dns.service` estava **habilitado e em laço de falha
com 22.365 reinícios** — o dnsmasq não conseguia fazer bind em `192.168.18.3`.
O crossplay não era mais usado, então saiu inteiro.

### O que foi preciso salvar antes de apagar

O `crossplay.py` guardava `ensure_stopped()`, que **não é de crossplay**: é a
trava que recusa rodar o `setup.sh` em cima de um runtime no ar. Ela foi
extraída para `server/scripts/servidor_parado.py` antes da remoção.

A trava usa dois sinais independentes, porque nenhum cobre tudo sozinho: um
processo `java` cujo `cwd` é o runtime, e o `session.lock` do mundo preso por
outro processo. O segundo pega instalações antigas iniciadas sem o `flock` do
`start.sh`; o primeiro pega um servidor que subiu mas ainda não abriu mundo.

O `.crossplay.lock` do `start.sh` virou `.servidor.lock` — mesmo mecanismo,
nome honesto.

### Armadilha encontrada ao testar a trava

A primeira versão do teste prendia o `session.lock` no próprio processo e
concluía que a trava não funcionava. `fcntl.lockf` é **de posse do processo**,
não do descritor: quem já segura o lock pega de novo sem bloquear. O teste
precisa de um processo separado segurando o arquivo. Está documentado dentro de
`server/tests/test_servidor_parado.py` para não se repetir.

### Efeito colateral bom

O instalador de crossplay deixava `enforce-secure-profile=false` no runtime,
necessário porque o Bedrock não assina chat. Era isso que produzia o aviso
amarelo *"Não é possível verificar as mensagens"* no topo da tela. Voltou a
`true`.

### Arquivos removidos

`CROSSPLAY.md`, `.github/workflows/crossplay-smoke.yml`,
`server/config/crossplay.lock.json`, `server/config/minecraft-switch-dns.service`,
`server/config/plugins/Geyser-Spigot/`, `server/config/plugins/floodgate/`,
`server/scripts/crossplay.py`, `server/scripts/install-crossplay.sh`,
`server/tests/smoke_crossplay.py`, `server/tests/test_crossplay.py`, e os
equivalentes em `~/minecraft/scripts/`.

O `doctor.sh` mantém uma verificação invertida: se sobrar Geyser ou Floodgate em
`plugins/`, ele avisa — um Geyser carregado sem o resto da stack abre uma porta
UDP que ninguém mais mantém.

### Porte aproveitado

O `start.sh` do runtime tinha uma melhoria que nunca voltou para o repositório:
o console colorido via `log4j2.xml`. Foi portada antes de sobrescrever.

---

---

## 🏙️ Mundo de plots: quatro defeitos, três deles do plugin

Regerado como `228 / 32 / 6` → **36 terrenos de 32x32** no lugar de 100 de 16x16.
Paleta nova: rua `DEEPSLATE_TILES`, meio-fio `POLISHED_BLACKSTONE_SLAB` quando
livre e `WAXED_CUT_COPPER_SLAB` quando ocupado — livre some no escuro, ocupado
acende em laranja.

### `/cp delete` não apaga nada

O `DeleteWorld` monta o caminho com `getWorldContainer() + nome`, ou seja procura
`~/minecraft/plots`. Mas o Paper guarda esse mundo como **dimensão**, em
`world/dimensions/minecraft/plots`. O plugin apaga o vazio e segue sem erro.

Sintoma: a grade nova é pintada **por cima** da antiga e as duas aparecem
sobrepostas, com passos diferentes (38 contra 20). A varredura dos `.mca`
mostrava os dois jogos de bloco ao mesmo tempo.

Conserto: `rm -rf` da pasta da dimensão com o servidor parado, antes do
`/cp generate`.

### A barreira ignora o `NW-Offset`

O `WorldManager` chama `setCenter(dconst_0, dconst_0)` — **centro zero cravado no
bytecode** — mas desloca a grade pelo `NW-Offset`. As duas coisas não conversam,
então a última fileira de terrenos nasce fora da barreira, com
`damage_per_block: 0.2`.

Medido: offset 20 → grade centrada em +21; offset 0 → centrada em **+41**. O
comentário do plugin estava certo e eu li ao contrário na primeira tentativa.
Valor correto para `228/32/6`: **`NW-Offset: 41`**.

O `setCenter` só roda na geração, então `/worldborder center` à mão é permanente.

### O escuro não era hora, era altura

`Surface-Y: -61` colocava o chão **123 blocos abaixo do horizonte**, que o
Minecraft crava em y=63. Abaixo dele o cliente desenha o disco escuro do void na
metade de baixo do céu: luz 15 no chão e cara de noite permanente. É o mesmo
visual de qualquer superflat moderno.

Corrigido para `Surface-Y: 64`. Custa altura de construção (255 em vez de 380).

Antes de achar isso, perdi três tentativas no sistema de tempo — que também
mudou no 26.3 e vale registrar:

- gamerules foram renomeados: `doDaylightCycle` → **`advance_time`**,
  `doWeatherCycle` → **`advance_weather`**, `doMobSpawning` → **`spawn_mobs`**;
- existe um registro de **relógios nomeados** (`data/minecraft/world_clock/`) e
  **timelines** (`timeline/day.json`) que interpolam luz, cor do céu e ângulo do
  sol contra esse relógio;
- `Do-Daylight-Cycle: false` faz o mundo nascer com `advance_time: 0`, congelado
  no tick da geração. Se calhar de ser noite, fica noite para sempre, e o `/time`
  não destrava. Por isso o config agora nasce com **`true`**;
- o `/time` do EssentialsX **não aceita `day`**. Os apelidos são `sunrise`,
  `dawn`, `daystart`, `morning`, `midday`, `noon`, `afternoon`, `sunset`, `dusk`,
  `sundown`, `nightfall`, `nightstart`, `night`, `midnight`. `day` cai em 0 ticks,
  que é o nascer do sol — o momento mais fraco do dia.

### O `ConfigUpdater` apaga comentário

O CreativePlots reescreve o próprio `config.yml` na geração e **remove todo
comentário acrescentado à mão**. Os valores sobrevivem, a explicação não. Por
isso o porquê de cada escolha mora aqui, não lá.

---

## 🛒 Economia: o teto limitava o valor, não o esforço

Ver `docs/economia-balanceamento.md`, seção 6-bis, para o raciocínio completo.

Em resumo: uma gold farm batia o teto diário em **4 minutos** enquanto minerar
pedra na mão levava ~3 h pelo mesmo dinheiro. O teto nunca foi furado — ele
virou mesada para quem montou a farm.

Aplicado: **50 preços de compra** reancorados na mediana de BC$/hora (29 para
baixo, 21 para cima), saldo inicial 250 → 2.000, teto 150 → 800, imposto e taxa
de loja zerados, `saldo-inicial.yml` destravado e a contradição do
`admin-buyback-enabled-at-launch` resolvida para `true`.

Uma trava nova impede que a fórmula crie arbitragem: **compra nunca passa de um
terço da venda**. Ela pegou 12 itens, incluindo um caso em que minha própria
fórmula sugeriu pagar 560 por algo que o servidor vende por 500.

Os 43 testes passam, `TransformationArbitrageTest` incluído.

---

## 🧺 ClickVillagers

Instalado `ClickVillagers-paper-1.6.3` (Modrinth, GPL-3.0). A API do Modrinth
lista **26.3** entre as versões suportadas e o `plugin.yml` traz
`api-version: '1.21'` com `folia-supported: true`. SHA1 conferido contra o que a
API publica.

Não entrou no `install-economy.sh`: aquele script cobre a stack econômica
fixada, e este plugin é conforto. Está no mesmo caso de GravesX, AdvancedPortals,
CreativePlots e FancyHolograms, todos instalados à mão.

Não cria moeda: o servidor não compra esmeralda, então facilitar hall de aldeão
não vira BC$. Acelera acesso a encantamento, o que é escolha de jogo, não de
economia.

## ⏭️ Pendências

Feito nesta sessão: mundo de plots regerado e tematizado, crossplay removido,
mensagens traduzidas, economia reprecificada e aplicada.

- [ ] **Restart** — falta para o GravesX e o ClickVillagers carregarem, para
      `enforce-secure-profile=true`, para as cores de ChestShop e
      AdvancedPortals e para os preços novos do BigaCore
- [ ] Depois do restart: `/worldedit reload` (a varinha de navegação virou
      relógio, mas a config não foi recarregada)
- [ ] **Decidir o modelo de Admin Shop** — placa de estoque infinito ou catálogo
      curado. Os dois estão escritos em lugares diferentes do projeto e são
      incompatíveis; o `server/economy/catalog.yml` que o config ao vivo promete
      **não existe**
- [ ] `Prevent-Interaction` do CreativePlots: hoje bloqueia o clique em placa de
      loja em terreno alheio. Enquanto estiver `true`, loja dentro de terreno
      não vende
- [ ] `//setbiome` no mundo de plots — o mosaico de 12 biomas deixa a grama
      manchada. Precisa do FAWE para não travar o servidor
- [ ] **Precisa de sudo:** desabilitar e remover o `minecraft-switch-dns.service`
- [ ] Decidir o destino dos 54 MB de Geyser/Floodgate em `plugins-desativados/`
- [ ] `EternalEconomy` ainda aparece no `setup.sh` e em
      `server/config/plugins/EternalEconomy/` — o provedor real é o EssentialsX
      desde a migração. Config morto, a limpar
