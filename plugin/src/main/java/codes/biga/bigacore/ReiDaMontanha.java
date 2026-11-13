package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * O Rei da Montanha: some um minuto no topo.
 *
 * <h2>O relógio é de cada um, e ele é INCREMENTAL</h2>
 *
 * Cada jogador tem o seu relógio, e ele anda enquanto o dono está SOZINHO no
 * topo. Sair do topo PARA o relógio — não zera. Voltar continua de onde
 * parou, e ganha quem primeiro somar o minuto.
 *
 * <p>A primeira versão deste jogo pedia o minuto SEGUIDO, e o defeito dela
 * aparecia no fim de toda partida: um empurrão de sorte aos cinquenta e nove
 * segundos apagava a subida inteira, e a décima subida de alguém valia
 * exatamente o mesmo que a primeira — nada. Com o relógio incremental cada
 * subida bem feita é paga na hora, e a montanha é disputada do primeiro ao
 * último segundo, porque ninguém está nunca "começando de novo".
 *
 * <h2>Disputa congela</h2>
 *
 * Dois no topo e nenhum dos dois relógios anda. É o que faz do topo um lugar
 * para EXPULSAR alguém e não um lugar para dividir: com dois parados lá em
 * cima a partida não avança para nenhum dos dois, e é aí que o bastão
 * importa.
 *
 * <h2>A tolerância protege o título, não o relógio</h2>
 *
 * Quem sai do topo continua com a coroa por dois segundos. O relógio não
 * precisa mais de proteção, porque não zera; o que precisa é o TÍTULO — sem
 * a tolerância, um pulo do rei daria o topo ao rival por dois tiques, e a
 * arena veria a coroa piscando de dono a cada salto.
 *
 * <h2>Ninguém é eliminado</h2>
 *
 * Não há dano e não há eliminação: o que existe é um bastão que empurra e uma
 * montanha com vazio em volta. Quem cai volta ao pé dela e perde a subida, que
 * é castigo de sobra. A partida acaba no minuto somado ou no relógio — e no
 * relógio ganha quem tiver somado mais tempo no topo.
 */
public final class ReiDaMontanha extends JogoDeArena {

    /** Quanto acima do cume ainda conta como estar no topo. */
    private static final double TETO_DO_TOPO = 5.0;

    /** Quantos tiques cada um já somou no topo. Nunca zera na partida. */
    private final Map<UUID, Integer> somaDe = new HashMap<>();
    private final Map<UUID, Long> carenciaDoBastao = new HashMap<>();
    private final Map<UUID, Long> castigoAte = new HashMap<>();
    private final Random sorte = new Random();

    private UUID rei;

    /** Há quantos tiques o rei de agora está no topo DESTA vez. */
    private int tiquesNaVez;
    private int toleranciaRestante;
    private UUID campeao;

    public ReiDaMontanha(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.reiCatalog();
    }

    private ReiCatalog planta2() {
        return plugin.reiCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "SUBA!";
    }

    /**
     * Este é o único mundo de arena em que o PvP tem de estar LIGADO.
     *
     * Não para machucar — o dano continua cancelado como em todo jogo daqui.
     * É que o evento de bater em jogador só acontece com PvP ligado, e é
     * nele que o bastão empurra. Com PvP desligado o bastão seria um graveto.
     */
    @Override
    protected void ajustesDoMundo(World mundo) {
        mundo.setPVP(true);
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        somaDe.clear();
        carenciaDoBastao.clear();
        castigoAte.clear();
        rei = null;
        campeao = null;
        tiquesNaVez = 0;
        toleranciaRestante = 0;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        ReiCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(ReiBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            darOBastao(jogador);
            somaDe.put(jogador.getUniqueId(), 0);
        }
    }

    /** Os parágrafos que o saguão escreve nas paredes da rua. */
    @Override
    public java.util.List<String> instrucoes() {
        java.util.List<String> linhas = new java.util.ArrayList<>();
        ReiCatalog planta = planta2();
        linhas.add("<gray>ganha quem SOMAR <white>"
                + planta.segundosParaGanhar() + "s</white> no topo. O seu "
                + "relógio <white>não zera</white> quando você sai: ele para "
                + "e continua na próxima subida — mas com dois lá em cima ele "
                + "congela para os dois</gray>");
        return linhas;
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        ReiCatalog planta = planta2();
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return ReiBuilder.pontoDaPlateia(mundo, planta2());
    }

    /**
     * Ninguém cai para fora deste jogo.
     *
     * A queda existe e custa caro, mas é castigo e não eliminação: quem cai
     * volta ao pé da montanha. Eliminar por queda num jogo em que o empurrão
     * é a mecânica central seria o mesmo que eliminar por jogar.
     */
    @Override
    protected boolean caiuFora(Player jogador) {
        return false;
    }

    @Override
    protected boolean rodadaAcabou() {
        if (campeao != null) {
            return true;
        }
        // Ninguém é eliminado aqui, então a arena só esvazia se todo mundo
        // sair — e aí a rodada tem de acabar na hora, e não daqui a sete
        // minutos quando o relógio estourar sozinho.
        if (quantosVivos() == 0) {
            return true;
        }
        return segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String vencedor() {
        if (campeao != null) {
            Player quem = Bukkit.getPlayer(campeao);
            return quem == null ? "alguém" : quem.getName();
        }
        // No relógio: ganha quem somou mais, se houver UM que somou mais.
        UUID melhor = null;
        int maior = 0;
        boolean empate = false;
        for (Map.Entry<UUID, Integer> marca : somaDe.entrySet()) {
            if (marca.getValue() > maior) {
                maior = marca.getValue();
                melhor = marca.getKey();
                empate = false;
            } else if (marca.getValue() == maior && maior > 0) {
                empate = true;
            }
        }
        if (melhor == null || empate || maior == 0) {
            return null;
        }
        Player quem = Bukkit.getPlayer(melhor);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        if (campeao != null) {
            return "somou os " + planta2().segundosParaGanhar()
                    + "s no topo";
        }
        int maior = 0;
        for (int marca : somaDe.values()) {
            maior = Math.max(maior, marca);
        }
        return "somou mais tempo no topo: " + ArenaComum.tempo(maior);
    }

    /**
     * A barra mostra o MAIOR relógio da arena, e não o do rei de agora.
     *
     * É a única leitura honesta com relógio incremental: o rei de agora pode
     * ter acabado de assumir com três segundos somados enquanto outro está a
     * dois da coroa, e uma barra quase vazia nessa hora diria o contrário do
     * que está acontecendo.
     */
    @Override
    protected float progressoDaBarra() {
        int alvo = planta2().segundosParaGanhar() * 20;
        int maior = 0;
        for (int marca : somaDe.values()) {
            maior = Math.max(maior, marca);
        }
        return ArenaComum.limite(maior / (float) Math.max(1, alvo));
    }

    @Override
    protected String textoDaBarra() {
        ReiCatalog planta = planta2();
        if (rei == null) {
            return "<white>o topo está livre</white> <gray>· "
                    + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
        }
        Player quem = Bukkit.getPlayer(rei);
        String nome = quem == null ? "alguém" : HubBuilder.escapar(quem.getName());
        boolean disputado = quantosNoTopo() > 1;
        return "<white>" + nome + "</white> <gray>somou <white>"
                + (somaDe.getOrDefault(rei, 0) / 20) + "s</white> de "
                + planta.segundosParaGanhar() + "s"
                + (disputado ? " · <color:#FF8A8A>DISPUTADO</color>" : "")
                + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        ReiCatalog planta = planta2();
        resgatarQuemCaiu(mundo, planta);
        contarOTopo(mundo, planta);
        if (rei != null && tiquesDeJogo() % 5 == 0) {
            enfeitarORei(mundo);
        }
    }

    /**
     * Quem caiu no vazio volta ao pé da montanha, com alguns segundos sem
     * poder reinar.
     *
     * O castigo não é o teletransporte: é a subida de novo. Os três segundos
     * existem só para impedir o caso bobo de alguém cair do topo de raspão e
     * voltar nele antes de o tempo de tolerância acabar.
     */
    private void resgatarQuemCaiu(World mundo, ReiCatalog planta) {
        for (Player jogador : jogadoresVivos()) {
            if (jogador.getLocation().getY() >= planta.quedaY()) {
                continue;
            }
            jogador.setFallDistance(0f);
            jogador.teleport(ReiBuilder.pontoDeVolta(mundo, planta, sorte));
            castigoAte.put(jogador.getUniqueId(),
                    tiquesDeJogo() + planta.castigoSegundos() * 20L);
            jogador.playSound(jogador.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL,
                    0.7f, 1.3f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>você caiu — suba de novo</color>"));
        }
    }

    /** A máquina de estados do topo, uma vez por tique. */
    private void contarOTopo(World mundo, ReiCatalog planta) {
        List<Player> emCima = quemEstaNoTopo(planta);

        if (rei != null) {
            Player quemEra = Bukkit.getPlayer(rei);
            boolean continua = quemEra != null && emCima.contains(quemEra);
            if (continua) {
                toleranciaRestante = planta.toleranciaTiques();
                if (emCima.size() == 1) {
                    tiquesNaVez++;
                    int soma = somaDe.merge(rei, 1, Integer::sum);
                    if (soma >= planta.segundosParaGanhar() * 20) {
                        coroar(mundo, quemEra);
                    }
                }
                return;
            }
            toleranciaRestante--;
            if (toleranciaRestante > 0) {
                return;
            }
            perderACoroa(quemEra);
        }

        if (emCima.size() == 1) {
            assumir(emCima.get(0), planta);
        }
    }

    /** Quem está dentro da zona do topo e pode reinar agora. */
    private List<Player> quemEstaNoTopo(ReiCatalog planta) {
        List<Player> emCima = new ArrayList<>();
        for (Player jogador : jogadoresVivos()) {
            if (castigoAte.getOrDefault(jogador.getUniqueId(), 0L)
                    > tiquesDeJogo()) {
                continue;
            }
            if (noTopo(jogador, planta)) {
                emCima.add(jogador);
            }
        }
        return emCima;
    }

    /**
     * Está no topo?
     *
     * Cilindro e não esfera: a zona é o chão plano do cume mais o espaço de
     * um pulo acima dele. Com distância reta, um pulo no meio do topo
     * afastaria o jogador do centro e ele deixaria de reinar no ar — o que
     * ninguém entenderia nem olhando.
     */
    private boolean noTopo(Player jogador, ReiCatalog planta) {
        Location onde = jogador.getLocation();
        double doMeio = Math.hypot(onde.getX() - 0.5, onde.getZ() - 0.5);
        if (doMeio > planta.topoRaio() + 0.5) {
            return false;
        }
        double altura = onde.getY() - (planta.cumeY() + 1);
        return altura >= -0.6 && altura <= TETO_DO_TOPO;
    }

    private int quantosNoTopo() {
        return quemEstaNoTopo(planta2()).size();
    }

    private void assumir(Player jogador, ReiCatalog planta) {
        rei = jogador.getUniqueId();
        tiquesNaVez = 0;
        toleranciaRestante = planta.toleranciaTiques();
        int soma = somaDe.getOrDefault(rei, 0);
        anunciar("<color:" + planta.corDoTexto() + ">"
                + HubBuilder.escapar(jogador.getName())
                + "</color> <gray>tomou o topo"
                + (soma >= 20 ? " — continua de <white>" + (soma / 20)
                        + "s</white>" : "") + "</gray>");
        for (Player quem : jogadoresDaArena()) {
            quem.playSound(quem.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                    0.6f, 1.2f);
        }
    }

    /**
     * Sai o rei, fica o que ele somou.
     *
     * Zerar {@link #tiquesNaVez} aqui é só para a próxima mensagem: a conta
     * que vale, {@link #somaDe}, não é tocada nem aqui nem em
     * {@link #assumir}, e é isso que faz o relógio ser incremental.
     */
    private void perderACoroa(Player quemEra) {
        if (quemEra != null && tiquesNaVez >= 20) {
            UUID quem = quemEra.getUniqueId();
            anunciar("<gray>" + HubBuilder.escapar(quemEra.getName())
                    + " saiu do topo depois de <white>"
                    + ArenaComum.tempo(tiquesNaVez) + "</white> — somou "
                    + "<white>" + (somaDe.getOrDefault(quem, 0) / 20)
                    + "s</white></gray>");
        }
        rei = null;
        tiquesNaVez = 0;
        toleranciaRestante = 0;
    }

    private void coroar(World mundo, Player jogador) {
        campeao = jogador.getUniqueId();
        mundo.spawnParticle(Particle.TOTEM_OF_UNDYING,
                jogador.getLocation().add(0, 1, 0), 100, 0.8, 1.2, 0.8, 0.4);
        mundo.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f, 1.0f);
    }

    private void enfeitarORei(World mundo) {
        Player quem = Bukkit.getPlayer(rei);
        if (quem == null) {
            return;
        }
        mundo.spawnParticle(Particle.END_ROD, quem.getLocation().add(0, 2.4, 0),
                3, 0.25, 0.05, 0.25, 0.005);
    }

    /** A cada segundo: avisa o topo e a arena de como vai o reinado. */
    @Override
    protected void aCadaSegundo(World mundo) {
        if (rei == null) {
            return;
        }
        ReiCatalog planta = planta2();
        int faltam = planta.segundosParaGanhar()
                - somaDe.getOrDefault(rei, 0) / 20;
        if (faltam > 0 && faltam <= 10) {
            for (Player jogador : jogadoresDaArena()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            }
            if (faltam == 10) {
                anunciar("<color:#FF8A8A>faltam 10s para a coroa</color>");
            }
        }
        Player quem = Bukkit.getPlayer(rei);
        if (quem != null && quantosNoTopo() > 1) {
            quem.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>disputado — o relógio parou</color>"));
        }
    }

    // ------------------------------------------------------------------
    //  O bastão
    // ------------------------------------------------------------------

    /**
     * O bastão empurra e não machuca.
     *
     * O dano está desligado nesta arena como em todas as outras, e dano
     * cancelado leva junto o empurrão do próprio Minecraft — então o empurrão
     * é dado à mão. Sai melhor assim: a força e a altura vêm do yml, e ficam
     * iguais para todo mundo, sem depender de encantamento, de arma ou do
     * tempo de recarga do golpe.
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
        ReiCatalog planta = planta2();
        UUID id = quemBateu.getUniqueId();
        if (carenciaDoBastao.getOrDefault(id, 0L) > tiquesDeJogo()) {
            return;
        }
        carenciaDoBastao.put(id, tiquesDeJogo() + planta.empurraoCarencia());
        empurrar(quemBateu, alvo, planta);
    }

    private void empurrar(Player quemBateu, Player alvo, ReiCatalog planta) {
        Vector direcao = alvo.getLocation().toVector()
                .subtract(quemBateu.getLocation().toVector()).setY(0);
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = quemBateu.getLocation().getDirection().setY(0);
        }
        if (direcao.lengthSquared() < 1.0E-6) {
            direcao = new Vector(1, 0, 0);
        }
        direcao.normalize().multiply(planta.empurrao());
        direcao.setY(planta.empurraoAltura());
        alvo.setVelocity(alvo.getVelocity().multiply(0.3).add(direcao));
        alvo.setFallDistance(0f);

        animarGolpe(alvo, quemBateu);
        alvo.getWorld().playSound(alvo.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.1f);
        quemBateu.playSound(quemBateu.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
        alvo.getWorld().spawnParticle(Particle.CRIT,
                alvo.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.1);
    }

    /** Um bastão na mão de todo mundo, e nada mais. */
    private void darOBastao(Player jogador) {
        ItemStack bastao = new ItemStack(Material.STICK);
        ItemMeta nome = bastao.getItemMeta();
        if (nome != null) {
            nome.displayName(plugin.mini().deserialize(
                    "<color:#FFD700>Empurrador</color>").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            bastao.setItemMeta(nome);
        }
        jogador.getInventory().setItem(0, bastao);
        jogador.getInventory().setHeldItemSlot(0);
    }

    // ------------------------------------------------------------------
    //  Limpeza
    // ------------------------------------------------------------------

    @Override
    protected void aoTerminarRodada() {
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            jogador.showTitle(Title.title(Component.empty(),
                    plugin.mini().deserialize("<gray>fim do reinado</gray>")));
        }
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        somaDe.remove(id);
        carenciaDoBastao.remove(id);
        castigoAte.remove(id);
        if (id.equals(rei)) {
            rei = null;
            tiquesNaVez = 0;
            toleranciaRestante = 0;
        }
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    /** Há quantos tiques o rei de agora está no topo desta vez. */
    public int tiquesNaVez() {
        return tiquesNaVez;
    }

    /** Quantos tiques um jogador já somou no topo nesta partida. */
    public int somaDe(UUID id) {
        return somaDe.getOrDefault(id, 0);
    }

    /** Quem está com a coroa agora, ou null. */
    public UUID rei() {
        return rei;
    }
}
