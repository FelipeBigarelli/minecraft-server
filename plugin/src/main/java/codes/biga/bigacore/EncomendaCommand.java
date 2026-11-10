package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * O comando /encomenda — o quadro de pedidos entre jogadores.
 *
 * Por que ele existe: ate 10/11/2026 o dinheiro do servidor so andava entre
 * jogador e servidor. O ChestShop exige estoque parado e alguem passando na
 * frente da placa; com quatro pessoas isso nunca aconteceu (3 transacoes em
 * tres meses, todas na loja do servidor). Uma encomenda precisa apenas de
 * alguem QUERENDO uma coisa, e por isso funciona com duas pessoas.
 *
 * O dinheiro fica em custodia: sai da conta de quem pede na criacao e so
 * aparece na de quem entrega na entrega. Nenhuma moeda e criada aqui — toda
 * moeda depositada foi sacada antes. Veja {@link VaultEconomyBridge#deposit}.
 */
public final class EncomendaCommand implements CommandExecutor, TabCompleter {

    static final String PERM_USO = "bigacore.encomenda";
    static final String PERM_ADMIN = "bigacore.encomenda.admin";

    private static final List<String> SUBCOMANDOS = List.of(
            "criar", "lista", "minhas", "entregar", "retirar", "cancelar");

    // Mesma paleta do /terreno, para os dois comandos parecerem o mesmo
    // servidor e nao dois plugins diferentes.
    private static final TextColor COMANDO = NamedTextColor.YELLOW;
    private static final TextColor TEXTO = NamedTextColor.GRAY;
    private static final TextColor DESTAQUE = NamedTextColor.GOLD;
    private static final TextColor VALOR = NamedTextColor.WHITE;
    private static final TextColor ALERTA = NamedTextColor.RED;
    private static final TextColor FEITO = NamedTextColor.GREEN;

    private final BigaCore plugin;

    public EncomendaCommand(BigaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text(
                    "/encomenda so funciona dentro do jogo — ele mexe na sua mochila.",
                    ALERTA));
            return true;
        }

        if (!jogador.hasPermission(PERM_USO)) {
            jogador.sendMessage(Component.text(
                    "Voce nao tem permissao para usar o quadro de encomendas.", ALERTA));
            return true;
        }

        if (!plugin.encomendaCatalog().ativo()) {
            jogador.sendMessage(Component.text(
                    "O quadro de encomendas esta desligado no momento.", ALERTA));
            return true;
        }

        if (args.length == 0) {
            ajuda(jogador, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "criar" -> criar(jogador, label, args);
            case "lista", "listar" -> lista(jogador);
            case "minhas" -> minhas(jogador);
            case "entregar" -> entregar(jogador, label, args);
            case "retirar" -> retirar(jogador);
            case "cancelar" -> cancelar(jogador, label, args);
            default -> ajuda(jogador, label);
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Ajuda
    // ------------------------------------------------------------------

    private void ajuda(Player jogador, String label) {
        EncomendaCatalog cfg = plugin.encomendaCatalog();
        EconomyCatalog eco = plugin.economyCatalog();

        cabecalho(jogador, "QUADRO DE ENCOMENDAS");
        jogador.sendMessage(Component.text(
                "Voce paga adiantado por um item e qualquer jogador pode entregar.", TEXTO));
        jogador.sendMessage(Component.text(
                "O dinheiro fica guardado ate alguem entregar ou voce cancelar.", TEXTO));
        jogador.sendMessage(Component.empty());

        linhaAjuda(jogador, "/" + label + " criar", "<item> <qtd> <preco>",
                "abre um pedido e paga na hora");
        linhaAjuda(jogador, "/" + label + " lista", null,
                "tudo que esta aberto");
        linhaAjuda(jogador, "/" + label + " minhas", null,
                "so os seus pedidos");
        linhaAjuda(jogador, "/" + label + " entregar", "<numero>",
                "entrega e recebe o dinheiro");
        linhaAjuda(jogador, "/" + label + " retirar", null,
                "pega o que entregaram para voce");
        linhaAjuda(jogador, "/" + label + " cancelar", "<numero>",
                "fecha o pedido e devolve o dinheiro");

        jogador.sendMessage(Component.empty());
        jogador.sendMessage(Component.text("Limites: ", TEXTO)
                .append(Component.text(cfg.limitePorJogador() + " pedidos abertos", VALOR))
                .append(Component.text(", ate ", TEXTO))
                .append(Component.text(cfg.quantidadeMaxima() + " itens", VALOR))
                .append(Component.text(", de ", TEXTO))
                .append(Component.text(eco.money(cfg.precoMinimo()), VALOR))
                .append(Component.text(" a ", TEXTO))
                .append(Component.text(eco.money(cfg.precoMaximo()), VALOR))
                .append(Component.text(".", TEXTO)));
        jogador.sendMessage(Component.text(
                "So vale item limpo — sem encantamento, nome ou dano.", TEXTO));
    }

    // ------------------------------------------------------------------
    // Criar
    // ------------------------------------------------------------------

    private void criar(Player jogador, String label, String[] args) {
        if (args.length < 4) {
            jogador.sendMessage(Component.text(
                    "Use /" + label + " criar <item> <quantidade> <preco total>.", ALERTA));
            jogador.sendMessage(Component.text(
                    "Exemplo: /" + label + " criar iron_ingot 64 500", TEXTO));
            return;
        }

        Material item = Material.matchMaterial(args[1]);
        if (item == null || !item.isItem()) {
            jogador.sendMessage(Component.text(
                    "Nao conheco o item '" + args[1] + "'. Use o TAB para completar.", ALERTA));
            return;
        }

        Integer quantidade = lerInteiro(jogador, args[2], "quantidade");
        if (quantidade == null) {
            return;
        }

        Long preco = lerLongo(jogador, args[3], "preco");
        if (preco == null) {
            return;
        }

        EncomendaCatalog cfg = plugin.encomendaCatalog();
        EconomyCatalog eco = plugin.economyCatalog();
        EncomendaLivro livro = plugin.encomendaLivro();

        EncomendaCatalog.Recusa recusa = cfg.validar(
                preco, quantidade, livro.abertasDe(jogador.getUniqueId()));

        if (recusa != EncomendaCatalog.Recusa.OK) {
            jogador.sendMessage(Component.text(motivo(recusa, cfg, eco), ALERTA));
            return;
        }

        if (!plugin.economyBridge().available()) {
            jogador.sendMessage(Component.text(
                    "A economia nao respondeu. Avise o dono do servidor.", ALERTA));
            return;
        }

        // O saque vem ANTES de gravar o pedido. Se gravasse primeiro, um saque
        // recusado deixaria no quadro uma encomenda sem lastro, e o entregador
        // receberia a promessa de um dinheiro que nunca saiu de ninguem.
        if (!plugin.economyBridge().withdraw(jogador, preco)) {
            jogador.sendMessage(Component.text(
                    "Saldo insuficiente: a encomenda custa " + eco.money(preco)
                            + " e e cobrada agora.", ALERTA));
            return;
        }

        Encomenda pedido = livro.criar(jogador.getUniqueId(), jogador.getName(),
                item, quantidade, preco);

        if (pedido == null) {
            plugin.economyBridge().deposit(jogador, preco);
            jogador.sendMessage(Component.text(
                    "Nao consegui gravar o pedido. O dinheiro foi devolvido.", ALERTA));
            return;
        }

        jogador.sendMessage(Component.text("Encomenda #" + pedido.id() + " aberta: ", FEITO)
                .append(Component.text(quantidade + "x " + nome(item), VALOR))
                .append(Component.text(" por ", TEXTO))
                .append(Component.text(eco.money(preco), DESTAQUE))
                .append(Component.text(".", TEXTO)));
        jogador.sendMessage(Component.text(
                "O valor ja saiu da sua conta e esta guardado.", TEXTO));

        anunciar(jogador, pedido, eco);
    }

    /**
     * Avisa quem esta online que ha trabalho novo.
     *
     * Um quadro que ninguem ve e um quadro que ninguem usa — e esse foi
     * exatamente o destino das placas de loja. O aviso e a diferenca entre um
     * pedido que some e um pedido que vira trabalho.
     */
    private void anunciar(Player autor, Encomenda pedido, EconomyCatalog eco) {
        Component aviso = Component.text("▎ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(autor.getName(), DESTAQUE))
                .append(Component.text(" quer ", TEXTO))
                .append(Component.text(pedido.quantidade() + "x " + nome(pedido.item()), VALOR))
                .append(Component.text(" por ", TEXTO))
                .append(Component.text(eco.money(pedido.preco()), DESTAQUE))
                .append(Component.text(" — /encomenda entregar " + pedido.id(), COMANDO));

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.getUniqueId().equals(autor.getUniqueId())
                    && online.hasPermission(PERM_USO)) {
                online.sendMessage(aviso);
            }
        }
    }

    // ------------------------------------------------------------------
    // Listas
    // ------------------------------------------------------------------

    private void lista(Player jogador) {
        List<Encomenda> abertas = plugin.encomendaLivro().abertas();
        EconomyCatalog eco = plugin.economyCatalog();

        cabecalho(jogador, "ENCOMENDAS ABERTAS");
        if (abertas.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Nenhuma no momento. Abra a sua com /encomenda criar.", TEXTO));
            return;
        }

        for (Encomenda pedido : abertas) {
            jogador.sendMessage(linhaPedido(pedido, eco, true));
        }
    }

    private void minhas(Player jogador) {
        List<Encomenda> minhas = plugin.encomendaLivro().de(jogador.getUniqueId());
        EconomyCatalog eco = plugin.economyCatalog();

        cabecalho(jogador, "SEUS PEDIDOS");
        if (minhas.isEmpty()) {
            jogador.sendMessage(Component.text("Voce nao tem pedido aberto.", TEXTO));
            return;
        }

        long guardado = 0;
        for (Encomenda pedido : minhas) {
            jogador.sendMessage(linhaPedido(pedido, eco, false));
            guardado += pedido.preco();
        }

        jogador.sendMessage(Component.text("Em custodia: ", TEXTO)
                .append(Component.text(eco.money(guardado), DESTAQUE)));
    }

    private Component linhaPedido(Encomenda pedido, EconomyCatalog eco, boolean comDono) {
        Component linha = Component.text("  #" + pedido.id() + " ", DESTAQUE)
                .append(Component.text(pedido.quantidade() + "x " + nome(pedido.item()), VALOR))
                .append(Component.text(" — ", TEXTO))
                .append(Component.text(eco.money(pedido.preco()), DESTAQUE));

        if (comDono) {
            linha = linha.append(Component.text(" — pedido por ", TEXTO))
                    .append(Component.text(pedido.donoNome(), VALOR));
        }
        return linha;
    }

    // ------------------------------------------------------------------
    // Entregar
    // ------------------------------------------------------------------

    private void entregar(Player jogador, String label, String[] args) {
        Encomenda pedido = lerPedido(jogador, label, args, "entregar");
        if (pedido == null) {
            return;
        }

        EconomyCatalog eco = plugin.economyCatalog();

        if (pedido.ehDono(jogador.getUniqueId())) {
            jogador.sendMessage(Component.text(
                    "Esse pedido e seu. Entregar para si mesmo so devolveria o "
                            + "proprio dinheiro — use /" + label + " cancelar " + pedido.id()
                            + ".", ALERTA));
            return;
        }

        int tem = contar(jogador, pedido.item());
        if (tem < pedido.quantidade()) {
            jogador.sendMessage(Component.text("Faltam itens: voce tem " + tem
                    + " de " + pedido.quantidade() + " " + nome(pedido.item()) + ".", ALERTA));
            return;
        }

        if (!plugin.economyBridge().available()) {
            jogador.sendMessage(Component.text(
                    "A economia nao respondeu. Nada foi tirado da sua mochila.", ALERTA));
            return;
        }

        List<ItemStack> retirados = retirarDaMochila(jogador, pedido.item(), pedido.quantidade());

        // A partir daqui o item ja saiu da mochila. Toda falha abaixo tem de
        // devolver, e por isso a ordem e: tirar item, guardar na caixa, pagar,
        // fechar o pedido. Qualquer passo que falhe desfaz o anterior.
        if (!plugin.encomendaLivro().guardarEntrega(pedido.dono(), retirados)) {
            devolver(jogador, retirados);
            jogador.sendMessage(Component.text(
                    "Nao consegui guardar a entrega. Seus itens voltaram.", ALERTA));
            return;
        }

        if (!plugin.economyBridge().deposit(jogador, pedido.preco())) {
            List<ItemStack> caixa = plugin.encomendaLivro().entregasDe(pedido.dono());
            for (ItemStack devolvido : retirados) {
                caixa.remove(devolvido);
            }
            plugin.encomendaLivro().definirEntregas(pedido.dono(), caixa);
            devolver(jogador, retirados);
            jogador.sendMessage(Component.text(
                    "O pagamento falhou. Seus itens voltaram e o pedido continua aberto.",
                    ALERTA));
            return;
        }

        plugin.encomendaLivro().remover(pedido.id());

        jogador.sendMessage(Component.text("Entregue. Voce recebeu ", FEITO)
                .append(Component.text(eco.money(pedido.preco()), DESTAQUE))
                .append(Component.text(".", TEXTO)));

        Player dono = plugin.getServer().getPlayer(pedido.dono());
        if (dono != null && dono.isOnline()) {
            dono.sendMessage(Component.text("▎ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(jogador.getName(), DESTAQUE))
                    .append(Component.text(" entregou sua encomenda #" + pedido.id()
                            + ". Use ", TEXTO))
                    .append(Component.text("/encomenda retirar", COMANDO)));
        }
    }

    // ------------------------------------------------------------------
    // Retirar
    // ------------------------------------------------------------------

    private void retirar(Player jogador) {
        EncomendaLivro livro = plugin.encomendaLivro();
        List<ItemStack> caixa = livro.entregasDe(jogador.getUniqueId());

        if (caixa.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Nao ha nada para retirar.", TEXTO));
            return;
        }

        Map<Integer, ItemStack> sobrou = jogador.getInventory()
                .addItem(caixa.toArray(new ItemStack[0]));

        List<ItemStack> restantes = new ArrayList<>(sobrou.values());
        livro.definirEntregas(jogador.getUniqueId(), restantes);

        int entregues = caixa.size() - restantes.size();
        jogador.sendMessage(Component.text(
                entregues + " pilha(s) foram para a sua mochila.", FEITO));

        if (!restantes.isEmpty()) {
            jogador.sendMessage(Component.text(
                    restantes.size() + " pilha(s) nao couberam e continuam guardadas. "
                            + "Abra espaco e use /encomenda retirar de novo.", TEXTO));
        }
    }

    // ------------------------------------------------------------------
    // Cancelar
    // ------------------------------------------------------------------

    private void cancelar(Player jogador, String label, String[] args) {
        Encomenda pedido = lerPedido(jogador, label, args, "cancelar");
        if (pedido == null) {
            return;
        }

        boolean admin = jogador.hasPermission(PERM_ADMIN);
        if (!pedido.ehDono(jogador.getUniqueId()) && !admin) {
            jogador.sendMessage(Component.text(
                    "Esse pedido nao e seu.", ALERTA));
            return;
        }

        EconomyCatalog eco = plugin.economyCatalog();

        // Devolve primeiro, fecha depois. Se fechasse antes e o deposito
        // falhasse, o dinheiro teria sumido junto com o pedido.
        if (!plugin.economyBridge().deposit(
                plugin.getServer().getOfflinePlayer(pedido.dono()), pedido.preco())) {
            jogador.sendMessage(Component.text(
                    "Nao consegui devolver o dinheiro. O pedido continua aberto.", ALERTA));
            return;
        }

        if (!plugin.encomendaLivro().remover(pedido.id())) {
            jogador.sendMessage(Component.text(
                    "O dinheiro voltou, mas o pedido nao saiu do quadro. Avise o dono.",
                    ALERTA));
            return;
        }

        jogador.sendMessage(Component.text("Pedido #" + pedido.id() + " cancelado. ", FEITO)
                .append(Component.text(eco.money(pedido.preco()) + " devolvido a "
                        + pedido.donoNome() + ".", TEXTO)));
    }

    // ------------------------------------------------------------------
    // Mochila
    // ------------------------------------------------------------------

    /**
     * So conta item limpo.
     *
     * Um pedido diz "64 picaretas de ferro" e nao tem como dizer "inteiras e
     * sem encantamento". Aceitar item com meta deixaria alguem pagar por
     * ferramenta nova e receber sucata — e o contrario tambem: alguem perderia
     * sem querer uma picareta encantada entregando um pedido.
     */
    static boolean limpo(ItemStack pilha, Material item) {
        return pilha != null && pilha.getType() == item && !pilha.hasItemMeta();
    }

    private int contar(Player jogador, Material item) {
        int total = 0;
        for (ItemStack pilha : jogador.getInventory().getStorageContents()) {
            if (limpo(pilha, item)) {
                total += pilha.getAmount();
            }
        }
        return total;
    }

    private List<ItemStack> retirarDaMochila(Player jogador, Material item, int quantidade) {
        List<ItemStack> tirados = new ArrayList<>();
        int faltam = quantidade;

        ItemStack[] conteudo = jogador.getInventory().getStorageContents();
        for (int i = 0; i < conteudo.length && faltam > 0; i++) {
            ItemStack pilha = conteudo[i];
            if (!limpo(pilha, item)) {
                continue;
            }

            int usar = Math.min(faltam, pilha.getAmount());
            ItemStack copia = pilha.clone();
            copia.setAmount(usar);
            tirados.add(copia);

            if (usar == pilha.getAmount()) {
                conteudo[i] = null;
            } else {
                pilha.setAmount(pilha.getAmount() - usar);
            }
            faltam -= usar;
        }

        jogador.getInventory().setStorageContents(conteudo);
        return tirados;
    }

    private void devolver(Player jogador, List<ItemStack> pilhas) {
        Map<Integer, ItemStack> sobrou = jogador.getInventory()
                .addItem(pilhas.toArray(new ItemStack[0]));
        for (ItemStack perdido : sobrou.values()) {
            jogador.getWorld().dropItemNaturally(jogador.getLocation(), perdido);
        }
    }

    // ------------------------------------------------------------------
    // Leitura de argumento
    // ------------------------------------------------------------------

    private Encomenda lerPedido(Player jogador, String label, String[] args, String sub) {
        if (args.length < 2) {
            jogador.sendMessage(Component.text(
                    "Use /" + label + " " + sub + " <numero do pedido>.", ALERTA));
            return null;
        }

        Integer id = lerInteiro(jogador, args[1], "numero do pedido");
        if (id == null) {
            return null;
        }

        return plugin.encomendaLivro().buscar(id).orElseGet(() -> {
            jogador.sendMessage(Component.text(
                    "Nao existe pedido #" + id + ". Veja /" + label + " lista.", ALERTA));
            return null;
        });
    }

    private Integer lerInteiro(Player jogador, String bruto, String campo) {
        try {
            return Integer.parseInt(bruto);
        } catch (NumberFormatException erro) {
            jogador.sendMessage(Component.text(
                    "'" + bruto + "' nao e um numero valido para " + campo + ".", ALERTA));
            return null;
        }
    }

    private Long lerLongo(Player jogador, String bruto, String campo) {
        try {
            return Long.parseLong(bruto);
        } catch (NumberFormatException erro) {
            jogador.sendMessage(Component.text(
                    "'" + bruto + "' nao e um numero valido para " + campo + ".", ALERTA));
            return null;
        }
    }

    private String motivo(EncomendaCatalog.Recusa recusa, EncomendaCatalog cfg,
                          EconomyCatalog eco) {
        return switch (recusa) {
            case PRECO_BAIXO -> "O preco minimo e " + eco.money(cfg.precoMinimo()) + ".";
            case PRECO_ALTO -> "O preco maximo e " + eco.money(cfg.precoMaximo()) + ".";
            case QUANTIDADE_BAIXA -> "A quantidade precisa ser pelo menos 1.";
            case QUANTIDADE_ALTA -> "A quantidade maxima e " + cfg.quantidadeMaxima() + ".";
            case LIMITE_DE_PEDIDOS -> "Voce ja tem " + cfg.limitePorJogador()
                    + " pedidos abertos. Cancele ou espere entregarem.";
            case OK -> "";
        };
    }

    private static String nome(Material item) {
        return item.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void cabecalho(Player jogador, String titulo) {
        jogador.sendMessage(Component.text("▎ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(titulo, DESTAQUE).decorate(TextDecoration.BOLD)));
    }

    private void linhaAjuda(Player jogador, String comando, String argumento,
                            String explicacao) {
        Component linha = Component.text("  " + comando, COMANDO);
        if (argumento != null) {
            linha = linha.append(Component.text(" " + argumento, VALOR));
        }
        jogador.sendMessage(linha.append(Component.text("  " + explicacao, TEXTO)));
    }

    // ------------------------------------------------------------------
    // Autocomplete
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        if (!(sender instanceof Player jogador) || !sender.hasPermission(PERM_USO)) {
            return List.of();
        }

        if (args.length == 1) {
            return filtrar(SUBCOMANDOS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && sub.equals("criar")) {
            List<String> itens = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.isItem() && !material.isLegacy()) {
                    itens.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
            return filtrar(itens, args[1]);
        }

        if (args.length == 2 && (sub.equals("entregar") || sub.equals("cancelar"))) {
            List<String> ids = new ArrayList<>();
            for (Encomenda pedido : plugin.encomendaLivro().abertas()) {
                boolean meu = pedido.ehDono(jogador.getUniqueId());
                boolean serve = sub.equals("cancelar") ? meu : !meu;
                if (serve || jogador.hasPermission(PERM_ADMIN)) {
                    ids.add(String.valueOf(pedido.id()));
                }
            }
            return filtrar(ids, args[1]);
        }

        return List.of();
    }

    private List<String> filtrar(List<String> fonte, String prefixo) {
        String alvo = prefixo.toLowerCase(Locale.ROOT);
        List<String> saida = new ArrayList<>();
        for (String opcao : fonte) {
            if (opcao.startsWith(alvo)) {
                saida.add(opcao);
            }
        }
        return saida;
    }
}
