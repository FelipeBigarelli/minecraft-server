package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

/**
 * As regras de quem está no hub: corre rápido, não passa fome, não apanha e
 * não encontra bicho.
 *
 * <h2>Por que isto é código e não flag de região</h2>
 *
 * PvP, dano e mob a WorldGuard faz. VELOCIDADE não: não existe flag de poção
 * no WorldGuard, e a solução de todo mundo é instalar mais um plugin só para
 * isso. Como o hub já é nosso, a regra mora junto com ele — e o hub.yml passa
 * a ser o único lugar onde se mexe nisso.
 *
 * <h2>O cuidado que importa</h2>
 *
 * O efeito SAI quando o jogador deixa o hub. Velocidade III vazando para o
 * survival não é enfeite: é vantagem permanente em PvP e em fuga, dada por
 * engano, e o tipo de coisa que ninguém reporta — só aproveita.
 *
 * Por isso também a poção é CURTA e renovada pela tarefa, em vez de infinita.
 * Se o plugin cair, o servidor reiniciar no meio ou alguém desligar a regra,
 * o efeito morre sozinho em quinze segundos em vez de ficar grudado no
 * jogador para sempre.
 */
public final class RegrasDoHub implements Listener {

    /** De quanto em quanto a regra é reaplicada. */
    private static final int INTERVALO_TICKS = 40;

    /** Quanto a poção dura. Mais que o intervalo, para não piscar. */
    private static final int DURACAO_TICKS = 20 * 15;

    private final BigaCore plugin;
    private BukkitTask tarefa;

    /**
     * Se as regras do MUNDO já foram gravadas nesta subida.
     *
     * O mundo do hub é carregado pelo Multiverse, que pode habilitar depois
     * do BigaCore — então não dá para gravar isso no onEnable: o mundo ainda
     * não existe. A tarefa grava na primeira vez que o enxerga.
     */
    private boolean mundoAjustado;

    public RegrasDoHub(BigaCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parar();
        this.mundoAjustado = false;
        this.tarefa = Bukkit.getScheduler().runTaskTimer(
                plugin, this::ciclo, 80L, INTERVALO_TICKS);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        // Devolver o jogador ao normal ao desligar o plugin: senão um
        // /reload deixa Speed III preso em quem estava no hub na hora.
        World hub = mundoDoHub();
        if (hub != null) {
            for (Player jogador : hub.getPlayers()) {
                jogador.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    private World mundoDoHub() {
        String nome = plugin.getConfig().getString("hub.mundo", "hub");
        return Bukkit.getWorld(nome);
    }

    private boolean noHub(Entity quem) {
        World hub = mundoDoHub();
        return hub != null && quem.getWorld().equals(hub);
    }

    // ------------------------------------------------------------------
    //  Velocidade e fome
    // ------------------------------------------------------------------

    /**
     * O amplificador da poção para um nível escrito como gente escreve.
     *
     * "Speed 3" no hub.yml é amplificador 2 no jogo. Trocar os dois é o erro
     * clássico: o hub ficaria um nível mais lento do que o arquivo diz, e
     * ninguém abriria a documentação do Bukkit para descobrir por quê.
     */
    static int amplificador(int nivel) {
        return Math.max(0, Math.min(9, nivel - 1));
    }

    /** Nível zero (ou negativo) é como se desliga a velocidade. */
    static boolean velocidadeLigada(int nivel) {
        return nivel > 0;
    }

    private void ciclo() {
        World hub = mundoDoHub();
        if (hub == null) {
            return;
        }
        if (!mundoAjustado) {
            ajustarMundo(hub, plugin.hubCatalog());
            mundoAjustado = true;
        }
        for (Player jogador : hub.getPlayers()) {
            aplicar(jogador);
        }
    }

    /**
     * As regras do mundo em si.
     *
     * O listener aqui embaixo já barra bicho, mas barrar DEPOIS de nascer
     * custa entidade criada e apagada a cada tique de spawn, a noite inteira,
     * num mundo onde ninguém veria nenhuma delas. A regra do mundo evita o
     * nascimento; o listener fica como rede, para spawner e plugin.
     */
    static void ajustarMundo(World mundo, HubCatalog planta) {
        if (planta.semMobs()) {
            mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        }
        if (planta.sempreDia()) {
            mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            mundo.setTime(6000L);
            mundo.setStorm(false);
        }
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
    }

    private void aplicar(Player jogador) {
        HubCatalog planta = plugin.hubCatalog();

        if (velocidadeLigada(planta.velocidade())) {
            // Sem partícula: o hub é branco e liso de propósito, e uma nuvem
            // de rodopios azuis em cima de cada jogador desmancha isso.
            jogador.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    DURACAO_TICKS, amplificador(planta.velocidade()),
                    false, false, true));
        }

        if (planta.semFome() && jogador.getFoodLevel() < 20) {
            jogador.setFoodLevel(20);
            jogador.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void aoEntrar(PlayerJoinEvent evento) {
        if (noHub(evento.getPlayer())) {
            aplicar(evento.getPlayer());
        }
    }

    /**
     * Sair do hub devolve o jogador ao normal.
     *
     * Vale até para quem bebeu poção de verdade antes de entrar: perder uma
     * poção é chateação, Velocidade III de graça no survival é economia
     * quebrada. O lado seguro é claro.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void aoTrocarDeMundo(PlayerChangedWorldEvent evento) {
        if (noHub(evento.getPlayer())) {
            aplicar(evento.getPlayer());
        } else {
            evento.getPlayer().removePotionEffect(PotionEffectType.SPEED);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoPerderFome(FoodLevelChangeEvent evento) {
        if (!plugin.hubCatalog().semFome() || !noHub(evento.getEntity())) {
            return;
        }
        evento.setFoodLevel(20);
        evento.setCancelled(true);
    }

    // ------------------------------------------------------------------
    //  Dano, PvP e bicho
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (!(evento.getEntity() instanceof Player) || !noHub(evento.getEntity())) {
            return;
        }
        if (plugin.hubCatalog().semDano()) {
            evento.setCancelled(true);
        }
    }

    /**
     * O PvP separado do dano de propósito.
     *
     * Quem um dia quiser uma arena no hub desliga o "sem-dano" e continua com
     * o soco entre jogadores barrado na praça — e não precisa de código novo
     * para isso.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoBaterEmAlguem(EntityDamageByEntityEvent evento) {
        if (!plugin.hubCatalog().semPvp()) {
            return;
        }
        if (!(evento.getEntity() instanceof Player) || !noHub(evento.getEntity())) {
            return;
        }
        if (agressorEhJogador(evento.getDamager())) {
            evento.setCancelled(true);
        }
    }

    /** Flecha, bola de neve e poção também são o jogador que atirou. */
    static boolean agressorEhJogador(Entity agressor) {
        if (agressor instanceof Player) {
            return true;
        }
        if (agressor instanceof Projectile flecha) {
            ProjectileSource dono = flecha.getShooter();
            return dono instanceof Player;
        }
        return false;
    }

    /**
     * Nenhum bicho nasce no hub.
     *
     * O que o admin colocar à mão continua valendo: ovo, comando e spawn de
     * plugin passam. O que se barra é o que nasce sozinho — e num mundo de
     * void isso é tudo que chega de noite na plataforma.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoNascerBicho(CreatureSpawnEvent evento) {
        if (!plugin.hubCatalog().semMobs() || !noHub(evento.getEntity())) {
            return;
        }
        if (nascimentoEhDeAdmin(evento.getSpawnReason())) {
            return;
        }
        evento.setCancelled(true);
    }

    static boolean nascimentoEhDeAdmin(CreatureSpawnEvent.SpawnReason motivo) {
        return motivo == CreatureSpawnEvent.SpawnReason.CUSTOM
                || motivo == CreatureSpawnEvent.SpawnReason.COMMAND
                || motivo == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || motivo == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG;
    }
}
