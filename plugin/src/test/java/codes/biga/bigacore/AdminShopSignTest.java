package codes.biga.bigacore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regressão da linha de preço da placa de Admin Shop.
 *
 * <h2>Por que este teste existe</h2>
 *
 * A linha 3 da placa é o que separa uma loja que só vende de uma loja que
 * também COMPRA do jogador. O lado da compra só pode aparecer quando o teto
 * diário do {@link ChestShopCapGuard} está ativo — sem ele, qualquer farm AFK
 * vende sem limite e a economia vira impressora de dinheiro durante a noite.
 *
 * Esse "fail closed" não aparece em lugar nenhum do jogo até o estrago estar
 * feito: a placa parece normal, e só o `/baltop` crescendo sozinho denuncia.
 * Agora existem DOIS escritores de placa ({@link SpawnShopBuilder} e
 * {@link ShopSignWriter}), então a regra mora num lugar só e é aqui que ela
 * fica amarrada.
 */
class AdminShopSignTest {

    private static EconomyCatalog.PriceEntry item(Integer venda, Integer compra) {
        return new EconomyCatalog.PriceEntry("cobblestone", "COMMON", 64, 10, compra, venda);
    }

    @Test
    void semTetoAtivoAPlacaSoVende() {
        // O caso que importa: o item TEM preço de compra, mas o teto não está
        // ativo. A compra não pode vazar para a placa.
        String linha = AdminShopSign.linhaDePreco(item(32, 8), false);
        assertEquals("B 32", linha);
        assertFalse(linha.contains("S"), "lado da compra vazou sem teto ativo");
    }

    @Test
    void comTetoAtivoAPlacaCompraEVende() {
        assertEquals("B 32:8 S", AdminShopSign.linhaDePreco(item(32, 8), true));
    }

    @Test
    void itemSemPrecoDeCompraNuncaGanhaOLadoDaCompra() {
        // Mesmo com o teto ativo: sem server-buy não há o que comprar.
        assertEquals("B 32", AdminShopSign.linhaDePreco(item(32, null), true));
    }

    @Test
    void oFormatoEExatamenteOQueOChestShopEspera() {
        // "B <venda>" e "B <venda>:<compra> S" — qualquer espaço ou dois-pontos
        // fora do lugar faz o ChestShop ignorar a placa em silêncio.
        assertEquals("B 500", AdminShopSign.linhaDePreco(item(500, null), true));
        assertEquals("B 500:120 S", AdminShopSign.linhaDePreco(item(500, 120), true));
    }
}
