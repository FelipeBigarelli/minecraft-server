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
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O ARCO E FLECHA: mire antes de ser mirado.
 *
 * <h2>Só vale tiro cheio, e é esta regra que faz o jogo</h2>
 *
 * Com flecha infinita, o jogo natural do arco do Minecraft é soltar a corda
 * sem puxar. Uma flecha de força 0,1 machuca pouco — mas num jogo em que um
 * acerto elimina ela mata igual, então a jogada certa seria encher o ar de
 * flechas e não mirar em nada. Aqui o tiro abaixo de
 * {@code regras.forca-minima} é RECUSADO: a flecha não sai e a puxada se
 * perde. Cada tiro passa a custar um segundo de arco armado, e é esse segundo
 * que transforma o jogo em mira em vez de em volume.
 *
 * <p>O número é 0,9 e não 1,0 por um motivo medido, não por gosto:
 * {@link ArcoCatalog#forcaDe} é {@code (t² + 2t)/3}, e dá 0,87 em 18 tiques,
 * 0,93 em 19 e 1,00 em 20. O arco chega ao máximo um tique antes de vez em
 * quando, por diferença entre cliente e servidor — com 1,0 exato o arco "às
 * vezes não atira" e o jogador nunca descobre por quê, o que é o pior tipo de
 * defeito porque parece azar.
 *
 * <h2>Um acerto elimina</h2>
 *
 * Não há vida, comida, armadura nem cura, e o dano continua cancelado como em
 * toda arena daqui: o que resolve é o ACERTO. O arco do Minecraft em
 * sobrevivência é decidido por quantos corações cada um tem e por quem comeu
 * maçã dourada, e isso é outro jogo — um em que mirar melhor não basta.
 *
 * <h2>A flecha come a cobertura</h2>
 *
 * Toda flecha que acerta um muro tira aquele bloco. Sem isso, um jogo de um
 * acerto elimina é dezesseis pessoas agachadas atrás de pedra esperando o
 * relógio. Com isso, ficar parado gasta o próprio abrigo, atirar no muro do
 * outro é jogada, e o fim de toda partida é em campo aberto — sem precisar de
 * anel que aperta nem de chão que cai, que este projeto já tem em três jogos.
 *
 * <h2>Os primeiros segundos não matam</h2>
 *
 * Largar dezesseis arqueiros juntos e valer tudo desde o primeiro tique faz
 * metade deles morrer de flecha perdida antes de ter onde se cobrir. A graça
 * resolve sem tirar nada do jogo: a flecha voa, acerta, faz barulho e não
 * elimina.
 */
public final class Arco extends JogoDeArena {

    private final Placar placar = new Placar();

    /** Quantos tiros cada um deu, para a conta de pontaria. */
    private final Map<UUID, Integer> tiros = new HashMap<>();
    private final Map<UUID, Integer> acertos = new HashMap<>();

    private boolean gracaAcabou;

    public Arco(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.arcoCatalog();
    }

    private ArcoCatalog planta2() {
        return plugin.arcoCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "ATIRE!";
    }

    /**
     * O PvP ligado é o que faz a flecha colidir com jogador.
     *
     * O dano continua cancelado — quem resolve a eliminação é
     * {@link #aoAcertar}. Mesmo arranjo do bastão do Rei da Montanha e da
     * captura do Esconde: o evento precisa acontecer, o dano não precisa
     * valer.
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
        placar.zerar();
        tiros.clear();
        acertos.clear();
        gracaAcabou = false;
        plugin.arcoBuilder().refazerCobertura(mundo);
        limparFlechas(mundo);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        ArcoCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(Math.max(2, entram.size()));
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            jogador.teleport(ArcoBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            darOArco(jogador);
            tiros.put(id, 0);
            acertos.put(id, 0);
            placar.entrar(id);
        }
    }

    /**
     * O arco com Infinidade e UMA flecha.
     *
     * Infinidade e não um arco sem flecha: {@code setConsumeItem} do evento
     * de tiro está documentado como sem efeito, e repor a flecha a cada tique
     * faria o inventário piscar. O encantamento é a maneira do próprio jogo
     * de ter flecha infinita, e ela funciona sem ajuda.
     */
    private void darOArco(Player jogador) {
        jogador.getInventory().clear();
        ItemStack arco = new ItemStack(Material.BOW);
        arco.addUnsafeEnchantment(Enchantment.INFINITY, 1);
        ItemMeta marca = arco.getItemMeta();
        if (marca != null) {
            marca.displayName(plugin.mini().deserialize("<color:"
                    + planta2().corDoTexto() + ">Arco do Campo</color>"));
            marca.setUnbreakable(true);
            arco.setItemMeta(marca);
        }
        jogador.getInventory().setItem(0, arco);
        jogador.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
        jogador.getInventory().setHeldItemSlot(0);
        jogador.updateInventory();
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        ArcoCatalog planta = planta2();
        anunciar("<gray>a flecha é <white>infinita</white> e <white>um acerto "
                + "elimina</white>. Tiro sem puxar o arco é <white>recusado"
                + "</white> — puxe <white>"
                + ArcoCatalog.tiquesParaForca(planta.forcaMinima())
                + " tiques</white> (um segundo) antes de soltar. A flecha "
                + "<white>come o muro</white> em que acerta, então acampar "
                + "gasta o próprio abrigo.</gray>");
        anunciar("<color:" + planta.corDoTexto() + ">os primeiros "
                + planta.gracaSegundos() + "s a flecha não mata</color> "
                + "<gray>— corra para um muro</gray>");
        mostrarOPainel(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return ArcoBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        tiros.remove(id);
        acertos.remove(id);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
        }
        if (mundo != null) {
            limparFlechas(mundo);
            plugin.arcoBuilder().refazerCobertura(mundo);
            ArenaComum.limparPaineis(mundo, ArcoBuilder.TAG_TIRO);
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
            return "empate nos acertos";
        }
        return pontaria(quem);
    }

    /** "3 acertos em 11 tiros · 27% de pontaria" */
    private String pontaria(UUID id) {
        int deu = tiros.getOrDefault(id, 0);
        int pegou = acertos.getOrDefault(id, 0);
        int parte = deu == 0 ? 0 : (int) Math.round(pegou * 100.0 / deu);
        return pegou + (pegou == 1 ? " acerto" : " acertos") + " em " + deu
                + (deu == 1 ? " tiro" : " tiros") + " · " + parte
                + "% de pontaria";
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        ArcoCatalog planta = planta2();
        if (!gracaAcabou) {
            return "<color:" + planta.corDoTexto() + ">a flecha ainda não "
                    + "mata — " + Math.max(0, planta.gracaSegundos()
                            - segundosDeJogo()) + "s</color>";
        }
        return "<white>" + quantosVivos() + "</white> <gray>de pé · "
                + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O tiro
    // ------------------------------------------------------------------

    /**
     * O tiro: recusa a puxada curta e prepara a flecha.
     *
     * A flecha sai sem poder ser catada do chão: num campo em que dezesseis
     * pessoas atiram uma vez por segundo por cinco minutos, flecha catável é
     * um tapete de itens no chão e algumas centenas de entidades para o
     * servidor acompanhar à toa.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoAtirar(EntityShootBowEvent evento) {
        if (!(evento.getEntity() instanceof Player jogador)) {
            return;
        }
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        ArcoCatalog planta = planta2();
        if (evento.getForce() < planta.forcaMinima()) {
            evento.setCancelled(true);
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>tiro fraco — recusado</color> <gray>· "
                            + "puxe o arco <white>um segundo</white> "
                            + "inteiro</gray>"));
            return;
        }
        UUID id = jogador.getUniqueId();
        tiros.merge(id, 1, Integer::sum);
        if (evento.getProjectile() instanceof AbstractArrow flecha) {
            flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            flecha.setCritical(true);
            flecha.setPersistent(false);
        }
    }

    /**
     * A flecha chegou: em alguém, ou num muro.
     *
     * Resolve no acerto do projétil e não no dano: a referência do gênero
     * compara o dano da flecha com a vida do alvo, e aí quem tem mais coração
     * é mais difícil de eliminar do que quem mira melhor. Aqui o dano
     * continua cancelado e o acerto resolve.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void aoAcertar(ProjectileHitEvent evento) {
        if (!(evento.getEntity() instanceof AbstractArrow flecha)) {
            return;
        }
        if (!jogando() || !nesteMundo(flecha.getWorld())) {
            return;
        }
        Player dono = flecha.getShooter() instanceof Player quem ? quem : null;

        if (evento.getHitEntity() instanceof Player alvo) {
            flecha.remove();
            if (dono == null || !estaVivo(alvo) || alvo.equals(dono)
                    || !estaVivo(dono)) {
                return;
            }
            if (!gracaAcabou) {
                avisarDaGraca(dono, alvo);
                return;
            }
            acertar(dono, alvo);
            return;
        }

        Block bloco = evento.getHitBlock();
        if (bloco != null) {
            if (plugin.arcoBuilder().comerCobertura(flecha.getWorld(), bloco)) {
                flecha.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE,
                        bloco.getLocation().add(0.5, 0.5, 0.5), 20,
                        0.3, 0.3, 0.3, 0.1, bloco.getBlockData());
                flecha.getWorld().playSound(bloco.getLocation(),
                        Sound.BLOCK_MUD_BRICKS_BREAK, 0.9f, 1.1f);
                if (dono != null) {
                    dono.sendActionBar(plugin.mini().deserialize(
                            "<gray>a sua flecha comeu um bloco de "
                                    + "muro</gray>"));
                }
            }
            flecha.remove();
        }
    }

    private void avisarDaGraca(Player dono, Player alvo) {
        dono.playSound(dono.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER,
                0.5f, 0.8f);
        dono.sendActionBar(plugin.mini().deserialize(
                "<color:#FFD93D>acertou</color> <gray>— mas a flecha ainda "
                        + "não mata (" + Math.max(0,
                                planta2().gracaSegundos() - segundosDeJogo())
                        + "s)</gray>"));
        alvo.sendActionBar(plugin.mini().deserialize(
                "<color:#FFD93D>levou uma flecha</color> <gray>— a graça "
                        + "salvou você</gray>"));
    }

    private void acertar(Player dono, Player alvo) {
        ArcoCatalog planta = planta2();
        UUID id = dono.getUniqueId();
        int quantos = acertos.merge(id, 1, Integer::sum);
        placar.somar(id, planta.pontosPorAcerto());

        dono.playSound(dono.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER,
                1.0f, 1.3f);
        dono.sendActionBar(plugin.mini().deserialize(
                "<color:#8BE04E>acertou " + HubBuilder.escapar(alvo.getName())
                        + "</color> <gray>· " + pontaria(id) + "</gray>"));
        alvo.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>FLECHADO</color>"),
                plugin.mini().deserialize("<gray>"
                        + HubBuilder.escapar(dono.getName()) + " · "
                        + pontaria(alvo.getUniqueId()) + "</gray>")));
        anunciar("<color:" + planta.corDoTexto() + ">"
                + HubBuilder.escapar(dono.getName()) + "</color> <gray>flechou "
                + "<white>" + HubBuilder.escapar(alvo.getName())
                + "</white> — faltam <white>" + (quantosVivos() - 1)
                + "</white></gray>");
        alvo.getWorld().spawnParticle(Particle.CRIT,
                alvo.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
        if (quantos >= 3) {
            dono.getWorld().spawnParticle(Particle.END_ROD,
                    dono.getLocation().add(0, 2.2, 0), 6, 0.3, 0.1, 0.3, 0.01);
        }
        eliminar(alvo);
        mostrarOPainel(alvo.getWorld(), planta);
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando() || gracaAcabou) {
            return;
        }
        if (segundosDeJogo() >= planta2().gracaSegundos()) {
            gracaAcabou = true;
            tituloEmTodos("<color:#FF8A8A>A FLECHA MATA</color>",
                    "<gray>um acerto elimina</gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
            }
            mostrarOPainel(mundo, planta2());
        }
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        ArcoCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            String aviso = gracaAcabou ? ""
                    : " <color:" + planta.corDoTexto() + ">· graça "
                            + Math.max(0, planta.gracaSegundos()
                                    - segundosDeJogo()) + "s</color>";
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>" + pontaria(id) + " · <white>" + quantosVivos()
                            + "</white> de pé</gray>" + aviso));
        }
    }

    private void mostrarOPainel(World mundo, ArcoCatalog planta) {
        String linha = gracaAcabou
                ? "<gray>um acerto elimina</gray>"
                : "<color:" + planta.corDoTexto() + ">a flecha ainda não "
                        + "mata</color>";
        plugin.arcoBuilder().painelDoTiro(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + quantosVivos() + " DE PÉ</b></color>\n"
                        + linha + "\n<gray>puxe o arco <white>um segundo"
                        + "</white></gray>"));
    }

    /** Tira do mundo toda flecha largada, que de outro jeito acumula. */
    private void limparFlechas(World mundo) {
        for (Entity bicho : mundo.getEntities()) {
            if (bicho instanceof AbstractArrow) {
                bicho.remove();
            }
        }
    }

    /** Quantos tiros e acertos um jogador tem, para quem pergunta de fora. */
    public int tirosDe(UUID id) {
        return tiros.getOrDefault(id, 0);
    }

    public int acertosDe(UUID id) {
        return acertos.getOrDefault(id, 0);
    }
}
