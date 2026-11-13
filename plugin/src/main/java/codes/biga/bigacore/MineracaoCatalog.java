package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A planta da Mineração — e a mina de cada rodada.
 *
 * <h2>Minério em VEIO, e não sorteado bloco por bloco</h2>
 *
 * A mina de servidor de prisão sorteia cada bloco independentemente de uma
 * tabela de porcentagens. Funciona e é errado para um jogo: minério solto
 * espalhado por igual faz com que cavar em QUALQUER direção pague exatamente
 * o mesmo valor esperado. Sem direção melhor não existe ler a mina, não
 * existe seguir o veio, não existe decisão — existe clicar depressa, e o
 * jogo vira teste de mouse.
 *
 * <p>Aqui cada minério entra em borrões: sorteia-se um ponto dentro da faixa
 * de profundidade dele e cresce-se dali por passeio aleatório. O resultado é
 * mensurável e o teste mede: um bloco de minério tem em média mais de um
 * vizinho do mesmo minério, contra os 0,3 que o sorteio por bloco daria na
 * mesma densidade. Essa diferença é o jogo inteiro — é ela que faz "achei
 * ouro" valer a frase.
 *
 * <h2>Fundo paga mais, e a rocha avisa</h2>
 *
 * Cada minério tem faixa própria: carvão nas seis primeiras fiadas,
 * esmeralda só nas duas últimas. Da fiada {@link #fundoProfundo()} para baixo
 * a rocha vira ardósia — mais dura e visivelmente outra. O jogador não
 * precisa de tutorial para entender que chegou onde paga: a parede mudou de
 * cor e ficou mais lenta.
 */
public final class MineracaoCatalog extends CatalogoDeArena {

    /** Um minério na tabela: o bloco, o que vale e onde mora. */
    public record Veio(String nome, Material bloco, Material profundo, int pontos,
                       int veios, int tamanho, int fundo, int fundura) {

        /** O bloco certo para uma fiada: ardósia abaixo do limite. */
        public Material blocoNaFiada(int fiada, int fundoProfundo) {
            return fiada >= fundoProfundo ? profundo : bloco;
        }

        /** Quantos blocos este minério põe na mina, no melhor caso. */
        public int blocos() {
            return veios * tamanho;
        }
    }

    /** Os seis vizinhos de um bloco, para o passeio que faz o veio. */
    private static final int[][] SEIS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private List<Veio> tabela = List.of();
    private Map<Material, Veio> porBloco = Map.of();

    public MineracaoCatalog(BigaCore plugin) {
        super(plugin, "mineracao.yml", "mineracao", "mina");
    }

    @Override
    protected void aoRecarregar() {
        this.tabela = lerTabela(config);
        Map<Material, Veio> indice = new HashMap<>();
        for (Veio veio : tabela) {
            indice.put(veio.bloco(), veio);
            indice.put(veio.profundo(), veio);
        }
        this.porBloco = Map.copyOf(indice);
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int lado() {
        return Math.max(9, config.getInt("arena.lado", 35));
    }

    /** A maior coordenada do maciço: ele vai de -meio a +meio. */
    public int meio() {
        return (lado() - 1) / 2;
    }

    public int profundidade() {
        return Math.max(4, config.getInt("arena.profundidade", 12));
    }

    public int fundoProfundo() {
        return Math.max(1, Math.min(profundidade() - 1,
                config.getInt("arena.fundo-profundo", 6)));
    }

    public int moldura() {
        return Math.max(1, config.getInt("arena.moldura", 1));
    }

    public int parapeito() {
        return Math.max(0, config.getInt("arena.parapeito", 2));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 18));
    }

    /** A fiada do piso de bedrock, que ninguém quebra. */
    public int fundoY() {
        return topoY() - profundidade();
    }

    @Override
    public int quedaY() {
        return fundoY() - Math.max(2, config.getInt("regras.queda-abaixo", 4));
    }

    @Override
    public int alcanceDaObra() {
        return meio() + moldura() + 5;
    }

    @Override
    public int fundoDaObra() {
        return fundoY() - 4;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int sonares() {
        return Math.max(0, config.getInt("regras.sonares", 2));
    }

    /**
     * Quantas tochas o jogador leva, e elas não acabam.
     *
     * O maciço é pedra maciça: nenhuma luz do céu entra nele, e cavar para
     * dentro é cavar no escuro — o jogador não via o minério que estava
     * raspando. A tocha não é conforto, é a diferença entre jogar e adivinhar.
     *
     * <p>Infinita porque a alternativa é pior: uma tocha contada vira um
     * segundo recurso para administrar, e o jogo já tem o sonar para isso.
     * Com tocha infinita a decisão que sobra é a boa — ONDE acender, que é o
     * que separa quem abre um túnel que dá para voltar de quem se perde no
     * próprio buraco.
     */
    public int tochas() {
        return Math.max(0, Math.min(64, config.getInt("regras.tochas", 16)));
    }

    public org.bukkit.Material tocha() {
        return cor("tocha", org.bukkit.Material.TORCH);
    }

    public int sonarAlcance() {
        return Math.max(2, config.getInt("regras.sonar-alcance", 10));
    }

    public int dobroSegundos() {
        return Math.max(0, Math.min(tempoLimite(),
                config.getInt("regras.dobro-segundos", 45)));
    }

    public long sementeFixa() {
        return config.getLong("arena.semente", -1L);
    }

    // ------------------------------------------------------------------
    //  A tabela
    // ------------------------------------------------------------------

    public List<Veio> tabela() {
        return tabela;
    }

    /** O minério de um bloco, ou null se aquele bloco não vale ponto. */
    public Veio veioDoBloco(Material bloco) {
        return porBloco.get(bloco);
    }

    /**
     * Lê a tabela de minérios do yml.
     *
     * Estática e recebendo a configuração de fora para o teste poder ler a
     * MESMA tabela que o servidor usa. Uma tabela repetida no teste é uma
     * tabela que não testa nada: ela concorda consigo mesma enquanto a de
     * produção diverge.
     */
    public static List<Veio> lerTabela(Configuration config) {
        List<Veio> saida = new ArrayList<>();
        List<Map<?, ?>> cru = config.getMapList("minerios");
        for (Map<?, ?> linha : cru) {
            String nome = texto(linha, "nome", "?");
            Material bloco = Material.matchMaterial(
                    texto(linha, "bloco", "STONE").toUpperCase(Locale.ROOT));
            if (bloco == null) {
                continue;
            }
            Material profundo = Material.matchMaterial("DEEPSLATE_" + bloco.name());
            saida.add(new Veio(nome, bloco, profundo == null ? bloco : profundo,
                    numero(linha, "pontos", 1), numero(linha, "veios", 1),
                    numero(linha, "tamanho", 1), numero(linha, "fundo", 0),
                    numero(linha, "fundura", 0)));
        }
        return List.copyOf(saida);
    }

    private static String texto(Map<?, ?> linha, String chave, String padrao) {
        Object valor = linha.get(chave);
        return valor == null ? padrao : String.valueOf(valor);
    }

    private static int numero(Map<?, ?> linha, String chave, int padrao) {
        Object valor = linha.get(chave);
        return valor instanceof Number quanto ? quanto.intValue() : padrao;
    }

    // ------------------------------------------------------------------
    //  A mina — conta pura, o teste cava quatrocentas sem servidor
    // ------------------------------------------------------------------

    /** O índice de uma casa da mina no vetor. */
    public static int indice(int lado, int fiada, int u, int v) {
        return (fiada * lado + v) * lado + u;
    }

    /**
     * Cava uma mina.
     *
     * @return um vetor de {@code lado*lado*profundidade}; 0 é rocha e n é o
     *         minério de índice n-1 na tabela
     */
    public static int[] gerar(int lado, int profundidade, long semente,
                              List<Veio> tabela) {
        int[] mina = new int[lado * lado * profundidade];
        Random sorte = new Random(semente);
        for (int o = 0; o < tabela.size(); o++) {
            Veio veio = tabela.get(o);
            int alto = Math.min(profundidade - 1, veio.fundura());
            int baixo = Math.max(0, Math.min(alto, veio.fundo()));
            for (int k = 0; k < veio.veios(); k++) {
                crescer(mina, lado, sorte, o + 1, veio.tamanho(), baixo, alto,
                        sorte.nextInt(lado), sorte.nextInt(lado),
                        baixo + sorte.nextInt(alto - baixo + 1));
            }
        }
        return mina;
    }

    /**
     * Cresce um veio por passeio aleatório, preso à faixa dele.
     *
     * O passeio só anda para onde ainda é faixa e maciço: assim um veio de
     * esmeralda nunca sobe para a terceira fiada por azar do sorteio, e a
     * promessa "fundo paga mais" não tem exceção. Casa já ocupada por outro
     * minério não é tomada — o veio fica menor, que é melhor do que um
     * minério apagar o outro e a tabela de pontos deixar de valer.
     */
    private static void crescer(int[] mina, int lado, Random sorte, int qual,
                                int tamanho, int baixo, int alto, int u, int v,
                                int fiada) {
        int postos = 0;
        int tentativas = tamanho * 8;
        while (postos < tamanho && tentativas-- > 0) {
            int onde = indice(lado, fiada, u, v);
            if (mina[onde] == 0) {
                mina[onde] = qual;
                postos++;
            }
            int[] rumo = SEIS[sorte.nextInt(SEIS.length)];
            int nu = u + rumo[0];
            int nv = v + rumo[1];
            int nf = fiada + rumo[2];
            if (nu >= 0 && nu < lado && nv >= 0 && nv < lado
                    && nf >= baixo && nf <= alto) {
                u = nu;
                v = nv;
                fiada = nf;
            }
        }
    }

    /** Quantos pontos a mina inteira tem no chão. */
    public static int pontosNaMina(int[] mina, List<Veio> tabela) {
        int total = 0;
        for (int casa : mina) {
            if (casa > 0 && casa <= tabela.size()) {
                total += tabela.get(casa - 1).pontos();
            }
        }
        return total;
    }

    /** Quantos blocos de minério a mina tem. */
    public static int blocosDeMinerio(int[] mina) {
        int total = 0;
        for (int casa : mina) {
            if (casa > 0) {
                total++;
            }
        }
        return total;
    }

    /** As largadas: um anel na superfície do maciço. */
    public List<int[]> largadas(int quantos) {
        return ArenaComum.largadas(Math.max(2, quantos), meio());
    }
}
