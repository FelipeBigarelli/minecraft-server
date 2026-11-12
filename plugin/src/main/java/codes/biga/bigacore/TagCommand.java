package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * O comando /tag: comprar, pintar e tirar o apelido da lista do TAB.
 *
 * <h2>Por que uma compra cosmética pede CONFIRMAR</h2>
 *
 * Pelo mesmo motivo de /terreno comprar: o preço é alto o bastante para doer
 * e o comando é fácil de digitar errado. A diferença é que aqui o erro mais
 * provável não é o preço — é o TEXTO. Ver a tag montada, com cor e
 * colchetes, antes de pagar evita a compra de "Rei da Ihla".
 */
public final class TagCommand implements CommandExecutor, TabCompleter {

    static final String PERM_USO = "bigacore.tag";
    static final String PERM_ADMIN = "bigacore.tag.admin";

    private static final List<String> SUBCOMANDOS =
            List.of("comprar", "cor", "cores", "remover", "lista", "ver");

    /** Só aparecem no TAB de quem pode usar. */
    private static final List<String> SUBCOMANDOS_ADMIN = List.of("limpar", "definir");

    /**
     * Teto do texto numa tag dada pela administração.
     *
     * Mais frouxo que o do jogador (o dono às vezes quer "DONO DO SERVIDOR"),
     * mas não ilimitado: a lista do TAB tem largura, e uma tag gigante
     * empurra nome e ping para fora da tela de todo mundo.
     */
    private static final int MAXIMO_ADMIN = 24;

    private static final TextColor COMANDO = NamedTextColor.YELLOW;
    private static final TextColor TEXTO = NamedTextColor.GRAY;
    private static final TextColor DESTAQUE = NamedTextColor.GOLD;
    private static final TextColor VALOR = NamedTextColor.WHITE;
    private static final TextColor ALERTA = NamedTextColor.RED;
    private static final TextColor FEITO = NamedTextColor.GREEN;

    private final BigaCore plugin;

    public TagCommand(BigaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender quem, Command comando, String rotulo, String[] args) {
        if (!quem.hasPermission(PERM_USO)) {
            quem.sendMessage(Component.text("Você não pode usar /tag.", ALERTA));
            return true;
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "" -> status(quem);
            case "comprar" -> comprar(quem, args);
            case "cor" -> cor(quem, args);
            case "cores" -> cores(quem);
            case "remover" -> remover(quem);
            case "lista" -> lista(quem);
            case "ver" -> ver(quem, args);
            case "limpar" -> limpar(quem, args);
            case "definir" -> definir(quem, args);
            default -> status(quem);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void status(CommandSender quem) {
        TagCatalog regras = plugin.tagCatalog();

        quem.sendMessage(Component.empty());
        quem.sendMessage(Component.text("SUA TAG NA LISTA", DESTAQUE));
        quem.sendMessage(Component.text("Aparece do lado do seu nome quando alguém segura TAB.",
                TEXTO));

        if (quem instanceof Player jogador) {
            var minha = plugin.tagStore().de(jogador.getUniqueId());
            if (minha.isPresent()) {
                quem.sendMessage(Component.text("Hoje: ", TEXTO)
                        .append(Component.text("[" + minha.get().texto() + "]", VALOR))
                        .append(Component.text(" em " + minha.get().cor(), TEXTO)));
            } else {
                quem.sendMessage(Component.text("Hoje: ", TEXTO)
                        .append(Component.text("você não tem nenhuma.", VALOR)));
            }
        }

        if (!regras.vendendo()) {
            quem.sendMessage(Component.text("A venda de tag está desligada agora.", TEXTO));
            return;
        }

        String moeda = plugin.economyCatalog().currencySymbol();
        quem.sendMessage(Component.empty());
        quem.sendMessage(Component.text("/tag comprar <texto>", COMANDO)
                .append(Component.text(" — " + moeda + regras.preco()
                        + " (trocar depois: " + moeda + regras.precoDaTroca() + ")", TEXTO)));
        quem.sendMessage(Component.text("/tag cor <cor>", COMANDO)
                .append(Component.text(" — " + moeda + regras.precoDaCor()
                        + " uma vez, depois à vontade", TEXTO)));
        quem.sendMessage(Component.text("/tag remover", COMANDO)
                .append(Component.text(" — tira a sua, de graça", TEXTO)));
        quem.sendMessage(Component.text("Entre " + regras.minimo() + " e " + regras.maximo()
                + " caracteres. Sem se passar pela equipe.", TEXTO));
        quem.sendMessage(Component.empty());
    }

    private void comprar(CommandSender quem, String[] args) {
        if (!(quem instanceof Player jogador)) {
            quem.sendMessage(Component.text("Só jogador tem tag.", ALERTA));
            return;
        }

        TagCatalog regras = plugin.tagCatalog();
        if (!regras.vendendo()) {
            jogador.sendMessage(Component.text("A venda de tag está desligada.", ALERTA));
            return;
        }

        boolean confirmou = args.length > 1
                && args[args.length - 1].equals("CONFIRMAR");

        int ultimo = confirmou ? args.length - 1 : args.length;
        String texto = String.join(" ", java.util.Arrays.copyOfRange(args, 1, ultimo)).trim();

        TagCatalog.Veredito veredito = regras.conferir(texto);
        if (veredito != TagCatalog.Veredito.OK) {
            jogador.sendMessage(Component.text(
                    TagCatalog.motivo(veredito, regras.minimo(), regras.maximo()), ALERTA));
            return;
        }

        var atual = plugin.tagStore().de(jogador.getUniqueId());
        int preco = atual.isPresent() ? regras.precoDaTroca() : regras.preco();
        String moeda = plugin.economyCatalog().currencySymbol();
        String cor = atual.map(TagStore.Tag::cor).orElse(regras.corPadrao());

        if (!confirmou) {
            jogador.sendMessage(Component.empty());
            jogador.sendMessage(Component.text("Vai ficar assim:", TEXTO));
            jogador.sendMessage(plugin.mini().deserialize(
                    "<dark_gray>[</dark_gray><" + cor + "><texto></" + cor + "><dark_gray>]</dark_gray> <white><nome></white>",
                    Placeholder.unparsed("texto", texto),
                    Placeholder.unparsed("nome", jogador.getName())));
            jogador.sendMessage(Component.text("Custa " + moeda + preco + ".", TEXTO));
            jogador.sendMessage(Component.text("/tag comprar " + texto + " CONFIRMAR", COMANDO));
            jogador.sendMessage(Component.empty());
            return;
        }

        if (preco > 0 && !plugin.economyBridge().withdraw(jogador, preco)) {
            jogador.sendMessage(Component.text("Saldo insuficiente: a tag custa "
                    + moeda + preco + ".", ALERTA));
            return;
        }

        plugin.tagStore().guardar(jogador.getUniqueId(), new TagStore.Tag(
                texto, cor, atual.map(TagStore.Tag::comprouCor).orElse(false),
                System.currentTimeMillis()));

        plugin.listaDeJogadores().atualizar(jogador);
        jogador.sendMessage(Component.text("Tag no ar. Segure TAB para ver.", FEITO));
        plugin.getLogger().info("[tag] " + jogador.getName() + " comprou \"" + texto
                + "\" por " + preco + ".");
    }

    private void cor(CommandSender quem, String[] args) {
        if (!(quem instanceof Player jogador)) {
            quem.sendMessage(Component.text("Só jogador tem tag.", ALERTA));
            return;
        }

        TagCatalog regras = plugin.tagCatalog();
        var atual = plugin.tagStore().de(jogador.getUniqueId());
        if (atual.isEmpty()) {
            jogador.sendMessage(Component.text("Compre uma tag antes: /tag comprar <texto>",
                    ALERTA));
            return;
        }

        if (args.length < 2) {
            cores(quem);
            return;
        }

        String escolhida = args[1].toLowerCase(Locale.ROOT);
        if (!regras.corDeVenda(escolhida)) {
            jogador.sendMessage(Component.text("Essa cor não está liberada. Veja /tag cores.",
                    ALERTA));
            return;
        }

        String moeda = plugin.economyCatalog().currencySymbol();
        boolean jaPagou = atual.get().comprouCor();
        int preco = jaPagou ? 0 : regras.precoDaCor();
        boolean confirmou = args.length > 2 && args[2].equals("CONFIRMAR");

        if (preco > 0 && !confirmou) {
            jogador.sendMessage(Component.text("Escolher cor custa " + moeda + preco
                    + " uma vez só. Depois você troca à vontade.", TEXTO));
            jogador.sendMessage(Component.text("/tag cor " + escolhida + " CONFIRMAR", COMANDO));
            return;
        }

        if (preco > 0 && !plugin.economyBridge().withdraw(jogador, preco)) {
            jogador.sendMessage(Component.text("Saldo insuficiente: " + moeda + preco + ".",
                    ALERTA));
            return;
        }

        plugin.tagStore().guardar(jogador.getUniqueId(), new TagStore.Tag(
                atual.get().texto(), escolhida, true, atual.get().criadaEm()));

        plugin.listaDeJogadores().atualizar(jogador);
        jogador.sendMessage(Component.text("Cor trocada.", FEITO));
    }

    private void cores(CommandSender quem) {
        quem.sendMessage(Component.text("Cores liberadas:", TEXTO));
        for (String cor : plugin.tagCatalog().cores()) {
            quem.sendMessage(plugin.mini().deserialize(
                    "  <" + cor + ">■ " + cor + "</" + cor + ">"));
        }
    }

    private void remover(CommandSender quem) {
        if (!(quem instanceof Player jogador)) {
            return;
        }
        if (plugin.tagStore().de(jogador.getUniqueId()).isEmpty()) {
            jogador.sendMessage(Component.text("Você não tem tag.", TEXTO));
            return;
        }

        plugin.tagStore().remover(jogador.getUniqueId());
        plugin.listaDeJogadores().atualizar(jogador);

        // Sem reembolso, e dito na cara: a surpresa seria pior.
        jogador.sendMessage(Component.text("Tag removida. Não há reembolso — "
                + "comprar de novo custa o preço cheio.", TEXTO));
    }

    private void lista(CommandSender quem) {
        Map<UUID, TagStore.Tag> todas = plugin.tagStore().todas();
        if (todas.isEmpty()) {
            quem.sendMessage(Component.text("Ninguém comprou tag ainda.", TEXTO));
            return;
        }

        quem.sendMessage(Component.text(todas.size() + " tag(s):", DESTAQUE));
        for (Map.Entry<UUID, TagStore.Tag> entrada : todas.entrySet()) {
            OfflinePlayer dono = Bukkit.getOfflinePlayer(entrada.getKey());
            String nome = dono.getName() == null ? entrada.getKey().toString() : dono.getName();
            quem.sendMessage(Component.text("  [" + entrada.getValue().texto() + "] ", VALOR)
                    .append(Component.text(nome, TEXTO)));
        }
    }

    private void ver(CommandSender quem, String[] args) {
        if (args.length < 2) {
            quem.sendMessage(Component.text("/tag ver <jogador>", COMANDO));
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (alvo == null) {
            quem.sendMessage(Component.text("Não conheço esse jogador.", ALERTA));
            return;
        }

        var tag = plugin.tagStore().de(alvo.getUniqueId());
        quem.sendMessage(tag.map(valor -> Component.text(args[1] + ": [" + valor.texto() + "]",
                        TEXTO))
                .orElse(Component.text(args[1] + " não tem tag.", TEXTO)));
    }

    /**
     * Moderação: tira a tag de alguém, online ou não.
     *
     * Existe porque a lista de palavras do tags.yml nunca vai dar conta — ela
     * cobre o óbvio e o resto é humano. Sem este subcomando, tirar uma tag
     * ofensiva exigiria editar YAML e reiniciar o servidor.
     */
    private void limpar(CommandSender quem, String[] args) {
        if (!quem.hasPermission(PERM_ADMIN)) {
            quem.sendMessage(Component.text("Só a administração.", ALERTA));
            return;
        }
        if (args.length < 2) {
            quem.sendMessage(Component.text("/tag limpar <jogador>", COMANDO));
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (alvo == null) {
            quem.sendMessage(Component.text("Não conheço esse jogador.", ALERTA));
            return;
        }

        plugin.tagStore().remover(alvo.getUniqueId());
        Player online = alvo.getPlayer();
        if (online != null) {
            plugin.listaDeJogadores().atualizar(online);
            online.sendMessage(Component.text("Sua tag foi removida pela administração.", ALERTA));
        }

        quem.sendMessage(Component.text("Tag de " + args[1] + " removida.", FEITO));
        plugin.getLogger().info("[tag] " + quem.getName() + " limpou a tag de " + args[1] + ".");
    }

    /**
     * A tag que a administração dá à mão.
     *
     * Existe por causa de um conflito real: "dono", "admin" e "staff" estão
     * na lista de palavras barradas, e é bom que estejam — elas existem para
     * ninguém se passar pela equipe. Mas o dono É a equipe, e sem este
     * caminho a única saída seria tirar a palavra da lista, que é
     * exatamente o que não se quer.
     *
     * Então aqui não passa pelo conferidor de texto, não cobra e aceita cor
     * especial. O que ele NÃO faz é passar por cima do limite de largura:
     * tag comprida empurra nome e ping para fora da lista de todo mundo, e
     * isso vale para o dono também.
     */
    private void definir(CommandSender quem, String[] args) {
        if (!quem.hasPermission(PERM_ADMIN)) {
            quem.sendMessage(Component.text("Só a administração.", ALERTA));
            return;
        }
        if (args.length < 4) {
            quem.sendMessage(Component.text("/tag definir <jogador> <cor> <texto>", COMANDO));
            quem.sendMessage(Component.text("Cores: " + String.join(", ", todasAsCores()), TEXTO));
            return;
        }

        OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (alvo == null) {
            quem.sendMessage(Component.text("Não conheço esse jogador.", ALERTA));
            return;
        }

        String cor = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.tagCatalog().corPermitida(cor)) {
            quem.sendMessage(Component.text("Cor desconhecida: " + cor, ALERTA));
            quem.sendMessage(Component.text("Cores: " + String.join(", ", todasAsCores()), TEXTO));
            return;
        }

        String texto = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (texto.isEmpty() || texto.length() > MAXIMO_ADMIN) {
            quem.sendMessage(Component.text("O texto tem de ter de 1 a "
                    + MAXIMO_ADMIN + " caracteres.", ALERTA));
            return;
        }

        // comprouCor = true: tag dada é presente inteiro, e trocar a cor
        // dela depois não volta a cobrar.
        plugin.tagStore().guardar(alvo.getUniqueId(),
                new TagStore.Tag(texto, cor, true, System.currentTimeMillis()));

        Player online = alvo.getPlayer();
        if (online != null) {
            plugin.listaDeJogadores().atualizar(online);
            online.sendMessage(Component.text("A administração te deu a tag ", FEITO)
                    .append(Component.text("[" + texto + "]", VALOR)));
        }

        quem.sendMessage(Component.text("Tag de " + args[1] + " definida: ", FEITO)
                .append(Component.text("[" + texto + "]", VALOR))
                .append(Component.text(" em " + cor, TEXTO)));
        plugin.getLogger().info("[tag] " + quem.getName() + " definiu a tag de "
                + args[1] + " como '" + texto + "' (" + cor + ").");
    }

    private List<String> todasAsCores() {
        List<String> todas = new ArrayList<>(plugin.tagCatalog().cores());
        todas.addAll(plugin.tagCatalog().coresEspeciais());
        return todas;
    }

    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender quem, Command comando,
                                      String rotulo, String[] args) {
        if (args.length == 1) {
            List<String> tudo = new ArrayList<>(SUBCOMANDOS);
            if (quem.hasPermission(PERM_ADMIN)) {
                tudo.addAll(SUBCOMANDOS_ADMIN);
            }
            return filtrar(tudo, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cor")) {
            return filtrar(plugin.tagCatalog().cores(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("definir")
                && quem.hasPermission(PERM_ADMIN)) {
            return filtrar(todasAsCores(), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ver")
                || args[0].equalsIgnoreCase("limpar")
                || args[0].equalsIgnoreCase("definir"))) {
            List<String> nomes = new ArrayList<>();
            for (Player jogador : Bukkit.getOnlinePlayers()) {
                nomes.add(jogador.getName());
            }
            return filtrar(nomes, args[1]);
        }
        return List.of();
    }

    private List<String> filtrar(List<String> fonte, String prefixo) {
        String alvo = prefixo.toLowerCase(Locale.ROOT);
        List<String> saida = new ArrayList<>();
        for (String item : fonte) {
            if (item.toLowerCase(Locale.ROOT).startsWith(alvo)) {
                saida.add(item);
            }
        }
        return saida;
    }
}
