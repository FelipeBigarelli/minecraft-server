package codes.biga.bigacore;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * O BUILD BATTLE: construa melhor que todos.
 *
 * <h2>Os três defeitos do gênero, e o que fazemos diferente</h2>
 *
 * O estudo da referência não rendeu uma lista de recursos faltando: rendeu
 * três reclamações que os jogadores fazem em TODO servidor de Build Battle, e
 * nenhuma delas é sobre construir.
 *
 * <ul>
 *   <li><b>"Todo mundo vota meh."</b> Dar a nota mínima para os outros
 *       levanta a própria colocação, então é a jogada certa — e com isso
 *       jogador novo nunca sobe ao pódio. A nota aqui não é a média crua:
 *       {@link BuildBattleCatalog#ordemDaCedula} converte a cédula de cada
 *       votante em ORDEM antes de somar. Quem dá 1 para todos produz uma
 *       cédula de postos todos iguais, que não desempata nada; quem combina
 *       com um amigo tem exatamente um primeiro lugar para dar. Um votante,
 *       uma voz — e sem precisar descobrir quem está trapaceando.</li>
 *   <li><b>"A primeira obra sempre toma nota baixa."</b> Quem vota sem ter
 *       visto as outras vota com medo. Por isso há duas voltas: o
 *       {@link Fase#PASSEIO}, rápido e sem voto nenhum, e depois o
 *       {@link Fase#VOTO}. Quando você vota no primeiro lote, já viu todos.
 *       A ordem dos lotes é embaralhada por rodada, para o resto do viés não
 *       cair sempre no mesmo número de lote.</li>
 *   <li><b>"O menu criativo é o jogo."</b> Com mil blocos e três minutos
 *       ganha quem sabe onde as coisas ficam no menu. {@link #darAPaleta}
 *       entrega 36 blocos curados, que é exatamente o que cabe no inventário:
 *       sem menu, sem busca, sem página. Os mesmos 36 fazem um castelo ou uma
 *       baleia dependendo de quem os segura, e é por isso que a paleta é uma
 *       só para todos os temas.</li>
 * </ul>
 *
 * <h2>O lote é uma caixa</h2>
 *
 * Durante a obra, quem sai do próprio lote volta para ele. É a única maneira
 * honesta de ninguém copiar o vizinho, e custa zero a quem não ia copiar:
 * {@code limite-do-lote} é bem maior que o meio lote, então dá para recuar e
 * olhar a própria obra de longe, e bem menor que o passo da grade, então não
 * alcança o lote de ninguém.
 *
 * <h2>Quem não escolhe nota dá a nota do meio</h2>
 *
 * A cada lote a mão do votante começa na nota do meio. Começar na primeira
 * faria a desatenção virar exatamente o voto baixo que este jogo foi desenhado
 * para desarmar — e punir quem se distraiu seria o contrário do que se quer:
 * quem não opina não deve mexer na ordem de ninguém.
 */
public final class BuildBattle extends JogoDeArena {

    private enum Fase { TEMA, OBRA, PASSEIO, VOTO, FIM }

    /** A escada de cor da nota, do pior para o melhor. */
    private static final Material[] CORES_DA_NOTA = {
            Material.RED_DYE, Material.ORANGE_DYE, Material.YELLOW_DYE,
            Material.LIME_DYE, Material.GREEN_DYE};

    private final Placar placar = new Placar();
    private final Random sorteio = new Random();

    private final Map<UUID, Integer> loteDe = new HashMap<>();
    private final Map<Integer, UUID> donoDe = new LinkedHashMap<>();

    /** A cédula de cada votante: lote -> nota crua. */
    private final Map<UUID, Map<Integer, Integer>> cedulas = new LinkedHashMap<>();

    /** A paleta da rodada, para a conferência de bloco não reler o yml. */
    private final Set<Material> paleta = new HashSet<>();

    private Fase fase = Fase.TEMA;
    private long tiqueDaFase;
    private List<Integer> ordem = new ArrayList<>();
    private int naOrdem;
    private String tema = "";
    private int campeao = -1;

    public BuildBattle(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.buildBattleCatalog();
    }

    private BuildBattleCatalog planta2() {
        return plugin.buildBattleCatalog();
    }

    /**
     * Aventura, e não criativo.
     *
     * É o modo que o arcabouço põe em quem está no saguão e na fila, e
     * criativo ali daria voo e menu a quem só está esperando. O criativo é
     * dado à mão em {@link #comecarAObra} e tirado em {@link #levarAoLote}.
     */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected String verboDaLargada() {
        return "O TEMA É...";
    }

    /** Ninguém é eliminado neste jogo, e voar por baixo da obra é trabalho. */
    @Override
    protected boolean caiuFora(Player jogador) {
        return false;
    }

    // ------------------------------------------------------------------
    //  A rodada
    // ------------------------------------------------------------------

    /** A lista lateral mostra o placar deste jogo. */
    @Override
    protected Placar placarDaLateral() {
        return placar;
    }

    @Override
    protected void prepararArena(World mundo) {
        BuildBattleCatalog planta = planta2();
        placar.zerar();
        loteDe.clear();
        donoDe.clear();
        cedulas.clear();
        paleta.clear();
        paleta.addAll(planta.blocos());
        ordem = new ArrayList<>();
        naOrdem = 0;
        campeao = -1;
        fase = Fase.TEMA;
        tiqueDaFase = 0;
        List<String> temas = planta.temas();
        tema = temas.get(sorteio.nextInt(temas.size()));

        BuildBattleBuilder obra = plugin.buildBattleBuilder();
        for (int k = 0; k < planta.plots(); k++) {
            obra.limparLote(mundo, planta, k);
            obra.refazerLote(mundo, planta, k);
        }
    }

    /**
     * A largada reparte os lotes e mostra o tema.
     *
     * Os lotes são sorteados e não dados por ordem de entrada: lote tem
     * vizinho, e vizinho conhecido é combinação possível. Sorteado, ninguém
     * escolhe ao lado de quem constrói.
     */
    @Override
    protected void largar(World mundo, List<Player> entram) {
        BuildBattleCatalog planta = planta2();
        List<Integer> lotes = new ArrayList<>();
        for (int k = 0; k < planta.plots(); k++) {
            lotes.add(k);
        }
        Collections.shuffle(lotes, sorteio);

        for (int k = 0; k < entram.size() && k < lotes.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            int lote = lotes.get(k);
            loteDe.put(id, lote);
            donoDe.put(lote, id);
            cedulas.put(id, new LinkedHashMap<>());
            placar.entrar(id);
            jogador.getInventory().clear();
            jogador.setGameMode(GameMode.ADVENTURE);
            jogador.teleport(BuildBattleBuilder.pontoDoLote(mundo, planta,
                    lote));
            jogador.setFallDistance(0f);
        }
        ordem = new ArrayList<>(donoDe.keySet());
        Collections.shuffle(ordem, sorteio);
        fase = Fase.TEMA;
        tiqueDaFase = tiquesDeJogo();
    }

    @Override
    protected void instrucoes(Player jogador) {
        BuildBattleCatalog planta = planta2();
        dizer(jogador, "<gray>você tem <white>" + planta.segundosDeObra()
                + "s</white> de obra num lote só seu, com uma paleta que "
                + "<white>cabe inteira no inventário</white>, e durante a "
                + "obra você <white>não sai do seu lote</white>.</gray>");
        dizer(jogador, "<gray>depois tem um <white>passeio</white> sem voto e "
                + "depois a <white>volta do voto</white>: quando você votar "
                + "no primeiro lote, já vai ter visto todos. E "
                + "<white>dar nota baixa para todos não levanta a sua</white> "
                + "— a conta usa a ordem da sua cédula, não as notas.</gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        BuildBattleCatalog planta = planta2();
        tituloEmTodos("<color:" + planta.corDoTexto() + ">" + tema
                + "</color>", "<gray>o tema é o mesmo para todos</gray>");
        anunciar("<gray>o tema é <color:" + planta.corDoTexto() + "><b>"
                + tema + "</b></color> — você tem <white>"
                + planta.segundosDeObra() + "s</white> de obra, a paleta é de "
                + "<white>" + paleta.size() + " blocos</white> e cabe inteira "
                + "no inventário, e durante a obra você <white>não sai do seu "
                + "lote</white>.</gray>");
        mostrarOPainel(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return BuildBattleBuilder.pontoDoAlto(mundo, planta2());
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        Integer lote = loteDe.remove(id);
        if (lote != null) {
            donoDe.remove(lote);
            ordem.remove(Integer.valueOf(lote));
        }
        cedulas.remove(id);
        for (Map<Integer, Integer> cedula : cedulas.values()) {
            if (lote != null) {
                cedula.remove(lote);
            }
        }
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            if (jogador.getGameMode() == GameMode.CREATIVE) {
                jogador.setGameMode(GameMode.ADVENTURE);
            }
        }
        if (mundo != null) {
            ArenaComum.limparPaineis(mundo, BuildBattleBuilder.TAG_FASE);
            ArenaComum.limparPaineis(mundo, BuildBattleBuilder.TAG_VOTO);
        }
    }

    @Override
    protected boolean rodadaAcabou() {
        return fase == Fase.FIM
                || segundosDeJogo() >= planta2().tempoLimite();
    }

    @Override
    protected String vencedor() {
        UUID quem = campeao >= 0 ? donoDe.get(campeao) : null;
        if (quem == null) {
            UUID lider = placar.lider();
            quem = lider;
        }
        if (quem == null) {
            return null;
        }
        Player jogador = Bukkit.getPlayer(quem);
        return jogador == null ? "alguém" : jogador.getName();
    }

    @Override
    protected String legendaDaVitoria() {
        BuildBattleCatalog planta = planta2();
        if (campeao < 0) {
            return "tema: " + tema;
        }
        double crua = BuildBattleCatalog.mediaCrua(cedulas.values(), campeao);
        return "nota " + (Math.round(crua * 10) / 10.0) + " de "
                + planta.notas() + " · tema: " + tema;
    }

    @Override
    protected float progressoDaBarra() {
        BuildBattleCatalog planta = planta2();
        int total = Math.max(1, planta.duracaoTotal(Math.max(1, ordem.size())));
        return ArenaComum.limite(segundosDeJogo() / (float) total);
    }

    @Override
    protected String textoDaBarra() {
        BuildBattleCatalog planta = planta2();
        int falta;
        switch (fase) {
            case TEMA -> {
                return "<color:" + planta.corDoTexto() + ">" + tema
                        + "</color> <gray>— prepare-se</gray>";
            }
            case OBRA -> {
                falta = Math.max(0, planta.segundosDeObra() - naFase());
                return "<white>" + tema + "</white> <gray>· obra · "
                        + ArenaComum.tempo(falta * 20L) + "</gray>";
            }
            case PASSEIO -> {
                return "<gray>passeio <white>" + (naOrdem + 1) + "/"
                        + ordem.size() + "</white> — sem voto ainda</gray>";
            }
            case VOTO -> {
                falta = Math.max(0, planta.segundosDeVoto() - naFase());
                return "<gray>voto <white>" + (naOrdem + 1) + "/"
                        + ordem.size() + "</white> · " + falta + "s</gray>";
            }
            default -> {
                return "<gray>resultado</gray>";
            }
        }
    }

    // ------------------------------------------------------------------
    //  As fases
    // ------------------------------------------------------------------

    private int naFase() {
        return (int) ((tiquesDeJogo() - tiqueDaFase) / 20);
    }

    private void virarFase(Fase nova) {
        fase = nova;
        tiqueDaFase = tiquesDeJogo();
    }

    @Override
    protected void aCadaSegundo(World mundo) {
        if (!jogando()) {
            return;
        }
        BuildBattleCatalog planta = planta2();
        switch (fase) {
            case TEMA -> {
                if (naFase() >= planta.segundosDeTema()) {
                    comecarAObra(mundo, planta);
                }
            }
            case OBRA -> {
                if (naFase() >= planta.segundosDeObra()) {
                    comecarOPasseio(mundo, planta);
                } else {
                    avisarDaObra(planta);
                    if (naFase() % 15 == 0) {
                        mostrarOPainel(mundo, planta);
                    }
                }
            }
            case PASSEIO -> {
                if (naFase() >= planta.segundosDePasseio()) {
                    passarAoProximo(mundo, planta, false);
                }
            }
            case VOTO -> {
                if (naFase() >= planta.segundosDeVoto()) {
                    guardarOsVotos(planta);
                    passarAoProximo(mundo, planta, true);
                } else {
                    lembrarDaNota(planta);
                }
            }
            default -> { }
        }
    }

    private void comecarAObra(World mundo, BuildBattleCatalog planta) {
        virarFase(Fase.OBRA);
        for (Player jogador : jogadoresVivos()) {
            Integer lote = loteDe.get(jogador.getUniqueId());
            if (lote == null) {
                continue;
            }
            jogador.setGameMode(GameMode.CREATIVE);
            jogador.setAllowFlight(true);
            jogador.teleport(BuildBattleBuilder.pontoDoLote(mundo, planta,
                    lote));
            darAPaleta(jogador, planta);
        }
        tituloEmTodos("<color:" + planta.corDoTexto() + ">CONSTRUA</color>",
                "<gray>" + tema + " · " + planta.segundosDeObra()
                        + "s</gray>");
        mostrarOPainel(mundo, planta);
    }

    private void comecarOPasseio(World mundo, BuildBattleCatalog planta) {
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            jogador.setGameMode(GameMode.ADVENTURE);
        }
        if (ordem.size() < 2) {
            // Treino de um só: não há obra de outro para julgar, e julgar a
            // própria seria dar nota a si mesmo.
            anunciar("<gray>treino: com um construtor só não há voto</gray>");
            virarFase(Fase.FIM);
            return;
        }
        naOrdem = 0;
        virarFase(Fase.PASSEIO);
        tituloEmTodos("<color:" + planta.corDoTexto() + ">PASSEIO</color>",
                "<gray>olhe todas antes de votar em alguma</gray>");
        anunciar("<gray>esta volta é só para <white>olhar</white>: "
                + "ninguém vota ainda. Quem vota sem ter visto as outras vota "
                + "com medo, e aí a primeira obra sempre leva nota baixa.</gray>");
        levarAoLote(mundo, planta, ordem.get(0), false);
    }

    private void passarAoProximo(World mundo, BuildBattleCatalog planta,
                                 boolean votando) {
        naOrdem++;
        if (naOrdem < ordem.size()) {
            virarFase(votando ? Fase.VOTO : Fase.PASSEIO);
            levarAoLote(mundo, planta, ordem.get(naOrdem), votando);
            return;
        }
        if (!votando) {
            naOrdem = 0;
            virarFase(Fase.VOTO);
            tituloEmTodos("<color:" + planta.corDoTexto() + ">AGORA VOTE"
                    + "</color>", "<gray>a nota é o item na sua mão</gray>");
            anunciar("<gray>a nota é o <white>item que você segura</white>: "
                    + "mude de item para mudar a nota. Quem não escolhe dá a "
                    + "nota do meio — desatenção não vira voto baixo.</gray>");
            levarAoLote(mundo, planta, ordem.get(0), true);
            return;
        }
        fecharAVotacao(mundo, planta);
    }

    /**
     * Leva todos à plateia do lote, e entrega as notas a quem pode votar.
     *
     * O dono do lote não recebe nota nenhuma: voto em si mesmo é o primeiro
     * jeito de furar qualquer votação, e tirar a nota da mão dele é mais
     * claro do que aceitar o voto e descartar depois.
     */
    private void levarAoLote(World mundo, BuildBattleCatalog planta, int lote,
                             boolean votando) {
        Location onde = BuildBattleBuilder.pontoDaPlateia(mundo, planta, lote);
        UUID dono = donoDe.get(lote);
        String nome = nomeDoDono(lote);

        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            if (jogador.getGameMode() != GameMode.SPECTATOR) {
                jogador.setGameMode(GameMode.ADVENTURE);
            }
            jogador.setFallDistance(0f);
            jogador.teleport(onde);
            boolean ehDele = jogador.getUniqueId().equals(dono);
            if (votando && !ehDele) {
                darAsNotas(jogador, planta);
            }
            if (votando && ehDele) {
                jogador.sendActionBar(plugin.mini().deserialize(
                        "<color:" + planta.corDoTexto() + ">esta obra é a sua"
                                + "</color> <gray>— você não vota nela</gray>"));
            }
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, votando ? 1.4f : 1.0f);
        }

        plugin.buildBattleBuilder().painelDoVoto(mundo, planta, lote,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + HubBuilder.escapar(nome) + "</b></color>\n"
                        + (votando
                                ? "<gray>a nota é o item na sua mão</gray>"
                                : "<gray>passeio — ninguém vota ainda</gray>")
                        + "\n<gray>lote " + (naOrdem + 1) + " de "
                        + ordem.size() + "</gray>"));
    }

    /** Guarda a nota de cada votante no lote que estava em julgamento. */
    private void guardarOsVotos(BuildBattleCatalog planta) {
        if (naOrdem < 0 || naOrdem >= ordem.size()) {
            return;
        }
        int lote = ordem.get(naOrdem);
        UUID dono = donoDe.get(lote);
        for (Player jogador : jogadoresDaArena()) {
            UUID id = jogador.getUniqueId();
            if (id.equals(dono) || !cedulas.containsKey(id)) {
                continue;
            }
            int nota = Math.max(1, Math.min(planta.notas(),
                    jogador.getInventory().getHeldItemSlot() + 1));
            cedulas.get(id).put(lote, nota);
        }
    }

    /**
     * Fecha a votação, conta pela ORDEM das cédulas e anuncia o pódio.
     *
     * O que o jogador vê é a média crua — "nota 4,2 de 5" é o que ele
     * entende. A ordem normalizada decide a colocação e não aparece em lugar
     * nenhum: ela é o conserto do defeito, não uma regra nova para aprender.
     */
    private void fecharAVotacao(World mundo, BuildBattleCatalog planta) {
        List<Integer> colocacao = BuildBattleCatalog.colocacao(
                cedulas.values(), planta.notas());
        campeao = colocacao.isEmpty() ? -1 : colocacao.get(0);

        Map<Integer, Double> finais = BuildBattleCatalog.notasFinais(
                cedulas.values(), planta.notas());
        for (Map.Entry<Integer, Double> item : finais.entrySet()) {
            UUID dono = donoDe.get(item.getKey());
            if (dono != null) {
                placar.somar(dono, (int) Math.round(item.getValue() * 1000));
            }
        }

        anunciar("<color:" + planta.corDoTexto() + "><b>RESULTADO</b></color> "
                + "<gray>— tema: " + tema + "</gray>");
        for (int k = 0; k < Math.min(3, colocacao.size()); k++) {
            int lote = colocacao.get(k);
            double crua = BuildBattleCatalog.mediaCrua(cedulas.values(), lote);
            anunciar("<white>" + (k + 1) + "º</white> <color:"
                    + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(nomeDoDono(lote)) + "</color> "
                    + "<gray>— nota " + (Math.round(crua * 10) / 10.0)
                    + " de " + planta.notas() + "</gray>");
        }
        for (Player jogador : jogadoresDaArena()) {
            Integer meu = loteDe.get(jogador.getUniqueId());
            if (meu == null) {
                continue;
            }
            int posicao = colocacao.indexOf(meu) + 1;
            double crua = BuildBattleCatalog.mediaCrua(cedulas.values(), meu);
            jogador.showTitle(Title.title(
                    plugin.mini().deserialize(posicao == 1
                            ? "<color:" + planta.corDoTexto() + ">1º LUGAR</color>"
                            : "<white>" + posicao + "º lugar</white>"),
                    plugin.mini().deserialize("<gray>nota "
                            + (Math.round(crua * 10) / 10.0) + " de "
                            + planta.notas() + "</gray>")));
        }

        if (campeao >= 0) {
            Location onde = BuildBattleBuilder.pontoDaPlateia(mundo, planta,
                    campeao);
            for (Player jogador : jogadoresDaArena()) {
                jogador.teleport(onde);
            }
            plugin.buildBattleBuilder().painelDoVoto(mundo, planta, campeao,
                    plugin.mini().deserialize("<color:" + planta.corDoTexto()
                            + "><b>1º LUGAR</b></color>\n<white>"
                            + HubBuilder.escapar(nomeDoDono(campeao))
                            + "</white>\n<gray>" + tema + "</gray>"));
        }
        virarFase(Fase.FIM);
    }

    private String nomeDoDono(int lote) {
        UUID dono = donoDe.get(lote);
        if (dono == null) {
            return "lote " + (lote + 1);
        }
        Player jogador = Bukkit.getPlayer(dono);
        return jogador == null ? "alguém" : jogador.getName();
    }

    // ------------------------------------------------------------------
    //  A paleta e as notas na mão
    // ------------------------------------------------------------------

    /**
     * A paleta na mão: 36 blocos, sem menu.
     *
     * Em criativo, colocar não gasta o item, então uma unidade de cada basta
     * — e o inventário fica legível em vez de cheio de pilhas de 64.
     */
    private void darAPaleta(Player jogador, BuildBattleCatalog planta) {
        jogador.getInventory().clear();
        List<Material> blocos = planta.blocos();
        for (int k = 0; k < blocos.size() && k < 36; k++) {
            jogador.getInventory().setItem(k, new ItemStack(blocos.get(k), 1));
        }
        jogador.getInventory().setHeldItemSlot(0);
        jogador.updateInventory();
    }

    /**
     * As notas na mão, e a mão começando na nota do meio.
     *
     * Começar na primeira faria a desatenção virar exatamente o voto baixo
     * que este jogo foi desenhado para desarmar. Quem não opina não deve
     * mexer na ordem de ninguém.
     */
    private void darAsNotas(Player jogador, BuildBattleCatalog planta) {
        jogador.getInventory().clear();
        int notas = planta.notas();
        for (int nota = 1; nota <= notas; nota++) {
            ItemStack item = new ItemStack(corDaNota(nota, notas), nota);
            ItemMeta marca = item.getItemMeta();
            if (marca != null) {
                marca.displayName(plugin.mini().deserialize("<color:"
                        + corDoRecado(nota, notas) + ">" + nota + " — "
                        + recadoDaNota(nota, notas) + "</color>"));
                item.setItemMeta(marca);
            }
            jogador.getInventory().setItem(nota - 1, item);
        }
        jogador.getInventory().setHeldItemSlot(Math.max(0, notas / 2));
        jogador.updateInventory();
    }

    private static Material corDaNota(int nota, int notas) {
        int k = (int) Math.round((nota - 1) / (double) Math.max(1, notas - 1)
                * (CORES_DA_NOTA.length - 1));
        return CORES_DA_NOTA[Math.max(0,
                Math.min(CORES_DA_NOTA.length - 1, k))];
    }

    private static String recadoDaNota(int nota, int notas) {
        double parte = (nota - 1) / (double) Math.max(1, notas - 1);
        if (parte <= 0.01) {
            return "ruim";
        }
        if (parte < 0.4) {
            return "fraca";
        }
        if (parte < 0.6) {
            return "ok";
        }
        if (parte < 0.99) {
            return "boa";
        }
        return "incrível";
    }

    private static String corDoRecado(int nota, int notas) {
        double parte = (nota - 1) / (double) Math.max(1, notas - 1);
        if (parte < 0.3) {
            return "#FF8A8A";
        }
        if (parte < 0.7) {
            return "#FFD93D";
        }
        return "#8BE04E";
    }

    /** Trocar de item é mudar a nota, e o votante vê a sua na hora. */
    @EventHandler(ignoreCancelled = true)
    public void aoTrocarItem(PlayerItemHeldEvent evento) {
        if (fase != Fase.VOTO || !jogando()) {
            return;
        }
        Player jogador = evento.getPlayer();
        if (!naArena(jogador) || !cedulas.containsKey(jogador.getUniqueId())) {
            return;
        }
        BuildBattleCatalog planta = planta2();
        int notas = planta.notas();
        int nota = evento.getNewSlot() + 1;
        if (nota < 1 || nota > notas) {
            return;
        }
        jogador.sendActionBar(plugin.mini().deserialize("<gray>a sua nota: "
                + "<color:" + corDoRecado(nota, notas) + ">" + nota + " — "
                + recadoDaNota(nota, notas) + "</color> <gray>(ninguém mais "
                + "vê)</gray>"));
        jogador.playSound(jogador.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.8f + nota * 0.12f);
    }

    /**
     * O menu criativo fica fechado para fora da paleta.
     *
     * É aqui que a paleta curada deixa de ser sugestão e passa a ser regra: em
     * criativo, o menu e o clique do meio entregam qualquer bloco do jogo, e
     * sem esta trava os 36 blocos seriam só a primeira página de mil.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoPegarNoCriativo(InventoryCreativeEvent evento) {
        if (!(evento.getWhoClicked() instanceof Player jogador)) {
            return;
        }
        if (!naArena(jogador) || jogador.hasPermission("bigacore.admin")) {
            return;
        }
        ItemStack item = evento.getCursor();
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (paleta.contains(item.getType())) {
            return;
        }
        evento.setCancelled(true);
        jogador.sendActionBar(plugin.mini().deserialize(
                "<color:#FF8A8A>fora da paleta</color> <gray>— a paleta é de "
                        + paleta.size() + " blocos e cabe inteira no seu "
                        + "inventário</gray>"));
    }

    // ------------------------------------------------------------------
    //  A caixa do lote
    // ------------------------------------------------------------------

    @Override
    protected boolean permiteColocar(BlockPlaceEvent evento) {
        return podeMexer(evento.getPlayer(), evento.getBlock(),
                evento.getBlock().getType());
    }

    @Override
    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        return podeMexer(evento.getPlayer(), evento.getBlock(), null);
    }

    /**
     * Só no próprio lote, só acima do chão, só até o teto, só com a paleta.
     *
     * O teto existe porque, sem ele, uma torre até o céu tampa a obra do
     * vizinho na hora de votar — e tampar a obra do vizinho é jogada de
     * atrapalhar num jogo que não pode ter uma.
     */
    private boolean podeMexer(Player jogador, Block bloco, Material material) {
        if (fase != Fase.OBRA || !jogando() || !estaVivo(jogador)) {
            return false;
        }
        Integer meu = loteDe.get(jogador.getUniqueId());
        if (meu == null) {
            return false;
        }
        BuildBattleCatalog planta = planta2();
        if (planta.plotDoPonto(bloco.getX(), bloco.getZ()) != meu) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora do seu lote</color> <gray>— o seu "
                            + "chão é o que está dentro da borda</gray>"));
            return false;
        }
        if (bloco.getY() <= planta.topoY()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>o chão do lote não se mexe</color>"));
            return false;
        }
        if (bloco.getY() > planta.topoY() + planta.alturaDeObra()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>teto da obra</color> <gray>— "
                            + planta.alturaDeObra() + " fiadas, para a sua "
                            + "torre não tampar a obra do vizinho</gray>"));
            return false;
        }
        if (material != null && !paleta.contains(material)) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora da paleta</color>"));
            return false;
        }
        return true;
    }

    /** Quem sai do lote volta para ele. */
    @Override
    protected void aCadaTique(World mundo) {
        if (!jogando() || fase != Fase.OBRA) {
            return;
        }
        BuildBattleCatalog planta = planta2();
        for (Player jogador : jogadoresVivos()) {
            Integer meu = loteDe.get(jogador.getUniqueId());
            if (meu == null) {
                continue;
            }
            Location onde = jogador.getLocation();
            if (!planta.foraDoLote(onde.getX(), onde.getY(), onde.getZ(), meu)) {
                continue;
            }
            jogador.setFallDistance(0f);
            jogador.teleport(BuildBattleBuilder.pontoDoLote(mundo, planta,
                    meu));
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:" + planta.corDoTexto() + ">o seu lote é este"
                            + "</color> <gray>— durante a obra ninguém sai do "
                            + "próprio lote</gray>"));
            jogador.playSound(jogador.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
        }
    }

    private void avisarDaObra(BuildBattleCatalog planta) {
        int falta = Math.max(0, planta.segundosDeObra() - naFase());
        if (falta > 30 && falta % 10 != 0) {
            return;
        }
        String cor = falta <= 10 ? "#FF8A8A" : planta.corDoTexto();
        for (Player jogador : jogadoresVivos()) {
            jogador.sendActionBar(plugin.mini().deserialize("<white>" + tema
                    + "</white> <gray>· <color:" + cor + ">" + falta
                    + "s</color> de obra</gray>"));
        }
    }

    private void lembrarDaNota(BuildBattleCatalog planta) {
        if (naOrdem < 0 || naOrdem >= ordem.size()) {
            return;
        }
        UUID dono = donoDe.get(ordem.get(naOrdem));
        int falta = Math.max(0, planta.segundosDeVoto() - naFase());
        for (Player jogador : jogadoresDaArena()) {
            if (jogador.getUniqueId().equals(dono)) {
                continue;
            }
            int nota = Math.max(1, Math.min(planta.notas(),
                    jogador.getInventory().getHeldItemSlot() + 1));
            jogador.sendActionBar(plugin.mini().deserialize("<gray>a sua nota: "
                    + "<color:" + corDoRecado(nota, planta.notas()) + ">"
                    + nota + "</color> <gray>· " + falta + "s</gray>"));
        }
    }

    private void mostrarOPainel(World mundo, BuildBattleCatalog planta) {
        String linha = switch (fase) {
            case TEMA -> "<gray>prepare-se</gray>";
            case OBRA -> "<gray>" + Math.max(0, planta.segundosDeObra()
                    - naFase()) + "s de obra</gray>";
            case PASSEIO -> "<gray>passeio — ninguém vota ainda</gray>";
            case VOTO -> "<gray>a nota é o item na sua mão</gray>";
            case FIM -> "<gray>resultado</gray>";
        };
        plugin.buildBattleBuilder().painelDaFase(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + tema.toUpperCase() + "</b></color>\n"
                        + linha + "\n<gray>" + paleta.size()
                        + " blocos, sem menu</gray>"));
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    public String temaDaRodada() {
        return tema;
    }

    public int loteDe(UUID id) {
        return loteDe.getOrDefault(id, -1);
    }

    public int votoDe(UUID id, int lote) {
        Map<Integer, Integer> cedula = cedulas.get(id);
        return cedula == null ? 0 : cedula.getOrDefault(lote, 0);
    }
}
