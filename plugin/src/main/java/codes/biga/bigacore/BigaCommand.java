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

    private static final List<String> SUBCOMANDOS = List.of("info", "eco", "loja", "reload", "voar");
    private static final List<String> ECO_SUBCOMANDOS =
            List.of("status", "saldo", "preco", "vender", "regras");
    private static final List<String> LOJA_SUBCOMANDOS =
            List.of("local", "validar", "preview", "criar", "placas",
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
        sender.sendMessage(linha("Lote", String.valueOf(entry.lot())));
        sender.sendMessage(linha("Referencia P2P", catalog.money(entry.p2p())));

        String serverBuyText = "P2P somente";
        if (entry.serverBuy() != null) {
            serverBuyText = catalog.money(entry.serverBuy());
        }
        sender.sendMessage(linha("Servidor compra", serverBuyText));

        String serverSellText = "P2P somente";
        if (entry.serverSell() != null) {
            serverSellText = catalog.money(entry.serverSell());
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
            case "proteger" -> protegerTerreno(jogador, true);
            case "desproteger" -> protegerTerreno(jogador, false);
            case "desfazer" -> desfazerLoja(jogador, label, args);
            default -> enviarAjudaLoja(sender, label);
        }
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

        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("CONFIRMAR")) {
                confirmar = true;
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
                "Uso: /" + label + " loja <local|validar|preview|criar|placas|desfazer>",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Ordem segura: local -> validar -> preview -> criar CONFIRMAR",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Construção pronta, colada com WorldEdit: placas [raio] CONFIRMAR",
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
