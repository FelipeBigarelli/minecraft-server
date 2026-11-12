package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.block.Biome;

import java.util.List;
import java.util.Random;

/**
 * Gerador de mundo vazio — nenhum bloco, nenhuma estrutura, nenhum bioma feio.
 *
 * <h2>Por que isto existe dentro do BigaCore</h2>
 *
 * Um hub flutuante precisa de um mundo sem chão. O jeito comum é instalar um
 * plugin só para isso (VoidGen e parecidos), e aí o hub passa a depender de um
 * plugin a mais para o mundo sequer carregar: se ele some num update, o mundo
 * volta a gerar pedra POR BAIXO da construção.
 *
 * Como o BigaCore já é obrigatório aqui, o gerador mora nele:
 *
 *   /mv create hub normal -g BigaCore
 *
 * <h2>Bioma</h2>
 *
 * THE_VOID de propósito, e não PLAINS. No void o céu fica preto, não chove,
 * não nasce grama, e — o que importa de verdade num hub — nenhum mob
 * aparece, sem precisar de regra de spawn nenhuma.
 */
public final class VazioGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.THE_VOID);
            }
        };
    }

    /**
     * Onde o jogador aparece antes de o hub existir.
     *
     * Sem isto o servidor procura chão para o spawn, não acha nenhum e deixa
     * o primeiro visitante caindo no void — inclusive quem rodar /mv create e
     * for olhar o mundo novo antes de construir.
     */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, HubCatalog.Y_PADRAO + 1, 0.5);
    }
}
