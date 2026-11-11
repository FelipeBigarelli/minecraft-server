package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
    static final String PERM_TELEPORTE_IMEDIATO = "bigacore.terreno.teleporte.imediato";

    private static final List<String> SUBCOMANDOS = List.of(
            "preco", "comprar", "teleportar", "expandir", "vender", "info",
            "listar", "ver", "cerca", "convidar", "remover");

    //
    // Paleta. O cinza-escuro saiu do corpo das mensagens: #555555 em cima de
    // grama clara e ceu e praticamente invisivel, e era com ele que estavam
    // escritas as explicacoes de todo comando. Sobrou so na barra, que e um
    // tracinho e nao precisa ser lida.
    //
    private static final TextColor COMANDO = NamedTextColor.YELLOW;
    private static final TextColor TEXTO = NamedTextColor.GRAY;
    private static final TextColor DESTAQUE = NamedTextColor.GOLD;
    private static final TextColor VALOR = NamedTextColor.WHITE;
    private static final TextColor ALERTA = NamedTextColor.RED;
    private static final TextColor FEITO = NamedTextColor.GREEN;

    private final BigaCore plugin;
    private final TerrenoViagem viagem;

    public TerrenoCommand(BigaCore plugin) {
        this.plugin = plugin;
        this.viagem = new TerrenoViagem(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text(
                    "/terreno so funciona dentro do jogo — ele depende de onde voce esta.",
                    ALERTA));
            return true;
        }

        if (!jogador.hasPermission(PERM_USO)) {
            jogador.sendMessage(Component.text(
                    "Voce nao tem permissao para comprar terreno.", ALERTA));
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
            case "teleportar", "ir", "casa", "voltar" -> teleportar(jogador, label, args);
            case "info" -> info(jogador);
            case "listar" -> listar(jogador);
            case "ver" -> ver(jogador);
            case "cerca" -> cerca(jogador);
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

        cabecalho(jogador, "TERRENO NO SURVIVAL");
        jogador.sendMessage(Component.text("Voce marca um quadrado em volta de onde esta "
                + "e paga pelo tamanho.", TEXTO));
        jogador.sendMessage(Component.empty());

        linhaAjuda(jogador, "/" + label + " preco", "<tamanho>", "quanto custa");
        linhaAjuda(jogador, "/" + label + " comprar", "<tamanho>", "compra o quadrado");
        linhaAjuda(jogador, "/" + label + " teleportar", "<numero>", "volta para o seu");
        linhaAjuda(jogador, "/" + label + " ver", null, "acende as bordas");
        linhaAjuda(jogador, "/" + label + " cerca", null, "refaz a cerca de madeira");
        linhaAjuda(jogador, "/" + label + " info", null, "de quem e este chao");
        linhaAjuda(jogador, "/" + label + " listar", null, "os seus terrenos");
        linhaAjuda(jogador, "/" + label + " expandir", "<tamanho>", "aumenta o seu");
        linhaAjuda(jogador, "/" + label + " convidar", "<jogador>", "libera alguem");
        linhaAjuda(jogador, "/" + label + " remover", "<jogador>", "tira a permissao");
        linhaAjuda(jogador, "/" + label + " vender", null,
                "devolve por " + cfg.reembolsoPercent() + "%");

        jogador.sendMessage(Component.empty());
        campo(jogador, "Preco", eco.money(cfg.precoPorBloco()) + " por bloco de chao");
        campo(jogador, "Tamanho", "de " + cfg.tamanhoMinimo() + "x" + cfg.tamanhoMinimo()
                + " a " + cfg.tamanhoMaximo() + "x" + cfg.tamanhoMaximo());
        campo(jogador, "Limite", cfg.limitePorJogador() + " por pessoa");
        jogador.sendMessage(Component.text("Vai do fundo ao teto do mundo: "
                + "ninguem cava por baixo.", TEXTO));
    }

    /** Cabecalho no tema do servidor: barra fina, titulo em dourado. */
    private void cabecalho(Player jogador, String titulo) {
        jogador.sendMessage(Component.text("▎ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(titulo, DESTAQUE).decorate(TextDecoration.BOLD)));
    }

    /**
     * Uma linha de ajuda: comando em amarelo, argumento em branco, explicacao
     * em cinza claro.
     *
     * O comando precisa saltar da explicacao, e nao o contrario — quem abre a
     * ajuda esta procurando o que digitar. Antes era o oposto: comando em
     * cinza, explicacao em cinza-escuro, e as duas sumiam no mato.
     */
    private void linhaAjuda(Player jogador, String comando, String argumento,
                            String explicacao) {
        Component linha = Component.text("  " + comando, COMANDO);
        if (argumento != null) {
            linha = linha.append(Component.text(" " + argumento, VALOR));
        }
        jogador.sendMessage(linha.append(Component.text("  " + explicacao, TEXTO)));
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
            jogador.sendMessage(Component.text(orcamento.motivo(), ALERTA));
            if (!orcamento.colisoes().isEmpty()) {
                jogador.sendMessage(Component.text("Encosta em: "
                        + String.join(", ", orcamento.colisoes()), TEXTO));
            }
            return;
        }

        Caixa caixa = orcamento.caixa();
        cabecalho(jogador, "ORCAMENTO");
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
                + tamanho + " CONFIRMAR", TEXTO));
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
                jogador.sendMessage(Component.text(orcamento.motivo(), ALERTA));
                if (!orcamento.colisoes().isEmpty()) {
                    jogador.sendMessage(Component.text("Encosta em: "
                            + String.join(", ", orcamento.colisoes()), TEXTO));
                }
                return;
            }
            jogador.sendMessage(Component.text("Vai comprar " + tamanho + "x" + tamanho
                    + " por " + plugin.economyCatalog().money(orcamento.preco()) + ".",
                    TEXTO));
            jogador.sendMessage(Component.text("Confirme: /" + label + " comprar "
                    + tamanho + " CONFIRMAR", DESTAQUE));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().comprar(jogador, tamanho);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), ALERTA));
            return;
        }

        jogador.sendMessage(Component.text("Terreno seu. ", FEITO)
                .append(Component.text(tamanho + "x" + tamanho + " por "
                        + plugin.economyCatalog().money(resultado.valor()), TEXTO)));
        jogador.sendMessage(Component.text("Regiao " + resultado.id()
                + ". Use /" + label + " ver para enxergar as bordas.", TEXTO));
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
                    TEXTO));
            jogador.sendMessage(Component.text("Confirme: /" + label + " expandir "
                    + tamanho + " CONFIRMAR", DESTAQUE));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().expandir(jogador, tamanho);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), ALERTA));
            return;
        }

        jogador.sendMessage(Component.text(resultado.mensagem() + " ", FEITO)
                .append(Component.text("Custou "
                        + plugin.economyCatalog().money(resultado.valor()), TEXTO)));
        desenharBordas(jogador);
    }

    // ------------------------------------------------------------------
    // Venda
    // ------------------------------------------------------------------

    private void vender(Player jogador, String label, String[] args) {
        if (!confirmou(args, 1)) {
            Optional<Terreno> talvez = plugin.terrenoVenda().onde(jogador);
            if (talvez.isEmpty()) {
                jogador.sendMessage(Component.text(
                        "Fique dentro do terreno que voce quer vender.", ALERTA));
                return;
            }
            TerrenoCatalog cfg = plugin.terrenoCatalog();
            long volta = TerrenoCatalog.reembolso(talvez.get().caixa().area(),
                    cfg.precoPorBloco(), cfg.reembolsoPercent());
            jogador.sendMessage(Component.text("Vender " + talvez.get().id() + " devolve "
                    + plugin.economyCatalog().money(volta) + " ("
                    + cfg.reembolsoPercent() + "% do preco base).", TEXTO));
            jogador.sendMessage(Component.text("A protecao sai e qualquer um pode construir "
                    + "ali. Nao da para desfazer.", ALERTA));
            jogador.sendMessage(Component.text("Confirme: /" + label + " vender CONFIRMAR",
                    DESTAQUE));
            return;
        }

        TerrenoVenda.Resultado resultado = plugin.terrenoVenda().vender(jogador);
        if (!resultado.ok()) {
            jogador.sendMessage(Component.text(resultado.mensagem(), ALERTA));
            return;
        }

        jogador.sendMessage(Component.text("Terreno vendido. ", FEITO)
                .append(Component.text("Voltaram "
                        + plugin.economyCatalog().money(resultado.valor()), TEXTO)));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Volta para o terreno
    // ------------------------------------------------------------------

    /**
     * /terreno teleportar [numero]
     *
     * Com um terreno so, o numero e dispensavel: digitar /terreno teleportar e
     * ja chegar e o caminho de quem esta com medo de perder o que carrega.
     *
     * Com mais de um e sem numero, LISTA em vez de escolher. Mandar para o
     * primeiro da lista seria adivinhar, e o jogador so descobriria o erro
     * depois de ja ter sido levado para longe.
     */
    private void teleportar(Player jogador, String label, String[] args) {
        List<TerrenoTeleporte.Destino> meus = TerrenoTeleporte.meusTerrenos(plugin, jogador);

        if (meus.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Voce ainda nao tem terreno para voltar.", TEXTO));
            jogador.sendMessage(Component.text("Compre um com /" + label
                    + " preco 16 e depois /" + label + " comprar 16 CONFIRMAR.", TEXTO));
            return;
        }

        TerrenoTeleporte.Destino alvo;
        if (args.length < 2) {
            if (meus.size() > 1) {
                jogador.sendMessage(Component.text("Voce tem " + meus.size()
                        + " terrenos. Diga para qual:", TEXTO));
                mostrarDestinos(jogador, label, meus);
                return;
            }
            alvo = meus.get(0);
        } else {
            List<String> ids = new ArrayList<>();
            for (TerrenoTeleporte.Destino destino : meus) {
                ids.add(destino.terreno().id());
            }
            Optional<String> escolhido = TerrenoTeleporte.idEscolhido(ids, args[1]);
            if (escolhido.isEmpty()) {
                jogador.sendMessage(Component.text("Nao achei terreno seu chamado "
                        + args[1] + ".", ALERTA));
                mostrarDestinos(jogador, label, meus);
                return;
            }
            alvo = null;
            for (TerrenoTeleporte.Destino destino : meus) {
                if (destino.terreno().id().equals(escolhido.get())) {
                    alvo = destino;
                    break;
                }
            }
            if (alvo == null) {
                return;
            }
        }

        Caixa caixa = alvo.terreno().caixa();
        Optional<Location> pouso = TerrenoTeleporte.pouso(
                alvo.mundo(), caixa, jogador.getLocation());

        if (pouso.isEmpty()) {
            jogador.sendMessage(Component.text("Nao achei onde te por de pe dentro do "
                    + apelido(jogador, alvo) + " sem te enterrar.", ALERTA));
            jogador.sendMessage(Component.text("Abra um vao de dois blocos de altura "
                    + "em algum ponto do terreno e tente de novo. Ele fica em "
                    + caixa.centroX() + ", " + caixa.centroZ()
                    + " no mundo " + alvo.mundo().getName() + ".", TEXTO));
            return;
        }

        // Quem tem a dispensa vai na hora: a espera existe para o survival, e
        // segurar o dono do servidor cinco segundos a cada teste e so atrito.
        int espera = jogador.hasPermission(PERM_TELEPORTE_IMEDIATO)
                ? 0 : plugin.terrenoCatalog().teleporteEspera();

        viagem.iniciar(jogador, pouso.get(), apelido(jogador, alvo), espera);
    }

    /**
     * Como o terreno aparece nas mensagens.
     *
     * O id da regiao ({@code t_zbigabiga_1}) e nome de banco de dados: ele
     * aparecia inteiro na tela de quem acabou de chegar em casa. O jogador so
     * precisa do numero, que e o mesmo que ele digita no comando.
     *
     * O mundo entra na frase so quando e outro — "seu terreno #1" dito dentro
     * do proprio terreno nao precisa dizer onde fica.
     */
    private String apelido(Player jogador, TerrenoTeleporte.Destino destino) {
        Caixa caixa = destino.terreno().caixa();
        int numero = TerrenoVenda.slotDoId(destino.terreno().id());

        StringBuilder texto = new StringBuilder("seu terreno");
        if (numero > 0) {
            texto.append(" #").append(numero);
        }
        texto.append(" (").append(caixa.larguraX()).append("x")
                .append(caixa.larguraZ()).append(")");
        if (!destino.mundo().equals(jogador.getWorld())) {
            texto.append(", em ").append(destino.mundo().getName());
        }
        return texto.toString();
    }

    /** As opcoes do teleporte, com o numero que o jogador tem de digitar. */
    private void mostrarDestinos(Player jogador, String label,
                                 List<TerrenoTeleporte.Destino> meus) {
        for (TerrenoTeleporte.Destino destino : meus) {
            Caixa caixa = destino.terreno().caixa();
            int numero = TerrenoVenda.slotDoId(destino.terreno().id());
            jogador.sendMessage(Component.text("  /" + label + " teleportar " + numero, COMANDO)
                    .append(Component.text("  " + caixa.larguraX() + "x" + caixa.larguraZ()
                            + " em " + caixa.centroX() + ", " + caixa.centroZ()
                            + " (" + destino.mundo().getName() + ")", TEXTO)));
        }
    }

    private void info(Player jogador) {
        Optional<Terreno> talvez = plugin.terrenoVenda().onde(jogador);
        if (talvez.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Este chao nao e terreno de ninguem.", TEXTO));
            return;
        }

        Terreno terreno = talvez.get();
        Caixa caixa = terreno.caixa();

        cabecalho(jogador, "TERRENO " + terreno.id());
        campo(jogador, "Dono", nomes(terreno.donos()));
        campo(jogador, "Convidados", terreno.membros().isEmpty()
                ? "ninguem" : nomes(terreno.membros()));
        campo(jogador, "Tamanho", caixa.larguraX() + "x" + caixa.larguraZ()
                + " (" + caixa.area() + " blocos)");
        campo(jogador, "Altura", caixa.minY() + " a " + caixa.maxY());
        if (terreno.ehDono(jogador.getUniqueId())) {
            jogador.sendMessage(Component.text("E seu.", FEITO));
        }
    }

    private void listar(Player jogador) {
        List<Terreno> meus = plugin.terrenoVenda().meus(jogador);
        if (meus.isEmpty()) {
            jogador.sendMessage(Component.text("Voce nao tem terreno neste mundo. "
                    + "Use /terreno preco 16 para ver quanto custa.", TEXTO));
            return;
        }

        cabecalho(jogador, "SEUS TERRENOS (" + meus.size() + "/"
                + plugin.terrenoCatalog().limitePorJogador() + ")");
        for (Terreno terreno : meus) {
            Caixa caixa = terreno.caixa();
            jogador.sendMessage(Component.text(terreno.id(), VALOR)
                    .append(Component.text(" " + caixa.larguraX() + "x" + caixa.larguraZ()
                            + " em " + caixa.centroX() + ", " + caixa.centroZ(),
                            TEXTO)));
        }
    }

    private void convidado(Player jogador, String label, String[] args, boolean adicionar) {
        if (args.length < 2) {
            jogador.sendMessage(Component.text("Use /" + label + " "
                    + (adicionar ? "convidar" : "remover") + " <jogador>", ALERTA));
            return;
        }

        OfflinePlayer alvo = procurar(args[1]);
        if (alvo == null) {
            jogador.sendMessage(Component.text("Nunca vi ninguem chamado " + args[1]
                    + " neste servidor.", ALERTA));
            return;
        }

        TerrenoVenda.Resultado resultado =
                plugin.terrenoVenda().convidado(jogador, alvo, adicionar);
        jogador.sendMessage(Component.text(resultado.mensagem(),
                resultado.ok() ? FEITO : ALERTA));
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

    /**
     * Refaz a cerca do terreno onde o jogador esta.
     *
     * Existe porque o chao muda: o jogador aplaina o terreno, abre um fosso,
     * sobe uma plataforma — e a cerca, que foi posta sobre o relevo do dia da
     * compra, fica flutuando ou enterrada. Derruba e levanta de novo em vez de
     * tentar corrigir poste a poste, que daria no mesmo com mais codigo.
     */
    private void cerca(Player jogador) {
        if (!plugin.terrenoCatalog().cercaAtiva()) {
            jogador.sendMessage(Component.text(
                    "A cerca esta desligada no terrenos.yml.", TEXTO));
            return;
        }

        Optional<Terreno> talvez = plugin.terrenoVenda().onde(jogador);
        if (talvez.isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Fique dentro do seu terreno para refazer a cerca.", ALERTA));
            return;
        }

        Terreno terreno = talvez.get();
        if (!terreno.ehDono(jogador.getUniqueId())) {
            jogador.sendMessage(Component.text("Este terreno nao e seu.", ALERTA));
            return;
        }

        jogador.sendMessage(Component.text("Refazendo a cerca...", TEXTO));
        plugin.terrenoVenda().descercar(jogador, terreno.caixa(),
                () -> plugin.terrenoVenda().cercar(jogador, terreno.caixa()));
    }

    // ------------------------------------------------------------------
    // Bordas com particulas
    // ------------------------------------------------------------------

    private void ver(Player jogador) {
        if (plugin.terrenoVenda().onde(jogador).isEmpty()) {
            jogador.sendMessage(Component.text(
                    "Fique dentro de um terreno para ver as bordas.", TEXTO));
            return;
        }
        jogador.sendMessage(Component.text(
                "Bordas marcadas por 10 segundos.", TEXTO));
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
        Optional<Terreno> talvez = plugin.terrenoVenda().onde(jogador);
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
        jogador.sendMessage(Component.text("  " + rotulo + ": ", TEXTO)
                .append(Component.text(valor, VALOR)));
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
                    ALERTA));
            jogador.sendMessage(Component.text("Tamanho e o lado do quadrado, em blocos. "
                    + "Exemplo: 16 faz um 16x16.", TEXTO));
            return null;
        }

        try {
            return Integer.parseInt(args[1].trim());
        } catch (NumberFormatException erro) {
            jogador.sendMessage(Component.text("\"" + args[1] + "\" nao e um numero.",
                    ALERTA));
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
            if (sub.equals("teleportar") || sub.equals("ir")
                    || sub.equals("casa") || sub.equals("voltar")) {
                List<String> numeros = new ArrayList<>();
                for (TerrenoTeleporte.Destino destino
                        : TerrenoTeleporte.meusTerrenos(plugin, jogador)) {
                    numeros.add(String.valueOf(
                            TerrenoVenda.slotDoId(destino.terreno().id())));
                }
                return filtrar(numeros, args[1]);
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
        for (int tamanho : new int[] {8, 12, 16, 24, 32, 48, 64, 80, 100}) {
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
