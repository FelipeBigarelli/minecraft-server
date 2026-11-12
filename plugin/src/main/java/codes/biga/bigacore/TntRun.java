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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Sign;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * O TNT Run: o chão some atrás de você, o último de pé ganha.
 *
 * <h2>A regra em uma frase, e o que ela exige do código</h2>
 *
 * O bloco em que o jogador pisa some meio segundo depois. Daí saem três
 * cuidados que não são óbvios:
 *
 * <p><b>O relógio do bloco não reinicia.</b> Se pisar de novo no mesmo bloco
 * recomeçasse a contagem, ficar PARADO seria a jogada ótima — o bloco sob os
 * pés nunca chegaria a zero e o jogo premiaria quem não joga. Por isso
 * {@link #registrar} só anota o bloco se ele ainda não estiver anotado.
 *
 * <p><b>Só derrete o que está debaixo de quem está EM PÉ.</b> Um jogador em
 * queda passa por cima de fiadas que pertencem a pisos inteiros; derreter o
 * que estiver sob ele no caminho abriria buracos aleatórios em pisos que
 * ninguém pisou. A porta é {@code isOnGround()} mais a conferência de que a
 * altura é inteira — duas travas porque a primeira é informada pelo cliente.
 *
 * <p><b>Só derrete os quatro materiais do piso.</b> Esta é a trava que
 * importa de verdade. O pior caso de um erro de uma linha na conta do bloco
 * do pé passa a ser um buraco a mais no chão, que a rodada seguinte refaz —
 * e não o saguão sendo comido de partida em partida sem ninguém entender por
 * quê.
 *
 * <h2>Por que um laço e não PlayerMoveEvent</h2>
 *
 * O mesmo motivo dos portais: PlayerMoveEvent dispara várias vezes por tique
 * por jogador, inclusive quando ele só vira a cabeça. Aqui o laço precisa
 * rodar de tique em tique de qualquer jeito, para descontar o relógio dos
 * blocos — então conferir o pé no mesmo laço é de graça.
 *
 * <h2>A largada é espalhada, não é queda do saguão</h2>
 *
 * Soltar dezesseis pessoas de uma caixa de dezenove de largura as faz chegar
 * em cima uma da outra, e o primeiro segundo da partida vira empurrão. No
 * anel de largada todos têm a mesma folga e a mesma distância da beira.
 */
public final class TntRun implements Listener {

    /** De quantos em quantos tiques o laço roda. Um: o derretimento é por tique. */
    private static final int INTERVALO_TICKS = 1;

    /** Até onde a cortina do arco de volta é desenhada. */
    private static final int ALCANCE_DA_CORTINA = 32;

    public enum Estado { ESPERANDO, CONTAGEM, JOGANDO, FIM }

    /** Um bloco do piso esperando a vez de sumir. */
    record Ponto(int x, int y, int z) {}

    /** Para o teste medir o bloco do pé sem mundo nenhum. */
    interface Solidez {
        boolean solido(int x, int y, int z);
    }

    private final BigaCore plugin;
    private BukkitTask tarefa;

    private Estado estado = Estado.ESPERANDO;
    private final Set<UUID> naArena = new LinkedHashSet<>();

    /**
     * A FILA: quem clicou na placa de entrar.
     *
     * Estar no saguão não é estar na rodada. O desenho anterior contava quem
     * estivesse no mundo, e isso tinha dois defeitos de uma vez: quem passava
     * para olhar era largado numa partida sem ter pedido, e não havia como
     * ver quantos faltavam para começar. A fila separa visitar de jogar, e a
     * placa é a única porta entre as duas.
     */
    private final Set<UUID> fila = new LinkedHashSet<>();
    private final Set<UUID> vivos = new LinkedHashSet<>();
    private final Map<UUID, GameMode> modoAnterior = new HashMap<>();
    private final Map<UUID, Long> ultimaVolta = new HashMap<>();
    private final Map<Ponto, Integer> derretendo = new HashMap<>();

    private int comecouCom;
    private int contagem;
    private long inicioDaRodada;
    private long pulso;
    private boolean mundoAjustado;
    private String ultimoTextoDaPlaca = "";
    private BossBar barra;

    public TntRun(BigaCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        parar();
        this.pulso = 0;
        this.mundoAjustado = false;
        this.barra = BossBar.bossBar(Component.empty(), 1f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        this.tarefa = Bukkit.getScheduler().runTaskTimer(
                plugin, this::ciclo, 100L, INTERVALO_TICKS);
    }

    public void parar() {
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
        // Devolver todo mundo ao normal: senão um /reload deixa quem estava
        // eliminado preso em espectador, e isso atravessa reinício.
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
        derretendo.clear();
        estado = Estado.ESPERANDO;
    }

    public Estado estado() {
        return estado;
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

    /** Se o chão do saguão existe. Barato, e é o que diz se há arena. */
    static boolean arenaConstruida(World mundo, TntRunCatalog planta) {
        return !mundo.getBlockAt(0, planta.saguaoY(), 0).getType().isAir();
    }

    /** O mundo da arena, ou null se ele não existe. */
    public World mundoDaArena() {
        return Bukkit.getWorld(plugin.tntRunCatalog().mundo());
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    private void ciclo() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        TntRunCatalog planta = plugin.tntRunCatalog();
        pulso++;

        if (!mundoAjustado) {
            ajustarMundo(mundo);
            mundoAjustado = true;
        }

        if (estado == Estado.JOGANDO) {
            pisarEDerreter(mundo, planta);
            if (TntRunCatalog.rodadaAcabou(vivos.size(), comecouCom,
                    planta.treinoSozinho())) {
                terminar(planta);
            }
        }

        // Arena vazia é o estado normal do servidor, e este laço roda de
        // tique em tique para sempre. Com ninguém dentro ele custa uma busca
        // de mundo num mapa: nem partícula, nem leitura de bloco, nem barra.
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
            cicloDeSegundo(planta);
            if (arenaConstruida(mundo, planta)) {
                atualizarPlacas(mundo, planta);
            }
        }
    }

    /**
     * As regras do MUNDO da arena.
     *
     * Dano de queda desligado na regra do mundo E no listener: o jogo é feito
     * de cair cinco blocos de propósito, e um dos dois falhando sozinho
     * transformaria a mecânica central em dano.
     */
    static void ajustarMundo(World mundo) {
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.FALL_DAMAGE, false);
        mundo.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        mundo.setTime(6000L);
        mundo.setStorm(false);
    }

    // ------------------------------------------------------------------
    //  Presença
    // ------------------------------------------------------------------

    /**
     * Confere quem está de fato no mundo.
     *
     * Os eventos de entrar e sair já cuidam do caso normal; isto é a rede.
     * Um teleporte feito por outro plugin, um mundo recarregado ou um evento
     * cancelado por terceiro deixariam a lista mentindo, e a lista mentindo
     * é jogador contado na rodada sem estar nela.
     */
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
        TntRunCatalog planta = plugin.tntRunCatalog();
        naArena.add(jogador.getUniqueId());
        modoAnterior.putIfAbsent(jogador.getUniqueId(), jogador.getGameMode());
        if (barra != null) {
            jogador.showBossBar(barra);
        }

        // Administrador voando não é jogador: nem vira aventura, nem conta
        // para o mínimo, nem é largado numa rodada.
        if (jogador.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Mundo sem arena: botar em espectador em vez de deixar cair no void
        // para sempre. É o estado em que o mundo nasce, antes do construir.
        if (!arenaConstruida(jogador.getWorld(), planta)) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendMessage(plugin.mini().deserialize(
                    "<gray>a arena do TNT Run ainda não foi construída — "
                            + "um admin precisa rodar <white>/biga tntrun construir "
                            + "CONFIRMAR</white></gray>"));
            return;
        }

        if (estado == Estado.JOGANDO || estado == Estado.FIM) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>a rodada já começou — você entra na próxima</gray>"));
            return;
        }
        jogador.setGameMode(GameMode.ADVENTURE);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:" + planta.corDoTexto() + ">"
                        + HubBuilder.escapar(planta.titulo()) + "</color>"),
                plugin.mini().deserialize("<gray>"
                        + HubBuilder.escapar(planta.subtitulo()) + "</gray>")));
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
    }

    private void saiu(UUID id) {
        naArena.remove(id);
        fila.remove(id);
        vivos.remove(id);
        ultimaVolta.remove(id);
        Player jogador = Bukkit.getPlayer(id);
        if (jogador != null) {
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
     * Não é detalhe: quem entra do lobby está em sobrevivência, e sair da
     * arena em ADVENTURE o deixaria sem poder quebrar bloco no survival —
     * reclamação que ninguém ligaria ao minigame de onde acabou de sair.
     */
    private void devolverModo(Player jogador) {
        GameMode antes = modoAnterior.remove(jogador.getUniqueId());
        if (antes != null && jogador.getGameMode() != antes) {
            jogador.setGameMode(antes);
        }
    }

    // ------------------------------------------------------------------
    //  O derretimento
    // ------------------------------------------------------------------

    /**
     * Se a altura do jogador é de alguém firme no chão.
     *
     * Em pé num bloco cheio o pé fica numa altura INTEIRA, exata. Em queda,
     * não. É a segunda trava: {@code isOnGround()} é informado pelo cliente,
     * e esta aqui é medida no servidor.
     */
    static boolean pisandoFirme(double y) {
        return Math.abs(y - Math.round(y)) < 0.02;
    }

    /**
     * A que piso pertence uma fiada, ou -1 se ela é ar entre pisos.
     *
     * É o que faz a coluna inteira sumir junto. Derreter só a capa de cima
     * deixaria o jogador em pé numa casca de areia, e derreter só a de baixo
     * deixaria o TNT flutuando.
     */
    static int camadaDaFiada(int y, int topoY, int passo, int espessura, int pisos) {
        for (int k = 0; k < pisos; k++) {
            int topo = topoY - k * passo;
            if (y <= topo && y > topo - espessura) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Os blocos que seguram o jogador.
     *
     * O do meio, normalmente. Se o meio for ar, é porque ele está equilibrado
     * na beira de um bloco vizinho — e aí derrete o primeiro vizinho que o
     * segura. Sem este desvio, parar com o corpo pela metade para fora de um
     * bloco seria um lugar seguro para sempre.
     *
     * <p>UM bloco por vez, nunca os quatro cantos de uma vez. É a convenção
     * do jogo clássico e não é detalhe de gosto: derretendo todo o apoio de
     * uma vez, correr em linha reta abriria uma trilha de dois de largura e o
     * chão acabaria no dobro da velocidade. Quem fica na quina perde os
     * apoios um por tique de qualquer jeito.
     *
     * <p>Só chame para jogador em pé: a fiada é {@code floor(y) - 1}, que para
     * quem está caindo aponta para o meio de um piso qualquer.
     */
    static List<int[]> blocosDoPe(double x, double y, double z, Solidez teste) {
        int fiada = (int) Math.floor(y) - 1;

        int mx = (int) Math.floor(x);
        int mz = (int) Math.floor(z);
        if (teste.solido(mx, fiada, mz)) {
            return List.of(new int[]{mx, fiada, mz});
        }

        // Meia largura do jogador: 0,3. São os quatro cantos da caixa dele.
        for (double ox : new double[]{-0.3, 0.3}) {
            for (double oz : new double[]{-0.3, 0.3}) {
                int nx = (int) Math.floor(x + ox);
                int nz = (int) Math.floor(z + oz);
                if (teste.solido(nx, fiada, nz)) {
                    return List.of(new int[]{nx, fiada, nz});
                }
            }
        }
        return List.of();
    }

    private void pisarEDerreter(World mundo, TntRunCatalog planta) {
        Set<Material> doPiso = planta.materiaisDoPiso();
        List<Player> caidos = new ArrayList<>();

        for (UUID id : vivos) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !jogador.getWorld().equals(mundo)) {
                continue;
            }
            Location onde = jogador.getLocation();
            if (onde.getY() < planta.quedaY()) {
                caidos.add(jogador);
                continue;
            }
            if (!jogador.isOnGround() || !pisandoFirme(onde.getY())) {
                continue;
            }
            for (int[] bloco : blocosDoPe(onde.getX(), onde.getY(), onde.getZ(),
                    (bx, by, bz) -> doPiso.contains(mundo.getBlockAt(bx, by, bz).getType()))) {
                registrar(planta, bloco[0], bloco[1], bloco[2]);
            }
        }

        for (Player jogador : caidos) {
            eliminar(jogador, planta);
        }
        derreter(mundo, planta, doPiso);
    }

    /**
     * Anota a coluna do piso para sumir, se ela ainda não estiver anotada.
     *
     * O {@code containsKey} é a regra do jogo, não uma otimização: sem ele o
     * relógio reiniciaria a cada tique em que o jogador continuasse no mesmo
     * bloco, e ficar parado seria imortalidade.
     */
    private void registrar(TntRunCatalog planta, int x, int fiada, int z) {
        int camada = camadaDaFiada(fiada, planta.topoY(), planta.passoEntrePisos(),
                planta.espessura(), planta.pisos());
        if (camada < 0) {
            return;
        }
        for (int y = planta.baseDoPiso(camada); y <= planta.topoDoPiso(camada); y++) {
            Ponto ponto = new Ponto(x, y, z);
            if (!derretendo.containsKey(ponto)) {
                derretendo.put(ponto, planta.derreteTicks());
            }
        }
    }

    private void derreter(World mundo, TntRunCatalog planta, Set<Material> doPiso) {
        if (derretendo.isEmpty()) {
            return;
        }
        List<Ponto> caem = new ArrayList<>();
        Iterator<Map.Entry<Ponto, Integer>> passo = derretendo.entrySet().iterator();
        while (passo.hasNext()) {
            Map.Entry<Ponto, Integer> item = passo.next();
            int resta = item.getValue() - 1;
            if (resta > 0) {
                item.setValue(resta);
                continue;
            }
            caem.add(item.getKey());
            passo.remove();
        }

        for (Ponto ponto : caem) {
            Block bloco = mundo.getBlockAt(ponto.x(), ponto.y(), ponto.z());
            if (!doPiso.contains(bloco.getType())) {
                continue;
            }
            boolean capa = ponto.y() == planta.topoDoPiso(camadaDaFiada(ponto.y(),
                    planta.topoY(), planta.passoEntrePisos(), planta.espessura(),
                    planta.pisos()));
            bloco.setType(Material.AIR, false);
            // Fumaça e barulho só na capa de cima: é a que se vê, e repetir nas
            // de baixo dobraria o pacote enviado sem mudar nada na tela.
            if (capa) {
                mundo.spawnParticle(Particle.SMOKE, ponto.x() + 0.5, ponto.y() + 0.6,
                        ponto.z() + 0.5, 2, 0.25, 0.1, 0.25, 0.01);
                mundo.playSound(bloco.getLocation(), Sound.BLOCK_SAND_BREAK, 0.35f, 1.2f);
            }
        }
    }

    // ------------------------------------------------------------------
    //  A máquina de estados
    // ------------------------------------------------------------------

    private void cicloDeSegundo(TntRunCatalog planta) {
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
                if (TntRunCatalog.anunciaContagem(contagem)) {
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
            case JOGANDO -> { }
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
     * sozinho — não há chão no saguão para andar até um botão —, e um jogador
     * preso em espectador para sempre é um jogo que, para ele, quebrou.
     *
     * <p>Quem quer só assistir usa CRIATIVO, que o jogo respeita; em espera,
     * espectador é sempre acidente.
     */
    private void destravarEspectadores(TntRunCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null || !arenaConstruida(mundo, planta)) {
            return;
        }
        Location saguao = TntRunBuilder.pontoDoSaguao(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            jogador.setGameMode(GameMode.ADVENTURE);
            jogador.setFallDistance(0f);
            jogador.teleport(saguao);
        }
    }

    // ------------------------------------------------------------------
    //  As placas
    // ------------------------------------------------------------------

    /**
     * Entra na fila. É o clique na placa de ENTRAR, e é a única porta.
     *
     * Recusa com motivo em vez de em silêncio: uma placa que não responde é
     * indistinguível de uma placa quebrada, e o jogador conclui que o jogo
     * não funciona em vez de que a rodada já começou.
     */
    private void entrarNaFila(Player jogador) {
        TntRunCatalog planta = plugin.tntRunCatalog();
        if (!naArena.contains(jogador.getUniqueId())) {
            entrou(jogador);
        }
        if (!arenaConstruida(jogador.getWorld(), planta)) {
            aviso(jogador, "<gray>a arena ainda não foi construída</gray>",
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
            return;
        }
        if (estado == Estado.JOGANDO || estado == Estado.FIM) {
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
            jogador.setGameMode(GameMode.ADVENTURE);
            jogador.teleport(TntRunBuilder.pontoDoSaguao(jogador.getWorld(), planta));
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

    /**
     * Sai da fila — e, para quem não estava nela, sai da arena.
     *
     * Dois passos na mesma placa de propósito: quem clicou em ENTRAR por
     * engano desfaz o engano, e quem só visitou tem um jeito óbvio de voltar
     * ao lobby sem precisar achar o arco.
     */
    private void sairDaFila(Player jogador) {
        TntRunCatalog planta = plugin.tntRunCatalog();
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

    /**
     * Reescreve as duas placas, e só quando o texto muda.
     *
     * Escrever placa manda pacote de bloco para quem está perto. De segundo em
     * segundo, com texto igual, seria pacote para nada o tempo todo — e a
     * comparação é uma linha.
     */
    private void atualizarPlacas(World mundo, TntRunCatalog planta) {
        String estadoEscrito = switch (estado) {
            case ESPERANDO -> planta.placa("esperando", "esperando");
            case CONTAGEM -> "em " + contagem + "s";
            case JOGANDO -> planta.placa("jogando", "JOGANDO");
            case FIM -> planta.placa("refazendo", "refazendo");
        };
        String contagemEscrita = (estado == Estado.JOGANDO ? vivos.size() : fila.size())
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

        TntRunBuilder.escreverPlaca(mundo, planta,
                TntRunBuilder.pontoDaPlacaEntrar(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize((aberta ? "<green>" : "<dark_gray>")
                                + HubBuilder.escapar(planta.placa("entrar", "[ENTRAR]"))),
                        plugin.mini().deserialize("<gray>" + estadoEscrito),
                        plugin.mini().deserialize((aberta ? "<white>" : "<red>")
                                + contagemEscrita)));

        TntRunBuilder.escreverPlaca(mundo, planta,
                TntRunBuilder.pontoDaPlacaSair(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize("<red>"
                                + HubBuilder.escapar(planta.placa("sair", "[SAIR]"))),
                        plugin.mini().deserialize("<gray>sai da fila"),
                        plugin.mini().deserialize("<dark_gray>ou volta ao lobby")));
    }

    /** Começa a rodada na hora. É o {@code /biga tntrun comecar}. */
    public String comecarAgora() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return "O mundo da arena não está carregado.";
        }
        if (estado == Estado.JOGANDO) {
            return "A rodada já está rolando.";
        }
        TntRunCatalog planta = plugin.tntRunCatalog();
        if (!arenaConstruida(mundo, planta)) {
            return "A arena ainda não foi construída.";
        }
        // Com a fila vazia, inscreve quem está no saguão em modo de jogo. É o
        // que deixa testar sozinho sem ir clicar na placa primeiro.
        if (fila.isEmpty()) {
            for (Player jogador : jogadoresDaArena()) {
                if (jogador.getGameMode() == GameMode.ADVENTURE
                        || jogador.getGameMode() == GameMode.SURVIVAL) {
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

    private void comecar(TntRunCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.tntRunBuilder().refazerPisos(mundo);
        derretendo.clear();
        vivos.clear();

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

        List<int[]> pontos = TntRunCatalog.largadas(entram.size(), planta.raio());
        for (int i = 0; i < entram.size(); i++) {
            Player jogador = entram.get(i);
            jogador.setGameMode(GameMode.ADVENTURE);
            jogador.setFallDistance(0f);
            jogador.teleport(TntRunBuilder.pontoDaLargada(mundo, planta, pontos.get(i)));
            vivos.add(jogador.getUniqueId());
        }

        comecouCom = vivos.size();
        inicioDaRodada = pulso;
        estado = Estado.JOGANDO;

        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<color:" + planta.corDoTexto()
                            + "><bold>JÁ!</bold></color>"),
                    plugin.mini().deserialize(comecouCom == 1
                            ? "<gray>treino: quanto você aguenta?</gray>"
                            : "<gray>" + comecouCom + " na arena</gray>")));
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL,
                    0.4f, 1.6f);
        }
    }

    private void eliminar(Player jogador, TntRunCatalog planta) {
        vivos.remove(jogador.getUniqueId());
        jogador.setFallDistance(0f);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(TntRunBuilder.pontoDaPlateia(jogador.getWorld(), planta));

        String aguentou = TntRunCatalog.tempo(pulso - inicioDaRodada);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>você caiu</color>"),
                plugin.mini().deserialize("<gray>aguentou <white>" + aguentou
                        + "</white></gray>")));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);

        if (comecouCom > 1) {
            anunciar("<color:#FF8A8A>" + HubBuilder.escapar(jogador.getName())
                    + "</color> <gray>caiu — faltam <white>" + vivos.size()
                    + "</white></gray>");
        }
    }

    private void terminar(TntRunCatalog planta) {
        estado = Estado.FIM;
        contagem = planta.fimSegundos();
        String tempo = TntRunCatalog.tempo(pulso - inicioDaRodada);

        if (vivos.size() == 1) {
            Player campeao = Bukkit.getPlayer(vivos.iterator().next());
            String nome = campeao == null ? "alguém" : campeao.getName();
            anunciar("<color:" + planta.corDoTexto() + "><bold>"
                    + HubBuilder.escapar(nome) + "</bold></color> <gray>ganhou em <white>"
                    + tempo + "</white></gray>");
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
                campeao.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                        campeao.getLocation().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.3);
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

    private void voltarAoSaguao(TntRunCatalog planta) {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        plugin.tntRunBuilder().refazerPisos(mundo);
        derretendo.clear();
        vivos.clear();
        // A fila esvazia: a rodada seguinte também começa pela placa. Manter
        // todos inscritos faria a próxima largada pegar quem já tinha ido
        // embora de vontade, e tiraria da placa o papel de ser a porta.
        fila.clear();
        estado = Estado.ESPERANDO;

        Location saguao = TntRunBuilder.pontoDoSaguao(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            // Quem está em criativo não é jogador desta rodada e não é
            // arrastado de volta para o saguão no fim dela.
            if (jogador.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            jogador.setGameMode(GameMode.ADVENTURE);
            jogador.setFallDistance(0f);
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>clique em <green>" + HubBuilder.escapar(
                            planta.placa("entrar", "[ENTRAR]"))
                            + "</green> para jogar de novo</gray>"));
        }
    }

    /** Para a rodada sem vencedor. É o {@code /biga tntrun parar}. */
    public String pararRodada() {
        if (estado != Estado.JOGANDO && estado != Estado.CONTAGEM) {
            return "Não há rodada para parar.";
        }
        TntRunCatalog planta = plugin.tntRunCatalog();
        anunciar("<gray>a rodada foi parada pela administração</gray>");
        voltarAoSaguao(planta);
        return "Rodada parada.";
    }

    // ------------------------------------------------------------------
    //  A volta ao lobby
    // ------------------------------------------------------------------

    private void cortinaDaVolta(World mundo, TntRunCatalog planta) {
        if (jogadoresDaArena().isEmpty()) {
            return;
        }
        int z = TntRunBuilder.zDoArcoDeVolta(planta);
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
            for (int alto = 1; alto < TntRunBuilder.alturaDoArcoDeVolta(); alto++) {
                if ((x + alto) % 2 != 0) {
                    continue;
                }
                mundo.spawnParticle(Particle.DUST, x + 0.5, y + alto + 0.5, z + 0.5,
                        1, 0.1, 0.2, 0.1, 0, po);
            }
        }
    }

    private void conferirVolta(World mundo, TntRunCatalog planta) {
        int z = TntRunBuilder.zDoArcoDeVolta(planta);
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
            if (!TntRunBuilder.dentroDaVolta(onde.getX(), onde.getY(), onde.getZ(),
                    planta.saguaoY(), z)) {
                continue;
            }
            ultimaVolta.put(jogador.getUniqueId(), agora);
            voltarAoLobby(jogador);
        }
    }

    /**
     * Devolve o jogador ao lobby dos minigames.
     *
     * Só precisa jogá-lo no mundo: o {@link LobbyPortais} lembra por qual
     * portal ele saiu e o põe de volta nele. É o mesmo caminho que um jogo
     * qualquer vai usar, e por isso nenhum jogo precisa saber onde fica o
     * nicho dele no claustro.
     */
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

    /**
     * Quem está fora da rodada e caiu volta ao saguão em vez de cair sem fim.
     *
     * Vale também para quem visita o mundo antes de a arena existir: sem isto
     * ele cai no void para sempre num mundo onde não há o que construir. Só
     * resgata se o saguão já estiver de pé — mandar alguém para um chão que
     * não existe é o mesmo void outra vez.
     */
    private void resgatarDoVoid(World mundo, TntRunCatalog planta) {
        if (!arenaConstruida(mundo, planta)) {
            return;
        }
        Location saguao = TntRunBuilder.pontoDoSaguao(mundo, planta);
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
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#8FD3FF>o saguão te pegou no ar</color>"));
        }
    }

    // ------------------------------------------------------------------
    //  Barra e avisos
    // ------------------------------------------------------------------

    private void atualizarBarra(TntRunCatalog planta) {
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
                barra.progress(limite(prontos / (float) planta.minimo()));
                barra.color(BossBar.Color.WHITE);
            }
            case CONTAGEM -> {
                barra.name(plugin.mini().deserialize("<color:" + cor
                        + ">começa em <white>" + contagem + "</white></color>"));
                barra.progress(limite(contagem / (float) planta.contagemSegundos()));
                barra.color(BossBar.Color.YELLOW);
            }
            case JOGANDO -> {
                barra.name(plugin.mini().deserialize("<color:" + cor + "><white>"
                        + vivos.size() + "</white> de pé</color> <gray>· "
                        + TntRunCatalog.tempo(pulso - inicioDaRodada) + "</gray>"));
                barra.progress(limite(vivos.size() / (float) Math.max(1, comecouCom)));
                barra.color(BossBar.Color.RED);
            }
            case FIM -> {
                barra.name(plugin.mini().deserialize("<gray>fim da rodada</gray>"));
                barra.progress(limite(contagem / (float) planta.fimSegundos()));
                barra.color(BossBar.Color.GREEN);
            }
        }
    }

    static float limite(float valor) {
        return ArenaComum.limite(valor);
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
    }

    /**
     * O clique na placa.
     *
     * Compara COORDENADA, e não o texto da placa. Ler o texto para decidir o
     * que fazer é o jeito do plugin de referência — ele precisa, porque lá as
     * placas são postas à mão em qualquer lugar. Aqui as duas nascem da planta
     * e o jogo sabe onde elas estão, então comparar posição é imune a
     * tradução, a cor e a alguém reescrever a placa.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void aoClicarNaPlaca(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK
                || evento.getHand() != EquipmentSlot.HAND
                || evento.getClickedBlock() == null) {
            return;
        }
        Block bloco = evento.getClickedBlock();
        if (!nesteMundo(bloco.getWorld()) || !(bloco.getState() instanceof Sign)) {
            return;
        }
        TntRunCatalog planta = plugin.tntRunCatalog();
        if (mesmoBloco(bloco, TntRunBuilder.pontoDaPlacaEntrar(planta))) {
            evento.setCancelled(true);
            entrarNaFila(evento.getPlayer());
        } else if (mesmoBloco(bloco, TntRunBuilder.pontoDaPlacaSair(planta))) {
            evento.setCancelled(true);
            sairDaFila(evento.getPlayer());
        }
    }

    static boolean mesmoBloco(Block bloco, int[] onde) {
        return ArenaComum.mesmoBloco(bloco, onde);
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

    /**
     * O piso é TNT de verdade, e TNT de verdade não acende aqui.
     *
     * É o preço de usar o bloco que dá nome ao jogo em vez de um parecido:
     * três eventos barrados. Vale a pena — a textura é metade da leitura do
     * jogo —, mas sem estas três linhas uma única fonte de fogo transformaria
     * a arena inteira numa reação em cadeia.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoAcender(BlockIgniteEvent evento) {
        if (nesteMundo(evento.getBlock().getWorld())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoArmarExplosao(ExplosionPrimeEvent evento) {
        if (nesteMundo(evento.getEntity().getWorld())) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodir(EntityExplodeEvent evento) {
        if (nesteMundo(evento.getEntity().getWorld())) {
            evento.setCancelled(true);
            evento.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoNascerBicho(CreatureSpawnEvent evento) {
        if (nesteMundo(evento.getEntity().getWorld())
                && !RegrasDoHub.nascimentoEhDeAdmin(evento.getSpawnReason())) {
            evento.setCancelled(true);
        }
    }
}
