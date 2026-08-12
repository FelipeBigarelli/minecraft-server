package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot de rollback da Biga Market.
 *
 * <h2>Escopo deliberadamente pequeno</h2>
 *
 * Salvar o mundo inteiro seria caro e perigoso. Este snapshot guarda apenas o
 * paralelepípedo que o gerador realmente toca: a footprint 21x17 da loja, do
 * solo mais baixo até o topo do telhado. São alguns milhares de blocos, não
 * milhões de chunks.
 *
 * <h2>Regras de segurança</h2>
 *
 * <ul>
 *   <li>nunca sobrescreve um snapshot existente em silêncio;</li>
 *   <li>nunca restaura sozinho — só via /biga loja desfazer CONFIRMAR;</li>
 *   <li>confere mundo (nome e UUID) antes de restaurar;</li>
 *   <li>guarda BlockData completo, então placa, baú e escada voltam com a
 *       orientação original.</li>
 * </ul>
 *
 * O conteúdo de inventários NÃO é salvo. Como a loja só é construída em área
 * validada como vazia, não deve existir baú com item dentro do volume; ainda
 * assim isso está documentado em LOJA-SPAWN.md como limitação conhecida.
 */
public final class ShopSnapshot {

    private static final String FILE_NAME = "loja-snapshot.yml";

    private final BigaCore plugin;

    public ShopSnapshot(BigaCore plugin) {
        this.plugin = plugin;
    }

    public File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    public boolean exists() {
        return file().isFile();
    }

    /**
     * Copia o volume que será alterado.
     *
     * @return null em caso de sucesso, ou a mensagem de erro que deve abortar a
     *         construção.
     */
    public String capture(World world, SpawnShopBuilder.Layout layout,
                          SpawnShopBuilder.FootprintSurvey survey, int floorY) {

        File target = file();
        if (target.isFile()) {
            return "Já existe um snapshot em " + FILE_NAME
                    + ". Rode /biga loja desfazer CONFIRMAR ou apague o arquivo antes.";
        }

        int minY = Math.max(world.getMinHeight(), survey.minGroundY());
        int maxY = Math.min(world.getMaxHeight() - 1, floorY + SpawnShopBuilder.BUILD_HEIGHT);
        if (maxY < minY) {
            return "Volume de snapshot inválido (" + minY + ".." + maxY + ").";
        }

        List<String> blocks = new ArrayList<>();
        for (int u = -SpawnShopBuilder.HALF_WIDTH; u <= SpawnShopBuilder.HALF_WIDTH; u++) {
            for (int v = SpawnShopBuilder.MIN_V; v <= SpawnShopBuilder.MAX_V; v++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = layout.block(world, u, v, y);
                    blocks.add(block.getX() + ";" + block.getY() + ";" + block.getZ()
                            + ";" + block.getBlockData().getAsString());
                }
            }
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mundo-nome", world.getName());
        yaml.set("mundo-uuid", world.getUID().toString());
        yaml.set("criado-em", System.currentTimeMillis());
        yaml.set("centro-x", layout.centerX());
        yaml.set("centro-z", layout.centerZ());
        yaml.set("piso-y", floorY);
        yaml.set("fachada", layout.frontFace().name());
        yaml.set("min-y", minY);
        yaml.set("max-y", maxY);
        yaml.set("blocos", blocks);

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return "Não foi possível criar a pasta " + parent.getPath() + ".";
        }

        try {
            yaml.save(target);
        } catch (IOException erro) {
            return "Falha ao gravar " + FILE_NAME + ": " + erro.getMessage();
        }

        plugin.getLogger().info("Snapshot da Biga Market salvo: " + blocks.size()
                + " blocos em " + target.getName() + ".");
        return null;
    }

    /** Restaura o volume salvo. Só é chamado por comando explícito. */
    public RestoreResult restore(World world) {
        File source = file();
        if (!source.isFile()) {
            return new RestoreResult(false, "Não existe snapshot para desfazer.", 0);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);

        String storedUuid = yaml.getString("mundo-uuid", "");
        String storedName = yaml.getString("mundo-nome", "");
        if (!world.getName().equals(storedName)) {
            return new RestoreResult(false,
                    "O snapshot é do mundo '" + storedName + "', não de '"
                            + world.getName() + "'.", 0);
        }
        if (!matchesUuid(world, storedUuid)) {
            return new RestoreResult(false,
                    "O UUID do mundo mudou desde o snapshot. Restauração recusada.", 0);
        }

        List<String> blocks = yaml.getStringList("blocos");
        if (blocks.isEmpty()) {
            return new RestoreResult(false, "O snapshot está vazio ou corrompido.", 0);
        }

        int restored = 0;
        for (String entry : blocks) {
            String[] parts = entry.split(";", 4);
            if (parts.length != 4) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                BlockData data = Bukkit.createBlockData(parts[3]);
                world.getBlockAt(x, y, z).setBlockData(data, false);
                restored++;
            } catch (IllegalArgumentException erro) {
                plugin.getLogger().warning("Entrada de snapshot ignorada: " + entry);
            }
        }

        if (!source.delete()) {
            plugin.getLogger().warning("Snapshot restaurado, mas " + FILE_NAME
                    + " não pôde ser apagado. Apague à mão antes de construir de novo.");
        }

        return new RestoreResult(true,
                "Biga Market desfeita: " + restored + " bloco(s) restaurado(s).", restored);
    }

    private boolean matchesUuid(World world, String storedUuid) {
        if (storedUuid.isBlank()) {
            return true;
        }
        try {
            return world.getUID().equals(UUID.fromString(storedUuid));
        } catch (IllegalArgumentException erro) {
            return false;
        }
    }

    public record RestoreResult(boolean success, String message, int restoredBlocks) {
    }
}
