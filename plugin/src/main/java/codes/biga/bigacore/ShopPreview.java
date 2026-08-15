package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Preview da Biga Market com partículas.
 *
 * Nenhum bloco é alterado — nem temporariamente. Partículas são pacotes
 * enviados só para o jogador que pediu, então o preview também não aparece
 * para os outros e não deixa resíduo se o servidor cair no meio.
 *
 * Legenda desenhada:
 * <ul>
 *   <li>verde  (HAPPY_VILLAGER) — perímetro da footprint;</li>
 *   <li>laranja (FLAME)         — linha da fachada;</li>
 *   <li>branco (END_ROD)        — cantos e vão da entrada;</li>
 *   <li>azul   (SOUL_FIRE_FLAME)— eixo central.</li>
 * </ul>
 */
public final class ShopPreview {

    private static final long PERIOD_TICKS = 10L;
    private static final int FRAMES = 20;

    private final BigaCore plugin;
    private final Map<UUID, BukkitTask> ativos = new HashMap<>();

    public ShopPreview(BigaCore plugin) {
        this.plugin = plugin;
    }

    /** Segundos que o preview fica visível. */
    public int durationSeconds() {
        return (int) (FRAMES * PERIOD_TICKS / 20L);
    }

    public void start(Player player, SpawnShopBuilder.Analysis analysis) {
        stop(player);

        World world = player.getWorld();
        SpawnShopBuilder.Layout layout = new SpawnShopBuilder.Layout(
                analysis.center().getBlockX(),
                analysis.center().getBlockZ(),
                analysis.frontFace()
        );
        int floorY = analysis.floorY();

        // Contador em array porque a lambda precisa de variável efetivamente
        // final; o task roda sempre na main thread, então não há corrida.
        int[] frame = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            frame[0]++;
            if (!player.isOnline() || frame[0] > FRAMES) {
                stop(player);
                return;
            }
            desenhar(player, world, layout, floorY);
        }, 0L, PERIOD_TICKS);

        ativos.put(player.getUniqueId(), task);
    }

    public void stop(Player player) {
        BukkitTask anterior = ativos.remove(player.getUniqueId());
        if (anterior != null) {
            anterior.cancel();
        }
    }

    /** Cancela tudo no onDisable para não deixar task órfã apontando ao plugin. */
    public void stopAll() {
        for (BukkitTask task : ativos.values()) {
            task.cancel();
        }
        ativos.clear();
    }

    private void desenhar(Player player, World world,
                          SpawnShopBuilder.Layout layout, int floorY) {

        int half = SpawnShopBuilder.HALF_WIDTH;
        int minV = SpawnShopBuilder.MIN_V;
        int maxV = SpawnShopBuilder.MAX_V;

        // Perímetro da footprint, logo acima do piso calculado.
        for (int u = -half; u <= half; u++) {
            marcar(player, world, layout, u, minV, floorY + 1, Particle.HAPPY_VILLAGER);
            marcar(player, world, layout, u, maxV, floorY + 1, Particle.HAPPY_VILLAGER);
        }
        for (int v = minV; v <= maxV; v++) {
            marcar(player, world, layout, -half, v, floorY + 1, Particle.HAPPY_VILLAGER);
            marcar(player, world, layout, half, v, floorY + 1, Particle.HAPPY_VILLAGER);
        }

        // Cantos: colunas verticais para enxergar a caixa mesmo de longe.
        for (int y = 1; y <= 8; y++) {
            marcar(player, world, layout, -half, minV, floorY + y, Particle.END_ROD);
            marcar(player, world, layout, half, minV, floorY + y, Particle.END_ROD);
            marcar(player, world, layout, -half, maxV, floorY + y, Particle.END_ROD);
            marcar(player, world, layout, half, maxV, floorY + y, Particle.END_ROD);
        }

        // Linha da fachada, na parede da frente. Referencia as constantes do
        // builder para nunca mais dessincronizar da geometria real quando a
        // loja mudar de tamanho (foi o que gerou o preview antigo em v = 2).
        int wallHalf = SpawnShopBuilder.WALL_HALF;
        int frontV = SpawnShopBuilder.FRONT_V;
        for (int u = -wallHalf; u <= wallHalf; u++) {
            marcar(player, world, layout, u, frontV, floorY + 1, Particle.FLAME);
        }

        // Vão da entrada, centrado na parede da frente.
        int doorHalf = SpawnShopBuilder.DOOR_HALF;
        for (int u = -doorHalf; u <= doorHalf; u++) {
            for (int y = 1; y <= 4; y++) {
                marcar(player, world, layout, u, frontV, floorY + y, Particle.END_ROD);
            }
        }

        // Eixo central: mostra onde o centro proposto realmente cai.
        int centerV = SpawnShopBuilder.CENTER_V;
        for (int y = 1; y <= 6; y++) {
            marcar(player, world, layout, 0, centerV, floorY + y, Particle.SOUL_FIRE_FLAME);
        }
    }

    private void marcar(Player player, World world, SpawnShopBuilder.Layout layout,
                        int u, int v, int y, Particle particle) {
        Location location = layout.block(world, u, v, y).getLocation().add(0.5, 0.1, 0.5);
        player.spawnParticle(particle, location, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
