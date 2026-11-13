package codes.biga.bigacore;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O BED WARS: proteja a sua cama.
 *
 * <h2>As três reclamações do gênero, e o que fazemos diferente</h2>
 *
 * As três têm a mesma raiz, e ela não é dificuldade: é a partida acabar para
 * você antes de você ter jogado.
 *
 * <ul>
 *   <li><b>"Rush demais, e não dá tempo de proteger a cama."</b> A proposta
 *       que aparece no fórum é um minuto de proteção, e ela não resolve:
 *       carência só adia o rush. Aqui a cama CUSTA tempo — oito golpes com
 *       dezesseis tiques entre um e outro — e, o que importa mais, cada golpe
 *       é ANUNCIADO ao time dono com o nome de quem bateu e de que lado
 *       ({@link #avisarOsDonos}). O defensor não ganha um atraso: ganha
 *       informação, que é o que faltava para ele chegar em tempo.</li>
 *   <li><b>"Sua cama foi destruída, você agora é espectador."</b> É o título
 *       literal de uma reclamação de fórum. Aqui a cama é o RESPAWN e não a
 *       vida: quando ela cai, o time passa a ter {@code vidas-do-time} vidas
 *       compartilhadas e continua jogando. Quem perde a cama primeiro ainda
 *       tem minutos de partida — e passa a ter um motivo para atacar, porque
 *       defender já não serve.</li>
 *   <li><b>"O colega fica parado em cima do gerador."</b> A reclamação mais
 *       repetida de todas, e é defeito de desenho: se o recurso nasce na sua
 *       base, ficar em casa É a jogada ótima. Aqui o tear é no meio e
 *       {@link #cuidarDoTear} só rende para quem está lá. Não existe lugar
 *       seguro onde ficar parado dê lucro.</li>
 * </ul>
 *
 * <h2>A cama se cura</h2>
 *
 * Um golpe de volta a cada {@code cura-da-cama} segundos sem ninguém encostar
 * nela. É a metade que faltava do primeiro conserto: avisar o defensor não
 * serve de nada se, quando ele chegar e expulsar o invasor, a cama continuar
 * pela metade para sempre. Expulsar tem de devolver terreno.
 *
 * <h2>A lã é da cor do time</h2>
 *
 * Cada bloco de defesa diz quem o pôs, e só se quebra o bloco que alguém
 * colocou — as bases não se mineram. Sem isso, cavar a própria base seria a
 * primeira jogada de todo mundo, e quem cavasse mais rápido teria mais
 * defesa, o que traz de volta pela porta dos fundos a desigualdade que o kit
 * igual tirou pela da frente.
 */
public final class BedWars extends JogoDeArena {

    /** Quantos tiques sem tomar dano antes de a barra de aviso voltar. */
    private static final int CALMA = 30;

    private final Placar placar = new Placar();

    private final Map<UUID, Integer> timeDe = new HashMap<>();
    private final Map<UUID, Integer> vida = new HashMap<>();
    private final Map<UUID, Long> carencia = new HashMap<>();
    private final Map<UUID, Long> voltaEm = new HashMap<>();
    private final Map<UUID, Long> ultimaLa = new HashMap<>();
    private final Map<UUID, Integer> eliminacoes = new HashMap<>();
    private final Map<UUID, Integer> camasQuebradas = new HashMap<>();

    /** Quem bateu por último em quem, e quando: o crédito da queda. */
    private final Map<UUID, UUID> agressor = new HashMap<>();
    private final Map<UUID, Long> tiqueDoGolpe = new HashMap<>();

    /** Os blocos que alguém colocou: os únicos que podem ser quebrados. */
    private final Set<Long> colocados = new HashSet<>();

    /** Quem está morto esperando a volta. */
    private final Set<UUID> esperando = new LinkedHashSet<>();

    private List<Set<UUID>> membros = new ArrayList<>();
    private int[] golpesDaCama = new int[0];
    private long[] ultimoGolpeNaCama = new long[0];
    private boolean[] camaDePe = new boolean[0];
    private int[] vidasDoTime = new int[0];
    private boolean[] timeNaPartida = new boolean[0];
    private int usados;

    public BedWars(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.bedWarsCatalog();
    }

    private BedWarsCatalog planta2() {
        return plugin.bedWarsCatalog();
    }

    /** Sobrevivência, porque aqui se constrói com a lã do tear. */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.SURVIVAL;
    }


    @Override
    protected String verboDaLargada() {
        return "DEFENDA!";
    }

    @Override
    protected void ajustesDoMundo(World mundo) {
        mundo.setPVP(true);
        mundo.setGameRule(GameRule.NATURAL_REGENERATION, false);
        mundo.setGameRule(GameRule.FALL_DAMAGE, false);
    }

    /**
     * O vazio não elimina ninguém por si: ele mata, e morrer é outra coisa.
     *
     * O arcabouço trata "caiu fora" como fim de partida para o jogador, e
     * neste jogo isso seria justamente o defeito que estamos consertando.
     * Quem cai morre, e morrer leva ao respawn enquanto houver cama ou vida —
     * a queda é tratada em {@link #aCadaTique}.
     */
    @Override
    protected boolean caiuFora(Player jogador) {
        return false;
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    /** A lista lateral mostra o placar deste jogo. */
    @Override
    protected Placar placarDaLateral() {
        return placar;
    }

    @Override
    protected void prepararArena(World mundo) {
        BedWarsCatalog planta = planta2();
        BedWarsBuilder obra = plugin.bedWarsBuilder();
        int times = planta.times();

        placar.zerar();
        timeDe.clear();
        vida.clear();
        carencia.clear();
        voltaEm.clear();
        ultimaLa.clear();
        eliminacoes.clear();
        camasQuebradas.clear();
        agressor.clear();
        tiqueDoGolpe.clear();
        esperando.clear();

        apagarAsDefesas(mundo);

        membros = new ArrayList<>();
        golpesDaCama = new int[times];
        ultimoGolpeNaCama = new long[times];
        camaDePe = new boolean[times];
        vidasDoTime = new int[times];
        timeNaPartida = new boolean[times];
        for (int k = 0; k < times; k++) {
            membros.add(new LinkedHashSet<>());
            golpesDaCama[k] = planta.golpesDaCama();
            ultimoGolpeNaCama[k] = Long.MIN_VALUE;
            camaDePe[k] = true;
            vidasDoTime[k] = 0;
            obra.refazerACama(mundo, planta, k);
        }
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        BedWarsCatalog planta = planta2();
        usados = BedWarsCatalog.timesUsados(entram.size(), planta.times());
        List<Integer> sorteio = BedWarsCatalog.distribuirTimes(entram.size(),
                planta.times());

        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            int time = sorteio.get(k);
            timeDe.put(id, time);
            membros.get(time).add(id);
            timeNaPartida[time] = true;
            vida.put(id, planta.vidaCheia());
            eliminacoes.put(id, 0);
            camasQuebradas.put(id, 0);
            placar.entrar(id);
            levarAoNascimento(jogador, planta, time);
            darOKit(jogador, planta, time);
            mostrarVida(jogador);
        }

        // As camas dos times que não entraram saem da arena: uma cama de pé
        // sem ninguém atrás dela faria o placar dizer que alguém está
        // ganhando sem jogar.
        for (int k = 0; k < planta.times(); k++) {
            if (!timeNaPartida[k]) {
                plugin.bedWarsBuilder().derrubarACama(mundo, planta, k);
                camaDePe[k] = false;
            }
        }
    }

    @Override
    protected void instrucoes(Player jogador) {
        BedWarsCatalog planta = planta2();
        int travessia = planta.travessiaEntreCamas();
        dizer(jogador, "<gray>a sua cama custa <white>" + planta.golpesDaCama()
                + " golpes</white> e <white>cada golpe avisa</white> o seu "
                + "time, com nome e lado. Ela também <white>se cura</white> um "
                + "golpe a cada " + planta.curaDaCama() + "s sem ninguém "
                + "encostar: expulsar o invasor devolve terreno.</gray>");        dizer(jogador, "<gray>a cama só pode ser batida depois de <white>"
                + planta.gracaSegundos() + "s</white>, que é o tempo de ir de "
                + "uma cama até a outra andando (<white>" + travessia
                + " blocos</white>) — o rush não é de graça nem é "
                + "proibido.</gray>");        dizer(jogador, "<gray>a lã sai <white>só do tear</white>, no meio, e é da "
                + "<white>cor do seu time</white>. Não há gerador em casa: "
                + "ficar parado não rende nada em lugar nenhum.</gray>");        dizer(jogador, "<gray>sem cama, o time passa a ter <white>"
                + planta.vidasDoTime() + " vidas</white> e <white>continua "
                + "jogando</white> — ninguém vira espectador na primeira "
                + "cama que cai.</gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        BedWarsCatalog planta = planta2();
        int travessia = planta.travessiaEntreCamas();
        mostrarOPainel(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return BedWarsBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEntrarNaArena(Player jogador) {
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        Integer time = timeDe.remove(id);
        if (time != null && time >= 0 && time < membros.size()) {
            membros.get(time).remove(id);
        }
        vida.remove(id);
        carencia.remove(id);
        voltaEm.remove(id);
        ultimaLa.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
        esperando.remove(id);
    }

    @Override
    protected void aoEliminarJogador(Player jogador) {
        UUID id = jogador.getUniqueId();
        vida.remove(id);
        carencia.remove(id);
        voltaEm.remove(id);
        esperando.remove(id);
        jogador.getInventory().clear();
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            devolverAVidaDoMinecraft(jogador);
        }
        if (mundo != null) {
            ArenaComum.limparPaineis(mundo, BedWarsBuilder.TAG_GUERRA);
        }
    }

    /**
     * A rodada acaba quando sobra um time, ou no relógio.
     *
     * Conta TIMES e não jogadores, porque um time de quatro com três mortos
     * esperando a volta continua inteiro: a cama é que diz se ele está na
     * partida.
     */
    @Override
    protected boolean rodadaAcabou() {
        if (segundosDeJogo() >= planta2().tempoLimite()) {
            return true;
        }
        int vivos = timesVivos();
        if (comecouCom() <= 1 && planta2().treinoSozinho()) {
            return vivos == 0;
        }
        return vivos <= 1;
    }

    /** Quantos times ainda têm gente na partida. */
    private int timesVivos() {
        int quantos = 0;
        for (int k = 0; k < membros.size(); k++) {
            if (gentePorTime(k) > 0) {
                quantos++;
            }
        }
        return quantos;
    }

    private int gentePorTime(int k) {
        if (k < 0 || k >= membros.size()) {
            return 0;
        }
        int quantos = 0;
        for (UUID id : membros.get(k)) {
            if (vivos().contains(id)) {
                quantos++;
            }
        }
        return quantos;
    }

    @Override
    protected String vencedor() {
        int ganhou = -1;
        for (int k = 0; k < membros.size(); k++) {
            if (gentePorTime(k) > 0) {
                if (ganhou >= 0) {
                    return null;
                }
                ganhou = k;
            }
        }
        if (ganhou < 0) {
            return null;
        }
        return planta2().time(ganhou).nome();
    }

    @Override
    protected String legendaDaVitoria() {
        int ganhou = -1;
        for (int k = 0; k < membros.size(); k++) {
            if (gentePorTime(k) > 0 && ganhou < 0) {
                ganhou = k;
            }
        }
        if (ganhou < 0) {
            return "nenhum time sobrou";
        }
        List<String> nomes = new ArrayList<>();
        for (UUID id : membros.get(ganhou)) {
            Player quem = Bukkit.getPlayer(id);
            if (quem != null && vivos().contains(id)) {
                nomes.add(quem.getName());
            }
        }
        String comCama = camaDePe[ganhou] ? "com a cama de pé"
                : "sem a cama, de vida em vida";
        return String.join(", ", nomes) + " · " + comCama;
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        BedWarsCatalog planta = planta2();
        if (segundosDeJogo() < planta.gracaSegundos()) {
            return "<color:" + planta.corDoTexto() + ">as camas abrem em "
                    + (planta.gracaSegundos() - segundosDeJogo())
                    + "s</color>";
        }
        StringBuilder texto = new StringBuilder();
        for (int k = 0; k < planta.times(); k++) {
            if (!timeNaPartida[k]) {
                continue;
            }
            BedWarsCatalog.Time time = planta.time(k);
            String marca = camaDePe[k]
                    ? String.valueOf(golpesDaCama[k])
                    : (gentePorTime(k) > 0 ? vidasDoTime[k] + "v" : "x");
            if (texto.length() > 0) {
                texto.append("<gray> · </gray>");
            }
            texto.append("<color:").append(time.cor()).append(">")
                    .append(time.nome().charAt(0)).append(marca)
                    .append("</color>");
        }
        return texto.toString();
    }

    // ------------------------------------------------------------------
    //  O kit e o nascimento
    // ------------------------------------------------------------------

    private void darOKit(Player jogador, BedWarsCatalog planta, int time) {
        jogador.getInventory().clear();
        ItemStack espada = new ItemStack(Material.IRON_SWORD);
        ItemMeta marca = espada.getItemMeta();
        if (marca != null) {
            marca.displayName(plugin.mini().deserialize("<color:"
                    + planta.time(time).cor() + ">Espada do "
                    + planta.time(time).nome() + "</color>"));
            espada.setItemMeta(marca);
        }
        jogador.getInventory().setItem(0, espada);
        jogador.getInventory().setHeldItemSlot(0);
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
        jogador.updateInventory();
    }

    /**
     * Põe o jogador de volta na base do time dele, jogando.
     *
     * O modo é o modo DO JOGO, e isto é um conserto. Aqui estava
     * {@code devolverModo}, que é o helper de quem está SAINDO da arena: ele
     * devolve o modo que o jogador tinha ANTES de entrar e apaga o registro
     * ao devolver. Numa volta de respawn isso errava duas vezes — na
     * primeira morte repunha o modo do hub em vez do modo do jogo, e na
     * segunda não havia mais registro nenhum para repor, então o jogador
     * ficava espectador até o fim da partida com a cama de pé. Era o relato
     * de "morreu, virou espectador e não voltou".
     */
    private void levarAoNascimento(Player jogador, BedWarsCatalog planta,
                                   int time) {
        jogador.teleport(BedWarsBuilder.pontoDaBase(jogador.getWorld(),
                planta, time));
        jogador.setFallDistance(0f);
        jogador.setGameMode(modoDeJogo());
        JogoDeArena.velocidadeNormal(jogador);
    }

    // ------------------------------------------------------------------
    //  O golpe
    // ------------------------------------------------------------------

    /**
     * O golpe é a conta da ARENA PVP, e não acerta o próprio time.
     *
     * Não acerta o próprio time porque num jogo de quatro bases o erro de
     * mirar no colega acontece toda partida, e um jogo que pune a confusão de
     * cores com a vida do colega estaria punindo o seu próprio desenho.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoBater(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player alvo)
                || !(evento.getDamager() instanceof Player quemBateu)) {
            return;
        }
        if (!jogando() || !naArena(alvo) || !naArena(quemBateu)) {
            return;
        }
        if (!estaVivo(alvo) || !estaVivo(quemBateu) || alvo.equals(quemBateu)) {
            return;
        }
        if (esperando.contains(alvo.getUniqueId())) {
            return;
        }
        BedWarsCatalog planta = planta2();
        UUID id = alvo.getUniqueId();
        int meuTime = timeDe.getOrDefault(quemBateu.getUniqueId(), -1);
        int seuTime = timeDe.getOrDefault(id, -2);
        if (meuTime == seuTime) {
            quemBateu.sendActionBar(plugin.mini().deserialize("<gray>é do "
                    + "<color:" + planta.time(meuTime).cor() + ">seu time"
                    + "</color></gray>"));
            return;
        }
        if (carencia.getOrDefault(id, 0L) > tiquesDeJogo()) {
            return;
        }
        double prontidao = quemBateu.getAttackCooldown();
        carencia.put(id, tiquesDeJogo() + planta.periodoDoGolpe());

        int dano = Math.max(1, (int) Math.round(planta.danoDoGolpe()
                * ArenaPvpCatalog.forcaDoGolpe(prontidao)));
        int sobrou = Math.max(0,
                vida.getOrDefault(id, planta.vidaCheia()) - dano);
        vida.put(id, sobrou);
        agressor.put(id, quemBateu.getUniqueId());
        tiqueDoGolpe.put(id, tiquesDeJogo());
        placar.somar(quemBateu.getUniqueId(), dano);
        mostrarVida(alvo);
        empurrar(quemBateu, alvo, planta, prontidao);

        String cor = prontidao >= 0.9 ? "#8BE04E"
                : prontidao >= 0.6 ? "#FFD93D" : "#FF8A8A";
        quemBateu.sendActionBar(plugin.mini().deserialize("<color:" + cor + ">"
                + (int) Math.round(prontidao * 100) + "% de golpe</color> "
                + "<gray>— tirou <white>" + dano + "</white> de "
                + HubBuilder.escapar(alvo.getName()) + ", restam <white>"
                + sobrou + "</white></gray>"));
        animarGolpe(alvo, quemBateu);
        alvo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                alvo.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.05);

        if (sobrou <= 0) {
            morrer(alvo, planta, "foi derrubado");
        }
    }

    /**
     * O empurrão do golpe, com a força escalada pela prontidão.
     *
     * A aplicação em si é a do arcabouço, que faz no tique SEGUINTE — dentro
     * do evento de dano o Minecraft sobrescreve a velocidade e o jogador
     * leva o golpe sem sair do lugar.
     */
    private void empurrar(Player quemBateu, Player alvo,
                          BedWarsCatalog planta, double prontidao) {
        empurrar(alvo, quemBateu.getLocation(),
                ArenaPvpCatalog.empurraoDoGolpe(prontidao, planta.empurrao()),
                ArenaPvpCatalog.empurraoDoGolpe(prontidao,
                        planta.empurraoAltura()));
    }

    // ------------------------------------------------------------------
    //  Morrer, voltar e sair de vez
    // ------------------------------------------------------------------

    /**
     * Morrer: volta se houver cama, volta se houver vida, sai se não houver.
     *
     * É o conserto da reclamação cujo título é "sua cama foi destruída, você
     * agora é espectador". Perder a cama tira o respawn de graça e põe uma
     * CONTA no lugar; só quando a conta zera o jogador sai da partida. A
     * diferença prática é de minutos, e é o que faz valer a pena continuar
     * jogando depois de ter perdido a cama.
     */
    private void morrer(Player jogador, BedWarsCatalog planta, String como) {
        UUID id = jogador.getUniqueId();
        if (esperando.contains(id) || !estaVivo(jogador)) {
            return;
        }
        int time = timeDe.getOrDefault(id, -1);
        creditar(id, jogador, planta);

        if (time >= 0 && camaDePe[time]) {
            esperar(jogador, planta, como, "a sua cama está de pé");
            return;
        }
        if (time >= 0 && vidasDoTime[time] > 0) {
            vidasDoTime[time]--;
            BedWarsCatalog.Time dados = planta.time(time);
            avisarOTime(time, "<color:" + dados.cor() + ">"
                    + HubBuilder.escapar(jogador.getName()) + "</color> "
                    + "<gray>" + como + " — o time tem <white>"
                    + vidasDoTime[time] + "</white> vida(s)</gray>");
            esperar(jogador, planta, como,
                    "restam " + vidasDoTime[time] + " vida(s) ao time");
            return;
        }
        // Sem cama e sem vida: agora sim a partida acabou para este jogador.
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>SEM VOLTA</color>"),
                plugin.mini().deserialize("<gray>a cama caiu e as vidas "
                        + "acabaram</gray>")));
        eliminar(jogador);
        if (time >= 0 && gentePorTime(time) == 0) {
            anunciar("<color:" + planta.time(time).cor() + ">"
                    + planta.time(time).nome() + "</color> <gray>saiu da "
                    + "partida — sobram <white>" + timesVivos()
                    + "</white> time(s)</gray>");
        }
        mostrarOPainel(jogador.getWorld(), planta);
    }

    /** Põe o jogador na espera da volta. */
    private void esperar(Player jogador, BedWarsCatalog planta, String como,
                         String porque) {
        UUID id = jogador.getUniqueId();
        esperando.add(id);
        voltaEm.put(id, tiquesDeJogo() + planta.respawnSegundos() * 20L);
        vida.put(id, planta.vidaCheia());
        carencia.remove(id);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(pontoDaPlateia(jogador.getWorld()));
        devolverAVidaDoMinecraft(jogador);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>VOCÊ "
                        + como.toUpperCase() + "</color>"),
                plugin.mini().deserialize("<gray>" + porque + " · volta em "
                        + planta.respawnSegundos() + "s</gray>")));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_HURT,
                1.0f, 0.7f);
    }

    /** Devolve o jogador à base dele. */
    private void voltar(Player jogador, BedWarsCatalog planta) {
        UUID id = jogador.getUniqueId();
        esperando.remove(id);
        voltaEm.remove(id);
        int time = timeDe.getOrDefault(id, 0);
        vida.put(id, planta.vidaCheia());
        levarAoNascimento(jogador, planta, time);
        darOKit(jogador, planta, time);
        mostrarVida(jogador);
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:"
                        + planta.time(time).cor() + ">DE VOLTA</color>"),
                plugin.mini().deserialize("<gray>a lã fica no tear, no "
                        + "meio</gray>")));
        jogador.playSound(jogador.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    private void creditar(UUID id, Player alvo, BedWarsCatalog planta) {
        UUID quem = agressor.get(id);
        long quando = tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE);
        if (quem == null || tiquesDeJogo() - quando > 8L * 20) {
            return;
        }
        placar.somar(quem, planta.pontosPorEliminacao());
        int quantas = eliminacoes.merge(quem, 1, Integer::sum);
        Player dono = Bukkit.getPlayer(quem);
        if (dono != null) {
            dono.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>"
                    + HubBuilder.escapar(alvo.getName()) + " caiu</color> "
                    + "<gray>— " + quantas + " no total</gray>"));
        }
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
    }

    // ------------------------------------------------------------------
    //  A cama
    // ------------------------------------------------------------------

    /**
     * O clique na cama: bater pela esquerda, e NUNCA dormir nela.
     *
     * <p><b>O direito está cancelado em TODA cama</b>, a sua e a do inimigo.
     * O Minecraft responde ao clique direito numa cama marcando o ponto de
     * renascimento do jogador ali — e num servidor onde o Bed Wars é um
     * mundo entre outros, isso tira o jogador do mundo dele e o joga numa
     * arena quando ele morrer em qualquer lugar. É o pior defeito que este
     * jogo teve: um clique sem querer, dentro de uma partida, muda para onde
     * o jogador nasce no servidor inteiro.
     *
     * <p><b>O esquerdo conta o golpe aqui</b>, e não só no quebrar bloco. O
     * evento de quebra pode não chegar: quem tem permissão de administrador
     * passa direto pela conferência, e qualquer plugin de proteção que
     * cancele a quebra antes faz o golpe sumir sem aviso — foi o relato de
     * "não dá para bater na cama". No clique o golpe sempre chega, e o
     * período entre golpes impede que os dois caminhos contem duas vezes.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoClicarNaCama(PlayerInteractEvent evento) {
        Block bloco = evento.getClickedBlock();
        if (bloco == null || !nesteMundo(bloco.getWorld())) {
            return;
        }
        if (!ehCama(bloco.getType())) {
            return;
        }
        Player jogador = evento.getPlayer();

        if (evento.getAction() == Action.RIGHT_CLICK_BLOCK) {
            evento.setCancelled(true);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>não se dorme na cama aqui</color> "
                            + "<gray>— ela é o seu respawn na partida, "
                            + "não no servidor</gray>"));
            return;
        }
        if (evento.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        evento.setCancelled(true);
        if (!jogando() || !estaVivo(jogador)
                || esperando.contains(jogador.getUniqueId())) {
            return;
        }
        BedWarsCatalog planta = planta2();
        int qual = BedWarsBuilder.camaDoBloco(bloco, planta);
        if (qual != BedWarsCatalog.FORA) {
            baterNaCama(jogador, planta, qual);
        }
    }

    /**
     * Nem deitar.
     *
     * O clique direito já está cancelado, mas este evento também chega por
     * outros caminhos — um plugin, um comando — e deitar na cama marca o
     * renascimento do mesmo jeito.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoDeitar(PlayerBedEnterEvent evento) {
        if (nesteMundo(evento.getPlayer().getWorld())) {
            evento.setCancelled(true);
        }
    }

    /** Todo bloco de cama, de qualquer cor. */
    static boolean ehCama(Material material) {
        return material != null && Tag.BEDS.isTagged(material);
    }

    /**
     * Quebrar: a cama conta golpes, e o resto só se quebra se foi posto.
     *
     * O evento de quebra chega uma vez por clique, porque a cama é bloco de
     * quebra instantânea — então cada clique vale um golpe, e
     * {@code periodo-da-cama} é o que dá o ritmo. A quebra em si é sempre
     * recusada: a cama sai do mundo quando a CONTA zera, não quando o
     * Minecraft decide.
     */
    @Override
    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)
                || esperando.contains(jogador.getUniqueId())) {
            return false;
        }
        BedWarsCatalog planta = planta2();
        Block bloco = evento.getBlock();
        int daCama = BedWarsBuilder.camaDoBloco(bloco, planta);
        if (daCama != BedWarsCatalog.FORA) {
            baterNaCama(jogador, planta, daCama);
            return false;
        }
        long chave = ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ());
        if (!colocados.remove(chave)) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>as bases não se mineram</color> <gray>— "
                            + "só se quebra o que alguém colocou</gray>"));
            return false;
        }
        return true;
    }

    /** Um golpe na cama de um time, com todas as recusas que ele tem. */
    private void baterNaCama(Player jogador, BedWarsCatalog planta, int qual) {
        UUID id = jogador.getUniqueId();
        int meuTime = timeDe.getOrDefault(id, -1);
        if (qual == meuTime) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>essa é a <white>sua</white> cama</gray>"));
            return;
        }
        if (!camaDePe[qual]) {
            return;
        }
        int falta = planta.gracaSegundos() - segundosDeJogo();
        if (falta > 0) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FFD93D>as camas abrem em " + falta + "s</color> "
                            + "<gray>— é o tempo da travessia</gray>"));
            return;
        }
        if (tiquesDeJogo() - ultimoGolpeNaCama[qual]
                < planta.periodoDaCama()) {
            return;
        }
        ultimoGolpeNaCama[qual] = tiquesDeJogo();
        golpesDaCama[qual] = Math.max(0, golpesDaCama[qual] - 1);
        placar.somar(id, planta.pontosPorGolpeNaCama());

        BedWarsCatalog.Time dados = planta.time(qual);
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_WOOL_BREAK, 1f,
                0.8f);
        jogador.sendActionBar(plugin.mini().deserialize("<color:"
                + dados.cor() + ">cama do " + dados.nome() + "</color> "
                + "<gray>— faltam <white>" + golpesDaCama[qual]
                + "</white> golpe(s)</gray>"));
        avisarOsDonos(jogador, planta, qual);

        if (golpesDaCama[qual] <= 0) {
            derrubarACama(jogador, planta, qual);
        }
    }

    /**
     * O aviso que conserta a reclamação do rush.
     *
     * Nome de quem está batendo, quantos golpes faltam e DE QUE LADO ele
     * veio. O lado é o que transforma o aviso em jogada: sem ele o defensor
     * corre para a cama e dá de cara com o invasor; com ele, escolhe por onde
     * chegar.
     */
    private void avisarOsDonos(Player quemBate, BedWarsCatalog planta,
                               int qual) {
        BedWarsCatalog.Time dados = planta.time(qual);
        String lado = BedWarsCatalog.ladoDaInvasao(
                quemBate.getLocation().getX(), quemBate.getLocation().getZ(),
                planta.pontoDaCama(qual));
        String recado = "<color:#FF8A8A><b>A SUA CAMA</b></color> <gray>está "
                + "levando golpe de <white>"
                + HubBuilder.escapar(quemBate.getName())
                + "</white>, vindo do <white>" + lado + "</white> — faltam "
                + "<white>" + golpesDaCama[qual] + "</white> golpe(s)</gray>";
        for (UUID id : membros.get(qual)) {
            Player dono = Bukkit.getPlayer(id);
            if (dono == null || !vivos().contains(id)) {
                continue;
            }
            dono.sendMessage(plugin.mini().deserialize(recado));
            dono.playSound(dono.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                    1.0f, golpesDaCama[qual] <= 2 ? 1.8f : 1.2f);
            if (golpesDaCama[qual] <= 2) {
                dono.showTitle(Title.title(
                        plugin.mini().deserialize("<color:#FF8A8A>A CAMA!"
                                + "</color>"),
                        plugin.mini().deserialize("<gray>faltam "
                                + golpesDaCama[qual] + " golpe(s) · "
                                + lado + "</gray>")));
            }
        }
        if (membros.get(qual).isEmpty()) {
            return;
        }
        if (golpesDaCama[qual] == planta.golpesDaCama() - 1) {
            anunciar("<gray>a cama do <color:" + dados.cor() + ">"
                    + dados.nome() + "</color> começou a levar golpe</gray>");
        }
    }

    /** A cama caiu: o time perde o respawn e passa a contar vidas. */
    private void derrubarACama(Player quemQuebrou, BedWarsCatalog planta,
                               int qual) {
        camaDePe[qual] = false;
        vidasDoTime[qual] = planta.vidasDoTime();
        plugin.bedWarsBuilder().derrubarACama(quemQuebrou.getWorld(), planta,
                qual);
        placar.somar(quemQuebrou.getUniqueId(), planta.pontosPorCama());
        camasQuebradas.merge(quemQuebrou.getUniqueId(), 1, Integer::sum);

        BedWarsCatalog.Time dados = planta.time(qual);
        anunciar("<color:" + dados.cor() + "><b>A CAMA DO " + dados.nome()
                + " CAIU</b></color> <gray>— quebrada por <white>"
                + HubBuilder.escapar(quemQuebrou.getName())
                + "</white></gray>");
        for (UUID id : membros.get(qual)) {
            Player dono = Bukkit.getPlayer(id);
            if (dono == null) {
                continue;
            }
            dono.showTitle(Title.title(
                    plugin.mini().deserialize("<color:#FF8A8A>A CAMA CAIU"
                            + "</color>"),
                    plugin.mini().deserialize("<gray>o time tem <white>"
                            + planta.vidasDoTime() + " vidas</white> e segue "
                            + "jogando</gray>")));
            dono.sendMessage(plugin.mini().deserialize("<gray>sem cama, "
                    + "morrer gasta uma das <white>" + planta.vidasDoTime()
                    + " vidas</white> do time. Defender já não serve: "
                    + "<white>ataque</white>.</gray>"));
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.ENTITY_WITHER_DEATH, 0.4f, 1.6f);
        }
        mostrarOPainel(quemQuebrou.getWorld(), planta);
    }

    /**
     * Colocar: só a lã do seu time, e nunca em cima de cama nenhuma.
     *
     * Nunca em cima de cama porque emparedar a própria cama com lã seria
     * trocar oito golpes por uma parede, e emparedar a do vizinho seria
     * trancar o jogo dele sem nunca ter arriscado nada.
     */
    @Override
    protected boolean permiteColocar(BlockPlaceEvent evento) {
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)
                || esperando.contains(jogador.getUniqueId())) {
            return false;
        }
        BedWarsCatalog planta = planta2();
        int meuTime = timeDe.getOrDefault(jogador.getUniqueId(), -1);
        Block bloco = evento.getBlock();
        if (meuTime < 0 || bloco.getType() != planta.time(meuTime).la()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>só a <white>sua lã</white> se coloca aqui</gray>"));
            return false;
        }
        int alcance = planta.alcanceDaObra();
        if (Math.abs(bloco.getX()) > alcance || Math.abs(bloco.getZ()) > alcance
                || bloco.getY() <= planta.quedaY()
                || bloco.getY() > planta.topoY() + planta.marcoAltura()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora da arena</color>"));
            return false;
        }
        if (BedWarsBuilder.camaDoBloco(bloco, planta)
                != BedWarsCatalog.FORA) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>não se empareda cama</color> <gray>— nem "
                            + "a sua</gray>"));
            return false;
        }
        colocados.add(ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ()));
        return true;
    }

    /** Apaga toda lã posta na rodada passada. */
    private void apagarAsDefesas(World mundo) {
        for (long chave : colocados) {
            Block bloco = mundo.getBlockAt(ArenaComum.daChave(chave, 0),
                    ArenaComum.daChave(chave, 1),
                    ArenaComum.daChave(chave, 2));
            if (bloco.getType().name().endsWith("_WOOL")) {
                bloco.setType(Material.AIR, false);
            }
        }
        colocados.clear();
    }

    // ------------------------------------------------------------------
    //  O relógio
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando()) {
            return;
        }
        BedWarsCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (esperando.contains(id)) {
                continue;
            }
            if (jogador.getLocation().getY() < planta.quedaY()) {
                morrer(jogador, planta, "caiu no vazio");
            }
        }
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        if (!jogando()) {
            return;
        }
        BedWarsCatalog planta = planta2();
        cuidarDasVoltas(planta);
        curarAsCamas(planta);
        cuidarDoTear(planta);
        avisarDaGuerra(planta);
    }

    private void cuidarDasVoltas(BedWarsCatalog planta) {
        for (UUID id : new ArrayList<>(esperando)) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null || !vivos().contains(id)) {
                esperando.remove(id);
                continue;
            }
            long quando = voltaEm.getOrDefault(id, 0L);
            if (tiquesDeJogo() >= quando) {
                voltar(jogador, planta);
            } else {
                int falta = (int) Math.ceil((quando - tiquesDeJogo()) / 20.0);
                jogador.sendActionBar(plugin.mini().deserialize("<gray>volta "
                        + "em <white>" + falta + "s</white></gray>"));
            }
        }
    }

    /**
     * A cama volta um golpe por vez, se ninguém encostar nela.
     *
     * É a metade que faltava do conserto do rush: avisar o defensor não serve
     * de nada se expulsar o invasor não devolver nada. O relógio da cura é o
     * do ÚLTIMO golpe, então o invasor que volta de dois em dois segundos
     * trava a cura — e tem de ser assim, senão a cura viraria um escudo que
     * atende sozinho.
     */
    private void curarAsCamas(BedWarsCatalog planta) {
        for (int k = 0; k < planta.times(); k++) {
            if (!camaDePe[k] || golpesDaCama[k] >= planta.golpesDaCama()) {
                continue;
            }
            if (tiquesDeJogo() - ultimoGolpeNaCama[k]
                    < planta.curaDaCama() * 20L) {
                continue;
            }
            ultimoGolpeNaCama[k] = tiquesDeJogo();
            golpesDaCama[k]++;
            avisarOTime(k, "<color:#8BE04E>a cama se recuperou</color> "
                    + "<gray>— aguenta <white>" + golpesDaCama[k]
                    + "</white> golpe(s)</gray>");
        }
    }

    /**
     * O tear: lã para quem está no meio, e só para quem está no meio.
     *
     * É a resposta inteira a "o colega fica parado em cima do gerador": não há
     * gerador em casa, então não existe lugar seguro onde ficar parado dê
     * lucro. A lã sai na cor do time de quem a pegou, porque cada bloco de
     * defesa tem de dizer quem o pôs.
     */
    private void cuidarDoTear(BedWarsCatalog planta) {
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (esperando.contains(id)) {
                continue;
            }
            if (!planta.noMeio(jogador.getLocation().getX(),
                    jogador.getLocation().getZ())) {
                continue;
            }
            if (tiquesDeJogo() - ultimaLa.getOrDefault(id, Long.MIN_VALUE)
                    < planta.tearSegundos() * 20L) {
                continue;
            }
            int time = timeDe.getOrDefault(id, -1);
            if (time < 0) {
                continue;
            }
            int tem = contarLa(jogador, planta, time);
            if (tem >= planta.laMaxima()) {
                jogador.sendActionBar(plugin.mini().deserialize("<gray>a sua "
                        + "lã está no máximo (<white>" + tem
                        + "</white>)</gray>"));
                continue;
            }
            ultimaLa.put(id, tiquesDeJogo());
            int quanto = Math.min(planta.laDoTear(), planta.laMaxima() - tem);
            jogador.getInventory().addItem(
                    new ItemStack(planta.time(time).la(), quanto));
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_WOOL_PLACE,
                    0.4f, 1.6f);
            jogador.getWorld().spawnParticle(Particle.END_ROD,
                    jogador.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private int contarLa(Player jogador, BedWarsCatalog planta, int time) {
        Material qual = planta.time(time).la();
        int quantos = 0;
        for (ItemStack item : jogador.getInventory().getContents()) {
            if (item != null && item.getType() == qual) {
                quantos += item.getAmount();
            }
        }
        return quantos;
    }

    private void avisarDaGuerra(BedWarsCatalog planta) {
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (esperando.contains(id)) {
                continue;
            }
            if (tiquesDeJogo() - tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE)
                    < CALMA) {
                continue;
            }
            int time = timeDe.getOrDefault(id, -1);
            if (time < 0) {
                continue;
            }
            String daCama = camaDePe[time]
                    ? "<gray>cama <white>" + golpesDaCama[time]
                            + "</white>/" + planta.golpesDaCama() + "</gray>"
                    : "<color:#FF8A8A>sem cama · " + vidasDoTime[time]
                            + " vida(s)</color>";
            int falta = planta.gracaSegundos() - segundosDeJogo();
            String graca = falta > 0
                    ? " <color:" + planta.corDoTexto() + ">· camas abrem em "
                            + falta + "s</color>"
                    : "";
            jogador.sendActionBar(plugin.mini().deserialize("<gray>vida "
                    + "<white>" + vida.getOrDefault(id, planta.vidaCheia())
                    + "</white>/" + planta.vidaCheia() + " · lã <white>"
                    + contarLa(jogador, planta, time) + "</white> · </gray>"
                    + daCama + graca));
        }
    }

    private void avisarOTime(int time, String recado) {
        if (time < 0 || time >= membros.size()) {
            return;
        }
        for (UUID id : membros.get(time)) {
            Player dono = Bukkit.getPlayer(id);
            if (dono != null) {
                dono.sendMessage(plugin.mini().deserialize(recado));
            }
        }
    }

    private void mostrarOPainel(World mundo, BedWarsCatalog planta) {
        StringBuilder linhas = new StringBuilder();
        for (int k = 0; k < planta.times(); k++) {
            if (!timeNaPartida[k]) {
                continue;
            }
            BedWarsCatalog.Time dados = planta.time(k);
            String estado = camaDePe[k]
                    ? "cama " + golpesDaCama[k] + "/" + planta.golpesDaCama()
                    : gentePorTime(k) > 0
                            ? vidasDoTime[k] + " vida(s)"
                            : "fora";
            linhas.append("\n<color:").append(dados.cor()).append(">")
                    .append(dados.nome()).append("</color> <gray>")
                    .append(estado).append("</gray>");
        }
        plugin.bedWarsBuilder().painelDaGuerra(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + timesVivos() + " TIMES</b></color>"
                        + linhas));
    }

    // ------------------------------------------------------------------
    //  Os corações como mostrador
    // ------------------------------------------------------------------

    private void mostrarVida(Player jogador) {
        int cheia = Math.max(1, planta2().vidaCheia());
        int agora = Math.max(0, vida.getOrDefault(jogador.getUniqueId(), cheia));
        double maximo = vidaMaxima(jogador);
        double quanto = maximo * Math.min(cheia, agora) / (double) cheia;
        jogador.setHealth(Math.min(maximo, Math.max(0.5, quanto)));
    }

    private void devolverAVidaDoMinecraft(Player jogador) {
        jogador.setHealth(vidaMaxima(jogador));
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
    }

    private double vidaMaxima(Player jogador) {
        AttributeInstance atributo = jogador.getAttribute(Attribute.MAX_HEALTH);
        return atributo == null ? 20.0 : atributo.getValue();
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    public int timeDe(UUID id) {
        return timeDe.getOrDefault(id, -1);
    }

    public int golpesDaCamaDoTime(int k) {
        return k >= 0 && k < golpesDaCama.length ? golpesDaCama[k] : 0;
    }

    public boolean camaDePe(int k) {
        return k >= 0 && k < camaDePe.length && camaDePe[k];
    }

    public int vidasDoTime(int k) {
        return k >= 0 && k < vidasDoTime.length ? vidasDoTime[k] : 0;
    }

    public int quantosTimesVivos() {
        return timesVivos();
    }

    public int camasQuebradasPor(UUID id) {
        return camasQuebradas.getOrDefault(id, 0);
    }
}
