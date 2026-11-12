package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Levanta o maciço da Mineração.
 *
 * <h2>O que se confere antes de pôr um bloco</h2>
 *
 * Uma mina tem três jeitos de sair errada e nenhum deles aparece olhando:
 *
 * <ul>
 *   <li>MINÉRIO DE MENOS. Dezesseis pessoas cavando quatro minutos tiram
 *       milhares de blocos. Se a tabela não puser minério bastante, a metade
 *       final da partida é todo mundo cavando pedra — e ninguém entende se
 *       foi azar ou se o jogo é assim;
 *   <li>MINÉRIO DE MAIS. Com minério por tudo, nenhuma rota é melhor que
 *       outra e a partida volta a ser teste de mouse;
 *   <li>FAIXA FORA DA MINA. Uma faixa que começa abaixo do bedrock, ou que
 *       termina acima da superfície, apaga aquele minério da mina inteira
 *       sem erro nenhum no log — e a tabela de pontos passa a mentir.
 * </ul>
 *
 * A conferência mede as três em cima da tabela de verdade, e a terceira
 * mede-as uma por uma por minério.
 */
public final class MineracaoBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_mina_painel";

    private final BigaCore plugin;
    private int contador;
    private long sementeDaVez;

    public MineracaoBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        MineracaoCatalog planta = plugin.mineracaoCatalog();

        String problema = conferirTabela(planta.tabela(), planta.lado(),
                planta.profundidade(), planta.fundoProfundo());
        if (problema == null) {
            problema = conferirRendimento(planta.tabela(), planta.lado(),
                    planta.profundidade(), planta.maximo(), planta.tempoLimite());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int[] amostra = MineracaoCatalog.gerar(planta.lado(),
                    planta.profundidade(), 7071L, planta.tabela());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Maciço de " + planta.lado() + "x" + planta.lado()
                    + "x" + planta.profundidade() + " com superfície na fiada "
                    + planta.topoY() + " e bedrock em " + planta.fundoY()
                    + ". Numa mina de amostra: "
                    + MineracaoCatalog.blocosDeMinerio(amostra) + " blocos de "
                    + "minério valendo " + MineracaoCatalog.pontosNaMina(amostra,
                    planta.tabela()) + " pontos. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga mina desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        refazerMina(mundo, planta.sementeFixa() >= 0 ? planta.sementeFixa() : 7071L);
        moldura(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Mina aberta: " + contador + " blocos. A "
                + "mina é sorteada de novo a cada rodada. O spawn do mundo foi "
                + "posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        MineracaoCatalog planta = plugin.mineracaoCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
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
        return new Resultado(true, "Mina apagada: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        MineracaoCatalog planta = plugin.mineracaoCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    /** A semente da mina que está de pé. */
    public long sementeDaVez() {
        return sementeDaVez;
    }

    /**
     * Sorteia e escreve uma mina nova.
     *
     * Escreve toda a coluna, o ar de cima incluído: é o que tapa os túneis da
     * rodada passada numa passada só. O {@link #por} não escreve bloco que já
     * esteja certo, então a parte intacta do maciço não custa nada.
     */
    public int refazerMina(World mundo, long semente) {
        MineracaoCatalog planta = plugin.mineracaoCatalog();
        this.sementeDaVez = semente;
        contador = 0;

        int lado = planta.lado();
        int meio = planta.meio();
        int fundura = planta.profundidade();
        int fundoProfundo = planta.fundoProfundo();
        int topo = planta.topoY();
        List<MineracaoCatalog.Veio> tabela = planta.tabela();
        int[] mina = MineracaoCatalog.gerar(lado, fundura, semente, tabela);

        Material rocha = planta.cor("rocha", Material.STONE);
        Material funda = planta.cor("rocha-funda", Material.DEEPSLATE);
        Material superficie = planta.cor("superficie", Material.COBBLESTONE);
        Material bedrock = planta.cor("moldura", Material.BEDROCK);

        for (int v = 0; v < lado; v++) {
            for (int u = 0; u < lado; u++) {
                int x = u - meio;
                int z = v - meio;
                por(mundo, x, planta.fundoY(), z, bedrock);
                for (int fiada = 0; fiada < fundura; fiada++) {
                    int qual = mina[MineracaoCatalog.indice(lado, fiada, u, v)];
                    Material bloco;
                    if (qual > 0 && qual <= tabela.size()) {
                        bloco = tabela.get(qual - 1).blocoNaFiada(fiada, fundoProfundo);
                    } else if (fiada == 0) {
                        bloco = superficie;
                    } else {
                        bloco = fiada >= fundoProfundo ? funda : rocha;
                    }
                    por(mundo, x, topo - fiada, z, bloco);
                }
                // O ar de cima: é o que tapa quem cavou para o céu.
                for (int alto = 1; alto <= 3; alto++) {
                    por(mundo, x, topo + alto, z, Material.AIR);
                }
            }
        }
        return contador;
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** Cada faixa de minério cabe na mina, e a tabela é coerente. */
    static String conferirTabela(List<MineracaoCatalog.Veio> tabela, int lado,
                                 int profundidade, int fundoProfundo) {
        if (tabela.isEmpty()) {
            return "A tabela de minérios está vazia: a mina seria um bloco de "
                    + "pedra e ninguém faria ponto.";
        }
        if (lado % 2 == 0) {
            return "'lado' é " + lado + ", que é par: o meio do maciço não "
                    + "cairia no (0,0), que é onde fica o saguão.";
        }
        if (fundoProfundo < 1 || fundoProfundo >= profundidade) {
            return "'fundo-profundo' é " + fundoProfundo + " numa mina de "
                    + profundidade + " fiadas: ou não há zona funda, ou ela "
                    + "come a mina toda.";
        }
        int antes = -1;
        int antesFundo = -1;
        int antesFundura = -1;
        for (MineracaoCatalog.Veio veio : tabela) {
            if (veio.fundo() < 0 || veio.fundura() >= profundidade) {
                return "O " + veio.nome() + " mora nas fiadas " + veio.fundo()
                        + " a " + veio.fundura() + ", e a mina só tem "
                        + profundidade + " (0 a " + (profundidade - 1) + "): a "
                        + "faixa cai fora da mina e aquele minério simplesmente "
                        + "não existiria.";
            }
            if (veio.fundo() > veio.fundura()) {
                return "O " + veio.nome() + " tem faixa invertida: começa em "
                        + veio.fundo() + " e termina em " + veio.fundura() + ".";
            }
            if (veio.pontos() <= 0) {
                return "O " + veio.nome() + " vale " + veio.pontos()
                        + " ponto(s): minério que não vale ponto é pedra com "
                        + "outra cor.";
            }
            if (veio.tamanho() <= 0 || veio.veios() <= 0) {
                return "O " + veio.nome() + " tem " + veio.veios() + " veio(s) "
                        + "de " + veio.tamanho() + " bloco(s).";
            }
            if (veio.pontos() < antes) {
                return "A tabela está fora de ordem no " + veio.nome() + ": ela "
                        + "é lida de cima para baixo e tem de ir do minério "
                        + "mais raso e mais barato ao mais fundo e mais caro, "
                        + "senão a promessa 'quanto mais fundo, mais ponto' "
                        + "deixa de valer.";
            }
            if (veio.fundo() < antesFundo || veio.fundura() < antesFundura) {
                return "A faixa do " + veio.nome() + " (" + veio.fundo() + " a "
                        + veio.fundura() + ") é mais RASA que a do minério "
                        + "anterior, que vale menos. A tabela vai do barato ao "
                        + "caro, então ela tem de ir também do raso ao fundo — "
                        + "senão existe minério caro perto da superfície e "
                        + "descer deixa de ser a decisão da partida.";
            }
            antes = veio.pontos();
            antesFundo = veio.fundo();
            antesFundura = veio.fundura();
        }
        return null;
    }

    /**
     * A mina tem minério bastante para a partida, e não demais.
     *
     * A conta é grosseira de propósito e é a que importa: dezesseis pessoas
     * quebrando um bloco a cada 0,4 segundo durante o relógio inteiro tiram
     * tantos blocos. Se o minério não chegar a 3% disso, a segunda metade da
     * partida é pedra. Se passar de 12%, não há rota ruim e o jogo vira
     * clique.
     */
    static String conferirRendimento(List<MineracaoCatalog.Veio> tabela, int lado,
                                     int profundidade, int jogadores,
                                     int tempoLimite) {
        int cavados = (int) (jogadores * tempoLimite / 0.4);
        int doMacico = lado * lado * profundidade;
        int cabe = Math.min(cavados, doMacico);
        int minerio = 0;
        for (MineracaoCatalog.Veio veio : tabela) {
            minerio += veio.blocos();
        }
        double parte = minerio / (double) cabe;
        if (parte < 0.03) {
            return "A tabela põe " + minerio + " blocos de minério e "
                    + jogadores + " jogadores cavam até " + cabe + " blocos no "
                    + "relógio: " + Math.round(parte * 1000) / 10.0 + "% de "
                    + "minério. Abaixo de 3% a segunda metade da partida é "
                    + "todo mundo cavando pedra.";
        }
        if (parte > 0.12) {
            return "A tabela põe " + minerio + " blocos de minério para até "
                    + cabe + " blocos cavados: " + Math.round(parte * 1000) / 10.0
                    + "%. Acima de 12% não existe rota ruim, e sem rota ruim "
                    + "não existe decisão — existe clicar depressa.";
        }
        if (minerio > doMacico / 8) {
            return "O minério ocupa " + minerio + " blocos de um maciço de "
                    + doMacico + ": mais de um oitavo da mina é minério, e a "
                    + "rocha deixa de ser obstáculo.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** A moldura de bedrock e o parapeito: ninguém cai da mina. */
    private void moldura(World mundo, MineracaoCatalog planta) {
        Material bedrock = planta.cor("moldura", Material.BEDROCK);
        Material topo = planta.cor("moldura-topo", Material.POLISHED_DEEPSLATE);
        int meio = planta.meio();
        int fora = meio + planta.moldura();

        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (Math.abs(x) <= meio && Math.abs(z) <= meio) {
                    continue;
                }
                for (int y = planta.fundoY(); y <= planta.topoY(); y++) {
                    por(mundo, x, y, z, bedrock);
                }
                for (int alto = 1; alto <= planta.parapeito(); alto++) {
                    por(mundo, x, planta.topoY() + alto, z, topo);
                }
            }
        }
    }

    /** As quatro torres de canto, para a mina ter escala vista de cima. */
    private void torres(World mundo, MineracaoCatalog planta) {
        Material torre = planta.cor("torre", Material.DEEPSLATE_BRICKS);
        Material luz = planta.cor("torre-luz", Material.LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int fora = planta.meio() + planta.moldura();
        int y = planta.topoY();

        for (int[] canto : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int x = canto[0] * fora;
            int z = canto[1] * fora;
            for (int alto = 1; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 5 == 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    private int contarOcupados(World mundo, MineracaoCatalog planta) {
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
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.mineracaoCatalog());
    }

    /** Onde um jogador nasce: na superfície do maciço, olhando para o meio. */
    public static Location pontoDaLargada(World mundo, MineracaoCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(20f);
        return pouso;
    }

    /** De onde se assiste: de cima, que é como se vê quem cavou onde. */
    public static Location pontoDaPlateia(World mundo, MineracaoCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }

    /** O bloco está dentro do maciço que se pode cavar? */
    public static boolean noMacico(MineracaoCatalog planta, int x, int y, int z) {
        int meio = planta.meio();
        return Math.abs(x) <= meio && Math.abs(z) <= meio
                && y > planta.fundoY() && y <= planta.topoY();
    }
}
