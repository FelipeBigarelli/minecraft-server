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

## ⏭️ Pendências

- [ ] **Restart** para o GravesX carregar (o boot de 13:24 foi antes da instalação)
- [ ] **Restart** para as cores de ChestShop e AdvancedPortals; o resto pega com
      `/ess reload`, `/cp reload`, `/biga reload`, `/wg reload`
- [ ] Decidir o modelo de economia a partir desta auditoria
- [ ] `/worldedit reload` — a varinha de navegação virou relógio, mas a config
      ainda não foi recarregada
- [ ] Gerar o mundo de plots
