package codes.biga.bigacore;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Corrida: duas voltas no autódromo, e o primeiro a cruzar leva.
 *
 * <h2>O portão é testado pelo SEGMENTO, nunca pela posição</h2>
 *
 * Com Velocidade II se anda perto de meio bloco por tique. Um portão
 * conferido por "o jogador está aqui?" é um portão que o jogador rápido
 * atravessa sem nunca estar nele em tique nenhum — e portão perdido não dá
 * erro: trava a volta para sempre, e o jogador fica dando voltas sem o
 * contador mexer. Por isso o jogo guarda a posição do tique anterior e
 * pergunta se o CAMINHO andado cruzou o plano.
 *
 * <h2>E só conta no sentido da corrida</h2>
 *
 * A normal do plano é a direção de quem corre. Cruzar de ré não conta, senão
 * bastaria andar para trás e para frente em cima de um portão.
 *
 * <h2>Cair não elimina — e é isso que faz o atalho ser uma escolha</h2>
 *
 * As três pontes de ouro cortam a ponta dos bojos e economizam um sexto da
 * volta, sobre o vazio e sem muro. Se cair eliminasse, ninguém sensato
 * tentaria e o atalho seria enfeite. Caindo, volta-se ao último portão — o
 * que custa mais tempo do que o atalho economiza. Aí a ponte passa a ser
 * aposta, que é o que "atalho é por sua conta" quer dizer.
 */
public final class Corrida extends JogoDeArena {

    /** A folga de altura em que um portão ainda conta. */
    private static final int FAIXA_DE_ALTURA = 5;

    private final Map<UUID, Integer> proximoPortao = new HashMap<>();
    private final Map<UUID, Integer> voltaDe = new HashMap<>();
    private final Map<UUID, Integer> ultimoPortao = new HashMap<>();
    private final Map<UUID, Integer> avancoDe = new HashMap<>();
    private final Map<UUID, Integer> gradeDe = new HashMap<>();
    private final Map<UUID, Integer> quedasDe = new HashMap<>();
    private final Map<UUID, double[]> ondeEstava = new HashMap<>();

    private UUID campeao;
    private long tempoDoCampeao;
    private int quantosTerminaram;

    public Corrida(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.corridaCatalog();
    }

    private CorridaCatalog planta2() {
        return plugin.corridaCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "VALENDO!";
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    @Override
    protected void prepararArena(World mundo) {
        plugin.corridaBuilder().refazerPista(mundo);
        proximoPortao.clear();
        voltaDe.clear();
        ultimoPortao.clear();
        avancoDe.clear();
        gradeDe.clear();
        quedasDe.clear();
        ondeEstava.clear();
        campeao = null;
        quantosTerminaram = 0;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        CorridaCatalog planta = planta2();
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            Location grade = CorridaBuilder.pontoDaGrade(mundo, planta, k);
            jogador.teleport(grade);
            gradeDe.put(id, k);
            proximoPortao.put(id, 0);
            voltaDe.put(id, 0);
            ultimoPortao.put(id, -1);
            avancoDe.put(id, 0);
            quedasDe.put(id, 0);
            ondeEstava.put(id, new double[]{grade.getX(), grade.getZ()});
            correr(jogador, planta);
        }
    }

    /**
     * Põe o jogador em condição de corrida: velocidade e barriga cheia.
     *
     * A barriga importa: sprint pede comida acima de seis, e uma corrida de
     * duas voltas gasta exatamente o bastante para o último trecho ser andado
     * em vez de corrido — o jogador sentiria o jogo "travando" sem motivo
     * visível. A máquina comum já cancela a perda de fome; aqui a barriga
     * começa cheia.
     */
    private void correr(Player jogador, CorridaCatalog planta) {
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
        jogador.setFallDistance(0f);
        if (planta.velocidade() > 0) {
            jogador.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION, planta.velocidade() - 1,
                    false, false, false));
        }
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return CorridaBuilder.pontoDaPlateia(mundo, planta2());
    }

    /** Cair não elimina: devolve ao último portão. */
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
     * O vencedor: quem cruzou. Se o tempo acabou antes, quem está na frente.
     *
     * Empate em avanço não dá vencedor: dois jogadores no mesmo portão não
     * foram decididos pela corrida, e desempatar aqui por quem entrou na fila
     * primeiro premiaria o que não é o jogo.
     */
    @Override
    protected String vencedor() {
        if (campeao != null) {
            Player quem = org.bukkit.Bukkit.getPlayer(campeao);
            return quem == null ? "alguém" : quem.getName();
        }
        List<Player> ordem = classificacao();
        if (ordem.isEmpty()) {
            return null;
        }
        if (ordem.size() > 1) {
            int primeiro = avancoDe.getOrDefault(ordem.get(0).getUniqueId(), 0);
            int segundo = avancoDe.getOrDefault(ordem.get(1).getUniqueId(), 0);
            if (primeiro == segundo) {
                return null;
            }
        }
        return avancoDe.getOrDefault(ordem.get(0).getUniqueId(), 0) > 0
                ? ordem.get(0).getName() : null;
    }

    @Override
    protected String legendaDaVitoria() {
        if (campeao == null) {
            return "na frente quando o tempo acabou";
        }
        return planta2().voltas() + " voltas em " + ArenaComum.tempo(tempoDoCampeao)
                + " · " + quedasDe.getOrDefault(campeao, 0) + " queda(s)";
    }

    @Override
    protected String textoDaBarra() {
        int liderVolta = 0;
        for (Map.Entry<UUID, Integer> volta : voltaDe.entrySet()) {
            liderVolta = Math.max(liderVolta, volta.getValue());
        }
        int voltas = planta2().voltas();
        return "<white>volta " + Math.min(Math.max(1, liderVolta), voltas) + "/" + voltas
                + "</white> <gray>· " + quantosVivos() + " na pista · "
                + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
    }

    // ------------------------------------------------------------------
    //  O laço do jogo
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        CorridaCatalog planta = planta2();
        List<List<PistaFechada.Portao>> planos = planta.planos();
        int quantos = planos.size();
        int chaoY = planta.topoY();

        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            Location onde = jogador.getLocation();

            if (onde.getY() < planta.quedaY()) {
                devolverAoPortao(mundo, jogador, planta);
                continue;
            }

            double[] antes = ondeEstava.get(id);
            if (antes == null) {
                ondeEstava.put(id, new double[]{onde.getX(), onde.getZ()});
                continue;
            }
            double ax = antes[0];
            double az = antes[1];
            antes[0] = onde.getX();
            antes[1] = onde.getZ();

            // Fora da faixa de altura da pista não se cruza portão nenhum:
            // quem está caindo passa por dentro do plano e não deve contar.
            if (onde.getY() < chaoY - 2 || onde.getY() > chaoY + FAIXA_DE_ALTURA) {
                continue;
            }

            // Laço e não "um por tique": dois portões podem cair no mesmo
            // segmento num tique de lag, e atender só o primeiro deixaria o
            // segundo para trás — e para trás é para sempre.
            boolean cruzou = true;
            while (cruzou) {
                cruzou = false;
                int alvo = Math.floorMod(proximoPortao.getOrDefault(id, 0), quantos);
                for (PistaFechada.Portao portao : planos.get(alvo)) {
                    if (portao.cruzouParaFrente(ax, az, onde.getX(), onde.getZ())) {
                        registrarPortao(mundo, jogador, planta, alvo, quantos);
                        cruzou = !terminou(id);
                        break;
                    }
                }
            }
        }
    }

    private boolean terminou(UUID id) {
        return !vivos().contains(id);
    }

    /** Conta o portão, fecha a volta se for a largada, e termina se foi a última. */
    private void registrarPortao(World mundo, Player jogador, CorridaCatalog planta,
                                 int portao, int quantos) {
        UUID id = jogador.getUniqueId();
        ultimoPortao.put(id, portao);
        proximoPortao.put(id, Math.floorMod(portao + 1, quantos));
        avancoDe.merge(id, 1, Integer::sum);

        if (portao != 0) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.5f, 1.8f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>portão <white>" + portao + "/" + (quantos - 1)
                            + "</white> · " + posicaoEscrita(jogador) + "</gray>"));
            return;
        }

        int volta = voltaDe.merge(id, 1, Integer::sum);
        if (volta > planta.voltas()) {
            cruzarAChegada(mundo, jogador, planta);
            return;
        }
        jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                0.7f, 1.2f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:" + planta.corDoTexto() + ">volta " + volta + "/"
                        + planta.voltas() + "</color> <gray>· "
                        + posicaoEscrita(jogador) + "</gray>"));
    }

    /**
     * A chegada: anota o tempo, tira da pista e anuncia.
     *
     * Tira da pista de propósito: quem terminou parado na reta final é um
     * obstáculo para quem ainda corre, e numa pista de sete de largura isso
     * decide a segunda posição.
     */
    private void cruzarAChegada(World mundo, Player jogador, CorridaCatalog planta) {
        UUID id = jogador.getUniqueId();
        quantosTerminaram++;
        int lugar = quantosTerminaram;
        String tempo = ArenaComum.tempo(tiquesDeJogo());

        if (campeao == null) {
            campeao = id;
            tempoDoCampeao = tiquesDeJogo();
        }

        vivos().remove(id);
        jogador.setGameMode(GameMode.SPECTATOR);
        jogador.teleport(CorridaBuilder.pontoDaPlateia(mundo, planta));
        mundo.spawnParticle(Particle.TOTEM_OF_UNDYING, jogador.getLocation(),
                60, 0.6, 1.0, 0.6, 0.3);
        jogador.playSound(jogador.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                0.8f, 1.0f);

        anunciar("<color:" + planta.corDoTexto() + ">"
                + HubBuilder.escapar(jogador.getName()) + "</color> <gray>cruzou em "
                + "<white>" + tempo + "</white> — " + lugar + "º</gray>");
    }

    /**
     * Devolve ao último portão, contando a queda.
     *
     * A posição anterior é reescrita no mesmo instante. Sem isso, o segmento
     * do tique seguinte iria do lugar da queda até o portão, atravessaria
     * meia pista e cruzaria portões que o jogador não correu.
     */
    private void devolverAoPortao(World mundo, Player jogador, CorridaCatalog planta) {
        UUID id = jogador.getUniqueId();
        int portao = ultimoPortao.getOrDefault(id, -1);
        Location volta = portao < 0
                ? CorridaBuilder.pontoDaGrade(mundo, planta, gradeDe.getOrDefault(id, 0))
                : CorridaBuilder.pontoDoPortao(mundo, planta, portao);

        jogador.setFallDistance(0f);
        jogador.teleport(volta);
        correr(jogador, planta);
        ondeEstava.put(id, new double[]{volta.getX(), volta.getZ()});

        int quedas = quedasDe.merge(id, 1, Integer::sum);
        jogador.playSound(volta, Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>caiu</color> <gray>· " + quedas + " queda(s) · volta ao "
                        + (portao < 0 ? "grid" : "portão " + portao) + "</gray>"));
    }

    // ------------------------------------------------------------------
    //  A classificação
    // ------------------------------------------------------------------

    /**
     * Quem está na frente, por avanço e depois por distância ao próximo portão.
     *
     * O desempate pela distância é o que faz a posição mexer DENTRO de um
     * trecho: sem ele, dez blocos de vantagem na reta não apareceriam até o
     * próximo portão, e o jogador leria "2º" enquanto lidera.
     */
    private List<Player> classificacao() {
        CorridaCatalog planta = planta2();
        List<double[]> eixo = planta.eixo();
        List<Player> ordem = new ArrayList<>(jogadoresVivos());
        ordem.sort((a, b) -> {
            int avancoA = avancoDe.getOrDefault(a.getUniqueId(), 0);
            int avancoB = avancoDe.getOrDefault(b.getUniqueId(), 0);
            if (avancoA != avancoB) {
                return Integer.compare(avancoB, avancoA);
            }
            return Double.compare(faltaPara(a, planta, eixo), faltaPara(b, planta, eixo));
        });
        return ordem;
    }

    private double faltaPara(Player jogador, CorridaCatalog planta,
                             List<double[]> eixo) {
        int alvo = planta.indiceDoPortao(
                proximoPortao.getOrDefault(jogador.getUniqueId(), 0));
        double[] p = eixo.get(alvo);
        Location onde = jogador.getLocation();
        return Math.hypot(p[0] - onde.getX(), p[1] - onde.getZ());
    }

    private String posicaoEscrita(Player jogador) {
        List<Player> ordem = classificacao();
        int lugar = ordem.indexOf(jogador) + 1 + quantosTerminaram;
        int total = ordem.size() + quantosTerminaram;
        return lugar + "º de " + total;
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        proximoPortao.remove(id);
        voltaDe.remove(id);
        ultimoPortao.remove(id);
        avancoDe.remove(id);
        gradeDe.remove(id);
        quedasDe.remove(id);
        ondeEstava.remove(id);
        if (id.equals(campeao)) {
            campeao = null;
        }
    }

    @Override
    protected void aoTerminarRodada() {
        for (Player jogador : jogadoresDaArena()) {
            jogador.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    @Override
    protected void instrucoes(Player jogador) {
        CorridaCatalog planta = planta2();
        dizer(jogador, "<gray>são <white>" + planta.voltas() + "</white> voltas de <white>"
                + planta.quantosBlocosDePista() + "</white> blocos — as <white>"
                + planta.atalhos().length + "</white> pontes de ouro cortam caminho "
                + "sobre o vazio</gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        CorridaCatalog planta = planta2();
    }
}
