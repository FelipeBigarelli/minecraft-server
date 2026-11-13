package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Levanta a grade do BUILD BATTLE.
 *
 * <h2>As duas conferências que vêm do estudo da referência</h2>
 *
 * {@link #conferirPaleta} guarda a decisão que mais muda o jogo: a paleta
 * cabe no inventário. Com mil blocos e três minutos ganha quem sabe onde as
 * coisas ficam no menu criativo, não quem constrói melhor — e a lista também
 * não pode ter bloco que AGE no mundo (TNT, lava, pistão, spawner), porque
 * obra que age estraga a obra do vizinho e um jogo de construir não pode ter
 * jogada de atrapalhar.
 *
 * <p>{@link #conferirGrade} guarda a descoberta geométrica que custou um
 * desenho inteiro: com quatro fiadas de quatro, a plateia da segunda fiada
 * cai DENTRO do lote da primeira. O vão entre lotes tem meia dúzia de blocos
 * e plateia nenhuma caberia nele a uma distância que mostre a obra — então ou
 * a grade tem no máximo duas fiadas, uma vista de cada lado, ou alguma obra é
 * julgada de um ângulo diferente das outras.
 *
 * <p>{@link #conferirPlateia} fecha a conta do ângulo: de onde se vota, a
 * obra inteira tem de caber na tela sem o votante torcer o pescoço.
 */
public final class BuildBattleBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_build_painel";

    /** O painel da fase, no alto do meio da grade. */
    public static final String TAG_FASE = "bigacore_build_fase";

    /** O painel do lote em julgamento, na plateia dele. */
    public static final String TAG_VOTO = "bigacore_build_voto";

    /** Quantos blocos a paleta precisa ter, no mínimo. */
    private static final int PALETA_MINIMA = 12;

    /** Até quantos graus o votante pode ter de olhar para cima. */
    private static final double SUBIDA_MAXIMA = 50;

    /** Entre que ângulos o lote tem de aparecer na tela do votante. */
    private static final double ABERTURA_MINIMA = 25;
    private static final double ABERTURA_MAXIMA = 80;

    /**
     * Os blocos que não podem entrar na paleta.
     *
     * Não é lista de bloco feio: é lista de bloco que AGE. Todos estes mudam
     * o mundo sozinhos ou por um clique — explodem, escorrem, empurram,
     * queimam, nascem bicho ou rodam comando. Num jogo em que dezesseis
     * pessoas constroem ao mesmo tempo, obra que age no mundo é jogada de
     * atrapalhar, e um jogo de construir não pode ter uma.
     */
    private static final List<String> PROIBIDOS = List.of(
            "TNT", "LAVA", "WATER", "FIRE", "SOUL_FIRE", "LAVA_BUCKET",
            "WATER_BUCKET", "SPAWNER", "TRIAL_SPAWNER", "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "BEDROCK",
            "BARRIER", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW",
            "LIGHT", "END_PORTAL", "END_PORTAL_FRAME", "END_GATEWAY",
            "NETHER_PORTAL", "PISTON", "STICKY_PISTON", "DISPENSER",
            "DROPPER", "HOPPER", "OBSERVER", "REDSTONE_BLOCK", "DRAGON_EGG",
            "BUDDING_AMETHYST", "SCULK_CATALYST", "SCULK_SHRIEKER",
            "RESPAWN_ANCHOR", "BELL", "TURTLE_EGG", "SNIFFER_EGG");

    private final BigaCore plugin;
    private int contador;

    public BuildBattleBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        BuildBattleCatalog planta = plugin.buildBattleCatalog();

        String problema = conferirPaleta(
                planta.config().getStringList("blocos"));
        if (problema == null) {
            problema = conferirGrade(planta.plots(), planta.plotsPorFiada(),
                    planta.plotLado(), planta.plotVao(), planta.limiteDoLote());
        }
        if (problema == null) {
            problema = conferirPlateia(planta.plotLado(),
                    planta.alturaDeObra(), planta.plateiaRecuo(),
                    planta.plateiaAltura(), planta.plateiaLado(),
                    planta.plateiaFundo());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.segundosDeTema(),
                    planta.segundosDeObra(), planta.segundosDePasseio(),
                    planta.segundosDeVoto(), planta.fimSegundos(),
                    planta.maximo(), planta.tempoLimite(),
                    planta.temas().size());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". " + planta.plots() + " lotes de " + planta.plotLado()
                    + "x" + planta.plotLado() + " em " + planta.fiadas()
                    + " fiada(s) de " + planta.plotsPorFiada() + ", passo "
                    + planta.passo() + ", teto de obra "
                    + planta.alturaDeObra() + " fiadas. Plateia a "
                    + planta.plateiaRecuo() + " da beira e "
                    + planta.plateiaAltura() + " de altura: subida "
                    + Math.round(BuildBattleCatalog.anguloDeSubida(
                            planta.alturaDeObra(), planta.plateiaRecuo(),
                            planta.plateiaAltura())) + "° e abertura "
                    + Math.round(BuildBattleCatalog.anguloDeAbertura(
                            planta.plotLado(), planta.plateiaRecuo()))
                    + "°. Paleta de " + planta.blocos().size() + " blocos e "
                    + planta.temas().size() + " temas. Uma partida cheia leva "
                    + planta.duracaoTotal(planta.maximo()) + "s num limite de "
                    + planta.tempoLimite() + "s. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga build desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_FASE);
        ArenaComum.limparPaineis(mundo, TAG_VOTO);
        for (int k = 0; k < planta.plots(); k++) {
            lote(mundo, planta, k);
            plateia(mundo, planta, k);
        }
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDosLotes(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "BUILD BATTLE levantado: " + contador
                + " blocos em " + planta.plots() + " lotes. O spawn do mundo "
                + "foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        BuildBattleCatalog planta = plugin.buildBattleCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_FASE);
        ArenaComum.limparPaineis(mundo, TAG_VOTO);
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return new Resultado(true, "BUILD BATTLE apagado: " + contador
                + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        BuildBattleCatalog planta = plugin.buildBattleCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_FASE);
        ArenaComum.limparPaineis(mundo, TAG_VOTO);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDosLotes(mundo, planta);
        return new Resultado(true, "Letreiros e placas dos lotes refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * A paleta cabe na mão e não tem bloco que age.
     *
     * Ver o cabeçalho da classe. O limite de cima é o inventário do jogador:
     * passar dele faria o jogo ter menu, e menu é o defeito que esta paleta
     * existe para não ter.
     */
    static String conferirPaleta(List<String> nomes) {
        if (nomes.size() < PALETA_MINIMA) {
            return "A paleta tem " + nomes.size() + " blocos e o mínimo é "
                    + PALETA_MINIMA + ": com menos que isso o tema não muda "
                    + "nada, porque toda obra sai da mesma cor.";
        }
        if (nomes.size() > BuildBattleCatalog.CABE_NO_INVENTARIO) {
            return "A paleta tem " + nomes.size() + " blocos e no inventário "
                    + "cabem " + BuildBattleCatalog.CABE_NO_INVENTARIO
                    + ": o resto viraria menu, e menu é justamente o defeito "
                    + "que esta paleta existe para não ter.";
        }
        Set<String> vistos = new LinkedHashSet<>();
        for (String nome : nomes) {
            String limpo = nome.trim().toUpperCase();
            if (!vistos.add(limpo)) {
                return "O bloco " + limpo + " está duas vezes na paleta, e "
                        + "cada repetição é uma vaga de inventário gasta à "
                        + "toa.";
            }
            if (Material.matchMaterial(limpo) == null) {
                return "O bloco " + limpo + " da paleta não existe neste "
                        + "Minecraft: ele sairia da lista em silêncio e o "
                        + "jogador ficaria com uma vaga vazia na mão.";
            }
            if (PROIBIDOS.contains(limpo)) {
                return "O bloco " + limpo + " age no mundo sozinho ou por um "
                        + "clique, e obra que age estraga a obra do vizinho: "
                        + "um jogo de construir não pode ter jogada de "
                        + "atrapalhar.";
            }
        }
        return null;
    }

    /**
     * A grade cabe, e a caixa do lote não alcança o vizinho.
     *
     * O limite de fiadas é a descoberta geométrica deste jogo: a plateia fica
     * a uma dúzia de blocos da beira do lote, e com três ou mais fiadas a
     * plateia de uma cai dentro do lote de outra. O vão entre lotes tem meia
     * dúzia de blocos e plateia nenhuma caberia nele a uma distância que
     * mostre a obra.
     */
    static String conferirGrade(int plots, int porFiada, int lado, int vao,
                                int limite) {
        if (lado % 2 == 0) {
            return "O lote tem lado " + lado + ", que é par: quadrado de lado "
                    + "par não tem bloco no meio, e a conta de \"este bloco é "
                    + "de quem?\" ganha meio bloco de erro em duas das quatro "
                    + "bordas — justamente onde dois lotes se encontram.";
        }
        if (porFiada < 1 || plots < 2) {
            return "A grade tem " + plots + " lotes em fiadas de " + porFiada
                    + ".";
        }
        int fiadas = (int) Math.ceil(plots / (double) porFiada);
        if (fiadas > 2) {
            return "A grade tem " + fiadas + " fiadas, e o máximo é 2: a "
                    + "plateia fica fora da grade, e com três fiadas a "
                    + "plateia da fiada do meio cairia dentro do lote de "
                    + "outra. Duas fiadas, uma vista de cada lado, é o que "
                    + "faz toda obra ser julgada do mesmo ângulo.";
        }
        if (vao < 4) {
            return "O vão entre lotes é de " + vao + " blocos: com menos de "
                    + "quatro as bordas se encostam e ninguém vê onde acaba o "
                    + "lote dele.";
        }
        int meio = (lado - 1) / 2;
        if (limite <= meio) {
            return "O limite do lote é " + limite + " e o meio lote mede "
                    + meio + ": o construtor não poderia nem recuar para "
                    + "olhar a própria obra de longe.";
        }
        int passo = lado + vao;
        if (limite >= passo - meio) {
            return "O limite do lote é " + limite + " e o chão do lote "
                    + "vizinho começa a " + (passo - meio) + " blocos do seu "
                    + "meio: a caixa alcançaria o lote do outro, e a caixa "
                    + "existe exatamente para não alcançar.";
        }
        return null;
    }

    /** De onde se vota, a obra inteira cabe na tela sem torcer o pescoço. */
    static String conferirPlateia(int lado, int alturaDeObra, int recuo,
                                  int plateiaAltura, int plateiaLado,
                                  int plateiaFundo) {
        if (plateiaLado < lado / 3) {
            return "A plateia tem " + plateiaLado + " blocos de largura para "
                    + "um lote de " + lado + ": com dezesseis votantes em "
                    + "cima dela, metade não veria a obra.";
        }
        if (plateiaFundo < 2) {
            return "A plateia tem " + plateiaFundo + " bloco(s) de fundo: não "
                    + "dá para dezesseis pessoas ficarem em pé nela.";
        }
        double subida = BuildBattleCatalog.anguloDeSubida(alturaDeObra, recuo,
                plateiaAltura);
        if (subida > SUBIDA_MAXIMA) {
            return "Da plateia, o topo de uma obra cheia fica a "
                    + Math.round(subida) + "° para cima, e o máximo é "
                    + Math.round(SUBIDA_MAXIMA) + "°: o votante teria de "
                    + "torcer o pescoço para ver a obra inteira, e quem vota "
                    + "sem ver a obra inteira vota no que deu para ver.";
        }
        double abertura = BuildBattleCatalog.anguloDeAbertura(lado, recuo);
        if (abertura < ABERTURA_MINIMA) {
            return "Da plateia, o lote aparece com " + Math.round(abertura)
                    + "° de abertura e o mínimo é "
                    + Math.round(ABERTURA_MINIMA) + "°: de tão longe a obra "
                    + "vira um pontinho e todas as notas ficam iguais.";
        }
        if (abertura > ABERTURA_MAXIMA) {
            return "Da plateia, o lote aparece com " + Math.round(abertura)
                    + "° de abertura e o máximo é "
                    + Math.round(ABERTURA_MAXIMA) + "°: de tão perto não cabe "
                    + "na tela e o votante vê um pedaço da obra.";
        }
        return null;
    }

    /**
     * As fases cabem no relógio, e o passeio existe.
     *
     * O passeio é o conserto do segundo defeito do gênero: quem vota sem ter
     * visto as outras obras vota com medo, e as últimas se beneficiam. Com
     * uma volta de passeio antes da volta do voto, quando você vota no lote 1
     * já viu os dezesseis — então o passeio não é enfeite e a conferência
     * recusa a partida sem ele.
     */
    static String conferirRelogio(int tema, int obra, int passeio, int voto,
                                  int fim, int maximo, int tempoLimite,
                                  int temas) {
        if (temas < 4) {
            return "Há " + temas + " temas na lista: com tão poucos, quem "
                    + "joga duas partidas na mesma tarde já repete, e tema "
                    + "repetido é obra decorada.";
        }
        if (passeio < 1) {
            return "O passeio é de " + passeio + "s por lote: sem passeio, "
                    + "quem vota no primeiro lote vota sem ter visto nenhum "
                    + "outro — e esse é o defeito que faz a primeira obra "
                    + "sempre tomar nota baixa.";
        }
        if (passeio >= voto) {
            return "O passeio é de " + passeio + "s e o voto de " + voto
                    + "s: o passeio é para reconhecer a obra de relance, e se "
                    + "ele durar tanto quanto o voto a partida fica duas "
                    + "vezes mais longa sem precisar.";
        }
        if (obra < 60) {
            return "A obra tem " + obra + "s: abaixo de um minuto não dá para "
                    + "construir nada que mereça nota.";
        }
        int total = BuildBattleCatalog.duracaoTotal(tema, obra, passeio, voto,
                fim, maximo);
        if (total > tempoLimite) {
            return "Uma partida cheia leva " + total + "s (tema " + tema
                    + " + obra " + obra + " + " + maximo + " lotes de "
                    + (passeio + voto) + "s + fim " + fim + ") e o limite é "
                    + tempoLimite + "s: a votação seria cortada no meio e "
                    + "alguns lotes não receberiam nota nenhuma.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /**
     * Um lote: chão de obra, borda em volta e a frente marcada.
     *
     * A borda não é enfeite: ela é a faixa onde {@code permiteColocar} diz
     * não, e o construtor vê a linha em vez de adivinhar onde ela está. A
     * frente é marcada com o bloco de canto no lado da plateia, porque o
     * construtor precisa saber de que lado a obra vai ser julgada — uma
     * fachada virada para o lado errado é a pior surpresa que este jogo pode
     * dar a quem construiu bem.
     */
    private void lote(World mundo, BuildBattleCatalog planta, int k) {
        Material piso = planta.cor("plot-piso", Material.SMOOTH_STONE);
        Material borda = planta.cor("plot-borda", Material.ORANGE_TERRACOTTA);
        Material canto = planta.cor("plot-canto", Material.ORANGE_CONCRETE);
        int[] centro = planta.centroDoPlot(k);
        if (centro == null) {
            return;
        }
        int y = planta.topoY();
        int meio = (planta.plotLado() - 1) / 2;
        int rumo = planta.rumoDaPlateia(k);

        for (int dx = -meio - 1; dx <= meio + 1; dx++) {
            for (int dz = -meio - 1; dz <= meio + 1; dz++) {
                boolean naBorda = Math.abs(dx) > meio || Math.abs(dz) > meio;
                Material qual = piso;
                if (naBorda) {
                    // A frente do lote é a borda do lado da plateia.
                    qual = dz == rumo * (meio + 1) ? canto : borda;
                }
                por(mundo, centro[0] + dx, y, centro[1] + dz, qual);
            }
        }
    }

    /**
     * A plateia: uma caixa de vidro fora da grade, virada para o lote.
     *
     * Fechada com vidro de dois blocos de altura e sem teto. Fechada porque o
     * votante está em aventura sobre o vazio e andar para fora da plataforma
     * seria cair; de vidro porque qualquer parede opaca tiraria dele
     * justamente a obra que ele está julgando; dois blocos porque um bloco se
     * pula.
     */
    private void plateia(World mundo, BuildBattleCatalog planta, int k) {
        Material piso = planta.cor("plateia-piso", Material.POLISHED_ANDESITE);
        Material vidro = planta.cor("plateia-vidro",
                Material.LIGHT_GRAY_STAINED_GLASS);
        Material luz = planta.cor("plateia-luz", Material.SEA_LANTERN);
        int[] onde = planta.pontoDaPlateia(k);
        if (onde == null) {
            return;
        }
        int y = planta.topoY() + planta.plateiaAltura();
        int meio = planta.plateiaLado() / 2;
        int fundo = planta.plateiaFundo();
        int rumo = planta.rumoDaPlateia(k);

        for (int dx = -meio; dx <= meio; dx++) {
            for (int df = 0; df < fundo; df++) {
                int z = onde[1] + rumo * df;
                por(mundo, onde[0] + dx, y, z,
                        df == 0 && dx == 0 ? luz : piso);
            }
        }
        // O vidro em volta, menos onde há chão do lado.
        for (int dx = -meio - 1; dx <= meio + 1; dx++) {
            for (int df = -1; df <= fundo; df++) {
                boolean dentro = Math.abs(dx) <= meio && df >= 0 && df < fundo;
                if (dentro) {
                    continue;
                }
                int z = onde[1] + rumo * df;
                for (int fiada = 1; fiada <= 2; fiada++) {
                    por(mundo, onde[0] + dx, y + fiada, z, vidro);
                }
                por(mundo, onde[0] + dx, y, z, piso);
            }
        }
    }

    /** As placas de cada plateia: o número do lote e as regras do voto. */
    public void placasDosLotes(World mundo, BuildBattleCatalog planta) {
        String cor = planta.corDoTexto();
        for (int k = 0; k < planta.plots(); k++) {
            int[] onde = planta.pontoDaPlateia(k);
            if (onde == null) {
                continue;
            }
            int rumo = planta.rumoDaPlateia(k);
            int y = planta.topoY() + planta.plateiaAltura() + 2;
            int z = onde[1] + rumo * planta.plateiaFundo();
            BlockFace frente = BuildBattleCatalog.frenteDaPlaca(rumo);
            int meio = planta.plateiaLado() / 2;
            ArenaComum.escreverPlaca(mundo,
                    new int[]{onde[0] - meio, y, z}, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:" + cor
                                    + "><b>LOTE " + (k + 1) + "</b></color>"),
                            plugin.mini().deserialize("<white>a nota é o</white>"),
                            plugin.mini().deserialize("<white>item na mão</white>"),
                            plugin.mini().deserialize("<gray>1 a "
                                    + planta.notas() + "</gray>")));
            ArenaComum.escreverPlaca(mundo,
                    new int[]{onde[0] + meio, y, z}, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:" + cor
                                    + "><b>NOTA BAIXA</b></color>"),
                            plugin.mini().deserialize("<white>para todos</white>"),
                            plugin.mini().deserialize("<white>não levanta</white>"),
                            plugin.mini().deserialize("<gray>a sua</gray>")));
        }
    }

    private void torres(World mundo, BuildBattleCatalog planta) {
        Material torre = planta.cor("torre", Material.ORANGE_TERRACOTTA);
        Material luz = planta.cor("torre-luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int longe = planta.beiraDasColunas() + 6;
        int fundo = planta.beiraDasPlateias() + 4;

        int[][] cantos = {{longe, fundo}, {-longe, fundo},
                {longe, -fundo}, {-longe, -fundo}};
        for (int[] onde : cantos) {
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, onde[0], y + alto, onde[1],
                        alto % 5 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, onde[0], y + planta.marcoAltura() + alto, onde[1],
                        rod);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Painéis
    // ------------------------------------------------------------------

    /** O painel da fase, no alto do meio da grade. */
    public void painelDaFase(World mundo, BuildBattleCatalog planta,
                             Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_FASE);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.painelAltura(), 0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_FASE);
    }

    /** O painel do lote em julgamento, no ar acima dele. */
    public void painelDoVoto(World mundo, BuildBattleCatalog planta, int k,
                             Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_VOTO);
        int[] centro = planta.centroDoPlot(k);
        if (centro == null) {
            return;
        }
        Location alvo = new Location(mundo, centro[0] + 0.5,
                planta.topoY() + planta.alturaDeObra() + 4, centro[1] + 0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaPlaca(), planta.fundoDoTexto(), TAG_VOTO);
    }

    /** Apaga a obra de um lote, para a rodada seguinte achar o chão limpo. */
    public int limparLote(World mundo, BuildBattleCatalog planta, int k) {
        int[] centro = planta.centroDoPlot(k);
        if (centro == null) {
            return 0;
        }
        contador = 0;
        int meio = (planta.plotLado() - 1) / 2 + 1;
        int deY = planta.topoY() + 1;
        int ateY = planta.topoY() + planta.alturaDeObra() + 2;
        for (int dx = -meio; dx <= meio; dx++) {
            for (int dz = -meio; dz <= meio; dz++) {
                for (int y = deY; y <= ateY; y++) {
                    Block bloco = mundo.getBlockAt(centro[0] + dx, y,
                            centro[1] + dz);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return contador;
    }

    /** Refaz o chão de um lote: a obra pode ter cavado a plataforma. */
    public int refazerLote(World mundo, BuildBattleCatalog planta, int k) {
        contador = 0;
        lote(mundo, planta, k);
        return contador;
    }

    private int contarOcupados(World mundo, BuildBattleCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int ocupados = 0;
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    if (!mundo.getBlockAt(x, y, z).getType().isAir()) {
                        ocupados++;
                    }
                }
            }
        }
        return ocupados;
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        Block bloco = mundo.getBlockAt(x, y, z);
        if (bloco.getType() != material) {
            bloco.setType(material, false);
            contador++;
        }
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.buildBattleCatalog());
    }

    /** O meio do lote, onde o construtor nasce. */
    public static Location pontoDoLote(World mundo, BuildBattleCatalog planta,
                                       int k) {
        int[] centro = planta.centroDoPlot(k);
        if (centro == null) {
            return SaguaoPadrao.pontoDoSaguao(mundo, planta);
        }
        Location pouso = new Location(mundo, centro[0] + 0.5,
                planta.topoY() + 1, centro[1] + 0.5);
        // Olhando para a frente do lote, que é o lado da plateia.
        pouso.setYaw(planta.rumoDaPlateia(k) < 0 ? 180f : 0f);
        pouso.setPitch(0f);
        return pouso;
    }

    /** O lugar de onde se olha e se vota um lote. */
    public static Location pontoDaPlateia(World mundo,
                                          BuildBattleCatalog planta, int k) {
        int[] onde = planta.pontoDaPlateia(k);
        if (onde == null) {
            return SaguaoPadrao.pontoDoSaguao(mundo, planta);
        }
        Location pouso = new Location(mundo, onde[0] + 0.5,
                planta.topoY() + planta.plateiaAltura() + 1, onde[1] + 0.5);
        // Virado para o lote, que fica do outro lado da plateia.
        pouso.setYaw(planta.rumoDaPlateia(k) < 0 ? 0f : 180f);
        pouso.setPitch(10f);
        return pouso;
    }

    /** Onde ficam os espectadores e quem está fora da rodada. */
    public static Location pontoDoAlto(World mundo,
                                       BuildBattleCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }

    /** Os lotes em ordem embaralhada, para o passeio e o voto. */
    public static List<Integer> ordemDosLotes(List<Integer> lotes,
                                              java.util.Random sorteio) {
        List<Integer> saida = new ArrayList<>(lotes);
        java.util.Collections.shuffle(saida, sorteio);
        return saida;
    }
}
