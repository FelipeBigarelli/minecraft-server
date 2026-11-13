package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O labirinto sorteado, cavado sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Uma célula nascer ILHADA: o jogador que entrar nela sai, mas o mapa tem um
 * pedaço que não leva a lugar nenhum e ninguém entende por quê. A grade dizer
 * que passa e o mapa de blocos não abrir o vão: labirinto IMPOSSÍVEL, com
 * dezesseis pessoas dentro e nenhuma linha de erro no log. A porta cair a
 * cinco passos da sala: uma partida de dez segundos. Sobrar um segundo buraco
 * na borda: dois caminhos de saída num jogo que promete um. Os números do yml
 * virarem pares: a sala do meio sai meio bloco torta do (0,0), que é onde o
 * saguão fica e onde todo mundo larga.
 *
 * <p>Nada disso quebra. Tudo isso deixa o jogo de pé e errado.
 *
 * <h2>O que mudou, e por que o teste mudou com ele</h2>
 *
 * Antes havia UM traçado, UM tamanho e um mapa por dia, e o teste andava
 * quatrocentos dias para cobrir o ano. Agora há cinco traçados, cinco
 * tamanhos, cinco estilos e três níveis de volta, sorteados a cada rodada —
 * e um traçado que só falhasse em vinte e cinco células apareceria numa
 * madrugada de terça, numa rodada, para dezesseis pessoas presas dentro.
 * Então o teste anda o PRODUTO: cada traçado em cada tamanho, com sementes
 * de sobra. É mais barato que os quatrocentos dias e cobre muito mais.
 *
 * <h2>E prova a coisa que o jogador reclamou</h2>
 *
 * A reclamação foi "um labirinto só acaba decorado". A resposta não é mapa
 * novo — mapa novo com a mesma cara continua sendo o mesmo labirinto. A
 * resposta é cinco algoritmos com TEXTURAS mensuravelmente diferentes, e é isso
 * que {@link #osCincoTracadosTemTexturaPropria} mede, número por número. Se
 * alguém trocar um algoritmo por outro que pareça o vizinho, este teste cai.
 */
class LabirintoTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int CELULA = 3;
    private static final int PAREDE = 1;
    private static final int PAREDE_ALTURA = 3;
    private static final int SALA = 1;
    private static final int SAIDA_FUNDO = 5;
    private static final int SAIDA_LARGURA = 7;
    private static final int GIZ = 12;
    private static final int MAXIMO = 16;

    private static final List<Integer> TAMANHOS = List.of(9, 13, 17, 21, 25);
    private static final List<String> TRACADOS =
            List.of("FUNDO", "GALHOS", "VENTANIA", "JUSTO", "SALOES");
    private static final List<Integer> VOLTAS_PCT = List.of(4, 7, 12);
    private static final List<String> ESTILOS =
            List.of("prismarinho", "catacumba", "jardim", "forja", "gelo");

    private static final int TEMPO_BASE = 45;
    private static final int POR_PASSO = 5;
    private static final int TEMPO_LIMITE = 420;

    /** Quantas sementes por traçado e por tamanho. */
    private static final int SEMENTES = 40;

    /** O tamanho em que a textura é medida: o do meio da lista. */
    private static final int N = 13;

    private static final int LADO = LabirintoCatalog.lado(N, CELULA, PAREDE);
    private static final int MEIO = (LADO - 1) / 2;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = LabirintoTest.class.getResourceAsStream("/labirinto.yml")) {
            assertNotNull(in, "labirinto.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** O labirinto como o jogo o constrói: com voltas e com a sala aberta. */
    private static boolean[] doJogo(int tracado, int celulas, long semente,
                                    int pct) {
        return LabirintoTracados.cavar(tracado, celulas, semente,
                LabirintoCatalog.voltasDe(celulas, pct), SALA);
    }

    /**
     * O traçado CRU, sem voltas e sem sala.
     *
     * A textura é do algoritmo, e é aqui que ela se mede. Abrir voltas só
     * derruba becos — nunca cria — então medir depois das voltas mediria uma
     * mistura de duas decisões e ninguém saberia de qual veio o número.
     */
    private static boolean[] cru(int tracado, int celulas, long semente) {
        return LabirintoTracados.cavar(tracado, celulas, semente, 0, 0);
    }

    private static long semente(int tracado, int celulas, int k) {
        return 500000L + k * 97L + celulas * 13L + tracado * 7919L;
    }

    private static int[] porta(int celulas, boolean[] ligado, long semente) {
        return LabirintoCatalog.portaLonge(celulas, ligado, SALA, semente);
    }

    private static int passos(int celulas, boolean[] ligado, int[] saida) {
        return LabirintoCatalog.distancias(celulas, ligado, SALA)
                [LabirintoCatalog.indice(celulas, saida[0], saida[1])];
    }

    /** A média de uma medida sobre as sementes, no traçado cru. */
    private static double media(int tracado, java.util.function.ToDoubleFunction<boolean[]> medida) {
        double soma = 0;
        for (int k = 0; k < SEMENTES; k++) {
            soma += medida.applyAsDouble(cru(tracado, N, semente(tracado, N, k)));
        }
        return soma / SEMENTES;
    }

    private static double pctDeGrau(boolean[] ligado, int grau, boolean ouMais) {
        int[] graus = LabirintoTracados.graus(N, ligado);
        int quantos = 0;
        for (int d : graus) {
            if (ouMais ? d >= grau : d == grau) {
                quantos++;
            }
        }
        return 100.0 * quantos / graus.length;
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(TAMANHOS, arena.getIntegerList("arena.tamanhos"));
        assertEquals(TRACADOS, arena.getStringList("arena.tracados"));
        assertEquals(VOLTAS_PCT, arena.getIntegerList("arena.voltas-por-cento"));
        assertEquals(CELULA, arena.getInt("arena.celula"));
        assertEquals(PAREDE, arena.getInt("arena.parede"));
        assertEquals(PAREDE_ALTURA, arena.getInt("arena.parede-altura"));
        assertEquals(SALA, arena.getInt("arena.sala"));
        assertEquals(SAIDA_FUNDO, arena.getInt("arena.saida-fundo"));
        assertEquals(SAIDA_LARGURA, arena.getInt("arena.saida-largura"));
        assertEquals(GIZ, arena.getInt("regras.giz"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_BASE, arena.getInt("regras.tempo-base"));
        assertEquals(POR_PASSO, arena.getInt("regras.segundos-por-passo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertEquals(-1, arena.getLong("arena.sorteio-fixo"),
                "o yml de produção não pode sair com o sorteio preso: seria um "
                        + "labirinto só, que é exatamente o problema que este "
                        + "jogo tinha");
        assertNotNull(arena.getConfigurationSection("estilos"),
                "sem a seção 'estilos' o sorteio perde um quinto das combinações");
        assertEquals(ESTILOS,
                List.copyOf(arena.getConfigurationSection("estilos").getKeys(false)));
    }

    @Test
    @DisplayName("os cinco traçados do yml existem todos")
    void osTracadosDoYmlExistem() {
        for (String nome : TRACADOS) {
            assertTrue(LabirintoTracados.porNome(nome) >= 0,
                    "'" + nome + "' não é traçado nenhum");
        }
        assertEquals(LabirintoTracados.quantos(), TRACADOS.size(),
                "o yml liga " + TRACADOS.size() + " traçado(s) dos "
                        + LabirintoTracados.quantos() + " que existem: um "
                        + "traçado escrito e nunca sorteado é código morto");
        assertEquals(LabirintoTracados.SALOES, LabirintoTracados.porNome("saloes"),
                "o nome tem til e o yml é escrito num teclado qualquer: 'saloes' "
                        + "sem til tem de achar o mesmo traçado");
        assertEquals(LabirintoTracados.SALOES, LabirintoTracados.porNome(" Salões "),
                "caixa e espaço em volta não podem desligar um traçado");
        assertEquals(-1, LabirintoTracados.porNome("ESPIRAL"),
                "nome que não existe tem de dar -1, para a obra recusar em vez "
                        + "de usar o primeiro em silêncio");
    }

    @Test
    @DisplayName("a planta aceita o yml inteiro, sorteio e estilos incluídos")
    void plantaAceitaOYml() {
        assertNull(LabirintoBuilder.conferirSorteio(TAMANHOS, TRACADOS, ESTILOS,
                VOLTAS_PCT));
        assertNull(LabirintoBuilder.conferirEstilos(ESTILOS, coresDoYml()));
        assertNull(LabirintoBuilder.conferirTodos(TAMANHOS, numerosDosTracados(),
                VOLTAS_PCT, CELULA, PAREDE, PAREDE_ALTURA, SALA, SAIDA_LARGURA,
                SAIDA_FUNDO));
    }

    @Test
    @DisplayName("o sorteio do yml dá as 375 combinações prometidas")
    void oSorteioDaAsCombinacoesPrometidas() {
        int quantas = TRACADOS.size() * TAMANHOS.size() * ESTILOS.size()
                * VOLTAS_PCT.size();
        assertEquals(375, quantas, "cinco traçados x cinco tamanhos x cinco "
                + "estilos x três níveis de volta são 375, e é esse número que "
                + "o cabeçalho do yml promete");
        assertTrue(quantas >= 20, "o pedido era 'pelo menos umas 20 variações'");
    }

    // ------------------------------------------------------------------
    //  As conferências
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a conferência pega célula par, muro baixo e sala grande demais")
    void conferenciaPegaOsErros() {
        assertNotNull(LabirintoBuilder.conferirNumeros(12, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO),
                "os tamanhos são ímpares de propósito: com par a sala sai torta");
        assertNotNull(LabirintoBuilder.conferirNumeros(5, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO),
                "cinco células não dá labirinto, dá corredor");
        assertNotNull(LabirintoBuilder.conferirNumeros(N, 2, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO));
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, 2,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO));
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, PAREDE,
                2, SALA, SAIDA_LARGURA, SAIDA_FUNDO),
                "muro de dois blocos se pula, e um labirinto que se pula não é um");
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, PAREDE,
                PAREDE_ALTURA, 5, SAIDA_LARGURA, SAIDA_FUNDO),
                "sala de raio 5 em 13 células encosta na borda");
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, 2, SAIDA_FUNDO),
                "patamar mais estreito que o corredor que ele recebe");
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, 6, SAIDA_FUNDO),
                "patamar de largura par não fica centrado no vão");
        assertNotNull(LabirintoBuilder.conferirNumeros(N, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, 1));
    }

    @Test
    @DisplayName("o sorteio que encolheu abaixo de vinte labirintos é recusado")
    void sorteioPequenoEhRecusado() {
        assertNotNull(LabirintoBuilder.conferirSorteio(List.of(13),
                List.of("FUNDO"), ESTILOS, List.of(7)),
                "um traçado, um tamanho, um nível de volta e cinco estilos dão "
                        + "cinco combinações: é o labirinto que se decora de "
                        + "volta, e foi essa a reclamação");
        assertNotNull(LabirintoBuilder.conferirSorteio(List.of(13),
                List.of("FUNDO"), ESTILOS, VOLTAS_PCT),
                "um traçado, um tamanho, cinco estilos e três níveis de volta "
                        + "dão quinze: ainda abaixo das vinte pedidas");
        // E a conferência é um PRODUTO de propósito: deixa o dono escolher
        // como chegar às vinte. Um traçado só, com cinco tamanhos e cinco
        // estilos, dá vinte e cinco — e passa.
        assertNull(LabirintoBuilder.conferirSorteio(TAMANHOS, List.of("FUNDO"),
                ESTILOS, List.of(7)),
                "cinco tamanhos x cinco estilos dão vinte e cinco combinações "
                        + "mesmo com um traçado só: a conferência não pode "
                        + "exigir quatro mínimos separados");
    }

    @Test
    @DisplayName("a conferência do sorteio pega lista vazia, nome errado e repetição")
    void conferenciaDoSorteioPegaOsErros() {
        assertNotNull(LabirintoBuilder.conferirSorteio(List.of(), TRACADOS,
                ESTILOS, VOLTAS_PCT), "sem tamanho nenhum não há o que sortear");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS, List.of(),
                ESTILOS, VOLTAS_PCT), "sem traçado nenhum não há o que cavar");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS, TRACADOS,
                ESTILOS, List.of()), "sem nível de volta não há o que abrir");
        assertNotNull(LabirintoBuilder.conferirSorteio(List.of(9, 13, 13),
                TRACADOS, ESTILOS, VOLTAS_PCT),
                "tamanho repetido não acrescenta labirinto, só chance de repetir");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS,
                List.of("FUNDO", "ESPIRAL"), ESTILOS, VOLTAS_PCT),
                "nome de traçado que não existe tem de ser recusado, para um "
                        + "erro de digitação não virar silêncio");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS,
                List.of("FUNDO", "fundo", "GALHOS"), ESTILOS, VOLTAS_PCT),
                "traçado repetido só faz ele sair com o dobro da chance");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS, TRACADOS,
                ESTILOS, List.of(12, 7, 4)),
                "fora de ordem a planta chama de 'poucas voltas' o nível que "
                        + "mais abre, e anuncia o contrário do que construiu");
        assertNotNull(LabirintoBuilder.conferirSorteio(TAMANHOS, TRACADOS,
                ESTILOS, List.of(4, 7, 60)),
                "sessenta por cento das casas abertas é descampado, não labirinto");
    }

    @Test
    @DisplayName("a conferência dos estilos pega piso da cor do muro e estilo clonado")
    void conferenciaDosEstilosPegaOsErros() {
        List<Material> um = List.of(Material.STONE_BRICKS,
                Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE);
        List<Material> invisivel = List.of(Material.STONE_BRICKS,
                Material.STONE_BRICKS, Material.DARK_PRISMARINE);
        assertNotNull(LabirintoBuilder.conferirEstilos(List.of("so-um"),
                List.of(um)), "um estilo só é o jogo sem a variação mais barata "
                + "que existe");
        assertNotNull(LabirintoBuilder.conferirEstilos(
                List.of("bom", "cego"), List.of(um, invisivel)),
                "piso da cor do muro é um labirinto invisível, e o jogador só "
                        + "descobre batendo o nariz nele");
        assertNotNull(LabirintoBuilder.conferirEstilos(
                List.of("um", "copia"), List.of(um, um)),
                "dois estilos com piso, muro e topo iguais são um estilo com "
                        + "dois nomes");
        assertNull(LabirintoBuilder.conferirEstilos(ESTILOS, coresDoYml()));
    }

    // ------------------------------------------------------------------
    //  A TEXTURA — o coração desta reforma
    // ------------------------------------------------------------------

    /**
     * Os cinco traçados são mensuravelmente diferentes.
     *
     * É o teste que sustenta a resposta dada à reclamação. Vinte labirintos
     * desenhados à mão teriam vinte mapas e UMA textura; cinco algoritmos têm
     * cinco. A diferença não é opinião — são os números abaixo, e cada um deles
     * é a assinatura de uma decisão do algoritmo:
     *
     * <ul>
     *   <li>FUNDO empilha e só volta atrás na parede: um décimo de becos e
     *       quatro quintos de casas de passagem. É o único que faz corredor.</li>
     *   <li>GALHOS sorteia na fronteira: um terço de becos, o avesso exato.</li>
     *   <li>VENTANIA fecha cada corrida cavando para o NORTE: nenhum beco abre
     *       ao sul, e a fiada do norte é uma avenida de ponta a ponta.</li>
     *   <li>SALÕES parte o terreno com muro reto: o maior muro reto dos cinco,
     *       com folga.</li>
     *   <li>JUSTO é sorteado sem viés: não tem assinatura nenhuma, e é isso
     *       que se confere nele.</li>
     * </ul>
     */
    @Test
    @DisplayName("os cinco traçados têm textura própria e medível")
    void osCincoTracadosTemTexturaPropria() {
        double[] beco = new double[LabirintoTracados.quantos()];
        double[] passa = new double[LabirintoTracados.quantos()];
        double[] muro = new double[LabirintoTracados.quantos()];
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            beco[t] = media(t, g -> pctDeGrau(g, 1, false));
            passa[t] = media(t, g -> pctDeGrau(g, 2, false));
            muro[t] = media(t, g -> LabirintoTracados.maiorMuroReto(N, g));
        }

        // FUNDO: o labirinto de corredor. Medido: 11,3% de beco, 78,7% de
        // passagem.
        assertTrue(beco[LabirintoTracados.FUNDO] < 15, "FUNDO saiu com "
                + beco[LabirintoTracados.FUNDO] + "% de becos: acima de 15 ele "
                + "deixou de ser o traçado de corredor comprido e passou a "
                + "parecer os outros");
        assertTrue(passa[LabirintoTracados.FUNDO] > 70, "FUNDO saiu com "
                + passa[LabirintoTracados.FUNDO] + "% de casas de passagem: "
                + "abaixo de 70 o corredor comprido virou emaranhado");

        // GALHOS: o avesso. Medido: 35,5% de beco.
        assertTrue(beco[LabirintoTracados.GALHOS] > 32, "GALHOS saiu com "
                + beco[LabirintoTracados.GALHOS] + "% de becos: abaixo de 32 ele "
                + "deixou de ser o labirinto de escolher");

        // E são as duas pontas: nenhum outro ultrapassa nenhum dos dois.
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            assertTrue(beco[t] >= beco[LabirintoTracados.FUNDO],
                    LabirintoTracados.nome(t) + " tem menos becos que o FUNDO ("
                            + beco[t] + "% contra " + beco[LabirintoTracados.FUNDO]
                            + "%): o FUNDO é o traçado de corredor e tem de ser "
                            + "o extremo");
            assertTrue(beco[t] <= beco[LabirintoTracados.GALHOS],
                    LabirintoTracados.nome(t) + " tem mais becos que o GALHOS ("
                            + beco[t] + "% contra " + beco[LabirintoTracados.GALHOS]
                            + "%): o GALHOS é o traçado de beco e tem de ser o "
                            + "outro extremo");
        }
        assertTrue(beco[LabirintoTracados.GALHOS] - beco[LabirintoTracados.FUNDO] > 18,
                "entre o traçado de menos becos e o de mais becos há só "
                        + (beco[LabirintoTracados.GALHOS] - beco[LabirintoTracados.FUNDO])
                        + " pontos: os cinco estão convergindo para a mesma "
                        + "textura e o sorteio deixa de se notar");

        // SALÕES: muro reto, e com folga sobre todos. Medido: 9,6 contra 6,9.
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            if (t == LabirintoTracados.SALOES) {
                continue;
            }
            assertTrue(muro[LabirintoTracados.SALOES] > muro[t] + 1.5,
                    "SALÕES tem muro reto de " + muro[LabirintoTracados.SALOES]
                            + " células e o " + LabirintoTracados.nome(t)
                            + " tem " + muro[t] + ": sem uma célula e meia de "
                            + "folga a arquitetura dele não se distingue de "
                            + "toca cavada");
        }

        // JUSTO: nenhuma assinatura. É o único de que isso se exige.
        assertTrue(beco[LabirintoTracados.JUSTO] > beco[LabirintoTracados.FUNDO] + 5
                        && beco[LabirintoTracados.JUSTO] < beco[LabirintoTracados.GALHOS] - 3,
                "JUSTO saiu com " + beco[LabirintoTracados.JUSTO] + "% de becos, "
                        + "colado numa das pontas: ele é o sorteio sem viés e o "
                        + "lugar dele é no meio");
    }

    /**
     * A VENTANIA não tem beco que abra para o sul. Nunca.
     *
     * É a única propriedade deste jogo que um jogador pode APRENDER e usar: num
     * beco, o caminho de volta nunca é ao sul. Está testada em semente por
     * semente — e, o que importa mais, testada DEPOIS das voltas e da sala,
     * que é o labirinto que ele vai andar de verdade. A conta de que isso
     * sobrevive é simples e vale a pena saber: abrir uma parede só aumenta o
     * grau de uma casa, então uma casa que não era beco não pode virar beco —
     * zero becos ao sul continua zero.
     */
    @Test
    @DisplayName("a VENTANIA nunca abre beco ao sul, nem depois das voltas")
    void aVentaniaNuncaAbreBecoAoSul() {
        for (int celulas : TAMANHOS) {
            for (int k = 0; k < SEMENTES; k++) {
                long sem = semente(LabirintoTracados.VENTANIA, celulas, k);
                boolean[] crua = cru(LabirintoTracados.VENTANIA, celulas, sem);
                assertEquals(0, LabirintoTracados.becosPara(celulas, crua,
                                LabirintoCatalog.SUL),
                        "VENTANIA crua de " + celulas + " células, semente " + sem
                                + ": apareceu beco virado ao sul, e é essa a "
                                + "única manha que o jogador pode aprender");
                assertEquals(celulas, LabirintoTracados.maiorCorridaDeitada(
                                celulas, crua),
                        "VENTANIA de " + celulas + " células não tem a avenida "
                                + "aberta de ponta a ponta: a fiada do norte é "
                                + "a assinatura dela");
                for (int pct : VOLTAS_PCT) {
                    boolean[] jogo = doJogo(LabirintoTracados.VENTANIA, celulas,
                            sem, pct);
                    assertEquals(0, LabirintoTracados.becosPara(celulas, jogo,
                                    LabirintoCatalog.SUL),
                            "VENTANIA de " + celulas + " células com " + pct
                                    + "% de voltas: as voltas criaram um beco ao "
                                    + "sul. Isso não devia ser possível — abrir "
                                    + "parede só aumenta grau — então quem mexeu "
                                    + "em abrirAsVoltas passou a FECHAR alguma");
                }
            }
        }
        // E os outros têm, senão a propriedade não distingue nada.
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            if (t == LabirintoTracados.VENTANIA) {
                continue;
            }
            assertTrue(media(t, g -> LabirintoTracados.becosPara(N, g,
                            LabirintoCatalog.SUL)) > 2,
                    LabirintoTracados.nome(t) + " também não abre beco ao sul: "
                            + "então a manha da VENTANIA não é manha de ninguém");
        }
    }

    // ------------------------------------------------------------------
    //  Todo traçado em todo tamanho
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todo traçado em todo tamanho é um labirinto inteiro e andável")
    void todoTracadoEmTodoTamanhoEhAndavel() {
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    boolean[] ligado = doJogo(t, celulas, sem, 7);
                    int[] dist = LabirintoCatalog.distancias(celulas, ligado, SALA);
                    for (int idx = 0; idx < dist.length; idx++) {
                        assertTrue(dist[idx] >= 0, LabirintoTracados.nome(t)
                                + " de " + celulas + " células, semente " + sem
                                + ": a célula " + idx + " ficou ilhada");
                    }
                    int[] saida = porta(celulas, ligado, sem);
                    assertNull(LabirintoBuilder.conferirMapa(celulas, CELULA,
                                    PAREDE, ligado, SALA, saida),
                            LabirintoTracados.nome(t) + " de " + celulas
                                    + " células, semente " + sem);
                }
            }
        }
    }

    @Test
    @DisplayName("todo traçado é um labirinto perfeito antes das voltas")
    void todoTracadoSaiPerfeito() {
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < 8; k++) {
                    boolean[] ligado = cru(t, celulas, semente(t, celulas, k));
                    int abertas = 0;
                    for (boolean passa : ligado) {
                        if (passa) {
                            abertas++;
                        }
                    }
                    assertEquals(celulas * celulas - 1, abertas,
                            LabirintoTracados.nome(t) + " de " + celulas
                                    + " células abriu " + abertas + " passagens: "
                                    + "um labirinto perfeito é uma árvore e tem "
                                    + "de ter exatamente células-1. Mais é ciclo "
                                    + "nascido no algoritmo, e aí as voltas "
                                    + "deixam de ser a única fonte de rota "
                                    + "alternativa");
                }
            }
        }
    }

    @Test
    @DisplayName("as voltas abrem rota alternativa em todo traçado e tamanho")
    void asVoltasAbremRota() {
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int pct : VOLTAS_PCT) {
                    boolean[] ligado = doJogo(t, celulas, semente(t, celulas, 3),
                            pct);
                    int abertas = 0;
                    for (boolean passa : ligado) {
                        if (passa) {
                            abertas++;
                        }
                    }
                    int arvore = celulas * celulas - 1;
                    int pedidas = LabirintoCatalog.voltasDe(celulas, pct);
                    assertTrue(abertas >= arvore + pedidas,
                            LabirintoTracados.nome(t) + " de " + celulas
                                    + " células com " + pct + "%: " + abertas
                                    + " passagens para uma árvore de " + arvore
                                    + " mais " + pedidas + " voltas pedidas — "
                                    + "errar a curva volta a custar a partida");
                }
            }
        }
    }

    @Test
    @DisplayName("a porcentagem de voltas dá a mesma densidade em todo tamanho")
    void aDensidadeDeVoltasEhAMesma() {
        for (int pct : VOLTAS_PCT) {
            for (int celulas : TAMANHOS) {
                int voltas = LabirintoCatalog.voltasDe(celulas, pct);
                double densidade = 100.0 * voltas / (celulas * celulas);
                assertTrue(Math.abs(densidade - pct) <= 1.0,
                        pct + "% em " + celulas + " células deu " + voltas
                                + " voltas, que é " + densidade + "% das casas: "
                                + "a porcentagem existe justamente para 12 "
                                + "voltas não ser descampado em 9 células e "
                                + "quase nada em 25");
            }
        }
        assertTrue(LabirintoCatalog.voltasDe(25, 12)
                        > LabirintoCatalog.voltasDe(9, 12) * 5,
                "um labirinto de 25 células tem quase oito vezes as casas de um "
                        + "de 9 e tem de receber voltas na mesma proporção");
    }

    // ------------------------------------------------------------------
    //  A porta
    // ------------------------------------------------------------------

    /**
     * A caminhada vale a pena em qualquer traçado.
     *
     * Era aqui que a solução antiga quebrava, e é por isso que ela foi
     * trocada. A janela fixa de passos pedia doze esquinas: o GALHOS em treze
     * células não chega a doze no melhor caso, então a janela re-sorteava
     * sessenta e quatro vezes e aceitava o último de qualquer jeito. Escolher
     * a porta entre as mais longes do meio resolve sem re-sortear nada — e o
     * pior caso medido, em cinco traçados vezes cinco tamanhos vezes quarenta
     * sementes, é 0,56 célula de caminhada por célula de grade.
     */
    @Test
    @DisplayName("a porta fica longe do meio em qualquer traçado e tamanho")
    void aPortaFicaLonge() {
        double pior = 99;
        String ondePior = "";
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    boolean[] ligado = doJogo(t, celulas, sem, 7);
                    int[] saida = porta(celulas, ligado, sem);
                    int quantos = passos(celulas, ligado, saida);
                    double razao = quantos * 1.0 / celulas;
                    if (razao < pior) {
                        pior = razao;
                        ondePior = LabirintoTracados.nome(t) + " de " + celulas
                                + " células (" + quantos + " passos)";
                    }
                    assertTrue(quantos * 2 >= celulas, LabirintoTracados.nome(t)
                            + " de " + celulas + " células, semente " + sem
                            + ": a porta ficou a " + quantos + " passos da sala, "
                            + "menos de meia grade. Isso não é labirinto, é "
                            + "corredor");
                }
            }
        }
        assertTrue(pior >= 0.5, "o pior caso foi " + pior + " em " + ondePior);
        assertTrue(pior < 1.0, "o melhor dos piores casos foi " + pior
                + ": se nem o pior caso fica abaixo de uma célula de caminhada "
                + "por célula de grade, a escolha da porta parou de sortear e "
                + "passou a pegar sempre a campeã — e aí a porta é sempre a "
                + "mesma esquina");
    }

    @Test
    @DisplayName("a porta muda de rodada para rodada no mesmo labirinto")
    void aPortaMudaEntreRodadas() {
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                Set<String> vistas = new HashSet<>();
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    int[] saida = porta(celulas, doJogo(t, celulas, sem, 7), sem);
                    vistas.add(saida[0] + ":" + saida[1]);
                }
                // O terço da borda de um labirinto de nove células tem nove
                // casas; o de vinte e cinco tem trinta. O mínimo acompanha,
                // senão este teste exigiria do pequeno mais esquinas do que
                // ele tem.
                int minimo = Math.max(6, Math.min(SEMENTES / 3, celulas));
                assertTrue(vistas.size() >= minimo,
                        LabirintoTracados.nome(t) + " de " + celulas
                                + " células: em " + SEMENTES + " rodadas a porta "
                                + "caiu em " + vistas.size() + " esquina(s) "
                                + "diferente(s), menos que " + minimo
                                + ". A escolha sorteia entre as mais longes de "
                                + "propósito: pegar a campeã daria sempre a "
                                + "mesma esquina");
            }
        }
    }

    @Test
    @DisplayName("a porta nunca cai num canto, onde ficam as torres")
    void aPortaNuncaCaiNoCanto() {
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    int[] saida = porta(celulas, doJogo(t, celulas, sem, 7), sem);
                    boolean cantoI = saida[0] == 0 || saida[0] == celulas - 1;
                    boolean cantoJ = saida[1] == 0 || saida[1] == celulas - 1;
                    assertTrue(!(cantoI && cantoJ), LabirintoTracados.nome(t)
                            + " de " + celulas + ": a porta saiu na célula de "
                            + "canto (" + saida[0] + "," + saida[1] + "), e o "
                            + "patamar dela encostaria nas duas torres");
                    assertTrue(saida[2] >= 0 && saida[2] <= 3, "lado inválido");
                    assertTrue(cantoI || cantoJ, LabirintoTracados.nome(t)
                            + " de " + celulas + ": a porta saiu em ("
                            + saida[0] + "," + saida[1] + "), que não é borda "
                            + "nenhuma — porta no meio do labirinto é buraco");
                }
            }
        }
    }

    @Test
    @DisplayName("os quatro lados saem, e nenhum domina")
    void osQuatroLadosSaem() {
        int[] quantos = new int[4];
        int total = 0;
        for (int celulas : TAMANHOS) {
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    quantos[porta(celulas, doJogo(t, celulas, sem, 7), sem)[2]]++;
                    total++;
                }
            }
        }
        for (int lado = 0; lado < 4; lado++) {
            double pct = 100.0 * quantos[lado] / total;
            assertTrue(pct >= 12, "a porta saiu em " + pct + "% das rodadas no "
                    + "lado " + LabirintoBuilder.nomeDoLado(lado) + ": abaixo de "
                    + "doze por cento os jogadores decoram os outros três e o "
                    + "facho perde a graça");
            assertTrue(pct <= 40, "a porta saiu em " + pct + "% das rodadas no "
                    + "lado " + LabirintoBuilder.nomeDoLado(lado) + ": acima de "
                    + "quarenta por cento esse lado é o palpite certo e a "
                    + "corrida começa antes do facho acender");
        }
    }

    @Test
    @DisplayName("a borda é fechada, menos pelo vão da porta")
    void aBordaEhFechada() {
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                long sem = semente(t, celulas, 5);
                boolean[] ligado = doJogo(t, celulas, sem, 7);
                int[] saida = porta(celulas, ligado, sem);
                int[] mapa = LabirintoCatalog.mapaDeBlocos(celulas, CELULA,
                        PAREDE, ligado, SALA, saida);
                int vaos = 0;
                for (int u = 0; u < lado; u++) {
                    for (int v = 0; v < lado; v++) {
                        boolean naBorda = u == 0 || v == 0 || u == lado - 1
                                || v == lado - 1;
                        if (naBorda && mapa[v * lado + u] == LabirintoCatalog.PISO) {
                            vaos++;
                        }
                    }
                }
                assertEquals(CELULA * PAREDE, vaos, LabirintoTracados.nome(t)
                        + " de " + celulas + " células: a borda tem " + vaos
                        + " blocos de vão e a porta pede " + CELULA * PAREDE);
            }
        }
    }

    // ------------------------------------------------------------------
    //  O sorteio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a mesma semente dá sempre o mesmo sorteio e o mesmo mapa")
    void mesmaSementeMesmoLabirinto() {
        for (long semente = 0; semente < 200; semente++) {
            assertEquals(
                    LabirintoCatalog.sortear(semente, TAMANHOS,
                            numerosDosTracados(), ESTILOS.size(), VOLTAS_PCT),
                    LabirintoCatalog.sortear(semente, TAMANHOS,
                            numerosDosTracados(), ESTILOS.size(), VOLTAS_PCT),
                    "o sorteio da semente " + semente + " mudou entre duas "
                            + "chamadas: com isso 'sorteio-fixo' não prenderia "
                            + "nada e um torneio sairia diferente a cada rodada");
        }
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            boolean[] uma = doJogo(t, N, 4242L, 7);
            boolean[] outra = doJogo(t, N, 4242L, 7);
            assertEquals(List.of(), diferencas(uma, outra),
                    LabirintoTracados.nome(t) + ": cavar duas vezes com a mesma "
                            + "semente deu dois labirintos");
        }
    }

    @Test
    @DisplayName("o sorteio tira as 375 combinações, e todas elas")
    void oSorteioTiraTudo() {
        Set<String> quadras = new HashSet<>();
        Set<Integer> tracados = new HashSet<>();
        Set<Integer> tamanhos = new HashSet<>();
        Set<Integer> estilos = new HashSet<>();
        Set<Integer> niveis = new HashSet<>();
        Random sorte = new Random(1234);
        for (int k = 0; k < 40000; k++) {
            LabirintoCatalog.Sorteio qual = LabirintoCatalog.sortear(
                    sorte.nextLong(), TAMANHOS, numerosDosTracados(),
                    ESTILOS.size(), VOLTAS_PCT);
            quadras.add(qual.tracado() + ":" + qual.celulas() + ":"
                    + qual.estilo() + ":" + qual.voltas());
            tracados.add(qual.tracado());
            tamanhos.add(qual.celulas());
            estilos.add(qual.estilo());
            // O NÚMERO de voltas depende do tamanho (é uma porcentagem das
            // casas), então dois níveis diferentes em tamanhos diferentes
            // podem dar o mesmo número. O que se confere é o NÍVEL.
            niveis.add(nivelDaVolta(qual.celulas(), qual.voltas()));
        }
        assertEquals(LabirintoTracados.quantos(), tracados.size(),
                "em quarenta mil sorteios só saíram " + tracados.size()
                        + " traçado(s): um traçado que nunca sai é código morto");
        assertEquals(TAMANHOS.size(), tamanhos.size());
        assertEquals(ESTILOS.size(), estilos.size());
        assertEquals(VOLTAS_PCT.size(), niveis.size(),
                "em quarenta mil sorteios só saíram " + niveis.size()
                        + " nível(eis) de volta dos " + VOLTAS_PCT.size()
                        + " que o yml lista");
        assertTrue(!niveis.contains(-1), "saiu um número de voltas que não "
                + "corresponde a nível nenhum da lista");
        assertEquals(375, quadras.size(), "o sorteio alcançou " + quadras.size()
                + " combinações das 375 que o yml promete");
    }

    @Test
    @DisplayName("o sorteio nunca tira tamanho par nem pequeno demais")
    void oSorteioSoTiraTamanhoBom() {
        Random sorte = new Random(99);
        for (int k = 0; k < 5000; k++) {
            // De propósito com uma lista estragada: o sorteio é a última linha
            // de defesa depois da conferência, e tem de corrigir em vez de
            // construir torto.
            LabirintoCatalog.Sorteio qual = LabirintoCatalog.sortear(
                    sorte.nextLong(), List.of(4, 8, 12, 13), List.of(0, 1),
                    3, List.of(7));
            assertEquals(1, qual.celulas() % 2, "o sorteio tirou "
                    + qual.celulas() + " células, que é par: a sala do meio não "
                    + "cairia sobre o (0,0) do mundo");
            assertTrue(qual.celulas() >= 7, "o sorteio tirou " + qual.celulas()
                    + " células: menos de sete não dá labirinto, dá corredor");
        }
    }

    @Test
    @DisplayName("rodadas seguidas quase nunca repetem o labirinto")
    void rodadasSeguidasNaoRepetem() {
        Random sorte = new Random(7);
        String anterior = null;
        int repetidas = 0;
        int rodadas = 2000;
        for (int k = 0; k < rodadas; k++) {
            LabirintoCatalog.Sorteio qual = LabirintoCatalog.sortear(
                    sorte.nextLong(), TAMANHOS, numerosDosTracados(),
                    ESTILOS.size(), VOLTAS_PCT);
            String agora = qual.tracado() + ":" + qual.celulas() + ":"
                    + qual.estilo() + ":" + qual.voltas();
            if (agora.equals(anterior)) {
                repetidas++;
            }
            anterior = agora;
        }
        // 1/375 das vezes por azar, e não mais. Com margem de três vezes.
        assertTrue(repetidas * 125 < rodadas, "em " + rodadas + " rodadas o "
                + "labirinto repetiu o da rodada anterior " + repetidas
                + " vez(es): o sorteio está viciado");
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    /**
     * O relógio acompanha o labirinto que saiu.
     *
     * É a consequência inevitável de sortear o tamanho, e vale registrar por
     * quê: a mesma arena agora tira rodadas de cinco esquinas e rodadas de
     * noventa. Um teto fixo que cabe nas duas ou dá uma eternidade de espera
     * na curta ou corta a longa no meio — e cortar a longa no meio é pior,
     * porque é a rodada boa.
     */
    @Test
    @DisplayName("toda rodada cabe no relógio dela, e o relógio cresce com ela")
    void oRelogioAcompanhaOLabirinto() {
        double menorRelogio = Double.MAX_VALUE;
        double maiorRelogio = 0;
        for (int celulas : TAMANHOS) {
            double somaRelogio = 0;
            int quantas = 0;
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    boolean[] ligado = doJogo(t, celulas, sem, 7);
                    int quantos = passos(celulas, ligado,
                            porta(celulas, ligado, sem));
                    int relogio = LabirintoCatalog.relogioDe(quantos, TEMPO_BASE,
                            POR_PASSO, TEMPO_LIMITE);
                    // Correndo a 5,6 blocos por segundo e vasculhando quatro
                    // vezes o caminho ótimo, que é o que custa não saber o
                    // caminho.
                    double precisa = quantos * (CELULA + PAREDE) / 5.6 * 4;
                    assertTrue(precisa <= relogio, LabirintoTracados.nome(t)
                            + " de " + celulas + " células: " + quantos
                            + " esquinas pedem " + Math.round(precisa)
                            + "s vasculhando e o relógio dá " + relogio
                            + "s — a rodada acaba no estouro e ninguém "
                            + "atravessa a porta");
                    somaRelogio += relogio;
                    quantas++;
                }
            }
            double medio = somaRelogio / quantas;
            if (celulas == TAMANHOS.get(0)) {
                menorRelogio = medio;
            }
            if (celulas == TAMANHOS.get(TAMANHOS.size() - 1)) {
                maiorRelogio = medio;
            }
        }
        assertTrue(maiorRelogio > menorRelogio * 1.8, "o relógio médio vai de "
                + Math.round(menorRelogio) + "s no menor labirinto a "
                + Math.round(maiorRelogio) + "s no maior: menos de 1,8x e o "
                + "relógio voltou a ser fixo na prática, que é o que esta "
                + "mudança veio desfazer");
        assertEquals(TEMPO_LIMITE, LabirintoCatalog.relogioDe(10000, TEMPO_BASE,
                        POR_PASSO, TEMPO_LIMITE),
                "nenhuma rodada pode durar para sempre: o teto é teto");
        assertTrue(LabirintoCatalog.relogioDe(0, TEMPO_BASE, POR_PASSO,
                TEMPO_LIMITE) >= 30, "relógio de zero segundo acabaria a rodada "
                + "antes da largada");
    }

    @Test
    @DisplayName("o giz dá para marcar o caminho e não para marcar tudo")
    void oGizDaParaOCaminho() {
        int menor = TAMANHOS.get(0);
        assertTrue(GIZ < menor * menor / 4, "com " + GIZ + " marcas num "
                + "labirinto de " + menor * menor + " células — o menor da "
                + "lista — o jogador marca o labirinto todo e deixa de ter de "
                + "escolher");
        assertTrue(GIZ >= menor / 2, "com " + GIZ + " marcas e um labirinto de "
                + menor + " células de lado o giz não marca nem metade das "
                + "escolhas do caminho mínimo");
        assertEquals(MAXIMO, LabirintoCatalog.GIZES.size(),
                "uma cor de giz por jogador, e são " + MAXIMO + " jogadores");
        assertEquals(MAXIMO, new HashSet<>(LabirintoCatalog.GIZES).size(),
                "duas cores iguais e dois jogadores leriam o mesmo rastro");
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /**
     * A área da obra é a do MAIOR labirinto, e não a do que está saindo.
     *
     * É o bug que esta reforma criou e fechou no mesmo dia. Com um tamanho só,
     * varrer a área do labirinto bastava. Com cinco, depois de uma rodada de
     * vinte e cinco células vem uma de nove — e varrer só a área da pequena
     * deixaria a grande de pé em volta dela, com porta, patamar e farol
     * apontando para uma saída que não existe mais. Não quebraria nada: só
     * deixaria o jogo de pé e errado, que é a pior categoria.
     */
    @Test
    @DisplayName("a área da obra cobre o maior labirinto e o patamar dele")
    void aObraVarreOMaior() {
        int menor = LabirintoCatalog.lado(TAMANHOS.get(0), CELULA, PAREDE);
        int maior = LabirintoCatalog.lado(TAMANHOS.get(TAMANHOS.size() - 1),
                CELULA, PAREDE);
        assertEquals(37, menor, "9 células de 3 com muro de 1 dão 37 blocos");
        assertEquals(101, maior, "25 células de 3 com muro de 1 dão 101 blocos");

        int meioMaximo = (maior - 1) / 2;
        int alcance = meioMaximo + SAIDA_FUNDO + 4;
        int fundoDoPatamar = meioMaximo + SAIDA_FUNDO + 1;
        assertTrue(fundoDoPatamar <= alcance, "o patamar do maior labirinto vai "
                + "até " + fundoDoPatamar + " e a obra só varre até " + alcance);
        assertTrue(alcance > (menor - 1) / 2 + SAIDA_FUNDO + 1,
                "a área da obra tem de passar da borda do MENOR labirinto, "
                        + "senão a rodada de 9 células não apaga a de 25");

        // As torres: um canto por tamanho, e todos distintos.
        Set<Integer> cantos = new HashSet<>();
        for (int quantas : TAMANHOS) {
            assertTrue(cantos.add(
                            (LabirintoCatalog.lado(quantas, CELULA, PAREDE) - 1) / 2),
                    "dois tamanhos com o mesmo canto: a limpeza das torres "
                            + "pularia um deles achando que é o da rodada");
        }
        assertEquals(TAMANHOS.size(), cantos.size());
    }

    @Test
    @DisplayName("as torres de canto não encostam no patamar da porta")
    void torresLongeDoPatamar() {
        int meiaLargura = (SAIDA_LARGURA - 1) / 2 + 1;
        for (int celulas : TAMANHOS) {
            int meio = (LabirintoCatalog.lado(celulas, CELULA, PAREDE) - 1) / 2;
            for (int t = 0; t < LabirintoTracados.quantos(); t++) {
                for (int k = 0; k < SEMENTES; k++) {
                    long sem = semente(t, celulas, k);
                    int[] saida = porta(celulas, doJogo(t, celulas, sem, 7), sem);
                    int indice = saida[2] == LabirintoCatalog.NORTE
                            || saida[2] == LabirintoCatalog.SUL ? saida[0] : saida[1];
                    int centro = LabirintoCatalog.meioDaCelula(indice, CELULA,
                            PAREDE) - meio;
                    assertTrue(Math.abs(centro) + meiaLargura < meio,
                            LabirintoTracados.nome(t) + " de " + celulas
                                    + " células: o patamar vai de "
                                    + (centro - meiaLargura) + " a "
                                    + (centro + meiaLargura) + " e a torre fica "
                                    + "em " + meio);
                }
            }
        }
    }

    @Test
    @DisplayName("os corredores têm a largura da planta em todo tamanho")
    void osCorredoresTemLargura() {
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            long sem = semente(LabirintoTracados.FUNDO, celulas, 7);
            boolean[] ligado = doJogo(LabirintoTracados.FUNDO, celulas, sem, 7);
            int[] saida = porta(celulas, ligado, sem);
            int[] mapa = LabirintoCatalog.mapaDeBlocos(celulas, CELULA, PAREDE,
                    ligado, SALA, saida);
            int[] vao = LabirintoCatalog.vaoDaPorta(celulas, CELULA, PAREDE, saida);
            for (int v = 0; v < lado; v++) {
                for (int u = 0; u < lado; u++) {
                    if (mapa[v * lado + u] != LabirintoCatalog.PISO) {
                        continue;
                    }
                    boolean noVao = u >= vao[0] && u < vao[0] + vao[1]
                            && v >= vao[2] && v < vao[2] + vao[3];
                    if (noVao) {
                        continue;
                    }
                    int i = LabirintoCatalog.celulaDe(u, CELULA, PAREDE, celulas);
                    int j = LabirintoCatalog.celulaDe(v, CELULA, PAREDE, celulas);
                    assertTrue(i >= 0 || j >= 0, celulas + " células: a casa de "
                            + "piso (" + u + "," + v + ") não pertence a célula "
                            + "nenhuma em nenhum eixo: é um buraco no cruzamento "
                            + "de dois muros");
                }
            }
        }
    }

    @Test
    @DisplayName("a sala do meio é uma sala, e fica sobre o (0,0) do mundo")
    void aSalaFicaNoMeio() {
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            int meio = (lado - 1) / 2;
            long sem = semente(LabirintoTracados.JUSTO, celulas, 3);
            boolean[] ligado = doJogo(LabirintoTracados.JUSTO, celulas, sem, 7);
            int[] saida = porta(celulas, ligado, sem);
            int[] mapa = LabirintoCatalog.mapaDeBlocos(celulas, CELULA, PAREDE,
                    ligado, SALA, saida);

            int centro = LabirintoCatalog.meioDaCelula(celulas / 2, CELULA, PAREDE);
            assertEquals(meio, centro, celulas + " células: o meio da célula do "
                    + "meio tem de cair exatamente no meio do labirinto, que é o "
                    + "(0,0) do mundo");

            int alcance = SALA * (CELULA + PAREDE) + (CELULA - 1) / 2;
            int mouroes = 0;
            for (int u = centro - alcance; u <= centro + alcance; u++) {
                for (int v = centro - alcance; v <= centro + alcance; v++) {
                    // O mourão é o quadradinho que sobra no cruzamento de duas
                    // faixas de muro: abrir passagem abre a faixa entre duas
                    // células e nunca o cruzamento. São as colunas do salão.
                    boolean mourao = emFaixaDeMuro(u, celulas)
                            && emFaixaDeMuro(v, celulas);
                    if (mourao) {
                        mouroes++;
                    }
                    assertEquals(mourao ? LabirintoCatalog.MURO
                                    : LabirintoCatalog.PISO,
                            mapa[v * lado + u], celulas + " células: a sala do "
                                    + "meio está errada em (" + u + "," + v + ")");
                }
            }
            int esperados = (2 * SALA) * (2 * SALA) * PAREDE * PAREDE;
            assertEquals(esperados, mouroes, celulas + " células: o salão de raio "
                    + SALA + " tem de sair com " + esperados + " coluna(s), uma "
                    + "por cruzamento de faixas de muro");

            int[] dist = LabirintoCatalog.distancias(celulas, ligado, SALA);
            for (int j = celulas / 2 - SALA; j <= celulas / 2 + SALA; j++) {
                for (int i = celulas / 2 - SALA; i <= celulas / 2 + SALA; i++) {
                    assertEquals(0, dist[LabirintoCatalog.indice(celulas, i, j)],
                            "sair por qualquer casa da sala custa o mesmo");
                }
            }
        }
    }

    @Test
    @DisplayName("as largadas cabem na sala e não se repetem")
    void asLargadasCabemNaSala() {
        int anel = SALA * (CELULA + PAREDE);
        int alcance = SALA * (CELULA + PAREDE) + (CELULA - 1) / 2;
        List<int[]> pontos = new ArrayList<>();
        for (int k = 0; k < MAXIMO; k++) {
            double angulo = 2 * Math.PI * k / MAXIMO;
            pontos.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        Set<String> unicos = new HashSet<>();
        for (int[] onde : pontos) {
            assertTrue(Math.abs(onde[0]) < alcance && Math.abs(onde[1]) < alcance,
                    "a largada em (" + onde[0] + "," + onde[1] + ") encosta no "
                            + "muro da sala, de alcance " + alcance);
            assertTrue(unicos.add(onde[0] + ":" + onde[1]),
                    "duas largadas no mesmo bloco: (" + onde[0] + ","
                            + onde[1] + ")");
            assertTrue(!(emFaixaDeMuro(onde[0] + MEIO, N)
                            && emFaixaDeMuro(onde[1] + MEIO, N)),
                    "a largada em (" + onde[0] + "," + onde[1] + ") cai dentro "
                            + "de uma coluna do salão");
        }
        assertEquals(MAXIMO, unicos.size());
        // A sala não muda de tamanho com o labirinto: é a mesma largada em
        // qualquer tamanho sorteado, e é por isso que este teste basta num.
        for (int celulas : TAMANHOS) {
            assertEquals(anel, SALA * (CELULA + PAREDE), celulas + " células: o "
                    + "anel de largada depende da sala e do passo, nunca do "
                    + "tamanho do labirinto");
        }
    }

    @Test
    @DisplayName("o vão da porta sai da célula de saída, em qualquer lado e tamanho")
    void oVaoSaiDaCelula() {
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            for (int qual = 0; qual < 4; qual++) {
                int k = celulas / 2;
                int[] saida = switch (qual) {
                    case LabirintoCatalog.NORTE -> new int[]{k, 0, qual};
                    case LabirintoCatalog.SUL -> new int[]{k, celulas - 1, qual};
                    case LabirintoCatalog.OESTE -> new int[]{0, k, qual};
                    default -> new int[]{celulas - 1, k, qual};
                };
                int[] vao = LabirintoCatalog.vaoDaPorta(celulas, CELULA, PAREDE,
                        saida);
                assertEquals(CELULA * PAREDE, vao[1] * vao[3], celulas
                        + " células: o vão do lado "
                        + LabirintoBuilder.nomeDoLado(qual) + " tem de ter a "
                        + "largura do corredor pela espessura do muro");
                boolean encostaNaBorda = vao[0] == 0 || vao[2] == 0
                        || vao[0] + vao[1] == lado || vao[2] + vao[3] == lado;
                assertTrue(encostaNaBorda, celulas + " células: o vão do lado "
                        + LabirintoBuilder.nomeDoLado(qual) + " não encosta na "
                        + "borda: não é porta, é buraco no meio do muro");
            }
        }
    }

    @Test
    @DisplayName("a célula mais próxima aceita o vão entre duas células")
    void celulaMaisProximaAceitaOVao() {
        int passo = CELULA + PAREDE;
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            for (int u = 0; u < lado; u++) {
                int exato = LabirintoCatalog.celulaDe(u, CELULA, PAREDE, celulas);
                int perto = Math.max(0, Math.min(celulas - 1, (u - PAREDE) / passo));
                if (exato >= 0) {
                    assertEquals(exato, perto, celulas + " células: no bloco " + u
                            + " a conta exata e a arredondada têm de concordar");
                }
                assertTrue(perto >= 0 && perto < celulas,
                        celulas + " células: o bloco " + u + " caiu fora da grade");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Miudezas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("os rumos e as tangentes são os quatro cardeais")
    void rumosSaoCardeais() {
        assertEquals(4, LabirintoCatalog.RUMOS.length);
        Set<String> vistos = new HashSet<>();
        for (int[] rumo : LabirintoCatalog.RUMOS) {
            assertEquals(1, Math.abs(rumo[0]) + Math.abs(rumo[1]),
                    "rumo diagonal não existe numa grade de labirinto");
            assertTrue(vistos.add(rumo[0] + ":" + rumo[1]), "rumo repetido");
            int[] lado = LabirintoBuilder.tangente(rumo);
            assertEquals(0, rumo[0] * lado[0] + rumo[1] * lado[1],
                    "a tangente tem de ser perpendicular ao rumo");
            assertEquals(1, Math.abs(lado[0]) + Math.abs(lado[1]));
        }
    }

    @Test
    @DisplayName("a passagem é a mesma vista das duas células, em todo traçado")
    void passagemEhSimetrica() {
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            boolean[] ligado = doJogo(t, N, semente(t, N, 11), 7);
            for (int j = 0; j < N; j++) {
                for (int i = 0; i < N; i++) {
                    for (int[] rumo : LabirintoCatalog.RUMOS) {
                        int ni = i + rumo[0];
                        int nj = j + rumo[1];
                        if (!LabirintoCatalog.dentro(N, ni, nj)) {
                            continue;
                        }
                        assertEquals(
                                LabirintoCatalog.passa(ligado, N, i, j, ni, nj),
                                LabirintoCatalog.passa(ligado, N, ni, nj, i, j),
                                LabirintoTracados.nome(t) + ": a parede entre ("
                                        + i + "," + j + ") e (" + ni + "," + nj
                                        + ") tem dois donos que discordam");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("andar a pé pelo mapa dá o mesmo que andar pela grade")
    void mapaEGradeConcordam() {
        for (int celulas : TAMANHOS) {
            int lado = LabirintoCatalog.lado(celulas, CELULA, PAREDE);
            int meio = (lado - 1) / 2;
            long sem = semente(LabirintoTracados.SALOES, celulas, 23);
            boolean[] ligado = doJogo(LabirintoTracados.SALOES, celulas, sem, 7);
            int[] saida = porta(celulas, ligado, sem);
            int[] mapa = LabirintoCatalog.mapaDeBlocos(celulas, CELULA, PAREDE,
                    ligado, SALA, saida);

            // Partindo do meio da sala, a pé pelo mapa, quantas células dá para
            // alcançar? Tem de ser todas — a grade diz que são todas.
            Set<Integer> alcancadas = new HashSet<>();
            boolean[] visto = new boolean[lado * lado];
            Deque<int[]> fila = new ArrayDeque<>();
            visto[meio * lado + meio] = true;
            fila.add(new int[]{meio, meio});
            while (!fila.isEmpty()) {
                int[] onde = fila.poll();
                int i = LabirintoCatalog.celulaDe(onde[0], CELULA, PAREDE, celulas);
                int j = LabirintoCatalog.celulaDe(onde[1], CELULA, PAREDE, celulas);
                if (i >= 0 && j >= 0) {
                    alcancadas.add(LabirintoCatalog.indice(celulas, i, j));
                }
                for (int[] rumo : LabirintoCatalog.RUMOS) {
                    int u = onde[0] + rumo[0];
                    int v = onde[1] + rumo[1];
                    if (u < 0 || v < 0 || u >= lado || v >= lado
                            || visto[v * lado + u]
                            || mapa[v * lado + u] != LabirintoCatalog.PISO) {
                        continue;
                    }
                    visto[v * lado + u] = true;
                    fila.add(new int[]{u, v});
                }
            }
            assertEquals(celulas * celulas, alcancadas.size(), celulas
                    + " células: a pé pelo mapa chega-se a " + alcancadas.size()
                    + " células das " + celulas * celulas + " que a grade diz "
                    + "existirem");
        }
    }

    @Test
    @DisplayName("todo traçado tem nome e tem carácter para anunciar")
    void todoTracadoTemNomeECarater() {
        assertEquals(LabirintoTracados.quantos(), LabirintoTracados.NOMES.size());
        assertEquals(LabirintoTracados.quantos(), LabirintoTracados.CARATER.size(),
                "o nome do traçado é anunciado na largada com o carácter dele: "
                        + "traçado sem carácter escrito é um nome que não ensina "
                        + "nada a quem ouve");
        Set<String> nomes = new HashSet<>();
        Set<String> carateres = new HashSet<>();
        for (int t = 0; t < LabirintoTracados.quantos(); t++) {
            assertTrue(nomes.add(LabirintoTracados.nome(t)), "nome repetido");
            assertTrue(carateres.add(LabirintoTracados.carater(t)),
                    "dois traçados com o mesmo carácter: o anúncio da largada "
                            + "diria a mesma coisa de dois jogos diferentes");
            assertTrue(LabirintoTracados.carater(t).length() > 20,
                    "o carácter do " + LabirintoTracados.nome(t) + " tem "
                            + LabirintoTracados.carater(t).length()
                            + " caracteres: curto demais para ensinar a manha");
        }
    }

    // ------------------------------------------------------------------
    //  Utilidades do teste
    // ------------------------------------------------------------------

    /** Qual nível de 'voltas-por-cento' deu este número de voltas, ou -1. */
    private static int nivelDaVolta(int celulas, int voltas) {
        for (int k = 0; k < VOLTAS_PCT.size(); k++) {
            if (LabirintoCatalog.voltasDe(celulas, VOLTAS_PCT.get(k)) == voltas) {
                return k;
            }
        }
        return -1;
    }

    private static List<Integer> numerosDosTracados() {
        List<Integer> saida = new ArrayList<>();
        for (String nome : TRACADOS) {
            saida.add(LabirintoTracados.porNome(nome));
        }
        return saida;
    }

    /** As três chaves que distinguem um estilo, lidas do yml de produção. */
    private static List<List<Material>> coresDoYml() {
        List<List<Material>> cores = new ArrayList<>();
        for (String qual : ESTILOS) {
            cores.add(List.of(doYml(qual, "piso", Material.STONE_BRICKS),
                    doYml(qual, "muro", Material.PRISMARINE_BRICKS),
                    doYml(qual, "muro-topo", Material.DARK_PRISMARINE)));
        }
        return cores;
    }

    private static Material doYml(String estilo, String chave, Material padrao) {
        String nome = arena.getString("estilos." + estilo + "." + chave);
        if (nome == null) {
            nome = arena.getString("paleta." + chave);
        }
        Material achado = nome == null ? null : Material.matchMaterial(nome);
        assertNotNull(achado, "estilos." + estilo + "." + chave + " = " + nome
                + " não é material nenhum desta versão do Minecraft");
        return achado == null ? padrao : achado;
    }

    /** O bloco cai numa faixa de muro, e não dentro de uma célula? */
    private static boolean emFaixaDeMuro(int u, int celulas) {
        return LabirintoCatalog.celulaDe(u, CELULA, PAREDE, celulas) < 0;
    }

    private static List<Integer> diferencas(boolean[] a, boolean[] b) {
        List<Integer> saida = new ArrayList<>();
        for (int k = 0; k < a.length; k++) {
            if (a[k] != b[k]) {
                saida.add(k);
            }
        }
        return saida;
    }
}
