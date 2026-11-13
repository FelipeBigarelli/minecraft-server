package codes.biga.bigacore;

import org.bukkit.Material;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O TNT Run, conferido sem servidor.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * Um jogo de arena erra de um jeito diferente de uma construção: não vira
 * obra torta, vira partida que não acaba, jogador imortal ou chão que some
 * onde ninguém pisou. Nada disso lança exceção, e tudo isso só aparece com
 * gente dentro. Cada teste aqui corresponde a uma regra do jogo que, quebrada,
 * deixa o TNT Run jogável e ERRADO — que é o pior estado possível.
 */
class TntRunTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 24;
    private static final int PISOS = 3;
    private static final int ESPESSURA = 2;
    private static final int VAO = 5;
    private static final int BEIRA = 2;
    private static final int SAGUAO_ALTURA = 18;
    private static final int SAGUAO_RAIO = 9;
    private static final int SAGUAO_PARAPEITO = 4;
    private static final int MARCO_RECUO = 8;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int DERRETE = 8;
    private static final int QUEDA_ABAIXO = 8;

    private static int passo() {
        return ESPESSURA + VAO;
    }

    private static int topoDoPiso(int camada) {
        return Y - camada * passo();
    }

    private static int baseDoPiso(int camada) {
        return topoDoPiso(camada) - ESPESSURA + 1;
    }

    private static int fundoDaArena() {
        return baseDoPiso(PISOS - 1);
    }

    private static int quedaY() {
        return fundoDaArena() - QUEDA_ABAIXO;
    }

    private static int saguaoY() {
        return Y + SAGUAO_ALTURA;
    }

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = TntRunTest.class.getResourceAsStream("/tnt-run.yml")) {
            assertNotNull(in, "tnt-run.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("tnt_run", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(PISOS, arena.getInt("arena.pisos"));
        assertEquals(ESPESSURA, arena.getInt("arena.espessura"));
        assertEquals(VAO, arena.getInt("arena.vao"));
        assertEquals(BEIRA, arena.getInt("arena.beira-aneis"));
        assertEquals(SAGUAO_ALTURA, arena.getInt("arena.saguao-altura"));
        assertEquals(SAGUAO_RAIO, arena.getInt("arena.saguao-raio"));
        assertEquals(SAGUAO_PARAPEITO, arena.getInt("arena.saguao-parapeito"));
        assertEquals(MARCO_RECUO, arena.getInt("arena.marco-recuo"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(DERRETE, arena.getInt("regras.derrete-ticks"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("regras.queda-abaixo"));
    }

    @Test
    @DisplayName("todo material da paleta existe neste Minecraft")
    void paletaEhValida() {
        for (String chave : arena.getConfigurationSection("paleta").getKeys(false)) {
            String nome = arena.getString("paleta." + chave);
            assertNotNull(Material.matchMaterial(nome.toUpperCase(Locale.ROOT)),
                    "paleta." + chave + " não é um material: " + nome);
        }
    }

    /**
     * A trava que impede o derretimento de comer a obra.
     *
     * O jogo só apaga os quatro materiais do piso. Se um deles fosse também o
     * material de alguma parte permanente — o vidro do saguão, o pilar do
     * marco —, um erro de uma linha na conta do bloco do pé passaria a
     * desfazer a arena de rodada em rodada, sem erro em lugar nenhum.
     */
    @Test
    @DisplayName("nenhum material do piso é material do saguão ou dos marcos")
    void oQueDerreteNaoEOQueFica() {
        Set<String> doPiso = new HashSet<>(List.of(
                arena.getString("paleta.piso"),
                arena.getString("paleta.piso-base"),
                arena.getString("paleta.beira"),
                arena.getString("paleta.beira-base")));
        assertEquals(4, doPiso.size(), "dois materiais do piso são o mesmo bloco");

        for (String chave : List.of("estrutura", "escuro", "borda", "metal",
                "vidro", "luz", "luz-fria", "nucleo")) {
            String nome = arena.getString("paleta." + chave);
            assertFalse(doPiso.contains(nome), "paleta." + chave + " (" + nome
                    + ") também é material de piso: o derretimento poderia comer "
                    + "a parte permanente da arena");
        }
    }

    // ------------------------------------------------------------------
    //  A geometria dos pisos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("os pisos descem de espessura+vão e não se encostam")
    void pisosEmpilhamCerto() {
        for (int k = 0; k < PISOS; k++) {
            assertEquals(ESPESSURA, topoDoPiso(k) - baseDoPiso(k) + 1,
                    "o piso " + k + " não tem a espessura pedida");
        }
        for (int k = 0; k + 1 < PISOS; k++) {
            int ar = baseDoPiso(k) - topoDoPiso(k + 1) - 1;
            assertEquals(VAO, ar, "entre o piso " + k + " e o " + (k + 1)
                    + " sobraram " + ar + " de ar");
        }
    }

    /**
     * O número que manda no jogo inteiro.
     *
     * Com três de ar entre um piso e o de baixo, não se pula de volta: o pulo
     * sobe 1,25 e precisaria de quatro de pé livre. Com DOIS, pula-se — e aí
     * cair de piso deixa de custar, as três chances deixam de ser chances e o
     * jogo vira uma caminhada.
     */
    @Test
    @DisplayName("o vão entre pisos impede voltar ao de cima num pulo")
    void vaoImpedeSubirDeVolta() {
        assertTrue(VAO >= TntRunCatalog.VAO_MINIMO,
                "vão de " + VAO + ": o jogador pula de volta para o piso de cima");

        // O pulo do Minecraft sobe 1,25 de bloco. Em pé na base de um piso,
        // o teto do vão é a base do piso de cima.
        double alcanceDoPulo = 1.25;
        assertTrue(VAO > alcanceDoPulo + 1,
                "um pulo de " + alcanceDoPulo + " alcança o piso de cima com "
                        + VAO + " de vão");
    }

    /**
     * A lâmpada do piso derrete junto com o piso.
     *
     * Se ela não estivesse na lista de materiais do piso, cada rodada
     * deixaria um céu de lâmpadas boiando onde a coluna sumiu — e o
     * derretimento tem a trava justamente para só comer o que é piso.
     */
    @Test
    @DisplayName("a lâmpada do piso é material de piso e derrete com ele")
    void aLampadaDerreteComOPiso() {
        String nome = arena.getString("paleta.piso-luz");
        assertNotNull(nome, "falta 'paleta.piso-luz': os andares de baixo "
                + "ficam no escuro, o teto deles é o piso de cima");
        Material luz = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        assertNotNull(luz, "material desconhecido em paleta.piso-luz: " + nome);
        assertNotEquals(arena.getString("paleta.piso"), nome,
                "a lâmpada é o mesmo bloco do piso: não ilumina nada");
        assertNotEquals(arena.getString("paleta.beira"), nome,
                "a lâmpada é o mesmo bloco da beira: não ilumina nada");
    }

    /**
     * A luz alcança o meio de cada quadrado da grade.
     *
     * A luz do Minecraft anda quinze blocos e perde um por bloco. O pior
     * ponto de uma grade de passo P é o centro do quadrado, a P/2·raiz(2)
     * da lâmpada mais próxima.
     */
    @Test
    @DisplayName("o passo da grade de lâmpadas acende o piso inteiro")
    void aGradeDeLuzAcendeTudo() {
        int passo = arena.getInt("arena.luz-passo", 6);
        assertTrue(passo >= 2, "passo de " + passo + ": o chão vira pisca-pisca");
        double piorCaso = passo / 2.0 * Math.sqrt(2);
        assertTrue(piorCaso < 15,
                "com passo " + passo + " o pior ponto fica a "
                        + Math.round(piorCaso) + " blocos da lâmpada mais "
                        + "próxima, e a luz só anda 15");
    }

    /** O preparo existe e é o mesmo do resto do servidor. */
    @Test
    @DisplayName("a rodada tem preparo antes de o chão começar a sumir")
    void aRodadaTemPreparo() {
        int preparo = arena.getInt("regras.preparo-segundos", 0);
        assertTrue(preparo >= 3, "preparo de " + preparo + "s: no TNT Run o "
                + "piso começa a cair no mesmo tique da largada");
        assertTrue(preparo <= 15, "preparo de " + preparo + "s é espera demais");
    }

    @Test
    @DisplayName("quem está de pé no piso mais baixo não conta como caído")
    void limiteDeQuedaTemFolga() {
        int pesDeQuemEstaNoUltimoPiso = topoDoPiso(PISOS - 1) + 1;
        assertTrue(quedaY() < pesDeQuemEstaNoUltimoPiso - 2,
                "o limite de queda (" + quedaY() + ") está grudado no último piso ("
                        + pesDeQuemEstaNoUltimoPiso + "): elimina de pé");
        assertTrue(quedaY() < fundoDaArena(),
                "o limite de queda está dentro da arena");
    }

    @Test
    @DisplayName("o saguão fica alto e pequeno demais para virar parte da arena")
    void saguaoNaoEstaNoCaminho() {
        assertTrue(SAGUAO_RAIO < RAIO, "o saguão é maior que a arena");
        assertTrue(saguaoY() > Y + 4,
                "o saguão está perto demais do piso de cima: dá para pular nele");
        assertTrue(saguaoY() > quedaY(),
                "quem espera no saguão já conta como caído");
        assertTrue(SAGUAO_PARAPEITO >= 3,
                "parapeito de " + SAGUAO_PARAPEITO + ": pula-se fora do saguão antes "
                        + "da largada, e quem cai antes da rodada é eliminado numa "
                        + "partida que não começou");
    }

    /**
     * Qualquer bloco sólido ao alcance de um pulo da beira é lugar de acampar,
     * e acampar é o único jeito de ganhar de graça neste jogo.
     */
    @Test
    @DisplayName("os marcos ficam longe demais para se pular da beira neles")
    void marcosForaDeAlcance() {
        int folga = MARCO_RECUO - 1;      // o marco tem 3 de largura: 1 para cada lado
        assertTrue(folga >= 5, "o marco fica a " + folga
                + " da beira do disco; um pulo com corrida passa de 4");
    }

    // ------------------------------------------------------------------
    //  O disco
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o piso é um disco, e a beira é só a casca dele")
    void discoEBeira() {
        assertTrue(TntRunCatalog.noDisco(RAIO, 0, RAIO));
        assertTrue(TntRunCatalog.noDisco(0, -RAIO, RAIO));
        assertFalse(TntRunCatalog.noDisco(RAIO + 1, 0, RAIO));
        assertFalse(TntRunCatalog.noDisco(RAIO, RAIO, RAIO), "a quina não é disco");

        assertTrue(TntRunCatalog.naBeira(RAIO, 0, RAIO, BEIRA));
        assertFalse(TntRunCatalog.naBeira(0, 0, RAIO, BEIRA), "o centro não é beira");
        assertFalse(TntRunCatalog.naBeira(RAIO + 1, 0, RAIO, BEIRA),
                "fora do disco não é beira");

        // E a beira cobre uma casca de espessura BEIRA, não mais.
        for (int x = -RAIO; x <= RAIO; x++) {
            for (int z = -RAIO; z <= RAIO; z++) {
                if (!TntRunCatalog.naBeira(x, z, RAIO, BEIRA)) {
                    continue;
                }
                assertTrue(TntRunCatalog.noDisco(x, z, RAIO));
                assertFalse(TntRunCatalog.noDisco(x, z, RAIO - BEIRA),
                        "a beira entrou no miolo em " + x + "," + z);
            }
        }
    }

    // ------------------------------------------------------------------
    //  A largada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as largadas cabem no disco, não se repetem e têm folga entre si")
    void largadasSaoJustas() {
        for (int quantos = 1; quantos <= MAXIMO; quantos++) {
            List<int[]> pontos = TntRunCatalog.largadas(quantos, RAIO);
            assertEquals(quantos, pontos.size(), "faltou largada para " + quantos);

            Set<String> vistos = new HashSet<>();
            for (int[] p : pontos) {
                assertTrue(TntRunCatalog.noDisco(p[0], p[1], RAIO - 3),
                        "largada em " + p[0] + "," + p[1] + " está colada na beira");
                assertTrue(vistos.add(p[0] + ":" + p[1]),
                        "duas largadas no mesmo bloco com " + quantos + " jogadores");
            }

            if (quantos < 2) {
                continue;
            }
            for (int i = 0; i < pontos.size(); i++) {
                for (int j = i + 1; j < pontos.size(); j++) {
                    int dx = pontos.get(i)[0] - pontos.get(j)[0];
                    int dz = pontos.get(i)[1] - pontos.get(j)[1];
                    assertTrue(dx * dx + dz * dz >= 4,
                            "duas largadas a menos de 2 blocos com " + quantos
                                    + " jogadores: a partida começa em empurrão");
                }
            }
        }
    }

    @Test
    @DisplayName("jogador sozinho larga no meio")
    void largadaSozinho() {
        List<int[]> pontos = TntRunCatalog.largadas(1, RAIO);
        assertEquals(1, pontos.size());
        assertEquals(0, pontos.get(0)[0]);
        assertEquals(0, pontos.get(0)[1]);
    }

    /**
     * A conta de yaw tem duas versões que parecem iguais e são opostas.
     * A errada larga todos olhando para a beira — e a primeira reação de meia
     * arena passa a ser correr para fora dela.
     */
    @Test
    @DisplayName("a largada vira o jogador para o centro, não para a beira")
    void largadaOlhaParaOCentro() {
        // Quem está no norte (z negativo) tem de olhar para +Z, que é yaw 0.
        assertEquals(0f, TntRunBuilder.yawParaOCentro(0, -19), 0.01f);
        assertEquals(180f, Math.abs(TntRunBuilder.yawParaOCentro(0, 19)), 0.01f);
        assertEquals(90f, TntRunBuilder.yawParaOCentro(19, 0), 0.01f);
        assertEquals(-90f, TntRunBuilder.yawParaOCentro(-19, 0), 0.01f);

        // E, para todo ponto do anel, olhar na direção do yaw tem de diminuir
        // a distância até o centro.
        for (int[] p : TntRunCatalog.largadas(16, RAIO)) {
            double yaw = Math.toRadians(TntRunBuilder.yawParaOCentro(p[0], p[1]));
            double dx = -Math.sin(yaw);
            double dz = Math.cos(yaw);
            double antes = Math.hypot(p[0], p[1]);
            double depois = Math.hypot(p[0] + dx * 2, p[1] + dz * 2);
            assertTrue(depois < antes, "em " + p[0] + "," + p[1]
                    + " o jogador larga de costas para o centro");
        }
    }

    // ------------------------------------------------------------------
    //  O derretimento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada fiada sabe a que piso pertence, e o ar não pertence a nenhum")
    void camadaDaFiada() {
        for (int k = 0; k < PISOS; k++) {
            for (int y = baseDoPiso(k); y <= topoDoPiso(k); y++) {
                assertEquals(k, TntRun.camadaDaFiada(y, Y, passo(), ESPESSURA, PISOS),
                        "a fiada " + y + " deveria ser do piso " + k);
            }
        }
        for (int k = 0; k + 1 < PISOS; k++) {
            for (int y = topoDoPiso(k + 1) + 1; y < baseDoPiso(k); y++) {
                assertEquals(-1, TntRun.camadaDaFiada(y, Y, passo(), ESPESSURA, PISOS),
                        "a fiada " + y + " é ar entre pisos e não pode derreter");
            }
        }
        assertEquals(-1, TntRun.camadaDaFiada(Y + 1, Y, passo(), ESPESSURA, PISOS),
                "acima do piso de cima não há piso");
        assertEquals(-1, TntRun.camadaDaFiada(fundoDaArena() - 1, Y, passo(),
                ESPESSURA, PISOS), "abaixo do fundo não há piso");
        assertEquals(-1, TntRun.camadaDaFiada(saguaoY(), Y, passo(), ESPESSURA, PISOS),
                "a fiada do saguão não pode pertencer a piso nenhum");
    }

    /**
     * Em pé num bloco cheio o pé fica numa altura INTEIRA. É a trava medida no
     * servidor, ao lado do {@code isOnGround()} informado pelo cliente: sem
     * ela, um jogador em queda derreteria fiadas de pisos pelos quais só
     * passou, abrindo buracos onde ninguém pisou.
     */
    @Test
    @DisplayName("só quem está firme no chão derrete bloco")
    void pisandoFirme() {
        assertTrue(TntRun.pisandoFirme(101.0));
        assertTrue(TntRun.pisandoFirme(96.0));
        assertFalse(TntRun.pisandoFirme(100.4), "altura de quem está caindo");
        assertFalse(TntRun.pisandoFirme(100.5), "meia altura não é piso cheio");
        assertFalse(TntRun.pisandoFirme(99.8));
    }

    @Test
    @DisplayName("o bloco do pé é o do meio, quando há um do meio")
    void bloquinhoDoMeio() {
        List<int[]> achados = TntRun.blocosDoPe(5.5, 101.0, -3.5,
                (x, y, z) -> y == 100);
        assertEquals(1, achados.size());
        assertEquals(5, achados.get(0)[0]);
        assertEquals(100, achados.get(0)[1], "a fiada do pé é floor(y) - 1");
        assertEquals(-4, achados.get(0)[2]);
    }

    /**
     * O defeito que isto fecha: parar com o corpo pela metade para fora de um
     * bloco. O bloco do meio é ar, nada derrete, e o jogador fica em pé para
     * sempre na beira do buraco que ele mesmo abriu.
     */
    @Test
    @DisplayName("na beira de um bloco derretem os vizinhos que seguram o jogador")
    void bloquinhosDaBeira() {
        // O jogador está em x=6,02: o bloco do meio é o 6, e só o 5 é sólido.
        List<int[]> achados = TntRun.blocosDoPe(6.02, 101.0, 0.5,
                (x, y, z) -> y == 100 && x == 5 && z == 0);
        assertEquals(1, achados.size(), "o vizinho que segura não foi achado");
        assertEquals(5, achados.get(0)[0]);
        assertEquals(0, achados.get(0)[2]);

        assertTrue(TntRun.blocosDoPe(0.5, 101.0, 0.5, (x, y, z) -> false).isEmpty(),
                "sem nada sólido por baixo não há o que derreter");
    }

    /**
     * Um bloco por vez, nunca os quatro apoios de uma vez.
     *
     * Não é gosto: derretendo todo o apoio junto, correr em linha reta abre
     * trilha de DOIS de largura e o chão da arena acaba no dobro da
     * velocidade — a partida inteira fica com metade da duração desenhada.
     * Quem para na quina perde os apoios um por vez de qualquer jeito.
     */
    @Test
    @DisplayName("derrete um bloco por vez, mesmo com quatro apoios sob o jogador")
    void umBlocoPorVez() {
        // Tudo sólido na fiada de baixo: os quatro cantos seguram.
        List<int[]> tudo = TntRun.blocosDoPe(6.0, 101.0, 6.0, (x, y, z) -> y == 100);
        assertEquals(1, tudo.size(), "derreteu " + tudo.size() + " blocos de uma vez");

        // Em cima da quina, com dois cantos sólidos: ainda um só.
        List<int[]> quina = TntRun.blocosDoPe(6.0, 101.0, 6.0,
                (x, y, z) -> y == 100 && x == 5);
        assertEquals(1, quina.size(), "derreteu " + quina.size() + " na quina");
        assertEquals(5, quina.get(0)[0], "escolheu um canto que não segura ninguém");
    }

    @Test
    @DisplayName("o relógio de um bloco não reinicia quando se pisa nele de novo")
    void relogioNaoReinicia() {
        // O mapa real é privado; a regra é o putIfAbsent, e o que se mede aqui
        // é o comportamento que ela garante: N tiques depois do PRIMEIRO passo,
        // e não do último.
        java.util.Map<TntRun.Ponto, Integer> derretendo = new java.util.HashMap<>();
        TntRun.Ponto ponto = new TntRun.Ponto(3, 100, 4);

        for (int tique = 0; tique < 4; tique++) {
            if (!derretendo.containsKey(ponto)) {
                derretendo.put(ponto, DERRETE);
            }
            derretendo.computeIfPresent(ponto, (k, v) -> v - 1);
        }
        assertEquals(DERRETE - 4, derretendo.get(ponto),
                "o relógio reiniciou: ficar parado viraria imortalidade");
    }

    // ------------------------------------------------------------------
    //  O fim da rodada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a rodada acaba com um de pé — menos no treino sozinho")
    void fimDaRodada() {
        assertFalse(TntRunCatalog.rodadaAcabou(4, 4, true));
        assertFalse(TntRunCatalog.rodadaAcabou(2, 4, true));
        assertTrue(TntRunCatalog.rodadaAcabou(1, 4, true), "um de pé é o vencedor");
        assertTrue(TntRunCatalog.rodadaAcabou(0, 4, true), "ninguém de pé acaba também");

        // Sozinho o critério muda, senão a rodada acabaria no mesmo tique em
        // que começasse e o único jogador seria declarado vencedor sem correr.
        assertFalse(TntRunCatalog.rodadaAcabou(1, 1, true), "treino: ainda está de pé");
        assertTrue(TntRunCatalog.rodadaAcabou(0, 1, true), "treino: caiu, acabou");

        // Com o treino desligado, um só volta a ser fim imediato.
        assertTrue(TntRunCatalog.rodadaAcabou(1, 1, false));
    }

    @Test
    @DisplayName("a contagem anuncia os dez últimos segundos, e de dez em dez antes")
    void contagemAnuncia() {
        for (int s = 1; s <= 10; s++) {
            assertTrue(TntRunCatalog.anunciaContagem(s),
                    "faltou anunciar um dos dez últimos: " + s);
        }
        for (int s : new int[]{20, 30, 60}) {
            assertTrue(TntRunCatalog.anunciaContagem(s), "faltou anunciar " + s);
        }
        for (int s : new int[]{19, 18, 15, 14, 11, 0, -1}) {
            assertFalse(TntRunCatalog.anunciaContagem(s), "anunciou demais em " + s);
        }
    }

    @Test
    @DisplayName("o tempo é escrito em minuto e segundo, com zero à esquerda")
    void tempoEscrito() {
        assertEquals("0:00", TntRunCatalog.tempo(0));
        assertEquals("0:09", TntRunCatalog.tempo(20 * 9));
        assertEquals("0:59", TntRunCatalog.tempo(20 * 59));
        assertEquals("1:00", TntRunCatalog.tempo(20 * 60));
        assertEquals("1:07", TntRunCatalog.tempo(20 * 67));
        assertEquals("0:00", TntRunCatalog.tempo(-5), "tique negativo não vira hora");
    }

    @Test
    @DisplayName("a barra nunca sai de 0..1, nem com divisão por zero")
    void barraNaoEstoura() {
        assertEquals(0f, TntRun.limite(-0.5f), 1e-6);
        assertEquals(1f, TntRun.limite(4f), 1e-6);
        assertEquals(0.5f, TntRun.limite(0.5f), 1e-6);
        assertEquals(0f, TntRun.limite(0f / 0f), 1e-6, "0/0 na barra vira NaN");
    }

    // ------------------------------------------------------------------
    //  O arco de volta ao lobby
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o arco de volta fica dentro do disco do saguão")
    void arcoCabeNoSaguao() {
        int z = TntRunBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        assertTrue(z < 0 && -z < SAGUAO_RAIO, "o arco saiu do saguão: z = " + z);
        for (int x = -2; x <= 2; x++) {
            assertTrue(TntRunCatalog.noDisco(x, z, SAGUAO_RAIO),
                    "a coluna x=" + x + " do arco está fora do chão do saguão");
        }
    }

    /**
     * O pior defeito possível de um portal de volta: chegar EM CIMA da caixa
     * dele. Quem aparecesse no saguão seria mandado ao lobby no mesmo tique,
     * voltaria pelo portal do lobby, seria mandado de novo — um laço fechado
     * entre dois mundos, sem erro em lugar nenhum.
     */
    @Test
    @DisplayName("quem chega no meio do saguão não cai dentro do arco de volta")
    void chegadaForaDoArco() {
        int z = TntRunBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        assertFalse(TntRunBuilder.dentroDaVolta(0.5, saguaoY() + 1, 0.5, saguaoY(), z),
                "o spawn do saguão está dentro do arco de volta");

        // E dentro do vão ele pega, senão o arco é decoração.
        assertTrue(TntRunBuilder.dentroDaVolta(0.5, saguaoY() + 1, z + 0.5,
                saguaoY(), z), "parado no vão do arco não volta para o lobby");
        assertTrue(TntRunBuilder.dentroDaVolta(1.5, saguaoY() + 2, z + 0.5,
                saguaoY(), z));

        // Quem passa ao lado dele não volta por engano.
        assertFalse(TntRunBuilder.dentroDaVolta(5.5, saguaoY() + 1, z + 0.5,
                saguaoY(), z), "passar ao lado do arco manda para o lobby");
        assertFalse(TntRunBuilder.dentroDaVolta(0.5, saguaoY() + 1, z + 3.5,
                saguaoY(), z), "andar dois blocos à frente do arco já teleporta");
    }

    /**
     * A arena fica num mundo só dela.
     *
     * Os dois são construídos em volta de (0,0), e o desfazer de cada um
     * apaga tudo na sua área: no mesmo mundo, um /tntrun desfazer comeria o
     * claustro de vinte portais inteiro.
     */
    @Test
    @DisplayName("a arena mora no próprio mundo, e o portal do lobby aponta para ele")
    void arenaTemMundoProprio() throws Exception {
        YamlConfiguration lobby;
        try (InputStream in = TntRunTest.class.getResourceAsStream("/minigames.yml")) {
            lobby = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration geral;
        try (InputStream in = TntRunTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml não foi empacotado");
            geral = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        String mundoDaArena = arena.getString("mundo");
        assertNotEquals(geral.getString("minigames.mundo"), mundoDaArena,
                "a arena foi posta no mesmo mundo do lobby");
        assertNotEquals(geral.getString("hub.mundo"), mundoDaArena,
                "a arena foi posta no mundo do hub");

        List<String> apontados = new ArrayList<>();
        for (Map<?, ?> portal : lobby.getMapList("portais")) {
            if ("tnt_run".equals(String.valueOf(portal.get("id")))) {
                apontados.add(String.valueOf(portal.get("mundo")));
            }
        }
        assertEquals(1, apontados.size(), "não há um portal tnt_run no lobby");
        assertEquals(mundoDaArena, apontados.get(0),
                "o portal vermelho do lobby não aponta para o mundo da arena: "
                        + "o jogo existiria sem como entrar nele");
    }

    // ------------------------------------------------------------------
    //  As placas: a única porta de entrada do jogo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as duas placas têm texto, e ele cabe numa placa")
    void placasTemTexto() {
        for (String chave : List.of("entrar", "sair", "fila", "esperando", "jogando",
                "refazendo")) {
            String valor = arena.getString("textos.placa-" + chave);
            assertNotNull(valor, "falta textos.placa-" + chave);
            assertFalse(valor.isBlank(), "textos.placa-" + chave + " está vazio");
            assertTrue(valor.length() <= 15, "textos.placa-" + chave + " tem "
                    + valor.length() + " caracteres e não cabe numa linha de placa");
        }
        assertEquals(2, arena.getInt("regras.minimo"),
                "ele pediu mínimo de dois jogadores");
    }

    /**
     * Placa de parede, e não de chão: ela fica pendurada no muro da fachada.
     * A conferência é pelo nome porque {@code org.bukkit.Tag} só existe com
     * servidor de pé — o jogo confere pela tag de verdade, em
     * {@code TntRunCatalog.materialDaPlaca()}.
     */
    @Test
    @DisplayName("o material da placa é placa DE PAREDE")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa");
        assertNotNull(nome, "falta paleta.placa");
        Material material = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        assertNotNull(material, "paleta.placa não é um material: " + nome);
        assertTrue(material.name().endsWith("_WALL_SIGN"),
                material + " não é placa de parede: não tem como encostar no muro");
    }

    /**
     * As três coisas que fazem a placa funcionar, e que erradas a deixam
     * decorativa: estar no chão do saguão, ter muro atrás (placa de parede
     * precisa de bloco de apoio) e ficar FORA da caixa do arco de volta —
     * parado na frente dela, o jogador não pode estar sendo mandado ao lobby.
     */
    @Test
    @DisplayName("as placas ficam no muro, dentro do saguão e fora do arco de volta")
    void placasFicamNoLugar() {
        int zMuro = TntRunBuilder.zDoArcoDeVolta(SAGUAO_RAIO);
        int fiada = saguaoY() + TntRunBuilder.FIADA_DA_PLACA;
        int[][] placas = {{-4, fiada, zMuro + 1}, {4, fiada, zMuro + 1}};

        for (int[] placa : placas) {
            assertEquals(zMuro + 1, placa[2], "a placa não está colada no muro");
            assertTrue(TntRunCatalog.noDisco(placa[0], placa[2], SAGUAO_RAIO),
                    "a placa em x=" + placa[0] + " está fora do chão do saguão");
            assertTrue(TntRunCatalog.noDisco(placa[0], zMuro, SAGUAO_RAIO),
                    "não há muro atrás da placa em x=" + placa[0]);
            assertTrue(Math.abs(placa[0]) > 2
                            && Math.abs(placa[0]) <= TntRunBuilder.LARGURA_DA_FACHADA,
                    "a placa em x=" + placa[0] + " caiu no vão do arco ou fora do muro");
            assertTrue(placa[1] > saguaoY()
                            && placa[1] < saguaoY() + TntRunBuilder.alturaDoArcoDeVolta(),
                    "a placa em x=" + placa[0] + " está dentro do chão ou acima do muro");

            // Quem para na frente dela não pode ser teleportado para o lobby.
            double px = placa[0] + 0.5;
            for (double dz = 1; dz <= 2; dz++) {
                assertFalse(TntRunBuilder.dentroDaVolta(px, saguaoY() + 1,
                                zMuro + dz + 0.5, saguaoY(), zMuro),
                        "parado na frente da placa em x=" + placa[0]
                                + " o jogador é mandado de volta ao lobby");
            }
        }
        assertNotEquals(placas[0][0], placas[1][0], "as duas placas no mesmo bloco");
    }

    @Test
    @DisplayName("o jogo é entrado pela placa, e o texto escrito diz isso")
    void asRegrasMandamClicarNaPlaca() {
        List<String> regras = arena.getStringList("textos.regras");
        assertFalse(regras.isEmpty(), "o letreiro de regras ficou vazio");
        String entrar = arena.getString("textos.placa-entrar");
        assertTrue(regras.stream().anyMatch(l -> l.contains(entrar)),
                "nenhuma linha das regras manda clicar em " + entrar
                        + ": quem chega no saguão não descobre como entrar");
    }
}
