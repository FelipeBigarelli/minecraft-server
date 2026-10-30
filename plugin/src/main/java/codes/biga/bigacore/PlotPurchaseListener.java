package codes.biga.bigacore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cobra pelo terreno interceptando os comandos do CreativePlots.
 *
 * <h2>Por que interceptar em vez de integrar</h2>
 *
 * O CreativePlots 5.1 não tem economia, não dispara evento de claim e o
 * {@code PlotAPI} só expõe leitura. Não existe ponto de extensão: ou se forka o
 * plugin, ou se intercepta o comando. Interceptar mantém o jar de terceiro
 * intocado e atualizável.
 *
 * <h2>A ordem importa</h2>
 *
 * <ol>
 *   <li>Antes do comando: confere saldo. Faltando, CANCELA e explica o preço —
 *       é o momento em que o jogador precisa da informação.</li>
 *   <li>Guarda qual terreno ele estava pisando e se já era dono.</li>
 *   <li>Deixa o CreativePlots rodar.</li>
 *   <li>Um tick depois: se ele virou dono de um terreno que não era dele,
 *       cobra. Senão, não cobra nada.</li>
 * </ol>
 *
 * O passo 4 é o que impede cobrança fantasma. Um claim pode falhar por motivos
 * que este listener não conhece — terreno já tomado, limite atingido, mundo
 * errado, flag do plugin — e todos eles terminam com o jogador sem terreno
 * novo. Conferir o fato consumado cobre todos de uma vez, sem reimplementar
 * nenhuma regra do CreativePlots.
 */
public final class PlotPurchaseListener implements Listener {

    /** O comando e o alias declarados no plugin.yml do CreativePlots. */
    private static final Set<String> COMANDOS = Set.of("plot", "plots");

    private static final Set<String> COMPRA = Set.of("claim", "auto");
    private static final String INFO = "info";
    private static final String ABANDONO = "abandon";

    /** Quem tem isto não paga. Não é default op de propósito: veja o plugin.yml. */
    private static final String ISENTO = "bigacore.plot.isento";

    private final BigaCore plugin;

    public PlotPurchaseListener(BigaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoDigitarComando(PlayerCommandPreprocessEvent evento) {
        String[] partes = evento.getMessage().trim().split("\\s+");
        if (partes.length < 2 || partes[0].length() < 2) {
            return;
        }

        String comando = partes[0].substring(1).toLowerCase(Locale.ROOT);
        if (!COMANDOS.contains(comando)) {
            return;
        }

        String sub = partes[1].toLowerCase(Locale.ROOT);
        Player jogador = evento.getPlayer();

        if (INFO.equals(sub)) {
            mostrarPrecoDepoisDoInfo(jogador);
            return;
        }

        if (ABANDONO.equals(sub)) {
            barrarAbandonoProtegido(evento, jogador);
            return;
        }

        if (!COMPRA.contains(sub)) {
            return;
        }

        PlotSale venda = plugin.plotSale();
        if (venda == null || !venda.habilitado() || jogador.hasPermission(ISENTO)) {
            return;
        }

        PlotApiBridge terrenos = plugin.plotApi();
        if (!terrenos.available() || !terrenos.mundoDeTerrenos(jogador.getLocation())) {
            // Fora do mundo de terrenos o próprio CreativePlots recusa com a
            // mensagem dele. Não é nosso papel duplicar isso.
            return;
        }

        int preco = venda.precoProximo(jogador.getUniqueId());
        if (preco <= 0) {
            return;
        }

        double saldo = plugin.economyBridge().balance(jogador).orElse(0.0);
        String moeda = plugin.economyCatalog().currencySymbol();

        if (saldo < preco) {
            evento.setCancelled(true);
            jogador.sendMessage(plugin.mini().deserialize(
                    "<color:#E03131>Terreno custa <color:#FF6A00>" + preco + " " + moeda
                            + "</color> — você tem <color:#FF6A00>" + (long) saldo + " " + moeda
                            + "</color>.</color>"));
            jogador.sendMessage(plugin.mini().deserialize(
                    "<color:#AAAAAA>Venda material para o servidor com <color:#FFD700>/biga eco vender</color>"
                            + " ou negocie com outros jogadores.</color>"));
            return;
        }

        Estado antes = estadoAtual(jogador);

        // Um tick depois: o comando do CreativePlots já terminou e o dono do
        // terreno, se mudou, já está gravado no arquivo dele.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> cobrarSeVirouDono(jogador, antes, preco, moeda));
    }

    /**
     * Recusa o /plot abandon num terreno marcado como protegido.
     *
     * O abandono do CreativePlots não solta a posse: ele chama
     * {@code resetPlotLayer} e REESCREVE os blocos do terreno. Num terreno com
     * a Biga Market dentro, um comando digitado por engano apaga a construção
     * inteira — sem confirmação, sem snapshot, sem volta.
     *
     * A trava é pela posição do jogador, que é como o CreativePlots resolve o
     * terreno quando o comando vem sem argumento. Não cobre uma variante futura
     * que aceite o id do terreno como parâmetro; se isso aparecer, o guard
     * precisa olhar o argumento também.
     */
    private void barrarAbandonoProtegido(PlayerCommandPreprocessEvent evento, Player jogador) {
        PlotSale venda = plugin.plotSale();
        PlotApiBridge terrenos = plugin.plotApi();
        if (venda == null || !terrenos.available()) {
            return;
        }

        Optional<String> terreno = terrenos.terrenoEm(jogador.getLocation());
        if (terreno.isEmpty()) {
            return;
        }

        String mundo = jogador.getWorld().getName();
        if (!venda.protegido(mundo, terreno.get())) {
            return;
        }

        evento.setCancelled(true);
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#E03131>Este terreno está protegido. Abandonar APAGARIA tudo"
                        + " que está construído nele.</color>"));
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#AAAAAA>Se é mesmo isso que você quer:"
                        + " <color:#FFD700>/biga loja desproteger</color> primeiro.</color>"));
    }

    // ------------------------------------------------------------------

    private void cobrarSeVirouDono(Player jogador, Estado antes, int preco, String moeda) {
        if (!jogador.isOnline()) {
            // Saiu no meio. Sem cobrança: o terreno pode ter sido reivindicado,
            // mas cobrar alguém que não pode ver a mensagem é pior que o
            // contrário — o admin corrige na mão se for o caso.
            return;
        }

        Estado agora = estadoAtual(jogador);
        if (!agora.donoEhOJogador()) {
            return;
        }

        // Mudou de terreno, ou continua no mesmo mas antes não era dele.
        boolean terrenoNovo = !agora.terreno().equals(antes.terreno()) || !antes.donoEhOJogador();
        if (!terrenoNovo) {
            return;
        }

        PlotSale.Resultado resultado = plugin.plotSale().cobrar(jogador, preco);
        if (!resultado.sucesso()) {
            jogador.sendMessage(plugin.mini().deserialize(
                    "<color:#E03131>" + resultado.mensagem() + "</color>"));
            return;
        }

        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#FFD700>Terreno comprado por <color:#FF6A00>" + resultado.preco()
                        + " " + moeda + "</color>.</color>"));

        if (resultado.proximo() > 0) {
            jogador.sendMessage(plugin.mini().deserialize(
                    "<color:#AAAAAA>Seu próximo terreno custará <color:#FF6A00>"
                            + resultado.proximo() + " " + moeda + "</color>.</color>"));
        }
    }

    /**
     * Acrescenta o preço à saída do {@code /plot info}.
     *
     * Vai um tick depois para a linha aparecer DEPOIS do que o CreativePlots
     * escreveu, e não no meio. Não dá para alterar a saída dele sem forkar o
     * plugin — esta é a forma de colocar a informação onde o jogador procura.
     */
    private void mostrarPrecoDepoisDoInfo(Player jogador) {
        PlotSale venda = plugin.plotSale();
        if (venda == null || !venda.habilitado()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!jogador.isOnline()) {
                return;
            }
            if (jogador.hasPermission(ISENTO)) {
                jogador.sendMessage(plugin.mini().deserialize(
                        "<color:#AAAAAA>Terrenos são <color:#FFD700>gratuitos</color> para você.</color>"));
                return;
            }

            String moeda = plugin.economyCatalog().currencySymbol();
            int preco = venda.precoProximo(jogador.getUniqueId());
            int pagos = venda.pagosPor(jogador.getUniqueId());

            jogador.sendMessage(plugin.mini().deserialize(
                    "<color:#AAAAAA>Terrenos comprados: <color:#FFD700>" + pagos
                            + "</color> — próximo por <color:#FF6A00>" + preco + " " + moeda
                            + "</color>.</color>"));
        });
    }

    private Estado estadoAtual(Player jogador) {
        PlotApiBridge terrenos = plugin.plotApi();
        String mundo = jogador.getWorld().getName();
        Optional<String> terreno = terrenos.terrenoEm(jogador.getLocation());

        if (terreno.isEmpty()) {
            return new Estado("", false);
        }

        Optional<UUID> dono = terrenos.donoDe(mundo, terreno.get());
        return new Estado(terreno.get(), dono.isPresent()
                && dono.get().equals(jogador.getUniqueId()));
    }

    /** Onde o jogador estava e se o terreno já era dele. */
    private record Estado(String terreno, boolean donoEhOJogador) {
    }
}
