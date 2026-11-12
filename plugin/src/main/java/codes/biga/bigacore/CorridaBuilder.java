package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Levanta o autódromo da Corrida por código.
 *
 * <h2>Chão calculado antes de ser posto</h2>
 *
 * O piso sai de {@link #chaoDaPista}, que é uma função pura: eixo e largura
 * entram, um mapa de casa para tipo de bloco sai. O construtor só escreve o
 * que esse mapa diz. Parece rodeio, mas é o que torna a pista TESTÁVEL sem
 * servidor: o teste chama a mesma função e confere que não há buraco, que a
 * faixa é andável de ponta a ponta e que a guia fica na borda — três defeitos
 * que em jogo só aparecem quando alguém cai por eles.
 *
 * <h2>Por que a faixa é varrida com meio bloco de passo</h2>
 *
 * Um ponto por bloco de eixo com um raio perpendicular inteiro deixa buraco na
 * borda de fora das curvas: lá, dois raios vizinhos abrem em leque e a casa do
 * meio não é tocada por nenhum. Varrer com passo de meio bloco em ambas as
 * direções tapa o leque. O custo é escrever a mesma casa várias vezes, e isso
 * o mapa resolve.
 */
public final class CorridaBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_corrida_painel";

    /** Os tipos de casa do chão. */
    public static final int ASFALTO = 0;
    public static final int GUIA = 1;
    public static final int EIXO = 2;
    public static final int LARGADA = 3;
    public static final int PONTE = 4;
    public static final int PONTE_BORDA = 5;

    /** De quanto em quanto se varre a faixa. Meio bloco tapa o leque das curvas. */
    private static final double PASSO = 0.5;

    private final BigaCore plugin;
    private int contador;

    public CorridaBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        CorridaCatalog planta = plugin.corridaCatalog();
        List<double[]> eixo = planta.eixo();

        String problema = conferirPista(eixo, planta.meiaLargura(),
                planta.muroAltura(), planta.atalhos(), planta.saguaoRaio());
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        Map<Long, Integer> chao = chaoDaPista(eixo, planta.meiaLargura(),
                planta.portoes());
        Map<Long, Integer> pontes = chaoDasPontes(eixo, planta.atalhos(),
                planta.atalhoLargura());

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Pista: " + eixo.size() + " blocos de volta, largura "
                    + planta.largura() + ", " + planta.quantosPortoes() + " portões, "
                    + planta.atalhos().length + " atalhos de "
                    + planta.atalhoAbertura() + "° (economia "
                    + economiaDosAtalhos(eixo, planta.atalhos()) + " blocos por volta). "
                    + "Chão: " + chao.size() + " casas de pista e " + pontes.size()
                    + " de ponte. Corrida de " + planta.voltas() + " voltas. "
                    + "Saguão em y " + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga corrida desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        escreverChao(mundo, planta, chao);
        muros(mundo, planta, eixo, pontes);
        escreverChao(mundo, planta, pontes);
        portico(mundo, planta, eixo);
        torres(mundo, planta, eixo);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Autódromo levantado: " + contador + " blocos, "
                + eixo.size() + " blocos de volta, " + planta.atalhos().length
                + " atalhos. O spawn do mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        CorridaCatalog planta = plugin.corridaCatalog();
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
        return new Resultado(true, "Autódromo apagado: " + contador + " blocos.",
                contador);
    }

    /** Refaz só o chão da pista e das pontes. É o que roda a cada rodada. */
    public int refazerPista(World mundo) {
        CorridaCatalog planta = plugin.corridaCatalog();
        List<double[]> eixo = planta.eixo();
        contador = 0;
        escreverChao(mundo, planta, chaoDaPista(eixo, planta.meiaLargura(),
                planta.portoes()));
        escreverChao(mundo, planta, chaoDasPontes(eixo, planta.atalhos(),
                planta.atalhoLargura()));
        return contador;
    }

    @Override
    public Resultado textos(World mundo) {
        CorridaCatalog planta = plugin.corridaCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferências — puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Confere a pista antes de pôr um bloco.
     *
     * Quatro perguntas, todas de defeito silencioso: a curva fecha sobre a
     * própria faixa? dois trechos da pista se encostam? algum atalho raspa na
     * pista principal e vira buraco no muro? algum atalho passa por baixo do
     * saguão?
     */
    static String conferirPista(List<double[]> eixo, int meia, int muro,
                               int[][] atalhos, int saguaoRaio) {
        double curva = PistaFechada.curvaturaMinima(eixo);
        if (curva <= meia + muro) {
            return "A pista tem curva de raio " + String.format("%.1f", curva)
                    + " e a faixa pede mais que " + (meia + muro)
                    + ": a borda de dentro se cruzaria. Baixe 'onda' ou suba 'raio'.";
        }

        int janela = (int) ((2 * meia + 2 * muro + 2) * 1.5);
        double folga = PistaFechada.folgaEntreTracos(eixo, janela);
        double precisa = 2 * meia + 2 * muro + 1;
        if (folga < precisa) {
            return "Dois trechos da pista ficam a " + String.format("%.1f", folga)
                    + " blocos um do outro, e a faixa com muro pede " + precisa
                    + ": a pista encostaria nela mesma. Baixe 'onda'.";
        }

        int n = eixo.size();
        for (int[] atalho : atalhos) {
            double[] a = eixo.get(atalho[0]);
            double[] b = eixo.get(atalho[1]);
            double comp = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (comp < 4) {
                return "Um atalho tem só " + String.format("%.1f", comp)
                        + " blocos: não é atalho, é um degrau. Suba 'atalho-abertura'.";
            }
            int boca = 10;
            for (int passo = 0; passo <= (int) comp; passo++) {
                double f = passo / comp;
                double px = a[0] + (b[0] - a[0]) * f;
                double pz = a[1] + (b[1] - a[1]) * f;
                if (Math.hypot(px, pz) < saguaoRaio + 3) {
                    return "Um atalho passa por baixo do saguão. Baixe "
                            + "'atalho-abertura'.";
                }
                for (int i = 0; i < n; i++) {
                    boolean perto = PistaFechada.entre(Math.floorMod(atalho[0] - boca, n),
                            Math.floorMod(atalho[1] + boca, n), i)
                            || i == Math.floorMod(atalho[0] - boca, n)
                            || i == Math.floorMod(atalho[1] + boca, n);
                    if (perto) {
                        continue;
                    }
                    if (Math.hypot(px - eixo.get(i)[0], pz - eixo.get(i)[1])
                            < meia + muro + 1) {
                        return "Um atalho raspa na pista no bloco " + i
                                + ": viraria buraco no muro. Baixe 'atalho-abertura'.";
                    }
                }
            }
        }
        return null;
    }

    /** Quantos blocos de volta cada atalho economiza, somados. */
    static int economiaDosAtalhos(List<double[]> eixo, int[][] atalhos) {
        int n = eixo.size();
        double soma = 0;
        for (int[] atalho : atalhos) {
            int arco = Math.floorMod(atalho[1] - atalho[0], n);
            double[] a = eixo.get(atalho[0]);
            double[] b = eixo.get(atalho[1]);
            soma += arco - Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        return (int) Math.round(soma);
    }

    /**
     * O chão da pista: de casa para tipo de bloco.
     *
     * A guia vai na borda, o eixo é tracejado no meio, e nos dois blocos em
     * volta de cada portão o chão é xadrez — não é enfeite: é como o jogador
     * sabe onde está o portão sem uma seta no HUD.
     */
    static Map<Long, Integer> chaoDaPista(List<double[]> eixo, int meia,
                                          int[] portoes) {
        Map<Long, Integer> saida = new HashMap<>();
        int n = eixo.size();
        Set<Integer> marcados = new HashSet<>();
        for (int p : portoes) {
            for (int d = -1; d <= 1; d++) {
                marcados.add(Math.floorMod(p + d, n));
            }
        }

        for (int i = 0; i < n; i++) {
            double[] a = eixo.get(i);
            double[] b = eixo.get((i + 1) % n);
            double[] la = PistaFechada.lateral(eixo, i);
            double[] lb = PistaFechada.lateral(eixo, (i + 1) % n);

            for (double sub = 0; sub < 1; sub += PASSO) {
                double cx = a[0] + (b[0] - a[0]) * sub;
                double cz = a[1] + (b[1] - a[1]) * sub;
                double lx = la[0] + (lb[0] - la[0]) * sub;
                double lz = la[1] + (lb[1] - la[1]) * sub;
                double norma = Math.hypot(lx, lz);
                lx /= norma;
                lz /= norma;

                for (double off = -meia; off <= meia; off += PASSO) {
                    int x = (int) Math.floor(cx + lx * off + 0.5);
                    int z = (int) Math.floor(cz + lz * off + 0.5);
                    long chave = chave(x, z);
                    int tipo;
                    if (marcados.contains(i)) {
                        tipo = LARGADA;
                    } else if (Math.abs(off) > meia - 0.5) {
                        tipo = GUIA;
                    } else if (Math.abs(off) < 0.5 && i % 6 < 3) {
                        tipo = EIXO;
                    } else {
                        tipo = ASFALTO;
                    }
                    // A guia e a largada não cedem lugar ao asfalto: a borda
                    // escrita por um raio vizinho não pode apagar a borda.
                    Integer antes = saida.get(chave);
                    if (antes == null || tipo > antes) {
                        saida.put(chave, tipo);
                    }
                }
            }
        }
        return saida;
    }

    /** O chão das pontes de atalho, com a borda dourada nas bocas. */
    static Map<Long, Integer> chaoDasPontes(List<double[]> eixo, int[][] atalhos,
                                            int largura) {
        Map<Long, Integer> saida = new HashMap<>();
        double meia = (largura - 1) / 2.0;
        for (int[] atalho : atalhos) {
            double[] a = eixo.get(atalho[0]);
            double[] b = eixo.get(atalho[1]);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double comp = Math.hypot(dx, dz);
            double ux = dx / comp;
            double uz = dz / comp;
            double px = -uz;
            double pz = ux;

            for (double andado = 0; andado <= comp; andado += PASSO) {
                boolean boca = andado <= 2 || andado >= comp - 2;
                boolean marco = ((int) andado) % 8 == 0;
                for (double off = -meia; off <= meia; off += PASSO) {
                    int x = (int) Math.floor(a[0] + ux * andado + px * off + 0.5);
                    int z = (int) Math.floor(a[1] + uz * andado + pz * off + 0.5);
                    int tipo = boca || (marco && Math.abs(off) > meia - 0.5)
                            ? PONTE_BORDA : PONTE;
                    Integer antes = saida.get(chave(x, z));
                    if (antes == null || tipo > antes) {
                        saida.put(chave(x, z), tipo);
                    }
                }
            }
        }
        return saida;
    }

    static long chave(int x, int z) {
        return (long) (x + 2048) * 4096L + (z + 2048);
    }

    static int xDaChave(long chave) {
        return (int) (chave / 4096L) - 2048;
    }

    static int zDaChave(long chave) {
        return (int) (chave % 4096L) - 2048;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void escreverChao(World mundo, CorridaCatalog planta,
                              Map<Long, Integer> chao) {
        Material asfalto = planta.cor("pista", Material.LIGHT_GRAY_CONCRETE);
        Material eixo = planta.cor("pista-eixo", Material.WHITE_CONCRETE);
        Material guiaClara = planta.cor("guia-clara", Material.WHITE_CONCRETE);
        Material guiaEscura = planta.cor("guia-escura", Material.RED_CONCRETE);
        Material largadaClara = planta.cor("largada-clara", Material.WHITE_CONCRETE);
        Material largadaEscura = planta.cor("largada-escura", Material.BLACK_CONCRETE);
        Material ponte = planta.cor("ponte", Material.POLISHED_BLACKSTONE);
        Material borda = planta.cor("ponte-borda", Material.GOLD_BLOCK);
        int y = planta.topoY();

        for (Map.Entry<Long, Integer> casa : chao.entrySet()) {
            int x = xDaChave(casa.getKey());
            int z = zDaChave(casa.getKey());
            Material material = switch (casa.getValue()) {
                case GUIA -> (Math.floorDiv(x + z, 3) % 2 == 0) ? guiaClara : guiaEscura;
                case EIXO -> eixo;
                case LARGADA -> Math.floorMod(x + z, 2) == 0
                        ? largadaClara : largadaEscura;
                case PONTE -> ponte;
                case PONTE_BORDA -> borda;
                default -> asfalto;
            };
            por(mundo, x, y, z, material);
        }
    }

    /**
     * Os muros dos dois lados — o que impede cortar caminho a pé.
     *
     * Dois blocos de altura e não um: um bloco se pula, e pular o muro é
     * cortar a volta inteira. E nada de muro na boca dos atalhos, senão o
     * atalho não tem entrada.
     */
    private void muros(World mundo, CorridaCatalog planta, List<double[]> eixo,
                       Map<Long, Integer> pontes) {
        Material muro = planta.cor("muro", Material.WHITE_TERRACOTTA);
        Material topo = planta.cor("muro-topo", Material.BLACK_CONCRETE);
        int y = planta.topoY();
        int altura = planta.muroAltura();
        int meia = planta.meiaLargura();
        int n = eixo.size();

        Set<Long> proibidas = new HashSet<>();
        for (long chave : pontes.keySet()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    proibidas.add(chave(xDaChave(chave) + dx, zDaChave(chave) + dz));
                }
            }
        }

        for (int i = 0; i < n; i++) {
            double[] a = eixo.get(i);
            double[] b = eixo.get((i + 1) % n);
            double[] la = PistaFechada.lateral(eixo, i);
            double[] lb = PistaFechada.lateral(eixo, (i + 1) % n);
            for (double sub = 0; sub < 1; sub += PASSO) {
                double cx = a[0] + (b[0] - a[0]) * sub;
                double cz = a[1] + (b[1] - a[1]) * sub;
                double lx = la[0] + (lb[0] - la[0]) * sub;
                double lz = la[1] + (lb[1] - la[1]) * sub;
                double norma = Math.hypot(lx, lz);
                lx /= norma;
                lz /= norma;
                for (int lado = -1; lado <= 1; lado += 2) {
                    double off = lado * (meia + 1);
                    int x = (int) Math.floor(cx + lx * off + 0.5);
                    int z = (int) Math.floor(cz + lz * off + 0.5);
                    if (proibidas.contains(chave(x, z))) {
                        continue;
                    }
                    for (int alto = 1; alto <= altura; alto++) {
                        por(mundo, x, y + alto, z, alto == altura ? topo : muro);
                    }
                }
            }
        }
    }

    /**
     * O pórtico da largada: duas torres, uma viga e a lâmpada.
     *
     * Serve de marco visível de longe — numa pista de duzentos e cinquenta
     * blocos, "falta meia volta" se lê pelo pórtico e não por um número no
     * canto da tela.
     */
    private void portico(World mundo, CorridaCatalog planta, List<double[]> eixo) {
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material nucleo = planta.cor("nucleo", Material.REDSTONE_LAMP);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        int y = planta.topoY();
        int meia = planta.meiaLargura();
        int alto = 6;
        double[] p = eixo.get(0);
        double[] lat = PistaFechada.lateral(eixo, 0);

        for (int lado = -1; lado <= 1; lado += 2) {
            double off = lado * (meia + 2);
            int x = (int) Math.floor(p[0] + lat[0] * off + 0.5);
            int z = (int) Math.floor(p[1] + lat[1] * off + 0.5);
            for (int k = 0; k <= alto; k++) {
                por(mundo, x, y + k, z, k == alto ? metal : (k % 3 == 0 ? escuro : metal));
            }
            por(mundo, x, y + alto - 1, z, luz);
        }

        // A viga: atravessa a pista de guia a guia, na altura do pórtico.
        for (double off = -(meia + 2); off <= meia + 2; off += PASSO) {
            int x = (int) Math.floor(p[0] + lat[0] * off + 0.5);
            int z = (int) Math.floor(p[1] + lat[1] * off + 0.5);
            por(mundo, x, y + alto, z, escuro);
            if (Math.abs(off) < 0.5) {
                por(mundo, x, y + alto + 1, z, nucleo);
            } else if (Math.abs(Math.abs(off) - (meia / 2.0)) < PASSO) {
                por(mundo, x, y + alto - 1, z, rod);
            }
        }
    }

    /**
     * Três torres de refletor, uma por bojo, fora da pista.
     *
     * Fora e não dentro: dentro elas seriam teto sobre a faixa, e teto sobre a
     * faixa é escuridão onde se corre. Fora, dão a altura que faz a pista
     * parecer estádio e servem de ponto de referência de onde se está na volta.
     */
    private void torres(World mundo, CorridaCatalog planta, List<double[]> eixo) {
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        int y = planta.topoY();
        int altura = planta.torreAltura();
        int recuo = planta.torreRecuo();

        for (int tip : PistaFechada.bojos(eixo, planta.voltasDaOnda())) {
            double[] p = eixo.get(tip);
            double r = Math.hypot(p[0], p[1]);
            int cx = (int) Math.round(p[0] / r * (r + recuo));
            int cz = (int) Math.round(p[1] / r * (r + recuo));

            for (int alto = -3; alto <= altura; alto++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean miolo = dx == 0 && dz == 0;
                        Material qual = miolo ? metal
                                : (alto % 6 == 0 ? borda : escuro);
                        por(mundo, cx + dx, y + alto, cz + dz, qual);
                    }
                }
            }
            // O refletor: uma moldura de 5x5 com lâmpadas e hastes de luz.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean beirada = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    por(mundo, cx + dx, y + altura + 1, cz + dz,
                            beirada ? metal : luz);
                    if (beirada && (dx == 0 || dz == 0)) {
                        por(mundo, cx + dx, y + altura, cz + dz, rod);
                    }
                }
            }
        }
    }

    private int contarOcupados(World mundo, CorridaCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.corridaCatalog());
    }

    /** O yaw de quem olha para a direção (dx, dz). */
    public static float yawDaDirecao(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * A posição de largada número k: grade de dois por fila, atrás da linha.
     *
     * Atrás da linha de propósito: a largada é um portão como os outros, e
     * quem nasce em cima dela não a cruza — a primeira volta nunca começaria.
     */
    public static Location pontoDaGrade(World mundo, CorridaCatalog planta, int k) {
        double[] onde = grade(planta.eixo(), k);
        Location pouso = new Location(mundo, onde[0], planta.topoY() + 1, onde[1]);
        pouso.setYaw((float) onde[2]);
        pouso.setPitch(0f);
        return pouso;
    }

    /** A grade em conta pura: {x, z, yaw} do lugar número k. */
    static double[] grade(List<double[]> eixo, int k) {
        int n = eixo.size();
        int fila = k / 2;
        int i = Math.floorMod(-3 - fila * 3, n);
        double off = (k % 2 == 0 ? -1.5 : 1.5);

        double[] p = eixo.get(i);
        double[] lat = PistaFechada.lateral(eixo, i);
        double[] t = PistaFechada.tangente(eixo, i);
        return new double[]{p[0] + lat[0] * off + 0.5, p[1] + lat[1] * off + 0.5,
                yawDaDirecao(t[0], t[1])};
    }

    /** Em cima do portão k, olhando para onde se corre. */
    public static Location pontoDoPortao(World mundo, CorridaCatalog planta, int k) {
        List<double[]> eixo = planta.eixo();
        int i = planta.indiceDoPortao(k);
        double[] p = eixo.get(i);
        double[] t = PistaFechada.tangente(eixo, i);
        Location onde = new Location(mundo, p[0] + 0.5, planta.topoY() + 1,
                p[1] + 0.5);
        onde.setYaw(yawDaDirecao(t[0], t[1]));
        onde.setPitch(0f);
        return onde;
    }

    /** De onde se assiste: no alto do meio da pista, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, CorridaCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.topoY() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(50f);
        return onde;
    }
}
