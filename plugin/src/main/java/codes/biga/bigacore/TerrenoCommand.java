package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * O comando /terreno.
 *
 * Mesma divisao do {@link BigaCommand}: executor e tab completer na mesma
 * classe, para o autocomplete nao sair de sincronia com os subcomandos.
 *
 * Toda acao que gasta dinheiro ou mexe em regiao exige a palavra CONFIRMAR,
 * igual ao /biga painel construir. Nao e burocracia: /terreno comprar 64
 * digitado por engano gastaria doze mil moedas sem pergunta nenhuma.
 */
public final class TerrenoCommand implements CommandExecutor, TabCompleter {

    static final String PERM_USO = "bigacore.terreno";

    private static final List<String> SUBCOMANDOS = List.of(
            "preco", "comprar", "expandir", "vender", "info", "listar",
            "ver", "convidar", "remover");

    private final BigaCore plugin;

    public TerrenoCommand(BigaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text(
                    "/terreno so funciona dentro do jogo — ele depende de onde voce esta.",
                    NamedTextColor.RED));
            return true;
        }

        if (!jogador.hasPermission(PERM_USO)) {
            jogador.sendMessage(Component.text(
                    "Voce nao tem permissao para comprar terreno.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            ajuda(jogador, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "preco", "preço" -> preco(jogador, label, args);
            case "comprar" -> comprar(jogador, label, args);
            case "expandir" -> expandir(jogador, label, args);
            case "vender" -> vender(jogador, label, args);
            case "info" -> info(jogador);
            case "listar" -> listar(jogador);
            case "ver" -> ver(jogador);
            case "convidar" -> convidado(jogador, label, args, true);
            case "remover" -> convidado(jogador, label, args, false);
            default -> ajuda(jogador, label);
        }

        return true;
    }

    // ------------------------------------------------------------------
    // Ajuda
    // ------------------------------------------------------------------

    private void ajuda(Player jogador, String label) {
        TerrenoCatalog cfg = plugin.terrenoCatalog();
        EconomyCatalog eco = plugin.economyCatalog();

        jogador.sendMessage(Component.text("TERRENO NO SURVIVAL", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        jogador.sendMessage(Component.text("Voce marca um quadrado em volta de onde esta "
                + "e paga pelo tamanho.", NamedTextColor.GRAY));
        jogador.sendMessage(Component.empty());

        linhaAjuda(jogador, "/" + label + " preco <tamanho>", "quanto custa, sem comprar");
        linhaAjuda(jogador, "/" + label + " comprar <tamanho>", "compra o quadrado");
        linhaAjuda(jogador, "/" + label + " ver", "mostra as bordas com particulas");
        linhaAjuda(jogador, "/" + label + " info", "de quem e o chao onde voce esta");
        linhaAjuda(jogador, "/" + label + " listar", "seus terrenos neste mundo");
        linhaAjuda(jogador, "/" + label + " expandir <tamanho>", "aumenta o terreno de baixo dos pes");
        linhaAjuda(jogador, "/" + label + " convidar <jogador>", "deixa alguem construir");
        linhaAjuda(jogador, "/" + label + " remover <jogador>", "tira a permissao de alguem");
        linhaAjuda(jogador, "/" + label + " vender", "devolve o terreno por "
                + cfg.reembolsoPercent() + "% do preco base");

        jogador.sendMessage(Component.empty());
        jogador.sendMessage(Component.text("Preco: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(eco.money(cfg.precoPorBloco()) + " por bloco de chao",
                        NamedTextColor.GRAY)));
        jogador.sendMessage(Component.text("Tamanho: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("de " + cfg.tamanhoMinimo() + "x" + cfg.tamanhoMinimo()
                        + " a " + cfg.tamanhoMaximo() + "x" + cfg.tamanhoMaximo(),
                        NamedTextColor.GRAY)));
        jogador.sendMessage(Component.text("Limite: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(cfg.limitePorJogador() + " terreno(s) por pessoa",
                        NamedTextColor.GRAY)));
        jogador.sendMessage(Component.text("O terreno pega do fundo ao teto do mundo, "
                + "entao ninguem cava por baixo.", NamedTextColor.DARK_GRAY));
    }

    private void linhaAjuda(Player jogador, String comando, String explicacao) {
        jogador.sendMessage(Component.text(comando, NamedTextColor.GRAY)
                .append(Component.text(" — " + explicacao, NamedTextColor.DARK_GRAY)));
    }

    // ------------------------------------------------------------------
    // Preco
    // ------------------------------------------------------------------

    private void preco(Player jogador, String label, String[] args) {
        Integer tamanho = lerTamanho(jogador, label, args, "preco");
        if (tamanho == null) {
            return;
        }

        TerrenoVenda.Orcamento orcamento = plugin.terrenoVenda().orcar(jogador, tamanho);
        EconomyCatalog eco = plugin.economyCatalog();

        if (!orcamento.ok()) {
            jogador.sendMessage(Component.text(orcamento.motivo(), NamedTextColor.RED));
            if (!orcamento.colisoes().isEmpty()) {
                jogador.sendMessage(Component.text("Encosta em: "
                        + String.join(", ", orcamento.colisoes()), NamedTextColor.DARK_GRAY));
            }
            return;
        }

        Caixa caixa = orcamento.caixa();
        jogador.sendMessage(Component.text("ORCAMENTO", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        campo(jogador, "Tamanho", tamanho + "x" + tamanho + " (" + caixa.area() + " blocos)");
        campo(jogador, "Canto a canto",
                caixa.minX() + ", " + caixa.minZ() + " ate " + caixa.maxX() + ", " + caixa.maxZ());
        campo(jogador, "Seu terreno numero", String.valueOf(orcamento.slot()));
        if (orcamento.multiplicador() != 1.0) {
            campo(jogador, "Multiplicador", "x" + orcamento.multiplicador()
                    + " (por ser o " + orcamento.slot() + "o terreno)");
        }
        campo(jogador, "Preco", eco.money(orcamento.preco()));
        jogador.sendMessage(Component.text("Para comprar: /" + label + " comprar "
                + tamanho + " CONFIRMAR", NamedTextColor.DARK_GRAY));
    }

    // ------------------------------------------------------------------
    // Compra
    // ------------------------------------------------------------------

    private void comprar(Player jogador, String label, String[] args) {
        Integer tamanho = lerTamanho(jogador, label, args, "comprar");
        if (tamanho == null) {
            return;
        }

        if (!confirmou(args, 2)) {
            TerrenoVenda.Orcamento orcamento = plugin.terrenoVenda().orcar(jogador, tamanho);
            if (!orcamento.ok()) {
                jogador.sendMessage(Component.text(orcamento.motivo(), NamedTextColor.RED));
                if (!orcamento.colisoes().isEmpty()) {
                    jogador.sendMessage(Component.text("Encosta em: "
                            + String.join(", ", orcamento.colisoes()), NamedTextColor.DARK_GRAY));
                }
                return;
            }
            jogador.sendMessage(Component.text("Vai comprar " + tamanho + "x" + tamanho
                    + " por " + plugin.economyCatalog().money(orcamento.preco()) + ".",
                    NamedTextColor.GRAY));
            jogador.sendMessage(Component.text("Confirme: /" + label + " comprar "
                    + tamanho + " CONFIRMAR", NamedTextColor.GOLD));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().comprar(jogador, tamanho);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), NamedTextColor.RED));
            return;
        }

        jogador.sendMessage(Component.text("Terreno seu. ", NamedTextColor.GREEN)
                .append(Component.text(tamanho + "x" + tamanho + " por "
                        + plugin.economyCatalog().money(resultado.valor()), NamedTextColor.GRAY)));
        jogador.sendMessage(Component.text("Regiao " + resultado.id()
                + ". Use /" + label + " ver para enxergar as bordas.", NamedTextColor.DARK_GRAY));
        desenharBordas(jogador);
    }

    // ------------------------------------------------------------------
    // Expansao
    // ------------------------------------------------------------------

    private void expandir(Player jogador, String label, String[] args) {
        Integer tamanho = lerTamanho(jogador, label, args, "expandir");
        if (tamanho == null) {
            return;
        }

        if (!confirmou(args, 2)) {
            jogador.sendMessage(Component.text("Isso vai aumentar o terreno onde voce esta "
                    + "para " + tamanho + "x" + tamanho + " e cobrar a area nova.",
                    NamedTextColor.GRAY));
            jogador.sendMessage(Component.text("Confirme: /" + label + " expandir "
                    + tamanho + " CONFIRMAR", NamedTextColor.GOLD));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().expandir(jogador, tamanho);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), NamedTextColor.RED));
            return;
        }

        jogador.sendMessage(Component.text(resultado.mensagem() + " ", NamedTextColor.GREEN)
                .append(Component.text("Custou "
                        + plugin.economyCatalog().money(resultado.valor()), NamedTextColor.GRAY)));
        desenharBordas(jogador);
    }

    // ------------------------------------------------------------------
    // Venda
    // ------------------------------------------------------------------

    private void vender(Player jogador, String label, String[] args) {
        if (!confirmou(args, 1)) {
            Optional<WorldGuardBridge.Terreno> talvez = plugin.terrenoVenda().onde(jogador);
            if (talvez.isEmpty()) {
                jogador.sendMessage(Component.text(
                        "Fique dentro do terreno que voce quer vender.", NamedTextColor.RED));
                return;
            }
            TerrenoCatalog cfg = plugin.terrenoCatalog();
            long volta = TerrenoCatalog.reembolso(talvez.get().caixa().area(),
                    cfg.precoPorBloco(), cfg.reembolsoPercent());
            jogador.sendMessage(Component.text("Vender " + talvez.get().id() + " devolve "
                    + plugin.economyCatalog().money(volta) + " ("
                    + cfg.reembolsoPercent() + "% do preco base).", NamedTextColor.GRAY));
            jogador.sendMessage(Component.text("A protecao sai e qualquer um pode construir "
                    + "ali. Nao da para desfazer.", NamedTextColor.RED));
            jogador.sendMessage(Component.text("Confirme: /" + label + " vender CONFIRMAR",
                    NamedTextColor.GOLD));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().vender(jogador);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), NamedTextColor.RED));
            return;
        }

        jogador.sendMessage(Component.text("Terreno vendido. ", NamedTextColor.GREEN)
                .append(Component.text("Voltaram "
                        + plugin.economyCatalog().money(resultado.valor()), NamedTextColor.GRAY)));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    private void info(Player jogador) {
        Optional<WorldGuardBridge.Terreno> talvez = plugin.terrenoVenda().onde(jogador);
        if (talvez.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Este chao nao e terreno de ninguem.", NamedTextColor.GRAY));
            return;
        }

        WorldGuardBridge.Terreno terreno = talvez.get();
        Caixa caixa = terreno.caixa();

        jogador.sendMessage(Component.text("TERRENO " + terreno.id(), NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        campo(jogador, "Dono", nomes(terreno.donos()));
        campo(jogador, "Convidados", terreno.membros().isEmpty()
                ? "ninguem" : nomes(terreno.membros()));
        campo(jogador, "Tamanho", caixa.larguraX() + "x" + caixa.larguraZ()
                + " (" + caixa.area() + " blocos)");
        campo(jogador, "Altura", caixa.minY() + " a " + caixa.maxY());
        if (terreno.ehDono(jogador.getUniqueId())) {
            jogador.sendMessage(Component.text("E seu.", NamedTextColor.GREEN));
        }
    }

    private void listar(Player jogador) {
        List<WorldGuardBridge.Terreno> meus = plugin.terrenoVenda().meus(jogador);
        if (meus.isEmpty()) {
            jogador.sendMessage(Component.text("Voce nao tem terreno neste mundo. "
                    + "Use /terreno preco 16 para ver quanto custa.", NamedTextColor.GRAY));
            return;
        }

        jogador.sendMessage(Component.text("SEUS TERRENOS (" + meus.size() + "/"
                + plugin.terrenoCatalog().limitePorJogador() + ")", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        for (WorldGuardBridge.Terreno terreno : meus) {
            Caixa caixa = terreno.caixa();
            jogador.sendMessage(Component.text(terreno.id(), NamedTextColor.WHITE)
                    .append(Component.text(" " + caixa.larguraX() + "x" + caixa.larguraZ()
                            + " em " + caixa.centroX() + ", " + caixa.centroZ(),
                            NamedTextColor.GRAY)));
        }
    }

    private void convidado(Player jogador, String label, String[] args, boolean adicionar) {
        if (args.length < 2) {
            jogador.sendMessage(Component.text("Use /" + label + " "
                    + (adicionar ? "convidar" : "remover") + " <jogador>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer alvo = procurar(args[1]);
        if (alvo == null) {
            jogador.sendMessage(Component.text("Nunca vi ninguem chamado " + args[1]
                    + " neste servidor.", NamedTextColor.RED));
            return;
        }

        TerrenoVenda.Resultado resultado =
                plugin.terrenoVenda().convidado(jogador, alvo, adicionar);
        jogador.sendMessage(Component.text(resultado.mensagem(),
                resultado.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    /**
     * Acha o jogador pelo nome sem bater no servico da Mojang.
     *
     * getOfflinePlayer(String) resolveria qualquer nome, inclusive um que
     * nunca entrou — e convidar um fantasma guardaria um UUID inutil na
     * regiao. Pior: na versao deprecada ele pode bloquear a thread principal
     * esperando resposta de rede.
     */
    private OfflinePlayer procurar(String nome) {
        Player online = Bukkit.getPlayerExact(nome);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(nome);
    }

    // ------------------------------------------------------------------
    // Bordas com particulas
    // ------------------------------------------------------------------

    private void ver(Player jogador) {
        if (plugin.terrenoVenda().onde(jogador).isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Fique dentro de um terreno para ver as bordas.", NamedTextColor.GRAY));
            return;
        }
        jogador.sendMessage(Component.text(
                "Bordas marcadas por 10 segundos.", NamedTextColor.GRAY));
        desenharBordas(jogador);
    }

    /**
     * Pinta o contorno do terreno so para quem pediu.
     *
     * player.spawnParticle, nao world.spawnParticle: a particula sai no
     * cliente de uma pessoa. Com a versao do mundo, cada /terreno ver de cada
     * jogador mandaria pacote para todo mundo no raio de visao.
     *
     * A altura segue os pes de quem olha a cada repeticao, para a borda
     * aparecer onde ele esta mesmo quando a regiao vai de -64 a 319.
     */
    private void desenharBordas(Player jogador) {
        Optional<WorldGuardBridge.Terreno> talvez = plugin.terrenoVenda().onde(jogador);
        if (talvez.isEmpty()) {
            return;
        }

        Caixa caixa = talvez.get().caixa();
        World mundo = jogador.getWorld();

        new BukkitRunnable() {
            private int restantes = 20;

            @Override
            public void run() {
                if (restantes-- <= 0 || !jogador.isOnline()
                        || !jogador.getWorld().equals(mundo)) {
                    cancel();
                    return;
                }

                double y = jogador.getLocation().getY() + 0.5;

                for (int x = caixa.minX(); x <= caixa.maxX(); x++) {
                    ponto(x, y, caixa.minZ(), Particle.END_ROD);
                    ponto(x, y, caixa.maxZ(), Particle.END_ROD);
                }
                for (int z = caixa.minZ(); z <= caixa.maxZ(); z++) {
                    ponto(caixa.minX(), y, z, Particle.END_ROD);
                    ponto(caixa.maxX(), y, z, Particle.END_ROD);
                }

                // Os quatro cantos sobem uma coluna, para dar para achar a
                // borda de longe sem andar em volta dela.
                for (int altura = 0; altura < 4; altura++) {
                    ponto(caixa.minX(), y + altura, caixa.minZ(), Particle.FLAME);
                    ponto(caixa.minX(), y + altura, caixa.maxZ(), Particle.FLAME);
                    ponto(caixa.maxX(), y + altura, caixa.minZ(), Particle.FLAME);
                    ponto(caixa.maxX(), y + altura, caixa.maxZ(), Particle.FLAME);
                }
            }

            private void ponto(double x, double y, double z, Particle particula) {
                jogador.spawnParticle(particula,
                        new Location(mundo, x + 0.5, y, z + 0.5), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void campo(Player jogador, String rotulo, String valor) {
        jogador.sendMessage(Component.text(rotulo + ": ", NamedTextColor.DARK_GRAY)
                .append(Component.text(valor, NamedTextColor.WHITE)));
    }

    private String nomes(java.util.Collection<java.util.UUID> ids) {
        List<String> saida = new ArrayList<>();
        for (java.util.UUID id : ids) {
            saida.add(TerrenoVenda.nomeDe(Bukkit.getOfflinePlayer(id)));
        }
        return saida.isEmpty() ? "ninguem" : String.join(", ", saida);
    }

    /** Le o tamanho do argumento. Devolve null depois de ja ter avisado o jogador. */
    private Integer lerTamanho(Player jogador, String label, String[] args, String sub) {
        if (args.length < 2) {
            jogador.sendMessage(Component.text("Use /" + label + " " + sub + " <tamanho>",
                    NamedTextColor.RED));
            jogador.sendMessage(Component.text("Tamanho e o lado do quadrado, em blocos. "
                    + "Exemplo: 16 faz um 16x16.", NamedTextColor.DARK_GRAY));
            return null;
        }

        try {
            return Integer.parseInt(args[1].trim());
        } catch (NumberFormatException erro) {
            jogador.sendMessage(Component.text("\"" + args[1] + "\" nao e um numero.",
                    NamedTextColor.RED));
            return null;
        }
    }

    private boolean confirmou(String[] args, int posicao) {
        return args.length > posicao && args[posicao].equalsIgnoreCase("CONFIRMAR");
    }

    // ------------------------------------------------------------------
    // Autocomplete
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {

        if (!(sender instanceof Player jogador) || !sender.hasPermission(PERM_USO)) {
            return List.of();
        }

        if (args.length == 1) {
            return filtrar(SUBCOMANDOS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("preco") || sub.equals("comprar") || sub.equals("expandir")) {
                return filtrar(tamanhosSugeridos(), args[1]);
            }
            if (sub.equals("convidar") || sub.equals("remover")) {
                List<String> nomes = new ArrayList<>();
                for (Player outro : Bukkit.getOnlinePlayers()) {
                    if (!outro.equals(jogador)) {
                        nomes.add(outro.getName());
                    }
                }
                return filtrar(nomes, args[1]);
            }
            if (sub.equals("vender")) {
                return filtrar(List.of("CONFIRMAR"), args[1]);
            }
        }

        if (args.length == 3
                && (sub.equals("comprar") || sub.equals("expandir"))) {
            return filtrar(List.of("CONFIRMAR"), args[2]);
        }

        return List.of();
    }

    /**
     * Tamanhos redondos dentro do que a config permite.
     *
     * Sugerir numero em vez de deixar o campo vazio importa: o jogador que ve
     * 16, 24, 32 entende sozinho que o argumento e o lado do quadrado, nao a
     * area nem o preco.
     */
    private List<String> tamanhosSugeridos() {
        TerrenoCatalog cfg = plugin.terrenoCatalog();
        List<String> saida = new ArrayList<>();
        for (int tamanho : new int[] {8, 12, 16, 24, 32, 48, 64}) {
            if (tamanho >= cfg.tamanhoMinimo() && tamanho <= cfg.tamanhoMaximo()) {
                saida.add(String.valueOf(tamanho));
            }
        }
        return saida;
    }

    private List<String> filtrar(List<String> opcoes, String texto) {
        String prefixo = texto.toLowerCase(Locale.ROOT);
        List<String> saida = new ArrayList<>();
        for (String opcao : opcoes) {
            if (opcao.toLowerCase(Locale.ROOT).startsWith(prefixo)) {
                saida.add(opcao);
            }
        }
        return saida;
    }
}
