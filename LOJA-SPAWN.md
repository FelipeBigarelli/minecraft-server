# 🏪 Biga Market — loja física do spawn

A Biga Market é a loja física/área comercial da economia BigaCore.

Ela foi desenhada para combinar com um spawn survival bonito, usando madeira
escura, spruce, pedra, lanternas, barris, vegetação, uma pequena cobertura de
mercado e um canto preparado para ChestShops de jogadores.

## Referência correta do spawn

O mundo (`world/` e `level.dat`) não fica no Git. Por isso o projeto **não grava
uma coordenada absoluta do spawn** em código ou documentação.

Em runtime, o BigaCore usa:

```java
world.getSpawnLocation()
```

e aplica o offset configurado em `plugins/BigaCore/config.yml`:

```yaml
loja-spawn:
  offset-x: 28
  offset-z: 0
  max-desnivel: 3
  max-fundacao: 3
  max-blocos-construidos: 0
```

Com o padrão acima, o centro da loja fica 28 blocos no eixo +X em relação ao
spawn real. A fachada é automaticamente orientada de volta para o spawn.

Isso continua funcionando mesmo depois de levar o mundo para outro computador.

> **Config já existente em runtime**
> Chaves novas não aparecem sozinhas num `plugins/BigaCore/config.yml` antigo.
> Enquanto elas faltarem, o BigaCore usa os padrões embutidos no código, que são
> os mesmos valores mostrados acima. Copie à mão apenas se quiser outro valor.

## Como o piso é calculado (e por que isso já deu errado)

Esta é a parte mais delicada do gerador.

A primeira versão usava `world.getHighestBlockYAt(x, z)` e adotava o **maior**
valor da footprint como piso. Esse método responde pelo heightmap
`MOTION_BLOCKING`, que considera folha e tronco como topo sólido.

Num spawn cercado de floresta de spruce o resultado foi absurdo:

```text
spawn real ................ Y = 71
solo real da footprint .... Y = 60..66
copa de spruce ............ Y = 89
piso escolhido ............ Y = 89   <- copa de árvore
centro proposto ........... Y = 90
```

Quatro colunas de `spruce_leaves` em Y=89 sequestraram o cálculo, e a fundação
teria empilhado cobblestone do chão até 89 em quase 360 colunas.

Nenhum heightmap do Bukkit resolve isso sozinho: `MOTION_BLOCKING_NO_LEAVES`
ainda para no tronco e `OCEAN_FLOOR` ainda para na folha.

Por isso o BigaCore agora **percorre cada coluna de cima para baixo** e
classifica bloco a bloco:

| Classe       | Exemplos                                        | Vira piso? | Bloqueia? |
| ------------ | ----------------------------------------------- | ---------- | --------- |
| `GROUND`     | dirt, podzol, stone, gravel, sand, calcite      | **sim**    | não       |
| `TREE`       | qualquer `Tag.LEAVES` ou `Tag.LOGS`, cogumelo   | nunca      | **sim**   |
| `VEGETATION` | grama, samambaia, flor, muda, neve fina, cipó   | nunca      | não       |
| `LIQUID`     | água, lava                                      | nunca      | **sim**   |
| `TILE_STATE` | baú, placa, fornalha, barril                    | nunca      | **sim**   |
| `MAN_MADE`   | cobblestone, tijolos, tábuas, ruína             | nunca      | **sim**   |

O solo natural é uma **lista positiva**: material desconhecido cai em `MAN_MADE`
e bloqueia a validação, em vez de virar piso por engano.

Cobblestone e stone bricks ficam de fora de propósito — num mundo gerado eles
indicam ruína, pedregulho de taiga ou construção, não terreno.

### Por que a lista é explícita e não vem de `Tag`

A primeira tentativa de correção usou `Tag.DIRT` como fonte do que é solo. Em
Paper 26.2 essa tag contém **apenas três blocos**:

```json
{ "values": ["minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt"] }
```

`grass_block`, `podzol` e `mycelium` não estão nela. O resultado em runtime foi
silencioso e enganoso:

```text
Blocos de construção: 383 (1º: grass_block)      <- grama virou "ruína"
Piso recomendado: Y=65                            <- um bloco abaixo do chão real
```

O conteúdo de uma `Tag` é dado do servidor e **muda entre versões**. Por isso a
allowlist explícita em `GROUND_MATERIALS` é a fonte de verdade, e as `Tag`
entram apenas como bônus para pegar material não enumerado.

`GroundClassificationTest` protege essa lista sem precisar de servidor — `Tag`
exige um `Server` ativo, `Material` não.

## Segurança antes de construir

Nada é criado quando o servidor liga.

A ordem recomendada é:

```text
/biga loja local
/biga loja validar
/biga loja preview
/biga loja criar CONFIRMAR
```

E, se necessário:

```text
/biga loja desfazer CONFIRMAR
```

### `/biga loja local`

Mostra:

- coordenadas do spawn real;
- centro proposto da Biga Market;
- direção para a qual a fachada ficará virada.

### `/biga loja validar`

O BigaCore inspeciona os blocos **do mundo real naquele momento** e devolve um
diagnóstico completo, não apenas SEGURA/BLOQUEADA:

```text
Biga Market — validação
Spawn real: world 176, 71, 64
Centro X/Z: 204, 64
Fachada: west
Footprint: 21x17 (357 colunas)
Solo real min/max: 60..66  (desnível 6)
Topo min/max (com folhagem): 62..89
Colunas com árvore: 232
Colunas com líquido: 16
Colunas com TileState: 0
Blocos de construção: 57 (1º: mossy_cobblestone)
Colunas sem solo: 0
Obstruções no volume: 635
Aterro necessário: 6 bloco(s)
Piso recomendado: Y=66
Status: BLOQUEADA
Motivo: Há líquido em 16 coluna(s) da footprint.
```

As duas linhas que importam são **Solo real** e **Topo**. Quando elas divergem
muito, há floresta em cima — e é exatamente esse buraco que produzia o Y=90.

Motivos de bloqueio, na ordem em que são reportados:

1. colunas sem solo natural (caverna, ravina, vazio);
2. líquido na footprint;
3. árvores na footprint;
4. bloco com estado próprio (baú, placa, fornalha);
5. blocos que parecem construção/estrutura existente;
6. desnível do solo acima de `max-desnivel`;
7. aterro acima de `max-fundacao`;
8. obstrução dentro do volume que a loja ocuparia.

Se a área for bloqueada, altere `offset-x`/`offset-z`, rode `/biga reload` e
valide de novo.

**O BigaCore nunca derruba árvore sozinho.** Se houver floresta na footprint, a
saída correta é outro offset ou limpeza consciente feita à mão.

### `/biga loja preview`

Desenha a loja com **partículas**, sem alterar nenhum bloco:

- verde (`HAPPY_VILLAGER`) — perímetro da footprint;
- laranja (`FLAME`) — linha da fachada;
- branco (`END_ROD`) — cantos e vão da entrada;
- azul (`SOUL_FIRE_FLAME`) — eixo central.

As partículas são enviadas só para quem pediu, expiram sozinhas em ~10 segundos
e não deixam resíduo se o servidor cair no meio.

Serve para conferir com o olho o que a validação afirmou com número — inclusive
se o piso calculado bate com o chão que você está pisando.

### `/biga loja criar CONFIRMAR`

Somente OP/admin pode executar.

O `CONFIRMAR` é proposital: este comando realmente altera blocos do mundo.

Antes de tocar em qualquer bloco ele:

1. **revalida** a área naquele instante e aborta se não estiver `SEGURA`;
2. grava um snapshot de rollback do volume exato que será alterado.

Se a construção lançar exceção no meio, o comando avisa e o snapshot continua
disponível — não há restauração automática.

### `/biga loja desfazer CONFIRMAR`

Restaura o volume salvo pelo snapshot e apaga o arquivo.

O snapshot fica em:

```text
plugins/BigaCore/loja-snapshot.yml
```

Regras:

- guarda **só** a caixa da loja (21x17 da base do solo ao topo do telhado),
  nunca o mundo inteiro;
- guarda `BlockData` completo, então placa, baú e escada voltam com a orientação
  original;
- **nunca sobrescreve** um snapshot existente: se já houver um, `criar` aborta e
  pede para desfazer ou apagar o arquivo antes;
- confere nome e UUID do mundo antes de restaurar;
- nunca restaura sozinho.

**Limitação conhecida:** o conteúdo de inventários não é salvo. Como a loja só é
construída em área validada como vazia (TileState bloqueia a validação), não
deve existir baú com item dentro do volume.

## Tamanho e implantação

A área analisada tem **21 colunas de largura por 17 de profundidade** (357
colunas), mais 12 blocos de altura acima do piso para telhado e chaminé.

A validação e a construção usam **exatamente a mesma geometria e o mesmo solo
medido**: a fundação preenche de `solo real da coluna + 1` até o piso, nunca a
partir do topo da folhagem. Combinado com `max-fundacao`, isso impede o paredão
de pedra que a versão anterior teria criado.

Não existe modo `force` no lançamento. Se a validação bloquear a área, a solução
correta é escolher outro offset, não ignorar a proteção.

## Escolhendo um offset

O padrão `+28, 0` **não é uma recomendação** — é só o valor inicial. Na floresta
ao redor do spawn restaurado ele cai em cima de árvore e água.

O fluxo correto é iterativo:

```text
1. editar loja-spawn.offset-x / offset-z
2. /biga reload
3. /biga loja validar
4. /biga loja preview
5. inspeção humana
6. repetir até a validação passar
7. só então /biga loja criar CONFIRMAR
```

## Paleta visual

A construção usa principalmente:

- `SPRUCE_PLANKS`;
- `DARK_OAK_PLANKS`;
- `STRIPPED_DARK_OAK_LOG`;
- `STONE_BRICKS`;
- `COBBLESTONE`;
- `POLISHED_ANDESITE` / `ANDESITE`;
- `GLASS_PANE`;
- `LANTERN` + `IRON_BARS` como detalhe de suporte;
- `BARREL`;
- `CHEST`;
- `BLUE_WOOL` + `WHITE_WOOL` na cobertura lateral;
- `FLOWERING_AZALEA_LEAVES` nos detalhes.

O telhado é alto/escalonado, com madeira escura, gable frontal e pequena
chaminé, seguindo a ideia visual de uma loja medieval de spawn.

## ChestShop

A Biga Market é hoje a **loja do servidor**: o balcão do fundo e as laterais são
Admin Shops de estoque infinito, com item flutuante sobre cada banca. Veja
[ECONOMIA.md](ECONOMIA.md).

Os baús da varanda continuam **vazios e sem dono**, reservados para ChestShops
de jogador.

Isso é intencional: a economia BigaCore prioriza comércio jogador ↔ jogador.
Depois da construção, os jogadores podem transformar os pontos comerciais em
ChestShops usando seus próprios estoques e preços.

Exemplo:

```text
<linha do dono automática>
16
B 175 : 125 S
IRON_INGOT
```

Veja também [ECONOMIA.md](ECONOMIA.md).

## Arquivos responsáveis

```text
plugin/src/main/java/codes/biga/bigacore/SpawnShopBuilder.java  análise + construção
plugin/src/main/java/codes/biga/bigacore/ShopPreview.java       preview por partículas
plugin/src/main/java/codes/biga/bigacore/ShopSnapshot.java      snapshot e rollback
plugin/src/main/java/codes/biga/bigacore/BigaCommand.java       comandos /biga loja
```

## Limitação importante

O GitHub consegue versionar o **gerador** da construção, mas não consegue saber
como o spawn restaurado ficará visualmente até o servidor abrir o mundo real.
Por isso a validação em runtime é parte obrigatória do design, não uma solução
temporária.
