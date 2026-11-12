package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O relógio do RITMO, conferido sem servidor.
 *
 * <h2>Os dois testes que valem mais aqui</h2>
 *
 * {@link #nenhumPuloCaiEmDuasBatidas} percorre a escada inteira tique por
 * tique e prova que nenhum instante está dentro da folga de duas batidas. É o
 * defeito que vem de copiar o osu!, que mede a folga em milissegundos: a 150
 * bpm a batida dura 8 tiques e 150 ms cobrem 6 deles, as folgas de batidas
 * vizinhas se encostam, e o jogo passa a aceitar qualquer pulo. Ele continua
 * tocando, piscando e contando ponto — só não pode mais ser perdido.
 *
 * <p>{@link #aBatidaNuncaEhMaisRapidaQueOPulo} é o irmão oposto, e pegou um
 * erro de verdade: a primeira escada deste jogo subia até 150 bpm, e um pulo
 * no Minecraft leva uns doze tiques do chão ao chão. De 100 bpm para cima a
 * batida chega mais rápido do que dá para pular, por mais ritmo que a pessoa
 * tenha — os dezesseis perderiam as três vidas nas três primeiras batidas da
 * faixa e a partida acabaria sem ninguém entender por quê.
 *
 * <h2>E os dois que provam que o jogo é jogo</h2>
 *
 * {@link #quemPulaSemRitmoPerde} e {@link #oRitmistaPerfeitoSobrevive}: um
 * jogador de fase aleatória tem de perder as três vidas cedo, e um que acerta
 * a batida tem de poder atravessar a escada inteira. Sem o primeiro o jogo
 * não exige nada; sem o segundo ele não pode ser ganho.
 */
class MusicaTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final List<Integer> TEMPOS = List.of(20, 19, 18, 17, 16, 15, 14);
    private static final List<Integer> FOLGAS = List.of(6, 5, 5, 4, 3, 3, 3);
    private static final int BATIDAS_POR_COMPASSO = 4;
    private static final int COMPASSOS_POR_TEMPO = 4;
    private static final int COMPASSOS_DE_AVISO = 1;
    private static final int BATIDAS_POR_TEMPO = BATIDAS_POR_COMPASSO * COMPASSOS_POR_TEMPO;
    private static final int FOLGA_MINIMA = 2;
    private static final int VIDAS = 3;
    private static final int PONTOS_NA_BATIDA = 1;
    private static final int Y = 100;
    private static final int TABLADOS = 16;
    private static final int TABLADO_RAIO = 3;
    private static final int TABLADO_ANEL = 32;
    private static final int METRONOMO_RAIO = 5;
    private static final int METRONOMO_ALTURA = 14;
    private static final int MARCO_ALTURA = 18;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 300;

    /** Quantas batidas a escada tem antes de ficar na faixa mais rápida. */
    private static final int BATIDAS_DA_ESCADA = 7 * BATIDAS_POR_TEMPO;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = MusicaTest.class.getResourceAsStream("/musica.yml")) {
            assertNotNull(in, "musica.yml não foi empacotado");
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
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(TABLADOS, arena.getInt("arena.tablados"));
        assertEquals(TABLADO_RAIO, arena.getInt("arena.tablado-raio"));
        assertEquals(TABLADO_ANEL, arena.getInt("arena.tablado-anel"));
        assertEquals(METRONOMO_RAIO, arena.getInt("arena.metronomo-raio"));
        assertEquals(METRONOMO_ALTURA, arena.getInt("arena.metronomo-altura"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(TEMPOS, arena.getIntegerList("regras.tempos"));
        assertEquals(FOLGAS, arena.getIntegerList("regras.folgas"));
        assertEquals(FOLGA_MINIMA, arena.getInt("regras.folga-minima"));
        assertEquals(BATIDAS_POR_COMPASSO, arena.getInt("regras.batidas-por-compasso"));
        assertEquals(COMPASSOS_POR_TEMPO, arena.getInt("regras.compassos-por-tempo"));
        assertEquals(COMPASSOS_DE_AVISO, arena.getInt("regras.compassos-de-aviso"));
        assertEquals(VIDAS, arena.getInt("regras.vidas"));
        assertEquals(PONTOS_NA_BATIDA, arena.getInt("regras.pontos-na-batida"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("a escada do yml passa nas três conferências")
    void aEscadaPassaNaConferencia() {
        assertNull(MusicaBuilder.conferirAndamentos(TEMPOS, FOLGAS, FOLGA_MINIMA, VIDAS,
                BATIDAS_POR_TEMPO));
        assertNull(MusicaBuilder.conferirNumeros(TABLADOS, TABLADO_RAIO,
                TABLADO_ANEL, METRONOMO_RAIO, MAXIMO, VIDAS));
        assertNull(MusicaBuilder.conferirRelogio(TEMPOS, BATIDAS_POR_TEMPO,
                BATIDAS_POR_COMPASSO, COMPASSOS_DE_AVISO * BATIDAS_POR_COMPASSO,
                TEMPO_LIMITE));
    }

    // ------------------------------------------------------------------
    //  A folga
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nenhum pulo cai dentro da folga de duas batidas")
    void nenhumPuloCaiEmDuasBatidas() {
        long fim = MusicaCatalog.tiqueDaBatida(BATIDAS_DA_ESCADA + BATIDAS_POR_TEMPO,
                TEMPOS, BATIDAS_POR_TEMPO);
        for (long tique = 0; tique <= fim; tique++) {
            int quantas = 0;
            // Olha as batidas em volta deste tique, não todas as 128.
            int perto = MusicaCatalog.batidaDoTique(tique, TEMPOS, BATIDAS_POR_TEMPO);
            for (int batida = Math.max(0, perto - 2); batida <= perto + 2; batida++) {
                long dela = MusicaCatalog.tiqueDaBatida(batida, TEMPOS,
                        BATIDAS_POR_TEMPO);
                int folga = MusicaCatalog.folgaDe(FOLGAS,
                        MusicaCatalog.faixaDaBatida(batida, TEMPOS.size(),
                                BATIDAS_POR_TEMPO), FOLGA_MINIMA);
                if (Math.abs(tique - dela) <= folga) {
                    quantas++;
                }
            }
            assertTrue(quantas <= 1, "o tique " + tique + " cai dentro da folga "
                    + "de " + quantas + " batidas ao mesmo tempo — com folgas "
                    + "que se encostam, todo pulo acerta e o jogo não pode "
                    + "mais ser perdido");
        }
    }

    @Test
    @DisplayName("a batida nunca é mais rápida que o pulo do jogador")
    void aBatidaNuncaEhMaisRapidaQueOPulo() {
        StringBuilder escada = new StringBuilder();
        for (int tempo : TEMPOS) {
            escada.append(" ").append(MusicaCatalog.bpmDe(tempo)).append("bpm");
        }
        for (int tempo : TEMPOS) {
            assertTrue(tempo >= MusicaCatalog.BATIDA_MAIS_RAPIDA,
                    "a faixa de " + MusicaCatalog.bpmDe(tempo) + " bpm pede uma "
                            + "batida a cada " + tempo + " tiques; um pulo "
                            + "leva " + MusicaCatalog.CICLO_DO_PULO
                            + " tiques entre um e outro e o ritmista erra ±"
                            + MusicaCatalog.JITTER_DO_RITMISTA
                            + ", então o mínimo é "
                            + MusicaCatalog.BATIDA_MAIS_RAPIDA
                            + " — escada:" + escada);
        }
        // E a obra recusa uma escada que passa do teto do pulo.
        String rapida = MusicaBuilder.conferirAndamentos(
                List.of(20, 16, 14, 12), List.of(6, 3, 3, 3), FOLGA_MINIMA,
                VIDAS, BATIDAS_POR_TEMPO);
        assertNotNull(rapida);
        assertTrue(rapida.contains("sem pulo para a seguinte"), rapida);
    }

    @Test
    @DisplayName("a folga aperta escada abaixo, e nunca cobre a batida")
    void aFolgaApertaEscadaAbaixo() {
        int antes = Integer.MAX_VALUE;
        for (int faixa = 0; faixa < TEMPOS.size(); faixa++) {
            int tempo = TEMPOS.get(faixa);
            int folga = MusicaCatalog.folgaDe(FOLGAS, faixa, FOLGA_MINIMA);
            assertTrue(folga <= antes, "a faixa " + (faixa + 1) + " dá folga de ±"
                    + folga + " e a anterior dava ±" + antes
                    + ": a folga é a única coisa que ainda dificulta depois de "
                    + "o andamento bater no teto do pulo");
            antes = folga;
            assertTrue(folga >= MusicaCatalog.FOLGA_HONESTA, "faixa "
                    + (faixa + 1) + " com folga de ±" + folga + " tique");
            double cobre = (2.0 * folga + 1) / tempo;
            assertTrue(cobre <= 0.70, "a " + MusicaCatalog.bpmDe(tempo)
                    + " bpm a janela cobre " + Math.round(cobre * 100)
                    + "% da batida: acima de 70% quase todo pulo acerta");
        }
        // A folga encolhe em tempo real, e não só em fração da batida.
        assertEquals(6, MusicaCatalog.folgaDe(FOLGAS, 0, FOLGA_MINIMA));
        assertEquals(3, MusicaCatalog.folgaDe(FOLGAS, TEMPOS.size() - 1,
                FOLGA_MINIMA));
    }

    @Test
    @DisplayName("a primeira faixa dá folga bastante para absorver o ping")
    void aPrimeiraFaixaAbsorveOPing() {
        int folga = MusicaCatalog.folgaDe(FOLGAS, 0, FOLGA_MINIMA);
        assertTrue(folga >= MusicaCatalog.FOLGA_DO_PING, "a faixa mais lenta dá "
                + "±" + folga + " tiques (" + (folga * 50) + " ms) e precisa de ±"
                + MusicaCatalog.FOLGA_DO_PING + ": é ela que absorve o ping de "
                + "quem joga de longe, e isso não aparece para quem testa em casa");

        String curta = MusicaBuilder.conferirAndamentos(TEMPOS,
                List.of(2, 2, 2, 2, 2, 2, 2), FOLGA_MINIMA, VIDAS,
                BATIDAS_POR_TEMPO);
        assertNotNull(curta);
        assertTrue(curta.contains("absorve o ping"), curta);
    }

    @Test
    @DisplayName("a conferência recusa folga larga, folga que não aperta e escada que desacelera")
    void aConferenciaPegaOsErrosDaEscada() {
        String larga = MusicaBuilder.conferirAndamentos(List.of(20, 18, 16, 14),
                List.of(10, 8, 7, 7), FOLGA_MINIMA, VIDAS, BATIDAS_POR_TEMPO);
        assertNotNull(larga);
        assertTrue(larga.contains("se encostam"), larga);

        String frouxa = MusicaBuilder.conferirAndamentos(List.of(20, 18, 16, 14),
                List.of(5, 5, 3, 5), FOLGA_MINIMA, VIDAS, BATIDAS_POR_TEMPO);
        assertNotNull(frouxa);
        assertTrue(frouxa.contains("apertar"), frouxa);

        String desacelera = MusicaBuilder.conferirAndamentos(
                List.of(20, 14, 16, 14), List.of(6, 3, 3, 3), FOLGA_MINIMA,
                VIDAS, BATIDAS_POR_TEMPO);
        assertNotNull(desacelera);
        assertTrue(desacelera.contains("a escada tem de subir"), desacelera);

        String poucas = MusicaBuilder.conferirAndamentos(TEMPOS,
                List.of(6, 5, 5), FOLGA_MINIMA, VIDAS,
                BATIDAS_POR_TEMPO);
        assertNotNull(poucas);
        assertTrue(poucas.contains("folga(s)"), poucas);

        assertNotNull(MusicaBuilder.conferirAndamentos(List.of(), FOLGAS,
                FOLGA_MINIMA, VIDAS,
                BATIDAS_POR_TEMPO));
        assertNotNull(MusicaBuilder.conferirAndamentos(TEMPOS, List.of(),
                FOLGA_MINIMA, VIDAS,
                BATIDAS_POR_TEMPO));
        assertNotNull(MusicaBuilder.conferirAndamentos(TEMPOS, FOLGAS, 1, VIDAS,
                BATIDAS_POR_TEMPO));
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o tique de cada batida e a batida de cada tique se fecham")
    void oRelogioEhConsistente() {
        for (int batida = 0; batida < 300; batida++) {
            long tique = MusicaCatalog.tiqueDaBatida(batida, TEMPOS,
                    BATIDAS_POR_TEMPO);
            assertEquals(batida, MusicaCatalog.batidaDoTique(tique, TEMPOS,
                            BATIDAS_POR_TEMPO),
                    "a batida " + batida + " cai no tique " + tique
                            + " e a volta não dá ela");
            int passo = MusicaCatalog.tempoDaBatida(batida, TEMPOS,
                    BATIDAS_POR_TEMPO);
            assertEquals(batida, MusicaCatalog.batidaDoTique(tique + passo - 1,
                            TEMPOS, BATIDAS_POR_TEMPO),
                    "um tique antes da batida " + (batida + 1)
                            + " já conta como ela");
            assertEquals(passo, MusicaCatalog.tiqueDaBatida(batida + 1, TEMPOS,
                            BATIDAS_POR_TEMPO) - tique,
                    "o passo da batida " + batida + " não bate com o andamento");
        }
    }

    @Test
    @DisplayName("depois da escada o andamento fica no mais rápido")
    void depoisDaEscadaFicaNoMaisRapido() {
        int maisRapido = TEMPOS.get(TEMPOS.size() - 1);
        for (int batida = BATIDAS_DA_ESCADA; batida < BATIDAS_DA_ESCADA + 50; batida++) {
            assertEquals(maisRapido, MusicaCatalog.tempoDaBatida(batida, TEMPOS,
                            BATIDAS_POR_TEMPO),
                    "a batida " + batida + " saiu da escada e mudou de andamento");
            assertEquals(TEMPOS.size() - 1, MusicaCatalog.faixaDaBatida(batida,
                    TEMPOS.size(), BATIDAS_POR_TEMPO));
        }
    }

    @Test
    @DisplayName("a batida mais perto é a vizinha, com o sinal certo")
    void aBatidaMaisPertoEscolheAVizinha() {
        for (int batida = 1; batida < 100; batida++) {
            long tique = MusicaCatalog.tiqueDaBatida(batida, TEMPOS,
                    BATIDAS_POR_TEMPO);

            int[] naMosca = MusicaCatalog.batidaMaisPerto(tique, TEMPOS,
                    BATIDAS_POR_TEMPO);
            assertEquals(batida, naMosca[0]);
            assertEquals(0, naMosca[1]);

            int[] adiantado = MusicaCatalog.batidaMaisPerto(tique - 2, TEMPOS,
                    BATIDAS_POR_TEMPO);
            assertEquals(batida, adiantado[0], "pular dois tiques antes da "
                    + "batida " + batida + " tem de contar como ela, e não "
                    + "como erro na anterior");
            assertEquals(-2, adiantado[1]);

            int[] atrasado = MusicaCatalog.batidaMaisPerto(tique + 2, TEMPOS,
                    BATIDAS_POR_TEMPO);
            assertEquals(batida, atrasado[0]);
            assertEquals(2, atrasado[1]);
        }
    }

    @Test
    @DisplayName("a escada cabe na partida e o aviso cabe na primeira faixa")
    void oRelogioDaPartidaFecha() {
        long escada = MusicaCatalog.tiqueDaBatida(BATIDAS_DA_ESCADA, TEMPOS,
                BATIDAS_POR_TEMPO);
        long segundos = escada / 20;
        assertTrue(segundos <= TEMPO_LIMITE * 2L / 3, "a escada dura "
                + segundos + "s e o limite da partida é " + TEMPO_LIMITE + "s");
        assertTrue(segundos >= 60, "a escada dura só " + segundos
                + "s: não dá tempo de a dificuldade subir");

        int aviso = COMPASSOS_DE_AVISO * BATIDAS_POR_COMPASSO;
        assertTrue(aviso >= BATIDAS_POR_COMPASSO, "o aviso tem de ser pelo "
                + "menos um compasso inteiro");
        assertTrue(aviso < BATIDAS_POR_TEMPO, "o aviso come a primeira faixa");

        assertNotNull(MusicaBuilder.conferirRelogio(TEMPOS, BATIDAS_POR_TEMPO,
                BATIDAS_POR_COMPASSO, 0, TEMPO_LIMITE));
        assertNotNull(MusicaBuilder.conferirRelogio(TEMPOS, BATIDAS_POR_TEMPO,
                BATIDAS_POR_COMPASSO, BATIDAS_POR_TEMPO, TEMPO_LIMITE));
        assertNotNull(MusicaBuilder.conferirRelogio(TEMPOS, BATIDAS_POR_TEMPO,
                BATIDAS_POR_COMPASSO, 4, 90));
    }

    // ------------------------------------------------------------------
    //  O jogo é jogo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("quem pula sem ritmo perde as três vidas cedo")
    void quemPulaSemRitmoPerde() {
        Random sorte = new Random(4242);
        int morreram = 0;
        int rodadas = 2000;
        for (int k = 0; k < rodadas; k++) {
            if (simular(sorte, -1) <= 0) {
                morreram++;
            }
        }
        double parte = morreram / (double) rodadas;
        assertTrue(parte >= 0.90, "só " + Math.round(parte * 100) + "% dos "
                + "jogadores de fase aleatória perderam as " + VIDAS
                + " vidas na escada — um jogo de ritmo em que pular a esmo "
                + "sobrevive não exige ritmo nenhum");
    }

    @Test
    @DisplayName("quem acerta a batida atravessa a escada inteira")
    void oRitmistaPerfeitoSobrevive() {
        Random sorte = new Random(777);
        for (int desvio = 0; desvio <= MusicaCatalog.JITTER_DO_RITMISTA; desvio++) {
            int piorCaso = VIDAS;
            for (int k = 0; k < 200; k++) {
                piorCaso = Math.min(piorCaso, simular(sorte, desvio));
            }
            assertTrue(piorCaso > 0, "um jogador que erra a batida por no "
                    + "máximo " + desvio + " tique(s) perdeu as vidas na "
                    + "escada: com o andamento parando em "
                    + MusicaCatalog.bpmDe(MusicaCatalog.CICLO_DO_PULO)
                    + " bpm e a folga mais apertada em ±"
                    + FOLGAS.get(FOLGAS.size() - 1) + ", isso tem de dar");
        }
    }

    /**
     * Roda a escada para um jogador e devolve quantas vidas sobraram.
     *
     * Com {@code precisao} negativa o jogador pula a esmo, respeitando só o
     * ciclo do pulo. Com zero ou mais ele mira a batida e erra no máximo
     * aquilo. É o mesmo laço do jogo: o pulo resolve a batida mais perto, e
     * batida que fecha a folga sem pulo também cobra.
     */
    private static int simular(Random sorte, int precisao) {
        int vidas = VIDAS;
        int aviso = COMPASSOS_DE_AVISO * BATIDAS_POR_COMPASSO;
        Set<Integer> resolvidas = new HashSet<>();
        List<Long> pulos = new ArrayList<>();

        long fim = MusicaCatalog.tiqueDaBatida(BATIDAS_DA_ESCADA, TEMPOS,
                BATIDAS_POR_TEMPO);
        if (precisao < 0) {
            long quando = sorte.nextInt(MusicaCatalog.CICLO_DO_PULO);
            while (quando <= fim) {
                pulos.add(quando);
                quando += MusicaCatalog.CICLO_DO_PULO + sorte.nextInt(5);
            }
        } else {
            for (int batida = 0; batida < BATIDAS_DA_ESCADA; batida++) {
                long dela = MusicaCatalog.tiqueDaBatida(batida, TEMPOS,
                        BATIDAS_POR_TEMPO);
                long quando = dela + (precisao == 0 ? 0
                        : sorte.nextInt(precisao * 2 + 1) - precisao);
                if (quando >= 0 && (pulos.isEmpty() || quando
                        - pulos.get(pulos.size() - 1) >= MusicaCatalog.CICLO_DO_PULO)) {
                    pulos.add(quando);
                }
            }
        }

        int proximoPulo = 0;
        for (int batida = 0; batida < BATIDAS_DA_ESCADA && vidas > 0; batida++) {
            long dela = MusicaCatalog.tiqueDaBatida(batida, TEMPOS,
                    BATIDAS_POR_TEMPO);
            int folga = MusicaCatalog.folgaDe(FOLGAS,
                    MusicaCatalog.faixaDaBatida(batida, TEMPOS.size(),
                            BATIDAS_POR_TEMPO), FOLGA_MINIMA);
            long fecha = dela + folga;

            // Todos os pulos que chegaram até a folga desta batida fechar.
            while (proximoPulo < pulos.size() && pulos.get(proximoPulo) <= fecha) {
                long quando = pulos.get(proximoPulo++);
                int[] perto = MusicaCatalog.batidaMaisPerto(quando, TEMPOS,
                        BATIDAS_POR_TEMPO);
                if (perto[0] < aviso || !resolvidas.add(perto[0])) {
                    continue;
                }
                int folgaDele = MusicaCatalog.folgaDe(FOLGAS,
                        MusicaCatalog.faixaDaBatida(perto[0], TEMPOS.size(),
                                BATIDAS_POR_TEMPO), FOLGA_MINIMA);
                if (Math.abs(perto[1]) > folgaDele) {
                    vidas--;
                }
            }
            if (batida >= aviso && !resolvidas.contains(batida)) {
                resolvidas.add(batida);
                vidas--;
            }
        }
        return vidas;
    }

    // ------------------------------------------------------------------
    //  A arena
    // ------------------------------------------------------------------

    @Test
    @DisplayName("não se pula do seu tablado para o do vizinho nem para o metrônomo")
    void osTabladosNaoSeAlcancam() {
        double entre = MusicaCatalog.entreVizinhos(TABLADOS, TABLADO_ANEL);
        double vazio = entre - 2.0 * TABLADO_RAIO;
        assertTrue(vazio >= MusicaBuilder.SALTO_DO_JOGADOR, "entre dois "
                + "tablados vizinhos sobram " + Math.round(vazio * 10) / 10.0
                + " blocos de vazio e um jogador correndo pula quatro");

        double ateOMeio = TABLADO_ANEL - TABLADO_RAIO - METRONOMO_RAIO;
        assertTrue(ateOMeio >= MusicaBuilder.SALTO_DO_JOGADOR, "entre o anel e "
                + "o metrônomo sobram " + ateOMeio + " blocos: o metrônomo não "
                + "tem vida para perder, e quem pulasse nele ficaria imune");

        // E bloco por bloco, não só de centro a centro.
        for (int dx = -TABLADO_RAIO; dx <= TABLADO_RAIO; dx++) {
            for (int dz = -TABLADO_RAIO; dz <= TABLADO_RAIO; dz++) {
                if (!ArenaComum.noDiscoMeio(dx, dz, TABLADO_RAIO)) {
                    continue;
                }
                int[] meu = MusicaCatalog.centroDoTablado(0, TABLADOS, TABLADO_ANEL);
                int x = meu[0] + dx;
                int z = meu[1] + dz;
                for (int outro : new int[]{1, TABLADOS - 1}) {
                    int[] centro = MusicaCatalog.centroDoTablado(outro, TABLADOS,
                            TABLADO_ANEL);
                    double longe = Math.hypot(x - centro[0], z - centro[1])
                            - TABLADO_RAIO;
                    assertTrue(longe >= MusicaBuilder.SALTO_DO_JOGADOR - 1,
                            "do bloco (" + dx + "," + dz + ") do tablado 1 até "
                                    + "a beira do tablado " + (outro + 1)
                                    + " há " + Math.round(longe * 10) / 10.0
                                    + " blocos");
                }
            }
        }
    }

    @Test
    @DisplayName("há um tablado por jogador, e cada um no seu")
    void umTabladoPorJogador() {
        assertTrue(TABLADOS >= MAXIMO, "são " + TABLADOS + " tablados para "
                + MAXIMO + " jogadores");
        Set<Long> vistos = new HashSet<>();
        for (int k = 0; k < TABLADOS; k++) {
            int[] centro = MusicaCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            assertTrue(vistos.add(ArenaComum.chave(centro[0], 0, centro[1])),
                    "dois tablados no mesmo lugar, no índice " + k);
            assertEquals(k, MusicaCatalog.tabladoDoPonto(centro[0], centro[1],
                    TABLADOS, TABLADO_ANEL, TABLADO_RAIO));
        }
        assertEquals(-1, MusicaCatalog.tabladoDoPonto(0, 0, TABLADOS,
                TABLADO_ANEL, TABLADO_RAIO), "o metrônomo é um tablado");

        assertNotNull(MusicaBuilder.conferirNumeros(8, TABLADO_RAIO,
                TABLADO_ANEL, METRONOMO_RAIO, MAXIMO, VIDAS));
        assertNotNull(MusicaBuilder.conferirNumeros(TABLADOS, 2, TABLADO_ANEL,
                METRONOMO_RAIO, MAXIMO, VIDAS));
        assertNotNull(MusicaBuilder.conferirNumeros(TABLADOS, TABLADO_RAIO, 18,
                METRONOMO_RAIO, MAXIMO, VIDAS));
        assertNotNull(MusicaBuilder.conferirNumeros(TABLADOS, TABLADO_RAIO,
                TABLADO_ANEL, 26, MAXIMO, VIDAS));
    }

    @Test
    @DisplayName("o tablado encolhe um anel por vida e acaba com elas")
    void oTabladoEncolheComAsVidas() {
        for (int sobrou = VIDAS; sobrou >= 0; sobrou--) {
            int raio = TABLADO_RAIO - (VIDAS - sobrou);
            if (sobrou > 0) {
                assertTrue(raio >= 1, "com " + sobrou + " vida(s) o tablado "
                        + "ficaria com raio " + raio + ": ele acabaria antes "
                        + "das vidas");
            } else {
                assertTrue(raio <= 0, "com zero vidas o tablado ainda teria "
                        + "raio " + raio);
            }
        }
    }

    @Test
    @DisplayName("o poste da placa fica fora do tablado, que encolhe")
    void oPosteFicaFora() {
        for (int k = 0; k < TABLADOS; k++) {
            int[] poste = MusicaCatalog.posteDoTablado(k, TABLADOS, TABLADO_ANEL,
                    TABLADO_RAIO);
            assertEquals(-1, MusicaCatalog.tabladoDoPonto(poste[0], poste[1],
                            TABLADOS, TABLADO_ANEL, TABLADO_RAIO),
                    "o poste do tablado " + (k + 1) + " nasce dentro de um "
                            + "tablado, e o tablado encolhe");
            org.bukkit.block.BlockFace frente =
                    MusicaCatalog.frenteDaPlaca(poste[0], poste[1]);
            int[] centro = MusicaCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            assertTrue(Math.hypot(poste[0] + frente.getModX() - centro[0],
                            poste[1] + frente.getModZ() - centro[1])
                            < Math.hypot(poste[0] - centro[0], poste[1] - centro[1]),
                    "a placa do tablado " + (k + 1) + " olha para fora dele");
        }
    }

    @Test
    @DisplayName("a melodia cabe no bloco de nota")
    void aMelodiaCabeNoBlocoDeNota() {
        List<Double> melodia = arena.getDoubleList("melodia");
        assertTrue(melodia.size() >= 4, "a melodia tem " + melodia.size()
                + " nota(s): com tão pouca, o metrônomo volta a ser seco");
        for (double tom : melodia) {
            assertTrue(tom >= 0.5 && tom <= 2.0, "o tom " + tom + " está fora "
                    + "da faixa do bloco de nota, que vai de 0,5 a 2,0");
        }
    }

    @Test
    @DisplayName("segurar a tecla de pulo não joga sozinho em nenhuma faixa")
    void segurarATeclaNaoJogaSozinho() {
        StringBuilder tabela = new StringBuilder();
        for (int faixa = 0; faixa < TEMPOS.size(); faixa++) {
            int tempo = TEMPOS.get(faixa);
            int folga = MusicaCatalog.folgaDe(FOLGAS, faixa, FOLGA_MINIMA);
            int erros = MusicaCatalog.errosDoPuloContinuo(tempo, folga,
                    BATIDAS_POR_TEMPO, MusicaCatalog.CICLO_DO_PULO);
            tabela.append(" ").append(MusicaCatalog.bpmDe(tempo))
                    .append("bpm±").append(folga).append("→").append(erros);
            assertTrue(erros >= VIDAS, "na faixa de "
                    + MusicaCatalog.bpmDe(tempo) + " bpm com folga ±" + folga
                    + ", quem segura a tecla erra só " + erros + " das "
                    + BATIDAS_POR_TEMPO + " batidas e tem " + VIDAS
                    + " vidas — a tecla presa atravessa a faixa sem ritmo "
                    + "nenhum. Tabela:" + tabela);
        }

        // E o caso concreto que obrigou a faixa de 75 bpm a ter folga ±3:
        // com ±4, o trilho de doze tiques acerta TODAS as dezesseis batidas.
        assertEquals(0, MusicaCatalog.errosDoPuloContinuo(16, 4,
                        BATIDAS_POR_TEMPO, MusicaCatalog.CICLO_DO_PULO),
                "com batida de 16 tiques e folga ±4 o trilho de 12 devia "
                        + "acertar tudo");
        String presa = MusicaBuilder.conferirAndamentos(List.of(20, 18, 16),
                List.of(6, 5, 4), FOLGA_MINIMA, VIDAS, BATIDAS_POR_TEMPO);
        assertNotNull(presa);
        assertTrue(presa.contains("SEGURA a tecla"), presa);
    }

    @Test
    @DisplayName("o andamento escrito bate com os tiques")
    void oBpmBateComOsTiques() {
        assertEquals(60, MusicaCatalog.bpmDe(20));
        assertEquals(85, MusicaCatalog.bpmDe(14));
        assertEquals(14, MusicaCatalog.BATIDA_MAIS_RAPIDA);
        assertEquals(85, MusicaCatalog.bpmDe(MusicaCatalog.BATIDA_MAIS_RAPIDA));
        for (int tempo : TEMPOS) {
            int bpm = MusicaCatalog.bpmDe(tempo);
            assertTrue(bpm >= 50 && bpm <= 85, "a faixa de " + tempo
                    + " tiques dá " + bpm + " bpm");
        }
    }
}
