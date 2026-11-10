package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As regras do quadro de encomendas.
 *
 * A validacao e static e recebe os limites por parametro justamente para ser
 * testavel sem subir um servidor. O que esta aqui e a tabela de casos que o
 * jogador encosta na pratica — principalmente as FRONTEIRAS, que sao onde um
 * {@code >} trocado por {@code >=} passa despercebido e recusa um pedido
 * perfeitamente valido.
 */
class EncomendaTest {

    // Limites usados nos casos, iguais aos que vao no encomendas.yml.
    private static final long MIN = 10;
    private static final long MAX = 100000;
    private static final int QTD_MAX = 1728;
    private static final int LIMITE = 5;

    private EncomendaCatalog.Recusa validar(long preco, int quantidade, int abertas) {
        return EncomendaCatalog.validar(preco, quantidade, abertas,
                MIN, MAX, QTD_MAX, LIMITE);
    }

    @Test
    void pedidoNormalPassa() {
        assertEquals(EncomendaCatalog.Recusa.OK, validar(500, 64, 0));
    }

    @Test
    void precoAbaixoDoMinimoERecusado() {
        assertEquals(EncomendaCatalog.Recusa.PRECO_BAIXO, validar(MIN - 1, 64, 0));
    }

    @Test
    void precoAcimaDoMaximoERecusado() {
        assertEquals(EncomendaCatalog.Recusa.PRECO_ALTO, validar(MAX + 1, 64, 0));
    }

    /**
     * O teto de preco e uma rede contra erro de digitacao, nao contra o
     * jogador rico. Recusar exatamente no teto tornaria o numero documentado
     * no encomendas.yml uma mentira.
     */
    @Test
    void asFronteirasDePrecoSaoAceitas() {
        assertEquals(EncomendaCatalog.Recusa.OK, validar(MIN, 64, 0));
        assertEquals(EncomendaCatalog.Recusa.OK, validar(MAX, 64, 0));
    }

    @Test
    void quantidadeZeroOuNegativaERecusada() {
        assertEquals(EncomendaCatalog.Recusa.QUANTIDADE_BAIXA, validar(500, 0, 0));
        assertEquals(EncomendaCatalog.Recusa.QUANTIDADE_BAIXA, validar(500, -1, 0));
    }

    @Test
    void quantidadeAcimaDoTetoERecusada() {
        assertEquals(EncomendaCatalog.Recusa.QUANTIDADE_ALTA, validar(500, QTD_MAX + 1, 0));
        assertEquals(EncomendaCatalog.Recusa.OK, validar(500, QTD_MAX, 0));
    }

    /**
     * O limite conta pedidos ABERTOS, e quem ja esta no teto nao abre mais um.
     * O caso de borda e o jogador com exatamente o limite: ele tem de ser
     * barrado, senao o teto vale n+1.
     */
    @Test
    void limiteDePedidosEPorJogador() {
        assertEquals(EncomendaCatalog.Recusa.OK, validar(500, 64, LIMITE - 1));
        assertEquals(EncomendaCatalog.Recusa.LIMITE_DE_PEDIDOS, validar(500, 64, LIMITE));
        assertEquals(EncomendaCatalog.Recusa.LIMITE_DE_PEDIDOS, validar(500, 64, LIMITE + 1));
    }

    /**
     * Quantidade e checada antes de preco.
     *
     * Nao e capricho: com os dois errados, a mensagem precisa ser estavel, ou
     * o jogador corrige o preco e leva outra recusa que nao esperava. Este
     * teste trava a ordem escolhida.
     */
    @Test
    void quantidadeEAvaliadaAntesDoPreco() {
        assertEquals(EncomendaCatalog.Recusa.QUANTIDADE_BAIXA, validar(MIN - 1, 0, 0));
    }

    /**
     * O limite do jogador e a ULTIMA checagem.
     *
     * Um pedido invalido nao deve ser recusado por "voce ja tem 5 abertos":
     * a pessoa cancelaria um pedido bom a toa para descobrir que o problema
     * era o preco.
     */
    @Test
    void limiteSoApareceQuandoOPedidoEValido() {
        assertEquals(EncomendaCatalog.Recusa.PRECO_ALTO, validar(MAX + 1, 64, LIMITE));
    }

    @Test
    void precoUnitarioEhSoParaExibir() {
        Encomenda pedido = new Encomenda(1, UUID.randomUUID(), "zBigaBiga",
                org.bukkit.Material.IRON_INGOT, 64, 640, 0L);

        assertEquals(10.0, pedido.precoUnitario(), 0.0001);
        assertTrue(pedido.ehDono(pedido.dono()));
        assertFalse(pedido.ehDono(UUID.randomUUID()));
    }

    /**
     * O encomendas.yml empacotado tem de bater com os defaults do codigo.
     *
     * Os dois existem: o YAML e o que o dono edita, e o default no
     * {@link EncomendaCatalog} e o que vale se a chave sumir. Quando os dois
     * discordam, o servidor se comporta de um jeito e o arquivo documenta
     * outro — e a descoberta vem por reclamacao, nao por erro.
     */
    @Test
    void oArquivoEmpacotadoConcordaComOsDefaults() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/encomendas.yml")) {
            assertNotNull(in, "encomendas.yml nao foi empacotado em target/classes");
            try (Reader leitor = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(leitor);

                assertTrue(yaml.getBoolean("ativo"), "o quadro deveria vir ligado");
                assertEquals(LIMITE, yaml.getInt("limite-por-jogador"));
                assertEquals(MIN, yaml.getLong("preco-minimo"));
                assertEquals(MAX, yaml.getLong("preco-maximo"));
                assertEquals(QTD_MAX, yaml.getInt("quantidade-maxima"));
            }
        }
    }
}
