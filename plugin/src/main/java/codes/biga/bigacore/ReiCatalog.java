package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta do Rei da Montanha.
 *
 * <h2>A montanha é uma função de uma variável</h2>
 *
 * A altura de cada casa sai só da DISTÂNCIA dela ao meio: {@link
 * #alturaDe(int)}. Não é economia de código, é justiça — uma montanha
 * desenhada à mão tem um lado mais fácil, e num jogo em que todo mundo sobe
 * ao mesmo tempo o lado fácil decide a partida antes de ela começar. Com a
 * altura saindo do raio, todas as direções custam o mesmo.
 *
 * <h2>Degraus de dois de fundo</h2>
 *
 * Um degrau por bloco de raio dá uma parede de escada: sobe-se, mas pulando
 * sem parar, e quem está empurrando alcança quem está pulando. Dois de fundo
 * por fiada dá uma encosta em que se CORRE, e correr é o que faz valer a pena
 * escolher por onde subir.
 */
public final class ReiCatalog extends CatalogoDeArena {

    /** Os tipos de célula da montanha. */
    public static final int CHAO = 0;
    public static final int BEIRA = 1;
    public static final int ENCOSTA = 2;
    public static final int DEGRAU = 3;
    public static final int TOPO = 4;
    public static final int TOPO_BEIRA = 5;
    public static final int PEDRA = 6;
    public static final int PEDRA_TOPO = 7;

    public ReiCatalog(BigaCore plugin) {
        super(plugin, "rei.yml", "rei", "rei");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(12, config.getInt("arena.raio", 24));
    }

    public int beiraAneis() {
        return Math.max(0, config.getInt("arena.beira-aneis", 1));
    }

    public int peRaio() {
        return Math.max(topoRaio() + 2, Math.min(raio() - 2,
                config.getInt("arena.pe-raio", 18)));
    }

    public int passo() {
        return Math.max(1, config.getInt("arena.passo", 2));
    }

    public int altura() {
        return Math.max(2, config.getInt("arena.altura", 8));
    }

    public int topoRaio() {
        return Math.max(2, config.getInt("arena.topo-raio", 3));
    }

    public int pedras() {
        return Math.max(0, config.getInt("arena.pedras", 8));
    }

    public int pedraRaio() {
        return Math.max(3, config.getInt("arena.pedra-raio", 12));
    }

    public int pedraAltura() {
        return Math.max(1, config.getInt("arena.pedra-altura", 2));
    }

    public int marcoRecuo() {
        return Math.max(4, config.getInt("arena.marco-recuo", 8));
    }

    public int marcoAltura() {
        return Math.max(10, config.getInt("arena.marco-altura", 22));
    }

    public int marcoDistancia() {
        return raio() + marcoRecuo();
    }

    /** A fiada do bloco mais alto da montanha. */
    public int cumeY() {
        return topoY() + altura();
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 10;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int segundosParaGanhar() {
        return Math.max(5, config.getInt("regras.segundos-para-ganhar", 60));
    }

    public int toleranciaTiques() {
        return Math.max(0, config.getInt("regras.tolerancia-tiques", 40));
    }

    public double empurrao() {
        return Math.max(0.1, config.getDouble("regras.empurrao", 1.05));
    }

    public double empurraoAltura() {
        return Math.max(0.0, config.getDouble("regras.empurrao-altura", 0.42));
    }

    public int empurraoCarencia() {
        return Math.max(0, config.getInt("regras.empurrao-carencia", 12));
    }

    public int castigoSegundos() {
        return Math.max(0, config.getInt("regras.castigo-segundos", 3));
    }

    public Material material(int tipo) {
        return switch (tipo) {
            case CHAO -> cor("chao", Material.SMOOTH_STONE);
            case BEIRA -> cor("beira", Material.POLISHED_ANDESITE);
            case ENCOSTA -> cor("encosta", Material.STONE_BRICKS);
            case DEGRAU -> cor("degrau", Material.CHISELED_STONE_BRICKS);
            case TOPO -> cor("topo", Material.GOLD_BLOCK);
            case TOPO_BEIRA -> cor("topo-beira", Material.POLISHED_BLACKSTONE_BRICKS);
            case PEDRA -> cor("pedra", Material.ANDESITE);
            default -> cor("pedra-topo", Material.MOSSY_STONE_BRICKS);
        };
    }

    // ------------------------------------------------------------------
    //  A montanha
    // ------------------------------------------------------------------

    /**
     * Quantas fiadas a montanha tem na distância {@code r} do meio.
     *
     * Zero no pé e {@link #altura()} no topo, subindo uma fiada a cada
     * {@link #passo()} blocos de raio.
     */
    public int alturaDe(int r) {
        return alturaDe(r, peRaio(), passo(), altura());
    }

    /** A mesma conta, sem o yml, para o teste e para o construtor. */
    public static int alturaDe(int r, int peRaio, int passo, int altura) {
        if (r > peRaio) {
            return 0;
        }
        return Math.max(0, Math.min(altura, (peRaio - r + 1) / Math.max(1, passo)));
    }

    /**
     * A distância do meio, arredondada — e é a distância REDONDA, e não o
     * disco de inteiros.
     *
     * Com {@code x² + z² ≤ r²} a montanha sai com quinas: na diagonal ela
     * é mais baixa que no eixo, e o lado da quina vira o caminho fácil. Com
     * a distância arredondada ela é redonda em qualquer direção.
     */
    public static int distancia(int x, int z) {
        return (int) Math.round(Math.hypot(x, z));
    }

    /** O raio em que a montanha chega ao topo, com os números de hoje. */
    public int raioDoCume() {
        int maior = 0;
        for (int r = 0; r <= peRaio(); r++) {
            if (alturaDe(r) >= altura()) {
                maior = r;
            }
        }
        return maior;
    }

    /** Os centros das pedras da encosta: {x, z}. */
    public List<int[]> pedrasDaEncosta() {
        return pedrasDaEncosta(pedras(), pedraRaio());
    }

    public static List<int[]> pedrasDaEncosta(int quantas, int raio) {
        List<int[]> saida = new ArrayList<>();
        for (int i = 0; i < quantas; i++) {
            double angulo = Math.PI / quantas + 2 * Math.PI * i / Math.max(1, quantas);
            saida.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * raio),
                    (int) Math.round(Math.sin(angulo) * raio)});
        }
        return saida;
    }

    /** As largadas: no chão, em volta do pé da montanha. */
    public List<int[]> largadas(int quantos) {
        return ArenaComum.largadas(Math.max(2, quantos), raio());
    }
}
