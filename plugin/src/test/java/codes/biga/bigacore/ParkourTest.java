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
import java.util.HashMap;
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
 * O Parkour, conferido sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Um salto de cinco blocos subindo: intransponível, e o jogo fica lá,
 * parecendo que o jogador é ruim. Duas plataformas encostadas: o salto
 * simplesmente deixa de existir e o percurso encurta sozinho. Uma folha três
 * fiadas acima de uma plataforma: teto no pulo, e o percurso trava num ponto
 * só, sem erro nenhum no console. Dois pedaços de plataforma na mesma casa:
 * progresso de graça, porque o mapa de posição guarda o último que escreveu.
 *
 * <p>Nenhuma dessas quebra. Todas deixam o jogo jogável e ERRADO, que é o
 * pior estado possível. Cada teste daqui é uma delas.
 */
class ParkourTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int SALTOS = 32;
    private static final int RAIO = 11;
    private static final int CHECKPOINT_A_CADA = 6;
    private static final int QUEDA_ABAIXO = 8;
    private static final int SAGUAO_FOLGA = 12;
    private static final int SAGUAO_RAIO = 9;
    private static final int SAGUAO_PARAPEITO = 4;
    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int CONTAGEM = 15;
    private static final int FIM = 8;
    private static final int TEMPO_LIMITE = 300;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = ParkourTest.class.getResourceAsStream("/parkour.yml")) {
            assertNotNull(in, "parkour.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<int[]> percurso() {
        return ParkourCatalog.percurso(SALTOS, RAIO, Y, CHECKPOINT_A_CADA);
    }

    /** Quantos blocos para cada lado tem a plataforma de cada tipo. */
    private static int pad(int tipo) {
        return switch (tipo) {
            case ParkourCatalog.LARGADA, ParkourCatalog.CHEGADA -> 2;
            case ParkourCatalog.CHECKPOINT -> 1;
            default -> 0;
        };
    }

    private static int cumeY() {
        List<int[]> pontos = percurso();
        return pontos.get(pontos.size() - 1)[1];
    }

    private static int saguaoY() {
        return cumeY() + SAGUAO_FOLGA;
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("parkour", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(SALTOS, arena.getInt("arena.saltos"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(CHECKPOINT_A_CADA, arena.getInt("arena.checkpoint-a-cada"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("arena.queda-abaixo"));
        assertEquals(SAGUAO_FOLGA, arena.getInt("arena.saguao-folga"));
        assertEquals(SAGUAO_RAIO, arena.getInt("arena.saguao-raio"));
        assertEquals(SAGUAO_PARAPEITO, arena.getInt("arena.saguao-parapeito"));
        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(CONTAGEM, arena.getInt("regras.contagem-segundos"));
        assertEquals(FIM, arena.getInt("regras.fim-segundos"));
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

    /**
     * A placa tem de ser de PAREDE, não de chão.
     *
     * O nome é conferido por sufixo e não pela tag do Bukkit de propósito:
     * {@code org.bukkit.Tag} precisa de servidor rodando. A tag de verdade
     * está em {@code CatalogoDeArena.materialDaPlaca()}.
     */
    @Test
    @DisplayName("a placa do saguão é de parede")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa", "");
        assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"),
                "paleta.placa precisa ser uma placa de parede: " + nome);
    }

    @Test
    @DisplayName("cada bloco do caminho é de uma cor diferente")
    void caminhoTemCoresDistintas() {
        List<String> chaves = List.of("salto", "salto-duro", "checkpoint",
                "largada", "chegada");
        Set<String> vistos = new HashSet<>();
        for (String chave : chaves) {
            String material = arena.getString("paleta." + chave);
            assertNotNull(material, "falta paleta." + chave);
            assertTrue(vistos.add(material), "paleta." + chave + " repete " + material
                    + ": o jogador não distingue checkpoint de plataforma comum");
        }
    }

    @Test
    @DisplayName("a arena tem mundo próprio")
    void arenaTemMundoProprio() {
        String mundo = arena.getString("mundo");
        assertEquals("parkour", mundo);
        assertFalse("world".equals(mundo) || "hub".equals(mundo)
                || "minigames".equals(mundo), "a arena cairia num mundo que já é de algo");
    }

    // ------------------------------------------------------------------
    //  O percurso
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o percurso tem a largada mais um ponto por salto")
    void percursoTemTodosOsSaltos() {
        assertEquals(SALTOS + 1, percurso().size());
        assertEquals(ParkourCatalog.LARGADA, percurso().get(0)[3]);
    }

    /**
     * O teste que justifica o gerador inteiro.
     *
     * Arredondar o ângulo para casa inteira mexe na distância real em até
     * meio bloco para cada lado, e meio bloco é a diferença entre o salto do
     * jogo e o salto impossível.
     */
    @Test
    @DisplayName("nenhum salto passa do alcance de um jogador")
    void nenhumSaltoEhImpossivel() {
        List<int[]> pontos = percurso();
        for (int i = 1; i < pontos.size(); i++) {
            int[] antes = pontos.get(i - 1);
            int[] agora = pontos.get(i);
            int dy = agora[1] - antes[1];
            double distancia = Math.hypot(agora[0] - antes[0], agora[2] - antes[2]);
            assertTrue(distancia <= ParkourCatalog.alcanceMaximo(dy),
                    "o salto " + i + " pede " + distancia + " subindo " + dy);
            assertTrue(distancia >= 1.5,
                    "o salto " + i + " tem só " + distancia + ": as plataformas"
                            + " se encostam e o salto some");
        }
    }

    @Test
    @DisplayName("nenhum salto sobe mais de um bloco nem desce")
    void saltoSobeNoMaximoUm() {
        List<int[]> pontos = percurso();
        for (int i = 1; i < pontos.size(); i++) {
            int dy = pontos.get(i)[1] - pontos.get(i - 1)[1];
            assertTrue(dy == 0 || dy == 1, "o salto " + i + " muda " + dy + " de altura");
        }
    }

    @Test
    @DisplayName("o percurso sobe de verdade e a chegada é o ponto mais alto")
    void chegadaEhOCume() {
        List<int[]> pontos = percurso();
        int ultimo = pontos.size() - 1;
        assertEquals(ParkourCatalog.CHEGADA, pontos.get(ultimo)[3]);
        int maisAlto = Y;
        for (int[] p : pontos) {
            maisAlto = Math.max(maisAlto, p[1]);
        }
        assertEquals(maisAlto, pontos.get(ultimo)[1], "a chegada não é o cume");
        assertTrue(pontos.get(ultimo)[1] - Y >= SALTOS / 2,
                "a torre subiu quase nada: " + (pontos.get(ultimo)[1] - Y) + " fiadas");
    }

    @Test
    @DisplayName("os checkpoints vêm de seis em seis e são pelo menos quatro")
    void checkpointsNoLugar() {
        List<int[]> pontos = percurso();
        List<Integer> onde = new ArrayList<>();
        for (int i = 1; i < pontos.size(); i++) {
            boolean deveria = i % CHECKPOINT_A_CADA == 0 && i != SALTOS;
            boolean eh = pontos.get(i)[3] == ParkourCatalog.CHECKPOINT;
            assertEquals(deveria, eh, "o ponto " + i + " devia ser checkpoint? "
                    + deveria);
            if (eh) {
                onde.add(i);
            }
        }
        assertTrue(onde.size() >= 4, "só " + onde.size() + " checkpoints em "
                + SALTOS + " saltos: cair no fim custaria o percurso inteiro");
    }

    /**
     * Duas plataformas não podem dividir uma casa.
     *
     * O jogo lê o progresso de um mapa de posição para número de salto, e num
     * mapa o último que escreve ganha. Duas plataformas sobrepostas dariam
     * progresso de graça a quem pisasse na sobra — e a sobreposição é fácil de
     * criar sem perceber, porque a largada e a chegada são 5x5 e o checkpoint
     * é 3x3.
     */
    @Test
    @DisplayName("nenhuma plataforma invade outra")
    void plataformasNaoSeSobrepoem() {
        List<int[]> pontos = percurso();
        Map<Long, Integer> dono = new HashMap<>();
        for (int i = 0; i < pontos.size(); i++) {
            int[] p = pontos.get(i);
            int raio = pad(p[3]);
            for (int ox = -raio; ox <= raio; ox++) {
                for (int oz = -raio; oz <= raio; oz++) {
                    long chave = Parkour.chave(p[0] + ox, p[1], p[2] + oz);
                    Integer antigo = dono.put(chave, i);
                    assertTrue(antigo == null || antigo == i,
                            "as plataformas " + antigo + " e " + i + " dividem uma casa");
                }
            }
        }
    }

    @Test
    @DisplayName("o caminho não passa por dentro do tronco")
    void caminhoNaoEncostaNoTronco() {
        for (int[] p : percurso()) {
            int borda = Math.max(Math.abs(p[0]), Math.abs(p[2])) - pad(p[3]);
            assertTrue(borda > 1, "a plataforma em " + p[0] + "," + p[2]
                    + " encosta no tronco 3x3");
        }
    }

    @Test
    @DisplayName("o caminho corre no anel do raio pedido")
    void caminhoSegueOAnel() {
        for (int[] p : percurso()) {
            double distancia = Math.hypot(p[0], p[2]);
            assertTrue(Math.abs(distancia - RAIO) <= 1.5,
                    "a plataforma em " + p[0] + "," + p[2] + " está a " + distancia
                            + " do eixo, e o anel é " + RAIO);
        }
    }

    @Test
    @DisplayName("o percurso cabe na área que a obra confere e apaga")
    void percursoCabeNaObra() {
        int alcance = RAIO + 14;
        int fundo = Y - 14;
        int teto = saguaoY() + SAGUAO_PARAPEITO + 6;
        for (int[] p : percurso()) {
            int borda = Math.max(Math.abs(p[0]), Math.abs(p[2])) + pad(p[3]);
            assertTrue(borda <= alcance, "a plataforma em " + p[0] + "," + p[2]
                    + " fica fora do alcance da obra");
            assertTrue(p[1] >= fundo && p[1] <= teto,
                    "a plataforma em y " + p[1] + " fica fora da altura da obra");
        }
    }

    /**
     * A copa é teto no pulo se vier a menos de quatro fiadas da plataforma.
     *
     * Quem está em pé num bloco tem o pé em y+1 e a cabeça em y+2, e o pulo
     * sobe 1,25. Uma folha em y+3 trava o pulo. É por isso que a copa começa
     * em cume+4 e não em cume+2, e é esta conta que o teste repete.
     */
    @Test
    @DisplayName("a copa não vira teto no pulo")
    void copaNaoEhTeto() {
        int cume = cumeY();
        Set<Long> folhas = new HashSet<>();
        int copa = cume + 6;
        for (int y = cume + 4; y <= saguaoY() - 1; y++) {
            int r = 13 - 2 * Math.abs(y - copa);
            if (r < 2) {
                continue;
            }
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r || (Math.abs(x) <= 1 && Math.abs(z) <= 1)) {
                        continue;
                    }
                    if (Math.floorMod(x * 7 + z * 3 + y, 5) == 0) {
                        continue;
                    }
                    folhas.add(Parkour.chave(x, y, z));
                }
            }
        }
        assertFalse(folhas.isEmpty(), "a copa saiu vazia");

        for (int[] p : percurso()) {
            int raio = pad(p[3]);
            for (int ox = -raio; ox <= raio; ox++) {
                for (int oz = -raio; oz <= raio; oz++) {
                    for (int alto = 1; alto <= 3; alto++) {
                        assertFalse(
                                folhas.contains(Parkour.chave(p[0] + ox, p[1] + alto,
                                        p[2] + oz)),
                                "há folha " + alto + " acima da plataforma em y " + p[1]);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("o saguão fica acima da copa, e o tronco não fura o piso dele")
    void saguaoAcimaDaCopa() {
        int topoDoTronco = saguaoY() - 1;
        assertTrue(saguaoY() > cumeY() + 10,
                "o saguão em y " + saguaoY() + " cairia dentro da copa");
        assertTrue(topoDoTronco < saguaoY(),
                "o tronco furaria o piso do saguão e apareceria dentro da sala");
        assertTrue(topoDoTronco >= cumeY() + 4,
                "o tronco para antes da copa e a copa ficaria flutuando");
        assertTrue(SAGUAO_RAIO + 2 < RAIO + 14, "o saguão não cabe na área da obra");
    }

    /**
     * A linha de queda tem de ficar abaixo da largada e acima do nada.
     *
     * Grudada na largada, quem está em pé no início já estaria caído. Longe
     * demais, a queda demora tanto que a volta ao checkpoint parece travamento.
     */
    @Test
    @DisplayName("a linha de queda fica abaixo da largada")
    void quedaAbaixoDaLargada() {
        int queda = Y - QUEDA_ABAIXO;
        assertTrue(queda < Y - 1, "a linha de queda pega quem está em pé na largada");
        assertTrue(queda > Y - 20, "a queda é longa demais para parecer queda");
    }

    @Test
    @DisplayName("o tempo limite dá para subir a torre")
    void tempoLimiteDaParaSubir() {
        assertTrue(TEMPO_LIMITE >= SALTOS * 4,
                "o teto de " + TEMPO_LIMITE + "s não dá quatro segundos por salto");
    }

    // ------------------------------------------------------------------
    //  O gerador
    // ------------------------------------------------------------------

    /**
     * O gerador não pode valer só para os números de hoje.
     *
     * A planta é editável: quem muda raio ou saltos no yml espera uma arena
     * diferente, não uma impossível. Este teste roda o gerador em quase
     * trezentas combinações e mede cada salto de cada uma.
     */
    @Test
    @DisplayName("o gerador aguenta qualquer raio e qualquer número de saltos")
    void geradorValeParaOutrosTamanhos() {
        int conferidas = 0;
        for (int raio = 6; raio <= 24; raio++) {
            for (int saltos = 8; saltos <= 64; saltos += 4) {
                List<int[]> pontos = ParkourCatalog.percurso(saltos, raio, Y,
                        CHECKPOINT_A_CADA);
                assertEquals(saltos + 1, pontos.size());
                assertNull(ParkourBuilder.conferirSaltos(pontos),
                        "raio " + raio + " com " + saltos + " saltos: "
                                + ParkourBuilder.conferirSaltos(pontos));
                conferidas++;
            }
        }
        assertTrue(conferidas >= 200, "conferiu só " + conferidas + " combinações");
    }

    @Test
    @DisplayName("o percurso é sempre o mesmo")
    void percursoEhDeterministico() {
        List<int[]> uma = percurso();
        List<int[]> outra = percurso();
        for (int i = 0; i < uma.size(); i++) {
            assertArrayEqualsLocal(uma.get(i), outra.get(i), i);
        }
    }

    private static void assertArrayEqualsLocal(int[] a, int[] b, int i) {
        assertEquals(a.length, b.length, "ponto " + i);
        for (int k = 0; k < a.length; k++) {
            assertEquals(a[k], b[k], "o ponto " + i + " mudou entre duas chamadas: "
                    + "o jogo, a obra e o teste deixariam de falar do mesmo lugar");
        }
    }

    @Test
    @DisplayName("a conferência de saltos pega um percurso impossível")
    void conferenciaPegaOImpossivel() {
        assertNull(ParkourBuilder.conferirSaltos(percurso()));

        assertNotNull(ParkourBuilder.conferirSaltos(List.of(
                new int[]{0, 100, 0, ParkourCatalog.LARGADA},
                new int[]{6, 101, 0, ParkourCatalog.CHEGADA})), "seis blocos subindo");

        assertNotNull(ParkourBuilder.conferirSaltos(List.of(
                new int[]{0, 100, 0, ParkourCatalog.LARGADA},
                new int[]{1, 100, 0, ParkourCatalog.CHEGADA})), "plataformas encostadas");

        assertNotNull(ParkourBuilder.conferirSaltos(List.of(
                new int[]{0, 100, 0, ParkourCatalog.LARGADA},
                new int[]{3, 103, 0, ParkourCatalog.CHEGADA})), "três de uma vez");
    }

    @Test
    @DisplayName("subir come alcance")
    void subirComeAlcance() {
        assertTrue(ParkourCatalog.alcanceMaximo(1) < ParkourCatalog.alcanceMaximo(0),
                "pular para cima tem de alcançar menos que pular no plano");
    }

    @Test
    @DisplayName("o primeiro terço é escada e o último é o longo")
    void dificuldadeCresce() {
        for (int i = 1; i <= SALTOS / 3; i++) {
            assertEquals(1, ParkourCatalog.subidaDoSalto(i, SALTOS),
                    "o salto " + i + " devia ser escada");
        }
        double primeiro = ParkourCatalog.cordaDoSalto(2, SALTOS);
        double ultimo = ParkourCatalog.cordaDoSalto(SALTOS - 2, SALTOS);
        assertTrue(ultimo > primeiro, "o fim não ficou mais difícil que o começo");
    }
}
