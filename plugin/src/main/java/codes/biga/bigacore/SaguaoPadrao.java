package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * O saguão: a sala de espera que todo jogo tem, e que nenhum reescreve.
 *
 * <h2>Sempre o mesmo, de propósito</h2>
 *
 * Caixa redonda de vidro suspensa sobre a arena, com o chão transparente para
 * se ver a partida em curso por baixo dos pés. Ao norte, uma fachada de onze
 * com uma porta de três no meio: a porta é o arco de volta ao lobby, à
 * esquerda dela a placa de ENTRAR, à direita a de SAIR, e acima das três o
 * nome do jogo, a frase dele e as regras.
 *
 * <p>Ser idêntico nos vinte jogos não é preguiça: é a única coisa que o
 * jogador aprende UMA vez. Quem descobriu no TNT Run que se entra clicando na
 * placa verde já sabe entrar nos outros dezenove, e quem se perdeu uma vez
 * sabe que a saída é sempre o arco do meio.
 *
 * <h2>O que cada jogo ainda decide</h2>
 *
 * A paleta (pelo catálogo), a altura em que o saguão flutua e o raio dele. O
 * resto — onde ficam as placas, para que lado elas olham, de que lado se lê o
 * letreiro, onde é a caixa que devolve ao lobby — é aqui, e é igual.
 */
public final class SaguaoPadrao {

    private SaguaoPadrao() {
    }

    /** Meia largura da fachada de placas, contando o vão do arco no meio. */
    public static final int LARGURA_DA_FACHADA = 5;

    /** A fiada em que as duas placas ficam: altura de olho de quem anda. */
    public static final int FIADA_DA_PLACA = 2;

    /** As placas ficam no muro ao norte e se leem de dentro do saguão. */
    public static final BlockFace FRENTE_DA_PLACA = BlockFace.SOUTH;

    private static final String REGUA = "<color:#55595E>";

    /** O cinza claro do corpo das regras: lê-se contra o céu e contra o muro. */
    private static final String TINTA = "#C9D1D9";

    /** O cinza escuro do rodapé e dos traços. */
    private static final String SOMBRA = "#6E7681";

    /**
     * Altura de UMA linha de letreiro, em blocos, na escala 1.
     *
     * É o número que faltava: os três painéis ficavam em alturas fixas
     * (11.0, 9.4, 6.6) escolhidas no olho para um bloco de regras curto.
     * Um jogo com seis regras longas quebra cada uma em duas linhas, o
     * painel cresce para os DOIS lados do ponto em que nasce, e aí ele
     * sobe por cima da frase e desce para dentro do piso do saguão — que
     * é exatamente o que se via no TNT Run.
     */
    private static final double ALTURA_DA_LINHA = 0.27;

    /** O vão entre dois painéis, em blocos. */
    private static final double FOLGA = 0.7;

    /** De quanto o painel mais baixo sobe do piso do saguão. */
    /**
     * Onde a pilha de letreiros começa, contada do piso do saguão.
     *
     * Era 3,2 — a altura do olho — e os letreiros ficavam pendurados bem no
     * vão do arco de volta: o maior painel da rua tapava a própria saída.
     * Agora começa ACIMA do frontão do arco, que vai até nove. A rua inteira
     * aponta para o arco, o arco é o fim da rua e o nome do jogo fica em
     * cima dele, que é onde a vila do mapa de referência põe o letreiro
     * dela.
     */
    private static final double ALTURA_LIVRE = 10.5;

    /**
     * Quantas letras cabem numa linha antes de o letreiro quebrar.
     *
     * O painel é criado com largura de 220 pixels e a fonte do Minecraft
     * gasta uns 6 por letra. Trinta e quatro é esse número com folga: a
     * conta só precisa errar para MAIS linhas, porque errar para menos é
     * o painel voltar a se atravessar.
     */
    private static final int LETRAS_POR_LINHA = 34;

    // ------------------------------------------------------------------
    //  Medidas
    // ------------------------------------------------------------------

    /** Em que z o arco de volta fica. Dentro do disco, não na beira. */
    public static int zDoArcoDeVolta(int saguaoRaio) {
        return -(saguaoRaio - 2);
    }

    public static int zDoArcoDeVolta(PlantaDeArena planta) {
        return zDoArcoDeVolta(planta.saguaoRaio());
    }

    public static int alturaDoArcoDeVolta() {
        return 5;
    }

    /**
     * Onde fica a placa de ENTRAR.
     *
     * Um bloco À FRENTE do muro, porque placa de parede mora no ar e encosta
     * no bloco de trás. Fora da caixa do arco de propósito: parado na frente
     * da placa, o jogador não pode estar sendo mandado para o lobby.
     */
    public static int[] pontoDaPlacaEntrar(PlantaDeArena planta) {
        return new int[]{-4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta) + 1};
    }

    public static int[] pontoDaPlacaSair(PlantaDeArena planta) {
        return new int[]{4, planta.saguaoY() + FIADA_DA_PLACA,
                zDoArcoDeVolta(planta) + 1};
    }

    /**
     * Se o jogador está no vão do arco de volta.
     *
     * Estática e sem mundo para o teste medir sem servidor. Aperta em z de
     * propósito: o arco fica no caminho de quem anda de um lado ao outro do
     * saguão, e uma caixa folgada mandaria para o lobby quem só passou perto.
     */
    public static boolean dentroDaVolta(double x, double y, double z,
                                        int saguaoY, int zArco) {
        return Math.abs(x - 0.5) <= 2.0
                && Math.abs(z - (zArco + 0.5)) <= 1.0
                && y >= saguaoY + 0.5 && y <= saguaoY + alturaDoArcoDeVolta();
    }

    /**
     * O meio do saguão, olhando para o arco de volta e para o letreiro.
     *
     * É o spawn do mundo: quem chega pelo portal do lobby cai aqui. Olhar
     * para o norte põe na mesma vista o nome do jogo, as regras e a saída —
     * as três coisas de que alguém que acabou de chegar precisa.
     */
    public static Location pontoDoSaguao(World mundo, PlantaDeArena planta) {
        Location pouso = new Location(mundo, 0.5, planta.saguaoY() + 1, 0.5);
        pouso.setYaw(180f);
        pouso.setPitch(0f);
        return pouso;
    }

    /** Se o chão do saguão existe. Barato, e é o que diz se há arena. */
    public static boolean construido(World mundo, PlantaDeArena planta) {
        return !mundo.getBlockAt(0, planta.saguaoY(), 0).getType().isAir();
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  A RUA
    //
    //  O saguão era uma caixa: disco de vidro, parapeito e um arco. Cumpria
    //  a função e não era lugar nenhum — o jogador pousava, lia as regras e
    //  clicava em ENTRAR sem nunca ter chegado a um sítio.
    //
    //  Esta é a planta de uma RUA DE VILA, tirada do saguão do mapa que o
    //  Felipe mandou: uma avenida de grama entre duas fileiras de casas de
    //  dois andares, com o andar de cima em BALANÇO sobre a calçada, vigas
    //  escuras e reboco claro, floreiras nas janelas, lampiões pendurados
    //  debaixo do balanço, bandeirinhas atravessadas no alto e, no fundo da
    //  rua, um arco monumental com o letreiro. Quem pousa está numa rua e
    //  olha para o fim dela.
    //
    //  AS MEDIDAS SAEM DO RAIO, e por isso a mesma planta serve aos vinte
    //  jogos sem ajuste: a avenida é um terço do diâmetro, a calçada é a
    //  faixa seguinte e as casas ocupam o que sobra até o parapeito.
    //
    //  AS CORES SAEM DA PALETA DE CADA JOGO. Toda chave nova tem padrão, e
    //  nenhum yml precisa mudar para a rua existir; o yml que quiser a sua
    //  rua com a sua cor escreve `paleta.viga`, `paleta.reboco` e as outras
    //  e tem a mesma rua na cor dele. É a diferença entre vinte saguões
    //  iguais e vinte saguões irmãos.
    // ------------------------------------------------------------------

    /** Metade da largura da avenida. Um terço do diâmetro, nunca menos de 2. */
    public static int meiaAvenida(int raio) {
        return Math.max(2, raio / 3);
    }

    /** A calçada: a faixa logo fora da avenida, onde ficam árvores e postes. */
    public static int xDaCalcada(int raio) {
        return meiaAvenida(raio) + 1;
    }

    /** A coluna da FACHADA: onde a parede térrea das casas encosta na rua. */
    public static int xDaFachada(int raio) {
        return meiaAvenida(raio) + 2;
    }

    /**
     * Se há casa neste ponto da fileira.
     *
     * Módulos de seis: cinco de casa e um de viela. A viela não é enfeite —
     * é o que impede a rua de virar um corredor de duas paredes lisas, e é
     * por ela que entra a luz do céu e se vê a arena lá embaixo.
     */
    public static boolean temCasa(int z, int zArco) {
        return Math.floorMod(z - zArco, 6) < 5;
    }

    /** Se este ponto é o MEIO de uma viela: onde vão árvore, poste e bandeira. */
    public static boolean noMeioDaViela(int z, int zArco) {
        return Math.floorMod(z - zArco, 6) == 5;
    }

    /**
     * Levanta o saguão inteiro: a rua, as casas, o arco, as placas e os
     * letreiros.
     *
     * @return quantos blocos foram postos, para o construtor do jogo somar.
     */
    public static int construir(BigaCore plugin, World mundo, PlantaDeArena planta,
                                String etiquetaDoPainel) {
        Obra obra = new Obra(mundo);

        Paleta tinta = new Paleta(planta);
        chaoDaRua(obra, planta, tinta);
        parapeito(obra, planta, tinta);
        asCasas(obra, planta, tinta);
        aRuaViva(obra, planta, tinta);
        arcoDeVolta(obra, planta, tinta);
        fachadaDasPlacas(obra, planta, tinta);

        escreverPlaca(mundo, planta, pontoDaPlacaEntrar(planta), List.of());
        escreverPlaca(mundo, planta, pontoDaPlacaSair(planta), List.of());
        escreverTextos(plugin, mundo, planta, etiquetaDoPainel);

        return obra.contador + 2;
    }

    /**
     * O chão: avenida de grama, caminho batido no meio, calçada e vielas.
     *
     * A VIELA É DE VIDRO. É o que sobrou da caixa antiga, e sobrou de
     * propósito: o saguão fica no alto, a arena está lá embaixo, e ver a
     * partida anterior acabando pelo vão entre duas casas é metade do motivo
     * de o saguão ficar no alto. Vidro em toda a rua devolveria a caixa;
     * vidro nenhum esconderia a arena.
     */
    private static void chaoDaRua(Obra obra, PlantaDeArena planta, Paleta tinta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();
        int zArco = zDoArcoDeVolta(planta);
        int avenida = meiaAvenida(raio);
        int calcada = xDaCalcada(raio);

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)
                        || ArenaComum.naBeira(x, z, raio, 1)) {
                    continue;
                }
                int fora = Math.abs(x);
                boolean viela = !temCasa(z, zArco);
                Material chao;
                if (fora <= 1) {
                    chao = tinta.caminho;                  // a trilha batida
                } else if (viela) {
                    // A VIELA É DE VIDRO DE PONTA A PONTA, e é o que sobrou
                    // da caixa antiga. O saguão fica no alto e a arena está
                    // lá embaixo: ver a partida anterior acabando pelo vão
                    // entre duas casas é metade do motivo de o saguão ficar
                    // no alto. Vidro na rua toda devolveria a caixa de vidro;
                    // vidro nenhum esconderia a arena.
                    chao = tinta.vidro;
                } else if (fora <= avenida) {
                    chao = tinta.grama;
                } else if (fora <= calcada) {
                    chao = tinta.alvenaria;                // a calçada
                } else {
                    chao = tinta.madeira;                  // o assoalho da casa
                }
                obra.por(x, y, z, chao);
            }
        }
    }

    /** O parapeito da beira: é ele que impede a queda, com casa ou sem casa. */
    private static void parapeito(Obra obra, PlantaDeArena planta, Paleta tinta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDisco(x, z, raio)
                        || !ArenaComum.naBeira(x, z, raio, 1)) {
                    continue;
                }
                obra.por(x, y, z, tinta.escuro);
                for (int alto = 1; alto <= planta.saguaoParapeito(); alto++) {
                    obra.por(x, y + alto, z,
                            Math.floorMod(x + z, 5) == 0 ? tinta.estrutura
                                    : tinta.vidro);
                }
                obra.por(x, y + planta.saguaoParapeito() + 1, z, tinta.borda);
            }
        }

        // Quatro agulhas de luz nos cardeais, para o saguão ter silhueta
        // vista de dentro da arena.
        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int alto = planta.saguaoParapeito() + 2;
                 alto <= planta.saguaoParapeito() + 4; alto++) {
                obra.por(eixo[0] * raio, y + alto, eixo[1] * raio, tinta.luzFria);
            }
        }
    }

    /**
     * As duas fileiras de casas.
     *
     * O ANDAR DE CIMA AVANÇA UM BLOCO sobre a calçada, e é só disso que vem
     * a silhueta da rua: sem o balanço são duas paredes retas e o lugar não
     * é uma vila, é um corredor. O balanço cria sombra sobre a calçada, dá
     * onde pendurar o lampião e põe a floreira na altura do olho de quem
     * anda embaixo.
     *
     * <p>Quatro blocos de pé-direito embaixo do balanço: dá para passar
     * correndo sem encostar a cabeça.
     */
    private static void asCasas(Obra obra, PlantaDeArena planta, Paleta tinta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();
        int zArco = zDoArcoDeVolta(planta);
        int fachada = xDaFachada(raio);
        int balanco = fachada - 1;

        for (int lado = -1; lado <= 1; lado += 2) {
            for (int z = -raio; z <= raio; z++) {
                if (!temCasa(z, zArco)) {
                    continue;
                }
                int modulo = Math.floorMod(z - zArco, 6);
                boolean meioDaCasa = modulo == 2;
                boolean quinaDaCasa = modulo == 0 || modulo == 4;

                // --- o térreo, na linha da fachada ---
                for (int x = fachada; x <= raio; x++) {
                    int px = lado * x;
                    if (!ArenaComum.noDisco(px, z, raio - 1)) {
                        continue;
                    }
                    if (x > fachada) {
                        continue;                      // o miolo da casa é oco
                    }
                    obra.por(px, y + 1, z, tinta.alvenaria);
                    for (int alto = 2; alto <= 4; alto++) {
                        Material qual = quinaDaCasa ? tinta.viga
                                : meioDaCasa && alto <= 3 ? tinta.vidro
                                        : tinta.reboco;
                        obra.por(px, y + alto, z, qual);
                    }
                }

                // --- o andar de cima, avançado um bloco sobre a calçada ---
                int px = lado * balanco;
                if (!ArenaComum.noDisco(px, z, raio - 1)) {
                    continue;
                }
                obra.por(px, y + 5, z, tinta.madeira);     // a forra do balanço
                for (int alto = 6; alto <= 8; alto++) {
                    Material qual = quinaDaCasa ? tinta.viga
                            : meioDaCasa && alto <= 7 ? tinta.vidro
                                    : tinta.reboco;
                    obra.por(px, y + alto, z, qual);
                }

                // --- a floreira, na janela do andar de cima ---
                if (meioDaCasa) {
                    obra.por(lado * (balanco - 1), y + 5, z, tinta.folhagem);
                    obra.por(lado * (balanco - 1), y + 6, z, tinta.flor);
                    // --- o lampião, pendurado debaixo do balanço ---
                    obra.pendurar(lado * (balanco - 1), y + 4, z, tinta.lampiao);
                }

                // --- o telhado, com beiral de um bloco sobre a rua ---
                for (int x = balanco - 1; x <= raio; x++) {
                    int rx = lado * x;
                    if (!ArenaComum.noDisco(rx, z, raio - 1)) {
                        continue;
                    }
                    obra.por(rx, y + 9, z,
                            x == balanco - 1 ? tinta.viga : tinta.madeira);
                }
            }
        }
    }

    /**
     * O que faz a rua parecer habitada: árvores, postes e bandeirinhas.
     *
     * Tudo nas VIELAS, e por um motivo prático antes do estético: é o único
     * lugar com céu aberto por cima, e é o único lugar onde nada disso fica
     * na frente do arco nem dos letreiros do fundo da rua.
     */
    private static void aRuaViva(Obra obra, PlantaDeArena planta, Paleta tinta) {
        int y = planta.saguaoY();
        int raio = planta.saguaoRaio();
        int zArco = zDoArcoDeVolta(planta);
        int avenida = meiaAvenida(raio);
        int calcada = xDaCalcada(raio);
        int vez = 0;

        for (int z = -raio; z <= raio; z++) {
            if (!noMeioDaViela(z, zArco) || !ArenaComum.noDisco(0, z, raio - 2)) {
                continue;
            }
            // --- as bandeirinhas atravessadas no alto da rua ---
            for (int x = -avenida; x <= avenida; x++) {
                obra.por(x, y + 8, z, Math.floorMod(x, 2) == 0
                        ? tinta.bandeira : tinta.bandeiraDois);
            }
            // --- árvore de um lado, poste do outro, alternando rua abaixo ---
            boolean arvore = Math.floorMod(vez, 2) == 0;
            for (int lado = -1; lado <= 1; lado += 2) {
                int px = lado * calcada;
                if (!ArenaComum.noDisco(px, z, raio - 1)) {
                    continue;
                }
                if (arvore) {
                    arvoreDaCalcada(obra, tinta, px, y, z, raio);
                } else {
                    posteDaCalcada(obra, tinta, px, y, z);
                }
            }
            vez++;
        }
    }

    private static void arvoreDaCalcada(Obra obra, Paleta tinta, int px, int y,
                                        int z, int raio) {
        for (int alto = 1; alto <= 4; alto++) {
            obra.por(px, y + alto, z, tinta.tronco);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int alto = 5; alto <= 6; alto++) {
                    if (Math.abs(dx) + Math.abs(dz) == 2 && alto == 6) {
                        continue;                       // a copa arredonda
                    }
                    int cx = px + dx;
                    if (!ArenaComum.noDisco(cx, z + dz, raio - 1)) {
                        continue;
                    }
                    obra.por(cx, y + alto, z + dz, tinta.folhagem);
                }
            }
        }
        obra.por(px, y + 7, z, tinta.folhagem);
    }

    private static void posteDaCalcada(Obra obra, Paleta tinta, int px, int y,
                                       int z) {
        for (int alto = 1; alto <= 3; alto++) {
            obra.por(px, y + alto, z, tinta.mastro);
        }
        obra.por(px, y + 4, z, tinta.luz);
    }

    /**
     * O arco de volta ao lobby, no fundo da rua.
     *
     * Sem ele o jogador que entra por curiosidade fica preso: não há chão por
     * onde sair e a única saída seria um comando que ele não conhece. O vão é
     * AR com cortina de partícula — bloco de portal de verdade levaria ao
     * Nether.
     *
     * <p>Ele é o ponto de fuga da rua, e é por isso que ganhou frontão: a
     * avenida inteira aponta para cá, e o que está no fim de uma rua tem de
     * valer o olhar. O vão continua com a mesma largura de antes porque é
     * {@link #dentroDaVolta} que decide quem volta ao lobby, e alargá-lo
     * mandaria para o lobby quem só passou perto.
     */
    private static void arcoDeVolta(Obra obra, PlantaDeArena planta,
                                    Paleta tinta) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);
        int altura = alturaDoArcoDeVolta();

        for (int x = -2; x <= 2; x++) {
            boolean montante = Math.abs(x) == 2;
            for (int alto = 1; alto <= altura; alto++) {
                if (montante || alto == altura) {
                    obra.por(x, y + alto, z,
                            alto == altura ? tinta.metal : tinta.viga);
                } else {
                    obra.ar(x, y + alto, z);
                }
            }
        }

        // O frontão: três fiadas que estreitam, vidradas no meio.
        for (int fiada = 0; fiada <= 2; fiada++) {
            int meio = 2 - fiada;
            for (int x = -meio; x <= meio; x++) {
                Material qual = Math.abs(x) == meio ? tinta.metal : tinta.nucleo;
                obra.por(x, y + altura + 1 + fiada, z, qual);
            }
        }
        obra.por(-2, y + altura + 2, z, tinta.luz);
        obra.por(2, y + altura + 2, z, tinta.luz);
        obra.por(0, y + altura + 4, z, tinta.luzFria);
    }

    /** O muro das duas placas, nos dois lados do arco de volta. */
    private static void fachadaDasPlacas(Obra obra, PlantaDeArena planta,
                                         Paleta tinta) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);

        for (int x = -LARGURA_DA_FACHADA; x <= LARGURA_DA_FACHADA; x++) {
            if (Math.abs(x) <= 2) {
                continue;                                // o vão do arco
            }
            for (int alto = 1; alto <= alturaDoArcoDeVolta(); alto++) {
                Material qual = alto == alturaDoArcoDeVolta() ? tinta.metal
                        : Math.abs(x) == LARGURA_DA_FACHADA ? tinta.viga
                                : tinta.reboco;
                obra.por(x, y + alto, z, qual);
            }
            obra.por(x, y + alturaDoArcoDeVolta() + 1, z, tinta.escuro);
        }
        // Duas floreiras ladeando o arco, na altura do peito de quem chega.
        obra.por(-3, y + alturaDoArcoDeVolta() + 2, z, tinta.flor);
        obra.por(3, y + alturaDoArcoDeVolta() + 2, z, tinta.flor);
    }

    /**
     * A paleta da rua, lida uma vez.
     *
     * Num objeto e não em quinze variáveis soltas porque são quinze materiais
     * passados por cinco métodos: a assinatura de {@code asCasas} com todos
     * eles seria ilegível, e trocar `reboco` por `viga` numa chamada de
     * quinze argumentos do mesmo tipo é um erro que o compilador não pega.
     *
     * <p>Todas as chaves têm padrão. Nenhum yml precisa mudar para a rua
     * existir, e o yml que quiser a rua na cor dele escreve só as que quer.
     */
    private static final class Paleta {
        private final Material estrutura;
        private final Material escuro;
        private final Material borda;
        private final Material metal;
        private final Material vidro;
        private final Material luz;
        private final Material luzFria;
        private final Material nucleo;
        private final Material grama;
        private final Material caminho;
        private final Material alvenaria;
        private final Material reboco;
        private final Material viga;
        private final Material madeira;
        private final Material folhagem;
        private final Material flor;
        private final Material lampiao;
        private final Material mastro;
        private final Material tronco;
        private final Material bandeira;
        private final Material bandeiraDois;

        private Paleta(PlantaDeArena planta) {
            estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
            escuro = planta.cor("escuro", Material.POLISHED_DEEPSLATE);
            borda = planta.cor("borda", Material.DEEPSLATE_TILES);
            metal = planta.cor("metal", Material.IRON_BLOCK);
            vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
            luz = planta.cor("luz", Material.SEA_LANTERN);
            luzFria = planta.cor("luz-fria", Material.END_ROD);
            nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);
            grama = planta.cor("grama", Material.GRASS_BLOCK);
            caminho = planta.cor("caminho", Material.DIRT_PATH);
            alvenaria = planta.cor("alvenaria", Material.COBBLESTONE);
            reboco = planta.cor("reboco", Material.BIRCH_PLANKS);
            viga = planta.cor("viga", Material.DARK_OAK_LOG);
            madeira = planta.cor("madeira", Material.SPRUCE_PLANKS);
            folhagem = planta.cor("folhagem", Material.OAK_LEAVES);
            flor = planta.cor("flor", Material.FLOWERING_AZALEA_LEAVES);
            lampiao = planta.cor("lampiao", Material.LANTERN);
            mastro = planta.cor("mastro", Material.OAK_FENCE);
            tronco = planta.cor("tronco", Material.OAK_LOG);
            bandeira = planta.cor("bandeira", Material.PINK_WOOL);
            bandeiraDois = planta.cor("bandeira-dois",
                    Material.LIGHT_BLUE_WOOL);
        }
    }

    // ------------------------------------------------------------------
    //  AS PAREDES QUE EXPLICAM O JOGO
    //
    //  As instruções viviam no BATE-PAPO, ditas uma a uma quando o jogador
    //  entrava na arena. Era a coisa errada por dois motivos.
    //
    //  O bate-papo empurra texto para cima: chegar com a rodada anterior
    //  acabando significava ler as regras entremeadas de "fulano caiu",
    //  "fulano ganhou em 0:10", e metade delas já tinha rolado para fora da
    //  tela antes de ser lida. E uma parede de texto cinza, de seis linhas,
    //  é a última coisa que alguém lê quando acabou de chegar num lugar
    //  novo — ele está olhando em volta, não lendo o canto da tela.
    //
    //  Agora cada parágrafo é uma PLACA na fachada de uma casa, na altura do
    //  olho de quem anda pela avenida. Ler passa a ser andar: quem quer
    //  saber caminha até o fim da rua, quem não quer entra pela placa e
    //  joga. É o quadro emoldurado na parede da vila do mapa de referência,
    //  e é o que uma rua serve para fazer.
    // ------------------------------------------------------------------

    /** Quanto o painel se afasta da fachada, para não entrar na parede. */
    private static final double RECUO_DA_PAREDE = 1.35;

    /** Onde o pé do painel fica: altura do olho de quem passa. */
    private static final double PE_DO_PAINEL = 1.5;

    private static final float ESCALA_DA_PAREDE = 0.85f;
    private static final int LARGURA_DA_PAREDE = 200;
    private static final int LETRAS_NA_PAREDE = 30;

    /**
     * As vagas de placa na rua: o meio de cada casa, dos dois lados.
     *
     * Contadas DO ARCO para trás porque é para o arco que a avenida aponta e
     * é para lá que quem pousa está olhando: a primeira placa de cada lado
     * fica no campo de visão de quem acabou de chegar, sem precisar virar.
     *
     * <p>Estática e sem mundo para o teste medir sem servidor.
     */
    static List<int[]> vagasDeParede(int raio) {
        List<int[]> vagas = new ArrayList<>();
        int zArco = zDoArcoDeVolta(raio);
        for (int z = zArco + 2; z <= raio - 2; z += 6) {
            if (!ArenaComum.noDisco(0, z, raio - 2)) {
                continue;
            }
            vagas.add(new int[]{-1, z});
            vagas.add(new int[]{1, z});
        }
        return vagas;
    }

    /**
     * Reparte os parágrafos pelas vagas.
     *
     * Sobrando parágrafo, os que não couberam se juntam ao último em vez de
     * sumirem: regra que o saguão não mostra é regra que o jogador descobre
     * perdendo, e cortar em silêncio é o pior dos dois mundos.
     */
    static List<String> repartir(List<String> paragrafos, int vagas) {
        List<String> saida = new ArrayList<>();
        if (paragrafos.isEmpty() || vagas <= 0) {
            return saida;
        }
        if (paragrafos.size() <= vagas) {
            saida.addAll(paragrafos);
            return saida;
        }
        for (int k = 0; k < vagas - 1; k++) {
            saida.add(paragrafos.get(k));
        }
        saida.add(String.join("\n\n",
                paragrafos.subList(vagas - 1, paragrafos.size())));
        return saida;
    }

    /** Escreve os parágrafos de instrução nas fachadas das casas. */
    public static void escreverAsParedes(BigaCore plugin, World mundo,
                                         PlantaDeArena planta, String etiqueta) {
        JogoRegistrado registrado = plugin.jogos().get(planta.etiqueta());
        if (registrado == null) {
            return;
        }
        List<String> paragrafos = registrado.jogo().instrucoes();
        if (paragrafos == null || paragrafos.isEmpty()) {
            return;
        }
        int raio = planta.saguaoRaio();
        List<int[]> vagas = vagasDeParede(raio);
        List<String> repartidos = repartir(paragrafos, vagas.size());

        int y = planta.saguaoY();
        double x = xDaFachada(raio) - RECUO_DA_PAREDE;
        String cor = planta.corDoTexto();

        for (int k = 0; k < repartidos.size() && k < vagas.size(); k++) {
            int[] vaga = vagas.get(k);
            String escrito = comCabecalho(repartidos.get(k), cor, k + 1,
                    repartidos.size());
            double alto = alturaDoPainel(
                    linhasDaParede(escrito), ESCALA_DA_PAREDE);
            ArenaComum.painelDeParede(mundo,
                    new Location(mundo, vaga[0] * x, y + PE_DO_PAINEL + alto / 2,
                            vaga[1] + 0.5),
                    plugin.mini().deserialize(escrito),
                    ESCALA_DA_PAREDE, fundoDaParede(planta.fundoDoTexto()),
                    LARGURA_DA_PAREDE, etiqueta);
        }
    }

    /**
     * O cabeçalho de cada placa: a régua na cor do jogo e a conta.
     *
     * A conta — "2 de 4" — existe para quem está lendo saber se falta placa.
     * Sem ela, uma placa escondida atrás de uma árvore é uma regra que o
     * jogador nunca saberá que existiu.
     */
    static String comCabecalho(String texto, String cor, int qual, int quantas) {
        return "<color:" + cor + ">\u2501\u2501 </color>"
                + "<color:" + cor + "><bold>COMO SE JOGA</bold></color>"
                + "<color:" + SOMBRA + "> " + qual + "/" + quantas + "</color>"
                + "<color:" + cor + "> \u2501\u2501</color>\n" + texto;
    }

    /** O fundo da placa de parede: mais firme que o do letreiro do fundo. */
    static int fundoDaParede(int escolhido) {
        return escolhido > 0 ? escolhido : 170;
    }

    /** Quantas linhas o texto ocupa na largura da placa de parede. */
    static int linhasDaParede(String texto) {
        int total = 0;
        for (String pedaco : texto.split("\n", -1)) {
            String limpo = semMarcacao(pedaco);
            total += Math.max(1,
                    (limpo.length() + LETRAS_NA_PAREDE - 1) / LETRAS_NA_PAREDE);
        }
        return Math.max(1, total);
    }

    public static void escreverPlaca(World mundo, PlantaDeArena planta, int[] onde,
                                     List<Component> linhas) {
        ArenaComum.escreverPlaca(mundo, onde, planta.materialDaPlaca(),
                FRENTE_DA_PLACA, linhas);
    }

    /**
     * O nome, a frase e as regras, empilhados acima da fachada.
     *
     * Yaw 0 porque quem lê está no meio do saguão olhando para o norte (yaw
     * 180), e um letreiro é legível por quem olha de yaw mais 180.
     *
     * <p>A pilha é montada DE BAIXO PARA CIMA, e isso é o conserto: o piso
     * do saguão é o único limite duro — por cima é céu aberto. Partindo do
     * piso e somando a altura real de cada painel, nenhum deles afunda no
     * chão e nenhum encosta no de cima, com uma regra ou com doze.
     */
    public static void escreverTextos(BigaCore plugin, World mundo,
                                      PlantaDeArena planta, String etiqueta) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);
        String cor = planta.corDoTexto();
        double escalaPlaca = planta.escalaPlaca();
        int fundo = planta.fundoDoTexto();

        List<String> corpo = corpoDasRegras(planta, cor);
        double altura = ALTURA_LIVRE;

        if (!corpo.isEmpty()) {
            double alto = alturaDoPainel(linhasDoTexto(corpo), escalaPlaca);
            ArenaComum.painel(mundo,
                    new Location(mundo, 0.5, y + altura + alto / 2, z + 0.8),
                    plugin.mini().deserialize(String.join("\n", corpo)),
                    (float) escalaPlaca, fundoDasRegras(fundo), etiqueta);
            altura += alto + FOLGA;
        }

        String frase = planta.subtitulo();
        if (frase != null && !frase.isBlank()) {
            String escrita = "<color:" + SOMBRA + "><italic>"
                    + HubBuilder.escapar(frase) + "</italic></color>";
            double alto = alturaDoPainel(
                    linhasDoTexto(List.of(escrita)), escalaPlaca);
            ArenaComum.painel(mundo,
                    new Location(mundo, 0.5, y + altura + alto / 2, z + 0.8),
                    plugin.mini().deserialize(escrita),
                    (float) escalaPlaca, fundo, etiqueta);
            altura += alto + FOLGA;
        }

        double altoDoNome = alturaDoPainel(1, planta.escalaTitulo());
        ArenaComum.painel(mundo,
                new Location(mundo, 0.5, y + altura + altoDoNome / 2, z + 0.8),
                plugin.mini().deserialize("<color:" + cor + "><bold>"
                        + HubBuilder.escapar(planta.titulo()) + "</bold></color>"),
                (float) planta.escalaTitulo(), fundo, etiqueta);

        // E as placas das paredes, aqui dentro e não no construir: assim o
        // /biga <jogo> textos — que é o comando que se roda quando algo sai
        // torto — refaz os dois, e não um só.
        escreverAsParedes(plugin, mundo, planta, etiqueta);
    }

    /**
     * O bloco das regras: cabeçalho, uma regra por linha, rodapé.
     *
     * Tudo num painel só e não um por linha — painéis separados voltariam
     * a precisar de alturas escolhidas no olho. A cor do jogo entra no
     * cabeçalho e no marcador de cada regra, e o texto fica num cinza
     * claro em vez do cinza do Minecraft: o cinza puro contra o muro de
     * pedra do saguão é o que o jogador chamou de "sumindo no bloco".
     */
    static List<String> corpoDasRegras(PlantaDeArena planta, String cor) {
        List<String> regras = planta.regrasEscritas();
        if (regras.isEmpty()) {
            return List.of();
        }
        List<String> corpo = new ArrayList<>();
        corpo.add("<color:" + cor + ">\u2501\u2501\u2501 COMO SE JOGA "
                + "\u2501\u2501\u2501</color>");
        for (String linha : regras) {
            corpo.add("<color:" + cor + ">\u203a</color> <color:" + TINTA + ">"
                    + HubBuilder.escapar(linha) + "</color>");
        }
        corpo.add(REGUA + "\u2501\u2501\u2501\u2501\u2501\u2501\u2501"
                + "\u2501\u2501\u2501\u2501\u2501</color>");
        corpo.add("<color:" + SOMBRA + ">" + rodape(planta) + "</color>");
        return List.copyOf(corpo);
    }

    /** Quantos jogam e quanto dura: o que se quer saber antes de entrar. */
    static String rodape(PlantaDeArena planta) {
        return planta.minimo() + " a " + planta.maximo() + " jogadores \u00b7 at\u00e9 "
                + ArenaComum.tempo(planta.tempoLimite() * 20L);
    }

    /**
     * O fundo do bloco de regras.
     *
     * Um pouco de preto atrás do texto denso mesmo quando o jogo pediu
     * fundo transparente: o nome e a frase são grandes e se leem contra
     * qualquer coisa, mas doze linhas pequenas em cima de pedra clara,
     * não. Quem já escolheu um fundo no yml fica com o dele.
     */
    static int fundoDasRegras(int escolhido) {
        return escolhido > 0 ? escolhido : 130;
    }

    /** Altura, em blocos, de um painel com tantas linhas naquela escala. */
    static double alturaDoPainel(int linhas, double escala) {
        return Math.max(1, linhas) * ALTURA_DA_LINHA * escala;
    }

    /** Quantas linhas o texto ocupa DEPOIS de o letreiro quebrar. */
    static int linhasDoTexto(List<String> linhas) {
        int total = 0;
        for (String linha : linhas) {
            int letras = semMarcacao(linha).length();
            total += Math.max(1, (letras + LETRAS_POR_LINHA - 1)
                    / LETRAS_POR_LINHA);
        }
        return total;
    }

    /** Tira as etiquetas do MiniMessage: a conta mede só o que se lê. */
    static String semMarcacao(String texto) {
        return texto == null ? "" : texto.replaceAll("<[^>]*>", "");
    }

    /** Conta blocos postos, para o construtor do jogo somar no resultado. */
    private static final class Obra {
        private final World mundo;
        private int contador;

        private Obra(World mundo) {
            this.mundo = mundo;
        }

        private void por(int x, int y, int z, Material material) {
            mundo.getBlockAt(x, y, z).setType(material, false);
            contador++;
        }

        /**
         * Põe o bloco PENDURADO, quando ele souber ficar pendurado.
         *
         * O lampião é o caso: posto do jeito comum ele fica em pé no chão, e
         * o que a rua precisa é dele preso embaixo do balanço. Quem não for
         * pendurável passa reto — a chave da paleta aceita qualquer material.
         */
        private void pendurar(int x, int y, int z, Material material) {
            Block bloco = mundo.getBlockAt(x, y, z);
            bloco.setType(material, false);
            if (bloco.getBlockData()
                    instanceof org.bukkit.block.data.type.Lantern pendente) {
                pendente.setHanging(true);
                bloco.setBlockData(pendente, false);
            }
            contador++;
        }

        private void ar(int x, int y, int z) {
            Block bloco = mundo.getBlockAt(x, y, z);
            if (!bloco.getType().isAir()) {
                bloco.setType(Material.AIR, false);
                contador++;
            }
        }
    }
}
