package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        // getPluginMeta() e a via do Paper para ler o plugin.yml.
        // No Spigot so existia getDescription(), que no Paper esta
        // deprecado. Como agora compilamos contra paper-api, usamos
        // a versao atual.
        var meta = plugin.getPluginMeta();

        String site = meta.getWebsite();
        String autores = String.join(", ", meta.getAuthors());

        // ---------------------------------------------------------
        // Um Component nao e so texto com cor: e um objeto que
        // carrega comportamento junto. Aqui a mesma linha tem
        // cor, negrito, um balao ao passar o mouse e uma acao ao
        // clicar. Com ChatColor + String isso era impossivel.
        // ---------------------------------------------------------
        Component titulo = Component.text("BigaCore v" + meta.getVersion())
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(
                        Component.text("Autor: " + autores, NamedTextColor.WHITE)
                                .appendNewline()
                                .append(Component.text(
                                        site == null ? "sem site declarado" : "Clique para abrir " + site,
                                        NamedTextColor.GRAY))
                ));

        // clickEvent so faz sentido se houver um site declarado no
        // plugin.yml. Anexar um evento com URL nula lanca excecao.
        if (site != null && !site.isBlank()) {
            titulo = titulo.clickEvent(ClickEvent.openUrl(site));
        }

        sender.sendMessage(titulo);

        // Component.text(...) sem cor herda a cor do contexto; por
        // isso a cor vai explicita em cada parte.
        sender.sendMessage(
                Component.text("Servidor: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.getServer().getVersion(), NamedTextColor.WHITE))
        );

        sender.sendMessage(
                Component.text("Online: ", NamedTextColor.GRAY)
                        .append(Component.text(
                                plugin.getServer().getOnlinePlayers().size()
                                        + "/" + plugin.getServer().getMaxPlayers(),
                                NamedTextColor.WHITE))
        );
    }

    private void recarregar(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(Component.text("Configuracao recarregada.", NamedTextColor.GREEN));
    }

    private void alternarVoo(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        // O console pode executar comandos, entao nunca assuma que
        // o sender e um Player sem checar.
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando so funciona em jogo.", NamedTextColor.RED));
            return;
        }
        boolean novoEstado = !jogador.getAllowFlight();
        jogador.setAllowFlight(novoEstado);
        jogador.sendMessage(Component.text(
                "Voo " + (novoEstado ? "ativado" : "desativado") + ".",
                NamedTextColor.GREEN));
    }

    private void semPermissao(CommandSender sender) {
        sender.sendMessage(Component.text("Voce nao tem permissao para isso.", NamedTextColor.RED));
    }

    private void enviarAjuda(CommandSender sender, String label) {
        Component ajuda = Component.text("Uso: ", NamedTextColor.YELLOW)
                .append(Component.text("/" + label + " ", NamedTextColor.WHITE));

        // Cada subcomando vira um botao: clicar preenche o chat com o
        // comando ja digitado (suggestCommand nao executa, so sugere -
        // e o certo para algo que pode ter efeito, como /biga voar).
        for (int i = 0; i < SUBCOMANDOS.size(); i++) {
            String sub = SUBCOMANDOS.get(i);
            if (i > 0) {
                ajuda = ajuda.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            ajuda = ajuda.append(
                    Component.text(sub, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand("/" + label + " " + sub))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Clique para preencher no chat", NamedTextColor.GRAY)))
            );
        }

        sender.sendMessage(ajuda);
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
