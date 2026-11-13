package codes.biga.bigacore;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta da ARENA PVP.
 *
 * <h2>O golpe vale pela prontidão, e essa conta é do Minecraft</h2>
 *
 * A espada do Minecraft tem recarga, e o dano do golpe é
 * {@code 0,2 + p²·0,8}, com {@code p} indo de 0 (acabou de bater) a 1
 * (recarregada). {@link #forcaDoGolpe} é essa conta, e ela é a conta DO JOGO
 * — num jogo em que todos têm a mesma espada e nada mais, o que resta para
 * decidir a luta é onde você está e quando você bate.
 *
 * <p>Pensei em recusar o golpe fraco, como o ARCO recusa o tiro sem puxar, e
 * não dá: o arco tem uma puxada visível e um gesto único; a espada é um
 * clique reflexo no meio da pancadaria, e clique recusado parece lag do
 * servidor. A aritmética castiga mais fundo e ensina melhor, porque o jogador
 * vê o número do seu golpe na tela a cada golpe.
 *
 * <h2>A carência igual ao período é o que mata o atalho</h2>
 *
 * {@link #danoPorSegundo} é a conta inteira: a prontidão de quem bate a cada
 * {@code intervalo} tiques é {@code intervalo/periodo} — porque TODO golpe
 * reinicia a recarga, inclusive o golpe que não causa dano — e dos golpes
 * dados só entram os que caem fora da carência do alvo.
 *
 * <p>Com a carência de 10 tiques do Minecraft e o período de 12,5 da espada,
 * bater a cada 10 tiques rende 87% do ótimo: "clicar rápido" é quase tão bom
 * quanto saber lutar, e esse é o defeito conhecido do PvP de vanilla. Com a
 * carência IGUAL ao período, bater adiantado rende 67% e martelar rende 37%.
 * Não existe atalho quase tão bom, e é só isso que faz do tempo do golpe uma
 * habilidade em vez de um detalhe.
 * {@link ArenaPvpBuilder#conferirGolpe} prende os três números.
 */
public final class ArenaPvpCatalog extends CatalogoDeArena {

    /**
     * O período de recarga da espada de ferro, em tiques.
     *
     * A velocidade de ataque é 1,6 por segundo, e 20/1,6 = 12,5 — arredondado
     * para cima porque tique é inteiro. Fica aqui como padrão e não como
     * verdade: {@code regras.periodo-do-golpe} manda, e
     * {@link ArenaPvp} confere ao começar a rodada o que a espada entregue
     * diz de verdade, pelo {@code getCooldownPeriod} do jogador.
     */
    public static final int TIQUES_DO_GOLPE = 13;

    /** O piso do dano de um golpe sem recarga nenhuma, no Minecraft. */
    public static final double PISO_DA_FORCA = 0.2;

    public ArenaPvpCatalog(BigaCore plugin) {
        super(plugin, "arena-pvp.yml", "arena_pvp", "pvp");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(10, config.getInt("arena.raio", 20));
    }

    public int palanqueRaio() {
        return Math.max(2, config.getInt("arena.palanque-raio", 6));
    }

    public int palanqueAltura() {
        return Math.max(0, config.getInt("arena.palanque-altura", 2));
    }

    public int palanqueDegrau() {
        return Math.max(1, config.getInt("arena.palanque-degrau", 3));
    }

    public int pilarAnel() {
        return Math.max(1, config.getInt("arena.pilar-anel", 13));
    }

    public int pilarAltura() {
        return Math.max(1, config.getInt("arena.pilar-altura", 3));
    }

    public int vaos() {
        return Math.max(0, config.getInt("arena.vaos", 4));
    }

    public int vaoLargura() {
        return Math.max(1, config.getInt("arena.vao-largura", 9));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 14));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 7));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return raio() + 8;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 6;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 8);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int periodoDoGolpe() {
        return Math.max(2, config.getInt("regras.periodo-do-golpe",
                TIQUES_DO_GOLPE));
    }

    public int vidaCheia() {
        return Math.max(10, config.getInt("regras.vida-cheia", 120));
    }

    public int danoPorGolpe() {
        return Math.max(1, config.getInt("regras.dano-por-golpe", 20));
    }

    public int curaPorEliminacao() {
        return Math.max(0, config.getInt("regras.cura-por-eliminacao", 40));
    }

    public double empurrao() {
        return Math.max(0.0, config.getDouble("regras.empurrao", 0.55));
    }

    public double empurraoAltura() {
        return Math.max(0.0, config.getDouble("regras.empurrao-altura", 0.32));
    }

    public int gracaSegundos() {
        return Math.max(0, config.getInt("regras.graca-segundos", 3));
    }

    public int pontosPorEliminacao() {
        return Math.max(0, config.getInt("regras.pontos-por-eliminacao", 40));
    }

    // ------------------------------------------------------------------
    //  As contas do golpe — puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** A prontidão de quem bateu há {@code tiques} tiques, de 0 a 1. */
    public double prontidaoDe(int tiques) {
        return prontidaoDe(tiques, periodoDoGolpe());
    }

    public static double prontidaoDe(int tiques, int periodo) {
        if (periodo <= 0) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0, tiques) / (double) periodo);
    }

    /**
     * A força do golpe para uma prontidão: a conta do Minecraft.
     *
     * {@code 0,2 + p²·0,8}. É quadrática e não linear, e a diferença é o jogo
     * inteiro: com meia recarga o golpe não faz meio dano, faz 40%.
     */
    public static double forcaDoGolpe(double prontidao) {
        double p = Math.min(1.0, Math.max(0.0, prontidao));
        return PISO_DA_FORCA + p * p * (1.0 - PISO_DA_FORCA);
    }

    /** Quanto tira um golpe de prontidão {@code p}, arredondado. */
    public static int danoDoGolpe(double prontidao, int danoCheio) {
        return Math.max(1, (int) Math.round(danoCheio * forcaDoGolpe(prontidao)));
    }

    /** Quanto empurra um golpe de prontidão {@code p}. */
    public static double empurraoDoGolpe(double prontidao, double empurrao) {
        return empurrao * Math.min(1.0, Math.max(0.0, prontidao));
    }

    /**
     * O dano por segundo de quem bate a cada {@code intervalo} tiques.
     *
     * Duas coisas entram: a PRONTIDÃO, que é {@code intervalo/periodo} porque
     * todo golpe reinicia a recarga — inclusive o golpe que a carência comeu;
     * e a CARÊNCIA, que deixa passar um golpe a cada
     * {@code max(intervalo, carencia)} tiques. É a conta que decide se o jogo
     * premia martelar o botão ou esperar a espada.
     */
    public static double danoPorSegundo(int intervalo, int periodo,
                                        int carencia, double dano) {
        if (intervalo < 1) {
            return 0;
        }
        double forca = forcaDoGolpe(prontidaoDe(intervalo, periodo));
        double entre = Math.max(intervalo, Math.max(1, carencia));
        return 20.0 / entre * forca * dano;
    }

    /** Em que intervalo o dano por segundo é maior, procurando até {@code ate}. */
    public static int melhorIntervalo(int periodo, int carencia, int ate) {
        int melhor = 1;
        double maior = 0;
        for (int i = 1; i <= Math.max(1, ate); i++) {
            double d = danoPorSegundo(i, periodo, carencia, 1.0);
            if (d > maior) {
                maior = d;
                melhor = i;
            }
        }
        return melhor;
    }

    /** Quantos segundos de pancada sem parar derrubam alguém. */
    public static double segundosDeUmDuelo(int vidaCheia, int dano, int periodo,
                                           int carencia) {
        double dps = danoPorSegundo(melhorIntervalo(periodo, carencia,
                4 * Math.max(1, periodo)), periodo, carencia, dano);
        return dps <= 0 ? 0 : vidaCheia / dps;
    }

    // ------------------------------------------------------------------
    //  O chão, a beira e os vãos
    // ------------------------------------------------------------------

    public static int blocosDoChao(int raio) {
        int quantos = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (ArenaComum.noDiscoMeio(x, z, raio)) {
                    quantos++;
                }
            }
        }
        return quantos;
    }

    /** Se o ponto está no anel de um bloco da beira do disco. */
    public static boolean noAnelDaBeira(int x, int z, int raio) {
        return ArenaComum.noDiscoMeio(x, z, raio)
                && !ArenaComum.noDiscoMeio(x, z, raio - 1);
    }

    /**
     * Se o ponto da beira cai num vão — isto é, se ali não há parapeito.
     *
     * Os vãos ficam nas diagonais, e os pilares nos rumos, de propósito: um
     * pilar na boca de um vão seria cobertura justamente onde o jogo quer
     * ninguém parado.
     */
    public static boolean noVao(double x, double z, int vaos, int largura,
                                int raio) {
        if (vaos <= 0 || raio <= 0) {
            return false;
        }
        double angulo = Math.atan2(z, x);
        double meioDoVao = largura / 2.0 / raio;
        for (int k = 0; k < vaos; k++) {
            double meio = Math.PI / 4 + k * 2 * Math.PI / vaos;
            double longe = Math.abs(sobra(angulo - meio));
            if (longe <= meioDoVao) {
                return true;
            }
        }
        return false;
    }

    /** O ângulo trazido para dentro de -π..π. */
    private static double sobra(double angulo) {
        double a = angulo;
        while (a <= -Math.PI) {
            a += 2 * Math.PI;
        }
        while (a > Math.PI) {
            a -= 2 * Math.PI;
        }
        return a;
    }

    /** Quantos blocos do anel da beira ficam abertos. */
    public static int blocosAbertos(int raio, int vaos, int largura) {
        int quantos = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (noAnelDaBeira(x, z, raio) && noVao(x, z, vaos, largura, raio)) {
                    quantos++;
                }
            }
        }
        return quantos;
    }

    /** Quantos blocos o anel da beira tem, abertos ou não. */
    public static int blocosDaBeira(int raio) {
        int quantos = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (noAnelDaBeira(x, z, raio)) {
                    quantos++;
                }
            }
        }
        return quantos;
    }

    // ------------------------------------------------------------------
    //  O palanque e os pilares
    // ------------------------------------------------------------------

    public int alturaDoPalanque(double x, double z) {
        return alturaDoPalanque(x, z, palanqueRaio(), palanqueAltura(),
                palanqueDegrau());
    }

    /**
     * Quantas fiadas o palanque tem sob o ponto, de 0 a {@code altura}.
     *
     * Em degraus de {@code degrau} blocos de recuo por fiada: o palanque tem
     * de poder ser subido andando, porque num jogo de empurrão o terreno alto
     * que só se alcança pulando é terreno de quem já está em cima.
     */
    public static int alturaDoPalanque(double x, double z, int palanqueRaio,
                                       int altura, int degrau) {
        double longe = Math.hypot(x, z);
        int alto = 0;
        for (int fiada = 1; fiada <= altura; fiada++) {
            if (longe <= palanqueRaio - (fiada - 1) * (double) degrau + 0.5) {
                alto = fiada;
            }
        }
        return alto;
    }

    public List<int[]> pontosDosPilares() {
        return pontosDosPilares(pilarAnel());
    }

    /** Os quatro pilares, nos rumos. */
    public static List<int[]> pontosDosPilares(int anel) {
        List<int[]> saida = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            double angulo = k * Math.PI / 2;
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return List.copyOf(saida);
    }

    /** Para que lado a placa olha: para o meio do disco. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    // ------------------------------------------------------------------
    //  As largadas
    // ------------------------------------------------------------------

    /**
     * O anel das largadas: entre os pilares e o parapeito.
     *
     * Não passa por {@link ArenaComum#largadas(int, int)} de propósito — ele
     * recua cinco blocos do raio que recebe por dentro, e aqui o anel precisa
     * de posição exata: longe do palanque, para ninguém nascer no terreno
     * alto, e longe da beira, para ninguém nascer num vão.
     */
    public int anelDaLargada() {
        return Math.max(palanqueRaio() + 2, raio() - 4);
    }

    public List<int[]> largadas(int quantos) {
        return largadas(quantos, anelDaLargada());
    }

    public static List<int[]> largadas(int quantos, int anel) {
        List<int[]> saida = new ArrayList<>();
        int quantas = Math.max(2, quantos);
        for (int k = 0; k < quantas; k++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * k / quantas;
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return saida;
    }
}
