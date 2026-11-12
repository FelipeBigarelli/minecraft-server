package codes.biga.bigacore;

import org.bukkit.Material;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A planta do Labirinto — e o labirinto de hoje.
 *
 * <h2>Tudo aqui é função pura da semente</h2>
 *
 * O labirinto não é desenhado à mão nem guardado em arquivo: ele SAI da data.
 * {@link #cavar} recebe uma semente e devolve o mapa, sempre o mesmo para a
 * mesma semente, e {@link #sementeDoDia} escolhe a semente a partir do número
 * do dia. Isso dá três coisas de uma vez: todo mundo joga o mesmo labirinto
 * o dia inteiro (dá para decorar, dá para contar para um amigo, dá para
 * disputar o recorde do dia), amanhã é outro, e o teste consegue rodar
 * quatrocentos dias em um segundo sem servidor nenhum.
 *
 * <h2>Labirinto com voltas, e não labirinto perfeito</h2>
 *
 * O algoritmo clássico (cavar em profundidade, sempre pelo galho mais novo)
 * devolve um labirinto PERFEITO: entre dois pontos quaisquer existe um
 * caminho e só um. Isso é bonito no papel e ruim numa corrida de dezesseis:
 * errar a curva custa a partida inteira, e os quinze de trás não têm o que
 * fazer além de seguir o primeiro. Depois de cavar, abrimos mais algumas
 * paredes ao acaso. Cada parede aberta é um ciclo, cada ciclo é uma rota
 * alternativa, e é a rota alternativa que transforma seguir o primeiro em
 * tentar passar na frente dele.
 *
 * <h2>A sala do meio é uma célula só, para quem conta</h2>
 *
 * As nove células do meio nascem com todas as paredes internas abertas: é
 * uma sala, e é de lá que todo mundo larga. A conta de distância trata as
 * nove como origem única, porque sair por qualquer uma delas custa o mesmo.
 */
public final class LabirintoCatalog extends CatalogoDeArena {

    /** Os quatro lados da borda, na ordem em que {@link #saidaDoDia} sorteia. */
    public static final int NORTE = 0;
    public static final int SUL = 1;
    public static final int OESTE = 2;
    public static final int LESTE = 3;

    /** Os rumos cardeais, {dx, dz}, na mesma ordem dos lados. */
    public static final int[][] RUMOS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /** As dezesseis cores de giz, uma por jogador. */
    public static final List<Material> GIZES = List.of(
            Material.RED_CARPET, Material.BLUE_CARPET, Material.YELLOW_CARPET,
            Material.LIME_CARPET, Material.MAGENTA_CARPET, Material.ORANGE_CARPET,
            Material.LIGHT_BLUE_CARPET, Material.PINK_CARPET,
            Material.PURPLE_CARPET, Material.CYAN_CARPET, Material.WHITE_CARPET,
            Material.GREEN_CARPET, Material.BROWN_CARPET, Material.LIGHT_GRAY_CARPET,
            Material.GRAY_CARPET, Material.BLACK_CARPET);

    /** Os tipos de casa do mapa de blocos. */
    public static final int VAZIO = 0;
    public static final int PISO = 1;
    public static final int MURO = 2;

    private long diaEmCache = Long.MIN_VALUE;
    private long sementeEmCache;
    private boolean[] ligadoEmCache;
    private int[] distanciaEmCache;
    private int[] distanciaDaPortaEmCache;
    private int[] saidaEmCache;

    public LabirintoCatalog(BigaCore plugin) {
        super(plugin, "labirinto.yml", "labirinto", "labirinto");
    }

    @Override
    protected void aoRecarregar() {
        diaEmCache = Long.MIN_VALUE;
        ligadoEmCache = null;
        distanciaEmCache = null;
        distanciaDaPortaEmCache = null;
        saidaEmCache = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int celulas() {
        return Math.max(7, config.getInt("arena.celulas", 13));
    }

    public int celula() {
        return Math.max(1, config.getInt("arena.celula", 3));
    }

    public int parede() {
        return Math.max(1, config.getInt("arena.parede", 1));
    }

    public int paredeAltura() {
        return Math.max(2, config.getInt("arena.parede-altura", 3));
    }

    public int sala() {
        return Math.max(0, config.getInt("arena.sala", 1));
    }

    public int saidaFundo() {
        return Math.max(2, config.getInt("arena.saida-fundo", 5));
    }

    public int saidaLargura() {
        return Math.max(celula(), config.getInt("arena.saida-largura", 7));
    }

    public int aberturasExtras() {
        return Math.max(0, config.getInt("arena.aberturas-extras", 12));
    }

    public int passosMinimos() {
        return Math.max(1, config.getInt("arena.passos-minimos", 12));
    }

    public int passosMaximos() {
        return Math.max(passosMinimos(), config.getInt("arena.passos-maximos", 30));
    }

    public int tentativas() {
        return Math.max(1, config.getInt("arena.tentativas", 64));
    }

    public long sementeBase() {
        return config.getLong("arena.semente", 4712L);
    }

    public int marcoAltura() {
        return Math.max(8, config.getInt("arena.marco-altura", 20));
    }

    /** O passo da grade: um corredor mais um muro. */
    public int passo() {
        return celula() + parede();
    }

    /** O lado do labirinto em BLOCOS, contando os dois muros da borda. */
    public int lado() {
        return lado(celulas(), celula(), parede());
    }

    /** A maior coordenada do labirinto: ele vai de -meio a +meio. */
    public int meio() {
        return (lado() - 1) / 2;
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return meio() + saidaFundo() + 4;
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

    public int giz() {
        return Math.max(0, config.getInt("regras.giz", 12));
    }

    public Material material(int tipo) {
        return switch (tipo) {
            case MURO -> cor("muro", Material.PRISMARINE_BRICKS);
            case PISO -> cor("piso", Material.STONE_BRICKS);
            default -> Material.AIR;
        };
    }

    // ------------------------------------------------------------------
    //  O labirinto do dia
    // ------------------------------------------------------------------

    /**
     * O número do dia de hoje, ou o que o yml prendeu.
     *
     * Prender o dia existe para duas coisas: um evento em que o labirinto
     * tem de ser o mesmo a semana toda, e o teste de que a conta não muda.
     */
    public long diaDeHoje() {
        int fixo = config.getInt("arena.dia-fixo", -1);
        return fixo >= 0 ? fixo : LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    private void garantirCache() {
        long dia = diaDeHoje();
        if (dia == diaEmCache && ligadoEmCache != null) {
            return;
        }
        int n = celulas();
        sementeEmCache = sementeDoDia(n, sementeBase(), dia, aberturasExtras(),
                sala(), passosMinimos(), passosMaximos(), tentativas());
        ligadoEmCache = cavar(n, sementeEmCache, aberturasExtras(), sala());
        distanciaEmCache = distancias(n, ligadoEmCache, sala());
        saidaEmCache = saidaDoDia(n, sementeEmCache);
        distanciaDaPortaEmCache = distanciasDe(n, ligadoEmCache,
                saidaEmCache[0], saidaEmCache[1]);
        diaEmCache = dia;
    }

    /** As passagens abertas do labirinto de hoje. */
    public boolean[] ligacoes() {
        garantirCache();
        return ligadoEmCache;
    }

    /** A distância, em células, de cada célula até a sala do meio. */
    public int[] distancias() {
        garantirCache();
        return distanciaEmCache;
    }

    /**
     * A distância, em células, de cada célula até a PORTA.
     *
     * É a conta do desempate no relógio, e ela é de propósito a distância
     * pelo labirinto e não em linha reta: em linha reta ganharia quem tivesse
     * parado atrás do muro certo sem nunca ter achado o caminho.
     */
    public int[] distanciasDaPorta() {
        garantirCache();
        return distanciaDaPortaEmCache;
    }

    /** A saída de hoje: {i, j, lado}. */
    public int[] saida() {
        garantirCache();
        return saidaEmCache;
    }

    /** A semente que o dia de hoje acabou usando. */
    public long semente() {
        garantirCache();
        return sementeEmCache;
    }

    /** Quantas células separam a sala do meio da porta de hoje. */
    public int passosAteASaida() {
        garantirCache();
        return distanciaEmCache[indice(celulas(), saidaEmCache[0], saidaEmCache[1])];
    }

    // ------------------------------------------------------------------
    //  As contas puras — o teste roda todas sem servidor
    // ------------------------------------------------------------------

    /** O lado em blocos de um labirinto de tantas células. */
    public static int lado(int celulas, int celula, int parede) {
        return parede + celulas * (celula + parede);
    }

    /** O índice da célula (i,j) na grade. */
    public static int indice(int celulas, int i, int j) {
        return j * celulas + i;
    }

    /**
     * O índice da ligação entre (i,j) e o vizinho a leste (eixo 0) ou ao sul
     * (eixo 1).
     *
     * Guardar a parede e não a passagem seria a mesma coisa invertida; o que
     * importa é guardar UMA das duas por par de células. Guardar as quatro
     * de cada célula dá dois donos para cada parede, e dois donos acabam
     * discordando.
     */
    public static int ligacao(int celulas, int i, int j, int eixo) {
        return indice(celulas, i, j) * 2 + eixo;
    }

    /**
     * Cava o labirinto.
     *
     * Em profundidade a partir do meio, sempre pelo galho mais novo — é o que
     * dá corredor comprido e sinuoso em vez de um emaranhado de becos de duas
     * casas. Depois abre {@code extras} paredes internas ao acaso, que são as
     * voltas, e por fim derruba todas as paredes internas da sala do meio.
     *
     * @return um vetor de {@code celulas*celulas*2} passagens; true = aberta
     */
    public static boolean[] cavar(int celulas, long semente, int extras, int sala) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        boolean[] visto = new boolean[celulas * celulas];
        Random sorte = new Random(semente);
        int meio = celulas / 2;

        Deque<int[]> pilha = new ArrayDeque<>();
        pilha.push(new int[]{meio, meio});
        visto[indice(celulas, meio, meio)] = true;

        List<int[]> livres = new ArrayList<>(4);
        while (!pilha.isEmpty()) {
            int[] onde = pilha.peek();
            livres.clear();
            for (int[] rumo : RUMOS) {
                int ni = onde[0] + rumo[0];
                int nj = onde[1] + rumo[1];
                if (dentro(celulas, ni, nj) && !visto[indice(celulas, ni, nj)]) {
                    livres.add(new int[]{ni, nj});
                }
            }
            if (livres.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] escolhido = livres.get(sorte.nextInt(livres.size()));
            abrir(ligado, celulas, onde[0], onde[1], escolhido[0], escolhido[1]);
            visto[indice(celulas, escolhido[0], escolhido[1])] = true;
            pilha.push(escolhido);
        }

        // As voltas: paredes internas ainda fechadas, sorteadas sem repetir.
        List<int[]> fechadas = new ArrayList<>();
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (i + 1 < celulas && !ligado[ligacao(celulas, i, j, 0)]) {
                    fechadas.add(new int[]{i, j, 0});
                }
                if (j + 1 < celulas && !ligado[ligacao(celulas, i, j, 1)]) {
                    fechadas.add(new int[]{i, j, 1});
                }
            }
        }
        Collections.shuffle(fechadas, sorte);
        for (int k = 0; k < Math.min(extras, fechadas.size()); k++) {
            int[] qual = fechadas.get(k);
            ligado[ligacao(celulas, qual[0], qual[1], qual[2])] = true;
        }

        // A sala: todas as paredes internas dela caem.
        for (int j = meio - sala; j <= meio + sala; j++) {
            for (int i = meio - sala; i <= meio + sala; i++) {
                if (i + 1 <= meio + sala) {
                    ligado[ligacao(celulas, i, j, 0)] = true;
                }
                if (j + 1 <= meio + sala) {
                    ligado[ligacao(celulas, i, j, 1)] = true;
                }
            }
        }
        return ligado;
    }

    private static void abrir(boolean[] ligado, int celulas, int i, int j,
                              int ni, int nj) {
        if (ni > i) {
            ligado[ligacao(celulas, i, j, 0)] = true;
        } else if (ni < i) {
            ligado[ligacao(celulas, ni, nj, 0)] = true;
        } else if (nj > j) {
            ligado[ligacao(celulas, i, j, 1)] = true;
        } else {
            ligado[ligacao(celulas, ni, nj, 1)] = true;
        }
    }

    /** Há passagem aberta entre duas células vizinhas? */
    public static boolean passa(boolean[] ligado, int celulas, int i, int j,
                                int ni, int nj) {
        if (!dentro(celulas, i, j) || !dentro(celulas, ni, nj)) {
            return false;
        }
        if (ni == i + 1 && nj == j) {
            return ligado[ligacao(celulas, i, j, 0)];
        }
        if (ni == i - 1 && nj == j) {
            return ligado[ligacao(celulas, ni, nj, 0)];
        }
        if (nj == j + 1 && ni == i) {
            return ligado[ligacao(celulas, i, j, 1)];
        }
        if (nj == j - 1 && ni == i) {
            return ligado[ligacao(celulas, ni, nj, 1)];
        }
        return false;
    }

    public static boolean dentro(int celulas, int i, int j) {
        return i >= 0 && j >= 0 && i < celulas && j < celulas;
    }

    /**
     * Quantas células separam cada casa da sala do meio. -1 = ilhada.
     *
     * Busca em largura com as nove casas da sala como origem única: sair por
     * qualquer uma delas custa o mesmo, e tratá-las como nove origens
     * diferentes daria vantagem a quem nascesse no canto certo da sala.
     */
    public static int[] distancias(int celulas, boolean[] ligado, int sala) {
        int[] dist = new int[celulas * celulas];
        java.util.Arrays.fill(dist, -1);
        Deque<int[]> fila = new ArrayDeque<>();
        int meio = celulas / 2;
        for (int j = meio - sala; j <= meio + sala; j++) {
            for (int i = meio - sala; i <= meio + sala; i++) {
                dist[indice(celulas, i, j)] = 0;
                fila.add(new int[]{i, j});
            }
        }
        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            for (int[] rumo : RUMOS) {
                int ni = onde[0] + rumo[0];
                int nj = onde[1] + rumo[1];
                if (!dentro(celulas, ni, nj) || dist[indice(celulas, ni, nj)] >= 0) {
                    continue;
                }
                if (!passa(ligado, celulas, onde[0], onde[1], ni, nj)) {
                    continue;
                }
                dist[indice(celulas, ni, nj)] = dist[indice(celulas, onde[0], onde[1])] + 1;
                fila.add(new int[]{ni, nj});
            }
        }
        return dist;
    }

    /** A mesma busca em largura, de uma célula só. */
    public static int[] distanciasDe(int celulas, boolean[] ligado, int i0,
                                     int j0) {
        int[] dist = new int[celulas * celulas];
        java.util.Arrays.fill(dist, -1);
        Deque<int[]> fila = new ArrayDeque<>();
        dist[indice(celulas, i0, j0)] = 0;
        fila.add(new int[]{i0, j0});
        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            for (int[] rumo : RUMOS) {
                int ni = onde[0] + rumo[0];
                int nj = onde[1] + rumo[1];
                if (!dentro(celulas, ni, nj) || dist[indice(celulas, ni, nj)] >= 0) {
                    continue;
                }
                if (!passa(ligado, celulas, onde[0], onde[1], ni, nj)) {
                    continue;
                }
                dist[indice(celulas, ni, nj)] = dist[indice(celulas, onde[0], onde[1])] + 1;
                fila.add(new int[]{ni, nj});
            }
        }
        return dist;
    }

    /**
     * A porta do dia: {i, j, lado}.
     *
     * Sorteada com a semente embaralhada, e nunca num canto — o canto é onde
     * ficam as torres, e um patamar saindo do canto encostaria nas duas.
     */
    public static int[] saidaDoDia(int celulas, long semente) {
        Random sorte = new Random(semente ^ 0x5A1DAL);
        int lado = sorte.nextInt(4);
        int k = 1 + sorte.nextInt(Math.max(1, celulas - 2));
        return switch (lado) {
            case NORTE -> new int[]{k, 0, NORTE};
            case SUL -> new int[]{k, celulas - 1, SUL};
            case OESTE -> new int[]{0, k, OESTE};
            default -> new int[]{celulas - 1, k, LESTE};
        };
    }

    /**
     * A semente do dia, re-sorteada até a caminhada valer a pena.
     *
     * Um labirinto sorteado dá dias em que a porta cai a cinco passos da
     * sala: é um dia inteiro de partidas de dez segundos. E dá dias de
     * oitenta passos, que é um dia inteiro de partidas que estouram o
     * relógio. A re-sortagem é barata — a mediana é uma tentativa — e é o
     * que faz o jogo ter o mesmo tamanho todo dia.
     */
    public static long sementeDoDia(int celulas, long base, long dia, int extras,
                                    int sala, int minimo, int maximo,
                                    int tentativas) {
        long ultima = 0;
        for (int k = 0; k < Math.max(1, tentativas); k++) {
            long semente = base * 1000003L + (dia * 64L + k) * 31L;
            ultima = semente;
            boolean[] ligado = cavar(celulas, semente, extras, sala);
            int[] dist = distancias(celulas, ligado, sala);
            int[] saida = saidaDoDia(celulas, semente);
            int passos = dist[indice(celulas, saida[0], saida[1])];
            if (passos >= minimo && passos <= maximo) {
                return semente;
            }
        }
        return ultima;
    }

    // ------------------------------------------------------------------
    //  O mapa em blocos
    // ------------------------------------------------------------------

    /**
     * O labirinto em resolução de BLOCO: {@link #PISO} ou {@link #MURO} para
     * cada coluna, indexado por {@code v * lado + u}.
     *
     * A grade de células diz onde há passagem; este mapa diz onde há bloco. A
     * conversão parece burocracia e é o contrário: é o único lugar em que a
     * largura do corredor e a espessura do muro existem, e é ele que o teste
     * percorre para conferir que o labirinto é andável de verdade — uma
     * passagem aberta na grade mas tapada no mapa seria um labirinto
     * impossível sem nenhum erro na grade.
     *
     * <p>Repare no que sobra onde quatro células abertas se encontram: o
     * MOURÃO, o quadradinho de muro no cruzamento das duas faixas. Abrir uma
     * passagem abre a faixa de muro entre duas células, e nunca o cruzamento
     * de duas faixas — então a sala do meio não sai um salão vazio, sai um
     * salão com quatro colunas. Não é defeito e não se corrige: é o que dá
     * arquitetura ao salão, e o anel de largada passa longe delas.
     */
    public static int[] mapaDeBlocos(int celulas, int celula, int parede,
                                     boolean[] ligado, int sala, int[] saida) {
        int lado = lado(celulas, celula, parede);
        int[] mapa = new int[lado * lado];
        java.util.Arrays.fill(mapa, MURO);

        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                pintar(mapa, lado, inicioDaCelula(i, celula, parede), celula,
                        inicioDaCelula(j, celula, parede), celula);
            }
        }
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (i + 1 < celulas && ligado[ligacao(celulas, i, j, 0)]) {
                    pintar(mapa, lado,
                            inicioDaCelula(i, celula, parede) + celula, parede,
                            inicioDaCelula(j, celula, parede), celula);
                }
                if (j + 1 < celulas && ligado[ligacao(celulas, i, j, 1)]) {
                    pintar(mapa, lado,
                            inicioDaCelula(i, celula, parede), celula,
                            inicioDaCelula(j, celula, parede) + celula, parede);
                }
            }
        }
        int[] vao = vaoDaPorta(celulas, celula, parede, saida);
        pintar(mapa, lado, vao[0], vao[1], vao[2], vao[3]);
        return mapa;
    }

    /**
     * O vão da porta no muro da borda: {u, largura, v, altura}, em blocos.
     *
     * O vão é a célula de saída projetada para fora pela espessura do muro —
     * e não um buraco de tamanho fixo. Quem mexer em 'parede' no yml não tem
     * de mexer aqui.
     */
    public static int[] vaoDaPorta(int celulas, int celula, int parede,
                                   int[] saida) {
        int fora = parede + celulas * (celula + parede) - parede;
        return switch (saida[2]) {
            case NORTE -> new int[]{inicioDaCelula(saida[0], celula, parede),
                    celula, 0, parede};
            case SUL -> new int[]{inicioDaCelula(saida[0], celula, parede),
                    celula, fora, parede};
            case OESTE -> new int[]{0, parede,
                    inicioDaCelula(saida[1], celula, parede), celula};
            default -> new int[]{fora, parede,
                    inicioDaCelula(saida[1], celula, parede), celula};
        };
    }

    private static void pintar(int[] mapa, int lado, int u0, int largura,
                               int v0, int altura) {
        for (int v = v0; v < v0 + altura; v++) {
            for (int u = u0; u < u0 + largura; u++) {
                if (u >= 0 && v >= 0 && u < lado && v < lado) {
                    mapa[v * lado + u] = PISO;
                }
            }
        }
    }

    /** O mapa de blocos do labirinto de hoje. */
    public int[] mapa() {
        garantirCache();
        return mapaDeBlocos(celulas(), celula(), parede(), ligadoEmCache,
                sala(), saidaEmCache);
    }

    // ------------------------------------------------------------------
    //  Da grade para o mundo
    // ------------------------------------------------------------------

    /**
     * A célula a que pertence uma coluna de bloco, ou -1 se ali é muro.
     *
     * @param u a coordenada já somada do meio: {@code x + meio}
     */
    public static int celulaDe(int u, int celula, int parede, int celulas) {
        int dentro = u - parede;
        if (dentro < 0) {
            return -1;
        }
        int i = dentro / (celula + parede);
        if (i >= celulas) {
            return -1;
        }
        return dentro % (celula + parede) < celula ? i : -1;
    }

    /** O primeiro bloco (em u) da célula de índice i. */
    public static int inicioDaCelula(int i, int celula, int parede) {
        return parede + i * (celula + parede);
    }

    /** O bloco do meio (em u) da célula de índice i. */
    public static int meioDaCelula(int i, int celula, int parede) {
        return inicioDaCelula(i, celula, parede) + (celula - 1) / 2;
    }

    /**
     * A coordenada de mundo do meio da célula de índice i.
     *
     * Serve para os dois eixos: a grade é quadrada e está centrada no (0,0),
     * então a conta de x e a de z são a mesma.
     */
    public int centroDaCelula(int i) {
        return meioDaCelula(i, celula(), parede()) - meio();
    }

    /**
     * A célula de uma coluna de bloco, aceitando vão de porta.
     *
     * {@link #celulaDe} devolve -1 quando a coluna é o vão aberto entre duas
     * células, que é onde o jogador está metade do tempo. Para contar passos
     * isso não serve: arredonda-se para a célula de que o vão saiu.
     */
    public int celulaMaisProxima(int u) {
        int i = (u - parede()) / passo();
        return Math.max(0, Math.min(celulas() - 1, i));
    }

    /**
     * As largadas: um anel dentro da sala do meio.
     *
     * Aqui não serve o {@link ArenaComum#largadas}: ele recua cinco blocos da
     * beira, que é o certo numa arena de cinquenta blocos e é demais numa
     * sala de onze — os dezesseis jogadores nasceriam todos no mesmo bloco.
     * O anel daqui é o maior que cabe na sala com um bloco de folga do muro.
     */
    public List<int[]> largadas(int quantos) {
        int anel = Math.max(1, sala() * passo());
        List<int[]> pontos = new ArrayList<>();
        if (quantos <= 1) {
            pontos.add(new int[]{anel, 0});
            return pontos;
        }
        for (int k = 0; k < quantos; k++) {
            double angulo = 2 * Math.PI * k / quantos;
            pontos.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return pontos;
    }
}
