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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O banco de perguntas e a arena do Quiz, conferidos sem servidor.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * É o {@link #aRespostaCertaSeEspalhaPelasPosicoes}. O erro que ele pega é o
 * mais comum em banco de pergunta escrito à mão e não aparece em lugar
 * nenhum: quem escreve põe a resposta certa onde ela soa melhor na leitura, e
 * sai um banco em que a quarta opção quase nunca é a certa. O jogo continua
 * funcionando — e na segunda rodada os dezesseis jogadores já sabem que não
 * vale pisar no tablado D, e aí não estão mais respondendo pergunta.
 *
 * <p>A primeira versão deste banco tinha a certa na quarta posição em UMA das
 * quarenta e oito perguntas. Este teste é o motivo de ela estar em doze.
 *
 * <h2>O irmão dele</h2>
 *
 * {@link #oBaralhoNaoRepeteEntreRodadas} prova que as quatro rodadas
 * seguintes gastam as dezesseis perguntas de um nível sem repetir nenhuma.
 * Sortear a cada pergunta seria mais simples e daria pergunta repetida na
 * mesma partida em quase toda rodada.
 */
class QuizTest {

    private static YamlConfiguration arena;
    private static List<QuizCatalog.Pergunta> banco;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int ATRIO_RAIO = 5;
    private static final int TABLADOS = 4;
    private static final int TABLADO_RAIO = 5;
    private static final int TABLADO_ANEL = 16;
    private static final int PONTE_LARGURA = 3;
    private static final int MARCO_ALTURA = 16;
    private static final int TEMPO_RESPOSTA = 9;
    private static final int TEMPO_REVELACAO = 4;
    private static final int QUEDA_TIQUES = 30;
    private static final int PERGUNTAS = 12;
    private static final List<Integer> POR_NIVEL = List.of(4, 4, 4);
    private static final int PONTOS_ACERTO = 10;
    private static final int PONTOS_RAPIDEZ = 10;
    private static final int MAXIMO = 16;
    private static final int VIDAS = 2;
    private static final int TEMPO_LIMITE = 300;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = QuizTest.class.getResourceAsStream("/quiz.yml")) {
            assertNotNull(in, "quiz.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        banco = QuizCatalog.lerBanco(arena);
        assertEquals(198, banco.size(), "o banco do yml mudou de tamanho");
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(ATRIO_RAIO, arena.getInt("arena.atrio-raio"));
        assertEquals(TABLADOS, arena.getInt("arena.tablados"));
        assertEquals(TABLADO_RAIO, arena.getInt("arena.tablado-raio"));
        assertEquals(TABLADO_ANEL, arena.getInt("arena.tablado-anel"));
        assertEquals(PONTE_LARGURA, arena.getInt("arena.ponte-largura"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(TEMPO_RESPOSTA, arena.getInt("regras.tempo-resposta"));
        assertEquals(TEMPO_REVELACAO, arena.getInt("regras.tempo-revelacao"));
        assertEquals(QUEDA_TIQUES, arena.getInt("regras.queda-tiques"));
        assertEquals(PERGUNTAS, arena.getInt("regras.perguntas-por-rodada"));
        assertEquals(POR_NIVEL, arena.getIntegerList("regras.por-nivel"));
        assertEquals(PONTOS_ACERTO, arena.getInt("regras.pontos-acerto"));
        assertEquals(PONTOS_RAPIDEZ, arena.getInt("regras.pontos-rapidez"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(VIDAS, arena.getInt("regras.vidas"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    // ------------------------------------------------------------------
    //  O banco
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o banco do yml passa na conferência inteira")
    void oBancoPassaNaConferencia() {
        assertNull(QuizBuilder.conferirBanco(banco, POR_NIVEL));
    }

    @Test
    @DisplayName("a resposta certa se espalha pelas quatro posições")
    void aRespostaCertaSeEspalhaPelasPosicoes() {
        int opcoes = QuizCatalog.maiorNumeroDeOpcoes(banco);
        assertEquals(4, opcoes, "toda pergunta tem de ter quatro opções");

        int[] quantas = new int[opcoes];
        for (QuizCatalog.Pergunta pergunta : banco) {
            quantas[pergunta.certa()]++;
        }
        StringBuilder conta = new StringBuilder();
        for (int k = 0; k < opcoes; k++) {
            conta.append(" ").append(QuizCatalog.LETRAS[k]).append("=")
                    .append(quantas[k]);
        }
        int justo = banco.size() / opcoes;
        for (int k = 0; k < opcoes; k++) {
            assertTrue(quantas[k] >= justo / 2 && quantas[k] <= justo * 2,
                    "a certa cai na posição " + QuizCatalog.LETRAS[k] + " em "
                            + quantas[k] + " das " + banco.size()
                            + " perguntas, e o justo é perto de " + justo
                            + " —" + conta + ". Um banco torto faz o jogador "
                            + "decorar a posição em vez de saber a resposta.");
        }

        // E também DENTRO de cada nível: um nível viciado estraga as quatro
        // perguntas em que ele cai, mesmo com o banco inteiro equilibrado.
        for (int nivel = 1; nivel <= 3; nivel++) {
            int[] doNivel = new int[opcoes];
            int total = 0;
            for (QuizCatalog.Pergunta pergunta : banco) {
                if (pergunta.nivel() == nivel) {
                    doNivel[pergunta.certa()]++;
                    total++;
                }
            }
            for (int k = 0; k < opcoes; k++) {
                assertTrue(doNivel[k] > 0, "no nível " + nivel
                        + " a certa nunca é a opção " + QuizCatalog.LETRAS[k]
                        + " (" + total + " perguntas no nível)");
            }
        }
    }

    @Test
    @DisplayName("o banco tem fôlego para muitas partidas seguidas")
    void oBancoTemFolego() {
        // O defeito que isto pega não é de código, é de conteúdo: um banco
        // curto faz a terceira partida da noite ser a repetição da primeira.
        // Com o banco de agora, uma noite inteira de rodadas não fecha o
        // ciclo de nenhum dos três níveis.
        for (int nivel = 1; nivel <= 3; nivel++) {
            int tem = QuizCatalog.quantasNoNivel(banco, nivel);
            int pede = POR_NIVEL.get(nivel - 1);
            assertTrue(tem >= pede * 10, "o nível " + nivel + " tem " + tem
                    + " perguntas e a rodada pede " + pede + ": dá para "
                    + (tem / pede) + " rodadas antes de repetir, e dez é o "
                    + "mínimo para uma noite não virar revisão.");
        }
    }

    @Test
    @DisplayName("errar custa uma vida, e só a última elimina")
    void aVidaSeguraOErro() {
        // Era o defeito mais caro do jogo e não aparecia em teste nenhum:
        // com dois jogadores, a primeira pergunta errada eliminava um,
        // sobrava um de pé, e a rodada fechava com onze perguntas por
        // fazer. Com vidas, o erro devolve ao átrio até elas acabarem.
        assertFalse(Quiz.erroElimina(VIDAS), "com " + VIDAS + " vidas o "
                + "primeiro erro não pode eliminar");
        assertFalse(Quiz.erroElimina(1), "com a última vida na mão o erro "
                + "ainda devolve ao átrio");
        assertTrue(Quiz.erroElimina(0), "sem vida, errar tira da rodada");

        // E o caminho inteiro: VIDAS erros perdoados, o seguinte não.
        int tem = VIDAS;
        for (int erro = 1; erro <= VIDAS; erro++) {
            assertFalse(Quiz.erroElimina(tem), "o erro " + erro + " de "
                    + VIDAS + " não podia eliminar");
            tem--;
        }
        assertTrue(Quiz.erroElimina(tem), "o erro " + (VIDAS + 1)
                + " tinha de eliminar");
    }

    @Test
    @DisplayName("com vidas zero volta a regra antiga: o primeiro erro elimina")
    void vidaZeroEhARegraAntiga() {
        assertTrue(Quiz.erroElimina(0));
    }

    @Test
    @DisplayName("a conferência recusa o banco viciado numa posição")
    void aConferenciaPegaOBancoViciado() {
        List<QuizCatalog.Pergunta> viciado = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            // Todas com a certa na segunda opção, que é o vício clássico.
            viciado.add(new QuizCatalog.Pergunta(1 + k % 3, "pergunta " + k,
                    List.of("a" + k, "b" + k, "c" + k, "d" + k), 1));
        }
        String erro = QuizBuilder.conferirBanco(viciado, POR_NIVEL);
        assertNotNull(erro, "um banco com a certa sempre no B tem de ser recusado");
        assertTrue(erro.contains("decorar a posição"), erro);
    }

    @Test
    @DisplayName("a conferência recusa pergunta repetida, opção comprida e certa fora da faixa")
    void aConferenciaPegaOsErrosDeBanco() {
        assertTrue(assertRecusa(List.of(
                nova(1, "igual", "a", "b", "c", "d", 0),
                nova(1, "igual", "e", "f", "g", "h", 1)))
                .contains("duas vezes no banco"));

        assertTrue(assertRecusa(List.of(nova(1, "quanto é dois mais dois",
                "uma opção comprida demais para a placa", "b", "c", "d", 0)))
                .contains("a placa do tablado aguenta"));

        assertTrue(assertRecusa(List.of(nova(1, "a".repeat(
                QuizBuilder.LETRAS_NA_PERGUNTA + 1), "a", "b", "c", "d", 0)))
                .contains("o painel do átrio aguenta"));

        assertTrue(assertRecusa(List.of(nova(1, "fora da faixa",
                "a", "b", "c", "d", 4))).contains("começa em zero"));

        assertTrue(assertRecusa(List.of(nova(1, "opção repetida",
                "a", "a", "c", "d", 0))).contains("haveria dois tablados"));

        assertTrue(assertRecusa(List.of()).contains("está vazio"));
    }

    @Test
    @DisplayName("a conferência recusa nível sem sobra sobre o que a rodada pede")
    void aConferenciaPegaONivelMagro() {
        List<QuizCatalog.Pergunta> magro = new ArrayList<>();
        // Seis perguntas de nível 1 para uma rodada que pede quatro: dá, mas
        // toda rodada sai quase igual à anterior.
        for (int k = 0; k < 6; k++) {
            magro.add(nova(1, "pergunta " + k, "a" + k, "b" + k, "c" + k,
                    "d" + k, k % 4));
        }
        String erro = QuizBuilder.conferirBanco(magro, List.of(4));
        assertNotNull(erro);
        assertTrue(erro.contains("quase igual à anterior"), erro);

        // Com o dobro do que a rodada pede, passa.
        for (int k = 6; k < 8; k++) {
            magro.add(nova(1, "pergunta " + k, "a" + k, "b" + k, "c" + k,
                    "d" + k, k % 4));
        }
        assertNull(QuizBuilder.conferirBanco(magro, List.of(4)));
    }

    // ------------------------------------------------------------------
    //  O baralho
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nenhuma rodada repete pergunta")
    void oBaralhoNaoRepeteNaRodada() {
        QuizCatalog.Baralho baralho = new QuizCatalog.Baralho(banco,
                new Random(7));
        for (int rodada = 0; rodada < 2000; rodada++) {
            List<QuizCatalog.Pergunta> saiu = baralho.rodada(POR_NIVEL);
            assertEquals(PERGUNTAS, saiu.size(),
                    "a rodada " + rodada + " saiu com " + saiu.size()
                            + " perguntas");
            assertEquals(saiu.size(), new HashSet<>(saiu).size(),
                    "a rodada " + rodada + " repetiu pergunta");
        }
    }

    @Test
    @DisplayName("as quatro rodadas seguintes gastam o nível inteiro sem repetir")
    void oBaralhoNaoRepeteEntreRodadas() {
        QuizCatalog.Baralho baralho = new QuizCatalog.Baralho(banco,
                new Random(11));
        int noNivel = QuizCatalog.quantasNoNivel(banco, 1);
        int rodadas = noNivel / POR_NIVEL.get(0);
        Set<QuizCatalog.Pergunta> vistas = new HashSet<>();
        for (int k = 0; k < rodadas; k++) {
            for (QuizCatalog.Pergunta pergunta : baralho.rodada(POR_NIVEL)) {
                if (pergunta.nivel() == 1) {
                    assertTrue(vistas.add(pergunta), "a pergunta \""
                            + pergunta.texto() + "\" voltou antes de o nível "
                            + "1 acabar, na rodada " + (k + 1) + " de "
                            + rodadas);
                }
            }
        }
        assertEquals(noNivel, vistas.size(), "as " + rodadas + " rodadas "
                + "tinham de gastar as " + noNivel + " perguntas do nível 1");
    }

    @Test
    @DisplayName("a rodada sobe do fácil para o difícil")
    void aRodadaSobeDeNivel() {
        QuizCatalog.Baralho baralho = new QuizCatalog.Baralho(banco,
                new Random(13));
        for (int rodada = 0; rodada < 200; rodada++) {
            int antes = 0;
            for (QuizCatalog.Pergunta pergunta : baralho.rodada(POR_NIVEL)) {
                assertTrue(pergunta.nivel() >= antes, "a rodada " + rodada
                        + " desceu de nível " + antes + " para "
                        + pergunta.nivel());
                antes = pergunta.nivel();
            }
            assertEquals(3, antes, "a rodada tinha de terminar no nível 3");
        }
    }

    @Test
    @DisplayName("a mesma semente dá as mesmas rodadas, e sementes diferentes não")
    void oBaralhoEhDeterminado() {
        List<QuizCatalog.Pergunta> uma = new QuizCatalog.Baralho(banco,
                new Random(99)).rodada(POR_NIVEL);
        List<QuizCatalog.Pergunta> outra = new QuizCatalog.Baralho(banco,
                new Random(99)).rodada(POR_NIVEL);
        assertEquals(uma, outra);

        Set<List<QuizCatalog.Pergunta>> distintas = new HashSet<>();
        for (int semente = 0; semente < 200; semente++) {
            distintas.add(new QuizCatalog.Baralho(banco, new Random(semente))
                    .rodada(POR_NIVEL));
        }
        assertTrue(distintas.size() >= 195, "só " + distintas.size()
                + " de 200 sementes deram rodadas diferentes");
    }

    // ------------------------------------------------------------------
    //  A arena
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a geometria do yml passa na conferência")
    void aGeometriaFecha() {
        assertNull(QuizBuilder.conferirNumeros(ATRIO_RAIO, TABLADOS,
                TABLADO_RAIO, TABLADO_ANEL, PONTE_LARGURA));
        assertNull(QuizBuilder.conferirCorrida(ATRIO_RAIO, TABLADO_ANEL,
                TABLADO_RAIO, TEMPO_RESPOSTA));
        assertNull(QuizBuilder.conferirRelogio(PERGUNTAS, POR_NIVEL,
                TEMPO_RESPOSTA, TEMPO_REVELACAO, QUEDA_TIQUES, TEMPO_LIMITE));
    }

    @Test
    @DisplayName("a conferência da geometria pega tablado encostado, ponte par e vão curto")
    void aConferenciaDaGeometriaPegaOsErros() {
        assertNotNull(QuizBuilder.conferirNumeros(ATRIO_RAIO, 1, TABLADO_RAIO,
                TABLADO_ANEL, PONTE_LARGURA));
        assertNotNull(QuizBuilder.conferirNumeros(ATRIO_RAIO, 5, TABLADO_RAIO,
                TABLADO_ANEL, PONTE_LARGURA));

        String par = QuizBuilder.conferirNumeros(ATRIO_RAIO, TABLADOS,
                TABLADO_RAIO, TABLADO_ANEL, 4);
        assertNotNull(par);
        assertTrue(par.contains("centrada no eixo"), par);

        String curto = QuizBuilder.conferirNumeros(ATRIO_RAIO, TABLADOS,
                TABLADO_RAIO, 12, PONTE_LARGURA);
        assertNotNull(curto);
        assertTrue(curto.contains("cair para o lado"), curto);

        String encostado = QuizBuilder.conferirNumeros(2, TABLADOS, 8, 13,
                PONTE_LARGURA);
        assertNotNull(encostado);
        assertTrue(encostado.contains("viram um tablado só"), encostado);

        String largaDemais = QuizBuilder.conferirNumeros(2, TABLADOS,
                TABLADO_RAIO, TABLADO_ANEL, 5);
        assertNotNull(largaDemais);
        assertTrue(largaDemais.contains("deixaria de ter beira"), largaDemais);
    }

    @Test
    @DisplayName("a pior travessia cabe na janela de resposta com folga")
    void aTravessiaCabeNaJanela() {
        double caminho = ATRIO_RAIO + TABLADO_ANEL - TABLADO_RAIO;
        double gasta = caminho / QuizBuilder.CORRIDA;
        assertTrue(gasta + 2.0 <= TEMPO_RESPOSTA, "a pior travessia é de "
                + caminho + " blocos e leva " + gasta + "s numa janela de "
                + TEMPO_RESPOSTA + "s");

        // E um anel largo demais é recusado antes de a obra sair.
        String longe = QuizBuilder.conferirCorrida(ATRIO_RAIO, 40,
                TABLADO_RAIO, TEMPO_RESPOSTA);
        assertNotNull(longe);
        assertTrue(longe.contains("pior travessia"), longe);
    }

    @Test
    @DisplayName("a conferência do relógio pega soma errada, queda atrasada e rodada longa")
    void aConferenciaDoRelogioPegaOsErros() {
        String soma = QuizBuilder.conferirRelogio(10, POR_NIVEL,
                TEMPO_RESPOSTA, TEMPO_REVELACAO, QUEDA_TIQUES, TEMPO_LIMITE);
        assertNotNull(soma);
        assertTrue(soma.contains("soma"), soma);

        String queda = QuizBuilder.conferirRelogio(PERGUNTAS, POR_NIVEL,
                TEMPO_RESPOSTA, TEMPO_REVELACAO, 100, TEMPO_LIMITE);
        assertNotNull(queda);
        assertTrue(queda.contains("já estar na tela"), queda);

        String longa = QuizBuilder.conferirRelogio(30, List.of(10, 10, 10),
                TEMPO_RESPOSTA, TEMPO_REVELACAO, QUEDA_TIQUES, TEMPO_LIMITE);
        assertNotNull(longa);
        assertTrue(longa.contains("nunca seriam feitas"), longa);
    }

    @Test
    @DisplayName("os quatro tablados ficam nos pontos cardeais")
    void osTabladosSaoCardeais() {
        int[][] esperados = {{0, -TABLADO_ANEL}, {TABLADO_ANEL, 0},
                {0, TABLADO_ANEL}, {-TABLADO_ANEL, 0}};
        for (int k = 0; k < TABLADOS; k++) {
            int[] centro = QuizCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            assertEquals(esperados[k][0], centro[0], "x do tablado "
                    + QuizCatalog.LETRAS[k]);
            assertEquals(esperados[k][1], centro[1], "z do tablado "
                    + QuizCatalog.LETRAS[k]);
        }
    }

    @Test
    @DisplayName("o átrio não é resposta nenhuma")
    void oAtrioNaoEhResposta() {
        assertEquals(-1, QuizCatalog.tabladoDoPonto(0, 0, TABLADOS,
                TABLADO_ANEL, TABLADO_RAIO), "o meio do átrio é um tablado");
        for (int x = -ATRIO_RAIO; x <= ATRIO_RAIO; x++) {
            for (int z = -ATRIO_RAIO; z <= ATRIO_RAIO; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, ATRIO_RAIO)) {
                    continue;
                }
                assertEquals(-1, QuizCatalog.tabladoDoPonto(x, z, TABLADOS,
                                TABLADO_ANEL, TABLADO_RAIO),
                        "o ponto do átrio (" + x + "," + z + ") conta como "
                                + "resposta — ficar no átrio tem de valer "
                                + "'sem resposta'");
            }
        }
    }

    @Test
    @DisplayName("todo ponto do meio de cada tablado é aquele tablado")
    void oTabladoDoPontoAcerta() {
        for (int k = 0; k < TABLADOS; k++) {
            int[] centro = QuizCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            assertEquals(k, QuizCatalog.tabladoDoPonto(centro[0], centro[1],
                    TABLADOS, TABLADO_ANEL, TABLADO_RAIO));
            for (int dx = -TABLADO_RAIO; dx <= TABLADO_RAIO; dx++) {
                for (int dz = -TABLADO_RAIO; dz <= TABLADO_RAIO; dz++) {
                    if (!ArenaComum.noDiscoMeio(dx, dz, TABLADO_RAIO)) {
                        continue;
                    }
                    assertEquals(k, QuizCatalog.tabladoDoPonto(centro[0] + dx,
                                    centro[1] + dz, TABLADOS, TABLADO_ANEL,
                                    TABLADO_RAIO),
                            "o ponto (" + dx + "," + dz + ") do tablado "
                                    + QuizCatalog.LETRAS[k] + " não é dele");
                }
            }
        }
    }

    @Test
    @DisplayName("a ponte liga o átrio ao tablado sem furo")
    void aPonteLigaSemFuro() {
        for (int k = 0; k < TABLADOS; k++) {
            int[] centro = QuizCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            double comprimento = Math.hypot(centro[0], centro[1]);
            double ux = centro[0] / comprimento;
            double uz = centro[1] / comprimento;
            for (double t = 0; t <= comprimento; t += 0.25) {
                int x = (int) Math.round(ux * t);
                int z = (int) Math.round(uz * t);
                boolean noAtrio = ArenaComum.noDiscoMeio(x, z, ATRIO_RAIO);
                boolean naPonte = QuizCatalog.naPonte(x, z, TABLADOS,
                        TABLADO_ANEL, PONTE_LARGURA);
                boolean noTablado = QuizCatalog.tabladoDoPonto(x, z, TABLADOS,
                        TABLADO_ANEL, TABLADO_RAIO) >= 0;
                assertTrue(noAtrio || naPonte || noTablado,
                        "o caminho até o tablado " + QuizCatalog.LETRAS[k]
                                + " tem um furo em (" + x + "," + z + ")");
            }
        }
    }

    @Test
    @DisplayName("o poste da placa fica fora do tablado, que some")
    void oPosteFicaFora() {
        for (int k = 0; k < TABLADOS; k++) {
            int[] poste = QuizCatalog.posteDoTablado(k, TABLADOS, TABLADO_ANEL,
                    TABLADO_RAIO);
            assertEquals(-1, QuizCatalog.tabladoDoPonto(poste[0], poste[1],
                            TABLADOS, TABLADO_ANEL, TABLADO_RAIO),
                    "o poste do tablado " + QuizCatalog.LETRAS[k]
                            + " nasce em cima de um tablado, e o tablado some");
            assertFalse(QuizCatalog.naPonte(poste[0], poste[1], TABLADOS,
                            TABLADO_ANEL, PONTE_LARGURA),
                    "o poste do tablado " + QuizCatalog.LETRAS[k]
                            + " nasce no meio de uma ponte");
            // E a placa olha para dentro, para ser lida por quem chega.
            org.bukkit.block.BlockFace frente =
                    QuizCatalog.frenteDaPlaca(poste[0], poste[1]);
            int[] centro = QuizCatalog.centroDoTablado(k, TABLADOS, TABLADO_ANEL);
            assertTrue(Math.hypot(poste[0] + frente.getModX() - centro[0],
                            poste[1] + frente.getModZ() - centro[1])
                            < Math.hypot(poste[0] - centro[0], poste[1] - centro[1]),
                    "a placa do tablado " + QuizCatalog.LETRAS[k]
                            + " olha para fora da arena");
        }
    }

    @Test
    @DisplayName("as dezesseis largadas cabem no átrio e não se repetem")
    void asLargadasCabemNoAtrio() {
        List<int[]> pontos = QuizCatalog.largadas(MAXIMO, ATRIO_RAIO);
        assertEquals(MAXIMO, pontos.size());
        Set<Long> vistos = new HashSet<>();
        for (int[] onde : pontos) {
            assertTrue(ArenaComum.noDiscoMeio(onde[0], onde[1], ATRIO_RAIO),
                    "a largada (" + onde[0] + "," + onde[1] + ") cai fora do "
                            + "átrio de raio " + ATRIO_RAIO);
            assertEquals(-1, QuizCatalog.tabladoDoPonto(onde[0], onde[1],
                            TABLADOS, TABLADO_ANEL, TABLADO_RAIO),
                    "a largada (" + onde[0] + "," + onde[1] + ") nasce já "
                            + "numa resposta");
            vistos.add(ArenaComum.chave(onde[0], 0, onde[1]));
        }
        // Com dezesseis jogadores num anel de raio quatro há repetição
        // inevitável, mas não mais que a metade.
        assertTrue(vistos.size() >= MAXIMO / 2, "só " + vistos.size()
                + " blocos distintos para " + MAXIMO + " largadas");
    }

    @Test
    @DisplayName("cada tablado tem uma cor, e a cor não escapa da lista")
    void asCoresSeguemOsTablados() {
        assertEquals(QuizCatalog.LETRAS.length, QuizCatalog.CORES.length);
        Set<String> distintas = new HashSet<>(List.of(QuizCatalog.CORES));
        assertEquals(QuizCatalog.CORES.length, distintas.size(),
                "duas letras com a mesma cor");
        assertEquals(QuizCatalog.CORES[0], QuizCatalog.corDaLetra(-5));
        assertEquals(QuizCatalog.CORES[QuizCatalog.CORES.length - 1],
                QuizCatalog.corDaLetra(99));
    }

    @Test
    @DisplayName("a rodada inteira cabe no tempo limite")
    void aRodadaCabeNoTempoLimite() {
        int precisa = PERGUNTAS * (TEMPO_RESPOSTA + TEMPO_REVELACAO);
        assertTrue(precisa <= TEMPO_LIMITE, "as " + PERGUNTAS
                + " perguntas levam " + precisa + "s e o limite é "
                + TEMPO_LIMITE + "s");
        // E sobra tempo de contagem e de fim.
        assertTrue(precisa >= TEMPO_LIMITE / 2, "a rodada só usa " + precisa
                + "s de " + TEMPO_LIMITE + ": o limite está folgado demais "
                + "para servir de freio");
        assertTrue(QUEDA_TIQUES < TEMPO_REVELACAO * 20);
    }

    @Test
    @DisplayName("a rapidez vale menos que o acerto, e mais que nada")
    void aRapidezPesaOCerto() {
        // É o preço de copiar: quem espera o vizinho ainda sobrevive à
        // pergunta, mas perde o bônus — e é o bônus que decide a partida
        // quando mais de um chega ao fim.
        assertTrue(PONTOS_RAPIDEZ > 0, "sem bônus de rapidez, copiar é grátis");
        assertTrue(PONTOS_RAPIDEZ <= PONTOS_ACERTO, "com a rapidez valendo "
                + "mais que o acerto, o jogo deixa de ser de conhecimento");
        int acertandoDevagar = PONTOS_ACERTO;
        int acertandoNaHora = PONTOS_ACERTO + PONTOS_RAPIDEZ;
        assertTrue(acertandoNaHora < acertandoDevagar * 3, "o rápido faria "
                + "tanto ponto que o segundo acerto do devagar não contaria");
    }

    // ------------------------------------------------------------------
    //  Ajudantes
    // ------------------------------------------------------------------

    private static QuizCatalog.Pergunta nova(int nivel, String texto, String a,
                                             String b, String c, String d,
                                             int certa) {
        return new QuizCatalog.Pergunta(nivel, texto, List.of(a, b, c, d), certa);
    }

    /** Confere que o banco é recusado e devolve o motivo. */
    private static String assertRecusa(List<QuizCatalog.Pergunta> banco) {
        String erro = QuizBuilder.conferirBanco(banco, List.of());
        assertNotNull(erro, "o banco tinha de ser recusado");
        return erro;
    }
}
