package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Levanta o poço vulcânico da Fuga da Lava.
 *
 * <h2>O poço é fechado de propósito</h2>
 *
 * Muro em volta, pilar no meio e nada mais. Quem cai da rampa cai no fundo —
 * e o fundo, depois de meio minuto, é lava. É esse fechamento que faz o jogo:
 * numa arena aberta, errar um salto custaria uma volta; aqui custa a partida.
 *
 * <h2>A lava é posta sem física</h2>
 *
 * Cada fiada que sobe é um disco de lava escrito com {@code applyPhysics}
 * desligado, e só onde o bloco é ar. Sem física porque lava com física
 * escorre: em vez de uma superfície plana subindo, viraria uma cascata
 * entrando por dentro da rampa e engolindo o jogo de uma vez. E só no ar
 * porque a rampa tem de sobreviver à subida — lava que come a rampa deixa a
 * arena inútil depois da primeira rodada.
 */
public final class LavaBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_lava_painel";

    private final BigaCore plugin;
    private int contador;

    public LavaBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        LavaCatalog planta = plugin.lavaCatalog();
        List<double[]> helice = planta.helice();
        Map<Long, Integer> celulas = planta.celulas();

        String problema = conferirRampa(helice, celulas, planta.raio(),
                planta.largura(), planta.pilarRaio(), planta.muroRaio(),
                planta.passoDaSubida());
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int subida = LavaCatalog.segundosAte(planta.altura(),
                    planta.segundosPorFiada(), planta.aceleraACada());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Poço: fundo em y " + planta.topoY() + ", saída em y "
                    + planta.cumeY() + " (" + planta.altura() + " fiadas), raio "
                    + planta.raio() + ", muro em " + planta.muroRaio() + ". Rampa: "
                    + planta.comprimentoDaRampa() + " blocos de caminhada em "
                    + celulas.size() + " células. A lava chega ao topo em "
                    + subida + "s. Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga lava desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        fundo(mundo, planta);
        pilar(mundo, planta);
        muro(mundo, planta);
        escreverRampa(mundo, planta, celulas);
        saida(mundo, planta, helice);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Poço da lava levantado: " + contador
                + " blocos, rampa de " + planta.comprimentoDaRampa()
                + " blocos subindo " + planta.altura() + " fiadas. O spawn do mundo "
                + "foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        LavaCatalog planta = plugin.lavaCatalog();
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
        return new Resultado(true, "Poço apagado: " + contador + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        LavaCatalog planta = plugin.lavaCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    /**
     * Refaz a rampa e tira a lava. É o que roda no começo de cada rodada.
     *
     * Tira a lava até a fiada que ela alcançou, e não o poço inteiro: a
     * rodada passada pode ter parado na terceira fiada, e varrer oitenta
     * fiadas de poço para apagar três é meio milhão de blocos lidos à toa.
     */
    public int refazerRampa(World mundo, int ateFiada) {
        LavaCatalog planta = plugin.lavaCatalog();
        contador = 0;
        Material lava = Material.LAVA;
        int raio = planta.muroRaio() - 1;

        for (int y = planta.topoY(); y <= Math.max(planta.topoY(), ateFiada); y++) {
            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (!ArenaComum.noDisco(x, z, raio)) {
                        continue;
                    }
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (bloco.getType() == lava) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        escreverRampa(mundo, planta, planta.celulas());
        return contador;
    }

    /**
     * Sobe a lava até a fiada dada, enchendo só o que é ar.
     *
     * Só o ar: a rampa, o pilar e o muro atravessam a lava inteiros, e quem
     * está em pé numa fiada já engolida continua em pé — o que elimina é a
     * ALTURA do jogador contra a superfície, não o bloco em que ele está.
     */
    public int subirLava(World mundo, int deFiada, int ateFiada) {
        LavaCatalog planta = plugin.lavaCatalog();
        int raio = planta.muroRaio() - 1;
        int postos = 0;
        for (int y = deFiada; y <= ateFiada; y++) {
            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (!ArenaComum.noDisco(x, z, raio)) {
                        continue;
                    }
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (bloco.getType().isAir()) {
                        bloco.setType(Material.LAVA, false);
                        postos++;
                    }
                }
            }
        }
        return postos;
    }

    // ------------------------------------------------------------------
    //  Conferências — puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Confere a rampa antes de pôr um bloco.
     *
     * Quatro perguntas de defeito silencioso: a rampa cabe entre o pilar e o
     * muro? uma volta passa por cima da outra sem pé direito? há vão que não
     * se salta? e há célula faltando onde devia haver chão?
     */
    static String conferirRampa(List<double[]> helice, Map<Long, Integer> celulas,
                                int raio, int largura, int pilarRaio, int muroRaio,
                                int passoDaSubida) {
        int meia = largura / 2;
        if (raio - meia <= pilarRaio + 1) {
            return "A rampa (raio " + raio + ", largura " + largura + ") encosta no "
                    + "pilar de raio " + pilarRaio + ": não dá para passar.";
        }
        if (raio + meia >= muroRaio) {
            return "A rampa (raio " + raio + ", largura " + largura + ") atravessa o "
                    + "muro em " + muroRaio + ".";
        }

        double peDireito = 2 * Math.PI * raio / passoDaSubida;
        if (peDireito < 4) {
            return "Cada volta da hélice sobe só " + String.format("%.1f", peDireito)
                    + " fiadas: uma volta passa raspando por cima da outra. Suba "
                    + "'raio' ou baixe 'passo-da-subida'.";
        }

        String salto = conferirSaltos(helice, celulas, largura);
        if (salto != null) {
            return salto;
        }
        return null;
    }

    /**
     * Anda a rampa de ponta a ponta e mede cada vão.
     *
     * É o teste que justifica o gerador: um vão de cinco blocos não dá erro
     * nenhum, só fica lá — e a rodada inteira trava nele, com a lava subindo
     * por baixo de todo mundo.
     */
    static String conferirSaltos(List<double[]> helice, Map<Long, Integer> celulas,
                                 int largura) {
        double[] anterior = null;
        for (double[] ponto : helice) {
            if (!temChao(celulas, ponto, largura)) {
                continue;
            }
            if (anterior != null) {
                double d = Math.hypot(ponto[0] - anterior[0], ponto[2] - anterior[2]);
                int dy = (int) (ponto[1] - anterior[1]);
                double alcance = dy >= 1 ? 4.0 : 5.0;
                if (d > alcance) {
                    return "O vão em " + (int) anterior[3] + " blocos de rampa mede "
                            + String.format("%.1f", d) + " subindo " + dy
                            + ", e o alcance é " + alcance + ": seria intransponível. "
                            + "Baixe 'vao-tamanho'.";
                }
                if (dy > 1) {
                    return "A rampa sobe " + dy + " fiadas de uma vez em "
                            + (int) anterior[3] + ": suba 'passo-da-subida'.";
                }
            }
            anterior = ponto;
        }
        return null;
    }

    /** Há chão na célula central deste ponto da hélice? */
    private static boolean temChao(Map<Long, Integer> celulas, double[] ponto,
                                   int largura) {
        int x = (int) Math.floor(ponto[0] + 0.5);
        int z = (int) Math.floor(ponto[2] + 0.5);
        return celulas.containsKey(ArenaComum.chave(x, (int) ponto[1], z));
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void fundo(World mundo, LavaCatalog planta) {
        Material chao = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material veio = planta.cor("pilar-veio", Material.MAGMA_BLOCK);
        int raio = planta.muroRaio() - 1;
        int y = planta.topoY();
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                boolean rachado = Math.floorMod(x * 5 + z * 3, 11) == 0;
                por(mundo, x, y - 1, z, rachado ? veio : chao);
            }
        }
    }

    private void pilar(World mundo, LavaCatalog planta) {
        Material pedra = planta.cor("pilar", Material.BASALT);
        Material veio = planta.cor("pilar-veio", Material.MAGMA_BLOCK);
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        int raio = planta.pilarRaio();
        for (int y = planta.topoY(); y <= planta.cumeY() + 3; y++) {
            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (!ArenaComum.noDisco(x, z, raio)) {
                        continue;
                    }
                    boolean beirada = !ArenaComum.noDisco(x, z, raio - 1);
                    Material qual = pedra;
                    if (beirada && Math.floorMod(x * 7 + z * 5 + y, 17) == 0) {
                        qual = veio;
                    } else if (beirada && y % 12 == 0) {
                        qual = luz;
                    }
                    por(mundo, x, y, z, qual);
                }
            }
        }
    }

    /**
     * O muro do poço, com veios de vidro laranja.
     *
     * Os veios não são enfeite: num poço de sessenta fiadas de parede lisa,
     * ninguém sabe a que altura está. Com um anel aceso a cada doze fiadas,
     * dá para contar quanto falta sem olhar número nenhum.
     */
    private void muro(World mundo, LavaCatalog planta) {
        Material pedra = planta.cor("muro", Material.DEEPSLATE_BRICKS);
        Material veio = planta.cor("muro-veio", Material.ORANGE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        int raio = planta.muroRaio();
        for (int y = planta.topoY() - 1; y <= planta.cumeY() + 4; y++) {
            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (!ArenaComum.noDisco(x, z, raio)
                            || ArenaComum.noDisco(x, z, raio - 1)) {
                        continue;
                    }
                    Material qual = pedra;
                    if (y % 12 == 0) {
                        qual = Math.floorMod(x + z, 3) == 0 ? luz : veio;
                    }
                    por(mundo, x, y, z, qual);
                }
            }
        }
    }

    private void escreverRampa(World mundo, LavaCatalog planta,
                               Map<Long, Integer> celulas) {
        Material rampa = planta.cor("rampa", Material.POLISHED_BLACKSTONE);
        Material borda = planta.cor("rampa-borda", Material.BLACKSTONE);
        Material rachada = planta.cor("rachada",
                Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        Material largada = planta.cor("largada", Material.POLISHED_BASALT);
        Material saida = planta.cor("saida", Material.GOLD_BLOCK);

        for (Map.Entry<Long, Integer> celula : celulas.entrySet()) {
            long chave = celula.getKey();
            int x = desempacotar(chave, 40);
            int y = desempacotar(chave, 20);
            int z = desempacotar(chave, 0);
            Material qual = switch (celula.getValue()) {
                case LavaCatalog.BORDA -> borda;
                case LavaCatalog.RACHADA -> rachada;
                case LavaCatalog.LARGADA -> largada;
                case LavaCatalog.SAIDA -> saida;
                default -> rampa;
            };
            por(mundo, x, y, z, qual);
        }
    }

    /** Um inteiro de vinte bits com sinal, de volta da chave empacotada. */
    static int desempacotar(long chave, int deslocamento) {
        return ArenaComum.daChave(chave, deslocamento);
    }

    /**
     * A saída: um terraço de ouro no fim da rampa, com farol.
     *
     * Largo porque é onde a corrida termina: um pixel de ouro no alto de uma
     * hélice seria decidido por sorte de passo, e não por quem subiu melhor.
     */
    private void saida(World mundo, LavaCatalog planta, List<double[]> helice) {
        Material ouro = planta.cor("saida", Material.GOLD_BLOCK);
        Material metal = planta.cor("metal", Material.NETHERITE_BLOCK);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.MAGMA_BLOCK);

        double[] fim = helice.get(helice.size() - 1);
        int cx = (int) Math.floor(fim[0] + 0.5);
        int cz = (int) Math.floor(fim[2] + 0.5);
        int y = planta.cumeY();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                    continue;
                }
                por(mundo, cx + dx, y, cz + dz,
                        Math.abs(dx) == 3 || Math.abs(dz) == 3 ? metal : ouro);
            }
        }
        por(mundo, cx, y, cz, nucleo);
        for (int alto = 1; alto <= 4; alto++) {
            por(mundo, cx + 3, y + alto, cz, rod);
            por(mundo, cx - 3, y + alto, cz, rod);
            por(mundo, cx, y + alto, cz + 3, rod);
            por(mundo, cx, y + alto, cz - 3, rod);
        }
    }

    private int contarOcupados(World mundo, LavaCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.lavaCatalog());
    }

    /** Onde o jogador número k nasce no fundo do poço. */
    public static Location pontoDaLargada(World mundo, LavaCatalog planta, int k,
                                          int quantos) {
        double volta = 2 * Math.PI * k / Math.max(1, quantos);
        double raio = Math.max(planta.pilarRaio() + 2, planta.raio() - 6);
        double x = raio * Math.cos(volta);
        double z = raio * Math.sin(volta);
        Location onde = new Location(mundo, x + 0.5, planta.topoY(), z + 0.5);
        onde.setYaw(ArenaComum.yawParaOCentro((int) x, (int) z) + 180f);
        onde.setPitch(0f);
        return onde;
    }

    /** O começo da rampa, para quem precisa ser devolvido a ela. */
    public static Location pontoDoPeDaRampa(World mundo, LavaCatalog planta) {
        List<double[]> helice = planta.helice();
        double[] p = helice.get(0);
        Location onde = new Location(mundo, p[0] + 0.5, p[1] + 1, p[2] + 0.5);
        onde.setYaw(ArenaComum.yawParaOCentro((int) p[0], (int) p[2]) + 180f);
        onde.setPitch(0f);
        return onde;
    }

    /** O terraço de ouro, para conferir chegada e para o pódio. */
    public static Location pontoDaSaida(World mundo, LavaCatalog planta) {
        List<double[]> helice = planta.helice();
        double[] p = helice.get(helice.size() - 1);
        Location onde = new Location(mundo, p[0] + 0.5, planta.cumeY() + 1, p[2] + 0.5);
        onde.setYaw(ArenaComum.yawParaOCentro((int) p[0], (int) p[2]));
        onde.setPitch(0f);
        return onde;
    }

    /** De onde se assiste: no alto do poço, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, LavaCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.cumeY() + 5, 0.5);
        onde.setYaw(180f);
        onde.setPitch(60f);
        return onde;
    }
}
