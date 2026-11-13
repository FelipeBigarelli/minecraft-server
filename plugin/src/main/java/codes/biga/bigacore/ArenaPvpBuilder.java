package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta a ARENA PVP.
 *
 * <h2>A conferência que vale mais aqui não é geométrica</h2>
 *
 * {@link #conferirGolpe} prende os números do golpe à conta do Minecraft, e
 * ela recusa justamente o arranjo de vanilla. Com a carência de 10 tiques do
 * Minecraft e o período de 12,5 da espada, bater a cada 10 tiques rende 87%
 * do dano de bater no tempo certo — ou seja, "clicar rápido" é quase tão bom
 * quanto saber lutar, e o tempo do golpe deixa de ser habilidade. A carência
 * igual ao período derruba isso para 67%, e martelar o botão para 37%.
 *
 * <p>São três limites e cada um erra de um jeito diferente:
 *
 * <ul>
 *   <li><b>O ótimo tem de cair no período.</b> Se cair antes, a carência está
 *       curta e o jogo premia adiantar; se cair depois, a carência está longa
 *       e o jogo premia esperar à toa.</li>
 *   <li><b>Martelar tem de render pouco.</b> É o defeito que todo mundo que
 *       já jogou PvP tenta: segurar o clique.</li>
 *   <li><b>Adiantar três tiques tem de render pouco.</b> É o defeito que
 *       ninguém vê, porque 87% do dano parece "quase igual" e é o que
 *       transforma o jogo num concurso de clique.</li>
 * </ul>
 *
 * <p>{@link #conferirArena} mede a beira: o parapeito é o que impede um golpe
 * limpo de jogar alguém no vazio em qualquer lugar da arena, e os vãos são o
 * único lugar onde o vazio volta a valer. Nem fechado demais (e aí não há
 * vazio nenhum, e o jogo é só a conta do golpe) nem aberto demais (e aí a
 * vida é enfeite e o jogo é de empurrão, que o Rei da Montanha já é).
 */
public final class ArenaPvpBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_pvp_painel";

    /** O painel da luta, refeito quando alguém é derrubado. */
    public static final String TAG_LUTA = "bigacore_pvp_luta";

    /** Quanto do ótimo quem martela o botão pode render, no máximo. */
    private static final double MARTELO_MAXIMO = 0.55;

    /** Quanto do ótimo quem adianta três tiques pode render, no máximo. */
    private static final double APRESSADO_MAXIMO = 0.80;

    /** Quantos tiques antes do tempo conta como "adiantado". */
    private static final int ADIANTADO = 3;

    /** Quanto da beira pode ficar aberta, no mínimo e no máximo. */
    private static final double ABERTO_MINIMO = 0.10;
    private static final double ABERTO_MAXIMO = 0.35;

    /** Quantos golpes cheios derrubam alguém, no mínimo e no máximo. */
    private static final int GOLPES_MINIMO = 4;
    private static final int GOLPES_MAXIMO = 10;

    private final BigaCore plugin;
    private int contador;

    public ArenaPvpBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        ArenaPvpCatalog planta = plugin.arenaPvpCatalog();

        String problema = conferirGolpe(planta.periodoDoGolpe(),
                planta.periodoDoGolpe(), planta.vidaCheia(),
                planta.danoPorGolpe(), planta.curaPorEliminacao());
        if (problema == null) {
            problema = conferirArena(planta.raio(), planta.palanqueRaio(),
                    planta.palanqueAltura(), planta.palanqueDegrau(),
                    planta.pilarAnel(), planta.pilarAltura(), planta.vaos(),
                    planta.vaoLargura());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.gracaSegundos(),
                    planta.tempoLimite(), planta.anelDaLargada(),
                    planta.palanqueRaio(), planta.maximo(), planta.vidaCheia(),
                    planta.danoPorGolpe(), planta.periodoDoGolpe());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int beira = ArenaPvpCatalog.blocosDaBeira(planta.raio());
            int abertos = ArenaPvpCatalog.blocosAbertos(planta.raio(),
                    planta.vaos(), planta.vaoLargura());
            double duelo = ArenaPvpCatalog.segundosDeUmDuelo(planta.vidaCheia(),
                    planta.danoPorGolpe(), planta.periodoDoGolpe(),
                    planta.periodoDoGolpe());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Disco de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + ", palanque de " + planta.palanqueAltura()
                    + " degraus no raio " + planta.palanqueRaio() + ", "
                    + ArenaPvpCatalog.pontosDosPilares(planta.pilarAnel()).size()
                    + " pilares no anel " + planta.pilarAnel() + ". Beira: "
                    + abertos + " de " + beira + " blocos abertos ("
                    + Math.round(abertos * 1000.0 / Math.max(1, beira)) / 10.0
                    + "%). O golpe cheio tira " + planta.danoPorGolpe()
                    + " de " + planta.vidaCheia() + ", e um duelo sem parar "
                    + "leva " + Math.round(duelo * 10) / 10.0 + "s. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga pvp desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_LUTA);
        chao(mundo, planta);
        palanque(mundo, planta);
        parapeito(mundo, planta);
        luzesDosVaos(mundo, planta);
        pilares(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDaArena(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "ARENA PVP levantada: " + contador
                + " blocos. O spawn do mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        ArenaPvpCatalog planta = plugin.arenaPvpCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_LUTA);
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
        return new Resultado(true, "ARENA PVP apagada: " + contador
                + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        ArenaPvpCatalog planta = plugin.arenaPvpCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_LUTA);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDaArena(mundo, planta);
        return new Resultado(true, "Letreiros e placas da arena refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * O tempo do golpe é habilidade, e não concurso de clique.
     *
     * Ver o cabeçalho da classe: os três limites recusam, cada um, um arranjo
     * em que o jogo deixa de ser sobre o tempo do golpe sem que nada no yml
     * pareça errado.
     */
    static String conferirGolpe(int periodo, int carencia, int vidaCheia,
                                int dano, int cura) {
        if (periodo < 2) {
            return "O período do golpe é " + periodo + " tique(s): abaixo de "
                    + "dois não há tempo de golpe nenhum para acertar.";
        }
        if (carencia < 1) {
            return "A carência é " + carencia + ": sem carência, martelar o "
                    + "botão vira a jogada certa outra vez.";
        }
        int ate = 4 * periodo;
        int otimo = ArenaPvpCatalog.melhorIntervalo(periodo, carencia, ate);
        if (otimo != periodo) {
            return "O melhor intervalo entre golpes é " + otimo + " tiques e "
                    + "o período da espada é " + periodo + ": com a carência "
                    + "em " + carencia + ", o jogo premia "
                    + (otimo < periodo ? "adiantar o golpe" : "esperar à toa")
                    + " em vez de bater no tempo da espada.";
        }
        double melhor = ArenaPvpCatalog.danoPorSegundo(otimo, periodo,
                carencia, 1.0);

        double martelo = 0;
        for (int i = 1; i <= periodo / 2; i++) {
            martelo = Math.max(martelo, ArenaPvpCatalog.danoPorSegundo(i,
                    periodo, carencia, 1.0));
        }
        if (martelo > melhor * MARTELO_MAXIMO) {
            return "Martelando o botão sai " + Math.round(martelo / melhor * 100)
                    + "% do dano de bater no tempo, e o máximo é "
                    + Math.round(MARTELO_MAXIMO * 100) + "%: segurar o clique "
                    + "é o primeiro defeito que todo jogador de PvP tenta.";
        }

        int adiantado = Math.max(1, periodo - ADIANTADO);
        double apressado = ArenaPvpCatalog.danoPorSegundo(adiantado, periodo,
                carencia, 1.0);
        if (apressado > melhor * APRESSADO_MAXIMO) {
            return "Batendo " + ADIANTADO + " tiques adiantado sai "
                    + Math.round(apressado / melhor * 100) + "% do dano de "
                    + "bater no tempo, e o máximo é "
                    + Math.round(APRESSADO_MAXIMO * 100) + "%: com um atalho "
                    + "quase tão bom, o jogo vira concurso de clique e o "
                    + "tempo do golpe deixa de ser habilidade.";
        }

        int golpes = vidaCheia / Math.max(1, dano);
        if (golpes < GOLPES_MINIMO) {
            return "A vida é " + vidaCheia + " e o golpe cheio tira " + dano
                    + ", o que dá " + golpes + " golpes: menos de "
                    + GOLPES_MINIMO + " e o duelo é cara ou coroa decidido "
                    + "por quem bateu primeiro.";
        }
        if (golpes > GOLPES_MAXIMO) {
            return "A vida é " + vidaCheia + " e o golpe cheio tira " + dano
                    + ", o que dá " + golpes + " golpes: mais de "
                    + GOLPES_MAXIMO + " e o duelo vira moagem, e quem está "
                    + "perdendo tem tempo de fugir sempre.";
        }
        if (cura < vidaCheia / 5) {
            return "A cura por eliminação é " + cura + " de " + vidaCheia
                    + " de vida: com tão pouco, quem ganha uma briga é comida "
                    + "de quem assistiu, e a jogada certa passa a ser nunca "
                    + "brigar.";
        }
        if (cura > vidaCheia / 2) {
            return "A cura por eliminação é " + cura + " de " + vidaCheia
                    + " de vida: com tanto, o primeiro a derrubar alguém vira "
                    + "uma bola de neve que limpa a arena sozinho.";
        }
        return null;
    }

    /**
     * O parapeito fecha a beira, e os vãos a abrem o bastante.
     *
     * O parapeito de um bloco é o que impede um golpe limpo de jogar alguém
     * no vazio em qualquer lugar do disco — sem ele a vida seria enfeite e o
     * jogo seria de empurrão. Os vãos são onde o vazio volta a valer, e eles
     * têm de ser poucos e visíveis.
     */
    static String conferirArena(int raio, int palanqueRaio, int palanqueAltura,
                                int degrau, int pilarAnel, int pilarAltura,
                                int vaos, int vaoLargura) {
        if (palanqueRaio + 5 > raio) {
            return "O palanque tem raio " + palanqueRaio + " num disco de "
                    + raio + ": ele encostaria na beira e a arena seria só o "
                    + "palanque.";
        }
        if (palanqueAltura > 0 && palanqueRaio - (palanqueAltura - 1) * degrau < 2) {
            return "O palanque tem " + palanqueAltura + " degraus de "
                    + degrau + " blocos de recuo num raio de " + palanqueRaio
                    + ": o degrau de cima não teria onde ficar.";
        }
        if (pilarAnel <= palanqueRaio + 4) {
            return "Os pilares estão no anel " + pilarAnel + " e o palanque "
                    + "tem raio " + palanqueRaio + ": com menos de cinco "
                    + "blocos de folga, pilar e palanque viram uma só "
                    + "muralha.";
        }
        if (pilarAnel > raio - 4) {
            return "Os pilares estão no anel " + pilarAnel + " num disco de "
                    + raio + ": encostados no parapeito, eles seriam "
                    + "cobertura de graça para quem luta na beira.";
        }
        if (pilarAltura > 5) {
            return "Os pilares têm " + pilarAltura + " fiadas: altos assim "
                    + "viram o ponto mais alto da arena, e quem subir num "
                    + "deles não é alcançado por espada nenhuma.";
        }
        for (int[] onde : ArenaPvpCatalog.pontosDosPilares(pilarAnel)) {
            if (ArenaPvpCatalog.noVao(onde[0], onde[1], vaos, vaoLargura, raio)) {
                return "O pilar em " + onde[0] + "," + onde[1] + " cai na boca "
                        + "de um vão: cobertura justamente onde o jogo não "
                        + "quer ninguém parado.";
            }
        }

        int beira = ArenaPvpCatalog.blocosDaBeira(raio);
        int abertos = ArenaPvpCatalog.blocosAbertos(raio, vaos, vaoLargura);
        double parte = abertos / (double) Math.max(1, beira);
        if (parte < ABERTO_MINIMO) {
            return "Os vãos abrem " + Math.round(parte * 1000) / 10.0
                    + "% da beira e o mínimo é "
                    + Math.round(ABERTO_MINIMO * 100) + "%: com a beira quase "
                    + "toda fechada o vazio não existe, e o jogo é só a conta "
                    + "do golpe.";
        }
        if (parte > ABERTO_MAXIMO) {
            return "Os vãos abrem " + Math.round(parte * 1000) / 10.0
                    + "% da beira e o máximo é "
                    + Math.round(ABERTO_MAXIMO * 100) + "%: com a beira tão "
                    + "aberta, a vida é enfeite e o jogo passa a ser de "
                    + "empurrão.";
        }
        return null;
    }

    /**
     * A graça dá para sair da largada, e o relógio dá para a chave inteira.
     *
     * O limite de tempo tem de caber os duelos de uma arena cheia: com
     * dezesseis na arena são quinze eliminações, e se o relógio não couber
     * nelas toda partida termina no tempo e o jogo é de placar, não de luta.
     */
    static String conferirRelogio(int graca, int tempoLimite, int anelDaLargada,
                                  int palanqueRaio, int maximo, int vidaCheia,
                                  int dano, int periodo) {
        // Um jogador andando faz uns 4,3 blocos por segundo.
        int andar = (int) Math.ceil(Math.max(1, anelDaLargada - palanqueRaio) / 4.3);
        if (graca < 2) {
            return "A graça é de " + graca + "s: largar dezesseis pessoas no "
                    + "mesmo anel e valer tudo no primeiro tique faz metade "
                    + "levar golpe antes de ver onde está.";
        }
        if (graca > andar + 4) {
            return "A graça é de " + graca + "s e andar da largada até o "
                    + "palanque leva " + andar + "s: tempo demais de espada "
                    + "que não corta vira desfile.";
        }
        if (graca > tempoLimite / 10) {
            return "A graça é de " + graca + "s num limite de " + tempoLimite
                    + "s: espada que não corta por um décimo da partida vira "
                    + "treino, não partida.";
        }
        double duelo = ArenaPvpCatalog.segundosDeUmDuelo(vidaCheia, dano,
                periodo, periodo);
        double chave = duelo * Math.max(1, maximo - 1);
        if (tempoLimite < chave) {
            return "O limite é " + tempoLimite + "s e as "
                    + (maximo - 1) + " eliminações de uma arena cheia levam "
                    + Math.round(chave) + "s de pancada sem parar: toda "
                    + "partida terminaria no relógio, e aí ganha o placar e "
                    + "não a luta.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void chao(World mundo, ArenaPvpCatalog planta) {
        Material piso = planta.cor("piso", Material.POLISHED_ANDESITE);
        Material xadrez = planta.cor("piso-xadrez", Material.POLISHED_DIORITE);
        Material beira = planta.cor("beira", Material.DEEPSLATE_TILES);
        Material vaoPiso = planta.cor("vao-piso", Material.DEEPSLATE_BRICKS);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                Material qual;
                if (ArenaPvpCatalog.noAnelDaBeira(x, z, raio)) {
                    qual = ArenaPvpCatalog.noVao(x, z, planta.vaos(),
                            planta.vaoLargura(), raio) ? vaoPiso : beira;
                } else {
                    qual = ((x >> 2) + (z >> 2)) % 2 == 0 ? piso : xadrez;
                }
                por(mundo, x, y, z, qual);
            }
        }
    }

    /** O palanque em degraus: terreno alto que se sobe andando. */
    private void palanque(World mundo, ArenaPvpCatalog planta) {
        Material pedra = planta.cor("palanque", Material.CHISELED_STONE_BRICKS);
        Material topo = planta.cor("palanque-topo", Material.SMOOTH_STONE);
        int raio = planta.palanqueRaio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                int alto = planta.alturaDoPalanque(x, z);
                for (int fiada = 1; fiada <= alto; fiada++) {
                    por(mundo, x, y + fiada, z, fiada == alto ? topo : pedra);
                }
            }
        }
    }

    /**
     * O parapeito: um bloco de muro na beira, menos nos vãos.
     *
     * Um muro e não um bloco cheio: muro tem a altura de um bloco e meio para
     * pular e a cara de corrimão, então ele fecha a beira sem parecer parede
     * — e quem olha a arena de longe vê onde o chão acaba.
     */
    private void parapeito(World mundo, ArenaPvpCatalog planta) {
        Material muro = planta.cor("parapeito", Material.STONE_BRICK_WALL);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaPvpCatalog.noAnelDaBeira(x, z, raio)) {
                    continue;
                }
                if (ArenaPvpCatalog.noVao(x, z, planta.vaos(),
                        planta.vaoLargura(), raio)) {
                    continue;
                }
                por(mundo, x, y + 1, z, muro);
            }
        }
    }

    /** Uma luz em cada ponta de vão: o perigo tem de se ver de longe. */
    private void luzesDosVaos(World mundo, ArenaPvpCatalog planta) {
        Material luz = planta.cor("vao-luz", Material.SOUL_LANTERN);
        Material poste = planta.cor("poste", Material.STONE_BRICK_WALL);
        int raio = planta.raio();
        int y = planta.topoY();
        int vaos = planta.vaos();
        if (vaos <= 0) {
            return;
        }
        double meioDoVao = planta.vaoLargura() / 2.0 / raio;

        for (int k = 0; k < vaos; k++) {
            double meio = Math.PI / 4 + k * 2 * Math.PI / vaos;
            for (int lado = -1; lado <= 1; lado += 2) {
                double angulo = meio + lado * (meioDoVao + 1.0 / raio);
                int x = (int) Math.round(Math.cos(angulo) * raio);
                int z = (int) Math.round(Math.sin(angulo) * raio);
                por(mundo, x, y + 1, z, poste);
                por(mundo, x, y + 2, z, poste);
                por(mundo, x, y + 3, z, luz);
            }
        }
    }

    /** Os quatro pilares, nos rumos: quebram o cerco e a linha de visão. */
    private void pilares(World mundo, ArenaPvpCatalog planta) {
        Material pedra = planta.cor("pilar", Material.POLISHED_ANDESITE);
        Material ponta = planta.cor("pilar-topo", Material.LIGHTNING_ROD);
        int y = planta.topoY();
        int alto = planta.pilarAltura();

        for (int[] onde : planta.pontosDosPilares()) {
            for (int fiada = 1; fiada <= alto; fiada++) {
                por(mundo, onde[0], y + fiada, onde[1], pedra);
            }
            por(mundo, onde[0], y + alto + 1, onde[1], ponta);
        }
    }

    /** As placas da arena: as regras do golpe, nas quatro diagonais. */
    public void placasDaArena(World mundo, ArenaPvpCatalog planta) {
        int y = planta.topoY() + 2;
        int longe = planta.palanqueRaio() + 3;
        String cor = planta.corDoTexto();
        List<List<Component>> textos = List.of(
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A ESPADA</b></color>"),
                        plugin.mini().deserialize("<white>igual para</white>"),
                        plugin.mini().deserialize("<white>todo mundo</white>"),
                        plugin.mini().deserialize("<gray>sem kit</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>O TEMPO</b></color>"),
                        plugin.mini().deserialize("<white>espere a</white>"),
                        plugin.mini().deserialize("<white>espada carregar</white>"),
                        plugin.mini().deserialize("<gray>martelar = 1/3</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>DERRUBAR</b></color>"),
                        plugin.mini().deserialize("<white>devolve um</white>"),
                        plugin.mini().deserialize("<white>terço da vida</white>"),
                        plugin.mini().deserialize("<gray>vale brigar</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>OS VÃOS</b></color>"),
                        plugin.mini().deserialize("<white>a beira tem</white>"),
                        plugin.mini().deserialize("<white>quatro buracos</white>"),
                        plugin.mini().deserialize("<gray>dão no vazio</gray>")));

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * longe);
            int z = (int) Math.round(Math.sin(angulo) * longe);
            Material poste = planta.cor("poste", Material.STONE_BRICK_WALL);
            por(mundo, x, planta.topoY() + 1, z, poste);
            por(mundo, x, planta.topoY() + 3, z,
                    planta.cor("poste-luz", Material.LANTERN));
            BlockFace frente = ArenaPvpCatalog.frenteDaPlaca(x, z);
            ArenaComum.escreverPlaca(mundo,
                    new int[]{x + frente.getModX(), y, z + frente.getModZ()},
                    planta.materialDaPlaca(), frente, textos.get(k));
        }
    }

    private void torres(World mundo, ArenaPvpCatalog planta) {
        Material torre = planta.cor("torre", Material.POLISHED_ANDESITE);
        Material luz = planta.cor("torre-luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.raio() + 5;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    /** O painel da luta, no alto do palanque. */
    public void painelDaLuta(World mundo, ArenaPvpCatalog planta,
                             Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_LUTA);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.palanqueAltura() + planta.painelAltura(),
                0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_LUTA);
    }

    private int contarOcupados(World mundo, ArenaPvpCatalog planta) {
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
        Block bloco = mundo.getBlockAt(x, y, z);
        if (bloco.getType() != material) {
            bloco.setType(material, false);
            contador++;
        }
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.arenaPvpCatalog());
    }

    public static Location pontoDaLargada(World mundo, ArenaPvpCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    public static Location pontoDaPlateia(World mundo, ArenaPvpCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(65f);
        return onde;
    }
}
