package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere o conteudo do paredao de ajuda do spawn.
 *
 * O risco que isto cobre e especifico: um marcador escrito errado no
 * ajuda.yml nao da erro nenhum. O PainelAjuda nao reconhece
 * {@code <terreno-preço-16>} com acento, nao troca por numero, e o
 * MiniMessage tambem nao sabe o que fazer com ele — o resultado e um painel
 * no spawn mostrando o texto cru do marcador para todo jogador novo, e so um
 * humano olhando a parede perceberia.
 */
class AjudaPainelTest {

    /**
     * Todo marcador que o PainelAjuda.resolver() sabe trocar.
     *
     * Ao ensinar um marcador novo ao resolver, ele entra aqui tambem. E
     * duplicacao de proposito: a lista e o contrato entre o YAML e o codigo,
     * e e ela que transforma um erro de digitacao em teste vermelho.
     */
    private static final Set<String> CONHECIDOS = Set.of(
            "<moeda-inicial>", "<lojas-limite>", "<taxa-loja>", "<simbolo>",
            "<preco-terreno-1>", "<preco-terreno-2>", "<preco-terreno-3>",
            "<terreno-bloco>", "<terreno-min>", "<terreno-max>",
            "<terreno-limite>", "<terreno-reembolso>");

    /** O <terreno-preco-N> aceita qualquer lado, entao casa por padrao. */
    private static final Pattern PRECO_POR_LADO =
            Pattern.compile("<terreno-preco-\\d{1,4}>");

    /**
     * Candidato a marcador: comeca por uma das familias que o resolver trata.
     *
     * Nao pode varrer todo {@code <...>} do arquivo — <gray>, <bold> e
     * <gradient:...> sao MiniMessage e nao passam pelo resolver. Prender a
     * busca nos prefixos conhecidos separa marcador de tag sem obrigar a
     * manter uma lista de cores aqui dentro.
     */
    private static final Pattern CANDIDATO =
            Pattern.compile("<(?:moeda|preco|lojas|taxa|simbolo|terreno)[^<>]*>");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carregar() throws Exception {
        try (InputStream in = AjudaPainelTest.class.getResourceAsStream("/ajuda.yml")) {
            assertNotNull(in, "ajuda.yml não foi empacotado em target/classes");
            return new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> todasAsLinhas(Map<String, Object> raiz) {
        List<Map<String, Object>> paineis =
                (List<Map<String, Object>>) raiz.get("paineis");
        assertNotNull(paineis, "ajuda.yml sem a lista 'paineis'");

        List<String> texto = new ArrayList<>();
        for (Map<String, Object> painel : paineis) {
            texto.add((String) painel.get("titulo"));
            texto.addAll((List<String>) painel.get("linhas"));
        }
        return texto;
    }

    @Test
    @DisplayName("todo marcador do ajuda.yml é um que o resolver conhece")
    void marcadoresSaoConhecidos() throws Exception {
        Set<String> desconhecidos = new TreeSet<>();

        for (String linha : todasAsLinhas(carregar())) {
            if (linha == null) {
                continue;
            }
            Matcher achado = CANDIDATO.matcher(linha);
            while (achado.find()) {
                String marcador = achado.group();
                if (CONHECIDOS.contains(marcador)
                        || PRECO_POR_LADO.matcher(marcador).matches()) {
                    continue;
                }
                desconhecidos.add(marcador);
            }
        }

        assertTrue(desconhecidos.isEmpty(),
                "estes marcadores apareceriam crus na parede do spawn: " + desconhecidos);
    }

    @Test
    @DisplayName("cada painel tem título e pelo menos uma linha")
    void paineisEstaoInteiros() throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paineis =
                (List<Map<String, Object>>) carregar().get("paineis");

        assertFalse(paineis.isEmpty(), "paredão sem nenhum painel");

        for (int i = 0; i < paineis.size(); i++) {
            Map<String, Object> painel = paineis.get(i);
            Object titulo = painel.get("titulo");
            Object linhas = painel.get("linhas");

            assertTrue(titulo instanceof String texto && !texto.isBlank(),
                    "painel " + i + " sem título");
            assertTrue(linhas instanceof List<?> lista && !lista.isEmpty(),
                    "painel " + i + " sem linhas");
        }
    }

    @Test
    @DisplayName("o espaçamento continua no modo automático")
    void espacamentoContinuaAutomatico() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) carregar().get("layout");

        // Numero fixo aqui volta a sobrepor os paineis no dia em que alguem
        // escrever uma linha mais comprida. Foi o que aconteceu com
        // espacamento: 4.0 contra paineis de quase 7 blocos.
        assertTrue(numero(layout.get("espacamento")) <= 0,
                "espacamento fixo não acompanha o texto; deixe 0 para calcular");
        assertTrue(numero(layout.get("espacamento_vertical")) <= 0,
                "espacamento_vertical fixo não acompanha o texto; deixe 0");
        assertTrue(numero(layout.get("margem")) > 0,
                "sem margem os painéis se encostam");
    }

    @Test
    @DisplayName("nenhuma fileira fica com um painel sozinho")
    void fileiraNaoFicaOrfa() throws Exception {
        Map<String, Object> raiz = carregar();
        @SuppressWarnings("unchecked")
        List<Object> paineis = (List<Object>) raiz.get("paineis");
        @SuppressWarnings("unchecked")
        Map<String, Object> layout = (Map<String, Object>) raiz.get("layout");

        int porFileira = (int) numero(layout.get("por_fileira"));
        int sobra = paineis.size() % porFileira;

        // Um painel sozinho na ultima fileira nao e so feio: cada fileira a
        // mais empurra a de cima para mais alto, e acima de uns 8 blocos
        // ninguem le sem empinar o pescoço.
        assertTrue(sobra == 0 || sobra > 1 || paineis.size() <= porFileira,
                "com " + paineis.size() + " painéis e " + porFileira
                        + " por fileira sobra 1 painel sozinho na última fileira");
    }

    private static double numero(Object valor) {
        return valor instanceof Number n ? n.doubleValue() : 0.0;
    }
}
