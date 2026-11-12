package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A planta da Pescaria.
 *
 * <h2>Todo poço paga o mesmo por segundo; o que muda é o ritmo</h2>
 *
 * É a decisão de desenho que sustenta o jogo, e ela é contra-intuitiva: se o
 * poço fundo pagasse MAIS por segundo do que a corredeira, os outros cinco
 * poços seriam enfeite e os dezesseis jogadores ficariam em cima do mesmo
 * buraco. Pagando o mesmo, o que se escolhe é o RITMO — 1,4 ponto a cada três
 * segundos ou 7 a cada treze — e aí a escolha é sobre variância, sobre quanto
 * tempo se tolera sem retorno, e sobre onde os outros quinze estão.
 *
 * <p>{@link #pontosPorSegundo} é a conta que prova isso, e o teste a roda
 * poço por poço. Sem ela a tabela de pesos é um palpite que ninguém consegue
 * conferir de cabeça.
 *
 * <h2>A espera é do poço, e não da vara</h2>
 *
 * Os plugins de pescaria mexem no que o jogador PEGA. A espera continua sendo
 * o sorteio de 5 a 30 segundos do jogo-base, e o jogador continua sem nada
 * para decidir — e os que também mexem na espera mexem por ENCANTAMENTO, que
 * numa competição decide a partida antes do primeiro lance. Aqui a espera sai
 * do poço em que o anzol pousou, e a vara é a mesma para os dezesseis.
 */
public final class PescariaCatalog extends CatalogoDeArena {

    /**
     * O custo de recolher o anzol e lançar de novo, em tiques.
     *
     * Entra só na conta de comparação entre poços, e tem de entrar: sem ele a
     * corredeira pareceria pagar o dobro do poço fundo por segundo, porque um
     * segundo de relance pesa muito numa espera de dois segundos e quase nada
     * numa de treze. É o número que faz a comparação ser honesta.
     */
    public static final int RELANCE_TIQUES = 20;

    /** Um peixe da tabela. */
    public record Peixe(String nome, int pontos) {
    }

    /** Um poço: o leito, a espera e os pesos de sorteio dele. */
    public record Poco(String nome, int fundo, Material leito, int esperaMin,
                       int esperaMax, List<Integer> pesos) {

        public int esperaMedia() {
            return (esperaMin + esperaMax) / 2;
        }

        public int pesoTotal() {
            int total = 0;
            for (int peso : pesos) {
                total += peso;
            }
            return total;
        }
    }

    private List<Peixe> peixes = List.of();
    private List<Poco> pocos = List.of();

    public PescariaCatalog(BigaCore plugin) {
        super(plugin, "pescaria.yml", "pescaria", "pescaria");
    }

    @Override
    protected void aoRecarregar() {
        this.peixes = lerPeixes(config);
        this.pocos = lerPocos(config, peixes.size());
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(16, config.getInt("arena.raio", 34));
    }

    public int beiraAneis() {
        return Math.max(0, config.getInt("arena.beira-aneis", 2));
    }

    public int pocoRaioMeio() {
        return Math.max(2, config.getInt("arena.poco-raio-meio", 8));
    }

    public int pocoRaio() {
        return Math.max(2, config.getInt("arena.poco-raio", 6));
    }

    /**
     * O raio do anel em que ficam os cinco poços de fora.
     *
     * O piso mínimo soma oito aos dois raios: são as duas paredes de bacia,
     * de dois blocos cada, e quatro blocos de deque entre elas. Com menos do
     * que isso o poste da placa de um poço nasce dentro da parede do outro.
     */
    public int pocoAnel() {
        return Math.max(pocoRaioMeio() + pocoRaio() + 8,
                config.getInt("arena.poco-anel", 22));
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
        return raio() + 5;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - fundoMaisFundo() - 6;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 6);
    }

    /** A profundidade do poço mais fundo, para a obra saber até onde cavar. */
    public int fundoMaisFundo() {
        int maior = 1;
        for (Poco poco : pocos) {
            maior = Math.max(maior, poco.fundo());
        }
        return maior;
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int cansacoPorPeixe() {
        return Math.max(0, config.getInt("regras.cansaco-por-peixe", 25));
    }

    public int cansacoAlivio() {
        return Math.max(0, config.getInt("regras.cansaco-alivio", 4));
    }

    public int cansacoMaximo() {
        return Math.max(0, config.getInt("regras.cansaco-maximo", 240));
    }

    public Peixe oGrande() {
        return new Peixe(config.getString("regras.grande-nome", "O GRANDE"),
                Math.max(1, config.getInt("regras.grande-pontos", 60)));
    }

    public int grandeIntervalo() {
        return Math.max(10, config.getInt("regras.grande-intervalo", 50));
    }

    public int grandeJanela() {
        return Math.max(5, config.getInt("regras.grande-janela", 25));
    }

    public int grandePeso() {
        return Math.max(1, config.getInt("regras.grande-peso", 30));
    }

    // ------------------------------------------------------------------
    //  As tabelas
    // ------------------------------------------------------------------

    public List<Peixe> peixes() {
        return peixes;
    }

    public List<Poco> pocos() {
        return pocos;
    }

    public static List<Peixe> lerPeixes(Configuration config) {
        List<Peixe> saida = new ArrayList<>();
        for (Map<?, ?> linha : config.getMapList("peixes")) {
            saida.add(new Peixe(texto(linha, "nome", "?"),
                    numero(linha, "pontos", 1)));
        }
        return List.copyOf(saida);
    }

    /**
     * Lê os poços, completando com zero os pesos que faltarem.
     *
     * Completar em vez de recusar é de propósito: a tabela de peixes pode
     * crescer, e um poço que não lista o peixe novo quer dizer "este peixe não
     * aparece aqui" — que é a resposta certa, e não um erro de configuração.
     */
    public static List<Poco> lerPocos(Configuration config, int quantosPeixes) {
        List<Poco> saida = new ArrayList<>();
        for (Map<?, ?> linha : config.getMapList("pocos")) {
            Material leito = Material.matchMaterial(
                    texto(linha, "leito", "GRAVEL").toUpperCase(Locale.ROOT));
            List<Integer> espera = numeros(linha.get("espera"));
            List<Integer> pesos = new ArrayList<>(numeros(linha.get("pesos")));
            while (pesos.size() < quantosPeixes) {
                pesos.add(0);
            }
            saida.add(new Poco(texto(linha, "nome", "?"),
                    Math.max(1, numero(linha, "fundo", 3)),
                    leito == null ? Material.GRAVEL : leito,
                    espera.isEmpty() ? 40 : espera.get(0),
                    espera.size() < 2 ? 100 : espera.get(1),
                    List.copyOf(pesos)));
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

    private static List<Integer> numeros(Object valor) {
        List<Integer> saida = new ArrayList<>();
        if (valor instanceof List<?> lista) {
            for (Object item : lista) {
                if (item instanceof Number quanto) {
                    saida.add(quanto.intValue());
                }
            }
        }
        return saida;
    }

    // ------------------------------------------------------------------
    //  Contas puras
    // ------------------------------------------------------------------

    /** Quantos pontos vale, em média, um peixe daquele poço. */
    public static double valorEsperado(Poco poco, List<Peixe> peixes) {
        int total = poco.pesoTotal();
        if (total <= 0) {
            return 0;
        }
        double soma = 0;
        for (int k = 0; k < peixes.size() && k < poco.pesos().size(); k++) {
            soma += poco.pesos().get(k) * (double) peixes.get(k).pontos();
        }
        return soma / total;
    }

    /**
     * Quantos pontos por segundo o poço rende para quem fica nele.
     *
     * É esta conta que tem de sair quase igual nos seis poços. O denominador
     * inclui o relance, senão a corredeira pareceria pagar o dobro.
     */
    public static double pontosPorSegundo(Poco poco, List<Peixe> peixes) {
        double tiques = poco.esperaMedia() + RELANCE_TIQUES;
        return valorEsperado(poco, peixes) / (tiques / 20.0);
    }

    /** Sorteia um índice pelos pesos. -1 se não há peso nenhum. */
    public static int sortear(List<Integer> pesos, Random sorte) {
        int total = 0;
        for (int peso : pesos) {
            total += Math.max(0, peso);
        }
        if (total <= 0) {
            return -1;
        }
        int bilhete = sorte.nextInt(total);
        for (int k = 0; k < pesos.size(); k++) {
            bilhete -= Math.max(0, pesos.get(k));
            if (bilhete < 0) {
                return k;
            }
        }
        return pesos.size() - 1;
    }

    /** O melhor peixe que um poço dá, para a placa dele. */
    public Peixe melhorPeixeDo(Poco poco) {
        return melhorPeixeDo(poco, peixes);
    }

    public static Peixe melhorPeixeDo(Poco poco, List<Peixe> peixes) {
        Peixe melhor = null;
        for (int k = 0; k < peixes.size() && k < poco.pesos().size(); k++) {
            if (poco.pesos().get(k) > 0
                    && (melhor == null || peixes.get(k).pontos() > melhor.pontos())) {
                melhor = peixes.get(k);
            }
        }
        return melhor;
    }

    // ------------------------------------------------------------------
    //  Do poço para o mundo
    // ------------------------------------------------------------------

    /** O centro de um poço: {x, z}. O primeiro da lista fica no meio. */
    public int[] centroDoPoco(int indice) {
        return centroDoPoco(indice, pocos().size(), pocoAnel());
    }

    /**
     * A mesma conta sem o yml, para a conferência e o teste.
     *
     * @param quantos quantos poços há em tudo, o do meio incluído
     */
    public static int[] centroDoPoco(int indice, int quantos, int anel) {
        if (indice <= 0) {
            return new int[]{0, 0};
        }
        int noAnel = Math.max(1, quantos - 1);
        double angulo = Math.PI / 2 + 2 * Math.PI * (indice - 1) / noAnel;
        return new int[]{(int) Math.round(Math.cos(angulo) * anel),
                (int) Math.round(-Math.sin(angulo) * anel)};
    }

    public int raioDoPoco(int indice) {
        return raioDoPoco(indice, pocoRaioMeio(), pocoRaio());
    }

    public static int raioDoPoco(int indice, int raioMeio, int raio) {
        return indice <= 0 ? raioMeio : raio;
    }

    /** Em que poço cai um ponto do mundo, ou -1 se é deque. */
    public int pocoDoPonto(double x, double z) {
        return pocoDoPonto(x, z, pocos().size(), pocoAnel(), pocoRaioMeio(),
                pocoRaio());
    }

    public static int pocoDoPonto(double x, double z, int quantos, int anel,
                                  int raioMeio, int raio) {
        for (int k = 0; k < quantos; k++) {
            int[] centro = centroDoPoco(k, quantos, anel);
            int dele = raioDoPoco(k, raioMeio, raio);
            double dx = x - centro[0];
            double dz = z - centro[1];
            if (dx * dx + dz * dz <= (dele + 0.5) * (dele + 0.5)) {
                return k;
            }
        }
        return -1;
    }

    /**
     * Onde fica o poste da placa de um poço, e para onde a placa olha.
     *
     * O poste fica do lado do poço que dá para o meio da plataforma, e a
     * placa olha para o meio — porque é do meio que o jogador vem. Placa
     * virada para fora ficaria de costas para quem está chegando.
     *
     * @return {x, z, índice do rumo em {@link #RUMOS_DA_PLACA}}
     */
    public int[] posteDoPoco(int indice) {
        return posteDoPoco(indice, pocos().size(), pocoAnel(), pocoRaioMeio(),
                pocoRaio());
    }

    public static int[] posteDoPoco(int indice, int quantos, int anel,
                                    int raioMeio, int raio) {
        if (indice <= 0) {
            // O poço do meio é abordado por todos os lados; a placa dele fica
            // ao norte, olhando para o norte.
            return new int[]{0, -(raioMeio + 3), 0};
        }
        int[] centro = centroDoPoco(indice, quantos, anel);
        double longe = Math.max(1.0, Math.hypot(centro[0], centro[1]));
        double recuo = (longe - raio - 3) / longe;
        int x = (int) Math.round(centro[0] * recuo);
        int z = (int) Math.round(centro[1] * recuo);
        return new int[]{x, z, rumoCardeal(-centro[0], -centro[1])};
    }

    /** Os quatro rumos que uma placa de parede aceita. */
    public static final BlockFace[] RUMOS_DA_PLACA = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /** O cardeal mais próximo de uma direção: índice em RUMOS_DA_PLACA. */
    public static int rumoCardeal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 1 : 3;
        }
        return dz >= 0 ? 2 : 0;
    }

    /** As largadas: no deque, entre o poço do meio e o anel. */
    public List<int[]> largadas(int quantos) {
        int anel = (pocoRaioMeio() + pocoAnel() - pocoRaio()) / 2;
        List<int[]> pontos = new ArrayList<>();
        if (quantos <= 1) {
            pontos.add(new int[]{anel, 0});
            return pontos;
        }
        for (int k = 0; k < quantos; k++) {
            double angulo = 2 * Math.PI * k / quantos;
            pontos.add(new int[]{(int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return pontos;
    }
}
