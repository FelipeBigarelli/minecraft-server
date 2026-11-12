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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas da SORTE, conferidas sem servidor.
 *
 * <h2>Os dois testes que valem mais aqui</h2>
 *
 * {@link #aHoraDeSairFicaNoMeioDaPartida} é o único teste do projeto que
 * prova que um jogo TEM uma decisão. A tabela de prêmios decide sozinha se o
 * banco do meio é jogo ou enfeite: com o prêmio subindo pouco, o certo é
 * guardar na primeira rodada e sair, e a partida é dezesseis pessoas pulando
 * no banco aos quinze segundos; com ele subindo muito, o certo é nunca sair,
 * e o jogo volta a ser moeda ao ar. É um defeito que não aparece em nenhuma
 * partida de teste, porque uma partida de teste não é jogada por quem fez a
 * conta — ela aparece na terceira semana, quando alguém faz.
 *
 * <p>{@link #oDadoEhJusto} parte o anel inteiro bloco por bloco e prova que
 * cada um pertence a exatamente uma fatia e que as seis têm quase o mesmo
 * tamanho. O erro que ele pega é o de o ângulo de uma fatia ficar maior que
 * o das outras por causa de um arredondamento — e aí uma face do dado é mais
 * fácil de ocupar, o que num jogo cuja única regra é "o dado é justo" é a
 * mentira mais cara possível.
 *
 * <h2>E o que prova que o desenho diz a verdade</h2>
 *
 * {@link #aFaceTemOsPingosQueDiz} e {@link #oPingoCabeNaFatia}: a face N tem
 * de ter N pontos, e os N pontos têm de caber dentro da fatia dela. O
 * construtor descarta o pingo que cairia na fatia vizinha — sem estes dois
 * testes, uma grade larga faria a face 6 aparecer no chão com quatro pontos,
 * e o jogador contaria os pontos para achar a fatia certa.
 */
class SorteTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 28;
    private static final int BANCO_RAIO = 7;
    private static final int SETORES = 6;
    private static final int PINGO_RAIO = 18;
    private static final int PINGO_PASSO = 2;
    private static final int MARCO_ALTURA = 16;
    private static final int RODADAS = 10;
    private static final int TEMPO_ESCOLHA = 8;
    private static final int TEMPO_REVELACAO = 5;
    private static final int QUEDA_TIQUES = 30;
    private static final int PREMIO_BASE = 8;
    private static final int PREMIO_PASSO = 2;
    private static final int BANCO_DEGRAU = 1;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 300;

    private static final List<Integer> PREMIOS =
            SorteCatalog.premios(RODADAS, PREMIO_BASE, PREMIO_PASSO);

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = SorteTest.class.getResourceAsStream("/sorte.yml")) {
            assertNotNull(in, "sorte.yml não foi empacotado");
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
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(BANCO_RAIO, arena.getInt("arena.banco-raio"));
        assertEquals(SETORES, arena.getInt("arena.setores"));
        assertEquals(PINGO_RAIO, arena.getInt("arena.pingo-raio"));
        assertEquals(PINGO_PASSO, arena.getInt("arena.pingo-passo"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(RODADAS, arena.getInt("regras.rodadas"));
        assertEquals(TEMPO_ESCOLHA, arena.getInt("regras.tempo-escolha"));
        assertEquals(TEMPO_REVELACAO, arena.getInt("regras.tempo-revelacao"));
        assertEquals(QUEDA_TIQUES, arena.getInt("regras.queda-tiques"));
        assertEquals(PREMIO_BASE, arena.getInt("regras.premio-base"));
        assertEquals(PREMIO_PASSO, arena.getInt("regras.premio-passo"));
        assertEquals(BANCO_DEGRAU, arena.getInt("regras.banco-degrau"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas três conferências")
    void aPlantaPassaNaConferencia() {
        assertNull(SorteBuilder.conferirNumeros(RAIO, BANCO_RAIO, SETORES,
                PINGO_RAIO, PINGO_PASSO));
        assertNull(SorteBuilder.conferirPremios(PREMIOS, SETORES));
        assertNull(SorteBuilder.conferirRelogio(RODADAS, TEMPO_ESCOLHA,
                TEMPO_REVELACAO, QUEDA_TIQUES, TEMPO_LIMITE));
    }

    // ------------------------------------------------------------------
    //  A decisão de quando sair
    // ------------------------------------------------------------------

    /**
     * O topo da curva de sair tem de ficar no MEIO da partida, e a curva tem
     * de ser chata em volta dele.
     *
     * Interior porque é a condição de o jogo ter duas decisões em vez de uma:
     * se o topo é a rodada 1, a jogada certa é guardar e sair; se é a última,
     * é nunca sair. Chata porque é a condição de a decisão ser do JOGADOR: com
     * a 7, a 8, a 9 e a 10 valendo quase o mesmo, a aritmética não manda, e
     * quem manda é quanto os outros já guardaram.
     */
    @Test
    @DisplayName("a melhor hora de sair fica no meio da partida, e a curva é chata")
    void aHoraDeSairFicaNoMeioDaPartida() {
        int topo = SorteCatalog.melhorHoraDeSair(PREMIOS, SETORES);
        assertTrue(topo > 1 && topo < RODADAS,
                "a melhor hora de sair é a rodada " + topo + " de " + RODADAS
                        + ": tinha de ficar no meio");
        assertEquals(8, topo, "com os prêmios de hoje o topo é a rodada 8");

        double noTopo = SorteCatalog.evDeSair(topo, PREMIOS, SETORES);
        for (int k = topo - 1; k <= RODADAS; k++) {
            double aqui = SorteCatalog.evDeSair(k, PREMIOS, SETORES);
            assertTrue(aqui >= noTopo * 0.97,
                    "sair na rodada " + k + " vale " + aqui + " contra "
                            + noTopo + " no topo: a curva ficou pontuda, e aí "
                            + "a conta decide por quem joga");
        }
        assertTrue(noTopo / SorteCatalog.evDeSair(1, PREMIOS, SETORES) > 3.0,
                "jogar até o topo tem de valer bem mais que sair na primeira");
    }

    @Test
    @DisplayName("a conferência recusa a tabela que manda sair na primeira rodada")
    void aConferenciaPegaOPremioFraco() {
        String erro = SorteBuilder.conferirPremios(
                List.of(100, 1, 1, 1), SETORES);
        assertNotNull(erro);
        assertTrue(erro.contains("rodada 1"), erro);
    }

    @Test
    @DisplayName("a conferência recusa a tabela que manda nunca sair")
    void aConferenciaPegaOPremioExplosivo() {
        String erro = SorteBuilder.conferirPremios(
                List.of(8, 16, 32, 64), SETORES);
        assertNotNull(erro);
        assertTrue(erro.contains("última"), erro);
    }

    @Test
    @DisplayName("a conferência recusa a tabela em que continuar não paga")
    void aConferenciaPegaOPremioSemGanho() {
        // Topo interior (rodada 2), mas valendo só 1,08 vez o de sair na
        // primeira: arriscar oito rodadas por oito por cento é burrice, e um
        // jogo em que a jogada certa é não jogar não tem tensão nenhuma.
        String erro = SorteBuilder.conferirPremios(
                List.of(10, 3, 2, 1), SETORES);
        assertNotNull(erro);
        assertTrue(erro.contains("vezes"), erro);
    }

    @Test
    @DisplayName("a conferência recusa prêmio zero e tabela vazia")
    void aConferenciaPegaOPremioVazio() {
        assertNotNull(SorteBuilder.conferirPremios(List.of(), SETORES));
        assertNotNull(SorteBuilder.conferirPremios(List.of(8, 0, 12), SETORES));
    }

    @Test
    @DisplayName("o valor de sair não passa do fim da tabela")
    void oValorDeSairNaoPassaDoFim() {
        assertEquals(SorteCatalog.evDeSair(RODADAS, PREMIOS, SETORES),
                SorteCatalog.evDeSair(99, PREMIOS, SETORES), 1e-9);
        assertEquals(0, SorteCatalog.evDeSair(0, PREMIOS, SETORES), 1e-9);
        assertEquals(0, SorteCatalog.evDeSair(3, List.of(), SETORES), 1e-9);
    }

    @Test
    @DisplayName("a tabela de prêmios sobe de passo em passo")
    void aTabelaDePremiosSobe() {
        assertEquals(RODADAS, PREMIOS.size());
        assertEquals(PREMIO_BASE, PREMIOS.get(0).intValue());
        for (int r = 1; r < PREMIOS.size(); r++) {
            assertEquals(PREMIO_PASSO,
                    PREMIOS.get(r) - PREMIOS.get(r - 1));
        }
    }

    // ------------------------------------------------------------------
    //  A divisão do prêmio
    // ------------------------------------------------------------------

    /**
     * A fatia cheia não paga nada, e é de propósito.
     *
     * Divisão inteira sem piso: com os dezesseis na mesma fatia de prêmio
     * oito, cada um leva zero. Um piso de um ponto pareceria mais gentil e
     * mataria o jogo — amontoar-se passaria a ser a jogada segura E lucrativa,
     * e a única decisão que o jogo tem deixaria de existir.
     */
    @Test
    @DisplayName("a fatia cheia não paga nada")
    void aFatiaCheiaNaoPagaNada() {
        assertEquals(0, SorteCatalog.divisao(PREMIO_BASE, MAXIMO));
        assertEquals(PREMIO_BASE, SorteCatalog.divisao(PREMIO_BASE, 1));
        assertEquals(4, SorteCatalog.divisao(PREMIO_BASE, 2));
        assertEquals(1, SorteCatalog.divisao(26, MAXIMO));
        assertEquals(0, SorteCatalog.divisao(PREMIO_BASE, 0));
    }

    @Test
    @DisplayName("a divisão nunca inventa pontos")
    void aDivisaoNuncaInventaPontos() {
        for (int premio : PREMIOS) {
            for (int quantos = 1; quantos <= MAXIMO; quantos++) {
                int cada = SorteCatalog.divisao(premio, quantos);
                assertTrue(cada * quantos <= premio,
                        "prêmio " + premio + " dividido por " + quantos
                                + " pagaria " + (cada * quantos));
                assertTrue(cada <= premio);
            }
        }
    }

    @Test
    @DisplayName("sozinho na fatia vale mais que acompanhado, sempre")
    void sozinhoValeMais() {
        for (int premio : PREMIOS) {
            for (int quantos = 1; quantos < MAXIMO; quantos++) {
                assertTrue(SorteCatalog.divisao(premio, quantos)
                                >= SorteCatalog.divisao(premio, quantos + 1),
                        "prêmio " + premio + " com " + quantos);
            }
        }
    }

    // ------------------------------------------------------------------
    //  O dado
    // ------------------------------------------------------------------

    /**
     * Cada bloco da coroa pertence a exatamente uma fatia, e as seis têm
     * quase o mesmo tamanho.
     */
    @Test
    @DisplayName("o dado é justo: a coroa se divide em seis partes iguais")
    void oDadoEhJusto() {
        int[] quantos = new int[SETORES];
        int naCoroa = 0;
        for (int x = -RAIO - 2; x <= RAIO + 2; x++) {
            for (int z = -RAIO - 2; z <= RAIO + 2; z++) {
                int setor = SorteCatalog.setorDoPonto(x, z, SETORES,
                        BANCO_RAIO, RAIO);
                double longe = Math.hypot(x, z);
                if (longe <= BANCO_RAIO + 0.5) {
                    assertEquals(SorteCatalog.BANCO, setor,
                            "(" + x + "," + z + ") está no banco");
                    continue;
                }
                if (longe > RAIO + 0.5) {
                    assertEquals(SorteCatalog.FORA, setor,
                            "(" + x + "," + z + ") está fora do disco");
                    continue;
                }
                assertTrue(setor >= 0 && setor < SETORES,
                        "(" + x + "," + z + ") caiu na fatia " + setor);
                quantos[setor]++;
                naCoroa++;
            }
        }
        assertTrue(naCoroa > 1000, "a coroa tem " + naCoroa + " blocos");

        // O limite é UMA FIADA de blocos de diferença para a média, e tem
        // razão de ser: duas das seis divisas caem exatamente sobre o eixo
        // leste-oeste, e a fiada de blocos em cima da divisa pertence inteira
        // a uma das duas fatias em vez de meia a meia. Esse é o erro de
        // quantização de um ângulo num chão de blocos, e ele não passa do
        // comprimento da divisa. Um erro de ÂNGULO — a fatia com sessenta e
        // cinco graus em vez de sessenta — move dezenas de blocos e não cabe
        // neste limite, que é exatamente o que o teste tem de pegar.
        double media = naCoroa / (double) SETORES;
        int fiada = RAIO - BANCO_RAIO;
        for (int setor = 0; setor < SETORES; setor++) {
            assertTrue(Math.abs(quantos[setor] - media) <= fiada,
                    "a fatia " + (setor + 1) + " tem " + quantos[setor]
                            + " blocos contra uma média de " + media
                            + ": passou de uma fiada de diferença, que é mais "
                            + "do que o chão de blocos explica");
        }
    }

    @Test
    @DisplayName("a fatia 1 fica ao norte e as outras seguem o relógio")
    void aFatiaUmFicaAoNorte() {
        assertEquals(0, SorteCatalog.setorDoPonto(0, -20, SETORES,
                BANCO_RAIO, RAIO));
        assertEquals(0, SorteCatalog.setorDoPonto(0, -RAIO, SETORES,
                BANCO_RAIO, RAIO));
        // No sentido do relógio visto de cima: norte, nordeste, sudeste...
        for (int setor = 0; setor < SETORES; setor++) {
            int[] meio = SorteCatalog.pontoDaFatia(setor, SETORES, 20);
            assertEquals(setor, SorteCatalog.setorDoPonto(meio[0], meio[1],
                            SETORES, BANCO_RAIO, RAIO),
                    "a bissetriz da fatia " + setor + " caiu em outra fatia");
        }
        int[] segunda = SorteCatalog.pontoDaFatia(1, SETORES, 20);
        assertTrue(segunda[0] > 0 && segunda[1] < 0,
                "a fatia 2 tinha de ficar a nordeste da 1");
    }

    @Test
    @DisplayName("o banco é o meio e o vazio é o lado de fora")
    void oBancoEhOMeio() {
        assertEquals(SorteCatalog.BANCO, SorteCatalog.setorDoPonto(0, 0,
                SETORES, BANCO_RAIO, RAIO));
        assertEquals(SorteCatalog.BANCO, SorteCatalog.setorDoPonto(0,
                -BANCO_RAIO, SETORES, BANCO_RAIO, RAIO));
        assertEquals(SorteCatalog.FORA, SorteCatalog.setorDoPonto(0,
                -RAIO - 2, SETORES, BANCO_RAIO, RAIO));
        assertTrue(SorteCatalog.setorDoPonto(0, -BANCO_RAIO - 2, SETORES,
                BANCO_RAIO, RAIO) >= 0);
    }

    // ------------------------------------------------------------------
    //  Os pingos da face
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a face N tem N pingos desenhados")
    void aFaceTemOsPingosQueDiz() {
        assertEquals(6, SorteCatalog.PINGOS.length);
        for (int face = 1; face <= SorteCatalog.PINGOS.length; face++) {
            assertEquals(face, Integer.bitCount(SorteCatalog.PINGOS[face - 1]),
                    "a face " + face + " está desenhada com outro número de "
                            + "pingos");
            int quantos = 0;
            for (int coluna = 0; coluna < 3; coluna++) {
                for (int fiada = 0; fiada < 3; fiada++) {
                    if (SorteCatalog.temPingo(face, coluna, fiada)) {
                        quantos++;
                    }
                }
            }
            assertEquals(face, quantos, "a face " + face);
        }
    }

    @Test
    @DisplayName("os pingos de uma face caem todos dentro da fatia dela")
    void oPingoCabeNaFatia() {
        for (int setor = 0; setor < SETORES; setor++) {
            int[] meio = SorteCatalog.pontoDaFatia(setor, SETORES, PINGO_RAIO);
            int desenhados = 0;
            for (int coluna = 0; coluna < 3; coluna++) {
                for (int fiada = 0; fiada < 3; fiada++) {
                    if (!SorteCatalog.temPingo(setor + 1, coluna, fiada)) {
                        continue;
                    }
                    int x = meio[0] + (coluna - 1) * PINGO_PASSO;
                    int z = meio[1] + (fiada - 1) * PINGO_PASSO;
                    assertEquals(setor, SorteCatalog.setorDoPonto(x, z,
                                    SETORES, BANCO_RAIO, RAIO),
                            "o pingo (" + coluna + "," + fiada + ") da face "
                                    + (setor + 1) + " caiu fora da fatia "
                                    + "dela, e o construtor o descartaria");
                    desenhados++;
                }
            }
            assertEquals(setor + 1, desenhados);
        }
    }

    @Test
    @DisplayName("pingo fora da grade não existe")
    void oPingoForaDaGradeNaoExiste() {
        assertFalse(SorteCatalog.temPingo(0, 1, 1));
        assertFalse(SorteCatalog.temPingo(7, 1, 1));
        assertFalse(SorteCatalog.temPingo(1, 3, 1));
        assertFalse(SorteCatalog.temPingo(1, 1, -1));
        assertTrue(SorteCatalog.temPingo(1, 1, 1), "a face 1 tem o pingo do meio");
    }

    // ------------------------------------------------------------------
    //  A arena
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o poste da fatia fica fora do disco")
    void oPosteFicaFora() {
        for (int setor = 0; setor < SETORES; setor++) {
            int[] onde = SorteCatalog.pontoDaFatia(setor, SETORES, RAIO + 2);
            assertTrue(Math.hypot(onde[0], onde[1]) > RAIO,
                    "o poste da fatia " + setor + " caiu dentro do disco");
            assertEquals(SorteCatalog.FORA, SorteCatalog.setorDoPonto(onde[0],
                            onde[1], SETORES, BANCO_RAIO, RAIO),
                    "o poste da fatia " + setor + " caiu numa fatia");
        }
    }

    @Test
    @DisplayName("as largadas caem em fatias, e espalhadas por todas elas")
    void asLargadasCaemEmFatias() {
        List<int[]> onde = SorteCatalog.largadas(MAXIMO, BANCO_RAIO, RAIO);
        assertEquals(MAXIMO, onde.size());
        Set<Integer> fatias = new HashSet<>();
        Set<String> repetidas = new HashSet<>();
        for (int[] ponto : onde) {
            int setor = SorteCatalog.setorDoPonto(ponto[0], ponto[1], SETORES,
                    BANCO_RAIO, RAIO);
            assertTrue(setor >= 0, "uma largada caiu em " + setor);
            fatias.add(setor);
            assertTrue(repetidas.add(ponto[0] + ":" + ponto[1]),
                    "duas largadas no mesmo bloco: " + ponto[0] + ","
                            + ponto[1]);
        }
        assertEquals(SETORES, fatias.size(),
                "as largadas tinham de cobrir as seis fatias, e cobriram "
                        + fatias.size());
    }

    @Test
    @DisplayName("a conferência pega a coroa estreita e a grade que não cabe")
    void aConferenciaPegaAGeometria() {
        // Coroa de sete blocos: não cabe a grade de pingos nem a corrida de
        // quem troca de fatia nos últimos segundos.
        assertNotNull(SorteBuilder.conferirNumeros(14, BANCO_RAIO, SETORES,
                10, PINGO_PASSO));
        // Grade de pingos encostando no banco.
        assertNotNull(SorteBuilder.conferirNumeros(RAIO, BANCO_RAIO, SETORES,
                8, PINGO_PASSO));
        // Fatia estreita no raio do pingo: os pingos invadem a vizinha.
        assertNotNull(SorteBuilder.conferirNumeros(20, 3, SETORES, 6,
                PINGO_PASSO));
        // Mais faces do que há desenho de pingo.
        assertNotNull(SorteBuilder.conferirNumeros(RAIO, BANCO_RAIO, 7,
                PINGO_RAIO, PINGO_PASSO));
        // Uma face só não é dado.
        assertNotNull(SorteBuilder.conferirNumeros(RAIO, BANCO_RAIO, 1,
                PINGO_RAIO, PINGO_PASSO));
    }

    @Test
    @DisplayName("a conferência pega a queda mais longa que a revelação")
    void aConferenciaPegaAQuedaLonga() {
        String erro = SorteBuilder.conferirRelogio(RODADAS, TEMPO_ESCOLHA,
                TEMPO_REVELACAO, TEMPO_REVELACAO * 20, TEMPO_LIMITE);
        assertNotNull(erro);
        assertTrue(erro.contains("queda"), erro);
    }

    @Test
    @DisplayName("a conferência pega as rodadas que não cabem no relógio")
    void aConferenciaPegaORelogioCurto() {
        String erro = SorteBuilder.conferirRelogio(30, TEMPO_ESCOLHA,
                TEMPO_REVELACAO, QUEDA_TIQUES, TEMPO_LIMITE);
        assertNotNull(erro);
        assertTrue(erro.contains("tempo limite"), erro);
    }

    @Test
    @DisplayName("a placa de cada fatia olha para o meio do disco")
    void aPlacaOlhaParaODentro() {
        List<String> vistas = new ArrayList<>();
        for (int setor = 0; setor < SETORES; setor++) {
            int[] onde = SorteCatalog.pontoDaFatia(setor, SETORES, RAIO + 2);
            var frente = SorteCatalog.frenteDaPlaca(onde[0], onde[1]);
            int x = onde[0] + frente.getModX();
            int z = onde[1] + frente.getModZ();
            assertTrue(Math.hypot(x, z) < Math.hypot(onde[0], onde[1]),
                    "a placa da fatia " + setor + " olha para fora");
            vistas.add(frente.name());
        }
        assertEquals(SETORES, vistas.size());
    }
}
