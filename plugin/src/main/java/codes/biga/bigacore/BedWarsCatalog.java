package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A planta do BED WARS.
 *
 * <h2>A carência não é escolhida: é medida</h2>
 *
 * A reclamação do gênero é "rush demais, e não dá tempo de proteger a cama", e
 * a proposta que aparece nos fóruns dos jogadores é dar um minuto de proteção.
 * Um minuto não resolve nada: a carência só muda o rush para o minuto
 * seguinte. O que o número precisa ser é o TEMPO DA VIAGEM —
 * {@link #travessiaEntreCamas} mede quantos blocos há de uma cama até a cama
 * de outro time e {@link #segundosAndando} diz quanto isso leva a pé. Com a
 * carência igual a esse tempo, o invasor chega exatamente quando a cama passa
 * a poder ser batida: o rush nunca é de graça e nunca é proibido.
 *
 * <p>{@link BedWarsBuilder#conferirGraca} recusa o número que não bate com a
 * distância da arena, nas duas direções — uma carência curta é enfeite com
 * aparência de regra, e uma longa faz o invasor ESPERAR em cima da cama, o
 * que troca a jogada por uma fila.
 *
 * <h2>A lã sai do meio, e por isso não há onde ficar parado</h2>
 *
 * "O colega fica parado em cima do gerador" é a reclamação mais repetida do
 * gênero, e ela é defeito de desenho e não de educação: se o recurso nasce na
 * sua base, ficar em casa É a jogada ótima. Aqui o tear é no meio.
 * {@link #laColhida} põe número na viagem e
 * {@link BedWarsBuilder#conferirTear} prende as duas pontas: a ida tem de
 * render o bastante para valer, e menos do que uma defesa inteira.
 *
 * <h2>Os times entram pelo norte, no sentido do relógio</h2>
 *
 * {@link #centroDaBase} começa no norte como em todas as arenas daqui, para
 * "time 1" querer dizer o mesmo lugar em toda partida. A cama fica RECUADA
 * para fora, de costas para o meio: quem a defende olha para o inimigo, e
 * quem a ataca tem de passar pela base inteira — sem isso, a cama ficaria na
 * boca da passarela e a defesa não teria onde acontecer.
 */
public final class BedWarsCatalog extends CatalogoDeArena {

    /** Quanto um jogador andando cobre num segundo, em blocos. */
    public static final double ANDANDO = 4.317;

    /** O que {@link #baseDoPonto} devolve fora de toda base. */
    public static final int FORA = -1;

    /** Um time: o nome, a cor do texto e os blocos que são dele. */
    public record Time(String nome, String cor, Material la, Material concreto,
                       Material terracota, Material cama) {
    }

    public BedWarsCatalog(BigaCore plugin) {
        super(plugin, "bed-wars.yml", "bed_wars", "bed");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int times() {
        return Math.max(2, Math.min(8, config.getInt("arena.times", 4)));
    }

    public int baseAnel() {
        return Math.max(20, config.getInt("arena.base-anel", 40));
    }

    public int baseRaio() {
        return Math.max(4, config.getInt("arena.base-raio", 7));
    }

    public int baseAltura() {
        return Math.max(1, config.getInt("arena.base-altura", 2));
    }

    public int camaRecuo() {
        return Math.max(1, config.getInt("arena.cama-recuo", 4));
    }

    public int meioRaio() {
        return Math.max(4, config.getInt("arena.meio-raio", 10));
    }

    public int meioAltura() {
        return Math.max(1, config.getInt("arena.meio-altura", 3));
    }

    public int tearAltura() {
        return Math.max(1, config.getInt("arena.tear-altura", 3));
    }

    public int passarelaLargura() {
        return Math.max(1, config.getInt("arena.passarela-largura", 3));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 18));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 10));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(6, config.getInt("regras.queda-abaixo", 20));
    }

    @Override
    public int alcanceDaObra() {
        return baseAnel() + baseRaio() + camaRecuo() + 10;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - baseAltura() - 6;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 8);
    }

    /** Quantos blocos de passarela há de uma base até a beira do meio. */
    public int comprimentoDaPassarela() {
        return Math.max(1, baseAnel() - baseRaio() - meioRaio());
    }

    /** Quantos blocos há de uma cama até a cama de outro time, andando. */
    public int travessiaEntreCamas() {
        return travessiaEntreCamas(baseAnel(), camaRecuo());
    }

    /**
     * De uma cama até a cama de outro time: passa pelo meio, sempre.
     *
     * Duas vezes o anel mais dois recuos, porque o caminho de base a base não
     * é a corda entre elas: as passarelas vão da base ao MEIO, e ir de uma
     * cama a outra é descer um raio inteiro e subir outro. É esta a distância
     * que a carência da cama tem de valer, e não a corda — usar a corda daria
     * um número menor do que qualquer jogador leva para chegar.
     */
    public static int travessiaEntreCamas(int anel, int recuo) {
        return 2 * (Math.max(0, anel) + Math.max(0, recuo));
    }

    /** Quantos segundos um jogador leva para andar tantos blocos. */
    public static int segundosAndando(int blocos) {
        return (int) Math.ceil(Math.max(0, blocos) / ANDANDO);
    }

    public int[] centroDaBase(int k) {
        return centroDaBase(k, times(), baseAnel());
    }

    /** O meio da base do time {@code k}, do norte no sentido do relógio. */
    public static int[] centroDaBase(int k, int times, int anel) {
        if (k < 0 || k >= times) {
            return null;
        }
        double angulo = anguloDoTime(k, times);
        return new int[]{(int) Math.round(Math.cos(angulo) * anel),
                (int) Math.round(Math.sin(angulo) * anel)};
    }

    public static double anguloDoTime(int k, int times) {
        return -Math.PI / 2 + 2 * Math.PI * k / Math.max(1, times);
    }

    public int[] pontoDaCama(int k) {
        return pontoDaCama(k, times(), baseAnel(), camaRecuo());
    }

    /**
     * Onde fica o pé da cama do time {@code k}: recuado para FORA.
     *
     * Para fora, de costas para o meio, para quem defende olhar o inimigo de
     * frente e para quem ataca ter de atravessar a base inteira. Na boca da
     * passarela a cama cairia antes de a defesa existir.
     */
    public static int[] pontoDaCama(int k, int times, int anel, int recuo) {
        double angulo = anguloDoTime(k, times);
        int longe = anel + recuo;
        return new int[]{(int) Math.round(Math.cos(angulo) * longe),
                (int) Math.round(Math.sin(angulo) * longe)};
    }

    /**
     * Para que lado fica o lado de FORA da base do time: o fundo dela.
     *
     * Cardinal e não o ângulo exato, porque é com isto que a cama é posta, e
     * uma cama é de dois blocos: a cabeceira tem de cair num bloco inteiro, e
     * não num ponto arredondado em polar que às vezes pularia um bloco e às
     * vezes voltaria para o mesmo.
     */
    public static BlockFace rumoDaCama(int k, int times) {
        double angulo = anguloDoTime(k, times);
        double dx = Math.cos(angulo);
        double dz = Math.sin(angulo);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** A cabeceira da cama: um bloco adiante do pé, para fora da base. */
    public static int[] cabecaDaCama(int k, int times, int anel, int recuo) {
        int[] pe = pontoDaCama(k, times, anel, recuo);
        BlockFace rumo = rumoDaCama(k, times);
        return new int[]{pe[0] + rumo.getModX(), pe[1] + rumo.getModZ()};
    }

    /** Para que lado a placa da base olha: para o meio, de onde o inimigo vem. */
    public static BlockFace frenteDaPlacaDaBase(int k, int times) {
        return rumoDaCama(k, times).getOppositeFace();
    }

    public int baseDoPonto(double x, double z) {
        return baseDoPonto(x, z, times(), baseAnel(), baseRaio(), camaRecuo());
    }

    /**
     * Em que base o ponto está, ou {@link #FORA}.
     *
     * A base conta como um disco de raio {@code baseRaio} mais o pedaço onde a
     * cama fica, porque a cama está FORA do disco: sem contar o recuo, bater
     * na própria cama seria "fora da base" e a regra de só quebrar o que
     * alguém colocou valeria onde não devia.
     */
    public static int baseDoPonto(double x, double z, int times, int anel,
                                  int raio, int recuo) {
        for (int k = 0; k < times; k++) {
            int[] centro = centroDaBase(k, times, anel);
            if (centro == null) {
                continue;
            }
            if (Math.hypot(x - centro[0], z - centro[1]) <= raio + recuo + 1.5) {
                return k;
            }
        }
        return FORA;
    }

    public boolean noMeio(double x, double z) {
        return Math.hypot(x, z) <= meioRaio() + 0.5;
    }

    /**
     * Se o ponto está em cima de alguma passarela.
     *
     * Mede a distância do ponto até o SEGMENTO que vai do meio à base, e não
     * o ângulo: por ângulo, uma faixa de três blocos de largura perto do meio
     * viraria um leque de vinte blocos na beira da base, e metade da arena
     * contaria como passarela.
     */
    public static boolean naPassarela(double x, double z, int times, int anel,
                                      int raio, int meioRaio, int largura) {
        double meia = Math.max(1, largura) / 2.0 + 0.5;
        double longe = Math.hypot(x, z);
        if (longe < meioRaio - 0.5 || longe > anel - raio + 0.5) {
            return false;
        }
        for (int k = 0; k < times; k++) {
            double angulo = anguloDoTime(k, times);
            double dx = Math.cos(angulo);
            double dz = Math.sin(angulo);
            // A projeção no raio do time, e a sobra perpendicular a ele.
            double ao = x * dx + z * dz;
            if (ao <= 0) {
                continue;
            }
            double sobra = Math.abs(-x * dz + z * dx);
            if (sobra <= meia) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Os times
    // ------------------------------------------------------------------

    /** Os times do yml, na ordem em que ocupam o anel. */
    public List<Time> listaDeTimes() {
        List<Time> saida = new ArrayList<>();
        List<Map<?, ?>> cru = config.getMapList("times");
        for (int k = 0; k < cru.size(); k++) {
            Map<?, ?> item = cru.get(k);
            saida.add(new Time(
                    texto(item, "nome", "TIME " + (k + 1)),
                    texto(item, "cor", corDoTexto()),
                    material(item, "la", Material.WHITE_WOOL),
                    material(item, "concreto", Material.WHITE_CONCRETE),
                    material(item, "terracota", Material.WHITE_TERRACOTTA),
                    material(item, "cama", Material.WHITE_BED)));
        }
        return List.copyOf(saida);
    }

    /** Quantos times o yml descreve de verdade. */
    public int quantosTimesDescritos() {
        return listaDeTimes().size();
    }

    public Time time(int k) {
        List<Time> lista = listaDeTimes();
        if (lista.isEmpty()) {
            return new Time("TIME " + (k + 1), corDoTexto(), Material.WHITE_WOOL,
                    Material.WHITE_CONCRETE, Material.WHITE_TERRACOTTA,
                    Material.WHITE_BED);
        }
        return lista.get(Math.max(0, Math.min(lista.size() - 1, k)));
    }

    private static String texto(Map<?, ?> item, String chave, String padrao) {
        Object valor = item.get(chave);
        return valor == null ? padrao : String.valueOf(valor);
    }

    private Material material(Map<?, ?> item, String chave, Material padrao) {
        Object valor = item.get(chave);
        if (valor == null) {
            return padrao;
        }
        Material achado = Material.matchMaterial(
                String.valueOf(valor).trim().toUpperCase(Locale.ROOT));
        if (achado == null) {
            plugin.getLogger().warning("[bed] Material desconhecido em times."
                    + chave + ": " + valor + " — usando " + padrao + ".");
            return padrao;
        }
        return achado;
    }

    /**
     * Quantos times a partida usa de verdade.
     *
     * Com três jogadores, três times de um — e não quatro, sendo um vazio: um
     * time vazio teria cama de pé até o fim da partida e o placar diria que
     * alguém está ganhando sem jogar.
     */
    public static int timesUsados(int quantos, int times) {
        return Math.max(1, Math.min(Math.max(1, times), Math.max(1, quantos)));
    }

    /**
     * Quem fica em que time: um de cada vez, dando a volta.
     *
     * Um de cada vez, e não "enche o primeiro time e passa para o segundo".
     * Enchendo, oito jogadores viram 4/4 e os times verde e amarelo ficam
     * vazios com a cama de pé até o fim — e o placar diria que alguém está
     * ganhando sem jogar. Dando a volta, a diferença entre o time mais cheio
     * e o mais vazio nunca passa de um, e {@link #timesUsados} garante que
     * nenhum time entra na partida sem gente.
     */
    public static List<Integer> distribuirTimes(int quantos, int times) {
        int usados = timesUsados(quantos, times);
        List<Integer> saida = new ArrayList<>();
        for (int k = 0; k < Math.max(0, quantos); k++) {
            saida.add(k % usados);
        }
        return List.copyOf(saida);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int vidaCheia() {
        return Math.max(10, config.getInt("regras.vida-cheia", 100));
    }

    public int periodoDoGolpe() {
        return Math.max(4, config.getInt("regras.periodo-do-golpe", 13));
    }

    public int danoDoGolpe() {
        return Math.max(1, config.getInt("regras.dano-do-golpe", 20));
    }

    public double empurrao() {
        return Math.max(0, config.getDouble("regras.empurrao", 0.5));
    }

    public double empurraoAltura() {
        return Math.max(0, config.getDouble("regras.empurrao-altura", 0.3));
    }

    public int golpesDaCama() {
        return Math.max(1, config.getInt("regras.golpes-da-cama", 8));
    }

    public int periodoDaCama() {
        return Math.max(2, config.getInt("regras.periodo-da-cama", 16));
    }

    public int curaDaCama() {
        return Math.max(1, config.getInt("regras.cura-da-cama", 8));
    }

    public int vidasDoTime() {
        return Math.max(1, config.getInt("regras.vidas-do-time", 3));
    }

    public int respawnSegundos() {
        return Math.max(1, config.getInt("regras.respawn-segundos", 5));
    }

    public int laDoTear() {
        return Math.max(1, config.getInt("regras.la-do-tear", 2));
    }

    public int tearSegundos() {
        return Math.max(1, config.getInt("regras.tear-segundos", 2));
    }

    public int laMaxima() {
        return Math.max(8, config.getInt("regras.la-maxima", 64));
    }

    public int laDaDefesa() {
        return Math.max(4, config.getInt("regras.la-da-defesa", 24));
    }

    public int gracaSegundos() {
        return Math.max(0, config.getInt("regras.graca-segundos", 20));
    }

    public int pontosPorGolpeNaCama() {
        return Math.max(0, config.getInt("regras.pontos-por-golpe-na-cama", 10));
    }

    public int pontosPorCama() {
        return Math.max(0, config.getInt("regras.pontos-por-cama", 150));
    }

    public int pontosPorEliminacao() {
        return Math.max(0, config.getInt("regras.pontos-por-eliminacao", 40));
    }

    // ------------------------------------------------------------------
    //  As contas do jogo
    // ------------------------------------------------------------------

    /** Quantos segundos de golpe a cama custa, se ninguém atrapalhar. */
    public static double tempoDeQuebrarACama(int golpes, int periodo) {
        return Math.max(0, golpes) * Math.max(0, periodo) / 20.0;
    }

    /** Quantos segundos a cama leva para voltar inteira, sem ninguém nela. */
    public static int tempoDeCurarACama(int golpes, int cura) {
        return Math.max(0, golpes) * Math.max(0, cura);
    }

    /** Quanta lã o tear rende a quem fica tantos segundos no meio. */
    public static int laColhida(double segundos, int laDoTear, int tearSegundos) {
        if (segundos <= 0 || tearSegundos <= 0) {
            return 0;
        }
        return (int) (segundos / tearSegundos) * Math.max(0, laDoTear);
    }

    /**
     * Quanta lã uma viagem ao meio rende, se ela for de ida e volta.
     *
     * O tempo de colher é o tempo de ANDAR, não um número escolhido: a
     * viagem honesta é ir, ficar no meio o mesmo tanto que levou para chegar,
     * e voltar. Quem fica mais rende mais e corre mais risco, que é o que se
     * quer.
     */
    public static int laDeUmaViagem(int anel, int meioRaio, int laDoTear,
                                    int tearSegundos) {
        double ida = Math.max(0, anel - meioRaio) / ANDANDO;
        return laColhida(2 * ida, laDoTear, tearSegundos);
    }

    /**
     * De que lado da cama o invasor está: norte, sul, leste ou oeste.
     *
     * É o que transforma o aviso em jogada. Sem o lado, o defensor corre
     * para a cama e dá de cara com o invasor; com o lado, ele escolhe por
     * onde chegar. Cardinal e não o ângulo, porque "nordeste a 37 graus" não
     * é uma informação que se use correndo.
     */
    public static String ladoDaInvasao(double x, double z, int[] cama) {
        if (cama == null) {
            return "algum lado";
        }
        double dx = x - cama[0];
        double dz = z - cama[1];
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) {
            return "cima dela";
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "leste" : "oeste";
        }
        return dz > 0 ? "sul" : "norte";
    }

    /** Quantos golpes cheios derrubam alguém de vida cheia. */
    public static double golpesParaDerrubar(int vidaCheia, int dano) {
        return dano <= 0 ? Double.MAX_VALUE : vidaCheia / (double) dano;
    }
}
