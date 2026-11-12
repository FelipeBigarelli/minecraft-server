package codes.biga.bigacore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Onde as tags compradas ficam guardadas.
 *
 * <p>Arquivo separado do tags.yml de propósito, e pelo mesmo motivo das
 * encomendas: tags.yml é REGRA, e vem dentro do jar — um update do plugin
 * reescreve regra sem dó. tags-jogadores.yml é COISA DE JOGADOR, comprada com
 * dinheiro, e não pode sumir numa atualização.
 */
public final class TagStore {

    /**
     * @param texto      o apelido, já aprovado pela moderação
     * @param cor        nome de cor do Adventure, ou #RRGGBB
     * @param moldura    a cor dos colchetes. Vazio usa a do tags.yml — é o
     *                   caso de toda tag comprada. Só os estilos da
     *                   administração pintam o colchete de outra coisa, e é
     *                   isso que faz [DONO] bater com o prefixo do chat.
     * @param comprouCor se ele pagou o direito de escolher cor
     */
    public record Tag(String texto, String cor, String moldura,
                      boolean comprouCor, long criadaEm) {

        /** Compatível com o arquivo antigo, que não tinha moldura. */
        public Tag(String texto, String cor, boolean comprouCor, long criadaEm) {
            this(texto, cor, "", comprouCor, criadaEm);
        }
    }

    private static final String ARQUIVO = "tags-jogadores.yml";

    private final BigaCore plugin;
    private final Map<UUID, Tag> tags = new HashMap<>();

    public TagStore(BigaCore plugin) {
        this.plugin = plugin;
        carregar();
    }

    public Optional<Tag> de(UUID jogador) {
        return Optional.ofNullable(tags.get(jogador));
    }

    public Map<UUID, Tag> todas() {
        return Map.copyOf(tags);
    }

    public void guardar(UUID jogador, Tag tag) {
        tags.put(jogador, tag);
        salvar();
    }

    public void remover(UUID jogador) {
        if (tags.remove(jogador) != null) {
            salvar();
        }
    }

    private File arquivo() {
        return new File(plugin.getDataFolder(), ARQUIVO);
    }

    private void carregar() {
        File file = arquivo();
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection raiz = yaml.getConfigurationSection("tags");
        if (raiz == null) {
            return;
        }

        for (String chave : raiz.getKeys(false)) {
            ConfigurationSection item = raiz.getConfigurationSection(chave);
            if (item == null) {
                continue;
            }
            try {
                tags.put(UUID.fromString(chave), new Tag(
                        item.getString("texto", ""),
                        item.getString("cor", "gold"),
                        item.getString("moldura", ""),
                        item.getBoolean("comprou-cor", false),
                        item.getLong("criada-em", 0L)));
            } catch (IllegalArgumentException erro) {
                plugin.getLogger().warning("[tag] UUID inválido em " + ARQUIVO + ": " + chave);
            }
        }

        plugin.getLogger().info("[tag] " + tags.size() + " tag(s) carregada(s).");
    }

    private void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Tag> entrada : tags.entrySet()) {
            String base = "tags." + entrada.getKey();
            yaml.set(base + ".texto", entrada.getValue().texto());
            yaml.set(base + ".cor", entrada.getValue().cor());
            if (!entrada.getValue().moldura().isBlank()) {
                yaml.set(base + ".moldura", entrada.getValue().moldura());
            }
            yaml.set(base + ".comprou-cor", entrada.getValue().comprouCor());
            yaml.set(base + ".criada-em", entrada.getValue().criadaEm());
        }

        try {
            yaml.save(arquivo());
        } catch (IOException erro) {
            // Gritar no console importa: uma tag paga que não gravou é
            // dinheiro cobrado por nada, e o jogador só descobre no relog.
            plugin.getLogger().severe("[tag] Falha ao gravar " + ARQUIVO
                    + " — tag comprada pode se perder no restart: " + erro.getMessage());
        }
    }
}
