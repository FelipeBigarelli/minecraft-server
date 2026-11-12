package codes.biga.bigacore;

import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta do RITMO.
 *
 * <h2>A folga está escrita faixa por faixa, e nunca em milissegundo</h2>
 *
 * É a decisão que faz o jogo existir, e ela é o contrário do que fazem os
 * jogos de ritmo de verdade. O osu! mede a folga em milissegundos — 50, 100 e
 * 150 ms para as três notas — e a porta do osu! para o Minecraft que estudei
 * copia isso convertendo em tique com arredondamento para cima. Num servidor
 * a vinte tiques por segundo, um tique é 50 ms: as três faixas viram UM, DOIS
 * e TRÊS tiques, e o pulo do jogador também chega carimbado em tique inteiro.
 * O que se mede é o arredondamento.
 *
 * <p>Fração da batida resolve esse, e traz outro: a fração encolhe junto com
 * a batida, então o fim da escada fica com a MESMA dificuldade relativa do
 * começo — o jogo acelera e não fica mais difícil. Por isso {@link #folgas()}
 * é uma lista, uma folga por faixa, e {@link MusicaBuilder#conferirAndamentos}
 * recusa a obra quando ela não aperta escada abaixo, quando passa de metade
 * da batida — que é quando as folgas de duas batidas vizinhas se encostam e
 * nenhum pulo pode errar — ou quando cai abaixo de dois tiques, que é quando
 * se mede a grade do servidor em vez da pessoa.
 *
 * <h2>O andamento para em 85 bpm, e o limite é o pulo</h2>
 *
 * {@link #CICLO_DO_PULO}: um pulo leva doze tiques do chão ao chão, e só se
 * pula do chão. O teto não é 100 bpm e sim 85 por causa de
 * {@link #JITTER_DO_RITMISTA}: dois pulos precisam de doze tiques ENTRE eles,
 * então pular um tique atrasado e depois um adiantado pede uma batida de
 * catorze. A escada sobe de 60 a 85 bpm e a dificuldade que falta vem da
 * folga apertando de ±6 para ±3 tiques.
 *
 * <h2>Segurar a tecla não pode jogar sozinho</h2>
 *
 * {@link #errosDoPuloContinuo} é a conta menos óbvia do jogo. Quem segura a
 * tecla de pulo pula a cada doze tiques exatos, sem erro humano; numa batida
 * de dezesseis tiques com folga de ±4, esse trilho cai dentro da folga de
 * TODAS as batidas. A conferência da obra roda essa simulação em cada faixa e
 * recusa a escada se a tecla presa sobrevive — e é por isso que a faixa de 75
 * bpm tem folga ±3 em vez de ±4.
 *
 * <h2>O andamento é lista de tiques, e não de bpm</h2>
 *
 * {@link #tempos()} vem em TIQUES entre batidas porque o tique é a unidade
 * que o servidor tem. Escrever o yml em batidas por minuto convidaria a pôr
 * 90 bpm, que pede 13,33 tiques — e o que aconteceria é a batida andar 13 ou
 * 14 tiques alternadamente, o que um jogo de ritmo não pode fazer.
 */
public final class MusicaCatalog extends CatalogoDeArena {

    /** Quantos tiques por minuto o servidor tem: 20 por segundo. */
    public static final int TIQUES_POR_MINUTO = 1200;

    /** A folga mais curta que ainda mede a pessoa, e não a grade de tiques. */
    public static final int FOLGA_HONESTA = 2;

    /** A folga que a faixa mais lenta precisa dar, para absorver o ping. */
    public static final int FOLGA_DO_PING = 5;

    /**
     * Quantos tiques um pulo do jogador leva, do chão ao chão.
     *
     * É o limite físico do jogo, e ele decide onde a escada de andamentos
     * para: só se pula do chão, e um pulo leva uns doze tiques para voltar a
     * ele. Doze tiques são 100 batidas por minuto. Uma faixa de 150 bpm não
     * seria difícil, seria impossível — e o sintoma seria os dezesseis
     * jogadores perderem as três vidas nas três primeiras batidas dela, sem
     * nada no log.
     */
    public static final int CICLO_DO_PULO = 12;

    /**
     * Quantos tiques de erro um bom ritmista ainda comete, para cada lado.
     *
     * Entra no teto do andamento junto com o ciclo do pulo, e é a diferença
     * entre 100 e 85 batidas por minuto: dois pulos seguidos precisam de
     * {@link #CICLO_DO_PULO} tiques ENTRE eles, então pular um tique atrasado
     * e depois um adiantado pede uma batida de {@code CICLO_DO_PULO + 2}
     * tiques. Sem esta margem, quem acertasse uma batida ficaria sem pulo
     * para a seguinte — o que é pior que difícil, é arbitrário.
     */
    public static final int JITTER_DO_RITMISTA = 1;

    /** A batida mais rápida que o jogo pode pedir, em tiques. */
    public static final int BATIDA_MAIS_RAPIDA =
            CICLO_DO_PULO + 2 * JITTER_DO_RITMISTA;

    public MusicaCatalog(BigaCore plugin) {
        super(plugin, "musica.yml", "musica", "musica");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int tablados() {
        return Math.max(2, config.getInt("arena.tablados", 16));
    }

    public int tabladoRaio() {
        return Math.max(1, config.getInt("arena.tablado-raio", 3));
    }

    public int tabladoAnel() {
        return Math.max(tabladoRaio() * 4,
                config.getInt("arena.tablado-anel", 32));
    }

    public int metronomoRaio() {
        return Math.max(2, config.getInt("arena.metronomo-raio", 5));
    }

    public int metronomoAltura() {
        return Math.max(4, config.getInt("arena.metronomo-altura", 14));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 18));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 6));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return tabladoAnel() + tabladoRaio() + 6;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 6;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + Math.max(marcoAltura(), metronomoAltura()) + 8);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    /** Os andamentos, em tiques entre batidas, do mais lento ao mais rápido. */
    public List<Integer> tempos() {
        List<Integer> lido = config.getIntegerList("regras.tempos");
        return lido.isEmpty() ? List.of(20, 18, 16, 15, 14, 13, 12)
                : List.copyOf(lido);
    }

    public int batidasPorCompasso() {
        return Math.max(1, config.getInt("regras.batidas-por-compasso", 4));
    }

    public int compassosPorTempo() {
        return Math.max(1, config.getInt("regras.compassos-por-tempo", 4));
    }

    /** Quantas batidas cada faixa de andamento dura. */
    public int batidasPorTempo() {
        return batidasPorCompasso() * compassosPorTempo();
    }

    public int compassosDeAviso() {
        return Math.max(0, config.getInt("regras.compassos-de-aviso", 1));
    }

    /** Quantas batidas tocam antes de alguém poder perder vida. */
    public int batidasDeAviso() {
        return compassosDeAviso() * batidasPorCompasso();
    }

    /**
     * A folga de cada faixa, em tiques, para cada lado da batida.
     *
     * Escrita faixa por faixa, e não como fração da batida, porque é ela que
     * faz a escada subir de dificuldade: o andamento para em
     * {@link #CICLO_DO_PULO} tiques e não pode subir mais, então o que aperta
     * é a janela. Uma fração faria o contrário — ela encolheria junto com a
     * batida e deixaria o fim da escada com a MESMA dificuldade do começo.
     */
    public List<Integer> folgas() {
        List<Integer> lido = config.getIntegerList("regras.folgas");
        return lido.isEmpty() ? List.of(6, 5, 5, 4, 4, 3, 3) : List.copyOf(lido);
    }

    public int folgaMinima() {
        return Math.max(1, config.getInt("regras.folga-minima", FOLGA_HONESTA));
    }

    public int vidas() {
        return Math.max(1, config.getInt("regras.vidas", 3));
    }

    public int pontosNaBatida() {
        return Math.max(1, config.getInt("regras.pontos-na-batida", 1));
    }

    public List<Double> melodia() {
        List<Double> lido = config.getDoubleList("melodia");
        return lido.isEmpty() ? List.of(1.0) : List.copyOf(lido);
    }

    // ------------------------------------------------------------------
    //  O relógio da música
    // ------------------------------------------------------------------

    /** A folga da faixa de índice {@code faixa}, com os números de hoje. */
    public int folgaDaFaixa(int faixa) {
        return folgaDe(folgas(), faixa, folgaMinima());
    }

    /** A folga que vale na batida de índice {@code batida}. */
    public int folgaDaBatida(int batida) {
        return folgaDaFaixa(faixaDaBatida(batida));
    }

    /**
     * A folga de uma faixa, com a última valendo para o que passar da lista.
     *
     * Sai da LISTA e nunca de milissegundo nem de fração. Ver a explicação na
     * cabeça da classe: milissegundo convertido em tique mede o
     * arredondamento, e fração da batida não aperta quando o andamento sobe.
     */
    public static int folgaDe(List<Integer> folgas, int faixa, int minima) {
        if (folgas.isEmpty()) {
            return minima;
        }
        int qual = Math.max(0, Math.min(folgas.size() - 1, faixa));
        return Math.max(minima, folgas.get(qual));
    }

    /**
     * Quantas batidas de uma faixa quem SEGURA a tecla de pulo erraria.
     *
     * É a conta que impede o jogo de se jogar sozinho, e ela não é óbvia:
     * quem segura a tecla pula a cada {@link #CICLO_DO_PULO} tiques, exatos,
     * sem erro humano nenhum. Num andamento em que doze e a batida têm
     * divisor comum — 16 tiques, por exemplo — esse trilho cai dentro da
     * folga de TODAS as batidas, e a tecla presa tira nota máxima.
     *
     * <p>Roda com a regra estrita do jogo: o pulo resolve a batida mais perto
     * dele, e resolver fora da folga é erro. Batida que ninguém resolveu
     * também é erro.
     */
    public static int errosDoPuloContinuo(int tempo, int folga, int batidas,
                                          int ciclo) {
        if (tempo < 1 || ciclo < 1) {
            return 0;
        }
        boolean[] resolvida = new boolean[batidas + 1];
        int erros = 0;
        long fim = (long) batidas * tempo;
        for (long pulo = 0; pulo <= fim; pulo += ciclo) {
            int antes = (int) (pulo / tempo);
            // Empate vai para a batida anterior, como em batidaMaisPerto.
            int batida = pulo - (long) antes * tempo
                    <= (long) (antes + 1) * tempo - pulo ? antes : antes + 1;
            if (batida < 0 || batida >= resolvida.length || resolvida[batida]) {
                continue;
            }
            resolvida[batida] = true;
            if (Math.abs(pulo - (long) batida * tempo) > folga) {
                erros++;
            }
        }
        for (boolean uma : resolvida) {
            if (!uma) {
                erros++;
            }
        }
        return erros;
    }

    public int faixaDaBatida(int batida) {
        return faixaDaBatida(batida, tempos().size(), batidasPorTempo());
    }

    /** Em que faixa de andamento a batida de índice {@code batida} cai. */
    public static int faixaDaBatida(int batida, int quantasFaixas,
                                    int batidasPorTempo) {
        if (quantasFaixas <= 1) {
            return 0;
        }
        return Math.min(quantasFaixas - 1,
                Math.max(0, batida) / Math.max(1, batidasPorTempo));
    }

    /** O andamento escrito em batidas por minuto, para a tela. */
    public static int bpmDe(int tempo) {
        return TIQUES_POR_MINUTO / Math.max(1, tempo);
    }

    public int tempoDaBatida(int batida) {
        return tempoDaBatida(batida, tempos(), batidasPorTempo());
    }

    /**
     * Quantos tiques dura a batida de índice {@code batida}.
     *
     * Depois da última faixa o andamento FICA no mais rápido, em vez de a
     * lista acabar: é o que faz a partida terminar por eliminação e não por
     * fim de partitura.
     */
    public static int tempoDaBatida(int batida, List<Integer> tempos,
                                    int batidasPorTempo) {
        if (tempos.isEmpty()) {
            return 20;
        }
        return tempos.get(faixaDaBatida(batida, tempos.size(), batidasPorTempo));
    }

    public long tiqueDaBatida(int batida) {
        return tiqueDaBatida(batida, tempos(), batidasPorTempo());
    }

    /**
     * Em que tique da rodada a batida de índice {@code batida} cai.
     *
     * A batida zero cai no tique zero. A conta anda por FAIXA e não por
     * batida — oito voltas em vez de cento e vinte e oito — porque ela é
     * chamada do laço de tique.
     */
    public static long tiqueDaBatida(int batida, List<Integer> tempos,
                                     int batidasPorTempo) {
        if (batida <= 0 || tempos.isEmpty()) {
            return 0;
        }
        long tique = 0;
        int restam = batida;
        for (int faixa = 0; faixa < tempos.size() && restam > 0; faixa++) {
            int quantas = faixa == tempos.size() - 1 ? restam
                    : Math.min(restam, batidasPorTempo);
            tique += (long) quantas * tempos.get(faixa);
            restam -= quantas;
        }
        return tique;
    }

    public int batidaDoTique(long tique) {
        return batidaDoTique(tique, tempos(), batidasPorTempo());
    }

    /**
     * Qual batida já tocou neste tique: o inverso de {@link #tiqueDaBatida}.
     *
     * Serve para o pulo do jogador achar a batida dele sem o jogo guardar
     * histórico — o pulo chega num tique, e a batida sai da conta.
     */
    public static int batidaDoTique(long tique, List<Integer> tempos,
                                    int batidasPorTempo) {
        if (tique <= 0 || tempos.isEmpty()) {
            return 0;
        }
        long acumulado = 0;
        int batida = 0;
        for (int faixa = 0; faixa < tempos.size() - 1; faixa++) {
            long bloco = (long) batidasPorTempo * tempos.get(faixa);
            if (acumulado + bloco > tique) {
                return batida + (int) ((tique - acumulado) / tempos.get(faixa));
            }
            acumulado += bloco;
            batida += batidasPorTempo;
        }
        int passo = tempos.get(tempos.size() - 1);
        return batida + (int) ((tique - acumulado) / Math.max(1, passo));
    }

    /**
     * A batida mais perto deste tique, e de quanto o jogador errou.
     *
     * Devolve {@code {batida, desvio}}, com o desvio em tiques e com sinal:
     * negativo é adiantado, positivo é atrasado. A batida mais perto, e não a
     * última que tocou, porque pular um tique ANTES da batida é acertar — e
     * chamar isso de erro na batida anterior seria punir quem está certo.
     */
    public static int[] batidaMaisPerto(long tique, List<Integer> tempos,
                                        int batidasPorTempo) {
        int antes = batidaDoTique(tique, tempos, batidasPorTempo);
        long tiqueAntes = tiqueDaBatida(antes, tempos, batidasPorTempo);
        long tiqueDepois = tiqueDaBatida(antes + 1, tempos, batidasPorTempo);
        long paraAntes = tique - tiqueAntes;
        long paraDepois = tiqueDepois - tique;
        if (paraDepois < paraAntes) {
            return new int[]{antes + 1, (int) -paraDepois};
        }
        return new int[]{antes, (int) paraAntes};
    }

    /** Quantas batidas a escada de andamentos tem antes de ficar na última. */
    public int batidasDaEscada() {
        return tempos().size() * batidasPorTempo();
    }

    // ------------------------------------------------------------------
    //  Os tablados
    // ------------------------------------------------------------------

    public int[] centroDoTablado(int indice) {
        return centroDoTablado(indice, tablados(), tabladoAnel());
    }

    /** O meio do tablado de índice {@code indice}: {x, z}. */
    public static int[] centroDoTablado(int indice, int quantos, int anel) {
        double angulo = -Math.PI / 2 + 2 * Math.PI * indice / Math.max(1, quantos);
        return new int[]{(int) Math.round(Math.cos(angulo) * anel),
                (int) Math.round(Math.sin(angulo) * anel)};
    }

    public int tabladoDoPonto(double x, double z) {
        return tabladoDoPonto(x, z, tablados(), tabladoAnel(), tabladoRaio());
    }

    /** Em que tablado o ponto está, ou −1 se está no vazio. */
    public static int tabladoDoPonto(double x, double z, int quantos, int anel,
                                     int raio) {
        for (int k = 0; k < quantos; k++) {
            int[] centro = centroDoTablado(k, quantos, anel);
            if (Math.hypot(x - centro[0], z - centro[1]) <= raio + 0.5) {
                return k;
            }
        }
        return -1;
    }

    /**
     * A distância entre os meios de dois tablados vizinhos.
     *
     * É o número que decide se dá para invadir o tablado do vizinho, e por
     * isso ele é conta e não palpite: {@code 2·anel·sen(π/quantos)}.
     */
    public static double entreVizinhos(int quantos, int anel) {
        if (quantos < 2) {
            return Double.MAX_VALUE;
        }
        return 2.0 * anel * Math.sin(Math.PI / quantos);
    }

    public int[] posteDoTablado(int indice) {
        return posteDoTablado(indice, tablados(), tabladoAnel(), tabladoRaio());
    }

    /** O poste da placa, do lado de fora do tablado: {x, z}. */
    public static int[] posteDoTablado(int indice, int quantos, int anel,
                                       int raio) {
        int[] centro = centroDoTablado(indice, quantos, anel);
        double longe = Math.hypot(centro[0], centro[1]);
        if (longe < 0.5) {
            return new int[]{centro[0], centro[1] + raio + 1};
        }
        double estica = (longe + raio + 1) / longe;
        return new int[]{(int) Math.round(centro[0] * estica),
                (int) Math.round(centro[1] * estica)};
    }

    /** Para que lado a placa olha: para o meio do tablado dela. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /**
     * As largadas: um tablado por jogador, na ordem.
     *
     * Não é anel de {@link ArenaComum#largadas} e não é sorteio: o tablado é
     * o jogador, e dois jogadores no mesmo tablado seriam duas pessoas
     * dividindo três vidas.
     */
    public List<int[]> largadas(int quantos) {
        List<int[]> saida = new ArrayList<>();
        for (int k = 0; k < Math.min(quantos, tablados()); k++) {
            saida.add(centroDoTablado(k));
        }
        return saida;
    }
}
