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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O Pega-Pega: quem encosta, passa a vez.
 *
 * <h2>Passa no TOQUE, e não no clique</h2>
 *
 * Os plugins de onde a ideia vem passam a bomba num evento de dano: o pegador
 * tem de MIRAR e BATER em quem está correndo. Aqui a bomba passa por
 * distância, varrida a cada tique. Três razões: "encostar" é o que pega-pega
 * sempre quis dizer; bater em alvo que corre é teste de ping e não de corrida;
 * e o dano está desligado nesta arena de qualquer jeito, como em todas as
 * outras.
 *
 * <h2>A carência é o que faz o pavio decidir alguma coisa</h2>
 *
 * Sem ela, dois jogadores colados trocam a bomba vinte vezes por segundo até
 * o pavio acabar na mão de quem teve azar no último tique. Com dois segundos
 * de carência para quem acabou de passar, passar a bomba é um ato: quem passou
 * tem de fugir, e quem recebeu tem de achar OUTRA pessoa.
 *
 * <h2>Quem carrega corre mais e brilha</h2>
 *
 * As duas coisas são necessárias e por motivos opostos. A velocidade, porque
 * num pátio de gente igual quem persegue nunca alcança e o pavio vira
 * sorteio. O brilho, porque num pátio de dois andares ninguém saberia de quem
 * está fugindo — e fugir de quem não se vê não é jogo, é susto.
 *
 * <h2>Rodadas dentro da partida</h2>
 *
 * Cada explosão fecha uma rodada e abre a seguinte, com pavio mais curto e
 * bombas na proporção de quem sobrou. É o que faz uma partida de dezesseis
 * caber em três minutos em vez de quinze: uma bomba só, como nos plugins de
 * referência, seria quinze rodadas em que quatorze pessoas não fazem nada.
 */
public final class PegaPega extends JogoDeArena {

    private final Set<UUID> comBomba = new HashSet<>();
    private final Map<UUID, Long> carenciaDe = new HashMap<>();
    private final Map<UUID, Integer> passesDe = new HashMap<>();

    private int rodada;
    private int pavioRestante;
    private UUID ultimoACarregar;

    public PegaPega(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.pegaCatalog();
    }

    private PegaPegaCatalog planta2() {
        return plugin.pegaCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "CORRA!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        // O pátio é de pedra e ninguém quebra nada nele: não há o que refazer.
        // O que precisa de limpeza é o estado da partida passada.
        comBomba.clear();
        carenciaDe.clear();
        passesDe.clear();
        rodada = 0;
        pavioRestante = 0;
        ultimoACarregar = null;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        PegaPegaCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            if (!pontos.isEmpty()) {
                jogador.teleport(PegaPegaBuilder.pontoDaLargada(mundo, planta,
                        pontos.get(k % pontos.size())));
            }
            jogador.setFallDistance(0f);
            passesDe.put(jogador.getUniqueId(), 0);
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        novaRodada();
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return PegaPegaBuilder.pontoDaPlateia(mundo, planta2());
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
            return "nunca explodiu";
        }
        int passes = passesDe.getOrDefault(vivos.get(0).getUniqueId(), 0);
        return "nunca explodiu · passou a bomba " + passes
                + (passes == 1 ? " vez" : " vezes");
    }

    @Override
    protected String textoDaBarra() {
        String bombas = comBomba.size() == 1 ? "1 bomba" : comBomba.size() + " bombas";
        return "<white>" + quantosVivos() + "</white> de pé <gray>· " + bombas
                + " · pavio <white>" + Math.max(0, pavioRestante)
                + "s</white> · " + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O pavio
    // ------------------------------------------------------------------

    /**
     * A cada segundo: queima o pavio e, no fim dele, explode quem estiver com
     * a bomba.
     */
    @Override
    protected void aCadaSegundo(World mundo) {
        if (comBomba.isEmpty()) {
            novaRodada();
            return;
        }
        pavioRestante--;
        if (pavioRestante > 0) {
            avisarDoPavio();
            return;
        }
        estourar(mundo);
        if (!rodadaAcabou()) {
            novaRodada();
        }
    }

    /** Começa uma rodada: sorteia as bombas e acerta o pavio. */
    private void novaRodada() {
        PegaPegaCatalog planta = planta2();
        List<Player> vivos = new ArrayList<>(jogadoresVivos());
        if (vivos.size() <= 1) {
            return;
        }
        rodada++;
        pavioRestante = planta.pavioDaRodada(rodada);

        int quantas = PegaPegaCatalog.quantasBombas(vivos.size(),
                planta.jogadoresPorBomba());
        Collections.shuffle(vivos);
        // Quem acabou de explodir não está mais vivo, mas quem acabou de
        // receber a bomba e sobreviveu pode ser sorteado de novo — menos o
        // último a carregar, que já correu a rodada inteira com ela.
        vivos.sort((a, b) -> Boolean.compare(a.getUniqueId().equals(ultimoACarregar),
                b.getUniqueId().equals(ultimoACarregar)));

        for (int i = 0; i < quantas && i < vivos.size(); i++) {
            darABomba(vivos.get(i), null);
        }
        anunciar("<color:" + planta.corDoTexto() + ">rodada " + rodada
                + "</color> <gray>— " + (quantas == 1 ? "uma bomba" : quantas
                + " bombas") + ", pavio de <white>" + pavioRestante
                + "s</white></gray>");
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT,
                    0.7f, 1.4f);
        }
    }

    /** O pavio acabou: quem está com a bomba sai. */
    private void estourar(World mundo) {
        List<Player> perdidos = new ArrayList<>();
        for (UUID id : comBomba) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador != null && estaVivo(jogador)) {
                perdidos.add(jogador);
            }
        }
        comBomba.clear();
        for (Player jogador : perdidos) {
            Location onde = jogador.getLocation();
            mundo.spawnParticle(Particle.EXPLOSION, onde.clone().add(0, 1, 0),
                    1, 0, 0, 0, 0);
            mundo.playSound(onde, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
            anunciar("<color:#FF8A8A>" + HubBuilder.escapar(jogador.getName())
                    + "</color> <gray>explodiu com a bomba na mão</gray>");
            tirarABomba(jogador);
            eliminar(jogador);
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.showTitle(Title.title(Component.empty(),
                    plugin.mini().deserialize("<color:#FF8A8A>BUM</color>")));
        }
    }

    private void avisarDoPavio() {
        if (pavioRestante > 5) {
            return;
        }
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.8f, 2.0f);
        }
        for (UUID id : comBomba) {
            Player jogador = Bukkit.getPlayer(id);
            if (jogador == null) {
                continue;
            }
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>a bomba estoura em " + pavioRestante
                            + "s — ENCOSTE EM ALGUÉM</color>"));
        }
    }

    // ------------------------------------------------------------------
    //  O toque
    // ------------------------------------------------------------------

    /**
     * A cada tique: procura alguém ao alcance de cada bomba, e mantém o
     * brilho e a velocidade de quem carrega.
     *
     * A varredura é de bombas contra vivos: quatro bombas e dezesseis vivos
     * dão sessenta e quatro contas de distância por tique, que é nada. Não
     * vale a pena indexar o pátio por região para isso.
     */
    @Override
    protected void aCadaTique(World mundo) {
        if (comBomba.isEmpty()) {
            return;
        }
        PegaPegaCatalog planta = planta2();
        double alcance = planta.alcance();
        List<Player> vivos = jogadoresVivos();

        for (UUID id : new ArrayList<>(comBomba)) {
            Player quemTem = Bukkit.getPlayer(id);
            if (quemTem == null || !estaVivo(quemTem)) {
                comBomba.remove(id);
                continue;
            }
            manterABomba(quemTem, planta);
            Player alvo = maisPertoAoAlcance(quemTem, vivos, alcance);
            if (alvo != null) {
                passar(quemTem, alvo, planta);
            }
        }
    }

    /** Quem está ao alcance e pode receber a bomba agora. */
    private Player maisPertoAoAlcance(Player quemTem, List<Player> vivos,
                                      double alcance) {
        Player melhor = null;
        double perto = Double.MAX_VALUE;
        for (Player outro : vivos) {
            if (outro.equals(quemTem) || comBomba.contains(outro.getUniqueId())) {
                continue;
            }
            if (carenciaDe.getOrDefault(outro.getUniqueId(), 0L) > tiquesDeJogo()) {
                continue;
            }
            Location aqui = quemTem.getLocation();
            Location ali = outro.getLocation();
            // A altura entra separada: num pátio de dois andares, a distância
            // reta deixaria alguém na galeria tocar quem passa por baixo.
            if (Math.abs(aqui.getY() - ali.getY()) > 2.0) {
                continue;
            }
            double entre = Math.hypot(aqui.getX() - ali.getX(),
                    aqui.getZ() - ali.getZ());
            if (entre <= alcance && entre < perto) {
                perto = entre;
                melhor = outro;
            }
        }
        return melhor;
    }

    private void passar(Player deQuem, Player paraQuem, PegaPegaCatalog planta) {
        tirarABomba(deQuem);
        comBomba.remove(deQuem.getUniqueId());
        carenciaDe.put(deQuem.getUniqueId(),
                tiquesDeJogo() + planta.carenciaTiques());
        passesDe.merge(deQuem.getUniqueId(), 1, Integer::sum);

        darABomba(paraQuem, deQuem);
        deQuem.sendActionBar(plugin.mini().deserialize(
                "<color:#A8E063>passou a bomba para "
                        + HubBuilder.escapar(paraQuem.getName()) + "</color>"));
        deQuem.playSound(deQuem.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.6f, 1.8f);
    }

    /** Põe a bomba na cabeça de alguém, com brilho, pressa e aviso. */
    private void darABomba(Player jogador, Player deQuem) {
        PegaPegaCatalog planta = planta2();
        comBomba.add(jogador.getUniqueId());
        ultimoACarregar = jogador.getUniqueId();
        jogador.getInventory().setHelmet(new ItemStack(Material.TNT));
        jogador.setGlowing(true);
        if (planta.velocidadeExtra() > 0) {
            jogador.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION,
                    planta.velocidadeExtra() - 1, false, false, false));
        }
        jogador.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>A BOMBA É SUA</color>"),
                plugin.mini().deserialize(deQuem == null
                        ? "<gray>encoste em alguém</gray>"
                        : "<gray>" + HubBuilder.escapar(deQuem.getName())
                                + " encostou em você</gray>")));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        jogador.getWorld().spawnParticle(Particle.SMOKE,
                jogador.getLocation().add(0, 2.2, 0), 12, 0.2, 0.1, 0.2, 0.01);
    }

    private void tirarABomba(Player jogador) {
        ItemStack capacete = jogador.getInventory().getHelmet();
        if (capacete != null && capacete.getType() == Material.TNT) {
            jogador.getInventory().setHelmet(null);
        }
        jogador.setGlowing(false);
        jogador.removePotionEffect(PotionEffectType.SPEED);
    }

    /**
     * Repõe o brilho e a pressa de quem carrega.
     *
     * Parece redundante e não é: um jogador que entra no servidor de novo, que
     * troca de mundo ou que é teleportado pelo administrador volta sem efeito
     * nenhum, e a bomba dele viraria invisível e lenta sem ninguém entender
     * por quê.
     */
    private void manterABomba(Player jogador, PegaPegaCatalog planta) {
        if (!jogador.isGlowing()) {
            jogador.setGlowing(true);
        }
        ItemStack capacete = jogador.getInventory().getHelmet();
        if (capacete == null || capacete.getType() != Material.TNT) {
            jogador.getInventory().setHelmet(new ItemStack(Material.TNT));
        }
        if (planta.velocidadeExtra() > 0
                && !jogador.hasPotionEffect(PotionEffectType.SPEED)) {
            jogador.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION,
                    planta.velocidadeExtra() - 1, false, false, false));
        }
        if (tiquesDeJogo() % 4 == 0) {
            jogador.getWorld().spawnParticle(Particle.SMOKE,
                    jogador.getLocation().add(0, 2.2, 0), 2, 0.1, 0.05, 0.1, 0.01);
        }
    }

    // ------------------------------------------------------------------
    //  Limpeza
    // ------------------------------------------------------------------

    @Override
    protected void aoEliminarJogador(Player jogador) {
        comBomba.remove(jogador.getUniqueId());
        tirarABomba(jogador);
    }

    @Override
    protected void aoTerminarRodada() {
        for (Player jogador : jogadoresDaArena()) {
            tirarABomba(jogador);
        }
        comBomba.clear();
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        comBomba.remove(id);
        carenciaDe.remove(id);
        passesDe.remove(id);
        Player jogador = Bukkit.getPlayer(id);
        if (jogador != null) {
            tirarABomba(jogador);
        }
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    /** Quantas bombas estão em jogo agora. */
    public int quantasBombas() {
        return comBomba.size();
    }

    /** Quantos segundos faltam para a próxima explosão. */
    public int pavioRestante() {
        return Math.max(0, pavioRestante);
    }

    /** Este jogador está com uma bomba? */
    public boolean temBomba(Player jogador) {
        return comBomba.contains(jogador.getUniqueId());
    }
}
