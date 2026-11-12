package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faz os portais do hub funcionarem — e impede o hub de matar alguém.
 *
 * <h2>Por que não é bloco de portal</h2>
 *
 * O vão do arco é AR. Portal do Nether ali dentro mandaria o jogador para o
 * Nether de verdade, e nenhum destino do hub.yml teria efeito; portal do End
 * exigiria estrutura. O que existe no vão é uma cortina de partículas na cor
 * do destino, desenhada daqui, e uma caixa invisível que este laço confere.
 *
 * <h2>Por que um laço e não PlayerMoveEvent</h2>
 *
 * PlayerMoveEvent dispara várias vezes por tick por jogador, inclusive quando
 * ele só vira a cabeça. Um hub tem poucos jogadores e quatro caixas; conferir
 * a cada quarto de segundo custa menos e é o mesmo laço que já precisa rodar
 * para desenhar partícula e para pescar quem caiu no void.
 */
public final class HubPortais implements Listener {

    private static final int INTERVALO_TICKS = 5;

    private final BigaCore plugin;
    private final Map<UUID, Long> ultimoSalto = new HashMap<>();
    private BukkitTask tarefa;

    public HubPortais(BigaCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parar();
        this.tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::ciclo, 60L, INTERVALO_TICKS);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    /** O mundo do hub, ou null se ele ainda não existe. */
    public World mundoDoHub() {
        String nome = plugin.getConfig().getString("hub.mundo", "hub");
        return Bukkit.getWorld(nome);
    }

    // ------------------------------------------------------------------

    private void ciclo() {
        World hub = mundoDoHub();
        if (hub == null) {
            return;
        }

        HubCatalog planta = plugin.hubCatalog();
        List<HubCatalog.Portal> portais = planta.portais();

        for (HubCatalog.Portal portal : portais) {
            cortina(hub, planta, portal);
        }

        for (Player jogador : hub.getPlayers()) {
            if (resgatarDoVoid(jogador, hub, planta)) {
                continue;
            }
            for (HubCatalog.Portal portal : portais) {
                if (dentroDoPortal(jogador.getLocation(), planta, portal)) {
                    atravessar(jogador, portal);
                    break;
                }
            }
        }
    }

    /**
     * A caixa do portal: o vão do arco, um bloco para cada lado.
     *
     * Estática e sem mundo para o teste medir sem servidor. A conta é feita
     * no eixo do portal (ao longo e atravessado), e não em x/z, porque o
     * mesmo portal existe nas quatro faces do hub.
     */
    static boolean dentroDaCaixa(double x, double y, double z,
                                 HubCatalog.Face face, int distancia, int piso) {
        double aoLongo = x * face.du() + z * face.dv();
        double atravessado = x * face.lu() + z * face.lv();

        return Math.abs(aoLongo - distancia) <= 1.2
                && Math.abs(atravessado) <= 2.5
                && y >= piso && y <= piso + 7;
    }

    private boolean dentroDoPortal(Location onde, HubCatalog planta, HubCatalog.Portal portal) {
        return dentroDaCaixa(onde.getX(), onde.getY(), onde.getZ(),
                portal.face(), planta.distanciaDoTerraco(), planta.pisoY());
    }

    private void atravessar(Player jogador, HubCatalog.Portal portal) {
        long agora = System.currentTimeMillis();
        long espera = Math.max(1, plugin.hubCatalog().esperaSegundos()) * 1000L;

        Long ultimo = ultimoSalto.get(jogador.getUniqueId());
        if (ultimo != null && agora - ultimo < espera) {
            return;
        }

        if (!portal.aberto()) {
            // Sem carimbo de tempo aqui: o aviso tem de poder repetir quando
            // ele voltar, mas não a cada quarto de segundo enquanto está
            // parado no vão.
            ultimoSalto.put(jogador.getUniqueId(), agora);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:" + portal.cor() + ">" + HubBuilder.escapar(portal.titulo())
                            + "</color> <gray>ainda está em obra</gray>"));
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }

        World destino = Bukkit.getWorld(portal.mundo());
        if (destino == null) {
            ultimoSalto.put(jogador.getUniqueId(), agora);
            jogador.sendMessage(Component.text(
                    "O mundo " + portal.mundo() + " não está carregado agora. Avise a administração."));
            plugin.getLogger().warning("[hub] Portal " + portal.id() + " aponta para o mundo '"
                    + portal.mundo() + "', que não existe ou não está carregado.");
            return;
        }

        ultimoSalto.put(jogador.getUniqueId(), agora);
        Location pouso = destino.getSpawnLocation().clone().add(0.5, 0, 0.5);

        jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
        jogador.teleportAsync(pouso).thenAccept(foi -> {
            if (Boolean.TRUE.equals(foi)) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:" + portal.cor() + ">"
                                + HubBuilder.escapar(portal.titulo()) + "</color>"));
                jogador.playSound(pouso, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
            }
        });
    }

    /**
     * Quem caiu da beirada volta para a praça em vez de morrer.
     *
     * No void não fica túmulo: o jogador perderia tudo por um passo em falso
     * num lugar que é só decoração. É a única regra do hub que existe para
     * desfazer um erro do jogador, e por isso não cobra nada nem avisa duas
     * vezes.
     */
    private boolean resgatarDoVoid(Player jogador, World hub, HubCatalog planta) {
        if (jogador.getLocation().getY() >= planta.resgateY()) {
            return false;
        }

        Location pouso = HubBuilder.pontoDeSpawn(hub, planta);
        jogador.teleportAsync(pouso);
        jogador.setFallDistance(0f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#8FD3FF>o hub te pegou no ar</color>"));
        jogador.playSound(pouso, Sound.ITEM_TRIDENT_RETURN, 0.5f, 1.6f);
        return true;
    }

    /** A cortina de luz no vão do arco, na cor do destino. */
    private void cortina(World hub, HubCatalog planta, HubCatalog.Portal portal) {
        HubCatalog.Face face = portal.face();
        int distancia = planta.distanciaDoTerraco();
        int piso = planta.pisoY();

        Color cor = corDe(portal.cor());
        Particle.DustOptions po = new Particle.DustOptions(cor, 1.6f);

        for (int lado = -2; lado <= 2; lado++) {
            for (int altura = 1; altura <= 6; altura++) {
                if ((lado + altura) % 2 != 0) {
                    continue;
                }
                double x = face.du() * distancia + face.lu() * lado + 0.5;
                double z = face.dv() * distancia + face.lv() * lado + 0.5;
                hub.spawnParticle(Particle.DUST, x, piso + altura + 0.5, z, 1, 0.1, 0.2, 0.1, 0, po);
            }
        }
        hub.spawnParticle(Particle.PORTAL,
                face.du() * distancia + 0.5, piso + 3.5, face.dv() * distancia + 0.5,
                6, 1.2, 1.6, 1.2, 0.02);
    }

    /** "#RRGGBB" do hub.yml para a cor da partícula. */
    static Color corDe(String hex) {
        try {
            String limpo = hex == null ? "" : hex.replace("#", "").trim();
            int valor = Integer.parseInt(limpo, 16);
            return Color.fromRGB((valor >> 16) & 0xFF, (valor >> 8) & 0xFF, valor & 0xFF);
        } catch (RuntimeException erro) {
            return Color.WHITE;
        }
    }

    // ------------------------------------------------------------------

    /**
     * Quem entra no servidor pela primeira vez cai no hub.
     *
     * Só na primeira: mandar todo login para o hub tiraria o jogador de onde
     * ele deslogou, que no survival é onde a casa dele está.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        if (!plugin.hubCatalog().spawnDeEntrada() || evento.getPlayer().hasPlayedBefore()) {
            return;
        }

        World hub = mundoDoHub();
        if (hub == null) {
            return;
        }
        evento.getPlayer().teleportAsync(
                HubBuilder.pontoDeSpawn(hub, plugin.hubCatalog()));
    }
}
