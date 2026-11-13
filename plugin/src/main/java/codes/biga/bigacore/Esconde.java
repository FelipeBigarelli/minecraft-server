package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O ESCONDE: vire um caixote.
 *
 * <h2>O disfarce é uma ENTIDADE do servidor, e não uma mentira ao cliente</h2>
 *
 * Quem se esconde fica invisível de verdade ({@link Entity#setInvisible}) e
 * ganha um {@link BlockDisplay} de caixote que segue o corpo dele. O jeito
 * clássico de fazer isto — e o que a referência do gênero faz — é mandar ao
 * cliente de cada jogador um bloco falso na posição do escondido, com
 * {@code sendBlockChange}. Isso parece mais simples e sai caro de três jeitos:
 * o bloco falso não existe para o servidor, então atravessa-se ele e ele não
 * pode ser acertado; ele sobrevive à rodada no cliente até o pedaço do mundo
 * recarregar; e o conjunto todo precisa de dois plugins externos para
 * funcionar. Com uma entidade, o disfarce é do servidor: ele anda com
 * interpolação, morre quando a rodada morre, e o que o caçador acerta é o
 * jogador que está ali.
 *
 * <h2>O que delata, e por que é só isso</h2>
 *
 * Nada de partícula, brilho ou nome em cima de quem se esconde. O que delata
 * é a POSIÇÃO do caixote: o de enfeite está no canto exato de um bloco da
 * grade, e o de um jogador está onde o jogador está. Parado por um segundo, o
 * jogador ENCAIXA — é empurrado para o centro do bloco e o caixote dele passa
 * a ser indistinguível. Andando, fica torto e deslizando.
 *
 * <p>Um rastro de partícula seria mais fácil de programar e seria outro jogo:
 * entregaria o escondido para quem NÃO está olhando. Esconde-esconde é sobre
 * quem olha.
 *
 * <h2>Só se encaixa onde cabe um caixote</h2>
 *
 * {@link EscondeCatalog#naGrade} é a condição, e ela não é capricho: a grade
 * do chão é de quatro em quatro, e um caixote fora dela está denunciado pelo
 * alinhamento. Sem essa regra, quem jogasse duas vezes acharia todo mundo sem
 * olhar para nada além do alinhamento, e o disfarce não seria disfarce.
 *
 * <h2>Os dois papéis valem o mesmo</h2>
 *
 * Um ponto por segundo de pé para quem se esconde; para quem caça, cada
 * captura vale o que vale sobreviver a partida inteira dividido pelos
 * escondidos por caçador. A conta está em
 * {@link EscondeCatalog#pontosPorCaptura} e o motivo é que sem ela o sorteio
 * do papel decide a partida — e ninguém reclamaria, porque ninguém saberia.
 */
public final class Esconde extends JogoDeArena {

    /** O time que esconde o nome de quem está disfarçado. */
    private static final String TIME = "bigacore_esconde";

    /** A etiqueta dos disfarces, para nenhum sobreviver à rodada. */
    private static final String TAG_DISFARCE = "bigacore_esconde_disfarce";

    /** Quanto o jogador pode ter se mexido e ainda contar como parado. */
    private static final double PARADO = 0.02;

    /** Em quantos tiques o caixote desliza até a posição nova. */
    private static final int DESLIZE = 2;

    private final Placar placar = new Placar();

    private final Set<UUID> cacadores = new LinkedHashSet<>();
    private final Map<UUID, BlockDisplay> disfarce = new HashMap<>();
    private final Map<UUID, Integer> capturas = new HashMap<>();
    private final Map<UUID, double[]> ultimoLugar = new HashMap<>();
    private final Map<UUID, Long> paradoDesde = new HashMap<>();
    private final Set<UUID> encaixados = new HashSet<>();

    /** Os lugares da grade, por chave, para a conferência de encaixe. */
    private final Set<Long> grade = new HashSet<>();

    /** Os lugares que estão com caixote de enfeite agora. */
    private final List<int[]> caixotes = new ArrayList<>();

    private boolean cabineAberta;
    private int ondaFeita;
    private int valorDaCaptura;

    public Esconde(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.escondeCatalog();
    }

    private EscondeCatalog planta2() {
        return plugin.escondeCatalog();
    }

    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "ESCONDA-SE!";
    }

    /**
     * O PvP tem de estar ligado para o evento de bater em jogador acontecer.
     *
     * O dano continua cancelado como em toda arena daqui — é a batida que
     * interessa, e sem PvP ela não chega ao plugin. Mesmo motivo do bastão do
     * Rei da Montanha.
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
        cacadores.clear();
        capturas.clear();
        ultimoLugar.clear();
        paradoDesde.clear();
        encaixados.clear();
        caixotes.clear();
        cabineAberta = false;
        ondaFeita = 0;
        valorDaCaptura = 0;

        tirarTodosOsDisfarces(mundo);
        limparOTime();
        plugin.escondeBuilder().limparCaixotes(mundo, planta2());

        grade.clear();
        for (int[] onde : planta2().pontosDosCaixotes()) {
            grade.add(ArenaComum.chave(onde[0], 0, onde[1]));
        }
    }

    /**
     * Sorteia os papéis e esconde quem tem de se esconder.
     *
     * Com um jogador só, ele é ESCONDIDO e não caçador: o treino sozinho
     * serve para aprender a encaixar, e um caçador sozinho num galpão vazio
     * não tem o que aprender.
     */
    @Override
    protected void largar(World mundo, List<Player> entram) {
        EscondeCatalog planta = planta2();
        List<Player> sorteio = new ArrayList<>(entram);
        Collections.shuffle(sorteio);

        int quantosCacam = entram.size() <= 1 ? 0
                : planta.cacadores(entram.size());
        int quantosEscondem = entram.size() - quantosCacam;
        valorDaCaptura = EscondeCatalog.pontosPorCaptura(
                Math.max(1, quantosCacam), Math.max(1, quantosEscondem),
                planta.premioDeSobreviver());

        List<int[]> largadas = planta.largadas(Math.max(2, quantosEscondem));
        int naCabine = 0;
        int noGalpao = 0;
        for (Player jogador : sorteio) {
            UUID id = jogador.getUniqueId();
            jogador.getInventory().clear();
            jogador.setFallDistance(0f);
            placar.entrar(id);
            if (naCabine < quantosCacam) {
                cacadores.add(id);
                capturas.put(id, 0);
                jogador.teleport(EscondeBuilder.pontoDaCabine(mundo, planta,
                        naCabine, quantosCacam));
                naCabine++;
            } else {
                jogador.teleport(EscondeBuilder.pontoDaLargada(mundo, planta,
                        largadas.get(noGalpao % largadas.size())));
                esconder(jogador, planta);
                noGalpao++;
            }
        }

        espalharCaixotes(mundo, planta,
                planta.caixotesDaOnda(0, Math.max(1, quantosEscondem)));
    }

    @Override
    protected void instrucoes(Player jogador) {
        EscondeCatalog planta = planta2();
        dizer(jogador, "<gray>quem se esconde ganha <white>"
                + planta.pontosPorSegundo() + " ponto por segundo</white> de "
                + "pé; cada captura paga ao caçador o que o escondido "
                + "deixou de ganhar, e sobreviver até o fim vale <white>"
                + planta.premioDeSobreviver() + "</white>. Os caçadores saem "
                + "da cabine em <white>"
                + planta.segredoSegundos() + "s</white> — fique <white>parado "
                + "um segundo</white> num lugar de caixote para "
                + "<color:" + planta.corDoTexto() + ">ENCAIXAR</color></gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        EscondeCatalog planta = planta2();
        mostrarOPainel(mundo, planta);
        for (Player jogador : jogadoresDaArena()) {
            boolean caca = cacadores.contains(jogador.getUniqueId());
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize(caca
                            ? "<color:#FF8A8A>VOCÊ CAÇA</color>"
                            : "<color:" + planta.corDoTexto()
                                    + ">VOCÊ SE ESCONDE</color>"),
                    plugin.mini().deserialize(caca
                            ? "<gray>a cabine abre em "
                                    + planta.segredoSegundos() + "s</gray>"
                            : "<gray>você é um caixote — pare para "
                                    + "encaixar</gray>")));
        }
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return EscondeBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEliminarJogador(Player jogador) {
        desesconder(jogador);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        cacadores.remove(id);
        capturas.remove(id);
        ultimoLugar.remove(id);
        paradoDesde.remove(id);
        encaixados.remove(id);
        Player jogador = Bukkit.getPlayer(id);
        if (jogador != null) {
            desesconder(jogador);
        } else {
            BlockDisplay velho = disfarce.remove(id);
            if (velho != null) {
                velho.remove();
            }
        }
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            desesconder(jogador);
        }
        limparOTime();
        if (mundo != null) {
            tirarTodosOsDisfarces(mundo);
            plugin.escondeBuilder().limparCaixotes(mundo, planta2());
            ArenaComum.limparPaineis(mundo, EscondeBuilder.TAG_CACA);
        }
        caixotes.clear();
    }

    @Override
    protected boolean rodadaAcabou() {
        if (segundosDeJogo() >= planta2().tempoLimite()) {
            return true;
        }
        if (comecouCom() <= 1) {
            // Treino: só o relógio acaba com ele.
            return quantosVivos() == 0;
        }
        if (quantosEscondidos() == 0) {
            return true;
        }
        return quantosCacadores() == 0;
    }

    @Override
    protected String vencedor() {
        UUID lider = placar.lider();
        if (lider == null) {
            return null;
        }
        Player quem = Bukkit.getPlayer(lider);
        return quem == null ? "alguém" : quem.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        UUID lider = placar.lider();
        if (lider == null) {
            return "empate";
        }
        int pontos = placar.de(lider);
        if (cacadores.contains(lider)) {
            int quantas = capturas.getOrDefault(lider, 0);
            return pontos + " pontos · " + quantas
                    + (quantas == 1 ? " captura" : " capturas");
        }
        return pontos + " pontos escondido · "
                + ArenaComum.tempo(tiquesDeJogo()) + " de pé";
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        EscondeCatalog planta = planta2();
        if (!cabineAberta) {
            int faltam = Math.max(0,
                    planta.segredoSegundos() - segundosDeJogo());
            return "<color:" + planta.corDoTexto() + ">a cabine abre em "
                    + faltam + "s</color> <gray>· " + quantosEscondidos()
                    + " se escondendo</gray>";
        }
        return "<white>" + quantosEscondidos() + "</white> <gray>escondido(s) "
                + "· " + caixotes.size() + " caixotes · onda " + ondaFeita
                + " de " + planta.ondas() + " · captura vale <white>"
                + valorDaCaptura + "</white></gray>";
    }

    // ------------------------------------------------------------------
    //  O disfarce
    // ------------------------------------------------------------------

    private void esconder(Player jogador, EscondeCatalog planta) {
        UUID id = jogador.getUniqueId();
        jogador.setInvisible(true);
        jogador.setCollidable(false);
        noTime(jogador);

        BlockDisplay velho = disfarce.remove(id);
        if (velho != null) {
            velho.remove();
        }
        Material caixote = planta.cor("caixote", Material.BARREL);
        Location onde = jogador.getLocation();
        Location canto = new Location(jogador.getWorld(), onde.getX() - 0.5,
                onde.getY(), onde.getZ() - 0.5);
        canto.setYaw(0f);
        canto.setPitch(0f);
        BlockDisplay novo = jogador.getWorld().spawn(canto, BlockDisplay.class,
                feito -> {
                    feito.setBlock(caixote.createBlockData());
                    feito.setBrightness(new Display.Brightness(15, 15));
                    feito.setTeleportDuration(DESLIZE);
                    feito.setViewRange(2.0f);
                    feito.setPersistent(false);
                    feito.addScoreboardTag(TAG_DISFARCE);
                });
        disfarce.put(id, novo);
        ultimoLugar.put(id, new double[]{onde.getX(), onde.getZ()});
        paradoDesde.put(id, tiquesDeJogo());
    }

    private void desesconder(Player jogador) {
        UUID id = jogador.getUniqueId();
        BlockDisplay velho = disfarce.remove(id);
        if (velho != null) {
            velho.remove();
        }
        encaixados.remove(id);
        jogador.setInvisible(false);
        jogador.setCollidable(true);
        foraDoTime(jogador);
    }

    /** Mata todo disfarce do mundo, inclusive o que sobrou de um /biga parar. */
    private void tirarTodosOsDisfarces(World mundo) {
        for (Entity bicho : mundo.getEntities()) {
            if (bicho instanceof BlockDisplay
                    && bicho.getScoreboardTags().contains(TAG_DISFARCE)) {
                bicho.remove();
            }
        }
        disfarce.clear();
    }

    /**
     * O time existe por uma razão só: o nome.
     *
     * Um jogador invisível continua com o nome flutuando, e o nome atravessa
     * parede. Sem o time, o disfarce seria um caixote com uma etiqueta em
     * cima dizendo quem é.
     */
    private void noTime(Player jogador) {
        Team time = oTime();
        if (time != null) {
            time.addEntry(jogador.getName());
        }
    }

    private void foraDoTime(Player jogador) {
        Team time = oTime();
        if (time != null) {
            time.removeEntry(jogador.getName());
        }
    }

    private void limparOTime() {
        Team time = oTime();
        if (time == null) {
            return;
        }
        for (String quem : new ArrayList<>(time.getEntries())) {
            time.removeEntry(quem);
        }
    }

    private Team oTime() {
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        Scoreboard tabuleiro = Bukkit.getScoreboardManager()
                .getMainScoreboard();
        Team time = tabuleiro.getTeam(TIME);
        if (time == null) {
            time = tabuleiro.registerNewTeam(TIME);
        }
        time.setOption(Team.Option.NAME_TAG_VISIBILITY,
                Team.OptionStatus.NEVER);
        return time;
    }

    // ------------------------------------------------------------------
    //  O laço
    // ------------------------------------------------------------------

    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando()) {
            return;
        }
        EscondeCatalog planta = planta2();
        if (!cabineAberta && segundosDeJogo() >= planta.segredoSegundos()) {
            abrirACabine(mundo, planta);
        }
        cuidarDosDisfarces(mundo, planta);
        cuidarDasOndas(mundo, planta);
    }

    /** Solta os caçadores da cabine. */
    private void abrirACabine(World mundo, EscondeCatalog planta) {
        cabineAberta = true;
        List<Player> caca = jogadoresCacadores();
        for (int k = 0; k < caca.size(); k++) {
            Player jogador = caca.get(k);
            jogador.teleport(EscondeBuilder.pontoDaSaida(mundo, planta, k,
                    caca.size()));
            jogador.setFallDistance(0f);
        }
        tituloEmTodos("<color:#FF8A8A>A CABINE ABRIU</color>",
                "<gray>" + quantosEscondidos() + " escondido(s) no "
                        + "galpão</gray>");
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);
        }
        mostrarOPainel(mundo, planta);
    }

    /**
     * Uma vez por tique: quem está parado, quem encaixou, e onde vai o
     * caixote de cada um.
     *
     * Por tique e lendo a posição, e não no evento de mexer: um jogador
     * PARADO não dispara evento de movimento nenhum, e é justamente o parado
     * que este jogo precisa reconhecer.
     */
    private void cuidarDosDisfarces(World mundo, EscondeCatalog planta) {
        long agora = tiquesDeJogo();
        for (Player jogador : jogadoresEscondidos()) {
            UUID id = jogador.getUniqueId();
            Location onde = jogador.getLocation();
            double[] antes = ultimoLugar.get(id);
            boolean mexeu = antes == null
                    || Math.abs(onde.getX() - antes[0]) > PARADO
                    || Math.abs(onde.getZ() - antes[1]) > PARADO;
            ultimoLugar.put(id, new double[]{onde.getX(), onde.getZ()});

            if (mexeu) {
                paradoDesde.put(id, agora);
                if (encaixados.remove(id)) {
                    jogador.playSound(onde, Sound.BLOCK_BARREL_OPEN, 0.5f,
                            1.4f);
                }
            } else if (!encaixados.contains(id)
                    && agora - paradoDesde.getOrDefault(id, agora)
                            >= planta.encaixeTiques()) {
                tentarEncaixar(jogador, planta);
            }
            levarOCaixote(jogador, planta);
        }
    }

    /** O encaixe: empurra para o centro do bloco, se o bloco é da grade. */
    private void tentarEncaixar(Player jogador, EscondeCatalog planta) {
        UUID id = jogador.getUniqueId();
        Location onde = jogador.getLocation();
        int bx = onde.getBlockX();
        int bz = onde.getBlockZ();
        if (!grade.contains(ArenaComum.chave(bx, 0, bz))) {
            return;
        }
        if (Math.abs(onde.getY() - (planta.topoY() + 1)) > 0.3) {
            // No ar ou em cima de alguma coisa: o caixote não encostaria no
            // chão, e um caixote flutuando é pior que um caixote torto.
            return;
        }
        double[] centro = EscondeCatalog.centroDoBloco(bx, bz);
        Location alvo = new Location(jogador.getWorld(), centro[0],
                planta.topoY() + 1, centro[1], onde.getYaw(), onde.getPitch());
        jogador.teleport(alvo);
        ultimoLugar.put(id, new double[]{alvo.getX(), alvo.getZ()});
        encaixados.add(id);
        jogador.playSound(alvo, Sound.BLOCK_BARREL_CLOSE, 0.6f, 1.2f);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:" + planta.corDoTexto() + ">ENCAIXADO</color> "
                        + "<gray>— o seu caixote é igual aos outros</gray>"));
    }

    /**
     * O caixote vai para onde o jogador está — ou para o canto do bloco, se
     * ele encaixou.
     *
     * A diferença é o jogo todo: no canto do bloco o caixote ocupa a célula
     * exatamente como um caixote de enfeite ocupa a dele; na posição do
     * jogador ele fica meio bloco fora de tudo. O {@code teleportDuration}
     * faz o caixote DESLIZAR em vez de pular, o que é o que o olho vê de
     * longe quando um escondido anda.
     */
    private void levarOCaixote(Player jogador, EscondeCatalog planta) {
        BlockDisplay caixote = disfarce.get(jogador.getUniqueId());
        if (caixote == null || !caixote.isValid()) {
            return;
        }
        Location onde = jogador.getLocation();
        Location alvo;
        if (encaixados.contains(jogador.getUniqueId())) {
            alvo = new Location(jogador.getWorld(), onde.getBlockX(),
                    planta.topoY() + 1, onde.getBlockZ());
        } else {
            alvo = new Location(jogador.getWorld(), onde.getX() - 0.5,
                    onde.getY(), onde.getZ() - 0.5);
        }
        alvo.setYaw(0f);
        alvo.setPitch(0f);
        caixote.teleport(alvo);
    }

    // ------------------------------------------------------------------
    //  As ondas
    // ------------------------------------------------------------------

    private void cuidarDasOndas(World mundo, EscondeCatalog planta) {
        if (ondaFeita >= planta.ondas()) {
            return;
        }
        int proxima = ondaFeita + 1;
        if (segundosDeJogo() < planta.segundoDaOnda(proxima)) {
            return;
        }
        ondaFeita = proxima;
        int sobram = planta.caixotesDaOnda(ondaFeita,
                Math.max(1, quantosEscondidos()));
        int tirados = 0;
        while (caixotes.size() > sobram && !caixotes.isEmpty()) {
            int[] onde = caixotes.remove(caixotes.size() - 1);
            if (plugin.escondeBuilder().tirarCaixote(mundo, planta, onde)) {
                tirados++;
            }
        }
        if (tirados == 0) {
            return;
        }
        anunciar("<color:" + planta.corDoTexto() + ">onda " + ondaFeita
                + " de " + planta.ondas() + "</color> <gray>— <white>"
                + tirados + "</white> caixotes sumiram, sobraram <white>"
                + caixotes.size() + "</white></gray>");
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        }
        mostrarOPainel(mundo, planta);
    }

    /** Espalha caixotes de enfeite por lugares sorteados da grade. */
    private void espalharCaixotes(World mundo, EscondeCatalog planta,
                                  int quantos) {
        List<int[]> lugares = new ArrayList<>(planta.pontosDosCaixotes());
        Collections.shuffle(lugares);
        caixotes.clear();
        for (int[] onde : lugares) {
            if (caixotes.size() >= quantos) {
                break;
            }
            if (plugin.escondeBuilder().porCaixote(mundo, planta, onde)) {
                caixotes.add(onde);
            }
        }
    }

    private void mostrarOPainel(World mundo, EscondeCatalog planta) {
        String linha = cabineAberta
                ? "<gray>" + caixotes.size() + " caixotes · onda "
                        + ondaFeita + " de " + planta.ondas() + "</gray>"
                : "<gray>a cabine abre em " + Math.max(0,
                        planta.segredoSegundos() - segundosDeJogo())
                        + "s</gray>";
        plugin.escondeBuilder().painelDaCaca(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + quantosEscondidos()
                        + " ESCONDIDO(S)</b></color>\n" + linha
                        + "\n<gray>captura vale <white>" + valorDaCaptura
                        + "</white></gray>"));
    }

    // ------------------------------------------------------------------
    //  A captura
    // ------------------------------------------------------------------

    /**
     * O caçador acertou um caixote que era gente.
     *
     * A captura é a BATIDA e não o dano: a referência do gênero compara o
     * dano com a vida do escondido, e aí quem comeu antes de entrar ou pegou
     * uma armadura é mais difícil de pegar do que quem não. Aqui o dano
     * continua cancelado e a batida resolve, então pegar alguém depende de
     * achar e acertar, e de nada mais.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoBater(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player alvo)
                || !(evento.getDamager() instanceof Player quemBateu)) {
            return;
        }
        if (!jogando() || !cabineAberta
                || !estaVivo(alvo) || !estaVivo(quemBateu)) {
            return;
        }
        UUID deQuem = quemBateu.getUniqueId();
        UUID doAlvo = alvo.getUniqueId();
        if (!cacadores.contains(deQuem) || cacadores.contains(doAlvo)) {
            return;
        }
        pegar(quemBateu, alvo);
    }

    private void pegar(Player cacador, Player pego) {
        EscondeCatalog planta = planta2();
        UUID deQuem = cacador.getUniqueId();
        int quantas = capturas.merge(deQuem, 1, Integer::sum);
        placar.somar(deQuem, valorDaCaptura);

        cacador.playSound(cacador.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        cacador.sendActionBar(plugin.mini().deserialize(
                "<color:#8BE04E>+" + valorDaCaptura + "</color> <gray>· "
                        + quantas + (quantas == 1 ? " captura" : " capturas")
                        + " · " + placar.de(deQuem) + " pontos</gray>"));

        animarGolpe(pego, cacador);
        pego.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>ACHARAM VOCÊ</color>"),
                plugin.mini().deserialize("<gray>"
                        + HubBuilder.escapar(cacador.getName())
                        + " · você fez <white>" + placar.de(pego.getUniqueId())
                        + "</white> pontos</gray>")));
        anunciar("<color:#FF8A8A>" + HubBuilder.escapar(cacador.getName())
                + "</color> <gray>achou <white>"
                + HubBuilder.escapar(pego.getName())
                + "</white> — faltam <white>" + (quantosEscondidos() - 1)
                + "</white></gray>");
        if (planta.pontosPorSegundo() > 0) {
            pego.getWorld().playSound(pego.getLocation(),
                    Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
        eliminar(pego);
    }

    // ------------------------------------------------------------------
    //  O ponto por segundo
    // ------------------------------------------------------------------

    @Override
    protected void aCadaSegundo(World mundo) {
        EscondeCatalog planta = planta2();
        for (Player jogador : jogadoresEscondidos()) {
            UUID id = jogador.getUniqueId();
            placar.somar(id, planta.pontosPorSegundo());
            boolean certo = encaixados.contains(id);
            jogador.sendActionBar(plugin.mini().deserialize(
                    (certo ? "<color:" + planta.corDoTexto()
                            + ">encaixado</color>"
                            : "<color:#FF8A8A>torto</color> <gray>— pare num "
                                    + "lugar de caixote</gray>")
                            + " <dark_gray>|</dark_gray> <gray>"
                            + placar.de(id) + " pontos · "
                            + caixotes.size() + " caixotes</gray>"));
        }
        if (!cabineAberta) {
            return;
        }
        for (Player jogador : jogadoresCacadores()) {
            UUID id = jogador.getUniqueId();
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>caçando</color> <gray>· "
                            + quantosEscondidos() + " escondido(s) entre "
                            + caixotes.size() + " caixotes · "
                            + capturas.getOrDefault(id, 0) + " captura(s) · "
                            + placar.de(id) + " pontos</gray>"));
        }
    }

    // ------------------------------------------------------------------
    //  Quem é quem
    // ------------------------------------------------------------------

    private List<Player> jogadoresCacadores() {
        List<Player> saida = new ArrayList<>();
        for (Player jogador : jogadoresVivos()) {
            if (cacadores.contains(jogador.getUniqueId())) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    private List<Player> jogadoresEscondidos() {
        List<Player> saida = new ArrayList<>();
        for (Player jogador : jogadoresVivos()) {
            if (!cacadores.contains(jogador.getUniqueId())) {
                saida.add(jogador);
            }
        }
        return saida;
    }

    private int quantosEscondidos() {
        return jogadoresEscondidos().size();
    }

    private int quantosCacadores() {
        return jogadoresCacadores().size();
    }

    /** Quantos caixotes de enfeite estão de pé, para quem pergunta de fora. */
    public int caixotesDePe() {
        return caixotes.size();
    }
}
