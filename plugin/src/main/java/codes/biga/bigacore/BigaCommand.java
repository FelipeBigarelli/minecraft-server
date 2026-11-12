package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Implementa /biga com subcomandos.
 *
 * Implementar CommandExecutor e TabCompleter na mesma classe e conveniente:
 * o autocomplete fica ao lado da logica que ele completa, entao os dois nao
 * saem de sincronia.
 */
public final class BigaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMANDOS =
            List.of("info", "eco", "loja", "hub", "lobby", "tntrun", "painel",
                    "reload", "voar");
    private static final List<String> PAINEL_SUBCOMANDOS = List.of("construir", "limpar");
    private static final List<String> ECO_SUBCOMANDOS =
            List.of("status", "saldo", "preco", "vender", "regras");
    private static final List<String> HUB_SUBCOMANDOS =
            List.of("info", "construir", "desfazer", "textos", "ir");
    private static final List<String> LOBBY_SUBCOMANDOS =
            List.of("info", "construir", "desfazer", "textos", "ir");
    private static final List<String> TNTRUN_SUBCOMANDOS =
            List.of("info", "construir", "desfazer", "textos", "ir", "comecar", "parar");
    private static final List<String> LOJA_SUBCOMANDOS =
            List.of("local", "validar", "preview", "criar", "placas", "vitrine",
                    "proteger", "desproteger", "desfazer");

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
            case "info" -> mostrarInfo(sender);
            case "eco" -> economia(sender, label, args);
            case "loja" -> lojaSpawn(sender, label, args);
            case "hub" -> hub(sender, label, args);
            case "lobby" -> lobby(sender, label, args);
            case "tntrun" -> tntRun(sender, label, args);
            case "painel" -> paredaoAjuda(sender, label, args);
            case "reload" -> recarregar(sender);
            case "voar" -> alternarVoo(sender);
            default -> enviarAjuda(sender, label);
        }

        return true;
    }

    private void mostrarInfo(CommandSender sender) {
        var meta = plugin.getPluginMeta();

        String site = meta.getWebsite();
        String autores = String.join(", ", meta.getAuthors());
        String hoverSite = "sem site declarado";
        if (site != null && !site.isBlank()) {
            hoverSite = "Clique para abrir " + site;
        }

        Component titulo = Component.text("BigaCore v" + meta.getVersion())
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(
                        Component.text("Autor: " + autores, NamedTextColor.WHITE)
                                .appendNewline()
                                .append(Component.text(hoverSite, NamedTextColor.GRAY))
                ));

        if (site != null && !site.isBlank()) {
            titulo = titulo.clickEvent(ClickEvent.openUrl(site));
        }

        sender.sendMessage(titulo);
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

    private void economia(CommandSender sender, String label, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            mostrarEconomiaStatus(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "saldo" -> mostrarSaldo(sender);
            case "preco" -> mostrarPreco(sender, label, args);
            case "vender" -> venderParaServidor(sender, label, args);
            case "regras" -> mostrarRegras(sender);
            default -> enviarAjudaEconomia(sender, label);
        }
    }

    private void mostrarEconomiaStatus(CommandSender sender) {
        EconomyCatalog catalog = plugin.economyCatalog();

        sender.sendMessage(Component.text("Economia BigaCore", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(linha("Moeda", catalog.currencyName() + " (" + catalog.currencySymbol() + ")"));
        sender.sendMessage(linha("Saldo inicial", catalog.money(catalog.startingBalance())));
        sender.sendMessage(linha("Taxa P2P ChestShop", catalog.chestShopTaxPercent() + "%"));
        sender.sendMessage(linha("Criar loja", catalog.money(catalog.shopCreationPrice())));
        sender.sendMessage(linha("Reembolso ao remover", catalog.money(catalog.shopRefundPrice())));
        sender.sendMessage(linha("Filosofia", "mercado entre jogadores primeiro"));

        String estadoBuyback = "desativado no lancamento";
        if (catalog.adminBuybackEnabledAtLaunch()) {
            estadoBuyback = "ativo";
        }
        sender.sendMessage(linha("Admin buyback", estadoBuyback));
    }

    private void mostrarSaldo(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Use /biga eco saldo dentro do jogo.", NamedTextColor.RED));
            return;
        }

        OptionalDouble saldo = plugin.economyBridge().balance(jogador);
        if (saldo.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Economia indisponivel. Confirme VaultUnlocked + EternalEconomy.",
                    NamedTextColor.RED));
            return;
        }

        String valor = String.format(Locale.ROOT, "%.0f", saldo.getAsDouble());
        jogador.sendMessage(Component.text("Seu saldo: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.economyCatalog().currencySymbol() + " " + valor,
                        NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)));
    }

    private void mostrarPreco(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Uso: /" + label + " eco preco <item>",
                    NamedTextColor.YELLOW));
            return;
        }

        String item = args[2];
        plugin.economyCatalog().find(item).ifPresentOrElse(
                entry -> enviarPreco(sender, entry),
                () -> sender.sendMessage(Component.text(
                        "Item nao encontrado no catalogo: " + item,
                        NamedTextColor.RED))
        );
    }

    private void enviarPreco(CommandSender sender, EconomyCatalog.PriceEntry entry) {
        EconomyCatalog catalog = plugin.economyCatalog();

        sender.sendMessage(Component.text(entry.key().toUpperCase(Locale.ROOT), NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("  [" + entry.tier() + "]", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(linha("Lote", String.valueOf(entry.lot()) + " unidades"));
        sender.sendMessage(linha("Referencia P2P", catalog.money(entry.p2p()) + " / lote"));

        String serverBuyText = "P2P somente";
        if (entry.serverBuy() != null) {
            serverBuyText = catalog.money(entry.serverBuy()) + " a cada " + entry.lot();
        }
        sender.sendMessage(linha("Servidor compra", serverBuyText));
        // "B$ 13" sozinho lê como preço de 1 iron. É de 16 (o lote). Sem esta
        // linha o jogador vende achando que recebe 16x o que recebe de fato.

        String serverSellText = "P2P somente";
        if (entry.serverSell() != null) {
            serverSellText = catalog.money(entry.serverSell()) + " a cada " + entry.lot();
        }
        sender.sendMessage(linha("Servidor vende", serverSellText));

        sender.sendMessage(Component.text(
                "P2P e referencia, nao preco obrigatorio.",
                NamedTextColor.DARK_GRAY));
    }

    /**
     * Vende ao servidor o item da mão.
     *
     * Não é /sellall: o comando nunca toca no resto do inventário. Quem decide
     * o que sai é a mão do jogador, e quanto entra de moeda nova é o teto
     * diário do ServerBuyback.
     */
    private void venderParaServidor(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Use /biga eco vender dentro do jogo.", NamedTextColor.RED));
            return;
        }

        int pedido = Integer.MAX_VALUE;
        if (args.length >= 3) {
            try {
                pedido = Integer.parseInt(args[2]);
            } catch (NumberFormatException erro) {
                jogador.sendMessage(Component.text(
                        "Uso: /" + label + " eco vender [quantidade]", NamedTextColor.YELLOW));
                return;
            }
            if (pedido <= 0) {
                jogador.sendMessage(Component.text(
                        "A quantidade precisa ser maior que zero.", NamedTextColor.YELLOW));
                return;
            }
        }

        ServerBuyback buyback = plugin.serverBuyback();
        ServerBuyback.Resultado resultado = buyback.comprar(jogador, pedido);

        if (!resultado.sucesso()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), NamedTextColor.RED));
            return;
        }

        EconomyCatalog catalog = plugin.economyCatalog();
        jogador.sendMessage(Component.text("Venda concluída", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Item", resultado.quantidade() + "x " + resultado.item()));
        jogador.sendMessage(linha("Recebido", catalog.money(resultado.pagamento())));
        jogador.sendMessage(linha("Teto restante hoje", catalog.money(resultado.restanteHoje())));
        jogador.sendMessage(Component.text(
                "O teto existe para farm não virar impressora de dinheiro. Acima dele, venda a outros jogadores.",
                NamedTextColor.DARK_GRAY));
    }

    private void mostrarRegras(CommandSender sender) {
        EconomyCatalog catalog = plugin.economyCatalog();

        sender.sendMessage(Component.text("Regras economicas", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("• Sem dinheiro por login, AFK, mob morto ou bloco quebrado.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("• Farms geram itens; nao imprimem B$ infinitamente.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("• Itens raros/progressao ficam principalmente no mercado P2P.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("• Auto-sell/sell-all fica desligado no lancamento.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "• Buyback futuro: max " + catalog.money(catalog.adminBuybackDailyCap())
                        + "/dia e " + catalog.money(catalog.adminBuybackWeeklyCap()) + "/semana por jogador.",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "• Limite inicial planejado: " + catalog.initialShopLimit() + " lojas por jogador.",
                NamedTextColor.GRAY));
    }

    private void lojaSpawn(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Use os comandos da loja dentro do jogo.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            enviarAjudaLoja(sender, label);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "local" -> mostrarLocalLoja(jogador);
            case "validar" -> validarLoja(jogador);
            case "preview" -> previewLoja(jogador);
            case "criar" -> criarLoja(jogador, label, args);
            case "placas" -> placasLoja(jogador, label, args);
            case "vitrine" -> vitrineLoja(jogador, label, args);
            case "proteger" -> protegerTerreno(jogador, true);
            case "desproteger" -> protegerTerreno(jogador, false);
            case "desfazer" -> desfazerLoja(jogador, label, args);
            default -> enviarAjudaLoja(sender, label);
        }
    }

    // ------------------------------------------------------------------
    //  /biga hub
    // ------------------------------------------------------------------

    /**
     * A praca de portais.
     *
     * Construir e desfazer pedem CONFIRMAR pelo mesmo motivo da loja: sao
     * comandos de uma palavra que mexem em dezenas de milhares de blocos.
     */
    private void hub(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        if (args.length < 2) {
            enviarAjudaHub(sender, label);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> hubInfo(sender);
            case "construir" -> hubConstruir(sender, label, args);
            case "desfazer" -> hubDesfazer(sender, label, args);
            case "ir" -> hubIr(sender);
            case "textos" -> hubTextos(sender);
            default -> enviarAjudaHub(sender, label);
        }
    }

    private void enviarAjudaHub(CommandSender sender, String label) {
        sender.sendMessage(Component.text("A praça de portais", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Primeiro crie o mundo vazio, uma vez só:",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mv create hub normal -g BigaCore",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " hub info", NamedTextColor.YELLOW)
                .append(Component.text(" — confere o terreno e o tamanho da obra",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " hub construir CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " hub desfazer CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " hub textos", NamedTextColor.YELLOW)
                .append(Component.text(" — refaz só os letreiros flutuantes, sem tocar em bloco",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " hub ir", NamedTextColor.YELLOW)
                .append(Component.text(" — leva você ao centro da praça", NamedTextColor.GRAY)));
    }

    /** O mundo em que o hub mora, pelo config.yml. */
    private org.bukkit.World mundoDoHub(CommandSender sender) {
        org.bukkit.World mundo = plugin.hubPortais().mundoDoHub();
        if (mundo == null) {
            String nome = plugin.getConfig().getString("hub.mundo", "hub");
            sender.sendMessage(Component.text("O mundo \"" + nome
                    + "\" não existe ou não está carregado.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Crie com: /mv create " + nome
                    + " normal -g BigaCore", NamedTextColor.YELLOW));
        }
        return mundo;
    }

    private void hubInfo(CommandSender sender) {
        org.bukkit.World mundo = mundoDoHub(sender);
        if (mundo == null) {
            return;
        }
        HubBuilder.Resultado conferido = plugin.hubBuilder().construir(mundo, false);
        sender.sendMessage(Component.text(conferido.mensagem(),
                conferido.ok() ? NamedTextColor.GRAY : NamedTextColor.RED));

        for (HubCatalog.Portal portal : plugin.hubCatalog().portais()) {
            String destino = portal.aberto() ? portal.mundo() : "em obra";
            boolean existe = !portal.aberto() || org.bukkit.Bukkit.getWorld(portal.mundo()) != null;
            sender.sendMessage(Component.text("  " + portal.face() + "  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(portal.titulo() + " → " + destino,
                            existe ? NamedTextColor.GRAY : NamedTextColor.RED)));
        }
    }

    private void hubConstruir(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDoHub(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text(
                    "Isto levanta a praça inteira no mundo " + mundo.getName() + ".",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " hub construir CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }

        HubBuilder.Resultado feito = plugin.hubBuilder().construir(mundo, true);
        sender.sendMessage(Component.text(feito.mensagem(),
                feito.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (feito.ok()) {
            sender.sendMessage(Component.text("O spawn do mundo foi posto na praça. "
                    + "Para o hub virar o spawn do servidor, rode também /mv setspawn aqui.",
                    NamedTextColor.GRAY));
        }
    }

    private void hubDesfazer(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDoHub(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text("Isto APAGA tudo na área do hub em "
                    + mundo.getName() + ". Não há cópia para restaurar.", NamedTextColor.RED));
            sender.sendMessage(Component.text("/" + label + " hub desfazer CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }

        HubBuilder.Resultado feito = plugin.hubBuilder().desfazer(mundo);
        sender.sendMessage(Component.text(feito.mensagem(), NamedTextColor.GREEN));
    }

    /**
     * Refaz os letreiros sem obra.
     *
     * Sem confirmação de propósito: não apaga bloco nenhum e o pior caso é
     * ver o mesmo texto de novo. Pedir CONFIRMAR aqui só atrapalharia quem
     * está ajustando cor e tamanho, que é justamente para quem isto existe.
     */
    private void hubTextos(CommandSender sender) {
        org.bukkit.World mundo = mundoDoHub(sender);
        if (mundo == null) {
            return;
        }
        HubBuilder.Resultado feito = plugin.hubBuilder().textos(mundo);
        sender.sendMessage(Component.text(feito.mensagem(),
                feito.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void hubIr(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Só dentro do jogo.", NamedTextColor.RED));
            return;
        }
        org.bukkit.World mundo = mundoDoHub(sender);
        if (mundo == null) {
            return;
        }
        jogador.teleportAsync(HubBuilder.pontoDeSpawn(mundo, plugin.hubCatalog()));
    }

    // ------------------------------------------------------------------
    //  /biga lobby
    // ------------------------------------------------------------------

    /**
     * O claustro de vinte portais dos minigames.
     *
     * Mesma forma do /biga hub, de propósito: quem aprendeu um sabe o outro.
     * Construir e desfazer pedem CONFIRMAR porque são comandos de uma palavra
     * que mexem em duzentos mil blocos.
     */
    private void lobby(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        if (args.length < 2) {
            enviarAjudaLobby(sender, label);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> lobbyInfo(sender);
            case "construir" -> lobbyConstruir(sender, label, args);
            case "desfazer" -> lobbyDesfazer(sender, label, args);
            case "ir" -> lobbyIr(sender);
            case "textos" -> lobbyTextos(sender);
            default -> enviarAjudaLobby(sender, label);
        }
    }

    private void enviarAjudaLobby(CommandSender sender, String label) {
        sender.sendMessage(Component.text("O lobby dos minigames", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Primeiro crie o mundo vazio, uma vez só:",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /mv create minigames normal -g BigaCore",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " lobby info", NamedTextColor.YELLOW)
                .append(Component.text(" — confere o terreno e lista os vinte portais",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " lobby construir CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " lobby desfazer CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " lobby textos", NamedTextColor.YELLOW)
                .append(Component.text(" — refaz só os letreiros, sem tocar em bloco",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " lobby ir", NamedTextColor.YELLOW)
                .append(Component.text(" — leva você ao pódio do monumento",
                        NamedTextColor.GRAY)));
    }

    /** O mundo em que o lobby mora, pelo config.yml. */
    private org.bukkit.World mundoDoLobby(CommandSender sender) {
        org.bukkit.World mundo = plugin.lobbyPortais().mundoDoLobby();
        if (mundo == null) {
            String nome = plugin.getConfig().getString("minigames.mundo", "minigames");
            sender.sendMessage(Component.text("O mundo \"" + nome
                    + "\" não existe ou não está carregado.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Crie com: /mv create " + nome
                    + " normal -g BigaCore", NamedTextColor.YELLOW));
        }
        return mundo;
    }

    /**
     * O estado da obra e dos vinte jogos, uma linha por galeria.
     *
     * Vinte linhas de portal encheriam o chat e ninguém leria a última.
     * Agrupado por galeria dá quatro linhas na ordem em que se anda, e a cor
     * de cada nome diz o estado: cinza escuro é obra, verde é aberto,
     * VERMELHO é o caso que interessa — tem mundo escrito no arquivo e o
     * mundo não está carregado.
     */
    private void lobbyInfo(CommandSender sender) {
        org.bukkit.World mundo = mundoDoLobby(sender);
        if (mundo == null) {
            return;
        }
        LobbyBuilder.Resultado conferido = plugin.lobbyBuilder().construir(mundo, false);
        sender.sendMessage(Component.text(conferido.mensagem(),
                conferido.ok() ? NamedTextColor.GRAY : NamedTextColor.RED));

        MinigamesCatalog planta = plugin.minigamesCatalog();
        List<MinigamesCatalog.Portal> portais = planta.portais();

        for (MinigamesCatalog.Lado lado : MinigamesCatalog.Lado.values()) {
            Component linha = Component.text("  " + lado + "  ", NamedTextColor.DARK_GRAY);
            boolean primeiro = true;
            for (int vao = -2; vao <= 2; vao++) {
                MinigamesCatalog.Portal achado = null;
                for (MinigamesCatalog.Portal portal : portais) {
                    if (portal.lado() == lado && portal.vao() == vao) {
                        achado = portal;
                        break;
                    }
                }
                if (achado == null) {
                    continue;
                }
                if (!primeiro) {
                    linha = linha.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
                }
                primeiro = false;

                NamedTextColor cor;
                if (!achado.aberto()) {
                    cor = NamedTextColor.DARK_GRAY;
                } else if (org.bukkit.Bukkit.getWorld(achado.mundo()) == null) {
                    cor = NamedTextColor.RED;
                } else {
                    cor = NamedTextColor.GREEN;
                }
                linha = linha.append(Component.text(achado.titulo(), cor));
            }
            sender.sendMessage(linha);
        }
    }

    private void lobbyConstruir(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDoLobby(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text("Isto levanta o lobby inteiro no mundo "
                    + mundo.getName() + " — cerca de duzentos mil blocos. O servidor "
                    + "trava por alguns segundos.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " lobby construir CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }

        LobbyBuilder.Resultado feito = plugin.lobbyBuilder().construir(mundo, true);
        sender.sendMessage(Component.text(feito.mensagem(),
                feito.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (feito.ok()) {
            sender.sendMessage(Component.text("O spawn do mundo foi posto no pódio. "
                    + "Falta abrir o portal do hub: ponha minigames em 'mundo:' no "
                    + "portal roxo do hub.yml e rode /biga reload.", NamedTextColor.GRAY));
        }
    }

    private void lobbyDesfazer(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDoLobby(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text("Isto APAGA tudo na área do lobby em "
                    + mundo.getName() + ". Não há cópia para restaurar.", NamedTextColor.RED));
            sender.sendMessage(Component.text("/" + label + " lobby desfazer CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }

        LobbyBuilder.Resultado feito = plugin.lobbyBuilder().desfazer(mundo);
        sender.sendMessage(Component.text(feito.mensagem(), NamedTextColor.GREEN));
    }

    /** Refaz os letreiros sem obra. Sem CONFIRMAR: não apaga bloco nenhum. */
    private void lobbyTextos(CommandSender sender) {
        org.bukkit.World mundo = mundoDoLobby(sender);
        if (mundo == null) {
            return;
        }
        LobbyBuilder.Resultado feito = plugin.lobbyBuilder().textos(mundo);
        sender.sendMessage(Component.text(feito.mensagem(),
                feito.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void lobbyIr(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Só dentro do jogo.", NamedTextColor.RED));
            return;
        }
        org.bukkit.World mundo = mundoDoLobby(sender);
        if (mundo == null) {
            return;
        }
        jogador.teleportAsync(LobbyBuilder.pontoDeSpawn(mundo, plugin.minigamesCatalog()));
    }

    // ------------------------------------------------------------------
    //  /biga tntrun
    // ------------------------------------------------------------------

    /**
     * A arena do TNT Run.
     *
     * Dois comandos existem só para quem administra e não para jogar:
     * {@code comecar} larga a rodada sem esperar o mínimo de gente — é o que
     * torna o jogo testável por uma pessoa só — e {@code parar} desfaz uma
     * rodada travada sem precisar derrubar o servidor.
     */
    private void tntRun(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        if (args.length < 2) {
            enviarAjudaTntRun(sender, label);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> tntRunInfo(sender);
            case "construir" -> tntRunConstruir(sender, label, args);
            case "desfazer" -> tntRunDesfazer(sender, label, args);
            case "textos" -> tntRunTextos(sender);
            case "ir" -> tntRunIr(sender);
            case "comecar" -> sender.sendMessage(Component.text(
                    plugin.tntRun().comecarAgora(), NamedTextColor.GRAY));
            case "parar" -> sender.sendMessage(Component.text(
                    plugin.tntRun().pararRodada(), NamedTextColor.GRAY));
            default -> enviarAjudaTntRun(sender, label);
        }
    }

    private void enviarAjudaTntRun(CommandSender sender, String label) {
        sender.sendMessage(Component.text("A arena do TNT Run", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("O mundo é criado sozinho na subida. "
                + "Falta só levantar a arena:", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " tntrun info",
                NamedTextColor.YELLOW).append(Component.text(
                        " — confere o terreno e mostra a geometria", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " tntrun construir CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " tntrun desfazer CONFIRMAR",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " tntrun textos",
                NamedTextColor.YELLOW).append(Component.text(
                        " — refaz só os letreiros", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " tntrun ir",
                NamedTextColor.YELLOW).append(Component.text(
                        " — leva você ao saguão", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " tntrun comecar",
                NamedTextColor.YELLOW).append(Component.text(
                        " — larga a rodada agora, mesmo sozinho (treino)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " tntrun parar",
                NamedTextColor.YELLOW).append(Component.text(
                        " — cancela a rodada e devolve todos ao saguão",
                        NamedTextColor.GRAY)));
    }

    /** O mundo da arena, criado na hora se ainda não existir. */
    private org.bukkit.World mundoDaArena(CommandSender sender) {
        String nome = plugin.tntRunCatalog().mundo();
        org.bukkit.World mundo = MundoDeJogo.garantir(plugin, nome);
        if (mundo == null) {
            sender.sendMessage(Component.text("Não foi possível criar nem carregar o "
                    + "mundo \"" + nome + "\". Veja o console.", NamedTextColor.RED));
            sender.sendMessage(Component.text("Crie à mão: /mv create " + nome
                    + " normal -g BigaCore", NamedTextColor.YELLOW));
        }
        return mundo;
    }

    private void tntRunInfo(CommandSender sender) {
        org.bukkit.World mundo = mundoDaArena(sender);
        if (mundo == null) {
            return;
        }
        TntRunBuilder.Resultado conferido = plugin.tntRunBuilder().construir(mundo, false);
        sender.sendMessage(Component.text(conferido.mensagem(),
                conferido.ok() ? NamedTextColor.GRAY : NamedTextColor.RED));

        TntRun jogo = plugin.tntRun();
        sender.sendMessage(Component.text("  estado: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(jogo.estado().name(), NamedTextColor.AQUA))
                .append(Component.text("  no saguão: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(jogo.quantosProntos() + "/"
                        + jogo.quantosNaArena(), NamedTextColor.WHITE))
                .append(Component.text("  de pé: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(jogo.quantosVivos()),
                        NamedTextColor.WHITE)));
    }

    private void tntRunConstruir(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDaArena(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text("Isto levanta a arena inteira no mundo "
                    + mundo.getName() + ". O servidor trava por um instante.",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " tntrun construir CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }

        TntRunBuilder.Resultado feito = plugin.tntRunBuilder().construir(mundo, true);
        sender.sendMessage(Component.text(feito.mensagem(),
                feito.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (feito.ok()) {
            sender.sendMessage(Component.text("O portal vermelho do lobby já aponta "
                    + "para cá. Entre por ele, ou use /" + label + " tntrun ir.",
                    NamedTextColor.GRAY));
        }
    }

    private void tntRunDesfazer(CommandSender sender, String label, String[] args) {
        org.bukkit.World mundo = mundoDaArena(sender);
        if (mundo == null) {
            return;
        }
        if (args.length < 3 || !args[2].equals("CONFIRMAR")) {
            sender.sendMessage(Component.text("Isto APAGA tudo na área da arena em "
                    + mundo.getName() + ". Não há cópia para restaurar.",
                    NamedTextColor.RED));
            sender.sendMessage(Component.text("/" + label + " tntrun desfazer CONFIRMAR",
                    NamedTextColor.YELLOW));
            return;
        }
        TntRunBuilder.Resultado feito = plugin.tntRunBuilder().desfazer(mundo);
        sender.sendMessage(Component.text(feito.mensagem(), NamedTextColor.GREEN));
    }

    private void tntRunTextos(CommandSender sender) {
        org.bukkit.World mundo = mundoDaArena(sender);
        if (mundo == null) {
            return;
        }
        TntRunBuilder.Resultado feito = plugin.tntRunBuilder().textos(mundo);
        sender.sendMessage(Component.text(feito.mensagem(), NamedTextColor.GREEN));
    }

    private void tntRunIr(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Só dentro do jogo.", NamedTextColor.RED));
            return;
        }
        org.bukkit.World mundo = mundoDaArena(sender);
        if (mundo == null) {
            return;
        }
        jogador.teleportAsync(
                TntRunBuilder.pontoDoSaguao(mundo, plugin.tntRunCatalog()));
    }

    private void mostrarLocalLoja(Player jogador) {
        SpawnShopBuilder.Analysis analysis = plugin.spawnShopBuilder().analyze(jogador.getWorld());
        if (!analysis.valid()) {
            jogador.sendMessage(Component.text(analysis.reason(), NamedTextColor.RED));
            return;
        }

        jogador.sendMessage(Component.text("Biga Market — referência do spawn", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Spawn real", formatLocation(analysis.spawn())));
        jogador.sendMessage(linha("Centro proposto", formatLocation(analysis.center())));
        jogador.sendMessage(linha("Fachada olha para", analysis.facingName()));
        jogador.sendMessage(Component.text(
                "A posição vem do spawn do mundo + offset configurado, nunca de coordenada fixa no Git.",
                NamedTextColor.DARK_GRAY));
    }

    /**
     * Diagnóstico detalhado.
     *
     * Separa "topo" de "solo" de propósito: foi exatamente essa confusão que
     * fez o centro ser calculado 19 blocos acima do spawn quando a copa de
     * spruce virou referência de piso.
     */
    private void validarLoja(Player jogador) {
        SpawnShopBuilder.Analysis analysis = plugin.spawnShopBuilder().analyze(jogador.getWorld());
        if (!analysis.valid()) {
            jogador.sendMessage(Component.text(analysis.reason(), NamedTextColor.RED));
            return;
        }

        SpawnShopBuilder.FootprintSurvey survey = analysis.survey();

        jogador.sendMessage(Component.text("Biga Market — validação", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Spawn real", formatLocation(analysis.spawn())));
        jogador.sendMessage(linha("Centro X/Z",
                analysis.center().getBlockX() + ", " + analysis.center().getBlockZ()));
        jogador.sendMessage(linha("Fachada", analysis.facingName()));
        jogador.sendMessage(linha("Footprint",
                analysis.footprintWidth() + "x" + analysis.footprintDepth()
                        + " (" + survey.totalColumns() + " colunas)"));
        jogador.sendMessage(linha("Solo real min/max",
                survey.minGroundY() + ".." + survey.maxGroundY()
                        + "  (desnível " + analysis.slope() + ")"));
        jogador.sendMessage(linha("Topo min/max (com folhagem)",
                survey.minTopY() + ".." + survey.maxTopY()));
        jogador.sendMessage(linha("Colunas com árvore", String.valueOf(survey.treeColumns())));
        jogador.sendMessage(linha("Colunas com líquido", String.valueOf(survey.liquidColumns())));
        jogador.sendMessage(linha("Colunas com TileState", String.valueOf(survey.tileStateColumns())));
        jogador.sendMessage(linha("Blocos de construção",
                survey.manMadeBlocks() + " (1º: " + survey.firstManMadeName() + ")"));
        jogador.sendMessage(linha("Colunas sem solo", String.valueOf(survey.columnsWithoutGround())));
        jogador.sendMessage(linha("Obstruções no volume", String.valueOf(analysis.obstructionCount())));
        jogador.sendMessage(linha("Aterro necessário", analysis.foundationDepth() + " bloco(s)"));
        jogador.sendMessage(linha("Piso recomendado", "Y=" + analysis.floorY()));

        NamedTextColor color = NamedTextColor.RED;
        String status = "BLOQUEADA";
        if (analysis.safe()) {
            color = NamedTextColor.GREEN;
            status = "SEGURA";
        }

        jogador.sendMessage(Component.text("Status: " + status, color).decorate(TextDecoration.BOLD));
        jogador.sendMessage(Component.text("Motivo: " + analysis.reason(), NamedTextColor.GRAY));

        if (!analysis.safe()) {
            jogador.sendMessage(Component.text(
                    "Ajuste loja-spawn.offset-x/offset-z no config e rode /biga reload.",
                    NamedTextColor.DARK_GRAY));
        }
    }

    private void previewLoja(Player jogador) {
        SpawnShopBuilder.Analysis analysis = plugin.spawnShopBuilder().analyze(jogador.getWorld());
        if (!analysis.valid()) {
            jogador.sendMessage(Component.text(analysis.reason(), NamedTextColor.RED));
            return;
        }

        ShopPreview preview = plugin.shopPreview();
        preview.start(jogador, analysis);

        jogador.sendMessage(Component.text("Biga Market — preview", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Spawn", formatLocation(analysis.spawn())));
        jogador.sendMessage(linha("Centro X/Z",
                analysis.center().getBlockX() + ", " + analysis.center().getBlockZ()));
        jogador.sendMessage(linha("Piso calculado", "Y=" + analysis.floorY()));
        jogador.sendMessage(linha("Fachada", analysis.facingName()));
        jogador.sendMessage(linha("Footprint",
                analysis.footprintWidth() + "x" + analysis.footprintDepth()));

        String status = "BLOQUEADA";
        if (analysis.safe()) {
            status = "SEGURA";
        }
        jogador.sendMessage(linha("Status", status));
        jogador.sendMessage(Component.text(
                "Verde = perímetro | laranja = fachada | branco = cantos e entrada | azul = centro.",
                NamedTextColor.DARK_GRAY));
        jogador.sendMessage(Component.text(
                "Nenhum bloco foi alterado. O preview some em ~"
                        + preview.durationSeconds() + "s.",
                NamedTextColor.DARK_GRAY));
    }

    private void criarLoja(Player jogador, String label, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("CONFIRMAR")) {
            jogador.sendMessage(Component.text(
                    "Este comando altera blocos do mundo. Valide antes com /" + label + " loja validar.",
                    NamedTextColor.YELLOW));
            jogador.sendMessage(Component.text(
                    "Para construir: /" + label + " loja criar CONFIRMAR",
                    NamedTextColor.GOLD));
            return;
        }

        SpawnShopBuilder.BuildResult result = plugin.spawnShopBuilder()
                .build(jogador.getWorld(), plugin.shopSnapshot());

        NamedTextColor color = NamedTextColor.RED;
        if (result.success()) {
            color = NamedTextColor.GREEN;
        }
        jogador.sendMessage(Component.text(result.message(), color).decorate(TextDecoration.BOLD));

        if (result.success()) {
            jogador.sendMessage(Component.text(
                    "Snapshot salvo. Para reverter: /" + label + " loja desfazer CONFIRMAR",
                    NamedTextColor.GRAY));
            return;
        }

        jogador.sendMessage(Component.text(
                "Ajuste loja-spawn.offset-x/offset-z no config e rode /biga reload antes de tentar novamente.",
                NamedTextColor.GRAY));
    }

    /**
     * Escreve o balcão do servidor nas placas vazias de uma construção pronta.
     *
     * Sem argumento nenhum o comando apenas SIMULA — e isso é deliberado. Não
     * existe snapshot de rollback para placa como existe para construção, e
     * desfazer 92 placas na mão é um castigo. O relatório mostra os dois
     * desencontros possíveis antes de qualquer escrita: placa que vai sobrar
     * vazia e item do catálogo que não encontrou vaga.
     */
    private void placasLoja(Player jogador, String label, String[] args) {
        int raio = ShopSignWriter.RAIO_PADRAO;
        boolean confirmar = false;
        boolean limpar = false;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("CONFIRMAR")) {
                confirmar = true;
                continue;
            }
            if (args[i].equalsIgnoreCase("limpar")) {
                limpar = true;
                continue;
            }
            try {
                raio = Integer.parseInt(args[i]);
            } catch (NumberFormatException ignorado) {
                jogador.sendMessage(Component.text(
                        "Raio inválido: '" + args[i] + "'. Usando " + raio + ".",
                        NamedTextColor.YELLOW));
            }
        }

        ShopSignWriter escritor = plugin.shopSignWriter();

        if (limpar) {
            if (!confirmar) {
                ShopSignWriter.Relatorio previa = escritor.simular(jogador.getLocation(), raio);
                jogador.sendMessage(Component.text(
                        previa.placasEmBranco() + " placa(s) em branco seriam APAGADAS.",
                        NamedTextColor.GOLD));
                jogador.sendMessage(Component.text(
                        "Para apagar de verdade: /" + label + " loja placas " + previa.raio()
                                + " limpar CONFIRMAR",
                        NamedTextColor.GRAY));
                return;
            }
            int apagadas = escritor.removerEmBranco(jogador.getLocation(), raio);
            jogador.sendMessage(Component.text(
                    apagadas + " placa(s) em branco removida(s).", NamedTextColor.GOLD));
            return;
        }

        ShopSignWriter.Relatorio r = confirmar
                ? escritor.escrever(jogador.getLocation(), raio)
                : escritor.simular(jogador.getLocation(), raio);

        if (r.placasEmBranco() == 0) {
            jogador.sendMessage(Component.text(
                    "Nenhuma placa em branco num raio de " + r.raio() + " blocos.",
                    NamedTextColor.RED));
            jogador.sendMessage(Component.text(
                    "Cole a construção primeiro, ou aumente o raio: /" + label
                            + " loja placas 48",
                    NamedTextColor.GRAY));
            return;
        }

        String titulo = confirmar ? "Balcão escrito" : "Simulação — nada foi alterado";
        jogador.sendMessage(Component.text(titulo, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Placas em branco", String.valueOf(r.placasEmBranco())));
        jogador.sendMessage(linha("Itens pedidos no economy.yml", String.valueOf(r.itensPedidos())));
        jogador.sendMessage(linha(confirmar ? "Placas escritas" : "Seriam escritas",
                String.valueOf(r.itensColocados())));

        if (r.sobrando() > 0) {
            jogador.sendMessage(Component.text(
                    "• " + r.sobrando() + " placa(s) ficam vazias: a construção tem mais"
                            + " vagas do que o catálogo tem itens vendáveis.",
                    NamedTextColor.YELLOW));
        }
        if (r.semVaga() > 0) {
            jogador.sendMessage(Component.text(
                    "• " + r.semVaga() + " item(ns) ficaram de fora por falta de vaga"
                            + " ou por não ter server-sell. Veja os avisos no console.",
                    NamedTextColor.YELLOW));
        }

        if (!confirmar) {
            jogador.sendMessage(Component.text(
                    "Para escrever de verdade: /" + label + " loja placas " + r.raio()
                            + " CONFIRMAR",
                    NamedTextColor.GOLD));
            return;
        }

        jogador.sendMessage(Component.text(
                "Só placas em branco foram tocadas — decoração da construção ficou intacta.",
                NamedTextColor.DARK_GRAY));
    }

    /**
     * Pendura (ou apaga) o item flutuante na frente de cada placa do balcão.
     *
     * <p>Aceita {@code limpar} como terceiro argumento porque remover é a
     * operação que o dono vai querer com pressa: item flutuante errado fica
     * visível para todo mundo que entra na loja, e esperar lembrar da sintaxe
     * de CONFIRMAR enquanto a loja está feia é tempo demais.</p>
     */
    private void vitrineLoja(Player jogador, String label, String[] args) {
        int raio = ShopSignWriter.RAIO_PADRAO;
        boolean confirmar = false;
        boolean limpar = false;

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("CONFIRMAR")) {
                confirmar = true;
                continue;
            }
            if (args[i].equalsIgnoreCase("limpar")) {
                limpar = true;
                continue;
            }
            try {
                raio = Integer.parseInt(args[i]);
            } catch (NumberFormatException ignorado) {
                jogador.sendMessage(Component.text(
                        "Raio inválido: '" + args[i] + "'. Usando " + raio + ".",
                        NamedTextColor.YELLOW));
            }
        }

        ShopDisplays vitrines = plugin.shopDisplays();

        if (limpar) {
            int removidos = vitrines.removerNoRaio(jogador.getLocation(), raio);
            jogador.sendMessage(Component.text(
                    removidos + " item(ns) flutuante(s) removido(s).", NamedTextColor.GOLD));
            return;
        }

        ShopDisplays.Relatorio r = vitrines.pendurar(jogador.getLocation(), raio, confirmar);

        if (r.placas() == 0) {
            jogador.sendMessage(Component.text(
                    "Nenhuma placa do balcão num raio de " + r.raio() + " blocos.",
                    NamedTextColor.RED));
            jogador.sendMessage(Component.text(
                    "Escreva as placas antes: /" + label + " loja placas " + r.raio()
                            + " CONFIRMAR",
                    NamedTextColor.GRAY));
            return;
        }

        String titulo = confirmar ? "Vitrine pendurada" : "Simulação — nada foi alterado";
        jogador.sendMessage(Component.text(titulo, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha("Placas do balcão", String.valueOf(r.placas())));
        jogador.sendMessage(linha(confirmar ? "Itens pendurados" : "Seriam pendurados",
                String.valueOf(r.penduradas())));
        if (confirmar && r.removidas() > 0) {
            jogador.sendMessage(linha("Vitrine antiga removida", String.valueOf(r.removidas())));
        }

        if (r.semMaterial() > 0) {
            jogador.sendMessage(Component.text(
                    "• " + r.semMaterial() + " placa(s) com nome que não bate com nenhum item"
                            + " do catálogo. Veja os avisos no console.",
                    NamedTextColor.YELLOW));
        }
        if (r.semEspaco() > 0) {
            jogador.sendMessage(Component.text(
                    "• " + r.semEspaco() + " placa(s) sem espaço livre na frente:"
                            + " o item ficaria dentro da parede.",
                    NamedTextColor.YELLOW));
        }

        if (!confirmar) {
            jogador.sendMessage(Component.text(
                    "Para pendurar de verdade: /" + label + " loja vitrine " + r.raio()
                            + " CONFIRMAR",
                    NamedTextColor.GOLD));
            jogador.sendMessage(Component.text(
                    "Para apagar depois: /" + label + " loja vitrine " + r.raio() + " limpar",
                    NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * Liga ou desliga a trava de /plot abandon no terreno onde o jogador está.
     *
     * Sem CONFIRMAR de propósito nos dois sentidos: proteger não destrói nada,
     * e desproteger também não — o estrago só viria do /plot abandon seguinte,
     * que é onde a confirmação já existe na cabeça de quem digita.
     */
    private void protegerTerreno(Player jogador, boolean ligar) {
        PlotApiBridge terrenos = plugin.plotApi();
        if (!terrenos.available()) {
            jogador.sendMessage(Component.text(
                    "CreativePlots não respondeu. Sem ele não há terreno para proteger.",
                    NamedTextColor.RED));
            return;
        }

        var terreno = terrenos.terrenoEm(jogador.getLocation());
        if (terreno.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Fique dentro do terreno que você quer proteger.", NamedTextColor.RED));
            return;
        }

        String mundo = jogador.getWorld().getName();
        boolean mudou = ligar
                ? plugin.plotSale().proteger(mundo, terreno.get())
                : plugin.plotSale().desproteger(mundo, terreno.get());

        if (!mudou) {
            jogador.sendMessage(Component.text(
                    "O terreno " + terreno.get() + " já estava "
                            + (ligar ? "protegido" : "desprotegido") + ".",
                    NamedTextColor.YELLOW));
            return;
        }

        if (ligar) {
            jogador.sendMessage(Component.text(
                    "Terreno " + terreno.get() + " protegido: /plot abandon passa a ser recusado.",
                    NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            jogador.sendMessage(Component.text(
                    "Abandonar um terreno apaga os blocos dele, não só a posse.",
                    NamedTextColor.DARK_GRAY));
            return;
        }

        jogador.sendMessage(Component.text(
                "Terreno " + terreno.get() + " desprotegido. /plot abandon volta a funcionar"
                        + " — e apaga o que estiver construído.",
                NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
    }

    private void desfazerLoja(Player jogador, String label, String[] args) {
        if (!plugin.shopSnapshot().exists()) {
            jogador.sendMessage(Component.text(
                    "Não existe snapshot da Biga Market para desfazer.", NamedTextColor.YELLOW));
            return;
        }

        if (args.length < 3 || !args[2].equalsIgnoreCase("CONFIRMAR")) {
            jogador.sendMessage(Component.text(
                    "Isto restaura os blocos originais e apaga a loja construída.",
                    NamedTextColor.YELLOW));
            jogador.sendMessage(Component.text(
                    "Para desfazer: /" + label + " loja desfazer CONFIRMAR",
                    NamedTextColor.GOLD));
            return;
        }

        // Snapshot cobre blocos. Item flutuante é entidade — se não for removido
        // aqui, a loja some e os itens ficam boiando sobre o mato.
        int displays = plugin.shopDisplays().removeAll(jogador.getWorld());

        ShopSnapshot.RestoreResult result = plugin.shopSnapshot().restore(jogador.getWorld());

        if (displays > 0) {
            jogador.sendMessage(linha("Itens flutuantes removidos", String.valueOf(displays)));
        }
        NamedTextColor color = NamedTextColor.RED;
        if (result.success()) {
            color = NamedTextColor.GREEN;
        }
        jogador.sendMessage(Component.text(result.message(), color).decorate(TextDecoration.BOLD));
    }

    private String formatLocation(Location location) {
        String worldName = "sem-mundo";
        if (location.getWorld() != null) {
            worldName = location.getWorld().getName();
        }
        return worldName + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    private Component linha(String chave, String valor) {
        return Component.text(chave + ": ", NamedTextColor.GRAY)
                .append(Component.text(valor, NamedTextColor.WHITE));
    }

    private void recarregar(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        plugin.reloadBigaConfigs();
        sender.sendMessage(Component.text("Configuracoes do BigaCore recarregadas.", NamedTextColor.GREEN));
    }

    private void alternarVoo(CommandSender sender) {
        if (!sender.hasPermission("bigacore.admin")) {
            semPermissao(sender);
            return;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando so funciona em jogo.", NamedTextColor.RED));
            return;
        }

        boolean novoEstado = !jogador.getAllowFlight();
        jogador.setAllowFlight(novoEstado);

        String textoEstado = "desativado";
        if (novoEstado) {
            textoEstado = "ativado";
        }
        jogador.sendMessage(Component.text("Voo " + textoEstado + ".", NamedTextColor.GREEN));
    }

    private void semPermissao(CommandSender sender) {
        sender.sendMessage(Component.text("Voce nao tem permissao para isso.", NamedTextColor.RED));
    }

    /**
     * Levanta ou apaga o paredão de ajuda do spawn.
     *
     * Exige CONFIRMAR para construir, como o resto dos comandos que
     * criam coisa no mundo: sem isso, uma digitação errada enche o
     * chunk de entidade de texto a seis blocos de onde a pessoa estava.
     */
    private void paredaoAjuda(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text(
                    "Use /" + label + " painel dentro do jogo: a parede nasce à frente"
                            + " de quem digita, na direção em que está olhando.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!jogador.hasPermission("bigacore.admin")) {
            semPermissao(jogador);
            return;
        }

        PainelAjuda painel = plugin.painelAjuda();
        String acao = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "construir";

        if (acao.equals("limpar")) {
            int removidos = painel.limpar(jogador.getWorld());
            jogador.sendMessage(Component.text(
                    removidos + " painel(éis) removido(s) de " + jogador.getWorld().getName() + ".",
                    NamedTextColor.GOLD));
            return;
        }

        if (!acao.equals("construir")) {
            jogador.sendMessage(Component.text(
                    "Uso: /" + label + " painel <construir|limpar>", NamedTextColor.YELLOW));
            return;
        }

        boolean confirmar = args.length >= 3 && args[2].equalsIgnoreCase("CONFIRMAR");
        PainelAjuda.Relatorio r = painel.construir(jogador, confirmar);

        if (!r.ok()) {
            jogador.sendMessage(Component.text(r.erro(), NamedTextColor.RED));
            return;
        }

        String titulo = confirmar ? "Paredão construído" : "Simulação — nada foi criado";
        jogador.sendMessage(Component.text(titulo, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(linha(confirmar ? "Painéis criados" : "Seriam criados",
                String.valueOf(r.criados())));
        jogador.sendMessage(linha(confirmar ? "Painéis antigos removidos" : "Já existem no mundo",
                String.valueOf(r.removidos())));
        jogador.sendMessage(linha("Espaçamento usado",
                String.format(java.util.Locale.ROOT, "%.1f na horizontal, %.1f na vertical",
                        r.espacamento(), r.espacamentoVertical())));

        if (!confirmar) {
            jogador.sendMessage(Component.text(
                    "Para construir de verdade: /" + label + " painel construir CONFIRMAR",
                    NamedTextColor.GOLD));
            return;
        }

        jogador.sendMessage(Component.text(
                "O texto fica em ajuda.yml. Edite, /" + label + " reload, e construa de novo.",
                NamedTextColor.GRAY));
        jogador.sendMessage(Component.text(
                "Saiu de costas? Troque layout.giro entre 0 e 180 no ajuda.yml.",
                NamedTextColor.DARK_GRAY));
    }

    private void enviarAjuda(CommandSender sender, String label) {
        Component ajuda = Component.text("Uso: ", NamedTextColor.YELLOW)
                .append(Component.text("/" + label + " ", NamedTextColor.WHITE));

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

    private void enviarAjudaEconomia(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Uso: /" + label + " eco <status|saldo|preco|vender|regras>",
                NamedTextColor.YELLOW));
    }

    private void enviarAjudaLoja(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Uso: /" + label
                        + " loja <local|validar|preview|criar|placas|vitrine|desfazer>",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Ordem segura: local -> validar -> preview -> criar CONFIRMAR",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Construção pronta, colada com WorldEdit: placas [raio] CONFIRMAR",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Item flutuante na frente das placas: vitrine [raio] CONFIRMAR"
                        + " (limpar apaga)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Reverter a última construção: desfazer CONFIRMAR",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return filtrar(SUBCOMANDOS, args[0]);
        }

        if (args[0].equalsIgnoreCase("eco")) {
            if (args.length == 2) {
                return filtrar(ECO_SUBCOMANDOS, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("preco")) {
                return filtrar(plugin.economyCatalog().keys(), args[2]);
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("painel")) {
            if (args.length == 2) {
                return filtrar(PAINEL_SUBCOMANDOS, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("construir")) {
                return filtrar(List.of("CONFIRMAR"), args[2]);
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("hub")) {
            if (args.length == 2) {
                return filtrar(HUB_SUBCOMANDOS, args[1]);
            }
            boolean pedeConfirmacao = args[1].equalsIgnoreCase("construir")
                    || args[1].equalsIgnoreCase("desfazer");
            if (args.length == 3 && pedeConfirmacao) {
                return filtrar(List.of("CONFIRMAR"), args[2]);
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("lobby")) {
            if (args.length == 2) {
                return filtrar(LOBBY_SUBCOMANDOS, args[1]);
            }
            boolean pedeConfirmacao = args[1].equalsIgnoreCase("construir")
                    || args[1].equalsIgnoreCase("desfazer");
            if (args.length == 3 && pedeConfirmacao) {
                return filtrar(List.of("CONFIRMAR"), args[2]);
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("tntrun")) {
            if (args.length == 2) {
                return filtrar(TNTRUN_SUBCOMANDOS, args[1]);
            }
            boolean pedeConfirmacao = args[1].equalsIgnoreCase("construir")
                    || args[1].equalsIgnoreCase("desfazer");
            if (args.length == 3 && pedeConfirmacao) {
                return filtrar(List.of("CONFIRMAR"), args[2]);
            }
            return List.of();
        }

        if (args[0].equalsIgnoreCase("loja")) {
            if (args.length == 2) {
                return filtrar(LOJA_SUBCOMANDOS, args[1]);
            }
            boolean pedeConfirmacao = args[1].equalsIgnoreCase("criar")
                    || args[1].equalsIgnoreCase("desfazer");
            if (args.length == 3 && pedeConfirmacao) {
                return filtrar(List.of("CONFIRMAR"), args[2]);
            }
            return List.of();
        }

        return List.of();
    }

    private List<String> filtrar(List<String> opcoes, String texto) {
        String prefixo = texto.toLowerCase(Locale.ROOT);
        List<String> resultado = new ArrayList<>();
        for (String opcao : opcoes) {
            if (opcao.toLowerCase(Locale.ROOT).startsWith(prefixo)) {
                resultado.add(opcao);
            }
        }
        return resultado;
    }
}
