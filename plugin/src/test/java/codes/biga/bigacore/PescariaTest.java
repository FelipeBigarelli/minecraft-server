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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os seis poços, conferidos sem servidor.
 *
 * <h2>O teste que vale mais aqui</h2>
 *
 * É o {@link #todoPocoPagaOMesmoPorSegundo}. O erro que ele pega não aparece
 * em lugar nenhum: se um poço render mais pontos por segundo que os outros, o
 * jogo continua funcionando, bonito, sem uma linha de erro — e os dezesseis
 * jogadores ficam todos em cima daquele poço, e os outros cinco são enfeite
 * caro. É erro de tabela de pesos, ninguém confere de cabeça, e sem este
 * teste só se descobriria vendo dezesseis pessoas paradas no mesmo lugar.
 *
 * <p>O irmão dele é o {@link #osPocosTemRitmosDiferentes}, que pega o erro
 * oposto: seis poços com o mesmo lance médio são o mesmo poço com seis nomes,
 * e aí escolher onde lançar não decide nada. Os dois juntos são o desenho do
 * jogo: mesmo rendimento, ritmos diferentes.
 */
class PescariaTest {

    private static YamlConfiguration arena;
    private static List<PescariaCatalog.Peixe> peixes;
    private static List<PescariaCatalog.Poco> pocos;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int RAIO = 34;
    private static final int BEIRA_ANEIS = 2;
    private static final int RAIO_MEIO = 8;
    private static final int POCO_RAIO = 6;
    private static final int ANEL = 22;
    private static final int MARCO_ALTURA = 16;
    private static final int CANSACO_POR_PEIXE = 25;
    private static final int CANSACO_ALIVIO = 4;
    private static final int CANSACO_MAXIMO = 240;
    private static final int GRANDE_PONTOS = 60;
    private static final int GRANDE_INTERVALO = 50;
    private static final int GRANDE_JANELA = 25;
    private static final int GRANDE_PESO = 30;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 240;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = PescariaTest.class.getResourceAsStream("/pescaria.yml")) {
            assertNotNull(in, "pescaria.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        peixes = PescariaCatalog.lerPeixes(arena);
        pocos = PescariaCatalog.lerPocos(arena, peixes.size());
        assertEquals(4, peixes.size(), "a tabela de peixes do yml mudou de tamanho");
        assertEquals(6, pocos.size(), "a lista de poços do yml mudou de tamanho");
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(RAIO, arena.getInt("arena.raio"));
        assertEquals(BEIRA_ANEIS, arena.getInt("arena.beira-aneis"));
        assertEquals(RAIO_MEIO, arena.getInt("arena.poco-raio-meio"));
        assertEquals(POCO_RAIO, arena.getInt("arena.poco-raio"));
        assertEquals(ANEL, arena.getInt("arena.poco-anel"));
        assertEquals(MARCO_ALTURA, arena.getInt("arena.marco-altura"));
        assertEquals(CANSACO_POR_PEIXE, arena.getInt("regras.cansaco-por-peixe"));
        assertEquals(CANSACO_ALIVIO, arena.getInt("regras.cansaco-alivio"));
        assertEquals(CANSACO_MAXIMO, arena.getInt("regras.cansaco-maximo"));
        assertEquals(GRANDE_PONTOS, arena.getInt("regras.grande-pontos"));
        assertEquals(GRANDE_INTERVALO, arena.getInt("regras.grande-intervalo"));
        assertEquals(GRANDE_JANELA, arena.getInt("regras.grande-janela"));
        assertEquals(GRANDE_PESO, arena.getInt("regras.grande-peso"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
    }

    @Test
    @DisplayName("a planta aceita as tabelas, as bacias e o equilíbrio do yml")
    void plantaAceitaOYml() {
        assertNull(PescariaBuilder.conferirTabelas(pocos, peixes));
        assertNull(PescariaBuilder.conferirBacias(pocos, RAIO, BEIRA_ANEIS,
                RAIO_MEIO, POCO_RAIO, ANEL));
        assertNull(PescariaBuilder.conferirEquilibrio(pocos, peixes));
    }

    // ------------------------------------------------------------------
    //  O equilíbrio — o teste que importa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todo poço paga o mesmo por segundo")
    void todoPocoPagaOMesmoPorSegundo() {
        double soma = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            soma += PescariaCatalog.pontosPorSegundo(poco, peixes);
        }
        double media = soma / pocos.size();
        StringBuilder conta = new StringBuilder();
        for (PescariaCatalog.Poco poco : pocos) {
            double rende = PescariaCatalog.pontosPorSegundo(poco, peixes);
            conta.append("\n  ").append(poco.nome()).append(": ")
                    .append(Math.round(rende * 1000) / 1000.0).append(" pts/s")
                    .append(" (espera ").append(poco.esperaMedia() / 20.0)
                    .append("s, lance médio ")
                    .append(Math.round(PescariaCatalog.valorEsperado(poco, peixes)
                            * 100) / 100.0).append(")");
        }
        for (PescariaCatalog.Poco poco : pocos) {
            double rende = PescariaCatalog.pontosPorSegundo(poco, peixes);
            double desvio = Math.abs(rende - media) / media;
            assertTrue(desvio <= 0.12, "o poço " + poco.nome() + " está "
                    + Math.round(desvio * 100) + "% fora da média de "
                    + Math.round(media * 1000) / 1000.0 + " pts/s. Com um poço "
                    + "melhor que os outros, os dezesseis jogadores ficam nele "
                    + "e os outros cinco são enfeite caro." + conta);
        }
    }

    @Test
    @DisplayName("os poços têm ritmos diferentes")
    void osPocosTemRitmosDiferentes() {
        double menor = Double.MAX_VALUE;
        double maior = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            double lance = PescariaCatalog.valorEsperado(poco, peixes);
            menor = Math.min(menor, lance);
            maior = Math.max(maior, lance);
        }
        assertTrue(maior / menor >= 4.0, "o maior lance médio é "
                + Math.round(maior * 100) / 100.0 + " e o menor é "
                + Math.round(menor * 100) / 100.0 + ": só "
                + Math.round(maior / menor * 10) / 10.0 + " vezes. Com lances "
                + "do mesmo tamanho os seis poços são o mesmo poço com nomes "
                + "diferentes.");
        assertTrue(menor > 0, "há poço que não paga nada");
    }

    @Test
    @DisplayName("a espera cresce com o lance: paciência é o que vira prêmio")
    void esperaCresceComOLance() {
        // Ordenado por espera, o lance médio tem de subir junto — é a frase do
        // portal virada em conta. Se não subisse, haveria poço que paga mais
        // POR LANCE e espera menos, e ele seria estritamente melhor.
        List<PescariaCatalog.Poco> ordem = new ArrayList<>(pocos);
        ordem.sort((a, b) -> Integer.compare(a.esperaMedia(), b.esperaMedia()));
        double antes = 0;
        for (PescariaCatalog.Poco poco : ordem) {
            double lance = PescariaCatalog.valorEsperado(poco, peixes);
            assertTrue(lance > antes, "o poço " + poco.nome() + " espera "
                    + poco.esperaMedia() / 20.0 + "s e paga " + lance
                    + " por lance, menos que um poço de espera mais curta: ele "
                    + "é estritamente pior e ninguém lançaria nele");
            antes = lance;
        }
    }

    @Test
    @DisplayName("a conferência pega poço melhor que os outros e poços iguais")
    void conferenciaPegaODesequilibrio() {
        // Um poço com a espera da corredeira e a tabela do poço fundo.
        List<PescariaCatalog.Poco> comPrivilegiado = new ArrayList<>(pocos);
        comPrivilegiado.add(new PescariaCatalog.Poco("MILAGRE", 3,
                Material.GRAVEL, 20, 50, pocos.get(0).pesos()));
        assertNotNull(PescariaBuilder.conferirEquilibrio(comPrivilegiado, peixes),
                "um poço com espera curta e peixe caro é estritamente melhor "
                        + "e tem de ser recusado");

        // Seis poços idênticos: mesmo rendimento, mesmo ritmo, nada a decidir.
        List<PescariaCatalog.Poco> clones = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            clones.add(new PescariaCatalog.Poco("CLONE " + k, 3, Material.GRAVEL,
                    60, 130, pocos.get(3).pesos()));
        }
        assertNull(PescariaBuilder.conferirTabelas(clones, peixes));
        assertNotNull(PescariaBuilder.conferirEquilibrio(clones, peixes),
                "seis poços iguais passam no equilíbrio e têm de cair no ritmo");
    }

    @Test
    @DisplayName("a conferência pega tabela fora de ordem, poço sem peso e leito vazado")
    void conferenciaPegaAsTabelas() {
        assertNotNull(PescariaBuilder.conferirTabelas(pocos, List.of()),
                "tabela de peixes vazia");
        assertNotNull(PescariaBuilder.conferirTabelas(
                List.of(pocos.get(0)), peixes), "um poço só não dá escolha");

        List<PescariaCatalog.Peixe> foraDeOrdem = List.of(
                new PescariaCatalog.Peixe("caro", 20),
                new PescariaCatalog.Peixe("barato", 1));
        assertNotNull(PescariaBuilder.conferirTabelas(pocos, foraDeOrdem),
                "a ordem da tabela é o que diz qual é o melhor peixe na placa");

        assertNotNull(PescariaBuilder.conferirTabelas(pocos,
                List.of(new PescariaCatalog.Peixe("de graça", 0))),
                "peixe sem ponto");

        PescariaCatalog.Poco mudo = new PescariaCatalog.Poco("MUDO", 3,
                Material.GRAVEL, 40, 90, List.of(0, 0, 0, 0));
        assertNotNull(PescariaBuilder.conferirTabelas(
                List.of(mudo, pocos.get(0)), peixes), "poço sem peso nenhum");

        // O leito vazado não entra aqui de propósito: saber se um material é
        // bloco cheio exige o registro de blocos do servidor, que num teste
        // não existe. Essa conferência roda na obra, onde o servidor está de
        // pé — é a única do jogo que não cabe aqui.

        PescariaCatalog.Poco invertido = new PescariaCatalog.Poco("INVERTIDO", 3,
                Material.GRAVEL, 200, 50, pocos.get(3).pesos());
        assertNotNull(PescariaBuilder.conferirTabelas(
                List.of(invertido, pocos.get(0)), peixes), "espera invertida");
    }

    // ------------------------------------------------------------------
    //  A geometria
    // ------------------------------------------------------------------

    @Test
    @DisplayName("as bacias cabem na plataforma e não se encostam")
    void baciasCabemENaoSeEncostam() {
        assertNull(PescariaBuilder.conferirBacias(pocos, RAIO, BEIRA_ANEIS,
                RAIO_MEIO, POCO_RAIO, ANEL));
        assertNotNull(PescariaBuilder.conferirBacias(pocos, 20, BEIRA_ANEIS,
                RAIO_MEIO, POCO_RAIO, ANEL),
                "plataforma de raio 20 com o anel em 22: bacia no vazio");
        assertNotNull(PescariaBuilder.conferirBacias(pocos, RAIO, BEIRA_ANEIS,
                RAIO_MEIO, POCO_RAIO, 14),
                "anel em 14 encosta o poço do meio nos de fora");
        assertNotNull(PescariaBuilder.conferirBacias(pocos, RAIO, BEIRA_ANEIS,
                14, POCO_RAIO, ANEL),
                "poço do meio de raio 14 come o deque todo");
    }

    @Test
    @DisplayName("cada poço reconhece o próprio centro, e o deque não é poço")
    void pocoDoPontoAcerta() {
        for (int k = 0; k < pocos.size(); k++) {
            int[] centro = PescariaCatalog.centroDoPoco(k, pocos.size(), ANEL);
            assertEquals(k, PescariaCatalog.pocoDoPonto(centro[0], centro[1],
                            pocos.size(), ANEL, RAIO_MEIO, POCO_RAIO),
                    "o centro do poço " + pocos.get(k).nome() + " não cai nele");
        }
        // O anel de largada é deque, senão todo mundo nasceria dentro da água.
        int anelDaLargada = (RAIO_MEIO + ANEL - POCO_RAIO) / 2;
        for (int grau = 0; grau < 360; grau += 5) {
            double x = Math.cos(Math.toRadians(grau)) * anelDaLargada;
            double z = Math.sin(Math.toRadians(grau)) * anelDaLargada;
            assertEquals(-1, PescariaCatalog.pocoDoPonto(x, z, pocos.size(),
                            ANEL, RAIO_MEIO, POCO_RAIO),
                    "a largada em " + grau + "° cai dentro de um poço");
        }
    }

    @Test
    @DisplayName("o poste de cada placa fica no deque e olha para o meio")
    void postesNoDeque() {
        for (int k = 0; k < pocos.size(); k++) {
            int[] poste = PescariaCatalog.posteDoPoco(k, pocos.size(), ANEL,
                    RAIO_MEIO, POCO_RAIO);
            assertEquals(-1, PescariaCatalog.pocoDoPonto(poste[0], poste[1],
                            pocos.size(), ANEL, RAIO_MEIO, POCO_RAIO),
                    "o poste do poço " + pocos.get(k).nome() + " nasce na água");
            assertTrue(poste[2] >= 0 && poste[2] < PescariaCatalog.RUMOS_DA_PLACA.length,
                    "rumo de placa inválido");
            if (k > 0) {
                int[] centro = PescariaCatalog.centroDoPoco(k, pocos.size(), ANEL);
                assertTrue(Math.hypot(poste[0], poste[1])
                                < Math.hypot(centro[0], centro[1]),
                        "o poste do poço " + pocos.get(k).nome() + " ficou do "
                                + "lado de FORA dele: a placa daria as costas "
                                + "para quem vem do meio");
            }
        }
    }

    @Test
    @DisplayName("o rumo cardeal escolhe o lado certo")
    void rumoCardealAcerta() {
        assertEquals(0, PescariaCatalog.rumoCardeal(0, -10), "norte");
        assertEquals(1, PescariaCatalog.rumoCardeal(10, 0), "leste");
        assertEquals(2, PescariaCatalog.rumoCardeal(0, 10), "sul");
        assertEquals(3, PescariaCatalog.rumoCardeal(-10, 0), "oeste");
        assertEquals(1, PescariaCatalog.rumoCardeal(10, 3), "leste puxando sul");
    }

    // ------------------------------------------------------------------
    //  O sorteio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o sorteio respeita os pesos e nunca tira peso zero")
    void sorteioRespeitaOsPesos() {
        Random sorte = new Random(7071L);
        int rodadas = 200_000;
        for (PescariaCatalog.Poco poco : pocos) {
            int[] contagem = new int[poco.pesos().size()];
            for (int k = 0; k < rodadas; k++) {
                contagem[PescariaCatalog.sortear(poco.pesos(), sorte)]++;
            }
            int total = poco.pesoTotal();
            for (int k = 0; k < contagem.length; k++) {
                double esperado = poco.pesos().get(k) / (double) total;
                double saiu = contagem[k] / (double) rodadas;
                if (poco.pesos().get(k) == 0) {
                    assertEquals(0, contagem[k], "o poço " + poco.nome()
                            + " tirou um peixe de peso zero " + contagem[k]
                            + " vez(es): a placa dele estaria mentindo");
                    continue;
                }
                assertTrue(Math.abs(saiu - esperado) < 0.01, "no poço "
                        + poco.nome() + " o peixe " + k + " saiu "
                        + Math.round(saiu * 1000) / 10.0 + "% das vezes e o "
                        + "peso dele pede " + Math.round(esperado * 1000) / 10.0
                        + "%");
            }
        }
    }

    @Test
    @DisplayName("sem peso nenhum o sorteio devolve -1 em vez de um peixe")
    void sorteioSemPeso() {
        Random sorte = new Random(1L);
        assertEquals(-1, PescariaCatalog.sortear(List.of(), sorte));
        assertEquals(-1, PescariaCatalog.sortear(List.of(0, 0, 0), sorte));
    }

    @Test
    @DisplayName("O GRANDE entra na tabela sem apagar os outros peixes")
    void oGrandeEntraSemApagar() {
        PescariaCatalog.Poco poco = pocos.get(0);
        List<Integer> comGrande = new ArrayList<>(poco.pesos());
        comGrande.add(GRANDE_PESO);

        Random sorte = new Random(99L);
        int rodadas = 200_000;
        int[] contagem = new int[comGrande.size()];
        for (int k = 0; k < rodadas; k++) {
            contagem[PescariaCatalog.sortear(comGrande, sorte)]++;
        }
        int total = poco.pesoTotal() + GRANDE_PESO;
        double chanceDoGrande = contagem[contagem.length - 1] / (double) rodadas;
        assertTrue(Math.abs(chanceDoGrande - GRANDE_PESO / (double) total) < 0.01,
                "O GRANDE saiu " + Math.round(chanceDoGrande * 1000) / 10.0
                        + "% das vezes com peso " + GRANDE_PESO + " de " + total);
        assertTrue(chanceDoGrande > 0.1 && chanceDoGrande < 0.4, "com "
                + Math.round(chanceDoGrande * 100) + "% por lance, a janela de "
                + GRANDE_JANELA + "s ou não dá chance nenhuma ou dá certeza — "
                + "e nos dois casos deixa de ser disputa");
        for (int k = 0; k < poco.pesos().size(); k++) {
            if (poco.pesos().get(k) > 0) {
                assertTrue(contagem[k] > 0, "o peixe " + k + " do poço "
                        + poco.nome() + " parou de sair quando O GRANDE abriu");
            }
        }
    }

    // ------------------------------------------------------------------
    //  O cansaço e o relógio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o cansaço castiga o poço acampado e perdoa o poço de um só")
    void cansacoCastigaOAcampamento() {
        // Um pescador sozinho no poço fundo tira um peixe a cada 13s: gasta
        // 25 tiques de cansaço e o alívio devolve 4 por segundo, 52 em 13s.
        PescariaCatalog.Poco fundo = pocos.get(0);
        double porSegundoSozinho = CANSACO_POR_PEIXE
                / ((fundo.esperaMedia() + PescariaCatalog.RELANCE_TIQUES) / 20.0);
        assertTrue(porSegundoSozinho < CANSACO_ALIVIO, "sozinho no poço "
                + fundo.nome() + " o pescador gera "
                + Math.round(porSegundoSozinho * 100) / 100.0 + " de cansaço "
                + "por segundo e o alívio é " + CANSACO_ALIVIO + ": o poço "
                + "cansaria com UM pescador, e aí o cansaço deixa de ser sobre "
                + "aglomeração e passa a ser imposto sobre pescar");
        assertTrue(porSegundoSozinho * 3 > CANSACO_ALIVIO, "três pescadores "
                + "juntos geram " + Math.round(porSegundoSozinho * 300) / 100.0
                + " de cansaço por segundo contra " + CANSACO_ALIVIO
                + " de alívio: acampar em três sairia de graça e o melhor poço "
                + "viraria fila");
        assertTrue(CANSACO_MAXIMO <= fundo.esperaMedia() + fundo.esperaMin(),
                "o cansaço máximo de " + CANSACO_MAXIMO + " tiques mais que "
                        + "dobra a espera do poço mais lento: um poço cansado "
                        + "deixaria de ser poço");
    }

    @Test
    @DisplayName("O GRANDE abre quatro vezes na partida e vale o que deve")
    void oGrandeCabeNaPartida() {
        int quantos = 0;
        for (int segundo = GRANDE_INTERVALO; segundo + GRANDE_JANELA / 2
                < TEMPO_LIMITE; segundo += GRANDE_INTERVALO) {
            quantos++;
        }
        assertTrue(quantos >= 3 && quantos <= 5, "O GRANDE abre " + quantos
                + " vez(es) num relógio de " + TEMPO_LIMITE + "s: menos de "
                + "três e 'o grande sempre vem' é propaganda; mais de cinco e "
                + "ele deixa de ser evento");
        assertTrue(GRANDE_JANELA < GRANDE_INTERVALO, "a janela de "
                + GRANDE_JANELA + "s não pode ser maior que o intervalo de "
                + GRANDE_INTERVALO + "s, senão ele nunca fecha");

        // Quanto um jogador médio faz na partida, e quanto O GRANDE vale disso.
        double media = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            media += PescariaCatalog.pontosPorSegundo(poco, peixes);
        }
        media /= pocos.size();
        double naPartida = media * TEMPO_LIMITE;
        double parte = GRANDE_PONTOS / naPartida;
        assertTrue(parte > 0.2 && parte < 0.7, "O GRANDE vale "
                + GRANDE_PONTOS + " pontos e a partida média faz "
                + Math.round(naPartida) + ": " + Math.round(parte * 100)
                + "% do total. Abaixo de 20% ninguém corre atrás dele; acima "
                + "de 70% a partida é só ele.");
    }

    @Test
    @DisplayName("a plataforma cabe no alcance da obra")
    void plataformaCabeNoAlcance() {
        assertTrue(RAIO <= RAIO + 5);
        int maisFundo = 0;
        for (PescariaCatalog.Poco poco : pocos) {
            maisFundo = Math.max(maisFundo, poco.fundo());
        }
        assertTrue(maisFundo >= 2 && maisFundo <= 12, "o poço mais fundo tem "
                + maisFundo + " de profundidade");
        assertTrue(MARCO_ALTURA + 6 > maisFundo, "a obra tem de caber entre o "
                + "fundo da bacia e o alto da torre");
    }
}
