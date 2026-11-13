package codes.biga.bigacore;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * O SKY WARS: uma ilha, um baú, uma chance.
 *
 * <h2>O erro que esta classe já cometeu</h2>
 *
 * A primeira versão não tinha baú nas ilhas. O kit chegava na mão de todo
 * mundo, igual, sorteado por partida, e a intenção era boa: atacar a
 * reclamação mais antiga do gênero, que tem nome próprio nos fóruns dos
 * jogadores — SEE, <i>Skywars Equipment Equality</i>. O resultado era outro
 * jogo. Sem baú não existe o SAQUE, e o saque é os quinze primeiros segundos
 * de toda partida de Sky Wars: cair, correr, abrir, ver o que veio e decidir
 * ali se dá para rushar o vizinho ou se é melhor se fechar. Tirar o baú não
 * consertou a loteria — apagou a abertura da partida.
 *
 * <p>Fica registrado porque o erro é sedutor: cada passo parecia uma melhora
 * e a soma deles era um jogo diferente com o mesmo nome.
 *
 * <h2>Os três defeitos do gênero, e o que fazemos diferente</h2>
 *
 * <ul>
 *   <li><b>"O baú decide a partida."</b> O baú ficou, e a loteria é cercada
 *       como os próprios jogadores do gênero propõem — garantindo o PISO em
 *       vez de apagar o sorteio; a proposta de fórum sobre as pérolas do meio
 *       pede exatamente que a menor quantidade achável seja garantida em pelo
 *       menos um baú. A ESPADA é a mesma em toda ilha, porque a reclamação é
 *       sobre a arma; o resto do baú varia, e é por isso que abrir vale a
 *       pena. Ver {@link SkyWarsCatalog#sortearSaque} e
 *       {@link SkyWarsBuilder#conferirSaque}.</li>
 *   <li><b>"Quem faz ponte rápido pega o baú do meio."</b> A proposta dos
 *       próprios jogadores é uma barreira em volta do meio que cai na hora
 *       marcada, e é o que {@link #abrirOMeio} faz. Chegar primeiro passa a
 *       ganhar POSIÇÃO, não o baú. E a réplica dos fóruns continua valendo e
 *       é boa: fazer ponte se aprende, e ficar sem bloco no meio da ponte é
 *       aperto honesto, não injustiça.</li>
 *   <li><b>"O baú do meio é inútil e chato" / "o do meio já dá vantagem
 *       demais."</b> As duas reclamações existem e são a mesma pergunta. O
 *       meio vale exatamente um nível acima do kit da partida, e
 *       {@link SkyWarsBuilder#conferirNiveis} prende as duas pontas.</li>
 * </ul>
 *
 * <h2>Só se quebra o que alguém colocou</h2>
 *
 * Sem essa regra, a primeira jogada de todo mundo é cavar a própria ilha para
 * virar bloco de ponte, e o anel desaparece antes de a partida começar — e
 * pior, quem cava mais rápido tem mais ponte, o que traz de volta pela porta
 * dos fundos a desigualdade que o piso garantido do baú fechou pela frente.
 * {@link #colocados} guarda o que foi posto e {@link #permiteQuebrar} só
 * deixa quebrar isso. O bloco de ponte é de outra cor justamente para a regra
 * ser visível no chão em vez de ser lida numa placa.
 *
 * <h2>O vazio tem autor</h2>
 *
 * Na referência, quem é empurrado e cai simplesmente some, e ninguém leva o
 * crédito. Aqui o último golpe vale por {@code credito-segundos}: quem
 * empurrou leva o nome e os pontos. É pequeno e muda o fim de toda partida,
 * porque num jogo em que a maioria das mortes é queda, um jogo sem crédito de
 * queda é um jogo sem placar.
 *
 * <h2>O anel cai em ORDEM, dando a volta</h2>
 *
 * Embaralhado, a queda da sua ilha é sorte e o jogador só pode reagir. Dando a
 * volta de um ponto sorteado, ele CONTA quantas faltam para a dele e decide
 * quando sair — e decidir quando sair é a jogada que este fim de partida
 * existe para criar.
 */
public final class SkyWars extends JogoDeArena {

    private final Placar placar = new Placar();
    private final Random sorteio = new Random();

    private final Map<UUID, Integer> vida = new HashMap<>();
    private final Map<UUID, Long> carencia = new HashMap<>();
    private final Map<UUID, Integer> ilhaDe = new HashMap<>();
    private final Map<UUID, Integer> quedas = new HashMap<>();

    /** Quem bateu por último em quem, e quando: o crédito da queda. */
    private final Map<UUID, UUID> agressor = new HashMap<>();
    private final Map<UUID, Long> tiqueDoGolpe = new HashMap<>();

    /** A força do tiro de cada flecha no ar. */
    private final Map<UUID, Double> forcaDaFlecha = new HashMap<>();

    /** Os blocos que alguém colocou: os únicos que podem ser quebrados. */
    private final Set<Long> colocados = new HashSet<>();

    /**
     * De quem é cada poça: posição do bloco de líquido → quem derramou.
     *
     * Existe por causa da mesma regra que dá autor à queda no vazio. Morrer
     * numa lava que alguém despejou na sua ponte é morte com autor, e sem
     * este mapa a lava seria a única coisa da arena que mata e não tem nome
     * — o jogador veria "você caiu" sem saber quem o empurrou.
     */
    private final Map<Long, UUID> deQuemEhAPoca = new HashMap<>();

    private int nivel;
    private int nivelDoMeio;
    private boolean meioAberto;
    private List<Integer> ordemDaQueda = new ArrayList<>();
    private int jaCairam;
    private int avisadas;

    public SkyWars(BigaCore plugin) {
        super(plugin);
    }

    @Override
    protected PlantaDeArena planta() {
        return plugin.skyWarsCatalog();
    }

    private SkyWarsCatalog planta2() {
        return plugin.skyWarsCatalog();
    }

    /** Sobrevivência, porque este é o único jogo daqui em que se constrói. */
    @Override
    protected GameMode modoDeJogo() {
        return GameMode.SURVIVAL;
    }

    @Override
    protected String verboDaLargada() {
        return "ATRAVESSE!";
    }

    @Override
    protected void ajustesDoMundo(World mundo) {
        mundo.setPVP(true);
        mundo.setGameRule(GameRule.NATURAL_REGENERATION, false);
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
        SkyWarsCatalog planta = planta2();
        SkyWarsBuilder obra = plugin.skyWarsBuilder();

        placar.zerar();
        vida.clear();
        carencia.clear();
        ilhaDe.clear();
        quedas.clear();
        agressor.clear();
        tiqueDoGolpe.clear();
        forcaDaFlecha.clear();
        meioAberto = false;
        jaCairam = 0;
        avisadas = 0;

        apagarAsPontes(mundo);
        secarAsPocas(mundo);
        limparFlechas(mundo);
        for (int k = 0; k < planta.ilhas(); k++) {
            obra.refazerIlha(mundo, planta, k);
        }
        obra.fecharOMeio(mundo, planta);

        nivel = SkyWarsCatalog.nivelDaPartida(planta.quantosNiveis(), sorteio);
        nivelDoMeio = SkyWarsCatalog.nivelDoMeio(nivel, planta.quantosNiveis());
        encherAsIlhas(mundo, planta);
        encherOsBaus(mundo, planta);

        ordemDaQueda = SkyWarsCatalog.ordemDaQueda(planta.ilhas(),
                sorteio.nextInt(Math.max(1, planta.ilhas())));
    }

    @Override
    protected void largar(World mundo, List<Player> entram) {
        SkyWarsCatalog planta = planta2();
        List<Integer> ilhas = new ArrayList<>();
        for (int k = 0; k < planta.ilhas(); k++) {
            ilhas.add(k);
        }
        Collections.shuffle(ilhas, sorteio);

        for (int k = 0; k < entram.size() && k < ilhas.size(); k++) {
            Player jogador = entram.get(k);
            UUID id = jogador.getUniqueId();
            int ilha = ilhas.get(k);
            ilhaDe.put(id, ilha);
            jogador.teleport(SkyWarsBuilder.pontoDaIlha(mundo, planta, ilha));
            jogador.setFallDistance(0f);
            esvaziar(jogador);
            vida.put(id, planta.vidaCheia());
            quedas.put(id, 0);
            placar.entrar(id);
            mostrarVida(jogador);
        }
    }

    @Override
    protected void instrucoes(Player jogador) {
        SkyWarsCatalog planta = planta2();
        dizer(jogador, "<gray>você larga <white>sem nada</white>: o saque "
                + "está nos <white>" + planta.bausDaIlha() + " baú(s)</white> "
                + "da sua ilha. A <white>espada</white> é a mesma em toda "
                + "ilha na mesma partida; o resto do baú varia — mas tem "
                + "<white>piso garantido</white>: espada, comida, uma peça "
                + "de armadura e blocos para a ponte.</gray>");
        dizer(jogador, "<gray>e pode vir <white>ferramenta</white>, que é o "
                + "que se usa no outro: o <color:#6FC3FF>balde d'água</color> "
                + "lava o inimigo para fora da ponte sem encostar nele, o "
                + "<color:#FF8A3D>balde de lava</color> fecha a ponte e cobra "
                + "<white>" + planta.danoDaLava() + "</white> por segundo de "
                + "quem atravessar, e a <color:#DCE9FF>bola de neve</color> "
                + "empurra de longe sem tirar vida nenhuma. As duas poças "
                + "<white>secam em " + planta.baldeSegundos() + "s</white>.</gray>");
        dizer(jogador, "<gray>o baú do <white>meio</white> tem a espada do "
                + "nível de cima e o <white>conjunto de armadura</white>, e "
                + "só abre quando a <white>cúpula cair</white>, em <white>"
                + planta.segundosParaOMeio() + "s</white> — chegar antes "
                + "ganha posição, não o baú.</gray>");
        dizer(jogador, "<gray>só se quebra o <white>bloco que alguém "
                + "colocou</white>: as ilhas não se mineram. E em <white>"
                + planta.segundosDeCerco() + "s</white> o anel começa a cair, "
                + "uma ilha de cada vez, <white>dando a volta</white>.</gray>");
    }

    @Override
    protected void aoComecarRodada(World mundo) {
        SkyWarsCatalog planta = planta2();
        // O que sobrou aqui é o que só esta rodada sabe: que espada saiu.
        // As regras do jogo são ditas no saguão, em instrucoes().
        anunciar("<color:" + planta.corDoTexto() + ">"
                + nomeBonito(planta.espadaDoNivel(nivel)) + "</color>"
                + "<gray> é a espada desta partida \u2014 igual em toda ilha. "
                + "No meio, <white>"
                + nomeBonito(planta.espadaDoNivel(nivelDoMeio))
                + "</white> e a armadura, em <white>"
                + planta.segundosParaOMeio() + "s</white></gray>");
        mostrarOPainel(mundo, planta);
    }

    @Override
    protected Location pontoDaPlateia(World mundo) {
        return SkyWarsBuilder.pontoDaPlateia(mundo, planta2());
    }

    @Override
    protected void aoEntrarNaArena(Player jogador) {
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoSairDaArena(UUID id) {
        placar.sair(id);
        vida.remove(id);
        carencia.remove(id);
        ilhaDe.remove(id);
        quedas.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
    }

    /**
     * Quem saiu da rodada devolve o kit — e a queda ganha autor.
     *
     * É aqui que o crédito do vazio acontece, porque a queda no vazio passa
     * pelo {@code eliminar} do arcabouço e não por esta classe: quem bateu há
     * pouco leva o nome. O anúncio é um ACRÉSCIMO ao "caiu" do arcabouço, e
     * não uma troca — "Y caiu" e depois "foi X quem empurrou" é a ordem em que
     * a coisa aconteceu.
     */
    @Override
    protected void aoEliminarJogador(Player jogador) {
        UUID id = jogador.getUniqueId();
        SkyWarsCatalog planta = planta2();
        UUID quem = agressor.get(id);
        long quando = tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE);
        boolean recente = tiquesDeJogo() - quando
                <= planta.creditoSegundos() * 20L;

        if (quem != null && recente && vida.containsKey(id)) {
            creditar(quem, jogador, planta, true);
        }
        vida.remove(id);
        carencia.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
        jogador.getInventory().clear();
        jogador.getInventory().setArmorContents(null);
        devolverAVidaDoMinecraft(jogador);
    }

    @Override
    protected void aoTerminarRodada() {
        World mundo = mundoDaArena();
        for (Player jogador : jogadoresDaArena()) {
            jogador.getInventory().clear();
            jogador.getInventory().setArmorContents(null);
            devolverAVidaDoMinecraft(jogador);
        }
        if (mundo != null) {
            limparFlechas(mundo);
            ArenaComum.limparPaineis(mundo, SkyWarsBuilder.TAG_AR);
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
            return "ninguém sobrou";
        }
        int quantas = quedas.getOrDefault(quem, 0);
        return quantas + (quantas == 1 ? " derrubado" : " derrubados")
                + " · kit " + nomeBonito(planta2().espadaDoNivel(nivel));
    }

    @Override
    protected float progressoDaBarra() {
        return ArenaComum.limite(segundosDeJogo()
                / (float) Math.max(1, planta2().tempoLimite()));
    }

    @Override
    protected String textoDaBarra() {
        SkyWarsCatalog planta = planta2();
        if (!meioAberto) {
            return "<color:" + planta.corDoTexto() + ">o meio abre em "
                    + Math.max(0, planta.segundosParaOMeio()
                            - segundosDeJogo()) + "s</color>";
        }
        if (segundosDeJogo() < planta.segundosDeCerco()) {
            return "<white>" + quantosVivos() + "</white> <gray>no ar · o "
                    + "anel cai em " + (planta.segundosDeCerco()
                            - segundosDeJogo()) + "s</gray>";
        }
        return "<white>" + quantosVivos() + "</white> <gray>no ar · <white>"
                + Math.max(0, planta.ilhas() - jaCairam) + "</white> ilhas de "
                + "pé</gray>";
    }

    // ------------------------------------------------------------------
    //  O kit e o baú
    // ------------------------------------------------------------------

    /**
     * Larga de mãos vazias, que é como se larga num Sky Wars.
     *
     * A versão anterior entregava o kit na mão. Era bem-intencionado e era
     * outro jogo: sem nada no inventário, a primeira decisão da partida passa
     * a ser para qual baú correr, e é essa decisão que abre a partida no
     * gênero. Limpa a armadura também — sobra de rodada anterior vestida é
     * vantagem que ninguém saqueou.
     */
    private void esvaziar(Player jogador) {
        jogador.getInventory().clear();
        jogador.getInventory().setArmorContents(null);
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
        jogador.updateInventory();
    }

    private ItemStack marcar(Material qual, int quantos,
                             SkyWarsCatalog planta, String nome) {
        ItemStack item = new ItemStack(qual, quantos);
        ItemMeta marca = item.getItemMeta();
        if (marca != null) {
            marca.displayName(plugin.mini().deserialize("<color:"
                    + planta.corDoTexto() + ">" + nome + "</color>"));
            item.setItemMeta(marca);
        }
        return item;
    }

    /**
     * Enche os baús de cada ilha do anel, um sorteio por baú.
     *
     * Um sorteio por BAÚ e não por ilha: com dois baús na ilha, abrir um e
     * depois o outro tem de revelar duas coisas diferentes, senão o segundo
     * baú é só um clique a mais. E o piso vem garantido nos dois — quem abriu
     * um já tem espada, comida, armadura e ponte para sair dali, e o segundo
     * é o que ele ganha por gastar mais três segundos na própria ilha em vez
     * de correr.
     */
    private void encherAsIlhas(World mundo, SkyWarsCatalog planta) {
        int y = planta.topoY() + 1;
        for (int k = 0; k < planta.ilhas(); k++) {
            for (int[] onde : planta.pontosDosBausDaIlha(k)) {
                Block bloco = mundo.getBlockAt(onde[0], y, onde[1]);
                if (!(bloco.getState() instanceof Chest arca)) {
                    continue;
                }
                Inventory dentro = arca.getBlockInventory();
                dentro.clear();
                porOSaque(dentro, planta, planta.sortearSaque(nivel, sorteio));
            }
        }
    }

    /** Põe um saque sorteado dentro de um baú, sempre na mesma ordem. */
    private void porOSaque(Inventory dentro, SkyWarsCatalog planta,
                           SkyWarsCatalog.Saque saque) {
        dentro.setItem(0, marcar(saque.espada(), 1, planta, "Espada do Céu"));
        dentro.setItem(2, new ItemStack(planta.blocoDePonte(), saque.blocos()));
        int slot = 3;
        for (Material peca : saque.armadura()) {
            dentro.setItem(slot++, new ItemStack(peca, 1));
        }
        if (saque.arco()) {
            dentro.setItem(7, marcar(Material.BOW, 1, planta, "Arco do Céu"));
            dentro.setItem(8, new ItemStack(Material.ARROW, saque.flechas()));
        }
        if (saque.curas() > 0) {
            dentro.setItem(13, new ItemStack(planta.itemDeCura(), saque.curas()));
        }
        if (saque.agua()) {
            dentro.setItem(9, marcar(Material.WATER_BUCKET, 1, planta,
                    "Balde d'Água"));
        }
        if (saque.lava()) {
            dentro.setItem(11, marcar(Material.LAVA_BUCKET, 1, planta,
                    "Balde de Lava"));
        }
        if (saque.bolas() > 0) {
            dentro.setItem(15, marcar(Material.SNOWBALL, saque.bolas(), planta,
                    "Bolas de Neve"));
        }
        dentro.setItem(17, new ItemStack(planta.itemDeComida(), planta.comida()));
    }

    /**
     * Enche os baús do meio, todos com o MESMO conteúdo.
     *
     * Iguais entre si de propósito: se o primeiro baú tivesse a espada e o
     * segundo tivesse flecha, chegar primeiro voltaria a decidir a partida — e
     * a cúpula existe justamente para chegar primeiro não decidir nada.
     *
     * <p>E o meio é o único lugar que dá o CONJUNTO COMPLETO de armadura. É
     * o que ele tem para oferecer a quem já saqueou a própria ilha: a ilha dá
     * uma ou duas peças, o meio dá as quatro e um nível acima. Sem isso, quem
     * abriu um baú de sorte não teria por que atravessar.
     */
    private void encherOsBaus(World mundo, SkyWarsCatalog planta) {
        int y = planta.topoY() + 1;
        for (int[] onde : planta.pontosDosBaus()) {
            Block bloco = mundo.getBlockAt(onde[0], y, onde[1]);
            if (!(bloco.getState() instanceof Chest arca)) {
                continue;
            }
            Inventory dentro = arca.getBlockInventory();
            dentro.clear();
            dentro.setItem(0, marcar(planta.espadaDoNivel(nivelDoMeio), 1,
                    planta, "Espada do Meio"));
            dentro.setItem(2, new ItemStack(planta.blocoDePonte(),
                    Math.max(1, planta.blocosDoNivel(nivelDoMeio))));
            int slot = 3;
            for (Material peca : SkyWarsCatalog.pecasDaFamilia(
                    planta.armaduraDoNivel(nivelDoMeio))) {
                dentro.setItem(slot++, new ItemStack(peca, 1));
            }
            int flechas = planta.flechasDoNivel(nivelDoMeio);
            if (flechas > 0) {
                dentro.setItem(8, marcar(Material.BOW, 1, planta,
                        "Arco do Meio"));
                dentro.setItem(9, new ItemStack(Material.ARROW, flechas));
            }
            if (planta.curasNoBau() > 0) {
                dentro.setItem(13, new ItemStack(planta.itemDeCura(),
                        planta.curasNoBau()));
            }
            // A pérola só existe aqui. É o que o meio tem a oferecer a quem
            // já saqueou a própria ilha e precisa de um motivo para
            // atravessar: a carta de fuga, que ninguém do anel tem como
            // responder.
            if (planta.perolasNoMeio() > 0) {
                dentro.setItem(11, marcar(Material.ENDER_PEARL,
                        planta.perolasNoMeio(), planta, "Pérola do Meio"));
            }
            dentro.setItem(15, marcar(Material.WATER_BUCKET, 1, planta,
                    "Balde d'Água"));
            dentro.setItem(17, marcar(Material.SNOWBALL,
                    planta.bolasDeNeve(), planta, "Bolas de Neve"));
        }
    }

    /** O nível da espada na mão: é ela que diz quanto o golpe tira. */
    private int nivelDaEspada(Material qual) {
        SkyWarsCatalog planta = planta2();
        List<String> espadas = planta.nivelEspada();
        for (int k = espadas.size() - 1; k >= 0; k--) {
            Material dela = Material.matchMaterial(
                    espadas.get(k).trim().toUpperCase());
            if (dela == qual) {
                return k;
            }
        }
        return -1;
    }

    private static String nomeBonito(Material qual) {
        return qual.name().toLowerCase().replace('_', ' ');
    }

    // ------------------------------------------------------------------
    //  O golpe
    // ------------------------------------------------------------------

    /**
     * O golpe: a espada na mão diz o dano, e a prontidão diz o resto.
     *
     * A conta da prontidão é {@link ArenaPvpCatalog#forcaDoGolpe}, a mesma da
     * ARENA PVP, e a carência do alvo é igual ao período — não por economia
     * de código, mas porque um jogador que aprendeu o tempo do golpe numa
     * arena não pode ter de reaprender na outra.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void aoBater(EntityDamageByEntityEvent evento) {
        if (!(evento.getEntity() instanceof Player alvo)
                || !(evento.getDamager() instanceof Player quemBateu)) {
            return;
        }
        if (!jogando() || !naArena(alvo) || !naArena(quemBateu)) {
            return;
        }
        if (!estaVivo(alvo) || !estaVivo(quemBateu) || alvo.equals(quemBateu)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        UUID id = alvo.getUniqueId();

        // Sem espada na mão o soco machuca, pouco. Existe porque agora todo
        // mundo larga sem nada: quem for rushado antes de abrir o baú tem de
        // poder revidar. `conferirSaque` exige que o soco valha menos da
        // metade da pior espada, para abrir o baú continuar sendo a primeira
        // coisa a fazer.
        int qualNivel = nivelDaEspada(
                quemBateu.getInventory().getItemInMainHand().getType());
        int base = qualNivel < 0 ? planta.danoDoSoco()
                : planta.danoDoNivel(qualNivel);
        String como = qualNivel < 0 ? "soco" : "espada";

        if (carencia.getOrDefault(id, 0L) > tiquesDeJogo()) {
            return;
        }
        double prontidao = quemBateu.getAttackCooldown();
        carencia.put(id, tiquesDeJogo() + planta.periodoDoGolpe());

        int dano = Math.max(1, (int) Math.round(base
                * ArenaPvpCatalog.forcaDoGolpe(prontidao)));
        aplicarDano(alvo, quemBateu, planta, dano, prontidao, como);
        empurrar(quemBateu, alvo, planta, prontidao);
    }

    /** O tiro fraco é recusado: mesma regra do ARCO E FLECHA. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoAtirar(EntityShootBowEvent evento) {
        if (!(evento.getEntity() instanceof Player jogador)) {
            return;
        }
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        if (evento.getForce() < planta.forcaMinima()) {
            evento.setCancelled(true);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>tiro fraco — recusado</color> <gray>· "
                            + "puxe mais o arco</gray>"));
            return;
        }
        if (evento.getProjectile() instanceof AbstractArrow flecha) {
            flecha.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            flecha.setPersistent(false);
            forcaDaFlecha.put(flecha.getUniqueId(),
                    (double) evento.getForce());
        }
    }

    /**
     * A flecha acertou, e o dano é proporcional à puxada.
     *
     * A força é guardada no tiro e lida aqui, e não deduzida da velocidade no
     * ar: a velocidade muda com a queda e com o vento do próprio voo, e um
     * tiro cheio que viajou longe chegaria valendo menos — o que premiaria
     * atirar de perto, que é o contrário do que um arco deve premiar.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void aoAcertar(ProjectileHitEvent evento) {
        if (!(evento.getEntity() instanceof AbstractArrow flecha)) {
            return;
        }
        if (!jogando() || !nesteMundo(flecha.getWorld())) {
            return;
        }
        double forca = forcaDaFlecha.getOrDefault(flecha.getUniqueId(), 1.0);
        if (!(evento.getHitEntity() instanceof Player alvo)) {
            if (evento.getHitBlock() != null) {
                forcaDaFlecha.remove(flecha.getUniqueId());
                flecha.remove();
            }
            return;
        }
        forcaDaFlecha.remove(flecha.getUniqueId());
        flecha.remove();
        Player dono = flecha.getShooter() instanceof Player quem ? quem : null;
        if (dono == null || alvo.equals(dono) || !estaVivo(alvo)
                || !estaVivo(dono)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        int dano = Math.max(1, (int) Math.round(planta.danoDaFlecha() * forca));
        aplicarDano(alvo, dono, planta, dano, forca, "flecha");
    }

    /** Tira a vida, guarda o crédito e derruba se zerou. */
    /**
     * Quanto a armadura vestida apara, em fração do dano.
     *
     * Lê a peça de verdade no corpo e acha a família dela na lista de níveis.
     * É assim e não por um número guardado num mapa porque a peça pode ter
     * vindo do baú da ilha, do baú do meio ou do chão de alguém que caiu — o
     * corpo é a única fonte que sabe de todas as três.
     *
     * <p>Do melhor nível para o pior, para o caso de uma família ser prefixo
     * de outra num yml editado à mão: ganha a melhor que casar, nunca a que
     * casar por acaso.
     */
    private double blindagemDe(Player alvo, SkyWarsCatalog planta) {
        List<String> familias = planta.nivelArmadura();
        int soma = 0;
        for (ItemStack peca : alvo.getInventory().getArmorContents()) {
            if (peca == null || peca.getType().isAir()) {
                continue;
            }
            String nome = peca.getType().name();
            for (int k = familias.size() - 1; k >= 0; k--) {
                String familia = familias.get(k).trim()
                        .toUpperCase(java.util.Locale.ROOT);
                if (nome.startsWith(familia + "_")) {
                    soma += planta.reducaoDoNivel(k);
                    break;
                }
            }
        }
        return SkyWarsCatalog.aparo(soma, planta.reducaoMaxima());
    }

    private void aplicarDano(Player alvo, Player dono, SkyWarsCatalog planta,
                             int bruto, double forca, String como) {
        UUID id = alvo.getUniqueId();
        double blindagem = blindagemDe(alvo, planta);
        int dano = SkyWarsCatalog.danoComArmadura(bruto, blindagem);
        int sobrou = Math.max(0,
                vida.getOrDefault(id, planta.vidaCheia()) - dano);
        vida.put(id, sobrou);
        agressor.put(id, dono.getUniqueId());
        tiqueDoGolpe.put(id, tiquesDeJogo());
        placar.somar(dono.getUniqueId(), dano);
        mostrarVida(alvo);

        String cor = forca >= 0.9 ? "#8BE04E"
                : forca >= 0.6 ? "#FFD93D" : "#FF8A8A";
        String aparou = blindagem <= 0 ? ""
                : " <gray>(a armadura aparou <white>"
                        + (int) Math.round(blindagem * 100) + "%</white>)</gray>";
        dono.sendActionBar(plugin.mini().deserialize("<color:" + cor + ">"
                + (int) Math.round(forca * 100) + "% de " + como
                + "</color> <gray>— tirou <white>" + dano + "</white> de "
                + HubBuilder.escapar(alvo.getName()) + ", restam <white>"
                + sobrou + "</white></gray>" + aparou));
        alvo.sendActionBar(plugin.mini().deserialize("<color:#FF8A8A>-" + dano
                + "</color> <gray>de " + HubBuilder.escapar(dono.getName())
                + " · restam <white>" + sobrou + "</white></gray>"));
        animarGolpe(alvo, dono);
        alvo.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                alvo.getLocation().add(0, 1, 0), 4, 0.3, 0.4, 0.3, 0.05);

        if (sobrou <= 0) {
            creditar(dono.getUniqueId(), alvo, planta, false);
            derrubar(alvo, dono, planta);
        }
    }

    /** O crédito de uma queda: pontos e nome a quem empurrou ou bateu. */
    private void creditar(UUID quem, Player alvo, SkyWarsCatalog planta,
                          boolean noVazio) {
        placar.somar(quem, planta.pontosPorQueda());
        int quantas = quedas.merge(quem, 1, Integer::sum);
        Player dono = Bukkit.getPlayer(quem);
        if (dono == null) {
            return;
        }
        if (noVazio) {
            anunciar("<gray>foi <color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName())
                    + "</color> quem empurrou <white>"
                    + HubBuilder.escapar(alvo.getName()) + "</white></gray>");
            dono.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>"
                    + HubBuilder.escapar(alvo.getName()) + " caiu no vazio"
                    + "</color> <gray>— o crédito é seu · " + quantas
                    + " no total</gray>"));
            dono.playSound(dono.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
                    0.5f, 1.6f);
        }
    }

    /**
     * Derrubado pela espada ou pela flecha, e não caído no vazio.
     *
     * Não passa pelo {@code eliminar} do arcabouço porque ele diz "você caiu",
     * que é a verdade de quem foi para o vazio e a coisa errada de quem perdeu
     * uma luta no ar.
     */
    private void derrubar(Player alvo, Player dono, SkyWarsCatalog planta) {
        vivos().remove(alvo.getUniqueId());
        alvo.setFallDistance(0f);
        alvo.setGameMode(GameMode.SPECTATOR);
        alvo.teleport(pontoDaPlateia(alvo.getWorld()));

        UUID id = alvo.getUniqueId();
        vida.remove(id);
        carencia.remove(id);
        agressor.remove(id);
        tiqueDoGolpe.remove(id);
        alvo.getInventory().clear();
        alvo.getInventory().setArmorContents(null);
        devolverAVidaDoMinecraft(alvo);

        alvo.showTitle(Title.title(
                plugin.mini().deserialize("<color:#FF8A8A>DERRUBADO</color>"),
                plugin.mini().deserialize("<gray>por <white>"
                        + HubBuilder.escapar(dono.getName())
                        + "</white></gray>")));
        alvo.playSound(alvo.getLocation(), Sound.ENTITY_GENERIC_EXPLODE,
                0.5f, 1.4f);
        if (comecouCom() > 1) {
            anunciar("<color:" + planta.corDoTexto() + ">"
                    + HubBuilder.escapar(dono.getName()) + "</color> <gray>"
                    + "derrubou <white>" + HubBuilder.escapar(alvo.getName())
                    + "</white> — faltam <white>" + quantosVivos()
                    + "</white></gray>");
        }
        mostrarOPainel(alvo.getWorld(), planta);
    }

    /**
     * O empurrão do golpe, com a força DESTA arena.
     *
     * Era um {@code setVelocity} escrito aqui dentro, e ele nunca chegava ao
     * jogador: velocidade escrita de dentro do evento de dano é sobrescrita
     * pela repulsa do Minecraft ou zerada junto com o evento cancelado. Num
     * jogo cuja arena inteira é uma fileira de blocos sobre o vazio, isso não
     * era um detalhe de polimento — era a metade do jogo que não acontecia.
     * Quem aplica agora é o arcabouço, no tique SEGUINTE; a escala pela
     * prontidão continua sendo daqui.
     */
    private void empurrar(Player quemBateu, Player alvo,
                          SkyWarsCatalog planta, double prontidao) {
        empurrar(alvo, quemBateu.getLocation(),
                ArenaPvpCatalog.empurraoDoGolpe(prontidao, planta.empurrao()),
                ArenaPvpCatalog.empurraoDoGolpe(prontidao,
                        planta.empurraoAltura()));
    }

    // ------------------------------------------------------------------
    //  A cura do baú
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoUsarCura(PlayerInteractEvent evento) {
        if (evento.getHand() != EquipmentSlot.HAND
                || (evento.getAction() != Action.RIGHT_CLICK_AIR
                        && evento.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player jogador = evento.getPlayer();
        if (!jogando() || !estaVivo(jogador)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        ItemStack item = evento.getItem();
        if (item == null || item.getType() != planta.itemDeCura()) {
            return;
        }
        evento.setCancelled(true);
        UUID id = jogador.getUniqueId();
        int antes = vida.getOrDefault(id, planta.vidaCheia());
        if (antes >= planta.vidaCheia()) {
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<gray>a sua vida já está cheia</gray>"));
            return;
        }
        int depois = Math.min(planta.vidaCheia(), antes + planta.curaDoItem());
        vida.put(id, depois);
        item.setAmount(item.getAmount() - 1);
        jogador.updateInventory();
        mostrarVida(jogador);
        jogador.sendActionBar(plugin.mini().deserialize("<color:#8BE04E>+"
                + (depois - antes) + " de vida</color> <gray>· agora "
                + depois + "/" + planta.vidaCheia() + "</gray>"));
        jogador.playSound(jogador.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f,
                1.4f);
        jogador.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                jogador.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.2);
    }

    // ------------------------------------------------------------------
    //  A ponte
    // ------------------------------------------------------------------

    /**
     * Colocar só o bloco de ponte, e só dentro da arena.
     *
     * Só o bloco de ponte porque o kit não tem outro e porque, se tivesse, a
     * cor de "o que se pode quebrar" deixaria de ser uma cor só e a regra
     * sairia do chão.
     */
    @Override
    protected boolean permiteColocar(BlockPlaceEvent evento) {
        if (!jogando() || !estaVivo(evento.getPlayer())) {
            return false;
        }
        SkyWarsCatalog planta = planta2();
        Block bloco = evento.getBlock();
        if (bloco.getType() != planta.blocoDePonte()) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<gray>só o bloco de ponte se coloca aqui</gray>"));
            return false;
        }
        int alcance = planta.ilhaAnel() + planta.ilhaRaio() + 4;
        if (Math.abs(bloco.getX()) > alcance || Math.abs(bloco.getZ()) > alcance
                || bloco.getY() <= planta.quedaY()
                || bloco.getY() > planta.topoY() + planta.marcoAltura()) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora da arena</color>"));
            return false;
        }
        colocados.add(ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ()));
        return true;
    }

    /** Só se quebra o que alguém colocou: ver o cabeçalho da classe. */
    @Override
    protected boolean permiteQuebrar(BlockBreakEvent evento) {
        if (!jogando() || !estaVivo(evento.getPlayer())) {
            return false;
        }
        Block bloco = evento.getBlock();
        long chave = ArenaComum.chave(bloco.getX(), bloco.getY(),
                bloco.getZ());
        if (!colocados.remove(chave)) {
            evento.getPlayer().sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>as ilhas não se mineram</color> <gray>— "
                            + "só se quebra o que alguém colocou</gray>"));
            return false;
        }
        return true;
    }

    /** Apaga toda ponte feita na rodada passada. */
    private void apagarAsPontes(World mundo) {
        SkyWarsCatalog planta = planta2();
        Material ponte = planta.blocoDePonte();
        for (long chave : colocados) {
            int x = ArenaComum.daChave(chave, 0);
            int y = ArenaComum.daChave(chave, 1);
            int z = ArenaComum.daChave(chave, 2);
            Block bloco = mundo.getBlockAt(x, y, z);
            if (bloco.getType() == ponte) {
                bloco.setType(Material.AIR, false);
            }
        }
        colocados.clear();
    }

    // ------------------------------------------------------------------
    //  AS FERRAMENTAS: o balde, a lava e a bola de neve
    //
    //  Vieram do estudo do SkyWarsReloaded, cujo baú garantido traz balde
    //  d'água e balde de lava, e cujo grupo de 40% traz bolas de neve. Nossa
    //  arena tinha o equipamento todo e nenhuma ferramenta: dava para bater
    //  no outro e não dava para fazer nada COM o mapa.
    // ------------------------------------------------------------------

    /**
     * O balde derramado: fica registrado, e seca sozinho.
     *
     * As duas coisas são obrigatórias, e por motivos diferentes.
     *
     * <p>REGISTRADO porque este é o único jogo com a regra "só se quebra o
     * que alguém colocou" e porque a lava mata: morrer numa poça que alguém
     * despejou na sua ponte é morte com autor, e sem o registro a lava seria
     * a única coisa da arena que mata sem nome.
     *
     * <p>SECA porque este mundo não tem chão. Água derramada na beira de uma
     * ilha escorre para o vazio e continua escorrendo para sempre — nenhuma
     * reconstrução de ilha alcança uma coluna de água pendurada vinte blocos
     * abaixo dela. Em três partidas o mapa estaria irreconhecível.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void aoDerramarBalde(PlayerBucketEmptyEvent evento) {
        Player jogador = evento.getPlayer();
        if (!nesteMundo(jogador.getWorld())) {
            return;
        }
        if (!jogando() || !estaVivo(jogador)) {
            evento.setCancelled(true);
            return;
        }
        SkyWarsCatalog planta = planta2();
        Block bloco = evento.getBlock();
        int alcance = planta.ilhaAnel() + planta.ilhaRaio() + 4;
        if (Math.abs(bloco.getX()) > alcance || Math.abs(bloco.getZ()) > alcance
                || bloco.getY() <= planta.quedaY()
                || bloco.getY() > planta.topoY() + planta.marcoAltura()) {
            evento.setCancelled(true);
            jogador.sendActionBar(plugin.mini().deserialize(
                    "<color:#FF8A8A>fora da arena</color>"));
            return;
        }
        long chave = ArenaComum.chave(bloco.getX(), bloco.getY(), bloco.getZ());
        deQuemEhAPoca.put(chave, jogador.getUniqueId());
        marcarParaSecar(jogador.getWorld(), chave, planta.baldeSegundos());
        jogador.sendActionBar(plugin.mini().deserialize("<gray>a poça seca em "
                + "<white>" + planta.baldeSegundos() + "s</white></gray>"));
    }

    /** Tira a fonte depois do prazo; o que escorreu drena sozinho. */
    private void marcarParaSecar(World mundo, long chave, int segundos) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (deQuemEhAPoca.remove(chave) == null) {
                return;
            }
            Block bloco = mundo.getBlockAt(ArenaComum.daChave(chave, 0),
                    ArenaComum.daChave(chave, 1), ArenaComum.daChave(chave, 2));
            if (bloco.getType() == Material.WATER
                    || bloco.getType() == Material.LAVA) {
                bloco.setType(Material.AIR, false);
            }
        }, Math.max(1L, segundos * 20L));
    }

    /** No começo de cada rodada: nenhuma poça da rodada passada sobrevive. */
    private void secarAsPocas(World mundo) {
        for (long chave : deQuemEhAPoca.keySet()) {
            Block bloco = mundo.getBlockAt(ArenaComum.daChave(chave, 0),
                    ArenaComum.daChave(chave, 1), ArenaComum.daChave(chave, 2));
            if (bloco.getType() == Material.WATER
                    || bloco.getType() == Material.LAVA) {
                bloco.setType(Material.AIR, false);
            }
        }
        deQuemEhAPoca.clear();
    }

    /**
     * A lava cobra pedágio, uma vez por segundo.
     *
     * Ela precisa de código porque o dano do Minecraft está cancelado nesta
     * arena inteira: a vida é número do plugin e os corações são o mostrador.
     * Sem isto, o balde de lava seria um balde de tinta laranja — fecharia a
     * ponte e deixaria quem atravessasse passar de graça.
     *
     * <p>O crédito vai para quem DERRAMOU, enquanto a poça for dele. É a
     * mesma regra do vazio: toda morte desta arena tem autor.
     */
    private void cobrarALava(SkyWarsCatalog planta) {
        if (planta.danoDaLava() <= 0) {
            return;
        }
        for (Player jogador : new ArrayList<>(jogadoresDaArena())) {
            if (!estaVivo(jogador)) {
                continue;
            }
            Block pes = jogador.getLocation().getBlock();
            Block corpo = pes.getRelative(0, 1, 0);
            Block qual = pes.getType() == Material.LAVA ? pes
                    : corpo.getType() == Material.LAVA ? corpo : null;
            if (qual == null) {
                continue;
            }
            Player dono = donoDaPoca(qual, jogador);
            if (dono != null) {
                aplicarDano(jogador, dono, planta, planta.danoDaLava(), 1.0,
                        "lava");
            } else {
                queimarSemAutor(jogador, planta);
            }
        }
    }

    /**
     * De quem é a lava que está queimando este jogador.
     *
     * Procura a fonte nas vizinhanças e não só no bloco pisado: lava escorre,
     * e quem morre três blocos adiante morreu da mesma poça. Nunca devolve o
     * próprio jogador — quem se queima na própria lava se queimou sozinho, e
     * dar o crédito a si mesmo viraria ponto de graça.
     */
    private Player donoDaPoca(Block qual, Player quemPisou) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    UUID quem = deQuemEhAPoca.get(ArenaComum.chave(
                            qual.getX() + dx, qual.getY() + dy,
                            qual.getZ() + dz));
                    if (quem == null || quem.equals(quemPisou.getUniqueId())) {
                        continue;
                    }
                    Player dono = Bukkit.getPlayer(quem);
                    if (dono != null && estaVivo(dono)) {
                        return dono;
                    }
                }
            }
        }
        return null;
    }

    /** Lava sem dono (a própria, ou a de quem já saiu): queima igual. */
    private void queimarSemAutor(Player jogador, SkyWarsCatalog planta) {
        UUID id = jogador.getUniqueId();
        int sobrou = Math.max(0, vida.getOrDefault(id, planta.vidaCheia())
                - planta.danoDaLava());
        vida.put(id, sobrou);
        mostrarVida(jogador);
        jogador.sendActionBar(plugin.mini().deserialize("<color:#FF8A8A>-"
                + planta.danoDaLava() + " de lava</color> <gray>· restam "
                + "<white>" + sobrou + "</white></gray>"));
        if (sobrou <= 0) {
            eliminar(jogador);
        }
    }

    /**
     * A bola de neve: empurra e não machuca.
     *
     * Ela não tira vida de propósito. Num jogo em que todo mundo anda sobre
     * uma fileira de blocos, empurrar JÁ é o dano — quem levou a bola no meio
     * da ponte não perdeu vida nenhuma e perdeu a partida. Uma bola que
     * também machucasse seria uma espada de dez blocos de alcance.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void aoAcertarBola(ProjectileHitEvent evento) {
        Projectile tiro = evento.getEntity();
        if (!(tiro instanceof Snowball)
                || !(evento.getHitEntity() instanceof Player alvo)
                || !nesteMundo(alvo.getWorld())) {
            return;
        }
        if (!jogando() || !estaVivo(alvo)) {
            return;
        }
        if (!(tiro.getShooter() instanceof Player quemJogou)
                || quemJogou.equals(alvo) || !estaVivo(quemJogou)) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        empurrar(alvo, quemJogou.getLocation(), planta.empurraoDaBolaForca(),
                planta.empurraoDaBolaAlto());
        animarGolpe(alvo, quemJogou, false);
        quemJogou.sendActionBar(plugin.mini().deserialize("<color:#A8D8FF>"
                + "bola em " + HubBuilder.escapar(alvo.getName())
                + "</color> <gray>— sem dano, só empurrão</gray>"));
    }

    private void limparFlechas(World mundo) {
        for (Entity bicho : mundo.getEntities()) {
            if (bicho instanceof AbstractArrow) {
                bicho.remove();
            }
        }
        forcaDaFlecha.clear();
    }

    // ------------------------------------------------------------------
    //  Os corações como mostrador
    // ------------------------------------------------------------------

    private void mostrarVida(Player jogador) {
        int cheia = Math.max(1, planta2().vidaCheia());
        int agora = Math.max(0, vida.getOrDefault(jogador.getUniqueId(), cheia));
        double maximo = vidaMaxima(jogador);
        double quanto = maximo * Math.min(cheia, agora) / (double) cheia;
        jogador.setHealth(Math.min(maximo, Math.max(0.5, quanto)));
    }

    private void devolverAVidaDoMinecraft(Player jogador) {
        jogador.setHealth(vidaMaxima(jogador));
        jogador.setFoodLevel(20);
        jogador.setSaturation(20f);
    }

    private double vidaMaxima(Player jogador) {
        AttributeInstance atributo = jogador.getAttribute(Attribute.MAX_HEALTH);
        return atributo == null ? 20.0 : atributo.getValue();
    }

    // ------------------------------------------------------------------
    //  O relógio: a cúpula e o anel
    // ------------------------------------------------------------------

    @Override
    protected void aCadaSegundo(World mundo) {
        if (!jogando()) {
            return;
        }
        SkyWarsCatalog planta = planta2();
        int agora = segundosDeJogo();

        if (!meioAberto && agora >= planta.segundosParaOMeio()) {
            abrirOMeio(mundo, planta);
        }
        cobrarALava(planta);
        cuidarDoCerco(mundo, planta, agora);
        avisarDoAr(planta, agora);
    }

    private void abrirOMeio(World mundo, SkyWarsCatalog planta) {
        meioAberto = true;
        plugin.skyWarsBuilder().abrirOMeio(mundo, planta);
        tituloEmTodos("<color:" + planta.corDoTexto() + ">O MEIO ABRIU"
                + "</color>", "<gray>" + planta.meioBaus() + " baús iguais, "
                        + nomeBonito(planta.espadaDoNivel(nivelDoMeio))
                        + "</gray>");
        anunciar("<gray>a cúpula caiu: os <white>" + planta.meioBaus()
                + " baús</white> do meio são <white>iguais entre si</white> — "
                + "chegar primeiro não dá baú melhor, dá posição melhor.</gray>");
        for (Player jogador : jogadoresDaArena()) {
            jogador.playSound(jogador.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    1.0f, 0.8f);
        }
        mostrarOPainel(mundo, planta);
    }

    /**
     * O cerco: as ilhas do anel caem uma a uma, dando a volta.
     *
     * Avisa a PRÓXIMA com nome e segundos. Avisar só a próxima e não a lista
     * inteira é de propósito: a ordem é dando a volta, então quem quiser saber
     * quando a sua cai conta as ilhas — e contar é a jogada.
     */
    private void cuidarDoCerco(World mundo, SkyWarsCatalog planta, int agora) {
        if (agora < planta.segundosDeCerco() - planta.avisoSegundos()) {
            return;
        }
        if (jaCairam >= ordemDaQueda.size()) {
            return;
        }
        int quando = planta.segundoDaQueda(jaCairam);
        if (agora >= quando) {
            int qual = ordemDaQueda.get(jaCairam);
            plugin.skyWarsBuilder().derrubarIlha(mundo, planta, qual);
            jaCairam++;
            avisadas = jaCairam;
            anunciar("<color:#FF8A8A>a ilha " + (qual + 1) + " caiu</color> "
                    + "<gray>— faltam <white>"
                    + (planta.ilhas() - jaCairam) + "</white> de pé</gray>");
            for (Player jogador : jogadoresDaArena()) {
                jogador.playSound(jogador.getLocation(),
                        Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.7f);
            }
            mostrarOPainel(mundo, planta);
            return;
        }
        if (avisadas <= jaCairam && quando - agora <= planta.avisoSegundos()) {
            avisadas = jaCairam + 1;
            int qual = ordemDaQueda.get(jaCairam);
            anunciar("<color:#FFD93D>a ilha " + (qual + 1) + " cai em "
                    + (quando - agora) + "s</color> <gray>— o anel cai dando "
                    + "a volta, conte as suas</gray>");
        }
    }

    private void avisarDoAr(SkyWarsCatalog planta, int agora) {
        for (Player jogador : jogadoresVivos()) {
            UUID id = jogador.getUniqueId();
            if (tiquesDeJogo() - tiqueDoGolpe.getOrDefault(id, Long.MIN_VALUE)
                    < 30) {
                continue;
            }
            int sua = vida.getOrDefault(id, planta.vidaCheia());
            int pontes = contarPontes(jogador, planta);
            String aviso = meioAberto
                    ? (agora < planta.segundosDeCerco()
                            ? " <gray>· anel cai em "
                                    + (planta.segundosDeCerco() - agora) + "s</gray>"
                            : " <color:#FF8A8A>· anel caindo</color>")
                    : " <color:" + planta.corDoTexto() + ">· meio abre em "
                            + Math.max(0, planta.segundosParaOMeio() - agora)
                            + "s</color>";
            jogador.sendActionBar(plugin.mini().deserialize("<gray>vida "
                    + "<white>" + sua + "</white>/" + planta.vidaCheia()
                    + " · <white>" + pontes + "</white> blocos · <white>"
                    + quantosVivos() + "</white> no ar</gray>" + aviso));
        }
    }

    private int contarPontes(Player jogador, SkyWarsCatalog planta) {
        int quantos = 0;
        for (ItemStack item : jogador.getInventory().getContents()) {
            if (item != null && item.getType() == planta.blocoDePonte()) {
                quantos += item.getAmount();
            }
        }
        return quantos;
    }

    private void mostrarOPainel(World mundo, SkyWarsCatalog planta) {
        String linha = !meioAberto
                ? "<color:" + planta.corDoTexto() + ">o meio abre em "
                        + Math.max(0, planta.segundosParaOMeio()
                                - segundosDeJogo()) + "s</color>"
                : jaCairam == 0
                        ? "<gray>o anel ainda está de pé</gray>"
                        : "<color:#FF8A8A>" + (planta.ilhas() - jaCairam)
                                + " ilhas de pé</color>";
        plugin.skyWarsBuilder().painelDoAr(mundo, planta,
                plugin.mini().deserialize("<color:" + planta.corDoTexto()
                        + "><b>" + quantosVivos() + " NO AR</b></color>\n"
                        + linha + "\n<gray>espada da partida: "
                        + nomeBonito(planta.espadaDoNivel(nivel))
                        + "</gray>"));
    }

    // ------------------------------------------------------------------
    //  Para quem pergunta de fora
    // ------------------------------------------------------------------

    public int nivelDaRodada() {
        return nivel;
    }

    public int nivelDoBauDoMeio() {
        return nivelDoMeio;
    }

    public int vidaDe(UUID id) {
        return vida.getOrDefault(id, 0);
    }

    public int quedasDe(UUID id) {
        return quedas.getOrDefault(id, 0);
    }

    public boolean oMeioEstaAberto() {
        return meioAberto;
    }

    public int ilhasDePe() {
        return Math.max(0, planta2().ilhas() - jaCairam);
    }
}
