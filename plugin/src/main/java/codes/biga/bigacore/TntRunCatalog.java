package codes.biga.bigacore;

import org.bukkit.Material;

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
public final class TntRunCatalog extends CatalogoDeArena {

    /** O vão mínimo que impede voltar ao piso de cima num pulo. */
    public static final int VAO_MINIMO = 3;

    public TntRunCatalog(BigaCore plugin) {
        super(plugin, "tnt-run.yml", "tnt_run", "tntrun");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    @Override
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

    /** De quantos em quantos blocos entra uma lâmpada na capa do piso. */
    public int luzPasso() {
        return Math.max(2, config.getInt("arena.luz-passo", 6));
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
    @Override
    public int quedaY() {
        return fundoDaArena() - Math.max(2, config.getInt("regras.queda-abaixo", 8));
    }

    /** A fiada do piso do saguão. */
    @Override
    public int saguaoY() {
        return topoY() + saguaoAltura();
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
    @Override
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    /** Da fiada mais baixa que a obra toca até a mais alta. */
    @Override
    public int fundoDaObra() {
        return fundoDaArena() - 10;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Paleta
    // ------------------------------------------------------------------

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
        saida.add(cor("piso-luz", Material.SHROOMLIGHT));
        return saida;
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int derreteTicks() {
        return Math.max(1, config.getInt("regras.derrete-ticks", 8));
    }

    // ------------------------------------------------------------------
    //  Textos
    // ------------------------------------------------------------------

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
