package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta a arena do TNT Run por código.
 *
 * <h2>Três discos, um saguão e quatro marcos</h2>
 *
 * Os pisos são discos concêntricos empilhados, cada um cinco abaixo do de
 * cima. O jogador começa no de cima; o bloco em que ele pisa some meio segundo
 * depois e ele cai para o próximo. Quando o de baixo acaba, não há próximo.
 *
 * <h2>Por que nada fica perto da beira</h2>
 *
 * Qualquer bloco sólido ao alcance de um pulo da beira do disco é um lugar
 * para acampar — e acampar é o único jeito de ganhar de graça neste jogo.
 * Por isso os marcos ficam a raio+8 do centro: um pulo com corrida passa de
 * quatro blocos e para muito antes de oito. O saguão, pelo mesmo motivo, fica
 * dezoito ACIMA e não ao lado.
 *
 * <h2>Os pisos são refeitos a cada rodada</h2>
 *
 * {@link #refazerPisos(World)} recoloca os discos e nada mais. São uns sete
 * mil blocos, imperceptíveis, e é o que permite jogar de novo sem comando
 * nenhum. O resto da obra — saguão, marcos, letreiros — é feito uma vez e
 * nunca mais tocado, que é também por que o derretimento só pode apagar os
 * quatro materiais do piso.
 *
 * <h2>A obra NÃO tem desfazer de verdade</h2>
 *
 * A arena nasce num mundo vazio, então desfazer é só apagar. O construtor se
 * recusa a levantar onde já exista bloco: sem essa trava, rodar o comando no
 * mundo errado apagaria construção de jogador sem copiar nada antes.
 */
public final class TntRunBuilder implements ConstrutorDeArena {

    /** Marca das entidades de texto da arena, para o desfazer achá-las. */
    public static final String TAG_PAINEL = "bigacore_tntrun_painel";

    private final BigaCore plugin;
    private int contador;

    public TntRunBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * @param aplicar false confere o terreno e devolve o tamanho da obra sem
     *                colocar um bloco. É o {@code /biga tntrun info}.
     */
    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        TntRunCatalog planta = plugin.tntRunCatalog();

        if (planta.vao() < TntRunCatalog.VAO_MINIMO) {
            return new Resultado(false, "O 'vao' do tnt-run.yml é " + planta.vao()
                    + ". Abaixo de " + TntRunCatalog.VAO_MINIMO + " o jogador pula de "
                    + "volta para o piso de cima e o jogo deixa de existir.", 0);
        }
        if (planta.saguaoRaio() >= planta.raio()) {
            return new Resultado(false, "O saguão (raio " + planta.saguaoRaio()
                    + ") é maior que a arena (raio " + planta.raio() + ").", 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Arena: " + (planta.raio() * 2 + 1) + " de diâmetro, "
                    + planta.pisos() + " pisos de " + planta.espessura()
                    + " com " + planta.vao() + " de vão (topo em y "
                    + planta.topoY() + ", fundo em y " + planta.fundoDaArena()
                    + ", elimina abaixo de y " + planta.quedaY() + "). Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área da "
                    + "arena. Rode /biga tntrun desfazer CONFIRMAR antes, ou construa "
                    + "num mundo vazio.", 0);
        }

        contador = 0;
        limparPaineis(mundo);

        pisos(mundo, planta);
        marcos(mundo, planta);
        saguao(mundo, planta);
        escreverTextos(mundo, planta);

        Location pouso = pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Arena do TNT Run levantada: " + contador
                + " blocos, " + planta.pisos() + " pisos. O spawn do mundo foi posto "
                + "no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        TntRunCatalog planta = plugin.tntRunCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        limparPaineis(mundo);
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
     * Recoloca só os discos. É o que roda no começo de cada rodada.
     *
     * Não mexe no saguão nem nos marcos de propósito: fosse a obra inteira, um
     * reinício de rodada custaria o travamento de segundos que o /construir
     * custa, a cada partida.
     */
    public int refazerPisos(World mundo) {
        contador = 0;
        pisos(mundo, plugin.tntRunCatalog());
        return contador;
    }

    /** Refaz só os letreiros, sem tocar em bloco. */
    @Override
    public Resultado textos(World mundo) {
        TntRunCatalog planta = plugin.tntRunCatalog();
        limparPaineis(mundo);
        escreverTextos(mundo, planta);
        return new Resultado(true, "Letreiros da arena refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Os pisos
    // ------------------------------------------------------------------

    private void pisos(World mundo, TntRunCatalog planta) {
        int raio = planta.raio();
        int aneis = planta.beiraAneis();

        Material piso = planta.cor("piso", Material.TNT);
        Material pisoBase = planta.cor("piso-base", Material.SAND);
        Material beira = planta.cor("beira", Material.POLISHED_BLACKSTONE);
        Material beiraBase = planta.cor("beira-base", Material.BLACKSTONE);

        for (int camada = 0; camada < planta.pisos(); camada++) {
            int topo = planta.topoDoPiso(camada);
            int base = planta.baseDoPiso(camada);

            for (int x = -raio; x <= raio; x++) {
                for (int z = -raio; z <= raio; z++) {
                    if (!TntRunCatalog.noDisco(x, z, raio)) {
                        continue;
                    }
                    boolean naBeira = TntRunCatalog.naBeira(x, z, raio, aneis);
                    por(mundo, x, topo, z, naBeira ? beira : piso);
                    for (int y = base; y < topo; y++) {
                        por(mundo, x, y, z, naBeira ? beiraBase : pisoBase);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Os quatro marcos
    // ------------------------------------------------------------------

    /**
     * Obeliscos nos pontos cardeais, e um anel no alto ligando os quatro.
     *
     * Eles não são enfeite gratuito: num disco de areia dentro de um mundo
     * vazio não há nada a que o olho se agarre, e sem referência fixa o
     * jogador não sabe para que lado está correndo. Quatro torres acesas nos
     * cardeais resolvem isso do mesmo jeito que as galerias do lobby — "estou
     * indo para o marco do norte" é uma frase que se pensa correndo.
     */
    private void marcos(World mundo, TntRunCatalog planta) {
        int d = planta.marcoDistancia();
        int base = planta.fundoDaArena() - 6;
        int topo = planta.topoY() + planta.marcoAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

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
                            material = metal;
                        } else if (meioDaAresta && alto % 7 == 3) {
                            material = luz;
                        } else {
                            material = estrutura;
                        }
                        por(mundo, cx + ox, y, cz + oz, material);
                    }
                }
            }

            // O coroamento: ametista e uma agulha de luz, que é o que faz o
            // marco se ler de longe e de noite.
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

        // O anel quadrado no alto. Passa pelos quatro marcos e fecha a
        // moldura: dá teto visual à arena sem pôr um bloco sobre ela.
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
     * Fica dezoito acima do piso de cima, com chão de vidro: quem espera vê a
     * arena inteira por baixo dos pés, e vê a rodada em curso antes de entrar
     * nela. O parapeito é alto o bastante para não se pular fora — não é
     * zelo, é que cair do saguão antes da largada eliminaria o jogador numa
     * rodada que ainda não começou.
     */
    private void saguao(World mundo, TntRunCatalog planta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!TntRunCatalog.noDisco(x, z, raio)) {
                    continue;
                }
                boolean rim = TntRunCatalog.naBeira(x, z, raio, 1);
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

        // Quatro agulhas de luz nos cardeais do parapeito, para o saguão ter
        // silhueta vista de dentro da arena.
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
     * O muro das duas placas, nos dois lados do arco de volta.
     *
     * Junto com o arco ele forma uma fachada de onze com uma porta de três no
     * meio: quem nasce no saguão olhando para o norte vê, numa só vista, o
     * nome do jogo, as regras, a placa de ENTRAR, a de SAIR e a saída. É o
     * contrário do desenho anterior, em que a rodada começava sozinha e o
     * jogador descobria isso caindo.
     */
    private void fachadaDasPlacas(World mundo, TntRunCatalog planta,
                                  Material estrutura, Material metal, Material escuro) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta.saguaoRaio());

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

    /**
     * O arco de volta ao lobby, em pé no chão do saguão.
     *
     * Existe porque sem ele o jogador que entra por curiosidade fica preso:
     * não há chão por onde sair, a rodada pode nem começar, e a única saída
     * seria um comando que ele não conhece. O vão é AR com cortina de
     * partícula — mesma solução dos portais do hub e do lobby, pelo mesmo
     * motivo (bloco de portal levaria ao Nether de verdade).
     */
    private void voltaDoSaguao(World mundo, TntRunCatalog planta, Material estrutura,
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

    /** Meia largura da fachada de placas, contando o vão do arco no meio. */
    static final int LARGURA_DA_FACHADA = 5;

    /** A fiada em que as duas placas ficam: altura de olho de quem anda. */
    static final int FIADA_DA_PLACA = 2;

    /**
     * Onde fica a placa de ENTRAR.
     *
     * Um bloco À FRENTE do muro, porque placa de parede mora no ar e encosta
     * no bloco de trás. Fora da caixa do arco de propósito: parada na frente
     * da placa, a pessoa não pode estar sendo mandada para o lobby.
     */
    static int[] pontoDaPlacaEntrar(TntRunCatalog planta) {
        return new int[]{-4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta.saguaoRaio()) + 1};
    }

    static int[] pontoDaPlacaSair(TntRunCatalog planta) {
        return new int[]{4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta.saguaoRaio()) + 1};
    }

    /** As placas ficam no muro ao norte e se leem de dentro do saguão. */
    static final BlockFace FRENTE_DA_PLACA = BlockFace.SOUTH;

    /**
     * Escreve (e coloca, se preciso) uma das placas.
     *
     * Coloca se preciso porque isto é chamado tanto pela obra quanto pelo jogo
     * a cada segundo: se a placa sumir por qualquer motivo, ela volta no
     * segundo seguinte em vez de o jogo virar um saguão sem como entrar.
     *
     * <p>A placa nasce ENCERADA. Não é enfeite: placa não encerada abre a tela
     * de edição no clique direito, e aí clicar nela não entra na fila — edita
     * o texto dela.
     */
    static void escreverPlaca(World mundo, TntRunCatalog planta, int[] onde,
                              List<Component> linhas) {
        ArenaComum.escreverPlaca(mundo, onde, planta.materialDaPlaca(),
                FRENTE_DA_PLACA, linhas);
    }

    /** Em que z o arco de volta fica. Dentro do disco, não na beira. */
    static int zDoArcoDeVolta(int saguaoRaio) {
        return -(saguaoRaio - 2);
    }

    static int zDoArcoDeVolta(TntRunCatalog planta) {
        return zDoArcoDeVolta(planta.saguaoRaio());
    }

    /**
     * Yaw de quem está em (x, z) e olha para o centro.
     *
     * Yaw do Bukkit: 0 olha para +Z, 90 para -X, 180 para -Z, 270 para +X. A
     * direção do centro é (-x, -z), que dá {@code atan2(x, -z)}. A conta
     * trocada — {@code atan2(-x, z)} — é a mesma virada ao contrário e
     * largaria todo mundo de costas para a arena, olhando para a beira: a
     * primeira reação de meia arena seria correr para fora dela.
     */
    static float yawParaOCentro(int x, int z) {
        return ArenaComum.yawParaOCentro(x, z);
    }

    static int alturaDoArcoDeVolta() {
        return 5;
    }

    /**
     * Se o jogador está no vão do arco de volta.
     *
     * Estática e sem mundo para o teste medir sem servidor. Aperta em z de
     * propósito: o arco fica no caminho de quem anda de um lado ao outro do
     * saguão, e uma caixa folgada mandaria para o lobby quem só passou perto.
     */
    static boolean dentroDaVolta(double x, double y, double z,
                                 int saguaoY, int zArco) {
        return Math.abs(x - 0.5) <= 2.0
                && Math.abs(z - (zArco + 0.5)) <= 1.0
                && y >= saguaoY + 0.5 && y <= saguaoY + alturaDoArcoDeVolta();
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return pontoDoSaguao(mundo, plugin.tntRunCatalog());
    }

    /**
     * O meio do saguão, olhando para o arco de volta e para o letreiro.
     *
     * É o spawn do mundo: quem chega pelo portal do lobby cai aqui. Olhar
     * para o norte põe na mesma vista o nome do jogo, as regras e a saída —
     * as três coisas que alguém que acabou de chegar precisa.
     */
    public static Location pontoDoSaguao(World mundo, TntRunCatalog planta) {
        Location pouso = new Location(mundo, 0.5, planta.saguaoY() + 1, 0.5);
        pouso.setYaw(180f);
        pouso.setPitch(0f);
        return pouso;
    }

    /** Uma das posições de largada, em pé no piso de cima. */
    public static Location pontoDaLargada(World mundo, TntRunCatalog planta, int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1, xz[1] + 0.5);
        pouso.setYaw(yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde o eliminado assiste: no alto do meio, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, TntRunCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.topoY() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(55f);
        return onde;
    }

    // ------------------------------------------------------------------
    //  Letreiros
    // ------------------------------------------------------------------

    private static final String REGUA = "<color:#55595E>";

    /**
     * O nome e as regras, no arco de volta.
     *
     * Yaw 0 porque quem lê está no meio do saguão olhando para o norte (yaw
     * 180), e um letreiro é legível por quem olha de yaw mais 180.
     */
    private void escreverTextos(World mundo, TntRunCatalog planta) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);
        String cor = planta.corDoTexto();

        StringBuilder regras = new StringBuilder();
        regras.append(REGUA).append("────────");
        for (String linha : planta.regrasEscritas()) {
            regras.append("\n<gray>").append(HubBuilder.escapar(linha));
        }

        // Empilhados acima da fachada, com folga entre eles: um letreiro de
        // cinco linhas cresce para os dois lados do ponto em que nasce, e
        // apertados eles se atravessam — defeito que só se vê em jogo.
        painel(mundo, new Location(mundo, 0.5, y + 11.0, z + 0.8),
                plugin.mini().deserialize("<color:" + cor + "><bold>"
                        + HubBuilder.escapar(planta.titulo()) + "</bold></color>"),
                (float) planta.escalaTitulo(), planta.fundoDoTexto());

        painel(mundo, new Location(mundo, 0.5, y + 9.4, z + 0.8),
                plugin.mini().deserialize("<gray>"
                        + HubBuilder.escapar(planta.subtitulo())),
                (float) planta.escalaPlaca(), planta.fundoDoTexto());

        if (!planta.regrasEscritas().isEmpty()) {
            painel(mundo, new Location(mundo, 0.5, y + 7.0, z + 0.8),
                    plugin.mini().deserialize(regras.toString()),
                    (float) planta.escalaPlaca(), planta.fundoDoTexto());
        }
    }

    private void painel(World mundo, Location alvo, Component texto,
                        float escala, int fundoAlpha) {
        ArenaComum.painel(mundo, alvo, texto, escala, fundoAlpha, TAG_PAINEL);
    }

    private void limparPaineis(World mundo) {
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
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

    private int contarOcupados(World mundo, TntRunCatalog planta) {
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

    /** Só para o teste: a lista de materiais que o construtor realmente põe no piso. */
    static List<Material> materiaisQueOConstrutorUsaNoPiso(TntRunCatalog planta) {
        return List.of(
                planta.cor("piso", Material.TNT),
                planta.cor("piso-base", Material.SAND),
                planta.cor("beira", Material.POLISHED_BLACKSTONE),
                planta.cor("beira-base", Material.BLACKSTONE));
    }
}
