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
```

Com o padrão acima, o centro da loja fica 28 blocos no eixo +X em relação ao
spawn real. A fachada é automaticamente orientada de volta para o spawn.

Isso continua funcionando mesmo depois de levar o mundo para outro computador.

## Segurança antes de construir

Nada é criado quando o servidor liga.

A ordem obrigatória é:

```text
/biga loja local
/biga loja validar
/biga loja criar CONFIRMAR
```

### `/biga loja local`

Mostra:

- coordenadas do spawn real;
- centro proposto da Biga Market;
- direção para a qual a fachada ficará virada.

### `/biga loja validar`

O BigaCore inspeciona os blocos **do mundo real naquele momento**, incluindo:

- desnível do terreno;
- água/líquidos;
- árvores e blocos sólidos acima da superfície;
- blocos com estado próprio, como baús e placas;
- concentração de materiais que parecem construção existente.

A construção é bloqueada se a área parecer insegura.

Se isso acontecer, altere `offset-x`/`offset-z`, rode `/biga reload` e valide de
novo.

### `/biga loja criar CONFIRMAR`

Somente OP/admin pode executar.

O `CONFIRMAR` é proposital: este comando realmente altera blocos do mundo.

## Tamanho e implantação

A área analisada tem aproximadamente **21 blocos de largura por 17 de
profundidade**, além da altura do telhado/chaminé.

O gerador nivela apenas uma área previamente considerada segura. Ele cria uma
fundação de pedra para evitar que a construção fique flutuando em pequenos
desníveis.

Não existe modo `force` no lançamento. Se a validação bloquear a área, a solução
correta é escolher outro offset, não ignorar a proteção.

## Paleta visual

A construção usa principalmente:

- `SPRUCE_PLANKS`;
- `DARK_OAK_PLANKS`;
- `STRIPPED_DARK_OAK_LOG`;
- `STONE_BRICKS`;
- `COBBLESTONE`;
- `POLISHED_ANDESITE` / `ANDESITE`;
- `GLASS_PANE`;
- `LANTERN` + `CHAIN`;
- `BARREL`;
- `CHEST`;
- `BLUE_WOOL` + `WHITE_WOOL` na cobertura lateral;
- `FLOWERING_AZALEA_LEAVES` nos detalhes.

O telhado é alto/escalonado, com madeira escura, gable frontal e pequena
chaminé, seguindo a ideia visual de uma loja medieval de spawn.

## ChestShop

Os baús colocados pelo gerador ficam **vazios e sem Admin Shop automática**.

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

## Arquivo responsável

A construção está implementada em:

```text
plugin/src/main/java/codes/biga/bigacore/SpawnShopBuilder.java
```

Os comandos ficam em:

```text
plugin/src/main/java/codes/biga/bigacore/BigaCommand.java
```

## Limitação importante

O GitHub consegue versionar o **gerador** da construção, mas não consegue saber
como o spawn restaurado ficará visualmente até o servidor abrir o mundo real.
Por isso a validação em runtime é parte obrigatória do design, não uma solução
temporária.
