package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Levanta o campo de neve.
 *
 * <h2>Um disco sem muro</h2>
 *
 * Todos os outros discos desta série têm muro: ali o perigo é o chão. Aqui o
 * perigo é a BEIRA, e muro mataria o jogo — "acertou, derrubou" só quer dizer
 * alguma coisa se houver para onde derrubar. Por isso o campo é uma ilha e o
 * que cerca é o vazio.
 *
 * <h2>Os abrigos são o jogo</h2>
 *
 * Campo aberto com bola infinita é todo mundo atirando em todo mundo desde o
 * primeiro segundo, e vence quem tiver o melhor ping. Com iglu, mureta e
 * pinheiro no caminho, atirar passa a exigir sair da proteção — e sair da
 * proteção é a decisão que faz o jogo.
 */
public final class NeveBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_neve_painel";

    private final BigaCore plugin;
    private int contador;

    public NeveBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        NeveCatalog planta = plugin.neveCatalog();
        List<int[]> abrigos = planta.abrigos();

        String problema = conferirCampo(abrigos, planta.quantosAbrigos(),
                planta.raio(), planta.abrigoFolgaCentro(), planta.abrigoFolgaBeira());
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Campo: raio " + planta.raio() + " na fiada " + planta.topoY()
                    + ", monte de raio " + planta.monteRaio() + " e "
                    + planta.monteAltura() + " de alto, " + abrigos.size()
                    + " abrigos. Aperta a partir de " + planta.apertoSegundos()
                    + "s, um anel a cada " + planta.apertoIntervalo()
                    + "s. Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga neve desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        campo(mundo, planta);
        monte(mundo, planta);
        for (int[] abrigo : abrigos) {
            abrigo(mundo, planta, abrigo);
        }
        marcos(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Campo de neve levantado: " + contador
                + " blocos, " + abrigos.size() + " abrigos. O spawn do mundo foi "
                + "posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        NeveCatalog planta = plugin.neveCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
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
        return new Resultado(true, "Campo apagado: " + contador + " blocos.", contador);
    }

    /** Refaz o campo inteiro. É o que roda no começo de cada rodada. */
    public int refazerCampo(World mundo) {
        NeveCatalog planta = plugin.neveCatalog();
        contador = 0;
        limparNeveSolta(mundo, planta);
        campo(mundo, planta);
        monte(mundo, planta);
        for (int[] abrigo : planta.abrigos()) {
            abrigo(mundo, planta, abrigo);
        }
        return contador;
    }

    /**
     * Tira a neve que as bolas deixaram pelo chão na rodada passada.
     *
     * A camada de neve é enfeite — mas enfeite que se acumula rodada após
     * rodada vira um campo de neve até a cintura, e aí o jogo muda sem
     * ninguém ter mudado nada.
     */
    private void limparNeveSolta(World mundo, NeveCatalog planta) {
        int raio = planta.raio();
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                for (int alto = 1; alto <= planta.monteAltura() + 5; alto++) {
                    Block bloco = mundo.getBlockAt(x, planta.topoY() + alto, z);
                    if (bloco.getType() == Material.SNOW) {
                        bloco.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    @Override
    public Resultado textos(World mundo) {
        NeveCatalog planta = plugin.neveCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferências — puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Confere o campo antes de pôr um bloco.
     *
     * O sorteio dos abrigos é um laço que tenta até caber. Laço que tenta até
     * caber é laço que um dia desiste — e desistir aqui devolve meia dúzia de
     * abrigos em vez de catorze, num campo que fica aberto sem ninguém notar.
     */
    static String conferirCampo(List<int[]> abrigos, int pedidos, int raio,
                                int folgaCentro, int folgaBeira) {
        if (abrigos.size() < pedidos) {
            return "O sorteio só coube " + abrigos.size() + " dos " + pedidos
                    + " abrigos pedidos: o campo de raio " + raio + " é pequeno "
                    + "demais para tantos. Baixe 'abrigos' ou suba 'raio'.";
        }
        for (int i = 0; i < abrigos.size(); i++) {
            int[] um = abrigos.get(i);
            double doCentro = Math.hypot(um[0], um[1]);
            if (doCentro - um[3] < folgaCentro) {
                return "Um abrigo nasceu em cima do monte do meio.";
            }
            if (doCentro + um[3] > raio - folgaBeira) {
                return "Um abrigo nasceu pendurado na beira, sobre o vazio.";
            }
            for (int j = i + 1; j < abrigos.size(); j++) {
                int[] outro = abrigos.get(j);
                if (Math.hypot(um[0] - outro[0], um[1] - outro[1])
                        < um[3] + outro[3] + NeveCatalog.FOLGA_ENTRE) {
                    return "Dois abrigos nasceram grudados, e viram parede.";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void campo(World mundo, NeveCatalog planta) {
        Material piso = planta.cor("piso", Material.SNOW_BLOCK);
        Material beira = planta.cor("beira", Material.PACKED_ICE);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                boolean naBeira = ArenaComum.naBeira(x, z, raio, planta.beiraAneis());
                por(mundo, x, y, z, naBeira ? beira : piso);
            }
        }
    }

    /**
     * O monte do meio: alto, escorregadio e indefensável.
     *
     * Sem luz nenhuma dentro nem em volta, e de gelo compacto e não de gelo
     * comum: gelo comum e camada de neve DERRETEM com luz de bloco acima de
     * 11, e uma lanterna enfeitando o meio do monte viraria, em dois minutos
     * de rodada, um poço de água no lugar do ponto alto da arena.
     */
    private void monte(World mundo, NeveCatalog planta) {
        Material gelo = planta.cor("monte", Material.PACKED_ICE);
        Material topo = planta.cor("monte-topo", Material.BLUE_ICE);
        int raio = planta.monteRaio();
        int altura = planta.monteAltura();
        int y = planta.topoY();

        for (int alto = 1; alto <= altura; alto++) {
            int r = Math.max(1, raio - (alto - 1));
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!ArenaComum.noDisco(x, z, r)) {
                        continue;
                    }
                    por(mundo, x, y + alto, z, alto == altura ? topo : gelo);
                }
            }
        }
    }

    private void abrigo(World mundo, NeveCatalog planta, int[] abrigo) {
        switch (abrigo[2]) {
            case NeveCatalog.IGLU -> iglu(mundo, planta, abrigo[0], abrigo[1],
                    abrigo[3]);
            case NeveCatalog.MURETA -> mureta(mundo, planta, abrigo[0], abrigo[1],
                    abrigo[3]);
            default -> pinheiro(mundo, planta, abrigo[0], abrigo[1]);
        }
    }

    /**
     * Um iglu: uma cúpula de neve com uma porta de duas fiadas.
     *
     * A cúpula é casca de bola, e não anel por fiada: ver {@link
     * NeveCatalog#naCasca}. E a porta é aberta DEPOIS de a cúpula estar de
     * pé, nas duas fiadas de baixo da direção que olha para o meio do campo.
     */
    private void iglu(World mundo, NeveCatalog planta, int cx, int cz, int raio) {
        Material neve = planta.cor("iglu", Material.SNOW_BLOCK);
        Material porta = planta.cor("iglu-porta", Material.PACKED_ICE);
        int y = planta.topoY();

        for (int alto = 1; alto <= raio; alto++) {
            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (NeveCatalog.naCasca(x, z, alto, raio)) {
                        por(mundo, cx + x, y + alto, cz + z, neve);
                    }
                }
            }
        }

        int[] abertura = NeveCatalog.portaDoIglu(cx, cz, raio);
        por(mundo, cx + abertura[0], y + 1, cz + abertura[1], Material.AIR);
        por(mundo, cx + abertura[0], y + 2, cz + abertura[1], Material.AIR);
        // Uma pedra de gelo no cume, para o iglu ser visto de longe.
        por(mundo, cx, y + raio, cz, porta);
    }

    /** Uma mureta: três de comprimento, dois de altura, atravessada ao raio. */
    private void mureta(World mundo, NeveCatalog planta, int cx, int cz, int raio) {
        Material gelo = planta.cor("mureta", Material.PACKED_ICE);
        int y = planta.topoY();
        double norma = Math.max(1, Math.hypot(cx, cz));
        double tx = -cz / norma;
        double tz = cx / norma;

        for (int passo = -raio; passo <= raio; passo++) {
            int x = cx + (int) Math.round(tx * passo);
            int z = cz + (int) Math.round(tz * passo);
            for (int alto = 1; alto <= 2; alto++) {
                por(mundo, x, y + alto, z, gelo);
            }
        }
    }

    /**
     * Um pinheiro nevado, com um monte de neve no pé.
     *
     * O tronco sozinho é um bloco de largura, e um bloco de largura não
     * esconde ninguém. O que faz do pinheiro abrigo é o monte de neve de uma
     * fiada em volta: cobre metade do corpo de quem se agacha atrás, que é
     * exatamente o que a bola de neve precisa errar.
     */
    private void pinheiro(World mundo, NeveCatalog planta, int cx, int cz) {
        Material tronco = planta.cor("tronco", Material.SPRUCE_LOG);
        Material folha = planta.cor("folha", Material.SPRUCE_LEAVES);
        Material neve = planta.cor("piso", Material.SNOW_BLOCK);
        int y = planta.topoY();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) == 2) {
                    por(mundo, cx + x, y + 1, cz + z, neve);
                }
            }
        }
        for (int alto = 1; alto <= 5; alto++) {
            por(mundo, cx, y + alto, cz, tronco);
        }
        for (int andar = 0; andar < 3; andar++) {
            int r = 2 - andar;
            int alto = 3 + andar;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z) > r + 1 || (x == 0 && z == 0)) {
                        continue;
                    }
                    por(mundo, cx + x, y + alto, cz + z, folha);
                }
            }
        }
        por(mundo, cx, y + 6, cz, folha);
    }

    /** Quatro marcos de gelo em volta, para a ilha ter escala. */
    private void marcos(World mundo, NeveCatalog planta) {
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int d = planta.marcoDistancia();
        int altura = planta.marcoAltura();
        int y = planta.topoY();

        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int cx = eixo[0] * d;
            int cz = eixo[1] * d;
            for (int alto = -4; alto <= altura; alto++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean miolo = dx == 0 && dz == 0;
                        Material qual = miolo && alto % 5 == 0 ? luz
                                : (miolo ? vidro : estrutura);
                        por(mundo, cx + dx, y + alto, cz + dz, qual);
                    }
                }
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, cx, y + altura + alto, cz, rod);
            }
        }
    }

    private int contarOcupados(World mundo, NeveCatalog planta) {
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
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.neveCatalog());
    }

    /** Uma das posições de largada, no anel entre o monte e a beira. */
    public static Location pontoDaLargada(World mundo, NeveCatalog planta, int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde o congelado assiste: no alto do meio, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, NeveCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.topoY() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(60f);
        return onde;
    }
}
