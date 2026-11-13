package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os letreiros do saguão não se atravessam nem afundam no piso.
 *
 * O defeito que este teste tranca apareceu em pé, dentro do jogo: no TNT Run
 * o bloco de regras subia por cima da frase do jogo e descia para dentro do
 * chão do saguão, e as duas coisas ao mesmo tempo. A causa era altura fixa —
 * 11.0 para o nome, 9.4 para a frase, 6.6 para as regras — escolhida no olho
 * para um bloco de regras curto. Seis regras longas quebram em doze linhas e
 * o painel cresce para os DOIS lados do ponto em que nasce.
 *
 * <p>O teste percorre os vinte ymls de verdade e refaz a conta da pilha.
 */
@DisplayName("Letreiros do saguão")
class SaguaoTextosTest {

    private static final List<String> JOGOS = List.of(
            "tnt-run", "spleef", "parkour", "corrida", "lava", "neve",
            "pega-pega", "rei", "labirinto", "mineracao", "pescaria", "quiz",
            "musica", "sorte", "esconde", "arco", "arena-pvp", "build-battle",
            "sky-wars", "bed-wars");

    private static YamlConfiguration ler(String jogo) {
        try (InputStream in = SaguaoTextosTest.class.getClassLoader()
                .getResourceAsStream(jogo + ".yml")) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception erro) {
            throw new IllegalStateException("não li " + jogo + ".yml", erro);
        }
    }

    /** As regras do yml com o enfeite que o saguão põe em volta. */
    private static List<String> corpo(YamlConfiguration yml, String cor) {
        List<String> regras = yml.getStringList("textos.regras");
        if (regras.isEmpty()) {
            return List.of();
        }
        List<String> corpo = new ArrayList<>();
        corpo.add("<color:" + cor + ">━━━ COMO SE JOGA "
                + "━━━</color>");
        for (String linha : regras) {
            corpo.add("<color:" + cor + ">›</color> <color:#C9D1D9>"
                    + linha + "</color>");
        }
        corpo.add("<color:#55595E>━━━━━━━"
                + "━━━━━</color>");
        corpo.add("<color:#6E7681>" + yml.getInt("regras.minimo") + " a "
                + yml.getInt("regras.maximo") + " jogadores</color>");
        return corpo;
    }

    // ------------------------------------------------------------------
    //  A conta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a marcação do MiniMessage não entra na medida do texto")
    void aMarcacaoNaoContaNaMedida() {
        assertEquals("oi", SaguaoPadrao.semMarcacao("<color:#FFF>oi</color>"));
        assertEquals("", SaguaoPadrao.semMarcacao("<bold></bold>"));
        assertEquals("", SaguaoPadrao.semMarcacao(null));
        assertEquals("a b", SaguaoPadrao.semMarcacao("a<gray> </gray>b"));
    }

    @Test
    @DisplayName("uma linha longa conta como as linhas em que ela quebra")
    void aLinhaLongaContaQuebrada() {
        assertEquals(1, SaguaoPadrao.linhasDoTexto(List.of("curta")));
        assertEquals(1, SaguaoPadrao.linhasDoTexto(List.of("x".repeat(34))));
        assertEquals(2, SaguaoPadrao.linhasDoTexto(List.of("x".repeat(35))));
        assertEquals(3, SaguaoPadrao.linhasDoTexto(List.of("x".repeat(100))));
        assertEquals(1, SaguaoPadrao.linhasDoTexto(List.of("")));
        assertEquals(4, SaguaoPadrao.linhasDoTexto(
                List.of("x".repeat(35), "y".repeat(35))));
    }

    @Test
    @DisplayName("o painel cresce com a escala e com o número de linhas")
    void oPainelCresceComOQueTemDentro() {
        double uma = SaguaoPadrao.alturaDoPainel(1, 1.6);
        assertTrue(SaguaoPadrao.alturaDoPainel(2, 1.6) > uma);
        assertTrue(SaguaoPadrao.alturaDoPainel(1, 2.6) > uma);
        assertEquals(uma, SaguaoPadrao.alturaDoPainel(0, 1.6),
                "zero linha não pode medir menos que uma");
    }

    // ------------------------------------------------------------------
    //  A pilha, nos vinte jogos
    // ------------------------------------------------------------------

    /**
     * Nenhum painel afunda no piso nem encosta no de cima, nos vinte.
     *
     * É a conta de {@code escreverTextos} refeita aqui: começa no piso e
     * sobe somando a altura real de cada painel mais a folga.
     */
    @Test
    @DisplayName("em nenhum dos vinte jogos os letreiros se atravessam")
    void aPilhaNuncaSeAtravessa() {
        for (String jogo : JOGOS) {
            YamlConfiguration yml = ler(jogo);
            String cor = yml.getString("textos.cor", "#FFFFFF");
            double escalaPlaca = yml.getDouble("textos.escala-placa", 1.6);
            double escalaTitulo = yml.getDouble("textos.escala-titulo", 2.6);

            double altura = 3.2;
            double topoAnterior = 0.0;

            List<String> corpo = corpo(yml, cor);
            if (!corpo.isEmpty()) {
                double alto = SaguaoPadrao.alturaDoPainel(
                        SaguaoPadrao.linhasDoTexto(corpo), escalaPlaca);
                assertTrue(altura > 0, jogo + ": o bloco de regras afunda no piso");
                topoAnterior = altura + alto;
                altura = topoAnterior + 0.7;
            }

            String frase = yml.getString("textos.subtitulo", "");
            if (!frase.isBlank()) {
                double alto = SaguaoPadrao.alturaDoPainel(
                        SaguaoPadrao.linhasDoTexto(List.of(frase)), escalaPlaca);
                assertTrue(altura >= topoAnterior, jogo
                        + ": a frase encosta no bloco de regras");
                topoAnterior = altura + alto;
                altura = topoAnterior + 0.7;
            }

            double altoDoNome = SaguaoPadrao.alturaDoPainel(1, escalaTitulo);
            assertTrue(altura >= topoAnterior,
                    jogo + ": o nome encosta na frase");
            double topoDoNome = altura + altoDoNome;

            // O letreiro inteiro cabe entre o piso do saguão e o marco.
            assertTrue(topoDoNome < yml.getInt("arena.marco-altura", 30) + 20,
                    jogo + ": a pilha de letreiros sobe " + topoDoNome
                            + " blocos e passa do marco");
        }
    }

    /**
     * O bloco de regras tem fundo mesmo quando o jogo pediu transparente.
     *
     * O jogador descreveu o defeito como "texto sumindo no bloco": doze
     * linhas pequenas e cinzas em cima de pedra clara não se leem.
     */
    @Test
    @DisplayName("o bloco de regras sempre tem fundo atrás do texto")
    void oBlocoDeRegrasTemFundo() {
        assertTrue(SaguaoPadrao.fundoDasRegras(0) > 0,
                "fundo zero deixa o texto denso ilegível contra o muro");
        assertEquals(200, SaguaoPadrao.fundoDasRegras(200),
                "quem escolheu um fundo no yml fica com o dele");
    }

    @Test
    @DisplayName("todo jogo tem regras escritas para o saguão mostrar")
    void todoJogoTemRegras() {
        for (String jogo : JOGOS) {
            YamlConfiguration yml = ler(jogo);
            assertFalse(yml.getStringList("textos.regras").isEmpty(),
                    jogo + ": sem textos.regras o saguão não ensina nada");
        }
    }

    /** O preparo é do arcabouço: todos os vinte têm de ter. */
    @Test
    @DisplayName("os vinte jogos têm preparo antes da largada")
    void osVinteTemPreparo() {
        for (String jogo : JOGOS) {
            int preparo = ler(jogo).getInt("regras.preparo-segundos", 0);
            assertTrue(preparo >= 1 && preparo <= 15,
                    jogo + ": preparo de " + preparo + "s");
        }
    }
}
