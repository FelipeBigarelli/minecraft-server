package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listeners de eventos de jogador.
 *
 * Cada metodo anotado com @EventHandler e chamado pelo servidor
 * quando o evento correspondente ocorre. O parametro do metodo e o
 * que define qual evento e escutado - nao o nome do metodo.
 */
public final class JogadorListener implements Listener {

    private final BigaCore plugin;

    public JogadorListener(BigaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR e a prioridade correta para apenas observar o evento
     * sem altera-lo - roda por ultimo, depois de todos os outros
     * plugins ja terem decidido o resultado.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isBoasVindasAtivo()) {
            return;
        }

        Player jogador = event.getPlayer();

        // Placeholder.unparsed() insere o valor como texto LITERAL: o
        // que vier dentro dele nunca e interpretado como marcacao.
        //
        // Isso importa por seguranca. Se usassemos String.replace no
        // template antes de passar pelo parser, um valor contendo
        // "<red>" ou "<click:run_command:/op alguem>" viraria marcacao
        // de verdade. Nick de Minecraft so aceita [a-zA-Z0-9_], entao
        // hoje o risco e teorico - mas o dia em que um placeholder vier
        // de texto gerado pela API do Claude, deixa de ser.
        //
        // Regra: dado que nao e seu, entra por unparsed().
        Component mensagem = plugin.mini().deserialize(
                plugin.getMensagemBoasVindas(),
                Placeholder.unparsed("jogador", jogador.getName()),
                Placeholder.unparsed("online",
                        String.valueOf(plugin.getServer().getOnlinePlayers().size()))
        );

        jogador.sendMessage(mensagem);

        // Primeira vez que este jogador entra no servidor.
        if (!jogador.hasPlayedBefore()) {
            // Component.text(texto, cor) monta o objeto direto, sem
            // passar por parser - e o caminho para texto fixo, escrito
            // por voce. MiniMessage e para texto que veio de fora
            // (config, banco, resposta de API).
            plugin.getServer().broadcast(
                    Component.text(jogador.getName() + " entrou no servidor pela primeira vez!",
                            NamedTextColor.GOLD)
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Bom lugar para persistir dados do jogador antes de ele sair.
        plugin.getLogger().info(event.getPlayer().getName() + " saiu.");
    }
}
