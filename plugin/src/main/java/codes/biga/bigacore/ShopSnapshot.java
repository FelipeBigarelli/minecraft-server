package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot de rollback da Biga Market.
 *
 * <h2>Escopo deliberadamente pequeno</h2>
 *
 * Salvar o mundo inteiro seria caro e perigoso. Este snapshot guarda apenas o
 * paralelepípedo que o gerador realmente toca: a footprint 31x29 da loja, do
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
            String arquivado = arquivarSeMundoMorreu(target);
            if (arquivado != null) {
                plugin.getLogger().info(arquivado);
            } else {
                return "Já existe um snapshot em " + FILE_NAME
                        + ". Rode /biga loja desfazer CONFIRMAR ou renomeie o arquivo antes.";
            }
        }

        int minY = Math.max(world.getMinHeight(), survey.minGroundY());
        int maxY = Math.min(world.getMaxHeight() - 1, floorY + SpawnShopBuilder.BUILD_HEIGHT);
        if (maxY < minY) {
            return "Volume de snapshot inválido (" + minY + ".." + maxY + ").";
        }

        // O volume copiado tem DOIS retangulos, e nao um so.
        //
        // Dentro da footprint vai tudo, do chao ao topo do telhado. Fora
        // dela, na praca, vai so uma faixa rasa: ali a obra assenta piso e
        // levanta poste, banco e arvore pequena — nada passa de PRACA_ALTURA.
        //
        // Copiar o envelope inteiro na altura cheia multiplicaria o arquivo
        // por tres sem devolver um bloco a mais no desfazer. E copiar SO a
        // footprint, que era o que acontecia antes da praca existir, deixaria
        // calcada, alameda e arvore de pe depois de /biga loja desfazer — a
        // loja sumiria e o quintal ficaria.
        List<String> blocks = new ArrayList<>();
        for (int u = -SpawnShopBuilder.ENVELOPE_HALF; u <= SpawnShopBuilder.ENVELOPE_HALF; u++) {
            for (int v = SpawnShopBuilder.ENVELOPE_MIN_V;
                    v <= SpawnShopBuilder.ENVELOPE_MAX_V; v++) {

                int topo = SpawnShopBuilder.dentroDaFootprint(u, v)
                        ? maxY
                        : Math.min(maxY, floorY + SpawnShopBuilder.PRACA_ALTURA);

                for (int y = minY; y <= topo; y++) {
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
                    "O mundo '" + storedName + "' foi recriado desde este snapshot (UUID diferente),"
                            + " então restaurar poria blocos antigos em coordenadas de outro mundo."
                            + " Restauração recusada. O próximo /biga loja criar arquiva este"
                            + " snapshot sozinho e segue em frente.", 0);
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

    /**
     * Arquiva um snapshot que nunca mais poderá ser restaurado.
     *
     * <h2>O impasse que isto resolve</h2>
     *
     * O mundo de terrenos é recriado de vez em quando — {@code /cp generate}
     * depois de apagar a pasta. O mundo novo nasce com outro UUID. A partir
     * daí o snapshot antigo trava os dois caminhos ao mesmo tempo:
     * {@code /biga loja criar} recusa porque já existe snapshot, e
     * {@code /biga loja desfazer} recusa porque o UUID não bate. Não sobra
     * saída nenhuma dentro do jogo — em 12/08/2026 e de novo em 11/11/2026 a
     * única saída foi renomear o arquivo na mão, pelo terminal.
     *
     * <h2>Por que arquivar é seguro aqui</h2>
     *
     * Só arquiva quando o mundo do snapshot <b>está carregado e tem outro
     * UUID</b>. Isso é prova de que o mundo foi recriado: os blocos guardados
     * descrevem coordenadas de um mundo que não existe mais, e aplicá-los seria
     * errado, não útil. Um snapshot válido nunca cai nesta condição.
     *
     * Mundo apenas descarregado NÃO entra aqui de propósito — ele pode voltar
     * com o mesmo UUID, e aí o snapshot ainda vale. Nesse caso o comando segue
     * recusando, que é o certo.
     *
     * E arquivar não é apagar: o arquivo continua no disco, com a data no
     * nome, igual ao {@code loja-snapshot.yml.mundo-antigo-20260812} que
     * sobrou da primeira vez que isso aconteceu.
     *
     * @return a mensagem de log quando arquivou, ou null quando não havia
     *         motivo para arquivar.
     */
    private String arquivarSeMundoMorreu(File target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        String storedName = yaml.getString("mundo-nome", "");
        String storedUuid = yaml.getString("mundo-uuid", "");
        if (storedName.isBlank() || storedUuid.isBlank()) {
            return null;
        }

        World atual = Bukkit.getWorld(storedName);
        if (!snapshotOrfao(storedName, storedUuid, atual == null ? null : atual.getUID())) {
            return null;
        }

        String carimbo = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File destino = new File(target.getParentFile(), FILE_NAME + ".mundo-antigo-" + carimbo);
        if (!target.renameTo(destino)) {
            return null;
        }
        return "Snapshot antigo arquivado como " + destino.getName()
                + ": o mundo '" + storedName + "' foi recriado e aquele volume não existe mais.";
    }

    /**
     * O snapshot descreve um mundo que não existe mais?
     *
     * Estático e visível ao teste porque é a decisão que move um arquivo de
     * rollback de lugar. Errar para o lado do "sim" descarta a única forma de
     * desfazer uma construção.
     *
     * @param uuidCarregado UUID do mundo que hoje atende por {@code storedName},
     *                      ou null se nenhum mundo com esse nome está carregado.
     */
    static boolean snapshotOrfao(String storedName, String storedUuid, UUID uuidCarregado) {
        if (storedName == null || storedName.isBlank()
                || storedUuid == null || storedUuid.isBlank()) {
            // Snapshot sem identificação de mundo é antigo demais para julgar.
            // Fica onde está e o comando segue recusando — alguém olha na mão.
            return false;
        }
        if (uuidCarregado == null) {
            // Mundo só descarregado pode voltar com o MESMO UUID, e aí o
            // snapshot ainda vale. Nunca arquiva neste caso.
            return false;
        }
        try {
            return !uuidCarregado.equals(UUID.fromString(storedUuid));
        } catch (IllegalArgumentException ilegivel) {
            // UUID gravado corrompido com o mundo carregado: o restore já
            // recusaria para sempre, então o arquivo só atrapalha.
            return true;
        }
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
