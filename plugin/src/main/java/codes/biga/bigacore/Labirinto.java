package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O Labirinto: ache a saída antes deles.
 *
 * <h2>O mapa é outro todo dia, e é o mesmo o dia inteiro</h2>
 *
 * A semente sai da data. Não é enfeite: é o que permite "o labirinto de hoje
 * tem a porta no norte", "eu fiz em quarenta segundos", "me ensina o caminho"
 * — conversas que um labirinto diferente a cada partida não deixa ninguém
 * ter, e que um labirinto fixo mata depois de uma semana. O dia é a unidade
 * certa: longa o bastante para decorar, curta o bastante para não cansar.
 *
 * <h2>A porta se vê, o caminho não</h2>
 *
 * O facho do farol entrega a direção de graça, e isso é escolha de desenho,
 * não descuido. Sem ele, dezesseis pessoas vasculhariam duzentos blocos de
 * borda à procura de um vão de três: seria sorteio. Com ele, todo mundo sabe
 * para onde ir desde o primeiro tique, e o que está em disputa é só o
 * caminho — que é o que o labirinto tem para oferecer.
 *
 * <h2>A barra não conta passos</h2>
 *
 * Ela mostra a distância EM LINHA RETA de quem está mais perto da porta, e
 * nunca a distância pelo labirinto. A diferença não é estética: distância
 * pelo labirinto na tela é um GPS — bastaria andar vendo o número descer e o
 * labirinto estaria resolvido. Em linha reta a barra não diz nada que o facho
 * já não diga, e ainda assim dá o aperto de saber que alguém está perto.
 *
 * <h2>Giz é memória, e memória é pública</h2>
 *
 * Doze marcas no chão, da sua cor. Serve para você não dar a mesma volta duas
 * vezes — e serve para o de trás ler o seu rastro e economizar a volta que
 * você perdeu. Quem não quiser ser lido tem de decidir não marcar, e essa
 * decisão é o jogo.
 */
public final class Labirinto extends JogoDeArena {

    private final Map<UUID, Material> corDoGiz = new HashMap<>();
    private final Map<UUID, Integer> melhorDe = new HashMap<>();
    private final Set<Long> gizNoChao = new HashSet<>();

    private UUID campeao;
    private int tiquesDoCampeao;
    private double menorDistancia;
    private double distanciaDaLargada;

    public Labirinto(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.labirintoCatalog();
    }

    private LabirintoCatalog planta2() {
        return plugin.labirintoCatalog();
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

    /**
     * Refaz o labirinto antes de cada rodada.
     *
     * Não é para limpar o giz, que o refazimento limpa de graça: é que o mapa
     * muda à meia-noite, e um servidor de pé desde terça mostraria o
     * labirinto de terça a quem joga na quinta. O construtor só escreve os
     * blocos que mudaram, então num mesmo dia isto custa quase nada.
     */
    @Override
    protected void prepararArena(World mundo) {
        corDoGiz.clear();
        melhorDe.clear();
        gizNoChao.clear();
        campeao = null;
        tiquesDoCampeao = 0;

        plugin.labirintoBuilder().refazerLabirinto(mundo);

        LabirintoCatalog planta = planta2();
        int[] porta = LabirintoBuilder.pontoDaPorta(planta);
        distanciaDaLargada = Math.hypot(porta[0], porta[1]);
        menorDistancia = distanciaDaLargada;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        LabirintoCatalog planta = planta2();
        List<int[]> pontos = planta.largadas(entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            jogador.teleport(LabirintoBuilder.pontoDaLargada(mundo, planta,
                    pontos.get(k % pontos.size())));
            jogador.setFallDistance(0f);
            Material cor = LabirintoCatalog.GIZES.get(
                    k % LabirintoCatalog.GIZES.size());
            corDoGiz.put(jogador.getUniqueId(), cor);
            darOGiz(jogador, cor, planta.giz());
            melhorDe.put(jogador.getUniqueId(), Integer.MAX_VALUE);
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        LabirintoCatalog planta = planta2();
        anunciar("<gray>a porta de hoje é a <white>"
                + LabirintoBuilder.nomeDoLado(planta.saida()[2])
                + "</white> — o facho mostra onde. São <white>"
                + planta.passosAteASaida() + " esquinas</white> pelo caminho "
                + "mais curto</gray>");
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return LabirintoBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        corDoGiz.remove(id);
        melhorDe.remove(id);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        if (mundo != null) {
            limparOGiz(mundo);
        }
    }

    @Override
    protected boolean rodadaAcabou() {
        if (campeao != null) {
            return true;
        }
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
        // No relógio ganha quem chegou mais perto da porta PELO LABIRINTO, e
        // só se houver um único mais perto.
        UUID melhor = null;
        int menor = Integer.MAX_VALUE;
        boolean empate = false;
        for (Map.Entry<UUID, Integer> marca : melhorDe.entrySet()) {
            if (marca.getValue() < menor) {
                menor = marca.getValue();
                melhor = marca.getKey();
                empate = false;
            } else if (marca.getValue().equals(menor)) {
                empate = true;
            }
        }
        if (melhor == null || empate || menor == Integer.MAX_VALUE) {
            return null;
        }
        Player quem = Bukkit.getPlayer(melhor);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        if (campeao != null) {
            return "atravessou a porta em " + ArenaComum.tempo(tiquesDoCampeao);
        }
        int menor = Integer.MAX_VALUE;
        for (int marca : melhorDe.values()) {
            menor = Math.min(menor, marca);
        }
        return menor == Integer.MAX_VALUE ? "ninguém saiu do meio"
                : "parou a " + menor + " esquina(s) da porta";
    }

    @Override
    protected float progressoDaBarra() {
        if (distanciaDaLargada <= 0) {
            return 0f;
        }
        return (float) (1.0 - menorDistancia / distanciaDaLargada);
    }

    @Override
    protected String textoDaBarra() {
        return "<white>o mais perto está a " + (int) Math.round(menorDistancia)
                + " blocos da porta</white> <gray>· "
                + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        LabirintoCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            Location onde = jogador.getLocation();
            if (campeao == null && LabirintoBuilder.naPorta(planta, onde.getX(),
                    onde.getY(), onde.getZ())) {
                atravessou(mundo, jogador);
            }
        }
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        LabirintoCatalog planta = planta2();
        int[] porta = LabirintoBuilder.pontoDaPorta(planta);
        int[] passos = planta.distanciasDaPorta();
        int meio = planta.meio();
        double perto = distanciaDaLargada;

        for (Player jogador : jogadoresVivos()) {
            Location onde = jogador.getLocation();
            perto = Math.min(perto, Math.hypot(onde.getX() - (porta[0] + 0.5),
                    onde.getZ() - (porta[1] + 0.5)));

            int i = planta.celulaMaisProxima(onde.getBlockX() + meio);
            int j = planta.celulaMaisProxima(onde.getBlockZ() + meio);
            int quantos = passos[LabirintoCatalog.indice(planta.celulas(), i, j)];
            if (quantos >= 0) {
                melhorDe.merge(jogador.getUniqueId(), quantos, Math::min);
            }
        }
        menorDistancia = perto;

        if (segundosDeJogo() % 3 == 0) {
            Location farol = LabirintoBuilder.pontoDoFarol(mundo, planta);
            mundo.spawnParticle(Particle.END_ROD, farol, 12, 0.4, 0.8, 0.4, 0.01);
        }
    }

    /** O primeiro a atravessar a porta fecha a partida. */
    private void atravessou(World mundo, Player jogador) {
        campeao = jogador.getUniqueId();
        tiquesDoCampeao = (int) tiquesDeJogo();
        melhorDe.put(campeao, 0);
        mundo.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f, 1.0f);
        anunciar("<color:#4FD1C5>" + HubBuilder.escapar(jogador.getName())
                + "</color> <gray>achou a saída em <white>"
                + ArenaComum.tempo(tiquesDoCampeao) + "</white></gray>");
    }

    // ------------------------------------------------------------------
    //  O giz
    // ------------------------------------------------------------------

    private void darOGiz(Player jogador, Material cor, int quantos) {
        jogador.getInventory().clear();
        if (quantos <= 0) {
            return;
        }
        ItemStack giz = new ItemStack(cor, quantos);
        ItemMeta nome = giz.getItemMeta();
        if (nome != null) {
            nome.displayName(plugin.mini().deserialize(
                    "<color:#4FD1C5>Giz</color>").decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            nome.lore(List.of(plugin.mini().deserialize(
                    "<gray>clique no chão para marcar onde passou</gray>")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                            false)));
            giz.setItemMeta(nome);
        }
        jogador.getInventory().setItem(0, giz);
        jogador.getInventory().setHeldItemSlot(0);
    }

    /**
     * A marca de giz é posta pelo plugin, e não pelo jogo.
     *
     * No modo aventura o jogador não assenta bloco nenhum — e é assim que se
     * quer, porque assentar à vontade num labirinto é escada para cima do
     * muro. Então o clique só avisa, e quem põe o tapete é este método, que
     * confere que é o PISO do labirinto, que está vazio em cima e que o
     * jogador ainda tem giz.
     */
    @EventHandler
    public void aoMarcarComGiz(PlayerInteractEvent evento) {
        if (evento.getAction() != Action.RIGHT_CLICK_BLOCK
                || evento.getClickedBlock() == null
                || evento.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        Material cor = corDoGiz.get(jogador.getUniqueId());
        ItemStack naMao = jogador.getInventory().getItemInMainHand();
        if (cor == null || naMao.getType() != cor) {
            return;
        }
        evento.setCancelled(true);

        LabirintoCatalog planta = planta2();
        Block chao = evento.getClickedBlock();
        Block acima = chao.getRelative(0, 1, 0);
        int meio = planta.meio();
        if (chao.getY() != planta.topoY() || !acima.getType().isAir()
                || Math.abs(chao.getX()) > meio || Math.abs(chao.getZ()) > meio) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_WOOL_HIT, 0.5f, 0.7f);
            return;
        }
        acima.setType(cor, false);
        gizNoChao.add(ArenaComum.chave(acima.getX(), acima.getY(), acima.getZ()));
        int resta = naMao.getAmount() - 1;
        if (resta <= 0) {
            jogador.getInventory().setItemInMainHand(null);
        } else {
            naMao.setAmount(resta);
            jogador.getInventory().setItemInMainHand(naMao);
        }
        jogador.playSound(acima.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.6f, 1.4f);
    }

    /** Apaga as marcas no fim da rodada, para a plateia ver o labirinto limpo. */
    private void limparOGiz(World mundo) {
        for (long chave : new ArrayList<>(gizNoChao)) {
            Block bloco = mundo.getBlockAt(ArenaComum.daChave(chave, 40),
                    ArenaComum.daChave(chave, 20), ArenaComum.daChave(chave, 0));
            if (org.bukkit.Tag.WOOL_CARPETS.isTagged(bloco.getType())) {
                bloco.setType(Material.AIR, false);
            }
        }
        gizNoChao.clear();
    }
}
