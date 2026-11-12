package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Levanta a árvore do Parkour por código.
 *
 * <h2>Uma árvore, e não uma torre</h2>
 *
 * O tronco é de madeira, a copa é de folhas floridas, e o caminho sobe em
 * espiral em volta dele em blocos de musgo. Podia ser uma torre de quartzo e
 * seria a mesma geometria — mas o jogador fica dentro disto por minutos
 * olhando para baixo, e árvore dá escala: cada plataforma se lê contra a casca
 * e contra a copa, e dá para saber quanto falta sem contar salto.
 *
 * <h2>O caminho se lê de três cores</h2>
 *
 * Musgo no terço fácil, terracota no terço longo, concreto nos checkpoints.
 * Não é enfeite: num percurso de trinta e dois saltos iguais ninguém sabe se o
 * próximo é o de dois ou o de quatro blocos, e descobrir isso no ar é cair.
 */
public final class ParkourBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_parkour_painel";

    private final BigaCore plugin;
    private int contador;

    public ParkourBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        ParkourCatalog planta = plugin.parkourCatalog();
        List<int[]> pontos = planta.percurso();

        String problema = conferirSaltos(pontos);
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Percurso: " + planta.saltos() + " saltos da fiada "
                    + planta.topoY() + " até a " + planta.cumeY() + " (sobe "
                    + (planta.cumeY() - planta.topoY()) + "), raio " + planta.raio()
                    + ", checkpoint a cada " + planta.checkpointACada()
                    + ". Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga parkour desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        arvore(mundo, planta);
        caminho(mundo, planta, pontos);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Árvore do Parkour levantada: " + contador
                + " blocos, " + planta.saltos() + " saltos. O spawn do mundo foi posto "
                + "no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        ParkourCatalog planta = plugin.parkourCatalog();
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
        return new Resultado(true, "Árvore apagada: " + contador + " blocos.", contador);
    }

    /** Refaz só o caminho. É o que roda no começo de cada rodada. */
    public int refazerCaminho(World mundo) {
        ParkourCatalog planta = plugin.parkourCatalog();
        contador = 0;
        caminho(mundo, planta, planta.percurso());
        return contador;
    }

    @Override
    public Resultado textos(World mundo) {
        ParkourCatalog planta = plugin.parkourCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    /**
     * Confere salto por salto antes de pôr um bloco.
     *
     * Um percurso impossível não dá erro nenhum: fica lá, intransponível,
     * parecendo que o jogador é que não consegue. Barato conferir, caríssimo
     * descobrir em jogo.
     */
    static String conferirSaltos(List<int[]> pontos) {
        for (int i = 1; i < pontos.size(); i++) {
            int[] antes = pontos.get(i - 1);
            int[] agora = pontos.get(i);
            int dy = agora[1] - antes[1];
            double distancia = Math.hypot(agora[0] - antes[0], agora[2] - antes[2]);
            if (distancia > ParkourCatalog.alcanceMaximo(dy)) {
                return "O salto " + i + " pede " + String.format("%.1f", distancia)
                        + " blocos subindo " + dy + ", e o alcance é "
                        + ParkourCatalog.alcanceMaximo(dy) + ". O percurso seria "
                        + "impossível — ajuste 'raio' ou 'saltos' no parkour.yml.";
            }
            if (distancia < 1.5) {
                return "O salto " + i + " tem só " + String.format("%.1f", distancia)
                        + " blocos: as plataformas se encostam e o salto some.";
            }
            if (dy > 1) {
                return "O salto " + i + " sobe " + dy + " blocos de uma vez.";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A árvore
    // ------------------------------------------------------------------

    private void arvore(World mundo, ParkourCatalog planta) {
        Material tronco = planta.cor("tronco", Material.OAK_LOG);
        Material casca = planta.cor("casca", Material.STRIPPED_OAK_LOG);
        Material folha = planta.cor("folha", Material.FLOWERING_AZALEA_LEAVES);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);

        int base = planta.topoY() - 12;
        int cume = planta.cumeY();

        // O tronco sobe até encostar no piso do saguão: a sala de espera fica
        // em cima da árvore, como casa na árvore, e não flutuando sobre ela.
        int topo = planta.saguaoY() - 1;

        // O tronco: 3x3 com o miolo claro, para ter veio.
        for (int y = base; y <= topo; y++) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    boolean miolo = ox == 0 && oz == 0;
                    por(mundo, ox, y, oz, miolo && y % 7 == 0 ? luz
                            : (miolo ? casca : tronco));
                }
            }
        }

        // As raízes: quatro braços no pé, para a árvore não nascer do nada.
        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int passo = 2; passo <= 5; passo++) {
                por(mundo, eixo[0] * passo, base + (passo - 2), eixo[1] * passo, tronco);
            }
        }

        // A copa: um disco de folhas que passa POR CIMA do caminho.
        //
        // A fiada em que ela começa não é escolha de gosto. Quem está em pé
        // numa plataforma tem o pé em y mais 1 e a cabeça em y mais 2, e o
        // pulo sobe 1,25: uma folha em y mais 3 é teto no pulo. A plataforma
        // mais alta do percurso é o cume, então a copa só pode começar em
        // cume mais 4 — e aí pode ser larga, que é o que faz parecer árvore.
        int copa = cume + 6;
        for (int y = cume + 4; y <= topo; y++) {
            int r = 13 - 2 * Math.abs(y - copa);
            if (r < 2) {
                continue;
            }
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!ArenaComum.noDisco(x, z, r) || Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                        continue;
                    }
                    if (Math.floorMod(x * 7 + z * 3 + y, 5) == 0) {
                        continue;                       // buracos, para não ser bola
                    }
                    por(mundo, x, y, z, folha);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  O caminho
    // ------------------------------------------------------------------

    private void caminho(World mundo, ParkourCatalog planta, List<int[]> pontos) {
        Material salto = planta.cor("salto", Material.MOSS_BLOCK);
        Material duro = planta.cor("salto-duro", Material.LIME_TERRACOTTA);
        Material checkpoint = planta.cor("checkpoint", Material.LIME_CONCRETE);
        Material largada = planta.cor("largada", Material.POLISHED_ANDESITE);
        Material chegada = planta.cor("chegada", Material.GOLD_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int saltos = planta.saltos();

        for (int i = 0; i < pontos.size(); i++) {
            int[] p = pontos.get(i);
            switch (p[3]) {
                case ParkourCatalog.LARGADA -> {
                    plataforma(mundo, p, 2, largada);
                    por(mundo, p[0], p[1] + 1, p[2] + 2, rod);
                    por(mundo, p[0], p[1] + 1, p[2] - 2, rod);
                }
                case ParkourCatalog.CHECKPOINT -> {
                    plataforma(mundo, p, 1, checkpoint);
                    por(mundo, p[0], p[1] - 1, p[2], luz);
                }
                case ParkourCatalog.CHEGADA -> {
                    plataforma(mundo, p, 2, chegada);
                    for (int alto = 1; alto <= 3; alto++) {
                        por(mundo, p[0] + 2, p[1] + alto, p[2] + 2, rod);
                        por(mundo, p[0] - 2, p[1] + alto, p[2] - 2, rod);
                    }
                }
                default -> por(mundo, p[0], p[1], p[2],
                        i > 2 * saltos / 3 ? duro : salto);
            }
        }
    }

    private void plataforma(World mundo, int[] centro, int raio, Material material) {
        for (int ox = -raio; ox <= raio; ox++) {
            for (int oz = -raio; oz <= raio; oz++) {
                por(mundo, centro[0] + ox, centro[1], centro[2] + oz, material);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.parkourCatalog());
    }

    public static Location pontoDaLargada(World mundo, ParkourCatalog planta) {
        int[] largada = planta.percurso().get(0);
        Location pouso = new Location(mundo, largada[0] + 0.5, largada[1] + 1,
                largada[2] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(largada[0], largada[2]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** Em cima do checkpoint de índice dado no percurso. */
    public static Location pontoDoCheckpoint(World mundo, int[] ponto) {
        Location pouso = new Location(mundo, ponto[0] + 0.5, ponto[1] + 1, ponto[2] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(ponto[0], ponto[2]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde se assiste: no alto da copa, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, ParkourCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.cumeY() + 9, 0.5);
        onde.setYaw(180f);
        onde.setPitch(50f);
        return onde;
    }

    // ------------------------------------------------------------------

    private void por(World mundo, int x, int y, int z, Material material) {
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }

    private int contarOcupados(World mundo, ParkourCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int achados = 0;
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    if (!mundo.getBlockAt(x, y, z).getType().isAir()) {
                        achados++;
                    }
                }
            }
        }
        return achados;
    }
}
