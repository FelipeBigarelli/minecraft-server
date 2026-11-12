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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Guerra de Neve, conferida sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * O sorteio dos abrigos desistir no meio e devolver seis em vez de dezesseis:
 * o campo fica aberto e o jogo vira tiro ao alvo. Um abrigo nascer em cima do
 * ponto de largada: alguém nasce dentro de um iglu e não sai. Um abrigo
 * nascer pendurado na beira: ele cobre o vazio e não o chão. A cúpula do iglu
 * sair sem telhado: quem está em cima atira para dentro. O aperto não chegar
 * ao monte antes do relógio: a rodada dos dois últimos escondidos acaba sem
 * vencedor. Uma fonte de luz perto do gelo: o campo DERRETE no meio da
 * partida.
 *
 * <p>Nada disso lança exceção. Tudo isso deixa o jogo de pé e errado. Por
 * isso o teste sorteia o campo trezentas vezes, mede todas as distâncias e
 * anda pelo relógio do aperto.
 */
class NeveTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 28;
    private static final int BEIRA_ANEIS = 2;
    private static final int MONTE_RAIO = 5;
    private static final int MONTE_ALTURA = 3;
    private static final int ABRIGOS = 16;
    private static final long SEMENTE = 70712L;
    private static final int FOLGA_CENTRO = 8;
    private static final int FOLGA_BEIRA = 7;
    private static final int LARGADA_RECUO = 3;
    private static final int SAGUAO_ALTURA = 16;
    private static final int SAGUAO_RAIO = 9;
    private static final int SAGUAO_PARAPEITO = 4;
    private static final int MARCO_RECUO = 8;
    private static final int MARCO_ALTURA = 22;

    private static final int MINIMO = 2;
    private static final int MAXIMO = 16;
    private static final int VIDAS = 3;
    private static final int BOLAS = 16;
    private static final double EMPURRAO = 0.85;
    private static final double EMPURRAO_ALTURA = 0.36;
    private static final int CARENCIA_TIQUES = 10;
    private static final int APERTO_SEGUNDOS = 90;
    private static final int APERTO_INTERVALO = 8;
    private static final int QUEDA_ABAIXO = 8;
    private static final int TEMPO_LIMITE = 300;

    private static final int ANEL_DA_LARGADA = RAIO - LARGADA_RECUO;

    /** Até onde a coluna do aperto varre, acima da fiada do campo. */
    private static final int TETO_DO_APERTO = MONTE_ALTURA + 5;

    /** As chaves da paleta que acabam no chão do campo ou num abrigo. */
    private static final List<String> CHAVES_DO_CAMPO = List.of(
            "piso", "beira", "monte", "monte-topo",
            "iglu", "iglu-porta", "mureta", "tronco", "folha");

    /** As chaves da paleta que só aparecem no saguão e nos marcos. */
    private static final List<String> CHAVES_DA_OBRA = List.of(
            "placa", "estrutura", "escuro", "borda", "metal", "vidro",
            "luz", "luz-fria", "nucleo");

    /**
     * O que acende, e por isso não pode encostar em gelo nem em neve.
     *
     * Gelo comum, neve e camada de neve DERRETEM quando a luz de bloco
     * vizinha passa de 11. Uma lanterna de enfeite no meio do campo não
     * quebra nada: só transforma, em dois minutos de rodada, metade da arena
     * num lago.
     */
    private static final Set<Material> ACENDEM = EnumSet.of(
            Material.SEA_LANTERN, Material.GLOWSTONE, Material.SHROOMLIGHT,
            Material.LANTERN, Material.SOUL_LANTERN, Material.TORCH,
            Material.SOUL_TORCH, Material.REDSTONE_TORCH, Material.END_ROD,
            Material.MAGMA_BLOCK, Material.JACK_O_LANTERN, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.FIRE, Material.LAVA,
            Material.BEACON, Material.CONDUIT, Material.GLOW_LICHEN,
            Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT,
            Material.PEARLESCENT_FROGLIGHT, Material.CRYING_OBSIDIAN,
            Material.RESPAWN_ANCHOR, Material.ENCHANTING_TABLE,
            Material.END_PORTAL_FRAME, Material.BREWING_STAND,
            Material.AMETHYST_CLUSTER, Material.SCULK_CATALYST,
            Material.LIGHT, Material.GLOWSTONE_DUST);

    /** Gelo que não derrete e escorrega: é disso que o monte tem de ser. */
    private static final Set<Material> GELO_FIRME = EnumSet.of(
            Material.PACKED_ICE, Material.BLUE_ICE);

    private static List<int[]> abrigos;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = NeveTest.class.getResourceAsStream("/neve.yml")) {
            assertNotNull(in, "neve.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        abrigos = NeveCatalog.abrigos(ABRIGOS, RAIO, SEMENTE,
                FOLGA_CENTRO, FOLGA_BEIRA);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor e o jogo esperam")
    void plantaConfere() {
        assertEquals("neve", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(BEIRA_ANEIS, arena.getInt("arena.beira-aneis"));
        assertEquals(MONTE_RAIO, arena.getInt("arena.monte-raio"));
        assertEquals(MONTE_ALTURA, arena.getInt("arena.monte-altura"));
        assertEquals(ABRIGOS, arena.getInt("arena.abrigos"));
        assertEquals(SEMENTE, arena.getLong("arena.semente"));
        assertEquals(FOLGA_CENTRO, arena.getInt("arena.abrigo-folga-centro"));
        assertEquals(FOLGA_BEIRA, arena.getInt("arena.abrigo-folga-beira"));
        assertEquals(LARGADA_RECUO, arena.getInt("arena.largada-recuo"));
        assertEquals(SAGUAO_ALTURA, arena.getInt("arena.saguao-altura"));
        assertEquals(SAGUAO_RAIO, arena.getInt("arena.saguao-raio"));
        assertEquals(SAGUAO_PARAPEITO, arena.getInt("arena.saguao-parapeito"));
        assertEquals(MARCO_RECUO, arena.getInt("arena.marco-recuo"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));

        assertEquals(MINIMO, arena.getInt("regras.minimo"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(VIDAS, arena.getInt("regras.vidas"));
        assertEquals(BOLAS, arena.getInt("regras.bolas"));
        assertEquals(EMPURRAO, arena.getDouble("regras.empurrao"), 1.0E-9);
        assertEquals(EMPURRAO_ALTURA, arena.getDouble("regras.empurrao-altura"),
                1.0E-9);
        assertEquals(CARENCIA_TIQUES, arena.getInt("regras.carencia-tiques"));
        assertEquals(APERTO_SEGUNDOS, arena.getInt("regras.aperto-segundos"));
        assertEquals(APERTO_INTERVALO, arena.getInt("regras.aperto-intervalo"));
        assertEquals(QUEDA_ABAIXO, arena.getInt("regras.queda-abaixo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("todo material da paleta existe")
    void paletaEhValida() {
        Map<String, Object> paleta = arena.getConfigurationSection("paleta")
                .getValues(false);
        assertFalse(paleta.isEmpty(), "a paleta ficou vazia");
        for (Map.Entry<String, Object> item : paleta.entrySet()) {
            assertNotNull(material(item.getKey()),
                    "paleta." + item.getKey() + " não é um material: "
                            + item.getValue());
        }
    }

    @Test
    @DisplayName("a placa do saguão é de parede")
    void placaEhDeParede() {
        String nome = arena.getString("paleta.placa", "");
        assertTrue(nome.toUpperCase(Locale.ROOT).endsWith("_WALL_SIGN"), nome);
    }

    /**
     * A paleta não cresceu sem alguém olhar o que o aperto come.
     *
     * {@code NeveCatalog.materiaisDoCampo()} lista à mão os materiais que o
     * aperto pode apagar. Material novo no campo que não esteja nessa lista
     * fica de pé depois de o chão em volta sumir — plataforma flutuante na
     * beira, fora do alcance do aperto para sempre. Este teste é o aviso:
     * mexeu na paleta, confira a lista.
     */
    @Test
    @DisplayName("a paleta não tem chave que ninguém classificou")
    void paletaEstaClassificada() {
        Set<String> todas = new TreeSet<>(arena.getConfigurationSection("paleta")
                .getKeys(false));
        Set<String> conhecidas = new TreeSet<>(CHAVES_DO_CAMPO);
        conhecidas.addAll(CHAVES_DA_OBRA);
        assertEquals(conhecidas, todas, "chave nova na paleta: classifique-a "
                + "em CHAVES_DO_CAMPO ou CHAVES_DA_OBRA e, se for do campo, "
                + "ponha o material em NeveCatalog.materiaisDoCampo()");
    }

    @Test
    @DisplayName("nada no campo acende, porque gelo e neve derretem com luz")
    void campoNaoTemLuz() {
        for (String chave : CHAVES_DO_CAMPO) {
            Material material = material(chave);
            assertFalse(ACENDEM.contains(material), "paleta." + chave + " = "
                    + material + " acende, e o campo é de neve e gelo");
        }
    }

    @Test
    @DisplayName("o monte é de gelo que escorrega e não derrete")
    void monteEhDeGeloFirme() {
        for (String chave : List.of("monte", "monte-topo")) {
            Material material = material(chave);
            assertTrue(GELO_FIRME.contains(material), "paleta." + chave + " = "
                    + material + ": o monte precisa de gelo que escorregue "
                    + "(é o preço de subir nele) e que não derreta");
        }
    }

    @Test
    @DisplayName("o painel de instruções diz como se joga")
    void textosExplicamOJogo() {
        assertEquals("GUERRA DE NEVE", arena.getString("textos.titulo"));
        assertFalse(arena.getString("textos.subtitulo", "").isBlank());
        List<String> regras = arena.getStringList("textos.regras");
        assertTrue(regras.size() >= 4, "o painel tem só " + regras.size()
                + " linha(s) de regra");
        assertTrue(regras.stream().anyMatch(linha -> linha.contains("ENTRAR")),
                "nenhuma linha diz para clicar na placa");
    }

    // ------------------------------------------------------------------
    //  Os abrigos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o sorteio entrega todos os abrigos pedidos")
    void abrigosCabemTodos() {
        assertEquals(ABRIGOS, abrigos.size(), "o sorteio desistiu no meio: "
                + "o campo de raio " + RAIO + " não cabe " + ABRIGOS
                + " abrigos com as folgas de hoje");
    }

    @Test
    @DisplayName("nenhum abrigo encosta em outro")
    void abrigosNaoSeTocam() {
        for (int i = 0; i < abrigos.size(); i++) {
            for (int j = i + 1; j < abrigos.size(); j++) {
                int[] um = abrigos.get(i);
                int[] outro = abrigos.get(j);
                double entre = Math.hypot(um[0] - outro[0], um[1] - outro[1]);
                assertTrue(entre >= um[3] + outro[3] + NeveCatalog.FOLGA_ENTRE,
                        "abrigos em (" + um[0] + "," + um[1] + ") e ("
                                + outro[0] + "," + outro[1] + ") estão a "
                                + String.format(Locale.ROOT, "%.2f", entre)
                                + " e viram uma parede só");
            }
        }
    }

    @Test
    @DisplayName("nenhum abrigo nasce no monte nem pendurado na beira")
    void abrigosFicamNaFaixaDoMeio() {
        for (int[] abrigo : abrigos) {
            double doCentro = Math.hypot(abrigo[0], abrigo[1]);
            assertTrue(doCentro - abrigo[3] >= FOLGA_CENTRO,
                    "abrigo a " + String.format(Locale.ROOT, "%.2f", doCentro)
                            + " do meio, em cima do monte");
            assertTrue(doCentro + abrigo[3] <= RAIO - FOLGA_BEIRA,
                    "abrigo a " + String.format(Locale.ROOT, "%.2f", doCentro)
                            + " do meio, pendurado na beira");
            assertTrue(abrigo[2] >= 0 && abrigo[2] <= NeveCatalog.PINHEIRO,
                    "tipo de abrigo desconhecido: " + abrigo[2]);
        }
    }

    @Test
    @DisplayName("o campo é sempre o mesmo com a mesma semente")
    void sorteioEhDeterministico() {
        List<int[]> outraVez = NeveCatalog.abrigos(ABRIGOS, RAIO, SEMENTE,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertEquals(abrigos.size(), outraVez.size());
        for (int i = 0; i < abrigos.size(); i++) {
            assertArrayEquals(abrigos.get(i), outraVez.get(i));
        }
    }

    @Test
    @DisplayName("sementes diferentes dão campos diferentes")
    void sementeMudaOCampo() {
        List<int[]> outro = NeveCatalog.abrigos(ABRIGOS, RAIO, SEMENTE + 1,
                FOLGA_CENTRO, FOLGA_BEIRA);
        boolean algumMudou = false;
        for (int i = 0; i < Math.min(abrigos.size(), outro.size()); i++) {
            if (abrigos.get(i)[0] != outro.get(i)[0]
                    || abrigos.get(i)[1] != outro.get(i)[1]) {
                algumMudou = true;
                break;
            }
        }
        assertTrue(algumMudou, "trocar a semente não mudou nada: o sorteio "
                + "não está usando a semente");
    }

    /**
     * O sorteio enche o campo em QUALQUER semente.
     *
     * A semente do yml é uma só, mas ela pode ser trocada por quem edita o
     * arquivo — e pior, o laço é "tenta até caber", de modo que uma planta
     * apertada funciona numa semente e devolve lista curta na seguinte. Se
     * trezentas sementes enchem, a planta tem folga de sobra.
     */
    @Test
    @DisplayName("o sorteio enche o campo em trezentas sementes")
    void sorteioAguentaQualquerSemente() {
        for (long semente = 0; semente < 300; semente++) {
            List<int[]> campo = NeveCatalog.abrigos(ABRIGOS, RAIO, semente,
                    FOLGA_CENTRO, FOLGA_BEIRA);
            assertEquals(ABRIGOS, campo.size(), "a semente " + semente
                    + " só coube " + campo.size() + " abrigos");
        }
    }

    @Test
    @DisplayName("o sorteio vale para outros tamanhos de campo")
    void sorteioValeParaOutrosTamanhos() {
        int conferidos = 0;
        for (int raio = 18; raio <= 34; raio += 2) {
            for (int quantos = 4; quantos <= 20; quantos += 4) {
                for (long semente = 0; semente < 3; semente++) {
                    List<int[]> campo = NeveCatalog.abrigos(quantos, raio,
                            semente, FOLGA_CENTRO, FOLGA_BEIRA);
                    assertTrue(campo.size() <= quantos,
                            "sorteou mais abrigos do que foram pedidos");
                    for (int i = 0; i < campo.size(); i++) {
                        int[] um = campo.get(i);
                        double doCentro = Math.hypot(um[0], um[1]);
                        assertTrue(doCentro - um[3] >= FOLGA_CENTRO,
                                "raio " + raio + ": abrigo no monte");
                        assertTrue(doCentro + um[3] <= raio - FOLGA_BEIRA,
                                "raio " + raio + ": abrigo na beira");
                        for (int j = i + 1; j < campo.size(); j++) {
                            int[] outro = campo.get(j);
                            assertTrue(Math.hypot(um[0] - outro[0],
                                            um[1] - outro[1])
                                            >= um[3] + outro[3]
                                            + NeveCatalog.FOLGA_ENTRE,
                                    "raio " + raio + ": abrigos grudados");
                        }
                    }
                    conferidos++;
                }
            }
        }
        assertEquals(135, conferidos, "o varredor mudou de tamanho");
    }

    @Test
    @DisplayName("a conferência reclama do campo que não cabe")
    void conferenciaPegaOCampoApertado() {
        List<int[]> poucos = NeveCatalog.abrigos(40, 14, SEMENTE,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertTrue(poucos.size() < 40, "40 abrigos não deveriam caber em "
                + "raio 14");
        String problema = NeveBuilder.conferirCampo(poucos, 40, 14,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertNotNull(problema, "a conferência passou um campo quase vazio");
        assertTrue(problema.contains("sorteio"), problema);
    }

    @Test
    @DisplayName("a conferência aprova o campo de verdade")
    void conferenciaAprovaOCampoReal() {
        assertNull(NeveBuilder.conferirCampo(abrigos, ABRIGOS, RAIO,
                FOLGA_CENTRO, FOLGA_BEIRA));
    }

    @Test
    @DisplayName("a conferência pega abrigo grudado")
    void conferenciaPegaAbrigoGrudado() {
        List<int[]> campo = new ArrayList<>();
        campo.add(new int[]{14, 0, NeveCatalog.IGLU, 3});
        campo.add(new int[]{15, 0, NeveCatalog.IGLU, 3});
        String problema = NeveBuilder.conferirCampo(campo, 2, RAIO,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertNotNull(problema, "dois iglus a um bloco passaram");
        assertTrue(problema.contains("grudados"), problema);
    }

    @Test
    @DisplayName("a conferência pega abrigo na beira e no meio")
    void conferenciaPegaAbrigoForaDaFaixa() {
        String naBeira = NeveBuilder.conferirCampo(
                List.of(new int[]{RAIO - 1, 0, NeveCatalog.IGLU, 3}), 1, RAIO,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertNotNull(naBeira, "abrigo na beira passou");
        assertTrue(naBeira.contains("beira"), naBeira);

        String noMeio = NeveBuilder.conferirCampo(
                List.of(new int[]{1, 0, NeveCatalog.IGLU, 3}), 1, RAIO,
                FOLGA_CENTRO, FOLGA_BEIRA);
        assertNotNull(noMeio, "abrigo no monte passou");
        assertTrue(noMeio.contains("monte"), noMeio);
    }

    // ------------------------------------------------------------------
    //  O iglu
    // ------------------------------------------------------------------

    /**
     * A cúpula fecha em cima.
     *
     * Iglu sem telhado é iglu que não protege de quem está no monte — que é
     * justamente de quem ele deveria proteger.
     */
    @Test
    @DisplayName("a cúpula do iglu fecha em cima")
    void cupulaFecha() {
        for (int raio = 3; raio <= 5; raio++) {
            assertTrue(NeveCatalog.naCasca(0, 0, raio, raio),
                    "a cúpula de raio " + raio + " não tem bloco no cume");
            for (int alto = 1; alto <= raio; alto++) {
                boolean algum = false;
                for (int x = -raio; x <= raio && !algum; x++) {
                    for (int z = -raio; z <= raio && !algum; z++) {
                        algum = NeveCatalog.naCasca(x, z, alto, raio);
                    }
                }
                assertTrue(algum, "a fiada " + alto + " da cúpula de raio "
                        + raio + " saiu vazia: buraco na casca");
            }
        }
    }

    /**
     * Dá para ficar em pé dentro do iglu.
     *
     * Duas fiadas de ar no eixo: um jogador tem 1,8 de altura, e cúpula de
     * uma fiada de pé direito é cúpula que ninguém usa.
     */
    @Test
    @DisplayName("dá para ficar em pé dentro do iglu")
    void igluTemPeDireito() {
        int raio = 3;
        assertFalse(NeveCatalog.naCasca(0, 0, 1, raio),
                "a primeira fiada do miolo do iglu é bloco");
        assertFalse(NeveCatalog.naCasca(0, 0, 2, raio),
                "a segunda fiada do miolo do iglu é bloco: não dá para "
                        + "ficar em pé lá dentro");
    }

    @Test
    @DisplayName("a porta do iglu está na casca e olha para o meio do campo")
    void portaDoIgluAbreParaODentro() {
        int raio = 3;
        for (int[] onde : new int[][]{{14, 0}, {-14, 0}, {0, 14}, {0, -14},
                {10, 10}, {-10, 10}, {10, -10}, {-10, -10}, {12, 5}, {-5, 12}}) {
            int[] porta = NeveCatalog.portaDoIglu(onde[0], onde[1], raio);
            assertTrue(NeveCatalog.naCasca(porta[0], porta[1], 1, raio),
                    "a porta de um iglu em (" + onde[0] + "," + onde[1]
                            + ") caiu em (" + porta[0] + "," + porta[1]
                            + "), que não é bloco da casca: ela abriria "
                            + "buraco no ar e o iglu ficaria fechado");
            double paraODentro = porta[0] * (double) -onde[0]
                    + porta[1] * (double) -onde[1];
            assertTrue(paraODentro > 0, "a porta de um iglu em (" + onde[0]
                    + "," + onde[1] + ") abriu para fora do campo");
        }
    }

    @Test
    @DisplayName("todo iglu do campo de verdade tem porta")
    void todoIgluTemPorta() {
        int iglus = 0;
        for (int[] abrigo : abrigos) {
            if (abrigo[2] != NeveCatalog.IGLU) {
                continue;
            }
            iglus++;
            int[] porta = NeveCatalog.portaDoIglu(abrigo[0], abrigo[1], abrigo[3]);
            assertTrue(NeveCatalog.naCasca(porta[0], porta[1], 1, abrigo[3]),
                    "o iglu em (" + abrigo[0] + "," + abrigo[1]
                            + ") ficou sem porta");
        }
        assertTrue(iglus > 0, "o campo de verdade não sorteou nenhum iglu");
    }

    // ------------------------------------------------------------------
    //  A largada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ninguém nasce dentro de um abrigo")
    void largadaForaDosAbrigos() {
        for (long semente = 0; semente < 300; semente++) {
            List<int[]> campo = NeveCatalog.abrigos(ABRIGOS, RAIO, semente,
                    FOLGA_CENTRO, FOLGA_BEIRA);
            for (int quantos = 1; quantos <= MAXIMO; quantos++) {
                for (int[] onde : NeveCatalog.largadas(quantos, ANEL_DA_LARGADA)) {
                    for (int[] abrigo : campo) {
                        double entre = Math.hypot(onde[0] - abrigo[0],
                                onde[1] - abrigo[1]);
                        assertTrue(entre >= abrigo[3] + 2,
                                "semente " + semente + ", " + quantos
                                        + " jogadores: largada em (" + onde[0]
                                        + "," + onde[1] + ") a "
                                        + String.format(Locale.ROOT, "%.2f",
                                                entre)
                                        + " do abrigo em (" + abrigo[0] + ","
                                        + abrigo[1] + ")");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("todo mundo nasce no chão de neve, fora do gelo da beira")
    void largadaFicaNoChao() {
        for (int quantos = 1; quantos <= MAXIMO; quantos++) {
            for (int[] onde : NeveCatalog.largadas(quantos, ANEL_DA_LARGADA)) {
                assertTrue(ArenaComum.noDisco(onde[0], onde[1],
                                RAIO - BEIRA_ANEIS),
                        "largada em (" + onde[0] + "," + onde[1] + ") cai no "
                                + "anel de gelo da beira, de onde se escorrega "
                                + "para o vazio");
                assertTrue(Math.hypot(onde[0], onde[1]) >= MONTE_RAIO + 3,
                        "largada em (" + onde[0] + "," + onde[1] + ") cai no "
                                + "monte do meio");
            }
        }
    }

    /**
     * Um jogador sozinho não nasce no meio.
     *
     * O {@link ArenaComum#largadas} larga quem está sozinho em (0,0), que
     * aqui é DENTRO do monte de gelo. É por isso que este jogo tem o sorteio
     * de largada dele.
     */
    @Test
    @DisplayName("quem joga sozinho não nasce dentro do monte")
    void treinoSozinhoNasceNoAnel() {
        List<int[]> um = NeveCatalog.largadas(1, ANEL_DA_LARGADA);
        assertEquals(1, um.size());
        assertEquals(ANEL_DA_LARGADA, Math.hypot(um.get(0)[0], um.get(0)[1]),
                0.51, "o jogador sozinho não caiu no anel");
        assertEquals(0, ArenaComum.largadas(1, RAIO).get(0)[0]);
        assertEquals(0, ArenaComum.largadas(1, RAIO).get(0)[1]);
    }

    @Test
    @DisplayName("a largada espalha o time em volta do campo")
    void largadaEspalha() {
        List<int[]> pontos = NeveCatalog.largadas(MAXIMO, ANEL_DA_LARGADA);
        assertEquals(MAXIMO, pontos.size());
        for (int i = 0; i < pontos.size(); i++) {
            int[] um = pontos.get(i);
            int[] proximo = pontos.get((i + 1) % pontos.size());
            double entre = Math.hypot(um[0] - proximo[0], um[1] - proximo[1]);
            assertTrue(entre >= 6, "dois vizinhos de largada a "
                    + String.format(Locale.ROOT, "%.2f", entre)
                    + ": com " + MAXIMO + " jogadores o anel de raio "
                    + ANEL_DA_LARGADA + " é apertado demais");
        }
    }

    // ------------------------------------------------------------------
    //  O aperto e o relógio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o campo fica inteiro até a hora marcada")
    void apertoEsperaAHora() {
        for (int segundos = 0; segundos < APERTO_SEGUNDOS; segundos++) {
            assertEquals(RAIO, ArenaComum.raioDoAperto(segundos, RAIO,
                    APERTO_SEGUNDOS, APERTO_INTERVALO), "o campo encolheu em "
                    + segundos + "s, antes da hora");
        }
        assertEquals(RAIO - 1, ArenaComum.raioDoAperto(APERTO_SEGUNDOS, RAIO,
                APERTO_SEGUNDOS, APERTO_INTERVALO));
    }

    /**
     * O aperto chega ao monte antes do relógio, com tempo de sobra para a
     * briga final — e não tão cedo que a partida inteira seja ela.
     */
    @Test
    @DisplayName("o aperto empurra todo mundo para o monte antes do fim")
    void apertoForcaOEncontro() {
        int chegou = -1;
        for (int segundos = 0; segundos <= TEMPO_LIMITE; segundos++) {
            if (ArenaComum.raioDoAperto(segundos, RAIO, APERTO_SEGUNDOS,
                    APERTO_INTERVALO) <= MONTE_RAIO) {
                chegou = segundos;
                break;
            }
        }
        assertTrue(chegou > 0, "o aperto nunca chega ao monte: a rodada dos "
                + "dois últimos escondidos acaba sem vencedor");
        int sobra = TEMPO_LIMITE - chegou;
        assertTrue(sobra >= 20, "o aperto só chega ao monte com " + sobra
                + "s de sobra: não dá tempo da briga final");
        assertTrue(sobra <= 120, "o aperto chega ao monte com " + sobra
                + "s de sobra: a partida inteira vira briga no monte");
    }

    @Test
    @DisplayName("o aperto não alcança o saguão nem os marcos")
    void apertoNaoComeAObra() {
        assertTrue(SAGUAO_ALTURA > TETO_DO_APERTO, "o saguão está a "
                + SAGUAO_ALTURA + " fiadas do campo e a varredura do aperto "
                + "sobe " + TETO_DO_APERTO + ": ela comeria o saguão");
        assertTrue(RAIO + MARCO_RECUO > RAIO, "os marcos estão dentro do "
                + "campo e o aperto os comeria");
        assertTrue(MARCO_RECUO >= 2, "os marcos encostam na beira do campo");
    }

    /**
     * A varredura do aperto é alta o bastante para comer o abrigo mais alto.
     *
     * O pinheiro é a coisa mais alta do campo: tronco de cinco e ponta na
     * sexta. Se a varredura parar na quinta, sobra uma ponta de folha
     * flutuando onde o chão sumiu.
     */
    @Test
    @DisplayName("a varredura do aperto alcança a ponta do pinheiro")
    void apertoAlcancaOPinheiro() {
        assertTrue(TETO_DO_APERTO >= 6, "a varredura do aperto sobe só "
                + TETO_DO_APERTO + " fiadas, e o pinheiro tem 6");
    }

    @Test
    @DisplayName("quem cai tem para onde cair, e nada embaixo")
    void quedaFicaAbaixoDoCampo() {
        assertTrue(QUEDA_ABAIXO >= 4, "o limite de queda está colado no chão: "
                + "um empurrão para cima já contaria como queda");
        assertTrue(QUEDA_ABAIXO <= 20, "o limite de queda está longe demais e "
                + "a queda demora a resolver");
        assertTrue(Y - QUEDA_ABAIXO > 0, "o limite de queda saiu do mundo");
    }

    @Test
    @DisplayName("o monte é uma escada e não uma parede")
    void monteEhEscalavel() {
        assertTrue(MONTE_ALTURA <= MONTE_RAIO, "o monte tem " + MONTE_ALTURA
                + " de alto e raio " + MONTE_RAIO + ": as fiadas de cima "
                + "viram pilar vertical, e pilar não se escala");
        for (int alto = 1; alto <= MONTE_ALTURA; alto++) {
            int r = Math.max(1, MONTE_RAIO - (alto - 1));
            int rAcima = Math.max(1, MONTE_RAIO - alto);
            assertTrue(r - rAcima <= 1, "a fiada " + alto + " do monte sobe "
                    + (r - rAcima) + " de uma vez: não dá para subir");
        }
    }

    // ------------------------------------------------------------------
    //  As regras
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o empurrão derruba de perto da beira e não do meio")
    void empurraoEhDoTamanhoCerto() {
        assertTrue(EMPURRAO >= 0.4 && EMPURRAO <= 1.5, "empurrão de "
                + EMPURRAO + ": ou não empurra nada, ou atravessa o campo");
        assertTrue(EMPURRAO_ALTURA > 0, "sem levantar nada, o empurrão não "
                + "tira ninguém do chão e o atrito come tudo");
        assertTrue(EMPURRAO_ALTURA < EMPURRAO, "o empurrão joga mais para "
                + "cima do que para o lado: todo acerto vira pulinho");
    }

    @Test
    @DisplayName("a carência cabe entre dois tiros e não mais que isso")
    void carenciaEhCurta() {
        assertTrue(CARENCIA_TIQUES >= 5, "carência de " + CARENCIA_TIQUES
                + " tiques: uma rajada de perto tira as " + VIDAS
                + " vidas de uma vez");
        assertTrue(CARENCIA_TIQUES <= 20, "carência de " + CARENCIA_TIQUES
                + " tiques é mais de um segundo de invencibilidade");
        assertTrue(VIDAS >= 2, "com uma vida só, o primeiro acerto da partida "
                + "elimina — e aí o jogo é sobre quem atira primeiro");
        assertTrue(BOLAS >= 8, "só " + BOLAS + " bolas na mão: entre duas "
                + "reposições o jogador fica desarmado");
    }

    @Test
    @DisplayName("o campo tem chão de sobra para a sala cheia")
    void campoCabeASalaCheia() {
        double area = Math.PI * RAIO * RAIO;
        double porJogador = area / MAXIMO;
        assertTrue(porJogador >= 60, "sobram só "
                + Math.round(porJogador) + " blocos por jogador com "
                + MAXIMO + " na arena");
        assertTrue(porJogador <= 400, "sobram " + Math.round(porJogador)
                + " blocos por jogador: o campo é tão grande que ninguém se "
                + "encontra antes do aperto");
    }

    // ------------------------------------------------------------------
    //  Ferramentas
    // ------------------------------------------------------------------

    private static Material material(String chave) {
        return Material.matchMaterial(arena.getString("paleta." + chave, "")
                .toUpperCase(Locale.ROOT));
    }

    private static void assertArrayEquals(int[] esperado, int[] veio) {
        assertEquals(esperado.length, veio.length);
        for (int i = 0; i < esperado.length; i++) {
            assertEquals(esperado[i], veio[i], "posição " + i);
        }
    }
}
