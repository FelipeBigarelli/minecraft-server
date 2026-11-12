package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A planta do Quiz.
 *
 * <h2>O banco de perguntas é o jogo</h2>
 *
 * Nos outros dezenove minigames o yml afina uma mecânica que já existe no
 * código. Aqui ele É a mecânica: tirando o banco, o Quiz é um átrio com
 * quatro tablados e um relógio. Por isso o banco mora no yml em formato que
 * se edita à mão, e por isso {@link QuizBuilder#conferirBanco} recusa a obra
 * por motivos de CONTEÚDO e não só de geometria.
 *
 * <h2>A posição da resposta certa é conferida</h2>
 *
 * É o defeito que mais aparece em banco de pergunta escrito à mão, e ele não
 * dá erro nenhum: quem escreve põe a resposta certa onde ela soa melhor na
 * leitura, e sai um banco em que a opção D quase nunca é a certa. Numa rodada
 * disso, dezesseis jogadores aprendem a nunca pisar no tablado D — e aí não
 * estão mais respondendo pergunta, estão lendo o vício de quem escreveu.
 * {@link QuizBuilder#conferirBanco} conta as posições e recusa o banco torto.
 *
 * <h2>O baralho não repete</h2>
 *
 * {@link Baralho} dá as perguntas de um nível em ordem sorteada e só
 * reembaralha quando o nível acaba. Sortear a cada pergunta é mais simples e
 * dá o pior resultado possível: com dezesseis perguntas por nível e doze
 * sorteios por rodada, a mesma pergunta cai duas vezes na mesma partida em
 * quase toda rodada.
 *
 * <h2>Os tablados são cardeais</h2>
 *
 * Com quatro tablados, {@link #centroDoTablado} os põe no norte, leste, sul e
 * oeste — e aí as quatro pontes são retas nos eixos, o que importa porque a
 * ponte é o caminho que se corre contra o relógio. Numa ponte diagonal o
 * jogador anda por escada de blocos e chega mais devagar que o vizinho, o que
 * seria uma desvantagem sorteada pela posição do tablado.
 */
public final class QuizCatalog extends CatalogoDeArena {

    /** As letras dos tablados, na ordem deles. */
    public static final char[] LETRAS = {'A', 'B', 'C', 'D'};

    /**
     * A cor de cada tablado no texto.
     *
     * Mora aqui, e não em cada lugar que escreve, porque a letra aparece em
     * cinco telas — a placa do poste, o painel sobre o tablado, a barra da
     * tela, o título da revelação e a barra de ação de quem trancou — e em
     * todas ela tem de ser a MESMA cor do bloco em que se pisa. Cinco cópias
     * de uma cor divergem, e aí a letra verde na tela manda pisar no tablado
     * amarelo.
     */
    public static final String[] CORES = {"#FF6B6B", "#6BC6FF", "#FFD93D",
            "#8BE04E"};

    public static String corDaLetra(int indice) {
        return CORES[Math.max(0, Math.min(CORES.length - 1, indice))];
    }

    /** Uma pergunta do banco. */
    public record Pergunta(int nivel, String texto, List<String> opcoes, int certa) {

        /** O texto da opção certa. */
        public String resposta() {
            return certa >= 0 && certa < opcoes.size() ? opcoes.get(certa) : "?";
        }
    }

    private List<Pergunta> banco = List.of();

    public QuizCatalog(BigaCore plugin) {
        super(plugin, "quiz.yml", "quiz", "quiz");
    }

    @Override
    protected void aoRecarregar() {
        this.banco = lerBanco(config);
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int atrioRaio() {
        return Math.max(3, config.getInt("arena.atrio-raio", 5));
    }

    /** Quantos tablados — no máximo quatro, que é quantas letras há. */
    public int tablados() {
        return Math.max(2, Math.min(LETRAS.length,
                config.getInt("arena.tablados", 4)));
    }

    public int tabladoRaio() {
        return Math.max(2, config.getInt("arena.tablado-raio", 5));
    }

    /**
     * O raio do anel dos tablados.
     *
     * O piso mínimo deixa quatro blocos de ponte entre a beira do átrio e a
     * beira do tablado: com menos, o tablado encosta no átrio e pisar na
     * resposta deixa de ser uma decisão — basta cair para o lado.
     */
    public int tabladoAnel() {
        return Math.max(atrioRaio() + tabladoRaio() + 4,
                config.getInt("arena.tablado-anel", 16));
    }

    /** A largura da ponte, sempre ímpar para ela ficar centrada no eixo. */
    public int ponteLargura() {
        int pedido = Math.max(1, config.getInt("arena.ponte-largura", 3));
        return pedido % 2 == 0 ? pedido + 1 : pedido;
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 7));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 16));
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return tabladoAnel() + tabladoRaio() + 6;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 8;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int tempoResposta() {
        return Math.max(4, config.getInt("regras.tempo-resposta", 9));
    }

    public int tempoRevelacao() {
        return Math.max(2, config.getInt("regras.tempo-revelacao", 4));
    }

    public int quedaTiques() {
        return Math.max(10, config.getInt("regras.queda-tiques", 30));
    }

    public int perguntasPorRodada() {
        return Math.max(1, config.getInt("regras.perguntas-por-rodada", 12));
    }

    /** Quantas perguntas de cada nível a rodada tem, do fácil ao difícil. */
    public List<Integer> porNivel() {
        List<Integer> lido = config.getIntegerList("regras.por-nivel");
        return lido.isEmpty() ? List.of(4, 4, 4) : List.copyOf(lido);
    }

    public int pontosAcerto() {
        return Math.max(1, config.getInt("regras.pontos-acerto", 10));
    }

    public int pontosRapidez() {
        return Math.max(0, config.getInt("regras.pontos-rapidez", 10));
    }

    public Material corDoTablado(int indice) {
        Material[] padroes = {Material.RED_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                Material.YELLOW_CONCRETE, Material.LIME_CONCRETE};
        int k = Math.max(0, Math.min(padroes.length - 1, indice));
        return cor("tablado-" + Character.toLowerCase(LETRAS[k]), padroes[k]);
    }

    public char letraDo(int indice) {
        return LETRAS[Math.max(0, Math.min(LETRAS.length - 1, indice))];
    }

    // ------------------------------------------------------------------
    //  O banco
    // ------------------------------------------------------------------

    public List<Pergunta> banco() {
        return banco;
    }

    /** Lê o banco do yml. Estático para o teste ler o banco de verdade. */
    public static List<Pergunta> lerBanco(Configuration config) {
        List<Pergunta> saida = new ArrayList<>();
        for (Map<?, ?> linha : config.getMapList("perguntas")) {
            List<String> opcoes = new ArrayList<>();
            if (linha.get("opcoes") instanceof List<?> lista) {
                for (Object item : lista) {
                    opcoes.add(String.valueOf(item));
                }
            }
            saida.add(new Pergunta(numero(linha, "nivel", 1),
                    texto(linha, "pergunta", "?"), List.copyOf(opcoes),
                    numero(linha, "certa", 0)));
        }
        return List.copyOf(saida);
    }

    /** Quantas perguntas o banco tem naquele nível. */
    public static int quantasNoNivel(List<Pergunta> banco, int nivel) {
        int quantas = 0;
        for (Pergunta pergunta : banco) {
            if (pergunta.nivel() == nivel) {
                quantas++;
            }
        }
        return quantas;
    }

    /** O maior número de opções que uma pergunta do banco tem. */
    public static int maiorNumeroDeOpcoes(List<Pergunta> banco) {
        int maior = 0;
        for (Pergunta pergunta : banco) {
            maior = Math.max(maior, pergunta.opcoes().size());
        }
        return maior;
    }

    /**
     * O baralho de perguntas: sorteia a ordem e só reembaralha no fim.
     *
     * <p>A rodada é montada do nível 1 ao nível 3, nessa ordem, e isso não é
     * apresentação: sem a subida de nível, a segunda pergunta da partida pode
     * ser a mais difícil do banco e metade da arena cai nos primeiros vinte
     * segundos para assistir os outros três minutos. Com ela, quase todo
     * mundo responde as quatro primeiras e a eliminação começa quando já
     * houve jogo.
     */
    public static final class Baralho {

        private final Map<Integer, List<Pergunta>> doNivel = new HashMap<>();
        private final Map<Integer, List<Pergunta>> resto = new HashMap<>();
        private final Random sorte;

        public Baralho(List<Pergunta> banco, Random sorte) {
            this.sorte = sorte;
            for (Pergunta pergunta : banco) {
                doNivel.computeIfAbsent(pergunta.nivel(),
                        nivel -> new ArrayList<>()).add(pergunta);
            }
        }

        /** As perguntas de uma rodada, do nível mais fácil ao mais difícil. */
        public List<Pergunta> rodada(List<Integer> quantasPorNivel) {
            List<Pergunta> saida = new ArrayList<>();
            Set<Pergunta> jaSaiu = new HashSet<>();
            for (int k = 0; k < quantasPorNivel.size(); k++) {
                int nivel = k + 1;
                for (int i = 0; i < quantasPorNivel.get(k); i++) {
                    Pergunta pergunta = puxar(nivel, jaSaiu);
                    if (pergunta == null) {
                        break;
                    }
                    saida.add(pergunta);
                    jaSaiu.add(pergunta);
                }
            }
            return saida;
        }

        private Pergunta puxar(int nivel, Set<Pergunta> jaSaiu) {
            List<Pergunta> fonte = doNivel.getOrDefault(nivel, List.of());
            if (fonte.isEmpty()) {
                return null;
            }
            List<Pergunta> pilha = resto.computeIfAbsent(nivel,
                    qual -> new ArrayList<>());
            // Duas voltas: a primeira esvazia o que sobrou do baralho velho,
            // a segunda roda num baralho novo. Se as duas falharem, o nível
            // inteiro já saiu NESTA rodada e não há o que dar.
            for (int volta = 0; volta < 2; volta++) {
                while (!pilha.isEmpty()) {
                    Pergunta pergunta = pilha.remove(pilha.size() - 1);
                    if (!jaSaiu.contains(pergunta)) {
                        return pergunta;
                    }
                }
                pilha.addAll(fonte);
                Collections.shuffle(pilha, sorte);
            }
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  Os tablados
    // ------------------------------------------------------------------

    public int[] centroDoTablado(int indice) {
        return centroDoTablado(indice, tablados(), tabladoAnel());
    }

    /**
     * O meio de um tablado: {x, z}.
     *
     * O índice zero fica no NORTE e os outros seguem no sentido do relógio,
     * para a letra A ser sempre o mesmo tablado — quem joga duas vezes já
     * sabe onde fica o A antes de a pergunta aparecer.
     */
    public static int[] centroDoTablado(int indice, int quantos, int anel) {
        double angulo = -Math.PI / 2 + 2 * Math.PI * indice / Math.max(1, quantos);
        return new int[]{(int) Math.round(Math.cos(angulo) * anel),
                (int) Math.round(Math.sin(angulo) * anel)};
    }

    public int tabladoDoPonto(double x, double z) {
        return tabladoDoPonto(x, z, tablados(), tabladoAnel(), tabladoRaio());
    }

    /** Em que tablado o ponto está, ou −1 se está no átrio, na ponte ou fora. */
    public static int tabladoDoPonto(double x, double z, int quantos, int anel,
                                     int raio) {
        for (int k = 0; k < quantos; k++) {
            int[] centro = centroDoTablado(k, quantos, anel);
            if (Math.hypot(x - centro[0], z - centro[1]) <= raio + 0.5) {
                return k;
            }
        }
        return -1;
    }

    public boolean naPonte(double x, double z) {
        return naPonte(x, z, tablados(), tabladoAnel(), ponteLargura());
    }

    /** Se o ponto está numa das pontes que ligam o átrio aos tablados. */
    public static boolean naPonte(double x, double z, int quantos, int anel,
                                  int largura) {
        double meia = (largura - 1) / 2.0 + 0.5;
        for (int k = 0; k < quantos; k++) {
            int[] centro = centroDoTablado(k, quantos, anel);
            double comprimento = Math.hypot(centro[0], centro[1]);
            if (comprimento < 0.5) {
                continue;
            }
            double ux = centro[0] / comprimento;
            double uz = centro[1] / comprimento;
            double aoLongo = x * ux + z * uz;
            double aoLado = Math.abs(-x * uz + z * ux);
            if (aoLongo >= 0 && aoLongo <= comprimento && aoLado <= meia) {
                return true;
            }
        }
        return false;
    }

    public int[] posteDoTablado(int indice) {
        return posteDoTablado(indice, tablados(), tabladoAnel(), tabladoRaio());
    }

    /**
     * Onde fica o poste da placa de um tablado: {x, z}.
     *
     * Do lado de FORA do tablado, com a placa virada para o meio da arena.
     * É o único lugar que funciona: quem corre do átrio para o tablado vem
     * olhando para fora, e uma placa do lado de dentro ficaria atrás dele.
     */
    public static int[] posteDoTablado(int indice, int quantos, int anel,
                                       int raio) {
        int[] centro = centroDoTablado(indice, quantos, anel);
        double longe = Math.hypot(centro[0], centro[1]);
        if (longe < 0.5) {
            return new int[]{centro[0], centro[1] + raio + 2};
        }
        double estica = (longe + raio + 2) / longe;
        return new int[]{(int) Math.round(centro[0] * estica),
                (int) Math.round(centro[1] * estica)};
    }

    /** Para que lado a placa do poste olha: para o meio da arena. */
    public static BlockFace frenteDaPlaca(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? BlockFace.WEST : BlockFace.EAST;
        }
        return z > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** As largadas: no átrio, que é de onde todo mundo parte. */
    public List<int[]> largadas(int quantos) {
        return largadas(quantos, atrioRaio());
    }

    /**
     * O anel de largada, dentro do átrio.
     *
     * Não usa {@link ArenaComum#largadas} porque o átrio é pequeno — o recuo
     * de cinco blocos do ajudante compartilhado punha os dezesseis jogadores
     * no mesmo bloco do meio. Aqui o anel fica a um bloco da beira, que é o
     * mais longe possível de todas as quatro pontes ao mesmo tempo.
     */
    public static List<int[]> largadas(int quantos, int atrioRaio) {
        List<int[]> saida = new ArrayList<>();
        int anel = Math.max(1, atrioRaio - 1);
        int quantas = Math.max(2, quantos);
        for (int k = 0; k < quantas; k++) {
            double angulo = 2 * Math.PI * k / quantas;
            saida.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return saida;
    }

    private static String texto(Map<?, ?> linha, String chave, String padrao) {
        Object valor = linha.get(chave);
        return valor == null ? padrao : String.valueOf(valor);
    }

    private static int numero(Map<?, ?> linha, String chave, int padrao) {
        Object valor = linha.get(chave);
        return valor instanceof Number quanto ? quanto.intValue() : padrao;
    }
}
