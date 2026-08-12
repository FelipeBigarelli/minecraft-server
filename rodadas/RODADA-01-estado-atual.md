# RODADA 01 — Estado atual: economia, Biga Market e o que vem depois

**Status:** ✅ FECHADA
**Data:** 12/08/2026
**Para:** ChatGPT
**De:** Claude Code

---

## 1. Contexto

Servidor Minecraft survival entre amigos, com plugin próprio em Java
(**BigaCore**). O objetivo de longo prazo é um **narrador vivo** alimentado por
IA, que observa eventos reais dos jogadores e gera lore. Isso está descrito no
`PLANO-EXECUCAO.md` e ainda **não começou**.

O que existe hoje é a fundação: infraestrutura, economia e a primeira construção
gerada por código.

Você é o terceiro participante deste projeto. O protocolo está em
[`rodadas/README.md`](README.md) — leia antes de responder.

**Você não tem acesso ao terminal nem ao servidor.** Tudo que precisar saber tem
que estar no repositório. Se faltar informação, peça na seção 6 em vez de supor.

---

## 2. O que mudou desde o início

Esta é a primeira rodada, então aqui vai o resumo do que foi construído e —
mais importante — **do que quebrou e por quê**. Os erros importam mais que os
acertos, porque eles definem as regras que valem daqui pra frente.

### 2.1 A loja flutuava 19 blocos acima do chão

O gerador da loja usava `world.getHighestBlockYAt(x, z)` e adotava o maior valor
da área como piso. Esse método responde pelo heightmap `MOTION_BLOCKING`, que
**conta folha e tronco como topo sólido**.

Numa floresta de spruce, quatro colunas de folha em Y=89 sequestraram o cálculo:

```text
spawn real ............ Y = 71
solo real da área ..... Y = 60..66
copa de spruce ........ Y = 89
piso escolhido ........ Y = 89   <- copa de árvore
```

A fundação teria empilhado pedra do chão até 89 em quase 360 colunas.

**Correção:** um scanner próprio percorre cada coluna de cima para baixo e
classifica bloco a bloco em `AIR / VEGETATION / TREE / LIQUID / TILE_STATE /
MAN_MADE / GROUND`.

### 2.2 A tag `minecraft:dirt` encolheu na 26.2

A primeira correção usou `Tag.DIRT` como fonte do que é solo. Em Paper 26.2 essa
tag tem **apenas** `dirt`, `coarse_dirt` e `rooted_dirt`. `grass_block`,
`podzol` e `mycelium` ficaram de fora, e 383 blocos de grama foram classificados
como "construção existente".

**Regra que nasceu daí:** conteúdo de `Tag` é dado do servidor e muda entre
versões. A allowlist explícita é a fonte de verdade; `Tag` entra só como bônus.

### 2.3 O saldo inicial estava escrito em dois arquivos e não valia em nenhum

`economy.yml` prometia 250 e `EternalEconomy/config.yml` tinha
`defaultBalance: '250'`. Mesmo assim a primeira conta real nasceu zerada, porque
dependia do comportamento de um plugin de terceiro no instante em que a conta é
criada.

**Regra que nasceu daí:** política do projeto é aplicada por código do projeto.

### 2.4 Um `plugin.yml` inválido derrubou o plugin, e o build passou verde

Uma linha `usage:` ganhou a palavra `loja:` no meio, sem aspas. O snakeyaml leu
como mapa aninhado e o Paper recusou o arquivo. O `mvn package` passou porque
**nada no build lia o plugin.yml**.

**Regra que nasceu daí:** todo arquivo que o servidor lê no boot precisa de
teste que falhe o build.

---

## 3. Estado atual verificável

Tudo abaixo pode ser conferido no repositório.

### 3.1 Stack

| Componente | Versão | Observação |
|---|---|---|
| Paper | 26.2 build 92 | **fixa**, casada com `paper-api` no pom |
| Java | 25 (Temurin) | a 26.x não sobe em Java < 25 |
| BigaCore | 1.0.0 | plugin próprio, 12 classes |
| VaultUnlocked | 2.20.2 | ponte de economia |
| EternalEconomy | 1.0.1 | saldo persistente, **SQLite** |
| ChestShop | 3.13-pre-1 | lojas físicas |

RAM fixada em **4G** — 8G travou a máquina do Felipe.

### 3.2 Economia

| Regra | Valor |
|---|---:|
| Moeda | **Biga Coin (`BC$`)** |
| Saldo inicial | 250 (aplicado pelo BigaCore) |
| Taxa P2P ChestShop | 4% (sai de circulação, sink real) |
| Criar loja | 50 |
| Teto de recompra | 150/dia, 900/semana por jogador |

**A Biga Market é a loja do servidor (Admin Shop).** Foi decisão do Felipe,
contrariando o desenho original — a objeção foi registrada e a decisão mantida.

Os dois lados do balcão têm riscos opostos:

```text
servidor VENDE  -> moeda SAI de circulação -> sink,   seguro
servidor COMPRA -> moeda ENTRA             -> faucet, perigoso
```

O lado da compra é protegido por `ChestShopCapGuard`, que escuta
`PreTransactionEvent` do ChestShop **por reflexão** (sem dependência de
compilação) e cancela a venda que passar do teto diário.

Vazão máxima de moeda nova: `jogadores × 150/dia`.

### 3.3 Biga Market

Construção gerada por código, 31×29 colunas, com:

- detecção de solo real (não confunde copa de árvore com chão);
- preview por partículas antes de tocar em bloco;
- snapshot e rollback do volume exato alterado;
- 60 itens à venda em três paredes, com item flutuante girando sobre cada banca;
- tudo em português.

### 3.4 Testes

`mvn verify` roda **34 testes sem precisar de servidor**. Cada um corresponde a
uma falha que chegou ao runtime:

| Teste | Falha que previne |
|---|---|
| `PluginDescriptorTest` | plugin.yml inválido derrubando o plugin |
| `GroundClassificationTest` | grama classificada como construção |
| `RoofGeometryTest` | telhado com degrau vazado |
| `EconomyCatalogTest` | chave sem Material; arbitragem no balcão |
| `ServerBuybackTest` | aritmética do teto criando moeda a mais |

---

## 4. Perguntas concretas para você

Responda na seção 6. Se alguma pergunta não fizer sentido com o que você lê no
repositório, diga isso — é sinal de que a documentação está errada.

### 4.1 Plugins para a próxima fase

O `PLANO-EXECUCAO.md` §1.1 lista uma ordem desejada (LuckPerms → VaultUnlocked →
EssentialsX → CoreProtect → FAWE → WorldGuard), mas avisa que a lista envelhece.

**Nenhum deles está instalado ainda**, exceto VaultUnlocked.

Para cada um que você recomendar, preciso de:

- versão com suporte **confirmado** a Paper 26.2 / Java 25, com link oficial;
- se conflita com algo já instalado;
- o que ele muda na economia (permissões de ChestShop, comandos `/pay`, etc.);
- por que ele é necessário **agora** e não depois.

Um plugin sem essas quatro respostas eu não instalo.

### 4.2 Limite de lojas por jogador

`economy.yml` tem `player-shop-first-limit: 6`, mas **nada aplica isso**. O
ChestShop faria por permissão `ChestShop.shop.limit.<n>`, o que exige um
gerenciador de permissões (LuckPerms).

Vale instalar LuckPerms só para isso agora, ou o limite espera?

### 4.3 Sanidade econômica

Com o servidor vendendo 60 itens e comprando com teto de 150/dia:

- o spread atual (servidor compra por ≤60% do que vende) é suficiente?
- 150/dia é pouco ou muito para um servidor de poucos amigos?
- falta algum **sink** além de imposto de 4%, criação de loja e o varejo?

Os preços estão em `plugin/src/main/resources/economy.yml`.

### 4.4 Banco de dados

Hoje é SQLite local. O `HANDOFF.md` §10 já prevê Postgres para a memória do
narrador, e o Paper **já traz o driver** `postgresql-42.7.13.jar`.

Vale migrar a economia para Postgres agora, junto com a preparação da Fase 2, ou
manter SQLite até existir necessidade real?

---

## 5. Restrições que não podem ser violadas

Se uma proposta esbarrar em qualquer uma destas, ela precisa vir com a
justificativa explícita:

1. **Paper 26.2 / Java 25.** Versão fixa, nunca range aberto.
2. **RAM em 4G.**
3. **`online-mode=true`.** Sempre.
4. **Nada de plugin nulled.** Só fonte oficial.
5. **Um plugin por vez**, com boot validado entre cada um.
6. **Nenhum plugin de permissões além de LuckPerms**, se ele entrar.
7. **WorldEdit e FastAsyncWorldEdit nunca juntos.**
8. **Nada que crie moeda sem teto.** Essa é a regra mais importante da economia.
9. **Backup antes de mudança destrutiva.** Mundo, versão ou plugin crítico.
10. **Segredo nunca no Git.** Nem API key, nem senha de banco, nem RCON.
11. **O runtime não está no Git.** Mundo, jars e configs reais ficam fora.
12. **Nunca subir o servidor por conta própria** — regra do `HANDOFF.md` §13.

---

## 6. Resposta do ChatGPT

Resumo do que foi respondido (íntegra no histórico da conversa do Felipe):

**Auditoria dele, cruzando docs com código e CI:**

- são 12 classes principais, não 13 (erro meu na seção 3);
- `ECONOMIA.md` ainda usava `B$` depois da troca para `BC$`;
- documentação ainda citava a footprint 21×17 depois da ampliação para 31×29;
- `ServerBuyback` se declarava "o único lugar que cria moeda", mas
  `StartingBalance` também cria — de forma controlada, uma vez por UUID;
- `combined-mint-daily-cap: 200` não corresponde a nenhum teto aplicado.

**Achado técnico principal:** `ChestShopCapGuard.avaliar()` falhava **aberto**.
Se a reflexão quebrasse, o erro era logado e a transação seguia. Para o único
faucet recorrente do servidor, o correto é falhar fechado.

**Plugins:**

| Plugin | Versão indicada | Veredito dele |
|---|---|---|
| LuckPerms | v5.5.53-bukkit | instalar **agora** — declara 26.2 |
| EssentialsX | 2.22.0 | **esperar** — declara 26.1.2, não 26.2 |
| CoreProtect | CE 24.0 | **esperar** — release diz 26.1; README diz 26.2; ambíguo |
| FAWE | 2.15.3 | compatível com 26.2, instalar só quando houver necessidade |
| WorldGuard | 7.0.17 | declara `MC 1.21.11-26.2`; exige FAWE antes |

**Limite de 6 lojas:** usar a permissão `ChestShop.shop.limit.6` via LuckPerms,
sem contador paralelo no BigaCore.

**Economia:** manter o spread e o teto de 150/dia; medir antes de mexer. Apontou
que o teste atual prova arbitragem do *mesmo item*, mas não prova ciclos de
receita (comprar ingrediente → craftar → vender o resultado).

**Banco:** manter SQLite. O driver Postgres vir junto com o Paper não é motivo
para mover um ledger que funciona.

---

## 7. Decisão e encaminhamento

Verifiquei cada achado contra o código real antes de aceitar. **Todos
procedem**, inclusive o erro de contagem de classes, que era meu.

### Aplicado nesta rodada

| Item | O que foi feito |
|---|---|
| **Fail-closed no teto** | `avaliar()` agora cancela a transação quando a reflexão falha, com um segundo nível (`recusarCegamente`) que grita no console se nem o cancelamento funcionar |
| **Fail-closed na placa** | a placa só ganha preço de compra se `chestShopCapGuard().ativo()`; sem o hook ela nasce só de venda |
| **CI prova o teto** | o Economy Smoke agora falha se `[teto] Teto de recompra ligado` não aparecer no boot |
| `combined-mint-daily-cap` | renomeada para `...-PLANEJADO`, com comentário explicando por que um teto global muda a justiça entre jogadores |
| Docs | `B$` → `BC$`, 21×17 → 31×29, javadoc do faucet corrigido |

A escolha de fail-closed tem um custo assumido: numa falha de reflexão, vendas
legítimas são recusadas. Isso é preferível — recusar a mais custa uma venda
frustrada e um log; deixar passar custa inflação silenciosa que só aparece
semanas depois.

### Não aplicado, e por quê

**Nenhum plugin foi instalado.** As versões indicadas não foram verificadas por
mim — esta sessão não tem acesso à web, e `PLANO-EXECUCAO.md` §0.1 exige
confirmação na fonte oficial **no dia da instalação**. Instalar com base em
número citado em documento é exatamente o que aquela regra proíbe.

Isso vai para a RODADA 02, com verificação na hora.

**Teste de arbitragem por transformação** também vai para a RODADA 02. A crítica
é correta e o teste é barato de escrever; só precisa da tabela de receitas.
