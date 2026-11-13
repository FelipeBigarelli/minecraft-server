package codes.biga.bigacore;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.boat.AcaciaBoat;
import org.bukkit.entity.boat.BirchBoat;
import org.bukkit.entity.boat.CherryBoat;
import org.bukkit.entity.boat.DarkOakBoat;
import org.bukkit.entity.boat.JungleBoat;
import org.bukkit.entity.boat.MangroveBoat;
import org.bukkit.entity.boat.OakBoat;
import org.bukkit.entity.boat.PaleOakBoat;
import org.bukkit.entity.boat.SpruceBoat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

    /** A folga de altura em que um portão ainda conta, na pista gerada. */
    private static final int FAIXA_DE_ALTURA = 5;

    /**
     * Os barcos da grade, um tipo de madeira por lugar.
     *
     * <p>Nove madeiras e não um barco só: numa descida de montanha os barcos
     * ficam a trinta blocos uns dos outros e a única coisa que distingue o
     * segundo do terceiro é a cor da madeira. Com um tipo só, "quem está na
     * minha frente?" não tem resposta visual nenhuma.
     */
    private static final List<Class<? extends Boat>> MADEIRAS = List.of(
            OakBoat.class, SpruceBoat.class, BirchBoat.class, JungleBoat.class,
            AcaciaBoat.class, DarkOakBoat.class, CherryBoat.class,
            MangroveBoat.class, PaleOakBoat.class);

    private final Map<UUID, Integer> proximoPortao = new HashMap<>();
    private final Map<UUID, Integer> voltaDe = new HashMap<>();
    private final Map<UUID, Integer> ultimoPortao = new HashMap<>();
    private final Map<UUID, Integer> avancoDe = new HashMap<>();
    private final Map<UUID, Integer> gradeDe = new HashMap<>();
    private final Map<UUID, Integer> quedasDe = new HashMap<>();
    private final Map<UUID, double[]> ondeEstava = new HashMap<>();

    /** O barco de cada um, pelo id da entidade. */
    private final Map<UUID, UUID> barcoDe = new HashMap<>();

    private final Random sorteio = new Random();

    /** Quantos largaram: é o que diz qual fila é a pole. */
    private int totalDaGrade = 2;

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
        // O sorteio ANTES da obra: é ele que diz qual percurso vai ser
        // decorado, e decorar primeiro seria pôr a largada na montanha
        // errada. Sem mapa pronto, sortearPercurso não faz nada.
        planta2().sortearPercurso(sorteio);
        plugin.corridaBuilder().refazerPista(mundo);
        tirarOsBarcos();
        proximoPortao.clear();
        voltaDe.clear();
        ultimoPortao.clear();
        avancoDe.clear();
        gradeDe.clear();
        quedasDe.clear();
        ondeEstava.clear();
        barcoDe.clear();
        campeao = null;
        quantosTerminaram = 0;
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        CorridaCatalog planta = planta2();
        totalDaGrade = Math.max(1, entram.size());
        for (int k = 0; k < entram.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            Location grade = CorridaBuilder.pontoDaGrade(mundo, planta, k,
                    totalDaGrade);
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
        acenderALargada(mundo, 1, false);
    }

    // ------------------------------------------------------------------
    //  Os barcos
    // ------------------------------------------------------------------

    /**
     * A árvore de largada, acesa no preparo e apagada na largada.
     *
     * <p>Uma luz por segundo, e todas apagam de uma vez quando a rodada abre
     * — que é a árvore de verdade. Quem está num barco olhando montanha
     * abaixo não tira os olhos da pista para ler um número no canto da tela;
     * cinco luzes vermelhas sumindo ele vê sem olhar para nada.
     */
    @Override
    protected void aCadaSegundoDePreparo(World mundo, int falta) {
        CorridaCatalog planta = planta2();
        int total = Math.max(1, planta.preparoSegundos());
        acenderALargada(mundo, total - falta + 1, false);
    }

    private void acenderALargada(World mundo, int acesas, boolean verde) {
        CorridaCatalog planta = planta2();
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso == null) {
            return;
        }
        plugin.corridaBuilder().luzesDaLargada(mundo, planta, curso, acesas, verde);
    }

    /**
     * Põe o jogador num barco, e é por isso que a corrida é de barco.
     *
     * <p>O barco nasce na LARGADA e não no preparo: o gelo da estrada é
     * escorregadio de verdade, e um barco parado cinco segundos numa rampa
     * desce sozinho antes de a rodada abrir. O jogador espera de pé — que o
     * arcabouço sabe congelar — e o barco aparece debaixo dele quando as
     * luzes apagam.
     */
    private void embarcar(World mundo, Player jogador, int lugar) {
        UUID id = jogador.getUniqueId();
        desembarcar(id);
        Location onde = jogador.getLocation();
        Boat barco = mundo.spawn(onde, MADEIRAS.get(
                Math.floorMod(lugar, MADEIRAS.size())));
        barco.setInvulnerable(true);
        barco.setPersistent(false);
        barco.addPassenger(jogador);
        barcoDe.put(id, barco.getUniqueId());
    }

    private void desembarcar(UUID id) {
        UUID qual = barcoDe.remove(id);
        if (qual == null) {
            return;
        }
        Entity barco = org.bukkit.Bukkit.getEntity(qual);
        if (barco != null) {
            barco.remove();
        }
    }

    /** Tira todos os barcos: no fim da rodada e antes de montar a próxima. */
    private void tirarOsBarcos() {
        for (UUID id : new ArrayList<>(barcoDe.keySet())) {
            desembarcar(id);
        }
        barcoDe.clear();
    }

    /**
     * Ninguém desce do barco no meio da corrida.
     *
     * <p>Sem isto, apertar agachar na primeira curva vira a corrida a pé —
     * que no gelo é mais lenta mas também mais controlável, e transformaria o
     * barco em desvantagem. Fora da rodada o evento passa: é o barco sendo
     * recolhido.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoSairDoBarco(VehicleExitEvent evento) {
        if (!jogando() || !(evento.getExited() instanceof Player jogador)) {
            return;
        }
        UUID qual = barcoDe.get(jogador.getUniqueId());
        if (qual != null && qual.equals(evento.getVehicle().getUniqueId())) {
            evento.setCancelled(true);
        }
    }

    /** E ninguém quebra o barco do vizinho a remada. */
    @EventHandler(ignoreCancelled = true)
    public void aoQuebrarOBarco(VehicleDestroyEvent evento) {
        if (barcoDe.containsValue(evento.getVehicle().getUniqueId())) {
            evento.setCancelled(true);
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
        CorridaCatalog.Percurso curso = planta2().percursoDaRodada();
        if (curso != null) {
            return curso.nome() + " em " + ArenaComum.tempo(tempoDoCampeao)
                    + " · " + quedasDe.getOrDefault(campeao, 0) + " queda(s)";
        }
        return planta2().voltas() + " voltas em " + ArenaComum.tempo(tempoDoCampeao)
                + " · " + quedasDe.getOrDefault(campeao, 0) + " queda(s)";
    }

    @Override
    protected String textoDaBarra() {
        CorridaCatalog.Percurso curso = planta2().percursoDaRodada();
        if (curso != null) {
            int lider = 0;
            for (Map.Entry<UUID, Integer> avanco : avancoDe.entrySet()) {
                lider = Math.max(lider, avanco.getValue());
            }
            int quantos = curso.quantosPortoes();
            return "<white>" + HubBuilder.escapar(curso.nome()) + "</white> "
                    + "<gray>· portão " + Math.min(lider, quantos) + "/" + quantos
                    + " · " + quantosVivos() + " na pista · "
                    + ArenaComum.tempo(tiquesDeJogo()) + "</gray>";
        }
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
        boolean comMapa = planta.percursoDaRodada() != null;

        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            Location onde = jogador.getLocation();

            // A altura é medida contra o PORTÃO que se está buscando, e não
            // contra uma fiada só. Numa descida de cento e trinta fiadas, uma
            // altura única para tudo ou perdoa quem caiu no alto ou acusa
            // queda em quem está correndo certo lá embaixo.
            int alvo = Math.floorMod(proximoPortao.getOrDefault(id, 0), quantos);
            int chaoY = comMapa ? planta.alturaDoPortao(alvo) : planta.topoY();
            int piso = comMapa ? planta.quedaDoPortao(alvo) : planta.quedaY();
            int faixa = comMapa ? planta.faixaDeAltura() : FAIXA_DE_ALTURA;

            if (onde.getY() < piso) {
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
            if (onde.getY() < chaoY - faixa || onde.getY() > chaoY + faixa) {
                continue;
            }

            // Laço e não "um por tique": dois portões podem cair no mesmo
            // segmento num tique de lag, e atender só o primeiro deixaria o
            // segundo para trás — e para trás é para sempre.
            boolean cruzou = true;
            while (cruzou) {
                cruzou = false;
                int qual = Math.floorMod(proximoPortao.getOrDefault(id, 0), quantos);
                for (PistaFechada.Portao portao : planos.get(qual)) {
                    if (portao.cruzouParaFrente(ax, az, onde.getX(), onde.getZ())) {
                        registrarPortao(mundo, jogador, planta, qual, quantos);
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

        // Num percurso ABERTO a chegada é o ÚLTIMO portão, e não a largada.
        // A pista gerada é um anel e fecha a volta na largada; a descida da
        // montanha não fecha, e esperar que ela feche seria esperar para
        // sempre — a corrida acabaria só no tempo limite.
        if (planta.percursoDaRodada() != null) {
            if (portao == quantos - 1) {
                cruzarAChegada(mundo, jogador, planta);
                return;
            }
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                    0.5f, 1.8f);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>portão <white>" + (portao + 1) + "/" + quantos
                            + "</white> · " + posicaoEscrita(jogador) + "</gray>"));
            return;
        }

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
        desembarcar(id);
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
        // O barco não acompanha o teleporte do passageiro: quem cai volta ao
        // portão a pé e termina a descida andando no gelo, que é outro jogo.
        if (planta.percursoDaRodada() != null) {
            embarcar(mundo, jogador, gradeDe.getOrDefault(id, 0));
        }
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
        List<Player> ordem = new ArrayList<>(jogadoresVivos());
        ordem.sort((a, b) -> {
            int avancoA = avancoDe.getOrDefault(a.getUniqueId(), 0);
            int avancoB = avancoDe.getOrDefault(b.getUniqueId(), 0);
            if (avancoA != avancoB) {
                return Integer.compare(avancoB, avancoA);
            }
            return Double.compare(faltaPara(a, planta), faltaPara(b, planta));
        });
        return ordem;
    }

    private double faltaPara(Player jogador, CorridaCatalog planta) {
        Location onde = jogador.getLocation();
        return planta.distanciaAoPortao(
                proximoPortao.getOrDefault(jogador.getUniqueId(), 0),
                onde.getX(), onde.getZ());
    }

    private String posicaoEscrita(Player jogador) {
        List<Player> ordem = classificacao();
        int lugar = ordem.indexOf(jogador) + 1 + quantosTerminaram;
        int total = ordem.size() + quantosTerminaram;
        return lugar + "º de " + total;
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        desembarcar(id);
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
        tirarOsBarcos();
        for (Player jogador : jogadoresDaArena()) {
            jogador.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    /** Os parágrafos que o saguão escreve nas paredes da rua. */
    @Override
    public java.util.List<String> instrucoes() {
        java.util.List<String> linhas = new java.util.ArrayList<>();
        CorridaCatalog planta = planta2();
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso != null) {
            linhas.add("<gray>descida de barco pela montanha: <white>"
                    + Math.round(curso.comprimento()) + "</white> blocos e <white>"
                    + curso.quantosPortoes() + "</white> portões, de <white>y "
                    + curso.alturaDaLargada() + "</white> a <white>y "
                    + curso.alturaDoPortao(curso.quantosPortoes() - 1)
                    + "</white></gray>");
            linhas.add("<gray>o trecho muda a cada rodada — são <white>"
                    + planta.arquivosDoRodizio().size() + "</white> no rodízio — e "
                    + "quem cai volta ao último portão, de barco novo</gray>");
            return linhas;
        }
        linhas.add("<gray>são <white>" + planta.voltas() + "</white> voltas de <white>"
                + planta.quantosBlocosDePista() + "</white> blocos — as <white>"
                + planta.atalhos().length + "</white> pontes de ouro cortam caminho "
                + "sobre o vazio</gray>");
        return linhas;
    }

    /**
     * A largada: as luzes apagam e os barcos aparecem debaixo de todos.
     *
     * <p>Nesta ordem, e no mesmo tique: a luz verde sem barco é largada sem
     * corrida, e o barco antes da luz é um barco escorregando rampa abaixo
     * enquanto o jogo ainda conta.
     */
    @Override
    protected void aoComecarRodada(World mundo) {
        CorridaCatalog planta = planta2();
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso == null) {
            return;
        }
        acenderALargada(mundo, 0, true);
        for (Player jogador : jogadoresVivos()) {
            embarcar(mundo, jogador,
                    gradeDe.getOrDefault(jogador.getUniqueId(), 0));
        }
        anunciar("<color:" + planta.corDoTexto() + ">" + curso.nome()
                + "</color> <gray>— " + Math.round(curso.comprimento())
                + " blocos de descida, " + curso.quantosPortoes()
                + " portões</gray>");
    }
}
