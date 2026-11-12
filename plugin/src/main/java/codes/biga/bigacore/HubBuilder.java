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
 * Levanta a praça de portais — o hub do servidor.
 *
 * <h2>O que faz uma construção parecer futurista</h2>
 *
 * Não é o bloco: é a SUPERFÍCIE. Futurista no Minecraft sai de muita área
 * lisa e clara, pouquíssima cor, e a cor que existe sendo LUZ e não tinta —
 * quartzo, concreto, vidro e lanterna. Um único bloco de madeira ou de pedra
 * bruta derruba o efeito inteiro, e por isso a paleta do hub.yml não tem
 * nenhum dos dois. A cor dos portais entra como FAIXA, nunca como parede:
 * branco manda em tudo e o verde, o laranja e o roxo só marcam caminho.
 *
 * <h2>Por que o meio da praça é vazio</h2>
 *
 * A primeira versão tinha uma torre no centro. Ela tapava os três caminhos de
 * uma vez e, de onde o jogador nascia, o hub inteiro virava um corredor entre
 * a torre e o parapeito. O vertical agora é o ANEL suspenso: ele fecha a
 * composição no alto, se vê de qualquer ponto, e o chão continua livre.
 *
 * O único volume no chão é o monumento — a parede que segura o paredão de
 * ajuda, de frente para quem nasce. Ela é larga, baixa e fica num plinto
 * redondo com nove blocos de passagem de cada lado.
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
                    + (planta.alcanceDaObra() * 2) + " blocos. Monumento: "
                    + monumentoMeiaLargura(planta) * 2 + "x" + monumentoAltura(planta) + ".", 0);
        }

        int piso = planta.pisoY();
        contador = 0;

        limparPaineis(mundo);
        if (plugin.painelAjuda() != null) {
            plugin.painelAjuda().limpar(mundo);
        }

        HubCatalog.Face chegada = HubCatalog.faceLivre(portais);
        List<HubCatalog.Face> caminhos = new ArrayList<>();
        for (HubCatalog.Portal portal : portais) {
            caminhos.add(portal.face());
        }
        caminhos.add(chegada);

        plataforma(mundo, planta, 0, 0, planta.raio(), 2, piso);
        pisoDaPraca(mundo, planta, portais, chegada, piso);
        parapeito(mundo, planta, 0, 0, planta.raio(), 2, caminhos, piso);
        colunata(mundo, planta, piso);
        monumento(mundo, planta, piso);
        anelSuspenso(mundo, planta, piso);

        for (HubCatalog.Portal portal : portais) {
            passarela(mundo, planta, portal.face(), portal.destaque(), piso);
            terraco(mundo, planta, portal.face(), portal.destaque(), piso);
            portao(mundo, planta, portal, piso);
            esculturaDoPortal(mundo, planta, portal, piso);
            painelDoPortal(mundo, planta, portal, piso);
        }

        passarela(mundo, planta, chegada, planta.cor("piso-linha",
                Material.LIGHT_BLUE_CONCRETE), piso);
        terraco(mundo, planta, chegada, planta.cor("piso-linha",
                Material.LIGHT_BLUE_CONCRETE), piso);
        terracoDaChegada(mundo, planta, chegada, piso);
        pousoDoJogador(mundo, planta, piso);
        RegrasDoHub.ajustarMundo(mundo, planta);

        int blocos = contador;
        String ajuda = paredeDeAjuda(mundo, planta);

        return new Resultado(true, "Hub levantado: " + blocos + " blocos, "
                + portais.size() + " portais, chegada ao " + chegada + ". " + ajuda, blocos);
    }

    /** Apaga tudo dentro do alcance da obra. Só serve em mundo de hub. */
    public Resultado desfazer(World mundo) {
        HubCatalog planta = plugin.hubCatalog();
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        int apagados = 0;

        for (int u = -alcance; u <= alcance; u++) {
            for (int v = -alcance; v <= alcance; v++) {
                for (int y = piso - 8; y <= piso + planta.alturaDaObra(); y++) {
                    Block bloco = mundo.getBlockAt(u, y, v);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        apagados++;
                    }
                }
            }
        }

        limparPaineis(mundo);
        if (plugin.painelAjuda() != null) {
            plugin.painelAjuda().limpar(mundo);
        }
        return new Resultado(true, "Hub removido: " + apagados + " blocos apagados.", apagados);
    }

    // ------------------------------------------------------------------
    //  A plataforma
    // ------------------------------------------------------------------

    /**
     * Quanto cada camada de baixo encolhe em relação à borda do piso.
     *
     * Um disco de três blocos de espessura boiando no void lê como folha de
     * papel grossa — e é de baixo que se chega voando. Os degraus crescendo
     * para dentro fazem a plataforma virar um cone truncado: de longe parece
     * peso, de perto some.
     */
    static final int[] DEGRAUS = {0, 1, 3, 6, 10, 15, 21};

    /** O raio da camada {@code k} abaixo do piso. Negativo = camada não existe. */
    static int raioDaCamada(int k, int raio) {
        if (k < 0 || k >= DEGRAUS.length) {
            return -1;
        }
        return raio - DEGRAUS[k];
    }

    /**
     * O anel em que (du, dv) cai.
     *
     * @param expoente 2 desenha um círculo; 4 desenha um quadrado de cantos
     *                 arredondados, que é o que segura parede larga perto da
     *                 borda sem o canto ficar no ar.
     */
    static int anel(int du, int dv, int expoente) {
        if (expoente == 2) {
            return (int) Math.round(Math.sqrt((double) du * du + (double) dv * dv));
        }
        double soma = Math.pow(Math.abs(du), expoente) + Math.pow(Math.abs(dv), expoente);
        return (int) Math.round(Math.pow(soma, 1.0 / expoente));
    }

    /** Meia largura disponível a {@code du} do centro de um terraço. */
    static int meiaLarguraEm(int du, int raio, int expoente) {
        double resto = Math.pow(raio, expoente) - Math.pow(Math.abs(du), expoente);
        if (resto <= 0) {
            return 0;
        }
        return (int) Math.floor(Math.pow(resto, 1.0 / expoente));
    }

    private void plataforma(World mundo, HubCatalog planta, int cu, int cv, int raio,
                            int expoente, int piso) {
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int k = 0; k < DEGRAUS.length; k++) {
            int r = raioDaCamada(k, raio);
            if (r < 2) {
                break;
            }
            for (int du = -r; du <= r; du++) {
                for (int dv = -r; dv <= r; dv++) {
                    int d = anel(du, dv, expoente);
                    if (d > r) {
                        continue;
                    }
                    // Só a casca interessa nas camadas fundas: miolo maciço
                    // a sete blocos de profundidade é bloco que ninguém vê.
                    if (k >= 2 && d < r - 1) {
                        continue;
                    }
                    por(mundo, cu + du, cv + dv, piso - 1 - k, d == r ? borda : estrutura);
                }
            }
        }
    }

    /** Parapeito na borda, menos onde um caminho sai. Quem cai, cai no void. */
    private void parapeito(World mundo, HubCatalog planta, int cu, int cv, int raio,
                           int expoente, List<HubCatalog.Face> caminhos, int piso) {
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);
        int meia = planta.passarelaLargura() / 2;

        for (int du = -raio; du <= raio; du++) {
            for (int dv = -raio; dv <= raio; dv++) {
                if (anel(du, dv, expoente) != raio) {
                    continue;
                }
                if (ehBocaDeCaminho(caminhos, meia, cu + du, cv + dv)) {
                    continue;
                }
                laje(mundo, cu + du, cv + dv, piso + 1, laje);
            }
        }
    }

    private boolean ehBocaDeCaminho(List<HubCatalog.Face> caminhos, int meia, int u, int v) {
        for (HubCatalog.Face face : caminhos) {
            int aoLongo = u * face.du() + v * face.dv();
            int atravessado = u * face.lu() + v * face.lv();
            if (aoLongo > 0 && Math.abs(atravessado) <= meia) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  O chão da praça
    // ------------------------------------------------------------------

    /** Raio do plinto redondo em que o monumento se apoia. */
    private int raioDoPlinto(HubCatalog planta) {
        return monumentoMeiaLargura(planta) + 3;
    }

    /**
     * Branco liso, uma linha de luz em volta do monumento, e uma faixa da cor
     * de cada mundo saindo do plinto até a boca da passarela.
     *
     * A faixa é a única sinalização que funciona de longe: o jogador segue a
     * cor antes de chegar perto o bastante para ler qualquer painel. Os anéis
     * concêntricos da primeira versão faziam o contrário — de cima viravam um
     * alvo de dardos, e o olho não achava mais o caminho.
     */
    private void pisoDaPraca(World mundo, HubCatalog planta, List<HubCatalog.Portal> portais,
                             HubCatalog.Face chegada, int piso) {
        int raio = planta.raio();
        int plinto = raioDoPlinto(planta);

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material linha = planta.cor("piso-linha", Material.LIGHT_BLUE_CONCRETE);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        for (int u = -raio; u <= raio; u++) {
            for (int v = -raio; v <= raio; v++) {
                int d = anel(u, v, 2);
                if (d > raio) {
                    continue;
                }
                Material material = claro;
                if (d >= raio - 1) {
                    material = borda;
                } else if (d == plinto) {
                    material = linha;
                }
                por(mundo, u, v, piso, material);
            }
        }

        // As faixas. Cada uma sai do plinto e vai até a passarela; as linhas
        // escuras de fora dela separam um caminho do outro no chão.
        Material escuro = planta.cor("piso-escuro", Material.POLISHED_DEEPSLATE);
        for (HubCatalog.Portal portal : portais) {
            faixa(mundo, portal.face(), portal.destaque(), escuro, plinto + 1, raio - 2, piso);
        }
        faixa(mundo, chegada, linha, escuro, plinto + 1, raio - 2, piso);
    }

    private void faixa(World mundo, HubCatalog.Face face, Material cor, Material escuro,
                       int de, int ate, int piso) {
        for (int passo = de; passo <= ate; passo++) {
            por(mundo, face.du() * passo, face.dv() * passo, piso, cor);
            for (int lado : new int[]{-2, 2}) {
                por(mundo, face.du() * passo + face.lu() * lado,
                        face.dv() * passo + face.lv() * lado, piso, escuro);
            }
        }
    }

    /**
     * Oito mastros de luz em volta da praça.
     *
     * Ficam a 22,5 graus dos eixos de propósito: na malha antiga eles caíam
     * em cima da boca das passarelas, e um pilar no meio do caminho é o tipo
     * de erro que só aparece quando alguém tenta andar.
     */
    private void colunata(World mundo, HubCatalog planta, int piso) {
        int raio = planta.raio() - 2;
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);

        for (int i = 0; i < 8; i++) {
            double angulo = Math.PI / 8.0 + Math.PI * i / 4.0;
            int u = (int) Math.round(Math.cos(angulo) * raio);
            int v = (int) Math.round(Math.sin(angulo) * raio);

            for (int y = piso + 1; y <= piso + 10; y++) {
                por(mundo, u, v, y, (y - piso) % 4 == 0 ? vidro : estrutura);
            }
            por(mundo, u, v, piso + 11, luz);
            for (int du = -1; du <= 1; du++) {
                for (int dv = -1; dv <= 1; dv++) {
                    if (du == 0 && dv == 0) {
                        continue;
                    }
                    laje(mundo, u + du, v + dv, piso + 12, laje);
                }
            }
            por(mundo, u, v, piso + 12, planta.cor("luz-fria", Material.END_ROD));
        }
    }

    // ------------------------------------------------------------------
    //  O monumento — a parede do paredão de ajuda
    // ------------------------------------------------------------------

    /**
     * O tamanho real do paredão de ajuda, medido no ajuda.yml.
     *
     * É daqui que sai o tamanho do monumento. Número fixo na parede quebra
     * calado: basta alguém acrescentar um painel ao ajuda.yml e a última
     * coluna passa a flutuar fora da pedra, contra o void.
     */
    private PainelAjuda.Parede parede() {
        PainelAjuda ajuda = plugin.painelAjuda();
        return ajuda == null ? new PainelAjuda.Parede(0, 0, 11) : ajuda.tamanhoDaParede();
    }

    /** Metade da largura do monumento, em blocos. */
    private int monumentoMeiaLargura(HubCatalog planta) {
        return meiaLarguraDoMonumento(parede().largura(),
                planta.monumentoMeiaLarguraMinima());
    }

    private int monumentoAltura(HubCatalog planta) {
        return alturaDoMonumento(parede().altura(), planta.monumentoAlturaMinima());
    }

    /** Dois blocos de folga de cada lado do texto, e nunca menos que o mínimo. */
    static int meiaLarguraDoMonumento(double larguraDaParede, int minimo) {
        return Math.max(minimo, (int) Math.ceil(larguraDaParede / 2.0) + 2);
    }

    /** Três blocos de folga acima da última linha. */
    static int alturaDoMonumento(double alturaDaParede, int minimo) {
        return Math.max(minimo, (int) Math.ceil(alturaDaParede) + 3);
    }

    private void monumento(World mundo, HubCatalog planta, int piso) {
        int meia = monumentoMeiaLargura(planta);
        int altura = monumentoAltura(planta);
        int fundo = Math.max(1, planta.monumentoEspessura() / 2);

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material metal = planta.cor("metal", Material.IRON_BLOCK);
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);

        for (int u = -meia; u <= meia; u++) {
            for (int v = -fundo; v <= fundo; v++) {
                for (int y = piso + 1; y <= piso + altura; y++) {
                    Material material;
                    if (y == piso + 1) {
                        material = borda;
                    } else if (Math.abs(u) >= meia - 1) {
                        // Os dois montantes das pontas: dão espessura à peça
                        // e param o olho antes do texto começar.
                        material = estrutura;
                    } else if (Math.abs(u) == meia - 2) {
                        material = (y - piso) % 2 == 0 ? luz : vidro;
                    } else if (y == piso + altura) {
                        material = estrutura;
                    } else {
                        material = claro;
                    }
                    por(mundo, u, v, y, material);
                }
            }
        }

        // Beiral: uma fiada que avança um bloco para cada lado. É o que
        // transforma a parede em peça, e não em pedaço de muro cortado.
        for (int u = -meia - 1; u <= meia + 1; u++) {
            for (int v = -fundo - 1; v <= fundo + 1; v++) {
                laje(mundo, u, v, piso + altura + 1, laje);
            }
        }

        // O farol no topo. A pirâmide de um nível cabe dentro da própria
        // parede, então o feixe sobe do monumento sem nenhuma torre embaixo.
        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                por(mundo, u, v, piso + altura, metal);
            }
        }
        por(mundo, 0, 0, piso + altura + 1, Material.BEACON);

        letreiroDoMonumento(mundo, planta, piso + altura + 4);
    }

    /**
     * O nome do servidor no alto do monumento.
     *
     * Uma cópia só, girando no eixo vertical. A versão anterior carimbava
     * quatro textos FIXED, um por lado, porque texto fixo só se lê de
     * frente — e quem chegasse na diagonal via os quatro de esguelha ao
     * mesmo tempo, que é o que faz aquele borrão no meio da praça.
     */
    private void letreiroDoMonumento(World mundo, HubCatalog planta, int y) {
        Component texto = plugin.mini().deserialize(
                "<gradient:#FFC46B:#FFD700:#FFC46B><bold>THE BIGAS</bold></gradient>"
                        + "<newline><color:#9FD8FF>escolha um caminho</color>");

        Location alvo = new Location(mundo, 0.5, y, 0.5);
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, TITULO);
    }

    /**
     * Levanta o paredão de ajuda de frente para o monumento.
     *
     * O jogador nasce no terraço de chegada olhando exatamente para esta
     * parede: o primeiro texto que ele lê no servidor é o "COMO COMEÇAR".
     */
    private String paredeDeAjuda(World mundo, HubCatalog planta) {
        PainelAjuda ajuda = plugin.painelAjuda();
        if (ajuda == null) {
            return "";
        }
        PainelAjuda.Relatorio r = ajuda.construir(mundo, baseDoPainel(mundo, planta), true);
        return r.ok() ? "Paredão de ajuda: " + r.criados() + " painéis."
                : "Paredão de ajuda NÃO subiu: " + r.erro();
    }

    /**
     * De onde o paredão é medido: em frente à face do monumento que olha para
     * a chegada, recuado da distância que o próprio ajuda.yml pede.
     */
    private Location baseDoPainel(World mundo, HubCatalog planta) {
        double distancia = parede().distancia();
        int frente = Math.max(1, planta.monumentoEspessura() / 2) + 1;

        HubCatalog.Face chegada = HubCatalog.faceLivre(planta.portais());
        Location base = new Location(mundo,
                chegada.du() * (frente + distancia) + 0.5, planta.pisoY() + 1,
                chegada.dv() * (frente + distancia) + 0.5);
        base.setYaw(chegada.yawParaOCentro());
        base.setPitch(0f);
        return base;
    }

    // ------------------------------------------------------------------
    //  O anel suspenso
    // ------------------------------------------------------------------

    /**
     * O raio do arco naquela altura.
     *
     * Cosseno e não reta: a curva sai vertical do chão e só deita perto do
     * topo, que é como um arcobotante se apoia. Reta pura lê como rampa.
     */
    static double raioDoArco(int y, int alturaAnel, int raioAnel, int raioBase) {
        if (alturaAnel <= 1) {
            return raioAnel;
        }
        double t = Math.min(1.0, Math.max(0.0, (y - 1.0) / (alturaAnel - 1.0)));
        return raioAnel + (raioBase - raioAnel) * Math.cos(t * Math.PI / 2.0);
    }

    /**
     * O anel que flutua sobre a praça, e os quatro arcos que o seguram.
     *
     * Este é o "colossal" do hub: ele fecha a composição no alto, se vê de
     * qualquer canto do mundo, e não custa um metro quadrado de chão. Os
     * arcos sobem nas diagonais justamente para não cruzar nenhum caminho.
     */
    private void anelSuspenso(World mundo, HubCatalog planta, int piso) {
        int alturaAnel = planta.anelAltura();
        int raioAnel = planta.anelRaio();
        // O pé do arco encosta no parapeito de propósito: a quatro blocos
        // para dentro ele caía bem na faixa por onde se contorna o
        // monumento, e um contraforte de três por três no meio da única
        // passagem é obstáculo, não arquitetura.
        int raioBase = planta.raio() - 1;
        int topo = piso + alturaAnel;

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material vidro = planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        for (int u = -raioAnel; u <= raioAnel; u++) {
            for (int v = -raioAnel; v <= raioAnel; v++) {
                int d = anel(u, v, 2);
                if (d != raioAnel && d != raioAnel - 1) {
                    continue;
                }
                por(mundo, u, v, topo, estrutura);
                por(mundo, u, v, topo + 1, d == raioAnel ? estrutura : luz);
                por(mundo, u, v, topo + 2, estrutura);
                if (d == raioAnel - 1 && (Math.abs(u) + Math.abs(v)) % 3 == 0) {
                    por(mundo, u, v, topo - 1, rod);
                }
            }
        }

        for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
                for (int y = 1; y <= alturaAnel; y++) {
                    double r = raioDoArco(y, alturaAnel, raioAnel, raioBase);
                    int cu = (int) Math.round(su * r / Math.sqrt(2.0));
                    int cv = (int) Math.round(sv * r / Math.sqrt(2.0));

                    Material material = y % 8 == 0 ? luz : y % 4 == 0 ? vidro : estrutura;
                    for (int du = -1; du <= 1; du++) {
                        for (int dv = -1; dv <= 1; dv++) {
                            por(mundo, cu + du, cv + dv, piso + y, material);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Caminho até o portal
    // ------------------------------------------------------------------

    private void passarela(World mundo, HubCatalog planta, HubCatalog.Face face,
                           Material destaque, int piso) {
        int meia = planta.passarelaLargura() / 2;

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);

        for (int passo = planta.raio() - 2; passo <= planta.distanciaDoTerraco(); passo++) {
            for (int lado = -meia; lado <= meia; lado++) {
                int u = face.du() * passo + face.lu() * lado;
                int v = face.dv() * passo + face.lv() * lado;

                por(mundo, u, v, piso - 2, borda);
                por(mundo, u, v, piso - 1, estrutura);
                por(mundo, u, v, piso, lado == 0 ? destaque
                        : Math.abs(lado) == meia ? borda : claro);

                if (Math.abs(lado) == meia) {
                    laje(mundo, u, v, piso + 1, laje);
                }
            }

            // Arcada: um pórtico leve a cada seis passos. É o que dá ritmo e
            // comprimento à passarela — sem ele são noventa metros de chão.
            if (passo % 6 == 0) {
                arcadaDaPassarela(mundo, planta, face, destaque, passo, meia, piso);
            }
        }
    }

    private void arcadaDaPassarela(World mundo, HubCatalog planta, HubCatalog.Face face,
                                   Material destaque, int passo, int meia, int piso) {
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);

        for (int lado : new int[]{-meia, meia}) {
            int u = face.du() * passo + face.lu() * lado;
            int v = face.dv() * passo + face.lv() * lado;
            for (int y = piso + 1; y <= piso + 5; y++) {
                por(mundo, u, v, y, estrutura);
            }
            por(mundo, u, v, piso + 6, destaque);
            por(mundo, u, v, piso + 7, rod);

            int dentro = lado > 0 ? lado - 1 : lado + 1;
            laje(mundo, face.du() * passo + face.lu() * dentro,
                    face.dv() * passo + face.lv() * dentro, piso + 6, laje);
        }
    }

    private void terraco(World mundo, HubCatalog planta, HubCatalog.Face face,
                         Material destaque, int piso) {
        int centroU = face.du() * planta.distanciaDoTerraco();
        int centroV = face.dv() * planta.distanciaDoTerraco();
        int raio = planta.terracoRaio();

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);

        plataforma(mundo, planta, centroU, centroV, raio, 4, piso);

        for (int du = -raio; du <= raio; du++) {
            for (int dv = -raio; dv <= raio; dv++) {
                int d = anel(du, dv, 4);
                if (d > raio) {
                    continue;
                }
                int u = centroU + du;
                int v = centroV + dv;
                por(mundo, u, v, piso, d >= raio - 1 ? borda
                        : d == raio - 4 ? destaque : claro);

                if (d == raio && !olhandoParaOCentro(face, du, dv)) {
                    laje(mundo, u, v, piso + 1,
                            planta.cor("parapeito", Material.QUARTZ_SLAB));
                }
            }
        }
    }

    /** O lado do terraço por onde a passarela chega fica aberto. */
    private boolean olhandoParaOCentro(HubCatalog.Face face, int du, int dv) {
        return du * face.du() + dv * face.dv() < 0;
    }

    // ------------------------------------------------------------------
    //  Portão, escultura e painel
    // ------------------------------------------------------------------

    /**
     * O portão por onde se passa.
     *
     * O vão fica VAZIO: a cortina de partículas é desenhada pelo HubPortais,
     * e não por bloco. Bloco de portal do Nether aqui dentro levaria o
     * jogador para o Nether de verdade na primeira vez que alguém esbarrasse
     * nele — e ninguém desconfiaria do hub.
     *
     * O corpo é branco e a cor do mundo entra só na faixa: um portão inteiro
     * de concreto colorido some contra a escultura, que é a peça que precisa
     * ser vista de longe.
     */
    private void portao(World mundo, HubCatalog planta, HubCatalog.Portal portal, int piso) {
        HubCatalog.Face face = portal.face();
        int centro = planta.distanciaDoPortal();
        int vao = planta.portaoVao();
        int pilar = planta.portaoPilar();
        int altura = planta.portaoAltura();
        int meia = vao + pilar;

        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);

        for (int lado = -meia; lado <= meia; lado++) {
            boolean montante = Math.abs(lado) > vao;
            for (int fundo = -1; fundo <= 1; fundo++) {
                for (int y = 1; y <= altura; y++) {
                    boolean verga = y > altura - 4;
                    if (!montante && !verga) {
                        continue;
                    }

                    Material material;
                    if (Math.abs(lado) == vao + 1 && !verga) {
                        // A aresta do vão é a única faixa de cor do portão.
                        material = y % 3 == 1 ? luz : portal.destaque();
                    } else if (y == altura - 2) {
                        material = portal.destaque();
                    } else if (y == altura) {
                        material = portal.moldura();
                    } else {
                        material = montante && Math.abs(fundo) == 1 ? claro : estrutura;
                    }

                    por(mundo, face.du() * centro + face.lu() * lado + face.du() * fundo,
                            face.dv() * centro + face.lv() * lado + face.dv() * fundo,
                            piso + y, material);
                }
            }
        }

        for (int lado : new int[]{-meia, meia}) {
            por(mundo, face.du() * centro + face.lu() * lado,
                    face.dv() * centro + face.lv() * lado, piso + altura + 1, rod);
        }

        // Soleira: a linha no chão que marca onde o portal começa.
        for (int lado = -vao; lado <= vao; lado++) {
            for (int fundo = -1; fundo <= 1; fundo++) {
                por(mundo, face.du() * (centro + fundo) + face.lu() * lado,
                        face.dv() * (centro + fundo) + face.lv() * lado,
                        piso, portal.destaque());
            }
        }
    }

    /**
     * A escultura: um desenho do hub.yml carimbado em pé, numa parede branca
     * de fundo, de frente para quem chega.
     *
     * É o que faz o hub funcionar sem legenda. Uma picareta de dezessete
     * blocos diz "survival" para qualquer pessoa, em qualquer idioma, antes
     * de ela chegar perto o bastante para ler o painel. O fundo branco não é
     * enfeite: sem ele a peça colorida fica recortada contra o void e some
     * de dia.
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
        int distancia = planta.distanciaDoPortal() + planta.esculturaAvanco();
        int largura = desenho.get(0).length();
        int meia = largura / 2;
        int base = piso + 3;
        int topo = base + desenho.size() - 1;

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material borda = planta.cor("borda", Material.DEEPSLATE_TILES);
        Material luz = planta.cor("luz-fria", Material.END_ROD);

        // A parede de fundo, duas fiadas atrás do desenho.
        for (int lado = -meia - 2; lado <= meia + 2; lado++) {
            for (int y = piso + 1; y <= topo + 2; y++) {
                Material material = y == piso + 1 || y == topo + 2
                        || Math.abs(lado) > meia + 1 ? borda
                        : Math.abs(lado) == meia + 1 ? estrutura : claro;
                for (int fundo = 1; fundo <= 2; fundo++) {
                    por(mundo, face.du() * (distancia + fundo) + face.lu() * lado,
                            face.dv() * (distancia + fundo) + face.lv() * lado, y, material);
                }
            }
        }

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
                for (int fundo = -1; fundo <= 0; fundo++) {
                    int u = face.du() * (distancia + fundo) + face.lu() * lado;
                    int v = face.dv() * (distancia + fundo) + face.lv() * lado;
                    por(mundo, u, v, y, material);
                }
            }
        }

        // Pedestal, para a peça não nascer do chão sem apoio.
        for (int lado = -meia - 2; lado <= meia + 2; lado++) {
            for (int fundo = -2; fundo <= 2; fundo++) {
                int u = face.du() * (distancia + fundo) + face.lu() * lado;
                int v = face.dv() * (distancia + fundo) + face.lv() * lado;
                por(mundo, u, v, piso + 1, borda);
                por(mundo, u, v, piso + 2, estrutura);
            }
        }
    }

    /**
     * A placa do portal, sobre a verga do portão.
     *
     * Lugar de placa é em cima da porta. Na altura dos olhos, ao lado do
     * caminho, ela competia com a escultura e com o próprio portão — e de
     * longe, que é de onde a pessoa escolhe para onde ir, virava um
     * borrãozinho colorido na beira da tela. Daqui ela é lida da passarela
     * inteira, e quem chega perto já não precisa dela: tem a escultura.
     *
     * Olha para o CENTRO, porque é de lá que o jogador vem. Virada para fora
     * ela só seria legível por quem já atravessou — ou seja, por ninguém.
     */
    private void painelDoPortal(World mundo, HubCatalog planta, HubCatalog.Portal portal,
                                int piso) {
        HubCatalog.Face face = portal.face();
        int centro = planta.distanciaDoPortal();

        String aviso = portal.aberto()
                ? "<newline><dark_gray>" + escapar(portal.detalhe()) + "</dark_gray>"
                : "<newline><color:#FF8A8A>em obra · ainda não abre</color>";

        Component texto = plugin.mini().deserialize(
                "<color:" + portal.cor() + "><bold>" + escapar(portal.titulo())
                        + "</bold></color>"
                        + "<newline><gray>" + escapar(portal.descricao()) + "</gray>"
                        + aviso);

        Location alvo = new Location(mundo,
                face.du() * centro + 0.5, piso + planta.portaoAltura() + 3.5,
                face.dv() * centro + 0.5);
        alvo.setYaw(face.yawParaOCentro());
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, PLACA);
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
    //  Chegada
    // ------------------------------------------------------------------

    /**
     * O terraço em que o jogador nasce.
     *
     * Fica ABERTO: sem teto, sem parede, sem torre na frente. A primeira
     * versão nascia dentro da praça, encostada na torre, e a queixa foi
     * exatamente essa — "onde os players nascem tá fechado". Daqui se vê, em
     * linha reta, a passarela, o monumento, o feixe e o anel.
     */
    private void terracoDaChegada(World mundo, HubCatalog planta, HubCatalog.Face face,
                                  int piso) {
        int centroU = face.du() * planta.distanciaDoTerraco();
        int centroV = face.dv() * planta.distanciaDoTerraco();

        Material claro = planta.cor("piso", Material.SMOOTH_QUARTZ);
        Material linha = planta.cor("piso-linha", Material.LIGHT_BLUE_CONCRETE);
        Material estrutura = planta.cor("estrutura", Material.QUARTZ_BLOCK);
        Material luz = planta.cor("luz", Material.SEA_LANTERN);
        Material laje = planta.cor("parapeito", Material.QUARTZ_SLAB);

        // O estrado de onde ele nasce: um degrau só, para o olho dele passar
        // por cima do parapeito e achar o monumento na primeira volta.
        for (int du = -5; du <= 5; du++) {
            for (int dv = -5; dv <= 5; dv++) {
                int d = anel(du, dv, 2);
                if (d > 5) {
                    continue;
                }
                if (d == 5) {
                    laje(mundo, centroU + du, centroV + dv, piso + 1, laje);
                } else {
                    por(mundo, centroU + du, centroV + dv, piso + 1,
                            d == 4 ? linha : d == 0 ? luz : claro);
                }
            }
        }

        // Quatro mastros nas diagonais do terraço: fecham o espaço sem tapar
        // nada, que é a diferença entre "aberto" e "vazio".
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        for (int su = -1; su <= 1; su += 2) {
            for (int sv = -1; sv <= 1; sv += 2) {
                int u = centroU + su * 9;
                int v = centroV + sv * 9;
                for (int y = piso + 1; y <= piso + 8; y++) {
                    por(mundo, u, v, y, (y - piso) % 4 == 0
                            ? planta.cor("vidro", Material.LIGHT_BLUE_STAINED_GLASS)
                            : estrutura);
                }
                por(mundo, u, v, piso + 9, luz);
                por(mundo, u, v, piso + 10, rod);
            }
        }

        boasVindas(mundo, planta, face, piso);
    }

    /**
     * O primeiro texto que o jogador lê no servidor.
     *
     * Em pé, não deitado: a versão larga de três cores numa linha só virava
     * uma faixa cinza atravessada no céu, e o olho não achava onde começar.
     * Uma direção por linha, com a cor do mundo à esquerda, lê na ordem em
     * que as faixas do chão aparecem.
     */
    private void boasVindas(World mundo, HubCatalog planta, HubCatalog.Face face, int piso) {
        Component texto = plugin.mini().deserialize(
                "<gradient:#AEE4FF:#FFFFFF:#AEE4FF><bold>BEM-VINDO</bold></gradient>"
                        + "<newline><white><bold>THE BIGAS</bold></white>"
                        + "<newline>"
                        + "<newline><color:#5BE37D>oeste</color> <gray>survival</gray>"
                        + "<newline><color:#FFB84D>leste</color> <gray>lotes</gray>"
                        + "<newline><color:#C77DFF>norte</color> <gray>minigames</gray>"
                        + "<newline>"
                        + "<newline><dark_gray>siga a faixa colorida do seu caminho</dark_gray>");

        Location alvo = new Location(mundo,
                face.du() * (planta.distanciaDoTerraco() - 13) + 0.5, piso + 8.0,
                face.dv() * (planta.distanciaDoTerraco() - 13) + 0.5);
        alvo.setYaw(face.yawParaOCentro() + 180f);
        alvo.setPitch(0f);
        painel(mundo, alvo, texto, CHEGADA);
    }

    /**
     * Refaz só os textos flutuantes, sem encostar num bloco.
     *
     * Estilo de texto se acerta olhando, e olhar custa entrar no jogo, sair,
     * recompilar e voltar. Desfazer e refazer o hub inteiro para mudar uma
     * cor são dezenas de milhares de blocos reescritos por causa de uma
     * linha — e cada ida dessas é um servidor travado por segundos.
     */
    public Resultado textos(World mundo) {
        HubCatalog planta = plugin.hubCatalog();
        List<HubCatalog.Portal> portais = planta.portais();
        if (portais.isEmpty()) {
            return new Resultado(false, "O hub.yml não tem nenhum portal em 'portais'.", 0);
        }

        int piso = planta.pisoY();
        limparPaineis(mundo);

        letreiroDoMonumento(mundo, planta, piso + monumentoAltura(planta) + 4);
        for (HubCatalog.Portal portal : portais) {
            painelDoPortal(mundo, planta, portal, piso);
        }
        boasVindas(mundo, planta, HubCatalog.faceLivre(portais), piso);

        return new Resultado(true, "Textos do hub refeitos: "
                + (portais.size() + 2) + " letreiros.", portais.size() + 2);
    }

    /** Onde o jogador nasce: no estrado do terraço de chegada. */
    private void pousoDoJogador(World mundo, HubCatalog planta, int piso) {
        Location pouso = pontoDeSpawn(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());
    }

    /** O ponto de pouso do hub, usado pelo spawn e pelo resgate do void. */
    public static Location pontoDeSpawn(World mundo, HubCatalog planta) {
        HubCatalog.Face face = HubCatalog.faceLivre(planta.portais());
        int d = planta.distanciaDoTerraco();
        Location pouso = new Location(mundo, face.du() * d + 0.5, planta.pisoY() + 2,
                face.dv() * d + 0.5);
        pouso.setYaw(face.yawParaOCentro());
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

    /**
     * O jeito de um texto do hub aparecer.
     *
     * Existe como tipo, e não como parâmetros soltos, porque o defeito que
     * ele conserta foi exatamente a falta disso: título, placa de portal e
     * letreiro de chegada nasceram com o MESMO tamanho, a mesma caixa e a
     * mesma largura de linha. Sem hierarquia, a tela fica com três avisos
     * brigando pela mesma atenção — e num hub branco a caixa cinza larga
     * vira o elemento mais pesado da composição.
     *
     * @param fundoAlpha    0 a 255. Baixo deixa o quartzo aparecer por trás;
     *                      alto vira placa de trânsito.
     * @param larguraLinha  onde o cliente quebra a linha, em pixels. Larga
     *                      demais faz a caixa sobrar dos dois lados do texto.
     */
    private record Estilo(float escala, int fundoAlpha, Display.Billboard giro,
                          int larguraLinha, float alcance) {
    }

    /**
     * O nome do servidor sobre o monumento.
     *
     * Gira no eixo vertical: fica sempre de frente para quem olha, de
     * qualquer ponto da praça, e por isso é UM texto em vez das quatro
     * cópias que a versão FIXED precisava — uma por lado.
     */
    private static final Estilo TITULO =
            new Estilo(3.0f, 70, Display.Billboard.VERTICAL, 300, 3.0f);

    /** A placa sobre o portão. Lida da passarela inteira. */
    private static final Estilo PLACA =
            new Estilo(2.2f, 60, Display.Billboard.FIXED, 240, 2.5f);

    /** O letreiro de chegada: o primeiro texto que o jogador lê no servidor. */
    private static final Estilo CHEGADA =
            new Estilo(1.9f, 60, Display.Billboard.FIXED, 260, 2.5f);

    private void painel(World mundo, Location alvo, Component texto, Estilo estilo) {
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(estilo.giro());
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

    /** Quantos blocos já existem onde o hub iria. Zero é o esperado. */
    private int contarOcupados(World mundo, HubCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int piso = planta.pisoY();
        int achados = 0;

        for (int u = -alcance; u <= alcance; u++) {
            for (int v = -alcance; v <= alcance; v++) {
                for (int y = piso - 8; y <= piso + planta.alturaDaObra(); y++) {
                    if (!mundo.getBlockAt(u, y, v).getType().isAir()) {
                        achados++;
                    }
                }
            }
        }
        return achados;
    }
}
