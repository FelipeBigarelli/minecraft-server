package codes.biga.bigacore;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta do ESCONDE.
 *
 * <h2>Os dois papéis valem o mesmo, e isso é uma conta</h2>
 *
 * É a decisão mais importante deste jogo, e ela não se vê jogando uma
 * partida: se caçar vale mais que se esconder, o sorteio do papel decide a
 * partida antes de ela começar, e ninguém reclama porque ninguém sabe. A
 * regra que resolve está em {@link #pontosPorCaptura}: uma captura vale o que
 * vale sobreviver a partida INTEIRA, dividido pelo número de escondidos por
 * caçador.
 *
 * <p>Ou seja: o caçador que pega a parte dele faz exatamente o mesmo que o
 * escondido que atravessa a partida. Com treze escondidos e três caçadores,
 * cada captura vale um quarto de uma sobrevivência — porque há mais de quatro
 * para pegar. Com dois escondidos e um caçador, cada captura vale meia. Sem
 * essa divisão, {@code pontos-por-captura} seria um número fixo no yml e o
 * equilíbrio mudaria sete vezes entre dois e dezesseis jogadores.
 *
 * <h2>O esconderijo acaba, e por isso não há empate</h2>
 *
 * {@link #caixotesDaOnda} desce de {@code caixotes-por-escondido} a
 * {@code caixotes-no-fim-por-escondido} ao longo das ondas, e o segundo é
 * apertado de propósito. Um esconde-esconde com cobertura constante termina
 * sempre igual: no relógio, com os bons escondidos parados onde entraram. Com
 * a cobertura acabando, a última onda obriga a andar, e quem anda é visto.
 *
 * <h2>Por que a cobertura é por ESCONDIDO e não um número</h2>
 *
 * Noventa caixotes são generosos para treze pessoas e um deserto para uma: a
 * mesma arena seria fácil cheia e impossível vazia. Com a conta por
 * escondido, o aperto do fim é o mesmo com dois jogadores e com dezesseis.
 */
public final class EscondeCatalog extends CatalogoDeArena {

    public EscondeCatalog(BigaCore plugin) {
        super(plugin, "esconde.yml", "esconde", "esconde");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(16, config.getInt("arena.raio", 30));
    }

    public int paredeAltura() {
        return Math.max(3, config.getInt("arena.parede-altura", 7));
    }

    public int pilarPasso() {
        return Math.max(4, config.getInt("arena.pilar-passo", 10));
    }

    public int pilarRaio() {
        return Math.max(0, config.getInt("arena.pilar-raio", 1));
    }

    public int pilarAltura() {
        return Math.max(2, config.getInt("arena.pilar-altura", 4));
    }

    public int caixotePasso() {
        return Math.max(2, config.getInt("arena.caixote-passo", 4));
    }

    public int cabineRaio() {
        return Math.max(1, config.getInt("arena.cabine-raio", 2));
    }

    public int cabineAltura() {
        return Math.max(3, config.getInt("arena.cabine-altura", 4));
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
    //  Regras
    // ------------------------------------------------------------------

    public double fracaoCacadores() {
        return Math.max(0.05, Math.min(0.5,
                config.getDouble("regras.fracao-cacadores", 0.2)));
    }

    public int segredoSegundos() {
        return Math.max(5, config.getInt("regras.segredo-segundos", 25));
    }

    public int encaixeTiques() {
        return Math.max(5, config.getInt("regras.encaixe-tiques", 20));
    }

    public int caixotesPorEscondido() {
        return Math.max(1, config.getInt("regras.caixotes-por-escondido", 7));
    }

    public int caixotesNoFimPorEscondido() {
        return Math.max(0,
                config.getInt("regras.caixotes-no-fim-por-escondido", 1));
    }

    public int ondas() {
        return Math.max(2, config.getInt("regras.ondas", 4));
    }

    public int pontosPorSegundo() {
        return Math.max(1, config.getInt("regras.pontos-por-segundo", 1));
    }

    /** Quanto vale atravessar a partida inteira escondido. */
    public int premioDeSobreviver() {
        return tempoLimite() * pontosPorSegundo();
    }

    // ------------------------------------------------------------------
    //  Os papéis
    // ------------------------------------------------------------------

    public int cacadores(int jogadores) {
        return cacadores(jogadores, fracaoCacadores());
    }

    /**
     * Quantos caçam, de {@code jogadores} na arena.
     *
     * Nunca zero e nunca todos: com zero caçadores ninguém é achado e a
     * partida é uma espera de quatro minutos; com todos caçadores não há o
     * que achar. Os dois acontecem com uma fração mal escrita no yml e os
     * dois são silenciosos.
     */
    public static int cacadores(int jogadores, double fracao) {
        if (jogadores <= 1) {
            return 1;
        }
        int quantos = (int) Math.round(jogadores * fracao);
        return Math.max(1, Math.min(jogadores - 1, quantos));
    }

    public int escondidos(int jogadores) {
        return Math.max(0, jogadores - cacadores(jogadores));
    }

    public static int escondidos(int jogadores, double fracao) {
        return Math.max(0, jogadores - cacadores(jogadores, fracao));
    }

    public int pontosPorCaptura(int jogadores) {
        return pontosPorCaptura(cacadores(jogadores), escondidos(jogadores),
                premioDeSobreviver());
    }

    /**
     * O que vale uma captura.
     *
     * {@code prêmio × caçadores / escondidos}: a parte de um caçador é
     * {@code escondidos / caçadores} capturas, e quem faz a parte dele tem de
     * terminar com o mesmo que quem sobreviveu a partida inteira. Qualquer
     * outro número põe o sorteio do papel decidindo a partida.
     */
    public static int pontosPorCaptura(int cacadores, int escondidos,
                                       int premioDeSobreviver) {
        if (escondidos <= 0 || cacadores <= 0) {
            return premioDeSobreviver;
        }
        return Math.max(1, (int) Math.round(
                premioDeSobreviver * cacadores / (double) escondidos));
    }

    // ------------------------------------------------------------------
    //  As ondas
    // ------------------------------------------------------------------

    /** Quantos caixotes de enfeite ficam de pé depois da onda {@code onda}. */
    public int caixotesDaOnda(int onda, int escondidos) {
        return caixotesDaOnda(onda, escondidos, ondas(),
                caixotesPorEscondido(), caixotesNoFimPorEscondido());
    }

    /**
     * A interpolação da cobertura, da onda zero à última.
     *
     * Linear, e não geométrica: a geométrica tira quase tudo na primeira onda
     * e quase nada nas últimas, e é justamente no fim que o aperto tem de
     * apertar.
     */
    public static int caixotesDaOnda(int onda, int escondidos, int ondas,
                                     int porEscondido, int noFim) {
        int quantos = Math.max(1, escondidos);
        int comeco = quantos * porEscondido;
        int fim = quantos * noFim;
        if (onda <= 0) {
            return comeco;
        }
        if (onda >= ondas) {
            return fim;
        }
        return (int) Math.round(comeco
                + (fim - comeco) * onda / (double) ondas);
    }

    /** Em que segundo da partida a onda {@code onda} cai. */
    public int segundoDaOnda(int onda) {
        return segundoDaOnda(onda, ondas(), segredoSegundos(), tempoLimite());
    }

    /**
     * As ondas caem espalhadas pelo tempo de caça, e a última antes do fim.
     *
     * A última onda numa fração do tempo e não no último segundo: uma onda
     * que cai junto com o apito não muda nada, e ela é a onda que existe para
     * desfazer o empate.
     */
    public static int segundoDaOnda(int onda, int ondas, int segredo,
                                    int tempoLimite) {
        int caca = Math.max(1, tempoLimite - segredo);
        return segredo + (int) Math.round(caca * onda / (double) (ondas + 1));
    }

    // ------------------------------------------------------------------
    //  Os lugares do chão
    // ------------------------------------------------------------------

    public List<int[]> pontosDosPilares() {
        return pontosDosPilares(pilarPasso(), raio(), cabineRaio());
    }

    /** Os centros dos pilares: múltiplos do passo, dentro do disco. */
    public static List<int[]> pontosDosPilares(int passo, int discoRaio,
                                               int cabineRaio) {
        List<int[]> saida = new ArrayList<>();
        int quantos = discoRaio / Math.max(1, passo);
        for (int cx = -quantos; cx <= quantos; cx++) {
            for (int cz = -quantos; cz <= quantos; cz++) {
                int x = cx * passo;
                int z = cz * passo;
                if (Math.hypot(x, z) > discoRaio - 2) {
                    continue;
                }
                if (Math.max(Math.abs(x), Math.abs(z)) <= cabineRaio + 2) {
                    continue;
                }
                saida.add(new int[]{x, z});
            }
        }
        return saida;
    }

    public boolean noPilar(int x, int z) {
        return noPilar(x, z, pilarPasso(), pilarRaio(), raio(), cabineRaio());
    }

    /** O bloco está dentro de algum pilar. */
    public static boolean noPilar(int x, int z, int passo, int pilarRaio,
                                 int discoRaio, int cabineRaio) {
        for (int[] onde : pontosDosPilares(passo, discoRaio, cabineRaio)) {
            if (Math.abs(x - onde[0]) <= pilarRaio
                    && Math.abs(z - onde[1]) <= pilarRaio) {
                return true;
            }
        }
        return false;
    }

    public List<int[]> pontosDosCaixotes() {
        return pontosDosCaixotes(caixotePasso(), raio(), pilarPasso(),
                pilarRaio(), cabineRaio());
    }

    /**
     * A grade de lugares onde cabe um caixote de enfeite.
     *
     * Múltiplos do passo, dentro do disco com dois blocos de folga do muro,
     * fora dos pilares e fora da cabine. A grade é a mesma toda partida e o
     * SORTEIO de quais lugares recebem caixote é por partida — se os lugares
     * mudassem, nada; se o sorteio não mudasse, quem joga duas vezes
     * decoraria o galpão e acharia o caixote a mais de longe.
     */
    public static List<int[]> pontosDosCaixotes(int passo, int discoRaio,
                                                int pilarPasso, int pilarRaio,
                                                int cabineRaio) {
        List<int[]> saida = new ArrayList<>();
        int quantos = discoRaio / Math.max(1, passo);
        for (int cx = -quantos; cx <= quantos; cx++) {
            for (int cz = -quantos; cz <= quantos; cz++) {
                int x = cx * passo;
                int z = cz * passo;
                if (Math.hypot(x, z) > discoRaio - 2) {
                    continue;
                }
                if (naCabine(x, z, cabineRaio + 1)) {
                    continue;
                }
                if (noPilar(x, z, pilarPasso, pilarRaio, discoRaio,
                        cabineRaio)) {
                    continue;
                }
                saida.add(new int[]{x, z});
            }
        }
        return saida;
    }

    /**
     * O bloco é um lugar da GRADE, onde um caixote caberia.
     *
     * É a condição de encaixar, e é a regra que faz o disfarce ser possível:
     * um caixote fora da grade de quatro em quatro é denunciado por estar
     * fora da grade, e quem joga duas vezes passa a achar todo mundo sem
     * olhar para nada além do alinhamento. Só se vira caixote onde cabe um
     * caixote.
     */
    public static boolean naGrade(int x, int z, int passo, int discoRaio,
                                  int pilarPasso, int pilarRaio,
                                  int cabineRaio) {
        if (passo <= 0 || x % passo != 0 || z % passo != 0) {
            return false;
        }
        if (Math.hypot(x, z) > discoRaio - 2) {
            return false;
        }
        if (naCabine(x, z, cabineRaio + 1)) {
            return false;
        }
        return !noPilar(x, z, pilarPasso, pilarRaio, discoRaio, cabineRaio);
    }

    public boolean naGrade(int x, int z) {
        return naGrade(x, z, caixotePasso(), raio(), pilarPasso(), pilarRaio(),
                cabineRaio());
    }

    /** Dentro da cabine do meio. */
    public static boolean naCabine(double x, double z, int raio) {
        return Math.abs(x) <= raio + 0.5 && Math.abs(z) <= raio + 0.5;
    }

    /**
     * O centro do bloco em que o ponto está.
     *
     * É para onde o jogador parado é empurrado ao encaixar, e é o que faz o
     * caixote dele ficar exatamente onde um caixote de enfeite ficaria. Sem o
     * empurrão, o jogador para onde parou — e parar onde parou é quase sempre
     * meio bloco fora do lugar.
     */
    public static double[] centroDoBloco(double x, double z) {
        return new double[]{Math.floor(x) + 0.5, Math.floor(z) + 0.5};
    }

    /** Para que lado a placa olha: para o meio do galpão. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** As largadas de quem se esconde: um anel no meio do galpão. */
    public List<int[]> largadas(int quantos) {
        return ArenaComum.largadas(Math.max(2, quantos), raio() - 6);
    }
}
