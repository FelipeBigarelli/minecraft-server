package codes.biga.bigacore;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta da SORTE.
 *
 * <h2>O dado é justo; a multidão não é</h2>
 *
 * É a decisão de desenho que faz um jogo de sorte ser jogo. Carregar o dado —
 * dar a uma fatia 1 em 12 e a outra 1 em 3, com prêmios diferentes — foi a
 * primeira ideia, e ela não fecha: o prêmio que iguala o valor esperado de
 * seis fatias com riscos diferentes sai numa escada de multiplicadores tão
 * apertada (1 · 1,06 · 1,16 · 1,49 · 2,39 · 4,7) que arredondar para número
 * inteiro apaga as quatro do meio. Sobram duas fatias que mandam em alguma
 * hora e quatro de enfeite.
 *
 * <p>Com o dado JUSTO e o prêmio DIVIDIDO entre quem escolheu a fatia, a
 * conta não precisa de ajuste nenhum: toda face é 1 em 6, e o que se escolhe
 * é onde os outros quinze não vão estar. {@link #divisao} é a regra inteira —
 * e ela paga ZERO para a fatia cheia, de propósito.
 *
 * <h2>A melhor hora de sair fica no meio da partida</h2>
 *
 * Sobreviver a uma rodada é {@code (setores-1)/setores}, e o prêmio sobe de
 * rodada em rodada. {@link #evDeSair} multiplica os dois, e
 * {@link #melhorHoraDeSair} acha o topo — que com os números de hoje cai na
 * rodada 8 de 10, com a curva tão chata entre a 7 e a 10 que a conta não
 * decide por ninguém. É o que se quer: quem decide é o placar dos outros.
 *
 * <p>{@link SorteBuilder#conferirPremios} recusa a tabela em que o topo cai na
 * primeira rodada — aí ninguém joga — ou na última, aí ninguém sai e o banco
 * do meio é enfeite.
 */
public final class SorteCatalog extends CatalogoDeArena {

    /** O que {@link #setorDoPonto} devolve para o meio e para fora do disco. */
    public static final int BANCO = -1;
    public static final int FORA = -2;

    /**
     * Os pontos de cada face do dado, numa grade de três por três.
     *
     * O bit {@code fiada*3 + coluna} está aceso onde há ponto. Não é
     * economia: é o que permite o teste conferir que a face N tem N pontos,
     * que é o tipo de erro que ninguém vê olhando o chão de uma arena de
     * cinquenta e seis blocos de diâmetro.
     */
    public static final int[] PINGOS = {
            0b000_010_000,  // 1
            0b100_000_001,  // 2
            0b100_010_001,  // 3
            0b101_000_101,  // 4
            0b101_010_101,  // 5
            0b101_101_101,  // 6
    };

    public SorteCatalog(BigaCore plugin) {
        super(plugin, "sorte.yml", "sorte", "sorte");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(12, config.getInt("arena.raio", 28));
    }

    public int bancoRaio() {
        return Math.max(3, config.getInt("arena.banco-raio", 7));
    }

    /** Quantas fatias, que são as faces do dado. */
    public int setores() {
        return Math.max(2, Math.min(PINGOS.length, config.getInt("arena.setores", 6)));
    }

    public int pingoRaio() {
        return Math.max(bancoRaio() + 3, Math.min(raio() - 3,
                config.getInt("arena.pingo-raio", 18)));
    }

    public int pingoPasso() {
        return Math.max(1, config.getInt("arena.pingo-passo", 2));
    }

    public int bancoDegrau() {
        return Math.max(0, config.getInt("arena.banco-degrau", 1));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 16));
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

    public int rodadas() {
        return Math.max(2, config.getInt("regras.rodadas", 10));
    }

    public int tempoEscolha() {
        return Math.max(3, config.getInt("regras.tempo-escolha", 8));
    }

    public int tempoRevelacao() {
        return Math.max(2, config.getInt("regras.tempo-revelacao", 5));
    }

    public int quedaTiques() {
        return Math.max(10, config.getInt("regras.queda-tiques", 30));
    }

    public int premioBase() {
        return Math.max(1, config.getInt("regras.premio-base", 8));
    }

    public int premioPasso() {
        return Math.max(0, config.getInt("regras.premio-passo", 2));
    }

    /** O prêmio da rodada de índice {@code rodada}, contando de zero. */
    public int premioDaRodada(int rodada) {
        return premioBase() + premioPasso() * Math.max(0, rodada);
    }

    public List<Integer> premios() {
        return premios(rodadas(), premioBase(), premioPasso());
    }

    public static List<Integer> premios(int rodadas, int base, int passo) {
        List<Integer> saida = new ArrayList<>();
        for (int r = 0; r < Math.max(0, rodadas); r++) {
            saida.add(base + passo * r);
        }
        return List.copyOf(saida);
    }

    // ------------------------------------------------------------------
    //  As contas do jogo
    // ------------------------------------------------------------------

    /**
     * Quanto cada um leva quando {@code quantos} escolheram a mesma fatia.
     *
     * Divisão inteira, e sem piso: com dezesseis numa fatia de prêmio oito,
     * cada um leva ZERO. É de propósito, e é o motor do jogo — amontoar-se na
     * fatia que "parece" segura não é só menos lucrativo, é inútil, porque
     * nenhuma fatia é mais segura que outra num dado justo.
     */
    public static int divisao(int premio, int quantos) {
        return quantos <= 0 ? 0 : premio / quantos;
    }

    /**
     * O valor esperado de sair no fim da rodada {@code k}.
     *
     * {@code (sobreviver uma rodada)^k × (soma dos prêmios até k)}. Usa o
     * prêmio inteiro, como se o jogador estivesse sozinho na fatia: a divisão
     * pela multidão é igual para todas as fatias e não muda onde está o topo.
     */
    public static double evDeSair(int k, List<Integer> premios, int setores) {
        if (k <= 0 || premios.isEmpty() || setores < 2) {
            return 0;
        }
        double sobreviver = (setores - 1) / (double) setores;
        int soma = 0;
        for (int r = 0; r < Math.min(k, premios.size()); r++) {
            soma += premios.get(r);
        }
        return Math.pow(sobreviver, Math.min(k, premios.size())) * soma;
    }

    /** Em que rodada o valor esperado de sair é maior. */
    public static int melhorHoraDeSair(List<Integer> premios, int setores) {
        int melhor = 0;
        double maior = 0;
        for (int k = 1; k <= premios.size(); k++) {
            double ev = evDeSair(k, premios, setores);
            if (ev > maior) {
                maior = ev;
                melhor = k;
            }
        }
        return melhor;
    }

    // ------------------------------------------------------------------
    //  As fatias
    // ------------------------------------------------------------------

    public int setorDoPonto(double x, double z) {
        return setorDoPonto(x, z, setores(), bancoRaio(), raio());
    }

    /**
     * Em que fatia o ponto está, ou {@link #BANCO} / {@link #FORA}.
     *
     * A fatia zero fica centrada no NORTE, e as outras seguem no sentido do
     * relógio: a face 1 é sempre a mesma fatia, partida por partida, e quem
     * joga duas vezes já sabe onde ela fica.
     */
    public static int setorDoPonto(double x, double z, int setores,
                                   int bancoRaio, int raio) {
        double longe = Math.hypot(x, z);
        if (longe <= bancoRaio + 0.5) {
            return BANCO;
        }
        if (longe > raio + 0.5) {
            return FORA;
        }
        double fatia = 2 * Math.PI / setores;
        double angulo = Math.atan2(z, x) + Math.PI / 2 + fatia / 2;
        while (angulo < 0) {
            angulo += 2 * Math.PI;
        }
        while (angulo >= 2 * Math.PI) {
            angulo -= 2 * Math.PI;
        }
        return Math.min(setores - 1, (int) (angulo / fatia));
    }

    /** O ângulo do meio de uma fatia, com zero no norte. */
    public static double anguloDaFatia(int setor, int setores) {
        return -Math.PI / 2 + 2 * Math.PI * setor / Math.max(1, setores);
    }

    public int[] pontoDaFatia(int setor, int longe) {
        return pontoDaFatia(setor, setores(), longe);
    }

    /** Um ponto na bissetriz da fatia, à distância {@code longe} do meio. */
    public static int[] pontoDaFatia(int setor, int setores, int longe) {
        double angulo = anguloDaFatia(setor, setores);
        return new int[]{(int) Math.round(Math.cos(angulo) * longe),
                (int) Math.round(Math.sin(angulo) * longe)};
    }

    public int[] posteDaFatia(int setor) {
        return pontoDaFatia(setor, setores(), raio() + 2);
    }

    /** Para que lado a placa olha: para o meio do disco. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** Se o ponto da grade de pingos da face tem pingo. */
    public static boolean temPingo(int face, int coluna, int fiada) {
        if (face < 1 || face > PINGOS.length) {
            return false;
        }
        if (coluna < 0 || coluna > 2 || fiada < 0 || fiada > 2) {
            return false;
        }
        return (PINGOS[face - 1] & (1 << (fiada * 3 + coluna))) != 0;
    }

    /**
     * As largadas: um anel no meio da coroa, espalhado pelas fatias.
     *
     * No meio da coroa, e não na bissetriz de cada fatia, porque com
     * dezesseis jogadores e seis fatias não há como largar um por fatia — e
     * largar três numa e dois noutra já daria vantagem de divisão a quem
     * nasceu sozinho.
     */
    public List<int[]> largadas(int quantos) {
        return largadas(quantos, bancoRaio(), raio());
    }

    public static List<int[]> largadas(int quantos, int bancoRaio, int raio) {
        List<int[]> saida = new ArrayList<>();
        int anel = (bancoRaio + raio) / 2;
        int quantas = Math.max(2, quantos);
        for (int k = 0; k < quantas; k++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * k / quantas;
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return saida;
    }
}
