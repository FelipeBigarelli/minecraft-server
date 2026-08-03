package codes.biga.bigacore;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementa /biga com subcomandos.
 *
 * Implementar CommandExecutor e TabCompleter na mesma classe e
 * conveniente: o autocomplete fica ao lado da logica que ele
 * completa, entao os dois nao saem de sincronia.
 */
public final class BigaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMANDOS = List.of("info", "reload", "voar");

    private final BigaCore plugin;

    public BigaCommand(BigaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (args.length == 0) {
            enviarAjuda(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info"   -> mostrarInfo(sender);
            case "reload" -> recarregar(sender);
            case "voar"   -> alternarVoo(sender);
            default       -> enviarAjuda(sender, label);
        }

        // Retornar false faz o servidor imprimir a 'usage' do
        // plugin.yml. Como ja tratamos tudo acima, sempre true.
        return true;
    }

    private void mostrarInfo(CommandSender sender) {
        // getDescription() e a via do Spigot-API para ler o plugin.yml.
        // (getPluginMeta() existe, mas so no Paper.)
        sender.sendMessage(ChatColor.AQUA + "BigaCore v"
                + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Servidor: " + plugin.getServer().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Online: "
                + plugin.getServer().getOnlinePlayers().size() + "/"
                + plugin.getServer().getMaxPlayers());
    }

    private void recarregar(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Configuracao recarregada.");
    }

    private void alternarVoo(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        // O console pode executar comandos, entao nunca assuma que
        // o sender e um Player sem checar.
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(ChatColor.RED + "Esse comando so funciona em jogo.");
            return;
        }
        boolean novoEstado = !jogador.getAllowFlight();
        jogador.setAllowFlight(novoEstado);
        jogador.sendMessage(ChatColor.GREEN + "Voo "
                + (novoEstado ? "ativado" : "desativado") + ".");
    }

    private void semPermissao(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Voce nao tem permissao para isso.");
    }

    private void enviarAjuda(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " <info|reload|voar>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        // Filtra pelo que o jogador ja digitou, senao o cliente
        // mostra todas as opcoes mesmo com prefixo escrito.
        String prefixo = args[0].toLowerCase(Locale.ROOT);
        List<String> resultado = new ArrayList<>();
        for (String sub : SUBCOMANDOS) {
            if (sub.startsWith(prefixo)) {
                resultado.add(sub);
            }
        }
        return resultado;
    }
}
