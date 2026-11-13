package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta o galpão do ESCONDE: chão, muro, pilares e a cabine do meio.
 *
 * <h2>A conferência que não é geométrica</h2>
 *
 * {@link #conferirCobertura} é a que decide se o jogo existe. Ela pede duas
 * coisas opostas da mesma arena:
 *
 * <ul>
 *   <li><b>No começo, cobertura de sobra.</b> Menos de um punhado de lugares
 *       por escondido e o primeiro caçador que sai da cabine acha todo mundo
 *       em trinta segundos, porque não há onde não estar.</li>
 *   <li><b>No fim, cobertura apertada.</b> Se a última onda ainda deixa dois
 *       caixotes por pessoa, a partida acaba sempre igual: no relógio, com os
 *       escondidos parados onde entraram. O aperto é o que obriga a andar, e
 *       andar é o que entrega.</li>
 * </ul>
 *
 * <p>E ela pede da GRADE do chão que caiba a cobertura do começo com a arena
 * cheia — senão o yml promete sete lugares por pessoa e o galpão tem três.
 */
public final class EscondeBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_esconde_painel";

    /** O painel da caça, refeito a cada onda. */
    public static final String TAG_CACA = "bigacore_esconde_caca";

    /** Quantos lugares por escondido são o mínimo para haver onde esconder. */
    private static final int COBERTURA_MINIMA = 3;

    /** Acima de quantos lugares por escondido no fim o aperto não aperta. */
    private static final int COBERTURA_DO_FIM = 2;

    private final BigaCore plugin;
    private int contador;

    public EscondeBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        EscondeCatalog planta = plugin.escondeCatalog();

        String problema = conferirNumeros(planta.raio(), planta.paredeAltura(),
                planta.pilarPasso(), planta.pilarRaio(), planta.pilarAltura(),
                planta.caixotePasso(), planta.cabineRaio(),
                planta.cabineAltura());
        if (problema == null) {
            problema = conferirCobertura(planta.pontosDosCaixotes().size(),
                    planta.maximo(), planta.fracaoCacadores(),
                    planta.caixotesPorEscondido(),
                    planta.caixotesNoFimPorEscondido(), planta.ondas());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.segredoSegundos(),
                    planta.ondas(), planta.tempoLimite(), planta.raio());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int cheio = planta.escondidos(planta.maximo());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Galpão de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + ", com "
                    + planta.pontosDosPilares().size() + " pilares e "
                    + planta.pontosDosCaixotes().size() + " lugares de "
                    + "caixote. Com a arena cheia: "
                    + planta.cacadores(planta.maximo()) + " caçadores, "
                    + cheio + " escondidos, "
                    + planta.caixotesDaOnda(0, cheio) + " caixotes no começo "
                    + "e " + planta.caixotesDaOnda(planta.ondas(), cheio)
                    + " no fim, cada captura valendo "
                    + planta.pontosPorCaptura(planta.maximo())
                    + ". Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga esconde desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_CACA);
        chao(mundo, planta);
        muro(mundo, planta);
        pilares(mundo, planta);
        cabine(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDaCabine(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "ESCONDE levantado: " + contador
                + " blocos, " + planta.pontosDosPilares().size()
                + " pilares e " + planta.pontosDosCaixotes().size()
                + " lugares de caixote. O spawn do mundo foi posto no saguão.",
                contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        EscondeCatalog planta = plugin.escondeCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_CACA);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "ESCONDE apagado: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        EscondeCatalog planta = plugin.escondeCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_CACA);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDaCabine(mundo, planta);
        return new Resultado(true, "Letreiros e placas da cabine refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    static String conferirNumeros(int raio, int paredeAltura, int pilarPasso,
                                  int pilarRaio, int pilarAltura,
                                  int caixotePasso, int cabineRaio,
                                  int cabineAltura) {
        if (pilarPasso <= pilarRaio * 2 + 2) {
            return "Os pilares têm " + (pilarRaio * 2 + 1) + " blocos de "
                    + "grossura e ficam de " + pilarPasso + " em "
                    + pilarPasso + ": eles se encostariam e o galpão viraria "
                    + "um labirinto de paredes em vez de um salão com "
                    + "colunas.";
        }
        if (pilarAltura < 3) {
            return "Os pilares têm " + pilarAltura + " fiadas: não cortam a "
                    + "linha de visão de quem está de pé, e um esconde-"
                    + "esconde sem obstáculo de vista não tem esconderijo.";
        }
        if (pilarAltura >= paredeAltura) {
            return "Os pilares têm " + pilarAltura + " fiadas e o muro "
                    + paredeAltura + ": os pilares virariam escada para sair "
                    + "do galpão.";
        }
        if (caixotePasso < 2) {
            return "Os caixotes ficariam de " + caixotePasso + " em "
                    + caixotePasso + " blocos: colados, e aí não há como "
                    + "saber qual deles está torto.";
        }
        if (cabineRaio < 1 || cabineRaio * 2 + 1 > raio / 2) {
            return "A cabine tem " + (cabineRaio * 2 + 1) + " blocos de lado "
                    + "num galpão de raio " + raio + ": ou não cabe ninguém "
                    + "dentro, ou ela é o galpão.";
        }
        if (cabineAltura < 3) {
            return "A cabine tem " + cabineAltura + " fiadas de altura: o "
                    + "caçador trancado nela não cabe de pé.";
        }
        if (cabineAltura >= paredeAltura) {
            return "A cabine tem " + cabineAltura + " fiadas e o muro "
                    + paredeAltura + ": de cima da cabine se veria o galpão "
                    + "inteiro, e o tempo de esconder não esconderia nada.";
        }
        return null;
    }

    /**
     * A cobertura começa sobrando e termina apertada.
     *
     * As duas pontas, e as duas são defeitos invisíveis: sem cobertura no
     * começo o jogo acaba em trinta segundos, e com cobertura no fim ele acaba
     * sempre no relógio com todo mundo parado. Nenhum dos dois aparece numa
     * partida de teste de duas pessoas num galpão vazio.
     */
    static String conferirCobertura(int lugaresNaGrade, int maximo,
                                    double fracao, int porEscondido,
                                    int noFim, int ondas) {
        if (porEscondido < COBERTURA_MINIMA) {
            return "A arena começa com " + porEscondido + " caixote(s) por "
                    + "escondido, e o mínimo é " + COBERTURA_MINIMA + ": com "
                    + "menos que isso não há onde não estar, e o primeiro "
                    + "caçador que sai da cabine acha todo mundo.";
        }
        if (noFim >= porEscondido) {
            return "A cobertura começa com " + porEscondido + " caixote(s) "
                    + "por escondido e termina com " + noFim
                    + ": ela tinha de DIMINUIR. As ondas são o que desfaz o "
                    + "empate no relógio.";
        }
        if (noFim > COBERTURA_DO_FIM) {
            return "A última onda ainda deixa " + noFim + " caixotes por "
                    + "escondido, e o limite é " + COBERTURA_DO_FIM + ": com "
                    + "cobertura de sobra no fim, a partida acaba sempre no "
                    + "relógio com os escondidos parados onde entraram.";
        }
        int precisa = EscondeCatalog.escondidos(maximo, fracao) * porEscondido;
        if (lugaresNaGrade < precisa) {
            return "Com a arena cheia o jogo quer " + precisa + " caixotes de "
                    + "enfeite e a grade do chão só tem " + lugaresNaGrade
                    + " lugares. Aumente o raio ou diminua "
                    + "arena.caixote-passo.";
        }
        if (ondas < 2) {
            return "Uma onda só: a cobertura cairia de uma vez, e o que o "
                    + "jogo quer é o cerco fechando.";
        }
        return null;
    }

    /**
     * O tempo de esconder dá para atravessar o galpão, e as ondas caem dentro
     * da caça.
     */
    static String conferirRelogio(int segredo, int ondas, int tempoLimite,
                                  int raio) {
        // Um jogador andando faz uns 4,3 blocos por segundo.
        int atravessar = (int) Math.ceil(raio * 2 / 4.3);
        if (segredo < atravessar) {
            return "O tempo de esconder é de " + segredo + "s e atravessar o "
                    + "galpão leva " + atravessar + "s: quem largar do lado "
                    + "errado não chega a lugar nenhum antes de a cabine "
                    + "abrir.";
        }
        if (segredo >= tempoLimite / 2) {
            return "O tempo de esconder é de " + segredo + "s num limite de "
                    + tempoLimite + "s: metade da partida seria gente parada "
                    + "esperando.";
        }
        int ultima = EscondeCatalog.segundoDaOnda(ondas, ondas, segredo,
                tempoLimite);
        if (ultima >= tempoLimite - 15) {
            return "A última onda cai no segundo " + ultima + " de "
                    + tempoLimite + ": uma onda que cai junto com o apito não "
                    + "muda nada, e ela é a onda que existe para desfazer o "
                    + "empate.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void chao(World mundo, EscondeCatalog planta) {
        Material piso = planta.cor("piso", Material.POLISHED_DEEPSLATE);
        Material xadrez = planta.cor("piso-xadrez", Material.DEEPSLATE_TILES);
        Material beira = planta.cor("beira", Material.DEEPSLATE_BRICKS);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira
                        : (((x >> 2) + (z >> 2)) % 2 == 0 ? piso : xadrez));
            }
        }
    }

    /**
     * O muro: fecha a vista, e é por isso que ele existe.
     *
     * Num esconde-esconde num disco aberto sobre o vazio, quem está na beira
     * vê o galpão inteiro contra o céu e acha todo mundo de longe. O muro
     * obriga a entrar.
     */
    private void muro(World mundo, EscondeCatalog planta) {
        Material parede = planta.cor("parede", Material.DEEPSLATE_BRICKS);
        Material topo = planta.cor("parede-topo", Material.POLISHED_DEEPSLATE);
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        int raio = planta.raio();
        int y = planta.topoY();
        int alto = planta.paredeAltura();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)
                        || ArenaComum.noDiscoMeio(x, z, raio - 1)) {
                    continue;
                }
                for (int fiada = 1; fiada <= alto; fiada++) {
                    por(mundo, x, y + fiada, z,
                            fiada == alto ? topo : parede);
                }
                // Uma luz a cada oito blocos de muro, na cintura.
                if ((Math.abs(x) + Math.abs(z)) % 8 == 0) {
                    por(mundo, x, y + alto - 2, z, luz);
                }
            }
        }
    }

    private void pilares(World mundo, EscondeCatalog planta) {
        Material pilar = planta.cor("pilar", Material.CHISELED_DEEPSLATE);
        Material capa = planta.cor("pilar-topo", Material.PURPLE_TERRACOTTA);
        Material lanterna = planta.cor("poste-luz", Material.SOUL_LANTERN);
        int y = planta.topoY();
        int raio = planta.pilarRaio();
        int alto = planta.pilarAltura();

        for (int[] onde : planta.pontosDosPilares()) {
            for (int dx = -raio; dx <= raio; dx++) {
                for (int dz = -raio; dz <= raio; dz++) {
                    for (int fiada = 1; fiada <= alto; fiada++) {
                        por(mundo, onde[0] + dx, y + fiada, onde[1] + dz,
                                fiada == alto ? capa : pilar);
                    }
                }
            }
            por(mundo, onde[0], y + alto + 1, onde[1], lanterna);
        }
    }

    /**
     * A cabine: uma caixa fechada no meio do galpão.
     *
     * Os caçadores ficam dentro dela enquanto os outros se escondem, e ela é
     * fechada de verdade — paredes de bloco cheio e teto de vidro. Cegar o
     * caçador com um efeito seria mais fácil e seria pior: os escondidos não
     * veriam que ele está cego, e metade da tensão do tempo de esconder é
     * saber que o caçador está ali, trancado, vendo o relógio.
     */
    private void cabine(World mundo, EscondeCatalog planta) {
        Material parede = planta.cor("cabine", Material.PURPLE_CONCRETE);
        Material piso = planta.cor("cabine-piso", Material.POLISHED_DEEPSLATE);
        Material teto = planta.cor("cabine-teto", Material.PURPLE_STAINED_GLASS);
        Material luz = planta.cor("luz-fria", Material.END_ROD);
        int raio = planta.cabineRaio();
        int y = planta.topoY();
        int alto = planta.cabineAltura();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                boolean naBorda = Math.abs(x) == raio || Math.abs(z) == raio;
                por(mundo, x, y, z, piso);
                for (int fiada = 1; fiada < alto; fiada++) {
                    if (naBorda) {
                        por(mundo, x, y + fiada, z, parede);
                    }
                }
                por(mundo, x, y + alto, z, naBorda ? parede : teto);
            }
        }
        por(mundo, 0, y + alto - 1, 0, luz);
    }

    /**
     * As placas das regras ficam na cabine, de dentro para fora.
     *
     * De fora: quem se esconde passa por elas correndo para o esconderijo e
     * lê a que der. O painel de instruções do saguão continua sendo o lugar
     * de ler com calma; estas quatro são o lembrete na hora.
     */
    public void placasDaCabine(World mundo, EscondeCatalog planta) {
        int raio = planta.cabineRaio();
        int y = planta.topoY() + 2;
        String cor = planta.corDoTexto();
        List<List<Component>> textos = List.of(
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>ESCONDE</b></color>"),
                        plugin.mini().deserialize("<white>vire um caixote</white>"),
                        plugin.mini().deserialize("<gray>parado = encaixado</gray>"),
                        plugin.mini().deserialize("<gray>andando = torto</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A CABINE</b></color>"),
                        plugin.mini().deserialize("<white>os caçadores</white>"),
                        plugin.mini().deserialize("<white>saem daqui</white>"),
                        plugin.mini().deserialize("<gray>corra</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>AS ONDAS</b></color>"),
                        plugin.mini().deserialize("<white>os caixotes</white>"),
                        plugin.mini().deserialize("<white>vão sumindo</white>"),
                        plugin.mini().deserialize("<gray>no fim, ande</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>OS PONTOS</b></color>"),
                        plugin.mini().deserialize("<white>1 por segundo</white>"),
                        plugin.mini().deserialize("<white>de pé</white>"),
                        plugin.mini().deserialize("<gray>pegar vale igual</gray>")));

        int[][] lados = {{0, -raio - 1}, {raio + 1, 0}, {0, raio + 1},
                {-raio - 1, 0}};
        for (int k = 0; k < lados.length; k++) {
            int x = lados[k][0];
            int z = lados[k][1];
            BlockFace frente = EscondeCatalog.frenteDaPlaca(-x, -z);
            ArenaComum.escreverPlaca(mundo, new int[]{x, y, z},
                    planta.materialDaPlaca(), frente, textos.get(k));
        }
    }

    private void torres(World mundo, EscondeCatalog planta) {
        Material torre = planta.cor("torre", Material.DEEPSLATE_BRICKS);
        Material luz = planta.cor("torre-luz", Material.SOUL_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.raio() + 5;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Os caixotes de enfeite
    // ------------------------------------------------------------------

    /** Põe um caixote de enfeite num lugar da grade. */
    public boolean porCaixote(World mundo, EscondeCatalog planta, int[] onde) {
        Block bloco = mundo.getBlockAt(onde[0], planta.topoY() + 1, onde[1]);
        if (!bloco.getType().isAir()) {
            return false;
        }
        bloco.setType(planta.cor("caixote", Material.BARREL), false);
        return true;
    }

    /** Tira um caixote de enfeite, se é um caixote que está ali. */
    public boolean tirarCaixote(World mundo, EscondeCatalog planta,
                                int[] onde) {
        Block bloco = mundo.getBlockAt(onde[0], planta.topoY() + 1, onde[1]);
        if (bloco.getType() != planta.cor("caixote", Material.BARREL)) {
            return false;
        }
        bloco.setType(Material.AIR, false);
        return true;
    }

    /** Apaga todo caixote de enfeite da grade. */
    public int limparCaixotes(World mundo, EscondeCatalog planta) {
        int quantos = 0;
        for (int[] onde : planta.pontosDosCaixotes()) {
            if (tirarCaixote(mundo, planta, onde)) {
                quantos++;
            }
        }
        return quantos;
    }

    /** O painel da caça, em cima da cabine, virado para quem olha. */
    public void painelDaCaca(World mundo, EscondeCatalog planta,
                             Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_CACA);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.cabineAltura() + planta.painelAltura(),
                0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_CACA);
    }

    private int contarOcupados(World mundo, EscondeCatalog planta) {
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.escondeCatalog());
    }

    /** Dentro da cabine: é onde o caçador espera. */
    public static Location pontoDaCabine(World mundo, EscondeCatalog planta,
                                         int qual, int quantos) {
        int raio = Math.max(0, planta.cabineRaio() - 1);
        int lado = raio * 2 + 1;
        int indice = quantos <= 0 ? 0 : qual % Math.max(1, lado * lado);
        double x = -raio + indice % lado;
        double z = -raio + (indice / lado) % lado;
        Location onde = new Location(mundo, x + 0.5, planta.topoY() + 1,
                z + 0.5);
        onde.setYaw(ArenaComum.yawDaDirecao(-x, -z));
        onde.setPitch(0f);
        return onde;
    }

    /** Onde um escondido nasce: no anel do meio, olhando para fora. */
    public static Location pontoDaLargada(World mundo, EscondeCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawDaDirecao(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** Onde o caçador cai quando a cabine abre: na porta dela. */
    public static Location pontoDaSaida(World mundo, EscondeCatalog planta,
                                        int qual, int quantos) {
        double angulo = 2 * Math.PI * qual / Math.max(1, quantos);
        int longe = planta.cabineRaio() + 2;
        double x = Math.cos(angulo) * longe;
        double z = Math.sin(angulo) * longe;
        Location onde = new Location(mundo, x, planta.topoY() + 1, z);
        onde.setYaw(ArenaComum.yawDaDirecao(x, z));
        onde.setPitch(0f);
        return onde;
    }

    /** De onde se assiste: em cima do galpão. */
    public static Location pontoDaPlateia(World mundo, EscondeCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(65f);
        return onde;
    }
}
