# Biga Market — correção da detecção de solo — 11/08/2026

## Objetivo

Auditar por que `/biga loja local` propôs `world 204, 90, 64` com o spawn real em
`world 176, 71, 64`, corrigir o cálculo de terreno e adicionar preview e rollback
— **sem construir nada no mundo**.

## Estado auditado

| Item | Valor confirmado |
| ---- | ---------------- |
| Branch | `main`, limpa, sincronizada com `origin/main` |
| `DEFAULT_RAM` do runtime | `4G` (inalterado) |
| Paper | 26.2 build 92 (não atualizado) |
| Java | Temurin 25 |
| Mundo | restaurado em `world/dimensions/minecraft/overworld` |
| Servidor | permaneceu **desligado** durante toda a sessão |

---

## Diagnóstico: a origem exata do Y=90

O `level.dat` e os arquivos de região foram lidos diretamente (somente leitura,
com um parser NBT descartável) para não depender de suposição.

### Cadeia causal

`SpawnShopBuilder.analyze()` fazia:

```java
int surfaceY = world.getHighestBlockYAt(block.getX(), block.getZ());
maxSurfaceY  = Math.max(maxSurfaceY, surfaceY);
int floorY   = maxSurfaceY;
Location center = new Location(world, centerX + 0.5, floorY + 1.0, centerZ + 0.5);
```

`World#getHighestBlockYAt(x, z)` responde pelo heightmap `MOTION_BLOCKING`, que
**conta folha e tronco como topo sólido**.

### Medição real da footprint (X 195..211, Z 54..74 — 357 colunas)

```text
MOTION_BLOCKING min/max ....... 62 .. 89
solo real min/max ............. 60 .. 66
colunas com árvore ............ 232 de 357  (65%)
colunas com líquido ...........  16
blocos de construção .......... 57 (pedregulho de taiga, mossy_cobblestone)
```

As quatro colunas que fixaram o máximo:

```text
x=207 z=65   topo = spruce_leaves @ 89   solo = mossy_cobblestone @ 64
x=207 z=66   topo = spruce_leaves @ 89   solo = mossy_cobblestone @ 65
x=208 z=65   topo = spruce_leaves @ 89   solo = mossy_cobblestone @ 65
x=208 z=66   topo = spruce_leaves @ 89   solo = mossy_cobblestone @ 65
```

Logo:

```text
floorY   = max(MOTION_BLOCKING) = 89   <- copa de spruce
centro Y = floorY + 1           = 90
```

Bate exatamente com `world 204, 90, 64` observado no jogo. **O Y=90 era a copa
da floresta, 25 blocos acima do chão.**

### Dano que isso teria causado

`prepareFoundation()` preenchia de `getHighestBlockYAt() + 1` até `floorY`. Numa
coluna de grama com topo em 62 e piso em 89, isso são **27 blocos de cobblestone
empilhados** — repetido em quase 360 colunas. Um monólito de pedra flutuando
19 blocos acima do spawn.

`countObstructions()` também varria a partir de `getHighestBlockYAt() + 1`, ou
seja, só enxergava o que estava **acima** do topo. Um tronco inteiro passava
despercebido.

> A validação antiga bloqueava por sorte, pelo desnível aparente de 27 blocos.
> Mas `/biga loja local` continuava anunciando `204, 90, 64` como centro válido.

---

## Correções aplicadas

### 1. Scanner de coluna próprio

Nenhum heightmap do Bukkit resolve isso sozinho: `MOTION_BLOCKING_NO_LEAVES`
ainda para no tronco e `OCEAN_FLOOR` ainda para na folha.

Cada coluna passou a ser percorrida de cima para baixo, classificando bloco a
bloco em `AIR / VEGETATION / TREE / LIQUID / TILE_STATE / MAN_MADE / GROUND`.

- folha e tronco (`Tag.LEAVES`, `Tag.LOGS`) **nunca** viram chão;
- solo natural é uma **lista positiva** (`Tag.DIRT`, `Tag.BASE_STONE_OVERWORLD`
  + areia, cascalho, argila, calcita…); material desconhecido cai em `MAN_MADE`
  e **bloqueia**, em vez de virar piso por engano;
- cobblestone e stone bricks ficaram fora da lista de solo de propósito — num
  mundo gerado indicam ruína ou pedregulho, não terreno;
- o teto de varredura usa o heightmap com `+1` de folga, então o resultado não
  depende de o Bukkit devolver "último bloco cheio" ou "primeiro espaço livre".

### 2. Fundação e obstrução usam o solo medido

`prepareFoundation()` e a contagem de obstruções agora partem do **solo real de
cada coluna**, não do topo da folhagem. A validação e a construção compartilham
exatamente a mesma varredura.

### 3. Novo limite `max-fundacao`

Trava explícita contra o paredão, independente do desnível.

### 4. Diagnóstico detalhado em `/biga loja validar`

Separa **solo real** de **topo com folhagem** — a divergência entre os dois é
justamente o sintoma que produzia o Y=90. Reporta árvores, líquidos, TileStates,
blocos de construção (com o nome do primeiro achado), colunas sem solo, aterro
necessário e piso recomendado.

### 5. `/biga loja preview`

Partículas enviadas só ao jogador que pediu, expiram em ~10s, **zero blocos
alterados**.

### 6. `/biga loja desfazer CONFIRMAR`

Snapshot do volume exato antes de construir (~5.7 mil blocos, não o mundo
inteiro), com `BlockData` completo. Não sobrescreve snapshot existente, confere
mundo/UUID e nunca restaura sozinho.

### 7. `Sign.line()` deprecado

Trocado por `getSide(Side.FRONT)`. Build ficou sem warnings.

### 8. Teste de regressão do `plugin.yml`

No primeiro boot com o JAR novo o BigaCore **não carregou**:

```text
InvalidDescriptionException: Invalid plugin.yml
Caused by: mapping values are not allowed here
 in 'reader', line 24, column 52
```

A linha de `usage` tinha ganhado a palavra `loja:` no meio da frase, sem aspas.
O snakeyaml leu os dois-pontos como início de mapa aninhado e o Paper recusou o
arquivo inteiro. O `mvn package` passou verde porque **nada no build lia o
plugin.yml** — ele era só um resource copiado.

Correções:

- `usage` passou a ser string entre aspas;
- adicionado `PluginDescriptorTest`, que carrega o `plugin.yml` já filtrado com
  o **próprio `PluginDescriptionFile` do Paper** — o mesmo caminho de código do
  boot — e confere nome, versão, `api-version`, classe principal, comando `biga`
  e a permissão `bigacore.admin`;
- `maven-surefire-plugin` subiu para 3.5.2, porque o 2.12.4 herdado do super-POM
  não enxerga JUnit 5 e reportava "No tests to run".

Verificado nos dois sentidos: com o YAML quebrado o `mvn verify` falha (5 erros);
com o YAML correto passa.

### 9. `Tag.DIRT` não cobre grama nem podzol em 26.2

O primeiro `/biga loja validar` real rodou e entregou quase tudo certo — mas com
duas linhas erradas:

```text
Blocos de construção: 383 (1º: grass_block)   (esperado: 57, mossy_cobblestone)
Piso recomendado: Y=65                        (esperado: Y=66)
```

Causa, confirmada lendo `data/minecraft/tags/block/dirt.json` dentro do
`paper-26.2.jar`:

```json
{ "values": ["minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"] }
```

`grass_block`, `podzol` e `mycelium` **saíram da tag**. Como a detecção de solo
usava `Tag.DIRT` como fonte principal, esses blocos caíram no ramo final
`MAN_MADE`. O scanner então descia mais um bloco até o `dirt` de baixo — daí o
piso um nível abaixo do chão real.

Os cogumelos pequenos tinham o mesmo problema: não estão em
`minecraft:replaceable` nem em `minecraft:flowers`.

Correção estrutural: a allowlist virou um `EnumSet<Material>` explícito
(`GROUND_MATERIALS`), e as `Tag` passaram a ser apenas um complemento. O
conteúdo de uma Tag é dado do servidor e muda entre versões; a lista explícita
não.

Todas as tags usadas foram auditadas contra o JSON real do jar:

| Tag | Situação em 26.2 |
| --- | --- |
| `DIRT` | **encolheu** — só dirt/coarse_dirt/rooted_dirt |
| `BASE_STONE_OVERWORLD` | ok — stone, granite, diorite, andesite, tuff, deepslate |
| `LEAVES` / `LOGS` | ok |
| `REPLACEABLE` / `REPLACEABLE_BY_TREES` | ok (contêm água/folha, mas líquido e árvore são testados antes) |
| `FLOWERS` / `SAPLINGS` / `CROPS` / `CAVE_VINES` / `WOOL_CARPETS` | ok |

Adicionado `GroundClassificationTest` (7 testes), que roda **sem servidor** —
`Material` é um enum comum, `Tag` exigiria um `Server` ativo.

---

## Resultado com o código corrigido

Previsão calculada sobre o mundo real, no offset atual `+28, 0`:

```text
Solo real min/max ........... 60..66  (desnível 6)
Topo min/max (folhagem) ..... 62..89
Piso recomendado ............ Y=66     <- era Y=89
Status ...................... BLOQUEADA
Motivo ...................... Há líquido em 16 coluna(s) da footprint.
```

O piso saiu de 89 para 66 e a área é corretamente recusada.

## Escolha de offset

Uma varredura de 625 offsets ao redor do spawn (passo 1 bloco) encontrou
**exatamente um** que passa em todos os critérios com `max-desnivel: 3`:

```text
offset-x: -6
offset-z: 37
-> centro X/Z 170, 101   piso Y=68   fachada north
   0 árvore, 0 líquido, 0 TileState, 0 construção, aterro 3
```

Relaxando `max-desnivel` para 4, aparecem ~16 opções na mesma clareira ao norte.

O default do config **continua `+28, 0`**: a escolha do local é decisão humana,
não do algoritmo.

---

## Verificação

```text
mvn clean package ................ BUILD SUCCESS, 0 warnings
mvn verify ....................... BUILD SUCCESS, 12/12 testes
bash -n server/scripts/*.sh ...... OK
teste de restore seguro .......... OK
py_compile exportar.py ........... OK
```

O **Economy Smoke** não foi executado localmente de propósito: ele sobe uma
instância do Paper, e a regra da sessão proibia subir um segundo servidor. Ele
roda normalmente no GitHub Actions, num diretório descartável.

## Limites respeitados

- nenhum bloco alterado no mundo;
- `/biga loja criar CONFIRMAR` **não** foi executado;
- servidor nunca iniciado, nenhum `kill -9`, nenhum `/reload confirm`;
- Paper não atualizado, RAM mantida em 4G;
- economia (Vault/EternalEconomy/ChestShop) intocada.
