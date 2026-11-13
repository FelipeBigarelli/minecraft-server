package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
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
        if (planta.querMapaPronto()) {
            return construirComMapa(mundo, planta, aplicar);
        }
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
        if (planta.querMapaPronto()) {
            // A caixa aqui é só o saguão — ver o javadoc de
            // CorridaCatalog#alcanceDaObra. A estrada da montanha não é obra
            // nossa e não se apaga; o que se apaga é a decoração dela, casa
            // por casa, e só onde o material é um que nós pusemos.
            int enfeite = limparDecoracoes(mundo, planta);
            contador += ArenaComum.apagarACaixa(mundo, alcance,
                    planta.fundoDaObra(), planta.tetoDaObra());
            return new Resultado(true, "Saguão apagado: " + contador
                    + " blocos, dos quais " + enfeite + " de decoração da "
                    + "largada e da chegada. A estrada do mapa ficou "
                    + "intacta.", contador);
        }
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "Autódromo apagado: " + contador + " blocos.",
                contador);
    }

    /** Refaz só o chão da pista e das pontes. É o que roda a cada rodada. */
    public int refazerPista(World mundo) {
        CorridaCatalog planta = plugin.corridaCatalog();
        contador = 0;
        if (planta.querMapaPronto()) {
            // A cada rodada o rodízio pode ter trocado de percurso: tira a
            // decoração de todos e põe a do sorteado. É isto que faz a
            // largada nascer onde a corrida vai começar de verdade.
            limparDecoracoes(mundo, planta);
            CorridaCatalog.Percurso curso = planta.percursoDaRodada();
            if (curso != null) {
                decorar(mundo, planta, curso);
            }
            return contador;
        }
        List<double[]> eixo = planta.eixo();
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
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }


    // ------------------------------------------------------------------
    //  O MAPA PRONTO: a montanha já existe, nós só decoramos
    //
    //  Com um mapa pronto o construtor não levanta pista nenhuma: a estrada
    //  do Monte Akina veio no mundo, cinco mil novecentos e cinquenta e
    //  quatro blocos de gelo descendo cento e trinta e quatro fiadas, e
    //  gerar qualquer coisa por cima seria estragá-la. O que ele faz é
    //  LARGADA e CHEGADA — e faz de um jeito que não pode danificar o mapa:
    //
    //   * no chão, só troca GELO COMPACTADO por GELO AZUL, casa sim casa
    //     não. A outra metade do xadrez é o gelo que já estava lá. Nenhum
    //     bloco da montanha é tocado, e desfazer é trocar de volta;
    //   * no alto, só ocupa casas que estão VAZIAS. Desfazer devolve ao
    //     vazio, e só nas casas que carregam um material nosso.
    //
    //  E o gelo azul foi escolhido por ser gelo: linha de largada de
    //  concreto seria um freio no meio da pista, e um barco a oitenta por
    //  hora batendo em concreto é o contrário de uma largada bonita.
    // ------------------------------------------------------------------

    /** A altura do pórtico sobre a estrada. Quatro de folga para o barco. */
    public static final int ALTURA_DO_PORTICO = 7;

    /** Quantas fiadas de fundura tem a linha pintada no chão. */
    public static final int FUNDURA_DA_LINHA = 3;

    /** Quantas luzes tem a árvore de largada. Cinco, como na de verdade. */
    public static final int LUZES_DA_LARGADA = 5;

    /** Os tipos de casa do pórtico. */
    public static final int PILAR = 0;
    public static final int VIGA = 1;
    public static final int LANTERNA = 2;
    public static final int LUZ = 3;
    public static final int BANDEIRADA = 4;

    /**
     * Levanta o saguão e decora o percurso da rodada. Não gera pista.
     */
    private Resultado construirComMapa(World mundo, CorridaCatalog planta,
                                       boolean aplicar) {
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso == null) {
            return new Resultado(false, "O mapa pronto está ligado mas nenhum "
                    + "percurso abriu. Veja plugins/BigaCore/"
                    + planta.pastaDosPercursos() + " e o console.", 0);
        }
        if (!aplicar) {
            int ocupados = ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                    planta.fundoDaObra(), planta.tetoDaObra());
            return new Resultado(true, "Mapa pronto: " + planta.arquivosDoRodizio().size()
                    + " percurso(s) no rodízio, o desta rodada é '" + curso.nome()
                    + "' — " + Math.round(curso.comprimento()) + " blocos, "
                    + curso.quantosPortoes() + " portões, y "
                    + curso.alturaDaLargada() + " → "
                    + curso.alturaDoPortao(curso.quantosPortoes() - 1) + ". "
                    + "A obra só mexe no saguão (y " + planta.saguaoY() + ", raio "
                    + planta.alcanceDaObra() + "): " + (ocupados == 0 ? "livre"
                    : ocupados + " bloco(s) no caminho") + ".", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        contador += limparDecoracoes(mundo, planta);
        contador += decorar(mundo, planta, curso);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Saguão levantado sobre o mapa pronto: "
                + contador + " blocos. A estrada é a do mundo '" + planta.mundo()
                + "' e não foi tocada. Percurso da rodada: '" + curso.nome()
                + "'.", contador);
    }

    /**
     * Apaga a decoração de TODOS os percursos do rodízio.
     *
     * <p>De todos, e não só do da rodada: o rodízio sorteia um percurso por
     * rodada, e limpar apenas o atual deixaria o pórtico do anterior plantado
     * na montanha. Como cada percurso diz exatamente quais casas ele usa, a
     * conta é determinística e não precisa de nada anotado em disco.
     */
    public int limparDecoracoes(World mundo, CorridaCatalog planta) {
        int antes = contador;
        for (String arquivo : planta.arquivosDoRodizio()) {
            CorridaCatalog.Percurso curso = planta.percursoDoArquivo(arquivo);
            if (curso == null) {
                continue;
            }
            for (int i : pontosDecorados(curso)) {
                limparUmaLinha(mundo, planta, curso, i);
            }
        }
        return contador - antes;
    }

    /** Desenha a largada e a chegada do percurso. */
    public int decorar(World mundo, CorridaCatalog planta,
                       CorridaCatalog.Percurso curso) {
        int antes = contador;
        int[] onde = pontosDecorados(curso);
        desenharLinha(mundo, planta, curso, onde[0], true);
        desenharLinha(mundo, planta, curso, onde[1], false);
        return contador - antes;
    }

    /** Os dois índices do eixo que levam enfeite: a largada e a chegada. */
    static int[] pontosDecorados(CorridaCatalog.Percurso curso) {
        return new int[]{0, curso.indiceDoPortao(curso.quantosPortoes() - 1)};
    }

    // ------------------------------------------------------------------
    //  A geometria da decoração — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** Uma casa do chão, a {@code off} de lado e {@code ao} ao longo do eixo. */
    static int[] casa(double[] p, double[] t, double off, double ao) {
        double x = p[0] - t[1] * off + t[0] * ao;
        double z = p[2] + t[0] * off + t[1] * ao;
        return new int[]{(int) Math.floor(x), (int) Math.floor(z)};
    }

    /**
     * As casas do xadrez da linha, em conta pura.
     *
     * <p>Devolve só as casas CLARAS do tabuleiro — as escuras são o gelo que
     * já estava na estrada. É o que faz a linha não poder estragar o mapa:
     * metade dela não é posta, é descoberta.
     *
     * @return {x, z} de cada casa que leva gelo azul
     */
    static List<int[]> casasDaLinha(double[] p, double[] t, int meia, int fundura) {
        List<int[]> saida = new ArrayList<>();
        Set<Long> vistas = new HashSet<>();
        for (int u = 0; u < Math.max(1, fundura); u++) {
            for (double off = -meia; off <= meia + 0.001; off += PASSO) {
                int[] c = casa(p, t, off, u);
                if (Math.floorMod(c[0] + c[1], 2) != 0) {
                    continue;
                }
                if (vistas.add(chave(c[0], c[1]))) {
                    saida.add(c);
                }
            }
        }
        return saida;
    }

    /**
     * As casas do pórtico, em conta pura.
     *
     * <p>Dois pilares fora da estrada, uma viga atravessando no alto, duas
     * lanternas penduradas e a árvore de cinco luzes no meio da viga. Nada
     * abaixo de {@code altura - 2}: um barco com gente dentro tem dois blocos,
     * e pórtico que raspa na cabeça é pórtico que derruba.
     *
     * @return {x, dy, z, tipo, ordem} — dy é a fiada acima da estrada
     */
    static List<int[]> casasDoPortico(double[] p, double[] t, int meia,
                                      int altura, boolean bandeirada) {
        List<int[]> saida = new ArrayList<>();
        int borda = meia + 1;
        for (int lado = -1; lado <= 1; lado += 2) {
            int[] c = casa(p, t, lado * borda, 0);
            for (int dy = 0; dy <= altura; dy++) {
                saida.add(new int[]{c[0], dy, c[1], PILAR, 0});
            }
            int[] pendurada = casa(p, t, lado * meia, 0);
            saida.add(new int[]{pendurada[0], altura - 1, pendurada[1], LANTERNA, 0});
        }

        Set<Long> naViga = new HashSet<>();
        List<int[]> faixa = new ArrayList<>();
        for (double off = -borda; off <= borda + 0.001; off += PASSO) {
            int[] c = casa(p, t, off, 0);
            if (!naViga.add(chave(c[0], c[1]))) {
                continue;
            }
            saida.add(new int[]{c[0], altura, c[1], VIGA, 0});
            faixa.add(c);
        }

        if (bandeirada) {
            // A chegada: duas fiadas de xadrez penduradas na viga, que é o
            // que se vê de longe descendo — e de longe é a única hora em que
            // uma bandeirada serve para alguma coisa.
            for (int k = 0; k < faixa.size(); k++) {
                int[] c = faixa.get(k);
                for (int dy = altura - 2; dy <= altura - 1; dy++) {
                    saida.add(new int[]{c[0], dy, c[1], BANDEIRADA,
                            Math.floorMod(k + dy, 2)});
                }
            }
            return saida;
        }

        // A largada: as cinco luzes espalhadas pela viga, da esquerda para a
        // direita de quem desce, para acenderem na ordem em que se lê.
        for (int luz = 0; luz < LUZES_DA_LARGADA; luz++) {
            int onde = faixa.isEmpty() ? 0 : (int) Math.round(
                    (luz + 0.5) * (faixa.size() - 1) / (double) LUZES_DA_LARGADA);
            if (faixa.isEmpty()) {
                break;
            }
            int[] c = faixa.get(Math.min(faixa.size() - 1, onde));
            saida.add(new int[]{c[0], altura - 1, c[1], LUZ, luz});
        }
        return saida;
    }

    /**
     * Em que índice do eixo nasce o lugar k da grade.
     *
     * <p>A pole é a da frente — o MAIOR índice, que é quem está mais perto da
     * descida. Todos nascem ADIANTE da linha pintada, nunca em cima: a linha
     * tem fundura, e barco largado dentro dela começa escorregando.
     */
    static int indiceDaFila(int k, int total, double passo, int fundura) {
        int filas = Math.max(1, (Math.max(1, total) + 1) / 2);
        int fila = Math.min(filas - 1, Math.max(0, k) / 2);
        int entre = Math.max(1, (int) Math.round(4.0 / Math.max(0.5, passo)));
        int base = fundura + entre;
        return base + (filas - 1 - fila) * entre;
    }

    // ------------------------------------------------------------------
    //  A decoração no mundo
    // ------------------------------------------------------------------

    /** Se este bloco é estrada: é por ele que se mede a largura da pista. */
    static boolean ehEstrada(Material tipo) {
        return tipo == Material.PACKED_ICE || tipo == Material.BLUE_ICE
                || tipo == Material.ICE || tipo == Material.FROSTED_ICE;
    }

    /**
     * Quantos blocos de estrada há para cada lado, medidos no mundo.
     *
     * <p>Medida e não configurada porque a estrada do mapa muda de largura:
     * a reta de cima é larga, o grampo é estreito, e um pórtico de largura
     * fixa ou fica plantado dentro da pista ou flutuando no ar ao lado dela.
     */
    static int larguraDaEstrada(World mundo, double[] p, double[] t, int maximo) {
        int achou = 1;
        for (int off = 1; off <= maximo; off++) {
            boolean dosDois = temEstrada(mundo, p, t, off)
                    && temEstrada(mundo, p, t, -off);
            if (!dosDois) {
                break;
            }
            achou = off;
        }
        return achou;
    }

    private static boolean temEstrada(World mundo, double[] p, double[] t, int off) {
        int[] c = casa(p, t, off, 0);
        int y = (int) Math.round(p[1]);
        for (int dy = 0; dy >= -1; dy--) {
            if (ehEstrada(mundo.getBlockAt(c[0], y + dy, c[1]).getType())) {
                return true;
            }
        }
        return false;
    }

    /** Desenha uma linha e o pórtico dela. */
    private void desenharLinha(World mundo, CorridaCatalog planta,
                               CorridaCatalog.Percurso curso, int i,
                               boolean ehLargada) {
        double[] p = curso.ponto(i);
        double[] t = PistaAberta.tangente(curso.eixo(), i, planta.janelaDaTangente());
        int meia = larguraDaEstrada(mundo, p, t, (int) planta.meiaLarguraDoMapa());
        int y = (int) Math.round(p[1]);

        Material azul = planta.linhaDaPista();
        for (int[] c : casasDaLinha(p, t, meia, FUNDURA_DA_LINHA)) {
            pintarGelo(mundo, c[0], y, c[1], azul);
        }

        Material pilar = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material viga = planta.cor("metal", Material.IRON_BLOCK);
        Material clara = planta.cor("largada-clara", Material.WHITE_CONCRETE);
        Material escura = planta.cor("largada-escura", Material.BLACK_CONCRETE);
        Material apagada = planta.cor("largada-apagada", Material.BLACK_CONCRETE);

        for (int[] c : casasDoPortico(p, t, meia, ALTURA_DO_PORTICO, !ehLargada)) {
            Material qual = switch (c[3]) {
                case PILAR -> c[1] % 3 == 0 ? viga : pilar;
                case VIGA -> pilar;
                case LANTERNA -> Material.LANTERN;
                case BANDEIRADA -> c[4] == 0 ? clara : escura;
                default -> apagada;
            };
            seVazio(mundo, c[0], y + c[1], c[2], qual);
        }
    }

    /** Apaga a linha e o pórtico de um ponto, sem tocar na montanha. */
    private void limparUmaLinha(World mundo, CorridaCatalog planta,
                                CorridaCatalog.Percurso curso, int i) {
        double[] p = curso.ponto(i);
        double[] t = PistaAberta.tangente(curso.eixo(), i, planta.janelaDaTangente());
        int y = (int) Math.round(p[1]);
        // A largura máxima, não a medida: o pórtico pode ter sido posto
        // quando a estrada media outra coisa — depois de um /biga configs
        // atualizar, por exemplo —, e limpar pela medida de hoje deixaria a
        // ponta do de ontem para trás.
        int meia = (int) planta.meiaLarguraDoMapa();
        Material gelo = planta.pisoDaPista();

        for (int[] c : casasDaLinha(p, t, meia, FUNDURA_DA_LINHA)) {
            Block onde = mundo.getBlockAt(c[0], y, c[1]);
            if (onde.getType() == planta.linhaDaPista()) {
                onde.setType(gelo, false);
                contador++;
            }
        }
        for (int[] c : casasDoPortico(p, t, meia, ALTURA_DO_PORTICO, true)) {
            tirarSeNosso(mundo, planta, c[0], y + c[1], c[2]);
        }
        for (int[] c : casasDoPortico(p, t, meia, ALTURA_DO_PORTICO, false)) {
            tirarSeNosso(mundo, planta, c[0], y + c[1], c[2]);
        }
    }

    /**
     * Acende as luzes da árvore de largada.
     *
     * <p>Cinco vermelhas acendendo uma por segundo no preparo e TODAS
     * apagando de uma vez na largada — que é a árvore de verdade, e é a única
     * contagem que um piloto lê sem tirar os olhos da pista.
     *
     * @param acesas quantas luzes vermelhas, de 0 a cinco
     * @param verde  se as luzes viram verde (a largada) em vez de apagar
     */
    public int luzesDaLargada(World mundo, CorridaCatalog planta,
                              CorridaCatalog.Percurso curso, int acesas,
                              boolean verde) {
        if (curso == null) {
            return 0;
        }
        double[] p = curso.ponto(0);
        double[] t = PistaAberta.tangente(curso.eixo(), 0, planta.janelaDaTangente());
        int meia = larguraDaEstrada(mundo, p, t, (int) planta.meiaLarguraDoMapa());
        int y = (int) Math.round(p[1]);

        Material apagada = planta.cor("largada-apagada", Material.BLACK_CONCRETE);
        Material vermelha = planta.cor("largada-vermelha", Material.REDSTONE_BLOCK);
        Material lima = planta.cor("largada-verde", Material.LIME_CONCRETE);

        int mexeu = 0;
        for (int[] c : casasDoPortico(p, t, meia, ALTURA_DO_PORTICO, false)) {
            if (c[3] != LUZ) {
                continue;
            }
            Material qual = verde ? lima : (c[4] < acesas ? vermelha : apagada);
            Block onde = mundo.getBlockAt(c[0], y + c[1], c[2]);
            if (onde.getType() != qual) {
                onde.setType(qual, false);
                mexeu++;
            }
        }
        return mexeu;
    }

    /** Só troca gelo por gelo: nunca escreve sobre um bloco da montanha. */
    private void pintarGelo(World mundo, int x, int y, int z, Material azul) {
        Block onde = mundo.getBlockAt(x, y, z);
        if (ehEstrada(onde.getType()) && onde.getType() != azul) {
            onde.setType(azul, false);
            contador++;
        }
    }

    /** Só ocupa o vazio: nunca escreve sobre um bloco da montanha. */
    private void seVazio(World mundo, int x, int y, int z, Material material) {
        Block onde = mundo.getBlockAt(x, y, z);
        if (onde.getType().isAir()) {
            onde.setType(material, false);
            contador++;
        }
    }

    /** Só tira o que é nosso: o resto da montanha fica onde está. */
    private void tirarSeNosso(World mundo, CorridaCatalog planta, int x, int y,
                              int z) {
        Block onde = mundo.getBlockAt(x, y, z);
        if (materiaisDoPortico(planta).contains(onde.getType())) {
            onde.setType(Material.AIR, false);
            contador++;
        }
    }

    /** Tudo que o pórtico pode ter posto. É a lista que o desfazer confere. */
    static Set<Material> materiaisDoPortico(CorridaCatalog planta) {
        Set<Material> saida = new HashSet<>();
        saida.add(planta.cor("escuro", Material.POLISHED_DEEPSLATE));
        saida.add(planta.cor("metal", Material.IRON_BLOCK));
        saida.add(planta.cor("largada-clara", Material.WHITE_CONCRETE));
        saida.add(planta.cor("largada-escura", Material.BLACK_CONCRETE));
        saida.add(planta.cor("largada-apagada", Material.BLACK_CONCRETE));
        saida.add(planta.cor("largada-vermelha", Material.REDSTONE_BLOCK));
        saida.add(planta.cor("largada-verde", Material.LIME_CONCRETE));
        saida.add(Material.LANTERN);
        return saida;
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
        return ArenaComum.yawDaDirecao(dx, dz);
    }

    /**
     * A posição de largada número k: grade de dois por fila, atrás da linha.
     *
     * Atrás da linha de propósito: a largada é um portão como os outros, e
     * quem nasce em cima dela não a cruza — a primeira volta nunca começaria.
     */
    public static Location pontoDaGrade(World mundo, CorridaCatalog planta, int k) {
        return pontoDaGrade(mundo, planta, k, 2);
    }

    /**
     * A mesma grade, sabendo quantos vão largar.
     *
     * <p>O total importa só no mapa pronto, e importa muito: a pole tem de
     * ser o lugar MAIS ADIANTADO, e sem saber quantas filas existem não dá
     * para contar de trás para frente. Sem o total, quem larga em último sai
     * montanha abaixo na frente de todo mundo.
     */
    public static Location pontoDaGrade(World mundo, CorridaCatalog planta, int k,
                                        int total) {
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso != null) {
            int i = Math.min(curso.eixo().size() - 1, indiceDaFila(k, total,
                    planta.passoDoEixo(), FUNDURA_DA_LINHA));
            double[] p = curso.ponto(i);
            double[] t = PistaAberta.tangente(curso.eixo(), i,
                    planta.janelaDaTangente());
            double off = (k % 2 == 0 ? -1.5 : 1.5);
            Location pouso = new Location(mundo, p[0] - t[1] * off + 0.5,
                    Math.round(p[1]) + 1, p[2] + t[0] * off + 0.5);
            pouso.setYaw(yawDaDirecao(t[0], t[1]));
            pouso.setPitch(0f);
            return pouso;
        }
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
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso != null) {
            int i = curso.indiceDoPortao(k);
            double[] p = curso.ponto(i);
            double[] t = PistaAberta.tangente(curso.eixo(), i,
                    planta.janelaDaTangente());
            Location onde = new Location(mundo, p[0] + 0.5,
                    Math.round(p[1]) + 1, p[2] + 0.5);
            onde.setYaw(yawDaDirecao(t[0], t[1]));
            onde.setPitch(0f);
            return onde;
        }
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
        CorridaCatalog.Percurso curso = planta.percursoDaRodada();
        if (curso != null) {
            // Sobre a CHEGADA, e não sobre o meio: num percurso aberto o meio
            // é um trecho qualquer de montanha, e quem já cruzou quer ver os
            // outros cruzarem.
            double[] p = curso.ponto(
                    curso.indiceDoPortao(curso.quantosPortoes() - 1));
            Location alto = new Location(mundo, p[0] + 0.5,
                    Math.round(p[1]) + 14, p[2] + 0.5);
            alto.setYaw(0f);
            alto.setPitch(55f);
            return alto;
        }
        Location onde = new Location(mundo, 0.5, planta.topoY() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(50f);
        return onde;
    }
}
