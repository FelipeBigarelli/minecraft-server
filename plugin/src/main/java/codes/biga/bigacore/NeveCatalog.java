package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A planta da Guerra de Neve — e o sorteio dos abrigos.
 *
 * <h2>Sorteado, mas sempre igual</h2>
 *
 * Os abrigos saem de {@link Random} com semente do yml. Sorteio dá um campo
 * que não parece desenhado por régua; semente fixa dá o mesmo campo em todas
 * as rodadas, no teste e no servidor. As duas coisas importam: um campo
 * diferente a cada rodada seria injusto com quem decorou onde ficava o iglu,
 * e um campo em grade seria entediante na segunda partida.
 *
 * <h2>O sorteio é conferido, não confiado</h2>
 *
 * Sortear posição em disco é fácil de errar de três jeitos: dois abrigos no
 * mesmo lugar, abrigo pendurado na beira (meio em cima do vazio) e abrigo em
 * cima do monte do meio. O gerador REJEITA e sorteia de novo até caber — e o
 * teste confere o resultado, porque "sorteou de novo até caber" é exatamente
 * o tipo de laço que um dia não acha lugar e devolve lista curta.
 */
public final class NeveCatalog extends CatalogoDeArena {

    /** Quantos blocos de chão livre entre as bordas de dois abrigos. */
    public static final int FOLGA_ENTRE = 2;

    /** Os tipos de abrigo. */
    public static final int IGLU = 0;
    public static final int MURETA = 1;
    public static final int PINHEIRO = 2;

    private List<int[]> abrigos;

    public NeveCatalog(BigaCore plugin) {
        super(plugin, "neve.yml", "neve", "neve");
    }

    @Override
    protected void aoRecarregar() {
        abrigos = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(10, config.getInt("arena.raio", 28));
    }

    public int beiraAneis() {
        return Math.max(0, config.getInt("arena.beira-aneis", 2));
    }

    public int monteRaio() {
        return Math.max(2, config.getInt("arena.monte-raio", 5));
    }

    public int monteAltura() {
        return Math.max(1, config.getInt("arena.monte-altura", 3));
    }

    public int quantosAbrigos() {
        return Math.max(0, config.getInt("arena.abrigos", 16));
    }

    public long semente() {
        return config.getLong("arena.semente", 70712L);
    }

    public int abrigoFolgaCentro() {
        return Math.max(monteRaio() + 2, config.getInt("arena.abrigo-folga-centro", 8));
    }

    public int abrigoFolgaBeira() {
        return Math.max(2, config.getInt("arena.abrigo-folga-beira", 7));
    }

    /**
     * O anel em que todos nascem.
     *
     * Fora da faixa dos abrigos e dentro da beira: quem nasce dentro de um
     * iglu nasce preso, e quem nasce em cima do anel de gelo da beira nasce
     * escorregando para o vazio. Entre as duas coisas sobra a faixa que a
     * folga da beira abriu, e é nela que o anel cai.
     */
    public int anelDaLargada() {
        int recuo = Math.max(1, config.getInt("arena.largada-recuo", 3));
        return Math.max(monteRaio() + 3, raio() - recuo);
    }

    public int marcoRecuo() {
        return Math.max(6, config.getInt("arena.marco-recuo", 8));
    }

    public int marcoAltura() {
        return Math.max(10, config.getInt("arena.marco-altura", 22));
    }

    public int marcoDistancia() {
        return raio() + marcoRecuo();
    }

    /** Quem cair abaixo disto, caiu. Não há chão nenhum lá embaixo. */
    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 8));
    }

    @Override
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 12;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int vidas() {
        return Math.max(1, config.getInt("regras.vidas", 3));
    }

    public int bolas() {
        return Math.max(1, Math.min(64, config.getInt("regras.bolas", 16)));
    }

    public double empurrao() {
        return Math.max(0.1, config.getDouble("regras.empurrao", 0.85));
    }

    public double empurraoAltura() {
        return Math.max(0.0, config.getDouble("regras.empurrao-altura", 0.36));
    }

    public int carenciaTiques() {
        return Math.max(0, config.getInt("regras.carencia-tiques", 10));
    }

    public int apertoSegundos() {
        return Math.max(10, config.getInt("regras.aperto-segundos", 90));
    }

    public int apertoIntervalo() {
        return Math.max(1, config.getInt("regras.aperto-intervalo", 8));
    }

    /** Os materiais que o aperto pode comer — e só eles. */
    public Set<Material> materiaisDoCampo() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("piso", Material.SNOW_BLOCK));
        saida.add(cor("beira", Material.PACKED_ICE));
        saida.add(cor("iglu", Material.SNOW_BLOCK));
        saida.add(cor("iglu-porta", Material.PACKED_ICE));
        saida.add(cor("mureta", Material.PACKED_ICE));
        saida.add(cor("monte", Material.PACKED_ICE));
        saida.add(cor("monte-topo", Material.BLUE_ICE));
        saida.add(cor("tronco", Material.SPRUCE_LOG));
        saida.add(cor("folha", Material.SPRUCE_LEAVES));
        return saida;
    }

    // ------------------------------------------------------------------
    //  Os abrigos
    // ------------------------------------------------------------------

    public List<int[]> abrigos() {
        if (abrigos == null) {
            abrigos = abrigos(quantosAbrigos(), raio(), semente(),
                    abrigoFolgaCentro(), abrigoFolgaBeira());
        }
        return abrigos;
    }

    /**
     * Sorteia os abrigos: {x, z, tipo, tamanho}.
     *
     * <p>O tamanho é o raio do abrigo em blocos, e é ele que diz de quanto
     * dois abrigos precisam estar separados para não virarem um bloco só —
     * dois iglus grudados fazem uma parede comprida, e parede comprida num
     * jogo de tiro é um lado do campo que ninguém atravessa.
     *
     * @param folgaCentro nenhum abrigo a menos disto do meio, que é o monte.
     * @param folgaBeira  nenhum abrigo a menos disto da beira, senão ele fica
     *                    pendurado sobre o vazio.
     */
    public static List<int[]> abrigos(int quantos, int raio, long semente,
                                      int folgaCentro, int folgaBeira) {
        List<int[]> saida = new ArrayList<>();
        Random sorte = new Random(semente);
        int tentativas = 0;

        while (saida.size() < quantos && tentativas < quantos * 400) {
            tentativas++;
            int tipo = sorte.nextInt(3);
            int tamanho = switch (tipo) {
                case IGLU -> 3;
                case MURETA -> 2;
                default -> 2;
            };
            double angulo = sorte.nextDouble() * 2 * Math.PI;
            // Sorteio por ÁREA e não por distância: sortear a distância reta
            // empilha abrigo perto do meio, porque o anel de dentro tem menos
            // chão para a mesma faixa de raio. A raiz corrige isso.
            double dentro = folgaCentro + tamanho;
            double fora = raio - folgaBeira - tamanho;
            if (fora <= dentro) {
                continue;
            }
            double distancia = Math.sqrt(dentro * dentro
                    + sorte.nextDouble() * (fora * fora - dentro * dentro));
            int x = (int) Math.round(distancia * Math.cos(angulo));
            int z = (int) Math.round(distancia * Math.sin(angulo));

            if (!cabe(saida, x, z, tamanho, raio, folgaCentro, folgaBeira)) {
                continue;
            }
            saida.add(new int[]{x, z, tipo, tamanho});
        }
        return saida;
    }

    /**
     * As posições de largada: {@code quantos} pontos no anel, espaçados.
     *
     * Não usa o {@link ArenaComum#largadas} porque este jogo não pode largar
     * ninguém no meio: o meio é o monte de gelo, e um jogador sozinho largado
     * em (0,0) nasceria DENTRO dele. Aqui até o treino de um só começa no
     * anel.
     */
    public static List<int[]> largadas(int quantos, int anel) {
        List<int[]> pontos = new ArrayList<>();
        int total = Math.max(1, quantos);
        for (int i = 0; i < total; i++) {
            double angulo = 2 * Math.PI * i / total;
            pontos.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return pontos;
    }

    // ------------------------------------------------------------------
    //  O iglu
    // ------------------------------------------------------------------

    /**
     * A célula está na casca da cúpula de raio {@code raio}?
     *
     * Casca de uma fiada: dentro da bola de raio {@code raio} e fora da de
     * raio {@code raio-1}. É a única forma de cúpula que FECHA em cima sem
     * buraco — a conta óbvia, pegar o anel de raio {@code √(raio²-alto²)} em
     * cada fiada, deixa a fiada do cume com raio zero, e raio zero não põe
     * bloco nenhum: o iglu sai sem telhado e ninguém repara até alguém atirar
     * de cima.
     */
    public static boolean naCasca(int x, int z, int alto, int raio) {
        int d = x * x + z * z + alto * alto;
        return d <= raio * raio && d > (raio - 1) * (raio - 1);
    }

    /**
     * A casa da porta do iglu, virada para o meio do campo.
     *
     * Virada para o meio de propósito: quem se enfia num iglu de costas para
     * a arena está seguro e inútil, e jogo em que a melhor jogada é não jogar
     * não é jogo. Com a porta para dentro, atirar custa aparecer.
     *
     * <p>Anda pela direção em passos de um quarto de bloco até achar a
     * primeira casa da casca. Não dá para calcular direto: na diagonal, o
     * arredondamento do ponto a {@code raio} blocos de distância cai DENTRO
     * da cúpula, e abrir buraco no que já é ar deixa o iglu sem porta.
     */
    public static int[] portaDoIglu(int cx, int cz, int raio) {
        double norma = Math.max(1, Math.hypot(cx, cz));
        double ux = -cx / norma;
        double uz = -cz / norma;
        for (double t = 1; t <= raio + 1; t += 0.25) {
            int dx = (int) Math.round(ux * t);
            int dz = (int) Math.round(uz * t);
            if (naCasca(dx, dz, 1, raio)) {
                return new int[]{dx, dz};
            }
        }
        return new int[]{0, 0};
    }

    /** O abrigo cabe aqui sem encostar em outro, na beira ou no monte? */
    static boolean cabe(List<int[]> jaPostos, int x, int z, int tamanho, int raio,
                        int folgaCentro, int folgaBeira) {
        double doCentro = Math.hypot(x, z);
        if (doCentro - tamanho < folgaCentro) {
            return false;
        }
        if (doCentro + tamanho > raio - folgaBeira) {
            return false;
        }
        for (int[] outro : jaPostos) {
            double entre = Math.hypot(x - outro[0], z - outro[1]);
            if (entre < tamanho + outro[3] + FOLGA_ENTRE) {
                return false;
            }
        }
        return true;
    }
}
