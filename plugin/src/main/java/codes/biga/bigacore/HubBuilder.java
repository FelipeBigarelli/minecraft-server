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

import java.util.List;

/**
 * Levanta a praça de portais — o hub do servidor.
 *
 * <h2>O que faz uma construção parecer futurista</h2>
 *
 * Não é o bloco: é a SUPERFÍCIE. Futurista no Minecraft sai de muita área
 * lisa e clara, pouquíssima cor, e a cor que existe sendo LUZ e não tinta —
 * quartzo, concreto, vidro e lanterna. Um único bloco de madeira ou de pedra
 * bruta derruba o efeito inteiro, e por isso a paleta do hub.yml não tem
 * nenhum dos dois.
 *
 * <h2>Por que tudo é redondo e alinhado ao norte</h2>
 *
 * A praça é um disco e os caminhos saem nos pontos cardeais. Isso não é
 * enfeite: é o que faz alguém dizer "o portal do survival é o do oeste" e
 * acertar sempre, sem placa. Cada braço tem cor própria do chão ao topo da
 * escultura, então o jogador escolhe o caminho pela cor antes de ler a
 * palavra — e quem não lê português escolhe igual.
 *
 * <h2>A obra NÃO tem desfazer de verdade</h2>
 *
 * O hub nasce num mundo vazio, e por isso desfazer é só apagar. O construtor
 * se recusa a levantar em lugar que já tenha bloco: sem essa trava, rodar o
 * comando no survival apagaria construção de jogador sem copiar nada antes.
 */
public final class HubBuilder {

    /** Marca das entidades de texto do hub, para o desfazer achá-las. */
    public static final String TAG_PAINEL = "bigacore_hub_painel";

    public record Resultado(boolean ok, String mensagem, int blocos) {}

    private final BigaCore plugin;

    public HubBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    /**
     * @param aplicar false confere o terreno e devolve o tamanho da obra sem
     *                colocar um bloco. É o /biga hub info.
     */
    public Resultado construir(World mundo, boolean aplicar) {
        HubCatalog planta = plugin.hubCatalog();
        List<HubCatalog.Portal> portais = planta.portais();

        if (portais.isEmpty()) {
            return new Resultado(false, "O hub.yml não tem nenhum portal em 'portais'.", 0);
        }
        if (!HubCatalog.facesSaoUnicas(portais)) {
            return new Resultado(false,
                    "Dois portais do hub.yml estão na mesma face — um sairia por cima do outro.",
                    0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0 && aplicar) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área do hub. "
                    + "Construa num mundo vazio (/mv create hub normal -g BigaCore) "
                    + "ou rode /biga hub desfazer CONFIRMAR antes.", 0);
        }
        if (!aplicar) {
            return new Resultado(true, "Área livre: " + (ocupados == 0 ? "sim" : "NÃO, "
                    + ocupados + " bloco(s) no caminho") + ". Diâmetro da obra: "
                    + (planta.alcanceDaObra() * 2) + " blocos.", 0);
        }

        int piso = planta.pisoY();
        contador = 0;

        limparPaineis(mundo);
        pracaCentral(mundo, planta, piso);
        torreCentral(mundo, planta, piso);

        for (HubCatalog.Portal portal : portais) {
            passarela(mundo, planta, portal, piso);
            terraco(mundo, planta, portal, piso);
            arcoDoPortal(mundo, planta, portal, piso);
            esculturaDoPortal(mundo, planta, portal, piso);
            painelDoPortal(mundo, planta, portal, piso);
        }

        mirante(mundo, planta, portais, piso);
        pousoDoJogador(mundo, planta, piso);

        return new Resultado(true, "Hub levantado: " + contador + " blocos, "
                + portais.size() + " portais.", contador);
    }

    /** Apaga tudo dentro do alcance da obra. Só serve em mundo de hub. */
    public Resultado desfazer(World mundo) {
        HubCatalog planta = plugin.hubCatalog();
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        int apagados = 0;

        for (int u = -alcance; u <= alcance; u++) {
            for (int v = -alcance; v <= alcance; v++) {
                for (int y = piso - 4; y <= piso + planta.torreAltura() + 6; y++) {
                    Block bloco = mundo.getBlockAt(u, y, v);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        apagados++;
                    }
                }
            }
        }

        limparPaineis(mundo);
        return new Resultado(true, "Hub removido: " + apagados + " blocos apagados.", apagados);
    }

    // ------------------------------------------------------------------
    //  Praça
    // ------------------------------------------------------------------

    private void pracaCentral(World mundo, HubCatalog planta, int piso) {
        int raio = planta.raio();

        Material pisoM = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material linha = planta.cor("piso-linha", Material.LIGHT_BLUE_CONCRETE);
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);

        for (int u = -raio; u <= raio; u++) {
            for (int v = -raio; v <= raio; v++) {
                int distancia = (int) Math.round(Math.sqrt(u * u + v * v));
                if (distancia > raio) {
                    continue;
                }

                // Três camadas: a praça tem ESPESSURA. Disco de um bloco só
                // flutuando no void parece folha de papel — e de baixo, que é
                // de onde se chega voando, parece erro.
                por(mundo, u, v, piso - 2, borda);
                por(mundo, u, v, piso - 1, estrutura);
                por(mundo, u, v, piso, pisoDaPraca(distancia, raio, pisoM, linha, escuro, nucleo));
            }
        }

        // Parapeito na borda, menos onde as passarelas saem — quem cai do
        // hub cai no void, e no void não fica nem túmulo.
        for (int u = -raio; u <= raio; u++) {
            for (int v = -raio; v <= raio; v++) {
                if ((int) Math.round(Math.sqrt(u * u + v * v)) != raio) {
                    continue;
                }
                if (ehBocaDePassarela(planta, u, v)) {
                    continue;
                }
                laje(mundo, u, v, piso + 1, planta.cor("parapeito", Material.QUARTZ_SLAB));
            }
        }

        // Colunata: oito pilares de luz em volta, na mesma malha dos portais.
        for (int i = 0; i < 8; i++) {
            double angulo = Math.PI * i / 4.0;
            int u = (int) Math.round(Math.cos(angulo) * (raio - 3));
            int v = (int) Math.round(Math.sin(angulo) * (raio - 3));

            for (int y = piso + 1; y <= piso + 7; y++) {
                por(mundo, u, v, y, y % 3 == 0
                        ? planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS)
                        : estrutura);
            }
            por(mundo, u, v, piso + 8, planta.cor("luz", Material.SEA_LANTERN));
            por(mundo, u, v, piso + 9, planta.cor("luz-fria", Material.END_ROD));
        }
    }

    /** O desenho do chão da praça, em anéis. */
    private Material pisoDaPraca(int distancia, int raio, Material piso, Material linha,
                                 Material escuro, Material nucleo) {
        if (distancia <= 3) {
            return nucleo;
        }
        if (distancia == 4 || distancia == raio - 1) {
            return linha;
        }
        if (distancia == raio) {
            return escuro;
        }
        return distancia % 5 == 0 ? escuro : piso;
    }

    /** A boca de uma passarela não leva parapeito, senão ninguém passa. */
    private boolean ehBocaDePassarela(HubCatalog planta, int u, int v) {
        int meia = planta.passarelaLargura() / 2;
        for (HubCatalog.Portal portal : planta.portais()) {
            HubCatalog.Face face = portal.face();
            int aoLongo = u * face.du() + v * face.dv();
            int atravessado = u * face.lu() + v * face.lv();
            if (aoLongo > 0 && Math.abs(atravessado) <= meia) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Torre
    // ------------------------------------------------------------------

    /**
     * A torre do meio — o que se vê de qualquer ponto do hub.
     *
     * Ela afina em três degraus e termina num farol de verdade, com feixe
     * subindo. O feixe é o único elemento do hub visível de longe no void, e
     * é ele que diz "é por aqui" para quem chega voando.
     */
    private void torreCentral(World mundo, HubCatalog planta, int piso) {
        int altura = planta.torreAltura();
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material escura = planta.cor("estrutura-escura", Material.BLACK_CONCRETE);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material nucleo = planta.cor("nucleo", Material.AMETHYST_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);

        for (int y = 0; y <= altura; y++) {
            int raio = raioDaTorre(y, altura);

            for (int u = -raio; u <= raio; u++) {
                for (int v = -raio; v <= raio; v++) {
                    int distancia = Math.max(Math.abs(u), Math.abs(v));
                    if (distancia > raio) {
                        continue;
                    }
                    // Quina cortada: torre quadrada no void lê como caixa.
                    if (Math.abs(u) == raio && Math.abs(v) == raio) {
                        continue;
                    }

                    Material material;
                    if (distancia < raio) {
                        material = (u == 0 && v == 0) ? nucleo : Material.AIR;
                    } else if (y % 6 == 0) {
                        material = escura;
                    } else if (y % 3 == 1) {
                        material = vidro;
                    } else {
                        material = estrutura;
                    }

                    if (material != Material.AIR) {
                        por(mundo, u, v, piso + 1 + y, material);
                    }
                }
            }
        }

        // Base do farol: pirâmide de metal, que é o que faz o feixe existir.
        for (int camada = 0; camada < 3; camada++) {
            int raio = 3 - camada;
            for (int u = -raio; u <= raio; u++) {
                for (int v = -raio; v <= raio; v++) {
                    por(mundo, u, v, piso + altura + 2 + camada, metal);
                }
            }
        }
        por(mundo, 0, 0, piso + altura + 5, Material.BEACON);
        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                if (u != 0 || v != 0) {
                    por(mundo, u, v, piso + altura + 5, luz);
                }
            }
        }

        letreiroDaTorre(mundo, planta, piso + altura - 4);
    }

    /** Raio da torre naquela altura: três degraus, de baixo para cima. */
    static int raioDaTorre(int y, int altura) {
        if (y < altura * 0.45) {
            return 3;
        }
        if (y < altura * 0.8) {
            return 2;
        }
        return 1;
    }

    private void letreiroDaTorre(World mundo, HubCatalog planta, int y) {
        Component texto = plugin.mini().deserialize(
                "<gradient:#FF6A00:#FFD700><bold>THE BIGAS</bold></gradient>"
                        + "<newline><color:#8FD3FF>escolha um caminho</color>");

        // Quatro cópias, uma por lado: o texto de um TextDisplay FIXED só se
        // lê de frente, e um hub tem gente chegando dos quatro lados.
        for (HubCatalog.Face face : HubCatalog.Face.values()) {
            Location alvo = new Location(mundo, 0.5 + face.du() * 3.0, y,
                    0.5 + face.dv() * 3.0);
            alvo.setYaw(face.yawParaOCentro() + 180f);
            alvo.setPitch(0f);
            painel(mundo, alvo, texto, 2.2f);
        }
    }

    // ------------------------------------------------------------------
    //  Caminho até o portal
    // ------------------------------------------------------------------

    private void passarela(World mundo, HubCatalog planta, HubCatalog.Portal portal, int piso) {
        HubCatalog.Face face = portal.face();
        int meia = planta.passarelaLargura() / 2;

        Material pisoM = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int passo = planta.raio() - 1; passo <= planta.distanciaDoTerraco(); passo++) {
            for (int lado = -meia; lado <= meia; lado++) {
                int u = face.du() * passo + face.lu() * lado;
                int v = face.dv() * passo + face.lv() * lado;

                por(mundo, u, v, piso - 1, estrutura);
                // Faixa central na cor do mundo de destino: o caminho JÁ diz
                // para onde vai, antes de qualquer texto.
                por(mundo, u, v, piso, lado == 0 ? portal.destaque()
                        : Math.abs(lado) == meia ? borda : pisoM);

                if (Math.abs(lado) == meia) {
                    laje(mundo, u, v, piso + 1, planta.cor("parapeito", Material.QUARTZ_SLAB));
                }
            }

            // Poste de luz a cada cinco passos, dos dois lados.
            if (passo % 5 == 0) {
                for (int lado : new int[]{-meia, meia}) {
                    int u = face.du() * passo + face.lu() * lado;
                    int v = face.dv() * passo + face.lv() * lado;
                    for (int y = piso + 1; y <= piso + 3; y++) {
                        por(mundo, u, v, y, estrutura);
                    }
                    por(mundo, u, v, piso + 4, portal.destaque());
                    por(mundo, u, v, piso + 5, planta.cor("luz-fria", Material.END_ROD));
                }
            }
        }
    }

    private void terraco(World mundo, HubCatalog planta, HubCatalog.Portal portal, int piso) {
        HubCatalog.Face face = portal.face();
        int centroU = face.du() * planta.distanciaDoTerraco();
        int centroV = face.dv() * planta.distanciaDoTerraco();
        int raio = planta.terracoRaio();

        Material pisoM = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int du = -raio; du <= raio; du++) {
            for (int dv = -raio; dv <= raio; dv++) {
                int distancia = (int) Math.round(Math.sqrt(du * du + dv * dv));
                if (distancia > raio) {
                    continue;
                }

                int u = centroU + du;
                int v = centroV + dv;

                por(mundo, u, v, piso - 2, borda);
                por(mundo, u, v, piso - 1, estrutura);
                por(mundo, u, v, piso, distancia >= raio - 1 ? borda
                        : distancia % 4 == 0 ? portal.destaque() : pisoM);

                if (distancia == raio && !olhandoParaOCentro(face, du, dv)) {
                    laje(mundo, u, v, piso + 1, planta.cor("parapeito", Material.QUARTZ_SLAB));
                }
            }
        }
    }

    /** O lado do terraço por onde a passarela chega fica aberto. */
    private boolean olhandoParaOCentro(HubCatalog.Face face, int du, int dv) {
        return du * face.du() + dv * face.dv() < 0;
    }

    // ------------------------------------------------------------------
    //  Arco, escultura e painel
    // ------------------------------------------------------------------

    /**
     * O arco por onde se passa.
     *
     * O vão fica VAZIO: a cortina de partículas é desenhada pelo HubPortais,
     * e não por bloco. Bloco de portal do Nether aqui dentro levaria o
     * jogador para o Nether de verdade na primeira vez que alguém esbarrasse
     * nele — e ninguém desconfiaria do hub.
     */
    private void arcoDoPortal(World mundo, HubCatalog planta, HubCatalog.Portal portal,
                              int piso) {
        HubCatalog.Face face = portal.face();
        int centro = planta.distanciaDoTerraco();
        Material luz = planta.cor("luz", Material.SEA_LANTERN);

        for (int lado = -3; lado <= 3; lado++) {
            for (int altura = 0; altura <= 8; altura++) {
                boolean pilar = Math.abs(lado) == 3;
                boolean verga = altura >= 7;
                boolean quina = Math.abs(lado) == 3 && altura >= 7;

                if (!pilar && !verga) {
                    continue;
                }

                Material material = quina ? portal.destaque()
                        : (altura % 3 == 1 && pilar) ? luz : portal.moldura();

                int u = face.du() * centro + face.lu() * lado;
                int v = face.dv() * centro + face.lv() * lado;
                por(mundo, u, v, piso + 1 + altura, material);
            }
        }

        // Soleira: a linha no chão que marca onde o portal começa.
        for (int lado = -2; lado <= 2; lado++) {
            int u = face.du() * centro + face.lu() * lado;
            int v = face.dv() * centro + face.lv() * lado;
            por(mundo, u, v, piso, portal.destaque());
        }
    }

    /**
     * A escultura: um desenho do hub.yml carimbado em pé, de frente para quem
     * chega.
     *
     * É o que faz o hub funcionar sem legenda. Uma picareta de quinze blocos
     * diz "survival" para qualquer pessoa, em qualquer idioma, antes de ela
     * chegar perto o bastante para ler o painel.
     */
    private void esculturaDoPortal(World mundo, HubCatalog planta, HubCatalog.Portal portal,
                                   int piso) {
        List<String> desenho = planta.escultura(portal.escultura());
        if (!HubCatalog.desenhoEhRetangular(desenho)) {
            if (!desenho.isEmpty()) {
                plugin.getLogger().warning("[hub] O desenho '" + portal.escultura()
                        + "' tem linhas de tamanhos diferentes — escultura não construída.");
            }
            return;
        }

        HubCatalog.Face face = portal.face();
        int distancia = planta.distanciaDoTerraco() + 6;
        int largura = desenho.get(0).length();
        int meia = largura / 2;
        int base = piso + 3;

        Material luz = planta.cor("luz-fria", Material.END_ROD);

        for (int linha = 0; linha < desenho.size(); linha++) {
            int y = base + (desenho.size() - 1 - linha);
            String texto = desenho.get(linha);

            for (int coluna = 0; coluna < largura; coluna++) {
                Material material = switch (texto.charAt(coluna)) {
                    case '#' -> portal.moldura();
                    case 'o' -> portal.destaque();
                    case '*' -> luz;
                    default -> null;
                };
                if (material == null) {
                    continue;
                }

                int lado = coluna - meia;
                // Duas espessuras: de lado, uma escultura de um bloco some.
                for (int fundo = 0; fundo <= 1; fundo++) {
                    int u = face.du() * (distancia + fundo) + face.lu() * lado;
                    int v = face.dv() * (distancia + fundo) + face.lv() * lado;
                    por(mundo, u, v, y, material);
                }
            }
        }

        // Pedestal, para a peça não nascer do chão sem apoio.
        for (int lado = -meia - 1; lado <= meia + 1; lado++) {
            for (int fundo = -1; fundo <= 2; fundo++) {
                int u = face.du() * (distancia + fundo) + face.lu() * lado;
                int v = face.dv() * (distancia + fundo) + face.lv() * lado;
                por(mundo, u, v, piso + 1, planta.cor("estrutura-escura", Material.BLACK_CONCRETE));
                por(mundo, u, v, piso + 2, portal.moldura());
            }
        }
    }

    private void painelDoPortal(World mundo, HubCatalog planta, HubCatalog.Portal portal,
                                int piso) {
        HubCatalog.Face face = portal.face();
        int centro = planta.distanciaDoTerraco();

        String aviso = portal.aberto() ? "" : "<newline><color:#FF6A6A>em obra, ainda não abre</color>";
        Component texto = plugin.mini().deserialize(
                "<color:" + portal.cor() + "><bold>" + escapar(portal.titulo()) + "</bold></color>"
                        + "<newline><gray>" + escapar(portal.descricao()) + "</gray>"
                        + "<newline><dark_gray>" + escapar(portal.detalhe()) + "</dark_gray>"
                        + aviso);

        Location alvo = new Location(mundo,
                face.du() * centro + 0.5, piso + 11.5, face.dv() * centro + 0.5);
        alvo.setYaw(face.yawParaOCentro() + 180f);
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, 1.6f);
    }

    /**
     * Texto de arquivo não pode virar formatação.
     *
     * O hub.yml é editado à mão; um título com < ou > passaria a ser lido
     * como tag de cor e o painel sairia pela metade, sem erro no console.
     */
    static String escapar(String texto) {
        return texto == null ? "" : texto.replace("<", "").replace(">", "");
    }

    // ------------------------------------------------------------------
    //  Mirante e pouso
    // ------------------------------------------------------------------

    /** A face que sobrou vira varanda: o hub fica simétrico e tem onde parar. */
    private void mirante(World mundo, HubCatalog planta, List<HubCatalog.Portal> portais,
                         int piso) {
        for (HubCatalog.Face face : HubCatalog.Face.values()) {
            boolean usada = portais.stream().anyMatch(portal -> portal.face() == face);
            if (usada) {
                continue;
            }

            int meia = planta.passarelaLargura() / 2;
            Material pisoM = planta.cor("piso", Material.SMOOTH_QUARTZ);
            Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
            Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

            for (int passo = planta.raio() - 1; passo <= planta.raio() + 6; passo++) {
                for (int lado = -meia; lado <= meia; lado++) {
                    int u = face.du() * passo + face.lu() * lado;
                    int v = face.dv() * passo + face.lv() * lado;
                    por(mundo, u, v, piso - 1, estrutura);
                    por(mundo, u, v, piso, Math.abs(lado) == meia ? borda : pisoM);
                    if (Math.abs(lado) == meia || passo == planta.raio() + 6) {
                        laje(mundo, u, v, piso + 1,
                                planta.cor("parapeito", Material.QUARTZ_SLAB));
                    }
                }
            }
        }
    }

    /** Onde o jogador nasce: na praça, de frente para a torre. */
    private void pousoDoJogador(World mundo, HubCatalog planta, int piso) {
        Location pouso = pontoDeSpawn(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());
    }

    /** O ponto de pouso do hub, usado pelo spawn e pelo resgate do void. */
    public static Location pontoDeSpawn(World mundo, HubCatalog planta) {
        Location pouso = new Location(mundo, 0.5, planta.pisoY() + 1,
                planta.raio() - 5 + 0.5);
        pouso.setYaw(180f);
        pouso.setPitch(0f);
        return pouso;
    }

    // ------------------------------------------------------------------
    //  Ferramentas
    // ------------------------------------------------------------------

    private int contador;

    private void por(World mundo, int u, int v, int y, Material material) {
        mundo.getBlockAt(u, y, v).setType(material, false);
        contador++;
    }

    /** Laje de parapeito, sempre na metade de baixo. */
    private void laje(World mundo, int u, int v, int y, Material material) {
        Block bloco = mundo.getBlockAt(u, y, v);
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

    private void painel(World mundo, Location alvo, Component texto, float escala) {
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(Display.Billboard.FIXED);
            novo.setAlignment(TextDisplay.TextAlignment.CENTER);
            novo.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(380);
            novo.setViewRange(2.0f);
            novo.setPersistent(true);
            novo.addScoreboardTag(TAG_PAINEL);

            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(escala, escala, escala), t.getRightRotation()));
        });
    }

    private void limparPaineis(World mundo) {
        for (Entity entidade : mundo.getEntities()) {
            if (entidade.getScoreboardTags().contains(TAG_PAINEL)) {
                entidade.remove();
            }
        }
    }

    /** Quantos blocos já existem onde o hub iria. Zero é o esperado. */
    private int contarOcupados(World mundo, HubCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        int achados = 0;

        for (int u = -alcance; u <= alcance; u++) {
            for (int v = -alcance; v <= alcance; v++) {
                for (int y = piso - 2; y <= piso + planta.torreAltura() + 5; y++) {
                    if (!mundo.getBlockAt(u, y, v).getType().isAir()) {
                        achados++;
                    }
                }
            }
        }
        return achados;
    }
}
