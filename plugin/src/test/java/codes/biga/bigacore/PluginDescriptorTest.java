package codes.biga.bigacore;

import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão: o plugin.yml precisa ser YAML válido para o Paper.
 *
 * <h2>Por que este teste existe</h2>
 *
 * Uma linha de {@code usage} ganhou a palavra "loja:" no meio da frase, sem
 * aspas. O snakeyaml leu os dois-pontos como início de um mapa aninhado e o
 * Paper recusou o arquivo inteiro:
 *
 * <pre>
 * InvalidDescriptionException: Invalid plugin.yml
 * Caused by: mapping values are not allowed here
 * </pre>
 *
 * O BigaCore simplesmente não carregou. E o {@code mvn package} passou verde,
 * porque nada no build lia o plugin.yml — ele é apenas um resource copiado.
 *
 * O teste usa o {@link PluginDescriptionFile} de verdade, o mesmo caminho de
 * código do boot do servidor, sobre o plugin.yml já filtrado em target/classes.
 * Assim qualquer erro de sintaxe quebra o build em vez de quebrar o servidor.
 */
class PluginDescriptorTest {

    private PluginDescriptionFile carregar() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml não foi empacotado em target/classes");
            return new PluginDescriptionFile(in);
        }
    }

    @Test
    void pluginYmlEValidoParaOPaper() throws Exception {
        PluginDescriptionFile descricao = carregar();

        assertEquals("BigaCore", descricao.getName());
        assertEquals("codes.biga.bigacore.BigaCore", descricao.getMain());
        assertEquals("26.2", descricao.getAPIVersion());
    }

    @Test
    void versaoFoiSubstituidaPeloMaven() throws Exception {
        String versao = carregar().getVersion();

        assertTrue(versao.matches("\\d+\\.\\d+\\.\\d+"),
                "version deveria ter virado número no filtering, veio: " + versao);
    }

    @Test
    void classePrincipalExisteDeVerdade() throws Exception {
        String main = carregar().getMain();

        assertEquals(BigaCore.class.getName(), main,
                "o main do plugin.yml não aponta para a classe real");
    }

    @Test
    void comandoBigaEstaDeclarado() throws Exception {
        Map<String, Map<String, Object>> comandos = carregar().getCommands();

        assertTrue(comandos.containsKey("biga"), "comando 'biga' sumiu do plugin.yml");
        assertNotNull(comandos.get("biga").get("usage"), "comando 'biga' sem usage");
    }

    @Test
    void permissaoAdminContinuaSendoOp() throws Exception {
        boolean achou = carregar().getPermissions().stream()
                .anyMatch(p -> p.getName().equals("bigacore.admin"));

        assertTrue(achou, "bigacore.admin protege /biga loja e não pode sumir");
    }
}
