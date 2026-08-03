package codes.biga.bigacore;

import org.bukkit.ChatColor;
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
 *
 * Nota: usamos String + ChatColor porque o Spigot-API nao embute a
 * biblioteca Adventure (Component). Isso e exclusividade do Paper.
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

        String texto = plugin.getMensagemBoasVindas()
                .replace("{jogador}", jogador.getName())
                .replace("{online}", String.valueOf(plugin.getServer().getOnlinePlayers().size()));

        // translateAlternateColorCodes deixa o admin escrever "&b" no
        // config.yml em vez do caractere secao, que e chato de digitar.
        jogador.sendMessage(ChatColor.translateAlternateColorCodes('&', texto));

        // Primeira vez que este jogador entra no servidor.
        if (!jogador.hasPlayedBefore()) {
            plugin.getServer().broadcastMessage(
                ChatColor.GOLD + jogador.getName() + " entrou no servidor pela primeira vez!"
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Bom lugar para persistir dados do jogador antes de ele sair.
        plugin.getLogger().info(event.getPlayer().getName() + " saiu.");
    }
}
