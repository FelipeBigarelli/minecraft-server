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
 * <h2>O labirinto é outro a cada rodada</h2>
 *
 * Traçado, tamanho, estilo e voltas saem de um sorteio novo em cada largada:
 * centenas de labirintos diferentes, cada um com um mapa inédito por cima.
 * Um labirinto só se decora, e decorado ele deixa de ser labirinto — vira uma
 * corrida de quem já sabe o caminho contra quem não sabe, que é a pior
 * corrida que existe.
 *
 * <p>Mas o que muda não é só o desenho, e é aí que está o jogo: cada um dos
 * cinco traçados tem uma TEXTURA própria — o FUNDO dá corredores compridos e
 * sinuosos, o GALHOS dá becos curtos, a VENTANIA dá uma avenida reta que
 * atravessa o mapa inteiro, o JUSTO não tem vício nenhum, os SALÕES dão
 * câmaras com muros longos. O nome do traçado é anunciado na largada de
 * propósito: quem jogou dez rodadas sabe o que esse nome significa, e esse
 * saber é vantagem de verdade — a única que sobrevive ao sorteio.
 *
 * <h2>Nada diz onde é a porta</h2>
 *
 * Esta classe já teve um farol: um beacon no patamar da porta, com o facho
 * subindo até o céu. A ideia era poupar a busca de um vão de três numa borda
 * de duzentos blocos. O que ele fez de verdade foi APAGAR a busca — e a
 * busca é o jogo. Saber para onde correr desde o primeiro tique transforma o
 * labirinto num corredor com curvas.
 *
 * <p>O que ficou no lugar não é nada: a porta é sorteada no terço mais
 * LONGE do meio, então ela sempre está onde se chega andando bastante, e o
 * número de esquinas do caminho mais curto é anunciado na largada. Dá para
 * saber se esta rodada é de trinta segundos ou de três minutos sem saber
 * para que lado ir.
 *
 * <h2>A barra não aponta</h2>
 *
 * Ela mostra o quanto o mais adiantado se AFASTOU DO MEIO, e nunca a
 * distância até a porta. A diferença é a mesma de antes, por escrito:
 * distância até a porta na tela é um medidor de quente-e-frio, e andar vendo
 * o número descer resolve o labirinto sem o jogador ler uma parede. O
 * afastamento do meio dá o aperto de saber que alguém está avançando sem
 * dizer para onde.
 *
 * <h2>Giz é memória, e memória é pública</h2>
 *
 * Doze marcas no chão, da sua cor. Serve para você não dar a mesma volta duas
 * vezes — e serve para o de trás ler o seu rastro e economizar a volta que
 * você perdeu. Quem não quiser ser lido tem de decidir não marcar, e essa
 * decisão é o jogo.
 */
public final class Labirinto extends JogoDeArena {

    /** O verde-água do jogo: nome, número e destaque. */
    private static final String COR = "#4FD1C5";

    /** O cinza claro do texto corrido. */
    private static final String TINTA = "#C9D1D9";

    /** O cinza escuro do que é só contexto. */
    private static final String SOMBRA = "#6E7681";

    private final Map<UUID, Material> corDoGiz = new HashMap<>();
    private final Map<UUID, Integer> melhorDe = new HashMap<>();
    private final Set<Long> gizNoChao = new HashSet<>();

    private UUID campeao;
    private int tiquesDoCampeao;
    private double maiorAfastamento;
    private double alcanceDoLabirinto;

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
     * Sorteia um labirinto novo e o levanta, antes de cada rodada.
     *
     * A ordem é essa e não pode ser outra: sortear PRIMEIRO, construir depois.
     * Invertido, a obra levantaria o labirinto da rodada anterior e o sorteio
     * novo só valeria para o anúncio — o jogo diria "SALÕES de vinte e uma
     * células" e os jogadores estariam dentro do GALHOS de nove.
     *
     * <p>O construtor só escreve bloco que mudou, mas agora quase tudo muda:
     * o tamanho muda, o traçado muda, a paleta muda. É o preço de não ter
     * labirinto decorado, e é um preço de uma vez por rodada.
     */
    @Override
    protected void prepararArena(World mundo) {
        corDoGiz.clear();
        melhorDe.clear();
        gizNoChao.clear();
        campeao = null;
        tiquesDoCampeao = 0;

        plugin.labirintoCatalog().sortearRodada();
        plugin.labirintoBuilder().refazerLabirinto(mundo);

        LabirintoCatalog planta = planta2();
        alcanceDoLabirinto = Math.max(1.0, planta.lado() / 2.0);
        maiorAfastamento = 0.0;
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

    /**
     * Três linhas na largada, e nenhuma delas diz onde é a porta.
     *
     * A primeira é o labirinto que saiu: traçado, tamanho e voltas. Não é
     * enfeite — é o que transforma dez rodadas jogadas em vantagem na décima
     * primeira. Ouvir "VENTANIA" e já saber que existe uma avenida reta a
     * procurar é a única habilidade que o sorteio não apaga.
     *
     * <p>A segunda é o que aquele traçado faz com quem anda dentro dele.
     *
     * <p>A terceira é o tamanho da caminhada, em esquinas. O LADO da porta
     * ficava aqui e saiu junto com o farol: ele respondia metade da pergunta
     * de graça. O número de esquinas diz se esta é uma corrida de trinta
     * segundos ou de três minutos, e isso muda como se corre sem dizer para
     * onde correr.
     */
    @Override
    protected void aoComecarRodada(World mundo) {
        LabirintoCatalog planta = planta2();
        anunciar("<color:" + COR + "><b>\u258c " + planta.nomeDoTracado()
                + "</b></color>  <color:" + TINTA + ">" + planta.celulas()
                + "\u00d7" + planta.celulas() + " c\u00e9lulas</color>"
                + "  <color:" + SOMBRA + ">\u00b7 " + planta.nomeDasVoltas()
                + "</color>");
        anunciar("<color:" + SOMBRA + ">   " + planta.caraterDoTracado()
                + "</color>");
        anunciar("<color:" + TINTA + ">   a porta est\u00e1 na borda e "
                + "<i>nada diz onde</i> \u2014 </color><color:" + COR + ">"
                + planta.passosAteASaida() + " esquinas</color><color:"
                + SOMBRA + "> pelo caminho mais curto</color>");
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
        if (alcanceDoLabirinto <= 0) {
            return 0f;
        }
        return ArenaComum.limite(
                (float) (maiorAfastamento / alcanceDoLabirinto));
    }

    @Override
    protected String textoDaBarra() {
        return "<color:" + COR + ">" + (int) Math.round(maiorAfastamento)
                + " blocos</color><color:" + SOMBRA + "> do meio \u00e9 o mais "
                + "longe que algu\u00e9m chegou \u00b7 </color><color:" + TINTA
                + ">" + ArenaComum.tempo(tiquesDeJogo()) + "</color>";
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
        int[] passos = planta.distanciasDaPorta();
        int meio = planta.meio();
        double longe = 0.0;

        for (Player jogador : jogadoresVivos()) {
            Location onde = jogador.getLocation();
            // Afastamento do MEIO, e não distância até a porta: a segunda
            // seria um medidor de quente-e-frio na barra de todo mundo.
            longe = Math.max(longe, Math.hypot(onde.getX(), onde.getZ()));

            int i = planta.celulaMaisProxima(onde.getBlockX() + meio);
            int j = planta.celulaMaisProxima(onde.getBlockZ() + meio);
            int quantos = passos[LabirintoCatalog.indice(planta.celulas(), i, j)];
            if (quantos >= 0) {
                melhorDe.merge(jogador.getUniqueId(), quantos, Math::min);
            }
        }
        maiorAfastamento = longe;
    }

    /** O primeiro a atravessar a porta fecha a partida. */
    private void atravessou(World mundo, Player jogador) {
        campeao = jogador.getUniqueId();
        tiquesDoCampeao = (int) tiquesDeJogo();
        melhorDe.put(campeao, 0);
        mundo.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                1.0f, 1.0f);
        anunciar("<color:" + COR + "><b>\u2726 "
                + HubBuilder.escapar(jogador.getName())
                + "</b></color><color:" + TINTA + "> achou a sa\u00edda em "
                + "</color><color:" + COR + ">"
                + ArenaComum.tempo(tiquesDoCampeao) + "</color>");
    }

    // ------------------------------------------------------------------
    //  O giz
    // ------------------------------------------------------------------

    private void darOGiz(Player jogador, Material cor, int quantos) {
        jogador.getInventory().clear();
        if (quantos <= 0) {
            return;
        }
        ItemStack giz = new ItemStack(cor, ArenaComum.cabemNaPilha(cor, quantos));
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
