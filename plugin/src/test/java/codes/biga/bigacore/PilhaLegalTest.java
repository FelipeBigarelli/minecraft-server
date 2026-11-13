package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PILHA LEGAL.
 *
 * A MINERAÇÃO entregava dois sonares numa pilha só. O sonar é uma LUNETA, e
 * luneta no Minecraft empilha até UM. O que chegava ao jogador não era uma
 * pilha de dois: era uma pilha ilegal, e o CreativePlots deste servidor tem
 * proteção contra isso ligada — escrevia "[Exploit Protection] Illegal stack
 * detected and removed" no bate-papo e APAGAVA o sonar.
 *
 * <p>A partida começava sem a ferramenta em volta da qual o jogo é feito, e
 * nada no plugin dava erro: do nosso lado o item tinha sido entregue. É a
 * mesma armadilha do {@code setUnbreakable} — item válido para o Bukkit e
 * inválido para o anti-exploit que roda ao lado.
 */
@DisplayName("Pilha legal")
class PilhaLegalTest {

    @Test
    @DisplayName("o que cabe numa pilha vai numa pilha só")
    void oQueCabeVaiNumaPilhaSo() {
        assertArrayEquals(new int[]{12}, ArenaComum.fatias(12, 64));
        assertArrayEquals(new int[]{16}, ArenaComum.fatias(16, 16));
        assertArrayEquals(new int[]{1}, ArenaComum.fatias(1, 1));
    }

    /** O caso que quebrou: duas lunetas, que empilham uma. */
    @Test
    @DisplayName("dois sonares são duas pilhas de um, e não uma de dois")
    void doisSonaresSaoDuasPilhas() {
        assertArrayEquals(new int[]{1, 1}, ArenaComum.fatias(2, 1));
        assertArrayEquals(new int[]{1, 1, 1}, ArenaComum.fatias(3, 1));
    }

    @Test
    @DisplayName("o que sobra vai numa pilha menor, no fim")
    void oRestoVaiNoFim() {
        assertArrayEquals(new int[]{16, 16, 4}, ArenaComum.fatias(36, 16));
        assertArrayEquals(new int[]{64, 1}, ArenaComum.fatias(65, 64));
    }

    @Test
    @DisplayName("nada a entregar não ocupa espaço nenhum")
    void nadaNaoOcupaEspaco() {
        assertEquals(0, ArenaComum.fatias(0, 16).length);
        assertEquals(0, ArenaComum.fatias(-5, 16).length);
    }

    /**
     * Nenhuma pilha pode passar do máximo, em nenhuma combinação.
     *
     * É a única coisa que importa: uma pilha acima do máximo é a que o
     * anti-exploit apaga, e apagar é silencioso.
     */
    @Test
    @DisplayName("nenhuma fatia passa do máximo, nunca")
    void nenhumaFatiaPassaDoMaximo() {
        for (int maximo : new int[]{1, 16, 64}) {
            for (int quantos = 1; quantos <= 300; quantos++) {
                int[] pilhas = ArenaComum.fatias(quantos, maximo);
                int soma = 0;
                for (int pilha : pilhas) {
                    assertTrue(pilha >= 1 && pilha <= maximo,
                            "pilha de " + pilha + " com máximo " + maximo);
                    soma += pilha;
                }
                assertEquals(quantos, soma, "sumiu item no caminho: pediu "
                        + quantos + " e entregou " + soma);
            }
        }
    }

    /**
     * Máximo zero ou negativo é um yml torto, e não pode dar divisão por
     * zero nem laço infinito: vale um por pilha.
     */
    @Test
    @DisplayName("máximo torto não trava nem divide por zero")
    void maximoTortoNaoTrava() {
        assertArrayEquals(new int[]{1, 1, 1}, ArenaComum.fatias(3, 0));
        assertArrayEquals(new int[]{1, 1}, ArenaComum.fatias(2, -7));
    }

    /**
     * O sonar da mineração tem de caber em espaços livres do cinto.
     *
     * A picareta fica no espaço 0 e os sonares escorrem a partir do 1. Se o
     * yml pedisse sonares demais, eles passariam do cinto e cairiam na
     * mochila — e o jogador abriria a partida sem ver a ferramenta.
     */
    @Test
    @DisplayName("os sonares cabem no cinto")
    void osSonaresCabemNoCinto() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config;
        try (java.io.InputStream in = PilhaLegalTest.class.getClassLoader()
                .getResourceAsStream("mineracao.yml")) {
            config = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(new java.io.InputStreamReader(in,
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        int sonares = config.getInt("regras.sonares");
        // Luneta empilha 1: cada sonar gasta um espaço inteiro.
        assertTrue(1 + sonares <= 9, "picareta mais " + sonares
                + " sonares não cabem nos 9 espaços do cinto");
    }
}
