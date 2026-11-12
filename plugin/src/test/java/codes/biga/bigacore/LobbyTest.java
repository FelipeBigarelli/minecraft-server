package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O lobby dos minigames, conferido sem servidor.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * O lobby é levantado por um comando, de uma vez, com cerca de duzentos mil
 * blocos. Erro de planta aqui não vira exceção: vira obra torta que só se
 * descobre andando nela, e desfazer e refazer custa minutos de servidor
 * parado. Cada teste abaixo corresponde a um defeito que a planta teve de
 * verdade antes de virar bloco.
 */
class LobbyTest {

    private static YamlConfiguration lobby;
    private static List<Map<?, ?>> portais;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int PATIO = 43;
    private static final int PROFUNDIDADE = 10;
    private static final int ALTURA_GALERIA = 20;
    private static final int VAO = 3;
    private static final int ALTURA_ARCO = 11;
    private static final int PILAR = 3;
    private static final int TORRE_CENTRO = 36;
    private static final int TORRE_MEIA = 6;
    private static final int MONUMENTO = 11;
    private static final List<Integer> VAOS = List.of(-28, -14, 0, 14, 28);

    private static int galeriaFim() {
        return PATIO + PROFUNDIDADE;
    }

    private static int camaraMeiaLargura() {
        return VAO + PILAR - 1;
    }

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = LobbyTest.class.getResourceAsStream("/minigames.yml")) {
            assertNotNull(in, "minigames.yml não foi empacotado");
            lobby = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        portais = lobby.getMapList("portais");
    }

    private static String texto(Map<?, ?> item, String chave) {
        Object valor = item.get(chave);
        return valor == null ? "" : String.valueOf(valor);
    }

    // ------------------------------------------------------------------
    //  O arquivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a planta tem os números que o construtor espera")
    void geometriaConfere() {
        assertEquals(PATIO, lobby.getInt("praca.patio"));
        assertEquals(PROFUNDIDADE, lobby.getInt("praca.galeria-profundidade"));
        assertEquals(ALTURA_GALERIA, lobby.getInt("praca.galeria-altura"));
        assertEquals(VAO, lobby.getInt("praca.vao-largura"));
        assertEquals(ALTURA_ARCO, lobby.getInt("praca.vao-altura"));
        assertEquals(PILAR, lobby.getInt("praca.nicho-pilar"));
        assertEquals(TORRE_CENTRO, lobby.getInt("praca.torre-centro"));
        assertEquals(TORRE_MEIA * 2 + 1, lobby.getInt("praca.torre-lado"));
        assertEquals(MONUMENTO, lobby.getInt("praca.monumento-raio"));
        assertEquals(VAOS, lobby.getIntegerList("praca.vaos"));
    }

    @Test
    @DisplayName("são vinte portais, cinco por galeria, sem nicho repetido nem vazio")
    void vinteNichosOcupados() {
        assertEquals(20, portais.size(), "o lobby foi desenhado para vinte jogos");

        Set<String> vagas = new HashSet<>();
        for (Map<?, ?> item : portais) {
            String vaga = texto(item, "lado") + ":" + texto(item, "vao");
            assertTrue(vagas.add(vaga), "dois portais no mesmo nicho: " + vaga);
        }
        for (String lado : new String[]{"NORTE", "LESTE", "SUL", "OESTE"}) {
            for (int vao = -2; vao <= 2; vao++) {
                assertTrue(vagas.contains(lado + ":" + vao),
                        "nicho vazio: " + lado + ":" + vao);
            }
        }
    }

    @Test
    @DisplayName("o catálogo também recusa dois portais no mesmo nicho")
    void catalogoPegaVagaRepetida() {
        List<MinigamesCatalog.Portal> dois = List.of(
                portalDeTeste("a", MinigamesCatalog.Lado.NORTE, 0),
                portalDeTeste("b", MinigamesCatalog.Lado.NORTE, 0));
        assertFalse(MinigamesCatalog.vagasSaoUnicas(dois));

        List<MinigamesCatalog.Portal> distintos = List.of(
                portalDeTeste("a", MinigamesCatalog.Lado.NORTE, 0),
                portalDeTeste("b", MinigamesCatalog.Lado.NORTE, 1));
        assertTrue(MinigamesCatalog.vagasSaoUnicas(distintos));
    }

    private static MinigamesCatalog.Portal portalDeTeste(String id,
                                                         MinigamesCatalog.Lado lado, int vao) {
        return new MinigamesCatalog.Portal(id, lado, vao, id, "", "", "", "#FFFFFF",
                Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE, "");
    }

    @Test
    @DisplayName("todo id é único e toda cor é #RRGGBB")
    void idsECores() {
        Set<String> ids = new HashSet<>();
        for (Map<?, ?> item : portais) {
            String id = texto(item, "id");
            assertFalse(id.isBlank(), "portal sem id");
            assertTrue(ids.add(id), "id repetido: " + id);
            assertTrue(texto(item, "cor").matches("#[0-9A-Fa-f]{6}"),
                    "cor fora do formato em " + id + ": " + texto(item, "cor"));
            assertFalse(texto(item, "titulo").isBlank(), "portal sem título: " + id);
            assertFalse(texto(item, "descricao").isBlank(), "portal sem descrição: " + id);
        }
    }

    /**
     * Material escrito errado não dá erro: o catálogo troca pelo padrão e
     * avisa no console, que ninguém lê. O jogo sai com vinte nichos da mesma
     * cor e o motivo fica num log de três dias atrás.
     */
    @Test
    @DisplayName("todo material do arquivo existe de verdade")
    void materiaisExistem() {
        for (Map<?, ?> item : portais) {
            for (String chave : new String[]{"moldura", "destaque"}) {
                String nome = texto(item, chave);
                assertNotNull(Material.matchMaterial(nome.toUpperCase(Locale.ROOT)),
                        "material inexistente em " + texto(item, "id") + "." + chave
                                + ": " + nome);
            }
        }
        ConfigurationSection paleta = lobby.getConfigurationSection("paleta");
        assertNotNull(paleta, "o minigames.yml tem de ter paleta");
        for (String chave : paleta.getKeys(false)) {
            String nome = String.valueOf(paleta.get(chave));
            assertNotNull(Material.matchMaterial(nome.toUpperCase(Locale.ROOT)),
                    "material inexistente em paleta." + chave + ": " + nome);
        }
        for (String chave : new String[]{"moldura", "destaque"}) {
            String nome = lobby.getString("volta." + chave, "");
            assertNotNull(Material.matchMaterial(nome.toUpperCase(Locale.ROOT)),
                    "material inexistente em volta." + chave + ": " + nome);
        }
    }

    /**
     * O bloco de destaque é o chão da câmara e o campo da porta. Um que faz
     * dano põe o jogador tomando fogo por estar parado no portal — e o
     * MAGMA_BLOCK é exatamente o bloco laranja que alguém escolheria para o
     * jogo da lava.
     */
    @Test
    @DisplayName("nenhum destaque é bloco que machuca quem pisa")
    void destaqueNaoMachuca() {
        Set<String> proibidos = Set.of("MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE",
                "CACTUS", "SWEET_BERRY_BUSH", "POINTED_DRIPSTONE", "LAVA", "FIRE",
                "WITHER_ROSE", "POWDER_SNOW");
        for (Map<?, ?> item : portais) {
            String nome = texto(item, "destaque").toUpperCase(Locale.ROOT);
            assertFalse(proibidos.contains(nome),
                    "destaque que machuca em " + texto(item, "id") + ": " + nome);
        }
    }

    // ------------------------------------------------------------------
    //  Os emblemas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todo emblema existe, é retangular e cabe na câmara")
    void emblemasCabem() {
        ConfigurationSection secao = lobby.getConfigurationSection("esculturas");
        assertNotNull(secao, "o minigames.yml tem de ter esculturas");

        // A conta do construtor: a porta vai do 1 ao 7, a verga dela ocupa o
        // 8 e o teto da câmara é o 19 — sobram onze fiadas, do 9 ao 19.
        int base = 7 + 2;
        int disponivel = ALTURA_GALERIA - 1 - base + 1;
        assertEquals(11, disponivel, "a conta da altura do emblema mudou");

        for (Map<?, ?> item : portais) {
            String nome = texto(item, "escultura");
            assertFalse(nome.isBlank(), "portal sem emblema: " + texto(item, "id"));
            List<String> desenho = secao.getStringList(nome);
            assertFalse(desenho.isEmpty(), "emblema que não existe: " + nome);
            assertTrue(HubCatalog.desenhoEhRetangular(desenho),
                    "emblema com linhas de tamanhos diferentes: " + nome);
            assertTrue(MinigamesCatalog.esculturaCabe(desenho, camaraMeiaLargura(),
                    disponivel), "emblema que não cabe na câmara: " + nome);
        }
    }

    @Test
    @DisplayName("o emblema usa o fundo inteiro da câmara, sem sobrar borda")
    void emblemaPreencheOFundo() {
        ConfigurationSection secao = lobby.getConfigurationSection("esculturas");
        for (String nome : secao.getKeys(false)) {
            List<String> desenho = secao.getStringList(nome);
            assertEquals(camaraMeiaLargura() * 2 + 1, desenho.get(0).length(),
                    "emblema que não ocupa a largura da câmara: " + nome);
            assertEquals(11, desenho.size(), "emblema fora da altura: " + nome);
        }
    }

    @Test
    @DisplayName("todo emblema só usa os símbolos que o construtor conhece")
    void emblemaSoUsaSimbolosConhecidos() {
        ConfigurationSection secao = lobby.getConfigurationSection("esculturas");
        for (String nome : secao.getKeys(false)) {
            for (String linha : secao.getStringList(nome)) {
                for (char c : linha.toCharArray()) {
                    assertTrue("#o*x ".indexOf(c) >= 0,
                            "símbolo desconhecido '" + c + "' no emblema " + nome);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  O arco
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a verga arredonda o arco de sete para cinco e para três")
    void vergaArredonda() {
        assertEquals(1, LobbyBuilder.meiaLarguraDoArco(0, VAO), "a fiada do topo");
        assertEquals(2, LobbyBuilder.meiaLarguraDoArco(1, VAO));
        assertEquals(3, LobbyBuilder.meiaLarguraDoArco(2, VAO));
        assertEquals(3, LobbyBuilder.meiaLarguraDoArco(9, VAO), "o fuste é reto");
        assertEquals(-1, LobbyBuilder.meiaLarguraDoArco(-1, VAO),
                "acima do topo não há vão");
    }

    /**
     * O defeito que este teste trava: a aduela era desenhada como "um bloco
     * em volta do vão", e na fiada em que a verga encolhe o contorno de uma
     * fiada caía onde a de baixo ainda era passagem. O arco amanhecia tapado
     * com a moldura do próprio jogo — e só se descobre atravessando.
     */
    @Test
    @DisplayName("a aduela nunca pinta dentro do arco")
    void aduelaNaoTapaOArco() {
        // A regra ERRADA, a que o arco teve: "um bloco em volta do vão",
        // olhando a fiada de cima para saber onde o vão encolheu.
        for (int y = 1; y <= ALTURA_ARCO; y++) {
            int aqui = LobbyBuilder.meiaLarguraDoArco(ALTURA_ARCO - y, VAO);
            int acima = LobbyBuilder.meiaLarguraDoArco(ALTURA_ARCO - y - 1, VAO);
            assertTrue(aqui >= 0, "toda fiada do arco tem vão, na fiada " + y);

            for (int ao = -(VAO + 1); ao <= VAO + 1; ao++) {
                int a = Math.abs(ao);
                boolean passagem = a <= aqui;
                boolean regraNova = a > aqui;
                boolean regraAntiga = a > aqui || acima < a;

                assertFalse(passagem && regraNova,
                        "a regra de hoje pinta dentro do vão em " + ao + "," + y);

                if (passagem && regraAntiga) {
                    // Documenta o defeito que motivou a troca: na fiada em
                    // que a verga encolhe, a regra antiga tapava passagem.
                    assertTrue(acima < a, "a regra antiga só errava onde a verga encolhe");
                }
            }
        }
    }

    @Test
    @DisplayName("o arco só fecha, nunca volta a abrir, de baixo para cima")
    void arcoFechaDeBaixoParaCima() {
        int anterior = LobbyBuilder.meiaLarguraDoArco(ALTURA_ARCO - 1, VAO);
        for (int y = 2; y <= ALTURA_ARCO; y++) {
            int aqui = LobbyBuilder.meiaLarguraDoArco(ALTURA_ARCO - y, VAO);
            assertTrue(aqui <= anterior, "o vão ALARGA da fiada " + (y - 1)
                    + " para a " + y + " — isso é uma aba de pedra pairando");
            anterior = aqui;
        }
    }

    @Test
    @DisplayName("vão e aduela juntos cobrem a faixa do arco, sem buraco e sem dobra")
    void vaoEAduelaCobremAFaixa() {
        for (int y = 1; y <= ALTURA_ARCO; y++) {
            int aqui = LobbyBuilder.meiaLarguraDoArco(ALTURA_ARCO - y, VAO);
            for (int ao = -(VAO + 1); ao <= VAO + 1; ao++) {
                int a = Math.abs(ao);
                int vezes = (a <= aqui ? 1 : 0) + (a > aqui ? 1 : 0);
                assertEquals(1, vezes, "o bloco " + ao + "," + y
                        + " foi tratado " + vezes + " vezes");
            }
        }
    }

    @Test
    @DisplayName("o painel da fachada fica acima da verga e abaixo da cimalha")
    void painelDaFachadaNaoColide() {
        int baixo = ALTURA_ARCO + 2;
        int topo = ALTURA_GALERIA - 3;
        assertTrue(baixo > ALTURA_ARCO + 1,
                "o painel não pode encostar na verga da fachada");
        assertTrue(topo < ALTURA_GALERIA - 1, "o painel não pode invadir a cimalha");
        assertTrue(topo - baixo >= 3, "painel baixo demais para caber o letreiro");
    }

    // ------------------------------------------------------------------
    //  A escada das torres
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a volta da torre é uma volta fechada de quarenta células")
    void voltaDaTorreEhFechada() {
        List<int[]> volta = LobbyBuilder.voltaDaTorre(TORRE_MEIA - 1);
        assertEquals(40, volta.size(),
                "quarenta células é o que sobe vinte a meio bloco por vez");

        Set<String> vistas = new HashSet<>();
        for (int[] celula : volta) {
            assertEquals(TORRE_MEIA - 1,
                    Math.max(Math.abs(celula[0]), Math.abs(celula[1])),
                    "célula fora do anel: " + celula[0] + "," + celula[1]);
            assertTrue(vistas.add(celula[0] + "," + celula[1]),
                    "célula repetida: " + celula[0] + "," + celula[1]);
        }

        for (int i = 1; i < volta.size(); i++) {
            assertEquals(1, distancia(volta.get(i - 1), volta.get(i)),
                    "salto na volta entre as células " + (i - 1) + " e " + i);
        }
        assertEquals(1, distancia(volta.get(volta.size() - 1), volta.get(0)),
                "a volta não fecha");
    }

    private static int distancia(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    /**
     * Meio bloco por célula é o ponto inteiro da escada não usar bloco de
     * escada: anda-se sem pular. Um degrau de um bloco inteiro em qualquer
     * lugar da volta já quebra isso.
     */
    @Test
    @DisplayName("a rampa sobe meio bloco por célula, sem pulo")
    void rampaSobeMeioBloco() {
        double anterior = LobbyBuilder.degrau(0).superficie();
        assertEquals(1.0, anterior, 1e-9, "a primeira célula é o próprio piso");

        for (int i = 1; i < 40; i++) {
            double agora = LobbyBuilder.degrau(i).superficie();
            assertEquals(0.5, agora - anterior, 1e-9, "degrau errado na célula " + i);
            anterior = agora;
        }
        assertEquals(20.5, anterior, 1e-9,
                "a rampa tem de chegar meio bloco abaixo do patamar");
    }

    @Test
    @DisplayName("a rampa chega no patamar da passarela em meio degrau")
    void rampaAlcancaOPatamar() {
        double topo = LobbyBuilder.degrau(39).superficie();
        double patamar = ALTURA_GALERIA + 1.0;
        assertEquals(0.5, patamar - topo, 1e-9,
                "entre o fim da rampa e o patamar tem de sobrar meio bloco");
    }

    /**
     * Nas quinas o vizinho de dentro ainda é célula de rampa. O corrimão ali
     * é vidro no meio do caminho, uma fiada acima do degrau seguinte — a
     * escada emparedava na curva.
     */
    @Test
    @DisplayName("nas quinas o vizinho de dentro ainda é rampa")
    void quinaNaoLevaCorrimao() {
        int raio = TORRE_MEIA - 1;
        int[] quina = LobbyBuilder.paraDentro(new int[]{-raio, -raio}, raio);
        assertEquals(raio, Math.max(Math.abs(quina[0]), Math.abs(quina[1])),
                "o vizinho da quina continua no anel da rampa");

        int[] meio = LobbyBuilder.paraDentro(new int[]{0, -raio}, raio);
        assertEquals(raio - 1, Math.max(Math.abs(meio[0]), Math.abs(meio[1])),
                "fora da quina o vizinho sai do anel");
    }

    @Test
    @DisplayName("a porta de baixo da torre cai na primeira célula da rampa")
    void portaDeBaixoEncostaNaRampa() {
        int raio = TORRE_MEIA - 1;
        List<int[]> volta = LobbyBuilder.voltaDaTorre(raio);
        int entrada = LobbyBuilder.entradaDaTorre(TORRE_MEIA);

        // A porta abre em entrada-1 .. entrada+1 na parede de dentro. A célula
        // mais baixa da rampa tem de estar nessa faixa, senão quem entra
        // encontra a rampa na altura do peito.
        int[] primeira = volta.get(0);
        assertTrue(primeira[0] >= entrada - 1 && primeira[0] <= entrada + 1,
                "a célula 0 está em " + primeira[0] + ", fora da porta em " + entrada);
        assertEquals(-raio, primeira[1], "a célula 0 tem de estar na parede da porta");
    }

    @Test
    @DisplayName("a ponte de cima passa muito acima da rampa que ela cruza")
    void ponteTemAlturaSobreARampa() {
        int raio = TORRE_MEIA - 1;
        List<int[]> volta = LobbyBuilder.voltaDaTorre(raio);
        int saida = LobbyBuilder.saidaDaTorre(TORRE_MEIA);

        for (int ox = saida; ox <= saida + 2; ox++) {
            int indice = -1;
            for (int i = 0; i < volta.size(); i++) {
                if (volta.get(i)[0] == ox && volta.get(i)[1] == raio) {
                    indice = i;
                    break;
                }
            }
            assertTrue(indice >= 0, "a ponte não cruza o anel em " + ox);
            double embaixo = LobbyBuilder.degrau(indice).superficie();
            assertTrue(ALTURA_GALERIA - embaixo >= 4,
                    "a ponte passa a " + (ALTURA_GALERIA - embaixo)
                            + " da rampa em " + ox + " — baixo demais para passar por baixo");
        }
    }

    // ------------------------------------------------------------------
    //  As faixas e as torres no pátio
    // ------------------------------------------------------------------

    /**
     * As quatro torres ficam soltas nos cantos do pátio e as faixas de cor
     * atravessam o pátio inteiro. Com o nicho mais de fora em 28 e a torre
     * começando em 30, a folga é de UM bloco: qualquer mexida em
     * praca.vaos ou em praca.torre-centro passa a torre por cima de uma
     * faixa, e a faixa não leva mais a nenhum portal.
     */
    @Test
    @DisplayName("nenhuma faixa de cor entra numa torre")
    void faixasNaoEntramNasTorres() {
        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            for (int centro : VAOS) {
                for (int faixa = -1; faixa <= 1; faixa++) {
                    for (int fora = MONUMENTO + 4; fora <= PATIO; fora++) {
                        int x = lado.x(centro + faixa, fora);
                        int z = lado.z(centro + faixa, fora);
                        assertFalse(naTorre(x, z), "a faixa de " + lado + "/" + centro
                                + " entra numa torre em " + x + "," + z);
                    }
                }
            }
        }
    }

    private static boolean naTorre(int x, int z) {
        return Math.abs(Math.abs(x) - TORRE_CENTRO) <= TORRE_MEIA
                && Math.abs(Math.abs(z) - TORRE_CENTRO) <= TORRE_MEIA;
    }

    @Test
    @DisplayName("as torres ficam inteiras dentro do pátio, longe do monumento")
    void torresCabemNoPatio() {
        assertTrue(TORRE_CENTRO + TORRE_MEIA <= PATIO,
                "a torre passa da parede da galeria");
        assertTrue(TORRE_CENTRO - TORRE_MEIA > MONUMENTO + 3,
                "a torre encosta na calçada do monumento");
        assertEquals(1, PATIO - (TORRE_CENTRO + TORRE_MEIA),
                "a fenda entre a torre e a galeria tem de ser de um bloco, "
                        + "que é o que o contraforte fecha");
    }

    @Test
    @DisplayName("o contraforte cobre a saída de cima da torre")
    void contraforteCobreASaida() {
        int saida = LobbyBuilder.saidaDaTorre(TORRE_MEIA);
        int dentro = TORRE_CENTRO - TORRE_MEIA;
        for (int ox = saida; ox <= saida + 2; ox++) {
            int aoLongo = TORRE_CENTRO + ox;
            assertTrue(aoLongo >= dentro && aoLongo <= PATIO,
                    "a saída em " + aoLongo + " cai fora do contraforte, "
                            + "que vai de " + dentro + " a " + PATIO);
        }
    }

    /**
     * O defeito que uma busca em largura pela planta pegou antes de a obra
     * existir: o parapeito de dentro corre o anel inteiro, e a ponte da torre
     * chega na galeria exatamente em cima dele. Com dois blocos de vidro na
     * frente da porta de cima, as quatro torres eram escada para nada —
     * subia-se vinte blocos para bater numa grade.
     */
    @Test
    @DisplayName("o parapeito de dentro abre exatamente na boca das quatro pontes")
    void bocaDaPonteCoincideComAPortaDeCima() {
        int anel = PATIO + 1;
        int aoLongo = TORRE_CENTRO + LobbyBuilder.saidaDaTorre(TORRE_MEIA);

        for (int i = 0; i <= 2; i++) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    assertTrue(LobbyBuilder.bocaDaPonte(PATIO, TORRE_CENTRO, TORRE_MEIA,
                                    sx * (aoLongo + i), sz * anel),
                            "o parapeito fecha a ponte em " + (aoLongo + i));
                }
            }
        }
        assertFalse(LobbyBuilder.bocaDaPonte(PATIO, TORRE_CENTRO, TORRE_MEIA,
                        aoLongo - 1, anel),
                "a abertura passou um bloco antes da porta");
        assertFalse(LobbyBuilder.bocaDaPonte(PATIO, TORRE_CENTRO, TORRE_MEIA,
                        aoLongo + 3, anel),
                "a abertura passou um bloco depois da porta");
        assertFalse(LobbyBuilder.bocaDaPonte(PATIO, TORRE_CENTRO, TORRE_MEIA,
                        anel, aoLongo),
                "a porta de cima sai só no eixo z — o outro eixo fica fechado");
        assertFalse(LobbyBuilder.bocaDaPonte(PATIO, TORRE_CENTRO, TORRE_MEIA, 0, anel),
                "no meio da galeria o parapeito não pode abrir");
    }

    /**
     * A porta de cima da torre fica na face de FORA, e o patamar que leva até
     * ela cruza o poço da rampa por uma ponte. Se a porta mudasse de face, a
     * abertura do parapeito ficaria num lado e a porta no outro — e nada
     * reclamaria.
     */
    @Test
    @DisplayName("a porta de cima, a ponte e a abertura do parapeito falam do mesmo lugar")
    void saidaDaTorreEhUmaSo() {
        int saida = LobbyBuilder.saidaDaTorre(TORRE_MEIA);
        assertTrue(saida >= -(TORRE_MEIA - 1) && saida + 2 <= TORRE_MEIA - 1,
                "a ponte tem de caber dentro da torre");

        int aoLongo = TORRE_CENTRO + saida;
        assertTrue(aoLongo + 2 <= PATIO,
                "a boca da ponte passa da parede da galeria");
        assertTrue(aoLongo - 1 > TORRE_CENTRO - TORRE_MEIA,
                "a boca da ponte cai fora do contraforte");
    }

    // ------------------------------------------------------------------
    //  As caixas dos portais
    // ------------------------------------------------------------------

    @Test
    @DisplayName("quem está na porta de um nicho é pego pela caixa dele")
    void caixaPegaQuemEstaNaPorta() {
        int piso = 100;
        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            for (int centro : VAOS) {
                int fundo = galeriaFim();
                double x = lado.x(centro, fundo - 1) + 0.5;
                double z = lado.z(centro, fundo - 1) + 0.5;
                assertTrue(LobbyPortais.dentroDaCaixa(x, piso + 1, z, lado, centro,
                        fundo, piso), "a caixa não pega quem está na porta de "
                        + lado + "/" + centro);
            }
        }
    }

    /**
     * Uma caixa que pega quem está passando pelo pátio teleporta quem só
     * atravessa. Com cinco nichos por galeria e catorze blocos entre eles, a
     * caixa do vizinho é a que mais perto chega.
     */
    @Test
    @DisplayName("a caixa de um nicho não pega quem está no nicho vizinho nem no pátio")
    void caixaNaoPegaDeFora() {
        int piso = 100;
        int fundo = galeriaFim();
        MinigamesCatalog.Lado lado = MinigamesCatalog.Lado.NORTE;

        for (int centro : VAOS) {
            for (int vizinho : VAOS) {
                if (centro == vizinho) {
                    continue;
                }
                double x = lado.x(vizinho, fundo - 1) + 0.5;
                double z = lado.z(vizinho, fundo - 1) + 0.5;
                assertFalse(LobbyPortais.dentroDaCaixa(x, piso + 1, z, lado, centro,
                        fundo, piso), "a caixa de " + centro + " pega o nicho "
                        + vizinho);
            }
            // No meio do pátio, a vinte blocos da parede.
            double x = lado.x(centro, 20) + 0.5;
            double z = lado.z(centro, 20) + 0.5;
            assertFalse(LobbyPortais.dentroDaCaixa(x, piso + 1, z, lado, centro,
                    fundo, piso), "a caixa de " + centro + " chega até o pátio");
        }
    }

    @Test
    @DisplayName("as caixas das quatro galerias não se confundem entre si")
    void caixasDeGaleriasDiferentesNaoSeCruzam() {
        int piso = 100;
        int fundo = galeriaFim();
        for (MinigamesCatalog.Lado onde : MinigamesCatalog.Lado.values()) {
            double x = onde.x(0, fundo - 1) + 0.5;
            double z = onde.z(0, fundo - 1) + 0.5;
            for (MinigamesCatalog.Lado caixa : MinigamesCatalog.Lado.values()) {
                boolean esperado = caixa == onde;
                assertEquals(esperado, LobbyPortais.dentroDaCaixa(x, piso + 1, z,
                                caixa, 0, fundo, piso),
                        "estando em " + onde + ", a caixa de " + caixa
                                + " devia dar " + esperado);
            }
        }
    }

    /**
     * O defeito mais caro que a planta podia ter: nascer dentro da caixa da
     * volta. O jogador chegaria do hub, seria devolvido ao hub no mesmo
     * instante, voltaria a entrar no portal — um laço fechado entre dois
     * mundos, sem nenhuma mensagem de erro em lugar nenhum.
     */
    @Test
    @DisplayName("ninguém nasce dentro da caixa da volta")
    void spawnForaDaCaixaDaVolta() {
        int piso = 100;
        assertFalse(LobbyPortais.dentroDaVolta(0.5,
                        piso + LobbyBuilder.SPAWN_ALTURA, LobbyBuilder.SPAWN_Z + 0.5, piso),
                "o ponto de nascimento está dentro do portal de volta");
        assertTrue(LobbyBuilder.SPAWN_Z >= 4,
                "quatro blocos é o mínimo entre o nascimento e a caixa da volta");
        assertTrue(LobbyBuilder.SPAWN_Z <= MONUMENTO,
                "o nascimento tem de ficar em cima do pódio");
    }

    @Test
    @DisplayName("a caixa da volta pega quem atravessa o vão e mais ninguém")
    void caixaDaVoltaEhApertada() {
        int piso = 100;
        assertTrue(LobbyPortais.dentroDaVolta(0.5, piso + 4, 0.5, piso),
                "quem está no meio do vão tem de ser pego");
        assertTrue(LobbyPortais.dentroDaVolta(-1.5, piso + 4, 0.5, piso),
                "a borda do vão também");
        assertFalse(LobbyPortais.dentroDaVolta(0.5, piso + 4, 3.5, piso),
                "três blocos adiante já é o pódio, não o portal");
        assertFalse(LobbyPortais.dentroDaVolta(6.5, piso + 4, 0.5, piso),
                "ao lado do vão é parede, não portal");
        assertFalse(LobbyPortais.dentroDaVolta(0.5, piso + 1, 0.5, piso),
                "no chão do pátio, três degraus abaixo, não é portal");
    }

    // ------------------------------------------------------------------
    //  A ligação com o hub
    // ------------------------------------------------------------------

    /**
     * O lobby é o destino do portal roxo do hub. Se o id mudar no hub.yml,
     * a instrução que o /biga lobby construir imprime manda mexer num portal
     * que não existe mais.
     */
    @Test
    @DisplayName("o hub ainda tem o portal que vai virar a entrada do lobby")
    void hubTemOPortalDosMinigames() throws Exception {
        YamlConfiguration hub;
        try (InputStream in = LobbyTest.class.getResourceAsStream("/hub.yml")) {
            assertNotNull(in, "hub.yml não foi empacotado");
            hub = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> ids = new ArrayList<>();
        for (Map<?, ?> item : hub.getMapList("portais")) {
            ids.add(texto(item, "id"));
        }
        assertTrue(ids.contains("minigames"),
                "o hub.yml não tem mais o portal 'minigames': " + ids);
    }

    @Test
    @DisplayName("o piso do lobby fica na mesma altura do piso do hub")
    void mesmaAlturaDoHub() throws Exception {
        YamlConfiguration hub;
        try (InputStream in = LobbyTest.class.getResourceAsStream("/hub.yml")) {
            hub = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals(hub.getInt("praca.y"), lobby.getInt("praca.y"),
                "as duas praças têm de ter o mesmo horizonte");
        assertEquals(HubCatalog.Y_PADRAO, MinigamesCatalog.Y_PADRAO,
                "o gerador de mundo vazio usa um piso só");
    }

    // ------------------------------------------------------------------
    //  A geometria dos lados
    // ------------------------------------------------------------------

    /**
     * O par (aoLongo, paraFora) é o que faz um código só construir as quatro
     * galerias. Se os vetores de um lado deixarem de ser perpendiculares e
     * unitários, a conta inversa que o LobbyPortais faz para achar em que
     * nicho o jogador está passa a dar outro ponto — e o portal do norte
     * teleporta quem está no do sul.
     */
    @Test
    @DisplayName("os quatro lados são rotações exatas um do outro")
    void ladosSaoRotacoes() {
        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            assertEquals(1, lado.vx() * lado.vx() + lado.vz() * lado.vz(),
                    lado + ": o vetor para fora tem de ser unitário");
            assertEquals(1, lado.ux() * lado.ux() + lado.uz() * lado.uz(),
                    lado + ": o vetor ao longo tem de ser unitário");
            assertEquals(0, lado.ux() * lado.vx() + lado.uz() * lado.vz(),
                    lado + ": os dois vetores têm de ser perpendiculares");

            // Ida e volta: (ao, fora) -> (x, z) -> (ao, fora).
            for (int ao = -30; ao <= 30; ao += 7) {
                for (int fora = 10; fora <= 50; fora += 13) {
                    int x = lado.x(ao, fora);
                    int z = lado.z(ao, fora);
                    assertEquals(ao, x * lado.ux() + z * lado.uz(), lado + ": ao longo");
                    assertEquals(fora, x * lado.vx() + z * lado.vz(), lado + ": para fora");
                }
            }
        }
    }

    @Test
    @DisplayName("olhar para dentro e para fora são opostos, e os quatro cobrem a volta")
    void yawsSaoOpostos() {
        Set<Float> dentro = new HashSet<>();
        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            assertEquals(180f, Math.abs(lado.yawParaDentro() - lado.yawParaFora()) % 360f,
                    1e-6, lado + ": os dois yaws têm de ser opostos");
            assertTrue(dentro.add(lado.yawParaDentro()),
                    "dois lados com o mesmo yaw: " + lado);
        }
        assertEquals(Set.of(0f, 90f, 180f, 270f), dentro,
                "os quatro lados têm de cobrir os quatro pontos cardeais");
    }

    @Test
    @DisplayName("o nicho de um deslocamento é único, e entre nichos não há nicho")
    void vaoEmAcertaONicho() {
        int meia = VAO + PILAR;
        for (int i = 0; i < VAOS.size(); i++) {
            int centro = VAOS.get(i);
            assertEquals(i - 2, MinigamesCatalog.vaoEm(centro, VAOS, meia),
                    "o meio do nicho " + (i - 2));
            assertEquals(i - 2, MinigamesCatalog.vaoEm(centro + meia, VAOS, meia),
                    "a borda do nicho " + (i - 2));
        }
        assertEquals(Integer.MIN_VALUE,
                MinigamesCatalog.vaoEm(VAOS.get(0) + meia + 1, VAOS, meia),
                "um bloco além da borda já é o maciço entre nichos");
        assertEquals(Integer.MIN_VALUE, MinigamesCatalog.vaoEm(40, VAOS, meia),
                "a ponta da galeria não é nicho");
    }
}
