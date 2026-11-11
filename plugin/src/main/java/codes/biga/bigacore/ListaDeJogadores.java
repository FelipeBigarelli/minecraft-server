package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * A lista do TAB: cabeçalho, rodapé e o nome de cada um.
 *
 * <h2>Por que isto é um plugin e não um recurso do jogo</h2>
 *
 * O cliente desenha a lista com o nome cru e as barrinhas de sinal. Nome do
 * servidor, quantos estão on, TPS, dia, saldo e ping em número são todos
 * API de servidor — nada aqui mexe no cliente nem pede mod.
 *
 * <h2>O texto do jogador NUNCA é interpretado</h2>
 *
 * A tag é escrita por um jogador e aparece na tela de todos. Ela entra na
 * mensagem por {@link Placeholder#unparsed}, não por concatenação: assim uma
 * tag contendo {@code <red>}, {@code <newline>} ou {@code <click>} vira texto
 * visível em vez de virar formatação, link ou comando. A allowlist de
 * caracteres do tags.yml já barraria isso, mas as duas defesas custam o mesmo
 * e só uma delas sobrevive a alguém editar o tags.yml.
 */
public final class ListaDeJogadores implements Listener {

    private final BigaCore plugin;
    private YamlConfiguration config;
    private BukkitTask tarefa;

    public ListaDeJogadores(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "tags.yml"));
    }

    /** Liga o redesenho periódico. Idempotente: chamar duas vezes não duplica. */
    public void start() {
        parar();

        long intervalo = Math.max(20L, config.getLong("lista.intervalo", 40L));
        this.tarefa = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player jogador : Bukkit.getOnlinePlayers()) {
                atualizar(jogador);
            }
        }, 40L, intervalo);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        // Na mesma hora, e não no próximo ciclo: quem entra e aperta TAB na
        // primeira meia-volta veria a lista crua do cliente.
        atualizar(evento.getPlayer());

        // E os outros precisam ver o nome dele já com tag.
        for (Player outro : Bukkit.getOnlinePlayers()) {
            if (!outro.equals(evento.getPlayer())) {
                atualizar(outro);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoSair(PlayerQuitEvent evento) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player jogador : Bukkit.getOnlinePlayers()) {
                atualizar(jogador);
            }
        });
    }

    /** Redesenha a lista para UM jogador. */
    public void atualizar(Player jogador) {
        jogador.playerListName(nomeNaLista(jogador));
        jogador.sendPlayerListHeaderAndFooter(
                montar(config.getStringList("lista.cabecalho"), jogador),
                montar(config.getStringList("lista.rodape"), jogador));
    }

    /** O nome dele na lista: tag comprada, nome e ping. */
    public Component nomeNaLista(Player jogador) {
        String formato = config.getString("lista.formato-do-nome",
                "<tag><white><nome></white> <ping>");

        return plugin.mini().deserialize(formato, TagResolver.resolver(
                Placeholder.component("tag", tagDe(jogador)),
                Placeholder.unparsed("nome", jogador.getName()),
                Placeholder.parsed("ping", pingPintado(jogador.getPing()))));
    }

    /** A etiqueta entre colchetes, ou nada para quem não comprou. */
    private Component tagDe(Player jogador) {
        var guardada = plugin.tagStore().de(jogador.getUniqueId());
        if (guardada.isEmpty() || guardada.get().texto().isBlank()) {
            return Component.empty();
        }

        TagStore.Tag tag = guardada.get();
        String formato = config.getString("lista.formato-da-tag",
                "<dark_gray>[</dark_gray><cor><texto></cor><dark_gray>]</dark_gray> ");

        String cor = plugin.tagCatalog().corPermitida(tag.cor())
                ? tag.cor().toLowerCase(Locale.ROOT)
                : plugin.tagCatalog().corPadrao();

        return plugin.mini().deserialize(formato.replace("<cor>", "<" + cor + ">")
                        .replace("</cor>", "</" + cor + ">"),
                Placeholder.unparsed("texto", tag.texto()));
    }

    private Component montar(List<String> linhas, Player jogador) {
        if (linhas.isEmpty()) {
            return Component.empty();
        }

        String bruto = String.join("<newline>", linhas);
        double[] tps = Bukkit.getTPS();

        String texto = bruto
                .replace("<online>", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("<limite>", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("<tps>", tpsPintado(tps.length > 0 ? tps[0] : 20.0))
                .replace("<dia>", String.valueOf(jogador.getWorld().getFullTime() / 24000L))
                .replace("<simbolo>", plugin.economyCatalog().currencySymbol())
                .replace("<saldo>", saldoDe(jogador))
                .replace("<ping>", pingPintado(jogador.getPing()));

        return plugin.mini().deserialize(texto, TagResolver.resolver(
                Placeholder.unparsed("nome", jogador.getName()),
                Placeholder.unparsed("mundo", jogador.getWorld().getName())));
    }

    private String saldoDe(Player jogador) {
        if (!plugin.economyBridge().available()) {
            return "—";
        }
        OptionalDouble saldo = plugin.economyBridge().balance(jogador);
        return saldo.isPresent() ? String.valueOf(Math.round(saldo.getAsDouble())) : "—";
    }

    // ------------------------------------------------------------------
    //  Pintura — estática e pura, para o teste medir sem servidor
    // ------------------------------------------------------------------

    private String pingPintado(int ping) {
        return pingPintado(ping,
                config.getInt("lista.ping.bom", 60),
                config.getInt("lista.ping.medio", 120),
                config.getInt("lista.ping.ruim", 200));
    }

    /**
     * O ping em número, com a cor da faixa.
     *
     * Ping 0 existe: é o do jogador no mesmo computador do servidor, e é o
     * caso do dono. Mostrar "0ms" em vermelho de "sem medida" seria errado
     * justamente para quem olha a lista todo dia.
     */
    static String pingPintado(int ping, int bom, int medio, int ruim) {
        String cor;
        if (ping < 0) {
            return "<dark_gray>--</dark_gray>";
        } else if (ping <= bom) {
            cor = "green";
        } else if (ping <= medio) {
            cor = "yellow";
        } else if (ping <= ruim) {
            cor = "gold";
        } else {
            cor = "red";
        }
        return "<" + cor + ">" + ping + "ms</" + cor + ">";
    }

    /** TPS com uma casa e a cor da saúde do servidor. */
    static String tpsPintado(double tps) {
        double limitado = Math.min(20.0, tps);
        String cor;
        if (limitado >= 19.0) {
            cor = "green";
        } else if (limitado >= 17.0) {
            cor = "yellow";
        } else {
            cor = "red";
        }
        return "<" + cor + ">" + String.format(Locale.ROOT, "%.1f", limitado) + "</" + cor + ">";
    }
}
