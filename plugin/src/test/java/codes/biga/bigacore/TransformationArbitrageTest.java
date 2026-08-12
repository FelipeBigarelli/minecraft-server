package codes.biga.bigacore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão de arbitragem por TRANSFORMAÇÃO.
 *
 * <h2>O buraco que este teste fecha</h2>
 *
 * {@link EconomyCatalogTest} prova que comprar e revender o <b>mesmo</b> item no
 * balcão dá prejuízo. Isso não prova o ciclo mais perigoso:
 *
 * <pre>
 * comprar ingrediente do servidor
 *   -> craftar / fundir
 *   -> vender o resultado ao servidor
 * </pre>
 *
 * Um servidor pode ter spread saudável item a item e ainda assim pagar mais por
 * um produto do que cobra pelos ingredientes dele. Aí a torneira de moeda deixa
 * de ser a recompra e passa a ser uma fornalha.
 *
 * <h2>Como o teste é conservador de propósito</h2>
 *
 * <ul>
 *   <li><b>Combustível conta como zero.</b> Se o ciclo já dá prejuízo com lenha
 *       grátis, cobrar combustível só o torna mais seguro.</li>
 *   <li><b>Exige a mesma margem do spread direto</b> (resultado ≤ 60% do custo),
 *       não apenas "não dá lucro". Empatar já seria um convite.</li>
 *   <li><b>Ciclo que não fecha é ignorado.</b> Se o resultado não tem
 *       {@code server-buy}, não há como vender ao servidor — logo não há
 *       exploit, e ausência de recompra é uma proteção legítima.</li>
 * </ul>
 *
 * A tabela vive em {@code economy.yml}, ao lado dos preços que ela protege. Ao
 * incluir item novo no balcão, acrescente as transformações dele lá.
 */
class TransformationArbitrageTest {

    /** Mesma margem exigida no spread direto do balcão. */
    private static final double MARGEM = 0.60;

    private static YamlConfiguration economia;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = TransformationArbitrageTest.class.getResourceAsStream("/economy.yml")) {
            assertNotNull(in, "economy.yml não foi empacotado");
            economia = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection item(String chave) {
        return economia.getConfigurationSection("catalog." + chave);
    }

    /** Quanto custa UMA unidade comprada do servidor. Null se não vende. */
    private static Double precoUnitarioCompra(String chave) {
        ConfigurationSection secao = item(chave);
        if (secao == null || !secao.contains("server-sell")) {
            return null;
        }
        return (double) secao.getInt("server-sell") / Math.max(1, secao.getInt("lot", 1));
    }

    /** Quanto o servidor paga por UMA unidade. Null se não compra. */
    private static Double precoUnitarioVenda(String chave) {
        ConfigurationSection secao = item(chave);
        if (secao == null || !secao.contains("server-buy")) {
            return null;
        }
        return (double) secao.getInt("server-buy") / Math.max(1, secao.getInt("lot", 1));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> receitas() {
        return (List<Map<?, ?>>) (List<?>) economia.getMapList("receitas");
    }

    @Test
    void existeTabelaDeReceitas() {
        assertFalse(receitas().isEmpty(),
                "sem tabela de receitas, este teste não protege nada");
    }

    @Test
    void todaReceitaAponta1ParaItensDoCatalogo() {
        List<String> problemas = new ArrayList<>();

        for (Map<?, ?> receita : receitas()) {
            String resultado = String.valueOf(receita.get("resultado"));
            if (item(resultado) == null) {
                problemas.add("resultado '" + resultado + "' fora do catálogo");
            }
            for (Map<?, ?> ingrediente : ingredientes(receita)) {
                String chave = String.valueOf(ingrediente.get("chave"));
                if (item(chave) == null) {
                    problemas.add("ingrediente '" + chave + "' fora do catálogo");
                }
            }
        }

        assertTrue(problemas.isEmpty(), "receitas inconsistentes: " + problemas);
    }

    @Test
    void nenhumCicloDeTransformacaoDaLucro() {
        List<String> lucrativos = new ArrayList<>();
        int avaliados = 0;

        for (Map<?, ?> receita : receitas()) {
            String resultado = String.valueOf(receita.get("resultado"));
            int qtdResultado = inteiro(receita.get("quantidade"), 1);

            Double vendaUnit = precoUnitarioVenda(resultado);
            if (vendaUnit == null) {
                // O servidor não recompra este item: o ciclo não fecha.
                continue;
            }

            double custo = 0.0;
            boolean fechavel = true;
            StringBuilder detalhe = new StringBuilder();

            for (Map<?, ?> ingrediente : ingredientes(receita)) {
                String chave = String.valueOf(ingrediente.get("chave"));
                int qtd = inteiro(ingrediente.get("quantidade"), 1);

                Double compraUnit = precoUnitarioCompra(chave);
                if (compraUnit == null) {
                    // Não dá para comprar este ingrediente do servidor.
                    fechavel = false;
                    break;
                }
                custo += compraUnit * qtd;
                detalhe.append(qtd).append("x ").append(chave).append(' ');
            }

            if (!fechavel) {
                continue;
            }

            avaliados++;
            double receitaBruta = vendaUnit * qtdResultado;

            if (receitaBruta > custo * MARGEM) {
                lucrativos.add(String.format(
                        "%s-> %dx %s | custo %.2f, recebe %.2f (limite %.2f)",
                        detalhe, qtdResultado, resultado, custo, receitaBruta, custo * MARGEM));
            }
        }

        assertTrue(avaliados > 0,
                "nenhum ciclo fechável foi avaliado — a tabela virou decorativa");
        assertTrue(lucrativos.isEmpty(),
                "arbitragem por transformação encontrada:\n  " + String.join("\n  ", lucrativos));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> ingredientes(Map<?, ?> receita) {
        Object bruto = receita.get("ingredientes");
        if (bruto instanceof List<?> lista) {
            return (List<Map<?, ?>>) lista;
        }
        return List.of();
    }

    private static int inteiro(Object valor, int padrao) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return padrao;
    }
}
