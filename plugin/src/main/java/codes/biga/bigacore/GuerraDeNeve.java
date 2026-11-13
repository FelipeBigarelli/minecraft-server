package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Guerra de Neve: acertou, derrubou.
 *
 * <h2>Quem mata é a beira, não a bola</h2>
 *
 * A bola de neve não machuca ninguém — nem no jogo, nem no Minecraft. O que
 * ela faz é EMPURRAR, e o campo é uma ilha sobre o vazio. Quem toma uma bola
 * no meio do campo perde uma vida e um pouco de equilíbrio; quem toma uma
 * bola a dois blocos da beira, cai. É a mesma bola: o que muda é onde o
 * jogador escolheu estar, e é essa escolha que o jogo cobra.
 *
 * <h2>Três vidas, mas a terceira raramente é usada</h2>
 *
 * Em teoria congela quem tomar três acertos. Na prática a maioria das
 * eliminações é queda, e as vidas servem para o caso contrário: o jogador que
 * se enterra no meio do campo, longe de qualquer beira, e de onde não dá para
 * derrubá-lo. Sem vidas ele seria invencível no centro; com vidas, ficar
 * parado em lugar seguro também custa.
 *
 * <h2>A carência é contra a rajada, não contra o acerto</h2>
 *
 * Dez tiques de invencibilidade DE VIDA depois de cada acerto — mas o
 * empurrão continua valendo durante a carência. Quem leva uma rajada de perto
 * perde uma vida só, e ainda assim pode ser empurrado para fora: o acerto
 * repetido não deve valer três vidas, mas também não deve deixar de empurrar,
 * porque empurrar é o jogo.
 */
public final class GuerraDeNeve extends JogoDeArena {

    /** Quanto da velocidade de antes sobra depois do empurrão. */
    private static final double HERANCA = 0.4;

    private final Map<UUID, Integer> vidasDe = new HashMap<>();
    private final Map<UUID, Long> carenciaDe = new HashMap<>();
    private final Map<UUID, Integer> acertosDe = new HashMap<>();

    private int raioAtual;

    public GuerraDeNeve(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.neveCatalog();
    }

    private NeveCatalog planta2() {
        return plugin.neveCatalog();
    }

    /**
     * Aventura, e não sobrevivência.
     *
     * Aventura impede cavar, e cavar aqui seria o fim do jogo: o campo é de
     * neve, neve se quebra com a mão, e o primeiro jogador a descobrir isso
     * abriria um buraco embaixo de quem estivesse do outro lado. Lançar bola
     * de neve funciona em aventura — usar item sempre funcionou; o que
     * aventura tira é bater em bloco.
     */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "ATIRE!";
    }

    /** O raio que o campo tem agora. Encolhe com o aperto. */
    public int raioAtual() {
        return raioAtual;
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        plugin.neveBuilder().refazerCampo(mundo);
        vidasDe.clear();
        carenciaDe.clear();
        acertosDe.clear();
        raioAtual = planta2().raio();
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        NeveCatalog planta = planta2();
        List<int[]> pontos = NeveCatalog.largadas(entram.size(),
                planta.anelDaLargada());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(NeveBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            vidasDe.put(jogador.getUniqueId(), planta.vidas());
            encherAMao(jogador, planta.bolas());
        }
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return NeveBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected boolean rodadaAcabou() {
        if (ArenaComum.rodadaAcabou(quantosVivos(), comecouCom(),
                planta2().treinoSozinho())) {
            return true;
        }
        return segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String legendaDaVitoria() {
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() != 1) {
            return "último de pé";
        }
        int acertos = acertosDe.getOrDefault(vivos.get(0).getUniqueId(), 0);
        return "último de pé · " + acertos + (acertos == 1 ? " acerto" : " acertos");
    }

    @Override
    protected String textoDaBarra() {
        NeveCatalog planta = planta2();
        boolean apertando = raioAtual < planta.raio();
        return "<white>" + quantosVivos() + "</white> de pé <gray>· "
                + ArenaComum.tempo(tiquesDeJogo())
                + (apertando ? " · <color:#7FD8FF>raio " + raioAtual + "</color>"
                        : " · aperta em " + Math.max(0, planta.apertoSegundos()
                                - segundosDeJogo()) + "s")
                + "</gray>";
    }

    @Override
    protected void instrucoes(Player jogador) {
        NeveCatalog planta = planta2();
        dizer(jogador, "<gray>cada um tem <white>" + planta.vidas()
                + "</white> vidas e bola de neve sem fim — o campo começa a "
                + "encolher em <white>" + planta.apertoSegundos()
                + "s</white></gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        NeveCatalog planta = planta2();
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    /** A cada segundo: enche a mão de todo mundo e aperta o campo. */
    @Override
    protected void aCadaSegundo(World mundo) {
        NeveCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            encherAMao(jogador, planta.bolas());
        }
        apertar(mundo, planta);
    }

    /**
     * Repõe as bolas de neve na primeira casa da barra.
     *
     * Uma casa só, e sempre a mesma: a mochila entra vazia neste jogo, largar
     * item está proibido, e assim a conta de quantas bolas o jogador tem é a
     * de um espaço. Dar bola a cada segundo em vez de dar uma pilha enorme no
     * começo é o que faz a arma ser infinita sem ser um inventário cheio de
     * pilhas sobrando.
     */
    private void encherAMao(Player jogador, int bolas) {
        ItemStack mao = jogador.getInventory().getItem(0);
        if (mao != null && mao.getType() == Material.SNOWBALL
                && mao.getAmount() >= bolas) {
            return;
        }
        jogador.getInventory().setItem(0, new ItemStack(Material.SNOWBALL, bolas));
        if (jogador.getInventory().getHeldItemSlot() != 0
                && !temBolaNaMao(jogador)) {
            jogador.getInventory().setHeldItemSlot(0);
        }
    }

    private boolean temBolaNaMao(Player jogador) {
        ItemStack mao = jogador.getInventory().getItemInMainHand();
        return mao != null && mao.getType() == Material.SNOWBALL;
    }

    /**
     * O campo perde um anel de tantos em tantos segundos.
     *
     * Come a COLUNA inteira, e não só o piso: um iglu cujo chão sumiu mas cuja
     * cúpula ficou é uma plataforma flutuante na beira do campo, e quem subir
     * nela fica fora do alcance do aperto para sempre.
     */
    private void apertar(World mundo, NeveCatalog planta) {
        int alvo = ArenaComum.raioDoAperto(segundosDeJogo(), planta.raio(),
                planta.apertoSegundos(), planta.apertoIntervalo());
        if (alvo >= raioAtual) {
            return;
        }
        boolean primeiraVez = raioAtual >= planta.raio();
        raioAtual = alvo;

        Set<Material> doCampo = planta.materiaisDoCampo();
        int raio = planta.raio();
        int deBaixo = planta.topoY();
        int deCima = planta.topoY() + planta.monteAltura() + 5;
        int sumiram = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.foraDoApertoAtual(x, z, alvo, raio)) {
                    continue;
                }
                for (int y = deBaixo; y <= deCima; y++) {
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (doCampo.contains(bloco.getType())
                            || bloco.getType() == Material.SNOW) {
                        bloco.setType(Material.AIR, false);
                        sumiram++;
                    }
                }
            }
        }
        if (sumiram == 0) {
            return;
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_SNOW_BREAK,
                    0.6f, 0.7f);
        }
        if (primeiraVez) {
            anunciar("<color:#7FD8FF>o campo começou a encolher</color>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.showTitle(Title.title(Component.empty(),
                        plugin.mini().deserialize(
                                "<color:#7FD8FF>o campo está encolhendo</color>")));
            }
        }
    }

    // ------------------------------------------------------------------
    //  O acerto
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void aoCairABola(ProjectileHitEvent evento) {
        if (!(evento.getEntity() instanceof Snowball bola)
                || !nesteMundo(bola.getWorld())) {
            return;
        }
        if (evento.getHitEntity() instanceof Player alvo) {
            acertou(bola, alvo);
            return;
        }
        if (evento.getHitBlock() != null) {
            marcarOChao(evento.getHitBlock());
        }
    }

    /**
     * Uma bola acertou alguém.
     *
     * A direção do empurrão sai da VELOCIDADE DA BOLA, e não da linha entre
     * atirador e alvo. Com a linha, uma bola em arco que desce de cima
     * empurraria o alvo para o lado como se tivesse vindo rasante; com a
     * velocidade, ela empurra para onde estava indo — que é o que qualquer um
     * que atira esperando derrubar alguém da beira tem em mente.
     */
    private void acertou(Snowball bola, Player alvo) {
        if (!jogando() || !estaVivo(alvo)) {
            return;
        }
        Player atirador = bola.getShooter() instanceof Player quem ? quem : null;
        if (atirador == null || atirador.equals(alvo) || !estaVivo(atirador)) {
            return;
        }
        NeveCatalog planta = planta2();
        empurrar(alvo, bola.getVelocity(), atirador, planta);

        UUID id = alvo.getUniqueId();
        acertosDe.merge(atirador.getUniqueId(), 1, Integer::sum);
        alvo.getWorld().spawnParticle(Particle.SNOWFLAKE,
                alvo.getLocation().add(0, 1.2, 0), 18, 0.3, 0.4, 0.3, 0.05);
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.9f, 0.8f);
        atirador.playSound(atirador.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);

        if (carenciaDe.getOrDefault(id, 0L) > tiquesDeJogo()) {
            return;
        }
        carenciaDe.put(id, tiquesDeJogo() + planta.carenciaTiques());

        int resta = Math.max(0, vidasDe.getOrDefault(id, planta.vidas()) - 1);
        vidasDe.put(id, resta);
        mostrarVidas(alvo, resta, planta.vidas());
        atirador.sendActionBar(plugin.mini().deserialize(
                "<color:#BFE9FF>acertou " + HubBuilder.escapar(alvo.getName())
                        + "</color> <gray>— " + resta + " de " + planta.vidas()
                        + "</gray>"));

        if (resta == 0) {
            congelar(alvo, atirador);
        }
    }

    private void empurrar(Player alvo, Vector velocidade, Player atirador,
                          NeveCatalog planta) {
        Vector direcao = velocidade.clone().setY(0);
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = alvo.getLocation().toVector()
                    .subtract(atirador.getLocation().toVector()).setY(0);
        }
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = new Vector(1, 0, 0);
        }
        direcao.normalize().multiply(planta.empurrao());
        direcao.setY(planta.empurraoAltura());
        alvo.setVelocity(alvo.getVelocity().multiply(HERANCA).add(direcao));
        alvo.setFallDistance(0f);
    }

    private void mostrarVidas(Player alvo, int resta, int total) {
        StringBuilder barra = new StringBuilder();
        for (int i = 0; i < total; i++) {
            barra.append(i < resta ? "<color:#BFE9FF>❄</color>"
                    : "<color:#55595E>❄</color>");
        }
        alvo.sendActionBar(plugin.mini().deserialize(barra.toString()
                + " <gray>" + resta + " de " + total + "</gray>"));
        alvo.playSound(alvo.getLocation(), resta == 0
                        ? Sound.BLOCK_GLASS_BREAK : Sound.BLOCK_SNOW_BREAK,
                1.0f, resta == 0 ? 0.7f : 1.3f);
    }

    /** Acabaram as vidas: congelou. */
    private void congelar(Player alvo, Player atirador) {
        anunciar("<color:#BFE9FF>" + HubBuilder.escapar(atirador.getName())
                + "</color> <gray>congelou</gray> <color:#8FD3FF>"
                + HubBuilder.escapar(alvo.getName()) + "</color>");
        alvo.getWorld().spawnParticle(Particle.ITEM_SNOWBALL,
                alvo.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.1);
        eliminar(alvo);
    }

    /**
     * A bola deixa uma camada de neve onde caiu.
     *
     * Enfeite, mas enfeite que conta uma história: no fim de uma rodada o
     * campo mostra onde foi a briga. Só cai em cima de bloco do campo e só no
     * ar, para a neve não entrar em cima da placa do saguão nem dentro de
     * iglu nenhum.
     */
    private void marcarOChao(Block onde) {
        if (!planta2().materiaisDoCampo().contains(onde.getType())) {
            return;
        }
        Block acima = onde.getRelative(0, 1, 0);
        if (acima.getType().isAir()) {
            acima.setType(Material.SNOW, false);
        }
    }

    // ------------------------------------------------------------------
    //  Limpeza
    // ------------------------------------------------------------------

    @Override
    protected void aoEliminarJogador(Player jogador) {
        jogador.getInventory().clear();
    }

    @Override
    protected void aoTerminarRodada() {
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
        }
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        vidasDe.remove(id);
        carenciaDe.remove(id);
        acertosDe.remove(id);
    }

    /** Quem acertou mais, para o anúncio do fim. */
    public List<Player> pontaria() {
        List<Player> ordem = new ArrayList<>(jogadoresDaArena());
        ordem.sort((a, b) -> Integer.compare(
                acertosDe.getOrDefault(b.getUniqueId(), 0),
                acertosDe.getOrDefault(a.getUniqueId(), 0)));
        return ordem;
    }

    /** Quantas vidas o jogador ainda tem. */
    public int vidasDe(Player jogador) {
        return vidasDe.getOrDefault(jogador.getUniqueId(), 0);
    }
}
