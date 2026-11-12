package codes.biga.bigacore;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Fuga da Lava: a lava sobe sozinha, e quem fica abaixo dela derrete.
 *
 * <h2>Quem elimina é a ALTURA, não o bloco</h2>
 *
 * O jogo não espera o jogador tomar dano de lava. Compara a altura dele com a
 * superfície e elimina quem está abaixo. Três razões: dano de lava mata em
 * segundos, e segundos a mais de agonia num jogo de corrida é tempo em que o
 * jogador já perdeu mas ainda atrapalha; lava com dano exige armadura,
 * resistência e um monte de casos; e a altura é um número que o teste
 * confere sem servidor.
 *
 * <h2>A lava é um relógio, não um acúmulo</h2>
 *
 * A fiada da superfície sai de {@link LavaCatalog#nivelDaLava(int)}, que é
 * uma função dos segundos de rodada. O jogo só pergunta "em que fiada a lava
 * deveria estar agora?" e enche o que faltar. Se o servidor engasgar meio
 * segundo, a lava continua onde o relógio diz — e não meio segundo atrás,
 * acumulando atraso até a rodada virar outra coisa.
 *
 * <h2>A pedra rachada é o que faz "quem parar, derrete"</h2>
 *
 * Pisou, tem um segundo e meio antes de o bloco sumir. Parar em cima é cair,
 * e cair num poço é descer para onde a lava já passou. É a mesma ideia do
 * chão que some do TNT Run, posta num jogo em que descer custa a partida.
 */
public final class FugaDaLava extends JogoDeArena {

    /** A que distância da saída já conta como escapar. */
    private static final double RAIO_DA_SAIDA = 4.0;

    private final Map<Long, Long> fusoDaRachada = new HashMap<>();
    private final Map<UUID, Integer> maisAltoDe = new HashMap<>();

    private int nivelDaLava;
    private UUID campeao;
    private long tempoDoCampeao;

    public FugaDaLava(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.lavaCatalog();
    }

    private LavaCatalog planta2() {
        return plugin.lavaCatalog();
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
        LavaCatalog planta = planta2();
        plugin.lavaBuilder().refazerRampa(mundo, Math.max(planta.topoY(), nivelDaLava));
        fusoDaRachada.clear();
        maisAltoDe.clear();
        campeao = null;
        nivelDaLava = planta.topoY() - 1;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        LavaCatalog planta = planta2();
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            Location largada = pontoDaLargada(mundo, planta, k);
            jogador.teleport(largada);
            jogador.setFallDistance(0f);
            maisAltoDe.put(jogador.getUniqueId(), largada.getBlockY());
        }
    }

    /**
     * Onde cada um nasce: no pé da rampa, em fila dupla.
     *
     * No pé da rampa e não no chão do poço: o chão é a primeira coisa que a
     * lava toma, e um jogador que precisa correr até a rampa antes de começar
     * a subir já largou perdendo sem ter errado nada.
     */
    private Location pontoDaLargada(World mundo, LavaCatalog planta, int k) {
        List<double[]> helice = planta.helice();
        int passo = (int) Math.round((1 + (k % 8) * 1.1) / LavaCatalog.AMOSTRA);
        double[] ponto = helice.get(Math.min(passo, helice.size() - 1));
        double r = Math.hypot(ponto[0], ponto[2]);
        double off = k < 8 ? -0.8 : 0.8;

        Location onde = new Location(mundo,
                ponto[0] + ponto[0] / r * off + 0.5, ponto[1] + 1,
                ponto[2] + ponto[2] / r * off + 0.5);
        onde.setYaw(ArenaComum.yawDaDirecao(-ponto[2] / r, ponto[0] / r));
        onde.setPitch(0f);
        return onde;
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return LavaBuilder.pontoDaPlateia(mundo, planta2());
    }

    /** Derrete quem está abaixo da superfície da lava. */
    @Override
    protected boolean caiuFora(Player jogador) {
        return jogador.getLocation().getY() < nivelDaLava + 1;
    }

    @Override
    protected boolean rodadaAcabou() {
        if (campeao != null) {
            return true;
        }
        if (ArenaComum.rodadaAcabou(quantosVivos(), comecouCom(),
                planta2().treinoSozinho())) {
            return true;
        }
        if (nivelDaLava > planta2().cumeY()) {
            return true;
        }
        return segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String vencedor() {
        if (campeao != null) {
            Player quem = org.bukkit.Bukkit.getPlayer(campeao);
            return quem == null ? "alguém" : quem.getName();
        }
        List<Player> vivos = jogadoresVivos();
        if (vivos.size() == 1) {
            return vivos.get(0).getName();
        }
        // Ninguém escapou e sobrou mais de um: ganha o mais alto, se houver
        // um mais alto. Empate na mesma fiada não é vitória de ninguém.
        Player melhor = null;
        double altura = -1;
        boolean empate = false;
        for (Player jogador : vivos) {
            double y = jogador.getLocation().getY();
            if (y > altura + 0.5) {
                altura = y;
                melhor = jogador;
                empate = false;
            } else if (Math.abs(y - altura) <= 0.5) {
                empate = true;
            }
        }
        return empate || melhor == null ? null : melhor.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        if (campeao == null) {
            return "o mais alto quando a lava chegou";
        }
        return "escapou em " + ArenaComum.tempo(tempoDoCampeao);
    }

    @Override
    protected String textoDaBarra() {
        LavaCatalog planta = planta2();
        int faltam = Math.max(0, planta.cumeY() - nivelDaLava);
        return "<white>lava a " + faltam + " fiadas do topo</white> <gray>· "
                + quantosVivos() + " de pé · " + ArenaComum.tempo(tiquesDeJogo())
                + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço do jogo
    // ------------------------------------------------------------------

    /**
     * A cada segundo: sobe a lava até onde o relógio manda.
     *
     * Enche de uma vez tudo o que faltou. Numa rodada sem engasgo isso é uma
     * fiada; numa com engasgo são duas, e é exatamente o que tem de acontecer
     * — a alternativa é a lava ficar devendo fiadas e o jogo ficar mais fácil
     * justamente quando o servidor está pior.
     */
    @Override
    protected void aCadaSegundo(World mundo) {
        LavaCatalog planta = planta2();
        int alvo = Math.min(planta.cumeY() + 1, planta.nivelDaLava(segundosDeJogo()));
        if (alvo <= nivelDaLava) {
            return;
        }

        plugin.lavaBuilder().subirLava(mundo, nivelDaLava + 1, alvo);
        nivelDaLava = alvo;

        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_LAVA_AMBIENT,
                    0.35f, 0.7f);
        }
        for (Player jogador : jogadoresVivos()) {
            int folga = (int) (jogador.getLocation().getY() - nivelDaLava);
            if (folga <= 4) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:#FF7A3D>a lava está a " + Math.max(0, folga)
                                + " fiadas de você</color>"));
                jogador.playSound(jogador.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.6f);
            }
        }
    }

    @Override
    protected void aCadaTique(World mundo) {
        LavaCatalog planta = planta2();
        Map<Long, Integer> celulas = planta.celulas();
        Set<Material> cedem = planta.materiaisQueCedem();
        Location saida = LavaBuilder.pontoDaSaida(mundo, planta);

        for (Player jogador : jogadoresVivos()) {
            Location onde = jogador.getLocation();
            UUID id = jogador.getUniqueId();

            int altura = onde.getBlockY();
            if (altura > maisAltoDe.getOrDefault(id, 0)) {
                maisAltoDe.put(id, altura);
            }

            if (campeao == null && onde.getY() >= planta.cumeY()
                    && onde.distance(saida) <= RAIO_DA_SAIDA) {
                escapar(mundo, jogador);
                continue;
            }

            if (!jogador.isOnGround()) {
                continue;
            }
            long sob = ArenaComum.chave(onde.getBlockX(), onde.getBlockY() - 1,
                    onde.getBlockZ());
            Integer tipo = celulas.get(sob);
            if (tipo != null && tipo == LavaCatalog.RACHADA
                    && !fusoDaRachada.containsKey(sob)) {
                acenderPavio(mundo, jogador, sob, planta);
            }
        }

        cobrarPavios(mundo, cedem);
    }

    /** Pisou na rachada: começa a contagem e avisa, com som e poeira. */
    private void acenderPavio(World mundo, Player jogador, long onde,
                              LavaCatalog planta) {
        fusoDaRachada.put(onde, tiquesDeJogo() + planta.rachadaTiques());
        int x = LavaBuilder.desempacotar(onde, 40);
        int y = LavaBuilder.desempacotar(onde, 20);
        int z = LavaBuilder.desempacotar(onde, 0);
        mundo.playSound(new Location(mundo, x, y, z), Sound.BLOCK_STONE_HIT,
                0.8f, 0.7f);
        mundo.spawnParticle(Particle.SMOKE, x + 0.5, y + 1.05, z + 0.5,
                8, 0.3, 0.05, 0.3, 0.01);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF7A3D>a pedra rachou</color> <gray>— não pare</gray>"));
    }

    /** Apaga as rachadas cujo tempo venceu. */
    private void cobrarPavios(World mundo, Set<Material> cedem) {
        long agora = tiquesDeJogo();
        Iterator<Map.Entry<Long, Long>> passo = fusoDaRachada.entrySet().iterator();
        while (passo.hasNext()) {
            Map.Entry<Long, Long> pavio = passo.next();
            if (pavio.getValue() > agora) {
                continue;
            }
            passo.remove();
            int x = LavaBuilder.desempacotar(pavio.getKey(), 40);
            int y = LavaBuilder.desempacotar(pavio.getKey(), 20);
            int z = LavaBuilder.desempacotar(pavio.getKey(), 0);
            Block bloco = mundo.getBlockAt(x, y, z);
            // Só apaga o que é pedra rachada. Sem esta trava, um defeito de
            // uma linha na conta do bloco do pé passa a comer a rampa.
            if (!cedem.contains(bloco.getType())) {
                continue;
            }
            bloco.setType(Material.AIR, false);
            mundo.spawnParticle(Particle.BLOCK, x + 0.5, y + 0.5, z + 0.5,
                    20, 0.3, 0.3, 0.3, 0.01, bloco.getBlockData());
            mundo.playSound(new Location(mundo, x, y, z), Sound.BLOCK_STONE_BREAK,
                    0.7f, 0.9f);
        }
    }

    /** Chegou no terraço de ouro: acabou a rodada. */
    private void escapar(World mundo, Player jogador) {
        campeao = jogador.getUniqueId();
        tempoDoCampeao = tiquesDeJogo();
        mundo.spawnParticle(Particle.TOTEM_OF_UNDYING,
                jogador.getLocation().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.3);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                0.9f, 1.0f);
        anunciar("<color:" + planta2().corDoTexto() + ">"
                + HubBuilder.escapar(jogador.getName()) + "</color> <gray>escapou do "
                + "poço em <white>" + ArenaComum.tempo(tiquesDeJogo())
                + "</white></gray>");
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        maisAltoDe.remove(id);
        if (id.equals(campeao)) {
            campeao = null;
        }
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        LavaCatalog planta = planta2();
        anunciar("<gray>a lava começa a subir em <white>" + planta.carenciaSegundos()
                + "s</white> e leva <white>"
                + LavaCatalog.segundosAte(planta.altura(), planta.segundosPorFiada(),
                        planta.aceleraACada())
                + "s</white> até o topo — são <white>" + planta.altura()
                + "</white> fiadas de rampa</gray>");
    }

    /** Quem está mais alto, para o anúncio do fim. */
    public List<Player> escalada() {
        List<Player> ordem = new ArrayList<>(jogadoresVivos());
        ordem.sort((a, b) -> Double.compare(b.getLocation().getY(),
                a.getLocation().getY()));
        return ordem;
    }
}
