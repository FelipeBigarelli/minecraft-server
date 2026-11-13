package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A LISTA LATERAL.
 *
 * O placar morava na barra de ação: uma linha, só a SUA pontuação e a SUA
 * colocação, e sumia toda vez que outra mensagem usava a mesma barra. O
 * jogador sabia que estava em terceiro e não sabia de quem, por quanto, nem
 * se o segundo estava escapando ou voltando.
 *
 * <p>O que estes testes guardam é a regra do pódio, que é uma regra de
 * EMPATE antes de ser de cor.
 */
@DisplayName("A lista lateral")
class PlacarLateralTest {

    private static List<PlacarLateral.Linha> comValores(String... valores) {
        List<PlacarLateral.Linha> linhas = new ArrayList<>();
        for (int k = 0; k < valores.length; k++) {
            linhas.add(new PlacarLateral.Linha("jogador" + k, valores[k]));
        }
        return linhas;
    }

    @Test
    @DisplayName("ouro, prata e bronze nos três primeiros")
    void oPodioTemOsTresMetais() {
        List<String> saiu = PlacarLateral.montar(
                comValores("30 pts", "20 pts", "10 pts", "5 pts"));
        assertTrue(saiu.get(0).contains(PlacarLateral.OURO), saiu.get(0));
        assertTrue(saiu.get(1).contains(PlacarLateral.PRATA), saiu.get(1));
        assertTrue(saiu.get(2).contains(PlacarLateral.BRONZE), saiu.get(2));
        assertFalse(saiu.get(3).contains(PlacarLateral.OURO));
        assertFalse(saiu.get(3).contains(PlacarLateral.PRATA));
        assertFalse(saiu.get(3).contains(PlacarLateral.BRONZE));
    }

    /**
     * Empatados dividem o lugar — e, portanto, o metal.
     *
     * Sem isto, dois com trinta pontos saem como ouro e prata: o segundo fez
     * exatamente o mesmo que o primeiro e aparece com o metal pior. É o tipo
     * de coisa de que se reclama com razão.
     */
    @Test
    @DisplayName("empatados dividem o lugar e o metal")
    void empatadosDividemOMetal() {
        List<String> saiu = PlacarLateral.montar(
                comValores("30 pts", "30 pts", "10 pts"));
        assertTrue(saiu.get(0).contains(PlacarLateral.OURO));
        assertTrue(saiu.get(1).contains(PlacarLateral.OURO),
                "o empatado em primeiro saiu com outro metal: " + saiu.get(1));
        // E quem vem depois de dois empatados em primeiro é TERCEIRO, não
        // segundo: dois lugares já foram gastos.
        assertTrue(saiu.get(2).contains(PlacarLateral.BRONZE), saiu.get(2));
    }

    @Test
    @DisplayName("três empatados em primeiro não deixam prata nem bronze")
    void tresEmpatadosTomamOPodio() {
        List<String> saiu = PlacarLateral.montar(
                comValores("7 pts", "7 pts", "7 pts", "1 pts"));
        for (int k = 0; k < 3; k++) {
            assertTrue(saiu.get(k).contains(PlacarLateral.OURO), saiu.get(k));
        }
        // O quarto é QUARTO: o pódio inteiro foi gasto pelo empate.
        assertTrue(saiu.get(3).contains("4º"), saiu.get(3));
    }

    /**
     * O Minecraft só desenha quinze linhas. Com mais gente, cortar em
     * silêncio faria o décimo-sexto achar que saiu da partida.
     */
    @Test
    @DisplayName("acima do teto, o resto vira uma linha que se conta")
    void acimaDoTetoSobraUmaLinhaQueSeConta() {
        String[] muitos = new String[20];
        for (int k = 0; k < muitos.length; k++) {
            muitos[k] = (100 - k) + " pts";
        }
        List<String> saiu = PlacarLateral.montar(comValores(muitos));
        assertEquals(PlacarLateral.MAXIMO, saiu.size(),
                "a lista passou do que o Minecraft desenha");
        assertTrue(saiu.get(saiu.size() - 1).contains("e mais 6"),
                "o resto sumiu sem dizer quantos: "
                        + saiu.get(saiu.size() - 1));
    }

    @Test
    @DisplayName("no teto exato ninguém é resumido")
    void noTetoExatoNinguemEhResumido() {
        String[] quinze = new String[PlacarLateral.MAXIMO];
        for (int k = 0; k < quinze.length; k++) {
            quinze[k] = (50 - k) + " pts";
        }
        List<String> saiu = PlacarLateral.montar(comValores(quinze));
        assertEquals(PlacarLateral.MAXIMO, saiu.size());
        assertFalse(saiu.get(saiu.size() - 1).contains("e mais"));
    }

    @Test
    @DisplayName("lista vazia não desenha nada")
    void listaVaziaNaoDesenha() {
        assertTrue(PlacarLateral.montar(List.of()).isEmpty());
    }

    /**
     * As entradas são o índice da lista para o Minecraft: duas iguais
     * colapsariam duas linhas numa só, e o segundo colocado sumiria.
     */
    @Test
    @DisplayName("as quinze entradas são diferentes entre si")
    void asEntradasSaoTodasDiferentes() {
        List<String> vistas = new ArrayList<>();
        for (int k = 0; k < PlacarLateral.MAXIMO; k++) {
            String entrada = PlacarLateral.entradaDe(k);
            assertFalse(vistas.contains(entrada),
                    "a entrada " + k + " repetiu: duas linhas viram uma");
            vistas.add(entrada);
        }
    }

    /** O nome do jogador não pode virar marcação do MiniMessage. */
    @Test
    @DisplayName("nome com sinal de menor não quebra a linha")
    void nomeComMarcacaoNaoQuebra() {
        String saiu = PlacarLateral.escrever(1,
                new PlacarLateral.Linha("<red>eu", "9 pts"));
        assertFalse(saiu.contains("<red>"),
                "o nome entrou como marcação: " + saiu);
    }

    @Test
    @DisplayName("quem está de pé e quem está fora cabem na mesma lista")
    void dePeEForaCabemNaMesmaLista() {
        List<String> saiu = PlacarLateral.montar(
                comValores("de pé", "de pé", "fora"));
        assertEquals(3, saiu.size());
        // Os dois de pé estão empatados: dividem o ouro, e quem caiu vem
        // depois, em terceiro.
        assertTrue(saiu.get(0).contains(PlacarLateral.OURO));
        assertTrue(saiu.get(1).contains(PlacarLateral.OURO));
        assertNotEquals(saiu.get(0), saiu.get(1));
    }
}
