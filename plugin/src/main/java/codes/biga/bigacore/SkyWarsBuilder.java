package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta as ilhas do SKY WARS.
 *
 * <h2>As três conferências, e o que cada uma evita</h2>
 *
 * {@link #conferirNiveis} responde às duas reclamações OPOSTAS que os
 * jogadores fazem do baú do meio — "é inútil e chato" e "já dá vantagem
 * demais". As duas são a mesma pergunta, quanto o meio vale, e as pontas
 * cabem em três números:
 *
 * <ul>
 *   <li><b>O melhor nível ainda precisa de três golpes cheios</b> para
 *       derrubar alguém de vida cheia e sem armadura. Menos que isso e pegar o
 *       baú do meio não é vantagem, é a partida — quem chegou primeiro mata de
 *       dois golpes e não há jogada que conserte.</li>
 *   <li><b>O melhor não bate mais que o dobro do pior.</b> Acima disso o
 *       equipamento decide a luta antes de ela começar, e é exatamente a
 *       reclamação que deu nome a uma proposta de fórum (SEE, <i>Skywars
 *       Equipment Equality</i>).</li>
 *   <li><b>A pior espada ainda derruba o conjunto completo do melhor nível</b>
 *       em menos de catorze golpes. É a ponta de cima, e ela é nova: com
 *       armadura no baú, a luta pode deixar de acabar — e duas pessoas
 *       trocando golpes sem resultado até o anel cair debaixo delas não é
 *       empate, é o jogo travando.</li>
 * </ul>
 *
 * <p>E uma quarta exigência, que não vem de reclamação nenhuma e sim de erro
 * de digitação: as seis listas de nível têm de ter o mesmo tamanho, e dano,
 * blocos, flechas e redução nunca podem DESCER de um nível para o próximo.
 * Sem isso, "nível mais alto" pode ser pior que o mais baixo e nada no yml
 * parece errado — o baú do meio PIORARIA o equipamento de quem o pegou.
 *
 * <p>{@link #conferirSaque} cuida do baú da ilha: o piso garantido tem de dar
 * para ir ao vizinho E VOLTAR, e o baú nunca pode dar o conjunto completo de
 * armadura — o conjunto é o prêmio do meio, e uma ilha do anel que já o desse
 * tornaria a travessia desnecessária.
 *
 * <p>{@link #conferirIlhas} mede o vão: a ponte entre vizinhas tem de ser
 * curta o bastante para alguém atravessar com o que achou no baú, e longa o
 * bastante para a travessia ser uma decisão.
 */
public final class SkyWarsBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_sky_painel";

    /** O painel do ar, refeito quando alguém cai. */
    public static final String TAG_AR = "bigacore_sky_ar";

    /** Quantos golpes cheios do melhor nível ainda precisam derrubar alguém. */
    private static final int GOLPES_MINIMOS_DO_MELHOR = 3;

    /** Quanto o melhor nível pode bater mais que o pior, no máximo. */
    private static final double RAZAO_MAXIMA = 2.0;

    /** Quantos blocos de ponte o vão entre vizinhas pode pedir, no máximo. */
    private static final int VAO_MAXIMO = 16;

    /**
     * Em quantos golpes a PIOR espada ainda tem de derrubar o conjunto
     * completo do melhor nível.
     *
     * Catorze golpes com o período de treze tiques são uns nove segundos de
     * pancadaria sem errar — longo, e é para ser: é o teto, não a média.
     * Acima disso a luta deixa de acabar, e duas pessoas trocando golpes sem
     * resultado até o anel cair debaixo delas não é empate, é travamento.
     */
    private static final int GOLPES_MAXIMOS_DO_PIOR = 14;

    private final BigaCore plugin;
    private int contador;

    public SkyWarsBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        SkyWarsCatalog planta = plugin.skyWarsCatalog();

        String problema = conferirNiveis(planta.nivelEspada(),
                planta.nivelDano(), planta.nivelBlocos(),
                planta.nivelFlechas(), planta.nivelArmadura(),
                planta.nivelReducao(), planta.vidaCheia(),
                planta.reducaoMaxima());
        if (problema == null) {
            problema = conferirSaque(planta.armaduraMinima(),
                    planta.armaduraMaxima(), planta.blocosGarantidos(),
                    planta.blocosExtras(), planta.chanceDeArco(),
                    planta.chanceDeCura(), planta.comida(),
                    planta.vaoEntreIlhas(), planta.danoDoSoco(),
                    planta.nivelDano());
        }
        // A conferência das ilhas mede uma arena GERADA: ela reprova um
        // anel apertado demais para a config poder ser corrigida. Num mapa
        // pronto não há config para corrigir — o desenho é o que é —, então
        // ela vira aviso em vez de recusa. Reprovar aqui deixaria o servidor
        // sem arena nenhuma por causa de um mapa com as ilhas perto.
        String avisoDoMapa = null;
        if (planta.mapa() != null) {
            avisoDoMapa = conferirIlhas(planta.ilhas(), planta.ilhaAnel(),
                    planta.ilhaRaio(), planta.meioRaio(),
                    planta.blocosGarantidos(), planta.bausDaIlha());
        } else if (problema == null) {
            problema = conferirIlhas(planta.ilhas(), planta.ilhaAnel(),
                    planta.ilhaRaio(), planta.meioRaio(),
                    planta.blocosGarantidos(), planta.bausDaIlha());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.segundosParaOMeio(),
                    planta.segundosDeCerco(), planta.segundosPorIlha(),
                    planta.ilhas(), planta.tempoLimite(),
                    planta.travessiaAteOMeio());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int pior = planta.danoDoNivel(0);
            int melhor = planta.danoDoNivel(planta.quantosNiveis() - 1);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". " + planta.ilhas() + " ilhas de raio "
                    + planta.ilhaRaio() + " no anel " + planta.ilhaAnel()
                    + ", com " + Math.round(planta.vaoEntreIlhas() * 10) / 10.0
                    + " blocos de vão entre vizinhas e " + planta.bausDaIlha()
                    + " baú(s) por ilha (garantem espada, comida, "
                    + planta.armaduraMinima() + "-" + planta.armaduraMaxima()
                    + " peça(s) e " + planta.blocosGarantidos() + "-"
                    + (planta.blocosGarantidos() + planta.blocosExtras())
                    + " blocos). Meio de raio "
                    + planta.meioRaio() + " com " + planta.meioBaus()
                    + " baús sob cúpula de " + planta.cupulaAltura()
                    + " fiadas, que cai em " + planta.segundosParaOMeio()
                    + "s. Travessia até o meio: "
                    + planta.travessiaAteOMeio() + " blocos. "
                    + planta.quantosNiveis() + " níveis de kit: o pior tira "
                    + pior + " (" + Math.ceil(planta.vidaCheia() / (double) pior)
                    + " golpes) e o melhor " + melhor + " ("
                    + Math.ceil(planta.vidaCheia() / (double) melhor)
                    + " golpes). O anel cai de " + planta.segundosDeCerco()
                    + "s a " + planta.segundoDaUltimaQueda() + "s. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga sky desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_AR);
        MapaDaArena pronto = planta.mapa();
        if (pronto != null) {
            contador += pronto.colar(mundo, plugin.getLogger());
            bausDoMapa(mundo, planta);
        } else {
            for (int k = 0; k < planta.ilhas(); k++) {
                ilha(mundo, planta, k);
            }
            meio(mundo, planta);
            cupula(mundo, planta);
            baus(mundo, planta);
        }
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDoMeio(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        if (pronto != null) {
            return new Resultado(true, "SKY WARS levantado do mapa '"
                    + pronto.nome() + "': " + contador + " blocos, "
                    + pronto.ilhas() + " ilhas no anel " + pronto.anel()
                    + ", raio " + pronto.raioDaIlha() + ", meio de raio "
                    + pronto.raioDoMeio() + ", jaulas na fiada "
                    + pronto.yDaJaula() + "."
                    + (pronto.naoTraduzidos() > 0 ? " " + pronto.naoTraduzidos()
                    + " bloco(s) desta versão do Minecraft não conhece e "
                    + "ficaram de fora." : "")
                    + (avisoDoMapa != null ? " AVISO: " + avisoDoMapa : ""),
                    contador);
        }
        return new Resultado(true, "SKY WARS levantado: " + contador
                + " blocos em " + planta.ilhas() + " ilhas mais o meio. O "
                + "spawn do mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        SkyWarsCatalog planta = plugin.skyWarsCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_AR);
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
        return new Resultado(true, "SKY WARS apagado: " + contador
                + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        SkyWarsCatalog planta = plugin.skyWarsCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_AR);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDoMeio(mundo, planta);
        return new Resultado(true, "Letreiros e placas do meio refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** Ver o cabeçalho da classe: as três pontas do valor do equipamento. */
    static String conferirNiveis(List<String> espadas, List<Integer> danos,
                                 List<Integer> blocos, List<Integer> flechas,
                                 List<String> armaduras,
                                 List<Integer> reducoes, int vidaCheia,
                                 int reducaoMaxima) {
        int quantos = espadas.size();
        if (quantos < 2) {
            return "Há " + quantos + " nível(is) de saque: com um só, o baú do "
                    + "meio não tem para onde subir e a ilha do meio vira "
                    + "enfeite.";
        }
        if (danos.size() != quantos || blocos.size() != quantos
                || flechas.size() != quantos || armaduras.size() != quantos
                || reducoes.size() != quantos) {
            return "As listas de nível têm tamanhos diferentes: " + quantos
                    + " espadas, " + danos.size() + " danos, " + blocos.size()
                    + " contagens de bloco, " + flechas.size()
                    + " de flecha, " + armaduras.size() + " armaduras e "
                    + reducoes.size() + " reduções. O nível que faltar seria "
                    + "lido do último da lista, em silêncio.";
        }
        for (String familia : armaduras) {
            List<Material> pecas = SkyWarsCatalog.pecasDaFamilia(
                    familia.trim().toUpperCase(java.util.Locale.ROOT));
            if (pecas.size() != SkyWarsCatalog.PECAS.size()) {
                return "A armadura '" + familia + "' só tem " + pecas.size()
                        + " das " + SkyWarsCatalog.PECAS.size() + " peças "
                        + "neste Minecraft: o baú daquele nível entregaria "
                        + "menos armadura que o do nível de baixo, sem avisar.";
            }
        }
        for (String nome : espadas) {
            if (Material.matchMaterial(nome.trim().toUpperCase()) == null) {
                return "A espada " + nome + " não existe neste Minecraft: o "
                        + "nível dela entregaria espada de madeira sem avisar.";
            }
        }
        for (int k = 1; k < quantos; k++) {
            if (danos.get(k) < danos.get(k - 1)) {
                return "O nível " + (k + 1) + " tira " + danos.get(k)
                        + " e o nível " + k + " tira " + danos.get(k - 1)
                        + ": nível mais alto tem de bater mais, senão o baú "
                        + "do meio pode PIORAR o kit de quem o pegou e nada "
                        + "no yml parece errado.";
            }
            if (blocos.get(k) < blocos.get(k - 1)) {
                return "O nível " + (k + 1) + " dá " + blocos.get(k)
                        + " blocos e o nível " + k + " dá "
                        + blocos.get(k - 1) + ": nível mais alto tem de dar "
                        + "mais ponte.";
            }
            if (flechas.get(k) < flechas.get(k - 1)) {
                return "O nível " + (k + 1) + " dá " + flechas.get(k)
                        + " flechas e o nível " + k + " dá "
                        + flechas.get(k - 1) + ".";
            }
            if (reducoes.get(k) < reducoes.get(k - 1)) {
                return "A armadura do nível " + (k + 1) + " tira "
                        + reducoes.get(k) + "% por peça e a do nível " + k
                        + " tira " + reducoes.get(k - 1) + "%: nível mais alto "
                        + "tem de defender mais, senão vestir o conjunto do "
                        + "meio PIORA a defesa de quem o pegou.";
            }
        }
        for (int reducao : reducoes) {
            if (reducao < 0 || reducao > 25) {
                return "Uma peça tira " + reducao + "% de dano: acima de 25 "
                        + "duas peças já passam de metade e a armadura deixa "
                        + "de ser vantagem para ser imunidade.";
            }
        }
        int melhor = danos.get(quantos - 1);
        int pior = danos.get(0);
        if (pior < 1) {
            return "O pior nível tira " + pior + ": espada que não machuca "
                    + "não é kit.";
        }
        double golpes = vidaCheia / (double) melhor;
        if (golpes < GOLPES_MINIMOS_DO_MELHOR) {
            return "O melhor nível tira " + melhor + " de " + vidaCheia
                    + " de vida, o que derruba alguém em "
                    + Math.round(golpes * 10) / 10.0 + " golpes, e o mínimo é "
                    + GOLPES_MINIMOS_DO_MELHOR + ": com menos, pegar o baú do "
                    + "meio não é vantagem, é a partida.";
        }
        double razao = melhor / (double) pior;
        if (razao > RAZAO_MAXIMA) {
            return "O melhor nível bate " + Math.round(razao * 100) / 100.0
                    + " vezes o pior, e o máximo é " + RAZAO_MAXIMA + ": "
                    + "acima disso o equipamento decide a luta antes de ela "
                    + "começar, que é a reclamação mais antiga do gênero.";
        }

        // A ponta de cima: a pior espada contra o conjunto completo do melhor
        // nível, que é o que alguém veste depois de saquear o meio.
        double blindagem = SkyWarsCatalog.reducaoTotal(
                SkyWarsCatalog.PECAS.size(), reducoes.get(quantos - 1),
                reducaoMaxima);
        double arrasto = SkyWarsCatalog.golpesPara(vidaCheia, pior, blindagem);
        if (arrasto > GOLPES_MAXIMOS_DO_PIOR) {
            return "A pior espada tira " + pior + " e o conjunto completo do "
                    + "melhor nível apara " + Math.round(blindagem * 100)
                    + "%, o que dá " + Math.round(arrasto * 10) / 10.0
                    + " golpes para derrubar alguém — o máximo é "
                    + GOLPES_MAXIMOS_DO_PIOR + ". Acima disso a luta deixa de "
                    + "acabar, e duas pessoas trocando golpes sem resultado "
                    + "até o anel cair debaixo delas não é empate, é o jogo "
                    + "travando.";
        }
        return null;
    }

    /**
     * O baú da ilha: o piso que ele garante e o teto que ele não passa.
     *
     * Duas exigências, e as duas vêm do mesmo lugar — da versão anterior
     * desta arena, que tirou o baú das ilhas para acabar com a loteria e
     * acabou com o saque junto. O baú voltou com o piso garantido, e é aqui
     * que o piso é conferido.
     */
    static String conferirSaque(int minimo, int maximo, int blocosPiso,
                                int blocosExtras, int chanceArco,
                                int chanceCura, int comida, double vao,
                                int danoDoSoco, List<Integer> danos) {
        if (minimo < 1) {
            return "O baú da ilha garante " + minimo + " peça(s) de armadura: "
                    + "com zero, existe o baú que não dá defesa nenhuma, e "
                    + "quem o abrir perde a partida por sorteio — que é "
                    + "exatamente a reclamação do gênero.";
        }
        if (maximo < minimo) {
            return "O baú da ilha dá no mínimo " + minimo + " peça(s) e no "
                    + "máximo " + maximo + ".";
        }
        if (maximo >= SkyWarsCatalog.PECAS.size()) {
            return "O baú da ilha pode dar " + maximo + " peças, que é o "
                    + "conjunto completo: o conjunto é o prêmio da ilha do "
                    + "meio, e uma ilha do anel que já o desse tornaria a "
                    + "travessia desnecessária — volta a reclamação de que o "
                    + "baú do meio é inútil e chato.";
        }
        if (maximo == minimo) {
            return "O baú da ilha dá sempre " + minimo + " peça(s): sem "
                    + "variação nenhuma, abrir o baú deixa de ser uma "
                    + "pergunta e vira uma formalidade.";
        }
        int precisa = (int) Math.ceil(vao) * 2;
        if (blocosPiso < precisa) {
            return "O baú da ilha garante " + blocosPiso + " bloco(s) de "
                    + "ponte e o vão até o vizinho pede " + precisa
                    + " para ir e voltar: existe baú que deixa quem o abriu "
                    + "preso na própria ilha, esperando o anel cair.";
        }
        if (blocosExtras <= 0) {
            return "O baú da ilha dá sempre os mesmos " + blocosPiso
                    + " blocos: é o piso sem sorteio nenhum por cima, e aí o "
                    + "baú não tem o que revelar.";
        }
        if (chanceArco <= 0 && chanceCura <= 0) {
            return "O baú da ilha nunca dá arco nem cura: tirando a armadura, "
                    + "todas as ilhas abrem a mesma coisa.";
        }
        if (chanceArco >= 100 && chanceCura >= 100) {
            return "O baú da ilha SEMPRE dá arco e cura: é o kit entregue na "
                    + "mão com um clique a mais, que foi o erro da versão "
                    + "anterior desta arena.";
        }
        if (comida < 1) {
            return "O baú da ilha não garante comida: perder a partida de "
                    + "fome por azar de baú não é jogada de ninguém.";
        }
        int pior = danos.isEmpty() ? 1 : danos.get(0);
        if (danoDoSoco * 2 >= pior) {
            return "O soco tira " + danoDoSoco + " e a pior espada tira "
                    + pior + ": com o soco valendo metade ou mais da espada, "
                    + "abrir o baú deixa de ser a primeira coisa a fazer e o "
                    + "saque perde a razão de existir.";
        }
        return null;
    }

    /**
     * A ponte entre vizinhas é curta, e dá para atravessar com o que o baú
     * garante.
     *
     * Contra o piso GARANTIDO do baú e não contra o melhor sorteio: a conta
     * tem de valer para a ilha mais azarada do anel, senão ela vale para
     * ninguém.
     */
    static String conferirIlhas(int ilhas, int anel, int raio, int meioRaio,
                                int blocosGarantidos, int bausDaIlha) {
        if (anel - raio - meioRaio < 8) {
            return "Do anel até a beira do meio há "
                    + (anel - raio - meioRaio) + " blocos: tão perto que a "
                    + "ilha do meio encosta nas do anel e não há travessia "
                    + "nenhuma.";
        }
        double vao = SkyWarsCatalog.vaoEntreIlhas(ilhas, anel, raio);
        if (vao < 4) {
            return "O vão entre ilhas vizinhas é de "
                    + Math.round(vao * 10) / 10.0 + " blocos: tão perto que "
                    + "dá para pular de uma para a outra, e aí não há ponte "
                    + "nem vazio — é uma arena redonda com buracos.";
        }
        if (vao > VAO_MAXIMO) {
            return "O vão entre ilhas vizinhas é de "
                    + Math.round(vao * 10) / 10.0 + " blocos e o máximo é "
                    + VAO_MAXIMO + ": ninguém atravessa para o vizinho e "
                    + "todas as partidas acabam no meio.";
        }
        if (blocosGarantidos < Math.ceil(vao) * 2) {
            return "O baú garante " + blocosGarantidos + " blocos e o vão para "
                    + "o vizinho pede " + (int) Math.ceil(vao) + ": com menos "
                    + "do dobro, quem atravessa chega sem ter com que "
                    + "voltar, e atravessar deixa de ser decisão para ser "
                    + "aposta.";
        }
        if (bausDaIlha < 1) {
            return "A ilha tem " + bausDaIlha + " baú(s): sem baú não existe "
                    + "o saque, e o saque é os quinze primeiros segundos de "
                    + "toda partida de Sky Wars.";
        }
        // Os baús moram num anelzinho de raio (raio - 2) em volta do marco.
        // Com raio 2 esse anelzinho encosta na beira, e baú na beira cai
        // junto com a ilha no cerco, com o conteúdo dentro.
        if (raio < 3) {
            return "A ilha tem raio " + raio + ": não sobra anel entre o "
                    + "marco do meio e a beira para pôr baú, e baú na beira "
                    + "some junto com a ilha quando o cerco a derruba.";
        }
        java.util.Set<String> ondes = new java.util.HashSet<>();
        for (int[] onde : SkyWarsCatalog.pontosDosBausDaIlha(0, ilhas, anel,
                raio, bausDaIlha)) {
            int[] centro = SkyWarsCatalog.centroDaIlha(0, ilhas, anel);
            if (centro == null) {
                continue;
            }
            int dx = onde[0] - centro[0];
            int dz = onde[1] - centro[1];
            if (!ArenaComum.noDiscoMeio(dx, dz, raio - 1)) {
                return "Um baú da ilha cairia em (" + dx + "," + dz + "), que "
                        + "está na beira de uma ilha de raio " + raio + ": "
                        + "ele sumiria junto com a ilha no cerco.";
            }
            if (!ondes.add(dx + ":" + dz)) {
                return "Dois baús da ilha cairiam no mesmo bloco (" + dx + ","
                        + dz + "): a ilha teria " + bausDaIlha + " baús no yml "
                        + "e menos que isso no chão.";
            }
        }
        return null;
    }

    /**
     * A cúpula cai depois de dar para atravessar, e o anel cai depois dela.
     *
     * A cúpula é a proposta dos próprios jogadores do gênero contra "quem faz
     * ponte rápido pega o baú do meio": ela tem de cair DEPOIS de o mais
     * lento conseguir chegar, senão ela não conserta nada.
     */
    static String conferirRelogio(int paraOMeio, int cerco, int porIlha,
                                  int ilhas, int tempoLimite, int travessia) {
        // Uma ponte cuidadosa sai a uns 1,5 blocos por segundo.
        int atravessar = (int) Math.ceil(travessia / 1.5);
        if (paraOMeio < atravessar) {
            return "A cúpula do meio cai em " + paraOMeio + "s e atravessar "
                    + travessia + " blocos de ponte leva " + atravessar
                    + "s: ela cairia antes de o mais lento chegar, e aí quem "
                    + "faz ponte rápido pega o baú sozinho — que é "
                    + "exatamente o defeito que a cúpula existe para "
                    + "consertar.";
        }
        if (cerco <= paraOMeio) {
            return "O anel começa a cair em " + cerco + "s e a cúpula do meio "
                    + "cai em " + paraOMeio + "s: o cerco começaria antes ou "
                    + "junto com a abertura do meio, e ninguém teria tempo de "
                    + "disputar o baú.";
        }
        if (cerco < atravessar) {
            return "O anel começa a cair em " + cerco + "s e a travessia leva "
                    + atravessar + "s: alguém perderia a ilha antes de poder "
                    + "ter saído dela.";
        }
        int ultima = SkyWarsCatalog.segundoDaQueda(ilhas - 1, cerco, porIlha);
        if (ultima > tempoLimite) {
            return "A última ilha do anel cai em " + ultima + "s e o limite é "
                    + tempoLimite + "s: a partida acabaria no relógio com "
                    + "ilha de pé, e o cerco não teria apertado nada.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** Uma ilha do anel: um disco raso de neve com a beira de quartzo. */
    private void ilha(World mundo, SkyWarsCatalog planta, int k) {
        Material topo = planta.cor("ilha-topo", Material.SNOW_BLOCK);
        Material corpo = planta.cor("ilha-corpo", Material.CALCITE);
        Material beira = planta.cor("ilha-beira", Material.SMOOTH_QUARTZ);
        Material marco = planta.cor("ilha-marco", Material.QUARTZ_PILLAR);
        int[] centro = planta.centroDaIlha(k);
        if (centro == null) {
            return;
        }
        int y = planta.topoY();
        int raio = planta.ilhaRaio();

        for (int dx = -raio; dx <= raio; dx++) {
            for (int dz = -raio; dz <= raio; dz++) {
                if (!ArenaComum.noDiscoMeio(dx, dz, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(dx, dz, raio - 1);
                por(mundo, centro[0] + dx, y, centro[1] + dz,
                        naBeira ? beira : topo);
                // E o pé da ilha, AFINANDO. Cada andar mais estreito que o
                // de cima: é o que faz a ilha parecer arrancada do chão em
                // vez de uma ficha de pôquer boiando.
                for (int fundo = 1; fundo < planta.ilhaAltura(); fundo++) {
                    int deste = SkyWarsCatalog.raioDoFundo(raio, fundo,
                            planta.ilhaAfunilamento());
                    if (deste < 0) {
                        break;
                    }
                    if (!ArenaComum.noDiscoMeio(dx, dz, deste)) {
                        continue;
                    }
                    por(mundo, centro[0] + dx, y - fundo, centro[1] + dz,
                            corpo);
                }
            }
        }
        // Um marco no meio da ilha, para ela ser achada de longe.
        por(mundo, centro[0], y + 1, centro[1], marco);
        por(mundo, centro[0], y + 2, centro[1],
                planta.cor("poste-luz", Material.SEA_LANTERN));

        // E os baús, encostados nele. Postos aqui e não numa passada
        // separada porque a ilha e o baú dela nascem e morrem juntos: o
        // cerco derruba a ilha inteira, e refazê-la tem de devolver os dois.
        Material bau = planta.cor("bau", Material.CHEST);
        for (int[] onde : planta.pontosDosBausDaIlha(k)) {
            por(mundo, onde[0], y + 1, onde[1], bau);
        }
    }

    /** Apaga uma ilha do anel: é isso que o cerco faz. */
    public int derrubarIlha(World mundo, SkyWarsCatalog planta, int k) {
        MapaDaArena pronto = planta.mapa();
        if (pronto != null) {
            return pronto.apagarIlha(mundo, k);
        }
        int[] centro = planta.centroDaIlha(k);
        if (centro == null) {
            return 0;
        }
        contador = 0;
        int raio = planta.ilhaRaio() + 1;
        int deY = planta.topoY() - planta.ilhaAltura() - 1;
        int ateY = planta.topoY() + 3;
        for (int dx = -raio; dx <= raio; dx++) {
            for (int dz = -raio; dz <= raio; dz++) {
                for (int y = deY; y <= ateY; y++) {
                    Block bloco = mundo.getBlockAt(centro[0] + dx, y,
                            centro[1] + dz);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return contador;
    }

    /** Levanta de volta uma ilha do anel, para a rodada seguinte. */
    public int refazerIlha(World mundo, SkyWarsCatalog planta, int k) {
        MapaDaArena pronto = planta.mapa();
        if (pronto != null) {
            int postos = pronto.refazerIlha(mundo, k, plugin.getLogger());
            Material bau = planta.cor("bau", Material.CHEST);
            for (int[] onde : planta.bausDaIlhaEmTresD(k)) {
                mundo.getBlockAt(onde[0], onde[1], onde[2]).setType(bau, false);
            }
            return postos;
        }
        contador = 0;
        ilha(mundo, planta, k);
        return contador;
    }

    private void meio(World mundo, SkyWarsCatalog planta) {
        Material topo = planta.cor("meio-topo", Material.QUARTZ_BLOCK);
        Material corpo = planta.cor("meio-corpo", Material.SMOOTH_QUARTZ);
        Material beira = planta.cor("meio-beira",
                Material.CHISELED_QUARTZ_BLOCK);
        Material luz = planta.cor("meio-luz", Material.SEA_LANTERN);
        int y = planta.topoY();
        int raio = planta.meioRaio();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira : topo);
                // A mesma tigela do mapa de referência: o meio é a ilha
                // maior e a que mais afunila, porque é a que se vê de todas
                // as dezesseis ilhas do anel ao mesmo tempo.
                for (int fundo = 1; fundo < planta.meioAltura(); fundo++) {
                    int deste = SkyWarsCatalog.raioDoFundo(raio, fundo,
                            planta.meioAfunilamento());
                    if (deste < 0) {
                        break;
                    }
                    if (!ArenaComum.noDiscoMeio(x, z, deste)) {
                        continue;
                    }
                    por(mundo, x, y - fundo, z, corpo);
                }
            }
        }
        por(mundo, 0, y, 0, luz);
    }

    /**
     * A cúpula de vidro do meio.
     *
     * É a proposta dos próprios jogadores do gênero contra "quem faz ponte
     * rápido pega o baú do meio". De vidro porque o baú tem de SER VISTO:
     * uma parede opaca esconderia o prêmio e tiraria do jogo o motivo de
     * atravessar. Fechada por cima também, senão bastava chegar voando de
     * ponte alta.
     */
    private void cupula(World mundo, SkyWarsCatalog planta) {
        if (planta.mapa() != null) {
            // Num mapa pronto o meio tem a forma que o autor deu, e uma
            // cúpula nossa por cima dele atravessaria a torre do meio. O
            // que a hora marcada passa a abrir não é o vidro: é o BAÚ.
            return;
        }
        Material vidro = planta.cor("cupula",
                Material.LIGHT_BLUE_STAINED_GLASS);
        int y = planta.topoY();
        int raio = planta.meioRaio() + 1;
        int alto = planta.cupulaAltura();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                boolean dentro = ArenaComum.noDiscoMeio(x, z, raio);
                boolean naBeira = dentro
                        && !ArenaComum.noDiscoMeio(x, z, raio - 1);
                if (naBeira) {
                    for (int fiada = 1; fiada <= alto; fiada++) {
                        por(mundo, x, y + fiada, z, vidro);
                    }
                } else if (dentro) {
                    por(mundo, x, y + alto, z, vidro);
                }
            }
        }
    }

    /** Tira a cúpula: é a hora marcada do meio. */
    public int abrirOMeio(World mundo, SkyWarsCatalog planta) {
        if (planta.mapa() != null) {
            return 0;
        }
        Material vidro = planta.cor("cupula",
                Material.LIGHT_BLUE_STAINED_GLASS);
        int y = planta.topoY();
        int raio = planta.meioRaio() + 2;
        int alto = planta.cupulaAltura();
        contador = 0;
        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                for (int fiada = 1; fiada <= alto; fiada++) {
                    Block bloco = mundo.getBlockAt(x, y + fiada, z);
                    if (bloco.getType() == vidro) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return contador;
    }

    /** Refaz a cúpula para a rodada seguinte. */
    public int fecharOMeio(World mundo, SkyWarsCatalog planta) {
        contador = 0;
        cupula(mundo, planta);
        return contador;
    }

    private void baus(World mundo, SkyWarsCatalog planta) {
        Material bau = planta.cor("bau", Material.CHEST);
        int y = planta.topoY() + 1;
        for (int[] onde : planta.pontosDosBaus()) {
            por(mundo, onde[0], y, onde[1], bau);
        }
    }

    /**
     * Os baús de um mapa pronto, onde os marcadores dele mandam.
     *
     * <p>O mapa não traz baú: traz marcador de baú. É a diferença entre um
     * mapa e uma partida — o autor diz ONDE o saque aparece, e quem diz o
     * QUE aparece continua sendo o nosso sorteio, com o piso garantido que o
     * gênero inteiro pede e quase nenhum servidor dá.
     */
    private void bausDoMapa(World mundo, SkyWarsCatalog planta) {
        Material bau = planta.cor("bau", Material.CHEST);
        for (int k = 0; k < planta.ilhas(); k++) {
            for (int[] onde : planta.bausDaIlhaEmTresD(k)) {
                por(mundo, onde[0], onde[1], onde[2], bau);
            }
        }
        for (int[] onde : planta.bausDoMeioEmTresD()) {
            por(mundo, onde[0], onde[1], onde[2], bau);
        }
    }

    /** As placas do meio: as regras que o jogador lê ao chegar. */
    public void placasDoMeio(World mundo, SkyWarsCatalog planta) {
        String cor = planta.corDoTexto();
        int y = planta.topoY() + 2;
        int longe = Math.max(3, planta.meioRaio() - 1);
        List<List<Component>> textos = List.of(
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A ESPADA</b></color>"),
                        plugin.mini().deserialize("<white>é a mesma em</white>"),
                        plugin.mini().deserialize("<white>toda ilha</white>"),
                        plugin.mini().deserialize("<gray>o resto varia</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>SÓ AQUI</b></color>"),
                        plugin.mini().deserialize("<white>a espada sobe</white>"),
                        plugin.mini().deserialize("<white>de nível, quando</white>"),
                        plugin.mini().deserialize("<gray>a cúpula cai</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A PONTE</b></color>"),
                        plugin.mini().deserialize("<white>só se quebra</white>"),
                        plugin.mini().deserialize("<white>o que alguém</white>"),
                        plugin.mini().deserialize("<gray>colocou</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>O ANEL CAI</b></color>"),
                        plugin.mini().deserialize("<white>no fim, uma</white>"),
                        plugin.mini().deserialize("<white>de cada vez</white>"),
                        plugin.mini().deserialize("<gray>dando a volta</gray>")));

        int[][] lados = {{0, -longe}, {longe, 0}, {0, longe}, {-longe, 0}};
        for (int k = 0; k < lados.length; k++) {
            int x = lados[k][0];
            int z = lados[k][1];
            por(mundo, x, planta.topoY() + 1, z,
                    planta.cor("poste", Material.QUARTZ_PILLAR));
            BlockFace frente = ArcoCatalog.frenteDaPlaca(x, z);
            ArenaComum.escreverPlaca(mundo,
                    new int[]{x + frente.getModX(), y, z + frente.getModZ()},
                    planta.materialDaPlaca(), frente, textos.get(k));
        }
    }

    private void torres(World mundo, SkyWarsCatalog planta) {
        Material torre = planta.cor("torre", Material.SMOOTH_QUARTZ);
        Material luz = planta.cor("torre-luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        // Num mapa pronto o anel é medido pelas JAULAS, e a ilha da diagonal
        // se estende para fora dele: uma torre a anel+7 nasceria dentro da
        // ilha. Por isso a medida aqui é o alcance do mapa inteiro.
        MapaDaArena pronto = planta.mapa();
        int raio = pronto != null
                ? Math.max(planta.ilhaAnel() + 7, pronto.alcance() + 8)
                : planta.ilhaAnel() + 7;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z,
                        alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    /** O painel do ar, no alto do meio. */
    public void painelDoAr(World mundo, SkyWarsCatalog planta,
                           Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_AR);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.painelAltura(), 0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_AR);
    }

    private int contarOcupados(World mundo, SkyWarsCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.skyWarsCatalog());
    }

    public static Location pontoDaIlha(World mundo, SkyWarsCatalog planta,
                                       int k) {
        int[] centro = planta.centroDaIlha(k);
        if (centro == null) {
            return SaguaoPadrao.pontoDoSaguao(mundo, planta);
        }
        Location pouso = new Location(mundo, centro[0] + 0.5,
                planta.topoY() + 1, centro[1] + 0.5 + 2);
        pouso.setYaw(ArenaComum.yawParaOCentro(centro[0], centro[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    public static Location pontoDaPlateia(World mundo,
                                          SkyWarsCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }
}
