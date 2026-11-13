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
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As contas do SKY WARS, conferidas sem servidor.
 *
 * <h2>Os testes que valem mais aqui</h2>
 *
 * {@link #oPisoDoBauEhGarantidoEmTodoSorteio} é o principal, e ele guarda a
 * correção de um erro de desenho que já foi para produção. A primeira versão
 * desta arena NÃO tinha baú nas ilhas: o kit chegava na mão de todo mundo,
 * igual. A intenção era atacar a reclamação mais antiga do gênero — que tem
 * nome próprio nos fóruns dos jogadores, SEE, <i>Skywars Equipment
 * Equality</i> — e o resultado foi outro jogo, porque sem baú não existe o
 * saque e o saque é os quinze primeiros segundos de toda partida.
 *
 * <p>O baú voltou e a loteria é cercada como os próprios jogadores propõem:
 * garantindo o PISO. Dez mil sorteios depois, todo baú tem espada, comida,
 * armadura e blocos de ponte bastantes para ir ao vizinho e voltar — e
 * {@link #doisBausQuaseNuncaAbremAMesmaCoisa} garante o outro lado, que é o
 * baú ainda ter o que revelar.
 *
 * <p>{@link #oSaqueQueMataDeDoisGolpesEhRecusado} e
 * {@link #oSaqueQueBateMaisQueODobroEhRecusado} põem número nas duas pontas
 * do equipamento: o melhor nível precisa de três golpes cheios, e não pode
 * bater mais que o dobro do pior. E
 * {@link #aLutaQueNaoAcabaEhRecusada} põe a ponta de cima, que é nova e veio
 * junto com a armadura: a pior espada ainda tem de derrubar o conjunto
 * completo do melhor nível antes de o anel cair debaixo dos dois.
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
    private static final List<String> ARMADURAS = List.of("LEATHER",
            "CHAINMAIL", "IRON", "DIAMOND");
    private static final List<Integer> REDUCOES = List.of(4, 6, 8, 10);
    private static final int REDUCAO_MAXIMA = 40;

    private static final int BAUS_DA_ILHA = 2;
    private static final int ARM_MINIMA = 1;
    private static final int ARM_MAXIMA = 2;
    private static final int BLOCOS_GARANTIDOS = 24;
    private static final int BLOCOS_EXTRAS = 16;
    private static final int CHANCE_ARCO = 40;
    private static final int CHANCE_CURA = 25;
    private static final int CURAS_NA_ILHA = 1;
    private static final int COMIDA = 3;
    private static final int DANO_DO_SOCO = 6;

    /** Quantos sorteios de baú o teste roda. */
    private static final int SORTEIOS = 10000;

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
        assertEquals(ARMADURAS, arena.getStringList("niveis.armadura"));
        assertEquals(REDUCOES, arena.getIntegerList("niveis.reducao"));
        assertEquals(REDUCAO_MAXIMA, arena.getInt("regras.reducao-maxima"));
        assertEquals(DANO_DO_SOCO, arena.getInt("regras.dano-do-soco"));
        assertEquals(BAUS_DA_ILHA, arena.getInt("arena.baus-da-ilha"));
        assertEquals(ARM_MINIMA, arena.getInt("saque.armadura-minima"));
        assertEquals(ARM_MAXIMA, arena.getInt("saque.armadura-maxima"));
        assertEquals(BLOCOS_GARANTIDOS, arena.getInt("saque.blocos-garantidos"));
        assertEquals(BLOCOS_EXTRAS, arena.getInt("saque.blocos-extras"));
        assertEquals(CHANCE_ARCO, arena.getInt("saque.chance-de-arco"));
        assertEquals(CHANCE_CURA, arena.getInt("saque.chance-de-cura"));
        assertEquals(CURAS_NA_ILHA, arena.getInt("saque.curas-na-ilha"));
        assertEquals(COMIDA, arena.getInt("saque.comida"));
        assertTrue(arena.getInt("arena.baus-da-ilha") >= 1,
                "sem baú na ilha não existe o saque, e foi esse o erro que "
                        + "esta arena já cometeu uma vez");
    }

    @Test
    @DisplayName("os números do yml passam nas quatro conferências")
    void ymlPassaNasConferencias() {
        assertNull(niveis(ESPADAS, DANOS, BLOCOS, FLECHAS));
        assertNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, CHANCE_ARCO, CHANCE_CURA,
                COMIDA, vao(), DANO_DO_SOCO, DANOS));
        assertNull(SkyWarsBuilder.conferirIlhas(ILHAS, ANEL, RAIO, MEIO_RAIO,
                BLOCOS_GARANTIDOS, BAUS_DA_ILHA));
        assertNull(SkyWarsBuilder.conferirRelogio(PARA_O_MEIO, CERCO,
                POR_ILHA, ILHAS, TEMPO_LIMITE, TRAVESSIA));
    }

    /** Atalho: as seis listas, com armadura e redução do yml. */
    private static String niveis(List<String> espadas, List<Integer> danos,
                                 List<Integer> blocos, List<Integer> flechas) {
        return SkyWarsBuilder.conferirNiveis(espadas, danos, blocos, flechas,
                ARMADURAS, REDUCOES, VIDA_CHEIA, REDUCAO_MAXIMA);
    }

    private static double vao() {
        return SkyWarsCatalog.vaoEntreIlhas(ILHAS, ANEL, RAIO);
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
    //  Os níveis de saque — o coração do jogo
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
    @DisplayName("o saque que mata de dois golpes é recusado")
    void oSaqueQueMataDeDoisGolpesEhRecusado() {
        List<Integer> brabo = List.of(14, 20, 30, 40);
        String erro = niveis(ESPADAS, brabo, BLOCOS, FLECHAS);
        assertNotNull(erro, "40 de dano contra 100 de vida derruba em 2,5 "
                + "golpes e tinha de ser recusado");
        assertTrue(erro.contains("2.5"), "o erro tem de dizer em quantos "
                + "golpes, que é o número que decide: " + erro);

        // E o do yml passa, com folga de um golpe inteiro.
        assertTrue(VIDA_CHEIA / (double) DANOS.get(DANOS.size() - 1) >= 3.0);
    }

    @Test
    @DisplayName("o saque que bate mais que o dobro do pior é recusado")
    void oSaqueQueBateMaisQueODobroEhRecusado() {
        List<Integer> esticado = List.of(10, 14, 18, 22);
        assertNotNull(niveis(ESPADAS, esticado, BLOCOS, FLECHAS),
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
        assertNotNull(niveis(ESPADAS, List.of(14, 18, 16, 22), BLOCOS, FLECHAS),
                "dano descendo do nível 2 para o 3");
        assertNotNull(niveis(ESPADAS, DANOS, List.of(24, 28, 20, 40), FLECHAS),
                "blocos descendo");
        assertNotNull(niveis(ESPADAS, DANOS, BLOCOS, List.of(0, 6, 4, 16)),
                "flechas descendo");
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                        FLECHAS, ARMADURAS, List.of(4, 6, 5, 10), VIDA_CHEIA,
                        REDUCAO_MAXIMA),
                "redução descendo: vestir o conjunto do meio PIORARIA a "
                        + "defesa de quem o pegou, e nada no yml pareceria errado");
    }

    @Test
    @DisplayName("as seis listas de nível têm de ter o mesmo tamanho")
    void aListaCurtaEhRecusada() {
        assertNotNull(niveis(ESPADAS, List.of(14, 18, 22), BLOCOS, FLECHAS),
                "três danos para quatro espadas: o quarto nível leria o "
                        + "último da lista em silêncio");
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                        FLECHAS, List.of("LEATHER", "IRON"), REDUCOES,
                        VIDA_CHEIA, REDUCAO_MAXIMA),
                "duas armaduras para quatro espadas");
    }

    @Test
    @DisplayName("a espada que não existe é recusada")
    void aEspadaInventadaEhRecusada() {
        List<String> torta = List.of("WOODEN_SWORD", "STONE_SWORD",
                "ESPADA_DE_PEDRA_SABAO", "DIAMOND_SWORD");
        assertNotNull(niveis(torta, DANOS, BLOCOS, FLECHAS),
                "nome inventado entregaria espada de madeira sem avisar");
    }

    @Test
    @DisplayName("a família de armadura incompleta é recusada")
    void aArmaduraIncompletaEhRecusada() {
        // TURTLE só existe como capacete: um nível com ela entregaria uma
        // peça onde o nível de baixo entrega quatro.
        assertNotNull(SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                        FLECHAS, List.of("LEATHER", "CHAINMAIL", "TURTLE",
                                "DIAMOND"), REDUCOES, VIDA_CHEIA,
                        REDUCAO_MAXIMA),
                "a tartaruga só tem capacete e o nível dela daria menos "
                        + "armadura que o de baixo");
        for (String familia : ARMADURAS) {
            assertEquals(SkyWarsCatalog.PECAS.size(),
                    SkyWarsCatalog.pecasDaFamilia(familia).size(),
                    "a família " + familia + " não tem as quatro peças");
        }
    }

    /**
     * A ponta de CIMA, que é nova e veio junto com a armadura.
     *
     * As duas pontas antigas cuidam do atacante forte demais. Esta cuida do
     * contrário, que a armadura trouxe: a vítima dura demais. Duas pessoas
     * trocando golpes sem resultado até o anel cair debaixo delas não é
     * empate, é o jogo travando — e nada no yml pareceria errado.
     */
    @Test
    @DisplayName("a luta que não acaba é recusada")
    void aLutaQueNaoAcabaEhRecusada() {
        String erro = SkyWarsBuilder.conferirNiveis(ESPADAS, DANOS, BLOCOS,
                FLECHAS, ARMADURAS, List.of(18, 20, 22, 24), VIDA_CHEIA, 90);
        assertNotNull(erro, "24% por peça com teto de 90% deixa o conjunto "
                + "aparando 90% e a pior espada tirando 1 por golpe");

        // E o do yml acaba: o conjunto completo de diamante apara 40%, e a
        // pior espada ainda derruba em menos de doze golpes.
        double blindagem = SkyWarsCatalog.reducaoTotal(
                SkyWarsCatalog.PECAS.size(),
                REDUCOES.get(REDUCOES.size() - 1), REDUCAO_MAXIMA);
        assertEquals(0.40, blindagem, 1e-9, "quatro peças de 10% com teto de "
                + REDUCAO_MAXIMA + "% têm de dar 40%");
        double golpes = SkyWarsCatalog.golpesPara(VIDA_CHEIA, DANOS.get(0),
                blindagem);
        assertTrue(golpes <= 14, "a pior espada precisa de " + golpes
                + " golpes contra o conjunto completo, e o teto é 14");
        assertTrue(golpes >= 5, "a pior espada derruba o conjunto completo em "
                + golpes + " golpes: com tão pouco, vestir as quatro peças do "
                + "meio não vale a travessia");

        // Esse é o canto extremo — espada de madeira contra diamante cheio, e
        // quem está de madeira DEVE perder essa. A luta que acontece de
        // verdade é entre dois do nível da partida, e ela é curta: com duas
        // peças do próprio nível, a conta fica entre quatro e sete golpes.
        for (int nivel = 0; nivel < DANOS.size(); nivel++) {
            double pelado = SkyWarsCatalog.golpesPara(VIDA_CHEIA,
                    DANOS.get(nivel), 0);
            double parelha = SkyWarsCatalog.golpesPara(VIDA_CHEIA,
                    DANOS.get(nivel), SkyWarsCatalog.reducaoTotal(ARM_MAXIMA,
                            REDUCOES.get(nivel), REDUCAO_MAXIMA));
            assertTrue(parelha >= 3.0 && parelha <= 8.0, "dois jogadores do "
                    + "nível " + nivel + ", cada um com " + ARM_MAXIMA
                    + " peças do próprio nível, levam " + parelha
                    + " golpes para se derrubar — a luta parelha tem de ficar "
                    + "entre três e oito golpes, que é onde o tempo do golpe "
                    + "ainda é a habilidade que decide");
            // E a armadura do próprio nível acrescenta pouco: ela é uma
            // vantagem de verdade sem ser a diferença entre ganhar e perder.
            // Antes de a armadura existir, estas lutas já levavam de 3,8 a
            // 7,1 golpes — a armadura não pode ser o que mudou o jogo.
            assertTrue(parelha <= pelado * 1.3, "no nível " + nivel
                    + " as " + ARM_MAXIMA + " peças do baú da ilha esticam a "
                    + "luta de " + Math.round(pelado * 100) / 100.0 + " para "
                    + Math.round(parelha * 100) / 100.0 + " golpes, que é "
                    + Math.round(parelha / pelado * 100) + "% a mais: acima "
                    + "de trinta por cento, quem abriu o baú com duas peças "
                    + "ganha de quem abriu com uma antes de qualquer jogada");
        }
    }

    @Test
    @DisplayName("a armadura apara, mas nunca zera o dano")
    void aArmaduraNuncaZera() {
        for (int dano = 1; dano <= 40; dano++) {
            for (int pecas = 0; pecas <= SkyWarsCatalog.PECAS.size(); pecas++) {
                for (int porPeca : REDUCOES) {
                    int sobra = SkyWarsCatalog.danoComArmadura(dano,
                            SkyWarsCatalog.reducaoTotal(pecas, porPeca,
                                    REDUCAO_MAXIMA));
                    assertTrue(sobra >= 1, "dano " + dano + " com " + pecas
                            + " peça(s) de " + porPeca + "% virou " + sobra
                            + ": armadura é vantagem, não imunidade — com "
                            + "zero, dois jogadores de armadura cheia ficariam "
                            + "se batendo até o anel cair");
                    assertTrue(sobra <= dano, "a armadura aumentou o dano");
                }
            }
        }
    }

    @Test
    @DisplayName("o teto da redução vale mesmo com mais peças que as quatro")
    void oTetoDaReducaoVale() {
        assertEquals(REDUCAO_MAXIMA / 100.0,
                SkyWarsCatalog.aparo(1000, REDUCAO_MAXIMA), 1e-9,
                "sem teto, um yml com dez por cento a mais por peça faria a "
                        + "vítima virar parede");
        assertEquals(0.0, SkyWarsCatalog.aparo(-5, REDUCAO_MAXIMA), 1e-9,
                "redução negativa não pode virar dano extra");
    }

    @Test
    @DisplayName("um nível só é recusado, porque o meio não teria para onde subir")
    void umNivelSoEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirNiveis(List.of("IRON_SWORD"),
                List.of(22), List.of(32), List.of(10), List.of("IRON"),
                List.of(8), VIDA_CHEIA, REDUCAO_MAXIMA));
    }

    @Test
    @DisplayName("o nível da partida nunca é o melhor, e o meio é um acima")
    void oNivelDaPartidaDeixaOMelhorParaOMeio() {
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
        double vao = vao();
        assertTrue(BLOCOS_GARANTIDOS >= Math.ceil(vao) * 2,
                "o baú garante " + BLOCOS_GARANTIDOS + " blocos e o vão pede "
                        + Math.ceil(vao) + ": sem o dobro, atravessar deixa "
                        + "de ser decisão e vira aposta");
        assertNotNull(SkyWarsBuilder.conferirIlhas(ILHAS, ANEL, RAIO,
                        MEIO_RAIO, 10, BAUS_DA_ILHA),
                "dez blocos garantidos não dão nem a ida");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA, 10,
                        BLOCOS_EXTRAS, CHANCE_ARCO, CHANCE_CURA, COMIDA, vao,
                        DANO_DO_SOCO, DANOS),
                "um baú que deixa quem o abriu preso na própria ilha não é um "
                        + "baú ruim, é um bug");
    }

    @Test
    @DisplayName("o anel apertado e o anel largo demais são recusados")
    void oAnelForaDeMedidaEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirIlhas(ILHAS, 20, RAIO, MEIO_RAIO,
                BLOCOS_GARANTIDOS, BAUS_DA_ILHA),
                "anel de 20 com meio de 9 encosta no meio");
        assertNotNull(SkyWarsBuilder.conferirIlhas(40, ANEL, RAIO, MEIO_RAIO,
                BLOCOS_GARANTIDOS, BAUS_DA_ILHA),
                "quarenta ilhas num anel de 44 ficam encostadas");
        assertNotNull(SkyWarsBuilder.conferirIlhas(4, ANEL, RAIO, MEIO_RAIO,
                BLOCOS_GARANTIDOS, BAUS_DA_ILHA),
                "quatro ilhas num anel de 44 ficam longe demais");
        assertNotNull(SkyWarsBuilder.conferirIlhas(ILHAS, ANEL, 2, MEIO_RAIO,
                BLOCOS_GARANTIDOS, BAUS_DA_ILHA),
                "ilha de raio 2 não tem onde pôr baú sem encostar na beira, e "
                        + "baú na beira some junto com a ilha no cerco");
    }

    // ------------------------------------------------------------------
    //  O BAÚ DA ILHA — a correção
    // ------------------------------------------------------------------

    /**
     * O piso do baú é garantido em TODO sorteio.
     *
     * Este é o teste que guarda a correção. A primeira versão desta arena não
     * tinha baú nas ilhas — o kit chegava na mão de todo mundo, igual — e a
     * intenção era atacar a reclamação mais antiga do gênero. O resultado era
     * outro jogo: sem baú não existe o saque, e o saque é os quinze primeiros
     * segundos de toda partida de Sky Wars.
     *
     * <p>O baú voltou e a loteria é cercada como os próprios jogadores do
     * gênero propõem — garantindo o PISO, não apagando o sorteio. Dez mil
     * sorteios: não pode existir um só que deixe alguém sem espada, sem
     * comida, sem armadura ou sem ponte para sair da ilha.
     */
    @Test
    @DisplayName("o piso do baú é garantido em todo sorteio")
    void oPisoDoBauEhGarantidoEmTodoSorteio() {
        Random sorteio = new Random(20261113L);
        int precisaParaIrEVoltar = (int) Math.ceil(vao()) * 2;
        for (int k = 0; k < SORTEIOS; k++) {
            int nivel = SkyWarsCatalog.nivelDaPartida(ESPADAS.size(), sorteio);
            SkyWarsCatalog.Saque saque = sorteia(nivel, sorteio);

            assertNotNull(saque.espada(), "sorteio " + k + ": baú sem espada");
            assertTrue(saque.espada().name().endsWith("_SWORD"),
                    "sorteio " + k + ": o baú deu " + saque.espada()
                            + " no lugar de uma espada");
            assertTrue(saque.armadura().size() >= ARM_MINIMA,
                    "sorteio " + k + ": o baú deu " + saque.armadura().size()
                            + " peça(s) de armadura e o mínimo é " + ARM_MINIMA
                            + " — quem abrir esse baú perde a partida por "
                            + "sorteio, que é a reclamação do gênero");
            assertTrue(saque.blocos() >= precisaParaIrEVoltar,
                    "sorteio " + k + ": o baú deu " + saque.blocos()
                            + " blocos e ir ao vizinho e voltar pede "
                            + precisaParaIrEVoltar + " — esse baú deixa quem "
                            + "o abriu preso na ilha, esperando o anel cair");
            assertTrue(saque.comida() >= 1, "sorteio " + k + ": baú sem "
                    + "comida — perder de fome por azar não é jogada de ninguém");
        }
    }

    /**
     * O baú NUNCA dá o conjunto completo, e a espada é a mesma em toda ilha.
     *
     * As duas metades da garantia, e elas puxam para lados opostos. A espada
     * fixa é onde a reclamação do gênero morde: "um abre espada de ferro e o
     * outro abre picareta de madeira" é sobre a ARMA. O conjunto que falta é o
     * que mantém a ilha do meio valendo a travessia — uma ilha do anel que já
     * desse as quatro peças tornaria o meio enfeite, e volta a outra
     * reclamação, a de que o baú do meio é inútil e chato.
     */
    @Test
    @DisplayName("o baú da ilha nunca dá o conjunto completo nem muda a espada")
    void oBauDaIlhaNaoDaOConjuntoNemMudaAEspada() {
        Random sorteio = new Random(777L);
        for (int nivel = 0; nivel < ESPADAS.size(); nivel++) {
            Material esperada = Material.matchMaterial(ESPADAS.get(nivel));
            for (int k = 0; k < SORTEIOS / ESPADAS.size(); k++) {
                SkyWarsCatalog.Saque saque = sorteia(nivel, sorteio);
                assertEquals(esperada, saque.espada(), "no nível " + nivel
                        + " um baú deu " + saque.espada() + " no lugar de "
                        + esperada + ": a espada é a mesma em toda ilha, e é "
                        + "nela que a garantia tem de morder");
                assertTrue(saque.armadura().size() < SkyWarsCatalog.PECAS.size(),
                        "um baú da ilha deu o conjunto completo: o conjunto é "
                        + "o prêmio do meio, e uma ilha que já o desse "
                        + "tornaria a travessia desnecessária");
                assertTrue(saque.armadura().size() <= ARM_MAXIMA);
                assertEquals(saque.armadura().size(),
                        new HashSet<>(saque.armadura()).size(),
                        "o baú deu a mesma peça duas vezes: a segunda não "
                                + "veste e o jogador fica com uma peça a menos "
                                + "do que o baú prometeu");
            }
        }
    }

    /**
     * Dois baús quase nunca abrem a mesma coisa.
     *
     * É o outro lado da garantia e é tão necessário quanto ela. Um piso
     * garantido sem sorteio por cima é o kit entregue na mão com um clique a
     * mais — que foi exatamente o erro da versão anterior. O baú tem de ter o
     * que revelar, senão abrir não é uma pergunta.
     */
    @Test
    @DisplayName("dois baús quase nunca abrem a mesma coisa")
    void doisBausQuaseNuncaAbremAMesmaCoisa() {
        Random sorteio = new Random(31337L);
        int iguais = 0;
        int pares = 4000;
        Set<String> vistos = new HashSet<>();
        for (int k = 0; k < pares; k++) {
            SkyWarsCatalog.Saque um = sorteia(1, sorteio);
            SkyWarsCatalog.Saque outro = sorteia(1, sorteio);
            if (assinatura(um).equals(assinatura(outro))) {
                iguais++;
            }
            vistos.add(assinatura(um));
        }
        assertTrue(iguais * 4 < pares, "em " + pares + " pares de baús, "
                + iguais + " abriram exatamente a mesma coisa: um piso "
                + "garantido sem sorteio por cima é o kit entregue na mão com "
                + "um clique a mais, que foi o erro da versão anterior");
        assertTrue(vistos.size() >= 20, "o baú só sabe abrir " + vistos.size()
                + " conteúdo(s) diferente(s): depois da segunda partida "
                + "ninguém tem mais o que descobrir abrindo");
    }

    @Test
    @DisplayName("o arco e a cura aparecem, e nem sempre")
    void oArcoEACuraAparecemENemSempre() {
        Random sorteio = new Random(4242L);
        int comArco = 0;
        int comCura = 0;
        int quantos = 20000;
        for (int k = 0; k < quantos; k++) {
            // Nível 1: o 0 não tem flecha nenhuma, e um baú sem arco por
            // falta de flecha não diz nada sobre a chance.
            SkyWarsCatalog.Saque saque = sorteia(1, sorteio);
            if (saque.arco()) {
                comArco++;
                assertTrue(saque.flechas() > 0, "veio arco sem flecha: um arco "
                        + "sem flecha ocupa a mão e não é jogada nenhuma");
            } else {
                assertEquals(0, saque.flechas(), "vieram flechas sem arco");
            }
            if (saque.curas() > 0) {
                comCura++;
            }
        }
        double pctArco = 100.0 * comArco / quantos;
        double pctCura = 100.0 * comCura / quantos;
        assertTrue(Math.abs(pctArco - CHANCE_ARCO) < 3, "o arco saiu em "
                + pctArco + "% dos baús e o yml pede " + CHANCE_ARCO + "%");
        assertTrue(Math.abs(pctCura - CHANCE_CURA) < 3, "a cura saiu em "
                + pctCura + "% dos baús e o yml pede " + CHANCE_CURA + "%");
    }

    @Test
    @DisplayName("o nível 0 não promete flecha que não tem")
    void oNivelZeroNaoPrometeFlecha() {
        Random sorteio = new Random(5L);
        for (int k = 0; k < 2000; k++) {
            SkyWarsCatalog.Saque saque = sorteia(0, sorteio);
            assertTrue(!saque.arco(), "o nível 0 tem 0 flechas no yml e mesmo "
                    + "assim um baú dele veio com arco");
        }
    }

    @Test
    @DisplayName("o baú que sempre dá tudo e o baú que nunca varia são recusados")
    void oBauSemSorteioEhRecusado() {
        assertNotNull(SkyWarsBuilder.conferirSaque(2, 2, BLOCOS_GARANTIDOS,
                        BLOCOS_EXTRAS, CHANCE_ARCO, CHANCE_CURA, COMIDA, vao(),
                        DANO_DO_SOCO, DANOS),
                "mínimo igual ao máximo: abrir o baú vira formalidade");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, 0, CHANCE_ARCO, CHANCE_CURA, COMIDA,
                        vao(), DANO_DO_SOCO, DANOS),
                "sem blocos extras o baú não tem o que revelar no bloco");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, 100, 100, COMIDA,
                        vao(), DANO_DO_SOCO, DANOS),
                "arco e cura sempre: é o kit na mão com um clique a mais");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, 0, 0, COMIDA, vao(),
                        DANO_DO_SOCO, DANOS),
                "arco e cura nunca: tirando a armadura, todo baú é igual");
        assertNotNull(SkyWarsBuilder.conferirSaque(0, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, CHANCE_ARCO,
                        CHANCE_CURA, COMIDA, vao(), DANO_DO_SOCO, DANOS),
                "zero peças garantidas é o baú que faz perder por sorteio");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, 4,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, CHANCE_ARCO,
                        CHANCE_CURA, COMIDA, vao(), DANO_DO_SOCO, DANOS),
                "o conjunto completo na ilha do anel apaga o valor do meio");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, CHANCE_ARCO,
                        CHANCE_CURA, 0, vao(), DANO_DO_SOCO, DANOS),
                "sem comida garantida dá para perder de fome por azar");
    }

    /**
     * O soco existe porque todo mundo larga sem nada, e vale pouco de
     * propósito.
     *
     * Com o baú de volta, o jogador cai na ilha de mãos vazias. Quem for
     * rushado no segundo três tem de poder revidar — mas se o soco valesse
     * perto da espada, abrir o baú deixaria de ser a primeira coisa a fazer e
     * o saque perderia a razão de existir.
     */
    @Test
    @DisplayName("o soco revida, mas não substitui o baú")
    void oSocoRevidaMasNaoSubstituiOBau() {
        assertTrue(DANO_DO_SOCO * 2 < DANOS.get(0), "o soco tira "
                + DANO_DO_SOCO + " e a pior espada tira " + DANOS.get(0)
                + ": com o soco valendo metade ou mais, correr para o baú "
                + "deixa de ser a jogada de abertura");
        assertTrue(DANO_DO_SOCO >= 1, "soco que não machuca deixa quem foi "
                + "rushado antes de abrir o baú sem resposta nenhuma");
        assertNotNull(SkyWarsBuilder.conferirSaque(ARM_MINIMA, ARM_MAXIMA,
                        BLOCOS_GARANTIDOS, BLOCOS_EXTRAS, CHANCE_ARCO,
                        CHANCE_CURA, COMIDA, vao(), 12, DANOS),
                "soco de 12 contra espada de 14 faria o baú não valer a corrida");

        // E o soco não derruba ninguém rápido: são muitos golpes.
        double golpes = SkyWarsCatalog.golpesPara(VIDA_CHEIA, DANO_DO_SOCO, 0);
        assertTrue(golpes >= 12, "o soco derruba em " + golpes + " golpes");
    }

    @Test
    @DisplayName("os baús da ilha ficam na ilha, não se empilham e não encostam na beira")
    void osBausDaIlhaCabemNaIlha() {
        Set<String> todos = new HashSet<>();
        for (int k = 0; k < ILHAS; k++) {
            int[] centro = SkyWarsCatalog.centroDaIlha(k, ILHAS, ANEL);
            assertNotNull(centro);
            List<int[]> baus = SkyWarsCatalog.pontosDosBausDaIlha(k, ILHAS,
                    ANEL, RAIO, BAUS_DA_ILHA);
            assertEquals(BAUS_DA_ILHA, baus.size(), "a ilha " + k + " tem "
                    + baus.size() + " baú(s) e o yml pede " + BAUS_DA_ILHA);
            for (int[] onde : baus) {
                int dx = onde[0] - centro[0];
                int dz = onde[1] - centro[1];
                assertTrue(ArenaComum.noDiscoMeio(dx, dz, RAIO - 1),
                        "o baú em (" + dx + "," + dz + ") da ilha " + k
                                + " está na beira: ele sumiria junto com a "
                                + "ilha quando o cerco a derrubasse, com o "
                                + "conteúdo dentro");
                assertTrue(!(dx == 0 && dz == 0), "o baú da ilha " + k
                        + " caiu em cima do marco, que ocupa o meio dela");
                assertTrue(todos.add(onde[0] + ":" + onde[1]),
                        "duas ilhas com baú no mesmo bloco do mundo: ("
                                + onde[0] + "," + onde[1] + ")");
            }
        }
        assertEquals(ILHAS * BAUS_DA_ILHA, todos.size());
    }

    @Test
    @DisplayName("o conjunto completo de armadura só existe na ilha do meio")
    void oConjuntoSoExisteNoMeio() {
        for (String familia : ARMADURAS) {
            List<Material> pecas = SkyWarsCatalog.pecasDaFamilia(familia);
            assertEquals(SkyWarsCatalog.PECAS.size(), pecas.size());
            assertEquals(pecas.size(), new HashSet<>(pecas).size(),
                    "a família " + familia + " repetiu uma peça");
        }
        // O baú da ilha para no máximo em ARM_MAXIMA; o do meio dá as quatro.
        assertTrue(ARM_MAXIMA < SkyWarsCatalog.PECAS.size(),
                "a ilha do anel pode dar o conjunto completo e o meio deixa "
                        + "de ter o que oferecer a quem já saqueou a própria "
                        + "ilha");
        // E o meio é um nível acima: as peças dele são de outra família.
        for (int nivel = 0; nivel < ESPADAS.size() - 1; nivel++) {
            int doMeio = SkyWarsCatalog.nivelDoMeio(nivel, ESPADAS.size());
            assertTrue(doMeio > nivel, "o meio do nível " + nivel + " é "
                    + doMeio);
            assertTrue(!ARMADURAS.get(doMeio).equals(ARMADURAS.get(nivel)),
                    "a armadura do meio é da mesma família da ilha no nível "
                            + nivel + ": atravessar daria quatro peças iguais "
                            + "às que já estavam no baú de casa");
        }
    }

    /** A assinatura de um saque, para contar quantos conteúdos diferentes há. */
    private static String assinatura(SkyWarsCatalog.Saque saque) {
        List<String> pecas = new ArrayList<>();
        for (Material peca : saque.armadura()) {
            pecas.add(peca.name());
        }
        java.util.Collections.sort(pecas);
        return saque.espada() + "|" + pecas + "|" + saque.blocos() + "|"
                + saque.arco() + "|" + saque.flechas() + "|" + saque.curas();
    }

    /** Um sorteio de baú com os números do yml, no nível pedido. */
    /**
     * As chances das ferramentas usadas nos sorteios de teste.
     *
     * Não são as do yml: são números que deixam as três ferramentas
     * ACONTECEREM e FALTAREM dentro de dez mil sorteios, que é o que os
     * testes precisam conferir. Se fossem 0 ou 100, metade das perguntas
     * abaixo não teria resposta.
     */
    private static final SkyWarsCatalog.Ferramentas FERRAMENTAS =
            new SkyWarsCatalog.Ferramentas(35, 16, 45, 8);

    // ------------------------------------------------------------------
    //  As ferramentas do gênero
    //
    //  Vieram do estudo do SkyWarsReloaded, cujo baú garantido traz balde
    //  d'água e balde de lava. A arena tinha o equipamento todo — espada,
    //  armadura, arco, bloco de ponte — e nenhuma ferramenta, que é o que se
    //  usa no OUTRO em vez de em si mesmo.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as três ferramentas acontecem E faltam")
    void asFerramentasAcontecemEFaltam() {
        Random sorteio = new Random(90210);
        int agua = 0;
        int lava = 0;
        int bolas = 0;
        int quantos = 10_000;
        for (int k = 0; k < quantos; k++) {
            SkyWarsCatalog.Saque saque = sorteia(1, sorteio);
            if (saque.agua()) {
                agua++;
            }
            if (saque.lava()) {
                lava++;
            }
            if (saque.bolas() > 0) {
                bolas++;
            }
        }
        // Nem nunca nem sempre: ferramenta que sai em todo baú é equipamento
        // disfarçado, e ferramenta que nunca sai é linha morta no yml.
        for (int[] conta : new int[][]{{agua, 35}, {lava, 16}, {bolas, 45}}) {
            assertTrue(conta[0] > 0 && conta[0] < quantos,
                    "ferramenta de " + conta[1] + "% saiu " + conta[0]
                            + " vezes em " + quantos);
            double visto = 100.0 * conta[0] / quantos;
            assertEquals(conta[1], visto, 3.0,
                    "a chance pedida era " + conta[1] + "% e saiu "
                            + String.format("%.1f", visto) + "%");
        }
    }

    @Test
    @DisplayName("as bolas vêm na quantidade cheia ou não vêm")
    void asBolasVemCheiasOuNaoVem() {
        Random sorteio = new Random(4242);
        for (int k = 0; k < 2_000; k++) {
            int bolas = sorteia(2, sorteio).bolas();
            assertTrue(bolas == 0 || bolas == 8,
                    "veio um punhado quebrado de bolas: " + bolas);
        }
    }

    /**
     * O piso garantido não depende de sorte nenhuma das ferramentas.
     *
     * Com as três chances em zero, o baú tem de continuar dando espada,
     * comida, armadura e blocos para a ponte. Ferramenta é sorte boa; o piso
     * é o que impede alguém de abrir um baú e ficar preso na própria ilha.
     */
    @Test
    @DisplayName("sem ferramenta nenhuma o piso continua de pé")
    void semFerramentaOPisoContinua() {
        Random sorteio = new Random(777);
        SkyWarsCatalog.Ferramentas secas =
                new SkyWarsCatalog.Ferramentas(0, 0, 0, 8);
        for (int k = 0; k < 2_000; k++) {
            SkyWarsCatalog.Saque saque = SkyWarsCatalog.sortearSaque(
                    Material.matchMaterial(ESPADAS.get(1)),
                    SkyWarsCatalog.pecasDaFamilia(ARMADURAS.get(1)),
                    ARM_MINIMA, ARM_MAXIMA, BLOCOS_GARANTIDOS, BLOCOS_EXTRAS,
                    CHANCE_ARCO, FLECHAS.get(1), CHANCE_CURA, CURAS_NA_ILHA,
                    COMIDA, secas, sorteio);
            assertFalse(saque.agua());
            assertFalse(saque.lava());
            assertEquals(0, saque.bolas());
            assertNotNull(saque.espada());
            assertTrue(saque.comida() > 0);
            assertTrue(saque.armadura().size() >= ARM_MINIMA);
            assertTrue(saque.blocos() >= BLOCOS_GARANTIDOS);
        }
    }

    /**
     * Mexer na chance de UMA ferramenta não pode mexer no resto do baú.
     *
     * É o motivo de as três serem sorteadas sempre, mesmo com chance zero:
     * se o sorteio pulasse a linha da água quando a chance é 0, ele gastaria
     * um valor a menos do gerador e TODO o resto do baú sairia diferente.
     * Quem baixasse `chance-de-agua` no yml veria as espadas, as armaduras e
     * os blocos mudarem junto, e não teria como saber por quê.
     */
    @Test
    @DisplayName("mexer na chance da água não mexe no resto do baú")
    void aChanceDaAguaNaoMexeNoResto() {
        for (int semente = 0; semente < 200; semente++) {
            SkyWarsCatalog.Saque com = comFerramentas(
                    new SkyWarsCatalog.Ferramentas(100, 16, 45, 8), semente);
            SkyWarsCatalog.Saque sem = comFerramentas(
                    new SkyWarsCatalog.Ferramentas(0, 16, 45, 8), semente);
            assertEquals(com.espada(), sem.espada());
            assertEquals(com.armadura(), sem.armadura());
            assertEquals(com.blocos(), sem.blocos());
            assertEquals(com.arco(), sem.arco());
            assertEquals(com.flechas(), sem.flechas());
            assertEquals(com.curas(), sem.curas());
            // E as OUTRAS duas ferramentas também não se mexem.
            assertEquals(com.lava(), sem.lava());
            assertEquals(com.bolas(), sem.bolas());
            // Só a água muda, que é o que se pediu.
            assertTrue(com.agua());
            assertFalse(sem.agua());
        }
    }

    private static SkyWarsCatalog.Saque comFerramentas(
            SkyWarsCatalog.Ferramentas quais, long semente) {
        return SkyWarsCatalog.sortearSaque(
                Material.matchMaterial(ESPADAS.get(1)),
                SkyWarsCatalog.pecasDaFamilia(ARMADURAS.get(1)),
                ARM_MINIMA, ARM_MAXIMA, BLOCOS_GARANTIDOS, BLOCOS_EXTRAS,
                CHANCE_ARCO, FLECHAS.get(1), CHANCE_CURA, CURAS_NA_ILHA,
                COMIDA, quais, new Random(semente));
    }

    /**
     * O yml tem de trazer as ferramentas, senão elas só existem no código.
     *
     * O padrão do catálogo cobriria a falta em silêncio, e a seção inteira
     * ficaria invisível para quem for ajustar o jogo.
     */
    @Test
    @DisplayName("o sky-wars.yml traz as ferramentas e o preço da lava")
    void oYmlTrazAsFerramentas() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config;
        try (java.io.InputStream in = SkyWarsTest.class.getClassLoader()
                .getResourceAsStream("sky-wars.yml")) {
            config = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(new java.io.InputStreamReader(in,
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        for (String chave : List.of("saque.ferramentas.chance-de-agua",
                "saque.ferramentas.chance-de-lava",
                "saque.ferramentas.chance-de-bolas",
                "saque.ferramentas.bolas-de-neve",
                "saque.ferramentas.perolas-no-meio",
                "regras.dano-da-lava", "regras.balde-segundos",
                "regras.empurrao-da-bola")) {
            assertTrue(config.contains(chave), "falta " + chave);
        }
        // A lava tem de MATAR em alguns segundos e não em um só: atravessar
        // correndo é jogada legítima, parar dentro é que tem de ser morte.
        int lava = config.getInt("regras.dano-da-lava");
        int cheia = config.getInt("regras.vida-cheia");
        assertTrue(lava > 0, "lava sem dano é um balde de tinta laranja");
        assertTrue(lava * 4 <= cheia,
                "a lava mata em " + (cheia / Math.max(1, lava))
                        + "s: atravessar correndo ficou impossível");
        assertTrue(config.getInt("regras.balde-segundos") >= 2,
                "poça que seca antes de a jogada acontecer");
    }

    private static SkyWarsCatalog.Saque sorteia(int nivel, Random sorteio) {
        return SkyWarsCatalog.sortearSaque(
                Material.matchMaterial(ESPADAS.get(nivel)),
                SkyWarsCatalog.pecasDaFamilia(ARMADURAS.get(nivel)),
                ARM_MINIMA, ARM_MAXIMA, BLOCOS_GARANTIDOS, BLOCOS_EXTRAS,
                CHANCE_ARCO, FLECHAS.get(nivel), CHANCE_CURA, CURAS_NA_ILHA,
                COMIDA, FERRAMENTAS, sorteio);
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
