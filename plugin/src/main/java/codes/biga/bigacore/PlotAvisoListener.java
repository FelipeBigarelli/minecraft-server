package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Troca as recusas em inglês do CreativePlots por mensagens nossas.
 *
 * <h2>O problema</h2>
 *
 * O {@code PlotProtectionListener} do CreativePlots 5.1 recusa com textos
 * fixos no jar — {@code "You can't break blocks here."},
 * {@code "You can only break blocks inside a plot."} e mais umas vinte. Não
 * existe chave de mensagem no {@code config.yml} dele: conferi chave por chave
 * contra o {@code config.yml} que vem dentro do próprio jar, e são idênticos.
 *
 * Pior que o idioma é o conteúdo. A frase diz o que o jogador NÃO pode, nunca
 * o que ele tem de fazer. Quem entra pela primeira vez cai numa rua, bate num
 * bloco, leva cinco linhas vermelhas iguais e não descobre que faltava
 * {@code /plot auto}.
 *
 * <h2>Por que não patchear o jar</h2>
 *
 * Porque já fizemos isso com o WorldGuard e a tradução morre no próximo
 * update. A mesma lição do apelido {@code /saldo}, que mora no
 * {@code commands.yml} e não dentro de um jar remendado: o conserto fica na
 * nossa camada.
 *
 * <h2>Como funciona</h2>
 *
 * Todo handler do {@code PlotProtectionListener} é
 * {@code @EventHandler(ignoreCancelled = true)}, e os de construção rodam em
 * {@code LOW}. Então este listener roda em {@code LOWEST} — antes dele —,
 * cancela o evento e manda a mensagem em português. Quando o CreativePlots
 * chega, o evento já está cancelado e ele se cala sozinho. Nenhum byte do jar
 * dele é tocado.
 *
 * <h2>A regra é dele, não nossa</h2>
 *
 * Quem decide se pode construir é o {@code PlotAPI.canBuild} do próprio
 * CreativePlots, via {@link PlotApiBridge}. Reimplementar aqui a conta de
 * dono, convidado, flag e bypass daria divergência na primeira atualização —
 * e a divergência perigosa é a que BLOQUEIA quem podia construir.
 *
 * Por isso a ponte devolve {@code Optional<Boolean>} e o vazio significa
 * "não sei": sem resposta, este listener não faz nada e o comportamento volta
 * a ser exatamente o de antes, em inglês. Degradar para o estado anterior é
 * aceitável; impedir alguém de construir no próprio terreno não é.
 */
public final class PlotAvisoListener implements Listener {

    /**
     * Espera entre dois avisos para o mesmo jogador.
     *
     * Segurar o botão esquerdo dispara um BlockBreakEvent por bloco. A foto que
     * motivou esta classe tinha cinco linhas idênticas de uma vez. Quatro
     * segundos cobrem a rajada sem esconder o aviso de quem tenta de novo mais
     * tarde, já em outro lugar.
     */
    private static final long ESPERA_MS = 4_000L;

    private final BigaCore plugin;
    private final Map<UUID, Long> ultimoAviso = new HashMap<>();

    public PlotAvisoListener(BigaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (barrar(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (barrar(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoEsvaziarBalde(PlayerBucketEmptyEvent evento) {
        if (barrar(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoEncherBalde(PlayerBucketFillEvent evento) {
        if (barrar(evento.getPlayer(), evento.getBlock().getLocation())) {
            evento.setCancelled(true);
        }
    }

    /**
     * @return true quando o evento tem de ser cancelado por nós. Nesse caso a
     *         mensagem já foi enviada (ou suprimida pela espera).
     */
    private boolean barrar(Player jogador, Location local) {
        PlotApiBridge ponte = plugin.plotApi();
        if (!ponte.mundoDeTerrenos(local)) {
            return false;
        }

        // Mesmos atalhos que o CreativePlots usa. Sem isto o dono do servidor
        // seria barrado por nós antes de o plugin dele dizer que podia.
        if (jogador.isOp()
                || jogador.hasPermission("plot.admin")
                || jogador.hasPermission("plot.bypass")) {
            return false;
        }

        Optional<Boolean> pode = ponte.podeConstruir(jogador, local);
        if (pode.isEmpty() || pode.get()) {
            return false;
        }

        avisar(jogador, local, ponte);
        return true;
    }

    private void avisar(Player jogador, Location local, PlotApiBridge ponte) {
        long agora = System.currentTimeMillis();
        Long anterior = ultimoAviso.get(jogador.getUniqueId());
        if (anterior != null && agora - anterior < ESPERA_MS) {
            return;
        }
        ultimoAviso.put(jogador.getUniqueId(), agora);

        Optional<String> terreno = ponte.terrenoEm(local);
        if (terreno.isEmpty()) {
            ruaNao(jogador);
            return;
        }

        String mundo = local.getWorld() == null ? "" : local.getWorld().getName();
        Optional<String> dono = ponte.donoNomeDe(mundo, terreno.get());
        if (dono.isEmpty()) {
            terrenoLivre(jogador);
        } else {
            terrenoDeOutro(jogador, dono.get());
        }
    }

    private void ruaNao(Player jogador) {
        linha(jogador, "<color:#E03131>Aqui é rua.</color>"
                + " <color:#AAAAAA>Ninguém constrói fora de um terreno.</color>");
        linha(jogador, "<color:#AAAAAA>Pegue um com </color>"
                + "<color:#FFD700>/plot auto</color>"
                + "<color:#AAAAAA> — ele te leva para dentro dele.</color>");
    }

    private void terrenoLivre(Player jogador) {
        linha(jogador, "<color:#FF6A00>Este terreno está livre</color>"
                + "<color:#AAAAAA>, mas ainda não é seu.</color>");

        String preco = precoDoProximo(jogador);
        linha(jogador, "<color:#AAAAAA>Fique com ele usando </color>"
                + "<color:#FFD700>/plot claim</color>"
                + "<color:#AAAAAA>" + preco + "</color>");
    }

    private void terrenoDeOutro(Player jogador, String dono) {
        linha(jogador, "<color:#E03131>Este terreno é de </color>"
                + "<color:#FF6A00>" + sanear(dono) + "</color><color:#E03131>.</color>");
        linha(jogador, "<color:#AAAAAA>Só quem ele liberar constrói aqui. Peça um </color>"
                + "<color:#FFD700>/plot trust " + sanear(jogador.getName()) + "</color>"
                + "<color:#AAAAAA>.</color>");
    }

    /**
     * O preço entra na frase só quando a venda está ligada de verdade.
     *
     * Com {@code policy.plot-prices} vazio o terreno é de graça, e anunciar um
     * preço que não existe seria a mesma falha do paredão que ensinava
     * {@code /saldo} antes do apelido existir.
     */
    private String precoDoProximo(Player jogador) {
        PlotSale venda = plugin.plotSale();
        if (venda == null || !venda.habilitado()) {
            return ".";
        }
        int preco = venda.precoProximo(jogador.getUniqueId());
        if (preco <= 0) {
            return ".";
        }
        return " — custa " + plugin.economyCatalog().money(preco) + ".";
    }

    private void linha(Player jogador, String texto) {
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#555555>▎ </color>" + texto));
    }

    /**
     * Nome de jogador entra na mensagem como texto, nunca como marcação.
     *
     * O MiniMessage é interpretado depois da concatenação. Um apelido com
     * {@code <red>} ou {@code <click:run_command:...>} viraria tag de verdade,
     * e em servidor com {@code online-mode=false} o apelido é escolhido por
     * quem conecta. Comer o {@code <} resolve na origem.
     */
    static String sanear(String nome) {
        return nome == null ? "" : nome.replace("<", "");
    }
}
