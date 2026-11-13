package codes.biga.bigacore;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do BED WARS, conferidas sem servidor.
 *
 * <h2>Os três testes que valem mais aqui</h2>
 *
 * Cada um guarda uma das três respostas deste jogo às três reclamações do
 * gênero, e cada resposta é um número que, se mudar sozinho, devolve o
 * defeito sem quebrar nada:
 *
 * <ul>
 *   <li>{@link #aCamaQueCaiNumGolpeEhRecusada} — a cama tem de custar tempo.
 *       Sem isso, "rush demais e não dá tempo de proteger a cama" volta
 *       inteiro, e nada no log avisa.</li>
 *   <li>{@link #umaVidaSoDepoisDaCamaEhRecusada} — é a reclamação "sua cama
 *       foi destruída, você agora é espectador" escrita em número.</li>
 *   <li>{@link #umaViagemAoMeioNaoFechaACama} — as duas pontas do tear. Pouca
 *       lã e ninguém sai de casa, que é o defeito do gerador caseiro de
 *       volta; lã demais e a primeira viagem fecha a cama.</li>
 * </ul>
 *
 * <p>E {@link #aCarenciaEhOTempoDaViagem}, que é o de que mais me orgulho,
 * porque ele prova que o número NÃO foi escolhido: a carência é medida na
 * geometria da arena, e o teste refaz a medida.
 */
class BedWarsTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int TIMES = 4;
    private static final int ANEL = 40;
    private static final int BASE_RAIO = 7;
    private static final int RECUO = 4;
    private static final int MEIO_RAIO = 10;
    private static final int LARGURA = 3;

    private static final int VIDA_CHEIA = 100;
    private static final int PERIODO = 13;
    private static final int DANO = 20;
    private static final int GOLPES = 8;
    private static final int PERIODO_DA_CAMA = 16;
    private static final int CURA = 8;
    private static final int VIDAS = 3;
    private static final int RESPAWN = 5;
    private static final int LA_DO_TEAR = 2;
    private static final int TEAR_SEGUNDOS = 2;
    private static final int LA_DA_DEFESA = 24;
    private static final int GRACA = 20;
    private static final int TEMPO_LIMITE = 480;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = BedWarsTest.class
                .getResourceAsStream("/bed-wars.yml")) {
            assertNotNull(in, "bed-wars.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals("bed_wars", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(TIMES, arena.getInt("arena.times"));
        assertEquals(ANEL, arena.getInt("arena.base-anel"));
        assertEquals(BASE_RAIO, arena.getInt("arena.base-raio"));
        assertEquals(RECUO, arena.getInt("arena.cama-recuo"));
        assertEquals(MEIO_RAIO, arena.getInt("arena.meio-raio"));
        assertEquals(LARGURA, arena.getInt("arena.passarela-largura"));
        assertEquals(VIDA_CHEIA, arena.getInt("regras.vida-cheia"));
        assertEquals(DANO, arena.getInt("regras.dano-do-golpe"));
        assertEquals(GOLPES, arena.getInt("regras.golpes-da-cama"));
        assertEquals(PERIODO_DA_CAMA, arena.getInt("regras.periodo-da-cama"));
        assertEquals(CURA, arena.getInt("regras.cura-da-cama"));
        assertEquals(VIDAS, arena.getInt("regras.vidas-do-time"));
        assertEquals(RESPAWN, arena.getInt("regras.respawn-segundos"));
        assertEquals(LA_DO_TEAR, arena.getInt("regras.la-do-tear"));
        assertEquals(TEAR_SEGUNDOS, arena.getInt("regras.tear-segundos"));
        assertEquals(LA_DA_DEFESA, arena.getInt("regras.la-da-defesa"));
        assertEquals(GRACA, arena.getInt("regras.graca-segundos"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas seis conferências")
    void ymlPassaNasConferencias() {
        assertNull(BedWarsBuilder.conferirCama(GOLPES, PERIODO_DA_CAMA, CURA,
                TEMPO_LIMITE));
        assertNull(BedWarsBuilder.conferirGraca(GRACA, ANEL, RECUO));
        assertNull(BedWarsBuilder.conferirTear(LA_DO_TEAR, TEAR_SEGUNDOS,
                ANEL, MEIO_RAIO, LA_DA_DEFESA));
        assertNull(BedWarsBuilder.conferirVidas(VIDAS, RESPAWN));
        assertNull(BedWarsBuilder.conferirLuta(PERIODO, VIDA_CHEIA, DANO));
        assertNull(BedWarsBuilder.conferirArena(TIMES, ANEL, BASE_RAIO,
                MEIO_RAIO, RECUO, LARGURA));
    }

    @Test
    @DisplayName("cada time tem a sua cor, e nenhuma se repete")
    void cadaTimeTemASuaCor() {
        List<Map<?, ?>> times = arena.getMapList("times");
        assertEquals(TIMES, times.size(), "o yml pede " + TIMES
                + " times e tem de descrever todos");
        Set<String> las = new HashSet<>();
        Set<String> camas = new HashSet<>();
        Set<String> cores = new HashSet<>();
        for (Map<?, ?> time : times) {
            assertTrue(las.add(String.valueOf(time.get("la"))),
                    "duas lãs iguais: num jogo em que o bloco diz quem o pôs, "
                            + "isso é a mesma coisa que não ter time");
            assertTrue(camas.add(String.valueOf(time.get("cama"))),
                    "duas camas iguais");
            assertTrue(cores.add(String.valueOf(time.get("cor"))),
                    "duas cores de texto iguais");
            assertTrue(String.valueOf(time.get("la")).endsWith("_WOOL"),
                    "a lã do time tem de ser lã");
            assertTrue(String.valueOf(time.get("cama")).endsWith("_BED"),
                    "a cama do time tem de ser cama");
        }
    }

    // ------------------------------------------------------------------
    //  A cama — o primeiro conserto
    // ------------------------------------------------------------------

    /**
     * A reclamação do rush, posta em número.
     *
     * Oito golpes com dezesseis tiques entre um e outro são seis segundos e
     * meio: tempo de alguém atravessar a base ao receber o aviso. Um golpe só
     * é a referência de volta — a cama cai em silêncio, e o aviso chega junto
     * com o fim.
     */
    @Test
    @DisplayName("a cama que cai num golpe é recusada")
    void aCamaQueCaiNumGolpeEhRecusada() {
        assertNotNull(BedWarsBuilder.conferirCama(1, PERIODO_DA_CAMA, CURA,
                        TEMPO_LIMITE),
                "um golpe só é a cama caindo em silêncio");
        assertNotNull(BedWarsBuilder.conferirCama(3, PERIODO_DA_CAMA, CURA,
                        TEMPO_LIMITE),
                "três golpes e cada um vale um terço da cama");

        double quebra = BedWarsCatalog.tempoDeQuebrarACama(GOLPES,
                PERIODO_DA_CAMA);
        assertTrue(quebra >= 5.0 && quebra <= 15.0,
                "quebrar leva " + quebra + "s");
        assertNotNull(BedWarsBuilder.conferirCama(GOLPES, 4, CURA,
                        TEMPO_LIMITE),
                "quatro tiques entre golpes derruba a cama em 1,6s: o rush "
                        + "volta a ser instantâneo");
        assertNotNull(BedWarsBuilder.conferirCama(GOLPES, 60, CURA,
                        TEMPO_LIMITE),
                "vinte e quatro segundos em cima da cama inimiga não é "
                        + "jogada, é suicídio");
    }

    /**
     * A cura é a metade que faltava do conserto do rush.
     *
     * Avisar o defensor não serve de nada se, quando ele chegar e expulsar o
     * invasor, a cama continuar pela metade para sempre. Mas a cura tem de
     * ser MUITO mais lenta que a quebra: se curasse quase na velocidade em
     * que é batida, defender seria automático e atacar seria perda de tempo.
     */
    @Test
    @DisplayName("a cama que se cura quase tão rápido quanto apanha é recusada")
    void aCuraRapidaDemaisEhRecusada() {
        double quebra = BedWarsCatalog.tempoDeQuebrarACama(GOLPES,
                PERIODO_DA_CAMA);
        int curaTotal = BedWarsCatalog.tempoDeCurarACama(GOLPES, CURA);
        assertTrue(curaTotal >= quebra * 3, "a cura leva " + curaTotal
                + "s e a quebra " + quebra + "s");
        assertNotNull(BedWarsBuilder.conferirCama(GOLPES, PERIODO_DA_CAMA, 1,
                        TEMPO_LIMITE),
                "um segundo por golpe curado faz defender ser automático");
        assertNotNull(BedWarsBuilder.conferirCama(GOLPES, PERIODO_DA_CAMA, 40,
                        TEMPO_LIMITE),
                "trezentos e vinte segundos de cura nunca terminam dentro de "
                        + "uma partida de " + TEMPO_LIMITE + "s");
    }

    // ------------------------------------------------------------------
    //  A carência — medida, não escolhida
    // ------------------------------------------------------------------

    /**
     * A carência é o tempo da viagem, e este teste refaz a medida.
     *
     * É o teste de que mais me orgulho neste jogo, porque ele prova que o
     * número não foi escolhido: oitenta e oito blocos de uma cama até a outra,
     * vinte e um segundos andando, vinte de carência. A proposta do fórum —
     * um minuto de proteção — seria recusada por este mesmo teste, por ser
     * quase três vezes a travessia.
     */
    @Test
    @DisplayName("a carência é o tempo da viagem, e um minuto seria recusado")
    void aCarenciaEhOTempoDaViagem() {
        int travessia = BedWarsCatalog.travessiaEntreCamas(ANEL, RECUO);
        assertEquals(2 * (ANEL + RECUO), travessia);
        int andando = BedWarsCatalog.segundosAndando(travessia);
        assertTrue(Math.abs(GRACA - andando) <= 3, "a carência é " + GRACA
                + "s e a travessia leva " + andando + "s: o número tem de ser "
                + "a medida, não uma escolha");

        assertNotNull(BedWarsBuilder.conferirGraca(60, ANEL, RECUO),
                "o minuto de proteção que o fórum propõe é quase três vezes "
                        + "a travessia: o invasor chegaria e teria de esperar");
        assertNotNull(BedWarsBuilder.conferirGraca(5, ANEL, RECUO),
                "cinco segundos acabam antes de o primeiro invasor chegar e "
                        + "não mudam nada");
        assertNull(BedWarsBuilder.conferirGraca(andando, ANEL, RECUO),
                "o tempo exato da travessia tem de passar");
    }

    // ------------------------------------------------------------------
    //  As vidas — o segundo conserto
    // ------------------------------------------------------------------

    /**
     * "Sua cama foi destruída, você agora é espectador", em número.
     *
     * Com uma vida, a morte seguinte tira o jogador da partida, que é
     * exatamente a frase. Com três, o time tem minutos de partida pela frente
     * e um motivo para atacar.
     */
    @Test
    @DisplayName("uma vida só depois da cama é recusada")
    void umaVidaSoDepoisDaCamaEhRecusada() {
        assertNotNull(BedWarsBuilder.conferirVidas(1, RESPAWN),
                "uma vida é a reclamação do espectador de volta");
        assertNotNull(BedWarsBuilder.conferirVidas(0, RESPAWN),
                "zero vidas é a referência sem disfarce");
        assertNotNull(BedWarsBuilder.conferirVidas(10, RESPAWN),
                "dez vidas e a cama deixa de ser o que se protege");
        assertNull(BedWarsBuilder.conferirVidas(VIDAS, RESPAWN));
    }

    @Test
    @DisplayName("a volta não é instantânea nem é uma espera")
    void aVoltaTemTempoHonesto() {
        assertNotNull(BedWarsBuilder.conferirVidas(VIDAS, 1),
                "voltar em 1s faz morrer não custar nada");
        assertNotNull(BedWarsBuilder.conferirVidas(VIDAS, 30),
                "voltar em 30s é o espectador por outro caminho");
        assertTrue(VIDAS * RESPAWN < TEMPO_LIMITE / 4, "as " + VIDAS
                + " vidas somam " + (VIDAS * RESPAWN) + "s de espera numa "
                + "partida de " + TEMPO_LIMITE + "s: o time sem cama tem de "
                + "ter partida pela frente, não fila");
    }

    // ------------------------------------------------------------------
    //  O tear — o terceiro conserto
    // ------------------------------------------------------------------

    /**
     * As duas pontas do tear, que são o conserto do gerador caseiro.
     *
     * Pouca lã e ninguém sai de casa — que é o defeito de volta, só que com
     * outra desculpa. Lã demais e a primeira viagem fecha a cama, e a partida
     * acaba no primeiro minuto com todas as camas de pé e ninguém jogando.
     */
    @Test
    @DisplayName("uma viagem ao meio vale a ida e não fecha a cama")
    void umaViagemAoMeioNaoFechaACama() {
        int viagem = BedWarsCatalog.laDeUmaViagem(ANEL, MEIO_RAIO, LA_DO_TEAR,
                TEAR_SEGUNDOS);
        assertTrue(viagem >= 4, "uma viagem rende " + viagem
                + " de lã: não paga nem o risco de ir");
        assertTrue(viagem < LA_DA_DEFESA, "uma viagem rende " + viagem
                + " e a defesa pede " + LA_DA_DEFESA + ": uma viagem só não "
                + "pode fechar a cama");

        assertNotNull(BedWarsBuilder.conferirTear(1, 30, ANEL, MEIO_RAIO,
                        LA_DA_DEFESA),
                "um bloco a cada trinta segundos não paga a ida");
        assertNotNull(BedWarsBuilder.conferirTear(8, 1, ANEL, MEIO_RAIO,
                        LA_DA_DEFESA),
                "oito blocos por segundo fecham a cama na primeira viagem");
    }

    @Test
    @DisplayName("o tear rende por tempo, e o tempo parado é o que conta")
    void oTearRendePorTempo() {
        assertEquals(0, BedWarsCatalog.laColhida(0, LA_DO_TEAR,
                TEAR_SEGUNDOS));
        assertEquals(LA_DO_TEAR, BedWarsCatalog.laColhida(TEAR_SEGUNDOS,
                LA_DO_TEAR, TEAR_SEGUNDOS));
        assertEquals(LA_DO_TEAR * 5, BedWarsCatalog.laColhida(
                TEAR_SEGUNDOS * 5, LA_DO_TEAR, TEAR_SEGUNDOS));
        assertEquals(LA_DO_TEAR, BedWarsCatalog.laColhida(
                TEAR_SEGUNDOS * 2 - 1, LA_DO_TEAR, TEAR_SEGUNDOS),
                "o tear não paga a prestação pela metade");
    }

    // ------------------------------------------------------------------
    //  A arena
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as bases não se encostam nem dá para bater na cama do vizinho de casa")
    void asBasesNaoSeEncostam() {
        double menor = Double.MAX_VALUE;
        for (int k = 0; k < TIMES; k++) {
            int[] minha = BedWarsCatalog.pontoDaCama(k, TIMES, ANEL, RECUO);
            int[] dele = BedWarsCatalog.pontoDaCama((k + 1) % TIMES, TIMES,
                    ANEL, RECUO);
            menor = Math.min(menor, Math.hypot(minha[0] - dele[0],
                    minha[1] - dele[1]));
        }
        assertTrue(menor > 2.0 * BASE_RAIO, "entre duas camas vizinhas há "
                + menor + " blocos e a base tem raio " + BASE_RAIO + ": daria "
                + "para bater na cama do vizinho de dentro da própria base");
        assertNotNull(BedWarsBuilder.conferirArena(TIMES, 16, BASE_RAIO,
                        MEIO_RAIO, RECUO, LARGURA),
                "um anel de 16 põe as bases encostadas");
    }

    @Test
    @DisplayName("a cama cabe na base e a cabeceira é um bloco adiante do pé")
    void aCamaCabeNaBase() {
        for (int k = 0; k < TIMES; k++) {
            int[] pe = BedWarsCatalog.pontoDaCama(k, TIMES, ANEL, RECUO);
            int[] cabeca = BedWarsCatalog.cabecaDaCama(k, TIMES, ANEL, RECUO);
            int passo = Math.abs(pe[0] - cabeca[0])
                    + Math.abs(pe[1] - cabeca[1]);
            assertEquals(1, passo, "a cabeceira do time " + k + " não encosta "
                    + "no pé: uma cama de dois blocos separados não é cama");

            int[] centro = BedWarsCatalog.centroDaBase(k, TIMES, ANEL);
            assertNotNull(centro);
            double longe = Math.hypot(cabeca[0] - centro[0],
                    cabeca[1] - centro[1]);
            assertTrue(longe <= RECUO + 1.5, "a cabeceira do time " + k
                    + " está a " + longe + " do centro da base, e o recuo é "
                    + RECUO);
        }
        assertNotNull(BedWarsBuilder.conferirArena(TIMES, ANEL, 4, MEIO_RAIO,
                        6, LARGURA),
                "recuo 6 numa base de raio 4 põe a cama no vazio");
    }

    @Test
    @DisplayName("a cama fica para FORA, de costas para o meio")
    void aCamaFicaDeCostasParaOMeio() {
        for (int k = 0; k < TIMES; k++) {
            int[] centro = BedWarsCatalog.centroDaBase(k, TIMES, ANEL);
            int[] cama = BedWarsCatalog.pontoDaCama(k, TIMES, ANEL, RECUO);
            assertNotNull(centro);
            assertTrue(Math.hypot(cama[0], cama[1])
                            > Math.hypot(centro[0], centro[1]),
                    "a cama do time " + k + " ficou mais perto do meio que a "
                            + "base: na boca da passarela ela cairia antes de "
                            + "a defesa existir");
            BlockFace rumo = BedWarsCatalog.rumoDaCama(k, TIMES);
            assertEquals(rumo.getOppositeFace(),
                    BedWarsCatalog.frenteDaPlacaDaBase(k, TIMES),
                    "a placa da base tem de olhar para o meio, de onde o "
                            + "inimigo vem");
        }
    }

    @Test
    @DisplayName("a primeira base fica no norte e o anel segue o relógio")
    void aPrimeiraBaseFicaNoNorte() {
        int[] primeira = BedWarsCatalog.centroDaBase(0, TIMES, ANEL);
        assertNotNull(primeira);
        assertEquals(0, primeira[0]);
        assertEquals(-ANEL, primeira[1], "a base 1 tem de ficar ao norte, "
                + "para 'time 1' querer dizer o mesmo lugar em toda partida");
        assertEquals(BlockFace.NORTH, BedWarsCatalog.rumoDaCama(0, TIMES),
                "a cama do time do norte é posta para o norte");
        int[] leste = BedWarsCatalog.centroDaBase(1, TIMES, ANEL);
        assertNotNull(leste);
        assertTrue(leste[0] > 0, "o segundo time cai no leste");
    }

    @Test
    @DisplayName("a passarela é estreita e comprida, e é um segmento e não um leque")
    void aPassarelaEhUmAperto() {
        assertNotNull(BedWarsBuilder.conferirArena(TIMES, ANEL, BASE_RAIO,
                        MEIO_RAIO, RECUO, 2),
                "largura par nasce torta em relação ao raio da base");
        assertNotNull(BedWarsBuilder.conferirArena(TIMES, ANEL, BASE_RAIO,
                        MEIO_RAIO, RECUO, 21),
                "largura 21 num meio de raio 10 não é aperto nenhum");

        // Perto da base, a faixa tem de continuar com a largura da passarela
        // e não abrir num leque: é por isso que a conta é de distância até o
        // segmento e não de ângulo.
        int longe = ANEL - BASE_RAIO - 1;
        assertTrue(BedWarsCatalog.naPassarela(0, -longe, TIMES, ANEL,
                BASE_RAIO, MEIO_RAIO, LARGURA), "o eixo da passarela do norte");
        assertTrue(BedWarsCatalog.naPassarela(1, -longe, TIMES, ANEL,
                BASE_RAIO, MEIO_RAIO, LARGURA), "um bloco ao lado do eixo");
        assertTrue(!BedWarsCatalog.naPassarela(5, -longe, TIMES, ANEL,
                        BASE_RAIO, MEIO_RAIO, LARGURA),
                "cinco blocos ao lado, perto da base, já tinha de estar fora: "
                        + "por ângulo estaria dentro, e metade da arena "
                        + "contaria como passarela");
    }

    @Test
    @DisplayName("o lado da invasão é cardinal e diz de onde o invasor veio")
    void oLadoDaInvasaoEhCardinal() {
        int[] cama = {0, -44};
        assertEquals("leste", BedWarsCatalog.ladoDaInvasao(6, -44, cama));
        assertEquals("oeste", BedWarsCatalog.ladoDaInvasao(-6, -44, cama));
        assertEquals("sul", BedWarsCatalog.ladoDaInvasao(0, -38, cama));
        assertEquals("norte", BedWarsCatalog.ladoDaInvasao(0, -50, cama));
        assertEquals("cima dela", BedWarsCatalog.ladoDaInvasao(0, -44, cama));
    }

    // ------------------------------------------------------------------
    //  Os times
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nenhum time entra vazio, e a diferença nunca passa de um")
    void osTimesSaemParelhos() {
        for (int quantos = 1; quantos <= 16; quantos++) {
            List<Integer> sorteio = BedWarsCatalog.distribuirTimes(quantos,
                    TIMES);
            assertEquals(quantos, sorteio.size());
            int usados = BedWarsCatalog.timesUsados(quantos, TIMES);
            assertEquals(Math.min(quantos, TIMES), usados,
                    "com " + quantos + " jogadores o certo é usar " + usados
                            + " times");

            int[] conta = new int[TIMES];
            for (int time : sorteio) {
                assertTrue(time >= 0 && time < usados,
                        "o time " + time + " não está em jogo");
                conta[time]++;
            }
            int menor = Integer.MAX_VALUE;
            int maior = 0;
            for (int k = 0; k < usados; k++) {
                assertTrue(conta[k] > 0, "o time " + k + " entrou vazio com "
                        + quantos + " jogadores: uma cama de pé sem ninguém "
                        + "atrás dela faria o placar dizer que alguém está "
                        + "ganhando sem jogar");
                menor = Math.min(menor, conta[k]);
                maior = Math.max(maior, conta[k]);
            }
            assertTrue(maior - menor <= 1, "com " + quantos + " jogadores a "
                    + "diferença entre o time mais cheio e o mais vazio foi "
                    + (maior - menor));
        }
    }

    // ------------------------------------------------------------------
    //  A luta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a espada derruba em cinco golpes e o melhor golpe é no tempo")
    void aEspadaEhAMesmaDasOutrasArenas() {
        assertEquals(5.0, BedWarsCatalog.golpesParaDerrubar(VIDA_CHEIA, DANO),
                1.0E-9);
        assertNotNull(BedWarsBuilder.conferirLuta(PERIODO, VIDA_CHEIA, 40),
                "40 de dano derruba em 2,5 golpes");
        assertNotNull(BedWarsBuilder.conferirLuta(PERIODO, VIDA_CHEIA, 8),
                "8 de dano pede 12,5 golpes e nenhuma briga acaba");
        assertNotNull(BedWarsBuilder.conferirLuta(4, VIDA_CHEIA, DANO),
                "período 4 é metade do das outras arenas: o jogador teria de "
                        + "reaprender o tempo do golpe num jogo em que não "
                        + "dá tempo de reaprender nada");
        assertNotNull(BedWarsBuilder.conferirLuta(40, VIDA_CHEIA, DANO),
                "período 40 é o dobro do teto da faixa");

        // A carência é o próprio período, posta pelo jogo: com isso o melhor
        // golpe cai exatamente no tempo da espada, e martelar rende um terço.
        assertEquals(PERIODO, ArenaPvpCatalog.melhorIntervalo(PERIODO,
                PERIODO, PERIODO * 3));

        int cheio = (int) Math.round(DANO * ArenaPvpCatalog.forcaDoGolpe(1.0));
        int martelado = (int) Math.round(DANO * ArenaPvpCatalog.forcaDoGolpe(
                ArenaPvpCatalog.prontidaoDe(2, PERIODO)));
        assertEquals(DANO, cheio);
        assertTrue(martelado * 3 < cheio, "martelar tirou " + martelado
                + " contra " + cheio + " no tempo");
    }

    @Test
    @DisplayName("o primeiro golpe na cama passa, e isso é um bug de verdade")
    void oPrimeiroGolpeNaCamaPassa() {
        // O RELATO: "bedwars quebro a cama varias vezes (tentei mais de 10) e
        // nao acontece nada. a cama do inimigo, claro."
        //
        // A causa: a cama nasce com ultimoGolpeNaCama = Long.MIN_VALUE para
        // dizer "ninguém bateu ainda", e o portão do período era a subtração
        // crua `tiquesDeJogo() - ultimo < periodo`. Essa subtração ESTOURA o
        // long e volta NEGATIVA, então o portão respondia "ainda está no
        // descanso" — e respondia para sempre, porque a marca só é anotada
        // depois de um golpe liberado. Nenhum som, nenhum recado, nenhum erro
        // no console: dez golpes numa cama que não tinha como cair.
        long marcaDeNunca = Long.MIN_VALUE;
        assertTrue(marcaDeNunca < 0 && 0L - marcaDeNunca < 0,
                "se o long parar de estourar aqui, este teste perdeu o "
                        + "sentido e o portão pode voltar a ser a subtração");
        assertTrue(ArenaComum.jaPassou(0L, marcaDeNunca, PERIODO_DA_CAMA));
        assertTrue(ArenaComum.jaPassou(20L, marcaDeNunca, 600L),
                "a lã do tear morreu do mesmo jeito: o tear só anota a hora "
                        + "DEPOIS de entregar, e nunca entregou a primeira");
    }

    @Test
    @DisplayName("o descanso entre golpes continua valendo")
    void oDescansoEntreGolpesVale() {
        // O conserto não pode abrir a cama ao martelo: o período é o que
        // segura o rush, e conferirCama exige de cinco a quinze segundos de
        // quebra justamente contando com ele.
        long golpe = 1_000L;
        assertFalse(ArenaComum.jaPassou(golpe, golpe, PERIODO_DA_CAMA),
                "golpe no mesmo tique");
        assertFalse(ArenaComum.jaPassou(golpe + PERIODO_DA_CAMA - 1, golpe,
                PERIODO_DA_CAMA), "um tique antes da hora");
        assertTrue(ArenaComum.jaPassou(golpe + PERIODO_DA_CAMA, golpe,
                PERIODO_DA_CAMA), "na hora exata o golpe conta");
        assertTrue(ArenaComum.jaPassou(golpe + PERIODO_DA_CAMA * 5, golpe,
                PERIODO_DA_CAMA));

        // espera zero é "sem descanso", e não pode virar um portão fechado
        assertTrue(ArenaComum.jaPassou(golpe, golpe, 0));
    }

    @Test
    @DisplayName("o relógio que volta atrás não tranca o jogador")
    void oRelogioQueVoltaAtras() {
        // Entre rodadas o contador de tiques zera. Uma marca da rodada
        // anterior ficaria no futuro, e com a subtração crua isso trancaria o
        // golpe pelo resto da partida. Diferença negativa é "faz tempo".
        assertTrue(ArenaComum.jaPassou(5L, 9_000L, PERIODO_DA_CAMA));
    }
}
