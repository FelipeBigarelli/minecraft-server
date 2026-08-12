# 💰 Economia BigaCore v1

Economia do servidor survival entre amigos. O objetivo é criar um mercado útil
sem transformar farms ou uma loja infinita do servidor em atalho para toda a
progressão do Minecraft.

A regra central é:

> **O servidor facilita liquidez; os jogadores formam o mercado.**

O lançamento usa principalmente **ChestShop jogador ↔ jogador**. Itens raros e
de progressão continuam vindo de exploração, gameplay e negociação real.

---

## ✅ O que está ativo agora

| Regra | Valor |
|---|---:|
| Moeda | **Biga Coin (`BC$`)** |
| Casas decimais | **0** |
| Saldo inicial | **250 BC$** (aplicado pelo BigaCore) |
| Taxa ChestShop P2P | **4%** |
| Criar uma loja | **50 BC$** |
| Reembolso ao remover | **10 BC$** |
| Transações parciais | **desativadas** |
| Auto-sell / sell-all | **não existe** |
| Admin Shop ilimitada | **ativa, só do lado da venda** |
| Buyback do servidor | **ativo, com teto diário por jogador** |

Os 4% de imposto não vão para outra conta: saem de circulação. Portanto são um
**money sink real**, não apenas dinheiro movido para uma tesouraria.

Não existe pagamento automático por:

- login;
- tempo AFK;
- bloco quebrado;
- mob morto;
- juros sobre saldo.

Uma farm pode produzir muitos **itens**, mas não imprime BC$ automaticamente.

### ⚠️ O saldo inicial não vinha do plugin de economia

O `defaultBalance: '250'` do EternalEconomy estava correto e mesmo assim a
primeira conta real nasceu zerada:

```text
('261ba52f-…', 'zBigaBiga', 0)
```

A promessa estava escrita em dois arquivos e não valia em lugar nenhum, porque
dependia do comportamento de um plugin de terceiro no instante exato em que a
conta é criada — e ela pode nascer por um caminho que ignora esse default (uma
consulta de saldo via Vault costuma criar a conta zerada).

Agora quem aplica a política é o BigaCore (`StartingBalance.java`), no login,
com duas travas: o UUID é marcado em `plugins/BigaCore/saldo-inicial.yml`, e o
crédito é um **complemento até** 250, nunca uma soma. Quem já tem 250 ou mais
não recebe nada, mesmo que o arquivo de controle seja apagado.

---

## 🏪 A Biga Market é a loja do servidor

Decisão do dono do servidor: a Biga Market é um **Admin Shop**, não um conjunto
de bancas de jogador.

Os dois lados do balcão têm riscos opostos, e por isso são implementados de
formas diferentes:

| Lado | Efeito na moeda | Como é feito | Limite |
|---|---|---|---|
| Servidor **vende** | sai de circulação → **sink** | placas ChestShop `Admin Shop`, estoque infinito | nenhum |
| Servidor **compra** | entra em circulação → **faucet** | `/biga eco vender` no BigaCore | **teto diário por jogador** |

### Por que a recompra não é uma placa

O ChestShop não sabe aplicar teto por jogador. Se a recompra fosse uma placa de
Admin Shop ilimitada, uma farm de cobblestone ligada a noite inteira viraria uma
impressora de dinheiro — exatamente o cenário que o resto deste documento existe
para evitar.

Passando pelo BigaCore, a vazão máxima de moeda nova é previsível:

```text
moeda nova por dia  ≤  jogadores × 150 BC$
```

contra os sinks de 4% de imposto, 50 BC$ por loja criada e a venda no varejo.

### O que o servidor vende

**60 itens** — praticamente todo o catálogo vendável — distribuídos em 61 vagas
na parede do fundo e nas duas laterais do salão. A lista fica em
`admin-shop.wall` no `economy.yml`.

Cada vaga é um barril, uma placa ChestShop e um **item flutuando** acima do
balcão, para dar para ver o que está à venda sem ler placa por placa.

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

Os tetos são `admin-buyback-daily-cap` (150 BC$) e `admin-buyback-weekly-cap`
(900 BC$), que já estavam escritos como política antes de existir código.

---

## 🧩 Stack instalada

O `setup.sh` instala automaticamente versões fixadas e testadas pelo projeto:

| Componente | Versão | Responsabilidade |
|---|---:|---|
| Paper | 26.2 build 92 | servidor |
| VaultUnlocked | 2.20.2 | bridge padrão de economia (`Vault`) |
| EternalEconomy | 1.0.1 | saldo persistente / SQLite |
| ChestShop | 3.13-pre-1 | lojas físicas jogador ↔ jogador |
| BigaCore | 1.0.0 | política, catálogo e UX da economia |

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

Criar a loja custa **50 BC$**. Isso é intencional: evita paredes de placas
inúteis e cria um pequeno sink desde o começo.

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

| Item | Lote | P2P ref. | Servidor compra* | Servidor vende* |
|---|---:|---:|---:|---:|
| Cobblestone | 64 | 20 BC$ | 8 BC$ | 32 BC$ |
| Stone | 64 | 28 BC$ | 10 BC$ | 44 BC$ |
| Oak Log | 32 | 48 BC$ | 16 BC$ | 80 BC$ |
| Coal | 32 | 64 BC$ | 20 BC$ | 100 BC$ |
| Iron Ingot | 16 | 160 BC$ | 80 BC$ | 240 BC$ |
| Gold Ingot | 16 | 240 BC$ | 96 BC$ | 360 BC$ |
| Redstone | 64 | 96 BC$ | 24 BC$ | 160 BC$ |
| Diamond | 1 | 120 BC$ | 55 BC$ | 190 BC$ |
| Blaze Rod | 8 | 240 BC$ | 32 BC$ | 380 BC$ |
| Shulker Shell | 1 | 300 BC$ | — | — |
| Ancient Debris | 1 | 650 BC$ | — | — |
| Netherite Ingot | 1 | 3.000 BC$ | — | — |
| Totem of Undying | 1 | 900 BC$ | — | — |
| Elytra | 1 | 3.200 BC$ | — | — |
| Nether Star | 1 | 3.500 BC$ | — | — |
| Beacon | 1 | 4.000 BC$ | — | — |
| Mace | 1 | 8.000 BC$ | — | — |
| Dragon Egg | 1 | 15.000 BC$ | — | — |

\* **Não existem Admin Shops de buyback/retail ativas no lançamento.** Esses
valores já estão catalogados para uma fase posterior, depois de observarmos o
mercado real. `—` significa P2P somente.

---

## 🌍 Por que não existe loja infinita de tudo

Se o servidor comprasse qualquer quantidade de ferro, ouro, bambu ou cana por
um preço atraente, a economia deixaria de ser comércio e viraria competição de
quem constrói a maior farm automática.

Por isso:

1. recursos renováveis/farmáveis têm referência de buyback baixa;
2. o buyback sistêmico começa **desligado**;
3. itens craftados normalmente não terão recompra automática;
4. itens de progressão permanecem P2P;
5. nenhum `/sellall` será lançado junto com a economia.

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
- limitar quantidade global por loja/transação;
- cobrar criação e aplicar imposto.

O `doctor.sh` impede o primeiro boot se detectar versões esperadas ausentes,
configs econômicos críticos incorretos ou JARs duplicados.

---

## 🟡 Política já desenhada, mas ainda NÃO aplicada

As seguintes ideias estão registradas em `economy.yml`, porém não devem ser
confundidas com recurso já ativo:

| Política futura | Referência atual | Situação |
|---|---:|---|
| Buyback máximo por jogador/dia | 150 BC$ | **aplicado** pelo BigaCore |
| Buyback máximo por jogador/semana | 900 BC$ | **aplicado** pelo BigaCore |
| Teto combinado de moeda nova/dia | 200 BC$ | só documentado |
| Primeiro tier comercial | até 6 lojas | só documentado |
| Tiers futuros | 12 / 20 lojas | só documentado |

O limite de lojas por jogador continua sendo um número decorativo: nada o
aplica. Para valer de verdade ele precisa virar permissão
`ChestShop.shop.limit.<n>` no gerenciador de permissões.

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
o `doctor.sh`, inicia Paper 26.2 com a stack econômica, confirma que Vault,
EternalEconomy, ChestShop e BigaCore carregaram juntos e encerra com `stop`.

Esse teste **não usa nem inicia o mundo real do Felipe**.

---

## Próxima fase econômica

A primeira fase de jogo é propositalmente simples:

```text
250 BC$ inicial
   ↓
ChestShops P2P
   ↓
4% de sink
   ↓
observar preços e produção real
   ↓
criar apenas os Admin Shops que realmente forem necessários
```

Depois de algumas sessões com os amigos, os dados reais devem decidir se
precisamos de buyback limitado, contratos/missões, licenças de comerciante ou
novos sinks. Não o contrário.
