package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Levanta o lobby dos minigames: o claustro de vinte portais.
 *
 * <h2>A planta, e por que ela é quadrada</h2>
 *
 * Um pátio de 87 por 87 no meio, cercado por quatro galerias de dez de
 * profundidade e vinte de altura. Cada galeria tem cinco nichos; cinco vezes
 * quatro são os vinte jogos. A proporção é de praça italiana — vinte de
 * parede em volta de oitenta e sete de chão —, alta o bastante para impor e
 * baixa o bastante para não virar poço.
 *
 * O hub é redondo porque tem três caminhos. Aqui são vinte, e vinte nichos
 * num círculo cairiam quase todos na diagonal. Parede diagonal no Minecraft é
 * escada: um emblema de onze por onze carimbado numa dessas vira um borrão de
 * degraus. Reto, cada desenho sai do jeito que foi desenhado.
 *
 * <h2>O arco é estreito e a câmara é larga</h2>
 *
 * Passa-se por um arco de sete e chega-se num salão de onze por dezenove.
 * Não é enfeite: o emblema tem onze de largura e, se a câmara tivesse a
 * largura do arco, as duas colunas das pontas de todos os vinte desenhos
 * ficariam escondidas atrás do montante. O aperto antes do salão é também o
 * que faz o emblema aparecer de uma vez, em vez de ir surgindo de longe.
 *
 * <h2>Vinte faixas no chão</h2>
 *
 * Do monumento até cada nicho vai uma faixa de três blocos na cor do jogo.
 * É o que faz o lobby funcionar sem mapa e sem placa: "siga a laranja" leva
 * ao Build Battle, e leva igual para quem não lê português. A cor aparece
 * três vezes — na faixa do chão, no painel sobre o arco e na moldura do
 * portal — então a pessoa associa antes de chegar perto o bastante para ler.
 *
 * <h2>A escada das torres não usa bloco de escada</h2>
 *
 * Ela sobe meio bloco por célula, alternando bloco inteiro e laje. Dá
 * quarenta células para subir vinte — uma volta exata na parede de dentro da
 * torre — e se anda sem pular. Bloco de escada subiria em vinte células, mas
 * a orientação de {@code Stairs} depende de uma convenção que só se confere
 * em jogo, e uma escada virada ao contrário é uma parede de vinte blocos.
 * Meio degrau não tem para que lado virar.
 *
 * <h2>A obra NÃO tem desfazer de verdade</h2>
 *
 * O lobby nasce num mundo vazio, e por isso desfazer é só apagar. O construtor
 * se recusa a levantar em lugar que já tenha bloco: sem essa trava, rodar o
 * comando no survival apagaria construção de jogador sem copiar nada antes.
 */
public final class LobbyBuilder {

    /** Marca das entidades de texto do lobby, para o desfazer achá-las. */
    public static final String TAG_PAINEL = "bigacore_lobby_painel";

    public record Resultado(boolean ok, String mensagem, int blocos) {}

    private final BigaCore plugin;
    private int contador;

    public LobbyBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * @param aplicar false confere o terreno e devolve o tamanho da obra sem
     *                colocar um bloco. É o {@code /biga lobby info}.
     */
    public Resultado construir(World mundo, boolean aplicar) {
        MinigamesCatalog planta = plugin.minigamesCatalog();
        List<MinigamesCatalog.Portal> portais = planta.portais();

        if (portais.isEmpty()) {
            return new Resultado(false,
                    "O minigames.yml não tem nenhum portal em 'portais'.", 0);
        }
        if (!MinigamesCatalog.vagasSaoUnicas(portais)) {
            return new Resultado(false, "Dois portais do minigames.yml estão no mesmo "
                    + "lado e no mesmo vão — um sairia por cima do outro.", 0);
        }

        List<String> tortos = esculturasTortas(planta, portais);
        if (!tortos.isEmpty()) {
            return new Resultado(false, "Desenho que não cabe ou com linhas de tamanhos "
                    + "diferentes: " + String.join(", ", tortos) + ".", 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            List<String> livres = MinigamesCatalog.vagasLivres(portais, planta.vaos());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Obra: " + (planta.alcanceDaObra() * 2) + " de lado, pátio de "
                    + (planta.patio() * 2 + 1) + ". Portais: " + portais.size()
                    + (livres.isEmpty() ? " (todos os nichos ocupados)"
                            : ", nichos vazios: " + String.join(" ", livres)) + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área do "
                    + "lobby. Construa num mundo vazio "
                    + "(/mv create minigames normal -g BigaCore) ou rode "
                    + "/biga lobby desfazer CONFIRMAR antes.", 0);
        }

        int piso = planta.pisoY();
        contador = 0;
        limparPaineis(mundo);

        estrado(mundo, planta, piso);
        galeria(mundo, planta, piso);
        for (MinigamesCatalog.Portal portal : portais) {
            nicho(mundo, planta, portal, piso);
            fachadaDoNicho(mundo, planta, portal, piso);
        }
        pisoDoPatio(mundo, planta, portais, piso);
        passarelaSuperior(mundo, planta, portais, piso);
        torres(mundo, planta, piso);
        monumento(mundo, planta, piso);
        anelSuspenso(mundo, planta, piso);

        escreverTextos(mundo, planta, portais, piso);
        Location pouso = pontoDeSpawn(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Lobby levantado: " + contador + " blocos, "
                + portais.size() + " portais de jogo e a volta para o hub.", contador);
    }

    public Resultado desfazer(World mundo) {
        MinigamesCatalog planta = plugin.minigamesCatalog();
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        contador = 0;

        limparPaineis(mundo);
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = piso - 10; y <= piso + planta.alturaDaObra(); y++) {
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return new Resultado(true, "Lobby apagado: " + contador + " blocos.", contador);
    }

    /** Desenhos que não cabem na câmara. Conferido ANTES de mexer no mundo. */
    private List<String> esculturasTortas(MinigamesCatalog planta,
                                          List<MinigamesCatalog.Portal> portais) {
        List<String> ruins = new ArrayList<>();
        int altura = alturaDaEscultura(planta);
        for (MinigamesCatalog.Portal portal : portais) {
            if (portal.escultura().isBlank()) {
                continue;
            }
            List<String> desenho = planta.escultura(portal.escultura());
            if (desenho.isEmpty()) {
                ruins.add(portal.id() + " (desenho '" + portal.escultura() + "' não existe)");
            } else if (!MinigamesCatalog.esculturaCabe(desenho, planta.camaraMeiaLargura(),
                    altura)) {
                ruins.add(portal.id() + " (" + portal.escultura() + ")");
            }
        }
        return ruins;
    }

    // ------------------------------------------------------------------
    //  O estrado
    // ------------------------------------------------------------------

    /**
     * Quanto cada fiada de baixo encolhe. Dá o chanfro que faz a ilha ter
     * fundo em vez de acabar numa borda de um bloco pairando no void.
     */
    static final int[] DEGRAUS = {0, 1, 3, 6, 10, 15, 21};

    /**
     * O estrado quadrado inteiro, com duas fiadas cheias e o resto em casca.
     *
     * As duas cheias existem porque num mundo vazio o jogador VOA POR BAIXO:
     * casca sozinha deixaria ver o céu pelo meio da praça de dentro para
     * fora. Da terceira para baixo só a borda, que é a única parte que
     * alguém vê de fora.
     */
    private void estrado(World mundo, MinigamesCatalog planta, int piso) {
        int alcance = planta.galeriaFim() + planta.bordaExterna();
        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);

        for (int k = 0; k < DEGRAUS.length; k++) {
            int meia = alcance - DEGRAUS[k];
            if (meia < 2) {
                break;
            }
            int y = piso - k;
            boolean cheia = k <= 1;

            for (int x = -meia; x <= meia; x++) {
                for (int z = -meia; z <= meia; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (!cheia && d < meia - 1) {
                        continue;
                    }
                    Material material;
                    if (k == 0) {
                        material = d > planta.galeriaFim() ? borda : chao;
                    } else if (k == 1) {
                        material = estrutura;
                    } else {
                        material = escuro;
                    }
                    por(mundo, x, y, z, material);
                }
            }
        }

        // O parapeito do terraço de fora, para ninguém andar direto no void.
        int fim = alcance;
        Material parapeito = planta.cor("parapeito", Material.QUARTZ_SLAB);
        for (int i = -fim; i <= fim; i++) {
            for (int[] p : new int[][]{{i, -fim}, {i, fim}, {-fim, i}, {fim, i}}) {
                por(mundo, p[0], piso + 1, p[1], estrutura);
                laje(mundo, p[0], piso + 2, p[1], parapeito);
            }
        }
        // Lanternas de canto, de vinte em vinte, marcando a borda de longe.
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        for (int i = -fim; i <= fim; i += 20) {
            for (int[] p : new int[][]{{i, -fim}, {i, fim}, {-fim, i}, {fim, i}}) {
                por(mundo, p[0], piso + 2, p[1], luz);
                por(mundo, p[0], piso + 3, p[1], rod);
            }
        }
    }

    // ------------------------------------------------------------------
    //  O piso do pátio
    // ------------------------------------------------------------------

    /**
     * O desenho do chão: anéis, diagonais e as vinte faixas de cor.
     *
     * Roda DEPOIS da galeria porque as faixas têm de terminar encostadas na
     * parede de dentro, e a galeria é quem define onde essa parede está.
     */
    private void pisoDoPatio(World mundo, MinigamesCatalog planta,
                             List<MinigamesCatalog.Portal> portais, int piso) {
        int patio = planta.patio();
        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material linha = planta.cor("piso-linha", Material.LIGHT_BLUE_CONCRETE);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int x = -patio; x <= patio; x++) {
            for (int z = -patio; z <= patio; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                Material material = chao;
                if (Math.abs(x) == Math.abs(z)) {
                    material = escuro;          // as quatro diagonais
                } else if (d % 8 == 0) {
                    material = linha;           // anéis de oito em oito
                } else if (d == planta.monumentoRaio() + 3) {
                    material = borda;           // a calçada em volta do monumento
                }
                por(mundo, x, piso, z, material);
            }
        }

        // As faixas: do fim da calçada do monumento até a parede da galeria.
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        int comeco = planta.monumentoRaio() + 4;
        for (MinigamesCatalog.Portal portal : portais) {
            MinigamesCatalog.Lado lado = portal.lado();
            int centro = planta.centroDoVao(portal.vao());
            for (int fora = comeco; fora <= patio; fora++) {
                for (int ao = centro - 1; ao <= centro + 1; ao++) {
                    boolean meio = ao == centro;
                    Material material = meio && fora % 6 == 0 ? luz : portal.destaque();
                    por(mundo, lado.x(ao, fora), piso, lado.z(ao, fora), material);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  A galeria
    // ------------------------------------------------------------------

    /**
     * A galeria maciça em volta do pátio, com a fachada já desenhada.
     *
     * Enche tudo e abre depois, em vez de montar parede por parede. Com vinte
     * nichos, quatro cantos e os vãos de um bloco entre nicho e nicho, montar
     * só as paredes significa acertar dezenas de emendas — e uma emenda errada
     * é um buraco de onde se vê o void, no lugar mais visível da obra. Encher
     * e furar não tem emenda.
     */
    private void galeria(World mundo, MinigamesCatalog planta, int piso) {
        int dentro = planta.patio() + 1;
        int fora = planta.galeriaFim();
        int altura = planta.galeriaAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);

        List<Integer> vaos = planta.vaos();
        int meiaNicho = planta.nichoMeiaLargura();

        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < dentro) {
                    continue;
                }
                boolean naFace = d == dentro;
                boolean naCosta = d == fora;

                // Em que ponto da galeria estamos, para as pilastras caírem
                // sempre na junta entre dois nichos.
                int aoLongo = Math.abs(x) > Math.abs(z) ? z : x;
                boolean pilastra = false;
                for (int centro : vaos) {
                    if (aoLongo == centro + meiaNicho || aoLongo == centro - meiaNicho) {
                        pilastra = true;
                        break;
                    }
                }

                for (int y = 1; y <= altura; y++) {
                    Material material;
                    if (y == altura) {
                        material = chao;                    // o piso da passarela
                    } else if (y == altura - 1) {
                        material = borda;                   // a cimalha
                    } else if (y == altura - 2 && (naFace || naCosta)) {
                        material = metal;                   // o friso de metal
                    } else if (y <= 2 && (naFace || naCosta)) {
                        material = borda;                   // o embasamento
                    } else if ((naFace || naCosta) && pilastra) {
                        material = escuro;                  // a pilastra
                    } else if (naCosta && y % 6 == 3) {
                        material = vidro;                   // janelas na costa
                    } else if (naFace || naCosta) {
                        material = estrutura;
                    } else {
                        material = estrutura;               // o miolo, que ninguém vê
                    }
                    por(mundo, x, piso + y, z, material);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  O nicho de um jogo
    // ------------------------------------------------------------------

    /** Altura do vão do portal dentro da câmara. */
    private static int alturaDaPorta(MinigamesCatalog planta) {
        return 7;
    }

    /**
     * Em que fiada o emblema começa: logo acima da verga da porta.
     *
     * A porta ocupa 1 a 7 e a verga dela o 8, então o emblema nasce no 9. O
     * teto da câmara é o 19, o que dá onze fiadas — exatamente a altura dos
     * vinte desenhos. Não é coincidência: o {@link #esculturasTortas} confere
     * essa conta antes de a obra começar, e é por isso que ela está escrita
     * aqui uma vez só.
     */
    private static int baseDaEscultura(MinigamesCatalog planta) {
        return alturaDaPorta(planta) + 2;
    }

    /** Quantas fiadas o emblema tem para ocupar. */
    private static int alturaDaEscultura(MinigamesCatalog planta) {
        return planta.galeriaAltura() - 1 - baseDaEscultura(planta) + 1;
    }

    /**
     * Quanto a verga do arco encolhe em cada uma das fiadas de cima.
     *
     * Três degraus, de dentro para fora: o arco sai de sete para cinco e para
     * três antes de fechar. Um vão retangular de sete por onze é um buraco;
     * com a verga ele vira arco, e é só isso que separa "galeria" de
     * "parede com furos".
     */
    static final int[] VERGA = {2, 1, 0};

    /** A meia largura do arco numa altura, contada do topo para baixo. */
    static int meiaLarguraDoArco(int fiadasAbaixoDoTopo, int vao) {
        if (fiadasAbaixoDoTopo < 0) {
            return -1;
        }
        if (fiadasAbaixoDoTopo >= VERGA.length) {
            return vao;
        }
        return Math.max(0, vao - VERGA[fiadasAbaixoDoTopo]);
    }

    /**
     * Abre o arco e a câmara de um jogo e carimba o emblema no fundo.
     *
     * Ordem importa: primeiro o ar, depois o acabamento. Carimbar antes de
     * abrir deixaria metade do desenho sendo apagada pelo próprio vazado.
     */
    private void nicho(World mundo, MinigamesCatalog planta,
                       MinigamesCatalog.Portal portal, int piso) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int vao = planta.vaoLargura();
        int arco = planta.vaoAltura();
        int camara = planta.camaraMeiaLargura();
        int dentro = planta.patio() + 1;
        int fundo = planta.galeriaFim();
        int teto = planta.galeriaAltura() - 1;

        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escura = planta.cor("estrutura-escura", Material.BLACK_CONCRETE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        // 1. o arco, nas três fiadas da parede de dentro
        for (int y = 1; y <= arco; y++) {
            int meia = meiaLarguraDoArco(arco - y, vao);
            for (int ao = -meia; ao <= meia; ao++) {
                for (int d = dentro; d <= dentro + 2; d++) {
                    ar(mundo, lado.x(centro + ao, d), piso + y, lado.z(centro + ao, d));
                }
            }
        }

        // 2. a câmara
        for (int y = 1; y <= teto; y++) {
            for (int ao = -camara; ao <= camara; ao++) {
                for (int d = dentro + 3; d <= fundo - 1; d++) {
                    ar(mundo, lado.x(centro + ao, d), piso + y, lado.z(centro + ao, d));
                }
            }
        }

        // 3. o chão da câmara, na cor do jogo, e o teto aceso
        for (int ao = -camara; ao <= camara; ao++) {
            for (int d = dentro + 3; d <= fundo - 1; d++) {
                boolean corredor = Math.abs(ao) <= 2 && d >= fundo - 3;
                por(mundo, lado.x(centro + ao, d), piso,
                        lado.z(centro + ao, d), corredor ? portal.destaque() : chao);
                por(mundo, lado.x(centro + ao, d), piso + teto + 1,
                        lado.z(centro + ao, d),
                        (ao + d) % 4 == 0 ? luz : chao);
            }
        }

        // 4. as paredes de lado da câmara, com um friso na cor
        for (int d = dentro + 3; d <= fundo - 1; d++) {
            for (int y = 1; y <= teto; y++) {
                Material material = y == 5 ? portal.destaque()
                        : y <= 1 ? borda : estrutura;
                for (int ao : new int[]{-camara - 1, camara + 1}) {
                    por(mundo, lado.x(centro + ao, d), piso + y,
                            lado.z(centro + ao, d), material);
                }
            }
        }

        // 5. o fundo: um painel claro, para o emblema não ficar recortado
        for (int ao = -camara; ao <= camara; ao++) {
            for (int y = 1; y <= teto; y++) {
                por(mundo, lado.x(centro + ao, fundo), piso + y,
                        lado.z(centro + ao, fundo), chao);
            }
        }

        // 6. a porta do portal, emoldurada no fundo
        int alturaPorta = alturaDaPorta(planta);
        for (int ao = -3; ao <= 3; ao++) {
            for (int y = 0; y <= alturaPorta + 1; y++) {
                boolean campo = Math.abs(ao) <= 2 && y >= 1 && y <= alturaPorta;
                Material material = campo ? portal.destaque()
                        : Math.abs(ao) == 3 && (y == 0 || y == alturaPorta + 1) ? luz
                        : portal.moldura();
                por(mundo, lado.x(centro + ao, fundo), piso + y,
                        lado.z(centro + ao, fundo), material);
            }
        }
        // As duas colunas de luz que flanqueiam a porta, já dentro da câmara.
        for (int y = 1; y <= alturaPorta; y++) {
            for (int ao : new int[]{-4, 4}) {
                por(mundo, lado.x(centro + ao, fundo - 1), piso + y,
                        lado.z(centro + ao, fundo - 1), rod);
            }
        }

        // 7. o emblema, acima da porta
        esculturaDoNicho(mundo, planta, portal, piso, baseDaEscultura(planta), escura);
    }

    /**
     * Carimba o desenho do jogo no fundo da câmara.
     *
     * Vai ACIMA da porta de propósito: na altura dos olhos ele brigaria com o
     * portal e ficaria metade escondido por quem está passando. Em cima, ele
     * é a primeira coisa que aparece quando se olha o nicho de longe, pelo
     * arco.
     */
    private void esculturaDoNicho(World mundo, MinigamesCatalog planta,
                                  MinigamesCatalog.Portal portal, int piso, int base,
                                  Material escura) {
        List<String> desenho = planta.escultura(portal.escultura());
        if (!HubCatalog.desenhoEhRetangular(desenho)) {
            return;
        }

        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fundo = planta.galeriaFim();
        int largura = desenho.get(0).length();
        int meia = largura / 2;
        Material luz = planta.cor("luz", Material.SEA_LANTERN);

        for (int linha = 0; linha < desenho.size(); linha++) {
            int y = base + (desenho.size() - 1 - linha);
            String texto = desenho.get(linha);
            for (int coluna = 0; coluna < largura; coluna++) {
                Material material = switch (texto.charAt(coluna)) {
                    case '#' -> portal.moldura();
                    case 'o' -> portal.destaque();
                    case '*' -> luz;
                    case 'x' -> escura;
                    default -> null;
                };
                if (material == null) {
                    continue;
                }
                int ao = centro + coluna - meia;
                por(mundo, lado.x(ao, fundo), piso + y, lado.z(ao, fundo), material);
            }
        }
    }

    /**
     * A fachada de um nicho, vista do pátio.
     *
     * É aqui que a cor do jogo aparece grande: um painel alto sobre o arco,
     * com a moldura por fora. O letreiro flutuante fica na frente dele, e por
     * isso o miolo do painel é o bloco APAGADO da dupla (terracota, não
     * concreto) — texto claro em cima de concreto saturado não se lê.
     */
    private void fachadaDoNicho(World mundo, MinigamesCatalog planta,
                                MinigamesCatalog.Portal portal, int piso) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int dentro = planta.patio() + 1;
        int arco = planta.vaoAltura();
        int altura = planta.galeriaAltura();
        int meia = planta.nichoMeiaLargura();

        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        // A aduela: tudo o que está na faixa do arco e NÃO é o arco.
        //
        // Escrito por exclusão de propósito. Desenhar o contorno "um bloco
        // em volta do vão" é a maneira óbvia e é a que erra: na fiada em que
        // a verga encolhe, o contorno de uma fiada cai onde a de baixo ainda
        // era passagem, e o arco amanhece tapado. Perguntar "este bloco é
        // vão?" não tem esse caso.
        int vao = planta.vaoLargura();
        for (int y = 1; y <= arco; y++) {
            int aqui = meiaLarguraDoArco(arco - y, vao);
            for (int ao = -(vao + 1); ao <= vao + 1; ao++) {
                if (Math.abs(ao) <= aqui) {
                    continue;
                }
                por(mundo, lado.x(centro + ao, dentro), piso + y,
                        lado.z(centro + ao, dentro), portal.moldura());
            }
        }
        for (int ao = -(vao + 1); ao <= vao + 1; ao++) {
            por(mundo, lado.x(centro + ao, dentro), piso + arco + 1,
                    lado.z(centro + ao, dentro), portal.destaque());
        }

        // O painel alto, entre a verga do arco e a cimalha.
        int baixo = arco + 2;
        int topo = altura - 3;
        for (int y = baixo; y <= topo; y++) {
            for (int ao = -meia + 1; ao <= meia - 1; ao++) {
                boolean moldura = y == baixo || y == topo || Math.abs(ao) == meia - 1;
                por(mundo, lado.x(centro + ao, dentro), piso + y,
                        lado.z(centro + ao, dentro),
                        moldura ? portal.destaque() : portal.moldura());
            }
        }

        // Dois lampiões, na altura de quem está no pátio.
        for (int ao : new int[]{-meia + 1, meia - 1}) {
            por(mundo, lado.x(centro + ao, dentro - 1), piso + 5,
                    lado.z(centro + ao, dentro - 1), luz);
            por(mundo, lado.x(centro + ao, dentro - 1), piso + 6,
                    lado.z(centro + ao, dentro - 1), rod);
        }
    }

    // ------------------------------------------------------------------
    //  A passarela de cima
    // ------------------------------------------------------------------

    /**
     * Onde o parapeito de dentro tem de ABRIR: a boca das quatro pontes.
     *
     * Este é o defeito que a busca em largura pegou antes de a obra existir.
     * O parapeito de dentro corre a volta inteira do anel, e a ponte da torre
     * chega na galeria exatamente em cima dele — dois blocos de guarda-corpo
     * na frente da porta de cima, e as quatro torres viravam escada para
     * nada. Subia-se vinte blocos para bater numa grade de vidro.
     *
     * A ponte sai da face de fora da torre no eixo z, três blocos de largura,
     * então a abertura é no lado |z| do anel, na faixa de x da porta.
     */
    static boolean bocaDaPonte(int patio, int torreCentro, int torreMeia, int x, int z) {
        int dentro = patio + 1;
        int aoLongo = torreCentro + saidaDaTorre(torreMeia);
        return Math.abs(z) == dentro
                && Math.abs(x) >= aoLongo && Math.abs(x) <= aoLongo + 2;
    }

    /**
     * A volta por cima da galeria, de onde se vê o pátio inteiro.
     *
     * O chão dela é o topo do maciço da galeria, que já está lá. O que falta
     * é parapeito nos dois lados — e ele não é enfeite: a borda de dentro dá
     * vinte blocos de queda no pátio e a de fora dá no void.
     */
    private void passarelaSuperior(World mundo, MinigamesCatalog planta,
                                   List<MinigamesCatalog.Portal> portais, int piso) {
        int dentro = planta.patio() + 1;
        int fora = planta.galeriaFim();
        int base = piso + planta.galeriaAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material parapeito = planta.cor("parapeito", Material.QUARTZ_SLAB);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        for (int i = -fora; i <= fora; i++) {
            for (int[] p : new int[][]{{i, -fora}, {i, fora}, {-fora, i}, {fora, i}}) {
                por(mundo, p[0], base + 1, p[1], estrutura);
                laje(mundo, p[0], base + 2, p[1], parapeito);
            }
        }
        for (int i = -dentro; i <= dentro; i++) {
            for (int[] p : new int[][]{{i, -dentro}, {i, dentro}, {-dentro, i}, {dentro, i}}) {
                if (Math.max(Math.abs(p[0]), Math.abs(p[1])) != dentro) {
                    continue;
                }
                if (bocaDaPonte(planta.patio(), planta.torreCentro(),
                        planta.torreMeia(), p[0], p[1])) {
                    continue;
                }
                por(mundo, p[0], base + 1, p[1], Math.abs(i) % 4 == 0 ? estrutura : vidro);
                laje(mundo, p[0], base + 2, p[1], parapeito);
            }
        }

        // Um mastro aceso em cima de cada nicho, para a passarela ter ritmo
        // e para quem está no pátio ver a linha do alto de noite.
        for (MinigamesCatalog.Portal portal : portais) {
            MinigamesCatalog.Lado lado = portal.lado();
            int centro = planta.centroDoVao(portal.vao());
            int meio = (dentro + fora) / 2;
            por(mundo, lado.x(centro, meio), base + 1, lado.z(centro, meio),
                    portal.destaque());
            por(mundo, lado.x(centro, meio), base + 2, lado.z(centro, meio),
                    planta.cor("luz", Material.SEA_LANTERN));
            for (int y = 3; y <= 5; y++) {
                por(mundo, lado.x(centro, meio), base + y, lado.z(centro, meio), rod);
            }
        }
    }

    // ------------------------------------------------------------------
    //  As torres
    // ------------------------------------------------------------------

    /**
     * Uma célula da rampa: em que fiada ela está e se é laje.
     *
     * A conta é a alma da escada e por isso é estática e pura — o teste
     * confere os quarenta degraus sem servidor. Meio bloco por célula: par
     * fecha a fiada com bloco inteiro, ímpar põe laje em cima da seguinte.
     */
    record Degrau(int fiada, boolean laje) {

        /** A altura em que se PISA, contada do piso. */
        double superficie() {
            return laje ? fiada + 0.5 : fiada + 1.0;
        }
    }

    static Degrau degrau(int celula) {
        if (celula % 2 == 0) {
            return new Degrau(celula / 2, false);
        }
        return new Degrau((celula + 1) / 2, true);
    }

    /**
     * A volta completa na parede de dentro da torre, em ordem de subida.
     *
     * Começa encostada na face de dentro (onde fica a porta do pátio), passa
     * pelas quatro paredes e termina um bloco antes de onde começou. Quarenta
     * células para subir vinte: uma volta, meio bloco por vez.
     */
    static List<int[]> voltaDaTorre(int raio) {
        List<int[]> volta = new ArrayList<>();
        for (int ox = -raio; ox <= raio; ox++) {
            volta.add(new int[]{ox, -raio});
        }
        for (int oz = -raio + 1; oz <= raio; oz++) {
            volta.add(new int[]{raio, oz});
        }
        for (int ox = raio - 1; ox >= -raio; ox--) {
            volta.add(new int[]{ox, raio});
        }
        for (int oz = raio - 1; oz >= -raio + 1; oz--) {
            volta.add(new int[]{-raio, oz});
        }
        return volta;
    }

    /** Onde a porta de baixo entra: junto da primeira célula da volta. */
    static int entradaDaTorre(int meia) {
        return -meia + 1;
    }

    /** Onde a porta de cima sai: junto da ponte sobre o poço da rampa. */
    static int saidaDaTorre(int meia) {
        return 2;
    }

    private void torres(World mundo, MinigamesCatalog planta, int piso) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                torre(mundo, planta, piso, sx, sz);
                contraforte(mundo, planta, piso, sx, sz);
            }
        }
    }

    /**
     * Uma das quatro torres do canto do pátio.
     *
     * Sobe dez acima da galeria e é a única maneira de chegar na passarela.
     * Entra-se pela face virada para o meio da praça, sobe-se uma volta
     * inteira e sai-se pela face virada para a galeria, na altura de cima.
     */
    private void torre(World mundo, MinigamesCatalog planta, int piso, int sx, int sz) {
        int centro = planta.torreCentro();
        int meia = planta.torreMeia();
        int altura = planta.torreAltura();
        int patamar = planta.galeriaAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material parapeito = planta.cor("parapeito", Material.QUARTZ_SLAB);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

        // 1. a casca
        for (int ox = -meia; ox <= meia; ox++) {
            for (int oz = -meia; oz <= meia; oz++) {
                boolean parede = Math.abs(ox) == meia || Math.abs(oz) == meia;
                if (!parede) {
                    continue;
                }
                for (int y = 1; y <= altura; y++) {
                    Material material;
                    if (y == altura || y == patamar) {
                        material = borda;
                    } else if (y <= 2) {
                        material = borda;
                    } else if (Math.abs(ox) == meia && Math.abs(oz) == meia) {
                        material = escuro;                 // as quinas
                    } else if (y > patamar + 3 && y % 3 == 0) {
                        material = vidro;                  // o mirante
                    } else {
                        material = estrutura;
                    }
                    por(mundo, mx(sx, centro, ox), piso + y, mx(sz, centro, oz), material);
                }
            }
        }

        // 2. o vazio de dentro
        for (int ox = -meia + 1; ox <= meia - 1; ox++) {
            for (int oz = -meia + 1; oz <= meia - 1; oz++) {
                for (int y = 1; y <= altura - 1; y++) {
                    ar(mundo, mx(sx, centro, ox), piso + y, mx(sz, centro, oz));
                }
            }
        }

        // 3. a rampa: uma volta, meio bloco por célula
        List<int[]> volta = voltaDaTorre(meia - 1);
        for (int i = 0; i < volta.size(); i++) {
            int[] celula = volta.get(i);
            Degrau d = degrau(i);
            int x = mx(sx, centro, celula[0]);
            int z = mx(sz, centro, celula[1]);

            for (int y = 1; y < d.fiada(); y++) {
                por(mundo, x, piso + y, z, estrutura);
            }
            if (d.laje()) {
                laje(mundo, x, piso + d.fiada(), z, parapeito);
            } else if (d.fiada() >= 1) {
                por(mundo, x, piso + d.fiada(), z, chao);
            }

            // O guarda-corpo de vidro, no lado de dentro da rampa.
            //
            // Duas condições, e as duas pegaram defeito de verdade. A altura
            // sai de ceil() e não de floor(): com floor(), o corrimão da
            // célula de meio degrau nasce um bloco acima do pé e fica
            // pairando. E nas QUINAS o vizinho de dentro ainda é célula de
            // rampa — pôr corrimão ali é pôr vidro no meio do caminho, uma
            // fiada acima do degrau seguinte, e a escada emparedava na curva.
            int[] dentro = paraDentro(celula, meia - 1);
            boolean vizinhoEhRampa =
                    Math.max(Math.abs(dentro[0]), Math.abs(dentro[1])) >= meia - 1;
            if (d.superficie() < patamar - 2 && !vizinhoEhRampa) {
                int base = (int) Math.ceil(d.superficie());
                for (int y = base; y <= base + 1; y++) {
                    por(mundo, mx(sx, centro, dentro[0]), piso + y,
                            mx(sz, centro, dentro[1]), vidro);
                }
            }
        }

        // 4. o patamar de cima, com a ponte por cima do poço da rampa
        for (int ox = -meia + 1; ox <= meia - 1; ox++) {
            for (int oz = -meia + 1; oz <= meia - 1; oz++) {
                boolean miolo = Math.max(Math.abs(ox), Math.abs(oz)) <= meia - 2;
                boolean ponte = ox >= saidaDaTorre(meia) && ox <= saidaDaTorre(meia) + 2
                        && oz == meia - 1;
                if (!miolo && !ponte) {
                    continue;
                }
                por(mundo, mx(sx, centro, ox), piso + patamar, mx(sz, centro, oz),
                        miolo && Math.abs(ox) == Math.abs(oz) ? escuro : chao);
            }
        }

        // 5. as portas, cada uma onde a rampa a alcança
        //
        // A de baixo fica na primeira célula da volta: três blocos adiante e
        // quem entrasse já encontraria a rampa na altura do peito.
        int entrada = entradaDaTorre(meia);
        for (int ox = entrada - 1; ox <= entrada + 1; ox++) {
            for (int y = 1; y <= 4; y++) {
                ar(mundo, mx(sx, centro, ox), piso + y, mx(sz, centro, -meia));
            }
        }
        int saida = saidaDaTorre(meia);
        for (int ox = saida; ox <= saida + 2; ox++) {
            for (int y = patamar + 1; y <= patamar + 3; y++) {
                ar(mundo, mx(sx, centro, ox), piso + y, mx(sz, centro, meia));
            }
        }

        // 6. o telhado e o facho
        for (int ox = -meia; ox <= meia; ox++) {
            for (int oz = -meia; oz <= meia; oz++) {
                int d = Math.max(Math.abs(ox), Math.abs(oz));
                por(mundo, mx(sx, centro, ox), piso + altura, mx(sz, centro, oz),
                        d == meia ? borda : d >= meia - 2 ? chao : estrutura);
                if (d <= 1) {
                    por(mundo, mx(sx, centro, ox), piso + altura + 1,
                            mx(sz, centro, oz), d == 0 ? luz : nucleo);
                }
            }
        }
        for (int y = 2; y <= 4; y++) {
            por(mundo, mx(sx, centro, 0), piso + altura + y, mx(sz, centro, 0), rod);
        }
    }

    /**
     * O maciço de um bloco que liga a torre à galeria.
     *
     * Serve de duas coisas ao mesmo tempo: fecha a fenda de um bloco entre as
     * duas (de onde se veria o void olhando para baixo) e, no topo, é o
     * pedaço de chão que leva da porta de cima da torre para a passarela.
     */
    private void contraforte(World mundo, MinigamesCatalog planta, int piso, int sx, int sz) {
        int patio = planta.patio();
        int dentro = planta.torreCentro() - planta.torreMeia();
        int altura = planta.galeriaAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int i = dentro; i <= patio; i++) {
            for (int y = 1; y <= altura; y++) {
                Material material = y == altura ? chao : y <= 2 ? borda : estrutura;
                por(mundo, sx * patio, piso + y, sz * i, material);
                por(mundo, sx * i, piso + y, sz * patio, material);
            }
        }
    }

    /** Espelha um deslocamento da torre para o quadrante certo. */
    private static int mx(int sinal, int centro, int deslocamento) {
        return sinal * (centro + deslocamento);
    }

    /** O vizinho de uma célula da volta, um passo na direção do meio. */
    static int[] paraDentro(int[] celula, int raio) {
        if (Math.abs(celula[0]) == raio) {
            return new int[]{celula[0] - Integer.signum(celula[0]), celula[1]};
        }
        return new int[]{celula[0], celula[1] - Integer.signum(celula[1])};
    }

    // ------------------------------------------------------------------
    //  O monumento
    // ------------------------------------------------------------------

    /**
     * O monumento do meio, e dentro dele a volta para o hub.
     *
     * Fica três degraus acima do pátio por um motivo prático: o portal de
     * volta no nível do chão seria atravessado sem querer por quem cruza a
     * praça de um nicho para outro. Para passar por ele é preciso SUBIR, e
     * subir não se faz distraído.
     */
    private void monumento(World mundo, MinigamesCatalog planta, int piso) {
        int raio = planta.monumentoRaio();
        int altura = planta.monumentoAltura();
        MinigamesCatalog.Volta volta = planta.volta();

        Material chao = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

        // 1. os três degraus do pódio
        int[][] camadas = {{1, raio + 3}, {2, raio + 1}, {3, raio}};
        for (int[] camada : camadas) {
            int y = camada[0];
            int meia = camada[1];
            for (int x = -meia; x <= meia; x++) {
                for (int z = -meia; z <= meia; z++) {
                    int d = Math.max(Math.abs(x), Math.abs(z));
                    if (d > meia) {
                        continue;
                    }
                    Material material = y < 3 ? borda
                            : d >= raio - 1 ? escuro
                            : d % 4 == 0 ? volta.destaque() : chao;
                    por(mundo, x, piso + y, z, material);
                }
            }
        }

        // 2. os quatro pilares e as vergas que fecham o pavilhão
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int ox = 0; ox <= 2; ox++) {
                    for (int oz = 0; oz <= 2; oz++) {
                        for (int y = 4; y <= altura - 4; y++) {
                            Material material = y >= altura - 6 ? metal
                                    : y % 5 == 0 ? volta.destaque() : estrutura;
                            por(mundo, sx * (raio - 2 - ox), piso + y,
                                    sz * (raio - 2 - oz), material);
                        }
                    }
                }
            }
        }
        for (int i = -(raio - 2); i <= raio - 2; i++) {
            for (int y = altura - 5; y <= altura - 4; y++) {
                for (int s = -1; s <= 1; s += 2) {
                    Material material = y == altura - 4 ? volta.destaque() : estrutura;
                    por(mundo, i, piso + y, s * (raio - 2), material);
                    por(mundo, s * (raio - 2), piso + y, i, material);
                }
            }
        }

        // 3. o teto, a pirâmide do farol e o facho
        for (int x = -(raio - 1); x <= raio - 1; x++) {
            for (int z = -(raio - 1); z <= raio - 1; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                por(mundo, x, piso + altura - 3, z, d >= raio - 3 ? borda : chao);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                por(mundo, x, piso + altura - 2, z, metal);
            }
        }
        por(mundo, 0, piso + altura - 1, 0, Material.BEACON);
        for (int d = 3; d <= 4; d++) {
            for (int i = -d; i <= d; i++) {
                if (Math.max(Math.abs(i), d) != d) {
                    continue;
                }
                for (int y = altura - 2; y <= altura; y++) {
                    Material material = y == altura ? rod : y == altura - 1 ? nucleo : luz;
                    por(mundo, i, piso + y, d, material);
                    por(mundo, i, piso + y, -d, material);
                    por(mundo, d, piso + y, i, material);
                    por(mundo, -d, piso + y, i, material);
                }
            }
        }

        // 4. a parede da volta, em pé no pódio, com o vão de cinco por sete
        for (int x = -5; x <= 5; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 4; y <= 12; y++) {
                    boolean campo = Math.abs(x) <= 2 && y <= 10;
                    if (campo) {
                        ar(mundo, x, piso + y, z);
                        continue;
                    }
                    Material material = Math.abs(x) == 3 && y <= 10 ? luz
                            : y == 12 ? volta.destaque() : volta.moldura();
                    por(mundo, x, piso + y, z, material);
                }
            }
        }
        // O chão do vão, na cor da volta, para ela se ver de longe pelo chão.
        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 1; z++) {
                por(mundo, x, piso + 3, z, volta.destaque());
            }
        }
    }

    // ------------------------------------------------------------------
    //  O anel suspenso
    // ------------------------------------------------------------------

    /**
     * O quadro que paira sobre o pátio.
     *
     * É o que fecha a composição por cima. Sem ele o lobby é um pátio com
     * parede em volta e céu aberto no meio — e céu aberto de 87 blocos some
     * da foto. Pairando, sem apoio nenhum, num mundo que é só void: é o
     * único momento da obra que não finge ser arquitetura possível.
     */
    private void anelSuspenso(World mundo, MinigamesCatalog planta, int piso) {
        int raio = planta.anelRaio();
        int base = piso + planta.anelAltura();

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < raio - 2) {
                    continue;
                }
                for (int y = 0; y <= 2; y++) {
                    Material material;
                    if (Math.abs(x) >= raio - 1 && Math.abs(z) >= raio - 1) {
                        material = y == 1 ? nucleo : metal;     // as quatro quinas
                    } else if (y == 1 && d == raio - 1) {
                        material = luz;                          // a tira de luz
                    } else {
                        material = estrutura;
                    }
                    por(mundo, x, base + y, z, material);
                }
            }
        }
        // Agulhas nas quinas, para o quadro ter silhueta contra o céu.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 3; y <= 6; y++) {
                    por(mundo, sx * raio, base + y, sz * raio, rod);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Letreiros
    // ------------------------------------------------------------------

    /**
     * Refaz só os textos, sem reescrever a obra.
     *
     * Existe pelo mesmo motivo do {@code /biga hub textos}: trocar uma cor ou
     * uma palavra não pode custar duzentos mil blocos reescritos, que é um
     * servidor travado por segundos a cada ida.
     */
    public Resultado textos(World mundo) {
        MinigamesCatalog planta = plugin.minigamesCatalog();
        List<MinigamesCatalog.Portal> portais = planta.portais();
        if (portais.isEmpty()) {
            return new Resultado(false,
                    "O minigames.yml não tem nenhum portal em 'portais'.", 0);
        }
        limparPaineis(mundo);
        int quantos = escreverTextos(mundo, planta, portais, planta.pisoY());
        return new Resultado(true, "Textos do lobby refeitos: " + quantos + " letreiros.",
                quantos);
    }

    private int escreverTextos(World mundo, MinigamesCatalog planta,
                               List<MinigamesCatalog.Portal> portais, int piso) {
        int quantos = 0;
        for (MinigamesCatalog.Portal portal : portais) {
            letreiroDaFachada(mundo, planta, portal, piso);
            placaDaCamara(mundo, planta, portal, piso);
            quantos += 2;
        }
        letreiroDoMonumento(mundo, planta, piso);
        boasVindas(mundo, planta, piso);
        return quantos + 3;
    }

    /**
     * O nome do jogo na fachada, legível de dentro do pátio.
     *
     * O yaw é o de quem olha a galeria — ou seja, {@code yawParaDentro}. A
     * conta é a mesma dos painéis do hub e vale a pena anotar de novo: um
     * TextDisplay de yaw Y se lê de quem está do lado oposto, então para
     * atender um leitor de yaw L o display tem de estar em L + 180. Aqui o
     * leitor está no pátio olhando para fora, logo L = yawParaFora, e
     * yawParaFora + 180 é yawParaDentro.
     */
    private void letreiroDaFachada(World mundo, MinigamesCatalog planta,
                                   MinigamesCatalog.Portal portal, int piso) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fora = planta.patio();
        int y = piso + planta.vaoAltura() + (planta.galeriaAltura() - planta.vaoAltura()) / 2;

        Component texto = plugin.mini().deserialize(
                "<gradient:" + HubBuilder.clarear(portal.cor(), 0.6) + ":" + portal.cor() + ">"
                        + "<bold>" + HubBuilder.escapar(portal.titulo()) + "</bold></gradient>"
                        + "<newline>" + REGUA + "━━━━━━━━━━</color>"
                        + "<newline><white>" + HubBuilder.escapar(portal.descricao())
                        + "</white>");

        Location alvo = new Location(mundo,
                lado.x(centro, fora) + 0.5, y + 0.5, lado.z(centro, fora) + 0.5);
        alvo.setYaw(lado.yawParaDentro());
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, new Estilo(2.8f, planta, 360, 4.0f));
    }

    /** A placa de dentro da câmara, sobre a porta: regra e estado do jogo. */
    private void placaDaCamara(World mundo, MinigamesCatalog planta,
                               MinigamesCatalog.Portal portal, int piso) {
        MinigamesCatalog.Lado lado = portal.lado();
        int centro = planta.centroDoVao(portal.vao());
        int fora = planta.galeriaFim() - 3;

        String rodape = portal.aberto()
                ? "<newline><gray>" + HubBuilder.escapar(portal.detalhe()) + "</gray>"
                : "<newline><color:#FF8A8A><bold>EM OBRA</bold></color>"
                        + "<color:#9A6A6A> · ainda não abre</color>";

        Component texto = plugin.mini().deserialize(
                "<color:" + portal.cor() + "><bold>»</bold></color> "
                        + "<gradient:" + HubBuilder.clarear(portal.cor(), 0.55) + ":"
                        + portal.cor() + "><bold>"
                        + HubBuilder.escapar(portal.titulo()) + "</bold></gradient> "
                        + "<color:" + portal.cor() + "><bold>«</bold></color>"
                        + "<newline>" + REGUA + "────────────────</color>"
                        + "<newline><white>" + HubBuilder.escapar(portal.descricao())
                        + "</white>" + rodape);

        Location alvo = new Location(mundo,
                lado.x(centro, fora) + 0.5, piso + alturaDaPorta(planta) + 1.6,
                lado.z(centro, fora) + 0.5);
        alvo.setYaw(lado.yawParaDentro());
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, new Estilo(1.9f, planta, 300, 2.5f));
    }

    /** O nome do lugar, nas duas faces do monumento. */
    private void letreiroDoMonumento(World mundo, MinigamesCatalog planta, int piso) {
        MinigamesCatalog.Volta volta = planta.volta();
        Component texto = plugin.mini().deserialize(
                MARCA + "<bold>THE BIGAS</bold></gradient>"
                        + "<newline><white><bold>LOBBY DOS MINIGAMES</bold></white>"
                        + "<newline>" + REGUA + "━━━━━━━━━━━━━━━━</color>"
                        + "<newline><color:" + volta.cor() + ">" + "▼ "
                        + HubBuilder.escapar(volta.titulo()) + " ▼</color>"
                        + "<newline><gray>" + HubBuilder.escapar(volta.descricao())
                        + "</gray>");

        for (int s = -1; s <= 1; s += 2) {
            Location alvo = new Location(mundo, 0.5, piso + 14.0, s * 2.5);
            alvo.setYaw(s > 0 ? 0f : 180f);
            alvo.setPitch(0f);
            painel(mundo, alvo, texto, new Estilo(2.4f, planta, 420, 4.0f));
        }
    }

    /** O recado de quem acabou de chegar, na frente de quem nasce. */
    private void boasVindas(World mundo, MinigamesCatalog planta, int piso) {
        Component texto = plugin.mini().deserialize(
                "<white><bold>VINTE JOGOS</bold></white>"
                        + "<newline>" + REGUA + "━━━━━━━━━━━━</color>"
                        + "<newline><gray>cada faixa do chão leva a um deles</gray>"
                        + "<newline><color:#8FD3FF>a cor da faixa é a cor do portal</color>");

        Location alvo = new Location(mundo, 0.5, piso + 8.0, 14.5);
        alvo.setYaw(180f);
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, new Estilo(2.0f, planta, 380, 3.0f));
    }

    /** A que distância do centro o jogador nasce, no eixo z. */
    static final int SPAWN_Z = 6;

    /** Em que fiada acima do piso ele pisa: o pódio tem três degraus. */
    static final int SPAWN_ALTURA = 4;

    /**
     * Onde o jogador nasce: no pódio, de COSTAS para a volta.
     *
     * Virado para o sul, o que ele vê é o pátio e os cinco portais da galeria
     * do sul; a volta para o hub fica atrás dele. Virado para a volta, a
     * primeira coisa que ele veria ao chegar seria a saída.
     *
     * Os seis blocos de afastamento não são estética: a caixa da volta pega
     * até 1,2 de z, e nascer dentro dela mandaria o jogador de volta ao hub
     * no mesmo instante em que chegasse — um laço fechado entre os dois
     * mundos. O {@code LobbyTest} confere essa distância.
     */
    public static Location pontoDeSpawn(World mundo, MinigamesCatalog planta) {
        Location pouso = new Location(mundo, 0.5, planta.pisoY() + SPAWN_ALTURA,
                SPAWN_Z + 0.5);
        pouso.setYaw(0f);
        pouso.setPitch(0f);
        return pouso;
    }

    // ------------------------------------------------------------------
    //  Ferramentas
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

    /** Laje sempre na metade de baixo: laje de cima vira degrau invisível. */
    private void laje(World mundo, int x, int y, int z, Material material) {
        Block bloco = mundo.getBlockAt(x, y, z);
        bloco.setType(material, false);
        if (bloco.getBlockData() instanceof Slab laje) {
            laje.setType(Slab.Type.BOTTOM);
            bloco.setBlockData(laje, false);
        } else if (bloco.getBlockData() instanceof Bisected cortado) {
            cortado.setHalf(Bisected.Half.BOTTOM);
            bloco.setBlockData(cortado, false);
        }
        contador++;
    }

    /** A régua fina embaixo dos títulos. Separa sem pesar. */
    private static final String REGUA = "<color:#55595E>";

    /** O laranja e o dourado do servidor — os mesmos do prefixo do chat. */
    private static final String MARCA = "<gradient:#FF6A00:#FFD700:#FF6A00>";

    private record Estilo(float escala, int fundoAlpha, int larguraLinha, float alcance) {

        Estilo(float escala, MinigamesCatalog planta, int larguraLinha, float alcance) {
            this(escala, 0, larguraLinha, alcance);
        }
    }

    private void painel(World mundo, Location alvo, Component texto, Estilo estilo) {
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(Display.Billboard.FIXED);
            novo.setAlignment(TextDisplay.TextAlignment.CENTER);
            novo.setBackgroundColor(org.bukkit.Color.fromARGB(estilo.fundoAlpha(), 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(estilo.larguraLinha());
            novo.setViewRange(estilo.alcance());
            novo.setPersistent(true);
            novo.addScoreboardTag(TAG_PAINEL);

            float e = estilo.escala();
            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(e, e, e), t.getRightRotation()));
        });
    }

    private void limparPaineis(World mundo) {
        for (Entity entidade : mundo.getEntities()) {
            if (entidade.getScoreboardTags().contains(TAG_PAINEL)) {
                entidade.remove();
            }
        }
    }

    /** Quantos blocos já existem onde o lobby iria. Zero é o esperado. */
    private int contarOcupados(World mundo, MinigamesCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        int achados = 0;

        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = piso - 10; y <= piso + planta.alturaDaObra(); y++) {
                    if (!mundo.getBlockAt(x, y, z).getType().isAir()) {
                        achados++;
                    }
                }
            }
        }
        return achados;
    }
}
