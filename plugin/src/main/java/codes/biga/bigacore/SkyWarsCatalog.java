package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta do SKY WARS.
 *
 * <h2>O baú da ilha é o jogo, e a garantia é no PISO</h2>
 *
 * A primeira versão desta planta não tinha baú nas ilhas: o kit chegava na mão
 * de todo mundo, igual, sorteado por partida. A intenção era atacar o defeito
 * mais antigo do gênero — que tem nome próprio nos fóruns dos jogadores, SEE,
 * <i>Skywars Equipment Equality</i>, e a reclamação é sempre a mesma: um abre
 * espada de ferro e o outro abre picareta de madeira. O resultado foi outro
 * jogo. Sem baú não existe o saque, e o saque é os quinze primeiros segundos
 * de toda partida de Sky Wars.
 *
 * <p>O baú voltou, e a loteria é cercada como os próprios jogadores do gênero
 * propõem: GARANTIR O PISO, não apagar o sorteio. A proposta de fórum sobre as
 * pérolas do meio pede exatamente isso — que a menor quantidade que se pode
 * achar seja garantida em pelo menos um baú.
 *
 * <ul>
 *   <li>A ESPADA é a mesma em toda ilha: {@link #nivelDaPartida} sorteia UM
 *       nível e ele vale para o anel inteiro. A reclamação do gênero é sobre a
 *       ARMA, e é na arma que a garantia morde.</li>
 *   <li>O RESTO varia, baú por baú: quantas peças de armadura, quantos blocos
 *       acima do piso, se veio arco, se veio cura. É {@link #sortearSaque},
 *       e é por isso que abrir o baú vale a pena.</li>
 *   <li>O PISO é garantido: espada, comida, pelo menos uma peça e blocos
 *       bastantes para ir ao vizinho E VOLTAR. Ninguém abre um baú que o
 *       deixa preso.</li>
 * </ul>
 *
 * <h2>O meio vale exatamente um nível</h2>
 *
 * As duas reclamações opostas sobre o baú do meio — "é inútil e chato" e "já
 * dá vantagem demais" — são a mesma pergunta: quanto o meio vale?
 * {@link #nivelDoMeio} responde com um número só, e
 * {@link SkyWarsBuilder#conferirNiveis} prende as duas pontas.
 *
 * <h2>A espada é a mesma de toda arena daqui</h2>
 *
 * O dano do golpe sai de {@link ArenaPvpCatalog#forcaDoGolpe} e a carência é
 * igual ao período, exatamente como na ARENA PVP. Não é economia de código: é
 * que um jogador que aprendeu o tempo do golpe numa arena não pode ter de
 * reaprender na outra.
 */
public final class SkyWarsCatalog extends CatalogoDeArena {

    public SkyWarsCatalog(BigaCore plugin) {
        super(plugin, "sky-wars.yml", "sky_wars", "sky");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int ilhas() {
        return Math.max(2, config.getInt("arena.ilhas", 16));
    }

    public int ilhaAnel() {
        return Math.max(12, config.getInt("arena.ilha-anel", 44));
    }

    public int ilhaRaio() {
        return Math.max(2, config.getInt("arena.ilha-raio", 3));
    }

    public int ilhaAltura() {
        return Math.max(1, config.getInt("arena.ilha-altura", 2));
    }

    /** Quantos baús tem cada ilha do anel. */
    public int bausDaIlha() {
        return Math.max(1, config.getInt("arena.baus-da-ilha", 2));
    }

    public int meioRaio() {
        return Math.max(4, config.getInt("arena.meio-raio", 9));
    }

    public int meioAltura() {
        return Math.max(1, config.getInt("arena.meio-altura", 3));
    }

    public int meioBaus() {
        return Math.max(1, config.getInt("arena.meio-baus", 4));
    }

    public int cupulaAltura() {
        return Math.max(3, config.getInt("arena.cupula-altura", 7));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 18));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 12));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(10, config.getInt("regras.queda-abaixo", 25));
    }

    @Override
    public int alcanceDaObra() {
        return ilhaAnel() + ilhaRaio() + 10;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - Math.max(ilhaAltura(), meioAltura()) - 3;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 10);
    }

    /** O vão de vazio entre duas ilhas vizinhas: o tamanho da ponte curta. */
    public double vaoEntreIlhas() {
        return vaoEntreIlhas(ilhas(), ilhaAnel(), ilhaRaio());
    }

    public static double vaoEntreIlhas(int ilhas, int anel, int raio) {
        return 2 * Math.PI * anel / Math.max(1, ilhas) - (2 * raio + 1);
    }

    /** Quantos blocos de ponte do anel até a beira do meio. */
    public int travessiaAteOMeio() {
        return Math.max(1, ilhaAnel() - ilhaRaio() - meioRaio());
    }

    public int[] centroDaIlha(int k) {
        return centroDaIlha(k, ilhas(), ilhaAnel());
    }

    /** O meio da ilha {@code k}, começando no norte e seguindo o relógio. */
    public static int[] centroDaIlha(int k, int ilhas, int anel) {
        if (k < 0 || k >= ilhas) {
            return null;
        }
        double angulo = -Math.PI / 2 + 2 * Math.PI * k / Math.max(1, ilhas);
        return new int[]{(int) Math.round(Math.cos(angulo) * anel),
                (int) Math.round(Math.sin(angulo) * anel)};
    }

    /** Em que ilha do anel o ponto está, ou -1. */
    public int ilhaDoPonto(double x, double z) {
        return ilhaDoPonto(x, z, ilhas(), ilhaAnel(), ilhaRaio());
    }

    public static int ilhaDoPonto(double x, double z, int ilhas, int anel,
                                  int raio) {
        for (int k = 0; k < ilhas; k++) {
            int[] centro = centroDaIlha(k, ilhas, anel);
            if (centro == null) {
                continue;
            }
            if (Math.hypot(x - centro[0], z - centro[1]) <= raio + 0.5) {
                return k;
            }
        }
        return -1;
    }

    /** Os baús do meio, num anel pequeno dentro da ilha do meio. */
    public List<int[]> pontosDosBaus() {
        return pontosDosBaus(meioBaus(), meioRaio());
    }

    public static List<int[]> pontosDosBaus(int quantos, int meioRaio) {
        List<int[]> saida = new ArrayList<>();
        int anel = Math.max(2, meioRaio - 4);
        for (int k = 0; k < Math.max(1, quantos); k++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * k
                    / Math.max(1, quantos);
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return List.copyOf(saida);
    }

    /** Os baús de uma ilha do anel, em coordenada do mundo. */
    public List<int[]> pontosDosBausDaIlha(int k) {
        return pontosDosBausDaIlha(k, ilhas(), ilhaAnel(), ilhaRaio(),
                bausDaIlha());
    }

    /**
     * Os baús de uma ilha, num anelzinho em volta do marco dela.
     *
     * Em volta do marco e não em cima dele: o marco é o que faz a ilha ser
     * achada de longe e ele ocupa o meio. O anelzinho é de raio
     * {@code raio - 2} — num raio 3 isso dá 1, que põe os baús encostados no
     * marco, à vista de quem larga e sem encostar na beira. Baú na beira cai
     * junto com a ilha no cerco e desaparece com o conteúdo dentro.
     */
    public static List<int[]> pontosDosBausDaIlha(int k, int ilhas, int anel,
                                                  int raio, int quantos) {
        int[] centro = centroDaIlha(k, ilhas, anel);
        if (centro == null) {
            return List.of();
        }
        List<int[]> saida = new ArrayList<>();
        int anelzinho = Math.max(1, raio - 2);
        for (int q = 0; q < Math.max(1, quantos); q++) {
            double angulo = -Math.PI / 2 + 2 * Math.PI * q
                    / Math.max(1, quantos);
            saida.add(new int[]{
                    centro[0] + (int) Math.round(Math.cos(angulo) * anelzinho),
                    centro[1] + (int) Math.round(Math.sin(angulo) * anelzinho)});
        }
        return List.copyOf(saida);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int danoDoSoco() {
        return Math.max(1, config.getInt("regras.dano-do-soco", 6));
    }

    public int vidaCheia() {
        return Math.max(20, config.getInt("regras.vida-cheia", 100));
    }

    public int periodoDoGolpe() {
        return Math.max(2, config.getInt("regras.periodo-do-golpe",
                ArenaPvpCatalog.TIQUES_DO_GOLPE));
    }

    public int danoDaFlecha() {
        return Math.max(1, config.getInt("regras.dano-da-flecha", 30));
    }

    public double forcaMinima() {
        return Math.min(1.0, Math.max(0.05,
                config.getDouble("regras.forca-minima", 0.3)));
    }

    public Material itemDeCura() {
        return cor("item-de-cura", Material.GOLDEN_APPLE);
    }

    public int curaDoItem() {
        return Math.max(1, config.getInt("regras.cura-do-item", 35));
    }

    public int curasNoBau() {
        return Math.max(0, config.getInt("regras.curas-no-bau", 2));
    }

    public double empurrao() {
        return Math.max(0.0, config.getDouble("regras.empurrao", 0.5));
    }

    public double empurraoAltura() {
        return Math.max(0.0, config.getDouble("regras.empurrao-altura", 0.3));
    }

    public int creditoSegundos() {
        return Math.max(1, config.getInt("regras.credito-segundos", 8));
    }

    public int segundosParaOMeio() {
        return Math.max(0, config.getInt("regras.segundos-para-o-meio", 45));
    }

    public int segundosDeCerco() {
        return Math.max(10, config.getInt("regras.segundos-de-cerco", 90));
    }

    public int segundosPorIlha() {
        return Math.max(2, config.getInt("regras.segundos-por-ilha", 6));
    }

    public int avisoSegundos() {
        return Math.max(1, Math.min(segundosPorIlha(),
                config.getInt("regras.aviso-segundos", 4)));
    }

    public int pontosPorQueda() {
        return Math.max(0, config.getInt("regras.pontos-por-queda", 50));
    }

    // ------------------------------------------------------------------
    //  Os níveis de kit
    // ------------------------------------------------------------------

    public List<String> nivelEspada() {
        List<String> lista = config.getStringList("niveis.espada");
        return lista.isEmpty() ? List.of("WOODEN_SWORD") : List.copyOf(lista);
    }

    public List<Integer> nivelDano() {
        List<Integer> lista = config.getIntegerList("niveis.dano");
        return lista.isEmpty() ? List.of(14) : List.copyOf(lista);
    }

    public List<Integer> nivelBlocos() {
        List<Integer> lista = config.getIntegerList("niveis.blocos");
        return lista.isEmpty() ? List.of(24) : List.copyOf(lista);
    }

    public List<Integer> nivelFlechas() {
        List<Integer> lista = config.getIntegerList("niveis.flechas");
        return lista.isEmpty() ? List.of(0) : List.copyOf(lista);
    }

    public int quantosNiveis() {
        return Math.min(nivelEspada().size(), Math.min(nivelDano().size(),
                Math.min(nivelBlocos().size(), nivelFlechas().size())));
    }

    /**
     * O nível do kit da partida: sorteado entre os de baixo.
     *
     * De baixo porque o meio é sempre um acima, e o de cima tem de sobrar
     * para ele. Se o kit da partida pudesse ser o melhor nível, o baú do meio
     * não teria para onde subir e a ilha do meio viraria enfeite — que é
     * exatamente a reclamação "o baú do meio é inútil e chato".
     */
    public static int nivelDaPartida(int quantos, java.util.Random sorteio) {
        int tetoDoKit = Math.max(1, quantos - 1);
        return sorteio.nextInt(tetoDoKit);
    }

    public static int nivelDoMeio(int nivelDaPartida, int quantos) {
        return Math.min(Math.max(0, quantos - 1), nivelDaPartida + 1);
    }

    public Material espadaDoNivel(int nivel) {
        List<String> lista = nivelEspada();
        String nome = lista.get(Math.max(0,
                Math.min(lista.size() - 1, nivel)));
        Material qual = Material.matchMaterial(nome.trim().toUpperCase());
        return qual == null ? Material.WOODEN_SWORD : qual;
    }

    public int danoDoNivel(int nivel) {
        return daLista(nivelDano(), nivel, 14);
    }

    public int blocosDoNivel(int nivel) {
        return daLista(nivelBlocos(), nivel, 24);
    }

    public int flechasDoNivel(int nivel) {
        return daLista(nivelFlechas(), nivel, 0);
    }

    private static int daLista(List<Integer> lista, int nivel, int padrao) {
        if (lista.isEmpty()) {
            return padrao;
        }
        return lista.get(Math.max(0, Math.min(lista.size() - 1, nivel)));
    }

    public Material blocoDePonte() {
        return cor("bloco-de-ponte", Material.LIGHT_BLUE_CONCRETE);
    }

    public List<String> nivelArmadura() {
        List<String> lista = config.getStringList("niveis.armadura");
        return lista.isEmpty() ? List.of("LEATHER") : List.copyOf(lista);
    }

    public List<Integer> nivelReducao() {
        List<Integer> lista = config.getIntegerList("niveis.reducao");
        return lista.isEmpty() ? List.of(4) : List.copyOf(lista);
    }

    /** Quanto cada peça equipada do nível tira de dano, em porcento. */
    public int reducaoDoNivel(int nivel) {
        return daLista(nivelReducao(), nivel, 4);
    }

    /**
     * O teto da redução somada, em porcento.
     *
     * Existe porque as peças somam e quatro peças do melhor nível somariam
     * quarenta por cento — e um teto escrito é o que impede que mexer numa
     * lista do yml transforme a vítima em parede. Sem ele, acrescentar um
     * quinto nível de armadura faria a luta nunca acabar e nada no yml
     * pareceria errado.
     */
    public int reducaoMaxima() {
        return Math.max(0, Math.min(80, config.getInt("regras.reducao-maxima", 40)));
    }

    /** Uma soma de porcentagens virada em fração, com o teto aplicado. */
    public static double aparo(int somaPorCento, int teto) {
        return Math.min(Math.max(0, teto), Math.max(0, somaPorCento)) / 100.0;
    }

    /** A redução somada de tantas peças iguais, já com o teto aplicado. */
    public static double reducaoTotal(int pecas, int porPeca, int teto) {
        return aparo(Math.max(0, pecas) * Math.max(0, porPeca), teto);
    }

    /** O dano que sobra depois da armadura. Nunca zero: armadura não é imunidade. */
    public static int danoComArmadura(int dano, double reducao) {
        return Math.max(1, (int) Math.round(dano * (1.0 - reducao)));
    }

    /** Quantos golpes derrubam alguém de vida cheia, com tanta armadura. */
    public static double golpesPara(int vida, int dano, double reducao) {
        return vida / (double) danoComArmadura(dano, reducao);
    }

    /** A família de armadura do nível, como PREFIXO: IRON, DIAMOND... */
    public String armaduraDoNivel(int nivel) {
        List<String> lista = nivelArmadura();
        return lista.get(Math.max(0, Math.min(lista.size() - 1, nivel)))
                .trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** As quatro peças da família, na ordem capacete, peito, calça, bota. */
    public static List<Material> pecasDaFamilia(String familia) {
        List<Material> saida = new ArrayList<>();
        for (String peca : PECAS) {
            Material qual = Material.matchMaterial(familia + "_" + peca);
            if (qual != null) {
                saida.add(qual);
            }
        }
        return List.copyOf(saida);
    }

    /** Os sufixos das quatro peças, na ordem em que se vestem. */
    public static final List<String> PECAS =
            List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    // ------------------------------------------------------------------
    //  O saque do baú da ilha
    // ------------------------------------------------------------------

    public int armaduraMinima() {
        return Math.max(0, Math.min(4, config.getInt("saque.armadura-minima", 1)));
    }

    public int armaduraMaxima() {
        return Math.max(armaduraMinima(),
                Math.min(4, config.getInt("saque.armadura-maxima", 2)));
    }

    public int blocosGarantidos() {
        return Math.max(1, config.getInt("saque.blocos-garantidos", 24));
    }

    public int blocosExtras() {
        return Math.max(0, config.getInt("saque.blocos-extras", 16));
    }

    public int chanceDeArco() {
        return Math.max(0, Math.min(100, config.getInt("saque.chance-de-arco", 40)));
    }

    public int chanceDeCura() {
        return Math.max(0, Math.min(100, config.getInt("saque.chance-de-cura", 25)));
    }

    public int curasNaIlha() {
        return Math.max(1, config.getInt("saque.curas-na-ilha", 1));
    }

    public Material itemDeComida() {
        return cor("item-de-comida", Material.COOKED_BEEF);
    }

    public int comida() {
        return Math.max(1, config.getInt("saque.comida", 3));
    }

    // ------------------------------------------------------------------
    //  As ferramentas do gênero
    //
    //  Vieram do estudo do SkyWarsReloaded: o baú dele carrega balde
    //  d'água e balde de lava no grupo garantido e bolas de neve no grupo
    //  de 40%. Nossa arena tinha o EQUIPAMENTO todo — espada, armadura,
    //  arco, bloco de ponte — e nenhuma ferramenta, que é o que se usa no
    //  outro em vez de em si mesmo. Num jogo em que todo mundo anda sobre
    //  uma fileira de blocos, lavar alguém para fora da ponte é a jogada
    //  mais característica que existe, e ela não estava no jogo.
    // ------------------------------------------------------------------

    public int chanceDeAgua() {
        return porcento("saque.ferramentas.chance-de-agua", 35);
    }

    public int chanceDeLava() {
        return porcento("saque.ferramentas.chance-de-lava", 16);
    }

    public int chanceDeBolas() {
        return porcento("saque.ferramentas.chance-de-bolas", 45);
    }

    public int bolasDeNeve() {
        return Math.max(1, Math.min(64,
                config.getInt("saque.ferramentas.bolas-de-neve", 8)));
    }

    /** A pérola só existe no baú do meio: é a recompensa de atravessar. */
    public int perolasNoMeio() {
        return Math.max(0, Math.min(8,
                config.getInt("saque.ferramentas.perolas-no-meio", 1)));
    }

    private int porcento(String chave, int padrao) {
        return Math.max(0, Math.min(100, config.getInt(chave, padrao)));
    }

    public double empurraoDaBolaForca() {
        return Math.max(0.0, Math.min(2.0,
                config.getDouble("regras.empurrao-da-bola", 0.45)));
    }

    public double empurraoDaBolaAlto() {
        return Math.max(0.0, Math.min(2.0,
                config.getDouble("regras.empurrao-da-bola-alto", 0.3)));
    }

    /**
     * Quanto a lava tira por segundo.
     *
     * Ele existe porque o dano do Minecraft está cancelado nesta arena
     * inteira. Sem este número o balde de lava seria tinta laranja: fecharia
     * a ponte e não cobraria nada de quem atravessasse.
     */
    public int danoDaLava() {
        return Math.max(0, Math.min(vidaCheia(),
                config.getInt("regras.dano-da-lava", 12)));
    }

    /**
     * Quantos segundos a água e a lava do balde duram.
     *
     * Elas secam porque este mundo não tem chão: água derramada na beira
     * escorre para o vazio e fica escorrendo para sempre, e a arena da
     * segunda partida não seria a mesma da primeira.
     */
    public int baldeSegundos() {
        return Math.max(2, Math.min(120,
                config.getInt("regras.balde-segundos", 10)));
    }

    /**
     * O que um baú de ilha recebeu.
     *
     * Um registro e não quatro variáveis soltas porque é ele que o teste
     * sorteia dez mil vezes para conferir o piso: a pergunta "existe algum
     * sorteio que não dá espada?" só tem resposta se o sorteio devolver um
     * valor em vez de mexer num inventário.
     */
    public record Saque(Material espada, List<Material> armadura, int blocos,
                        boolean arco, int flechas, int curas, int comida,
                        boolean agua, boolean lava, int bolas) {
    }

    /**
     * As chances das ferramentas, num pacote só.
     *
     * Num registro e não em quatro parâmetros soltos porque
     * {@link #sortearSaque} já recebia onze: o décimo-quinto argumento
     * posicional é onde se troca `chanceArco` por `chanceCura` sem o
     * compilador notar, já que os dois são int.
     */
    public record Ferramentas(int chanceAgua, int chanceLava, int chanceBolas,
                              int bolas) {
    }

    /**
     * Sorteia o conteúdo de um baú de ilha.
     *
     * A estrutura é "piso garantido, sorteio por cima", e cada linha abaixo é
     * de um dos dois lados:
     *
     * <ul>
     *   <li>GARANTIDO: a espada do nível da partida — a mesma em toda ilha,
     *       porque é na arma que a reclamação do gênero morde —, a comida, os
     *       {@code blocosGarantidos} e {@code armaduraMinima} peças.</li>
     *   <li>SORTEADO: quantas peças acima do mínimo (até
     *       {@code armaduraMaxima}, nunca as quatro), QUAIS peças, quantos
     *       blocos extras, se veio arco, se veio cura.</li>
     * </ul>
     *
     * <p>Nada do que é sorteado sobe o dano da espada. É de propósito: o arco
     * é outra jogada, a armadura é defesa com teto, e o bloco é mobilidade.
     * Assim o baú pode ser mais sortudo que o do vizinho sem que isso decida
     * a luta antes de ela começar — que é a reclamação que a primeira versão
     * desta arena tentou resolver apagando o baú inteiro.
     */
    public static Saque sortearSaque(Material espada, List<Material> familia,
                                     int minimo, int maximo, int blocosPiso,
                                     int blocosExtras, int chanceArco,
                                     int flechas, int chanceCura, int curas,
                                     int comida, Ferramentas ferramentas,
                                     java.util.Random sorteio) {
        int quantasPecas = minimo + (maximo > minimo
                ? sorteio.nextInt(maximo - minimo + 1) : 0);
        List<Material> baralho = new ArrayList<>(familia);
        java.util.Collections.shuffle(baralho, sorteio);
        List<Material> pecas = List.copyOf(baralho.subList(0,
                Math.min(quantasPecas, baralho.size())));

        int blocos = blocosPiso
                + (blocosExtras > 0 ? sorteio.nextInt(blocosExtras + 1) : 0);
        boolean temArco = flechas > 0 && sorteio.nextInt(100) < chanceArco;
        int temCura = sorteio.nextInt(100) < chanceCura ? curas : 0;

        // As ferramentas, uma a uma e cada uma com a sua chance. Sorteadas
        // SEMPRE, mesmo quando a chance é zero: o sorteio tem de gastar o
        // mesmo número de valores do gerador em toda configuração, senão
        // mudar `chance-de-agua` no yml mudaria todo o resto do baú junto.
        boolean temAgua = sorteio.nextInt(100) < ferramentas.chanceAgua();
        boolean temLava = sorteio.nextInt(100) < ferramentas.chanceLava();
        boolean temBolas = sorteio.nextInt(100) < ferramentas.chanceBolas();
        return new Saque(espada, pecas, blocos, temArco,
                temArco ? flechas : 0, temCura, comida, temAgua, temLava,
                temBolas ? Math.max(1, ferramentas.bolas()) : 0);
    }

    /** O mesmo, com os números desta planta e o nível da partida. */
    public Ferramentas ferramentas() {
        return new Ferramentas(chanceDeAgua(), chanceDeLava(),
                chanceDeBolas(), bolasDeNeve());
    }

    public Saque sortearSaque(int nivel, java.util.Random sorteio) {
        return sortearSaque(espadaDoNivel(nivel),
                pecasDaFamilia(armaduraDoNivel(nivel)), armaduraMinima(),
                armaduraMaxima(), blocosGarantidos(), blocosExtras(),
                chanceDeArco(), flechasDoNivel(nivel), chanceDeCura(),
                curasNaIlha(), comida(), ferramentas(), sorteio);
    }

    // ------------------------------------------------------------------
    //  O anel que cai
    // ------------------------------------------------------------------

    /**
     * A ordem em que as ilhas caem: dando a volta, de um ponto sorteado.
     *
     * Em ORDEM e não embaralhada. Embaralhada, a queda da sua ilha é sorte e o
     * jogador só pode reagir; dando a volta, ele CONTA quantas faltam para a
     * dele e decide quando sair — e decidir quando sair é a jogada que este
     * fim de partida existe para criar.
     */
    public static List<Integer> ordemDaQueda(int ilhas, int comeco) {
        List<Integer> saida = new ArrayList<>();
        for (int k = 0; k < Math.max(0, ilhas); k++) {
            saida.add((comeco + k) % Math.max(1, ilhas));
        }
        return List.copyOf(saida);
    }

    /** Em que segundo de partida a ilha de índice {@code posicao} cai. */
    public int segundoDaQueda(int posicao) {
        return segundoDaQueda(posicao, segundosDeCerco(), segundosPorIlha());
    }

    public static int segundoDaQueda(int posicao, int cerco, int porIlha) {
        return cerco + Math.max(0, posicao) * porIlha;
    }

    /** Quanto tempo o anel inteiro leva para cair. */
    public int segundoDaUltimaQueda() {
        return segundoDaQueda(ilhas() - 1);
    }
}
