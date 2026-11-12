package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A planta da Fuga da Lava — e a rampa, que é GERADA aqui.
 *
 * <h2>A rampa é uma hélice, e isso resolve três coisas de uma vez</h2>
 *
 * Sobe sempre, cabe num poço estreito e nunca passa por cima de si mesma com
 * pouca folga: a cada volta ela ganha a circunferência inteira dividida pelo
 * passo, que com raio catorze e passo três dá vinte e nove fiadas de pé
 * direito. Uma escada reta precisaria de um poço do tamanho da escada, e uma
 * torre de plataformas soltas precisaria de um teste de alcançabilidade por
 * grafo. A hélice se confere andando por ela.
 *
 * <h2>A lava sobe por conta, e a conta é pura</h2>
 *
 * {@link #fiadasSubidas} transforma segundos em fiadas. Pura e monótona de
 * propósito: é o número que decide quem morre, e número que decide quem morre
 * não pode depender de quantas vezes o tique rodou nem de o servidor ter
 * engasgado. Se a rodada travar meio segundo, a lava continua onde o relógio
 * diz, e não meio segundo atrás.
 */
public final class LavaCatalog extends CatalogoDeArena {

    /** Os tipos de célula da rampa. */
    public static final int RAMPA = 0;
    public static final int BORDA = 1;
    public static final int RACHADA = 2;
    public static final int LARGADA = 3;
    public static final int SAIDA = 4;

    /** De quanto em quanto se anda a hélice ao gerá-la. */
    public static final double AMOSTRA = 0.25;

    private List<double[]> helice;
    private Map<Long, Integer> celulas;

    public LavaCatalog(BigaCore plugin) {
        super(plugin, "lava.yml", "lava", "lava");
    }

    @Override
    protected void aoRecarregar() {
        helice = null;
        celulas = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    /** A fiada do fundo do poço: onde a lava nasce e onde se larga. */
    @Override
    public int topoY() {
        return config.getInt("arena.y", 60);
    }

    public int altura() {
        return Math.max(20, config.getInt("arena.altura", 60));
    }

    /** A fiada da saída, no alto da rampa. */
    public int cumeY() {
        return topoY() + altura();
    }

    public int raio() {
        return Math.max(8, config.getInt("arena.raio", 14));
    }

    public int largura() {
        int pedido = Math.max(2, config.getInt("arena.largura", 3));
        return pedido % 2 == 0 ? pedido + 1 : pedido;
    }

    public int passoDaSubida() {
        return Math.max(2, config.getInt("arena.passo-da-subida", 4));
    }

    public int pilarRaio() {
        return Math.max(1, config.getInt("arena.pilar-raio", 3));
    }

    public int muroRecuo() {
        return Math.max(2, config.getInt("arena.muro-recuo", 3));
    }

    public int muroRaio() {
        return raio() + largura() / 2 + muroRecuo();
    }

    public int vaoACada() {
        return Math.max(6, config.getInt("arena.vao-a-cada", 20));
    }

    public int vaoTamanho() {
        return Math.max(1, config.getInt("arena.vao-tamanho", 3));
    }

    public int rachadaACada() {
        return Math.max(5, config.getInt("arena.rachada-a-cada", 13));
    }

    public int rachadaTamanho() {
        return Math.max(1, config.getInt("arena.rachada-tamanho", 2));
    }

    public int saguaoFolga() {
        return Math.max(6, config.getInt("arena.saguao-folga", 10));
    }

    /**
     * O saguão fica acima do CUME, não acima do fundo.
     *
     * Como no parkour: a arena é vertical. Um saguão medido do fundo nasceria
     * no meio da rampa, e a sala de espera teria lava passando por dentro.
     */
    @Override
    public int saguaoY() {
        return cumeY() + saguaoFolga();
    }

    /** A linha de queda não é usada: quem elimina aqui é a lava. */
    @Override
    public int quedaY() {
        return topoY() - 4;
    }

    @Override
    public int alcanceDaObra() {
        return muroRaio() + 4;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 6;
    }

    @Override
    public int tetoDaObra() {
        return saguaoY() + saguaoParapeito() + 6;
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int carenciaSegundos() {
        return Math.max(0, config.getInt("regras.carencia-segundos", 10));
    }

    public int segundosPorFiada() {
        return Math.max(1, config.getInt("regras.segundos-por-fiada", 3));
    }

    public int aceleraACada() {
        return Math.max(1, config.getInt("regras.acelera-a-cada", 12));
    }

    public int rachadaTiques() {
        return Math.max(5, config.getInt("regras.rachada-tiques", 30));
    }

    /** Os materiais que o jogo pode apagar da rampa — e só eles. */
    public Set<Material> materiaisQueCedem() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("rachada", Material.CRACKED_POLISHED_BLACKSTONE_BRICKS));
        return saida;
    }

    // ------------------------------------------------------------------
    //  A lava
    // ------------------------------------------------------------------

    /**
     * Quantas fiadas a lava já subiu com tantos segundos de rodada.
     *
     * A cada {@code aceleraACada} fiadas ela fica um segundo mais rápida, até
     * o mínimo de um segundo por fiada. É o que faz a partida terminar: sem
     * aceleração, uma rampa longa o bastante para ser interessante é longa
     * demais para a lava alcançar, e a rodada acabaria sempre no relógio.
     */
    public static int fiadasSubidas(int segundos, int segundosPorFiada,
                                    int aceleraACada) {
        int subidas = 0;
        int relogio = 0;
        int custo = Math.max(1, segundosPorFiada);
        while (relogio + custo <= segundos) {
            relogio += custo;
            subidas++;
            if (subidas % Math.max(1, aceleraACada) == 0) {
                custo = Math.max(1, custo - 1);
            }
        }
        return subidas;
    }

    /** Quantos segundos a lava leva para chegar a tantas fiadas. */
    public static int segundosAte(int fiadas, int segundosPorFiada, int aceleraACada) {
        int relogio = 0;
        int custo = Math.max(1, segundosPorFiada);
        for (int subidas = 1; subidas <= fiadas; subidas++) {
            relogio += custo;
            if (subidas % Math.max(1, aceleraACada) == 0) {
                custo = Math.max(1, custo - 1);
            }
        }
        return relogio;
    }

    /**
     * A fiada mais alta já tomada pela lava, com tantos segundos de rodada.
     *
     * Começa uma fiada ABAIXO do chão do poço: antes da primeira subida não
     * há lava nenhuma, e o chão é chão. A carência é descontada aqui e não no
     * jogo para que a conta inteira — "em que fiada está a lava neste
     * segundo?" — tenha uma resposta só, conferível sem servidor.
     */
    public int nivelDaLava(int segundos) {
        int andando = Math.max(0, segundos - carenciaSegundos());
        return topoY() - 1
                + fiadasSubidas(andando, segundosPorFiada(), aceleraACada());
    }

    // ------------------------------------------------------------------
    //  A rampa
    // ------------------------------------------------------------------

    /** Quantos blocos de caminhada a rampa tem, do fundo ao cume. */
    public int comprimentoDaRampa() {
        return altura() * passoDaSubida();
    }

    public List<double[]> helice() {
        if (helice == null) {
            helice = helice(raio(), passoDaSubida(), altura(), topoY(), AMOSTRA);
        }
        return helice;
    }

    /**
     * A hélice, ponto a ponto: {x, y, z, caminhada}.
     *
     * Amostrada fino porque o que vai virar bloco é o arredondamento disto:
     * com passo de um bloco a borda de fora da curva fica com buraco, e
     * buraco na rampa é queda sem erro de ninguém.
     */
    public static List<double[]> helice(double raio, int passoDaSubida, int altura,
                                        int fundo, double amostra) {
        List<double[]> saida = new ArrayList<>();
        double total = (double) altura * passoDaSubida;
        for (double s = 0; s <= total; s += amostra) {
            double t = s / raio;
            int y = fundo + 1 + (int) Math.floor(s / passoDaSubida);
            saida.add(new double[]{raio * Math.cos(t), y, raio * Math.sin(t), s});
        }
        return saida;
    }

    public Map<Long, Integer> celulas() {
        if (celulas == null) {
            celulas = celulas(helice(), largura(), vaoACada(), vaoTamanho(),
                    rachadaACada(), rachadaTamanho());
        }
        return celulas;
    }

    /**
     * As células da rampa: de posição para tipo de bloco.
     *
     * <h2>Onde ficam os vãos</h2>
     *
     * De tantos em tantos blocos de caminhada, e nunca nos dez primeiros nem
     * nos dez últimos: vão na largada é queda antes de começar, e vão colado
     * na saída é a rodada decidida por um salto cego.
     *
     * <h2>Por que a rachada nunca encosta no vão</h2>
     *
     * Pedra que cede na beirada de um buraco é morte sem escolha: o jogador
     * pisa para saltar e o chão some no impulso. Quando os dois caem no mesmo
     * lugar, o vão ganha e a rachada é adiada.
     */
    public static Map<Long, Integer> celulas(List<double[]> helice, int largura,
                                             int vaoACada, int vaoTamanho,
                                             int rachadaACada, int rachadaTamanho) {
        Map<Long, Integer> saida = new LinkedHashMap<>();
        double meia = (largura - 1) / 2.0;
        double total = helice.get(helice.size() - 1)[3];

        for (double[] ponto : helice) {
            double s = ponto[3];
            boolean comeco = s < 10;
            boolean fim = s > total - 10;

            if (!comeco && !fim && Math.floorMod((int) s, vaoACada) < vaoTamanho) {
                continue;
            }
            boolean rachada = !comeco && !fim
                    && Math.floorMod((int) s, rachadaACada) < rachadaTamanho
                    && !pertoDeVao((int) s, vaoACada, vaoTamanho);

            double r = Math.hypot(ponto[0], ponto[2]);
            double ux = ponto[0] / r;
            double uz = ponto[2] / r;
            int y = (int) ponto[1];

            for (double off = -meia; off <= meia; off += 0.5) {
                int x = (int) Math.floor(ponto[0] + ux * off + 0.5);
                int z = (int) Math.floor(ponto[2] + uz * off + 0.5);
                int tipo;
                if (comeco) {
                    tipo = LARGADA;
                } else if (fim) {
                    tipo = SAIDA;
                } else if (rachada) {
                    tipo = RACHADA;
                } else if (Math.abs(off) > meia - 0.5) {
                    tipo = BORDA;
                } else {
                    tipo = RAMPA;
                }
                long chave = ArenaComum.chave(x, y, z);
                Integer antes = saida.get(chave);
                if (antes == null || tipo > antes) {
                    saida.put(chave, tipo);
                }
            }
        }
        return saida;
    }

    /** Está a um passo de um vão? Então não cede. */
    private static boolean pertoDeVao(int s, int vaoACada, int vaoTamanho) {
        for (int d = -2; d <= 2; d++) {
            if (Math.floorMod(s + d, vaoACada) < vaoTamanho) {
                return true;
            }
        }
        return false;
    }
}
