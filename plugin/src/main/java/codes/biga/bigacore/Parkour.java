package codes.biga.bigacore;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O Parkour: suba a árvore saltando, e o primeiro no topo ganha.
 *
 * <h2>Aqui ninguém é eliminado — e isso muda o jogo inteiro</h2>
 *
 * Em todos os outros jogos da lista, cair tira o jogador da partida. Aqui cair
 * custa TEMPO: volta ao último checkpoint e segue tentando. A diferença não é
 * de gosto, é o que faz o parkour ser jogável por quem está aprendendo —
 * eliminado no terceiro salto, ninguém chega ao décimo nunca, e um percurso de
 * trinta e dois saltos jamais seria visto por inteiro.
 *
 * <p>Por isso {@link #caiuFora} devolve sempre falso: a máquina comum
 * elimina quem cai abaixo da linha, e este é o jogo em que ela não deve.
 *
 * <h2>Como o jogo sabe onde o jogador está</h2>
 *
 * No começo da rodada, as plataformas viram um mapa de bloco para número do
 * salto. A cada tique, o bloco debaixo do pé é consultado nesse mapa. É mais
 * barato que medir distância a trinta e duas plataformas por jogador por
 * tique, e é exato — nada de "perto o bastante" no meio do caminho.
 */
public final class Parkour extends JogoDeArena {

    /** Quanto o jogador pode cair abaixo do checkpoint antes de voltar. */
    private static final int QUEDA_DO_CHECKPOINT = 5;

    private final Map<UUID, Integer> alcancou = new HashMap<>();
    private final Map<UUID, Integer> checkpointDe = new HashMap<>();
    private final Map<UUID, Integer> quedasDe = new HashMap<>();
    private final Map<Long, Integer> saltoDoBloco = new HashMap<>();

    private UUID campeao;
    private long tempoDoCampeao;

    public Parkour(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.parkourCatalog();
    }

    private ParkourCatalog planta2() {
        return plugin.parkourCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "SUBA!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        plugin.parkourBuilder().refazerCaminho(mundo);
        alcancou.clear();
        checkpointDe.clear();
        quedasDe.clear();
        campeao = null;
        indexar(planta2());
    }

    /**
     * Guarda cada bloco de plataforma no mapa, com o número do salto.
     *
     * A chave é a coordenada empacotada num long: mapa de três inteiros por
     * jogador por tique seria alocação à toa trinta vezes por segundo.
     */
    private void indexar(ParkourCatalog planta) {
        saltoDoBloco.clear();
        List<int[]> pontos = planta.percurso();
        for (int i = 0; i < pontos.size(); i++) {
            int[] p = pontos.get(i);
            int raio = switch (p[3]) {
                case ParkourCatalog.LARGADA, ParkourCatalog.CHEGADA -> 2;
                case ParkourCatalog.CHECKPOINT -> 1;
                default -> 0;
            };
            for (int ox = -raio; ox <= raio; ox++) {
                for (int oz = -raio; oz <= raio; oz++) {
                    saltoDoBloco.put(chave(p[0] + ox, p[1], p[2] + oz), i);
                }
            }
        }
    }

    static long chave(int x, int y, int z) {
        return ArenaComum.chave(x, y, z);
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        ParkourCatalog planta = planta2();
        Location largada = ParkourBuilder.pontoDaLargada(mundo, planta);
        for (Player jogador : entram) {
            jogador.teleport(largada);
            alcancou.put(jogador.getUniqueId(), 0);
            checkpointDe.put(jogador.getUniqueId(), 0);
            quedasDe.put(jogador.getUniqueId(), 0);
        }
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return ParkourBuilder.pontoDaPlateia(mundo, planta2());
    }

    /** Cair não elimina: é este jogo inteiro numa linha. */
    @Override
    protected boolean caiuFora(Player jogador) {
        return false;
    }

    @Override
    protected boolean rodadaAcabou() {
        if (campeao != null) {
            return true;
        }
        if (jogadoresVivos().isEmpty()) {
            return true;
        }
        return segundosDeJogo() >= planta2().tempoLimite();
    }

    /**
     * O vencedor: quem chegou. Se o tempo acabou antes, quem está mais acima.
     *
     * Empate em número de saltos não dá vencedor — dois jogadores no mesmo
     * salto não foram decididos pelo jogo, e inventar um critério aqui
     * (quem entrou na fila primeiro?) premiaria o que não é o jogo.
     */
    @Override
    protected String vencedor() {
        if (campeao != null) {
            Player quem = org.bukkit.Bukkit.getPlayer(campeao);
            return quem == null ? "alguém" : quem.getName();
        }
        int melhor = -1;
        List<Player> lideres = new ArrayList<>();
        for (Player jogador : jogadoresVivos()) {
            int onde = alcancou.getOrDefault(jogador.getUniqueId(), 0);
            if (onde > melhor) {
                melhor = onde;
                lideres.clear();
                lideres.add(jogador);
            } else if (onde == melhor) {
                lideres.add(jogador);
            }
        }
        return melhor > 0 && lideres.size() == 1 ? lideres.get(0).getName() : null;
    }

    @Override
    protected String legendaDaVitoria() {
        if (campeao == null) {
            return "mais alto quando o tempo acabou";
        }
        return "no topo em " + ArenaComum.tempo(tempoDoCampeao) + " · "
                + quedasDe.getOrDefault(campeao, 0) + " queda(s)";
    }

    @Override
    protected String textoDaBarra() {
        int melhor = 0;
        for (int onde : alcancou.values()) {
            melhor = Math.max(melhor, onde);
        }
        return "<white>salto " + melhor + "/" + planta2().saltos() + "</white> <gray>· "
                + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço do jogo
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        ParkourCatalog planta = planta2();
        List<int[]> pontos = planta.percurso();

        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            Location onde = jogador.getLocation();

            int indiceDoCheckpoint = checkpointDe.getOrDefault(id, 0);
            int alturaDoCheckpoint = pontos.get(indiceDoCheckpoint)[1];
            if (onde.getY() < alturaDoCheckpoint - QUEDA_DO_CHECKPOINT) {
                devolverAoCheckpoint(mundo, jogador, pontos.get(indiceDoCheckpoint));
                continue;
            }

            if (!jogador.isOnGround()) {
                continue;
            }
            Integer salto = saltoDoBloco.get(chave(onde.getBlockX(),
                    onde.getBlockY() - 1, onde.getBlockZ()));
            if (salto == null) {
                continue;
            }

            int antes = alcancou.getOrDefault(id, 0);
            if (salto > antes) {
                alcancou.put(id, salto);
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:" + planta.corDoTexto() + ">salto " + salto + "/"
                                + planta.saltos() + "</color>"));
            }

            int[] plataforma = pontos.get(salto);
            if (plataforma[3] == ParkourCatalog.CHECKPOINT
                    && salto > indiceDoCheckpoint) {
                checkpointDe.put(id, salto);
                jogador.playSound(onde, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:#9BE85B>checkpoint <white>" + salto + "</white></color>"));
                mundo.spawnParticle(Particle.HAPPY_VILLAGER, onde.clone().add(0, 1, 0),
                        12, 0.4, 0.5, 0.4, 0.01);
            }

            if (plataforma[3] == ParkourCatalog.CHEGADA && campeao == null) {
                campeao = id;
                tempoDoCampeao = tiquesDeJogo();
                mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, onde.clone().add(0, 1, 0),
                        80, 0.6, 1.0, 0.6, 0.3);
            }
        }
    }

    /**
     * Devolve ao checkpoint, contando a queda.
     *
     * Contar a queda não é para humilhar: é o desempate honesto entre duas
     * subidas do mesmo tempo, e é a única estatística que diz se o percurso
     * está duro demais — vinte quedas no mesmo salto, por todo mundo, é
     * defeito de projeto e não de jogador.
     */
    private void devolverAoCheckpoint(World mundo, Player jogador, int[] checkpoint) {
        jogador.setFallDistance(0f);
        jogador.teleport(ParkourBuilder.pontoDoCheckpoint(mundo, checkpoint));
        jogador.playSound(jogador.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f);
        int quedas = quedasDe.merge(jogador.getUniqueId(), 1, Integer::sum);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>caiu</color> <gray>· " + quedas + " queda(s) · volta ao "
                        + "checkpoint</gray>"));
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        alcancou.remove(id);
        checkpointDe.remove(id);
        quedasDe.remove(id);
        if (id.equals(campeao)) {
            campeao = null;
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        anunciar("<gray>são <white>" + planta2().saltos() + "</white> saltos e "
                + "<white>" + (planta2().saltos() / planta2().checkpointACada())
                + "</white> checkpoints — cair não elimina</gray>");
    }
}
