# 💰 Economia BigaCore v1

> ### Conferido contra o servidor em 03/11/2026
>
> Todo número daqui saiu de `plugins/BigaCore/economy.yml`,
> `plugins/ChestShop/config.yml` e `plugins/Essentials/config.yml` — não de
> memória. A versão anterior deste documento descrevia um servidor que não
> existe mais: imposto de 4%, loja custando 50 BC$, saldo inicial de 250 BC$,
> teto de 150/dia e recompra fora da placa. Nada disso vale.
>
> Duas pendências da auditoria de 28/10/2026 estão **resolvidas**:
>
> - o provedor de economia é o **EssentialsX Economy** (o EternalEconomy foi
>   para `plugins-desativados/`), com `starting-balance: 2000` nos dois lados;
> - o saldo inicial **foi entregue**: as cinco contas do servidor têm 2.000 BC$.
>
> Leitura complementar:
>
> - [docs/sessoes/2026-10-28-auditoria-economia-e-tema-the-bigas.md](docs/sessoes/2026-10-28-auditoria-economia-e-tema-the-bigas.md) — o estado real, com evidências
> - [docs/economia-balanceamento.md](docs/economia-balanceamento.md) — estudo de balanceamento para servidor de 2 a 10 amigos e os números recomendados

Economia do servidor survival entre amigos. O objetivo é criar um mercado útil
sem transformar farms ou uma loja infinita do servidor em atalho para toda a
progressão do Minecraft.

A regra central é:

> **O servidor facilita liquidez; os jogadores formam o mercado.**

A espinha dorsal é o **ChestShop jogador ↔ jogador**. A Biga Market existe
para dar piso e teto de preço aos materiais comuns, não para substituir o
mercado. Itens raros e de progressão continuam vindo de exploração, gameplay e
negociação real.

---

## ✅ O que está ativo agora

| Regra | Valor |
|---|---:|
| Moeda | **Biga Coin (`BC$`)** |
| Casas decimais | **0** |
| Saldo inicial | **2.000 BC$** (aplicado pelo BigaCore) |
| Taxa ChestShop P2P | **0%** |
| Criar uma loja | **grátis** |
| Reembolso ao remover | **0 BC$** |
| Terreno (CreativePlots) | **1.200 → 3.600 → 10.800 BC$**, depois ×3 a cada um |
| Transações parciais | **desativadas** |
| Auto-sell / sell-all | **não existe** |
| Limite de lojas por jogador | **nenhum** (ver política não aplicada) |
| Admin Shop ilimitada | **ativa nos dois lados do balcão** |
| Buyback do servidor | **ativo, com teto diário e semanal por jogador** |

O único sink recorrente hoje é o **preço do terreno**. Imposto, criação de loja
e reembolso estão todos em zero — foi uma escolha, não esquecimento: cobrar 4%
de um mercado com cinco contas e duas compras registradas em toda a história do
servidor (`ChestShop.log`, 12/08 e 30/10/2026) só atrapalharia a formação de
preço. O terreno cobre o papel de sink porque é caro,
repetível e opcional.

Não existe pagamento automático por:

- login;
- tempo AFK;
- bloco quebrado;
- mob morto;
- juros sobre saldo.

Uma farm pode produzir muitos **itens**, mas não imprime BC$ automaticamente.

### Por que o saldo inicial não pode vir do plugin de economia

O `defaultBalance` do plugin de economia estava correto e mesmo assim a
primeira conta real nasceu zerada:

```text
('261ba52f-…', 'zBigaBiga', 0)
```

A promessa estava escrita em dois arquivos e não valia em lugar nenhum, porque
dependia do comportamento de um plugin de terceiro no instante exato em que a
conta é criada — e ela pode nascer por um caminho que ignora esse default (uma
consulta de saldo via Vault costuma criar a conta zerada).

Quem aplica a política é o BigaCore (`StartingBalance.java`), no login, com
duas travas: o UUID é marcado em `plugins/BigaCore/saldo-inicial.yml`, e o
crédito é um **complemento até** o alvo, nunca uma soma. Quem já tem 2.000 ou
mais não recebe nada, mesmo que o arquivo de controle seja apagado.

Funcionou: em 03/11/2026 as cinco contas do servidor estão com 2.000 BC$
(uma delas com 1.910, que gastou 90).

---

## 🏪 A Biga Market é a loja do servidor

Decisão do dono do servidor: a Biga Market é um **Admin Shop**, não um conjunto
de bancas de jogador.

Os dois lados do balcão têm riscos opostos, e por isso são implementados de
formas diferentes:

| Lado | Efeito na moeda | Como é feito | Limite |
|---|---|---|---|
| Servidor **vende** | sai de circulação → **sink** | placas ChestShop `Admin Shop`, estoque infinito | nenhum |
| Servidor **compra** | entra em circulação → **faucet** | a mesma placa (`B x : y S`) ou `/biga eco vender` | **teto diário e semanal por jogador** |

### Como a placa compra sem virar impressora de dinheiro

O ChestShop não sabe aplicar teto por jogador. Uma placa de Admin Shop
ilimitada do lado da compra transformaria uma farm de cobblestone ligada a noite
inteira em impressora de dinheiro — exatamente o cenário que o resto deste
documento existe para evitar.

A placa compra mesmo assim, e o teto é aplicado por fora: o
`ChestShopCapGuard` do BigaCore escuta o `PreTransactionEvent`, que é
cancelável, deixa passar o que couber no teto do dia e recusa o resto. Detalhes
que importam:

- só olha **venda** (jogador → servidor). Compra é sink, não precisa de limite;
- só conta loja **do servidor**. Venda entre jogadores move moeda, não cria;
- é **fail closed**: se a reflexão falhar, a venda é recusada. Perder uma venda
  custa um log; deixar passar custa inflação que só aparece semanas depois;
- se o console avisar que o teto não pôde ser ligado, desligue
  `admin-shop.placa-compra` na hora.

Com o teto aplicado, a vazão máxima de moeda nova é previsível:

```text
moeda nova por dia  ≤  jogadores × 800 BC$
```

contra o sink do terreno (1.200 BC$ o primeiro, ×3 a cada seguinte) e a venda
no varejo.

### O que o servidor vende

**62 itens** — todo o catálogo que tem `server-sell` — em exatamente 62 vagas
no arcade que contorna o pátio da Biga Market. A lista fica em
`admin-shop.wall` no `economy.yml`, e um teste falha o build se ela não couber.

Cada vaga é um balcão de bétula, uma placa ChestShop e um **item flutuando no
bloco acima da placa**, girando, para dar para ver o que está à venda sem ler
placa por placa. Pendurar/recolher é `/biga loja vitrine <raio>`.

**Item de progressão fica fora de propósito.** Diamante, elytra, netherite,
totem, shulker shell e afins continuam vindo de exploração ou de negociação
entre jogadores. Existe um teste automatizado que falha o build se algum deles
aparecer na parede da loja.

### O que o servidor compra

Qualquer item que tenha `server-buy` no catálogo, pelo preço de lá — sempre bem
abaixo do preço de venda, para que negociar com outro jogador continue valendo
mais a pena do que despachar no balcão.

```text
/biga eco vender              vende o que está na mão
/biga eco vender 32           vende no máximo 32
```

Regras:

- vende **apenas o item na mão**, nunca o inventário — não é um `/sellall`
  disfarçado;
- item com encantamento, nome ou dano é recusado: preço de tabela não vale para
  item único;
- o pagamento sempre arredonda **para baixo**;
- ao bater o teto, o comando avisa e manda negociar com outros jogadores.

Os tetos são `admin-buyback-daily-cap` (**800 BC$**) e
`admin-buyback-weekly-cap` (**4.000 BC$**), e vale sempre o que apertar
primeiro: o jogador recebe `min(sobra do dia, sobra da semana)`. O dia vira à
meia-noite local do servidor e a semana vira na segunda (semana ISO).

Para calibrar: 800 BC$ é ~1,4 dia de poupança para o primeiro terreno e, no
item mais valioso do catálogo, dá 39 unidades antes de fechar o dia. É piso de
liquidez, não profissão.

---

## 🧩 Stack instalada

O que está rodando no servidor do Felipe:

| Componente | Versão | Responsabilidade |
|---|---:|---|
| Paper | 26.3 build 167-beta | servidor |
| VaultUnlocked | 2.20.2 | bridge padrão de economia (`Vault`) |
| EssentialsX | 2.22.1-dev | saldo persistente (`starting-balance: 2000`) |
| ChestShop | 3.13-pre-1 | placas de loja, P2P e Admin Shop |
| BigaCore | 1.0.0 | política, catálogo, teto de recompra e UX |

> ⚠️ **O `setup.sh` ainda não reproduz isto.** Numa máquina nova ele chama o
> `install-economy.sh`, que instala **EternalEconomy 1.0.1** e nem toca no
> EssentialsX. O `doctor.sh` já aceita os dois provedores, mas um PC montado
> hoje pelo script não fica igual ao servidor real. Alinhar os dois é uma
> decisão em aberto: o EssentialsX traz muito além de economia (homes, warps,
> kits, spawn), então trocar não é só mudar uma URL.

Os JARs de terceiros **não ficam commitados no Git**. O script
`server/scripts/install-economy.sh` baixa versões exatas de fontes oficiais e
valida os artefatos antes de colocá-los no runtime.

O `doctor.sh` também verifica presença de JARs, duplicatas e valores críticos da
configuração antes do servidor ser iniciado.

---

## 🎮 Comandos para jogadores

```text
/biga eco
/biga eco status
/biga eco saldo
/biga eco preco diamond
/biga eco preco iron_ingot
/biga eco vender
/biga eco vender 32
/biga eco regras
```

`/biga eco preco <item>` mostra:

- lote usado como referência;
- tier econômico;
- preço P2P inicial sugerido;
- possível preço futuro de compra pelo servidor;
- possível preço futuro de venda pelo servidor.

O preço P2P é **referência, não obrigação**. Se duas pessoas quiserem vender um
diamante por 80 BC$, 120 BC$ ou 200 BC$, o mercado continua livre.

---

## 🧰 Como criar uma ChestShop

Coloque um baú e uma placa associada à loja. A placa segue o padrão do
ChestShop:

```text
<deixe a primeira linha vazia>
<quantidade>
B <preço-compra> : <preço-venda> S
<item>
```

Exemplo de uma loja de jogador que vende 16 ferros por 175 BC$ e compra 16
ferros por 125 BC$:

```text

16
B 175 : 125 S
IRON_INGOT
```

Interpretação:

- `B 175`: outro jogador paga 175 BC$ para **comprar da loja**;
- `125 S`: outro jogador recebe 125 BC$ para **vender para a loja**.

A primeira linha é preenchida pelo ChestShop com o dono.

Os jogadores comuns já recebem pelas permissões padrão do ChestShop acesso a
criar, comprar e vender em lojas. Poderes administrativos continuam restritos a
OP/staff.

Criar a loja é **grátis** (`SHOP_CREATION_PRICE: 0`), e remover não devolve
nada. Com cinco contas no servidor, cobrar pela placa só desencorajaria o
mercado que a gente está tentando fazer nascer. Se um dia virarem paredes de
placa inútil, o sink volta aqui.

---

## 📊 Catálogo BigaCore

A fonte de verdade é:

```text
plugin/src/main/resources/economy.yml
```

Quando o BigaCore roda pela primeira vez, uma cópia vai para:

```text
~/minecraft/plugins/BigaCore/economy.yml
```

Algumas referências iniciais:

O catálogo tem **85 itens**: 62 com `server-sell` (o servidor vende) e 51 com
`server-buy` (o servidor compra).

| Item | Lote | P2P ref. | Servidor compra | Servidor vende |
|---|---:|---:|---:|---:|
| Cobblestone | 64 | 20 BC$ | 2 BC$ | 32 BC$ |
| Stone | 64 | 28 BC$ | 14 BC$ | 44 BC$ |
| Oak Log | 32 | 48 BC$ | 12 BC$ | 80 BC$ |
| Coal | 32 | 64 BC$ | 33 BC$ | 100 BC$ |
| Iron Ingot | 16 | 160 BC$ | 13 BC$ | 240 BC$ |
| Gold Ingot | 16 | 240 BC$ | 2 BC$ | 360 BC$ |
| Redstone | 64 | 96 BC$ | 53 BC$ | 160 BC$ |
| Diamond | 1 | 120 BC$ | — | — |
| Blaze Rod | 8 | 240 BC$ | 6 BC$ | 380 BC$ |
| Shulker Shell | 1 | 300 BC$ | — | — |
| Ancient Debris | 1 | 650 BC$ | — | — |
| Netherite Ingot | 1 | 3.000 BC$ | — | — |
| Totem of Undying | 1 | 900 BC$ | — | — |
| Elytra | 1 | 3.200 BC$ | — | — |
| Nether Star | 1 | 3.500 BC$ | — | — |
| Beacon | 1 | 4.000 BC$ | — | — |
| Mace | 1 | 8.000 BC$ | — | — |
| Dragon Egg | 1 | 15.000 BC$ | — | — |

`—` significa **P2P somente**: o servidor não negocia aquele item em nenhum dos
dois lados. É por isso que diamante sumiu dos dois últimos campos — ele é item
de progressão e saiu do balcão de propósito.

Repare no ferro e no ouro: o servidor vende a 240/360 e recompra a 13/2. O
spread é brutal **de propósito** — é o que faz vender para outro jogador valer
mais a pena do que despachar no balcão.

---

## 🌍 Por que não existe loja infinita de tudo

Se o servidor comprasse qualquer quantidade de ferro, ouro, bambu ou cana por
um preço atraente, a economia deixaria de ser comércio e viraria competição de
quem constrói a maior farm automática.

Por isso:

1. recursos renováveis/farmáveis têm recompra **muito** baixa — cobblestone
   são 2 BC$ por 64, ouro 2 BC$ por 16;
2. a recompra tem teto por jogador, por dia e por semana;
3. 34 dos 85 itens do catálogo não têm recompra nenhuma;
4. itens de progressão permanecem P2P, nos dois lados do balcão;
5. não existe `/sellall`: `/biga eco vender` só vende o item na mão.

A experiência desejada é:

> “Posso montar uma farm para ter estoque e ficar rico vendendo para meus
> amigos.”

E não:

> “Posso deixar uma farm AFK ligada e imprimir dinheiro infinito no servidor.”

---

## 🔒 Proteções econômicas ativas

O ChestShop está configurado para:

- rejeitar loja em que `sell > buy` de forma explorável;
- não aceitar transação parcial;
- manter proteção de placa e hopper;
- não permitir múltiplas lojas no mesmo bloco;
- registrar transações/remoções em log;
- não atualizar o próprio JAR automaticamente;
- limitar quantidade global por loja/transação.

Criação e imposto estão em **zero** hoje; quem segura a torneira é o teto de
recompra do BigaCore, não o ChestShop.

O `priceLimits.yml` está **vazio de propósito**: preço entre jogadores não cria
moeda, só move, e a política escrita no `economy.yml` diz explicitamente que o
catálogo é referência e não tabela obrigatória. Até 03/11/2026 esse arquivo
ainda tinha os exemplos que vêm no jar do ChestShop (`piston_head`,
`powered_rail`, `placed_banner`), que recusavam lojas desses itens com limites
de 1 a 3 BC$ sem explicação. Não apague o arquivo: o ChestShop só recria os
exemplos quando ele não existe.

O `doctor.sh` impede o primeiro boot se detectar versões esperadas ausentes,
configs econômicos críticos incorretos ou JARs duplicados.

---

## 🟡 Política já desenhada, mas ainda NÃO aplicada

As seguintes ideias estão registradas em `economy.yml`, porém não devem ser
confundidas com recurso já ativo:

| Política | Referência atual | Situação |
|---|---:|---|
| Buyback máximo por jogador/dia | 800 BC$ | **aplicado** pelo BigaCore |
| Buyback máximo por jogador/semana | 4.000 BC$ | **aplicado** pelo BigaCore |
| Teto combinado de moeda nova/dia | 200 BC$ | só documentado |
| Primeiro tier comercial | até 6 lojas | só documentado |
| Tiers futuros | 12 / 20 lojas | só documentado |

O teto combinado seria **global**, e não por jogador. Isso muda a justiça entre
as pessoas: quem vendesse primeiro no dia comeria o orçamento dos outros. Por
isso continua só escrito — precisa de decisão do dono antes de virar código.

O limite de lojas por jogador continua sendo um número decorativo: nada o
aplica.

**A ideia de aplicá-lo via permissão `ChestShop.shop.limit.<n>` estava errada.**
Esse node não existe. O enum `Permission` do ChestShop 3.13-pre-1 tem 34 nodes e
nenhum limita a **quantidade** de lojas — os `NOLIMIT_*` são sobre limite de
**preço**. Verificado no jar instalado, não na documentação do plugin.

Instalar um gerenciador de permissões esperando que isso funcione seria trabalho
perdido.

Se um dia virar necessidade real, o caminho é `PreShopCreationEvent` — que
existe, é cancelável e entrega `getPlayer()`. Antes disso é preciso decidir como
contar as lojas de um UUID de forma confiável: loja destruída, placa removida
fora do fluxo normal, mundo restaurado de backup.

Elas só serão ativadas quando existir motivo real. Um servidor entre poucos
amigos não precisa começar com uma camada de “banco central” complexa antes de
ter sequer histórico de transações.

---

## 🚫 Itens que o servidor não deve vender como atalho

No modelo inicial, itens como estes ficam fora de uma loja infinita do servidor:

- Elytra;
- Netherite e Ancient Debris;
- Mending e encantamentos especiais;
- Shulker Shell/Box;
- Totem of Undying;
- Nether Star/Beacon;
- Trident;
- Mace/Heavy Core;
- templates raros;
- Dragon Egg e outros troféus únicos.

Eles podem ser vendidos **entre jogadores** pelo preço que o mercado aceitar.

---

## 🛠️ Alterar preços depois

Existem novamente duas cópias, como no restante do projeto:

```text
REPOSITÓRIO
plugin/src/main/resources/economy.yml

RUNTIME
~/minecraft/plugins/BigaCore/economy.yml
```

Para testar uma mudança no servidor atual, altere o runtime e execute:

```text
/biga reload
```

Para a mudança sobreviver a uma instalação nova, atualize também o arquivo do
repositório.

Não altere preço porque “parece barato” depois de uma única venda. Rebalancear
depois de observar:

- quantidade realmente negociada;
- preço mediano das vendas concluídas;
- itens que ficaram sem estoque;
- itens que ninguém compra;
- farms que começaram a dominar a oferta.

---

## 🧪 Validação automática

Além do CI normal, existe:

```text
.github/workflows/economy-smoke.yml
```

Ele cria um runtime descartável como um PC novo, roda o mesmo `setup.sh`, passa
o `doctor.sh`, inicia o Paper com a stack econômica, confirma que Vault, o
provedor de economia, o ChestShop e o BigaCore carregaram juntos e encerra com
`stop`. Como o `setup.sh` ainda instala o EternalEconomy, é essa a stack que o
CI valida — e não a do servidor real.

Esse teste **não usa nem inicia o mundo real do Felipe**.

---

## Próxima fase econômica

A primeira fase de jogo é propositalmente simples:

```text
2.000 BC$ inicial
   ↓
Biga Market (servidor vende) + ChestShops P2P
   ↓
recompra com teto de 800/dia como piso de liquidez
   ↓
terreno a 1.200 BC$ como sink
   ↓
observar preços e produção real
   ↓
decidir se precisa de mais sink, mais teto ou nenhum dos dois
```

Depois de algumas sessões com os amigos, os dados reais devem decidir se
precisamos de buyback limitado, contratos/missões, licenças de comerciante ou
novos sinks. Não o contrário.
