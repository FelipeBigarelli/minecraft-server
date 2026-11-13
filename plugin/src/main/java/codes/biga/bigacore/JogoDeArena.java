package codes.biga.bigacore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * A máquina que todo minigame de arena compartilha.
 *
 * <h2>O que mora aqui</h2>
 *
 * A fila, as duas placas, a contagem, a largada, a eliminação, o fim de
 * rodada, a barra, a volta ao lobby, a presença no mundo e as proteções. Em
 * outras palavras: tudo o que o jogador faz ANTES e DEPOIS de jogar, que é
 * igual nos vinte jogos.
 *
 * <h2>O que cada jogo escreve</h2>
 *
 * Três métodos obrigatórios — refazer a arena, largar os jogadores e dizer de
 * onde o eliminado assiste — e os ganchos de que precisar. O TNT Run
 * acrescenta o derretimento; o Spleef, a cavada e o aperto; o parkour,
 * checkpoint e cronômetro. A regra do jogo fica no jogo, e nunca aqui.
 *
 * <h2>Por que herança e não cópia</h2>
 *
 * Os dois primeiros jogos nasceram como cópias, e a segunda cópia já mostrou
 * o preço: o mesmo defeito de placa teria de ser achado duas vezes. Com
 * dezoito jogos pela frente seriam vinte. Herança aqui é o que mantém uma
 * correção valendo para todos — e é por isso que este arquivo não pode ter
 * nenhum "se for o jogo tal": no dia em que precisar, vira gancho.
 *
 * <h2>O laço roda sempre, e custa nada vazio</h2>
 *
 * Com a arena vazia o ciclo custa uma busca de mundo num mapa: nem partícula,
 * nem leitura de bloco, nem barra. É o estado normal do servidor e tem de ser
 * de graça.
 */
public abstract class JogoDeArena implements Listener, JogoDeMinigame {

    private static final int INTERVALO_TICKS = 1;
    private static final int ALCANCE_DA_CORTINA = 32;

    public enum Estado { ESPERANDO, CONTAGEM, PREPARO, JOGANDO, FIM }

    protected final BigaCore plugin;
    private BukkitTask tarefa;

    private Estado estado = Estado.ESPERANDO;
    private final Set<UUID> naArena = new LinkedHashSet<>();
    private final Set<UUID> fila = new LinkedHashSet<>();
    private final Set<UUID> vivos = new LinkedHashSet<>();
    private final Map<UUID, GameMode> modoAnterior = new HashMap<>();
    private final Map<UUID, Long> ultimaVolta = new HashMap<>();

    private int comecouCom;
    private int contagem;
    private int preparo;
    private int segundosDeJogo;
    private long inicioDaRodada;
    private long pulso;
    private boolean mundoAjustado;
    private String ultimoTextoDaPlaca = "";
    private BossBar barra;

    /**
     * A lista lateral com o placar da partida.
     *
     * Uma por jogo e criada na primeira vez que alguém entra: pôr a lateral
     * no quadro principal do servidor faria os vinte jogos disputarem a mesma
     * faixa e mostraria o placar da MINERAÇÃO a quem está no spawn.
     */
    private PlacarLateral lateral;

    protected JogoDeArena(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  O contrato de cada jogo
    // ------------------------------------------------------------------

    /** A planta e as regras deste jogo. */
    protected abstract PlantaDeArena planta();

    /**
     * Refaz o que a rodada consome, antes de cada largada.
     *
     * Só o que a partida gasta — piso, blocos, baús. Saguão, marcos e
     * letreiros são obra de uma vez: refazê-los a cada rodada custaria o
     * travamento do /construir em toda partida.
     */
    protected abstract void prepararArena(World mundo);

    /** Põe os jogadores na posição inicial e entrega o que for do jogo. */
    protected abstract void largar(World mundo, List<Player> entram);

    /** De onde o eliminado assiste ao resto da rodada. */
    protected abstract Location pontoDaPlateia(World mundo);

    // ------------------------------------------------------------------
    //  Os ganchos
    // ------------------------------------------------------------------

    /**
     * O modo de quem está na arena.
     *
     * Aventura por padrão. Jogo cujo verbo é cavar ou quebrar PRECISA de
     * sobrevivência: em aventura o cliente nem envia o pacote de quebrar, e o
     * clique esquerdo nunca chega ao servidor.
     */
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    /** A lista lateral desta arena. */
    protected PlacarLateral lateral() {
        if (lateral == null) {
            lateral = new PlacarLateral(plugin, planta().etiqueta());
        }
        return lateral;
    }

    /**
     * O placar que a lista lateral mostra.
     *
     * Nulo nos jogos em que o número não é ponto e sim quem continua de pé —
     * metade dos vinte. Esses caem no desenho de sobrevivência, que lista os
     * da arena com "de pé" ou "fora": a pergunta que o jogador tem num TNT
     * RUN não é quantos pontos alguém fez, é quem ainda está correndo.
     */
    protected Placar placarDaLateral() {
        return null;
    }

    /**
     * As linhas da lista lateral.
     *
     * Jogo de pontos: o ranking, do primeiro ao último. Jogo de sobrevivência:
     * quem está de pé primeiro, quem já caiu depois — e os caídos continuam na
     * lista de propósito, porque ver o próprio nome apagado é a confirmação de
     * que a rodada acabou PARA VOCÊ e não para todo mundo.
     */
    protected List<PlacarLateral.Linha> linhasDaLateral() {
        Placar quadro = placarDaLateral();
        List<PlacarLateral.Linha> linhas = new ArrayList<>();
        if (quadro != null) {
            for (UUID id : quadro.ranking()) {
                Player quem = Bukkit.getPlayer(id);
                if (quem != null) {
                    linhas.add(new PlacarLateral.Linha(quem.getName(),
                            quadro.de(id) + " pts"));
                }
            }
            return linhas;
        }
        for (Player quem : jogadoresDaArena()) {
            if (estaVivo(quem)) {
                linhas.add(new PlacarLateral.Linha(quem.getName(), "de pé"));
            }
        }
        for (Player quem : jogadoresDaArena()) {
            if (!estaVivo(quem) && quem.getGameMode() != GameMode.CREATIVE) {
                linhas.add(new PlacarLateral.Linha(quem.getName(), "fora"));
            }
        }
        return linhas;
    }

    /** O cabeçalho da lista lateral: o nome do jogo, na cor dele. */
    protected String tituloDaLateral() {
        PlantaDeArena planta = planta();
        return "<color:" + planta.corDoTexto() + "><bold>"
                + HubBuilder.escapar(planta.titulo()) + "</bold></color>";
    }

    protected void aCadaTique(World mundo) {
    }

    protected void aCadaSegundo(World mundo) {
    }

    protected void aoEntrarNaArena(Player jogador) {
    }

    protected void aoSairDaArena(UUID id) {
    }

    protected void aoEliminarJogador(Player jogador) {
    }

    protected void aoComecarRodada(World mundo) {
    }

    /**
     * As regras do jogo, ditas no SAGUÃO e para um jogador só.
     *
     * Elas moravam no {@link #aoComecarRodada}, e lá não serviam para nada:
     * quatro parágrafos aparecem no bate-papo no instante em que o chão
     * começa a sumir, com o jogador correndo. Quem precisa das regras é
     * quem ainda não entrou, e esse está parado no saguão esperando a fila
     * encher — é ali que dá tempo de ler.
     *
     * <p>Para um jogador só, e não para a arena: quem entra no meio de uma
     * partida em curso recebe as regras sem encher o bate-papo de quem já
     * está jogando.
     */
    /**
     * Os parágrafos de instrução deste jogo, para o saguão escrever nas
     * paredes da rua. Vazio quer dizer "este jogo não tem o que explicar".
     */
    @Override
    public java.util.List<String> instrucoes() {
        return java.util.List.of();
    }

    /** Uma linha de instrução, no bate-papo de um jogador só. */
    protected void dizer(Player jogador, String mini) {
        jogador.sendMessage(plugin.mini().deserialize(mini));
    }

    protected void aoTerminarRodada() {
    }

    /** Regras de mundo além das comuns. */
    protected void ajustesDoMundo(World mundo) {
    }

    /** A palavra que aparece na largada. */
    protected String verboDaLargada() {
        return "JÁ!";
    }

    /**
     * O quanto a barra enche durante a rodada.
     *
     * Por padrão, a fração de quem ainda está de pé — que é a medida certa
     * nos jogos de eliminação. Num jogo em que ninguém é eliminado, essa
     * fração fica em um para sempre e a barra não diz nada; é para esses que
     * este gancho existe.
     */
    protected float progressoDaBarra() {
        return vivos.size() / (float) Math.max(1, comecouCom);
    }

    /** O que a barra mostra durante a partida. */
    protected String textoDaBarra() {
        return "<white>" + vivos.size() + "</white> de pé <gray>· "
                + ArenaComum.tempo(pulso - inicioDaRodada) + "</gray>";
    }

    /** Se o jogador caiu para fora da arena. */
    protected boolean caiuFora(Player jogador) {
        return jogador.getLocation().getY() < planta().quedaY();
    }

    /** Se a rodada acabou. */
    protected boolean rodadaAcabou() {
        return ArenaComum.rodadaAcabou(vivos.size(), comecouCom,
                planta().treinoSozinho());
    }

    /** O nome do vencedor, ou null se não houve um. */
    protected String vencedor() {
        if (vivos.size() != 1) {
            return null;
        }
        Player campeao = Bukkit.getPlayer(vivos.iterator().next());
        return campeao == null ? "alguém" : campeao.getName();
    }

    /** A linha de baixo do título de fim de rodada do vencedor. */
    protected String legendaDaVitoria() {
        return "último de pé · " + ArenaComum.tempo(pulso - inicioDaRodada);
    }

    // ------------------------------------------------------------------
    //  Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    public void start() {
        parar();
        this.pulso = 0;
        this.mundoAjustado = false;
        this.barra = BossBar.bossBar(Component.empty(), 1f,
                BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        this.tarefa = Bukkit.getScheduler().runTaskTimer(
                plugin, this::ciclo, 100L, INTERVALO_TICKS);
    }

    /**
     * Para o jogo e devolve todo mundo ao normal.
     *
     * Sem este laço, um /reload deixaria quem estava eliminado preso em
     * espectador — e isso atravessa reinício de servidor.
     */
    @Override
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
        estado = Estado.ESPERANDO;
    }

    // ------------------------------------------------------------------
    //  Estado, para o comando e para o jogo
    // ------------------------------------------------------------------

    public Estado estado() {
        return estado;
    }

    @Override
    public String estadoEscrito() {
        return estado.name();
    }

    @Override
    public int quantosNaArena() {
        return naArena.size();
    }

    @Override
    public int quantosProntos() {
        return fila.size();
    }

    @Override
    public int quantosVivos() {
        return vivos.size();
    }

    protected Set<UUID> vivos() {
        return vivos;
    }

    protected boolean estaVivo(Player jogador) {
        return vivos.contains(jogador.getUniqueId());
    }

    protected boolean jogando() {
        return estado == Estado.JOGANDO;
    }

    protected int segundosDeJogo() {
        return segundosDeJogo;
    }

    protected long tiquesDeJogo() {
        return pulso - inicioDaRodada;
    }

    protected int comecouCom() {
        return comecouCom;
    }

    public World mundoDaArena() {
        return Bukkit.getWorld(planta().mundo());
    }

    public boolean arenaConstruida(World mundo) {
        return SaguaoPadrao.construido(mundo, planta());
    }

    protected Location pontoDoSaguao(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, planta());
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    private void ciclo() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        pulso++;

        if (!mundoAjustado) {
            ajustarMundo(mundo);
            ajustesDoMundo(mundo);
            mundoAjustado = true;
        }

        // A meia-noite de meio minuto em meio minuto.
        //
        // O ciclo do dia já está desligado, mas isso só congela a hora em
        // que o mundo estava: um /time de administrador, um mundo que
        // nasceu à noite ou outro plugin mexendo deixam a arena no escuro
        // para sempre. Reafirmar é barato e tira o problema da mão de
        // quem joga.
        if (pulso % 600 == 0 && mundo.getTime() != 6000L) {
            mundo.setTime(6000L);
            mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }

        if (estado == Estado.JOGANDO) {
            conferirQuedas(mundo);
            aCadaTique(mundo);
            if (rodadaAcabou()) {
                terminar();
            }
        }

        if (pulso % 5 == 0) {
            reconciliar(mundo);
            if (!naArena.isEmpty()) {
                cortinaDaVolta(mundo);
                conferirVolta(mundo);
                resgatarDoVoid(mundo);
            }
        }
        if (pulso % 10 == 0 && !naArena.isEmpty()) {
            atualizarBarra();
        }
        if (pulso % 20 == 0 && (!naArena.isEmpty() || estado != Estado.ESPERANDO)) {
            cicloDeSegundo(mundo);
            if (arenaConstruida(mundo)) {
                atualizarPlacas(mundo);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Comandos dentro da arena
    // ------------------------------------------------------------------

    /**
     * O que não se digita dentro de uma arena.
     *
     * /speed é o caso que motivou isto: ele transforma qualquer jogo de
     * corrida numa piada, e nenhum dos vinte tem defesa contra. A lista
     * cobre a mesma família — velocidade, voo, vida, item, modo e
     * teleporte — e é lida do config.yml para crescer sem recompilar.
     *
     * O que a lista NÃO tem é tão importante quanto o que tem: /spawn,
     * /hub, /lobby, /home e /biga ficam livres de propósito. Um jogador
     * preso numa arena sem saída é um defeito pior do que um jogador
     * voando.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void aoDigitarComando(PlayerCommandPreprocessEvent evento) {
        Player jogador = evento.getPlayer();
        if (!naArena(jogador)) {
            return;
        }
        String raiz = ComandosDaArena.raiz(evento.getMessage());
        if (!ComandosDaArena.lista(plugin).contains(raiz)) {
            return;
        }
        evento.setCancelled(true);
        velocidadeNormal(jogador);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>/" + HubBuilder.escapar(raiz)
                        + " não vale dentro do minigame</color>"));
        jogador.playSound(jogador.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    /**
     * As regras do MUNDO da arena.
     *
     * Dano de queda desligado na regra do mundo E no listener: quase todo jogo
     * daqui é feito de cair de propósito, e um dos dois falhando sozinho
     * transformaria a mecânica central em dano.
     */
    public static void ajustarMundo(World mundo) {
        mundo.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        mundo.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        mundo.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        mundo.setGameRule(GameRule.DO_FIRE_TICK, false);
        mundo.setGameRule(GameRule.FALL_DAMAGE, false);
        mundo.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        mundo.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        mundo.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        mundo.setTime(6000L);
        mundo.setStorm(false);
    }

    // ------------------------------------------------------------------
    //  Presença
    // ------------------------------------------------------------------

    /**
     * Confere quem está de fato no mundo.
     *
     * Os eventos de entrar e sair já cuidam do caso normal; isto é a rede. Um
     * teleporte feito por outro plugin, um mundo recarregado ou um evento
     * cancelado por terceiro deixariam a lista mentindo — e lista mentindo é
     * jogador contado na rodada sem estar nela.
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
        PlantaDeArena planta = planta();
        naArena.add(jogador.getUniqueId());
        modoAnterior.putIfAbsent(jogador.getUniqueId(), jogador.getGameMode());

        // A mochila é guardada para TODO jogo, e não só para os que dão item.
        // O perigo é o contrário do que parece: sem guardar, nada impede
        // alguém de levar arco, poção e blocos para dentro do TNT Run.
        if (jogador.getGameMode() != GameMode.CREATIVE) {
            plugin.mochila().guardar(jogador);
        }
        velocidadeNormal(jogador);
        if (barra != null) {
            jogador.showBossBar(barra);
        }
        lateral().mostrarPara(jogador);
        aoEntrarNaArena(jogador);

        // Administrador voando não é jogador: nem muda de modo, nem conta para
        // o mínimo, nem é largado numa rodada.
        if (jogador.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Mundo sem arena: espectador em vez de queda sem fim. É o estado em
        // que o mundo nasce, antes do construir.
        if (!arenaConstruida(jogador.getWorld())) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendMessage(plugin.mini().deserialize(
                    "<gray>a arena do " + HubBuilder.escapar(planta.titulo())
                            + " ainda não foi construída — um admin precisa rodar "
                            + "<white>/biga " + planta.etiqueta()
                            + " construir CONFIRMAR</white></gray>"));
            return;
        }

        if (estado == Estado.PREPARO || estado == Estado.JOGANDO
                || estado == Estado.FIM) {
            jogador.setGameMode(GameMode.SPECTATOR);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>a rodada já começou — você entra na próxima</gray>"));
            return;
        }
        jogador.setGameMode(modoDeJogo());
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
        aoSairDaArena(id);
        Player jogador = Bukkit.getPlayer(id);
        if (jogador != null) {
            velocidadeNormal(jogador);
            if (barra != null) {
                jogador.hideBossBar(barra);
            }
            lateral().esconderDe(jogador);
            devolverModo(jogador);
            plugin.mochila().devolver(jogador);
        } else {
            modoAnterior.remove(id);
        }
    }

    /**
     * Devolve o jogador ao modo em que ele estava antes de entrar.
     *
     * Quem entra do lobby está em sobrevivência, e sair da arena em aventura o
     * deixaria sem poder quebrar bloco no survival — reclamação que ninguém
     * ligaria ao minigame de onde acabou de sair.
     */
    protected void devolverModo(Player jogador) {
        GameMode antes = modoAnterior.remove(jogador.getUniqueId());
        if (antes != null && jogador.getGameMode() != antes) {
            jogador.setGameMode(antes);
        }
    }

    // ------------------------------------------------------------------
    //  Quedas
    // ------------------------------------------------------------------

    private void conferirQuedas(World mundo) {
        if (vivos.isEmpty()) {
            return;
        }
        List<Player> caidos = new ArrayList<>();
        for (UUID id : vivos) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !jogador.getWorld().equals(mundo)) {
                continue;
            }
            if (caiuFora(jogador)) {
                caidos.add(jogador);
            }
        }
        for (Player jogador : caidos) {
            eliminar(jogador);
        }
    }

    // ------------------------------------------------------------------
    //  A máquina de estados
    // ------------------------------------------------------------------

    private void cicloDeSegundo(World mundo) {
        PlantaDeArena planta = planta();
        switch (estado) {
            case ESPERANDO -> {
                destravarEspectadores();
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
                                        + planta.corDoTexto() + ">" + contagem
                                        + "</color>"),
                                Component.empty()));
                        jogador.playSound(jogador.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f,
                                contagem <= 3 ? 1.8f : 1.2f);
                    }
                }
                if (contagem <= 0) {
                    comecar();
                }
            }
            case PREPARO -> {
                preparo--;
                if (preparo <= 0) {
                    abrirARodada(mundo);
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
                aCadaSegundo(mundo);
            }
            case FIM -> {
                contagem--;
                if (contagem <= 0) {
                    voltarAoSaguao();
                }
            }
        }
        desenharALateral();
    }

    /**
     * Desenha a lista lateral, e só enquanto há rodada.
     *
     * No saguão ela fica apagada de propósito: antes da largada ninguém está
     * "de pé" nem "fora" e ninguém tem ponto, e uma lista de todo mundo
     * marcado como fora é pior que lista nenhuma.
     */
    private void desenharALateral() {
        if (estado == Estado.ESPERANDO || estado == Estado.CONTAGEM) {
            lateral().apagar();
            return;
        }
        lateral().titulo(tituloDaLateral());
        lateral().desenhar(linhasDaLateral());
    }

    /**
     * Em espera, ninguém fica preso em espectador.
     *
     * Dois casos chegam aqui: quem entrou antes de a arena existir e foi posto
     * em espectador para não cair no void, e quem foi eliminado numa rodada
     * que acabou de um jeito torto. Nenhum dos dois tem como se destravar
     * sozinho, e espectador para sempre é, para ele, um jogo quebrado.
     *
     * <p>Quem quer só assistir usa CRIATIVO, que o jogo respeita; em espera,
     * espectador é sempre acidente.
     */
    private void destravarEspectadores() {
        World mundo = mundoDaArena();
        if (mundo == null || !arenaConstruida(mundo)) {
            return;
        }
        Location saguao = pontoDoSaguao(mundo);
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            jogador.setGameMode(modoDeJogo());
            jogador.setFallDistance(0f);
            velocidadeNormal(jogador);
            jogador.teleport(saguao);
        }
    }

    // ------------------------------------------------------------------
    //  A fila e as placas
    // ------------------------------------------------------------------

    /**
     * Entra na fila. É o clique na placa de ENTRAR, e é a única porta.
     *
     * Recusa com motivo em vez de em silêncio: uma placa que não responde é
     * indistinguível de uma placa quebrada, e o jogador conclui que o jogo não
     * funciona em vez de que a rodada já começou.
     */
    protected void entrarNaFila(Player jogador) {
        PlantaDeArena planta = planta();
        if (!naArena.contains(jogador.getUniqueId())) {
            entrou(jogador);
        }
        if (!arenaConstruida(jogador.getWorld())) {
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
            jogador.setGameMode(modoDeJogo());
            jogador.teleport(pontoDoSaguao(jogador.getWorld()));
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
        anunciar("<color:" + planta.corDoTexto() + ">"
                + HubBuilder.escapar(jogador.getName()) + "</color> <gray>entrou na fila "
                + "<white>" + fila.size() + "/" + planta.maximo() + "</white>"
                + (fila.size() < planta.minimo()
                        ? " — faltam <white>" + (planta.minimo() - fila.size())
                                + "</white> para começar"
                        : "") + "</gray>");
        atualizarPlacas(jogador.getWorld());
    }

    /**
     * Sai da fila — e, para quem não estava nela, sai da arena.
     *
     * Dois passos na mesma placa de propósito: quem clicou em ENTRAR por
     * engano desfaz o engano, e quem só visitou tem um jeito óbvio de voltar
     * ao lobby sem precisar achar o arco.
     */
    protected void sairDaFila(Player jogador) {
        if (fila.remove(jogador.getUniqueId())) {
            if (estado == Estado.JOGANDO && estaVivo(jogador)) {
                eliminar(jogador);
            }
            aviso(jogador, "<gray>você saiu da fila</gray>",
                    Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f);
            atualizarPlacas(jogador.getWorld());
            return;
        }
        voltarAoLobby(jogador);
    }

    protected void aviso(Player jogador, String mini, Sound som, float tom) {
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
    private void atualizarPlacas(World mundo) {
        PlantaDeArena planta = planta();
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

        SaguaoPadrao.escreverPlaca(mundo, planta,
                SaguaoPadrao.pontoDaPlacaEntrar(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize((aberta ? "<green>" : "<dark_gray>")
                                + HubBuilder.escapar(planta.placa("entrar", "[ENTRAR]"))),
                        plugin.mini().deserialize("<gray>" + estadoEscrito),
                        plugin.mini().deserialize((aberta ? "<white>" : "<red>")
                                + contagemEscrita)));

        SaguaoPadrao.escreverPlaca(mundo, planta,
                SaguaoPadrao.pontoDaPlacaSair(planta), List.of(
                        titulo.get(),
                        plugin.mini().deserialize("<red>"
                                + HubBuilder.escapar(planta.placa("sair", "[SAIR]"))),
                        plugin.mini().deserialize("<gray>sai da fila"),
                        plugin.mini().deserialize("<dark_gray>ou volta ao lobby")));
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    /** Começa a rodada na hora. É o {@code /biga <jogo> comecar}. */
    @Override
    public String comecarAgora() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return "O mundo da arena não está carregado.";
        }
        if (estado == Estado.JOGANDO || estado == Estado.PREPARO) {
            return "A rodada já está rolando.";
        }
        if (!arenaConstruida(mundo)) {
            return "A arena ainda não foi construída.";
        }
        // Com a fila vazia, inscreve quem está no saguão em modo de jogo. É o
        // que deixa testar sozinho sem ir clicar na placa primeiro.
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
        comecar();
        return "Rodada começada com " + comecouCom + " jogador(es).";
    }

    private void comecar() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        PlantaDeArena planta = planta();

        // PREPARAR A ARENA PODE FALHAR, E A FALHA NÃO PODE TRANCAR O SAGUÃO.
        //
        // Já trancou: uma classe que faltava no jar fez o prepararArena do
        // SKY WARS estourar, a largada nunca aconteceu, e o estado ficou em
        // CONTAGEM — com o relógio descontando para sempre. O jogador via
        // "começa em -8", depois -9, depois -10, e a única saída era um
        // administrador parar a rodada à mão.
        //
        // O defeito de origem era outro e foi consertado. Isto aqui é o que
        // impede que o PRÓXIMO defeito de qualquer um dos vinte jogos vire
        // uma contagem negativa sem fim: se a arena não sobe, a rodada é
        // cancelada, o erro vai para o console com nome e sobrenome, e o
        // saguão volta a esperar.
        try {
            prepararArena(mundo);
        } catch (RuntimeException falhou) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "a arena do " + planta.etiqueta()
                            + " não subiu — a rodada foi cancelada", falhou);
            estado = Estado.ESPERANDO;
            contagem = 0;
            anunciar("<color:#FF8A8A>a arena não subiu</color> <gray>— a "
                    + "rodada foi cancelada. Um administrador precisa ver o "
                    + "console e rodar <white>/biga " + planta.etiqueta()
                    + " construir CONFIRMAR</white>.</gray>");
            atualizarPlacas(mundo);
            return;
        }
        vivos.clear();
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
        for (Player jogador : entram) {
            jogador.setGameMode(modoDeJogo());
            jogador.setFallDistance(0f);
            vivos.add(jogador.getUniqueId());
        }

        largar(mundo, entram);

        comecouCom = vivos.size();
        estado = Estado.PREPARO;
        preparo = planta.preparoSegundos();

        // O relógio do jogo NÃO anda no preparo: inicioDaRodada só é marcado
        // quando a rodada abre de verdade, para tiquesDeJogo() continuar
        // sendo o tempo de JOGO e nenhuma conta de jogo precisar descontar o
        // preparo.
        for (Player jogador : entram) {
            congelar(jogador);
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

    /**
     * Abre a rodada de verdade, no fim do preparo.
     *
     * O preparo existe porque a largada põe o jogador num chão que já está
     * sumindo: no TNT Run o piso começa a cair no mesmo tique em que ele
     * aparece, e quem ainda está carregando o pedaço de mundo perde sem ter
     * jogado. Cinco segundos parado resolvem isso sem mudar jogo nenhum.
     */
    private void abrirARodada(World mundo) {
        PlantaDeArena planta = planta();
        for (Player jogador : jogadoresDaArena()) {
            descongelar(jogador);
        }
        inicioDaRodada = pulso;
        segundosDeJogo = 0;
        estado = Estado.JOGANDO;
        aoComecarRodada(mundo);

        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<color:" + planta.corDoTexto()
                            + "><bold>" + verboDaLargada() + "</bold></color>"),
                    plugin.mini().deserialize(comecouCom == 1
                            ? "<gray>treino</gray>"
                            : "<gray>" + comecouCom + " na arena</gray>")));
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                    0.5f, 1.5f);
        }
    }

    // ------------------------------------------------------------------
    //  Velocidade
    // ------------------------------------------------------------------

    /**
     * Para o jogador no lugar durante o preparo.
     *
     * Velocidade zero e não efeito de lentidão: lentidão ainda deixa andar,
     * e andar no preparo é largar antes. O voo também vai a zero para o
     * administrador em criativo não atravessar a arena enquanto conta.
     */
    private void congelar(Player jogador) {
        jogador.setWalkSpeed(0f);
        jogador.setFlySpeed(0f);
    }

    /**
     * Devolve a velocidade de fábrica.
     *
     * Chamado também na ENTRADA da arena, e isso é o que impede o /speed de
     * entrar junto: o comando fica bloqueado lá dentro, mas quem já estava
     * voando rápido no hub traria a velocidade no corpo.
     */
    protected static void velocidadeNormal(Player jogador) {
        jogador.setWalkSpeed(0.2f);
        jogador.setFlySpeed(0.1f);
    }

    private void descongelar(Player jogador) {
        velocidadeNormal(jogador);
    }

    protected void eliminar(Player jogador) {
        PlantaDeArena planta = planta();
        vivos.remove(jogador.getUniqueId());
        jogador.setFallDistance(0f);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(pontoDaPlateia(jogador.getWorld()));
        aoEliminarJogador(jogador);

        String aguentou = ArenaComum.tempo(tiquesDeJogo());
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

    protected void terminar() {
        PlantaDeArena planta = planta();
        estado = Estado.FIM;
        contagem = planta.fimSegundos();
        String tempo = ArenaComum.tempo(tiquesDeJogo());
        aoTerminarRodada();

        String nome = vencedor();
        if (nome != null) {
            anunciar("<color:" + planta.corDoTexto() + "><bold>"
                    + HubBuilder.escapar(nome) + "</bold></color> <gray>ganhou em <white>"
                    + tempo + "</white></gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.showTitle(Title.title(
                        plugin.mini().deserialize("<color:" + planta.corDoTexto() + ">"
                                + HubBuilder.escapar(nome) + "</color>"),
                        plugin.mini().deserialize("<gray>"
                                + HubBuilder.escapar(legendaDaVitoria()) + "</gray>")));
                jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                        0.6f, 1.0f);
            }
            return;
        }

        if (comecouCom == 1) {
            anunciar("<gray>treino encerrado: <white>" + tempo + "</white></gray>");
        } else {
            anunciar("<gray>rodada sem vencedor</gray>");
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize("<gray>" + tempo + "</gray>"),
                    plugin.mini().deserialize(comecouCom == 1
                            ? "<gray>seu tempo</gray>" : "<gray>sem vencedor</gray>")));
        }
    }

    private void voltarAoSaguao() {
        World mundo = mundoDaArena();
        if (mundo == null) {
            return;
        }
        PlantaDeArena planta = planta();
        prepararArena(mundo);
        vivos.clear();
        // A fila esvazia: a rodada seguinte também começa pela placa. Manter
        // todos inscritos faria a próxima largada pegar quem já tinha ido
        // embora de vontade, e tiraria da placa o papel de ser a porta.
        fila.clear();
        segundosDeJogo = 0;
        estado = Estado.ESPERANDO;

        Location saguao = pontoDoSaguao(mundo);
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            jogador.setGameMode(modoDeJogo());
            jogador.setFallDistance(0f);
            velocidadeNormal(jogador);
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>clique em <green>" + HubBuilder.escapar(
                            planta.placa("entrar", "[ENTRAR]"))
                            + "</green> para jogar de novo</gray>"));
        }
    }

    /** Para a rodada sem vencedor. É o {@code /biga <jogo> parar}. */
    @Override
    public String pararRodada() {
        if (estado != Estado.JOGANDO && estado != Estado.CONTAGEM
                && estado != Estado.PREPARO) {
            return "Não há rodada para parar.";
        }
        anunciar("<gray>a rodada foi parada pela administração</gray>");
        voltarAoSaguao();
        return "Rodada parada.";
    }

    // ------------------------------------------------------------------
    //  A volta ao lobby
    // ------------------------------------------------------------------

    private void cortinaDaVolta(World mundo) {
        PlantaDeArena planta = planta();
        int z = SaguaoPadrao.zDoArcoDeVolta(planta);
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
            for (int alto = 1; alto < SaguaoPadrao.alturaDoArcoDeVolta(); alto++) {
                if ((x + alto) % 2 != 0) {
                    continue;
                }
                mundo.spawnParticle(Particle.DUST, x + 0.5, y + alto + 0.5, z + 0.5,
                        1, 0.1, 0.2, 0.1, 0, po);
            }
        }
    }

    private void conferirVolta(World mundo) {
        PlantaDeArena planta = planta();
        int z = SaguaoPadrao.zDoArcoDeVolta(planta);
        long agora = System.currentTimeMillis();

        for (Player jogador : jogadoresDaArena()) {
            if (estado == Estado.JOGANDO && estaVivo(jogador)) {
                continue;
            }
            Long antes = ultimaVolta.get(jogador.getUniqueId());
            if (antes != null && agora - antes < 3000L) {
                continue;
            }
            Location onde = jogador.getLocation();
            if (!SaguaoPadrao.dentroDaVolta(onde.getX(), onde.getY(), onde.getZ(),
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
     * portal ele saiu e o põe de volta nele. É o mesmo caminho para os vinte
     * jogos, e por isso nenhum jogo precisa saber onde fica o nicho dele no
     * claustro.
     */
    protected void voltarAoLobby(Player jogador) {
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
     * Vale também para quem visita o mundo antes de a arena existir. Só
     * resgata se o saguão já estiver de pé — mandar alguém para um chão que
     * não existe é o mesmo void outra vez.
     */
    private void resgatarDoVoid(World mundo) {
        if (!arenaConstruida(mundo)) {
            return;
        }
        Location saguao = pontoDoSaguao(mundo);
        for (Player jogador : jogadoresDaArena()) {
            if (estaVivo(jogador) || jogador.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (jogador.getLocation().getY() >= planta().quedaY()) {
                continue;
            }
            jogador.setFallDistance(0f);
            velocidadeNormal(jogador);
            jogador.teleport(saguao);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#8FD3FF>o saguão te pegou no ar</color>"));
        }
    }

    // ------------------------------------------------------------------
    //  Barra e avisos
    // ------------------------------------------------------------------

    private void atualizarBarra() {
        if (barra == null) {
            return;
        }
        PlantaDeArena planta = planta();
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
                barra.name(plugin.mini().deserialize("<color:" + cor + ">"
                        + textoDaBarra() + "</color>"));
                barra.progress(ArenaComum.limite(progressoDaBarra()));
                barra.color(BossBar.Color.RED);
            }
            case FIM -> {
                barra.name(plugin.mini().deserialize("<gray>fim da rodada</gray>"));
                barra.progress(ArenaComum.limite(contagem / (float) planta.fimSegundos()));
                barra.color(BossBar.Color.GREEN);
            }
        }
    }

    protected void barraDeProgresso(float valor, BossBar.Color cor) {
        if (barra != null) {
            barra.progress(ArenaComum.limite(valor));
            barra.color(cor);
        }
    }

    protected List<Player> naFila() {
        List<Player> saida = new ArrayList<>();
        for (UUID id : fila) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    protected List<Player> jogadoresDaArena() {
        List<Player> saida = new ArrayList<>();
        for (UUID id : naArena) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    protected List<Player> jogadoresVivos() {
        List<Player> saida = new ArrayList<>();
        for (UUID id : vivos) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    protected void anunciar(String mini) {
        Component texto = plugin.mini().deserialize(mini);
        for (Player jogador : jogadoresDaArena()) {
            jogador.sendMessage(texto);
        }
    }

    protected void tituloEmTodos(String titulo, String legenda) {
        Title aviso = Title.title(plugin.mini().deserialize(titulo),
                plugin.mini().deserialize(legenda));
        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(aviso);
        }
    }

    // ------------------------------------------------------------------
    //  Eventos
    // ------------------------------------------------------------------

    protected boolean naArena(Player jogador) {
        return naArena.contains(jogador.getUniqueId());
    }

    protected boolean nesteMundo(World mundo) {
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
    public void aoEntrarNoServidor(PlayerJoinEvent evento) {
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
        aoSairDaArena(id);
    }

    /**
     * O clique direito nas placas.
     *
     * Compara COORDENADA, e não o texto da placa. Ler o texto para decidir o
     * que fazer é o jeito dos plugins de referência — eles precisam, porque lá
     * as placas são postas à mão em qualquer lugar. Aqui as duas nascem da
     * planta e o jogo sabe onde elas estão, então comparar posição é imune a
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
        PlantaDeArena planta = planta();
        if (ArenaComum.mesmoBloco(bloco, SaguaoPadrao.pontoDaPlacaEntrar(planta))) {
            evento.setCancelled(true);
            entrarNaFila(evento.getPlayer());
        } else if (ArenaComum.mesmoBloco(bloco, SaguaoPadrao.pontoDaPlacaSair(planta))) {
            evento.setCancelled(true);
            sairDaFila(evento.getPlayer());
        }
    }

    /** Dano nenhum por padrão: a mecânica de quase todo jogo daqui é cair. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (evento.getEntity() instanceof Player jogador && naArena(jogador)
                && !permiteDano(evento)) {
            evento.setCancelled(true);
        }
    }

    /** Jogo de luta liga isto. Por padrão, nada machuca. */
    protected boolean permiteDano(EntityDamageEvent evento) {
        return false;
    }

    /**
     * A animação do golpe: o lampejo vermelho de quem levou.
     *
     * <h2>Por que isto precisa existir</h2>
     *
     * Toda arena daqui cancela o dano do Minecraft — a vida é número do
     * plugin e os corações são só o mostrador. O que vai junto, sem avisar, é
     * TODA a reação visual do golpe: o alvo não pisca vermelho, não se
     * inclina para o lado de onde veio a pancada, e o som de dor toca só para
     * ele. Quem bate vê o próprio braço girar (isso o cliente desenha
     * sozinho) e nada acontecer do outro lado. O golpe acerta, tira vida, e
     * parece que errou.
     *
     * <p>É um defeito difícil de nomear jogando — ninguém diz "falta o
     * ClientboundHurtAnimationPacket", diz "tá estranho". E é o tipo de coisa
     * que não aparece em teste nenhum, porque a conta do dano está certa.
     *
     * <h2>O ângulo é relativo ao rosto do alvo</h2>
     *
     * O {@code playHurtAnimation} do Paper quer o ângulo de ONDE VEIO a
     * pancada, contado a partir da cara do jogador: zero é de frente. É o que
     * faz a tela dele inclinar para o lado certo, e é por isso que não dá
     * para passar o yaw absoluto da direção — de costas, a inclinação sairia
     * invertida e o jogador olharia para o lado errado.
     *
     * <p>No arcabouço porque são cinco jogos com briga e seriam cinco cópias:
     * o sexto esqueceria, e o defeito voltaria num jogo só, que é a pior
     * forma de ele voltar.
     *
     * @param alvo   quem levou
     * @param deOnde de onde veio a pancada
     */
    protected void animarGolpe(Player alvo, Location deOnde) {
        animarGolpe(alvo, deOnde, true);
    }

    /**
     * O mesmo, para o jogo que empurra com a força DELE.
     *
     * A Arena PVP e o Sky Wars escalam o empurrão pela prontidão da espada —
     * golpe no tempo certo joga mais longe que golpe apressado —, e isso é
     * metade do que se aprende nessas duas arenas. Elas passam
     * {@code false} aqui e chamam o {@code empurrar} de quatro argumentos
     * com os próprios números; sem este parâmetro, o empurrão genérico do
     * arcabouço seria agendado junto e os dois disputariam o mesmo tique.
     */
    protected void animarGolpe(Player alvo, Location deOnde, boolean empurra) {
        if (alvo == null || deOnde == null) {
            return;
        }
        Location onde = alvo.getLocation();
        double dx = deOnde.getX() - onde.getX();
        double dz = deOnde.getZ() - onde.getZ();
        float daPancada = dx * dx + dz * dz < 1.0E-6
                ? onde.getYaw()
                : ArenaComum.yawDaDirecao(dx, dz);
        alvo.playHurtAnimation(daPancada - onde.getYaw());
        if (empurra) {
            empurrar(alvo, deOnde);
        }
        // No mundo e não no jogador: o som de dor é a confirmação de quem
        // BATEU, e tocado só para o alvo ele nunca chega em quem precisa.
        alvo.getWorld().playSound(onde, Sound.ENTITY_PLAYER_HURT, 0.9f, 1.0f);
    }

    /**
     * O empurrão do golpe.
     *
     * Faltava, e a falta era visível: o golpe piscava vermelho, tirava vida
     * e deixava os dois jogadores exatamente onde estavam. Quem apanhava não
     * sentia nada, quem batia não via nada acontecer, e em Sky Wars e Bed
     * Wars o empurrão não é enfeite — derrubar é metade do jogo.
     *
     * <p>Ele não vinha de graça porque estes jogos calculam o dano SOZINHOS,
     * sem passar pelo dano do Minecraft: sem o dano baunilha não há repulsa
     * baunilha. O jeito de ter os dois é este, aplicar a repulsa à mão.
     *
     * <p>A conta é a do Minecraft: a velocidade de agora vale metade, o
     * empurrão soma por cima, e a subida tem teto — sem o teto, dois golpes
     * seguidos jogam o jogador para fora do mapa.
     */
    protected void empurrar(Player alvo, Location deOnde) {
        empurrar(alvo, deOnde, forcaDoEmpurrao(), alturaDoEmpurrao());
    }

    /** O mesmo, com a força dita pelo jogo em vez da do config.yml. */
    protected void empurrar(Player alvo, Location deOnde, double forca,
                            double alto) {
        if (alvo == null || deOnde == null) {
            return;
        }
        Location onde = alvo.getLocation();
        double[] fuga = ArenaComum.empurrao(
                onde.getX() - deOnde.getX(), onde.getZ() - deOnde.getZ(),
                alvo.getVelocity().getX(), alvo.getVelocity().getY(),
                alvo.getVelocity().getZ(),
                forca, alto, tetoDoEmpurrao());

        // NO TIQUE SEGUINTE, e esta linha é o conserto inteiro.
        //
        // Aplicar a velocidade DENTRO do evento de dano não funciona: o
        // Minecraft resolve o empurrão dele depois do evento e sobrescreve
        // o nosso, e quando o evento é cancelado — que é o caso em todos os
        // vinte, porque o dano é calculado aqui — o cancelamento devolve a
        // velocidade de antes. Nos dois casos o jogador leva o golpe e não
        // sai do lugar, que foi o relato.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (alvo.isOnline()) {
                alvo.setVelocity(new org.bukkit.util.Vector(
                        fuga[0], fuga[1], fuga[2]));
                alvo.setFallDistance(0f);
            }
        });
    }

    protected double forcaDoEmpurrao() {
        return plugin.getConfig().getDouble("minigames.empurrao", 0.42);
    }

    protected double alturaDoEmpurrao() {
        return plugin.getConfig().getDouble("minigames.empurrao-alto", 0.36);
    }

    protected double tetoDoEmpurrao() {
        return plugin.getConfig().getDouble("minigames.empurrao-teto", 0.42);
    }

    /** O mesmo, quando quem bateu é um jogador. */
    protected void animarGolpe(Player alvo, Player quemBateu) {
        animarGolpe(alvo, quemBateu, true);
    }

    protected void animarGolpe(Player alvo, Player quemBateu,
                               boolean empurra) {
        animarGolpe(alvo, quemBateu == null ? null : quemBateu.getLocation(),
                empurra);
    }

    /**
     * O kit da arena não se gasta.
     *
     * Antes isto era um item "inquebrável", e inquebrável é NBT que todo
     * plugin de anti-exploit trata como sinal de item duplicado ou forjado —
     * o CreativePlots deste servidor apagava a espada da mão do jogador no
     * meio da partida, com um aviso no console e nenhum no jogo. Cancelar o
     * desgaste faz a mesma coisa pelo lado de cá: o item é comum, nenhum
     * plugin estranha, e o arcabouço é quem garante que ele dura a rodada.
     *
     * <p>No arcabouço e não em cada jogo, porque a arena que esquecesse de
     * fazer isto só mostraria o defeito na partida em que a espada quebrasse
     * — que é tarde, e é raro, e por isso ninguém acharia o motivo.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoGastarItem(PlayerItemDamageEvent evento) {
        if (naArena(evento.getPlayer())) {
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
     * Quebrar bloco na arena.
     *
     * O passe de administrador vale só para quem NÃO está jogando. Antes
     * valia sempre, e isso fazia o jogo ignorar as próprias regras para o
     * dono do servidor: no Bed Wars a cama dele saía num golpe sem contar
     * golpe nenhum, e testar o jogo virava testar outro jogo. Quem entrou
     * na rodada joga a rodada; quem está de criativo olhando continua
     * podendo mexer na arena.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        if (!nesteMundo(evento.getBlock().getWorld())) {
            return;
        }
        if (jogador.getGameMode() == GameMode.CREATIVE
                || (!estaVivo(jogador)
                        && jogador.hasPermission("bigacore.admin"))) {
            return;
        }
        if (!permiteQuebrar(evento)) {
            evento.setCancelled(true);
        }
    }

    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (nesteMundo(evento.getBlock().getWorld())
                && !evento.getPlayer().hasPermission("bigacore.admin")
                && !permiteColocar(evento)) {
            evento.setCancelled(true);
        }
    }

    protected boolean permiteColocar(BlockPlaceEvent evento) {
        return false;
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
}
