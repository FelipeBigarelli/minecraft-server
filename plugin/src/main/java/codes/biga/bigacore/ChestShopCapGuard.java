package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

/**
 * Aplica o teto diário de recompra nas placas do Admin Shop.
 *
 * <h2>O problema</h2>
 *
 * O balcão passou a comprar do jogador na própria placa, e não só a vender.
 * Isso é o que o dono do servidor pediu — e é também o lado perigoso do
 * balcão: moeda ENTRA em circulação. Sem limite, uma farm de cobblestone AFK
 * vira impressora de dinheiro e o mercado entre jogadores morre.
 *
 * O ChestShop não sabe aplicar teto por jogador. Mas ele dispara
 * {@code PreTransactionEvent}, que é cancelável — dá para deixar a venda
 * acontecer até o teto e recusar o resto.
 *
 * <h2>Por que reflexão em vez de dependência</h2>
 *
 * Compilar contra a API do ChestShop exigiria adicionar um repositório Maven
 * novo ao build e ao CI, e amarraria o BigaCore a uma versão específica de um
 * plugin de terceiro. O projeto já evita isso no {@link VaultEconomyBridge}
 * pelo mesmo motivo.
 *
 * Aqui a classe do evento é resolvida por nome em runtime e registrada com
 * {@code registerEvent(Class, ...)}, que aceita a classe como parâmetro. Se o
 * ChestShop não estiver instalado, ou se ele mudar o nome do evento numa versão
 * futura, o registro simplesmente não acontece e o servidor sobe igual — com um
 * aviso alto no console, porque nesse caso o teto deixaria de valer.
 */
public final class ChestShopCapGuard implements Listener {

    private static final String EVENTO = "com.Acrobot.ChestShop.Events.PreTransactionEvent";
    private static final String TIPO = "com.Acrobot.ChestShop.Events.TransactionEvent$TransactionType";

    private final BigaCore plugin;

    private Method getClient;
    private Method getTransactionType;
    private Method getPrice;
    private Method setCancelled;
    private Object tipoVenda;
    private boolean ativo;

    public ChestShopCapGuard(BigaCore plugin) {
        this.plugin = plugin;
    }

    public boolean ativo() {
        return ativo;
    }

    /** Registra o hook. Silencioso quando não há ChestShop; ruidoso quando falha. */
    @SuppressWarnings("unchecked")
    public void register() {
        if (plugin.getServer().getPluginManager().getPlugin("ChestShop") == null) {
            plugin.getLogger().info("[teto] ChestShop ausente; nada a proteger.");
            return;
        }

        try {
            Class<?> eventoClass = Class.forName(EVENTO);
            Class<?> tipoClass = Class.forName(TIPO);

            this.getClient = eventoClass.getMethod("getClient");
            this.getTransactionType = eventoClass.getMethod("getTransactionType");
            this.getPrice = eventoClass.getMethod("getPrice");
            this.setCancelled = eventoClass.getMethod("setCancelled", boolean.class);
            this.tipoVenda = Enum.valueOf((Class<Enum>) tipoClass.asSubclass(Enum.class), "SELL");

            EventExecutor executor = (listener, evento) -> avaliar(evento);
            plugin.getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) eventoClass.asSubclass(Event.class),
                    this, EventPriority.HIGH, executor, plugin, true);

            this.ativo = true;
            plugin.getLogger().info("[teto] Teto de recompra ligado nas placas do Admin Shop.");
        } catch (ReflectiveOperationException | IllegalArgumentException erro) {
            // Isto é grave: sem o hook, a placa compra sem limite nenhum.
            plugin.getLogger().severe("[teto] NÃO foi possível proteger as placas ("
                    + erro.getClass().getSimpleName() + ": " + erro.getMessage()
                    + "). Desligue admin-shop.placa-compra até resolver, ou a"
                    + " recompra fica SEM TETO.");
        }
    }

    /**
     * Deixa passar o que couber no teto e recusa o resto.
     *
     * Só olha venda (jogador → servidor). Compra é sink, nunca precisa de
     * limite. E só conta transação em loja do próprio servidor: venda entre
     * jogadores não cria moeda, apenas move.
     */
    private void avaliar(Object evento) {
        if (!ativo) {
            return;
        }

        try {
            Object tipo = getTransactionType.invoke(evento);
            if (tipo != tipoVenda) {
                return;
            }

            Object dono = evento.getClass().getMethod("getOwnerAccount").invoke(evento);
            if (!ehLojaDoServidor(dono)) {
                return;
            }

            Object cliente = getClient.invoke(evento);
            if (!(cliente instanceof Player jogador)) {
                return;
            }

            double preco = ((Number) getPrice.invoke(evento)).doubleValue();
            int restante = plugin.serverBuyback().restanteHoje(jogador);

            if (preco <= restante) {
                plugin.serverBuyback().registrarVendaExterna(jogador, (int) Math.ceil(preco));
                return;
            }

            setCancelled.invoke(evento, true);
            jogador.sendMessage(Component.text(
                    "Teto de recompra atingido. Restam "
                            + plugin.economyCatalog().money(restante)
                            + " hoje — venda o excedente a outros jogadores.",
                    NamedTextColor.YELLOW));
        } catch (ReflectiveOperationException erro) {
            // Falhar aqui em silêncio deixaria a venda passar sem contar no teto.
            plugin.getLogger().severe("[teto] Falha ao avaliar transação: " + erro.getMessage());
        }
    }

    /**
     * Loja do servidor ou de jogador?
     *
     * O ChestShop entrega a conta do dono; numa Admin Shop ela é nula ou tem o
     * nome configurado como dono administrativo.
     */
    private boolean ehLojaDoServidor(Object contaDono) {
        if (contaDono == null) {
            return true;
        }
        try {
            Object nome = contaDono.getClass().getMethod("getName").invoke(contaDono);
            return nome == null
                    || nome.toString().equalsIgnoreCase(plugin.economyCatalog().adminShopSignOwner());
        } catch (ReflectiveOperationException erro) {
            // Na dúvida, trata como loja do servidor: aplicar o teto a mais é
            // recuperável; deixar a moeda vazar, não.
            return true;
        }
    }
}
