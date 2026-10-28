# 💹 Economia balanceada para servidor survival de amigos

Estudo do caso concreto do The Bigas: survival, **2 a 10 jogadores**, todos
amigos, sem rotatividade. Parte de uma auditoria do que já existe no servidor e
termina em recomendações numéricas.

Documento irmão: [`ECONOMIA.md`](../ECONOMIA.md) descreve a política escrita.
[`docs/sessoes/2026-10-28-auditoria-economia-e-tema-the-bigas.md`](sessoes/2026-10-28-auditoria-economia-e-tema-the-bigas.md)
descreve o estado real do servidor hoje. Este aqui é o **porquê** dos números.

---

## 1. A conclusão primeiro

O catálogo de preços do BigaCore **já está bem calibrado**. Não precisa ser
refeito. O que impediu a economia de existir foram três coisas, nenhuma delas
de preço:

1. **Nunca entrou dinheiro.** O saldo inicial ficou órfão na troca de plugin de
   economia e ninguém jamais teve 1 BC$.
2. **O teto de recompra é pequeno demais** para a escala. 150 BC$/dia equivalem
   a ~30 minutos de venda no ritmo máximo. Um jogador que joga 3 horas num
   sábado bate o teto no primeiro meia hora e o resto da sessão não tem
   economia nenhuma.
3. **O desenho foi calibrado contra a ameaça errada.** Toda a arquitetura
   defende contra hiperinflação por farm automática — uma ameaça real em
   servidor público de 200 pessoas. Num servidor de 3 amigos, o modo de falha
   observado foi o oposto: **irrelevância**. Duas transações em toda a história
   do servidor.

A correção é **abrir a torneira**, mantendo intactos o spread de preços e a
lista de itens de progressão fora da loja.

---

## 2. O que a escala de 10 jogadores muda

### 2.1 Mercado entre jogadores não funciona nesse tamanho

A literatura de design de SMP é consistente: escambo direto funciona bem entre
**2 a 4** jogadores e quebra a partir de **6**, porque ninguém sabe mais o que o
outro tem ou quer. Lojas de baú entre jogadores só começam a fazer sentido com
dezenas de jogadores ativos, e ficam boas até umas 50 pessoas.

O The Bigas tem **2 contas com saldo registrado**. Isso está abaixo do piso onde
o mercado P2P existe.

Consequência prática: **não vale investir em infraestrutura P2P.** O
`player-shop-first-limit`, o imposto de 4% sobre transação entre jogadores, o
distrito comercial — tudo isso resolve problemas que esse servidor não tem. Com
dois jogadores, se um quer o ferro do outro, ele pede no WhatsApp.

Isso não significa desligar o ChestShop. Significa que **o papel dele é ser o
balcão do servidor**, não um mercado livre.

### 2.2 Dinheiro não pode ser o portão de progressão

Em servidor público, dinheiro é o que separa quem tem de quem não tem. Num
servidor de amigos, **todo mundo vai conseguir tudo de qualquer jeito** — é só
questão de jogar. Se a loja vende elytra, ela não cria desigualdade: ela apaga o
End do jogo para todos ao mesmo tempo.

Então a pergunta "o que pode comprar?" tem uma resposta mais limpa aqui do que
num servidor grande:

> **A loja pode vender o que é chato mas não é raro. Não pode vender o que é
> raro, nem o que é a recompensa de um conteúdo.**

Minerar 20 stacks de cobblestone para uma construção é chato e não é conteúdo.
Matar o dragão é conteúdo. A loja resolve o primeiro e não toca no segundo.

### 2.3 Inflação quase não importa; velocidade importa

Com 2 a 10 pessoas, mesmo que alguém acumule 100.000 BC$, não há mercado para
distorcer — não existe outro jogador competindo por oferta limitada. O preço do
servidor é fixo, então "inflação" aqui só significa "o dinheiro parou de ter
função".

O que importa de verdade é **a velocidade com que se ganha comparada ao tempo de
pegar na mão**. Se ganhar BC$ para comprar um stack de cobblestone demora mais
que minerar o stack, a loja é decorativa. Foi exatamente o que aconteceu.

---

## 3. Auditoria numérica do que já existe

### 3.1 Os preços estão coerentes

Convertendo o catálogo para **BC$ por minuto de trabalho manual equivalente**:

| Item | Lote | `server-sell` | Tempo de obter na mão | Taxa implícita |
|---|---:|---:|---:|---:|
| Cobblestone | 64 | 32 | ~1,5 min | ~21 BC$/min |
| Pedra | 64 | 44 | ~2 min | ~22 BC$/min |
| Ferro | 16 | 240 | ~17 min de caverna | ~14 BC$/min |
| Ouro | 16 | 360 | ~25 min | ~14 BC$/min |
| Vara de Blaze | 8 | 380 | ~20 min de fortaleza | ~19 BC$/min |

A faixa fica entre **14 e 22 BC$/min**. Isso é calibragem boa, e provavelmente
não foi acidente. O lado da compra:

| Item | Lote | `server-buy` | Taxa implícita |
|---|---:|---:|---:|
| Cobblestone | 64 | 8 | ~5 BC$/min |
| Ferro | 16 | 80 | ~4,7 BC$/min |
| Ouro | 16 | 96 | ~3,8 BC$/min |

Também uniforme, em torno de **5 BC$/min** — ou seja, o servidor compra por
**25% a 30% do que vende**. Esse spread de 3,5x a 4x é a principal defesa
anti-arbitragem do sistema e **deve ser mantido**. É ele que impede o loop
"compro barato do servidor, vendo caro para o servidor".

### 3.2 Os itens de progressão já estão protegidos

O catálogo marca `diamond`, `emerald`, `shulker_shell`, `netherite_*`, `elytra`
e `totem_of_undying` com `p2p` mas **sem `server-sell` nem `server-buy`**. Ou
seja: existem como referência de preço entre jogadores, e o servidor não os
negocia dos dois lados. Isso está certo e é a decisão mais importante do
arquivo inteiro.

### 3.3 Onde está o risco real: `server-buy` de item farmável

Estes itens têm `server-buy` e são **produzíveis infinitamente por farm**:

| Item | `server-buy` por unidade | Farm que o produz |
|---|---:|---|
| `iron_ingot` | 5,0 | Iron farm (~300/h) |
| `gold_ingot` | 6,0 | Gold farm no Nether (milhares/h) |
| `gunpowder` | 3,0 | Creeper farm |
| `ender_pearl` | 4,0 | Enderman farm |
| `blaze_rod` | 4,0 | Blaze spawner |
| `slime_ball` | 3,0 | Slime farm |
| `cobblestone` | 0,125 | Cobble gen (infinito, AFK) |
| `bone` / `string` | 0,25 | Mob farm genérica |

Sem teto, uma gold farm sozinha geraria algo como **6.000 BC$/hora** — mais que
o preço de uma elytra inteira a cada 30 minutos. Seria o fim do jogo.

**O teto diário resolve isso completamente.** Com `admin-buyback-daily-cap: 150`,
o máximo que qualquer farm pode render é 150 BC$/dia, independente do que
produza. A farm vira irrelevante para a economia, que é o objetivo.

O mecanismo está certo. **O valor está errado para a escala.**

### 3.4 O teto de 150 BC$/dia é pequeno demais

Com taxa de compra de ~5 BC$/min, o teto de 150 BC$ representa **30 minutos** de
venda. E o que 150 BC$ compram na loja?

| Compra | Custo | Dias no teto |
|---|---:|---:|
| 1 stack de cobblestone | 32 | 0,2 |
| 1 stack de vidro | 110 | 0,7 |
| 16 ferro | 240 | **1,6** |
| 1 stack de areia | 80 | 0,5 |
| Criar 1 loja ChestShop | 50 | 0,3 |

Um jogador que queira 10 stacks de vidro para uma estufa precisa de **7 dias** de
venda no teto máximo. Minerar e fundir a areia leva uma hora. A loja perde para
o jogo em qualquer comparação.

E o saldo inicial de **250 BC$** — que nem chegou a ser entregue — compra
**7 stacks de cobblestone**. É menos do que um jogador pega em dez minutos.

---

## 4. Desenho recomendado

### 4.1 Princípio: a loja vende tempo, não poder

Toda decisão deriva disso:

- **Pode vender** o que é volumoso, repetitivo e sem conteúdo associado.
- **Não pode vender** o que é a recompensa de uma estrutura, de um chefe ou de
  uma dimensão.
- **Pode comprar** quase qualquer coisa, porque o teto diário neutraliza o risco.
- **Não deve comprar** o que incentiva destruir o mundo por dinheiro.

### 4.2 Tabela de política por categoria

| Categoria | Servidor **vende**? | Servidor **compra**? | Razão |
|---|:---:|:---:|---|
| Blocos de construção (pedra, terra, areia, vidro, madeira, concreto, lã) | ✅ | ✅ | Volumoso, zero conteúdo. É o coração da loja |
| Comida básica e cultivo | ✅ | ✅ | Chato, renovável, sem risco |
| Minérios comuns (ferro, cobre, ouro, redstone, lápis, carvão) | ✅ | ✅ com teto | Caverna é conteúdo, mas repetitivo. Preço alto no lado da venda mantém a mineração viva |
| Drops de mob comuns (osso, linha, pólvora, carne podre) | ✅ | ✅ com teto | Farm os produz; o teto neutraliza |
| **Diamante, esmeralda** | ❌ | ⚠️ opcional | Vender mata a mineração profunda. Comprar é aceitável como dreno de excedente |
| **Netherite, sucata, molde** | ❌ | ❌ | É o topo da progressão. Dinheiro não pode tocar |
| **Elytra, totem, casca de shulker** | ❌ | ❌ | São a recompensa do End e das mansões. Vender apaga o conteúdo |
| **Nether star, beacon, cabeça de wither** | ❌ | ❌ | Recompensa de chefe |
| **Maçã dourada encantada, trident, coração do mar** | ❌ | ❌ | Loot raro; vender destrói a exploração |
| **Livro de Mending, livros encantados bons** | ❌ | ❌ | Gate de durabilidade do jogo inteiro |
| **Spawner, ovo de spawn** | ❌ | ❌ | Quebra o balanço de farms |
| **Ovo de dragão, cabeça de dragão** | ❌ | ❌ | Único por mundo. Não tem preço |
| Decoração, corantes, cerâmica, flores | ✅ | ✅ | É exatamente o caso de uso ideal |
| **XP / níveis** | ❌ | ❌ | Vender XP vende encantamento, que é progressão |

O catálogo atual **já obedece a essa tabela** nos pontos críticos. A tabela
serve para decidir os itens futuros sem reabrir a discussão toda vez.

### 4.3 Números recomendados

| Chave | Hoje | Recomendado | Razão |
|---|---:|---:|---|
| `starting-balance` | 250 | **2.000** | Compra umas 2h de material. O suficiente para a loja ser útil na primeira sessão, longe de pular progressão |
| `admin-buyback-daily-cap` | 150 | **800** | ~2,5h de venda. Cobre uma sessão longa sem virar renda passiva |
| `admin-buyback-weekly-cap` | 900 | **4.000** | Mantém a razão dia/semana em ~5, reconhecendo que ninguém joga 7 dias |
| `chestshop-tax-percent` | 4 | **0** | Imposto sobre P2P num servidor de 2 pessoas só gera atrito. O dreno real é a compra na loja |
| `shop-creation-price` | 50 | **0** | Mesma razão. Cobrar para montar loja desestimula o único mercado que poderia nascer |
| Spread `server-buy`/`server-sell` | ~27% | **manter** | É a defesa anti-arbitragem. Não mexer |

Com `starting-balance: 2000` e teto de 800/dia, um jogador ativo acumula uns
**5.000 BC$ por semana** se quiser. Isso compra ~45 stacks de vidro ou ~330
ferro. É material de construção de verdade, e continua **muito** mais lento que
simplesmente jogar — que é o ponto.

### 4.4 A torneira que falta: pagar por conquista

Hoje o servidor tem a regra, correta, de **não pagar** por login, tempo AFK,
bloco quebrado ou mob morto. Isso evita renda passiva. Mas deixa a economia sem
nenhuma entrada além da recompra.

Proposta: **pagar por advancement**, uma vez por jogador.

Por que encaixa bem neste servidor:

- É **impossível de farmar**: cada conquista paga uma vez só, para sempre.
- É **limitado por construção**: existem ~120 advancements, então o total
  emitido por jogador tem teto natural.
- **Premia o conteúdo** em vez do tempo. Quem vai ao Nether, dropa o dragão,
  acha uma mansão, ganha.
- Chega **na hora certa**: o jogador recebe dinheiro logo quando acabou de
  gastar recursos numa expedição.
- Não precisa de plugin novo — é um listener de `PlayerAdvancementDoneEvent` no
  BigaCore, que já existe e já fala com o Vault.

Faixas sugeridas:

| Tipo de conquista | Valor | Exemplos | Total aproximado |
|---|---:|---|---:|
| Básica (`story/`) | 60 | Pegar madeira, fazer bancada | ~1.500 |
| Nether (`nether/`) | 150 | Fortaleza, blaze, debris | ~3.500 |
| End (`end/`) | 300 | Matar dragão, cidade, elytra | ~2.500 |
| Aventura (`adventure/`) | 120 | Mansão, raid, trial chamber | ~4.000 |
| Criação (`husbandry/`) | 90 | Bichos, cultivo, pesca | ~3.000 |
| Receitas (`recipes/`) | 0 | São centenas e são automáticas | 0 |

Total ao longo de todo o jogo: **~14.000 BC$ por jogador**, distribuídos
naturalmente ao longo de meses. É um faucet generoso sem ser uma fonte contínua.

> **Cuidado de implementação:** filtrar `minecraft:recipes/*`. São centenas de
> advancements que disparam sozinhos ao pegar um item, e pagariam milhares de
> BC$ no primeiro dia sem nenhum esforço.

### 4.5 Drenos que fazem sentido aqui

O dreno principal já existe e é a própria compra na loja. Além dele, dois que
cabem num servidor pequeno:

- **Plots extras no mundo criativo.** O primeiro é de graça; do segundo em
  diante custa. É dreno puro e dá uso ao mundo que acabou de ser gerado.
- **Conserto e renomeio.** Um `/biga reparar` que cobra por durabilidade é um
  dreno que cresce junto com o quanto o jogador joga, e não exige bigorna nem
  XP. Encaixa com a elytra, que desgasta sempre.

Dois que **não** cabem:

- **Taxa de teleporte.** Com 2 jogadores, só gera irritação.
- **Aluguel de terreno.** Cobrar manutenção de casa entre amigos é hostil, e se
  alguém parar de jogar por um mês a casa some.

---

## 5. Riscos conhecidos e como vigiar

| Risco | Sintoma | O que fazer |
|---|---|---|
| Farm vira impressora | `/baltop` crescendo sem ninguém jogar | Confirmar que o `ChestShopCapGuard` está ativo no boot. O console avisa se não subiu |
| Arbitragem compra/venda | Saldo crescendo com 0 itens novos | Conferir que nenhum item tem `server-buy` ≥ `server-sell` |
| Economia irrelevante de novo | `ChestShop.log` parado por semanas | É o modo de falha **histórico** deste servidor. Vigiar mais que a inflação |
| Preço fora de lugar | Um item sendo vendido ao servidor muito mais que os outros | Recalcular pela taxa BC$/min da seção 3.1 |

### Contradição a resolver antes de ligar

O `economy.yml` tem, em lugares diferentes:

```yaml
policy:
  admin-buyback-enabled-at-launch: false   # linha 44
admin-shop:
  buyback-enabled: true                    # linha ~69
```

Uma diz que a recompra não entra no lançamento; a outra liga a recompra. Antes
de qualquer ajuste de número, decidir qual vale — é a chave que controla a
**única torneira existente hoje**.

---

## 6. Ordem de execução sugerida

1. **Destravar o saldo inicial.** Limpar `plugins/BigaCore/saldo-inicial.yml` ou
   dar `/eco give` manual nos dois jogadores. São duas pessoas, dá para fazer na
   mão.
2. **Alinhar o `ECONOMIA.md` com a realidade**: o provedor é EssentialsX, não
   EternalEconomy; aplicar `BC$` como `currency-symbol` se o nome importa.
3. **Resolver a contradição** da seção 5.
4. **Subir os três números** da seção 4.3 (saldo inicial, teto diário, teto
   semanal) e zerar imposto e taxa de criação de loja.
5. **Decidir o modelo de Admin Shop** — placa infinita ou catálogo curado. Os
   dois estão escritos em lugares diferentes do projeto e são incompatíveis.
6. **Construir a Biga Market de fato** e povoar com os itens da seção 4.2. Loja
   vazia não ensina ninguém a usar economia.
7. **Só então** avaliar o faucet de advancements, com uma semana de `/baltop`
   como base de comparação.

Não fazer tudo de uma vez. Subir número é fácil; tirar dinheiro de circulação
depois que entrou é o que dá trabalho.

---

## 6-bis. Aplicado em 28/10/2026 — o esforço, não só o valor

Esta seção substitui a conclusão da seção 3.3, que dizia que o teto diário
"resolve isso completamente". Ele resolve metade.

### O que faltava ver

O teto limita **quanto dinheiro entra**. Ele não diz nada sobre **quanto esforço
custa tirá-lo**. Com os preços originais e um teto de 800 BC$:

| Item | Como se obtém | BC$/hora | Bate o teto em | vs. trabalho manual |
|---|---|---:|---:|---:|
| Ouro | Gold farm no Nether | 12000 | 4 min | 46x |
| Perola do End | Farm de enderman | 2400 | 20 min | 9x |
| Slime | Farm de slime | 1800 | 27 min | 7x |
| Ferro | Iron farm | 1750 | 27 min | 7x |
| Vara de Blaze | Spawner de blaze | 1600 | 30 min | 6x |

Minerar pedra na mão leva ~3 h para o mesmo dinheiro. Uma gold farm chegava ao
teto em **4 minutos**. O teto não estava sendo furado — estava sendo atingido
por quem não jogou, e virou mesada para quem montou a farm uma vez.

### A regra nova

> O servidor paga o mesmo **por hora de jogo**, não por item.

Para cada item, `server-buy` por unidade = alvo de BC$/hora ÷ velocidade de
obtenção. A âncora é a **mediana** do que o catálogo já pagava
(562 BC$/h), não o item mais lento: ancorar embaixo faria o
servidor pagar menos por quase tudo e empurraria a loja de volta para a
irrelevância, que é o modo de falha histórico deste servidor.

Item de farm 100% automática leva mais um corte de 50%, porque igualar BC$/hora
ainda premia quem dorme ao lado da máquina.

### A trava que pegou um erro meu

A fórmula sugeriu comprar concha de náutilo por **560**, sendo que o servidor a
vende por **500** — eu estaria criando exatamente a arbitragem que este
documento manda evitar. A causa: pesca AFK rende pouco por hora, então a fórmula
manda pagar muito; mas como é AFK, o certo é pagar pouco.

Daí a trava: **nenhuma compra pode passar de um terço da venda**. Ela corrigiu
12 itens.

Lição que vale para o método inteiro: a fórmula iguala *taxa*, não *atenção*.
Onde ela brigar com o bom senso, o bom senso ganha.

### Mudanças aplicadas

| O quê | De | Para |
|---|---:|---:|
| Preços de compra ajustados | — | **50 itens** (29 para baixo, 21 para cima) |
| `currency.starting-balance` | 250 | **2.000** |
| `admin-buyback-daily-cap` | 150 | **800** |
| `admin-buyback-weekly-cap` | 900 | **4.000** |
| `chestshop-tax-percent` | 4 | **0** |
| `shop-creation-price` / `refund` | 50 / 10 | **0 / 0** |
| `admin-buyback-enabled-at-launch` | false | **true** (resolve a contradição da seção 5) |
| EssentialsX `starting-balance` | 0 | **2.000** |
| EssentialsX `currency-symbol` | `$` | **`BC$`** |
| ChestShop `SHOP_CREATION_PRICE` | 50 | **0** |
| `saldo-inicial.yml` | 3 UUIDs travados | **zerado** |

Zerar o `saldo-inicial.yml` é seguro: o `StartingBalance` credita o que **falta**
para chegar ao alvo, nunca soma. Quem já tiver 2.000 não recebe nada.

### Verificações feitas

- As 10 receitas modeladas continuam dando prejuízo ao atacante.
- Nenhum item ficou com spread abaixo de 3x.
- Os 43 testes do BigaCore passam, incluindo `TransformationArbitrageTest`.

O levantamento completo item a item saiu como PDF e CSV, gerados a partir do
`economy.yml` real.

---

## Fontes

Princípios gerais de faucet/sink e de escala de SMP vieram de:

- [Setting Up a Minecraft Server Economy: Plugins, Configuration, and Balance](https://space-node.net/blog/minecraft-economy-plugin-setup-guide-2025)
- [Setting up an economy server: the right plugins and settings](https://mc-node.net/blog/en/minecraft-economy-server-plugins-2/)
- [Minecraft SMP Guide: Economy, Land Claims, Events](https://guildorder.com/games/minecraft/guides/smp-frameworks) — limiares de 2-4 e 6+ jogadores
- [Economy Servers: What Makes Them Successful](https://minecraft.how/blog/post/economy-server-success-stories)
- [Gold sink — Wikipedia](https://en.wikipedia.org/wiki/Gold_sink)

São em maioria blogs de empresas de hospedagem: servem como ponto de partida,
não como referência medida. Os números deste documento vêm da calibragem do
próprio catálogo do servidor, não deles.
