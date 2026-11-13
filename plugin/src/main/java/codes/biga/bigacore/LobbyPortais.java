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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Faz os vinte portais do lobby funcionarem, e a volta para o hub.
 *
 * <h2>Por que não é bloco de portal</h2>
 *
 * O mesmo do hub: o vão é AR. Portal do Nether ali mandaria o jogador para o
 * Nether de verdade e nenhum destino do minigames.yml teria efeito. O que
 * existe é uma cortina de partículas na cor do jogo e uma caixa invisível que
 * este laço confere.
 *
 * <h2>Por que a cortina não é desenhada em todos os vinte</h2>
 *
 * Partícula só chega a quem está perto, mas o servidor monta o pacote de
 * qualquer jeito. Num lobby de 119 de lado, um jogador no meio do pátio está
 * a cinquenta blocos de qualquer nicho — ou seja, dezenove dos vinte pacotes
 * seriam montados para ninguém, vinte vezes por segundo. O laço mede a
 * distância antes.
 */
public final class LobbyPortais implements Listener {

    private static final int INTERVALO_TICKS = 5;

    /** Até onde vale a pena desenhar a cortina de um portal. */
    private static final double ALCANCE_DA_CORTINA = 40.0;

    private final BigaCore plugin;
    private final Map<UUID, Long> ultimoSalto = new HashMap<>();

    /**
     * Por qual portal cada jogador SAIU do lobby.
     *
     * É o que faz a porta ser de ida e volta. Sem isto, quem voltasse de um
     * jogo cairia no pódio do meio da praça e teria de atravessar o pátio
     * inteiro de novo para achar o portal de onde tinha acabado de sair —
     * e, num claustro de vinte, achar o mesmo de novo não é imediato.
     *
     * <p>Mora na memória de propósito. É estado de SESSÃO: se o servidor
     * reiniciar enquanto o jogador está num minigame, ele volta para o pódio,
     * que é o lugar certo para quem chegou sem vir de lugar nenhum.
     */
    private final Map<UUID, String> portalDeSaida = new HashMap<>();

    private BukkitTask tarefa;

    public LobbyPortais(BigaCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parar();
        this.tarefa = Bukkit.getScheduler().runTaskTimer(plugin, this::ciclo, 80L,
                INTERVALO_TICKS);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    /** O mundo do lobby, ou null se ele ainda não existe. */
    public World mundoDoLobby() {
        String nome = plugin.getConfig().getString("minigames.mundo", "minigames");
        return Bukkit.getWorld(nome);
    }

    // ------------------------------------------------------------------

    private void ciclo() {
        World lobby = mundoDoLobby();
        if (lobby == null || lobby.getPlayers().isEmpty()) {
            return;
        }

        MinigamesCatalog planta = plugin.minigamesCatalog();
        List<MinigamesCatalog.Portal> portais = planta.portais();

        for (MinigamesCatalog.Portal portal : portais) {
            Location porta = centroDaPorta(lobby, planta, portal);
            if (alguemPerto(lobby, porta)) {
                cortina(lobby, planta, portal);
            }
        }
        cortinaDaVolta(lobby, planta);

        for (Player jogador : lobby.getPlayers()) {
            if (resgatarDoVoid(jogador, lobby, planta)) {
                continue;
            }
            if (dentroDaVolta(jogador.getLocation(), planta)) {
                voltarParaOHub(jogador, planta);
                continue;
            }
            for (MinigamesCatalog.Portal portal : portais) {
                if (dentroDoPortal(jogador.getLocation(), planta, portal)) {
                    atravessar(jogador, portal);
                    break;
                }
            }
        }
    }

    private boolean alguemPerto(World lobby, Location onde) {
        for (Player jogador : lobby.getPlayers()) {
            if (jogador.getLocation().distanceSquared(onde)
                    <= ALCANCE_DA_CORTINA * ALCANCE_DA_CORTINA) {
                return true;
            }
        }
        return false;
    }

    private Location centroDaPorta(World lobby, MinigamesCatalog planta,
                                   MinigamesCatalog.Portal portal) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fundo = planta.galeriaFim() - 1;
        return new Location(lobby, lado.x(centro, fundo) + 0.5, planta.pisoY() + 4,
                lado.z(centro, fundo) + 0.5);
    }

    /**
     * A caixa de um portal de jogo: o ar na frente da porta, no fundo da
     * câmara.
     *
     * Estática e sem mundo para o teste medir sem servidor. A conta é feita
     * no eixo da galeria (ao longo e para fora) e não em x/z, porque o mesmo
     * portal existe nas quatro galerias. O {@code 2.5} de folga cobre os dois
     * sentidos: o jogador fica no meio do bloco, e o meio do bloco cai meio
     * para um lado no NORTE e meio para o outro no SUL.
     */
    static boolean dentroDaCaixa(double x, double y, double z,
                                 MinigamesCatalog.Lado lado, int centro, int fundo,
                                 int piso) {
        double aoLongo = x * lado.ux() + z * lado.uz();
        double paraFora = x * lado.vx() + z * lado.vz();

        return Math.abs(aoLongo - centro) <= 2.5
                && paraFora >= fundo - 2.2
                && paraFora <= fundo + 1.0
                && y >= piso && y <= piso + 9;
    }

    private boolean dentroDoPortal(Location onde, MinigamesCatalog planta,
                                   MinigamesCatalog.Portal portal) {
        return dentroDaCaixa(onde.getX(), onde.getY(), onde.getZ(), portal.lado(),
                planta.centroDoVao(portal.vao()), planta.galeriaFim(), planta.pisoY());
    }

    /**
     * A caixa da volta: o vão da parede em pé no pódio do monumento.
     *
     * Aperta em z de propósito. Ela fica no meio da praça, e uma caixa folgada
     * mandaria para o hub quem só está passando pelo pódio de um lado para o
     * outro — que é o caminho natural de quem vai do nicho do norte para o do
     * sul.
     */
    static boolean dentroDaVolta(double x, double y, double z, int piso) {
        return Math.abs(x - 0.5) <= 2.6
                && Math.abs(z - 0.5) <= 1.2
                && y >= piso + 3.5 && y <= piso + 11.5;
    }

    private boolean dentroDaVolta(Location onde, MinigamesCatalog planta) {
        return dentroDaVolta(onde.getX(), onde.getY(), onde.getZ(), planta.pisoY());
    }

    // ------------------------------------------------------------------

    /** Quanto tempo falta para este jogador poder saltar de novo. */
    private boolean esperando(Player jogador, long agora) {
        long espera = Math.max(1, plugin.minigamesCatalog().esperaSegundos()) * 1000L;
        Long ultimo = ultimoSalto.get(jogador.getUniqueId());
        return ultimo != null && agora - ultimo < espera;
    }

    private void atravessar(Player jogador, MinigamesCatalog.Portal portal) {
        long agora = System.currentTimeMillis();
        if (esperando(jogador, agora)) {
            return;
        }

        if (!portal.aberto()) {
            ultimoSalto.put(jogador.getUniqueId(), agora);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:" + portal.cor() + ">" + HubBuilder.escapar(portal.titulo())
                            + "</color> <gray>ainda está em obra</gray>"));
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }

        World destino = Bukkit.getWorld(portal.mundo());
        if (destino == null) {
            // O recado para o JOGADOR fala do jogo, não do mundo: "o mundo
            // corrida não está carregado" é verdade e não quer dizer nada
            // para quem só queria jogar. O recado para o DONO vai no
            // console e diz as duas chaves que precisam concordar — porque o
            // nome do mundo está escrito em dois lugares, e foi exatamente
            // isso que quebrou quando a Corrida virou 'corrida_akina'.
            ultimoSalto.put(jogador.getUniqueId(), agora);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:" + portal.cor() + ">"
                            + HubBuilder.escapar(portal.titulo())
                            + "</color> <gray>está fora do ar — já avisamos a "
                            + "administração</gray>"));
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            plugin.getLogger().warning("[lobby] O portal '" + portal.id()
                    + "' leva ao mundo '" + portal.mundo() + "', que não está "
                    + "carregado. Confira se minigames.yml (portais → " + portal.id()
                    + " → mundo) diz o MESMO nome que o yml do jogo, e se a "
                    + "pasta desse mundo existe em disco.");
            return;
        }

        ultimoSalto.put(jogador.getUniqueId(), agora);
        portalDeSaida.put(jogador.getUniqueId(), portal.id());
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

    private void voltarParaOHub(Player jogador, MinigamesCatalog planta) {
        long agora = System.currentTimeMillis();
        if (esperando(jogador, agora)) {
            return;
        }
        ultimoSalto.put(jogador.getUniqueId(), agora);
        portalDeSaida.remove(jogador.getUniqueId());

        String nome = plugin.getConfig().getString("hub.mundo", "hub");
        World hub = Bukkit.getWorld(nome);
        if (hub == null) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>o hub não está carregado agora</color>"));
            return;
        }

        Location pouso = chegadaNoHub(hub);
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
        jogador.teleportAsync(pouso).thenAccept(foi -> {
            if (Boolean.TRUE.equals(foi)) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:" + planta.volta().cor() + ">"
                                + HubBuilder.escapar(planta.volta().titulo()) + "</color>"));
                jogador.playSound(pouso, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
            }
        });
    }

    /**
     * Onde se chega no hub ao voltar do lobby: no portão do próprio lobby.
     *
     * Acha o portal do hub pelo MUNDO para onde ele aponta, e não por um id
     * escrito aqui. Enquanto o portão roxo do hub ainda estiver em obra
     * (sem mundo), não há portão para onde voltar e a chegada é o terraço de
     * sempre — que é o certo, porque nesse caso o jogador não entrou por ele.
     */
    private Location chegadaNoHub(World hub) {
        HubCatalog planta = plugin.hubCatalog();
        String lobby = plugin.getConfig().getString("minigames.mundo", "minigames");
        for (HubCatalog.Portal portal : planta.portais()) {
            if (portal.aberto() && portal.mundo().equals(lobby)) {
                return HubBuilder.pontoDoPortal(hub, planta, portal.face());
            }
        }
        return HubBuilder.pontoDeSpawn(hub, planta);
    }

    /**
     * Onde se chega no lobby ao voltar de um jogo: dentro da própria câmara.
     *
     * Quatro blocos à frente da porta, olhando para o pátio — ou seja, saindo
     * do portal, não caindo nele. A folga importa: a caixa do portal começa a
     * 2,2 do fundo, e chegar em cima dela mandaria o jogador de volta ao jogo
     * no mesmo instante.
     */
    static Location pontoDoPortal(World lobby, MinigamesCatalog planta,
                                  MinigamesCatalog.Portal portal) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fora = planta.galeriaFim() - 4;
        Location pouso = new Location(lobby,
                lado.x(centro, fora) + 0.5, planta.pisoY() + 1,
                lado.z(centro, fora) + 0.5);
        pouso.setYaw(lado.yawParaDentro());
        pouso.setPitch(0f);
        return pouso;
    }

    /**
     * Quem volta de um jogo sai pelo portal por onde entrou.
     *
     * O minigame só precisa devolver o jogador para o mundo do lobby, em
     * qualquer ponto; o lugar certo é escolhido aqui. Assim cada jogo que
     * entrar não precisa saber onde fica o nicho dele — e não há vinte
     * coordenadas espalhadas por vinte plugins para alguém manter.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoChegarNoLobby(PlayerChangedWorldEvent evento) {
        World lobby = mundoDoLobby();
        if (lobby == null || !evento.getPlayer().getWorld().equals(lobby)) {
            return;
        }
        String id = portalDeSaida.remove(evento.getPlayer().getUniqueId());
        if (id == null) {
            return;
        }
        for (MinigamesCatalog.Portal portal : plugin.minigamesCatalog().portais()) {
            if (portal.id().equals(id)) {
                // Sem reacender a espera: o jogador acabou de ser devolvido
                // pelo jogo, e cobrar dele os três segundos faria o portal
                // ignorá-lo se ele quisesse entrar de novo na hora.
                evento.getPlayer().teleportAsync(pontoDoPortal(lobby,
                        plugin.minigamesCatalog(), portal));
                return;
            }
        }
    }

    /**
     * Quem caiu da beirada volta para o pódio em vez de morrer.
     *
     * A passarela de cima dá vinte de queda no pátio e a borda de fora dá no
     * void. É a única regra do lobby que existe para desfazer um erro do
     * jogador, e por isso não cobra nada nem avisa duas vezes.
     */
    private boolean resgatarDoVoid(Player jogador, World lobby, MinigamesCatalog planta) {
        if (jogador.getLocation().getY() >= planta.resgateY()) {
            return false;
        }
        Location pouso = LobbyBuilder.pontoDeSpawn(lobby, planta);
        jogador.teleportAsync(pouso);
        jogador.setFallDistance(0f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#8FD3FF>o lobby te pegou no ar</color>"));
        jogador.playSound(pouso, Sound.ITEM_TRIDENT_RETURN, 0.5f, 1.6f);
        return true;
    }

    // ------------------------------------------------------------------

    /** A cortina de luz na porta de um jogo, na cor dele. */
    private void cortina(World lobby, MinigamesCatalog planta,
                         MinigamesCatalog.Portal portal) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fundo = planta.galeriaFim() - 1;
        int piso = planta.pisoY();

        Particle.DustOptions po = new Particle.DustOptions(
                HubPortais.corDe(portal.cor()), 1.6f);

        for (int ao = -2; ao <= 2; ao++) {
            for (int altura = 1; altura <= 7; altura++) {
                if ((ao + altura) % 2 != 0) {
                    continue;
                }
                lobby.spawnParticle(Particle.DUST,
                        lado.x(centro + ao, fundo) + 0.5, piso + altura + 0.5,
                        lado.z(centro + ao, fundo) + 0.5, 1, 0.1, 0.2, 0.1, 0, po);
            }
        }
        lobby.spawnParticle(Particle.PORTAL,
                lado.x(centro, fundo) + 0.5, piso + 3.5, lado.z(centro, fundo) + 0.5,
                4, 1.0, 1.4, 0.4, 0.02);
    }

    /** A cortina da volta, no meio da praça. */
    private void cortinaDaVolta(World lobby, MinigamesCatalog planta) {
        int piso = planta.pisoY();
        Particle.DustOptions po = new Particle.DustOptions(
                HubPortais.corDe(planta.volta().cor()), 1.8f);

        for (int x = -2; x <= 2; x++) {
            for (int altura = 4; altura <= 10; altura++) {
                if ((x + altura) % 2 != 0) {
                    continue;
                }
                lobby.spawnParticle(Particle.DUST, x + 0.5, piso + altura + 0.5, 0.5,
                        1, 0.1, 0.2, 0.3, 0, po);
            }
        }
        lobby.spawnParticle(Particle.PORTAL, 0.5, piso + 7.0, 0.5, 8, 1.6, 2.0, 0.6, 0.02);
    }
}
