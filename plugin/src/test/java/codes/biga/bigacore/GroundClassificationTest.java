package codes.biga.bigacore;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão da classificação de terreno da Biga Market.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A detecção de solo foi escrita apoiada em {@code Tag.DIRT}. Em Paper 26.2
 * essa tag contém apenas {@code dirt}, {@code coarse_dirt} e
 * {@code rooted_dirt} — {@code grass_block}, {@code podzol} e {@code mycelium}
 * ficaram de fora.
 *
 * O sintoma em runtime foi silencioso e enganoso: 383 blocos de grama viraram
 * "construção existente" e o piso saiu um bloco abaixo do chão real.
 *
 * O conteúdo de uma Tag é dado do servidor e muda entre versões. Por isso a
 * allowlist explícita é a fonte de verdade — e é ela que este teste protege.
 * Tags não podem ser testadas aqui: elas exigem um Server ativo.
 */
class GroundClassificationTest {

    @Test
    void grassPodzolEMycelioSaoSolo() {
        Set<Material> solo = SpawnShopBuilder.groundMaterials();

        // Exatamente os três que a minecraft:dirt de 26.2 deixou de fora.
        assertTrue(solo.contains(Material.GRASS_BLOCK), "grass_block precisa ser solo");
        assertTrue(solo.contains(Material.PODZOL), "podzol precisa ser solo");
        assertTrue(solo.contains(Material.MYCELIUM), "mycelium precisa ser solo");
    }

    @Test
    void soloCobreOsTerrenosNaturaisComuns() {
        Set<Material> solo = SpawnShopBuilder.groundMaterials();

        for (Material material : new Material[]{
                Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
                Material.STONE, Material.DEEPSLATE, Material.GRANITE,
                Material.DIORITE, Material.ANDESITE, Material.TUFF,
                Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY}) {
            assertTrue(solo.contains(material), material + " precisa ser solo");
        }
    }

    @Test
    void folhaTroncoEVegetacaoNuncaSaoSolo() {
        Set<Material> solo = SpawnShopBuilder.groundMaterials();

        for (Material material : new Material[]{
                Material.SPRUCE_LEAVES, Material.OAK_LEAVES, Material.SPRUCE_LOG,
                Material.STRIPPED_SPRUCE_LOG, Material.SHORT_GRASS, Material.FERN,
                Material.SNOW, Material.WATER, Material.LAVA}) {
            assertFalse(solo.contains(material),
                    material + " jamais pode contar como chão — foi assim que o piso virou Y=90");
        }
    }

    @Test
    void materialDeConstrucaoNaoEhSolo() {
        Set<Material> solo = SpawnShopBuilder.groundMaterials();

        // Num mundo gerado estes indicam ruína, pedregulho de taiga ou build.
        for (Material material : new Material[]{
                Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                Material.SPRUCE_PLANKS, Material.POLISHED_ANDESITE,
                Material.FARMLAND}) {
            assertFalse(solo.contains(material), material + " não pode ser tratado como terreno");
        }
    }

    @Test
    void cogumeloPequenoEVegetacaoENaoConstrucao() {
        Set<Material> vegetacao = SpawnShopBuilder.vegetationMaterials();

        // Não estão em minecraft:replaceable nem em minecraft:flowers.
        assertTrue(vegetacao.contains(Material.BROWN_MUSHROOM));
        assertTrue(vegetacao.contains(Material.RED_MUSHROOM));
    }

    @Test
    void cogumeloGiganteEhArvore() {
        Set<Material> arvore = SpawnShopBuilder.treeMaterials();

        assertTrue(arvore.contains(Material.MUSHROOM_STEM));
        assertTrue(arvore.contains(Material.BROWN_MUSHROOM_BLOCK));
        assertTrue(arvore.contains(Material.RED_MUSHROOM_BLOCK));
    }

    @Test
    void asListasNaoSeSobrepoem() {
        Set<Material> solo = SpawnShopBuilder.groundMaterials();
        Set<Material> vegetacao = SpawnShopBuilder.vegetationMaterials();
        Set<Material> arvore = SpawnShopBuilder.treeMaterials();

        for (Material material : vegetacao) {
            assertFalse(solo.contains(material), material + " está em solo e vegetação");
        }
        for (Material material : arvore) {
            assertFalse(solo.contains(material), material + " está em solo e árvore");
            assertFalse(vegetacao.contains(material), material + " está em vegetação e árvore");
        }
    }
}
