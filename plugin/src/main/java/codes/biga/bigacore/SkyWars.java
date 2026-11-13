package codes.biga.bigacore;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * O SKY WARS: uma ilha, uma chance.
 *
 * <h2>Os três defeitos do gênero, e o que fazemos diferente</h2>
 *
 * <ul>
 *   <li><b>"O baú decide a partida."</b> A reclamação tem nome próprio nos
 *       fóruns dos jogadores — SEE, <i>Skywars Equipment Equality</i> — e um
 *       servidor grande já aceitou garantir armadura em todo baú. Aqui a
 *       ideia vai até o fim: NÃO HÁ BAÚ NAS ILHAS. {@link #darOKit} entrega o
 *       mesmo kit na mão de todo mundo, sorteado uma vez por PARTIDA. A
 *       variedade entre partidas fica, a loteria entre jogadores não — e de
 *       lambuja acaba a reclamação de que quem joga no celular saqueia mais
 *       devagar, porque não há o que saquear.</li>
 *   <li><b>"Quem faz ponte rápido pega o baú do meio."</b> A proposta dos
 *       próprios jogadores é uma barreira em volta do meio que cai na hora
 *       marcada, e é o que {@link #abrirOMeio} faz. Chegar primeiro passa a
 *       ganhar POSIÇÃO, não o baú. E a réplica dos fóruns continua valendo e
 *       é boa: fazer ponte se aprende, e ficar sem bloco no meio da ponte é
 *       aperto honesto, não injustiça.</li>
 *   <li><b>"O baú do meio é inútil e chato" / "o do meio já dá vantagem
 *       demais."</b> As duas reclamações existem e são a mesma pergunta. O
 *       meio vale exatamente um nível acima do kit da partida, e
 *       {@link SkyWarsBuilder#conferirNiveis} prende as duas pontas.</li>
 * </ul>
 *
 * <h2>Só se quebra o que alguém colocou</h2>
 *
 * Sem essa regra, a primeira jogada de todo mundo é cavar a própria ilha para
 * virar bloco de ponte, e o anel desaparece antes de a partida começar — e
 * pior, quem cava mais rápido tem mais ponte, o que traz de volta pela porta
 * dos fundos a desigualdade que o kit igual tirou pela porta da frente.
 * {@link #colocados} guarda o que foi posto e {@link #permiteQuebrar} só
 * deixa quebrar isso. O bloco de ponte é de outra cor justamente para a regra
 * ser visível no chão em vez de ser lida numa placa.
 *
 * <h2>O vazio tem autor</h2>
 *
 * Na referência, quem é empurrado e cai simplesmente some, e ninguém leva o
 * crédito. Aqui o último golpe vale por {@code credito-segundos}: quem
 * empurrou leva o nome e os pontos. É pequeno e muda o fim de toda partida,
 * porque num jogo em que a maioria das mortes é queda, um jogo sem crédito de
 * queda é um jogo sem placar.
 *
 * <h2>O anel cai em ORDEM, dando a volta</h2>
 *
 * Embaralhado, a queda da sua ilha é sorte e o jogador só pode reagir. Dando a
 * volta de um ponto sorteado, ele CONTA quantas faltam para a dele e decide
 * quando sair — e decidir quando sair é a jogada que este fim de partida
 * existe para criar.
 */
public final class SkyWars extends JogoDeArena {

    private final Placar placar = new Placar();
    private final Random sorteio = new Random();

    private final Map<UUID, Integer> vida = new HashMap<>();
    private final Map<UUID, Long> carencia = new HashMap<>();
    private final Map<UUID, Integer> ilhaDe = new HashMap<>();
    private final Map<UUID, Integer> quedas = new HashMap<>();

    /** Quem bateu por último em quem, e quando: o crédito da queda. */
    private final Map<UUID, UUID> agressor = new HashMap<>();
    private final Map<UUID, Long> tiqueDoGolpe = new HashMap<>();

    /** A força do tiro de cada flecha no ar. */
    private final Map<UUID, Double> forcaDaFlecha = new HashMap<>();

    /** Os blocos que alguém colocou: os únicos que podem ser quebrados. */
    private final Set<Long> colocados = new HashSet<>();

    private int nivel;
    private int nivelDoMeio;
    private boolean meioAberto;
    private List<Integer> ordemDaQueda = new ArrayList<>();
    private int jaCairam;
    private int avisadas;

    public SkyWars(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.skyWarsCatalog();
    }

    private SkyWarsCatalog planta2() {
        return plugin.skyWarsCatalog();
    }

    /** Sobrevivência, porque este é o único jogo daqui em que se constrói. */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.SURVIVAL;
    }

    @Override
    protected String verboDaLargada() {
        return "ATRAVESSE!";
    }

    @Override
    protected void ajustesDoMundo(World mundo) {
        mundo.setPVP(true);
        mundo.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        SkyWarsCatalog planta = planta2();
        SkyWarsBuilder obra = plugin.skyWarsBuilder();

        placar.zerar();
        vida.clear();
        carencia.clear();
        ilhaDe.clear();
        quedas.clear();
        agressor.clear();
        tiqueDoGolpe.clear();
        forcaDaFlecha.clear();
        meioAberto = false;
        jaCairam = 0;
        avisadas = 0;

        apagarAsPontes(mundo);
        limparFlechas(mundo);
        for (int k = 0; k < planta.ilhas(); k++) {
            obra.refazerIlha(mundo, planta, k);
        }
        obra.fecharOMeio(mundo, planta);

        nivel = SkyWarsCatalog.nivelDaPartida(planta.quantosNiveis(), sorteio);
        nivelDoMeio = SkyWarsCatalog.nivelDoMeio(nivel, planta.quantosNiveis());
        encherOsBaus(mundo, planta);

        ordemDaQueda = SkyWarsCatalog.ordemDaQueda(planta.ilhas(),
                sorteio.nextInt(Math.max(1, planta.ilhas())));
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        SkyWarsCatalog planta = planta2();
        List<Integer> ilhas = new ArrayList<>();
        for (int k = 0; k < planta.ilhas(); k++) {
            ilhas.add(k);
        }
        Collections.shuffle(ilhas, sorteio);

        for (int k = 0; k < entram.size() && k < ilhas.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            int ilha = ilhas.get(k);
            ilhaDe.put(id, ilha);
            jogador.teleport(SkyWarsBuilder.pontoDaIlha(mundo, planta, ilha));
            jogador.setFallDistance(0f);
            darOKit(jogador, planta, nivel);
            vida.put(id, planta.vidaCheia());
            quedas.put(id, 0);
            placar.entrar(id);
            mostrarVida(jogador);
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        SkyWarsCatalog planta = planta2();
        anunciar("<gray>o kit é <white>igual para todos</white> e foi "
                + "sorteado para esta partida: <white>"
                + nomeBonito(planta.espadaDoNivel(nivel)) + "</white>, "
                + "<white>" + planta.blocosDoNivel(nivel) + "</white> blocos "
                + "de ponte" + (planta.flechasDoNivel(nivel) > 0
                        ? " e <white>" + planta.flechasDoNivel(nivel)
                                + "</white> flechas"
                        : "") + ". Não há baú nas ilhas.</gray>");
        anunciar("<gray>o baú do meio tem <white>"
                + nomeBonito(planta.espadaDoNivel(nivelDoMeio))
                + "</white> e abre em <white>" + planta.segundosParaOMeio()
                + "s</white>, quando a cúpula cair — chegar antes ganha "
                + "<white>posição</white>, não o baú.</gray>");
        anunciar("<gray>só se quebra o <white>bloco que alguém colocou</white>: "
                + "as ilhas não se mineram. E em <white>"
                + planta.segundosDeCerco() + "s</white> o anel começa a cair, "
                + "uma ilha de cada vez, <white>dando a volta</white>.</gray>");
        mostrarOPainel(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return SkyWarsBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEntrarNaArena(Player jogador) {
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        vida.remove(id);
        carencia.remove(id);
        ilhaDe.remove(id);
        quedas.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
    }

    /**
     * Quem saiu da rodada devolve o kit — e a queda ganha autor.
     *
     * É aqui que o crédito do vazio acontece, porque a queda no vazio passa
     * pelo {@code eliminar} do arcabouço e não por esta classe: quem bateu há
     * pouco leva o nome. O anúncio é um ACRÉSCIMO ao "caiu" do arcabouço, e
     * não uma troca — "Y caiu" e depois "foi X quem empurrou" é a ordem em que
     * a coisa aconteceu.
     */
    @Override
    protected void aoEliminarJogador(Player jogador) {
        UUID id = jogador.getUniqueId();
        SkyWarsCatalog planta = planta2();
        UUID quem = agressor.get(id);
        long quando = tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE);
        boolean recente = tiquesDeJogo() - quando
                <= planta.creditoSegundos() * 20L;

        if (quem != null && recente && vida.containsKey(id)) {
            creditar(quem, jogador, planta, true);
        }
        vida.remove(id);
        carencia.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
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
            limparFlechas(mundo);
            ArenaComum.limparPaineis(mundo, SkyWarsBuilder.TAG_AR);
        }
    }

    @Override
    protected boolean rodadaAcabou() {
        if (segundosDeJogo() >= planta2().tempoLimite()) {
            return true;
        }
        return ArenaComum.rodadaAcabou(quantosVivos(), comecouCom(),
                planta2().treinoSozinho());
    }

    @Override
    protected String vencedor() {
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            return vivos.get(0).getName();
        }
        if (vivos.isEmpty()) {
            return null;
        }
        UUID lider = placar.lider();
        if (lider == null) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        UUID quem = placar.lider();
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            quem = vivos.get(0).getUniqueId();
        }
        if (quem == null) {
            return "ninguém sobrou";
        }
        int quantas = quedas.getOrDefault(quem, 0);
        return quantas + (quantas == 1 ? " derrubado" : " derrubados")
                + " · kit " + nomeBonito(planta2().espadaDoNivel(nivel));
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        SkyWarsCatalog planta = planta2();
        if (!meioAberto) {
            return "<color:" + planta.corDoTexto() + ">o meio abre em "
                    + Math.max(0, planta.segundosParaOMeio()
                            - segundosDeJogo()) + "s</color>";
        }
        if (segundosDeJogo() < planta.segundosDeCerco()) {
            return "<white>" + quantosVivos() + "</white> <gray>no ar · o "
                    + "anel cai em " + (planta.segundosDeCerco()
                            - segundosDeJogo()) + "s</gray>";
        }
        return "<white>" + quantosVivos() + "</white> <gray>no ar · <white>"
                + Math.max(0, planta.ilhas() - jaCairam) + "</white> ilhas de "
                + "pé</gray>";
    }

    // ------------------------------------------------------------------
    //  O kit e o baú
    // ------------------------------------------------------------------

    private void darOKit(Player jogador, SkyWarsCatalog planta, int qualNivel) {
        jogador.getInventory().clear();
        jogador.getInventory().setItem(0,
                marcar(planta.espadaDoNivel(qualNivel), 1, planta,
                        "Espada do Céu"));
        jogador.getInventory().setItem(1, new ItemStack(planta.blocoDePonte(),
                Math.max(1, planta.blocosDoNivel(qualNivel))));
        int flechas = planta.flechasDoNivel(qualNivel);
        if (flechas > 0) {
            jogador.getInventory().setItem(2,
                    marcar(Material.BOW, 1, planta, "Arco do Céu"));
            jogador.getInventory().setItem(3,
                    new ItemStack(Material.ARROW, flechas));
        }
        jogador.getInventory().setHeldItemSlot(1);
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
        jogador.updateInventory();
    }

    private ItemStack marcar(Material qual, int quantos,
                             SkyWarsCatalog planta, String nome) {
        ItemStack item = new ItemStack(qual, quantos);
        ItemMeta marca = item.getItemMeta();
        if (marca != null) {
            marca.displayName(plugin.mini().deserialize("<color:"
                    + planta.corDoTexto() + ">" + nome + "</color>"));
            item.setItemMeta(marca);
        }
        return item;
    }

    /**
     * Enche os baús do meio, todos com o MESMO conteúdo.
     *
     * Iguais entre si de propósito: se o primeiro baú tivesse a espada e o
     * segundo tivesse flecha, chegar primeiro voltaria a decidir a partida — e
     * a cúpula existe justamente para chegar primeiro não decidir nada.
     */
    private void encherOsBaus(World mundo, SkyWarsCatalog planta) {
        int y = planta.topoY() + 1;
        for (int[] onde : planta.pontosDosBaus()) {
            Block bloco = mundo.getBlockAt(onde[0], y, onde[1]);
            if (!(bloco.getState() instanceof Chest arca)) {
                continue;
            }
            Inventory dentro = arca.getBlockInventory();
            dentro.clear();
            dentro.setItem(0, marcar(planta.espadaDoNivel(nivelDoMeio), 1,
                    planta, "Espada do Meio"));
            dentro.setItem(2, new ItemStack(planta.blocoDePonte(),
                    Math.max(1, planta.blocosDoNivel(nivelDoMeio))));
            int flechas = planta.flechasDoNivel(nivelDoMeio);
            if (flechas > 0) {
                dentro.setItem(4, marcar(Material.BOW, 1, planta,
                        "Arco do Meio"));
                dentro.setItem(5, new ItemStack(Material.ARROW, flechas));
            }
            if (planta.curasNoBau() > 0) {
                dentro.setItem(7, new ItemStack(planta.itemDeCura(),
                        planta.curasNoBau()));
            }
        }
    }

    /** O nível da espada na mão: é ela que diz quanto o golpe tira. */
    private int nivelDaEspada(Material qual) {
        SkyWarsCatalog planta = planta2();
        List<String> espadas = planta.nivelEspada();
        for (int k = espadas.size() - 1; k >= 0; k--) {
            Material dela = Material.matchMaterial(
                    espadas.get(k).trim().toUpperCase());
            if (dela == qual) {
                return k;
            }
        }
        return -1;
    }

    private static String nomeBonito(Material qual) {
        return qual.name().toLowerCase().replace('_', ' ');
    }

    // ------------------------------------------------------------------
    //  O golpe
    // ------------------------------------------------------------------

    /**
     * O golpe: a espada na mão diz o dano, e a prontidão diz o resto.
     *
     * A conta da prontidão é {@link ArenaPvpCatalog#forcaDoGolpe}, a mesma da
     * ARENA PVP, e a carência do alvo é igual ao período — não por economia
     * de código, mas porque um jogador que aprendeu o tempo do golpe numa
     * arena não pode ter de reaprender na outra.
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
        SkyWarsCatalog planta = planta2();
        UUID id = alvo.getUniqueId();

        int qualNivel = nivelDaEspada(
                quemBateu.getInventory().getItemInMainHand().getType());
        if (qualNivel < 0) {
            // Sem espada na mão: o soco não é jogada deste jogo, e deixar o
            // soco machucar faria o kit valer menos do que ele vale.
            quemBateu.sendActionBar(plugin.mini().deserialize(
                    "<gray>só a espada machuca aqui</gray>"));
            return;
        }
        if (carencia.getOrDefault(id, 0L) > tiquesDeJogo()) {
            return;
        }
        double prontidao = quemBateu.getAttackCooldown();
        carencia.put(id, tiquesDeJogo() + planta.periodoDoGolpe());

        int dano = Math.max(1, (int) Math.round(planta.danoDoNivel(qualNivel)
                * ArenaPvpCatalog.forcaDoGolpe(prontidao)));
        aplicarDano(alvo, quemBateu, planta, dano, prontidao, "espada");
        empurrar(quemBateu, alvo, planta, prontidao);
    }

    /** O tiro fraco é recusado: mesma regra do ARCO E FLECHA. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoAtirar(EntityShootBowEvent evento) {
        if (!(evento.getEntity() instanceof Player jogador)) {
            return;
        }
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        if (evento.getForce() < planta.forcaMinima()) {
            evento.setCancelled(true);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>tiro fraco — recusado</color> <gray>· "
                            + "puxe mais o arco</gray>"));
            return;
        }
        if (evento.getProjectile() instanceof AbstractArrow flecha) {
            flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            flecha.setPersistent(false);
            forcaDaFlecha.put(flecha.getUniqueId(),
                    (double) evento.getForce());
        }
    }

    /**
     * A flecha acertou, e o dano é proporcional à puxada.
     *
     * A força é guardada no tiro e lida aqui, e não deduzida da velocidade no
     * ar: a velocidade muda com a queda e com o vento do próprio voo, e um
     * tiro cheio que viajou longe chegaria valendo menos — o que premiaria
     * atirar de perto, que é o contrário do que um arco deve premiar.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void aoAcertar(ProjectileHitEvent evento) {
        if (!(evento.getEntity() instanceof AbstractArrow flecha)) {
            return;
        }
        if (!jogando() || !nesteMundo(flecha.getWorld())) {
            return;
        }
        double forca = forcaDaFlecha.getOrDefault(flecha.getUniqueId(), 1.0);
        if (!(evento.getHitEntity() instanceof Player alvo)) {
            if (evento.getHitBlock() != null) {
                forcaDaFlecha.remove(flecha.getUniqueId());
                flecha.remove();
            }
            return;
        }
        forcaDaFlecha.remove(flecha.getUniqueId());
        flecha.remove();
        Player dono = flecha.getShooter() instanceof Player quem ? quem : null;
        if (dono == null || alvo.equals(dono) || !estaVivo(alvo)
                || !estaVivo(dono)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        int dano = Math.max(1, (int) Math.round(planta.danoDaFlecha() * forca));
        aplicarDano(alvo, dono, planta, dano, forca, "flecha");
    }

    /** Tira a vida, guarda o crédito e derruba se zerou. */
    private void aplicarDano(Player alvo, Player dono, SkyWarsCatalog planta,
                             int dano, double forca, String como) {
        UUID id = alvo.getUniqueId();
        int sobrou = Math.max(0,
                vida.getOrDefault(id, planta.vidaCheia()) - dano);
        vida.put(id, sobrou);
        agressor.put(id, dono.getUniqueId());
        tiqueDoGolpe.put(id, tiquesDeJogo());
        placar.somar(dono.getUniqueId(), dano);
        mostrarVida(alvo);

        String cor = forca >= 0.9 ? "#8BE04E"
                : forca >= 0.6 ? "#FFD93D" : "#FF8A8A";
        dono.sendActionBar(plugin.mini().deserialize("<color:" + cor + ">"
                + (int) Math.round(forca * 100) + "% de " + como
                + "</color> <gray>— tirou <white>" + dano + "</white> de "
                + HubBuilder.escapar(alvo.getName()) + ", restam <white>"
                + sobrou + "</white></gray>"));
        alvo.sendActionBar(plugin.mini().deserialize("<color:#FF8A8A>-" + dano
                + "</color> <gray>de " + HubBuilder.escapar(dono.getName())
                + " · restam <white>" + sobrou + "</white></gray>"));
        animarGolpe(alvo, dono);
        alvo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                alvo.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.05);

        if (sobrou <= 0) {
            creditar(dono.getUniqueId(), alvo, planta, false);
            derrubar(alvo, dono, planta);
        }
    }

    /** O crédito de uma queda: pontos e nome a quem empurrou ou bateu. */
    private void creditar(UUID quem, Player alvo, SkyWarsCatalog planta,
                          boolean noVazio) {
        placar.somar(quem, planta.pontosPorQueda());
        int quantas = quedas.merge(quem, 1, Integer::sum);
        Player dono = Bukkit.getPlayer(quem);
        if (dono == null) {
            return;
        }
        if (noVazio) {
            anunciar("<gray>foi <color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName())
                    + "</color> quem empurrou <white>"
                    + HubBuilder.escapar(alvo.getName()) + "</white></gray>");
            dono.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>"
                    + HubBuilder.escapar(alvo.getName()) + " caiu no vazio"
                    + "</color> <gray>— o crédito é seu · " + quantas
                    + " no total</gray>"));
            dono.playSound(dono.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
                    0.5f, 1.6f);
        }
    }

    /**
     * Derrubado pela espada ou pela flecha, e não caído no vazio.
     *
     * Não passa pelo {@code eliminar} do arcabouço porque ele diz "você caiu",
     * que é a verdade de quem foi para o vazio e a coisa errada de quem perdeu
     * uma luta no ar.
     */
    private void derrubar(Player alvo, Player dono, SkyWarsCatalog planta) {
        vivos().remove(alvo.getUniqueId());
        alvo.setFallDistance(0f);
        alvo.setGameMode(GameMode.SPECTATOR);
        alvo.teleport(pontoDaPlateia(alvo.getWorld()));

        UUID id = alvo.getUniqueId();
        vida.remove(id);
        carencia.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
        alvo.getInventory().clear();
        devolverAVidaDoMinecraft(alvo);

        alvo.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>DERRUBADO</color>"),
                plugin.mini().deserialize("<gray>por <white>"
                        + HubBuilder.escapar(dono.getName())
                        + "</white></gray>")));
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                0.5f, 1.4f);
        if (comecouCom() > 1) {
            anunciar("<color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName()) + "</color> <gray>"
                    + "derrubou <white>" + HubBuilder.escapar(alvo.getName())
                    + "</white> — faltam <white>" + quantosVivos()
                    + "</white></gray>");
        }
        mostrarOPainel(alvo.getWorld(), planta);
    }

    private void empurrar(Player quemBateu, Player alvo,
                          SkyWarsCatalog planta, double prontidao) {
        Vector direcao = alvo.getLocation().toVector()
                .subtract(quemBateu.getLocation().toVector()).setY(0);
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = quemBateu.getLocation().getDirection().setY(0);
        }
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = new Vector(1, 0, 0);
        }
        direcao.normalize().multiply(ArenaPvpCatalog.empurraoDoGolpe(
                prontidao, planta.empurrao()));
        direcao.setY(ArenaPvpCatalog.empurraoDoGolpe(prontidao,
                planta.empurraoAltura()));
        alvo.setVelocity(alvo.getVelocity().multiply(0.4).add(direcao));
        alvo.setFallDistance(0f);
    }

    // ------------------------------------------------------------------
    //  A cura do baú
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoUsarCura(PlayerInteractEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND
                || (evento.getAction() != Action.RIGHT_CLICK_AIR
                        && evento.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        ItemStack item = evento.getItem();
        if (item == null || item.getType() != planta.itemDeCura()) {
            return;
        }
        evento.setCancelled(true);
        UUID id = jogador.getUniqueId();
        int antes = vida.getOrDefault(id, planta.vidaCheia());
        if (antes >= planta.vidaCheia()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>a sua vida já está cheia</gray>"));
            return;
        }
        int depois = Math.min(planta.vidaCheia(), antes + planta.curaDoItem());
        vida.put(id, depois);
        item.setAmount(item.getAmount() - 1);
        jogador.updateInventory();
        mostrarVida(jogador);
        jogador.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>+"
                + (depois - antes) + " de vida</color> <gray>· agora "
                + depois + "/" + planta.vidaCheia() + "</gray>"));
        jogador.playSound(jogador.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f,
                1.4f);
        jogador.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                jogador.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.2);
    }

    // ------------------------------------------------------------------
    //  A ponte
    // ------------------------------------------------------------------

    /**
     * Colocar só o bloco de ponte, e só dentro da arena.
     *
     * Só o bloco de ponte porque o kit não tem outro e porque, se tivesse, a
     * cor de "o que se pode quebrar" deixaria de ser uma cor só e a regra
     * sairia do chão.
     */
    @Override
    protected boolean permiteColocar(BlockPlaceEvent evento) {
        if (!jogando() || !estaVivo(evento.getPlayer())) {
            return false;
        }
        SkyWarsCatalog planta = planta2();
        Block bloco = evento.getBlock();
        if (bloco.getType() != planta.blocoDePonte()) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<gray>só o bloco de ponte se coloca aqui</gray>"));
            return false;
        }
        int alcance = planta.ilhaAnel() + planta.ilhaRaio() + 4;
        if (Math.abs(bloco.getX()) > alcance || Math.abs(bloco.getZ()) > alcance
                || bloco.getY() <= planta.quedaY()
                || bloco.getY() > planta.topoY() + planta.marcoAltura()) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora da arena</color>"));
            return false;
        }
        colocados.add(ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ()));
        return true;
    }

    /** Só se quebra o que alguém colocou: ver o cabeçalho da classe. */
    @Override
    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        if (!jogando() || !estaVivo(evento.getPlayer())) {
            return false;
        }
        Block bloco = evento.getBlock();
        long chave = ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ());
        if (!colocados.remove(chave)) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>as ilhas não se mineram</color> <gray>— "
                            + "só se quebra o que alguém colocou</gray>"));
            return false;
        }
        return true;
    }

    /** Apaga toda ponte feita na rodada passada. */
    private void apagarAsPontes(World mundo) {
        SkyWarsCatalog planta = planta2();
        Material ponte = planta.blocoDePonte();
        for (long chave : colocados) {
            int x = ArenaComum.daChave(chave, 0);
            int y = ArenaComum.daChave(chave, 1);
            int z = ArenaComum.daChave(chave, 2);
            Block bloco = mundo.getBlockAt(x, y, z);
            if (bloco.getType() == ponte) {
                bloco.setType(Material.AIR, false);
            }
        }
        colocados.clear();
    }

    private void limparFlechas(World mundo) {
        for (Entity bicho : mundo.getEntities()) {
            if (bicho instanceof AbstractArrow) {
                bicho.remove();
            }
        }
        forcaDaFlecha.clear();
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
    //  O relógio: a cúpula e o anel
    // ------------------------------------------------------------------

    @Override
    protected void aCadaSegundo(World mundo) {
        if (!jogando()) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        int agora = segundosDeJogo();

        if (!meioAberto && agora >= planta.segundosParaOMeio()) {
            abrirOMeio(mundo, planta);
        }
        cuidarDoCerco(mundo, planta, agora);
        avisarDoAr(planta, agora);
    }

    private void abrirOMeio(World mundo, SkyWarsCatalog planta) {
        meioAberto = true;
        plugin.skyWarsBuilder().abrirOMeio(mundo, planta);
        tituloEmTodos("<color:" + planta.corDoTexto() + ">O MEIO ABRIU"
                + "</color>", "<gray>" + planta.meioBaus() + " baús iguais, "
                        + nomeBonito(planta.espadaDoNivel(nivelDoMeio))
                        + "</gray>");
        anunciar("<gray>a cúpula caiu: os <white>" + planta.meioBaus()
                + " baús</white> do meio são <white>iguais entre si</white> — "
                + "chegar primeiro não dá baú melhor, dá posição melhor.</gray>");
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    1.0f, 0.8f);
        }
        mostrarOPainel(mundo, planta);
    }

    /**
     * O cerco: as ilhas do anel caem uma a uma, dando a volta.
     *
     * Avisa a PRÓXIMA com nome e segundos. Avisar só a próxima e não a lista
     * inteira é de propósito: a ordem é dando a volta, então quem quiser saber
     * quando a sua cai conta as ilhas — e contar é a jogada.
     */
    private void cuidarDoCerco(World mundo, SkyWarsCatalog planta, int agora) {
        if (agora < planta.segundosDeCerco() - planta.avisoSegundos()) {
            return;
        }
        if (jaCairam >= ordemDaQueda.size()) {
            return;
        }
        int quando = planta.segundoDaQueda(jaCairam);
        if (agora >= quando) {
            int qual = ordemDaQueda.get(jaCairam);
            plugin.skyWarsBuilder().derrubarIlha(mundo, planta, qual);
            jaCairam++;
            avisadas = jaCairam;
            anunciar("<color:#FF8A8A>a ilha " + (qual + 1) + " caiu</color> "
                    + "<gray>— faltam <white>"
                    + (planta.ilhas() - jaCairam) + "</white> de pé</gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);
            }
            mostrarOPainel(mundo, planta);
            return;
        }
        if (avisadas <= jaCairam && quando - agora <= planta.avisoSegundos()) {
            avisadas = jaCairam + 1;
            int qual = ordemDaQueda.get(jaCairam);
            anunciar("<color:#FFD93D>a ilha " + (qual + 1) + " cai em "
                    + (quando - agora) + "s</color> <gray>— o anel cai dando "
                    + "a volta, conte as suas</gray>");
        }
    }

    private void avisarDoAr(SkyWarsCatalog planta, int agora) {
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (tiquesDeJogo() - tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE)
                    < 30) {
                continue;
            }
            int sua = vida.getOrDefault(id, planta.vidaCheia());
            int pontes = contarPontes(jogador, planta);
            String aviso = meioAberto
                    ? (agora < planta.segundosDeCerco()
                            ? " <gray>· anel cai em "
                                    + (planta.segundosDeCerco() - agora) + "s</gray>"
                            : " <color:#FF8A8A>· anel caindo</color>")
                    : " <color:" + planta.corDoTexto() + ">· meio abre em "
                            + Math.max(0, planta.segundosParaOMeio() - agora)
                            + "s</color>";
            jogador.sendActionBar(plugin.mini().deserialize("<gray>vida "
                    + "<white>" + sua + "</white>/" + planta.vidaCheia()
                    + " · <white>" + pontes + "</white> blocos · <white>"
                    + quantosVivos() + "</white> no ar</gray>" + aviso));
        }
    }

    private int contarPontes(Player jogador, SkyWarsCatalog planta) {
        int quantos = 0;
        for (ItemStack item : jogador.getInventory().getContents()) {
            if (item != null && item.getType() == planta.blocoDePonte()) {
                quantos += item.getAmount();
            }
        }
        return quantos;
    }

    private void mostrarOPainel(World mundo, SkyWarsCatalog planta) {
        String linha = !meioAberto
                ? "<color:" + planta.corDoTexto() + ">o meio abre em "
                        + Math.max(0, planta.segundosParaOMeio()
                                - segundosDeJogo()) + "s</color>"
                : jaCairam == 0
                        ? "<gray>o anel ainda está de pé</gray>"
                        : "<color:#FF8A8A>" + (planta.ilhas() - jaCairam)
                                + " ilhas de pé</color>";
        plugin.skyWarsBuilder().painelDoAr(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + quantosVivos() + " NO AR</b></color>\n"
                        + linha + "\n<gray>kit igual: "
                        + nomeBonito(planta.espadaDoNivel(nivel))
                        + "</gray>"));
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    public int nivelDaRodada() {
        return nivel;
    }

    public int nivelDoBauDoMeio() {
        return nivelDoMeio;
    }

    public int vidaDe(UUID id) {
        return vida.getOrDefault(id, 0);
    }

    public int quedasDe(UUID id) {
        return quedas.getOrDefault(id, 0);
    }

    public boolean oMeioEstaAberto() {
        return meioAberto;
    }

    public int ilhasDePe() {
        return Math.max(0, planta2().ilhas() - jaCairam);
    }
}
