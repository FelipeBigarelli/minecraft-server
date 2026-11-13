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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do BUILD BATTLE, conferidas sem servidor.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * {@link #aMediaCruaCoroaOQueDaNotaBaixaEAOrdemNao} é o teste que justifica
 * este jogo não contar como todos os outros Build Battle contam. Ele monta a
 * reclamação que os jogadores do gênero fazem em todo servidor — um votante
 * dá a nota mínima para todos os rivais, para levantar a própria colocação —
 * e mostra os dois resultados lado a lado: pela MÉDIA CRUA o trapaceiro ganha
 * a partida, e pela ORDEM das cédulas ganha quem construiu melhor.
 *
 * <p>{@link #aCedulaSemOrdemNaoEntraNaConta} é o irmão exato dele. Converter
 * a cédula em ordem já tira a força do voto baixo, mas sobrava uma brecha: a
 * cédula de nota única virava 0,5 em todos os lotes menos no do próprio
 * votante, e isso comprimia as notas dos outros para o meio sem tocar na
 * dele. Descartada, ela vale ZERO exato — e o teste prende a igualdade, não
 * uma aproximação.
 *
 * <h2>E a descoberta geométrica que custou um desenho inteiro</h2>
 *
 * {@link #aGradeDeTresFiadasEhRecusada}: a plateia de cada lote fica a uma
 * dúzia de blocos da beira dele, e com três fiadas a plateia de uma cai
 * DENTRO do lote de outra. O vão entre lotes tem meia dúzia de blocos e
 * plateia nenhuma caberia nele a uma distância que mostre a obra. Duas
 * fiadas, uma vista de cada lado, é o que faz toda obra ser julgada do mesmo
 * ângulo — e ângulo igual para todos é condição de a nota querer dizer algo.
 */
class BuildBattleTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int PLOTS = 16;
    private static final int POR_FIADA = 8;
    private static final int LADO = 13;
    private static final int VAO = 7;
    private static final int PASSO = LADO + VAO;
    private static final int ALTURA_DE_OBRA = 16;
    private static final int LIMITE = 11;
    private static final int PLATEIA_RECUO = 12;
    private static final int PLATEIA_ALTURA = 5;
    private static final int PLATEIA_LADO = 5;
    private static final int PLATEIA_FUNDO = 3;

    private static final int TEMA = 6;
    private static final int OBRA = 180;
    private static final int PASSEIO = 3;
    private static final int VOTO = 8;
    private static final int FIM = 12;
    private static final int NOTAS = 5;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 420;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = BuildBattleTest.class
                .getResourceAsStream("/build-battle.yml")) {
            assertNotNull(in, "build-battle.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Map<Integer, Integer> cedula(int... paresLoteNota) {
        Map<Integer, Integer> saida = new LinkedHashMap<>();
        for (int k = 0; k + 1 < paresLoteNota.length; k += 2) {
            saida.put(paresLoteNota[k], paresLoteNota[k + 1]);
        }
        return saida;
    }

    /**
     * Quatro construtores, e o do lote 1 dá a nota mínima para todos.
     *
     * A verdade do cenário: o lote 0 é a melhor obra, o 1 a segunda, o 2 a
     * terceira e o 3 a pior. Os votantes dos lotes 0, 2 e 3 votam honesto. O
     * do lote 1 dá 1 para todos, que é a jogada que os jogadores do gênero
     * reclamam.
     */
    private static List<Map<Integer, Integer>> aMesaComUmTrapaceiro() {
        List<Map<Integer, Integer>> mesa = new ArrayList<>();
        mesa.add(cedula(1, 4, 2, 3, 3, 2));   // dono do lote 0, honesto
        mesa.add(cedula(0, 1, 2, 1, 3, 1));   // dono do lote 1, nota mínima
        mesa.add(cedula(0, 5, 1, 4, 3, 2));   // dono do lote 2, honesto
        mesa.add(cedula(0, 5, 1, 4, 2, 3));   // dono do lote 3, honesto
        return mesa;
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(PLOTS, arena.getInt("arena.plots"));
        assertEquals(POR_FIADA, arena.getInt("arena.plots-por-fiada"));
        assertEquals(LADO, arena.getInt("arena.plot-lado"));
        assertEquals(VAO, arena.getInt("arena.plot-vao"));
        assertEquals(ALTURA_DE_OBRA, arena.getInt("arena.altura-de-obra"));
        assertEquals(LIMITE, arena.getInt("arena.limite-do-lote"));
        assertEquals(PLATEIA_RECUO, arena.getInt("arena.plateia-recuo"));
        assertEquals(PLATEIA_ALTURA, arena.getInt("arena.plateia-altura"));
        assertEquals(PLATEIA_LADO, arena.getInt("arena.plateia-lado"));
        assertEquals(PLATEIA_FUNDO, arena.getInt("arena.plateia-fundo"));
        assertEquals(TEMA, arena.getInt("regras.segundos-de-tema"));
        assertEquals(OBRA, arena.getInt("regras.segundos-de-obra"));
        assertEquals(PASSEIO, arena.getInt("regras.segundos-de-passeio"));
        assertEquals(VOTO, arena.getInt("regras.segundos-de-voto"));
        assertEquals(FIM, arena.getInt("regras.fim-segundos"));
        assertEquals(NOTAS, arena.getInt("regras.notas"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("os números do yml passam nas quatro conferências")
    void aPlantaPassaNaConferencia() {
        assertNull(BuildBattleBuilder.conferirPaleta(
                arena.getStringList("blocos")));
        assertNull(BuildBattleBuilder.conferirGrade(PLOTS, POR_FIADA, LADO,
                VAO, LIMITE));
        assertNull(BuildBattleBuilder.conferirPlateia(LADO, ALTURA_DE_OBRA,
                PLATEIA_RECUO, PLATEIA_ALTURA, PLATEIA_LADO, PLATEIA_FUNDO));
        assertNull(BuildBattleBuilder.conferirRelogio(TEMA, OBRA, PASSEIO,
                VOTO, FIM, MAXIMO, TEMPO_LIMITE,
                arena.getStringList("temas").size()));
    }

    @Test
    @DisplayName("as regras escritas avisam que nota baixa não levanta a sua")
    void asRegrasAvisamDoVotoBaixo() {
        List<String> regras = arena.getStringList("textos.regras");
        String tudo = String.join(" ", regras).toLowerCase();
        assertTrue(tudo.contains("não levanta") || tudo.contains("nao levanta"),
                "o painel não avisa que dar nota baixa para todos não levanta "
                        + "a própria colocação, e essa é a regra que o jogador "
                        + "não descobre jogando: ele dá meh em todos, vê a sua "
                        + "colocação subir no servidor antigo e supõe que aqui "
                        + "funciona igual. Regras: " + regras);
        assertTrue(tudo.contains("passeio"),
                "o painel não avisa do passeio sem voto: " + regras);
        assertTrue(tudo.contains("lote"),
                "o painel não avisa que o construtor fica no próprio lote: "
                        + regras);
    }

    @Test
    @DisplayName("a paleta do yml cabe no inventário e é de bloco de verdade")
    void aPaletaCabeNaMao() {
        List<String> nomes = arena.getStringList("blocos");
        assertEquals(BuildBattleCatalog.CABE_NO_INVENTARIO, nomes.size(),
                "a paleta tem de ocupar o inventário inteiro e não passar "
                        + "dele: o que passa viraria menu, e menu é o defeito "
                        + "que esta paleta existe para não ter");
        Set<String> vistos = new HashSet<>();
        for (String nome : nomes) {
            assertTrue(vistos.add(nome), "o bloco " + nome + " está repetido");
            assertNotNull(Material.matchMaterial(nome),
                    "o bloco " + nome + " da paleta não existe neste "
                            + "Minecraft, e sairia da lista em silêncio "
                            + "deixando uma vaga vazia na mão do jogador");
        }
    }

    // ------------------------------------------------------------------
    //  A conta da nota
    // ------------------------------------------------------------------

    /**
     * O teste que justifica o jogo não contar como os outros contam.
     */
    @Test
    @DisplayName("a média crua coroa quem dá nota baixa; a ordem não")
    void aMediaCruaCoroaOQueDaNotaBaixaEAOrdemNao() {
        List<Map<Integer, Integer>> mesa = aMesaComUmTrapaceiro();

        int melhorCru = 0;
        double maiorCru = -1;
        for (int lote = 0; lote < 4; lote++) {
            double crua = BuildBattleCatalog.mediaCrua(mesa, lote);
            if (crua > maiorCru) {
                maiorCru = crua;
                melhorCru = lote;
            }
        }
        assertEquals(1, melhorCru, "pela média crua o campeão tem de ser o "
                + "lote 1, que é o do votante que deu a nota mínima para "
                + "todos: se isso deixar de ser verdade, o cenário parou de "
                + "reproduzir a reclamação do gênero e o teste não prova mais "
                + "nada");

        assertEquals(0, BuildBattleCatalog.campeao(mesa, NOTAS),
                "pela ordem das cédulas o campeão tem de ser o lote 0, que é "
                        + "a melhor obra: é isto que a normalização existe "
                        + "para fazer");
        assertEquals(List.of(0, 1, 2, 3),
                BuildBattleCatalog.colocacao(mesa, NOTAS),
                "a colocação tem de sair na ordem da verdade do cenário");
    }

    /**
     * A brecha residual, fechada de maneira exata.
     */
    @Test
    @DisplayName("a cédula sem ordem nenhuma não entra na conta")
    void aCedulaSemOrdemNaoEntraNaConta() {
        List<Map<Integer, Integer>> comTrapaceiro = aMesaComUmTrapaceiro();
        List<Map<Integer, Integer>> sem = new ArrayList<>();
        for (Map<Integer, Integer> cedula : comTrapaceiro) {
            if (BuildBattleCatalog.temOrdem(cedula)) {
                sem.add(cedula);
            }
        }
        assertEquals(3, sem.size(), "só a cédula de nota única é sem ordem");

        Map<Integer, Double> com = BuildBattleCatalog.notasFinais(
                comTrapaceiro, NOTAS);
        Map<Integer, Double> semEla = BuildBattleCatalog.notasFinais(sem,
                NOTAS);
        assertEquals(semEla.keySet(), com.keySet());
        for (int lote : com.keySet()) {
            assertEquals(semEla.get(lote), com.get(lote), 1e-12,
                    "a nota do lote " + lote + " mudou quando a cédula de "
                            + "nota única entrou. Ela tem de valer ZERO "
                            + "exato: 0,5 em todos os lotes menos no do "
                            + "próprio votante comprime as notas dos outros "
                            + "para o meio sem tocar na dele, e isso é "
                            + "vantagem por não ter opinião");
        }
    }

    @Test
    @DisplayName("a cédula de nota única não tem ordem; a variada tem")
    void temOrdemSabeDistinguir() {
        assertTrue(!BuildBattleCatalog.temOrdem(cedula(0, 1, 2, 1, 3, 1)));
        assertTrue(!BuildBattleCatalog.temOrdem(cedula(0, 5, 2, 5)));
        assertTrue(BuildBattleCatalog.temOrdem(cedula(0, 5, 2, 4)));
        assertTrue(!BuildBattleCatalog.temOrdem(cedula()));
    }

    @Test
    @DisplayName("a ordem da cédula vai de zero a um, e o empate divide o posto")
    void aOrdemDaCedula() {
        Map<Integer, Double> estrita = BuildBattleCatalog.ordemDaCedula(
                cedula(1, 4, 2, 3, 3, 2), NOTAS);
        assertEquals(1.0, estrita.get(1), 1e-9, "a melhor nota dá 1");
        assertEquals(0.5, estrita.get(2), 1e-9, "a do meio dá 0,5");
        assertEquals(0.0, estrita.get(3), 1e-9, "a pior dá 0");

        Map<Integer, Double> igual = BuildBattleCatalog.ordemDaCedula(
                cedula(0, 1, 2, 1, 3, 1), NOTAS);
        for (double valor : igual.values()) {
            assertEquals(0.5, valor, 1e-9, "nota igual para todos divide o "
                    + "posto e sai no meio: é a propriedade de que votar "
                    + "igual para todos é, literalmente, não dizer nada");
        }

        Map<Integer, Double> uma = BuildBattleCatalog.ordemDaCedula(
                cedula(2, 4), NOTAS);
        assertEquals(0.75, uma.get(2), 1e-9, "com uma obra só para julgar não "
                + "há contra o que ordenar, e a nota crua é tudo o que "
                + "existe: 4 de 5 dá 0,75");
    }

    @Test
    @DisplayName("a média crua é a média mesmo, que é o que o jogador vê")
    void aMediaCruaEhAMedia() {
        List<Map<Integer, Integer>> mesa = aMesaComUmTrapaceiro();
        assertEquals((1 + 5 + 5) / 3.0,
                BuildBattleCatalog.mediaCrua(mesa, 0), 1e-9);
        assertEquals((4 + 4 + 4) / 3.0,
                BuildBattleCatalog.mediaCrua(mesa, 1), 1e-9);
        assertEquals(0.0, BuildBattleCatalog.mediaCrua(mesa, 9), 1e-9,
                "lote sem voto nenhum tem média zero");
    }

    @Test
    @DisplayName("o voto em si mesmo não existe na cédula de ninguém")
    void ninguemVotaEmSiMesmo() {
        List<Map<Integer, Integer>> mesa = aMesaComUmTrapaceiro();
        for (int votante = 0; votante < mesa.size(); votante++) {
            assertTrue(!mesa.get(votante).containsKey(votante),
                    "o votante do lote " + votante + " votou no próprio lote: "
                            + "voto em si mesmo é o primeiro jeito de furar "
                            + "qualquer votação");
        }
    }

    // ------------------------------------------------------------------
    //  A grade
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a grade de três fiadas é recusada")
    void aGradeDeTresFiadasEhRecusada() {
        assertNotNull(BuildBattleBuilder.conferirGrade(16, 6, LADO, VAO,
                        LIMITE),
                "16 lotes em fiadas de 6 dá três fiadas, e a plateia da fiada "
                        + "do meio cairia dentro do lote de outra: o vão entre "
                        + "lotes tem " + VAO + " blocos e a plateia fica a "
                        + PLATEIA_RECUO + " da beira");
        assertNull(BuildBattleBuilder.conferirGrade(16, 8, LADO, VAO, LIMITE),
                "duas fiadas de oito cabem");
        assertNotNull(BuildBattleBuilder.conferirGrade(PLOTS, POR_FIADA, 12,
                VAO, LIMITE), "lado par não tem bloco no meio");
        assertNotNull(BuildBattleBuilder.conferirGrade(PLOTS, POR_FIADA, LADO,
                2, LIMITE), "vão de dois blocos encosta as bordas");
    }

    @Test
    @DisplayName("a caixa do lote deixa recuar e não alcança o vizinho")
    void aCaixaDoLoteNaoAlcancaOVizinho() {
        int meio = (LADO - 1) / 2;
        assertTrue(LIMITE > meio, "o limite " + LIMITE + " tem de passar do "
                + "meio lote " + meio + ", senão o construtor não recua para "
                + "olhar a própria obra");
        assertTrue(LIMITE < PASSO - meio, "o limite " + LIMITE + " tem de "
                + "ficar abaixo de " + (PASSO - meio) + ", que é onde começa o "
                + "chão do lote vizinho");

        assertNotNull(BuildBattleBuilder.conferirGrade(PLOTS, POR_FIADA, LADO,
                VAO, meio), "limite igual ao meio lote não deixa recuar");
        assertNotNull(BuildBattleBuilder.conferirGrade(PLOTS, POR_FIADA, LADO,
                VAO, PASSO), "limite do tamanho do passo alcança o vizinho");

        // E na prática: do meio do lote 0, o lote 1 está fora da caixa.
        int[] meu = BuildBattleCatalog.centroDoPlot(0, PLOTS, POR_FIADA, PASSO);
        int[] dele = BuildBattleCatalog.centroDoPlot(1, PLOTS, POR_FIADA,
                PASSO);
        assertTrue(!BuildBattleCatalog.foraDoLote(meu[0], Y + 1, meu[1], meu,
                LIMITE, Y, ALTURA_DE_OBRA), "o meio do próprio lote está "
                + "dentro da caixa");
        assertTrue(BuildBattleCatalog.foraDoLote(dele[0] - meio, Y + 1,
                        dele[1], meu, LIMITE, Y, ALTURA_DE_OBRA),
                "a beira de perto do lote vizinho está fora da caixa");
        assertTrue(BuildBattleCatalog.foraDoLote(meu[0], Y + ALTURA_DE_OBRA
                        + 20, meu[1], meu, LIMITE, Y, ALTURA_DE_OBRA),
                "bem acima do teto da obra está fora da caixa");
        assertTrue(!BuildBattleCatalog.foraDoLote(meu[0], Y - 8, meu[1], meu,
                        LIMITE, Y, ALTURA_DE_OBRA),
                "um pouco abaixo do chão está DENTRO da caixa: fazer o fundo "
                        + "de uma obra voando por baixo dela é trabalho "
                        + "legítimo");
    }

    @Test
    @DisplayName("cada bloco do chão tem um dono só, e a borda não tem dono")
    void cadaBlocoTemUmDonoSo() {
        int meio = (LADO - 1) / 2;
        int[] quantos = new int[PLOTS];
        int alcance = (POR_FIADA / 2 + 1) * PASSO;
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                int plot = BuildBattleCatalog.plotDoPonto(x, z, PLOTS,
                        POR_FIADA, PASSO, LADO);
                if (plot >= 0) {
                    quantos[plot]++;
                }
            }
        }
        for (int k = 0; k < PLOTS; k++) {
            assertEquals(LADO * LADO, quantos[k], "o lote " + k + " tem de ter "
                    + (LADO * LADO) + " blocos de chão de obra");
        }
        // A borda fica de fora: é a faixa onde permiteColocar diz não, e é
        // por isso que ela existe no chão.
        for (int k = 0; k < PLOTS; k++) {
            int[] centro = BuildBattleCatalog.centroDoPlot(k, PLOTS, POR_FIADA,
                    PASSO);
            assertEquals(-1, BuildBattleCatalog.plotDoPonto(
                            centro[0] + meio + 1, centro[1], PLOTS, POR_FIADA,
                            PASSO, LADO),
                    "a borda do lote " + k + " não pode ter dono");
            assertEquals(k, BuildBattleCatalog.plotDoPonto(centro[0] + meio,
                            centro[1], PLOTS, POR_FIADA, PASSO, LADO),
                    "a última fiada de chão do lote " + k + " é dele");
        }
    }

    @Test
    @DisplayName("as duas fiadas são vistas de lados opostos, e de fora da grade")
    void asDuasFiadasOlhamParaLadosOpostos() {
        int fiadas = (int) Math.ceil(PLOTS / (double) POR_FIADA);
        assertEquals(2, fiadas);
        for (int k = 0; k < POR_FIADA; k++) {
            assertEquals(-1, BuildBattleCatalog.rumoDaPlateia(k, POR_FIADA,
                    fiadas), "a fiada do norte é vista do norte");
        }
        for (int k = POR_FIADA; k < PLOTS; k++) {
            assertEquals(1, BuildBattleCatalog.rumoDaPlateia(k, POR_FIADA,
                    fiadas), "a fiada do sul é vista do sul");
        }

        int meio = (LADO - 1) / 2;
        for (int k = 0; k < PLOTS; k++) {
            int[] centro = BuildBattleCatalog.centroDoPlot(k, PLOTS, POR_FIADA,
                    PASSO);
            int rumo = BuildBattleCatalog.rumoDaPlateia(k, POR_FIADA, fiadas);
            int plateiaZ = centro[1] + rumo * (meio + PLATEIA_RECUO);
            // A plateia tem de ficar fora de TODO lote da grade, senão ela
            // nasce no meio da obra de alguém.
            for (int outro = 0; outro < PLOTS; outro++) {
                int[] dele = BuildBattleCatalog.centroDoPlot(outro, PLOTS,
                        POR_FIADA, PASSO);
                boolean mesmaColuna = dele[0] == centro[0];
                boolean dentroEmZ = Math.abs(plateiaZ - dele[1]) <= meio + 1;
                assertTrue(!(mesmaColuna && dentroEmZ), "a plateia do lote "
                        + k + " (z=" + plateiaZ + ") cai dentro do lote "
                        + outro + " (z=" + dele[1] + "): é exatamente o que "
                        + "acontece com três fiadas, e é por isso que a grade "
                        + "tem duas");
            }
        }
    }

    @Test
    @DisplayName("da plateia a obra inteira cabe na tela")
    void aPlateiaMostraAObraInteira() {
        double subida = BuildBattleCatalog.anguloDeSubida(ALTURA_DE_OBRA,
                PLATEIA_RECUO, PLATEIA_ALTURA);
        assertTrue(subida <= 50, "o topo de uma obra cheia fica a "
                + Math.round(subida) + "° para cima, e acima de 50° o votante "
                + "torce o pescoço e vota no que deu para ver");
        double abertura = BuildBattleCatalog.anguloDeAbertura(LADO,
                PLATEIA_RECUO);
        assertTrue(abertura >= 25 && abertura <= 80, "o lote aparece com "
                + Math.round(abertura) + "° de abertura: de tão longe vira "
                + "pontinho e todas as notas ficam iguais, de tão perto não "
                + "cabe na tela");

        assertNotNull(BuildBattleBuilder.conferirPlateia(LADO, 60,
                PLATEIA_RECUO, PLATEIA_ALTURA, PLATEIA_LADO, PLATEIA_FUNDO),
                "obra de 60 fiadas não cabe na tela de ninguém");
        assertNotNull(BuildBattleBuilder.conferirPlateia(LADO, ALTURA_DE_OBRA,
                200, PLATEIA_ALTURA, PLATEIA_LADO, PLATEIA_FUNDO),
                "plateia a 200 blocos vê um pontinho");
    }

    // ------------------------------------------------------------------
    //  A paleta e o relógio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a conferência da paleta recusa bloco que age no mundo")
    void aPaletaRecusaBlocoQueAge() {
        List<String> boa = new ArrayList<>(arena.getStringList("blocos"));
        for (String ruim : List.of("TNT", "LAVA", "PISTON", "SPAWNER",
                "COMMAND_BLOCK", "BEDROCK")) {
            List<String> comRuim = new ArrayList<>(boa.subList(0,
                    boa.size() - 1));
            comRuim.add(ruim);
            assertNotNull(BuildBattleBuilder.conferirPaleta(comRuim),
                    "o bloco " + ruim + " age no mundo e tem de ser recusado: "
                            + "obra que age estraga a obra do vizinho");
        }
    }

    @Test
    @DisplayName("a conferência da paleta recusa paleta que não cabe na mão")
    void aPaletaRecusaOQueNaoCabe() {
        List<String> demais = new ArrayList<>(arena.getStringList("blocos"));
        demais.add("DIAMOND_BLOCK");
        assertNotNull(BuildBattleBuilder.conferirPaleta(demais),
                "um bloco além do inventário já viraria menu");
        assertNotNull(BuildBattleBuilder.conferirPaleta(List.of("STONE",
                        "OAK_PLANKS")),
                "paleta de dois blocos faz toda obra sair da mesma cor");
        assertNotNull(BuildBattleBuilder.conferirPaleta(
                        new ArrayList<>(List.of("STONE", "STONE", "SAND",
                                "GLASS", "BRICKS", "SMOOTH_STONE",
                                "COBBLESTONE", "OAK_PLANKS", "OAK_LOG",
                                "OAK_SLAB", "OAK_STAIRS", "OAK_FENCE",
                                "GLOWSTONE"))),
                "bloco repetido é vaga de inventário gasta à toa");
        assertNotNull(BuildBattleBuilder.conferirPaleta(
                        new ArrayList<>(List.of("BLOCO_QUE_NAO_EXISTE",
                                "SAND", "GLASS", "BRICKS", "SMOOTH_STONE",
                                "COBBLESTONE", "OAK_PLANKS", "OAK_LOG",
                                "OAK_SLAB", "OAK_STAIRS", "OAK_FENCE",
                                "GLOWSTONE"))),
                "nome que não existe sairia da lista em silêncio");
    }

    @Test
    @DisplayName("a partida cheia cabe no relógio, e o passeio é obrigatório")
    void oRelogioCabeEOPasseioEhObrigatorio() {
        int total = BuildBattleCatalog.duracaoTotal(TEMA, OBRA, PASSEIO, VOTO,
                FIM, MAXIMO);
        assertEquals(TEMA + OBRA + (PASSEIO + VOTO) * MAXIMO + FIM, total);
        assertTrue(total <= TEMPO_LIMITE, "uma partida cheia leva " + total
                + "s e o limite é " + TEMPO_LIMITE + "s: passar faria a "
                + "votação ser cortada no meio e alguns lotes não receberiam "
                + "nota nenhuma");

        assertNotNull(BuildBattleBuilder.conferirRelogio(TEMA, OBRA, 0, VOTO,
                        FIM, MAXIMO, TEMPO_LIMITE, 14),
                "sem passeio, quem vota no primeiro lote vota sem ter visto "
                        + "nenhum outro — e esse é o defeito que faz a "
                        + "primeira obra sempre tomar nota baixa");
        assertNotNull(BuildBattleBuilder.conferirRelogio(TEMA, OBRA, VOTO,
                        VOTO, FIM, MAXIMO, TEMPO_LIMITE, 14),
                "passeio tão longo quanto o voto dobra a partida à toa");
        assertNotNull(BuildBattleBuilder.conferirRelogio(TEMA, OBRA, PASSEIO,
                        VOTO, FIM, MAXIMO, 120, 14),
                "num limite de 120s a votação não cabe");
        assertNotNull(BuildBattleBuilder.conferirRelogio(TEMA, OBRA, PASSEIO,
                        VOTO, FIM, MAXIMO, TEMPO_LIMITE, 2),
                "dois temas repetem na mesma tarde, e tema repetido é obra "
                        + "decorada");
        assertNotNull(BuildBattleBuilder.conferirRelogio(TEMA, 30, PASSEIO,
                        VOTO, FIM, MAXIMO, TEMPO_LIMITE, 14),
                "meio minuto de obra não dá para construir nada");
    }

    @Test
    @DisplayName("há tema o bastante para não repetir na mesma tarde")
    void temTemaOBastante() {
        List<String> temas = arena.getStringList("temas");
        assertTrue(temas.size() >= 10, "achei " + temas.size() + " temas");
        assertEquals(new HashSet<>(temas).size(), temas.size(),
                "há tema repetido na lista");
        for (String tema : temas) {
            assertTrue(!tema.isBlank() && tema.length() <= 30,
                    "o tema \"" + tema + "\" não cabe num título de tela");
        }
    }

    @Test
    @DisplayName("o meio dos lotes cai em número inteiro")
    void osCentrosSaoInteiros() {
        for (int k = 0; k < PLOTS; k++) {
            int[] centro = BuildBattleCatalog.centroDoPlot(k, PLOTS, POR_FIADA,
                    PASSO);
            assertNotNull(centro, "o lote " + k + " tem de ter meio");
            assertEquals(0, centro[0] % 2, "a coluna do lote " + k
                    + " caiu em " + centro[0] + ": com passo par e lado ímpar "
                    + "os meios têm de cair redondos");
        }
        assertNull(BuildBattleCatalog.centroDoPlot(PLOTS, PLOTS, POR_FIADA,
                PASSO), "lote além da grade não tem meio");
        assertNotEquals(
                BuildBattleCatalog.centroDoPlot(0, PLOTS, POR_FIADA, PASSO)[0],
                BuildBattleCatalog.centroDoPlot(1, PLOTS, POR_FIADA, PASSO)[0],
                "dois lotes da mesma fiada não podem ter a mesma coluna");
    }
}
