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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Corrida, conferida sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Um portão que a linha normal não cruza: a volta nunca fecha, e o jogador
 * corre para sempre com o contador parado. Um atalho que pula um portão sem
 * portão alternativo: idem, só que só para quem atalha — e quem atalha é
 * justamente quem está ganhando. A largada dentro de um atalho: a linha de
 * chegada vira contornável. Um buraco no chão da faixa na borda de fora da
 * curva: o jogador cai sem ter errado nada. A grade na frente da linha: a
 * primeira volta não conta.
 *
 * <p>Nada disso quebra. Tudo isso deixa a corrida jogável e ERRADA. Por isso
 * o teste ANDA: ele percorre uma volta inteira ponto por ponto, pela linha de
 * dentro, pela de fora, pelos atalhos e de ré, e confere a sequência de
 * portões que sai.
 */
class CorridaTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 36;
    private static final int ONDA = 9;
    private static final int VOLTAS_DA_ONDA = 3;
    private static final int LARGURA = 7;
    private static final int MEIA = LARGURA / 2;
    private static final int MURO = 2;
    private static final int PORTOES = 8;
    private static final int ATALHO_ABERTURA = 72;
    private static final int ATALHO_LARGURA = 2;
    private static final int TORRE_RECUO = 8;
    private static final int TORRE_ALTURA = 18;
    private static final int QUEDA_ABAIXO = 6;
    private static final int SAGUAO_RAIO = 9;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int VOLTAS = 2;
    private static final int VELOCIDADE = 2;
    private static final int TEMPO_LIMITE = 300;

    private static List<double[]> eixo;
    private static int[] portoes;
    private static int[][] atalhos;
    private static List<List<PistaFechada.Portao>> planos;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = CorridaTest.class.getResourceAsStream("/corrida.yml")) {
            assertNotNull(in, "corrida.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        eixo = PistaFechada.eixo(RAIO, ONDA, VOLTAS_DA_ONDA);
        portoes = PistaFechada.portoes(PORTOES, eixo.size());
        atalhos = PistaFechada.atalhos(eixo, VOLTAS_DA_ONDA, ATALHO_ABERTURA);
        planos = PistaFechada.planos(eixo, portoes, atalhos, MEIA + 1.5,
                ATALHO_LARGURA / 2.0 + 1.0);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("corrida", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(ONDA, arena.getInt("arena.onda"));
        assertEquals(VOLTAS_DA_ONDA, arena.getInt("arena.voltas-da-onda"));
        assertEquals(LARGURA, arena.getInt("arena.largura"));
        assertEquals(MURO, arena.getInt("arena.muro-altura"));
        assertEquals(PORTOES, arena.getInt("arena.portoes"));
        assertEquals(ATALHO_ABERTURA, arena.getInt("arena.atalho-abertura"));
        assertEquals(ATALHO_LARGURA, arena.getInt("arena.atalho-largura"));
        assertEquals(TORRE_RECUO, arena.getInt("arena.torre-recuo"));
        assertEquals(TORRE_ALTURA, arena.getInt("arena.torre-altura"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("arena.queda-abaixo"));
        assertEquals(SAGUAO_RAIO, arena.getInt("arena.saguao-raio"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(VOLTAS, arena.getInt("regras.voltas"));
        assertEquals(VELOCIDADE, arena.getInt("regras.velocidade"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertTrue(arena.getBoolean("regras.treino-sozinho"));
    }

    @Test
    @DisplayName("todo material da paleta existe")
    void paletaEhValida() {
        Map<String, Object> paleta = arena.getConfigurationSection("paleta")
                .getValues(false);
        assertFalse(paleta.isEmpty(), "a paleta ficou vazia");
        for (Map.Entry<String, Object> item : paleta.entrySet()) {
            Material material = Material.matchMaterial(
                    String.valueOf(item.getValue()).toUpperCase(Locale.ROOT));
            assertNotNull(material, "paleta." + item.getKey() + " não é um material: "
                    + item.getValue());
        }
    }

    @Test
    @DisplayName("a placa do saguão é de parede")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa", "");
        assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"),
                "paleta.placa precisa ser uma placa de parede: " + nome);
    }

    @Test
    @DisplayName("a arena tem mundo próprio")
    void arenaTemMundoProprio() {
        String mundo = arena.getString("mundo");
        assertEquals("corrida", mundo);
        assertFalse("world".equals(mundo) || "minigames".equals(mundo));
    }

    @Test
    @DisplayName("a pista, a guia e a largada são de cores diferentes")
    void pistaTemCoresDistintas() {
        Set<String> vistos = new HashSet<>();
        for (String chave : List.of("pista", "guia-escura", "largada-escura",
                "ponte", "ponte-borda", "muro")) {
            String material = arena.getString("paleta." + chave);
            assertNotNull(material, "falta paleta." + chave);
            assertTrue(vistos.add(material), "paleta." + chave + " repete " + material
                    + ": o jogador não distingue a borda do asfalto");
        }
    }

    // ------------------------------------------------------------------
    //  O eixo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o eixo tem um ponto por bloco de pista")
    void eixoEhDeUmEmUm() {
        int n = eixo.size();
        assertTrue(n > 200, "a pista saiu com só " + n + " blocos de volta");
        for (int i = 0; i < n; i++) {
            double d = Math.hypot(eixo.get(i)[0] - eixo.get((i + 1) % n)[0],
                    eixo.get(i)[1] - eixo.get((i + 1) % n)[1]);
            assertTrue(d > 0.5 && d < 1.6,
                    "o passo " + i + " mede " + d + ", e a reamostragem pede 1");
        }
    }

    @Test
    @DisplayName("a pista não encosta nela mesma")
    void pistaNaoSeCruza() {
        int janela = (int) ((2 * MEIA + 2 * MURO + 2) * 1.5);
        double folga = PistaFechada.folgaEntreTracos(eixo, janela);
        assertTrue(folga >= 2 * MEIA + 2 * MURO + 1,
                "dois trechos da pista ficam a " + folga + " um do outro");
    }

    @Test
    @DisplayName("nenhuma curva dobra sobre a própria faixa")
    void curvaCabeNaFaixa() {
        double curva = PistaFechada.curvaturaMinima(eixo);
        assertTrue(curva > MEIA + MURO,
                "curva de raio " + curva + " com faixa de meia-largura " + (MEIA + MURO));
    }

    @Test
    @DisplayName("a conferência aprova a planta de hoje")
    void conferenciaAprovaAPlanta() {
        assertNull(CorridaBuilder.conferirPista(eixo, MEIA, MURO, atalhos, SAGUAO_RAIO));
    }

    @Test
    @DisplayName("a conferência reprova uma onda que fecha a pista")
    void conferenciaReprovaOndaDemais() {
        List<double[]> apertada = PistaFechada.eixo(20, 17, 3);
        int[][] seus = PistaFechada.atalhos(apertada, 3, ATALHO_ABERTURA);
        assertNotNull(CorridaBuilder.conferirPista(apertada, MEIA, MURO, seus,
                SAGUAO_RAIO), "uma pista de raio 20 com onda 17 tinha de ser reprovada");
    }

    // ------------------------------------------------------------------
    //  Portões e atalhos
    // ------------------------------------------------------------------

    /**
     * A largada não pode ficar dentro de um atalho.
     *
     * Se ficasse, a ponte seria um caminho que passa por cima da linha de
     * chegada sem cruzá-la pela pista — e "primeiro a cruzar leva" deixaria
     * de querer dizer alguma coisa. É por isso que o anel usa menos cosseno:
     * para o ângulo zero cair no vale, longe dos bojos.
     */
    @Test
    @DisplayName("a largada fica fora de todos os atalhos")
    void largadaForaDosAtalhos() {
        for (int[] atalho : atalhos) {
            assertFalse(PistaFechada.entre(atalho[0], atalho[1], portoes[0]),
                    "o atalho " + atalho[0] + "->" + atalho[1] + " passa pela largada");
        }
    }

    @Test
    @DisplayName("todo portão pulado por um atalho tem plano na ponte")
    void portaoPuladoTemAlternativo() {
        for (int[] atalho : atalhos) {
            for (int k = 0; k < portoes.length; k++) {
                if (!PistaFechada.entre(atalho[0], atalho[1], portoes[k])) {
                    continue;
                }
                double[] a = eixo.get(atalho[0]);
                double[] b = eixo.get(atalho[1]);
                boolean achou = false;
                for (PistaFechada.Portao plano : planos.get(k)) {
                    double dist = distanciaDoSegmento(plano.cx(), plano.cz(),
                            a[0], a[1], b[0], b[1]);
                    if (dist < 0.5) {
                        achou = true;
                    }
                }
                assertTrue(achou, "o portão " + k + " é pulado pelo atalho "
                        + atalho[0] + "->" + atalho[1] + " e não tem plano na ponte: "
                        + "quem atalhar trava nele para sempre");
            }
        }
    }

    @Test
    @DisplayName("os atalhos economizam, mas não resolvem a corrida sozinhos")
    void atalhosEconomizamOCerto() {
        int economia = CorridaBuilder.economiaDosAtalhos(eixo, atalhos);
        double fatia = (double) economia / eixo.size();
        assertTrue(fatia >= 0.08, "os atalhos economizam só " + economia + " blocos");
        assertTrue(fatia <= 0.28, "os atalhos economizam " + economia
                + " blocos: quem não arriscar não tem chance nenhuma");
    }

    // ------------------------------------------------------------------
    //  Andar a volta — o teste que vale por todos
    // ------------------------------------------------------------------

    /** A sequência de portões que um caminho cruza, do jeito que o jogo conta. */
    private static List<Integer> andar(List<double[]> caminho) {
        List<Integer> vistos = new ArrayList<>();
        int proximo = 0;
        for (int i = 1; i < caminho.size(); i++) {
            double[] a = caminho.get(i - 1);
            double[] b = caminho.get(i);
            boolean cruzou = true;
            while (cruzou) {
                cruzou = false;
                int alvo = Math.floorMod(proximo, planos.size());
                for (PistaFechada.Portao plano : planos.get(alvo)) {
                    if (plano.cruzouParaFrente(a[0], a[1], b[0], b[1])) {
                        vistos.add(alvo);
                        proximo++;
                        cruzou = true;
                        break;
                    }
                }
            }
        }
        return vistos;
    }

    /** Quantos blocos atrás da largada o caminho começa. Igual ao grid. */
    private static final int ATRAS = 6;

    private static List<double[]> linha(double lado, int voltas, double passo) {
        int n = eixo.size();
        List<double[]> caminho = new ArrayList<>();
        for (double andado = 0; andado <= voltas * n + ATRAS + 2; andado += passo) {
            int i = Math.floorMod((int) Math.floor(n - ATRAS + andado), n);
            double f = andado - Math.floor(andado);
            double[] p = eixo.get(i);
            double[] q = eixo.get((i + 1) % n);
            double[] lat = PistaFechada.lateral(eixo, i);
            caminho.add(new double[]{p[0] + (q[0] - p[0]) * f + lat[0] * lado,
                    p[1] + (q[1] - p[1]) * f + lat[1] * lado});
        }
        return caminho;
    }

    private static List<Integer> esperado(int voltas) {
        List<Integer> saida = new ArrayList<>();
        for (int v = 0; v < voltas; v++) {
            for (int k = 0; k < PORTOES; k++) {
                saida.add(k);
            }
        }
        saida.add(0);
        return saida;
    }

    @Test
    @DisplayName("uma volta pela linha cruza todos os portões, em ordem")
    void voltaPelaLinhaCruzaTudo() {
        for (double lado : new double[]{-2.5, -1.0, 0.0, 1.0, 2.5}) {
            assertEquals(esperado(1), andar(linha(lado, 1, 1.0)),
                    "correndo a " + lado + " do eixo");
        }
    }

    /**
     * O mesmo, andando rápido.
     *
     * Com Velocidade II se anda perto de meio bloco por tique, e em lag se
     * anda mais. Se o portão fosse uma caixa, este é o teste que pegaria o
     * jogador atravessando-a entre dois tiques.
     */
    @Test
    @DisplayName("quem corre rápido não perde portão")
    void correrRapidoNaoPerdePortao() {
        for (double passo : new double[]{0.4, 1.0, 2.0, 4.0}) {
            assertEquals(esperado(1), andar(linha(0.0, 1, passo)),
                    "andando " + passo + " blocos por tique");
        }
    }

    @Test
    @DisplayName("duas voltas fecham duas vezes")
    void duasVoltasFecham() {
        assertEquals(esperado(2), andar(linha(0.0, 2, 1.0)));
    }

    @Test
    @DisplayName("a volta pelos três atalhos cruza os mesmos portões, na mesma ordem")
    void voltaPelosAtalhosCruzaTudo() {
        assertEquals(esperado(1), andar(caminhoComAtalhos()));
    }

    private static List<double[]> caminhoComAtalhos() {
        int n = eixo.size();
        List<double[]> caminho = new ArrayList<>();
        int i = Math.floorMod(n - ATRAS, n);
        int andados = 0;
        while (andados < n + ATRAS + 2) {
            caminho.add(eixo.get(i));
            int destino = -1;
            for (int[] atalho : atalhos) {
                if (atalho[0] == i) {
                    destino = atalho[1];
                }
            }
            if (destino >= 0) {
                double[] a = eixo.get(i);
                double[] b = eixo.get(destino);
                double comp = Math.hypot(b[0] - a[0], b[1] - a[1]);
                for (int passo = 1; passo <= (int) comp; passo++) {
                    double f = passo / comp;
                    caminho.add(new double[]{a[0] + (b[0] - a[0]) * f,
                            a[1] + (b[1] - a[1]) * f});
                }
                andados += Math.floorMod(destino - i, n);
                i = destino;
            }
            i = (i + 1) % n;
            andados++;
        }
        return caminho;
    }

    @Test
    @DisplayName("andar de ré não conta portão nenhum")
    void andarDeReNaoConta() {
        List<double[]> frente = linha(0.0, 1, 1.0);
        List<double[]> tras = new ArrayList<>(frente);
        java.util.Collections.reverse(tras);
        assertTrue(andar(tras).size() <= 1,
                "andando de ré o jogo contou " + andar(tras).size() + " portões");
    }

    @Test
    @DisplayName("o portão pega quem passa raspando no muro")
    void portaoPegaQuemRaspaNoMuro() {
        assertEquals(esperado(1), andar(linha(MEIA - 0.2, 1, 1.0)));
        assertEquals(esperado(1), andar(linha(-(MEIA - 0.2), 1, 1.0)));
    }

    // ------------------------------------------------------------------
    //  O chão
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o chão da pista não tem buraco")
    void chaoNaoTemBuraco() {
        Map<Long, Integer> chao = CorridaBuilder.chaoDaPista(eixo, MEIA, portoes);
        assertTrue(chao.size() > eixo.size() * (LARGURA - 2),
                "o chão saiu com " + chao.size() + " casas para " + eixo.size()
                        + " blocos de pista de largura " + LARGURA);

        // Toda casa do eixo está no chão, e o chão é um pedaço só.
        for (double[] p : eixo) {
            long chave = CorridaBuilder.chave((int) Math.floor(p[0] + 0.5),
                    (int) Math.floor(p[1] + 0.5));
            assertTrue(chao.containsKey(chave),
                    "falta chão no eixo em " + p[0] + "," + p[1]);
        }
        assertEquals(chao.size(), alcancaveis(chao.keySet()),
                "o chão da pista está partido em mais de um pedaço");
    }

    /** Quantas casas se alcança andando em quatro direções a partir da primeira. */
    private static int alcancaveis(Set<Long> casas) {
        Set<Long> vistos = new HashSet<>();
        Deque<Long> fila = new ArrayDeque<>();
        Long inicio = casas.iterator().next();
        fila.add(inicio);
        vistos.add(inicio);
        while (!fila.isEmpty()) {
            long atual = fila.poll();
            int x = CorridaBuilder.xDaChave(atual);
            int z = CorridaBuilder.zDaChave(atual);
            for (int[] passo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long vizinho = CorridaBuilder.chave(x + passo[0], z + passo[1]);
                if (casas.contains(vizinho) && vistos.add(vizinho)) {
                    fila.add(vizinho);
                }
            }
        }
        return vistos.size();
    }

    @Test
    @DisplayName("o chão cobre a largura pedida em toda a volta")
    void chaoCobreALargura() {
        Map<Long, Integer> chao = CorridaBuilder.chaoDaPista(eixo, MEIA, portoes);
        for (int i = 0; i < eixo.size(); i++) {
            double[] p = eixo.get(i);
            double[] lat = PistaFechada.lateral(eixo, i);
            for (double off = -MEIA + 0.5; off <= MEIA - 0.5; off += 1.0) {
                int x = (int) Math.floor(p[0] + lat[0] * off + 0.5);
                int z = (int) Math.floor(p[1] + lat[1] * off + 0.5);
                assertTrue(chao.containsKey(CorridaBuilder.chave(x, z)),
                        "falta chão a " + off + " do eixo no bloco " + i);
            }
        }
    }

    @Test
    @DisplayName("as pontes encostam na pista nas duas bocas")
    void pontesEncostamNasBocas() {
        Map<Long, Integer> chao = CorridaBuilder.chaoDaPista(eixo, MEIA, portoes);
        Map<Long, Integer> pontes = CorridaBuilder.chaoDasPontes(eixo, atalhos,
                ATALHO_LARGURA);
        assertFalse(pontes.isEmpty(), "nenhuma ponte foi gerada");

        for (int[] atalho : atalhos) {
            for (int ponta : new int[]{atalho[0], atalho[1]}) {
                double[] p = eixo.get(ponta);
                long chave = CorridaBuilder.chave((int) Math.floor(p[0] + 0.5),
                        (int) Math.floor(p[1] + 0.5));
                assertTrue(pontes.containsKey(chave) || chao.containsKey(chave),
                        "a boca do atalho em " + ponta + " não tem chão");
            }
        }
    }

    // ------------------------------------------------------------------
    //  A grade
    // ------------------------------------------------------------------

    /**
     * A grade tem de ficar ATRÁS da linha.
     *
     * Quem nasce em cima da largada não a cruza, e a largada é o portão que
     * fecha a volta: a primeira volta nunca começaria e a corrida terminaria
     * no tempo, com todo mundo numa volta a menos.
     */
    @Test
    @DisplayName("todo lugar da grade fica atrás da linha e dentro da pista")
    void gradeFicaAtrasDaLinha() {
        PistaFechada.Portao largada = planos.get(0).get(0);
        Map<Long, Integer> chao = CorridaBuilder.chaoDaPista(eixo, MEIA, portoes);
        for (int k = 0; k < MAXIMO; k++) {
            double[] onde = CorridaBuilder.grade(eixo, k);
            assertTrue(largada.lado(onde[0], onde[1]) < 0,
                    "o lugar " + k + " da grade já está depois da linha");
            long chave = CorridaBuilder.chave((int) Math.floor(onde[0]),
                    (int) Math.floor(onde[1]));
            assertTrue(chao.containsKey(chave),
                    "o lugar " + k + " da grade fica fora da pista");
        }
    }

    @Test
    @DisplayName("ninguém divide lugar na grade")
    void gradeNaoRepeteLugar() {
        Set<String> vistos = new HashSet<>();
        for (int k = 0; k < MAXIMO; k++) {
            double[] onde = CorridaBuilder.grade(eixo, k);
            String casa = (int) Math.floor(onde[0]) + "," + (int) Math.floor(onde[1]);
            assertTrue(vistos.add(casa), "dois lugares da grade caem em " + casa);
        }
    }

    @Test
    @DisplayName("o yaw da grade aponta para onde se corre")
    void gradeOlhaParaFrente() {
        for (int k = 0; k < MAXIMO; k += 3) {
            double[] onde = CorridaBuilder.grade(eixo, k);
            float yaw = (float) onde[2];
            assertTrue(yaw >= -180f && yaw <= 180f, "yaw fora de faixa: " + yaw);
        }
        // Olhar para frente é olhar para o mesmo lado da tangente do lugar
        // onde o jogador nasce — e não da tangente da largada, que já está
        // três blocos adiante.
        int n = eixo.size();
        double[] t = PistaFechada.tangente(eixo, Math.floorMod(-3, n));
        assertEquals(CorridaBuilder.yawDaDirecao(t[0], t[1]),
                (float) CorridaBuilder.grade(eixo, 0)[2], 0.001);

        // E o yaw tem de ser o da direção, não o do contrário dela: andar
        // para frente aproxima da largada.
        double[] onde = CorridaBuilder.grade(eixo, 0);
        double adiante = Math.hypot(onde[0] + t[0] * 2 - eixo.get(0)[0],
                onde[1] + t[1] * 2 - eixo.get(0)[1]);
        double parado = Math.hypot(onde[0] - eixo.get(0)[0],
                onde[1] - eixo.get(0)[1]);
        assertTrue(adiante < parado, "a grade olha para o lado errado da pista");
    }

    // ------------------------------------------------------------------
    //  As regras
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o tempo limite dá para as duas voltas")
    void tempoLimiteDaParaCorrer() {
        int blocos = eixo.size() * VOLTAS;
        assertTrue(TEMPO_LIMITE >= blocos / 8,
                "o teto de " + TEMPO_LIMITE + "s não dá para " + blocos + " blocos");
        assertTrue(TEMPO_LIMITE <= blocos * 2,
                "o teto de " + TEMPO_LIMITE + "s é tão largo que não é teto");
    }

    @Test
    @DisplayName("a linha de queda fica abaixo da pista, mas perto")
    void quedaFicaLogoAbaixo() {
        int queda = Y - QUEDA_ABAIXO;
        assertTrue(queda < Y - 2, "a linha de queda pega quem está em pé na pista");
        assertTrue(queda > Y - 16, "a queda demora tanto que parece travamento");
    }

    private static double distanciaDoSegmento(double px, double pz, double ax,
                                              double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double comp = dx * dx + dz * dz;
        double t = comp <= 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / comp;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
    }
}
