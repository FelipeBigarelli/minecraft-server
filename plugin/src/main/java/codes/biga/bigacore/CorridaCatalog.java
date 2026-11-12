package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A planta da Corrida — e a pista, que é GERADA aqui.
 *
 * <h2>Uma pista, calculada num lugar só</h2>
 *
 * O construtor põe blocos no eixo, o jogo testa portões contra o eixo, o
 * teste anda uma volta pelo eixo e o mímico desenha o eixo. Se cada um
 * calculasse o seu, meio bloco de diferença bastaria para o portão ficar
 * fora da pista — e portão fora da pista é uma volta que nunca fecha.
 *
 * <p>Por isso o eixo e os portões ficam guardados: a conta é determinística,
 * mas é uma reamostragem de 2880 pontos, e refazê-la trinta vezes por segundo
 * por jogador seria desperdício. O cache morre no {@link #aoRecarregar()},
 * que é exatamente quando a planta pode ter mudado.
 */
public final class CorridaCatalog extends CatalogoDeArena {

    private List<double[]> eixo;
    private int[] portoes;
    private int[][] atalhos;
    private List<List<PistaFechada.Portao>> planos;

    public CorridaCatalog(BigaCore plugin) {
        super(plugin, "corrida.yml", "corrida", "corrida");
    }

    @Override
    protected void aoRecarregar() {
        eixo = null;
        portoes = null;
        atalhos = null;
        planos = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(16, config.getInt("arena.raio", 36));
    }

    public int onda() {
        return Math.max(0, config.getInt("arena.onda", 9));
    }

    public int voltasDaOnda() {
        return Math.max(2, config.getInt("arena.voltas-da-onda", 3));
    }

    /** A largura da faixa. Sempre ímpar: a pista tem eixo, e eixo é um bloco. */
    public int largura() {
        int pedido = Math.max(3, config.getInt("arena.largura", 7));
        return pedido % 2 == 0 ? pedido + 1 : pedido;
    }

    /** Quantos blocos de pista para cada lado do eixo. */
    public int meiaLargura() {
        return largura() / 2;
    }

    public int muroAltura() {
        return Math.max(1, config.getInt("arena.muro-altura", 2));
    }

    public int quantosPortoes() {
        return Math.max(4, config.getInt("arena.portoes", 8));
    }

    public int atalhoAbertura() {
        return Math.max(20, Math.min(120, config.getInt("arena.atalho-abertura", 72)));
    }

    public int atalhoLargura() {
        return Math.max(1, config.getInt("arena.atalho-largura", 2));
    }

    public int torreRecuo() {
        return Math.max(5, config.getInt("arena.torre-recuo", 8));
    }

    public int torreAltura() {
        return Math.max(8, config.getInt("arena.torre-altura", 18));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(3, config.getInt("arena.queda-abaixo", 6));
    }

    /** O raio máximo que a pista alcança, já com muro. */
    public int alcanceDaPista() {
        return raio() + onda() + meiaLargura() + 1;
    }

    @Override
    public int alcanceDaObra() {
        return alcanceDaPista() + torreRecuo() + 4;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 8;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + torreAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int voltas() {
        return Math.max(1, config.getInt("regras.voltas", 2));
    }

    public int velocidade() {
        return Math.max(0, Math.min(3, config.getInt("regras.velocidade", 2)));
    }

    /** Os materiais que o construtor põe no chão da pista — e só eles. */
    public Set<Material> materiaisDaPista() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("pista", Material.LIGHT_GRAY_CONCRETE));
        saida.add(cor("pista-eixo", Material.WHITE_CONCRETE));
        saida.add(cor("guia-clara", Material.WHITE_CONCRETE));
        saida.add(cor("guia-escura", Material.RED_CONCRETE));
        saida.add(cor("largada-clara", Material.WHITE_CONCRETE));
        saida.add(cor("largada-escura", Material.BLACK_CONCRETE));
        saida.add(cor("ponte", Material.POLISHED_BLACKSTONE));
        saida.add(cor("ponte-borda", Material.GOLD_BLOCK));
        return saida;
    }

    // ------------------------------------------------------------------
    //  A pista
    // ------------------------------------------------------------------

    public List<double[]> eixo() {
        if (eixo == null) {
            eixo = PistaFechada.eixo(raio(), onda(), voltasDaOnda());
        }
        return eixo;
    }

    public int quantosBlocosDePista() {
        return eixo().size();
    }

    public int[] portoes() {
        if (portoes == null) {
            portoes = PistaFechada.portoes(quantosPortoes(), eixo().size());
        }
        return portoes;
    }

    public int[][] atalhos() {
        if (atalhos == null) {
            atalhos = PistaFechada.atalhos(eixo(), voltasDaOnda(), atalhoAbertura());
        }
        return atalhos;
    }

    /**
     * Os planos de cada portão, com meia-largura folgada de propósito.
     *
     * Na pista, meia-largura é a faixa inteira mais um e meio: o portão tem de
     * pegar quem passa raspando no muro, porque um portão estreito deixa o
     * jogador rente à parede atravessar sem contar. Na ponte, a largura dela
     * mais um.
     */
    public List<List<PistaFechada.Portao>> planos() {
        if (planos == null) {
            planos = PistaFechada.planos(eixo(), portoes(), atalhos(),
                    meiaLargura() + 1.5, atalhoLargura() / 2.0 + 1.0);
        }
        return planos;
    }

    /** O índice do eixo onde fica o portão número k. */
    public int indiceDoPortao(int k) {
        int[] ps = portoes();
        return ps[Math.floorMod(k, ps.length)];
    }
}
