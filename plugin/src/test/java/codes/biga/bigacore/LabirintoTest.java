package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O labirinto do dia, cavado sem servidor.
 *
 * <h2>O que pode dar errado aqui e não lança exceção</h2>
 *
 * Uma célula nascer ILHADA: o jogador que entrar nela sai, mas o mapa do dia
 * tem um pedaço que não leva a lugar nenhum e ninguém entende por quê. A
 * grade dizer que passa e o mapa de blocos não abrir o vão: labirinto
 * IMPOSSÍVEL, com dezesseis pessoas dentro e nenhuma linha de erro no log. A
 * porta cair a cinco passos da sala: um dia inteiro de partidas de dez
 * segundos. A porta cair a oitenta: um dia inteiro de partidas que estouram o
 * relógio. Sobrar um segundo buraco na borda: dois caminhos de saída num jogo
 * que promete um. Os números do yml virarem pares: a sala do meio sai meio
 * bloco torta do (0,0), que é onde o saguão fica e onde todo mundo larga.
 *
 * <p>Nada disso quebra. Tudo isso deixa o jogo de pé e errado.
 *
 * <h2>Por que o teste anda quatrocentos dias</h2>
 *
 * Porque o labirinto é sorteado, e um sorteio testado uma vez é um sorteio
 * não testado. O mapa de hoje passa; é o de 14 de março que tem a porta a
 * três passos da sala. Rodar o ano inteiro custa menos de um segundo e é a
 * única forma de a promessa "muda todo dia" valer todo dia.
 */
class LabirintoTest {

    private static YamlConfiguration arena;

    /** Os números da planta, repetidos aqui para o teste não depender dela. */
    private static final int Y = 100;
    private static final int CELULAS = 13;
    private static final int CELULA = 3;
    private static final int PAREDE = 1;
    private static final int PAREDE_ALTURA = 3;
    private static final int SALA = 1;
    private static final int SAIDA_FUNDO = 5;
    private static final int SAIDA_LARGURA = 7;
    private static final int EXTRAS = 12;
    private static final int MINIMOS = 12;
    private static final int MAXIMOS = 30;
    private static final int TENTATIVAS = 64;
    private static final long SEMENTE = 4712L;
    private static final int GIZ = 12;
    private static final int MAXIMO = 16;
    private static final int TEMPO_LIMITE = 300;

    /** Quantos dias o teste anda. Mais de um ano. */
    private static final int DIAS = 400;

    private static final int LADO = LabirintoCatalog.lado(CELULAS, CELULA, PAREDE);
    private static final int MEIO = (LADO - 1) / 2;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = LabirintoTest.class.getResourceAsStream("/labirinto.yml")) {
            assertNotNull(in, "labirinto.yml não foi empacotado");
            arena = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static long sementeDoDia(long dia) {
        return LabirintoCatalog.sementeDoDia(CELULAS, SEMENTE, dia, EXTRAS, SALA,
                MINIMOS, MAXIMOS, TENTATIVAS);
    }

    private static boolean[] doDia(long dia) {
        return LabirintoCatalog.cavar(CELULAS, sementeDoDia(dia), EXTRAS, SALA);
    }

    private static int passosDoDia(long dia) {
        long semente = sementeDoDia(dia);
        boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
        int[] dist = LabirintoCatalog.distancias(CELULAS, ligado, SALA);
        int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
        return dist[LabirintoCatalog.indice(CELULAS, saida[0], saida[1])];
    }

    // ------------------------------------------------------------------
    //  O yml
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o yml diz os números que o teste usa")
    void ymlBate() {
        assertEquals(Y, arena.getInt("arena.y"));
        assertEquals(CELULAS, arena.getInt("arena.celulas"));
        assertEquals(CELULA, arena.getInt("arena.celula"));
        assertEquals(PAREDE, arena.getInt("arena.parede"));
        assertEquals(PAREDE_ALTURA, arena.getInt("arena.parede-altura"));
        assertEquals(SALA, arena.getInt("arena.sala"));
        assertEquals(SAIDA_FUNDO, arena.getInt("arena.saida-fundo"));
        assertEquals(SAIDA_LARGURA, arena.getInt("arena.saida-largura"));
        assertEquals(EXTRAS, arena.getInt("arena.aberturas-extras"));
        assertEquals(MINIMOS, arena.getInt("arena.passos-minimos"));
        assertEquals(MAXIMOS, arena.getInt("arena.passos-maximos"));
        assertEquals(TENTATIVAS, arena.getInt("arena.tentativas"));
        assertEquals(SEMENTE, arena.getLong("arena.semente"));
        assertEquals(GIZ, arena.getInt("regras.giz"));
        assertEquals(MAXIMO, arena.getInt("regras.maximo"));
        assertEquals(TEMPO_LIMITE, arena.getInt("regras.tempo-limite"));
        assertEquals(-1, arena.getInt("arena.dia-fixo"),
                "o yml de produção não pode sair com o mapa preso num dia");
    }

    @Test
    @DisplayName("a planta aceita os números do yml")
    void plantaAceitaOYml() {
        assertNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO));
    }

    @Test
    @DisplayName("a conferência pega célula par, muro baixo e sala grande demais")
    void conferenciaPegaOsErros() {
        assertNotNull(LabirintoBuilder.conferirNumeros(12, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO),
                "13 células é ímpar de propósito: com par a sala sai torta");
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, 2, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO));
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, 2,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, SAIDA_FUNDO));
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                2, SALA, SAIDA_LARGURA, SAIDA_FUNDO),
                "muro de dois blocos se pula, e um labirinto que se pula não é um");
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                PAREDE_ALTURA, 5, SAIDA_LARGURA, SAIDA_FUNDO),
                "sala de raio 5 em 13 células encosta na borda");
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, 2, SAIDA_FUNDO),
                "patamar mais estreito que o corredor que ele recebe");
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, 6, SAIDA_FUNDO),
                "patamar de largura par não fica centrado no vão");
        assertNotNull(LabirintoBuilder.conferirNumeros(CELULAS, CELULA, PAREDE,
                PAREDE_ALTURA, SALA, SAIDA_LARGURA, 1));
    }

    // ------------------------------------------------------------------
    //  O labirinto, dia por dia
    // ------------------------------------------------------------------

    @Test
    @DisplayName("todo dia tem labirinto, e nenhuma célula fica ilhada")
    void todoDiaTemLabirinto() {
        for (long dia = 0; dia < DIAS; dia++) {
            boolean[] ligado = doDia(dia);
            int[] dist = LabirintoCatalog.distancias(CELULAS, ligado, SALA);
            for (int j = 0; j < CELULAS; j++) {
                for (int i = 0; i < CELULAS; i++) {
                    assertTrue(dist[LabirintoCatalog.indice(CELULAS, i, j)] >= 0,
                            "dia " + dia + ": a célula (" + i + "," + j
                                    + ") ficou ilhada");
                }
            }
        }
    }

    @Test
    @DisplayName("a caminhada até a porta fica sempre dentro da janela")
    void aCaminhadaValeAPena() {
        int menor = Integer.MAX_VALUE;
        int maior = 0;
        for (long dia = 0; dia < DIAS; dia++) {
            int passos = passosDoDia(dia);
            assertTrue(passos >= MINIMOS && passos <= MAXIMOS,
                    "dia " + dia + ": a porta ficou a " + passos + " passos da "
                            + "sala, fora da janela [" + MINIMOS + ", " + MAXIMOS
                            + "] — a re-sortagem da semente não deu conta");
            menor = Math.min(menor, passos);
            maior = Math.max(maior, passos);
        }
        assertTrue(maior - menor >= 8, "em " + DIAS + " dias a caminhada variou "
                + "só de " + menor + " a " + maior + " passos: a janela está "
                + "apertada demais e todo dia é igual");
    }

    @Test
    @DisplayName("a mesma semente dá sempre o mesmo labirinto")
    void mesmaSementeMesmoLabirinto() {
        for (long dia = 0; dia < 40; dia++) {
            assertEquals(sementeDoDia(dia), sementeDoDia(dia),
                    "a escolha da semente do dia não pode variar");
            boolean[] uma = doDia(dia);
            boolean[] outra = doDia(dia);
            assertEquals(List.of(), diferencas(uma, outra),
                    "dia " + dia + ": cavar duas vezes deu dois labirintos");
        }
    }

    @Test
    @DisplayName("dias diferentes dão labirintos diferentes")
    void diasDiferentesMudamOMapa() {
        Set<String> vistos = new HashSet<>();
        for (long dia = 0; dia < 120; dia++) {
            vistos.add(assinatura(doDia(dia), LabirintoCatalog.saidaDoDia(
                    CELULAS, sementeDoDia(dia))));
        }
        assertEquals(120, vistos.size(), "em 120 dias saíram " + vistos.size()
                + " labirintos distintos: a promessa é um por dia");
    }

    @Test
    @DisplayName("o labirinto tem voltas, e não um caminho só")
    void oLabirintoTemVoltas() {
        for (long dia = 0; dia < DIAS; dia++) {
            boolean[] ligado = doDia(dia);
            int abertas = 0;
            for (boolean passa : ligado) {
                if (passa) {
                    abertas++;
                }
            }
            // Um labirinto perfeito é uma árvore: células - 1 passagens. Cada
            // passagem a mais é um ciclo, e ciclo é rota alternativa.
            int arvore = CELULAS * CELULAS - 1;
            assertTrue(abertas > arvore + EXTRAS / 2,
                    "dia " + dia + ": " + abertas + " passagens para "
                            + CELULAS * CELULAS + " células — perto demais da "
                            + "árvore (" + arvore + "), o labirinto saiu "
                            + "perfeito e errar a curva custa a partida");
        }
    }

    // ------------------------------------------------------------------
    //  O mapa em blocos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("o mapa de blocos é todo andável a partir da porta")
    void oMapaEhTodoAndavel() {
        for (long dia = 0; dia < DIAS; dia++) {
            long semente = sementeDoDia(dia);
            boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
            int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
            assertNull(LabirintoBuilder.conferirMapa(CELULAS, CELULA, PAREDE,
                    ligado, SALA, saida), "dia " + dia);
        }
    }

    @Test
    @DisplayName("a borda é fechada, menos pelo vão da porta")
    void aBordaEhFechada() {
        for (long dia = 0; dia < DIAS; dia++) {
            long semente = sementeDoDia(dia);
            boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
            int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
            int[] mapa = LabirintoCatalog.mapaDeBlocos(CELULAS, CELULA, PAREDE,
                    ligado, SALA, saida);
            int vaos = 0;
            for (int u = 0; u < LADO; u++) {
                for (int v = 0; v < LADO; v++) {
                    boolean naBorda = u == 0 || v == 0 || u == LADO - 1 || v == LADO - 1;
                    if (naBorda && mapa[v * LADO + u] == LabirintoCatalog.PISO) {
                        vaos++;
                    }
                }
            }
            assertEquals(CELULA * PAREDE, vaos, "dia " + dia + ": a borda tem "
                    + vaos + " blocos de vão e a porta pede " + CELULA * PAREDE);
        }
    }

    @Test
    @DisplayName("a porta nunca cai num canto, onde ficam as torres")
    void aPortaNuncaCaiNoCanto() {
        for (long dia = 0; dia < DIAS; dia++) {
            int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, sementeDoDia(dia));
            boolean cantoI = saida[0] == 0 || saida[0] == CELULAS - 1;
            boolean cantoJ = saida[1] == 0 || saida[1] == CELULAS - 1;
            assertTrue(!(cantoI && cantoJ), "dia " + dia + ": a porta saiu na "
                    + "célula de canto (" + saida[0] + "," + saida[1] + "), e o "
                    + "patamar dela encostaria nas duas torres");
            assertTrue(saida[2] >= 0 && saida[2] <= 3, "lado inválido");
        }
    }

    @Test
    @DisplayName("os quatro lados saem sorteados ao longo do ano")
    void osQuatroLadosSaem() {
        int[] quantos = new int[4];
        for (long dia = 0; dia < DIAS; dia++) {
            quantos[LabirintoCatalog.saidaDoDia(CELULAS, sementeDoDia(dia))[2]]++;
        }
        for (int lado = 0; lado < 4; lado++) {
            assertTrue(quantos[lado] >= DIAS / 10, "em " + DIAS + " dias a porta "
                    + "saiu só " + quantos[lado] + " vez(es) no lado "
                    + LabirintoBuilder.nomeDoLado(lado) + ": o sorteio está "
                    + "viciado e os jogadores vão decorar um lado só");
        }
    }

    @Test
    @DisplayName("os corredores têm a largura da planta")
    void osCorredoresTemLargura() {
        long semente = sementeDoDia(7);
        boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
        int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
        int[] mapa = LabirintoCatalog.mapaDeBlocos(CELULAS, CELULA, PAREDE,
                ligado, SALA, saida);
        int[] vao = LabirintoCatalog.vaoDaPorta(CELULAS, CELULA, PAREDE, saida);
        for (int v = 0; v < LADO; v++) {
            for (int u = 0; u < LADO; u++) {
                if (mapa[v * LADO + u] != LabirintoCatalog.PISO) {
                    continue;
                }
                boolean noVao = u >= vao[0] && u < vao[0] + vao[1]
                        && v >= vao[2] && v < vao[2] + vao[3];
                if (noVao) {
                    continue;
                }
                int i = LabirintoCatalog.celulaDe(u, CELULA, PAREDE, CELULAS);
                int j = LabirintoCatalog.celulaDe(v, CELULA, PAREDE, CELULAS);
                assertTrue(i >= 0 || j >= 0, "a casa de piso (" + u + "," + v
                        + ") não pertence a célula nenhuma em nenhum eixo: é "
                        + "um buraco no cruzamento de dois muros");
            }
        }
    }

    @Test
    @DisplayName("a sala do meio é uma sala, e fica sobre o (0,0) do mundo")
    void aSalaFicaNoMeio() {
        long semente = sementeDoDia(3);
        boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
        int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
        int[] mapa = LabirintoCatalog.mapaDeBlocos(CELULAS, CELULA, PAREDE,
                ligado, SALA, saida);

        int centro = LabirintoCatalog.meioDaCelula(CELULAS / 2, CELULA, PAREDE);
        assertEquals(MEIO, centro, "o meio da célula do meio tem de cair "
                + "exatamente no meio do labirinto, que é o (0,0) do mundo");

        int alcance = SALA * (CELULA + PAREDE) + (CELULA - 1) / 2;
        int mouroes = 0;
        for (int u = centro - alcance; u <= centro + alcance; u++) {
            for (int v = centro - alcance; v <= centro + alcance; v++) {
                // O mourão é o quadradinho que sobra no cruzamento de duas
                // faixas de muro: abrir passagem abre a faixa entre duas
                // células e nunca o cruzamento. São as colunas do salão.
                boolean mourao = emFaixaDeMuro(u) && emFaixaDeMuro(v);
                if (mourao) {
                    mouroes++;
                }
                assertEquals(mourao ? LabirintoCatalog.MURO : LabirintoCatalog.PISO,
                        mapa[v * LADO + u], "a sala do meio está errada em ("
                                + u + "," + v + ")");
            }
        }
        int esperados = (2 * SALA) * (2 * SALA) * PAREDE * PAREDE;
        assertEquals(esperados, mouroes, "o salão de raio " + SALA + " tem de "
                + "sair com " + esperados + " coluna(s), uma por cruzamento de "
                + "faixas de muro");
        int[] dist = LabirintoCatalog.distancias(CELULAS, ligado, SALA);
        for (int j = CELULAS / 2 - SALA; j <= CELULAS / 2 + SALA; j++) {
            for (int i = CELULAS / 2 - SALA; i <= CELULAS / 2 + SALA; i++) {
                assertEquals(0, dist[LabirintoCatalog.indice(CELULAS, i, j)],
                        "sair por qualquer casa da sala custa o mesmo");
            }
        }
    }

    @Test
    @DisplayName("as largadas cabem na sala e não se repetem")
    void asLargadasCabemNaSala() {
        int anel = SALA * (CELULA + PAREDE);
        int alcance = SALA * (CELULA + PAREDE) + (CELULA - 1) / 2;
        List<int[]> pontos = new ArrayList<>();
        for (int k = 0; k < MAXIMO; k++) {
            double angulo = 2 * Math.PI * k / MAXIMO;
            pontos.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        Set<String> unicos = new HashSet<>();
        for (int[] onde : pontos) {
            assertTrue(Math.abs(onde[0]) < alcance && Math.abs(onde[1]) < alcance,
                    "a largada em (" + onde[0] + "," + onde[1] + ") encosta no "
                            + "muro da sala, de alcance " + alcance);
            assertTrue(unicos.add(onde[0] + ":" + onde[1]),
                    "duas largadas no mesmo bloco: (" + onde[0] + ","
                            + onde[1] + ")");
            assertTrue(!(emFaixaDeMuro(onde[0] + MEIO) && emFaixaDeMuro(onde[1] + MEIO)),
                    "a largada em (" + onde[0] + "," + onde[1] + ") cai dentro "
                            + "de uma coluna do salão");
        }
        assertEquals(MAXIMO, unicos.size());
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a partida mais longa cabe no relógio")
    void partidaCabeNoRelogio() {
        // O caminho mais curto, em blocos, no pior dia da janela.
        double blocos = MAXIMOS * (CELULA + PAREDE);
        // Correndo a 5,6 blocos por segundo e vasculhando quatro vezes o
        // caminho ótimo, que é o que custa não saber o caminho.
        double segundos = blocos / 5.6 * 4;
        assertTrue(segundos < TEMPO_LIMITE, "no pior dia da janela a caminhada "
                + "é de " + Math.round(blocos) + " blocos, que dá "
                + Math.round(segundos) + "s vasculhando — e o relógio é de "
                + TEMPO_LIMITE + "s");
        assertTrue(segundos > TEMPO_LIMITE / 4.0, "no pior dia da janela a "
                + "partida leva " + Math.round(segundos) + "s de um relógio de "
                + TEMPO_LIMITE + "s: o relógio está folgado demais para "
                + "apertar ninguém");
    }

    @Test
    @DisplayName("o giz dá para marcar o caminho e não para marcar tudo")
    void oGizDaParaOCaminho() {
        assertTrue(GIZ >= MINIMOS / 2, "com " + GIZ + " marcas e " + MINIMOS
                + " esquinas de caminho mínimo, o giz não marca nem metade "
                + "das escolhas");
        assertTrue(GIZ < CELULAS * CELULAS / 4, "com " + GIZ + " marcas num "
                + "labirinto de " + CELULAS * CELULAS + " células o jogador "
                + "marca o labirinto todo e deixa de ter de escolher");
        assertEquals(MAXIMO, LabirintoCatalog.GIZES.size(),
                "uma cor de giz por jogador, e são " + MAXIMO + " jogadores");
        assertEquals(MAXIMO, new HashSet<>(LabirintoCatalog.GIZES).size(),
                "duas cores iguais e dois jogadores leriam o mesmo rastro");
    }

    @Test
    @DisplayName("o vão da porta sai da célula de saída, em qualquer lado")
    void oVaoSaiDaCelula() {
        for (int lado = 0; lado < 4; lado++) {
            int k = CELULAS / 2;
            int[] saida = switch (lado) {
                case LabirintoCatalog.NORTE -> new int[]{k, 0, lado};
                case LabirintoCatalog.SUL -> new int[]{k, CELULAS - 1, lado};
                case LabirintoCatalog.OESTE -> new int[]{0, k, lado};
                default -> new int[]{CELULAS - 1, k, lado};
            };
            int[] vao = LabirintoCatalog.vaoDaPorta(CELULAS, CELULA, PAREDE, saida);
            assertEquals(CELULA * PAREDE, vao[1] * vao[3], "o vão do lado "
                    + LabirintoBuilder.nomeDoLado(lado) + " tem de ter a "
                    + "largura do corredor pela espessura do muro");
            boolean encostaNaBorda = vao[0] == 0 || vao[2] == 0
                    || vao[0] + vao[1] == LADO || vao[2] + vao[3] == LADO;
            assertTrue(encostaNaBorda, "o vão do lado "
                    + LabirintoBuilder.nomeDoLado(lado) + " não encosta na "
                    + "borda: não é porta, é buraco no meio do muro");
        }
    }

    @Test
    @DisplayName("a célula mais próxima aceita o vão entre duas células")
    void celulaMaisProximaAceitaOVao() {
        int passo = CELULA + PAREDE;
        for (int u = 0; u < LADO; u++) {
            int exato = LabirintoCatalog.celulaDe(u, CELULA, PAREDE, CELULAS);
            int perto = Math.max(0, Math.min(CELULAS - 1, (u - PAREDE) / passo));
            if (exato >= 0) {
                assertEquals(exato, perto, "no bloco " + u + " a conta exata e a "
                        + "arredondada têm de concordar");
            }
            assertTrue(perto >= 0 && perto < CELULAS,
                    "o bloco " + u + " caiu fora da grade");
        }
    }

    @Test
    @DisplayName("as torres de canto não encostam no patamar da porta")
    void torresLongeDoPatamar() {
        int meiaLargura = (SAIDA_LARGURA - 1) / 2 + 1;
        for (long dia = 0; dia < DIAS; dia++) {
            long semente = sementeDoDia(dia);
            int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
            int indice = saida[2] == LabirintoCatalog.NORTE
                    || saida[2] == LabirintoCatalog.SUL ? saida[0] : saida[1];
            int centro = LabirintoCatalog.meioDaCelula(indice, CELULA, PAREDE) - MEIO;
            assertTrue(Math.abs(centro) + meiaLargura < MEIO,
                    "dia " + dia + ": o patamar vai de " + (centro - meiaLargura)
                            + " a " + (centro + meiaLargura) + " e a torre fica "
                            + "em " + MEIO);
        }
    }

    // ------------------------------------------------------------------
    //  Miudezas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("os rumos e as tangentes são os quatro cardeais")
    void rumosSaoCardeais() {
        assertEquals(4, LabirintoCatalog.RUMOS.length);
        Set<String> vistos = new HashSet<>();
        for (int[] rumo : LabirintoCatalog.RUMOS) {
            assertEquals(1, Math.abs(rumo[0]) + Math.abs(rumo[1]),
                    "rumo diagonal não existe numa grade de labirinto");
            assertTrue(vistos.add(rumo[0] + ":" + rumo[1]), "rumo repetido");
            int[] lado = LabirintoBuilder.tangente(rumo);
            assertEquals(0, rumo[0] * lado[0] + rumo[1] * lado[1],
                    "a tangente tem de ser perpendicular ao rumo");
            assertEquals(1, Math.abs(lado[0]) + Math.abs(lado[1]));
        }
    }

    @Test
    @DisplayName("a grade cabe no alcance da obra")
    void gradeCabeNoAlcance() {
        int alcance = MEIO + SAIDA_FUNDO + 4;
        int fundoDoPatamar = MEIO + SAIDA_FUNDO + 1;
        assertTrue(fundoDoPatamar <= alcance, "o patamar vai até "
                + fundoDoPatamar + " e o desfazer só limpa até " + alcance);
        assertEquals(53, LADO, "13 células de 3 com muro de 1 dão 53 blocos");
        assertEquals(26, MEIO);
    }

    @Test
    @DisplayName("a passagem é a mesma vista das duas células")
    void passagemEhSimetrica() {
        boolean[] ligado = doDia(11);
        for (int j = 0; j < CELULAS; j++) {
            for (int i = 0; i < CELULAS; i++) {
                for (int[] rumo : LabirintoCatalog.RUMOS) {
                    int ni = i + rumo[0];
                    int nj = j + rumo[1];
                    if (!LabirintoCatalog.dentro(CELULAS, ni, nj)) {
                        continue;
                    }
                    assertEquals(
                            LabirintoCatalog.passa(ligado, CELULAS, i, j, ni, nj),
                            LabirintoCatalog.passa(ligado, CELULAS, ni, nj, i, j),
                            "a parede entre (" + i + "," + j + ") e (" + ni + ","
                                    + nj + ") tem dois donos que discordam");
                }
            }
        }
    }

    @Test
    @DisplayName("andar a pé pelo mapa dá o mesmo que andar pela grade")
    void mapaEGradeConcordam() {
        long semente = sementeDoDia(23);
        boolean[] ligado = LabirintoCatalog.cavar(CELULAS, semente, EXTRAS, SALA);
        int[] saida = LabirintoCatalog.saidaDoDia(CELULAS, semente);
        int[] mapa = LabirintoCatalog.mapaDeBlocos(CELULAS, CELULA, PAREDE,
                ligado, SALA, saida);

        // Partindo do meio da sala, a pé pelo mapa, quantas células dá para
        // alcançar? Tem de ser todas — a grade diz que são todas.
        Set<Integer> alcancadas = new HashSet<>();
        boolean[] visto = new boolean[LADO * LADO];
        Deque<int[]> fila = new ArrayDeque<>();
        visto[MEIO * LADO + MEIO] = true;
        fila.add(new int[]{MEIO, MEIO});
        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            int i = LabirintoCatalog.celulaDe(onde[0], CELULA, PAREDE, CELULAS);
            int j = LabirintoCatalog.celulaDe(onde[1], CELULA, PAREDE, CELULAS);
            if (i >= 0 && j >= 0) {
                alcancadas.add(LabirintoCatalog.indice(CELULAS, i, j));
            }
            for (int[] rumo : LabirintoCatalog.RUMOS) {
                int u = onde[0] + rumo[0];
                int v = onde[1] + rumo[1];
                if (u < 0 || v < 0 || u >= LADO || v >= LADO
                        || visto[v * LADO + u]
                        || mapa[v * LADO + u] != LabirintoCatalog.PISO) {
                    continue;
                }
                visto[v * LADO + u] = true;
                fila.add(new int[]{u, v});
            }
        }
        assertEquals(CELULAS * CELULAS, alcancadas.size(), "a pé pelo mapa "
                + "chega-se a " + alcancadas.size() + " células das "
                + CELULAS * CELULAS + " que a grade diz existirem");
    }

    // ------------------------------------------------------------------
    //  Utilidades do teste
    // ------------------------------------------------------------------

    /** O bloco cai numa faixa de muro, e não dentro de uma célula? */
    private static boolean emFaixaDeMuro(int u) {
        return LabirintoCatalog.celulaDe(u, CELULA, PAREDE, CELULAS) < 0;
    }

    private static List<Integer> diferencas(boolean[] a, boolean[] b) {
        List<Integer> saida = new ArrayList<>();
        for (int k = 0; k < a.length; k++) {
            if (a[k] != b[k]) {
                saida.add(k);
            }
        }
        return saida;
    }

    private static String assinatura(boolean[] ligado, int[] saida) {
        StringBuilder texto = new StringBuilder(saida[0] + ":" + saida[1] + ":"
                + saida[2] + ":");
        for (boolean passa : ligado) {
            texto.append(passa ? '1' : '0');
        }
        return texto.toString();
    }
}
