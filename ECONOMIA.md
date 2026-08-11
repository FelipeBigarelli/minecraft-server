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
| Moeda | **Biga (`B$`)** |
| Casas decimais | **0** |
| Saldo inicial | **250 B$** |
| Taxa ChestShop P2P | **4%** |
| Criar uma loja | **50 B$** |
| Reembolso ao remover | **10 B$** |
| Transações parciais | **desativadas** |
| Auto-sell / sell-all | **não existe no lançamento** |
| Admin Shop ilimitada | **desativada** |
| Buyback do servidor | **desativado no lançamento** |

Os 4% de imposto não vão para outra conta: saem de circulação. Portanto são um
**money sink real**, não apenas dinheiro movido para uma tesouraria.

Não existe pagamento automático por:

- login;
- tempo AFK;
- bloco quebrado;
- mob morto;
- juros sobre saldo.

Uma farm pode produzir muitos **itens**, mas não imprime B$ automaticamente.

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
/biga eco regras
```

`/biga eco preco <item>` mostra:

- lote usado como referência;
- tier econômico;
- preço P2P inicial sugerido;
- possível preço futuro de compra pelo servidor;
- possível preço futuro de venda pelo servidor.

O preço P2P é **referência, não obrigação**. Se duas pessoas quiserem vender um
diamante por 80 B$, 120 B$ ou 200 B$, o mercado continua livre.

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

Exemplo de uma loja de jogador que vende 16 ferros por 175 B$ e compra 16
ferros por 125 B$:

```text

16
B 175 : 125 S
IRON_INGOT
```

Interpretação:

- `B 175`: outro jogador paga 175 B$ para **comprar da loja**;
- `125 S`: outro jogador recebe 125 B$ para **vender para a loja**.

A primeira linha é preenchida pelo ChestShop com o dono.

Os jogadores comuns já recebem pelas permissões padrão do ChestShop acesso a
criar, comprar e vender em lojas. Poderes administrativos continuam restritos a
OP/staff.

Criar a loja custa **50 B$**. Isso é intencional: evita paredes de placas
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
| Cobblestone | 64 | 20 B$ | 8 B$ | 32 B$ |
| Stone | 64 | 28 B$ | 10 B$ | 44 B$ |
| Oak Log | 32 | 48 B$ | 16 B$ | 80 B$ |
| Coal | 32 | 64 B$ | 20 B$ | 100 B$ |
| Iron Ingot | 16 | 160 B$ | 80 B$ | 240 B$ |
| Gold Ingot | 16 | 240 B$ | 96 B$ | 360 B$ |
| Redstone | 64 | 96 B$ | 24 B$ | 160 B$ |
| Diamond | 1 | 120 B$ | 55 B$ | 190 B$ |
| Blaze Rod | 8 | 240 B$ | 32 B$ | 380 B$ |
| Shulker Shell | 1 | 300 B$ | — | — |
| Ancient Debris | 1 | 650 B$ | — | — |
| Netherite Ingot | 1 | 3.000 B$ | — | — |
| Totem of Undying | 1 | 900 B$ | — | — |
| Elytra | 1 | 3.200 B$ | — | — |
| Nether Star | 1 | 3.500 B$ | — | — |
| Beacon | 1 | 4.000 B$ | — | — |
| Mace | 1 | 8.000 B$ | — | — |
| Dragon Egg | 1 | 15.000 B$ | — | — |

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

| Política futura | Referência atual |
|---|---:|
| Buyback máximo por jogador/dia | 150 B$ |
| Buyback máximo por jogador/semana | 900 B$ |
| Teto combinado de moeda nova/dia | 200 B$ |
| Primeiro tier comercial | até 6 lojas |
| Tiers futuros | 12 / 20 lojas |

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
250 B$ inicial
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
