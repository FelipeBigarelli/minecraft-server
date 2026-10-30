package codes.biga.bigacore;

import java.util.List;
import java.util.Locale;

/**
 * As quatro linhas de uma placa de Admin Shop do ChestShop.
 *
 * <h2>Por que isto é uma classe e não um método privado</h2>
 *
 * Existem dois escritores de placa: o {@link SpawnShopBuilder}, que levanta a
 * loja que o próprio BigaCore gera, e o {@link ShopSignWriter}, que preenche as
 * placas de uma construção baixada e colada com WorldEdit. Se a regra morasse
 * nos dois, uma cópia acabaria ficando para trás — e o pedaço que ficaria para
 * trás é justamente o que decide se a placa COMPRA do jogador.
 *
 * <h2>Fail closed</h2>
 *
 * A linha de preço só ganha o lado da compra quando o teto diário está
 * registrado E ativo. Placa que compra sem teto é uma impressora de dinheiro
 * ligada a qualquer farm AFK — melhor uma loja que só vende do que uma economia
 * que se esvazia sozinha durante a noite.
 */
final class AdminShopSign {

    private AdminShopSign() {
    }

    /**
     * Linha 3 da placa: {@code "B 32"} para só venda, {@code "B 32:8 S"} nos
     * dois sentidos.
     *
     * Estática e sem Bukkit no caminho para o teste conferir a invariante que
     * importa: o lado da compra nunca aparece sem {@code podeComprar}.
     */
    static String linhaDePreco(EconomyCatalog.PriceEntry item, boolean podeComprar) {
        if (podeComprar && item.serverBuy() != null) {
            return "B " + item.serverSell() + ":" + item.serverBuy() + " S";
        }
        return "B " + item.serverSell();
    }

    /** As quatro linhas, na ordem que o ChestShop espera. */
    static List<String> linhas(BigaCore plugin, EconomyCatalog.PriceEntry item, String dono) {
        EconomyCatalog catalog = plugin.economyCatalog();

        boolean podeComprar = catalog.adminShopSignBuysBack()
                && plugin.chestShopCapGuard() != null
                && plugin.chestShopCapGuard().ativo();

        if (!podeComprar && catalog.adminShopSignBuysBack() && item.serverBuy() != null) {
            plugin.getLogger().warning("[admin-shop] Teto inativo: '" + item.key()
                    + "' fica só como venda.");
        }

        String nomeItem = item.key().toUpperCase(Locale.ROOT);
        if (catalog.adminShopPortugueseNames()) {
            nomeItem = catalog.displayName(item.key());
        }

        return List.of(
                dono,
                String.valueOf(item.lot()),
                linhaDePreco(item, podeComprar),
                nomeItem);
    }
}
