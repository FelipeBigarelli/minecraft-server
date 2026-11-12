package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A planta e as regras do TNT Run, lidas do tnt-run.yml.
 *
 * <h2>A geometria sai de quatro números</h2>
 *
 * Raio, quantos pisos, espessura e vão. Tudo o mais — onde cada piso fica,
 * onde elimina, onde o saguão fica, a que distância ficam os marcos — é
 * calculado daqui. É de propósito: num arquivo com vinte números soltos, dois
 * deles passam a brigar no dia em que alguém muda um e esquece o outro, e a
 * briga aparece como "o jogo não funciona mais" sem dizer onde.
 *
 * <h2>O número que manda no jogo é o vão</h2>
 *
 * Três de ar entre um piso e o de baixo. Com três não se pula de volta — o
 * pulo sobe 1,25 e precisaria de quatro de pé livre. Com dois, pula-se, e aí
 * cair de piso deixa de custar e o jogo inteiro perde o sentido. Por isso o
 * vão é conferido no construtor e não só documentado.
 */
public final class TntRunCatalog {

    /** O vão mínimo que impede voltar ao piso de cima num pulo. */
    public static final int VAO_MINIMO = 3;

    private final BigaCore plugin;
    private YamlConfiguration config;

    public TntRunCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "tnt-run.yml"));
    }

    // ------------------------------------------------------------------
    //  Mundo
    // ------------------------------------------------------------------

    public String mundo() {
        String nome = config.getString("mundo", "tnt_run");
        return nome == null || nome.isBlank() ? "tnt_run" : nome;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int topoY() {
        return config.getInt("arena.y", 100);
    }

    public int raio() {
        return Math.max(8, config.getInt("arena.raio", 24));
    }

    public int pisos() {
        return Math.max(1, config.getInt("arena.pisos", 3));
    }

    public int espessura() {
        return Math.max(1, config.getInt("arena.espessura", 2));
    }

    public int vao() {
        return Math.max(VAO_MINIMO, config.getInt("arena.vao", 3));
    }

    public int beiraAneis() {
        return Math.max(0, config.getInt("arena.beira-aneis", 2));
    }

    /** De quanto em quanto um piso está abaixo do de cima. */
    public int passoEntrePisos() {
        return espessura() + vao();
    }

    /** O topo do piso número {@code camada}, contando 0 de cima para baixo. */
    public int topoDoPiso(int camada) {
        return topoY() - camada * passoEntrePisos();
    }

    /** A fiada de baixo do piso número {@code camada}. */
    public int baseDoPiso(int camada) {
        return topoDoPiso(camada) - espessura() + 1;
    }

    /** A fiada mais baixa de toda a arena. */
    public int fundoDaArena() {
        return baseDoPiso(pisos() - 1);
    }

    /**
     * Abaixo desta altura o jogador está eliminado.
     *
     * Precisa de folga sobre o fundo: quem está em pé no último piso tem o pé
     * na base dele, e um limite grudado no fundo eliminaria de pé.
     */
    public int quedaY() {
        return fundoDaArena() - Math.max(2, config.getInt("regras.queda-abaixo", 8));
    }

    public int saguaoAltura() {
        return Math.max(8, config.getInt("arena.saguao-altura", 18));
    }

    /** A fiada do piso do saguão. */
    public int saguaoY() {
        return topoY() + saguaoAltura();
    }

    public int saguaoRaio() {
        return Math.max(4, config.getInt("arena.saguao-raio", 9));
    }

    public int saguaoParapeito() {
        return Math.max(3, config.getInt("arena.saguao-parapeito", 4));
    }

    public int marcoRecuo() {
        return Math.max(6, config.getInt("arena.marco-recuo", 8));
    }

    public int marcoAltura() {
        return Math.max(10, config.getInt("arena.marco-altura", 30));
    }

    /** Distância do centro até o eixo de cada marco. */
    public int marcoDistancia() {
        return raio() + marcoRecuo();
    }

    /**
     * O retângulo que a obra ocupa, em raio a partir do centro.
     *
     * É com ele que o construtor confere se o lugar está vazio e é ele que o
     * desfazer apaga. Generoso: o marco tem três de largura e o desfazer que
     * deixa pedaço para trás é pior do que o que apaga ar num mundo de ar.
     */
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    /** Da fiada mais baixa que a obra toca até a mais alta. */
    public int fundoDaObra() {
        return fundoDaArena() - 10;
    }

    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Paleta
    // ------------------------------------------------------------------

    public Material cor(String chave, Material padrao) {
        String nome = config.getString("paleta." + chave);
        if (nome == null) {
            return padrao;
        }
        Material material = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("[tntrun] Material desconhecido em paleta." + chave
                    + ": " + nome + " — usando " + padrao + ".");
            return padrao;
        }
        return material;
    }

    /**
     * Os quatro materiais que o jogo pode apagar, e SÓ eles.
     *
     * Esta é a trava mais importante do derretimento. Sem ela, um defeito de
     * uma linha no cálculo do bloco do pé passa a comer o que estiver por
     * baixo — vidro do saguão, pilar do marco — e o mundo vai sendo desfeito
     * de rodada em rodada sem ninguém entender por quê. Com ela, o pior caso
     * de um defeito desses é um buraco a mais no piso, que a rodada seguinte
     * refaz.
     */
    public Set<Material> materiaisDoPiso() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("piso", Material.TNT));
        saida.add(cor("piso-base", Material.SAND));
        saida.add(cor("beira", Material.POLISHED_BLACKSTONE));
        saida.add(cor("beira-base", Material.BLACKSTONE));
        return saida;
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int minimo() {
        return Math.max(1, config.getInt("regras.minimo", 2));
    }

    /** O material das placas. Tem de ser placa de PAREDE para encostar no muro. */
    public Material materialDaPlaca() {
        Material achado = cor("placa", Material.CRIMSON_WALL_SIGN);
        if (!org.bukkit.Tag.WALL_SIGNS.isTagged(achado)) {
            plugin.getLogger().warning("[tntrun] paleta.placa (" + achado
                    + ") não é uma placa de parede — usando OAK_WALL_SIGN.");
            return Material.OAK_WALL_SIGN;
        }
        return achado;
    }

    public int maximo() {
        return Math.max(minimo(), config.getInt("regras.maximo", 16));
    }

    public int contagemSegundos() {
        return Math.max(3, config.getInt("regras.contagem-segundos", 20));
    }

    public int derreteTicks() {
        return Math.max(1, config.getInt("regras.derrete-ticks", 8));
    }

    public int fimSegundos() {
        return Math.max(1, config.getInt("regras.fim-segundos", 6));
    }

    public boolean treinoSozinho() {
        return config.getBoolean("regras.treino-sozinho", true);
    }

    // ------------------------------------------------------------------
    //  Textos
    // ------------------------------------------------------------------

    public String titulo() {
        return config.getString("textos.titulo", "TNT RUN");
    }

    public String corDoTexto() {
        return config.getString("textos.cor", "#FF5555");
    }

    public String subtitulo() {
        return config.getString("textos.subtitulo", "");
    }

    public List<String> regrasEscritas() {
        return config.getStringList("textos.regras");
    }

    public double escalaTitulo() {
        return config.getDouble("textos.escala-titulo", 2.6);
    }

    public double escalaPlaca() {
        return config.getDouble("textos.escala-placa", 1.6);
    }

    public int fundoDoTexto() {
        return Math.max(0, Math.min(255, config.getInt("textos.fundo-alpha", 0)));
    }

    public String placa(String chave, String padrao) {
        String valor = config.getString("textos.placa-" + chave);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    // ------------------------------------------------------------------
    //  Conferências — estáticas e puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * As contas que não são deste jogo moram em {@link ArenaComum}.
     *
     * Ficam repetidas aqui como uma linha cada porque é por estes nomes que o
     * jogo e o teste do TNT Run chamam — e porque o dia em que o TNT Run
     * precisar de uma largada diferente da dos outros, é esta linha que muda,
     * e só ela.
     */
    public static boolean noDisco(int x, int z, int raio) {
        return ArenaComum.noDisco(x, z, raio);
    }

    public static boolean naBeira(int x, int z, int raio, int aneis) {
        return ArenaComum.naBeira(x, z, raio, aneis);
    }

    public static List<int[]> largadas(int quantos, int raio) {
        return ArenaComum.largadas(quantos, raio);
    }

    public static boolean rodadaAcabou(int vivos, int comecouCom, boolean treinoSozinho) {
        return ArenaComum.rodadaAcabou(vivos, comecouCom, treinoSozinho);
    }

    public static boolean anunciaContagem(int segundos) {
        return ArenaComum.anunciaContagem(segundos);
    }

    public static String tempo(long ticks) {
        return ArenaComum.tempo(ticks);
    }

}
