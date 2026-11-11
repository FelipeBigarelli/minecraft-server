package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A espera de alguns segundos antes do teleporte para o terreno.
 *
 * <h2>Por que esperar</h2>
 *
 * Teleporte instantaneo no survival e uma tecla de fuga. Quem esta perdendo
 * uma briga, quem viu um creeper acender, quem caiu num buraco com lava
 * embaixo: todos escapam sem perder nada, e a morte deixa de ser o preco de
 * errar. A contagem devolve esse preco — durante ela o jogador continua no
 * mundo, visivel e atingivel.
 *
 * <h2>O que cancela</h2>
 *
 * Andar e levar dano. Sao as duas condicoes classicas, e cada uma fecha um
 * buraco diferente:
 *
 * <ul>
 *   <li><b>andar</b> impede atravessar o mapa de graca — digitar o comando e
 *       continuar correndo para o destino;</li>
 *   <li><b>dano</b> impede a fuga: levou flecha, o teleporte morre.</li>
 * </ul>
 *
 * <h2>Olhar em vez de escutar</h2>
 *
 * Daria para registrar listener de movimento e de dano. Sao dois dos eventos
 * mais quentes do servidor — PlayerMoveEvent dispara varias vezes por segundo
 * por jogador — e aqui eles rodariam para todo mundo o tempo todo, para
 * atender uma contagem que quase nunca existe. Esta classe faz o contrario:
 * enquanto ninguem esta viajando nao ha tarefa nenhuma, e durante a viagem uma
 * tarefa de um jogador olha quatro vezes por segundo para a posicao e a vida
 * dele.
 */
public final class TerrenoViagem {

    /**
     * De quanto em quanto tempo a viagem e conferida, em ticks.
     *
     * 5 ticks = 4 conferidas por segundo. Com uma por segundo daria para dar
     * um passo e voltar entre duas conferidas, e o jogador fugiria andando sem
     * perder o teleporte.
     */
    static final long INTERVALO = 5L;

    private final BigaCore plugin;
    private final Map<UUID, BukkitTask> emViagem = new HashMap<>();

    public TerrenoViagem(BigaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Saiu do lugar?
     *
     * Em blocos, nao em coordenada exata: mexer o mouse, pular no lugar ou
     * escorregar meio bloco numa escada nao e "ir embora", e cancelar por isso
     * faria o comando parecer quebrado. Trocar de mundo tambem conta como
     * sair — da para ser levado por um portal no meio da contagem.
     */
    static boolean saiuDoLugar(Location partida, Location agora) {
        if (partida == null || agora == null) {
            return true;
        }
        if (!Objects.equals(partida.getWorld(), agora.getWorld())) {
            return true;
        }
        return partida.getBlockX() != agora.getBlockX()
                || partida.getBlockY() != agora.getBlockY()
                || partida.getBlockZ() != agora.getBlockZ();
    }

    /** Quantos segundos inteiros ainda faltam, para a contagem na tela. */
    static int segundosRestantes(long ticksPassados, long ticksTotais) {
        long faltam = Math.max(0, ticksTotais - ticksPassados);
        return (int) ((faltam + 19) / 20);
    }

    /**
     * Comeca a viagem. Com {@code segundos <= 0} o teleporte e na hora — e o
     * caso de quem tem a permissao de dispensa.
     */
    public void iniciar(Player jogador, Location destino, String apelido, int segundos) {
        cancelarSilencioso(jogador.getUniqueId());

        if (segundos <= 0) {
            chegar(jogador, destino, apelido);
            return;
        }

        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#555555>▎ </color><color:#AAAAAA>Voltando para </color>"
                        + "<color:#FFD700>" + apelido + "</color><color:#AAAAAA>.</color>"));
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#555555>▎ </color><color:#AAAAAA>Fique parado por </color>"
                        + "<color:#FF6A00>" + segundos + "s</color>"
                        + "<color:#AAAAAA> — andar ou levar dano cancela.</color>"));

        Location partida = jogador.getLocation().clone();
        double vidaInicial = jogador.getHealth();
        long total = segundos * 20L;
        UUID dono = jogador.getUniqueId();

        BukkitTask tarefa = new BukkitRunnable() {
            long passados = 0;
            int ultimoAviso = -1;

            @Override
            public void run() {
                passados += INTERVALO;

                if (!jogador.isOnline()) {
                    encerrar(dono);
                    cancel();
                    return;
                }
                if (saiuDoLugar(partida, jogador.getLocation())) {
                    abortar(jogador, "você se mexeu");
                    cancel();
                    return;
                }
                if (jogador.getHealth() < vidaInicial - 0.01) {
                    abortar(jogador, "você levou dano");
                    cancel();
                    return;
                }

                if (passados >= total) {
                    encerrar(dono);
                    chegar(jogador, destino, apelido);
                    cancel();
                    return;
                }

                int restam = segundosRestantes(passados, total);
                if (restam != ultimoAviso) {
                    ultimoAviso = restam;
                    jogador.sendActionBar(plugin.mini().deserialize(
                            "<color:#AAAAAA>Teleporte em </color>"
                                    + "<color:#FFD700><bold>" + restam + "</bold></color>"
                                    + "<color:#AAAAAA>…</color>"));
                    jogador.playSound(jogador.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, INTERVALO, INTERVALO);

        emViagem.put(dono, tarefa);
    }

    /** Cancela a viagem de quem esta esperando, sem avisar. */
    public void cancelarSilencioso(UUID jogador) {
        BukkitTask anterior = emViagem.remove(jogador);
        if (anterior != null) {
            anterior.cancel();
        }
    }

    private void encerrar(UUID jogador) {
        emViagem.remove(jogador);
    }

    private void abortar(Player jogador, String motivo) {
        encerrar(jogador.getUniqueId());
        jogador.sendActionBar(Component.empty());
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#555555>▎ </color><color:#E03131>Teleporte cancelado</color>"
                        + "<color:#AAAAAA> — " + motivo + ".</color>"));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /**
     * A chegada.
     *
     * A fumacinha sai dos dois lados — de onde ele saiu e de onde ele chegou.
     * Quem estava por perto ve que alguem se foi, em vez de o jogador sumir do
     * nada no meio de uma conversa.
     */
    private void chegar(Player jogador, Location destino, String apelido) {
        Location saida = jogador.getLocation();
        if (saida.getWorld() != null) {
            saida.getWorld().spawnParticle(Particle.PORTAL, saida.clone().add(0, 1, 0),
                    40, 0.3, 0.8, 0.3, 0.05);
        }

        jogador.teleport(destino);
        jogador.sendActionBar(Component.empty());

        if (destino.getWorld() != null) {
            destino.getWorld().spawnParticle(Particle.PORTAL, destino.clone().add(0, 1, 0),
                    40, 0.3, 0.8, 0.3, 0.05);
        }
        jogador.playSound(destino, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);

        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#555555>▎ </color><color:#5BD75B>Você chegou em </color>"
                        + "<color:#FFD700>" + apelido + "</color><color:#5BD75B>.</color>"));
    }
}
