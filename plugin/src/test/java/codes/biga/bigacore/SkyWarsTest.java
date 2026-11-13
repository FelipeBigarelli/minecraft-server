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
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do SKY WARS, conferidas sem servidor.
 *
 * <h2>Os dois testes que valem mais aqui</h2>
 *
 * {@link #oKitQueMataDeDoisGolpesEhRecusado} e
 * {@link #oKitQueBateMaisQueODobroEhRecusado} são o par que justifica este
 * jogo não entregar o kit no baú como todo Sky Wars entrega. A reclamação do
 * gênero tem nome próprio nos fóruns dos jogadores — SEE, <i>Skywars
 * Equipment Equality</i> — e ela não é "o baú é injusto" no abstrato: é que o
 * baú bom mata o baú ruim em dois golpes, e não há jogada que conserte dois
 * golpes. Os dois testes põem número nisso: o melhor nível precisa de três
 * golpes cheios, e não pode bater mais que o dobro do pior.
 *
 * <p>{@link #aCupulaQueCaiAntesDaTravessiaEhRecusada} é o terceiro. A cúpula
 * sobre o meio é a proposta dos próprios jogadores contra "quem faz ponte
 * rápido pega o baú do meio", e ela só conserta algo se cair DEPOIS de o mais
 * lento ter conseguido atravessar. Uma cúpula que cai cedo é enfeite com
 * aparência de regra, e é o tipo de erro que ninguém vê no yml.
 *
 * <p>{@link #aOrdemDaQuedaDaAVoltaENaoEmbaralha} guarda a decisão de desenho
 * do fim de partida: o anel cai em ordem, dando a volta de um ponto sorteado.
 * Embaralhado, perder a ilha é sorte; dando a volta, o jogador conta quantas
 * faltam para a dele e decide quando sair.
 */
class SkyWarsTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int ILHAS = 16;
    private static final int ANEL = 44;
    private static final int RAIO = 3;
    private static final int MEIO_RAIO = 9;
    private static final int MEIO_BAUS = 4;

    private static final int VIDA_CHEIA = 100;
    private static final int PERIODO = 13;
    private static final int PARA_O_MEIO = 45;
    private static final int CERCO = 90;
    private static final int POR_ILHA = 6;
    private static final int AVISO = 4;
    private static final int TEMPO_LIMITE = 360;

    private static final List<String> ESPADAS = List.of("WOODEN_SWORD",
            "STONE_SWORD", "IRON_SWORD", "DIAMOND_SWORD");
    private static final List<Integer> DANOS = List.of(14, 18, 22, 26);
    private static final List<Integer> BLOCOS = List.of(24, 28, 32, 40);
    private static final List<Integer> FLECHAS = List.of(0, 6, 10, 16);

    /** A travessia do anel até a beira do meio, em blocos de ponte. */
    private static final int TRAVESSIA = ANEL - RAIO - MEIO_RAIO;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = SkyWarsTest.class
                .getResourceAsStream("/sky-wars.yml")) {
            assertNotNull(in, "sky-wars.yml não foi empacotado");
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
        assertEquals("sky_wars", arena.getString("mundo"));
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(ILHAS, arena.getInt("arena.ilhas"));
        assertEquals(ANEL, arena.getInt("arena.ilha-anel"));
        assertEquals(RAIO, arena.getInt("arena.ilha-raio"));
        assertEquals(MEIO_RAIO, arena.getInt("arena.meio-raio"));
        assertEquals(MEIO_BAUS, arena.getInt("arena.meio-baus"));
        assertEquals(VIDA_CHEIA, arena.getInt("regras.vida-cheia"));
        assertEquals(PERIODO, arena.getInt("regras.periodo-do-golpe"));
        assertEquals(PARA_O_MEIO, arena.getInt("regras.segundos-para-o-meio"));
        assertEquals(CERCO, arena.getInt("regras.segundos-de-cerco"));
        assertEquals(POR_ILHA, arena.getInt("regras.segundos-por-ilha"));
        assertEquals(AVISO, arena.getInt("regras.aviso-segundos"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertEquals(ESPADAS, arena.getStringList("niveis.espada"));
        assertEquals(DANOS, arena.getIntegerList("niveis.dano"));
        assertEquals(BLOCOS, arena.getIntegerList("niveis.blocos"));
        assertEquals(FLECHAS, arena.getIntegerList("niveis.flechas"));
    }

    @Test
    @DisplayName("os números do yml passam nas três conferências")
    void ymlPassaNasConferencias() {
        assertNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                FLECHAS, VIDA_CHEIA));
        assertNull(SkyWarsBuilder.conferirIlhas(ILHAS, ANEL, RAIO, MEIO_RAIO,
                BLOCOS));
        assertNull(SkyWarsBuilder.conferirRelogio(PARA_O_MEIO, CERCO,
                POR_ILHA, ILHAS, TEMPO_LIMITE, TRAVESSIA));
    }

    @Test
    @DisplayName("o bloco de ponte não é bloco de ilha nenhuma")
    void aPonteTemCorPropria() {
        String ponte = arena.getString("paleta.bloco-de-ponte", "");
        assertTrue(!ponte.isBlank(), "falta o bloco-de-ponte na paleta");
        for (String chave : List.of("ilha-topo", "ilha-corpo", "ilha-beira",
                "ilha-marco", "meio-topo", "meio-corpo", "meio-beira",
                "cupula")) {
            assertTrue(!ponte.equalsIgnoreCase(
                            arena.getString("paleta." + chave, "")),
                    "o bloco de ponte é igual ao " + chave + ": a regra de "
                            + "'só se quebra o que alguém colocou' tem de ser "
                            + "VISÍVEL no chão, e é a cor que a mostra");
        }
    }

    // ------------------------------------------------------------------
    //  Os níveis de kit — o coração do jogo
    // ------------------------------------------------------------------

    /**
     * O defeito do gênero, posto em número.
     *
     * Um dano de 40 contra 100 de vida derruba alguém em dois golpes e meio.
     * Quem pegou esse baú não tem vantagem: tem a partida. É este número que
     * {@link SkyWarsBuilder#conferirNiveis} existe para recusar, e por isso o
     * resto do jogo pode confiar que pegar o meio é bom e não é tudo.
     */
    @Test
    @DisplayName("o kit que mata de dois golpes é recusado")
    void oKitQueMataDeDoisGolpesEhRecusado() {
        List<Integer> brabo = List.of(14, 20, 30, 40);
        String erro = SkyWarsBuilder.conferirNiveis(ESPADAS, brabo, BLOCOS,
                FLECHAS, VIDA_CHEIA);
        assertNotNull(erro, "40 de dano contra 100 de vida derruba em 2,5 "
                + "golpes e tinha de ser recusado");
        assertTrue(erro.contains("2.5"), "o erro tem de dizer em quantos "
                + "golpes, que é o número que decide: " + erro);

        // E o do yml passa, com folga de um golpe inteiro.
        assertTrue(VIDA_CHEIA / (double) DANOS.get(DANOS.size() - 1) >= 3.0);
    }

    @Test
    @DisplayName("o kit que bate mais que o dobro do pior é recusado")
    void oKitQueBateMaisQueODobroEhRecusado() {
        List<Integer> esticado = List.of(10, 14, 18, 22);
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, esticado, BLOCOS,
                        FLECHAS, VIDA_CHEIA),
                "22 contra 10 é 2,2 vezes e tinha de ser recusado");

        double razao = DANOS.get(DANOS.size() - 1) / (double) DANOS.get(0);
        assertTrue(razao <= 2.0, "o yml está em " + razao + " vezes");
        assertTrue(razao >= 1.3, "com menos de 1,3 vezes o baú do meio não "
                + "muda nada e a cúpula não tem o que proteger — está em "
                + razao);
    }

    @Test
    @DisplayName("a lista de nível que desce é recusada")
    void aListaQueDesceEhRecusada() {
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS,
                List.of(14, 18, 16, 22), BLOCOS, FLECHAS, VIDA_CHEIA),
                "dano descendo do nível 2 para o 3");
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS,
                List.of(24, 28, 20, 40), FLECHAS, VIDA_CHEIA),
                "blocos descendo");
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                List.of(0, 6, 4, 16), VIDA_CHEIA),
                "flechas descendo");
    }

    @Test
    @DisplayName("as quatro listas de nível têm de ter o mesmo tamanho")
    void aListaCurtaEhRecusada() {
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS,
                List.of(14, 18, 22), BLOCOS, FLECHAS, VIDA_CHEIA),
                "três danos para quatro espadas: o quarto nível leria o "
                        + "último da lista em silêncio");
    }

    @Test
    @DisplayName("a espada que não existe é recusada")
    void aEspadaInventadaEhRecusada() {
        List<String> torta = List.of("WOODEN_SWORD", "STONE_SWORD",
                "ESPADA_DE_PEDRA_SABAO", "DIAMOND_SWORD");
        assertNotNull(SkyWarsBuilder.conferirNiveis(torta, DANOS, BLOCOS,
                        FLECHAS, VIDA_CHEIA),
                "nome inventado entregaria espada de madeira sem avisar");
    }

    @Test
    @DisplayName("um nível só é recusado, porque o meio não teria para onde subir")
    void umNivelSoEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirNiveis(List.of("IRON_SWORD"),
                List.of(22), List.of(32), List.of(10), VIDA_CHEIA));
    }

    @Test
    @DisplayName("o kit da partida nunca é o melhor nível, e o meio é um acima")
    void oKitDaPartidaDeixaOMelhorParaOMeio() {
        Random sorteio = new Random(20261112L);
        Set<Integer> vistos = new HashSet<>();
        for (int k = 0; k < 4000; k++) {
            int nivel = SkyWarsCatalog.nivelDaPartida(ESPADAS.size(), sorteio);
            vistos.add(nivel);
            assertTrue(nivel >= 0 && nivel < ESPADAS.size() - 1,
                    "o kit da partida saiu no nível " + nivel + ", e o melhor "
                            + "tem de sobrar para o baú do meio");
            int meio = SkyWarsCatalog.nivelDoMeio(nivel, ESPADAS.size());
            assertEquals(nivel + 1, meio, "o meio é exatamente um acima");
            assertTrue(DANOS.get(meio) > DANOS.get(nivel),
                    "o baú do meio tem de bater mais que o kit da partida");
        }
        assertEquals(ESPADAS.size() - 1, vistos.size(),
                "em quatro mil sorteios todos os níveis de baixo têm de sair");
    }

    @Test
    @DisplayName("o nível do meio nunca passa do topo da lista")
    void oNivelDoMeioNaoEstoura() {
        for (int k = 0; k < ESPADAS.size() + 3; k++) {
            int meio = SkyWarsCatalog.nivelDoMeio(k, ESPADAS.size());
            assertTrue(meio >= 0 && meio < ESPADAS.size(),
                    "nível do meio " + meio + " fora da lista");
        }
    }

    // ------------------------------------------------------------------
    //  As ilhas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as ilhas vizinhas não se encostam nem dá para pular")
    void asIlhasNaoSeEncostam() {
        double menor = Double.MAX_VALUE;
        for (int k = 0; k < ILHAS; k++) {
            int[] a = SkyWarsCatalog.centroDaIlha(k, ILHAS, ANEL);
            int[] b = SkyWarsCatalog.centroDaIlha((k + 1) % ILHAS, ILHAS, ANEL);
            assertNotNull(a);
            assertNotNull(b);
            double vao = Math.hypot(a[0] - b[0], a[1] - b[1]) - (2 * RAIO + 1);
            menor = Math.min(menor, vao);
        }
        assertTrue(menor > 4, "o menor vão entre vizinhas é de " + menor
                + " blocos: com quatro ou menos dá para pular, e aí não há "
                + "ponte nem vazio — é uma arena redonda com buracos");
        assertTrue(menor < 16, "o menor vão é de " + menor + " blocos: "
                + "ninguém atravessaria e toda partida acabaria no meio");
    }

    @Test
    @DisplayName("cada ilha tem um lugar só, e o meio não é ilha de ninguém")
    void cadaIlhaTemUmLugarSo() {
        Set<String> centros = new HashSet<>();
        for (int k = 0; k < ILHAS; k++) {
            int[] centro = SkyWarsCatalog.centroDaIlha(k, ILHAS, ANEL);
            assertNotNull(centro);
            assertTrue(centros.add(centro[0] + ":" + centro[1]),
                    "duas ilhas no mesmo lugar: " + k);
            assertEquals(k, SkyWarsCatalog.ilhaDoPonto(centro[0], centro[1],
                    ILHAS, ANEL, RAIO), "o meio da ilha " + k + " devolveu "
                            + "outra ilha");
        }
        assertEquals(-1, SkyWarsCatalog.ilhaDoPonto(0, 0, ILHAS, ANEL, RAIO),
                "o meio do mundo não é ilha do anel");
        assertEquals(-1, SkyWarsCatalog.ilhaDoPonto(ANEL + 20, 0, ILHAS, ANEL,
                RAIO), "fora do anel não é ilha nenhuma");
    }

    @Test
    @DisplayName("a primeira ilha fica no norte, e o anel segue o relógio")
    void aPrimeiraIlhaFicaNoNorte() {
        int[] primeira = SkyWarsCatalog.centroDaIlha(0, ILHAS, ANEL);
        assertNotNull(primeira);
        assertEquals(0, primeira[0], "a ilha 1 tem de ficar no eixo");
        assertEquals(-ANEL, primeira[1], "e ao norte, para 'ilha 1' querer "
                + "dizer o mesmo lugar em toda partida");
        int[] quarto = SkyWarsCatalog.centroDaIlha(ILHAS / 4, ILHAS, ANEL);
        assertNotNull(quarto);
        assertTrue(quarto[0] > 0, "um quarto da volta no sentido do relógio "
                + "cai no leste, e caiu em x=" + quarto[0]);
    }

    @Test
    @DisplayName("o pior kit atravessa para o vizinho e tem com que voltar")
    void oPiorKitAtravessaEVolta() {
        double vao = SkyWarsCatalog.vaoEntreIlhas(ILHAS, ANEL, RAIO);
        assertTrue(BLOCOS.get(0) >= Math.ceil(vao) * 2,
                "o pior kit dá " + BLOCOS.get(0) + " blocos e o vão pede "
                        + Math.ceil(vao) + ": sem o dobro, atravessar deixa "
                        + "de ser decisão e vira aposta");
        assertNotNull(SkyWarsBuilder.conferirIlhas(ILHAS, ANEL, RAIO,
                        MEIO_RAIO, List.of(10, 14, 18, 22)),
                "dez blocos não dão nem a ida");
    }

    @Test
    @DisplayName("o anel apertado e o anel largo demais são recusados")
    void oAnelForaDeMedidaEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirIlhas(ILHAS, 20, RAIO, MEIO_RAIO,
                BLOCOS), "anel de 20 com meio de 9 encosta no meio");
        assertNotNull(SkyWarsBuilder.conferirIlhas(40, ANEL, RAIO, MEIO_RAIO,
                BLOCOS), "quarenta ilhas num anel de 44 ficam encostadas");
        assertNotNull(SkyWarsBuilder.conferirIlhas(4, ANEL, RAIO, MEIO_RAIO,
                BLOCOS), "quatro ilhas num anel de 44 ficam longe demais");
    }

    @Test
    @DisplayName("os baús do meio cabem no meio e não se empilham")
    void osBausCabemNoMeio() {
        List<int[]> baus = SkyWarsCatalog.pontosDosBaus(MEIO_BAUS, MEIO_RAIO);
        assertEquals(MEIO_BAUS, baus.size());
        Set<String> onde = new HashSet<>();
        for (int[] bau : baus) {
            assertTrue(onde.add(bau[0] + ":" + bau[1]),
                    "dois baús no mesmo bloco");
            double longe = Math.hypot(bau[0], bau[1]);
            assertTrue(longe <= MEIO_RAIO - 2, "o baú em " + bau[0] + ","
                    + bau[1] + " está a " + longe + " do meio e a ilha tem "
                    + MEIO_RAIO + ": ele cairia na beira");
            assertTrue(longe >= 2, "o baú está no miolo e os quatro se "
                    + "encostariam");
        }
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    /**
     * A cúpula tem de cair DEPOIS de o mais lento poder chegar.
     *
     * Trinta e dois blocos de ponte a um e meio por segundo são vinte e dois
     * segundos. Uma cúpula que caísse em quinze seria enfeite com aparência de
     * regra: quem faz ponte rápido pegaria o baú sozinho, que é o defeito que
     * a cúpula existe para consertar.
     */
    @Test
    @DisplayName("a cúpula que cai antes da travessia é recusada")
    void aCupulaQueCaiAntesDaTravessiaEhRecusada() {
        int atravessar = (int) Math.ceil(TRAVESSIA / 1.5);
        assertTrue(atravessar > 15, "a travessia leva " + atravessar + "s");
        assertNotNull(SkyWarsBuilder.conferirRelogio(15, CERCO, POR_ILHA,
                        ILHAS, TEMPO_LIMITE, TRAVESSIA),
                "cúpula caindo em 15s com travessia de " + atravessar + "s");
        assertNull(SkyWarsBuilder.conferirRelogio(atravessar, CERCO, POR_ILHA,
                        ILHAS, TEMPO_LIMITE, TRAVESSIA),
                "o segundo exato da travessia tem de passar");
        assertTrue(PARA_O_MEIO >= atravessar * 2, "o yml abre o meio em "
                + PARA_O_MEIO + "s e a travessia leva " + atravessar
                + "s: com menos do dobro, chegar e abrir o baú é a mesma "
                + "jogada e a cúpula não dá tempo de ninguém mais chegar");
    }

    @Test
    @DisplayName("o cerco que começa antes do meio abrir é recusado")
    void oCercoAntesDoMeioEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirRelogio(PARA_O_MEIO, PARA_O_MEIO,
                        POR_ILHA, ILHAS, TEMPO_LIMITE, TRAVESSIA),
                "cerco junto com a abertura do meio: ninguém disputaria o baú");
        assertNotNull(SkyWarsBuilder.conferirRelogio(PARA_O_MEIO, 30,
                        POR_ILHA, ILHAS, TEMPO_LIMITE, TRAVESSIA),
                "cerco antes da abertura do meio");
        assertTrue(CERCO - PARA_O_MEIO >= 30, "entre abrir o meio e começar o "
                + "cerco há " + (CERCO - PARA_O_MEIO) + "s: é a janela em que "
                + "o jogo inteiro acontece e ela não pode ser um sopro");
    }

    @Test
    @DisplayName("o anel inteiro cai dentro do tempo limite")
    void oAnelCabeNoLimite() {
        int ultima = SkyWarsCatalog.segundoDaQueda(ILHAS - 1, CERCO, POR_ILHA);
        assertEquals(CERCO + (ILHAS - 1) * POR_ILHA, ultima);
        assertTrue(ultima <= TEMPO_LIMITE, "a última ilha cai em " + ultima
                + "s e o limite é " + TEMPO_LIMITE + "s: a partida acabaria "
                + "no relógio com ilhas de pé, e o cerco não teria servido "
                + "para nada");
        assertNotNull(SkyWarsBuilder.conferirRelogio(PARA_O_MEIO, CERCO, 30,
                        ILHAS, TEMPO_LIMITE, TRAVESSIA),
                "trinta segundos por ilha não cabem em " + TEMPO_LIMITE + "s");
    }

    @Test
    @DisplayName("a queda é cadenciada: uma ilha a cada tantos segundos, sempre o mesmo")
    void aQuedaEhCadenciada() {
        int anterior = SkyWarsCatalog.segundoDaQueda(0, CERCO, POR_ILHA);
        assertEquals(CERCO, anterior, "a primeira cai no segundo do cerco");
        for (int k = 1; k < ILHAS; k++) {
            int agora = SkyWarsCatalog.segundoDaQueda(k, CERCO, POR_ILHA);
            assertEquals(POR_ILHA, agora - anterior,
                    "a cadência mudou na ilha " + k + ": contar quantas "
                            + "faltam para a sua só é jogada se o passo for "
                            + "sempre o mesmo");
            anterior = agora;
        }
        assertTrue(POR_ILHA > AVISO, "cada ilha cai a cada " + POR_ILHA
                + "s e o aviso é de " + AVISO + "s: o aviso da próxima sairia "
                + "antes de a anterior ter caído e os dois se atropelariam");
    }

    /**
     * A ordem da queda dá a volta, de um ponto sorteado.
     *
     * É a diferença entre reagir e decidir. Embaralhada, perder a ilha é
     * sorte e só dá para correr; dando a volta, o jogador conta quantas
     * faltam para a dele — e decidir quando sair é a jogada que este fim de
     * partida existe para criar.
     */
    @Test
    @DisplayName("a ordem da queda dá a volta e não embaralha")
    void aOrdemDaQuedaDaAVoltaENaoEmbaralha() {
        List<Integer> daCinco = SkyWarsCatalog.ordemDaQueda(ILHAS, 5);
        List<Integer> esperada = new ArrayList<>();
        for (int k = 0; k < ILHAS; k++) {
            esperada.add((5 + k) % ILHAS);
        }
        assertEquals(esperada, daCinco, "a ordem tem de ser a volta inteira a "
                + "partir do começo sorteado");

        for (int comeco = 0; comeco < ILHAS; comeco++) {
            List<Integer> ordem = SkyWarsCatalog.ordemDaQueda(ILHAS, comeco);
            assertEquals(ILHAS, ordem.size());
            assertEquals(ILHAS, new HashSet<>(ordem).size(),
                    "com começo " + comeco + " alguma ilha caiu duas vezes "
                            + "ou nunca");
            assertEquals(comeco, ordem.get(0).intValue());
            for (int k = 1; k < ILHAS; k++) {
                int passo = (ordem.get(k) - ordem.get(k - 1) + ILHAS) % ILHAS;
                assertEquals(1, passo, "a ordem pulou uma ilha no passo " + k
                        + " do começo " + comeco + ": o jogador não poderia "
                        + "contar");
            }
        }
    }

    // ------------------------------------------------------------------
    //  O golpe, que é o mesmo da ARENA PVP
    // ------------------------------------------------------------------

    /**
     * O golpe usa a conta da ARENA PVP, de propósito.
     *
     * Não por economia de código: um jogador que aprendeu o tempo do golpe
     * numa arena não pode ter de reaprender na outra, e duas contas
     * parecidas mas diferentes seriam pior do que duas contas abertamente
     * diferentes.
     */
    @Test
    @DisplayName("o golpe do céu usa a mesma conta da arena pvp")
    void oGolpeEhOMesmoDaArenaPvp() {
        assertEquals(1.0, ArenaPvpCatalog.forcaDoGolpe(1.0), 1.0E-9);
        assertEquals(0.4, ArenaPvpCatalog.forcaDoGolpe(0.5), 1.0E-9);

        for (int nivel = 0; nivel < DANOS.size(); nivel++) {
            int cheio = (int) Math.round(DANOS.get(nivel)
                    * ArenaPvpCatalog.forcaDoGolpe(1.0));
            assertEquals(DANOS.get(nivel).intValue(), cheio,
                    "o golpe no tempo tem de tirar o dano inteiro do nível");
            int martelado = (int) Math.round(DANOS.get(nivel)
                    * ArenaPvpCatalog.forcaDoGolpe(
                            ArenaPvpCatalog.prontidaoDe(2, PERIODO)));
            assertTrue(martelado * 3 < cheio, "martelar no nível " + nivel
                    + " tirou " + martelado + " contra " + cheio + " no "
                    + "tempo: clicar rápido tem de render menos de um terço, "
                    + "senão o arco e a ponte perdem para o clique");
        }
    }

    @Test
    @DisplayName("a melhor hora do golpe é o período da espada, e não antes")
    void oMelhorGolpeEhNoPeriodo() {
        assertEquals(PERIODO, ArenaPvpCatalog.melhorIntervalo(PERIODO, PERIODO,
                        PERIODO * 3),
                "a carência igual ao período põe o melhor golpe exatamente no "
                        + "tempo da espada");
        double noTempo = ArenaPvpCatalog.danoPorSegundo(PERIODO, PERIODO,
                PERIODO, DANOS.get(0));
        double adiantado = ArenaPvpCatalog.danoPorSegundo(PERIODO - 3, PERIODO,
                PERIODO, DANOS.get(0));
        assertTrue(adiantado < noTempo * 0.85, "bater três tiques adiantado "
                + "rendeu " + adiantado + " contra " + noTempo + ": a conta "
                + "tem de punir a ansiedade, senão não há tempo a aprender");
    }

    @Test
    @DisplayName("a flecha fraca vale menos, e o tiro cheio vale a flecha inteira")
    void aFlechaValeAPuxada() {
        int danoDaFlecha = arena.getInt("regras.dano-da-flecha");
        double minima = arena.getDouble("regras.forca-minima");
        assertTrue(minima > 0 && minima < 0.5, "a força mínima está em "
                + minima + ": acima de meio o arco viraria inútil e abaixo de "
                + "zero não recusaria nada");
        assertEquals(danoDaFlecha, (int) Math.round(danoDaFlecha * 1.0));
        int fraca = (int) Math.round(danoDaFlecha * minima);
        assertTrue(fraca * 2 <= danoDaFlecha, "o tiro no limite da força "
                + "mínima tirou " + fraca + " de " + danoDaFlecha + ": tem de "
                + "valer no máximo metade, senão cuspir flecha empata com "
                + "mirar");
        assertTrue(danoDaFlecha > DANOS.get(DANOS.size() - 1),
                "a flecha tira " + danoDaFlecha + " e a melhor espada "
                        + DANOS.get(DANOS.size() - 1) + ": a flecha é um tiro "
                        + "só e contado, e tem de valer mais que um golpe");
        assertTrue(VIDA_CHEIA / (double) danoDaFlecha >= 3.0,
                "a flecha derruba em menos de três tiros");
    }

    @Test
    @DisplayName("a cura do baú ajuda sem desfazer a luta")
    void aCuraAjudaSemDesfazer() {
        int cura = arena.getInt("regras.cura-do-item");
        int quantas = arena.getInt("regras.curas-no-bau");
        assertTrue(cura >= VIDA_CHEIA / 5, "a cura devolve " + cura + " de "
                + VIDA_CHEIA + ": menos de um quinto não muda decisão nenhuma");
        assertTrue(cura <= VIDA_CHEIA / 2, "a cura devolve " + cura + ": mais "
                + "de metade da vida desfaz a luta que acabou de acontecer");
        assertTrue(quantas * cura < VIDA_CHEIA, "as " + quantas + " curas do "
                + "baú somam " + (quantas * cura) + " de " + VIDA_CHEIA
                + ": quem pega o baú voltaria à vida cheia e mais, e aí o "
                + "meio decide a partida em vez de adiantá-la");
    }

    @Test
    @DisplayName("o crédito da queda dura o bastante para a queda acontecer")
    void oCreditoDaQuedaDura() {
        int credito = arena.getInt("regras.credito-segundos");
        int queda = arena.getInt("regras.queda-abaixo");
        assertTrue(credito >= 4, "o crédito dura " + credito + "s: empurrar "
                + "alguém e vê-lo cair leva mais que isso, e sem crédito "
                + "este jogo fica sem placar, porque quase toda morte é "
                + "queda");
        assertTrue(credito <= 15, "o crédito dura " + credito + "s: tanto "
                + "tempo premia um golpe de raspão dado meia vida antes");
        assertTrue(queda >= 20, "a queda conta a partir de " + queda
                + " blocos abaixo: perto demais e um pulo na beira da ilha "
                + "viraria morte");
    }
}
