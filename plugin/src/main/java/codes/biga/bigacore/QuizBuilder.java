package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Levanta o átrio, as pontes e os tablados do Quiz.
 *
 * <h2>Metade da conferência é sobre o CONTEÚDO</h2>
 *
 * Nos outros jogos a conferência é geométrica: bacia pendurada no vazio,
 * largada dentro de um muro, tablado que não cabe na plataforma. Aqui a
 * geometria é fácil — quatro discos num anel — e o que estraga o jogo é o
 * banco de perguntas, que não dá erro nenhum ao estar errado:
 *
 * <ul>
 *   <li>Resposta certa quase sempre na mesma posição. Uma rodada e os
 *       jogadores decoram a posição em vez de saber a resposta.</li>
 *   <li>Nível com o número exato de perguntas que a rodada pede. Toda rodada
 *       sai idêntica, e na terceira todo mundo já sabe as doze.</li>
 *   <li>Opção comprida demais para a placa, pergunta comprida demais para o
 *       painel. O texto corta e o jogador responde o que não leu.</li>
 *   <li>Pergunta repetida no banco. Cai duas vezes na mesma partida.</li>
 * </ul>
 *
 * <h2>A conferência do relógio</h2>
 *
 * {@link #conferirCorrida} é a única conta não óbvia da obra: a janela de
 * resposta tem de dar para sair do átrio e atravessar a ponte CORRENDO, com
 * folga. Com uma janela curta demais o jogo não é de conhecimento, é de
 * reflexo — e com o anel largo demais, o jogador que largou do lado errado do
 * átrio perde por geometria.
 */
public final class QuizBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_quiz_painel";

    /** Os painéis da pergunta e das opções, refeitos a cada pergunta. */
    public static final String TAG_PERGUNTA = "bigacore_quiz_pergunta";

    /** Quantos blocos por segundo um jogador corre, de verdade. */
    public static final double CORRIDA = 5.6;

    /** Quantos segundos de folga a travessia tem de sobrar. */
    private static final double FOLGA_DA_CORRIDA = 2.0;

    /** Quantas vezes o nível tem de ter o que a rodada pede dele. */
    private static final int SOBRA_DO_NIVEL = 2;

    /** O maior texto que cabe numa linha de placa sem cortar. */
    public static final int LETRAS_NA_PLACA = 14;

    /** O maior texto de pergunta que cabe no painel do átrio. */
    public static final int LETRAS_NA_PERGUNTA = 70;

    private final BigaCore plugin;
    private int contador;

    public QuizBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        QuizCatalog planta = plugin.quizCatalog();

        String problema = conferirBanco(planta.banco(), planta.porNivel());
        if (problema == null) {
            problema = conferirNumeros(planta.atrioRaio(), planta.tablados(),
                    planta.tabladoRaio(), planta.tabladoAnel(),
                    planta.ponteLargura());
        }
        if (problema == null) {
            problema = conferirCorrida(planta.atrioRaio(), planta.tabladoAnel(),
                    planta.tabladoRaio(), planta.tempoResposta());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.perguntasPorRodada(),
                    planta.porNivel(), planta.tempoResposta(),
                    planta.tempoRevelacao(), planta.quedaTiques(),
                    planta.tempoLimite());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Átrio de raio " + planta.atrioRaio() + " na fiada "
                    + planta.topoY() + " com " + planta.tablados()
                    + " tablados no anel " + planta.tabladoAnel() + ". Banco com "
                    + planta.banco().size() + " perguntas, rodada de "
                    + planta.perguntasPorRodada() + " em "
                    + planta.porNivel() + ". Saguão em y " + planta.saguaoY()
                    + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga quiz desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_PERGUNTA);
        atrio(mundo, planta);
        pontes(mundo, planta);
        for (int k = 0; k < planta.tablados(); k++) {
            tablado(mundo, planta, k);
        }
        postes(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDosTablados(mundo, planta, null);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Quiz levantado: " + contador + " blocos, "
                + planta.tablados() + " tablados e " + planta.banco().size()
                + " perguntas no banco. O spawn do mundo foi posto no saguão.",
                contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        QuizCatalog planta = plugin.quizCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_PERGUNTA);
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
        return new Resultado(true, "Quiz apagado: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        QuizCatalog planta = plugin.quizCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_PERGUNTA);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDosTablados(mundo, planta, null);
        return new Resultado(true, "Letreiros e placas dos tablados refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * O banco de perguntas serve.
     *
     * A conferência da POSIÇÃO da resposta certa é a que importa e é a que
     * ninguém faz à mão: num banco de quarenta e oito perguntas escrito de
     * uma sentada, a certa cai na quarta opção uma ou duas vezes, porque
     * quem escreve põe a resposta onde ela soa melhor na leitura. O jogo
     * continua funcionando e fica quebrado: na segunda rodada os jogadores
     * já sabem que o tablado D quase nunca é a resposta.
     */
    static String conferirBanco(List<QuizCatalog.Pergunta> banco,
                                List<Integer> porNivel) {
        if (banco.isEmpty()) {
            return "O banco de perguntas está vazio: sem pergunta, o Quiz é um "
                    + "átrio com quatro tablados e um relógio.";
        }
        Set<String> vistas = new HashSet<>();
        for (QuizCatalog.Pergunta pergunta : banco) {
            String texto = pergunta.texto();
            if (texto == null || texto.isBlank()) {
                return "Há uma pergunta sem texto no banco.";
            }
            if (texto.length() > LETRAS_NA_PERGUNTA) {
                return "A pergunta \"" + texto + "\" tem " + texto.length()
                        + " letras e o painel do átrio aguenta "
                        + LETRAS_NA_PERGUNTA + ": ela cortaria na tela e o "
                        + "jogador responderia o que não leu.";
            }
            if (!vistas.add(texto)) {
                return "A pergunta \"" + texto + "\" está duas vezes no banco: "
                        + "ela cairia duas vezes na mesma partida.";
            }
            if (pergunta.opcoes().size() < 2) {
                return "A pergunta \"" + texto + "\" tem "
                        + pergunta.opcoes().size() + " opção(ões).";
            }
            if (pergunta.certa() < 0 || pergunta.certa() >= pergunta.opcoes().size()) {
                return "A pergunta \"" + texto + "\" diz que a certa é a de "
                        + "número " + pergunta.certa() + " e só tem "
                        + pergunta.opcoes().size() + " opções — lembre que a "
                        + "contagem começa em zero.";
            }
            Set<String> opcoes = new HashSet<>();
            for (String opcao : pergunta.opcoes()) {
                if (opcao == null || opcao.isBlank()) {
                    return "A pergunta \"" + texto + "\" tem uma opção vazia.";
                }
                if (opcao.length() > LETRAS_NA_PLACA) {
                    return "A opção \"" + opcao + "\" da pergunta \"" + texto
                            + "\" tem " + opcao.length() + " letras e a placa "
                            + "do tablado aguenta " + LETRAS_NA_PLACA + ".";
                }
                if (!opcoes.add(opcao)) {
                    return "A pergunta \"" + texto + "\" tem a opção \"" + opcao
                            + "\" duas vezes: haveria dois tablados certos.";
                }
            }
        }

        for (int k = 0; k < porNivel.size(); k++) {
            int nivel = k + 1;
            int pede = porNivel.get(k);
            if (pede <= 0) {
                continue;
            }
            int tem = QuizCatalog.quantasNoNivel(banco, nivel);
            if (tem < pede) {
                return "A rodada pede " + pede + " pergunta(s) de nível "
                        + nivel + " e o banco só tem " + tem + ".";
            }
            if (tem < pede * SOBRA_DO_NIVEL) {
                return "O nível " + nivel + " tem " + tem + " perguntas e a "
                        + "rodada pede " + pede + ": com tão pouca sobra, toda "
                        + "rodada sai quase igual à anterior e na terceira "
                        + "partida já se sabem todas. O mínimo é "
                        + (pede * SOBRA_DO_NIVEL) + ".";
            }
        }

        return conferirPosicoes(banco);
    }

    /** A resposta certa se espalha pelas posições em vez de viciar numa. */
    private static String conferirPosicoes(List<QuizCatalog.Pergunta> banco) {
        int opcoes = QuizCatalog.maiorNumeroDeOpcoes(banco);
        if (opcoes < 2) {
            return null;
        }
        for (int posicao = 0; posicao < opcoes; posicao++) {
            int elegiveis = 0;
            int certas = 0;
            for (QuizCatalog.Pergunta pergunta : banco) {
                if (pergunta.opcoes().size() <= posicao) {
                    continue;
                }
                elegiveis++;
                if (pergunta.certa() == posicao) {
                    certas++;
                }
            }
            if (elegiveis == 0) {
                continue;
            }
            int minimo = elegiveis / (opcoes * 2);
            int maximo = elegiveis * 2 / opcoes;
            if (certas < minimo || certas > maximo) {
                return "A resposta certa é a opção "
                        + QuizCatalog.LETRAS[Math.min(posicao,
                        QuizCatalog.LETRAS.length - 1)] + " em " + certas
                        + " das " + elegiveis + " perguntas, e o justo seria "
                        + "perto de " + (elegiveis / opcoes) + " (entre "
                        + minimo + " e " + maximo + "). Um banco torto assim "
                        + "faz o jogador decorar a posição em vez de saber a "
                        + "resposta — troque a ordem das opções de algumas "
                        + "perguntas.";
            }
        }
        return null;
    }

    /** A geometria fecha: os tablados não se encostam e as pontes existem. */
    static String conferirNumeros(int atrioRaio, int tablados, int tabladoRaio,
                                  int anel, int ponteLargura) {
        if (tablados < 2) {
            return "O Quiz tem " + tablados + " tablado(s): com menos de dois "
                    + "não há resposta errada em que pisar.";
        }
        if (tablados > QuizCatalog.LETRAS.length) {
            return "O Quiz tem " + tablados + " tablados e só há "
                    + QuizCatalog.LETRAS.length + " letras para eles.";
        }
        if (ponteLargura % 2 == 0) {
            return "A ponte tem " + ponteLargura + " blocos de largura: com "
                    + "largura par ela não fica centrada no eixo e um dos "
                    + "lados sai mais perto do tablado que o outro.";
        }
        if (ponteLargura > atrioRaio * 2 - 1) {
            return "A ponte tem " + ponteLargura + " de largura e o átrio só "
                    + (atrioRaio * 2 + 1) + " de diâmetro: as quatro pontes se "
                    + "encostariam e o átrio deixaria de ter beira.";
        }
        int vao = anel - tabladoRaio - atrioRaio;
        if (vao < 3) {
            return "Entre a beira do átrio e a beira do tablado sobram " + vao
                    + " bloco(s) de ponte: com tão pouco, pisar na resposta "
                    + "deixa de ser uma decisão — basta cair para o lado.";
        }
        for (int k = 0; k < tablados; k++) {
            int[] centro = QuizCatalog.centroDoTablado(k, tablados, anel);
            for (int o = k + 1; o < tablados; o++) {
                int[] outro = QuizCatalog.centroDoTablado(o, tablados, anel);
                double entre = Math.hypot(centro[0] - outro[0],
                        centro[1] - outro[1]);
                if (entre < tabladoRaio * 2 + 3) {
                    return "Os tablados " + QuizCatalog.LETRAS[k] + " e "
                            + QuizCatalog.LETRAS[o] + " estão a "
                            + Math.round(entre) + " um do outro e precisam de "
                            + (tabladoRaio * 2 + 3) + ": encostados, eles "
                            + "viram um tablado só com duas respostas.";
                }
            }
            int[] poste = QuizCatalog.posteDoTablado(k, tablados, anel, tabladoRaio);
            if (QuizCatalog.tabladoDoPonto(poste[0], poste[1], tablados, anel,
                    tabladoRaio) >= 0) {
                return "O poste da placa do tablado " + QuizCatalog.LETRAS[k]
                        + " nasce dentro de um tablado, em (" + poste[0] + ","
                        + poste[1] + ") — e ele tem de ficar de fora, porque o "
                        + "tablado some quando a resposta está errada.";
            }
        }
        return null;
    }

    /**
     * A travessia cabe na janela de resposta, correndo, com folga.
     *
     * A pior travessia é a de quem largou na beira OPOSTA do átrio: ele
     * atravessa o átrio inteiro e a ponte toda. Se isso não couber com folga,
     * o jogo deixa de ser de conhecimento e passa a ser de reflexo — e quem
     * largou do lado errado perde por geometria.
     */
    static String conferirCorrida(int atrioRaio, int anel, int tabladoRaio,
                                  int tempoResposta) {
        double caminho = atrioRaio + anel - tabladoRaio;
        double gasta = caminho / CORRIDA;
        if (gasta + FOLGA_DA_CORRIDA > tempoResposta) {
            return "A pior travessia é de " + Math.round(caminho)
                    + " blocos, que correndo leva "
                    + Math.round(gasta * 10) / 10.0 + "s, e a janela de "
                    + "resposta é de " + tempoResposta + "s — a folga ficaria "
                    + "abaixo dos " + FOLGA_DA_CORRIDA + "s necessários. "
                    + "Aumente regras.tempo-resposta ou aproxime os tablados.";
        }
        return null;
    }

    /** A rodada inteira cabe no tempo limite, e a queda cabe na revelação. */
    static String conferirRelogio(int perguntas, List<Integer> porNivel,
                                  int tempoResposta, int tempoRevelacao,
                                  int quedaTiques, int tempoLimite) {
        int soma = 0;
        for (int quantas : porNivel) {
            soma += Math.max(0, quantas);
        }
        if (soma != perguntas) {
            return "A rodada é de " + perguntas + " perguntas e a divisão por "
                    + "nível soma " + soma + " " + porNivel + ".";
        }
        if (quedaTiques >= tempoRevelacao * 20) {
            return "A queda leva " + quedaTiques + " tiques e a revelação dura "
                    + (tempoRevelacao * 20) + ": quem errou seria tirado da "
                    + "rodada depois de a pergunta seguinte já estar na tela.";
        }
        int precisa = perguntas * (tempoResposta + tempoRevelacao);
        if (precisa > tempoLimite) {
            return "As " + perguntas + " perguntas levam " + precisa
                    + "s e o tempo limite é " + tempoLimite + "s: as últimas "
                    + "perguntas nunca seriam feitas, e um jogo de eliminação "
                    + "que acaba no relógio acaba sem ninguém eliminado.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** O átrio: o disco do meio, de onde todo mundo parte. */
    private void atrio(World mundo, QuizCatalog planta) {
        Material piso = planta.cor("atrio", Material.POLISHED_BLACKSTONE);
        Material beira = planta.cor("atrio-beira",
                Material.CHISELED_POLISHED_BLACKSTONE);
        int raio = planta.atrioRaio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira : piso);
            }
        }
    }

    /** As pontes: do átrio a cada tablado. */
    private void pontes(World mundo, QuizCatalog planta) {
        Material ponte = planta.cor("ponte", Material.POLISHED_BLACKSTONE_BRICKS);
        int alcance = planta.tabladoAnel();
        int y = planta.topoY();

        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                if (!planta.naPonte(x, z)) {
                    continue;
                }
                if (ArenaComum.noDiscoMeio(x, z, planta.atrioRaio())
                        || planta.tabladoDoPonto(x, z) >= 0) {
                    continue;
                }
                por(mundo, x, y, z, ponte);
            }
        }
    }

    /** Um tablado: o disco colorido da resposta. */
    private void tablado(World mundo, QuizCatalog planta, int indice) {
        int[] centro = planta.centroDoTablado(indice);
        int raio = planta.tabladoRaio();
        int y = planta.topoY();
        Material chao = planta.corDoTablado(indice);
        Material beira = planta.cor("tablado-beira", Material.POLISHED_BLACKSTONE);

        for (int dx = -raio; dx <= raio; dx++) {
            for (int dz = -raio; dz <= raio; dz++) {
                if (!ArenaComum.noDiscoMeio(dx, dz, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(dx, dz, raio - 1);
                por(mundo, centro[0] + dx, y, centro[1] + dz,
                        naBeira ? beira : chao);
            }
        }
    }

    /**
     * Refaz os tablados que sumiram na pergunta anterior.
     *
     * Vale por {@link #por}: o bloco só é escrito quando está diferente, e
     * então refazer três tablados intactos e um apagado custa o disco de um,
     * e não de quatro.
     */
    public int refazerTablados(World mundo) {
        QuizCatalog planta = plugin.quizCatalog();
        contador = 0;
        for (int k = 0; k < planta.tablados(); k++) {
            tablado(mundo, planta, k);
        }
        return contador;
    }

    /** Apaga o piso de um tablado: quem está nele cai. */
    public int apagarTablado(World mundo, int indice) {
        QuizCatalog planta = plugin.quizCatalog();
        int[] centro = planta.centroDoTablado(indice);
        int raio = planta.tabladoRaio();
        int y = planta.topoY();
        contador = 0;
        for (int dx = -raio; dx <= raio; dx++) {
            for (int dz = -raio; dz <= raio; dz++) {
                if (ArenaComum.noDiscoMeio(dx, dz, raio)) {
                    por(mundo, centro[0] + dx, y, centro[1] + dz, Material.AIR);
                }
            }
        }
        return contador;
    }

    /** Um poste com luz do lado de fora de cada tablado. */
    private void postes(World mundo, QuizCatalog planta) {
        Material poste = planta.cor("poste", Material.CRIMSON_FENCE);
        Material luz = planta.cor("poste-luz", Material.SHROOMLIGHT);
        int y = planta.topoY();

        for (int k = 0; k < planta.tablados(); k++) {
            int[] onde = planta.posteDoTablado(k);
            por(mundo, onde[0], y, onde[1],
                    planta.cor("escuro", Material.POLISHED_BLACKSTONE));
            for (int alto = 1; alto <= SaguaoPadrao.FIADA_DA_PLACA + 1; alto++) {
                por(mundo, onde[0], y + alto, onde[1], poste);
            }
            por(mundo, onde[0], y + SaguaoPadrao.FIADA_DA_PLACA + 2, onde[1], luz);
        }
    }

    /**
     * A placa de cada tablado: a letra dele e a opção da pergunta de agora.
     *
     * Com {@code pergunta} nula ela volta a dizer só a letra, que é como a
     * arena fica entre rodadas — uma placa parada com a opção da última
     * pergunta diria a resposta errada a quem chegasse depois.
     */
    public void placasDosTablados(World mundo, QuizCatalog planta,
                                  QuizCatalog.Pergunta pergunta) {
        for (int k = 0; k < planta.tablados(); k++) {
            int[] onde = planta.posteDoTablado(k);
            BlockFace frente = QuizCatalog.frenteDaPlaca(onde[0], onde[1]);
            int[] alvo = {onde[0] + frente.getModX(),
                    planta.topoY() + SaguaoPadrao.FIADA_DA_PLACA,
                    onde[1] + frente.getModZ()};
            String opcao = pergunta == null || k >= pergunta.opcoes().size()
                    ? "" : pergunta.opcoes().get(k);
            ArenaComum.escreverPlaca(mundo, alvo, planta.materialDaPlaca(),
                    frente, List.of(
                            plugin.mini().deserialize("<color:"
                                    + QuizCatalog.corDaLetra(k) + "><b>"
                                    + planta.letraDo(k) + "</b></color>"),
                            plugin.mini().deserialize(opcao.isEmpty() ? ""
                                    : "<white>" + HubBuilder.escapar(opcao)
                                    + "</white>"),
                            Component.empty(),
                            plugin.mini().deserialize(opcao.isEmpty()
                                    ? "<gray>aguardando</gray>"
                                    : "<gray>pise aqui</gray>")));
        }
    }

    /** Quatro torres nas diagonais, longe das pontes, para a arena ter escala. */
    private void torres(World mundo, QuizCatalog planta) {
        Material torre = planta.cor("torre", Material.POLISHED_BLACKSTONE_BRICKS);
        Material luz = planta.cor("torre-luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.tabladoAnel() + planta.tabladoRaio() + 4;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 5 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    private int contarOcupados(World mundo, QuizCatalog planta) {
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
    //  Os painéis da pergunta
    // ------------------------------------------------------------------

    /**
     * Pendura os painéis da pergunta: um no átrio e um sobre cada tablado.
     *
     * <p>Não usa {@link ArenaComum#painel} por um motivo só, e é o motivo de
     * o Quiz ser diferente dos outros jogos: o letreiro compartilhado nasce
     * FIXO, virado para um lado, porque o painel de instrução do saguão é
     * lido de frente. Esta arena é lida das QUATRO direções ao mesmo tempo —
     * quem corre para o tablado norte está de costas para quem corre para o
     * sul — e um painel fixo seria ilegível para três dos quatro.
     */
    public void painelDaPergunta(World mundo, QuizCatalog planta,
                                 Component noAtrio, List<Component> nosTablados) {
        ArenaComum.limparPaineis(mundo, TAG_PERGUNTA);
        Location meio = new Location(mundo, 0.5,
                planta.topoY() + planta.painelAltura(), 0.5);
        virado(mundo, meio, noAtrio, (float) planta.escalaTitulo(),
                planta.fundoDoTexto());

        for (int k = 0; k < planta.tablados() && k < nosTablados.size(); k++) {
            int[] centro = planta.centroDoTablado(k);
            Location sobre = new Location(mundo, centro[0] + 0.5,
                    planta.topoY() + 3.2, centro[1] + 0.5);
            virado(mundo, sobre, nosTablados.get(k),
                    (float) planta.escalaPlaca() * 1.4f, planta.fundoDoTexto());
        }
    }

    private static void virado(World mundo, Location alvo, Component texto,
                               float escala, int fundoAlpha) {
        alvo.setYaw(0f);
        alvo.setPitch(0f);
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            // CENTER, e não FIXED: o painel acompanha quem olha.
            novo.setBillboard(Display.Billboard.CENTER);
            novo.setAlignment(TextDisplay.TextAlignment.CENTER);
            novo.setBackgroundColor(Color.fromARGB(fundoAlpha, 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(260);
            novo.setViewRange(2.0f);
            novo.setPersistent(false);
            novo.addScoreboardTag(TAG_PERGUNTA);

            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(escala, escala, escala), t.getRightRotation()));
        });
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.quizCatalog());
    }

    /** Onde um jogador nasce: no átrio, olhando para o meio. */
    public static Location pontoDaLargada(World mundo, QuizCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(5f);
        return pouso;
    }

    /** Onde quem errou vai ver o resto da partida. */
    public static Location pontoDaPlateia(World mundo, QuizCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }

    /** O meio de um tablado, para o som e a partícula. */
    public static Location pontoDoTablado(World mundo, QuizCatalog planta,
                                          int indice) {
        int[] centro = planta.centroDoTablado(indice);
        return new Location(mundo, centro[0] + 0.5, planta.topoY() + 1.2,
                centro[1] + 0.5);
    }
}
