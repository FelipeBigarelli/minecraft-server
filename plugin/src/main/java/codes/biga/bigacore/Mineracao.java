package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A Mineração: cave mais que o vizinho.
 *
 * <h2>Uma mina só, e é por isso que é um jogo</h2>
 *
 * A versão fácil daria a cada jogador o mesmo poço sorteado com a mesma
 * semente: justo, simétrico e solitário — dezesseis paciências lado a lado em
 * que ninguém pode fazer nada a respeito de ninguém. Aqui a mina é UMA. O
 * veio que você tira é o veio que o vizinho não tira, o túnel que você abre
 * ele vê, e o fundo onde está o diamante é o mesmo fundo para os dezesseis.
 * "Cave mais que o vizinho" só quer dizer alguma coisa quando o vizinho e
 * você estão cavando a mesma pedra.
 *
 * <h2>A picareta é a mesma e não melhora</h2>
 *
 * Nenhuma loja, nenhum encantamento, nenhuma pressa que compre pressa. O que
 * separa o primeiro do último é onde ele cavou e quando desceu — e isso é
 * decisão, que se aprende. Picareta comprável premiaria quem já tinha ganhado
 * a partida anterior, e aí a décima partida estaria decidida antes de começar.
 *
 * <h2>O último minuto vale dobrado</h2>
 *
 * Sem isso, quem abre vantagem nos dois primeiros minutos joga os dois
 * últimos sozinho e os outros quinze cavam por nada. Com isso, a vantagem de
 * dois minutos vale um minuto — grande, e não definitiva. É o que mantém
 * dezesseis pessoas cavando até o relógio fechar.
 *
 * <h2>O sonar é o lugar da decisão</h2>
 *
 * Dois usos por partida, e cada um diz o minério mais valioso num raio e para
 * onde ele fica. Não cava, não dá ponto, não acelera nada: só informa. É de
 * propósito a única coisa escassa da partida — quando se gasta um sonar é a
 * única escolha que não dá para desfazer cavando mais.
 */
public final class Mineracao extends JogoDeArena {

    /** Os oito rumos do sonar, com o nome que o jogador lê. */
    private static final String[] ROSA = {"LESTE", "NORDESTE", "NORTE",
            "NOROESTE", "OESTE", "SUDOESTE", "SUL", "SUDESTE"};

    private final Placar placar = new Placar();
    private final Random sorte = new Random();

    private boolean avisouDoDobro;

    public Mineracao(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.mineracaoCatalog();
    }

    private MineracaoCatalog planta2() {
        return plugin.mineracaoCatalog();
    }

    /**
     * Este jogo é em SOBREVIVÊNCIA, e é o único até aqui.
     *
     * No modo aventura o jogador não quebra bloco nenhum — e quebrar bloco é
     * o jogo. Tudo o que a sobrevivência traz de indesejado já está desligado
     * no mundo da arena: dano, fome, queda, e assentar bloco segue proibido
     * pelo {@link #permiteColocar} do framework.
     */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.SURVIVAL;
    }

    @Override
    protected String verboDaLargada() {
        return "CAVE!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        placar.zerar();
        avisouDoDobro = false;
        long semente = planta2().sementeFixa() >= 0
                ? planta2().sementeFixa() : sorte.nextLong();
        plugin.mineracaoBuilder().refazerMina(mundo, semente);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        MineracaoCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(MineracaoBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            equipar(jogador, planta);
            placar.entrar(jogador.getUniqueId());
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        MineracaoCatalog planta = planta2();
        MineracaoCatalog.Veio melhor = planta.tabela().isEmpty() ? null
                : planta.tabela().get(planta.tabela().size() - 1);
        anunciar("<gray>a mina é nova — minério vale ponto, e o fundo paga "
                + "mais" + (melhor == null ? "" : ": <white>" + melhor.nome()
                + "</white> vale <white>" + melhor.pontos() + "</white>")
                + ". Os últimos <white>" + planta.dobroSegundos()
                + "s</white> valem dobrado</gray>");
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return MineracaoBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
    }

    @Override
    protected boolean rodadaAcabou() {
        return quantosVivos() == 0 || segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String vencedor() {
        UUID lider = placar.lider();
        if (lider == null || placar.pontosDoLider() <= 0) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        return placar.pontosDoLider() + " pontos de minério";
    }

    @Override
    protected float progressoDaBarra() {
        int limite = Math.max(1, planta2().tempoLimite());
        return 1f - segundosDeJogo() / (float) limite;
    }

    @Override
    protected String textoDaBarra() {
        MineracaoCatalog planta = planta2();
        int resta = Math.max(0, planta.tempoLimite() - segundosDeJogo());
        String dobro = dobrando() ? " <color:#FFD700>· DOBRADO</color>" : "";
        if (placar.pontosDoLider() <= 0) {
            return "<white>ninguém fez ponto ainda</white> <gray>· "
                    + ArenaComum.tempo(resta * 20L) + "</gray>" + dobro;
        }
        return "<white>" + placar.nomeDoLider("empate na frente")
                + "</white> <gray>com <white>" + placar.pontosDoLider()
                + "</white> pts · " + ArenaComum.tempo(resta * 20L)
                + "</gray>" + dobro;
    }

    /** Estamos nos segundos finais, em que todo minério vale dobrado? */
    private boolean dobrando() {
        MineracaoCatalog planta = planta2();
        return segundosDeJogo() >= planta.tempoLimite() - planta.dobroSegundos();
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaSegundo(World mundo) {
        if (dobrando() && !avisouDoDobro) {
            avisouDoDobro = true;
            tituloEmTodos("<color:#FFD700>DOBRADO</color>",
                    "<gray>todo minério vale o dobro agora</gray>");
            for (Player jogador : jogadoresVivos()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            }
        }
        for (Player jogador : jogadoresVivos()) {
            int meus = placar.de(jogador.getUniqueId());
            int lugar = placar.posicaoDe(jogador.getUniqueId());
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#B5835A>" + meus + " pts</color> <gray>· "
                            + lugar + "º de " + placar.quantos() + "</gray>"));
        }
    }

    // ------------------------------------------------------------------
    //  Cavar
    // ------------------------------------------------------------------

    /**
     * Só se cava dentro do maciço.
     *
     * A moldura é bedrock e nenhuma picareta a leva, então isto não é sobre a
     * moldura: é sobre o saguão, as torres e as placas, que estão no mesmo
     * mundo e seriam desmontadas em dois minutos por alguém que achasse graça.
     */
    @Override
    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        if (!jogando() || !estaVivo(evento.getPlayer())) {
            return false;
        }
        Block bloco = evento.getBlock();
        return MineracaoBuilder.noMacico(planta2(), bloco.getX(), bloco.getY(),
                bloco.getZ());
    }

    /**
     * O ponto é dado aqui, e não no {@link #permiteQuebrar}.
     *
     * O gancho de permissão é uma PERGUNTA, e pergunta que soma ponto soma
     * duas vezes no dia em que outro ouvinte consultá-la. Em MONITOR, com
     * {@code ignoreCancelled}, este método só roda para a quebra que de fato
     * aconteceu — e aí ele é o único lugar do jogo em que o placar sobe.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void aoCavar(BlockBreakEvent evento) {
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador) || !nesteMundo(evento.getBlock().getWorld())) {
            return;
        }
        // Nada cai e nada dá experiência: o inventário é a picareta e o
        // sonar, e item no chão numa mina de dezesseis viraria corrida de
        // catar em vez de corrida de cavar.
        evento.setDropItems(false);
        evento.setExpToDrop(0);

        MineracaoCatalog.Veio veio = planta2().veioDoBloco(evento.getBlock().getType());
        if (veio == null) {
            return;
        }
        int pontos = veio.pontos() * (dobrando() ? 2 : 1);
        placar.somar(jogador.getUniqueId(), pontos);
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.7f, veio.pontos() >= 10 ? 1.8f : 1.2f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#B5835A>" + veio.nome() + "</color> <gray>+" + pontos
                        + " · total " + placar.de(jogador.getUniqueId())
                        + "</gray>"));
    }

    // ------------------------------------------------------------------
    //  O sonar
    // ------------------------------------------------------------------

    private void equipar(Player jogador, MineracaoCatalog planta) {
        jogador.getInventory().clear();

        ItemStack picareta = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = picareta.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.mini().deserialize(
                    "<color:#B5835A>Picareta da Mina</color>").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.setUnbreakable(true);
            picareta.setItemMeta(meta);
        }
        jogador.getInventory().setItem(0, picareta);

        if (planta.sonares() > 0) {
            ItemStack sonar = new ItemStack(Material.SPYGLASS, planta.sonares());
            ItemMeta nome = sonar.getItemMeta();
            if (nome != null) {
                nome.displayName(plugin.mini().deserialize(
                        "<color:#4FD1C5>Sonar</color>").decoration(
                        net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                nome.lore(List.of(plugin.mini().deserialize(
                        "<gray>clique para achar o melhor minério por perto</gray>")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                false)));
                sonar.setItemMeta(nome);
            }
            jogador.getInventory().setItem(1, sonar);
        }
        jogador.getInventory().setHeldItemSlot(0);
    }

    @EventHandler
    public void aoUsarOSonar(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_AIR
                && evento.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (evento.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        ItemStack naMao = jogador.getInventory().getItemInMainHand();
        if (naMao.getType() != Material.SPYGLASS) {
            return;
        }
        // Sem a luneta aproximando: o que o sonar dá é a leitura, e a luneta
        // de verdade só atrapalharia quem está num túnel de um bloco.
        evento.setCancelled(true);

        MineracaoCatalog planta = planta2();
        Block achado = melhorPorPerto(jogador, planta);
        if (achado == null) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.8f, 0.6f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>o sonar não achou minério num raio de "
                            + planta.sonarAlcance() + " — ande e tente de novo</gray>"));
            return;
        }

        MineracaoCatalog.Veio veio = planta.veioDoBloco(achado.getType());
        Location onde = jogador.getLocation();
        int dx = achado.getX() - onde.getBlockX();
        int dz = achado.getZ() - onde.getBlockZ();
        int dy = achado.getY() - onde.getBlockY();
        int plano = (int) Math.round(Math.hypot(dx, dz));
        String fundo = dy == 0 ? "na sua fiada"
                : (dy < 0 ? Math.abs(dy) + " fiadas ABAIXO" : dy + " fiadas ACIMA");

        gastarUmSonar(jogador, naMao);
        jogador.playSound(onde, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        jogador.sendMessage(plugin.mini().deserialize(
                "<color:#4FD1C5>SONAR</color> <gray>·</gray> <white>"
                        + (veio == null ? "minério" : veio.nome())
                        + "</white> <gray>a " + plano + " bloco(s) ao "
                        + rumoEscrito(dx, dz) + ", " + fundo + "</gray>"));
    }

    /**
     * O minério mais valioso num raio, desempatado pelo mais perto.
     *
     * Varre a cubo inteiro em volta do jogador — nove mil blocos, uma vez por
     * clique, duas vezes por partida. É varredura burra de propósito: manter
     * um índice das posições de minério exigiria mantê-lo em dia a cada bloco
     * quebrado, e um índice desatualizado faria o sonar apontar para um buraco.
     */
    private Block melhorPorPerto(Player jogador, MineracaoCatalog planta) {
        int alcance = planta.sonarAlcance();
        Location onde = jogador.getLocation();
        World mundo = jogador.getWorld();
        Block melhor = null;
        int valor = 0;
        double perto = Double.MAX_VALUE;

        for (int dx = -alcance; dx <= alcance; dx++) {
            for (int dz = -alcance; dz <= alcance; dz++) {
                for (int dy = -alcance; dy <= alcance; dy++) {
                    int x = onde.getBlockX() + dx;
                    int y = onde.getBlockY() + dy;
                    int z = onde.getBlockZ() + dz;
                    if (!MineracaoBuilder.noMacico(planta, x, y, z)) {
                        continue;
                    }
                    Block bloco = mundo.getBlockAt(x, y, z);
                    MineracaoCatalog.Veio veio = planta.veioDoBloco(bloco.getType());
                    if (veio == null) {
                        continue;
                    }
                    double longe = Math.sqrt(dx * dx + dy * dy + (double) dz * dz);
                    if (longe > alcance) {
                        continue;
                    }
                    if (veio.pontos() > valor
                            || (veio.pontos() == valor && longe < perto)) {
                        melhor = bloco;
                        valor = veio.pontos();
                        perto = longe;
                    }
                }
            }
        }
        return melhor;
    }

    private void gastarUmSonar(Player jogador, ItemStack naMao) {
        int resta = naMao.getAmount() - 1;
        if (resta <= 0) {
            jogador.getInventory().setItemInMainHand(null);
            jogador.getInventory().setHeldItemSlot(0);
        } else {
            naMao.setAmount(resta);
            jogador.getInventory().setItemInMainHand(naMao);
        }
    }

    /** O rumo em palavra, nos oito pontos da rosa. */
    static String rumoEscrito(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "seus pés";
        }
        // O ângulo cresce de leste para norte; cada fatia da rosa tem 45°.
        double angulo = Math.toDegrees(Math.atan2(-dz, dx));
        int fatia = (int) Math.round(((angulo + 360) % 360) / 45.0) % 8;
        return ROSA[fatia];
    }
}
