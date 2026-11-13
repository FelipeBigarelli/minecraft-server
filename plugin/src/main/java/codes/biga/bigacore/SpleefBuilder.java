package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta a arena do Spleef por código.
 *
 * <h2>Um poço de gelo com um disco de neve na boca</h2>
 *
 * O chão é um disco só. Em volta dele, colado, um muro de vidro que vai do
 * nível do piso para cima: é por ele que não se perde a partida andando
 * distraído para fora. Abaixo do disco, o poço — trinta fiadas de parede de
 * gelo até um lago congelado no fundo. O lago está abaixo da linha de
 * eliminação e é cenário puro: cair num vazio preto não dá noção de altura
 * nenhuma, e metade da graça do Spleef é a queda.
 *
 * <h2>O muro tem dois de espessura, e isso é uma correção</h2>
 *
 * Um anel de um bloco só definido por "está em raio+1 e não em raio" TEM
 * buraco na diagonal: em raio 20, a célula (14,14) ainda é chão e a (15,15)
 * já está fora do anel. Quem corresse na diagonal sairia da arena pelo vão.
 * Com dois de espessura o anel fecha — e o mímico em
 * {@code gen/arena_spleef.py} confere isso andando pela arena inteira.
 *
 * <h2>O que é refeito a cada rodada</h2>
 *
 * Só o disco de neve. O poço, o muro, o lago, o saguão e os marcos são obra
 * de uma vez — e é por isso que o jogo só pode apagar os dois materiais do
 * piso, nunca o que estiver no caminho.
 */
public final class SpleefBuilder implements ConstrutorDeArena {

    /** Marca das entidades de texto da arena, para o desfazer achá-las. */
    public static final String TAG_PAINEL = "bigacore_spleef_painel";

    /** Espessura do muro. Dois, senão a diagonal tem vão — ver o cabeçalho. */
    static final int ESPESSURA_DO_MURO = 2;

    /** Meia largura da fachada de placas, contando o vão do arco no meio. */
    static final int LARGURA_DA_FACHADA = 5;

    /** A fiada em que as duas placas ficam: altura de olho de quem anda. */
    static final int FIADA_DA_PLACA = 2;

    /** As placas ficam no muro ao norte e se leem de dentro do saguão. */
    static final BlockFace FRENTE_DA_PLACA = BlockFace.SOUTH;

    private final BigaCore plugin;
    private int contador;

    public SpleefBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * @param aplicar false confere o terreno e devolve o tamanho da obra sem
     *                colocar um bloco. É o {@code /biga spleef info}.
     */
    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        SpleefCatalog planta = plugin.spleefCatalog();

        if (planta.saguaoRaio() >= planta.raio()) {
            return new Resultado(false, "O saguão (raio " + planta.saguaoRaio()
                    + ") é maior que a arena (raio " + planta.raio() + ").", 0);
        }
        if (planta.lagoY() >= planta.quedaY()) {
            return new Resultado(false, "O lago do fundo ficou acima da linha de "
                    + "eliminação: quem caísse pousaria nele em vez de ser eliminado.", 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Arena: " + (planta.raio() * 2 + 1) + " de diâmetro em y "
                    + planta.topoY() + ", muro de " + planta.muroAltura()
                    + ", poço de " + planta.poco() + " (elimina abaixo de y "
                    + planta.quedaY() + ", lago em y " + planta.lagoY()
                    + "). Saguão em y " + planta.saguaoY() + ". Aperta aos "
                    + planta.apertoSegundos() + "s, um anel a cada "
                    + planta.apertoIntervalo() + "s.", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área da "
                    + "arena. Rode /biga spleef desfazer CONFIRMAR antes, ou construa "
                    + "num mundo vazio.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        piso(mundo, planta);
        pocoEMuro(mundo, planta);
        lago(mundo, planta);
        marcos(mundo, planta);
        saguao(mundo, planta);
        escreverTextos(mundo, planta);

        Location pouso = pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Arena do Spleef levantada: " + contador
                + " blocos. O spawn do mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        SpleefCatalog planta = plugin.spleefCatalog();
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
        return new Resultado(true, "Arena apagada: " + contador + " blocos.", contador);
    }

    /**
     * Recoloca só o disco de neve. É o que roda no começo de cada rodada.
     *
     * Mil e duzentos blocos, imperceptíveis — e é o que permite jogar de novo
     * sem comando nenhum, inclusive depois de o aperto ter comido metade da
     * arena.
     */
    public int refazerPiso(World mundo) {
        contador = 0;
        piso(mundo, plugin.spleefCatalog());
        return contador;
    }

    /** Refaz só os letreiros, sem tocar em bloco. */
    @Override
    public Resultado textos(World mundo) {
        SpleefCatalog planta = plugin.spleefCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        escreverTextos(mundo, planta);
        return new Resultado(true, "Letreiros da arena refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  O disco
    // ------------------------------------------------------------------

    private void piso(World mundo, SpleefCatalog planta) {
        int raio = planta.raio();
        int y = planta.topoY();
        Material neve = planta.cor("piso", Material.SNOW_BLOCK);
        Material gelo = planta.cor("beira", Material.PACKED_ICE);

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                por(mundo, x, y, z,
                        ArenaComum.naBeira(x, z, raio, planta.beiraAneis()) ? gelo : neve);
            }
        }
    }

    // ------------------------------------------------------------------
    //  O poço e o muro
    // ------------------------------------------------------------------

    /**
     * O anel do muro, do nível do piso para cima, e a parede do poço, dele
     * para baixo.
     *
     * O muro começa NA fiada do piso, e não uma acima: deixando a fiada do
     * piso vazia no anel, sobraria uma fresta entre o chão e o muro por onde
     * se cai andando — que é exatamente o que o muro existe para impedir.
     */
    private void pocoEMuro(World mundo, SpleefCatalog planta) {
        int raio = planta.raio();
        int fora = raio + ESPESSURA_DO_MURO;
        int piso = planta.topoY();
        int topoDoMuro = piso + planta.muroAltura();

        Material vidro = planta.cor("muro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material remate = planta.cor("muro-topo", Material.BLUE_ICE);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material veio = planta.cor("lago-veio", Material.WHITE_CONCRETE);
        Material pedra = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora) || ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                // Para cima: o muro de vidro, para a arena se ver do saguão.
                for (int y = piso; y < topoDoMuro; y++) {
                    por(mundo, x, y, z, vidro);
                }
                por(mundo, x, topoDoMuro, z, remate);

                // Para baixo: a parede do poço, com veios de gelo de seis em
                // seis fiadas — é o que dá escala à queda enquanto se cai.
                for (int y = planta.lagoY() + 1; y < piso; y++) {
                    int fundo = y - planta.lagoY();
                    Material material = fundo % 6 == 0 ? veio
                            : (fundo % 6 == 1 ? pedra : escuro);
                    por(mundo, x, y, z, material);
                }
            }
        }
    }

    /** O lago congelado do fundo do poço. Cenário, abaixo da eliminação. */
    private void lago(World mundo, SpleefCatalog planta) {
        int fora = planta.raio() + ESPESSURA_DO_MURO;
        int y = planta.lagoY();
        Material gelo = planta.cor("lago", Material.BLUE_ICE);
        Material veio = planta.cor("lago-veio", Material.WHITE_CONCRETE);

        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora)) {
                    continue;
                }
                boolean rachadura = Math.floorMod(x * 3 + z * 5, 11) == 0
                        || Math.abs(x) == Math.abs(z);
                por(mundo, x, y, z, rachadura ? veio : gelo);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Os quatro marcos
    // ------------------------------------------------------------------

    /**
     * Obeliscos de gelo nos pontos cardeais, e um anel no alto ligando os
     * quatro.
     *
     * Mesma razão do TNT Run: num disco branco dentro de um mundo vazio não
     * há nada a que o olho se agarre, e sem referência fixa o jogador não sabe
     * para que lado está correndo. Ficam a raio+8 — fora de qualquer pulo,
     * porque bloco sólido ao alcance da beira é lugar de acampar.
     */
    private void marcos(World mundo, SpleefCatalog planta) {
        int d = planta.marcoDistancia();
        int base = planta.topoY() - 8;
        int topo = planta.topoY() + planta.marcoAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.DIAMOND_BLOCK);
        Material gelo = planta.cor("beira", Material.PACKED_ICE);

        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int cx = eixo[0] * d;
            int cz = eixo[1] * d;

            for (int y = base; y <= topo; y++) {
                int alto = y - base;
                boolean faixa = alto % 7 == 0;
                boolean pe = y < base + 3;
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        boolean miolo = ox == 0 && oz == 0;
                        boolean meioDaAresta = Math.abs(ox) + Math.abs(oz) == 1;
                        Material material;
                        if (pe) {
                            material = escuro;
                        } else if (miolo) {
                            material = borda;
                        } else if (faixa) {
                            material = gelo;
                        } else if (meioDaAresta && alto % 7 == 3) {
                            material = luz;
                        } else {
                            material = estrutura;
                        }
                        por(mundo, cx + ox, y, cz + oz, material);
                    }
                }
            }

            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    por(mundo, cx + ox, topo + 1, cz + oz,
                            Math.abs(ox) + Math.abs(oz) <= 1 ? nucleo : metal);
                }
            }
            for (int y = topo + 2; y <= topo + 5; y++) {
                por(mundo, cx, y, cz, rod);
            }
        }

        int anel = planta.topoY() + planta.marcoAltura() - 2;
        for (int i = -d; i <= d; i++) {
            for (int[] p : new int[][]{{i, -d}, {i, d}, {-d, i}, {d, i}}) {
                por(mundo, p[0], anel, p[1], Math.floorMod(i, 8) == 0 ? luz : borda);
            }
        }
    }

    // ------------------------------------------------------------------
    //  O saguão
    // ------------------------------------------------------------------

    /**
     * A caixa de vidro de onde se entra na rodada.
     *
     * Dezesseis acima do disco, com chão de vidro: quem espera vê a partida em
     * curso por baixo dos pés antes de entrar nela. O parapeito é alto o
     * bastante para não se cair fora — cair do saguão antes da largada
     * eliminaria o jogador numa rodada que ainda não começou.
     */
    private void saguao(World mundo, SpleefCatalog planta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.DIAMOND_BLOCK);

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                boolean rim = ArenaComum.naBeira(x, z, raio, 1);
                Material chao;
                if (rim) {
                    chao = escuro;
                } else if (x == 0 || z == 0) {
                    chao = Math.floorMod(x + z, 4) == 0 ? luz : estrutura;
                } else {
                    chao = vidro;
                }
                por(mundo, x, y, z, chao);

                if (!rim) {
                    continue;
                }
                for (int alto = 1; alto <= planta.saguaoParapeito(); alto++) {
                    por(mundo, x, y + alto, z,
                            Math.floorMod(x + z, 5) == 0 ? estrutura : vidro);
                }
                por(mundo, x, y + planta.saguaoParapeito() + 1, z, borda);
            }
        }

        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int px = eixo[0] * raio;
            int pz = eixo[1] * raio;
            for (int alto = planta.saguaoParapeito() + 2;
                 alto <= planta.saguaoParapeito() + 4; alto++) {
                por(mundo, px, y + alto, pz, rod);
            }
        }

        voltaDoSaguao(mundo, planta, estrutura, metal, nucleo, luz);
        fachadaDasPlacas(mundo, planta, estrutura, metal, escuro);
    }

    /**
     * O arco de volta ao lobby, em pé no chão do saguão.
     *
     * Sem ele o jogador que entra por curiosidade fica preso: não há chão por
     * onde sair e a única saída seria um comando que ele não conhece. O vão é
     * AR com cortina de partícula — bloco de portal de verdade levaria ao
     * Nether.
     */
    private void voltaDoSaguao(World mundo, SpleefCatalog planta, Material estrutura,
                               Material metal, Material nucleo, Material luz) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);
        int altura = alturaDoArcoDeVolta();

        for (int x = -2; x <= 2; x++) {
            boolean montante = Math.abs(x) == 2;
            for (int alto = 1; alto <= altura; alto++) {
                if (montante || alto == altura) {
                    por(mundo, x, y + alto, z, alto == altura ? metal : estrutura);
                } else {
                    ar(mundo, x, y + alto, z);
                }
            }
            por(mundo, x, y + altura + 1, z, Math.abs(x) <= 1 ? nucleo : metal);
        }
        por(mundo, -2, y + altura + 2, z, luz);
        por(mundo, 2, y + altura + 2, z, luz);
    }

    /**
     * O muro das duas placas, nos dois lados do arco de volta.
     *
     * Junto com o arco ele forma uma fachada de onze com uma porta de três no
     * meio: quem nasce no saguão olhando para o norte vê, numa só vista, o
     * nome do jogo, as regras, a placa de ENTRAR, a de SAIR e a saída.
     */
    private void fachadaDasPlacas(World mundo, SpleefCatalog planta,
                                  Material estrutura, Material metal, Material escuro) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);

        for (int x = -LARGURA_DA_FACHADA; x <= LARGURA_DA_FACHADA; x++) {
            if (Math.abs(x) <= 2) {
                continue;                                // o vão do arco
            }
            for (int alto = 1; alto <= alturaDoArcoDeVolta(); alto++) {
                por(mundo, x, y + alto, z,
                        alto == alturaDoArcoDeVolta() ? metal : estrutura);
            }
            por(mundo, x, y + alturaDoArcoDeVolta() + 1, z, escuro);
        }

        escreverPlaca(mundo, planta, pontoDaPlacaEntrar(planta), List.of());
        escreverPlaca(mundo, planta, pontoDaPlacaSair(planta), List.of());
        contador += 2;
    }

    // ------------------------------------------------------------------
    //  Pontos e medidas
    // ------------------------------------------------------------------

    static int zDoArcoDeVolta(int saguaoRaio) {
        return -(saguaoRaio - 2);
    }

    static int zDoArcoDeVolta(SpleefCatalog planta) {
        return zDoArcoDeVolta(planta.saguaoRaio());
    }

    static int alturaDoArcoDeVolta() {
        return 5;
    }

    /**
     * Onde fica a placa de ENTRAR.
     *
     * Um bloco À FRENTE do muro, porque placa de parede mora no ar e encosta
     * no bloco de trás. Fora da caixa do arco de propósito: parado na frente
     * da placa, o jogador não pode estar sendo mandado para o lobby.
     */
    static int[] pontoDaPlacaEntrar(SpleefCatalog planta) {
        return new int[]{-4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta) + 1};
    }

    static int[] pontoDaPlacaSair(SpleefCatalog planta) {
        return new int[]{4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta) + 1};
    }

    static void escreverPlaca(World mundo, SpleefCatalog planta, int[] onde,
                              List<Component> linhas) {
        ArenaComum.escreverPlaca(mundo, onde, planta.materialDaPlaca(),
                FRENTE_DA_PLACA, linhas);
    }

    /**
     * Se o jogador está no vão do arco de volta.
     *
     * Aperta em z de propósito: o arco fica no caminho de quem anda de um lado
     * ao outro do saguão, e uma caixa folgada mandaria para o lobby quem só
     * passou perto.
     */
    static boolean dentroDaVolta(double x, double y, double z,
                                 int saguaoY, int zArco) {
        return Math.abs(x - 0.5) <= 2.0
                && Math.abs(z - (zArco + 0.5)) <= 1.0
                && y >= saguaoY + 0.5 && y <= saguaoY + alturaDoArcoDeVolta();
    }

    @Override
    public Location pontoDeEntrada(World mundo) {
        return pontoDoSaguao(mundo, plugin.spleefCatalog());
    }

    public static Location pontoDoSaguao(World mundo, SpleefCatalog planta) {
        Location pouso = new Location(mundo, 0.5, planta.saguaoY() + 1, 0.5);
        pouso.setYaw(180f);
        pouso.setPitch(0f);
        return pouso;
    }

    public static Location pontoDaLargada(World mundo, SpleefCatalog planta, int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1, xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /**
     * De onde o eliminado assiste: acima do muro, olhando para baixo.
     *
     * Acima do muro e não dentro dele — de dentro, o vidro do muro fica entre
     * o olho e a arena e o eliminado assiste à partida através de uma parede.
     */
    public static Location pontoDaPlateia(World mundo, SpleefCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.muroAltura() + 6, 0.5);
        onde.setYaw(180f);
        onde.setPitch(55f);
        return onde;
    }

    // ------------------------------------------------------------------
    //  Letreiros
    // ------------------------------------------------------------------

    /**
     * Os letreiros, pelo mesmo código dos outros dezoito.
     *
     * Esta classe tinha cópia própria, com alturas fixas, e uma cópia é um
     * lugar a mais para o mesmo defeito voltar: o bloco de regras crescia
     * para cima da frase e para dentro do piso do saguão.
     */
    private void escreverTextos(World mundo, SpleefCatalog planta) {
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
    }

    // ------------------------------------------------------------------

    private void por(World mundo, int x, int y, int z, Material material) {
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }

    private void ar(World mundo, int x, int y, int z) {
        Block bloco = mundo.getBlockAt(x, y, z);
        if (!bloco.getType().isAir()) {
            bloco.setType(Material.AIR, false);
            contador++;
        }
    }

    private int contarOcupados(World mundo, SpleefCatalog planta) {
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

    /** Só para o teste: os materiais que o construtor realmente põe no piso. */
    static List<Material> materiaisQueOConstrutorUsaNoPiso(SpleefCatalog planta) {
        return List.of(
                planta.cor("piso", Material.SNOW_BLOCK),
                planta.cor("beira", Material.PACKED_ICE));
    }
}
