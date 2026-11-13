package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A planta do Labirinto — e o labirinto desta rodada.
 *
 * <h2>Um labirinto novo por rodada, e nunca o mesmo duas vezes</h2>
 *
 * O labirinto não é desenhado à mão nem guardado em arquivo: ele sai de um
 * SORTEIO, refeito a cada rodada. O sorteio tira quatro coisas de uma vez —
 * o traçado (qual dos cinco algoritmos de {@link LabirintoTracados} cava), o
 * tamanho em células, o estilo (a paleta) e quantas voltas o labirinto tem —
 * e por último a semente do mapa em si.
 *
 * <p>A conta é inteira: cinco traçados, cinco tamanhos, cinco estilos e três
 * níveis de volta dão trezentas e setenta e cinco combinações, cada uma com
 * um mapa diferente por cima. Não é "vinte labirintos": é um labirinto
 * diferente toda vez, para sempre.
 *
 * <p>E o que muda não é só o desenho. Cada traçado tem uma textura própria e
 * reconhecível — corredor comprido, beco curto, avenida reta, salão — e é
 * isso que impede a coisa de virar ruído: o jogador que joga dez rodadas
 * aprende a reconhecer o traçado na primeira esquina, e esse reconhecimento
 * é vantagem de verdade. Ver {@link LabirintoTracados}.
 *
 * <h2>Labirinto com voltas, e não labirinto perfeito</h2>
 *
 * Os cinco algoritmos devolvem um labirinto PERFEITO: entre dois pontos
 * quaisquer existe um caminho e só um. Isso é bonito no papel e ruim numa
 * corrida de dezesseis: errar a curva custa a partida inteira, e os quinze
 * de trás não têm o que fazer além de seguir o primeiro. Depois de cavar,
 * abrem-se mais algumas paredes ao acaso. Cada parede aberta é um ciclo,
 * cada ciclo é uma rota alternativa, e é a rota alternativa que transforma
 * seguir o primeiro em tentar passar na frente dele.
 *
 * <h2>A porta é ESCOLHIDA, não sorteada</h2>
 *
 * Antes a porta caía num ponto qualquer da borda e, quando a caminhada saía
 * curta demais, o labirinto inteiro era re-sorteado até dar certo. Isso
 * funcionava com um traçado só. Com cinco não funciona: o GALHOS é um
 * labirinto de becos curtos e as caminhadas dele são naturalmente mais
 * curtas que as do FUNDO — exigir o mesmo número dos dois é exigir que o
 * GALHOS deixe de ser o que é.
 *
 * <p>Então a porta passa a ser escolhida: {@link #portaLonge} mede a
 * distância do meio até cada casa da borda e sorteia entre as mais longes.
 * Funciona em qualquer traçado e em qualquer tamanho, nunca precisa
 * re-sortear nada, e dá de graça uma coisa que a re-sortagem não dava — a
 * caminhada é sempre das mais longas QUE AQUELE labirinto tinha para
 * oferecer, em vez de uma caminhada qualquer que coube numa janela.
 *
 * <h2>A sala do meio é uma célula só, para quem conta</h2>
 *
 * As nove células do meio nascem com todas as paredes internas abertas: é
 * uma sala, e é de lá que todo mundo larga. A conta de distância trata as
 * nove como origem única, porque sair por qualquer uma delas custa o mesmo.
 */
public final class LabirintoCatalog extends CatalogoDeArena {

    /** Os quatro lados da borda, na ordem em que {@link #portaLonge} olha. */
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

    /**
     * O sorteio de uma rodada: tudo o que faz este labirinto ser este.
     *
     * Guardado inteiro num objeto só, e não em cinco campos soltos, porque é
     * uma coisa só: trocar o tamanho sem trocar a semente dá um labirinto que
     * não é nem o de antes nem um novo. Ou troca tudo, ou não troca nada.
     */
    public record Sorteio(int tracado, int celulas, int estilo, int voltas,
                          long semente) {
    }

    private Sorteio sorteioAtual;
    private Sorteio sorteioEmCache;
    private boolean[] ligadoEmCache;
    private int[] distanciaEmCache;
    private int[] distanciaDaPortaEmCache;
    private int[] saidaEmCache;

    public LabirintoCatalog(BigaCore plugin) {
        super(plugin, "labirinto.yml", "labirinto", "labirinto");
    }

    @Override
    protected void aoRecarregar() {
        sorteioAtual = null;
        sorteioEmCache = null;
        ligadoEmCache = null;
        distanciaEmCache = null;
        distanciaDaPortaEmCache = null;
        saidaEmCache = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    /** O tamanho desta rodada, em células. */
    public int celulas() {
        return sorteio().celulas();
    }

    /**
     * Os tamanhos que o sorteio pode tirar, em células.
     *
     * Ímpares, sempre: é o que põe a sala do meio exatamente sobre o (0,0) do
     * mundo, que é onde fica o saguão e onde todo mundo larga. Um tamanho par
     * na lista é recusado pela conferência em vez de sair torto em silêncio.
     */
    public List<Integer> tamanhos() {
        List<Integer> lista = config.getIntegerList("arena.tamanhos");
        return lista.isEmpty() ? List.of(9, 13, 17, 21, 25) : List.copyOf(lista);
    }

    /**
     * O maior tamanho que o sorteio pode tirar.
     *
     * É ele, e não o tamanho da rodada, que manda na área da obra: depois de
     * uma rodada de vinte e cinco células vem uma de nove, e o que sobrou da
     * grande tem de ser apagado. Uma área de obra que encolhesse junto com o
     * labirinto deixaria o anterior de pé em volta do novo.
     */
    public int celulasMaximo() {
        int maior = 7;
        for (int qual : tamanhos()) {
            maior = Math.max(maior, qual);
        }
        return maior;
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

    /**
     * Quantas voltas o labirinto desta rodada tem, em paredes abertas.
     *
     * Vem de uma PORCENTAGEM e não de um número fixo, porque doze voltas num
     * labirinto de nove células é um descampado e num de vinte e cinco é
     * quase nada. A porcentagem é sobre o número de casas, que é o que
     * cresce com o quadrado do tamanho — é a mesma densidade de rota em
     * qualquer tamanho.
     */
    public int voltas() {
        return sorteio().voltas();
    }

    /** Os três níveis de volta que o sorteio pode tirar, em porcentagem. */
    public List<Integer> voltasPorCento() {
        List<Integer> lista = config.getIntegerList("arena.voltas-por-cento");
        return lista.isEmpty() ? List.of(4, 7, 12) : List.copyOf(lista);
    }

    /** Quantas voltas uma porcentagem dá num labirinto de tantas células. */
    public static int voltasDe(int celulas, int porCento) {
        return Math.max(0, celulas * celulas * Math.max(0, porCento) / 100);
    }

    /** Como chamar o nível de volta desta rodada, para o jogador. */
    public String nomeDasVoltas() {
        List<Integer> niveis = voltasPorCento();
        int pct = Math.round(voltas() * 100f / Math.max(1, celulas() * celulas()));
        int menor = niveis.get(0);
        int maior = niveis.get(niveis.size() - 1);
        if (pct <= menor) {
            return "poucas voltas";
        }
        return pct >= maior ? "muitas voltas" : "voltas na medida";
    }

    /**
     * Os traçados que o sorteio pode tirar.
     *
     * Por nome no yml e não por número, para dar para desligar um sem contar
     * de cabeça qual é o índice dele. Nome desconhecido é recusado pela
     * conferência: um erro de digitação que virasse "usa o primeiro" deixaria
     * o jogo com um traçado só e ninguém saberia por quê.
     */
    public List<String> tracadosEscritos() {
        List<String> lista = config.getStringList("arena.tracados");
        return lista.isEmpty() ? LabirintoTracados.NOMES : List.copyOf(lista);
    }

    /** Os traçados ligados, já convertidos em número. */
    public List<Integer> tracadosLigados() {
        List<Integer> saida = new ArrayList<>();
        for (String nome : tracadosEscritos()) {
            int qual = LabirintoTracados.porNome(nome);
            if (qual >= 0 && !saida.contains(qual)) {
                saida.add(qual);
            }
        }
        return saida.isEmpty() ? List.of(LabirintoTracados.FUNDO)
                : List.copyOf(saida);
    }

    /** O traçado desta rodada. */
    public int tracado() {
        return sorteio().tracado();
    }

    public String nomeDoTracado() {
        return LabirintoTracados.nome(tracado());
    }

    public String caraterDoTracado() {
        return LabirintoTracados.carater(tracado());
    }

    /**
     * Prende o sorteio num valor, para evento e para teste.
     *
     * -1 sorteia de verdade. Qualquer outro valor faz toda rodada tirar o
     * mesmo labirinto — que é o que um torneio precisa, e o que o teste
     * precisa para a mesma pergunta dar sempre a mesma resposta.
     */
    public long sorteioFixo() {
        return config.getLong("arena.sorteio-fixo", -1L);
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

    /** O lado do MAIOR labirinto que pode sair: a área que a obra varre. */
    public int ladoMaximo() {
        return lado(celulasMaximo(), celula(), parede());
    }

    /** A maior coordenada do labirinto: ele vai de -meio a +meio. */
    public int meio() {
        return (lado() - 1) / 2;
    }

    /** O mesmo, para o maior labirinto possível. */
    public int meioMaximo() {
        return (ladoMaximo() - 1) / 2;
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return meioMaximo() + saidaFundo() + 4;
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
            case MURO -> corDoEstilo("muro", Material.PRISMARINE_BRICKS);
            case PISO -> corDoEstilo("piso", Material.STONE_BRICKS);
            default -> Material.AIR;
        };
    }

    // ------------------------------------------------------------------
    //  Os estilos
    // ------------------------------------------------------------------

    /**
     * Os nomes dos estilos, na ordem em que estão no yml.
     *
     * O estilo é só paleta — piso, muro, topo — e é de propósito: trocar
     * material não muda regra nenhuma, então um estilo novo nunca pode
     * quebrar o jogo. É a variação mais barata que existe e é a que o
     * jogador mais vê, porque ele corre olhando para o muro.
     */
    public List<String> estilos() {
        org.bukkit.configuration.ConfigurationSection secao =
                config.getConfigurationSection("estilos");
        if (secao == null) {
            return List.of();
        }
        return List.copyOf(secao.getKeys(false));
    }

    public int estilo() {
        return sorteio().estilo();
    }

    public String nomeDoEstilo() {
        List<String> lista = estilos();
        if (lista.isEmpty()) {
            return "padrão";
        }
        return lista.get(Math.max(0, Math.min(lista.size() - 1, estilo())));
    }

    /**
     * Um material do estilo da rodada, caindo para a paleta se ele não disser.
     *
     * A queda para a paleta é o que permite um estilo trocar só três
     * materiais e herdar o resto: sem ela, cada estilo novo teria de repetir
     * a paleta inteira, e a décima cópia divergiria da primeira em silêncio.
     */
    public Material corDoEstilo(String chave, Material padrao) {
        return corDoEstilo(estilo(), chave, padrao);
    }

    /**
     * O mesmo, para um estilo qualquer da lista.
     *
     * Existe para a conferência da obra poder olhar os CINCO estilos antes de
     * pôr um bloco. Olhar só o da rodada deixaria um estilo com o muro da cor
     * do piso esperando o dia em que fosse sorteado — e aí o labirinto sai
     * invisível, no meio de uma partida, sem nada no log.
     */
    public Material corDoEstilo(int qual0, String chave, Material padrao) {
        List<String> lista = estilos();
        if (!lista.isEmpty()) {
            String qual = lista.get(Math.max(0,
                    Math.min(lista.size() - 1, qual0)));
            String nome = config.getString("estilos." + qual + "." + chave);
            if (nome != null) {
                Material achado = Material.matchMaterial(
                        nome.trim().toUpperCase(java.util.Locale.ROOT));
                if (achado != null) {
                    return achado;
                }
                plugin.getLogger().warning("[labirinto] Material desconhecido "
                        + "em estilos." + qual + "." + chave + ": " + nome
                        + " — usando a paleta.");
            }
        }
        return cor(chave, padrao);
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    /**
     * O teto da partida, medido no labirinto que saiu.
     *
     * Um teto fixo não serve mais. Antes servia: um tamanho só, uma janela de
     * passos só. Agora a mesma arena tira rodadas de dez esquinas e rodadas
     * de noventa, e um número que caiba nas duas ou dá uma eternidade de
     * espera na curta ou corta a longa no meio. Então o teto é a caminhada
     * vezes um tempo por esquina, mais uma folga — e um teto duro por cima,
     * porque nenhuma rodada pode durar para sempre.
     */
    @Override
    public int tempoLimite() {
        return relogioDe(passosAteASaida(),
                Math.max(10, config.getInt("regras.tempo-base", 45)),
                Math.max(1, config.getInt("regras.segundos-por-passo", 5)),
                Math.max(60, config.getInt("regras.tempo-limite", 420)));
    }

    /** A mesma conta, pura: o teste confere o relógio sem subir servidor. */
    public static int relogioDe(int passos, int base, int porPasso, int teto) {
        return Math.max(30, Math.min(teto, base + Math.max(0, passos) * porPasso));
    }

    // ------------------------------------------------------------------
    //  O labirinto do dia
    // ------------------------------------------------------------------

    /**
     * O sorteio desta rodada, tirando um novo se ainda não houver.
     *
     * Tira um sozinho na primeira pergunta para que o comando {@code info} e
     * a conferência da obra funcionem antes de qualquer partida: sem isso, um
     * admin que rodasse {@code /biga labirinto construir} com o servidor
     * recém-ligado levaria um erro em vez de um labirinto.
     */
    public Sorteio sorteio() {
        if (sorteioAtual == null) {
            sortearRodada();
        }
        return sorteioAtual;
    }

    /**
     * Tira um labirinto novo: traçado, tamanho, estilo, voltas e semente.
     *
     * É chamado no começo de toda rodada. Com {@code sorteio-fixo} ligado no
     * yml, tira sempre o mesmo — que é o que um torneio precisa.
     */
    public Sorteio sortearRodada() {
        long fixo = sorteioFixo();
        long semente = fixo >= 0 ? fixo : new Random().nextLong();
        return sortearRodada(semente);
    }

    /** O mesmo, com a semente na mão: é por aqui que o teste entra. */
    public Sorteio sortearRodada(long semente) {
        sorteioAtual = sortear(semente, tamanhos(), tracadosLigados(),
                Math.max(1, estilos().size()), voltasPorCento());
        sorteioEmCache = null;
        return sorteioAtual;
    }

    /**
     * O sorteio, puro: a mesma semente dá sempre o mesmo labirinto.
     *
     * Puro e estático para o teste poder rodar mil sorteios por segundo sem
     * servidor nenhum, e para a pergunta "este sorteio pode sair?" ter
     * resposta sem ter de subir um mundo.
     */
    public static Sorteio sortear(long semente, List<Integer> tamanhos,
                                  List<Integer> tracados, int quantosEstilos,
                                  List<Integer> pctVoltas) {
        Random sorte = new Random(semente);
        int tracado = tracados.isEmpty() ? LabirintoTracados.FUNDO
                : tracados.get(sorte.nextInt(tracados.size()));
        int celulas = tamanhos.isEmpty() ? 13
                : tamanhos.get(sorte.nextInt(tamanhos.size()));
        celulas = Math.max(7, celulas % 2 == 0 ? celulas + 1 : celulas);
        int estilo = quantosEstilos <= 0 ? 0 : sorte.nextInt(quantosEstilos);
        int pct = pctVoltas.isEmpty() ? 7
                : pctVoltas.get(sorte.nextInt(pctVoltas.size()));
        return new Sorteio(tracado, celulas, estilo, voltasDe(celulas, pct),
                sorte.nextLong());
    }

    private void garantirCache() {
        Sorteio agora = sorteio();
        if (agora.equals(sorteioEmCache) && ligadoEmCache != null) {
            return;
        }
        int n = agora.celulas();
        ligadoEmCache = LabirintoTracados.cavar(agora.tracado(), n,
                agora.semente(), agora.voltas(), sala());
        distanciaEmCache = distancias(n, ligadoEmCache, sala());
        saidaEmCache = portaLonge(n, ligadoEmCache, sala(), agora.semente());
        distanciaDaPortaEmCache = distanciasDe(n, ligadoEmCache,
                saidaEmCache[0], saidaEmCache[1]);
        sorteioEmCache = agora;
    }

    /** As passagens abertas do labirinto desta rodada. */
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

    /** A saída desta rodada: {i, j, lado}. */
    public int[] saida() {
        garantirCache();
        return saidaEmCache;
    }

    /** A semente do mapa desta rodada. */
    public long semente() {
        return sorteio().semente();
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
     * A porta: {i, j, lado}, escolhida entre as mais longes do meio.
     *
     * Mede a distância do meio até cada casa da borda, põe as mais longes na
     * frente e sorteia entre o primeiro terço. É o que substituiu a
     * re-sortagem da semente, e é melhor por três motivos.
     *
     * <p>Primeiro: funciona em qualquer traçado. A janela fixa de passos não
     * funcionava — no GALHOS, que é um labirinto de becos curtos, treze
     * células não chegam a doze passos nem no melhor caso, então exigir doze
     * dele era re-sortear sessenta e quatro vezes e aceitar o último de
     * qualquer jeito.
     *
     * <p>Segundo: nunca precisa de segunda tentativa. A borda tem sempre
     * casas, as casas têm sempre distância, e a mais longe delas é a resposta
     * — não existe sorteio ruim para descartar.
     *
     * <p>Terceiro, e é o que mais importa para o jogo: a caminhada passa a ser
     * das mais longas QUE AQUELE labirinto tinha para oferecer, em vez de uma
     * caminhada qualquer que coube numa janela. Um labirinto apertado dá uma
     * corrida apertada; um labirinto largo dá uma corrida larga. O relógio da
     * partida é que se ajusta ao labirinto — ver {@link #tempoLimite()} — e
     * não o contrário.
     *
     * <p>Sorteia entre as mais longes, e não pega a campeã, porque a campeã é
     * uma só: dez rodadas no mesmo traçado e mesmo tamanho cairiam na mesma
     * esquina. O terço é largo o bastante para a porta mudar e estreito o
     * bastante para a caminhada nunca encurtar.
     *
     * <p>Canto nunca: é lá que ficam as torres, e um patamar saindo do canto
     * encostaria nas duas.
     */
    public static int[] portaLonge(int celulas, boolean[] ligado, int sala,
                                   long semente) {
        return portaLonge(celulas, ligado, sala, semente, 3);
    }

    /** O mesmo, dizendo em quantas fatias a borda se divide. */
    public static int[] portaLonge(int celulas, boolean[] ligado, int sala,
                                   long semente, int fatia) {
        int[] dist = distancias(celulas, ligado, sala);
        List<int[]> borda = new ArrayList<>();
        for (int k = 1; k < celulas - 1; k++) {
            borda.add(new int[]{k, 0, NORTE});
            borda.add(new int[]{k, celulas - 1, SUL});
            borda.add(new int[]{0, k, OESTE});
            borda.add(new int[]{celulas - 1, k, LESTE});
        }
        borda.removeIf(casa -> dist[indice(celulas, casa[0], casa[1])] < 0);
        if (borda.isEmpty()) {
            return new int[]{celulas / 2, 0, NORTE};
        }
        borda.sort((a, b) -> Integer.compare(dist[indice(celulas, b[0], b[1])],
                dist[indice(celulas, a[0], a[1])]));
        int quantas = Math.max(1, borda.size() / Math.max(1, fatia));
        Random sorte = new Random(semente ^ 0x5A1DAL);
        return borda.get(sorte.nextInt(quantas));
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
