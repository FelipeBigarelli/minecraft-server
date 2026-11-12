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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Fuga da Lava, conferida sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Um vão de cinco blocos no meio da rampa: ninguém passa, e a rodada inteira
 * trava ali com a lava subindo por baixo. Uma hélice apertada demais: a volta
 * de cima vira teto na de baixo e não dá para correr em pé. Pedra rachada na
 * beirada de um vão: o chão some no impulso do salto, e a morte não teve
 * escolha. Uma lava rápida demais: ninguém chega ao topo nunca, e o jogo vira
 * sorteio. Uma lava lenta demais: todo mundo chega, e não é jogo.
 *
 * <p>Nada disso quebra. Tudo isso deixa o jogo jogável e ERRADO. Por isso o
 * teste ANDA a rampa célula por célula, e mede a corrida contra o relógio da
 * lava.
 */
class LavaTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 60;
    private static final int ALTURA = 60;
    private static final int RAIO = 14;
    private static final int LARGURA = 3;
    private static final int PASSO = 4;
    private static final int PILAR_RAIO = 3;
    private static final int MURO_RECUO = 3;
    private static final int VAO_A_CADA = 20;
    private static final int VAO_TAMANHO = 3;
    private static final int RACHADA_A_CADA = 13;
    private static final int RACHADA_TAMANHO = 2;
    private static final int SAGUAO_FOLGA = 10;
    private static final int CARENCIA = 10;
    private static final int SEGUNDOS_POR_FIADA = 3;
    private static final int ACELERA_A_CADA = 12;
    private static final int RACHADA_TIQUES = 30;
    private static final int TEMPO_LIMITE = 240;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;

    /** Quantos blocos por segundo um jogador faz subindo degraus de um. */
    private static final double VELOCIDADE = 4.0;

    private static final int MURO_RAIO = RAIO + LARGURA / 2 + MURO_RECUO;
    private static final int CUME = Y + ALTURA;

    private static List<double[]> helice;
    private static Map<Long, Integer> celulas;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = LavaTest.class.getResourceAsStream("/lava.yml")) {
            assertNotNull(in, "lava.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        helice = LavaCatalog.helice(RAIO, PASSO, ALTURA, Y, LavaCatalog.AMOSTRA);
        celulas = LavaCatalog.celulas(helice, LARGURA, VAO_A_CADA, VAO_TAMANHO,
                RACHADA_A_CADA, RACHADA_TAMANHO);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("lava", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(ALTURA, arena.getInt("arena.altura"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(LARGURA, arena.getInt("arena.largura"));
        assertEquals(PASSO, arena.getInt("arena.passo-da-subida"));
        assertEquals(PILAR_RAIO, arena.getInt("arena.pilar-raio"));
        assertEquals(MURO_RECUO, arena.getInt("arena.muro-recuo"));
        assertEquals(VAO_A_CADA, arena.getInt("arena.vao-a-cada"));
        assertEquals(VAO_TAMANHO, arena.getInt("arena.vao-tamanho"));
        assertEquals(RACHADA_A_CADA, arena.getInt("arena.rachada-a-cada"));
        assertEquals(RACHADA_TAMANHO, arena.getInt("arena.rachada-tamanho"));
        assertEquals(SAGUAO_FOLGA, arena.getInt("arena.saguao-folga"));
        assertEquals(CARENCIA, arena.getInt("regras.carencia-segundos"));
        assertEquals(SEGUNDOS_POR_FIADA, arena.getInt("regras.segundos-por-fiada"));
        assertEquals(ACELERA_A_CADA, arena.getInt("regras.acelera-a-cada"));
        assertEquals(RACHADA_TIQUES, arena.getInt("regras.rachada-tiques"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
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
        assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"), nome);
    }

    @Test
    @DisplayName("a arena tem mundo próprio")
    void arenaTemMundoProprio() {
        assertEquals("lava", arena.getString("mundo"));
    }

    /**
     * A pedra que cede tem de se distinguir da que não cede.
     *
     * É a única pista que o jogador tem antes de pisar. Mesma cor que a
     * rampa, e o jogo passa a matar sem aviso.
     */
    @Test
    @DisplayName("a pedra rachada não tem a cor da rampa")
    void rachadaSeDistingueDaRampa() {
        assertFalse(arena.getString("paleta.rachada", "a")
                .equals(arena.getString("paleta.rampa", "b")));
        assertFalse(arena.getString("paleta.rachada", "a")
                .equals(arena.getString("paleta.rampa-borda", "b")));
    }

    // ------------------------------------------------------------------
    //  A hélice
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a hélice sobe sempre e termina no cume")
    void heliceSobeAteOCume() {
        double anterior = helice.get(0)[1];
        for (double[] ponto : helice) {
            assertTrue(ponto[1] >= anterior, "a hélice desceu em " + ponto[3]);
            assertTrue(ponto[1] - anterior <= 1,
                    "a hélice subiu " + (ponto[1] - anterior) + " de uma vez");
            anterior = ponto[1];
        }
        assertEquals(Y + 1, (int) helice.get(0)[1]);
        assertEquals(CUME + 1, (int) helice.get(helice.size() - 1)[1]);
    }

    @Test
    @DisplayName("a rampa cabe entre o pilar e o muro")
    void rampaCabeNoPoco() {
        assertNull(LavaBuilder.conferirRampa(helice, celulas, RAIO, LARGURA,
                PILAR_RAIO, MURO_RAIO, PASSO));
        for (Map.Entry<Long, Integer> celula : celulas.entrySet()) {
            int x = LavaBuilder.desempacotar(celula.getKey(), 40);
            int z = LavaBuilder.desempacotar(celula.getKey(), 0);
            double r = Math.hypot(x, z);
            assertTrue(r < MURO_RAIO, "célula em " + x + "," + z + " atravessa o muro");
            assertTrue(r > PILAR_RAIO, "célula em " + x + "," + z + " entra no pilar");
        }
    }

    /**
     * O pé direito entre uma volta e a de cima.
     *
     * Com raio catorze e passo três cada volta ganha vinte e nove fiadas, mas
     * é a conta que importa e não o número: quem mexer no raio no yml tem de
     * ser avisado aqui, e não descobrir em jogo que a rampa virou um túnel de
     * um bloco de altura.
     */
    @Test
    @DisplayName("nenhuma volta vira teto da volta de baixo")
    void peDireitoEntreVoltas() {
        for (Long chave : celulas.keySet()) {
            int x = LavaBuilder.desempacotar(chave, 40);
            int y = LavaBuilder.desempacotar(chave, 20);
            int z = LavaBuilder.desempacotar(chave, 0);

            // Célula com outra logo em cima é espelho de degrau: a de baixo
            // está enterrada e ninguém anda nela. Quem precisa de pé direito
            // é a exposta.
            if (celulas.containsKey(ArenaComum.chave(x, y + 1, z))) {
                continue;
            }
            for (int alto = 2; alto <= 3; alto++) {
                assertFalse(celulas.containsKey(ArenaComum.chave(x, y + alto, z)),
                        "há rampa " + alto + " fiadas acima de " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    @DisplayName("a rampa é subível de ponta a ponta")
    void rampaEhSubivel() {
        assertNull(LavaBuilder.conferirSaltos(helice, celulas, LARGURA));
    }

    /** Os vãos: onde a hélice tem ponto mas não tem chão. */
    private static List<double[]> vaos() {
        List<double[]> saida = new ArrayList<>();
        double[] antes = null;
        for (double[] ponto : helice) {
            boolean chao = celulas.containsKey(ArenaComum.chave(
                    (int) Math.floor(ponto[0] + 0.5), (int) ponto[1],
                    (int) Math.floor(ponto[2] + 0.5)));
            if (!chao) {
                continue;
            }
            if (antes != null && ponto[3] - antes[3] > LavaCatalog.AMOSTRA * 1.5) {
                saida.add(new double[]{antes[3], ponto[3],
                        Math.hypot(ponto[0] - antes[0], ponto[2] - antes[2]),
                        ponto[1] - antes[1]});
            }
            antes = ponto;
        }
        return saida;
    }

    @Test
    @DisplayName("a rampa tem vãos, e todos são saltáveis")
    void vaosExistemESaoSaltaveis() {
        List<double[]> buracos = vaos();
        assertTrue(buracos.size() >= 5, "a rampa saiu com só " + buracos.size()
                + " vãos: a subida vira caminhada");
        for (double[] vao : buracos) {
            double alcance = vao[3] >= 1 ? 4.0 : 5.0;
            assertTrue(vao[2] <= alcance, "o vão em " + vao[0] + " mede " + vao[2]
                    + " subindo " + vao[3]);
            assertTrue(vao[2] >= 1.5, "o vão em " + vao[0] + " mede só " + vao[2]
                    + ": não é vão, é uma fresta");
        }
    }

    /**
     * Pedra que cede na beirada do vão é morte sem escolha.
     *
     * O jogador pisa para tomar impulso e o chão some no impulso. Por isso a
     * rachada é adiada quando cai perto de um vão — e por isso aqui se exige
     * chão firme dos dois lados de cada rachada.
     */
    @Test
    @DisplayName("nenhuma pedra rachada fica na beirada de um vão")
    void rachadaLongeDosVaos() {
        int rachadas = 0;
        for (Map.Entry<Long, Integer> celula : celulas.entrySet()) {
            if (celula.getValue() != LavaCatalog.RACHADA) {
                continue;
            }
            rachadas++;
            int x = LavaBuilder.desempacotar(celula.getKey(), 40);
            int y = LavaBuilder.desempacotar(celula.getKey(), 20);
            int z = LavaBuilder.desempacotar(celula.getKey(), 0);
            int vizinhos = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dz == 0 && dy == 0) {
                            continue;
                        }
                        if (celulas.containsKey(
                                ArenaComum.chave(x + dx, y + dy, z + dz))) {
                            vizinhos++;
                        }
                    }
                }
            }
            assertTrue(vizinhos >= 4, "a rachada em " + x + "," + y + "," + z
                    + " tem só " + vizinhos + " vizinhos: está na beirada");
        }
        assertTrue(rachadas >= 20, "só " + rachadas + " células de pedra rachada");
    }

    @Test
    @DisplayName("a largada é no pé da rampa e a saída no cume")
    void largadaEmBaixoSaidaEmCima() {
        boolean achouLargada = false;
        boolean achouSaida = false;
        for (Map.Entry<Long, Integer> celula : celulas.entrySet()) {
            int y = LavaBuilder.desempacotar(celula.getKey(), 20);
            if (celula.getValue() == LavaCatalog.LARGADA) {
                achouLargada = true;
                assertTrue(y <= Y + 5, "largada alta demais, em y " + y);
            }
            if (celula.getValue() == LavaCatalog.SAIDA) {
                achouSaida = true;
                assertTrue(y >= CUME - 5, "saída baixa demais, em y " + y);
            }
        }
        assertTrue(achouLargada, "a rampa não tem largada");
        assertTrue(achouSaida, "a rampa não tem saída");
    }

    // ------------------------------------------------------------------
    //  A lava
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a lava sobe sempre e nunca desce")
    void lavaSobeSempre() {
        int anterior = 0;
        for (int s = 0; s <= 400; s++) {
            int fiadas = LavaCatalog.fiadasSubidas(s, SEGUNDOS_POR_FIADA,
                    ACELERA_A_CADA);
            assertTrue(fiadas >= anterior, "a lava desceu em " + s + "s");
            assertTrue(fiadas - anterior <= 1, "a lava pulou fiada em " + s + "s");
            anterior = fiadas;
        }
        assertTrue(anterior >= ALTURA, "em 400s a lava só subiu " + anterior);
    }

    @Test
    @DisplayName("a lava acelera")
    void lavaAcelera() {
        int primeira = LavaCatalog.segundosAte(1, SEGUNDOS_POR_FIADA, ACELERA_A_CADA);
        int depois = LavaCatalog.segundosAte(ACELERA_A_CADA + 1, SEGUNDOS_POR_FIADA,
                ACELERA_A_CADA)
                - LavaCatalog.segundosAte(ACELERA_A_CADA, SEGUNDOS_POR_FIADA,
                        ACELERA_A_CADA);
        assertTrue(depois < primeira, "a fiada " + (ACELERA_A_CADA + 1)
                + " custa " + depois + "s e a primeira custou " + primeira + "s");
    }

    @Test
    @DisplayName("as duas contas da lava são a mesma conta")
    void segundosEFiadasBatem() {
        for (int fiadas = 1; fiadas <= ALTURA; fiadas++) {
            int quando = LavaCatalog.segundosAte(fiadas, SEGUNDOS_POR_FIADA,
                    ACELERA_A_CADA);
            assertEquals(fiadas, LavaCatalog.fiadasSubidas(quando,
                            SEGUNDOS_POR_FIADA, ACELERA_A_CADA),
                    "aos " + quando + "s a lava devia estar na fiada " + fiadas);
        }
    }

    @Test
    @DisplayName("a lava chega ao topo antes do tempo limite")
    void lavaChegaAntesDoRelogio() {
        int subida = LavaCatalog.segundosAte(ALTURA, SEGUNDOS_POR_FIADA,
                ACELERA_A_CADA);
        assertTrue(subida + CARENCIA < TEMPO_LIMITE,
                "a lava leva " + (subida + CARENCIA) + "s e o teto é " + TEMPO_LIMITE
                        + ": a rodada acabaria no relógio e não no jogo");
    }

    /**
     * O equilíbrio do jogo inteiro, numa conta só.
     *
     * Uma subida limpa tem de ganhar da lava — senão o jogo é sorteio — mas
     * não pode ganhar de goleada, senão ninguém sente a lava e a arena podia
     * ser uma escada. Entre uma vez e meia e três vezes é a faixa em que a
     * subida é corrida.
     */
    @Test
    @DisplayName("uma subida limpa ganha da lava, mas não de goleada")
    void subidaLimpaGanhaDaLava() {
        double subida = (ALTURA * PASSO) / VELOCIDADE;
        int lava = CARENCIA + LavaCatalog.segundosAte(ALTURA, SEGUNDOS_POR_FIADA,
                ACELERA_A_CADA);
        double folga = lava / subida;
        assertTrue(folga >= 1.3, "a lava chega ao topo em " + lava + "s e a subida "
                + "limpa leva " + (int) subida + "s: ninguém escapa");
        assertTrue(folga <= 3.0, "a lava chega ao topo em " + lava + "s e a subida "
                + "limpa leva " + (int) subida + "s: a lava é enfeite");
    }

    @Test
    @DisplayName("a pedra rachada dá tempo de sair de cima")
    void rachadaDaTempoDeSair() {
        assertTrue(RACHADA_TIQUES >= 20, "a rachada cede em " + RACHADA_TIQUES
                + " tiques: some debaixo do pé antes do segundo passo");
        assertTrue(RACHADA_TIQUES <= 60, "a rachada aguenta " + RACHADA_TIQUES
                + " tiques: dá para ir e voltar");
    }

    // ------------------------------------------------------------------
    //  A chave empacotada
    // ------------------------------------------------------------------

    /**
     * Ida e volta da chave, inclusive com coordenada negativa.
     *
     * Metade da arena tem x ou z negativo, e um empacotamento que perde o
     * sinal põe a rampa inteira do lado errado do poço — sem erro nenhum,
     * porque bloco de ar aceita qualquer coordenada.
     */
    @Test
    @DisplayName("a chave empacotada volta igual ao que entrou")
    void chaveVoltaIgual() {
        int[][] casos = {{0, 0, 0}, {17, 61, -3}, {-14, 120, 14}, {-1, -1, -1},
                {500, 300, -500}};
        for (int[] caso : casos) {
            long chave = ArenaComum.chave(caso[0], caso[1], caso[2]);
            assertEquals(caso[0], LavaBuilder.desempacotar(chave, 40), "x");
            assertEquals(caso[1], LavaBuilder.desempacotar(chave, 20), "y");
            assertEquals(caso[2], LavaBuilder.desempacotar(chave, 0), "z");
        }
    }

    @Test
    @DisplayName("a conferência reprova uma hélice que vira túnel")
    void conferenciaReprovaHeliceApertada() {
        List<double[]> apertada = LavaCatalog.helice(5, 3, 20, Y, LavaCatalog.AMOSTRA);
        Map<Long, Integer> suas = LavaCatalog.celulas(apertada, LARGURA, VAO_A_CADA,
                VAO_TAMANHO, RACHADA_A_CADA, RACHADA_TAMANHO);
        assertNotNull(LavaBuilder.conferirRampa(apertada, suas, 5, LARGURA,
                PILAR_RAIO, MURO_RAIO, 3),
                "uma hélice de raio 5 em volta de um pilar de raio 3 tinha de "
                        + "ser reprovada");
    }

    @Test
    @DisplayName("a conferência reprova um vão que ninguém salta")
    void conferenciaReprovaVaoEnorme() {
        Map<Long, Integer> rasgada = LavaCatalog.celulas(helice, LARGURA,
                VAO_A_CADA, 8, RACHADA_A_CADA, RACHADA_TAMANHO);
        assertNotNull(LavaBuilder.conferirSaltos(helice, rasgada, LARGURA),
                "um vão de oito blocos tinha de ser reprovado");
    }
}
