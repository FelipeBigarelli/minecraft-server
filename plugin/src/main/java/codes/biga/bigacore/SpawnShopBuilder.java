package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Gera a loja física da economia em posição relativa ao spawn real do mundo.
 *
 * A construção nunca roda automaticamente. O admin primeiro inspeciona o plano
 * com /biga loja validar e /biga loja preview e só depois executa
 * /biga loja criar CONFIRMAR.
 *
 * O mundo não fica no Git, então coordenadas absolutas seriam frágeis. Em vez
 * disso, a loja usa o spawn retornado pelo próprio Bukkit em runtime e um offset
 * configurável no config.yml. Assim a mesma versão do BigaCore funciona após
 * restaurar o mundo em outra máquina.
 *
 * <h2>Por que existe um scanner de coluna próprio</h2>
 *
 * A versão anterior usava {@code world.getHighestBlockYAt(x, z)} e adotava o
 * MAIOR valor da footprint como piso. Esse método responde pelo heightmap
 * MOTION_BLOCKING, que conta folhas e troncos como topo. Numa floresta de
 * spruce o topo da copa vira "superfície": no mundo restaurado o spawn está em
 * Y=71, o solo real da footprint fica entre Y=60 e Y=66, mas quatro colunas de
 * spruce_leaves em Y=89 puxavam o piso para 89 — e o centro para Y=90.
 *
 * Nenhum heightmap do Bukkit resolve isso sozinho: MOTION_BLOCKING_NO_LEAVES
 * ainda para no tronco e OCEAN_FLOOR ainda para na folha. Por isso cada coluna
 * é percorrida de cima para baixo classificando bloco a bloco até achar solo
 * natural de verdade.
 */
public final class SpawnShopBuilder {

    // ------------------------------------------------------------------
    //  Geometria — um lugar só
    //
    //  Tudo abaixo é derivado destes quatro números. A versão anterior
    //  espalhava -8, 8, 2 e 12 por dez métodos, e por isso não dava para
    //  ampliar a loja sem caçar constante solta.
    // ------------------------------------------------------------------

    /** Metade da largura da footprint. 15 => 31 colunas de largura. */
    static final int HALF_WIDTH = 15;

    /** Profundidade da footprint: de MIN_V (frente) a MAX_V (fundo). */
    static final int MIN_V = -4;
    static final int MAX_V = 24;

    /** Metade da largura do salão fechado. */
    static final int WALL_HALF = HALF_WIDTH - 2;

    /** Parede da frente e parede do fundo, em v. */
    static final int FRONT_V = 3;
    static final int BACK_V = MAX_V - 2;

    /** Altura das paredes acima do piso. */
    static final int WALL_HEIGHT = 6;

    /** Metade do vão do telhado, em colunas. */
    static final int ROOF_HALF_SPAN = HALF_WIDTH - 1;

    /** Primeira e última fileira do telhado, em v. */
    static final int ROOF_MIN_V = MIN_V + 2;
    static final int ROOF_MAX_V = MAX_V - 1;

    /** Altura do envelope construído acima do piso (telhado + chaminé). */
    static final int BUILD_HEIGHT = roofHeight(0, 0) + 5;

    /** Meio da footprint no eixo de profundidade. */
    static final int CENTER_V = (MIN_V + MAX_V) / 2;

    /** Vão livre da entrada, para cada lado do centro. */
    static final int DOOR_HALF = 3;

    /** Quantos blocos descer procurando solo antes de desistir da coluna. */
    private static final int MAX_COLUMN_DEPTH = 96;

    /**
     * Solo natural, listado explicitamente.
     *
     * <h2>Por que não confiar só nas Tags</h2>
     *
     * A primeira versão desta classe usava {@code Tag.DIRT} como fonte
     * principal. Em Paper 26.2 essa tag contém apenas:
     *
     * <pre>minecraft:dirt, minecraft:coarse_dirt, minecraft:rooted_dirt</pre>
     *
     * {@code grass_block}, {@code podzol} e {@code mycelium} ficaram de fora.
     * Resultado em runtime: 383 blocos de grama e podzol foram classificados
     * como "construção" e o piso saiu um bloco abaixo do correto.
     *
     * O conteúdo de uma Tag é dado do servidor e muda entre versões. A
     * allowlist explícita é a fonte de verdade; as Tags entram só como bônus,
     * para pegar material que não está enumerado aqui.
     */
    private static final Set<Material> GROUND_MATERIALS = EnumSet.of(
            // A minecraft:dirt de 26.2 não cobre estes três.
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.MYCELIUM,

            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.DIRT_PATH,
            Material.MUD,
            Material.PACKED_MUD,
            Material.MUDDY_MANGROVE_ROOTS,
            Material.MOSS_BLOCK,
            Material.PALE_MOSS_BLOCK,

            Material.STONE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.TUFF,
            Material.DEEPSLATE,
            Material.CALCITE,
            Material.BASALT,
            Material.BLACKSTONE,

            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.CLAY,
            Material.SANDSTONE,
            Material.RED_SANDSTONE,
            Material.TERRACOTTA,
            Material.SNOW_BLOCK
    );

    /**
     * Vegetação que as Tags de 26.2 não cobrem.
     *
     * Os cogumelos pequenos são o caso real: não estão em
     * {@code minecraft:replaceable} nem em {@code minecraft:flowers}, então
     * sem esta lista virariam "construção" e bloqueariam a validação.
     */
    private static final Set<Material> VEGETATION_MATERIALS = EnumSet.of(
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.MOSS_CARPET,
            Material.PALE_MOSS_CARPET,
            Material.SWEET_BERRY_BUSH,
            Material.BIG_DRIPLEAF,
            Material.SMALL_DRIPLEAF,
            Material.LILY_PAD,
            Material.BAMBOO,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.POWDER_SNOW
    );

    /** Cogumelo gigante: bloco de árvore, nunca chão. */
    private static final Set<Material> TREE_MATERIALS = EnumSet.of(
            Material.MUSHROOM_STEM,
            Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK
    );

    // As três listas acima são testáveis sem servidor: são só constantes de
    // Material. As Tags, não — elas só existem com um Server ativo. Por isso a
    // allowlist é a fonte de verdade e é ela que o teste protege.

    static Set<Material> groundMaterials() {
        return Collections.unmodifiableSet(GROUND_MATERIALS);
    }

    static Set<Material> vegetationMaterials() {
        return Collections.unmodifiableSet(VEGETATION_MATERIALS);
    }

    static Set<Material> treeMaterials() {
        return Collections.unmodifiableSet(TREE_MATERIALS);
    }

    private static final int DEFAULT_OFFSET_X = 28;
    private static final int DEFAULT_OFFSET_Z = 0;
    private static final int DEFAULT_MAX_SLOPE = 3;
    private static final int DEFAULT_MAX_FOUNDATION = 3;
    private static final int DEFAULT_MAX_MAN_MADE = 0;

    private final BigaCore plugin;

    public SpawnShopBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Análise
    // ------------------------------------------------------------------

    public Analysis analyze(World world) {
        Location spawn = world.getSpawnLocation();

        if (world.getEnvironment() != World.Environment.NORMAL) {
            return Analysis.invalid(spawn, "A loja do spawn só pode ser planejada no Overworld.");
        }

        int offsetX = plugin.getConfig().getInt("loja-spawn.offset-x", DEFAULT_OFFSET_X);
        int offsetZ = plugin.getConfig().getInt("loja-spawn.offset-z", DEFAULT_OFFSET_Z);
        int maxSlope = plugin.getConfig().getInt("loja-spawn.max-desnivel", DEFAULT_MAX_SLOPE);
        int maxFoundation = plugin.getConfig().getInt(
                "loja-spawn.max-fundacao", DEFAULT_MAX_FOUNDATION);
        int maxManMade = plugin.getConfig().getInt(
                "loja-spawn.max-blocos-construidos", DEFAULT_MAX_MAN_MADE);
        boolean limparArvores = plugin.getConfig().getBoolean(
                "loja-spawn.limpar-arvores", false);

        if (offsetX == 0 && offsetZ == 0) {
            return Analysis.invalid(spawn,
                    "O offset da loja não pode ser 0,0 porque isso pisaria no spawn.");
        }

        int centerX = spawn.getBlockX() + offsetX;
        int centerZ = spawn.getBlockZ() + offsetZ;
        BlockFace frontFace = frontFace(offsetX, offsetZ);
        Layout layout = new Layout(centerX, centerZ, frontFace);

        FootprintSurvey survey = surveyFootprint(world, layout);
        if (survey.groundColumns() == 0) {
            return Analysis.invalid(spawn,
                    "Nenhuma coluna da footprint tem solo natural identificável.");
        }

        int floorY = survey.maxGroundY();
        int slope = survey.maxGroundY() - survey.minGroundY();
        int foundationDepth = floorY - survey.minGroundY();
        int obstructions = countEnvelopeObstructions(world, layout, survey, floorY, limparArvores);

        Location center = new Location(world, centerX + 0.5, floorY + 1.0, centerZ + 0.5);
        String reason = blockingReason(survey, slope, maxSlope, foundationDepth,
                maxFoundation, maxManMade, obstructions, limparArvores);

        boolean safe = reason == null;
        String message = reason;
        if (safe) {
            message = "Área compatível com a construção.";
        }

        return new Analysis(true, safe, message, spawn.clone(), center, frontFace,
                floorY, survey, obstructions, foundationDepth, slope);
    }

    /**
     * Primeiro motivo de bloqueio, ou null se a área passar.
     *
     * Cada regra fica num if próprio e retorna cedo: nada de ternário aninhado,
     * e a ordem das checagens é a ordem de gravidade que o admin deve resolver.
     */
    private String blockingReason(FootprintSurvey survey, int slope, int maxSlope,
                                  int foundationDepth, int maxFoundation,
                                  int maxManMade, int obstructions,
                                  boolean limparArvores) {

        if (survey.columnsWithoutGround() > 0) {
            return survey.columnsWithoutGround()
                    + " coluna(s) não têm solo natural abaixo (caverna, ravina ou vazio).";
        }
        if (survey.liquidColumns() > 0) {
            return "Há líquido em " + survey.liquidColumns()
                    + " coluna(s) da footprint (água ou lava).";
        }
        if (survey.treeColumns() > 0 && !limparArvores) {
            return "Árvores ocupam " + survey.treeColumns() + " de "
                    + survey.totalColumns() + " colunas. Escolha outro offset,"
                    + " limpe a área à mão, ou ligue loja-spawn.limpar-arvores"
                    + " para autorizar a derrubada.";
        }
        if (survey.tileStateColumns() > 0) {
            return "Há bloco com estado próprio (baú, placa, fornalha...) em "
                    + survey.tileStateColumns() + " coluna(s): parece construção habitada.";
        }
        if (survey.manMadeBlocks() > maxManMade) {
            return "Há " + survey.manMadeBlocks() + " bloco(s) que parecem construção/estrutura"
                    + " existente (máximo tolerado: " + maxManMade + "). Primeiro achado: "
                    + survey.firstManMadeName() + ".";
        }
        if (slope > maxSlope) {
            return "Desnível real do solo é " + slope + " bloco(s) (Y "
                    + survey.minGroundY() + ".." + survey.maxGroundY()
                    + "), acima do máximo de " + maxSlope + ".";
        }
        if (foundationDepth > maxFoundation) {
            return "A fundação precisaria de " + foundationDepth
                    + " bloco(s) de aterro (máximo: " + maxFoundation
                    + "). Isso viraria um paredão em vez de nivelamento.";
        }
        if (obstructions > 0) {
            return "Há " + obstructions + " bloco(s) não removível(is) dentro do volume"
                    + " que a loja ocuparia.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Varredura de colunas — o coração da correção
    // ------------------------------------------------------------------

    private FootprintSurvey surveyFootprint(World world, Layout layout) {
        int depth = MAX_V - MIN_V + 1;
        int width = HALF_WIDTH * 2 + 1;
        int[] ground = new int[width * depth];
        int[] top = new int[width * depth];

        FootprintSurvey.Builder builder = new FootprintSurvey.Builder(width, depth, ground, top);

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Block probe = layout.block(world, u, v, world.getMinHeight());
                scanColumn(world, probe.getX(), probe.getZ(), index(u, v, depth), builder);
            }
        }

        return builder.build();
    }

    /**
     * Percorre uma coluna do topo para baixo separando solo de tudo que não é solo.
     *
     * O ponto de partida vem do heightmap só como teto de varredura, com +1 de
     * folga: assim o resultado não depende de o Bukkit devolver "último bloco
     * cheio" ou "primeiro espaço livre".
     */
    private void scanColumn(World world, int x, int z, int slot, FootprintSurvey.Builder builder) {
        int ceiling = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
        int maxY = Math.min(ceiling, world.getMaxHeight() - 1);
        int minY = Math.max(world.getMinHeight(), maxY - MAX_COLUMN_DEPTH);

        int topY = Integer.MIN_VALUE;
        boolean tree = false;
        boolean liquid = false;
        boolean tileState = false;

        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            BlockRole role = roleOf(block);

            if (role == BlockRole.AIR) {
                continue;
            }
            if (topY == Integer.MIN_VALUE) {
                topY = y;
            }

            if (role == BlockRole.GROUND) {
                builder.column(slot, y, topY, tree, liquid, tileState);
                return;
            }

            // Tudo daqui para baixo é "não-solo acima do solo": conta como
            // ocupação da área, nunca como piso.
            if (role == BlockRole.TREE) {
                tree = true;
            } else if (role == BlockRole.LIQUID) {
                liquid = true;
            } else if (role == BlockRole.TILE_STATE) {
                tileState = true;
            } else if (role == BlockRole.MAN_MADE) {
                builder.manMade(block.getType());
            }
        }

        builder.columnWithoutGround(slot, topY, tree, liquid, tileState);
    }

    /** Papel de um bloco na leitura do terreno. */
    private enum BlockRole { AIR, VEGETATION, TREE, LIQUID, TILE_STATE, MAN_MADE, GROUND }

    /**
     * Classifica um bloco.
     *
     * A ordem importa: árvore antes de vegetação (folha casa nas duas regras),
     * e solo natural antes de TileState para não pagar getState() em bloco comum
     * — getState() aloca um snapshot e roda para cada bloco da footprint.
     */
    private BlockRole roleOf(Block block) {
        Material material = block.getType();

        if (material.isAir()) {
            return BlockRole.AIR;
        }
        if (block.isLiquid()) {
            return BlockRole.LIQUID;
        }
        if (isTreePart(material)) {
            return BlockRole.TREE;
        }
        if (isVegetation(material)) {
            return BlockRole.VEGETATION;
        }
        if (isNaturalGround(material)) {
            return BlockRole.GROUND;
        }
        if (block.getState() instanceof TileState) {
            return BlockRole.TILE_STATE;
        }
        return BlockRole.MAN_MADE;
    }

    /**
     * Folha e tronco NUNCA são chão.
     *
     * Tag.LOGS já cobre log, wood, stripped_log e stripped_wood de todas as
     * madeiras, então não há lista de espécies para manter aqui.
     */
    private boolean isTreePart(Material material) {
        if (TREE_MATERIALS.contains(material)) {
            return true;
        }
        if (Tag.LEAVES.isTagged(material)) {
            return true;
        }
        return Tag.LOGS.isTagged(material);
    }

    /** Vegetação e neve fina: some na limpeza, não define piso, não bloqueia. */
    private boolean isVegetation(Material material) {
        if (VEGETATION_MATERIALS.contains(material)) {
            return true;
        }
        if (Tag.REPLACEABLE.isTagged(material)) {
            return true;
        }
        if (Tag.REPLACEABLE_BY_TREES.isTagged(material)) {
            return true;
        }
        if (Tag.FLOWERS.isTagged(material)) {
            return true;
        }
        if (Tag.SAPLINGS.isTagged(material)) {
            return true;
        }
        if (Tag.CROPS.isTagged(material)) {
            return true;
        }
        if (Tag.CAVE_VINES.isTagged(material)) {
            return true;
        }
        return Tag.WOOL_CARPETS.isTagged(material);
    }

    /**
     * Allowlist de solo natural.
     *
     * É de propósito uma allowlist: material desconhecido cai em MAN_MADE e
     * bloqueia a validação, em vez de virar piso por engano. Cobblestone,
     * stone_bricks e planks ficam de fora — num mundo gerado eles indicam
     * ruína, pedregulho de taiga ou construção, não terreno.
     */
    private boolean isNaturalGround(Material material) {
        if (GROUND_MATERIALS.contains(material)) {
            return true;
        }
        if (Tag.DIRT.isTagged(material)) {
            return true;
        }
        return Tag.BASE_STONE_OVERWORLD.isTagged(material);
    }

    /**
     * Blocos não removíveis dentro do volume que a loja ocuparia.
     *
     * A versão anterior varria a partir de getHighestBlockYAt()+1, ou seja, só
     * enxergava o que estava ACIMA do topo — um tronco inteiro passava batido.
     * Agora a varredura começa logo acima do solo real de cada coluna.
     */
    private int countEnvelopeObstructions(World world, Layout layout,
                                          FootprintSurvey survey, int floorY,
                                          boolean limparArvores) {
        int depth = MAX_V - MIN_V + 1;
        int count = 0;
        int envelopeTop = Math.min(floorY + BUILD_HEIGHT, world.getMaxHeight() - 1);

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                int groundY = survey.groundAt(index(u, v, depth));
                if (groundY == FootprintSurvey.NO_GROUND) {
                    continue;
                }

                Block probe = layout.block(world, u, v, world.getMinHeight());
                for (int y = groundY + 1; y <= envelopeTop; y++) {
                    Block block = world.getBlockAt(probe.getX(), y, probe.getZ());
                    BlockRole role = roleOf(block);
                    if (role == BlockRole.AIR || role == BlockRole.VEGETATION) {
                        continue;
                    }
                    if (role == BlockRole.TREE && limparArvores) {
                        continue;
                    }
                    count++;
                }
            }
        }

        return count;
    }

    private static int index(int u, int v, int depth) {
        return (u + HALF_WIDTH) * depth + (v - MIN_V);
    }

    // ------------------------------------------------------------------
    //  Construção
    // ------------------------------------------------------------------

    /**
     * Constrói a loja.
     *
     * Revalida imediatamente antes de tocar em qualquer bloco e tira um
     * snapshot do volume exato que será alterado, para o /biga loja desfazer.
     */
    public BuildResult build(World world, ShopSnapshot snapshot) {
        Analysis analysis = analyze(world);
        if (!analysis.valid()) {
            return new BuildResult(false, analysis.reason(), analysis);
        }
        if (!analysis.safe()) {
            return new BuildResult(false,
                    "Construção bloqueada pela validação: " + analysis.reason(), analysis);
        }

        Layout layout = new Layout(
                analysis.center().getBlockX(),
                analysis.center().getBlockZ(),
                analysis.frontFace()
        );

        FootprintSurvey survey = analysis.survey();
        int floorY = analysis.floorY();

        String snapshotError = snapshot.capture(world, layout, survey, floorY);
        if (snapshotError != null) {
            return new BuildResult(false,
                    "Construção abortada: não foi possível guardar o snapshot de rollback. "
                            + snapshotError, analysis);
        }

        try {
            // Entidade antiga primeiro: construir por cima empilharia duas
            // gerações de item flutuante no mesmo lugar.
            plugin.shopDisplays().removeAll(world);

            if (plugin.getConfig().getBoolean("loja-spawn.limpar-arvores", false)) {
                int derrubados = clearTrees(world, layout, survey, floorY);
                if (derrubados > 0) {
                    plugin.getLogger().info("[loja] " + derrubados
                            + " bloco(s) de árvore/vegetação removidos da footprint.");
                }
            }

            prepareFoundation(world, layout, survey, floorY);
            clearVegetationVolume(world, layout, floorY);
            buildFloor(world, layout, floorY);
            buildMainStructure(world, layout, floorY);
            buildRoof(world, layout, floorY);
            buildMarketAwning(world, layout, floorY);
            buildTradeCorner(world, layout, floorY);
            buildDecor(world, layout, floorY);
            placeMarketSign(world, layout, floorY);
            buildAdminShopWall(world, layout, floorY);
        } catch (RuntimeException erro) {
            plugin.getLogger().severe("Falha durante a construção da Biga Market: " + erro);
            return new BuildResult(false,
                    "A construção falhou no meio (" + erro.getClass().getSimpleName()
                            + "). O snapshot foi salvo: rode /biga loja desfazer CONFIRMAR.",
                    analysis);
        }

        return new BuildResult(true,
                "Biga Market construída com referência no spawn real do mundo.", analysis);
    }

    /**
     * Aterro entre o solo real de cada coluna e o piso.
     *
     * Antes esta rotina partia de getHighestBlockYAt()+1. Com a copa em Y=89 e
     * o piso também em 89 ela teria enchido de cobblestone da grama (Y≈62) até
     * 89 em quase 360 colunas — um monólito de 27 blocos. Agora o preenchimento
     * parte do solo real e a análise já limita a altura via max-fundacao.
     */
    private void prepareFoundation(World world, Layout layout,
                                   FootprintSurvey survey, int floorY) {
        int depth = MAX_V - MIN_V + 1;

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                int groundY = survey.groundAt(index(u, v, depth));
                if (groundY == FootprintSurvey.NO_GROUND) {
                    continue;
                }

                for (int y = groundY + 1; y <= floorY; y++) {
                    Material material = Material.COBBLESTONE;
                    if (y == floorY) {
                        material = Material.STONE_BRICKS;
                    }
                    layout.block(world, u, v, y).setType(material, false);
                }
            }
        }
    }

    /**
     * Limpa só vegetação dentro do salão.
     *
     * Árvore, líquido, TileState e construção nunca chegam aqui: a validação
     * bloqueia antes. A limpeza é deliberadamente conservadora.
     */
    /**
     * Limpa vegetação dentro do salão.
     *
     * Árvore, líquido, TileState e construção nunca chegam aqui quando
     * limpar-arvores está desligado: a validação bloqueia antes. Com a opção
     * ligada, a derrubada acontece em {@link #clearTrees} — separada de
     * propósito, para que "limpar grama" e "derrubar floresta" nunca sejam a
     * mesma linha de código.
     */
    private void clearVegetationVolume(World world, Layout layout, int floorY) {
        int topo = roofHeight(0, floorY) + 1;

        for (int u = -WALL_HALF; u <= WALL_HALF; u++) {
            for (int v = FRONT_V - 1; v <= BACK_V; v++) {
                for (int y = floorY + 1; y <= topo; y++) {
                    Block block = layout.block(world, u, v, y);
                    if (roleOf(block) == BlockRole.VEGETATION) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /**
     * Derruba árvore dentro da footprint. SÓ roda com limpar-arvores ligado.
     *
     * Remove apenas folha e tronco, e só acima do solo medido — nunca cava.
     * Não replanta e não devolve madeira: é terraplanagem, não colheita.
     */
    private int clearTrees(World world, Layout layout, FootprintSurvey survey, int floorY) {
        int depth = MAX_V - MIN_V + 1;
        int topo = Math.min(floorY + BUILD_HEIGHT, world.getMaxHeight() - 1);
        int removidos = 0;

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                int groundY = survey.groundAt(index(u, v, depth));
                if (groundY == FootprintSurvey.NO_GROUND) {
                    continue;
                }

                Block probe = layout.block(world, u, v, world.getMinHeight());
                for (int y = groundY + 1; y <= topo; y++) {
                    Block block = world.getBlockAt(probe.getX(), y, probe.getZ());
                    BlockRole role = roleOf(block);
                    if (role != BlockRole.TREE && role != BlockRole.VEGETATION) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                    removidos++;
                }
            }
        }

        return removidos;
    }

    private void buildFloor(World world, Layout layout, int floorY) {
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Material floorMaterial = Material.STONE_BRICKS;

                boolean border = Math.abs(u) == HALF_WIDTH || v == MIN_V || v == MAX_V;
                if (border) {
                    floorMaterial = Material.POLISHED_ANDESITE;
                } else if ((Math.abs(u) + Math.abs(v)) % 7 == 0) {
                    floorMaterial = Material.ANDESITE;
                }

                layout.block(world, u, v, floorY).setType(floorMaterial, false);
            }
        }
    }

    private void buildMainStructure(World world, Layout layout, int floorY) {
        // Paredes da frente e do fundo.
        for (int y = 1; y <= WALL_HEIGHT - 1; y++) {
            for (int u = -WALL_HALF; u <= WALL_HALF; u++) {
                placeWallBlock(world, layout, u, FRONT_V, floorY + y, y, true);
                placeWallBlock(world, layout, u, BACK_V, floorY + y, y, false);
            }
        }

        // Paredes laterais inteiras em tronco, incluindo os cantos.
        for (int v = FRONT_V; v <= BACK_V; v++) {
            for (int u : new int[]{-WALL_HALF, WALL_HALF}) {
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    layout.block(world, u, v, floorY + y)
                            .setType(Material.STRIPPED_DARK_OAK_LOG, false);
                }
            }
        }

        // Pilares que quebram a monotonia da fachada.
        for (int u = -WALL_HALF + 4; u <= WALL_HALF - 4; u += 5) {
            for (int y = 1; y <= WALL_HEIGHT - 1; y++) {
                layout.block(world, u, FRONT_V, floorY + y)
                        .setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
        }

        // Entrada ampla — circulação boa mesmo com vários jogadores.
        for (int u = -DOOR_HALF; u <= DOOR_HALF; u++) {
            for (int y = 1; y <= WALL_HEIGHT - 2; y++) {
                layout.block(world, u, FRONT_V, floorY + y).setType(Material.AIR, false);
            }
        }

        // Janelas frontais, dos dois lados da porta.
        for (int u = DOOR_HALF + 2; u <= WALL_HALF - 2; u += 3) {
            for (int y = 2; y <= 3; y++) {
                layout.block(world, u, FRONT_V, floorY + y).setType(Material.GLASS_PANE, false);
                layout.block(world, -u, FRONT_V, floorY + y).setType(Material.GLASS_PANE, false);
            }
        }

        // Janelas laterais.
        for (int v = FRONT_V + 3; v <= BACK_V - 3; v += 4) {
            for (int y = 2; y <= 3; y++) {
                layout.block(world, -WALL_HALF, v, floorY + y).setType(Material.GLASS_PANE, false);
                layout.block(world, WALL_HALF, v, floorY + y).setType(Material.GLASS_PANE, false);
            }
        }
    }

    private void placeWallBlock(World world, Layout layout, int u, int v,
                                int worldY, int relativeY, boolean front) {
        if (front && u >= -DOOR_HALF && u <= DOOR_HALF && relativeY <= WALL_HEIGHT - 2) {
            return;
        }

        Material material = Material.SPRUCE_PLANKS;
        if (relativeY == 1) {
            material = Material.STONE_BRICKS;
        }
        layout.block(world, u, v, worldY).setType(material, false);
    }

    /**
     * Altura da telha na coluna u.
     *
     * Extraída como método estático para poder ser testada sem servidor — a
     * geometria do telhado foi onde a primeira construção real saiu errada.
     */
    static int roofHeight(int u, int floorY) {
        int distanceFromEdge = ROOF_HALF_SPAN - Math.abs(u);
        return floorY + WALL_HEIGHT + (distanceFromEdge / 2);
    }

    /** Coluna vizinha na direção da cumeeira. */
    static int towardRidge(int u) {
        if (u > 0) {
            return u - 1;
        }
        if (u < 0) {
            return u + 1;
        }
        return 0;
    }

    private void buildRoof(World world, Layout layout, int floorY) {
        // Telhado escalonado: aparência medieval sem depender de WorldEdit.
        //
        // Cada coluna sobe até a altura da vizinha de dentro. Sem isso o degrau
        // fica com a face vertical aberta: na primeira construção dava para ver
        // o sótão de lado, e chuva e luz entravam pelas frestas.
        for (int u = -ROOF_HALF_SPAN; u <= ROOF_HALF_SPAN; u++) {
            int roofY = roofHeight(u, floorY);
            int riserTop = roofHeight(towardRidge(u), floorY);

            for (int v = ROOF_MIN_V; v <= ROOF_MAX_V; v++) {
                Material material = Material.SPRUCE_PLANKS;
                if ((v + Math.abs(u)) % 5 == 0) {
                    material = Material.DARK_OAK_PLANKS;
                }
                for (int y = roofY; y <= riserTop; y++) {
                    layout.block(world, u, v, y).setType(material, false);
                }
            }
        }

        // Cumeeira APOIADA sobre o telhado, não no lugar dele. Antes o slab
        // substituía a telha do topo e abria uma calha de meio bloco ao longo
        // de toda a cumeeira.
        int ridgeY = roofHeight(0, floorY) + 1;
        for (int v = ROOF_MIN_V; v <= ROOF_MAX_V; v++) {
            layout.block(world, 0, v, ridgeY).setType(Material.DARK_OAK_SLAB, false);
        }

        // Gable nas DUAS pontas. Só a da frente existia, então o fundo ficava
        // com um rombo triangular aberto entre o topo da parede e o telhado.
        buildGable(world, layout, floorY, FRONT_V);
        buildGable(world, layout, floorY, BACK_V);

        // Chaminé, encostada na lateral direita.
        int chamineU = WALL_HALF - 3;
        int chamineV = BACK_V - 4;
        int topoChamine = roofHeight(chamineU, floorY) + 4;
        for (int y = floorY + WALL_HEIGHT + 1; y <= topoChamine; y++) {
            layout.block(world, chamineU, chamineV, y).setType(Material.STONE_BRICKS, false);
        }
        layout.block(world, chamineU, chamineV, topoChamine + 1)
                .setType(Material.CAMPFIRE, false);
    }

    /** Frontão triangular, preenchendo até encostar na telha de cada coluna. */
    private void buildGable(World world, Layout layout, int floorY, int v) {
        for (int u = -ROOF_HALF_SPAN; u <= ROOF_HALF_SPAN; u++) {
            int topo = roofHeight(u, floorY);
            for (int y = floorY + WALL_HEIGHT; y <= topo; y++) {
                layout.block(world, u, v, y).setType(Material.DARK_OAK_PLANKS, false);
            }
        }
    }

    private void buildMarketAwning(World world, Layout layout, int floorY) {
        int deU = -WALL_HALF;
        int ateU = -DOOR_HALF - 2;

        for (int u = deU; u <= ateU; u++) {
            for (int v = MIN_V + 1; v <= FRONT_V - 1; v++) {
                Material canopy = Material.BLUE_WOOL;
                if (Math.abs(u) % 2 == 0) {
                    canopy = Material.WHITE_WOOL;
                }
                layout.block(world, u, v, floorY + 4).setType(canopy, false);
            }
        }

        for (int u : new int[]{deU, ateU}) {
            for (int y = 1; y <= 3; y++) {
                layout.block(world, u, MIN_V + 1, floorY + y).setType(Material.OAK_FENCE, false);
            }
        }

        // Bancada da feirinha sob a lona.
        for (int u = deU + 1; u <= ateU - 1; u++) {
            layout.block(world, u, FRONT_V - 1, floorY + 1).setType(Material.BARREL, false);
        }
    }

    /**
     * Bancas livres para ChestShop de jogador, do lado oposto ao toldo.
     *
     * A Biga Market é a loja do servidor, mas o projeto continua priorizando
     * mercado entre jogadores. Estes baús ficam vazios e sem dono.
     */
    private void buildTradeCorner(World world, Layout layout, int floorY) {
        for (int u = DOOR_HALF + 2; u <= WALL_HALF - 1; u++) {
            layout.block(world, u, MIN_V + 2, floorY + 1).setType(Material.CHEST, false);
            layout.block(world, u, MIN_V + 1, floorY + 1).setType(Material.DARK_OAK_SLAB, false);
        }
    }

    private void buildDecor(World world, Layout layout, int floorY) {
        // Vasos de azaleia flanqueando a fachada.
        for (int u : new int[]{-WALL_HALF + 1, WALL_HALF - 1}) {
            layout.block(world, u, FRONT_V - 1, floorY + 1).setType(Material.BARREL, false);
            placePersistentLeaves(layout.block(world, u, FRONT_V - 1, floorY + 2));
        }

        // Material.CHAIN não existe na API 26.2 usada pelo projeto; IRON_BARS
        // mantém o detalhe vertical da luminária sem depender de material legado.
        //
        // Fora do alcance do toldo: a lanterna chegou a apagar um bloco da lona.
        for (int u : new int[]{-DOOR_HALF - 1, DOOR_HALF + 1}) {
            layout.block(world, u, FRONT_V - 1, floorY + 5).setType(Material.IRON_BARS, false);
            layout.block(world, u, FRONT_V - 1, floorY + 4).setType(Material.LANTERN, false);
        }

        // Iluminação interna: uma malha, não três lanternas perdidas num salão
        // que agora tem quase o triplo da área.
        for (int u = -WALL_HALF + 3; u <= WALL_HALF - 3; u += 6) {
            for (int v = FRONT_V + 3; v <= BACK_V - 2; v += 6) {
                layout.block(world, u, v, floorY + WALL_HEIGHT - 1)
                        .setType(Material.LANTERN, false);
            }
        }
    }

    /**
     * Folha decorativa que não apodrece.
     *
     * setType() cria a folha com persistent=false. Sem tronco por perto ela
     * simplesmente sumiu da loja construída — o censo de blocos não achou
     * nenhuma das quatro folhas de azaleia depois de algumas horas de servidor.
     */
    private void placePersistentLeaves(Block block) {
        block.setType(Material.FLOWERING_AZALEA_LEAVES, false);

        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }
    }

    private void placeMarketSign(World world, Layout layout, int floorY) {
        Block signBlock = layout.block(world, 0, FRONT_V - 1, floorY + 5);
        signBlock.setType(Material.OAK_WALL_SIGN, false);

        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(layout.frontFace());
            signBlock.setBlockData(directional, false);
        }

        if (signBlock.getState() instanceof Sign sign) {
            // Sign.line(int, Component) está deprecado desde que placas ganharam
            // dois lados; getSide(Side.FRONT) é a forma atual e deixa explícito
            // que o texto vai na face virada para o spawn.
            SignSide frente = sign.getSide(Side.FRONT);
            frente.line(0, Component.text("BIGA"));
            frente.line(1, Component.text("MARKET"));
            frente.line(2, Component.text("B$"));
            frente.line(3, Component.text("Loja do servidor"));
            sign.update(true, false);
        }
    }

    /**
     * Vagas do balcão do servidor: fundo + as duas laterais.
     *
     * Cada vaga é um barril com uma placa ChestShop logo acima. O barril fica
     * DIRETAMENTE abaixo da placa porque é assim que o ChestShop associa
     * container e placa.
     *
     * A placa encosta na parede e olha para dentro do salão — por isso a
     * direção muda conforme a parede, e não dá para usar sempre a fachada.
     */
    private java.util.List<ShopSlot> adminShopSlots(Layout layout) {
        java.util.List<ShopSlot> vagas = new java.util.ArrayList<>();

        BlockFace inward = layout.frontFace().getOppositeFace();
        BlockFace right = Layout.rightOf(inward);

        // Parede do fundo: placa olha para a entrada.
        for (int u = -(WALL_HALF - 1); u <= WALL_HALF - 1; u++) {
            vagas.add(new ShopSlot(u, BACK_V - 1, layout.frontFace()));
        }

        // Laterais: placa olha para o centro do salão.
        for (int v = FRONT_V + 1; v <= BACK_V - 1; v++) {
            vagas.add(new ShopSlot(-(WALL_HALF - 1), v, right));
            vagas.add(new ShopSlot(WALL_HALF - 1, v, right.getOppositeFace()));
        }

        return vagas;
    }

    private record ShopSlot(int u, int v, BlockFace facing) {
    }

    /**
     * Quantas vagas o balcão oferece. Aritmética pura, para o teste conferir
     * que o economy.yml não pede mais itens do que cabe na parede.
     */
    static int adminShopSlotCount() {
        int fundo = 2 * (WALL_HALF - 1) + 1;
        int lateral = (BACK_V - 1) - (FRONT_V + 1) + 1;
        return fundo + 2 * lateral;
    }

    /**
     * Monta o balcão da loja do servidor.
     *
     * Formato da placa, exatamente como o ChestShop espera:
     * <pre>
     * Admin Shop      &lt;- precisa bater com ADMIN_SHOP_NAME
     * 64              &lt;- lote
     * B 32            &lt;- só compra: o jogador paga 32 para levar 64
     * COBBLESTONE
     * </pre>
     * Note que não há preço de venda ("S"): o servidor não compra por placa.
     * Recompra é só via /biga eco vender, onde o teto diário vale.
     */
    private void buildAdminShopWall(World world, Layout layout, int floorY) {
        EconomyCatalog catalog = plugin.economyCatalog();
        if (!catalog.adminShopEnabled()) {
            return;
        }

        java.util.List<ShopSlot> vagas = adminShopSlots(layout);
        var vitrine = catalog.adminShopWall(vagas.size());
        if (vitrine.isEmpty()) {
            plugin.getLogger().warning("[admin-shop] Nenhum item válido na parede; balcão vazio.");
            return;
        }

        String dono = catalog.adminShopSignOwner();
        int montadas = 0;

        for (int i = 0; i < vitrine.size() && i < vagas.size(); i++) {
            ShopSlot vaga = vagas.get(i);
            EconomyCatalog.PriceEntry item = vitrine.get(i);

            layout.block(world, vaga.u(), vaga.v(), floorY + 1)
                    .setType(Material.BARREL, false);
            placeShopSign(world, layout, vaga, floorY + 2, dono, item);
            plugin.shopDisplays().spawn(world, layout, vaga.u(), vaga.v(), floorY, item);
            montadas++;
        }

        plugin.getLogger().info("[admin-shop] Balcão montado com " + montadas
                + " vaga(s) de " + vagas.size() + " disponíveis.");
    }

    private void placeShopSign(World world, Layout layout, ShopSlot vaga, int y,
                               String dono, EconomyCatalog.PriceEntry item) {
        Block signBlock = layout.block(world, vaga.u(), vaga.v(), y);
        signBlock.setType(Material.OAK_WALL_SIGN, false);

        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(vaga.facing());
            signBlock.setBlockData(directional, false);
        }

        if (!(signBlock.getState() instanceof Sign sign)) {
            return;
        }

        EconomyCatalog catalog = plugin.economyCatalog();

        // Linha 3: "B <venda>" compra apenas, ou "B <venda>:<compra> S" nos dois
        // sentidos. O segundo só é ligado com o teto de recompra funcionando.
        String preco = "B " + item.serverSell();
        if (catalog.adminShopSignBuysBack() && item.serverBuy() != null) {
            preco = "B " + item.serverSell() + ":" + item.serverBuy() + " S";
        }

        // Linha 4: o ChestShop resolve o nome em português pelo itemAliases.yml.
        String nomeItem = item.key().toUpperCase(Locale.ROOT);
        if (catalog.adminShopPortugueseNames()) {
            nomeItem = catalog.displayName(item.key());
        }

        SignSide frente = sign.getSide(Side.FRONT);
        frente.line(0, Component.text(dono));
        frente.line(1, Component.text(String.valueOf(item.lot())));
        frente.line(2, Component.text(preco));
        frente.line(3, Component.text(nomeItem));
        sign.update(true, false);
    }

    private BlockFace frontFace(int offsetX, int offsetZ) {
        if (Math.abs(offsetX) >= Math.abs(offsetZ)) {
            if (offsetX > 0) {
                return BlockFace.WEST;
            }
            return BlockFace.EAST;
        }

        if (offsetZ > 0) {
            return BlockFace.NORTH;
        }
        return BlockFace.SOUTH;
    }

    // ------------------------------------------------------------------
    //  Tipos de apoio
    // ------------------------------------------------------------------

    /**
     * Resultado da varredura da footprint, coluna a coluna.
     *
     * Guarda o solo real de cada coluna porque a fundação e o snapshot precisam
     * exatamente do mesmo número usado na validação — validar com uma geometria
     * e construir com outra é como o Y=90 nasceu.
     */
    public static final class FootprintSurvey {

        public static final int NO_GROUND = Integer.MIN_VALUE;

        private final int[] groundY;
        private final int[] topY;
        private final int totalColumns;
        private final int groundColumns;
        private final int columnsWithoutGround;
        private final int treeColumns;
        private final int liquidColumns;
        private final int tileStateColumns;
        private final int manMadeBlocks;
        private final String firstManMadeName;
        private final int minGroundY;
        private final int maxGroundY;
        private final int minTopY;
        private final int maxTopY;

        private FootprintSurvey(Builder b) {
            this.groundY = b.groundY;
            this.topY = b.topY;
            this.totalColumns = b.groundY.length;
            this.groundColumns = b.groundColumns;
            this.columnsWithoutGround = b.columnsWithoutGround;
            this.treeColumns = b.treeColumns;
            this.liquidColumns = b.liquidColumns;
            this.tileStateColumns = b.tileStateColumns;
            this.manMadeBlocks = b.manMadeBlocks;
            this.firstManMadeName = b.firstManMade;
            this.minGroundY = b.minGroundY;
            this.maxGroundY = b.maxGroundY;
            this.minTopY = b.minTopY;
            this.maxTopY = b.maxTopY;
        }

        public int groundAt(int slot) {
            return groundY[slot];
        }

        public int topAt(int slot) {
            return topY[slot];
        }

        public int totalColumns() {
            return totalColumns;
        }

        public int groundColumns() {
            return groundColumns;
        }

        public int columnsWithoutGround() {
            return columnsWithoutGround;
        }

        public int treeColumns() {
            return treeColumns;
        }

        public int liquidColumns() {
            return liquidColumns;
        }

        public int tileStateColumns() {
            return tileStateColumns;
        }

        public int manMadeBlocks() {
            return manMadeBlocks;
        }

        public String firstManMadeName() {
            return firstManMadeName;
        }

        public int minGroundY() {
            return minGroundY;
        }

        public int maxGroundY() {
            return maxGroundY;
        }

        public int minTopY() {
            return minTopY;
        }

        public int maxTopY() {
            return maxTopY;
        }

        private static final class Builder {
            private final int[] groundY;
            private final int[] topY;
            private int groundColumns;
            private int columnsWithoutGround;
            private int treeColumns;
            private int liquidColumns;
            private int tileStateColumns;
            private int manMadeBlocks;
            private String firstManMade = "nenhum";
            private int minGroundY = Integer.MAX_VALUE;
            private int maxGroundY = Integer.MIN_VALUE;
            private int minTopY = Integer.MAX_VALUE;
            private int maxTopY = Integer.MIN_VALUE;

            private Builder(int width, int depth, int[] groundY, int[] topY) {
                this.groundY = groundY;
                this.topY = topY;
                java.util.Arrays.fill(this.groundY, NO_GROUND);
                java.util.Arrays.fill(this.topY, NO_GROUND);
                if (width * depth != groundY.length) {
                    throw new IllegalArgumentException("Footprint inconsistente.");
                }
            }

            private void column(int slot, int ground, int top,
                                boolean tree, boolean liquid, boolean tileState) {
                groundY[slot] = ground;
                groundColumns++;
                minGroundY = Math.min(minGroundY, ground);
                maxGroundY = Math.max(maxGroundY, ground);
                recordTop(slot, top, ground);
                recordFlags(tree, liquid, tileState);
            }

            private void columnWithoutGround(int slot, int top,
                                             boolean tree, boolean liquid, boolean tileState) {
                columnsWithoutGround++;
                recordTop(slot, top, NO_GROUND);
                recordFlags(tree, liquid, tileState);
            }

            private void recordTop(int slot, int top, int fallback) {
                int value = top;
                if (value == Integer.MIN_VALUE) {
                    value = fallback;
                }
                if (value == NO_GROUND) {
                    return;
                }
                topY[slot] = value;
                minTopY = Math.min(minTopY, value);
                maxTopY = Math.max(maxTopY, value);
            }

            private void recordFlags(boolean tree, boolean liquid, boolean tileState) {
                if (tree) {
                    treeColumns++;
                }
                if (liquid) {
                    liquidColumns++;
                }
                if (tileState) {
                    tileStateColumns++;
                }
            }

            private void manMade(Material material) {
                manMadeBlocks++;
                if (manMadeBlocks == 1) {
                    firstManMade = material.name().toLowerCase(Locale.ROOT);
                }
            }

            private FootprintSurvey build() {
                if (groundColumns == 0) {
                    minGroundY = 0;
                    maxGroundY = 0;
                }
                if (minTopY == Integer.MAX_VALUE) {
                    minTopY = 0;
                    maxTopY = 0;
                }
                return new FootprintSurvey(this);
            }
        }
    }

    public record Analysis(
            boolean valid,
            boolean safe,
            String reason,
            Location spawn,
            Location center,
            BlockFace frontFace,
            int floorY,
            FootprintSurvey survey,
            int obstructionCount,
            int foundationDepth,
            int slope
    ) {
        private static Analysis invalid(Location spawn, String reason) {
            return new Analysis(false, false, reason, spawn, spawn.clone(),
                    BlockFace.NORTH, spawn.getBlockY(), null, 0, 0, 0);
        }

        public String facingName() {
            return frontFace.name().toLowerCase(Locale.ROOT);
        }

        public int footprintWidth() {
            return HALF_WIDTH * 2 + 1;
        }

        public int footprintDepth() {
            return MAX_V - MIN_V + 1;
        }
    }

    public record BuildResult(boolean success, String message, Analysis analysis) {
    }

    /**
     * Converte coordenadas locais da loja (u = lateral, v = profundidade) para
     * coordenadas do mundo, respeitando a fachada.
     */
    record Layout(int centerX, int centerZ, BlockFace frontFace) {

        Block block(World world, int u, int v, int y) {
            BlockFace inward = frontFace.getOppositeFace();
            BlockFace right = rightOf(inward);

            // O "7" era fixo aqui e correspondia ao meio da footprint 21x17
            // antiga. Com a loja em 31x29 esse número virou mentira: o centro
            // reportado por /biga loja local ficaria deslocado do centro real
            // da construção. Agora sai da própria geometria.
            int meio = CENTER_V;
            int frontCenterX = centerX - inward.getModX() * meio;
            int frontCenterZ = centerZ - inward.getModZ() * meio;

            int x = frontCenterX + inward.getModX() * v + right.getModX() * u;
            int z = frontCenterZ + inward.getModZ() * v + right.getModZ() * u;
            return world.getBlockAt(x, y, z);
        }

        static BlockFace rightOf(BlockFace inward) {
            return switch (inward) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> throw new IllegalArgumentException("Direção não cardinal: " + inward);
            };
        }
    }
}
