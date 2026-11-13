package codes.biga.bigacore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * O Spleef: cave o chão de quem corre, e não caia no seu próprio buraco.
 *
 * <h2>A regra em uma frase, e o que ela muda em relação ao TNT Run</h2>
 *
 * O chão é cavável. Só isso. Mas a consequência inverte o jogo: no TNT Run o
 * adversário é o relógio — o chão some sob os SEUS pés, você só precisa
 * correr. Aqui o chão não some sozinho nunca, e o adversário é gente. Daí
 * saem três coisas que o TNT Run não precisa ter:
 *
 * <p><b>Fica parado é seguro — e por isso o gelo cede.</b> No TNT Run parar é
 * morrer, e o jogo se resolve sozinho. Aqui duas pessoas paradas em pontas
 * opostas ficariam assim até o servidor cair. O gelo range aos cinco segundos
 * e cede aos nove, por jogador parado. Agachado conta como parado: agachar na
 * beira é a pose de quem acampa sem cair.
 *
 * <p><b>A arena encolhe.</b> Passados dois minutos, um anel some de dez em dez
 * segundos. É o que garante que toda partida acaba, mesmo entre dois que se
 * recusem a cavar.
 *
 * <p><b>Cava-se com a mão, não com pá.</b> Pá seria mais bonita e custaria o
 * preço errado: dar item a quem entra obriga a guardar e devolver o inventário
 * de cada jogador, e inventário devolvido errado é item perdido de verdade,
 * do mundo de sobrevivência. Enquanto esse guarda-volumes não existir — e um
 * dos jogos seguintes vai obrigar a fazê-lo — o clique da mão quebra o gelo
 * na hora, que é o padrão do plugin de referência de qualquer forma.
 *
 * <h2>Só o disco é cavável, e só a fiada dele</h2>
 *
 * Duas travas, não uma: o material tem de ser um dos dois do piso E a fiada
 * tem de ser a do disco. Sem a segunda, um bloco de neve que alguém deixasse
 * em qualquer lugar do mundo viraria buraco de Spleef.
 */
public final class Spleef implements Listener, JogoDeMinigame {

    private static final int INTERVALO_TICKS = 1;
    private static final int ALCANCE_DA_CORTINA = 32;

    /**
     * O modo de quem está na arena: SOBREVIVÊNCIA, e não aventura.
     *
     * Isto parece o contrário do seguro e é o contrário do que o TNT Run faz,
     * então vale a explicação — é o tipo de coisa que alguém "conserta" para
     * aventura um dia e só descobre em jogo:
     *
     * <p>Em aventura o CLIENTE nem envia o pacote de cavar. Ele confere
     * sozinho, antes de falar com o servidor, que naquele modo não se quebra
     * bloco, e cala a boca. O servidor nunca recebe nada, o
     * {@code PlayerInteractEvent} de clique esquerdo nunca acontece, e o jogo
     * inteiro — cujo verbo é cavar — não responderia ao clique. Os três
     * plugins de referência põem o jogador em sobrevivência exatamente por
     * isso.
     *
     * <p>O que sobraria de risco está fechado por fora: quebrar e colocar
     * bloco estão barrados para quem não é admin, dano e fome estão barrados,
     * largar item está barrado, e o mundo não gera bicho nenhum.
     */
    private static final GameMode MODO_DE_JOGO = GameMode.SURVIVAL;

    public enum Estado { ESPERANDO, CONTAGEM, PREPARO, JOGANDO, FIM }

    private final BigaCore plugin;
    private BukkitTask tarefa;

    private Estado estado = Estado.ESPERANDO;
    private final Set<UUID> naArena = new LinkedHashSet<>();
    private final Set<UUID> fila = new LinkedHashSet<>();
    private final Set<UUID> vivos = new LinkedHashSet<>();

    /**
     * A lista lateral com quem continua de pé.
     *
     * Aqui e não herdada do {@code JogoDeArena} porque este jogo fica FORA do
     * arcabouço — ele e o Spleef são os dois últimos, e já foram esquecidos
     * em três mudanças do arcabouço seguidas: os letreiros do saguão, o
     * preparo da largada e a trava do /speed. Toda vez que o arcabouço ganha
     * alguma coisa, estes dois precisam dela à mão.
     */
    private PlacarLateral lateral;

    private PlacarLateral lateral() {
        if (lateral == null) {
            lateral = new PlacarLateral(plugin, "spleef");
        }
        return lateral;
    }

    /**
     * As linhas: quem está de pé primeiro, quem caiu depois.
     *
     * Os caídos continuam na lista de propósito. Ver o próprio nome apagado
     * é a confirmação de que a rodada acabou PARA VOCÊ e não para todo mundo
     * — e num jogo em que se perde caindo num buraco, essa confusão acontece.
     */
    /**
     * Desenha a lateral, e só enquanto há rodada.
     *
     * No saguão ela fica apagada de propósito: antes da largada ninguém está
     * "de pé" nem "fora", e uma lista de todo mundo marcado como fora é pior
     * que lista nenhuma.
     */
    private void desenharALateral(SpleefCatalog planta) {
        if (estado == Estado.ESPERANDO || estado == Estado.CONTAGEM) {
            lateral().apagar();
            return;
        }
        lateral().titulo("<color:" + planta.corDoTexto() + "><bold>"
                + HubBuilder.escapar(planta.titulo()) + "</bold></color>");
        lateral().desenhar(linhasDaLateral());
    }

    private java.util.List<PlacarLateral.Linha> linhasDaLateral() {
        java.util.List<PlacarLateral.Linha> linhas = new java.util.ArrayList<>();
        for (UUID id : naArena) {
            Player quem = Bukkit.getPlayer(id);
            if (quem != null && vivos.contains(id)) {
                linhas.add(new PlacarLateral.Linha(quem.getName(), "de pé"));
            }
        }
        for (UUID id : naArena) {
            Player quem = Bukkit.getPlayer(id);
            if (quem != null && !vivos.contains(id)
                    && quem.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                linhas.add(new PlacarLateral.Linha(quem.getName(), "fora"));
            }
        }
        return linhas;
    }
    private final Map<UUID, GameMode> modoAnterior = new HashMap<>();
    private final Map<UUID, Long> ultimaVolta = new HashMap<>();

    /** Onde cada jogador estava no segundo anterior, para medir quem parou. */
    private final Map<UUID, double[]> ondeEstava = new HashMap<>();
    private final Map<UUID, Integer> paradoHa = new HashMap<>();
    private final Map<UUID, Integer> cavou = new HashMap<>();

    private int comecouCom;
    private int contagem;
    private int preparo;
    private int segundosDeJogo;
    private int raioAtual;
    private long inicioDaRodada;
    private long pulso;
    private boolean mundoAjustado;
    private String ultimoTextoDaPlaca = "";
    private BossBar barra;

    public Spleef(BigaCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parar();
        this.pulso = 0;
        this.mundoAjustado = false;
        this.barra = BossBar.bossBar(Component.empty(), 1f,
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        this.tarefa = Bukkit.getScheduler().runTaskTimer(
                plugin, this::ciclo, 100L, INTERVALO_TICKS);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        for (UUID id : new ArrayList<>(naArena)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                devolverModo(jogador);
                if (barra != null) {
                    jogador.hideBossBar(barra);
                }
            }
        }
        naArena.clear();
        fila.clear();
        vivos.clear();
        paradoHa.clear();
        ondeEstava.clear();
        cavou.clear();
        estado = Estado.ESPERANDO;
    }

    public Estado estado() {
        return estado;
    }

    @Override
    public String estadoEscrito() {
        return estado.name();
    }

    public int quantosNaArena() {
        return naArena.size();
    }

    public int quantosProntos() {
        return fila.size();
    }

    public int quantosVivos() {
        return vivos.size();
    }

    /** O raio que a arena tem agora. Encolhe com o aperto. */
    public int raioAtual() {
        return raioAtual;
    }

    static boolean arenaConstruida(World mundo, SpleefCatalog planta) {
        return !mundo.getBlockAt(0, planta.saguaoY(), 0).getType().isAir();
    }

    public World mundoDaArena() {
        return Bukkit.getWorld(plugin.spleefCatalog().mundo());
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    private void ciclo() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        SpleefCatalog planta = plugin.spleefCatalog();
        pulso++;

        if (!mundoAjustado) {
            ajustarMundo(mundo);
            mundoAjustado = true;
        }

        if (estado == Estado.JOGANDO) {
            conferirQuedas(mundo, planta);
            if (ArenaComum.rodadaAcabou(vivos.size(), comecouCom, planta.treinoSozinho())) {
                terminar(planta);
            }
        }

        if (pulso % 5 == 0) {
            reconciliar(mundo);
            if (!naArena.isEmpty()) {
                cortinaDaVolta(mundo, planta);
                conferirVolta(mundo, planta);
                resgatarDoVoid(mundo, planta);
            }
        }
        if (pulso % 10 == 0 && !naArena.isEmpty()) {
            atualizarBarra(planta);
        }
        if (pulso % 20 == 0 && (!naArena.isEmpty() || estado != Estado.ESPERANDO)) {
            cicloDeSegundo(mundo, planta);
            if (arenaConstruida(mundo, planta)) {
                atualizarPlacas(mundo, planta);
            }
        }
    }

    /**
     * As regras do MUNDO da arena.
     *
     * Dano de queda desligado na regra do mundo E no listener: o jogo é feito
     * de cair de propósito, e um dos dois falhando sozinho transformaria a
     * mecânica central em dano.
     *
     * <p>Tique aleatório em zero: num mundo de neve e gelo é o que garante que
     * nada derrete, cresce ou muda sozinho entre uma rodada e outra.
     */
    static void ajustarMundo(World mundo) {
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.FALL_DAMAGE, false);
        mundo.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        mundo.setTime(18000L);
        mundo.setStorm(false);
    }

    // ------------------------------------------------------------------
    //  Presença
    // ------------------------------------------------------------------

    private void reconciliar(World mundo) {
        for (Player jogador : mundo.getPlayers()) {
            if (!naArena.contains(jogador.getUniqueId())) {
                entrou(jogador);
            }
        }
        for (UUID id : new ArrayList<>(naArena)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !jogador.getWorld().equals(mundo)) {
                saiu(id);
            }
        }
    }

    private void entrou(Player jogador) {
        lateral().mostrarPara(jogador);
        instrucoes(jogador);
        // Quem chegou com /speed ligado no hub não traz a velocidade
        // para dentro da arena.
        JogoDeArena.velocidadeNormal(jogador);
        SpleefCatalog planta = plugin.spleefCatalog();
        naArena.add(jogador.getUniqueId());
        modoAnterior.putIfAbsent(jogador.getUniqueId(), jogador.getGameMode());
        if (barra != null) {
            jogador.showBossBar(barra);
        }

        if (jogador.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!arenaConstruida(jogador.getWorld(), planta)) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendMessage(plugin.mini().deserialize(
                    "<gray>a arena do Spleef ainda não foi construída — "
                            + "um admin precisa rodar <white>/biga spleef construir "
                            + "CONFIRMAR</white></gray>"));
            return;
        }
        if (estado == Estado.PREPARO || estado == Estado.JOGANDO
                || estado == Estado.FIM) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>a rodada já começou — você entra na próxima</gray>"));
            return;
        }
        jogador.setGameMode(MODO_DE_JOGO);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:" + planta.corDoTexto() + ">"
                        + HubBuilder.escapar(planta.titulo()) + "</color>"),
                plugin.mini().deserialize("<gray>"
                        + HubBuilder.escapar(planta.subtitulo()) + "</gray>")));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.6f, 1.2f);
    }

    private void saiu(UUID id) {
        naArena.remove(id);
        fila.remove(id);
        vivos.remove(id);
        ultimaVolta.remove(id);
        paradoHa.remove(id);
        ondeEstava.remove(id);
        Player jogador = Bukkit.getPlayer(id);
        if (jogador != null) {
            lateral().esconderDe(jogador);
            if (barra != null) {
                jogador.hideBossBar(barra);
            }
            devolverModo(jogador);
        } else {
            modoAnterior.remove(id);
        }
    }

    /**
     * Devolve o jogador ao modo em que ele estava antes de entrar.
     *
     * Quem entra do lobby está em sobrevivência, e sair da arena em ADVENTURE
     * o deixaria sem poder quebrar bloco no survival — reclamação que ninguém
     * ligaria ao minigame de onde acabou de sair.
     */
    private void devolverModo(Player jogador) {
        GameMode antes = modoAnterior.remove(jogador.getUniqueId());
        if (antes != null && jogador.getGameMode() != antes) {
            jogador.setGameMode(antes);
        }
    }

    // ------------------------------------------------------------------
    //  Cavar
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  O kit: a pá e a bola de neve
    // ------------------------------------------------------------------

    /**
     * A pá e as bolas de neve, e nada mais.
     *
     * Nenhuma das duas é "inquebrável" no sentido do Minecraft: o
     * CreativePlots está com Prevent-Unbreakable-Items ligado e apaga item
     * assim do servidor inteiro — já apagou o kit do Sky Wars uma vez. A pá
     * não gasta porque {@link #aoDanificarItem} cancela o desgaste dentro
     * da arena, e as bolas não acabam porque {@link #reporOEstoque} as
     * recompõe a cada segundo.
     */
    private void darOKit(Player jogador, SpleefCatalog planta) {
        jogador.getInventory().clear();
        jogador.getInventory().setArmorContents(null);

        ItemStack pa = new ItemStack(planta.ferramenta());
        pa.editMeta(meta -> {
            meta.displayName(plugin.mini().deserialize("<color:"
                    + planta.corDoTexto() + ">pá do spleef</color>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration
                            .ITALIC, false));
            meta.lore(List.of(plugin.mini().deserialize(
                    "<gray>o gelo só cede para ela</gray>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration
                            .ITALIC, false)));
        });
        jogador.getInventory().setItem(0, pa);

        int quantas = planta.bolasDeNeve();
        if (quantas > 0) {
            ItemStack bolas = new ItemStack(Material.SNOWBALL, ArenaComum.cabemNaPilha(Material.SNOWBALL, quantas));
            bolas.editMeta(meta -> {
                meta.displayName(plugin.mini().deserialize(
                        "<color:#A8D8FF>bola de neve</color>")
                        .decoration(net.kyori.adventure.text.format
                                .TextDecoration.ITALIC, false));
                meta.lore(List.of(plugin.mini().deserialize(
                        "<gray>empurra quem levar — não machuca</gray>")
                        .decoration(net.kyori.adventure.text.format
                                .TextDecoration.ITALIC, false)));
            });
            jogador.getInventory().setItem(1, bolas);
        }
        jogador.getInventory().setHeldItemSlot(0);
    }

    /**
     * Repõe a pá e as bolas, uma vez por segundo.
     *
     * Repor em vez de marcar o item como infinito: é a mesma regra do resto
     * do servidor desde o acidente do Sky Wars — nada de item inquebrável,
     * porque o CreativePlots apaga.
     */
    private void reporOEstoque(SpleefCatalog planta) {
        int quantas = planta.bolasDeNeve();
        for (UUID id : new ArrayList<>(vivos)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null) {
                continue;
            }
            if (!jogador.getInventory().contains(planta.ferramenta())) {
                darOKit(jogador, planta);
                continue;
            }
            if (quantas <= 0) {
                continue;
            }
            ItemStack bolas = jogador.getInventory().getItem(1);
            if (bolas == null || bolas.getType() != Material.SNOWBALL
                    || bolas.getAmount() < quantas) {
                darOKit(jogador, planta);
            }
        }
    }

    /** A pá não gasta: o desgaste é cancelado dentro da arena. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoDanificarItem(PlayerItemDamageEvent evento) {
        if (nesteMundo(evento.getPlayer().getWorld())) {
            evento.setCancelled(true);
        }
    }

    /**
     * A bola de neve empurra, e não machuca.
     *
     * O empurrão é explícito e não o do Minecraft: a bola baunilha quase
     * não move um jogador, e o que o jogo quer é tirar alguém de cima de
     * uma beirada. Pequeno de propósito — empurrar para fora do disco é
     * sorte, empurrar para cima de um buraco é a jogada.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void aoAcertarBola(ProjectileHitEvent evento) {
        Projectile tiro = evento.getEntity();
        if (!(tiro instanceof Snowball)
                || !(evento.getHitEntity() instanceof Player alvo)
                || !nesteMundo(alvo.getWorld())) {
            return;
        }
        if (estado != Estado.JOGANDO || !vivos.contains(alvo.getUniqueId())) {
            return;
        }
        SpleefCatalog planta = plugin.spleefCatalog();
        Vector rumo = tiro.getVelocity().normalize();
        alvo.setVelocity(alvo.getVelocity().add(new Vector(
                rumo.getX() * planta.empurrao(),
                planta.empurraoAlto(),
                rumo.getZ() * planta.empurrao())));
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.8f, 1.4f);
        if (tiro.getShooter() instanceof Player quemJogou) {
            animarGolpeDaBola(alvo, quemJogou);
        }
    }

    /** O baque na tela de quem levou a bola, sem dano nenhum. */
    private void animarGolpeDaBola(Player alvo, Player quemJogou) {
        alvo.sendActionBar(plugin.mini().deserialize(
                "<color:#A8D8FF>" + HubBuilder.escapar(quemJogou.getName())
                        + " te empurrou</color>"));
    }

    /**
     * Se este bloco pode ser cavado agora.
     *
     * Estática e sem mundo para o teste medir sem servidor. As duas travas
     * juntas: material do piso E fiada do disco.
     */
    static boolean ehChaoCavavel(Material material, int y, int topoY,
                                 Set<Material> doPiso) {
        return y == topoY && doPiso.contains(material);
    }

    private void cavar(World mundo, Block bloco, Player quem) {
        Material material = bloco.getType();
        bloco.setType(Material.AIR, false);
        cavou.merge(quem.getUniqueId(), 1, Integer::sum);

        mundo.spawnParticle(Particle.SNOWFLAKE, bloco.getX() + 0.5, bloco.getY() + 0.8,
                bloco.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.02);
        mundo.playSound(bloco.getLocation(),
                material == Material.SNOW_BLOCK ? Sound.BLOCK_SNOW_BREAK
                        : Sound.BLOCK_GLASS_BREAK, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------
    //  Quedas
    // ------------------------------------------------------------------

    private void conferirQuedas(World mundo, SpleefCatalog planta) {
        if (vivos.isEmpty()) {
            return;
        }
        List<Player> caidos = new ArrayList<>();
        for (UUID id : vivos) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !jogador.getWorld().equals(mundo)) {
                continue;
            }
            if (jogador.getLocation().getY() < planta.quedaY()) {
                caidos.add(jogador);
            }
        }
        for (Player jogador : caidos) {
            eliminar(jogador, planta);
        }
    }

    // ------------------------------------------------------------------
    //  Quem fica parado
    // ------------------------------------------------------------------

    /**
     * O gelo range sob quem fica parado, e cede alguns segundos depois.
     *
     * É a resposta ao defeito estrutural do Spleef: aqui, ao contrário do TNT
     * Run, ficar parado é SEGURO — o chão só some se alguém cavar. Sem isto,
     * dois jogadores em pontas opostas que não queiram arriscar empatam para
     * sempre, e a partida acaba quando alguém desliga o computador.
     *
     * <p>O aviso vem quatro segundos antes de o bloco ceder de propósito: a
     * punição tem de ser evitável, senão não é regra, é armadilha.
     */
    private void conferirParados(World mundo, SpleefCatalog planta) {
        if (estado != Estado.JOGANDO) {
            return;
        }
        Set<Material> doPiso = planta.materiaisDoPiso();

        for (UUID id : new ArrayList<>(vivos)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !jogador.getWorld().equals(mundo)) {
                continue;
            }
            Location onde = jogador.getLocation();
            double[] antes = ondeEstava.get(id);
            ondeEstava.put(id, new double[]{onde.getX(), onde.getZ()});

            boolean parado = antes != null && ArenaComum.ficouParado(
                    onde.getX(), onde.getZ(), antes[0], antes[1], jogador.isSneaking());
            if (!parado) {
                paradoHa.remove(id);
                continue;
            }

            int ha = paradoHa.merge(id, 1, Integer::sum);
            if (ha == planta.paradoAviso()) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:#7FD8FF>o gelo range sob seus pés</color>"));
                jogador.playSound(onde, Sound.BLOCK_GLASS_HIT, 0.7f, 0.6f);
                mundo.spawnParticle(Particle.SNOWFLAKE, onde.getX(), onde.getY() + 0.1,
                        onde.getZ(), 10, 0.3, 0.05, 0.3, 0.01);
            } else if (ha >= planta.paradoRacha()) {
                paradoHa.remove(id);
                Block pe = mundo.getBlockAt(onde.getBlockX(), planta.topoY(),
                        onde.getBlockZ());
                if (doPiso.contains(pe.getType())) {
                    cavar(mundo, pe, jogador);
                    jogador.sendActionBar(plugin.mini().deserialize(
                            "<color:#FF8A8A>o gelo cedeu</color>"));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  O aperto
    // ------------------------------------------------------------------

    /**
     * A arena perde um anel de tantos em tantos segundos.
     *
     * Só apaga material do piso: o muro está em raio+1 e em diante, e o aperto
     * nunca chega nele — mas a conferência fica, porque é ela que garante que
     * um erro de conta aqui não come a obra.
     */
    private void apertar(World mundo, SpleefCatalog planta) {
        int alvo = SpleefCatalog.raioDoAperto(segundosDeJogo, planta.raio(),
                planta.apertoSegundos(), planta.apertoIntervalo());
        if (alvo >= raioAtual) {
            return;
        }
        boolean primeiraVez = raioAtual >= planta.raio();
        raioAtual = alvo;

        Set<Material> doPiso = planta.materiaisDoPiso();
        int y = planta.topoY();
        int raio = planta.raio();
        int sumiram = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!SpleefCatalog.foraDoApertoAtual(x, z, alvo, raio)) {
                    continue;
                }
                Block bloco = mundo.getBlockAt(x, y, z);
                if (doPiso.contains(bloco.getType())) {
                    bloco.setType(Material.AIR, false);
                    sumiram++;
                }
            }
        }
        if (sumiram == 0) {
            return;
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 0.7f);
        }
        if (primeiraVez) {
            anunciar("<color:#7FD8FF>a arena começou a encolher</color>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.showTitle(Title.title(Component.empty(),
                        plugin.mini().deserialize(
                                "<color:#7FD8FF>a arena está encolhendo</color>")));
            }
        }
    }

    // ------------------------------------------------------------------
    //  A máquina de estados
    // ------------------------------------------------------------------

    private void cicloDeSegundo(World mundo, SpleefCatalog planta) {
        desenharALateral(plugin.spleefCatalog());
        switch (estado) {
            case ESPERANDO -> {
                destravarEspectadores(planta);
                if (fila.size() >= planta.minimo()) {
                    estado = Estado.CONTAGEM;
                    contagem = planta.contagemSegundos();
                    anunciar("<gray>a rodada começa em <white>" + contagem
                            + "</white> segundos</gray>");
                }
            }
            case CONTAGEM -> {
                if (fila.size() < planta.minimo()) {
                    estado = Estado.ESPERANDO;
                    anunciar("<gray>a fila esvaziou — a contagem parou</gray>");
                    return;
                }
                contagem--;
                if (ArenaComum.anunciaContagem(contagem)) {
                    for (Player jogador : jogadoresDaArena()) {
                        jogador.showTitle(Title.title(
                                plugin.mini().deserialize("<color:"
                                        + planta.corDoTexto() + ">" + contagem + "</color>"),
                                Component.empty()));
                        jogador.playSound(jogador.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f,
                                contagem <= 3 ? 1.8f : 1.2f);
                    }
                }
                if (contagem <= 0) {
                    comecar(planta);
                }
            }
            case PREPARO -> {
                preparo--;
                if (preparo <= 0) {
                    abrirARodada(planta);
                    return;
                }
                for (Player jogador : jogadoresDaArena()) {
                    jogador.showTitle(Title.title(
                            plugin.mini().deserialize("<color:"
                                    + planta.corDoTexto() + ">" + preparo
                                    + "</color>"),
                            plugin.mini().deserialize(
                                    "<gray>prepare-se</gray>")));
                    jogador.playSound(jogador.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f,
                            1.0f + (3 - Math.min(3, preparo)) * 0.2f);
                }
            }
            case JOGANDO -> {
                segundosDeJogo++;
                reporOEstoque(planta);
                conferirParados(mundo, planta);
                apertar(mundo, planta);
                if (segundosDeJogo >= planta.tempoLimite()) {
                    anunciar("<gray>o tempo acabou</gray>");
                    terminar(planta);
                }
            }
            case FIM -> {
                contagem--;
                if (contagem <= 0) {
                    voltarAoSaguao(planta);
                }
            }
        }
    }

    /**
     * Em espera, ninguém fica preso em espectador.
     *
     * Dois casos chegam aqui: quem entrou antes de a arena existir e foi posto
     * em espectador para não cair no void, e quem foi eliminado numa rodada
     * que acabou de um jeito torto. Nenhum dos dois tem como se destravar
     * sozinho, e espectador para sempre é, para ele, um jogo quebrado.
     */
    private void destravarEspectadores(SpleefCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null || !arenaConstruida(mundo, planta)) {
            return;
        }
        Location saguao = SpleefBuilder.pontoDoSaguao(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            jogador.setGameMode(MODO_DE_JOGO);
            jogador.setFallDistance(0f);
            JogoDeArena.velocidadeNormal(jogador);
            jogador.getInventory().clear();
            jogador.teleport(saguao);
        }
    }

    // ------------------------------------------------------------------
    //  As placas
    // ------------------------------------------------------------------

    private void entrarNaFila(Player jogador) {
        SpleefCatalog planta = plugin.spleefCatalog();
        if (!naArena.contains(jogador.getUniqueId())) {
            entrou(jogador);
        }
        if (!arenaConstruida(jogador.getWorld(), planta)) {
            aviso(jogador, "<gray>a arena ainda não foi construída</gray>",
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
            return;
        }
        if (estado == Estado.PREPARO || estado == Estado.JOGANDO
                || estado == Estado.FIM) {
            aviso(jogador, "<gray>a rodada já começou — entre na próxima</gray>",
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
            return;
        }
        if (fila.contains(jogador.getUniqueId())) {
            aviso(jogador, "<gray>você já está na fila <white>(" + fila.size() + "/"
                    + planta.maximo() + ")</white></gray>",
                    Sound.BLOCK_NOTE_BLOCK_HAT, 1.2f);
            return;
        }
        if (fila.size() >= planta.maximo()) {
            aviso(jogador, "<gray>a fila está cheia <white>(" + planta.maximo()
                    + ")</white></gray>", Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
            return;
        }

        fila.add(jogador.getUniqueId());
        if (jogador.getGameMode() == GameMode.SPECTATOR) {
            jogador.setGameMode(MODO_DE_JOGO);
            jogador.teleport(SpleefBuilder.pontoDoSaguao(jogador.getWorld(), planta));
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
        anunciar("<color:" + planta.corDoTexto() + ">"
                + HubBuilder.escapar(jogador.getName()) + "</color> <gray>entrou na fila "
                + "<white>" + fila.size() + "/" + planta.maximo() + "</white>"
                + (fila.size() < planta.minimo()
                        ? " — faltam <white>" + (planta.minimo() - fila.size())
                                + "</white> para começar"
                        : "") + "</gray>");
        atualizarPlacas(jogador.getWorld(), planta);
    }

    private void sairDaFila(Player jogador) {
        SpleefCatalog planta = plugin.spleefCatalog();
        if (fila.remove(jogador.getUniqueId())) {
            if (estado == Estado.JOGANDO && vivos.contains(jogador.getUniqueId())) {
                eliminar(jogador, planta);
            }
            aviso(jogador, "<gray>você saiu da fila</gray>",
                    Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f);
            atualizarPlacas(jogador.getWorld(), planta);
            return;
        }
        voltarAoLobby(jogador);
    }

    private void aviso(Player jogador, String mini, Sound som, float tom) {
        jogador.sendActionBar(plugin.mini().deserialize(mini));
        jogador.playSound(jogador.getLocation(), som, 0.6f, tom);
    }

    /** Reescreve as duas placas, e só quando o texto muda. */
    private void atualizarPlacas(World mundo, SpleefCatalog planta) {
        String estadoEscrito = switch (estado) {
            case ESPERANDO -> planta.placa("esperando", "esperando");
            case CONTAGEM -> "em " + contagem + "s";
            case PREPARO -> "prepare-se";
            case JOGANDO -> planta.placa("jogando", "JOGANDO");
            case FIM -> planta.placa("refazendo", "refazendo");
        };
        String contagemEscrita = (estado == Estado.JOGANDO
                || estado == Estado.PREPARO ? vivos.size() : fila.size())
                + "/" + planta.maximo();
        String assinatura = estadoEscrito + "|" + contagemEscrita;
        if (assinatura.equals(ultimoTextoDaPlaca)) {
            return;
        }
        ultimoTextoDaPlaca = assinatura;

        String cor = planta.corDoTexto();
        boolean aberta = estado == Estado.ESPERANDO || estado == Estado.CONTAGEM;
        Supplier<Component> titulo = () -> plugin.mini().deserialize("<color:" + cor
                + "><bold>" + HubBuilder.escapar(planta.titulo()) + "</bold></color>");

        SpleefBuilder.escreverPlaca(mundo, planta,
                SpleefBuilder.pontoDaPlacaEntrar(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize((aberta ? "<green>" : "<dark_gray>")
                                + HubBuilder.escapar(planta.placa("entrar", "[ENTRAR]"))),
                        plugin.mini().deserialize("<gray>" + estadoEscrito),
                        plugin.mini().deserialize((aberta ? "<white>" : "<red>")
                                + contagemEscrita)));

        SpleefBuilder.escreverPlaca(mundo, planta,
                SpleefBuilder.pontoDaPlacaSair(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize("<red>"
                                + HubBuilder.escapar(planta.placa("sair", "[SAIR]"))),
                        plugin.mini().deserialize("<gray>sai da fila"),
                        plugin.mini().deserialize("<dark_gray>ou volta ao lobby")));
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    /** Começa a rodada na hora. É o {@code /biga spleef comecar}. */
    public String comecarAgora() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return "O mundo da arena não está carregado.";
        }
        if (estado == Estado.JOGANDO || estado == Estado.PREPARO) {
            return "A rodada já está rolando.";
        }
        SpleefCatalog planta = plugin.spleefCatalog();
        if (!arenaConstruida(mundo, planta)) {
            return "A arena ainda não foi construída.";
        }
        if (fila.isEmpty()) {
            for (Player jogador : jogadoresDaArena()) {
                if (jogador.getGameMode() == GameMode.SURVIVAL
                        || jogador.getGameMode() == GameMode.ADVENTURE) {
                    fila.add(jogador.getUniqueId());
                }
            }
        }
        if (fila.isEmpty()) {
            return "Não há ninguém no saguão em modo de jogo. "
                    + "Quem está em criativo não é inscrito automaticamente.";
        }
        comecar(planta);
        return "Rodada começada com " + comecouCom + " jogador(es).";
    }

    private void comecar(SpleefCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.spleefBuilder().refazerPiso(mundo);
        vivos.clear();
        paradoHa.clear();
        ondeEstava.clear();
        cavou.clear();
        raioAtual = planta.raio();
        segundosDeJogo = 0;

        List<Player> entram = new ArrayList<>();
        for (Player jogador : naFila()) {
            if (entram.size() < planta.maximo()) {
                entram.add(jogador);
            } else {
                jogador.setGameMode(GameMode.SPECTATOR);
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<gray>a rodada está cheia — você assiste esta</gray>"));
            }
        }

        List<int[]> pontos = ArenaComum.largadas(entram.size(), planta.raio());
        for (int i = 0; i < entram.size(); i++) {
            Player jogador = entram.get(i);
            jogador.setGameMode(MODO_DE_JOGO);
            jogador.setFallDistance(0f);
            jogador.teleport(SpleefBuilder.pontoDaLargada(mundo, planta, pontos.get(i)));
            darOKit(jogador, planta);
            vivos.add(jogador.getUniqueId());
        }

        comecouCom = vivos.size();
        estado = Estado.PREPARO;
        preparo = planta.preparoSegundos();

        // O relógio do jogo só começa no fim do preparo: sem isso a largada
        // põe o jogador num chão que já está sumindo, e ele perde antes de
        // ver onde está.
        for (Player jogador : entram) {
            jogador.setWalkSpeed(0f);
            jogador.setFlySpeed(0f);
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<color:" + planta.corDoTexto()
                            + "><bold>PREPARE-SE</bold></color>"),
                    plugin.mini().deserialize("<gray>" + preparo
                            + " segundos para a largada</gray>")));
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.8f);
        }
    }

    /** Abre a rodada de verdade, no fim do preparo. */
    private void abrirARodada(SpleefCatalog planta) {
        for (Player jogador : jogadoresDaArena()) {
            JogoDeArena.velocidadeNormal(jogador);
        }
        inicioDaRodada = pulso;
        estado = Estado.JOGANDO;

        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<color:" + planta.corDoTexto()
                            + "><bold>CAVE!</bold></color>"),
                    plugin.mini().deserialize(comecouCom == 1
                            ? "<gray>treino: cave e tente não cair</gray>"
                            : "<gray>" + comecouCom + " na arena</gray>")));
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                    0.5f, 1.4f);
        }
    }

    private void eliminar(Player jogador, SpleefCatalog planta) {
        vivos.remove(jogador.getUniqueId());
        jogador.getInventory().clear();
        paradoHa.remove(jogador.getUniqueId());
        ondeEstava.remove(jogador.getUniqueId());
        jogador.setFallDistance(0f);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(SpleefBuilder.pontoDaPlateia(jogador.getWorld(), planta));

        String aguentou = ArenaComum.tempo(pulso - inicioDaRodada);
        int buracos = cavou.getOrDefault(jogador.getUniqueId(), 0);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:#8FD3FF>você caiu</color>"),
                plugin.mini().deserialize("<gray>aguentou <white>" + aguentou
                        + "</white> · cavou <white>" + buracos + "</white></gray>")));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 0.8f);

        if (comecouCom > 1) {
            anunciar("<color:#8FD3FF>" + HubBuilder.escapar(jogador.getName())
                    + "</color> <gray>caiu — faltam <white>" + vivos.size()
                    + "</white></gray>");
        }
    }

    private void terminar(SpleefCatalog planta) {
        estado = Estado.FIM;
        contagem = planta.fimSegundos();
        String tempo = ArenaComum.tempo(pulso - inicioDaRodada);

        if (vivos.size() == 1) {
            Player campeao = Bukkit.getPlayer(vivos.iterator().next());
            String nome = campeao == null ? "alguém" : campeao.getName();
            int buracos = campeao == null ? 0
                    : cavou.getOrDefault(campeao.getUniqueId(), 0);
            anunciar("<color:" + planta.corDoTexto() + "><bold>"
                    + HubBuilder.escapar(nome) + "</bold></color> <gray>ganhou em <white>"
                    + tempo + "</white> cavando <white>" + buracos
                    + "</white> blocos</gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.showTitle(Title.title(
                        plugin.mini().deserialize("<color:" + planta.corDoTexto() + ">"
                                + HubBuilder.escapar(nome) + "</color>"),
                        plugin.mini().deserialize("<gray>último de pé · " + tempo
                                + "</gray>")));
                jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                        0.6f, 1.0f);
            }
            if (campeao != null) {
                campeao.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        campeao.getLocation().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.2);
            }
            return;
        }

        if (comecouCom == 1) {
            anunciar("<gray>treino encerrado: <white>" + tempo + "</white> de pé</gray>");
        } else {
            anunciar("<gray>ninguém sobrou — rodada sem vencedor</gray>");
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<gray>" + tempo + "</gray>"),
                    plugin.mini().deserialize(comecouCom == 1
                            ? "<gray>seu tempo</gray>" : "<gray>sem vencedor</gray>")));
        }
    }

    private void voltarAoSaguao(SpleefCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.spleefBuilder().refazerPiso(mundo);
        vivos.clear();
        paradoHa.clear();
        ondeEstava.clear();
        fila.clear();
        raioAtual = planta.raio();
        segundosDeJogo = 0;
        estado = Estado.ESPERANDO;

        Location saguao = SpleefBuilder.pontoDoSaguao(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            jogador.setGameMode(MODO_DE_JOGO);
            jogador.setFallDistance(0f);
            JogoDeArena.velocidadeNormal(jogador);
            jogador.getInventory().clear();
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>clique em <green>" + HubBuilder.escapar(
                            planta.placa("entrar", "[ENTRAR]"))
                            + "</green> para jogar de novo</gray>"));
        }
    }

    /** Para a rodada sem vencedor. É o {@code /biga spleef parar}. */
    public String pararRodada() {
        if (estado != Estado.JOGANDO && estado != Estado.CONTAGEM
                && estado != Estado.PREPARO) {
            return "Não há rodada para parar.";
        }
        anunciar("<gray>a rodada foi parada pela administração</gray>");
        voltarAoSaguao(plugin.spleefCatalog());
        return "Rodada parada.";
    }

    // ------------------------------------------------------------------
    //  A volta ao lobby
    // ------------------------------------------------------------------

    private void cortinaDaVolta(World mundo, SpleefCatalog planta) {
        if (jogadoresDaArena().isEmpty()) {
            return;
        }
        int z = SpleefBuilder.zDoArcoDeVolta(planta);
        int y = planta.saguaoY();
        Color cor = HubPortais.corDe(planta.corDoTexto());
        Particle.DustOptions po = new Particle.DustOptions(cor, 1.4f);

        boolean perto = false;
        for (Player jogador : mundo.getPlayers()) {
            if (jogador.getLocation().distanceSquared(
                    new Location(mundo, 0.5, y + 2, z + 0.5))
                    <= ALCANCE_DA_CORTINA * ALCANCE_DA_CORTINA) {
                perto = true;
                break;
            }
        }
        if (!perto) {
            return;
        }
        for (int x = -1; x <= 1; x++) {
            for (int alto = 1; alto < SpleefBuilder.alturaDoArcoDeVolta(); alto++) {
                if ((x + alto) % 2 != 0) {
                    continue;
                }
                mundo.spawnParticle(Particle.DUST, x + 0.5, y + alto + 0.5, z + 0.5,
                        1, 0.1, 0.2, 0.1, 0, po);
            }
        }
    }

    private void conferirVolta(World mundo, SpleefCatalog planta) {
        int z = SpleefBuilder.zDoArcoDeVolta(planta);
        long agora = System.currentTimeMillis();

        for (Player jogador : jogadoresDaArena()) {
            if (estado == Estado.JOGANDO && vivos.contains(jogador.getUniqueId())) {
                continue;
            }
            Long antes = ultimaVolta.get(jogador.getUniqueId());
            if (antes != null && agora - antes < 3000L) {
                continue;
            }
            Location onde = jogador.getLocation();
            if (!SpleefBuilder.dentroDaVolta(onde.getX(), onde.getY(), onde.getZ(),
                    planta.saguaoY(), z)) {
                continue;
            }
            ultimaVolta.put(jogador.getUniqueId(), agora);
            voltarAoLobby(jogador);
        }
    }

    private void voltarAoLobby(Player jogador) {
        String nome = plugin.getConfig().getString("minigames.mundo", "minigames");
        World lobby = Bukkit.getWorld(nome);
        if (lobby == null) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>o lobby não está carregado agora</color>"));
            return;
        }
        devolverModo(jogador);
        jogador.setFallDistance(0f);
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
        jogador.teleportAsync(lobby.getSpawnLocation().clone().add(0.5, 0, 0.5));
    }

    private void resgatarDoVoid(World mundo, SpleefCatalog planta) {
        if (!arenaConstruida(mundo, planta)) {
            return;
        }
        Location saguao = SpleefBuilder.pontoDoSaguao(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            if (vivos.contains(jogador.getUniqueId())) {
                continue;
            }
            if (jogador.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (jogador.getLocation().getY() >= planta.quedaY()) {
                continue;
            }
            jogador.setFallDistance(0f);
            JogoDeArena.velocidadeNormal(jogador);
            jogador.getInventory().clear();
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#8FD3FF>o saguão te pegou no ar</color>"));
        }
    }

    // ------------------------------------------------------------------
    //  Barra e avisos
    // ------------------------------------------------------------------

    private void atualizarBarra(SpleefCatalog planta) {
        if (barra == null) {
            return;
        }
        String cor = planta.corDoTexto();
        switch (estado) {
            case ESPERANDO -> {
                int prontos = fila.size();
                barra.name(plugin.mini().deserialize("<color:" + cor + ">"
                        + HubBuilder.escapar(planta.titulo())
                        + "</color> <gray>esperando <white>" + prontos + "/"
                        + planta.minimo() + "</white></gray>"));
                barra.progress(ArenaComum.limite(prontos / (float) planta.minimo()));
                barra.color(BossBar.Color.WHITE);
            }
            case CONTAGEM -> {
                barra.name(plugin.mini().deserialize("<color:" + cor
                        + ">começa em <white>" + contagem + "</white></color>"));
                barra.progress(ArenaComum.limite(
                        contagem / (float) planta.contagemSegundos()));
                barra.color(BossBar.Color.YELLOW);
            }
            case JOGANDO -> {
                boolean apertando = raioAtual < planta.raio();
                barra.name(plugin.mini().deserialize("<color:" + cor + "><white>"
                        + vivos.size() + "</white> de pé</color> <gray>· "
                        + ArenaComum.tempo(pulso - inicioDaRodada)
                        + (apertando ? " · <color:#7FD8FF>raio " + raioAtual + "</color>"
                                : "") + "</gray>"));
                barra.progress(ArenaComum.limite(
                        vivos.size() / (float) Math.max(1, comecouCom)));
                barra.color(apertando ? BossBar.Color.RED : BossBar.Color.BLUE);
            }
            case FIM -> {
                barra.name(plugin.mini().deserialize("<gray>fim da rodada</gray>"));
                barra.progress(ArenaComum.limite(contagem / (float) planta.fimSegundos()));
                barra.color(BossBar.Color.GREEN);
            }
        }
    }

    private List<Player> naFila() {
        List<Player> saida = new ArrayList<>();
        for (UUID id : fila) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    private List<Player> jogadoresDaArena() {
        List<Player> saida = new ArrayList<>();
        for (UUID id : naArena) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    private void anunciar(String mini) {
        Component texto = plugin.mini().deserialize(mini);
        for (Player jogador : jogadoresDaArena()) {
            jogador.sendMessage(texto);
        }
    }

    // ------------------------------------------------------------------
    //  Eventos
    // ------------------------------------------------------------------

    private boolean naArena(Player jogador) {
        return naArena.contains(jogador.getUniqueId());
    }

    private boolean nesteMundo(World mundo) {
        World arena = mundoDaArena();
        return arena != null && arena.equals(mundo);
    }

    /** O que não se digita aqui dentro — ver {@link ComandosDaArena}. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoDigitarComando(PlayerCommandPreprocessEvent evento) {
        Player jogador = evento.getPlayer();
        if (!nesteMundo(jogador.getWorld())) {
            return;
        }
        String raiz = ComandosDaArena.raiz(evento.getMessage());
        if (!ComandosDaArena.lista(plugin).contains(raiz)) {
            return;
        }
        evento.setCancelled(true);
        JogoDeArena.velocidadeNormal(jogador);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>/" + HubBuilder.escapar(raiz)
                        + " não vale dentro do minigame</color>"));
        jogador.playSound(jogador.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoTrocarDeMundo(PlayerChangedWorldEvent evento) {
        Player jogador = evento.getPlayer();
        if (nesteMundo(jogador.getWorld())) {
            entrou(jogador);
        } else if (nesteMundo(evento.getFrom())) {
            saiu(jogador.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        if (nesteMundo(evento.getPlayer().getWorld())) {
            entrou(evento.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoDeslogar(PlayerQuitEvent evento) {
        UUID id = evento.getPlayer().getUniqueId();
        naArena.remove(id);
        fila.remove(id);
        vivos.remove(id);
        ultimaVolta.remove(id);
        modoAnterior.remove(id);
        paradoHa.remove(id);
        ondeEstava.remove(id);
    }

    /**
     * O clique: direito nas placas, esquerdo no gelo.
     *
     * Os dois no mesmo evento porque é o mesmo evento — e assim não há duas
     * conferências de mundo e de estado podendo divergir uma da outra.
     *
     * <p>O gelo é quebrado AQUI, no interagir, e não no quebrar bloco. Em
     * aventura o jogador não quebra nada por conta própria: o servidor é quem
     * apaga o bloco, na hora, com a arena inteira conferida antes. É a mesma
     * escolha do plugin de referência ("instant break") e ela tem uma
     * vantagem que não é de desempenho: não existe o estado intermediário de
     * "bloco sendo quebrado", onde entram dano de ferramenta, item caído e
     * quebra parcial atravessando o fim da rodada.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicar(PlayerInteractEvent evento) {
        Block bloco = evento.getClickedBlock();
        if (bloco == null || !nesteMundo(bloco.getWorld())) {
            return;
        }
        Player jogador = evento.getPlayer();
        SpleefCatalog planta = plugin.spleefCatalog();

        if (evento.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (estado != Estado.JOGANDO || !vivos.contains(jogador.getUniqueId())) {
                return;
            }
            if (!ehChaoCavavel(bloco.getType(), bloco.getY(), planta.topoY(),
                    planta.materiaisDoPiso())) {
                return;
            }
            // Só a pá cava. Com a bola de neve na mão você empurra, e
            // naquele instante NÃO cava — é a troca de item que o jogo
            // não tinha e que dá o que decidir.
            if (jogador.getInventory().getItemInMainHand().getType()
                    != planta.ferramenta()) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<gray>o gelo só cede para a <white>pá</white></gray>"));
                return;
            }
            evento.setCancelled(true);
            cavar(bloco.getWorld(), bloco, jogador);
            return;
        }

        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK
                || evento.getHand() != EquipmentSlot.HAND
                || !(bloco.getState() instanceof Sign)) {
            return;
        }
        if (ArenaComum.mesmoBloco(bloco, SpleefBuilder.pontoDaPlacaEntrar(planta))) {
            evento.setCancelled(true);
            entrarNaFila(jogador);
        } else if (ArenaComum.mesmoBloco(bloco, SpleefBuilder.pontoDaPlacaSair(planta))) {
            evento.setCancelled(true);
            sairDaFila(jogador);
        }
    }

    /** Dano nenhum: a mecânica do jogo é cair, e cair não pode doer. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player jogador && naArena(jogador)) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoPerderFome(FoodLevelChangeEvent evento) {
        if (evento.getEntity() instanceof Player jogador && naArena(jogador)) {
            evento.setFoodLevel(20);
            evento.setCancelled(true);
        }
    }

    /**
     * Quebrar bloco continua proibido, inclusive o gelo.
     *
     * Parece contradizer o jogo e não contradiz: cavar não passa por aqui. Em
     * aventura este evento nem chega a existir para o jogador comum, e se
     * chegar — outro plugin, outro modo — é quebra de verdade, com item caído
     * e tudo, que é justamente o que o jogo não quer.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (nesteMundo(evento.getBlock().getWorld())
                && !evento.getPlayer().hasPermission("bigacore.admin")) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (nesteMundo(evento.getBlock().getWorld())
                && !evento.getPlayer().hasPermission("bigacore.admin")) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoLargarItem(PlayerDropItemEvent evento) {
        if (naArena(evento.getPlayer())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoNascerBicho(CreatureSpawnEvent evento) {
        if (nesteMundo(evento.getEntity().getWorld())
                && !RegrasDoHub.nascimentoEhDeAdmin(evento.getSpawnReason())) {
            evento.setCancelled(true);
        }
    }

    /**
     * As regras, no SAGUÃO e para um jogador só.
     *
     * Não na largada: quatro parágrafos no bate-papo no instante em que o
     * chão começa a sumir não são lidos por ninguém. Quem precisa das
     * regras está parado aqui esperando a fila encher.
     */
    private void instrucoes(Player jogador) {
        SpleefCatalog planta = plugin.spleefCatalog();
        dizer(jogador, "<gray>o chão <white>não some sozinho</white>: alguém "
                + "tem de cavar. A <white>pá de ferro</white> é a única coisa "
                + "que abre o gelo — com a bola de neve na mão você não "
                + "cava.</gray>");
        dizer(jogador, "<gray>a <white>bola de neve</white> não acaba e não "
                + "machuca: ela <white>empurra</white>. Empurrar alguém para "
                + "cima de um buraco que você cavou é a jogada.</gray>");
        dizer(jogador, "<gray>ficar parado <white>racha o gelo</white> debaixo "
                + "de você em <white>" + planta.paradoRacha() + "s</white>, e "
                + "depois de <white>" + planta.apertoSegundos() + "s</white> a "
                + "arena começa a <white>encolher</white> — toda partida "
                + "termina.</gray>");
    }

    /** Uma linha de instrução, no bate-papo de um jogador só. */
    private void dizer(Player jogador, String mini) {
        jogador.sendMessage(plugin.mini().deserialize(mini));
    }
}
