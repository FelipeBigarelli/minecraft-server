package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A RUA DO SAGUÃO.
 *
 * O saguão era uma caixa: disco de vidro, parapeito e um arco. Cumpria a
 * função e não era lugar nenhum. Virou uma rua de vila — avenida de grama
 * entre duas fileiras de casas com o andar de cima em balanço, vielas,
 * árvores, lampiões, bandeirinhas e um arco monumental no fundo.
 *
 * <p>As medidas saem todas do RAIO, e é isso que faz a mesma planta servir
 * aos vinte jogos. Estes testes são sobre o que acontece quando o raio muda:
 * é lá que uma rua desenhada para raio 9 vira um corredor entupido em raio 6
 * ou um descampado em raio 20, e nada no yml pareceria errado.
 */
@DisplayName("A rua do saguão")
class SaguaoRuaTest {

    /** Os raios que os vinte ymls usam hoje, e as pontas em volta deles. */
    private static final int[] RAIOS = {6, 7, 8, 9, 10, 11, 12, 14, 16, 20};

    @Test
    @DisplayName("em todo raio sobra avenida, calçada e casa — nessa ordem")
    void asTresFaixasExistemEmTodoRaio() {
        for (int raio : RAIOS) {
            int avenida = SaguaoPadrao.meiaAvenida(raio);
            int calcada = SaguaoPadrao.xDaCalcada(raio);
            int fachada = SaguaoPadrao.xDaFachada(raio);

            assertTrue(avenida >= 2, "raio " + raio + ": avenida de "
                    + (avenida * 2 + 1) + " blocos não é rua, é corredor");
            assertEquals(avenida + 1, calcada);
            assertEquals(avenida + 2, fachada);
            // A fachada tem de caber DENTRO do parapeito, senão a fileira de
            // casas nasce em cima da beira e o saguão fica sem muro.
            assertTrue(fachada <= raio - 1, "raio " + raio
                    + ": a fachada cairia em cima do parapeito (x=" + fachada
                    + ", beira=" + raio + ")");
        }
    }

    /**
     * A avenida não pode encostar na fachada.
     *
     * Se encostasse, as casas nasceriam na beira da trilha e o balanço do
     * andar de cima ficaria por cima da cabeça de quem anda no meio da rua —
     * a rua viraria um túnel.
     */
    @Test
    @DisplayName("a calçada separa a avenida das casas, sempre")
    void aCalcadaSempreSepara() {
        for (int raio : RAIOS) {
            int avenida = SaguaoPadrao.meiaAvenida(raio);
            int fachada = SaguaoPadrao.xDaFachada(raio);
            assertTrue(fachada - avenida >= 2, "raio " + raio
                    + ": a casa encosta na avenida");
        }
    }

    /**
     * O meio da rua é do jogador.
     *
     * Ele pousa em (0, 0) olhando para o arco. Se a planta pusesse casa,
     * árvore ou poste nessa coluna, o jogador nasceria dentro de uma parede.
     */
    @Test
    @DisplayName("ninguém constrói na coluna do meio da rua")
    void oMeioDaRuaFicaLivre() {
        for (int raio : RAIOS) {
            assertTrue(SaguaoPadrao.meiaAvenida(raio) >= 1,
                    "raio " + raio + ": não sobra meio de rua");
            assertTrue(SaguaoPadrao.xDaCalcada(raio) > 1, "raio " + raio
                    + ": a calçada cairia em cima do ponto de pouso");
        }
    }

    /**
     * As casas vêm em módulos de seis: cinco de casa, um de viela.
     *
     * A viela não é enfeite. É por ela que entra a luz do céu, é onde ficam
     * as árvores e os postes, e é o chão de vidro por onde se vê a arena lá
     * embaixo. Uma fileira sem viela é um corredor de duas paredes lisas.
     */
    @Test
    @DisplayName("a cada cinco de casa vem uma viela")
    void aVielaVemACadaCinco() {
        int zArco = -7;
        int casas = 0;
        int vielas = 0;
        for (int z = zArco; z < zArco + 60; z++) {
            if (SaguaoPadrao.temCasa(z, zArco)) {
                casas++;
                assertFalse(SaguaoPadrao.noMeioDaViela(z, zArco),
                        "z=" + z + " é casa E viela ao mesmo tempo");
            } else {
                vielas++;
                assertTrue(SaguaoPadrao.noMeioDaViela(z, zArco),
                        "z=" + z + " não é casa e não é viela: é um buraco");
            }
        }
        assertEquals(50, casas);
        assertEquals(10, vielas);
    }

    /**
     * A primeira casa começa colada no arco.
     *
     * A fileira é contada A PARTIR do arco de propósito: assim o fundo da rua
     * é sempre parede de casa emoldurando o arco, e nunca uma viela aberta
     * bem ao lado dele — que deixaria o ponto de fuga da avenida com um vão
     * torto de um lado só.
     */
    @Test
    @DisplayName("a fileira começa no arco, e não num lugar qualquer")
    void aFileiraComecaNoArco() {
        for (int raio : RAIOS) {
            int zArco = SaguaoPadrao.zDoArcoDeVolta(raio);
            assertTrue(SaguaoPadrao.temCasa(zArco, zArco),
                    "raio " + raio + ": o arco nasceu dentro de uma viela");
            assertTrue(SaguaoPadrao.temCasa(zArco + 1, zArco));
            assertFalse(SaguaoPadrao.temCasa(zArco + 5, zArco));
        }
    }

    /**
     * O muro das placas tem de caber entre as duas fileiras de casas.
     *
     * As placas de ENTRAR e SAIR ficam em x=±4, no muro que ladeia o arco. Se
     * a fachada das casas passasse por dentro desse muro, a casa comeria a
     * placa — e a placa é como se entra na fila.
     */
    @Test
    @DisplayName("as placas de ENTRAR e SAIR não ficam dentro de uma casa")
    void asPlacasNaoFicamDentroDeCasa() {
        for (int raio : RAIOS) {
            int fachada = SaguaoPadrao.xDaFachada(raio);
            assertTrue(fachada >= 4, "raio " + raio
                    + ": a fileira de casas (x=" + fachada
                    + ") engole a placa em x=4");
        }
    }

    /**
     * O arco não pode ser engolido pela fileira.
     *
     * O vão dele vai de x=-2 a x=2 e {@code dentroDaVolta} é quem decide quem
     * volta ao lobby. Se uma casa nascesse dentro desse vão, a saída do
     * saguão estaria murada e o jogador ficaria preso lá dentro.
     */
    @Test
    @DisplayName("o vão do arco fica fora da fileira de casas")
    void oVaoDoArcoFicaLivre() {
        for (int raio : RAIOS) {
            assertTrue(SaguaoPadrao.xDaFachada(raio) > 2, "raio " + raio
                    + ": a casa foi construída dentro do vão do arco");
        }
    }

    // ------------------------------------------------------------------
    //  As paredes que explicam o jogo
    // ------------------------------------------------------------------

    /**
     * Toda rua tem de ter vaga de placa dos DOIS lados.
     *
     * Só de um lado, metade das instruções ficaria atrás do jogador quando
     * ele pousa — e quem pousa está olhando para o arco.
     */
    @Test
    @DisplayName("as vagas de placa vêm aos pares, uma de cada lado")
    void asVagasVemAosPares() {
        for (int raio : RAIOS) {
            java.util.List<int[]> vagas = SaguaoPadrao.vagasDeParede(raio);
            assertTrue(vagas.size() >= 2, "raio " + raio
                    + ": a rua não tem onde pendurar instrução nenhuma");
            assertEquals(0, vagas.size() % 2, "raio " + raio
                    + ": sobrou vaga sem par do outro lado");
            for (int k = 0; k < vagas.size(); k += 2) {
                assertEquals(-1, vagas.get(k)[0]);
                assertEquals(1, vagas.get(k + 1)[0]);
                assertEquals(vagas.get(k)[1], vagas.get(k + 1)[1],
                        "o par não está no mesmo ponto da rua");
            }
        }
    }

    /** Nenhuma placa pode nascer fora do saguão. */
    @Test
    @DisplayName("as vagas ficam todas dentro da rua")
    void asVagasFicamDentroDaRua() {
        for (int raio : RAIOS) {
            for (int[] vaga : SaguaoPadrao.vagasDeParede(raio)) {
                assertTrue(Math.abs(vaga[1]) <= raio - 1, "raio " + raio
                        + ": vaga em z=" + vaga[1] + " passou do parapeito");
                assertTrue(vaga[1] >= SaguaoPadrao.zDoArcoDeVolta(raio),
                        "raio " + raio + ": vaga atrás do arco, onde ninguém "
                                + "passa");
            }
        }
    }

    /**
     * Parágrafo que não cabe não some: junta-se ao último.
     *
     * Regra que o saguão não mostra é regra que o jogador descobre perdendo.
     */
    @Test
    @DisplayName("parágrafo a mais se junta ao último, e não some")
    void oParagrafoQueSobraNaoSome() {
        java.util.List<String> oito = new java.util.ArrayList<>();
        for (int k = 1; k <= 8; k++) {
            oito.add("regra" + k);
        }
        java.util.List<String> saiu = SaguaoPadrao.repartir(oito, 6);
        assertEquals(6, saiu.size());
        for (int k = 1; k <= 8; k++) {
            boolean achou = false;
            for (String placa : saiu) {
                if (placa.contains("regra" + k)) {
                    achou = true;
                    break;
                }
            }
            assertTrue(achou, "a regra" + k + " sumiu da rua");
        }
    }

    @Test
    @DisplayName("cabendo tudo, nada é juntado")
    void cabendoTudoNadaEhJuntado() {
        java.util.List<String> tres = java.util.List.of("a", "b", "c");
        assertEquals(tres, SaguaoPadrao.repartir(tres, 6));
    }

    @Test
    @DisplayName("sem instrução, nenhuma placa é pendurada")
    void semInstrucaoNenhumaPlaca() {
        assertTrue(SaguaoPadrao.repartir(java.util.List.of(), 6).isEmpty());
        assertTrue(SaguaoPadrao.repartir(java.util.List.of("a"), 0).isEmpty());
    }

    /** O cabeçalho diz quantas placas existem, senão não se sabe se faltou. */
    @Test
    @DisplayName("o cabeçalho de cada placa diz qual é e quantas são")
    void oCabecalhoDizQualEQuantas() {
        String saiu = SaguaoPadrao.comCabecalho("o texto", "#FF0000", 2, 4);
        assertTrue(saiu.contains("2/4"), saiu);
        assertTrue(saiu.contains("#FF0000"), saiu);
        assertTrue(saiu.contains("o texto"), saiu);
    }

    /**
     * Os letreiros começam ACIMA do frontão do arco.
     *
     * Antes começavam na altura do olho, e o maior painel da rua ficava
     * pendurado bem no vão da saída: o jogo tapava a própria porta. O arco
     * com frontão sobe até nove blocos; a pilha tem de começar depois disso.
     */
    @Test
    @DisplayName("a pilha de letreiros não tapa o arco de volta")
    void osLetreirosNaoTapamOArco() throws Exception {
        java.lang.reflect.Field campo =
                SaguaoPadrao.class.getDeclaredField("ALTURA_LIVRE");
        campo.setAccessible(true);
        double livre = (double) campo.get(null);
        int topoDoArco = SaguaoPadrao.alturaDoArcoDeVolta() + 4;
        assertTrue(livre > topoDoArco, "os letreiros começam em " + livre
                + " e o arco vai até " + topoDoArco);
    }
}
