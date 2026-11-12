package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * A parte do catálogo que é igual em todo jogo de arena.
 *
 * <h2>O que isto economiza</h2>
 *
 * Cento e cinquenta linhas por jogo, vezes dezoito jogos — mas o que importa
 * não é o tamanho: é que as cento e cinquenta linhas eram as MESMAS, e cópias
 * iguais divergem em silêncio. O sétimo jogo leria {@code textos.cor} com
 * outro padrão, o décimo esqueceria de conferir se a placa é de parede, e
 * cada defeito teria de ser achado de novo em cada cópia.
 *
 * <h2>Como um jogo usa</h2>
 *
 * Estende, passa o nome do arquivo e o do mundo, e acrescenta só a geometria
 * e as regras DELE. Tudo o que este arquivo já responde some do catálogo do
 * jogo.
 */
public abstract class CatalogoDeArena implements PlantaDeArena {

    protected final BigaCore plugin;
    private final String arquivo;
    private final String mundoPadrao;
    private final String etiqueta;
    protected YamlConfiguration config;

    /**
     * @param arquivo     o nome do yml, "spleef.yml"
     * @param mundoPadrao o mundo usado se o arquivo não disser, "spleef"
     * @param etiqueta    como o jogo se chama no log e nos comandos, "spleef"
     */
    protected CatalogoDeArena(BigaCore plugin, String arquivo, String mundoPadrao,
                              String etiqueta) {
        this.plugin = plugin;
        this.arquivo = arquivo;
        this.mundoPadrao = mundoPadrao;
        this.etiqueta = etiqueta;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), arquivo));
        aoRecarregar();
    }

    /** Gancho para o jogo reler o que for só dele. */
    protected void aoRecarregar() {
    }

    public YamlConfiguration config() {
        return config;
    }

    @Override
    public String etiqueta() {
        return etiqueta;
    }

    @Override
    public String mundo() {
        String nome = config.getString("mundo", mundoPadrao);
        return nome == null || nome.isBlank() ? mundoPadrao : nome;
    }

    // ------------------------------------------------------------------
    //  Geometria comum
    // ------------------------------------------------------------------

    @Override
    public int topoY() {
        return config.getInt("arena.y", 100);
    }

    public int saguaoAltura() {
        return Math.max(8, config.getInt("arena.saguao-altura", 16));
    }

    @Override
    public int saguaoY() {
        return topoY() + saguaoAltura();
    }

    @Override
    public int saguaoRaio() {
        return Math.max(4, config.getInt("arena.saguao-raio", 9));
    }

    @Override
    public int saguaoParapeito() {
        return Math.max(3, config.getInt("arena.saguao-parapeito", 4));
    }

    // ------------------------------------------------------------------
    //  Paleta
    // ------------------------------------------------------------------

    @Override
    public Material cor(String chave, Material padrao) {
        String nome = config.getString("paleta." + chave);
        if (nome == null) {
            return padrao;
        }
        Material material = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("[" + etiqueta + "] Material desconhecido em "
                    + "paleta." + chave + ": " + nome + " — usando " + padrao + ".");
            return padrao;
        }
        return material;
    }

    /**
     * O material das placas, conferido.
     *
     * Tem de ser placa DE PAREDE: ela fica pendurada no muro da fachada, e
     * uma placa de chão posta no ar cai no mesmo tique em que nasce — o
     * saguão ficaria sem porta de entrada sem erro nenhum no log.
     */
    @Override
    public Material materialDaPlaca() {
        Material achado = cor("placa", Material.OAK_WALL_SIGN);
        if (!org.bukkit.Tag.WALL_SIGNS.isTagged(achado)) {
            plugin.getLogger().warning("[" + etiqueta + "] paleta.placa (" + achado
                    + ") não é uma placa de parede — usando OAK_WALL_SIGN.");
            return Material.OAK_WALL_SIGN;
        }
        return achado;
    }

    // ------------------------------------------------------------------
    //  Regras comuns
    // ------------------------------------------------------------------

    @Override
    public int minimo() {
        return Math.max(1, config.getInt("regras.minimo", 2));
    }

    @Override
    public int maximo() {
        return Math.max(minimo(), config.getInt("regras.maximo", 16));
    }

    @Override
    public int contagemSegundos() {
        return Math.max(3, config.getInt("regras.contagem-segundos", 20));
    }

    @Override
    public int fimSegundos() {
        return Math.max(1, config.getInt("regras.fim-segundos", 6));
    }

    @Override
    public boolean treinoSozinho() {
        return config.getBoolean("regras.treino-sozinho", true);
    }

    /** O teto absoluto da partida, em segundos. */
    public int tempoLimite() {
        return Math.max(30, config.getInt("regras.tempo-limite", 300));
    }

    // ------------------------------------------------------------------
    //  Textos
    // ------------------------------------------------------------------

    @Override
    public String titulo() {
        return config.getString("textos.titulo", etiqueta.toUpperCase(Locale.ROOT));
    }

    @Override
    public String corDoTexto() {
        return config.getString("textos.cor", "#FFFFFF");
    }

    @Override
    public String subtitulo() {
        return config.getString("textos.subtitulo", "");
    }

    @Override
    public List<String> regrasEscritas() {
        return config.getStringList("textos.regras");
    }

    @Override
    public double escalaTitulo() {
        return config.getDouble("textos.escala-titulo", 2.6);
    }

    @Override
    public double escalaPlaca() {
        return config.getDouble("textos.escala-placa", 1.6);
    }

    @Override
    public int fundoDoTexto() {
        return Math.max(0, Math.min(255, config.getInt("textos.fundo-alpha", 0)));
    }

    @Override
    public String placa(String chave, String padrao) {
        String valor = config.getString("textos.placa-" + chave);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
