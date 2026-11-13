package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas da ARENA PVP, conferidas sem servidor.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * {@link #aCarenciaDoMinecraftSeriaRecusada} é o teste que justifica o jogo
 * existir diferente de vanilla. O Minecraft deixa o alvo invulnerável por 10
 * tiques e a espada recarrega em 12,5 — e nessa folga bater adiantado rende
 * 87% do dano de bater no tempo certo. 87% é o número mais perigoso do PvP de
 * Minecraft, porque "quase igual" é igual na prática: o jogo passa a premiar
 * clique rápido, e quem aprendeu o tempo da espada não ganha nada por isso.
 * O teste roda a conta com a carência de vanilla e exige que
 * {@link ArenaPvpBuilder#conferirGolpe} RECUSE.
 *
 * <p>{@link #martelarNuncaAcumulaProntidao} é o irmão dele e guarda a parte
 * da regra que não é óbvia: todo golpe reinicia a recarga, inclusive o golpe
 * que a carência comeu. Quem bate a cada 2 tiques e quem bate a cada 10
 * acertam o MESMO número de golpes — um por carência — e o primeiro faz um
 * terço do dano do segundo. Sem isso, martelar seria só desperdício de
 * clique; com isso, é desperdício de luta.
 *
 * <h2>E o que prova que a arena é de espada</h2>
 *
 * {@link #aBeiraFechaEAbreOBastante}: o parapeito é o que impede um golpe
 * limpo de jogar alguém no vazio em qualquer lugar do disco. Sem ele a vida
 * seria enfeite e o jogo seria de empurrão, que o Rei da Montanha já é; com a
 * beira toda fechada o vazio não existiria e o jogo seria só aritmética.
 */
class ArenaPvpTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 20;
    private static final int PALANQUE_RAIO = 6;
    private static final int PALANQUE_ALTURA = 2;
    private static final int PALANQUE_DEGRAU = 3;
    private static final int PILAR_ANEL = 13;
    private static final int PILAR_ALTURA = 3;
    private static final int VAOS = 4;
    private static final int VAO_LARGURA = 9;
    private static final int MARCO_ALTURA = 14;

    private static final int PERIODO = 13;
    private static final int VIDA_CHEIA = 120;
    private static final int DANO = 20;
    private static final int CURA = 40;
    private static final int GRACA = 3;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 240;

    /** A carência que o Minecraft usa: 10 tiques de invulnerabilidade. */
    private static final int CARENCIA_DO_MINECRAFT = 10;

    private static int anelDaLargada() {
        return Math.max(PALANQUE_RAIO + 2, RAIO - 4);
    }

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = ArenaPvpTest.class
                .getResourceAsStream("/arena-pvp.yml")) {
            assertNotNull(in, "arena-pvp.yml não foi empacotado");
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
        assertEquals(PALANQUE_RAIO, arena.getInt("arena.palanque-raio"));
        assertEquals(PALANQUE_ALTURA, arena.getInt("arena.palanque-altura"));
        assertEquals(PALANQUE_DEGRAU, arena.getInt("arena.palanque-degrau"));
        assertEquals(PILAR_ANEL, arena.getInt("arena.pilar-anel"));
        assertEquals(PILAR_ALTURA, arena.getInt("arena.pilar-altura"));
        assertEquals(VAOS, arena.getInt("arena.vaos"));
        assertEquals(VAO_LARGURA, arena.getInt("arena.vao-largura"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(PERIODO, arena.getInt("regras.periodo-do-golpe"));
        assertEquals(VIDA_CHEIA, arena.getInt("regras.vida-cheia"));
        assertEquals(DANO, arena.getInt("regras.dano-por-golpe"));
        assertEquals(CURA, arena.getInt("regras.cura-por-eliminacao"));
        assertEquals(GRACA, arena.getInt("regras.graca-segundos"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas três conferências")
    void aPlantaPassaNaConferencia() {
        assertNull(ArenaPvpBuilder.conferirGolpe(PERIODO, PERIODO, VIDA_CHEIA,
                DANO, CURA));
        assertNull(ArenaPvpBuilder.conferirArena(RAIO, PALANQUE_RAIO,
                PALANQUE_ALTURA, PALANQUE_DEGRAU, PILAR_ANEL, PILAR_ALTURA,
                VAOS, VAO_LARGURA));
        assertNull(ArenaPvpBuilder.conferirRelogio(GRACA, TEMPO_LIMITE,
                anelDaLargada(), PALANQUE_RAIO, MAXIMO, VIDA_CHEIA, DANO,
                PERIODO));
    }

    @Test
    @DisplayName("as regras escritas avisam que martelar não compensa")
    void asRegrasAvisamQueMartelarNaoCompensa() {
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 3, "o painel tem de ter regras");
        String tudo = String.join(" ", regras).toLowerCase();
        assertTrue(tudo.contains("martelar") || tudo.contains("martela"),
                "o painel não avisa que martelar o botão rende menos, e essa "
                        + "é a regra que o jogador não descobre jogando: ele "
                        + "martela, vê o alvo tomando dano e conclui que "
                        + "funciona. Regras: " + regras);
        assertTrue(tudo.contains("prontidão") || tudo.contains("carregar"),
                "o painel não diz que o golpe vale pela recarga da espada: "
                        + regras);
        assertTrue(tudo.contains("vão") || tudo.contains("vazio"),
                "o painel não avisa dos vãos da beira: " + regras);
    }

    // ------------------------------------------------------------------
    //  A conta do golpe
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a força do golpe é a curva do Minecraft, não o tempo")
    void aForcaDoGolpeNaoEhOTempo() {
        assertEquals(0.2, ArenaPvpCatalog.forcaDoGolpe(0.0), 1e-9,
                "golpe sem recarga nenhuma faz um quinto do dano");
        assertEquals(1.0, ArenaPvpCatalog.forcaDoGolpe(1.0), 1e-9,
                "golpe cheio faz o dano cheio");
        assertEquals(0.4, ArenaPvpCatalog.forcaDoGolpe(0.5), 1e-9,
                "com meia recarga o golpe faz 40% do dano, e não 50%: a "
                        + "curva é quadrática, e quem escrever a regra em "
                        + "\"metade do tempo, metade do dano\" acerta por "
                        + "acaso e erra quando mexer no número");
        assertTrue(ArenaPvpCatalog.forcaDoGolpe(-1) >= 0.2,
                "prontidão negativa não pode virar dano negativo");
        assertTrue(ArenaPvpCatalog.forcaDoGolpe(5) <= 1.0,
                "prontidão acima de um não pode passar do dano cheio");
    }

    @Test
    @DisplayName("a prontidão é o tempo dividido pelo período da espada")
    void aProntidaoEhOTempoPeloPeriodo() {
        assertEquals(0.0, ArenaPvpCatalog.prontidaoDe(0, PERIODO), 1e-9);
        assertEquals(1.0, ArenaPvpCatalog.prontidaoDe(PERIODO, PERIODO), 1e-9);
        assertEquals(1.0, ArenaPvpCatalog.prontidaoDe(PERIODO * 3, PERIODO),
                1e-9, "esperar mais que o período não dá bônus nenhum");
        assertEquals(0.5, ArenaPvpCatalog.prontidaoDe(6, 12), 1e-9);
    }

    @Test
    @DisplayName("o melhor intervalo entre golpes é o período da espada")
    void oMelhorIntervaloEhOPeriodoDaEspada() {
        int melhor = ArenaPvpCatalog.melhorIntervalo(PERIODO, PERIODO,
                4 * PERIODO);
        assertEquals(PERIODO, melhor, "com a carência igual ao período, o "
                + "ótimo tem de cair exatamente no período: é isso que faz "
                + "do tempo do golpe a habilidade do jogo");

        double otimo = ArenaPvpCatalog.danoPorSegundo(PERIODO, PERIODO,
                PERIODO, DANO);
        for (int i = 1; i <= 4 * PERIODO; i++) {
            if (i == PERIODO) {
                continue;
            }
            double outro = ArenaPvpCatalog.danoPorSegundo(i, PERIODO, PERIODO,
                    DANO);
            assertTrue(outro < otimo, "bater a cada " + i + " tiques rende "
                    + Math.round(outro) + " por segundo e bater no tempo "
                    + "rende " + Math.round(otimo) + ": o ótimo tem de ser "
                    + "único, senão há dois jeitos de jogar igualmente bons e "
                    + "um deles não exige saber nada");
        }
    }

    @Test
    @DisplayName("martelar o botão rende um terço de bater no tempo")
    void martelarRendeUmTerco() {
        double otimo = ArenaPvpCatalog.danoPorSegundo(PERIODO, PERIODO,
                PERIODO, DANO);
        double martelo = 0;
        for (int i = 1; i <= PERIODO / 2; i++) {
            martelo = Math.max(martelo, ArenaPvpCatalog.danoPorSegundo(i,
                    PERIODO, PERIODO, DANO));
        }
        double parte = martelo / otimo;
        assertTrue(parte < 0.45, "martelando sai " + Math.round(parte * 100)
                + "% do dano de bater no tempo, e isso é alto demais para o "
                + "jogo ser sobre o tempo do golpe");
        assertTrue(parte > 0.2, "martelando sai só " + Math.round(parte * 100)
                + "% do dano: tão pouco que quem errar o tempo não luta, e o "
                + "jogo deixa de ser jogável para quem está aprendendo");
    }

    /**
     * O defeito que esta arena existe para não ter.
     *
     * Com a carência de 10 tiques do Minecraft, bater três tiques adiantado
     * rende 87% do ótimo. Oitenta e sete por cento é o número perigoso: é
     * "quase igual", e quase igual é igual na prática — o jogo passa a
     * premiar clique rápido e quem aprendeu o tempo da espada não ganha nada.
     */
    @Test
    @DisplayName("a carência do Minecraft seria recusada pela conferência")
    void aCarenciaDoMinecraftSeriaRecusada() {
        double otimo = ArenaPvpCatalog.danoPorSegundo(PERIODO, PERIODO,
                CARENCIA_DO_MINECRAFT, DANO);
        double adiantado = ArenaPvpCatalog.danoPorSegundo(
                CARENCIA_DO_MINECRAFT, PERIODO, CARENCIA_DO_MINECRAFT, DANO);
        double parte = adiantado / otimo;
        assertTrue(parte > 0.8, "com a carência de " + CARENCIA_DO_MINECRAFT
                + " tiques do Minecraft, bater adiantado rende "
                + Math.round(parte * 100) + "% do ótimo — se esse número "
                + "tivesse caído abaixo de 80%, a carência do próprio jogo "
                + "serviria e esta arena não precisaria mexer nela");

        assertNotNull(ArenaPvpBuilder.conferirGolpe(PERIODO,
                        CARENCIA_DO_MINECRAFT, VIDA_CHEIA, DANO, CURA),
                "a conferência tem de RECUSAR a carência de vanilla: é o "
                        + "arranjo em que clicar rápido é quase tão bom "
                        + "quanto saber lutar");

        double nossa = ArenaPvpCatalog.danoPorSegundo(PERIODO - 3, PERIODO,
                PERIODO, DANO)
                / ArenaPvpCatalog.danoPorSegundo(PERIODO, PERIODO, PERIODO,
                        DANO);
        assertTrue(nossa < 0.8, "com a carência igual ao período, adiantar "
                + "três tiques tem de render menos de 80%, e rendeu "
                + Math.round(nossa * 100) + "%");
    }

    /**
     * A regra que o jogador não descobre jogando.
     *
     * Todo golpe reinicia a recarga, inclusive o que a carência comeu. Então
     * quem bate a cada 2 tiques e quem bate a cada 10 acertam o mesmo número
     * de golpes — um por carência — e o primeiro faz um terço do dano.
     */
    @Test
    @DisplayName("martelar nunca acumula prontidão")
    void martelarNuncaAcumulaProntidao() {
        double rapido = ArenaPvpCatalog.danoPorSegundo(2, PERIODO, PERIODO,
                DANO);
        double calmo = ArenaPvpCatalog.danoPorSegundo(10, PERIODO, PERIODO,
                DANO);
        assertTrue(rapido < calmo / 2.5, "bater a cada 2 tiques e a cada 10 "
                + "acerta o mesmo número de golpes, porque a carência é de "
                + PERIODO + " tiques nos dois casos — mas o de 2 tiques faz "
                + Math.round(rapido) + " por segundo e o de 10 faz "
                + Math.round(calmo) + ", porque cada golpe reinicia a "
                + "recarga e quem martela bate sempre descarregado");

        // E o dano de um golpe só confirma a mesma coisa num número que o
        // jogador vê na tela.
        assertEquals(4, ArenaPvpCatalog.danoDoGolpe(
                        ArenaPvpCatalog.prontidaoDe(2, PERIODO), DANO),
                "o golpe martelado tira 4 de " + DANO);
        assertEquals(DANO, ArenaPvpCatalog.danoDoGolpe(1.0, DANO),
                "o golpe cheio tira o dano cheio");
    }

    @Test
    @DisplayName("o empurrão segue a prontidão do golpe")
    void oEmpurraoSegueAProntidao() {
        double cheio = ArenaPvpCatalog.empurraoDoGolpe(1.0, 0.55);
        double fraco = ArenaPvpCatalog.empurraoDoGolpe(0.1, 0.55);
        assertEquals(0.55, cheio, 1e-9);
        assertTrue(fraco < cheio / 5, "se o golpe fraco empurrasse igual, "
                + "martelar voltaria a ser jogada — não para fazer dano, mas "
                + "para jogar alguém num vão");
    }

    @Test
    @DisplayName("a conferência do golpe recusa vida de cara ou coroa e moagem")
    void aConferenciaRecusaVidaErrada() {
        assertNotNull(ArenaPvpBuilder.conferirGolpe(PERIODO, PERIODO, 40, DANO,
                12), "dois golpes cheios é cara ou coroa de quem bateu "
                + "primeiro");
        assertNotNull(ArenaPvpBuilder.conferirGolpe(PERIODO, PERIODO, 400,
                DANO, 100), "vinte golpes cheios é moagem, e quem está "
                + "perdendo tem tempo de fugir sempre");
    }

    @Test
    @DisplayName("a conferência do golpe recusa cura fraca e cura demais")
    void aConferenciaRecusaCuraErrada() {
        assertNotNull(ArenaPvpBuilder.conferirGolpe(PERIODO, PERIODO,
                        VIDA_CHEIA, DANO, 0),
                "sem cura, quem ganha uma briga é comida de quem assistiu e "
                        + "a jogada certa passa a ser nunca brigar");
        assertNotNull(ArenaPvpBuilder.conferirGolpe(PERIODO, PERIODO,
                        VIDA_CHEIA, DANO, VIDA_CHEIA),
                "com cura cheia, o primeiro a derrubar alguém limpa a arena "
                        + "sozinho");
    }

    @Test
    @DisplayName("um duelo cabe no relógio, e a chave inteira também")
    void umDueloCabeNoRelogio() {
        double duelo = ArenaPvpCatalog.segundosDeUmDuelo(VIDA_CHEIA, DANO,
                PERIODO, PERIODO);
        assertTrue(duelo > 2.5 && duelo < 8, "um duelo sem parar leva "
                + Math.round(duelo * 10) / 10.0 + "s, e fora de dois e meio a "
                + "oito segundos ele é cara ou coroa ou é moagem");
        assertTrue(TEMPO_LIMITE > duelo * (MAXIMO - 1) * 1.5,
                "o limite de " + TEMPO_LIMITE + "s tem de sobrar sobre os "
                        + Math.round(duelo * (MAXIMO - 1)) + "s de pancada "
                        + "sem parar de uma arena cheia, porque ninguém luta "
                        + "sem parar: metade do tempo é corrida");
    }

    // ------------------------------------------------------------------
    //  A arena
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a beira fecha e abre o bastante")
    void aBeiraFechaEAbreOBastante() {
        int beira = ArenaPvpCatalog.blocosDaBeira(RAIO);
        int abertos = ArenaPvpCatalog.blocosAbertos(RAIO, VAOS, VAO_LARGURA);
        assertTrue(beira > 100, "o anel da beira de um disco de raio " + RAIO
                + " tem " + beira + " blocos");
        double parte = abertos / (double) beira;
        assertTrue(parte >= 0.10 && parte <= 0.35, "os vãos abrem "
                + Math.round(parte * 1000) / 10.0 + "% da beira: fora de 10% "
                + "a 35% o jogo deixa de ser de espada — fechado demais e o "
                + "vazio não existe, aberto demais e a vida é enfeite");

        // Os vãos são quatro buracos e não uma renda: cada um tem de ser
        // contíguo o bastante para caber alguém sendo empurrado para dentro.
        assertTrue(abertos / VAOS >= 3, "cada vão tem " + (abertos / VAOS)
                + " blocos de boca, e com menos de três ninguém cai neles "
                + "sem mirar");
    }

    @Test
    @DisplayName("nenhum pilar cai na boca de um vão")
    void nenhumPilarCaiNumVao() {
        List<int[]> pilares = ArenaPvpCatalog.pontosDosPilares(PILAR_ANEL);
        assertEquals(4, pilares.size());
        for (int[] onde : pilares) {
            assertTrue(!ArenaPvpCatalog.noVao(onde[0], onde[1], VAOS,
                            VAO_LARGURA, RAIO),
                    "o pilar em " + onde[0] + "," + onde[1] + " cai num vão: "
                            + "cobertura justamente onde o jogo não quer "
                            + "ninguém parado");
            double longe = Math.hypot(onde[0], onde[1]);
            assertTrue(longe > PALANQUE_RAIO + 4, "o pilar está a "
                    + Math.round(longe) + " do meio e o palanque tem raio "
                    + PALANQUE_RAIO);
            assertTrue(longe < RAIO - 3, "o pilar está a " + Math.round(longe)
                    + " do meio num disco de " + RAIO + ": encostado no "
                    + "parapeito ele é cobertura de graça");
        }
    }

    /**
     * O palanque tem de ser subido andando.
     *
     * Num jogo em que o golpe empurra, terreno alto que só se alcança pulando
     * é terreno de quem já está em cima: quem está embaixo precisa de um
     * pulo, e um pulo no ar é um alvo parado. Cada degrau sobe uma fiada só, e
     * entre blocos vizinhos a altura nunca muda mais que um.
     */
    @Test
    @DisplayName("o palanque sobe andando, degrau por degrau")
    void oPalanqueSobeAndando() {
        int topo = ArenaPvpCatalog.alturaDoPalanque(0, 0, PALANQUE_RAIO,
                PALANQUE_ALTURA, PALANQUE_DEGRAU);
        assertEquals(PALANQUE_ALTURA, topo, "o meio do palanque é o topo");
        assertEquals(0, ArenaPvpCatalog.alturaDoPalanque(PALANQUE_RAIO + 2, 0,
                        PALANQUE_RAIO, PALANQUE_ALTURA, PALANQUE_DEGRAU),
                "fora do palanque o chão é o chão");

        for (int x = -RAIO; x <= RAIO; x++) {
            for (int z = -RAIO; z <= RAIO; z++) {
                int aqui = ArenaPvpCatalog.alturaDoPalanque(x, z,
                        PALANQUE_RAIO, PALANQUE_ALTURA, PALANQUE_DEGRAU);
                int[][] vizinhos = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] passo : vizinhos) {
                    int lado = ArenaPvpCatalog.alturaDoPalanque(x + passo[0],
                            z + passo[1], PALANQUE_RAIO, PALANQUE_ALTURA,
                            PALANQUE_DEGRAU);
                    assertTrue(Math.abs(aqui - lado) <= 1, "em " + x + ","
                            + z + " o palanque tem " + aqui + " fiadas e no "
                            + "bloco do lado tem " + lado + ": esse degrau "
                            + "precisa de pulo, e pulo é alvo parado");
                }
            }
        }
    }

    @Test
    @DisplayName("as largadas ficam no chão firme, longe do palanque e da beira")
    void asLargadasFicamNoChaoFirme() {
        int anel = anelDaLargada();
        for (int quantos = 2; quantos <= MAXIMO; quantos++) {
            List<int[]> pontos = ArenaPvpCatalog.largadas(quantos, anel);
            assertEquals(Math.max(2, quantos), pontos.size());
            for (int[] onde : pontos) {
                double longe = Math.hypot(onde[0], onde[1]);
                assertTrue(longe > PALANQUE_RAIO + 1, "com " + quantos
                        + " jogadores, uma largada caiu a " + Math.round(longe)
                        + " do meio, dentro do palanque (raio " + PALANQUE_RAIO
                        + "): ninguém nasce no terreno alto");
                assertTrue(!ArenaPvpCatalog.noAnelDaBeira(onde[0], onde[1],
                                RAIO),
                        "com " + quantos + " jogadores, uma largada caiu no "
                                + "anel da beira, e ali pode não haver chão");
                assertTrue(ArenaComum.noDiscoMeio(onde[0], onde[1], RAIO - 1),
                        "com " + quantos + " jogadores, uma largada caiu fora "
                                + "do disco: nasceria no vazio");
            }
        }
    }

    @Test
    @DisplayName("a conferência da arena recusa as duas beiras erradas")
    void aConferenciaRecusaBeiraErrada() {
        assertNotNull(ArenaPvpBuilder.conferirArena(RAIO, PALANQUE_RAIO,
                        PALANQUE_ALTURA, PALANQUE_DEGRAU, PILAR_ANEL,
                        PILAR_ALTURA, 0, VAO_LARGURA),
                "sem vão nenhum a beira é toda parapeito, o vazio não existe "
                        + "e o jogo é só a conta do golpe");
        assertNotNull(ArenaPvpBuilder.conferirArena(RAIO, PALANQUE_RAIO,
                        PALANQUE_ALTURA, PALANQUE_DEGRAU, PILAR_ANEL,
                        PILAR_ALTURA, VAOS, RAIO),
                "com vãos do tamanho do raio a beira fica quase toda aberta, "
                        + "a vida é enfeite e o jogo passa a ser de empurrão");
        assertNotNull(ArenaPvpBuilder.conferirArena(RAIO, PALANQUE_RAIO,
                        PALANQUE_ALTURA, PALANQUE_DEGRAU, RAIO - 1,
                        PILAR_ALTURA, VAOS, VAO_LARGURA),
                "pilar encostado no parapeito é cobertura de graça para quem "
                        + "luta na beira");
        assertNotNull(ArenaPvpBuilder.conferirArena(RAIO, RAIO - 2,
                        PALANQUE_ALTURA, PALANQUE_DEGRAU, PILAR_ANEL,
                        PILAR_ALTURA, VAOS, VAO_LARGURA),
                "palanque do tamanho do disco não é palanque, é a arena");
    }

    @Test
    @DisplayName("o chão e a beira têm os blocos que a obra espera")
    void oChaoTemOsBlocos() {
        int chao = ArenaPvpCatalog.blocosDoChao(RAIO);
        assertTrue(chao > 1000 && chao < 1500, "o disco de raio " + RAIO
                + " tem " + chao + " blocos de chão");
        for (int x = -RAIO; x <= RAIO; x++) {
            for (int z = -RAIO; z <= RAIO; z++) {
                boolean naBeira = ArenaPvpCatalog.noAnelDaBeira(x, z, RAIO);
                if (naBeira) {
                    assertTrue(ArenaComum.noDiscoMeio(x, z, RAIO),
                            "um bloco da beira tem de estar no disco");
                    assertTrue(!ArenaComum.noDiscoMeio(x, z, RAIO - 1),
                            "um bloco da beira não pode estar no disco de "
                                    + "dentro");
                }
            }
        }
    }

    @Test
    @DisplayName("a conferência do relógio recusa graça de menos e de mais")
    void aConferenciaRecusaGracaErrada() {
        assertNotNull(ArenaPvpBuilder.conferirRelogio(0, TEMPO_LIMITE,
                        anelDaLargada(), PALANQUE_RAIO, MAXIMO, VIDA_CHEIA,
                        DANO, PERIODO),
                "sem graça, largar dezesseis pessoas no mesmo anel faz "
                        + "metade levar golpe antes de ver onde está");
        assertNotNull(ArenaPvpBuilder.conferirRelogio(60, TEMPO_LIMITE,
                        anelDaLargada(), PALANQUE_RAIO, MAXIMO, VIDA_CHEIA,
                        DANO, PERIODO),
                "espada que não corta por um quarto da partida vira desfile");
        assertNotNull(ArenaPvpBuilder.conferirRelogio(GRACA, 30,
                        anelDaLargada(), PALANQUE_RAIO, MAXIMO, VIDA_CHEIA,
                        DANO, PERIODO),
                "num limite de 30s as quinze eliminações de uma arena cheia "
                        + "não cabem, e aí ganha o placar e não a luta");
    }
}
