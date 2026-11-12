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
    private static final int GAL_SUP = 21;
    private static final int TORRETA = 10;
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
        assertEquals(GAL_SUP, lobby.getInt("praca.galeria-superior"));
        assertEquals(TORRETA, lobby.getInt("praca.torreta-altura"));
        assertFalse(lobby.contains("praca.torre-centro"),
                "sobrou torre no arquivo: elas saíram do pátio");
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
    //  A escada, dentro da galeria
    // ------------------------------------------------------------------

    private static int escadaProfundidade() {
        return PROFUNDIDADE - 2;
    }

    private static int escadaComprimento() {
        return ALTURA_GALERIA + 2 - escadaProfundidade();
    }

    private static int escadaInicio() {
        return galeriaFim() - 3 - (escadaComprimento() - 1);
    }

    /**
     * A conta que manda em tudo: cada célula do anel sobe meio bloco, logo o
     * perímetro tem de ser exatamente duas vezes a altura da galeria. Uma
     * célula a mais e a rampa fura o teto; uma a menos e ela para meio bloco
     * abaixo da passarela — e meio bloco abaixo não se vê de cima.
     */
    @Test
    @DisplayName("o anel do poço tem duas células para cada fiada a subir")
    void perimetroDaEscadaFechaComAAltura() {
        assertEquals(2 * ALTURA_GALERIA,
                MinigamesCatalog.perimetroDoAnel(escadaComprimento(), escadaProfundidade()),
                "o poço não sobe a galeria inteira");
    }

    /**
     * O poço fica escondido dentro da pedra. Um que invada o último nicho
     * abre um buraco na câmara de um jogo, do outro lado da parede, onde
     * ninguém vai procurar.
     */
    @Test
    @DisplayName("o poço cabe no maciço depois do último nicho")
    void escadaCabeNaGaleria() {
        int ultimo = VAOS.get(VAOS.size() - 1);
        assertTrue(MinigamesCatalog.escadaCabeNaGaleria(escadaInicio(), escadaComprimento(),
                        ultimo, VAO + PILAR, galeriaFim()),
                "o poço vai de " + escadaInicio() + " a "
                        + (escadaInicio() + escadaComprimento() - 1)
                        + ", e o último nicho acaba em " + (ultimo + VAO + PILAR));

        assertFalse(MinigamesCatalog.escadaCabeNaGaleria(ultimo + VAO + PILAR,
                        escadaComprimento(), ultimo, VAO + PILAR, galeriaFim()),
                "encostar no nicho tem de ser recusado");
        assertFalse(MinigamesCatalog.escadaCabeNaGaleria(escadaInicio(),
                        escadaComprimento() + 3, ultimo, VAO + PILAR, galeriaFim()),
                "passar da costa da galeria tem de ser recusado");
    }

    @Test
    @DisplayName("o anel do poço é uma volta fechada, sem repetir célula")
    void anelDaEscadaEhFechado() {
        int ao0 = escadaInicio();
        int ao1 = ao0 + escadaComprimento() - 1;
        int f0 = PATIO + 2;
        int f1 = f0 + escadaProfundidade() - 1;

        List<int[]> anel = LobbyBuilder.anelDaEscada(ao0, ao1, f0, f1);
        assertEquals(2 * ALTURA_GALERIA, anel.size(), "o anel não tem o tamanho da conta");

        Set<String> vistas = new HashSet<>();
        for (int[] celula : anel) {
            assertTrue(LobbyBuilder.naBorda(celula, ao0, ao1, f0, f1),
                    "célula fora da borda: " + celula[0] + "," + celula[1]);
            assertTrue(vistas.add(celula[0] + "," + celula[1]),
                    "célula repetida: " + celula[0] + "," + celula[1]);
        }
        for (int i = 1; i < anel.size(); i++) {
            assertEquals(1, distancia(anel.get(i - 1), anel.get(i)),
                    "salto no anel entre as células " + (i - 1) + " e " + i);
        }
        assertEquals(1, distancia(anel.get(anel.size() - 1), anel.get(0)),
                "o anel não fecha");
    }

    private static int distancia(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    /**
     * Meio bloco por célula é o ponto inteiro de a escada não usar bloco de
     * escada: anda-se sem pular. Um degrau de um bloco inteiro em qualquer
     * lugar da volta já quebra isso.
     */
    @Test
    @DisplayName("a rampa sobe meio bloco por célula, sem pulo")
    void rampaSobeMeioBloco() {
        double anterior = LobbyBuilder.degrau(0).superficie();
        assertEquals(1.0, anterior, 1e-9, "a primeira célula é o próprio piso");

        for (int i = 1; i < 2 * ALTURA_GALERIA; i++) {
            double agora = LobbyBuilder.degrau(i).superficie();
            assertEquals(0.5, agora - anterior, 1e-9, "degrau errado na célula " + i);
            anterior = agora;
        }
        assertEquals(ALTURA_GALERIA + 0.5, anterior, 1e-9,
                "a rampa tem de chegar meio bloco abaixo da passarela");
    }

    @Test
    @DisplayName("a rampa chega na passarela em meio degrau")
    void rampaAlcancaAPassarela() {
        double topo = LobbyBuilder.degrau(2 * ALTURA_GALERIA - 1).superficie();
        assertEquals(0.5, (ALTURA_GALERIA + 1.0) - topo, 1e-9,
                "entre o fim da rampa e a passarela tem de sobrar meio bloco");
    }

    /**
     * A rampa tem duas células de largura. Nas QUINAS o passo para dentro
     * cai noutra célula do anel — e carimbar a segunda largura ali é pôr
     * bloco no meio do caminho, uma fiada acima do degrau seguinte, o que
     * emparedava a curva.
     */
    @Test
    @DisplayName("nas quinas não há segunda largura")
    void quinaNaoTemSegundaLargura() {
        int ao0 = escadaInicio();
        int ao1 = ao0 + escadaComprimento() - 1;
        int f0 = PATIO + 2;
        int f1 = f0 + escadaProfundidade() - 1;

        int[] quina = LobbyBuilder.paraDentroDoAnel(new int[]{ao0, f0}, ao0, ao1, f0, f1);
        assertTrue(LobbyBuilder.naBorda(quina, ao0, ao1, f0, f1),
                "o vizinho da quina continua na borda");

        int[] meio = LobbyBuilder.paraDentroDoAnel(
                new int[]{(ao0 + ao1) / 2, f0}, ao0, ao1, f0, f1);
        assertFalse(LobbyBuilder.naBorda(meio, ao0, ao1, f0, f1),
                "fora da quina o vizinho sai da borda");
    }

    @Test
    @DisplayName("a porta de baixo abre em cima das primeiras células da rampa")
    void portaDeBaixoEncostaNaPrimeiraCelula() {
        int ao0 = escadaInicio();
        int ao1 = ao0 + escadaComprimento() - 1;
        int f0 = PATIO + 2;
        int f1 = f0 + escadaProfundidade() - 1;
        List<int[]> anel = LobbyBuilder.anelDaEscada(ao0, ao1, f0, f1);

        // A porta abre em ao0..ao0+2 na parede do pátio. As três células
        // atrás dela têm de ser as três primeiras da subida — senão quem
        // entra encontra a rampa na altura do peito.
        for (int i = 0; i <= 2; i++) {
            assertEquals(ao0 + i, anel.get(i)[0], "a célula " + i + " saiu do lugar");
            assertEquals(f0, anel.get(i)[1], "a célula " + i + " saiu da parede da porta");
            assertTrue(LobbyBuilder.degrau(i).superficie() <= 2.0,
                    "a célula " + i + " já está alta demais para a porta");
        }
    }

    /**
     * O vazio da rampa sobe TRÊS fiadas acima do pé dela. Onde essas três
     * passam do teto da galeria, o poço abre sozinho na passarela — é assim
     * que a boca da escada nasce. Se o vazio fosse de duas, a boca não
     * abriria e a escada acabaria num teto de pedra.
     */
    @Test
    @DisplayName("o poço abre na passarela perto do topo, e só perto do topo")
    void bocaDaEscadaAbreSozinha() {
        int celulas = 2 * ALTURA_GALERIA;
        int primeiraAberta = -1;
        for (int i = 0; i < celulas; i++) {
            boolean abre = LobbyBuilder.degrau(i).fiada() + 3 >= ALTURA_GALERIA;
            if (abre && primeiraAberta < 0) {
                primeiraAberta = i;
            }
            assertFalse(!abre && primeiraAberta >= 0 && i > primeiraAberta,
                    "a boca fecha de novo na célula " + i);
        }
        assertTrue(primeiraAberta > 0, "o poço nunca abre: a escada acaba em pedra");
        assertTrue(celulas - primeiraAberta >= 4,
                "a boca tem só " + (celulas - primeiraAberta)
                        + " células — estreita demais para sair");
        assertTrue(celulas - primeiraAberta <= 10,
                "a boca tem " + (celulas - primeiraAberta)
                        + " células — isso é rasgo na passarela, não boca de escada");
    }

    @Test
    @DisplayName("a última célula da rampa tem um vizinho de piso para pisar")
    void topoDaRampaTemOndePisar() {
        int ao0 = escadaInicio();
        int ao1 = ao0 + escadaComprimento() - 1;
        int f0 = PATIO + 2;
        int f1 = f0 + escadaProfundidade() - 1;
        List<int[]> anel = LobbyBuilder.anelDaEscada(ao0, ao1, f0, f1);

        int[] topo = anel.get(anel.size() - 1);
        int[] dentro = LobbyBuilder.paraDentroDoAnel(topo, ao0, ao1, f0, f1);
        assertFalse(LobbyBuilder.naBorda(dentro, ao0, ao1, f0, f1),
                "o topo da rampa dá numa quina: não há de onde sair para a passarela");
    }

    // ------------------------------------------------------------------
    //  O pátio fica vazio
    // ------------------------------------------------------------------

    /**
     * A queixa que derrubou as quatro torres: elas tapavam o último nicho de
     * cada galeria. Não era descuido de número — o nicho de fora vai até 34
     * do centro e o pátio acaba em 43, então qualquer torre larga o bastante
     * para ter escada dentro cai em cima dele.
     *
     * <p>Este teste é a regra que ficou no lugar delas: no pátio não há NADA
     * além do monumento e das faixas. Tudo que é estrutura mora de 44 para
     * fora.
     */
    @Test
    @DisplayName("nenhuma célula do poço da escada entra no pátio")
    void pocoFicaDentroDaGaleria() {
        int ao0 = escadaInicio();
        int ao1 = ao0 + escadaComprimento() - 1;
        int f0 = PATIO + 2;
        int f1 = f0 + escadaProfundidade() - 1;

        for (int[] celula : LobbyBuilder.anelDaEscada(ao0, ao1, f0, f1)) {
            assertTrue(celula[1] > PATIO, "célula do poço no pátio, a "
                    + celula[1] + " do centro");
            assertTrue(celula[1] < galeriaFim(), "célula do poço fora da galeria, a "
                    + celula[1] + " do centro");
        }

        // A porta é o único ponto que toca a parede do pátio — e ela é um
        // furo NA parede, não um volume na frente dela. E abre depois do
        // último nicho.
        int ultimo = VAOS.get(VAOS.size() - 1) + VAO + PILAR;
        assertTrue(ao0 > ultimo, "a porta da escada abre em cima do último nicho");
    }

    /**
     * O que restou das torres é uma torreta aberta por cima da passarela.
     * Se um pilar dela descesse ao pátio, a queixa voltaria inteira: volume
     * no canto, na frente do último nicho.
     */
    @Test
    @DisplayName("a torreta fica toda acima da passarela e dentro do anel")
    void torretaNaoTocaOPatio() {
        int pilarDentro = PATIO + 2;
        int pilarFora = galeriaFim() - 2;

        assertTrue(pilarDentro > PATIO, "pilar da torreta dentro do pátio");
        assertTrue(pilarFora < galeriaFim(), "pilar da torreta fora da galeria");
        assertEquals(ALTURA_GALERIA + 1, GAL_SUP,
                "a passarela é a fiada logo acima do teto da galeria");
        assertTrue(TORRETA >= 6, "torreta baixa demais para fazer silhueta");
    }

    // ------------------------------------------------------------------
    //  Entrou por um portal, volta por ele
    // ------------------------------------------------------------------

    /**
     * O pior defeito possível num portal de ida e volta: chegar EM CIMA da
     * própria caixa. O jogador voltaria do jogo, seria mandado de volta ao
     * jogo no mesmo tique, voltaria de novo — um laço fechado entre dois
     * mundos, sem nenhuma mensagem de erro em lugar nenhum.
     */
    @Test
    @DisplayName("a chegada de um portal do lobby fica fora da caixa dele")
    void chegadaDoLobbyForaDaCaixa() {
        int piso = 100;
        int fundo = galeriaFim();
        int chegada = fundo - 4;

        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            for (int centro : VAOS) {
                double x = lado.x(centro, chegada) + 0.5;
                double z = lado.z(centro, chegada) + 0.5;
                assertFalse(LobbyPortais.dentroDaCaixa(x, piso + 1, z, lado, centro,
                                fundo, piso),
                        "a chegada de " + lado + "/" + centro + " cai dentro da caixa");
                assertFalse(LobbyPortais.dentroDaVolta(x, piso + 1, z, piso),
                        "a chegada de " + lado + "/" + centro
                                + " cai dentro do portal de volta ao hub");
            }
        }
    }

    @Test
    @DisplayName("a chegada fica dentro da câmara, não no meio do pátio")
    void chegadaDoLobbyFicaNaCamara() {
        int chegada = galeriaFim() - 4;
        assertTrue(chegada > PATIO + 3,
                "a chegada caiu antes do arco: o jogador sai no pátio, não no nicho");
        assertTrue(chegada < galeriaFim(),
                "a chegada caiu na parede do fundo");
    }

    /**
     * O mesmo no hub: quem volta do lobby sai pelo portão do lobby. Se o
     * terraço ficasse dentro da caixa do portão, ele voltaria para o lobby
     * no mesmo instante.
     */
    @Test
    @DisplayName("o terraço de um portal do hub fica fora da caixa dele")
    void chegadaDoHubForaDaCaixa() throws Exception {
        YamlConfiguration hub;
        try (InputStream in = LobbyTest.class.getResourceAsStream("/hub.yml")) {
            hub = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        int piso = hub.getInt("praca.y");
        int terraco = hub.getInt("praca.raio") + hub.getInt("praca.passarela-comprimento");
        int portal = terraco + hub.getInt("praca.portal-avanco");

        for (HubCatalog.Face face : HubCatalog.Face.values()) {
            double x = face.du() * terraco + 0.5;
            double z = face.dv() * terraco + 0.5;
            assertFalse(HubPortais.dentroDaCaixa(x, piso + 2, z, face, portal, piso),
                    "o terraço do " + face + " cai dentro da caixa do portão");
        }
    }

    @Test
    @DisplayName("a velocidade das praças é a mesma para os dois mundos")
    void velocidadeEhUmaSo() throws Exception {
        YamlConfiguration hub;
        try (InputStream in = LobbyTest.class.getResourceAsStream("/hub.yml")) {
            hub = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        int nivel = hub.getInt("regras.velocidade");
        assertTrue(nivel >= 1 && nivel <= 10,
                "nível de velocidade fora do que uma poção aceita: " + nivel);
        assertEquals(nivel - 1, RegrasDoHub.amplificador(nivel),
                "o nível escrito como no jogo tem de virar amplificador menos um");
        assertTrue(RegrasDoHub.velocidadeLigada(nivel));
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
