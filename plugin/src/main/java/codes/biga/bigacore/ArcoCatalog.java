package codes.biga.bigacore;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A planta do ARCO E FLECHA.
 *
 * <h2>A força do arco não é o tempo</h2>
 *
 * {@link #forcaDe} é a conta do Minecraft, e ela é o motivo de este jogo
 * existir do jeito que existe: {@code (t² + 2t)/3}, com {@code t} em
 * segundos, limitada a 1. Em 18 tiques de arco armado dá 0,87; em 19, 0,93;
 * em 20, 1,00. Não é linear, e isso importa — "metade do tempo" não é
 * "metade da força".
 *
 * <p>É por isso que {@code forca-minima} é 0,9 e não 1,0.
 * {@link ArcoBuilder#conferirForca} exige que o número recuse 18 tiques e
 * aceite 19, e a razão é concreta: o arco chega ao máximo um tique antes de
 * vez em quando, por diferença entre o cliente e o servidor. Com 1,0 exato,
 * o arco "às vezes não atira" e o jogador nunca descobre por quê — é o pior
 * tipo de defeito, porque parece azar.
 *
 * <h2>A cobertura é o jogo</h2>
 *
 * Num jogo em que um acerto elimina, sem cobertura os dezesseis se matam nos
 * primeiros dez segundos por flecha perdida, e com cobertura demais ninguém
 * se acerta nunca. Os três anéis de muros baixos são a resposta, e
 * {@link #blocosDaCobertura} é a conta exata do que eles ocupam — para
 * {@link ArcoBuilder#conferirCobertura} poder recusar as duas pontas.
 *
 * <p>Os anéis são deslocados um em relação ao outro de propósito: muros
 * alinhados radialmente deixariam corredores retos do meio até a beira, e um
 * corredor reto num jogo de arco é uma sentença para quem entra nele.
 */
public final class ArcoCatalog extends CatalogoDeArena {

    /** Quantos tiques o arco leva para chegar à força máxima. */
    public static final int TIQUES_DO_ARCO = 20;

    public ArcoCatalog(BigaCore plugin) {
        super(plugin, "arco.yml", "arco", "arco");
    }

    // ------------------------------------------------------------------
    //  A física do arco
    // ------------------------------------------------------------------

    /**
     * A força de um tiro de {@code tiques} de arco armado, de 0 a 1.
     *
     * É a conta do próprio Minecraft, e está aqui para o teste poder dizer
     * exatamente quais tempos de arco o jogo aceita. Escrever "um segundo" no
     * yml e comparar com o tempo seria mais simples e estaria errado: o que
     * o evento do tiro entrega é a FORÇA, e ela não é proporcional ao tempo.
     */
    public static double forcaDe(int tiques) {
        double t = Math.max(0, tiques) / (double) TIQUES_DO_ARCO;
        return Math.min(1.0, (t * t + 2 * t) / 3.0);
    }

    /** O menor número de tiques que chega à força pedida. */
    public static int tiquesParaForca(double forca) {
        for (int tiques = 0; tiques <= TIQUES_DO_ARCO; tiques++) {
            if (forcaDe(tiques) >= forca) {
                return tiques;
            }
        }
        return TIQUES_DO_ARCO;
    }

    public double forcaMinima() {
        return Math.max(0.1, Math.min(1.0,
                config.getDouble("regras.forca-minima", 0.9)));
    }

    public int gracaSegundos() {
        return Math.max(0, config.getInt("regras.graca-segundos", 5));
    }

    public int pontosPorAcerto() {
        return Math.max(1, config.getInt("regras.pontos-por-acerto", 10));
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(16, config.getInt("arena.raio", 34));
    }

    public int paredeAltura() {
        return Math.max(3, config.getInt("arena.parede-altura", 5));
    }

    public List<Integer> aneis() {
        List<Integer> lista = config.getIntegerList("arena.aneis");
        return lista.isEmpty() ? List.of(11, 20, 28) : List.copyOf(lista);
    }

    public List<Integer> murosPorAnel() {
        List<Integer> lista = config.getIntegerList("arena.muros-por-anel");
        return lista.isEmpty() ? List.of(6, 9, 12) : List.copyOf(lista);
    }

    public int muroComprimento() {
        return Math.max(3, config.getInt("arena.muro-comprimento", 7));
    }

    public int muroAltura() {
        return Math.max(2, config.getInt("arena.muro-altura", 3));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 18));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 8));
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
    //  A cobertura
    // ------------------------------------------------------------------

    public List<int[]> blocosDaCobertura() {
        return blocosDaCobertura(aneis(), murosPorAnel(), muroComprimento());
    }

    /**
     * A pegada no chão de todos os muros de cobertura.
     *
     * Cada muro é TANGENTE ao anel dele: um muro radial é uma parede que se
     * olha de lado e não cobre ninguém. E cada anel é deslocado em relação ao
     * de dentro, porque muros alinhados deixariam corredores retos do meio à
     * beira.
     */
    public static List<int[]> blocosDaCobertura(List<Integer> aneis,
                                                List<Integer> murosPorAnel,
                                                int comprimento) {
        Set<Long> vistos = new LinkedHashSet<>();
        List<int[]> saida = new ArrayList<>();
        int meio = (Math.max(3, comprimento) - 1) / 2;
        for (int k = 0; k < aneis.size(); k++) {
            int raio = aneis.get(k);
            int quantos = Math.max(1, k < murosPorAnel.size()
                    ? murosPorAnel.get(k) : 1);
            double passo = 2 * Math.PI / quantos;
            double recuo = passo * k / (double) Math.max(1, aneis.size());
            for (int i = 0; i < quantos; i++) {
                double angulo = recuo + passo * i;
                double cx = Math.cos(angulo) * raio;
                double cz = Math.sin(angulo) * raio;
                double tx = -Math.sin(angulo);
                double tz = Math.cos(angulo);
                for (int t = -meio; t <= meio; t++) {
                    int x = (int) Math.round(cx + tx * t);
                    int z = (int) Math.round(cz + tz * t);
                    if (vistos.add(ArenaComum.chave(x, 0, z))) {
                        saida.add(new int[]{x, z});
                    }
                }
            }
        }
        return saida;
    }

    /**
     * Quantos blocos de cobertura um tiro do meio para fora atravessa.
     *
     * É a leitura que diz se a arena tem linha de tiro. Com muros alinhados
     * entre os anéis, metade das direções fica impossível de atravessar e a
     * outra metade vira corredor.
     */
    public static int coberturaNoCaminho(double angulo, int raio,
                                         List<Integer> aneis,
                                         List<Integer> murosPorAnel,
                                         int comprimento) {
        Set<Long> cobertura = new LinkedHashSet<>();
        for (int[] onde : blocosDaCobertura(aneis, murosPorAnel, comprimento)) {
            cobertura.add(ArenaComum.chave(onde[0], 0, onde[1]));
        }
        Set<Long> jaVi = new LinkedHashSet<>();
        int quantos = 0;
        for (double longe = 1; longe <= raio - 1; longe += 0.5) {
            int x = (int) Math.round(Math.cos(angulo) * longe);
            int z = (int) Math.round(Math.sin(angulo) * longe);
            long chave = ArenaComum.chave(x, 0, z);
            if (!jaVi.add(chave)) {
                continue;
            }
            if (cobertura.contains(chave)) {
                quantos++;
            }
        }
        return quantos;
    }

    /** Quantos blocos de chão o disco tem, para a conta de proporção. */
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

    // ------------------------------------------------------------------
    //  Os alvos de enfeite
    // ------------------------------------------------------------------

    /** Os alvos pendurados no muro de fora, de enfeite. */
    public List<int[]> pontosDosAlvos() {
        return pontosDosAlvos(raio(), 8);
    }

    public static List<int[]> pontosDosAlvos(int raio, int quantos) {
        List<int[]> saida = new ArrayList<>();
        for (int k = 0; k < Math.max(1, quantos); k++) {
            double angulo = 2 * Math.PI * k / Math.max(1, quantos);
            saida.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * (raio - 1)),
                    (int) Math.round(Math.sin(angulo) * (raio - 1))});
        }
        return saida;
    }

    /** Para que lado a placa olha: para o meio do campo. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /**
     * O anel em que os arqueiros largam: entre o muro de fora e o primeiro
     * anel de cobertura.
     *
     * Não passa por {@link ArenaComum#largadas}, e é de propósito: aquele
     * método recua cinco blocos do raio que recebe, o que é certo para uma
     * arena vazia e errado aqui — com o anel de muros mais de fora no raio
     * 28, o recuo de cinco punha gente largando DENTRO dos muros, e alguns
     * em cima deles. Aqui o anel é dito de uma vez.
     */
    public int anelDaLargada() {
        return Math.max(4, raio() - 3);
    }

    /**
     * As largadas: na beira do campo, olhando para o meio.
     *
     * Na beira e não no meio: no meio, dezesseis pessoas largam a dois passos
     * uma da outra e a primeira flecha de cada um acerta alguém por acidente.
     * Da beira, os primeiros segundos são todo mundo correndo para dentro,
     * para um muro — que é como uma partida de arco tem de começar.
     */
    public List<int[]> largadas(int quantos) {
        return largadas(quantos, anelDaLargada());
    }

    public static List<int[]> largadas(int quantos, int anel) {
        List<int[]> saida = new ArrayList<>();
        int quantas = Math.max(2, quantos);
        for (int k = 0; k < quantas; k++) {
            double angulo = 2 * Math.PI * k / quantas;
            saida.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return saida;
    }
}
