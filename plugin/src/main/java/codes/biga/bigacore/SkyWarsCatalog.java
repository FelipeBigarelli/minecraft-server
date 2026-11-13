package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A planta do SKY WARS.
 *
 * <h2>O kit é sorteado por partida, não por jogador</h2>
 *
 * O defeito do gênero tem nome próprio nos fóruns dos jogadores — SEE,
 * <i>Skywars Equipment Equality</i> — e a reclamação é sempre a mesma: um abre
 * espada de ferro e armadura e o outro abre picareta de madeira. Aqui a ideia
 * vai até o fim: {@link #nivelDaPartida} sorteia UM nível e ele vale para
 * todas as ilhas. A variedade entre partidas fica, a loteria entre jogadores
 * não — e não há baú nas ilhas para saquear, o kit chega na mão.
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

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

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
