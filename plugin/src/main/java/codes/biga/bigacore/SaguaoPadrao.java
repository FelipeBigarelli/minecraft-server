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
    private static final double ALTURA_LIVRE = 3.2;

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

    /**
     * Levanta o saguão inteiro: caixa, arco, fachada, placas e letreiros.
     *
     * @return quantos blocos foram postos, para o construtor do jogo somar.
     */
    public static int construir(BigaCore plugin, World mundo, PlantaDeArena planta,
                                String etiquetaDoPainel) {
        Obra obra = new Obra(mundo);
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

        // A caixa: chão de vidro, parapeito e remate.
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
                obra.por(x, y, z, chao);

                if (!rim) {
                    continue;
                }
                for (int alto = 1; alto <= planta.saguaoParapeito(); alto++) {
                    obra.por(x, y + alto, z,
                            Math.floorMod(x + z, 5) == 0 ? estrutura : vidro);
                }
                obra.por(x, y + planta.saguaoParapeito() + 1, z, borda);
            }
        }

        // Quatro agulhas de luz nos cardeais, para o saguão ter silhueta vista
        // de dentro da arena.
        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int alto = planta.saguaoParapeito() + 2;
                 alto <= planta.saguaoParapeito() + 4; alto++) {
                obra.por(eixo[0] * raio, y + alto, eixo[1] * raio, rod);
            }
        }

        arcoDeVolta(obra, planta, estrutura, metal, nucleo, luz);
        fachadaDasPlacas(obra, planta, estrutura, metal, escuro);

        escreverPlaca(mundo, planta, pontoDaPlacaEntrar(planta), List.of());
        escreverPlaca(mundo, planta, pontoDaPlacaSair(planta), List.of());
        escreverTextos(plugin, mundo, planta, etiquetaDoPainel);

        return obra.contador + 2;
    }

    /**
     * O arco de volta ao lobby, em pé no chão do saguão.
     *
     * Sem ele o jogador que entra por curiosidade fica preso: não há chão por
     * onde sair e a única saída seria um comando que ele não conhece. O vão é
     * AR com cortina de partícula — bloco de portal de verdade levaria ao
     * Nether.
     */
    private static void arcoDeVolta(Obra obra, PlantaDeArena planta, Material estrutura,
                                    Material metal, Material nucleo, Material luz) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);
        int altura = alturaDoArcoDeVolta();

        for (int x = -2; x <= 2; x++) {
            boolean montante = Math.abs(x) == 2;
            for (int alto = 1; alto <= altura; alto++) {
                if (montante || alto == altura) {
                    obra.por(x, y + alto, z, alto == altura ? metal : estrutura);
                } else {
                    obra.ar(x, y + alto, z);
                }
            }
            obra.por(x, y + altura + 1, z, Math.abs(x) <= 1 ? nucleo : metal);
        }
        obra.por(-2, y + altura + 2, z, luz);
        obra.por(2, y + altura + 2, z, luz);
    }

    /** O muro das duas placas, nos dois lados do arco de volta. */
    private static void fachadaDasPlacas(Obra obra, PlantaDeArena planta,
                                         Material estrutura, Material metal,
                                         Material escuro) {
        int y = planta.saguaoY();
        int z = zDoArcoDeVolta(planta);

        for (int x = -LARGURA_DA_FACHADA; x <= LARGURA_DA_FACHADA; x++) {
            if (Math.abs(x) <= 2) {
                continue;                                // o vão do arco
            }
            for (int alto = 1; alto <= alturaDoArcoDeVolta(); alto++) {
                obra.por(x, y + alto, z,
                        alto == alturaDoArcoDeVolta() ? metal : estrutura);
            }
            obra.por(x, y + alturaDoArcoDeVolta() + 1, z, escuro);
        }
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

        private void ar(int x, int y, int z) {
            Block bloco = mundo.getBlockAt(x, y, z);
            if (!bloco.getType().isAir()) {
                bloco.setType(Material.AIR, false);
                contador++;
            }
        }
    }
}
