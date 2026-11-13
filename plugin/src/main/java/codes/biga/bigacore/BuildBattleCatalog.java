package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A planta do BUILD BATTLE.
 *
 * <h2>A nota não é a média: é a ordem</h2>
 *
 * O defeito que os jogadores de Build Battle reclamam em todo servidor é
 * sempre o mesmo, e não é sobre construir: dar a nota mínima para os outros
 * levanta a própria colocação, então é a jogada certa — e jogador novo nunca
 * sobe ao pódio. {@link #ordemDaCedula} resolve isso sem punir ninguém:
 * antes de entrar na conta, a cédula de cada votante é convertida em ORDEM.
 *
 * <p>Com posto médio para notas iguais, quem dá 1 para todos produz uma
 * cédula de postos TODOS IGUAIS — ela soma a mesma constante para todo mundo
 * e não desempata nada. E quem combina com um amigo tem exatamente um
 * primeiro lugar para dar, igual a qualquer outro votante. É um votante, uma
 * voz, e isso não depende de descobrir quem está trapaceando.
 *
 * <p>{@link #mediaCrua} continua existindo e é o que o jogador vê — "nota 4,2
 * de 5" é o que ele entende. A ordem normalizada é máquina invisível: ela
 * decide a COLOCAÇÃO e nada mais.
 *
 * <h2>Por que o lote tem lado ímpar</h2>
 *
 * Um quadrado de lado par não tem bloco no meio, e aí o lote não fica
 * centrado em bloco nenhum: a conta de "este bloco é de quem?" ganha meio
 * bloco de erro em duas das quatro bordas, e meio bloco de erro na borda é
 * exatamente onde dois lotes se encontram. Com lado 13 e vão 7 o passo é 20,
 * e {@link #centroDoPlot} devolve números inteiros.
 */
public final class BuildBattleCatalog extends CatalogoDeArena {

    public BuildBattleCatalog(BigaCore plugin) {
        super(plugin, "build-battle.yml", "build_battle", "build");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int plots() {
        return Math.max(2, config.getInt("arena.plots", 16));
    }

    public int plotsPorFiada() {
        return Math.max(1, config.getInt("arena.plots-por-fiada", 4));
    }

    /** O lado do lote, sempre ímpar: ver o cabeçalho. */
    public int plotLado() {
        int lado = Math.max(5, config.getInt("arena.plot-lado", 13));
        return lado % 2 == 0 ? lado + 1 : lado;
    }

    public int plotVao() {
        return Math.max(3, config.getInt("arena.plot-vao", 7));
    }

    public int passo() {
        return plotLado() + plotVao();
    }

    public int alturaDeObra() {
        return Math.max(4, config.getInt("arena.altura-de-obra", 16));
    }

    public int limiteDoLote() {
        return Math.max(plotLado() / 2 + 1,
                config.getInt("arena.limite-do-lote", 11));
    }

    public int plateiaRecuo() {
        return Math.max(4, config.getInt("arena.plateia-recuo", 12));
    }

    public int plateiaAltura() {
        return Math.max(0, config.getInt("arena.plateia-altura", 5));
    }

    public int plateiaLado() {
        return Math.max(3, config.getInt("arena.plateia-lado", 5));
    }

    public int plateiaFundo() {
        return Math.max(2, config.getInt("arena.plateia-fundo", 3));
    }

    public int marcoAltura() {
        return Math.max(6, config.getInt("arena.marco-altura", 20));
    }

    public int painelAltura() {
        return Math.max(3, config.getInt("arena.painel-altura", 10));
    }

    /**
     * A queda cai muito abaixo de propósito.
     *
     * Ninguém é eliminado neste jogo, e o construtor voa por baixo do próprio
     * lote para fazer o fundo da obra. Se a queda ficasse perto do chão, voar
     * para baixo viraria eliminação por acidente.
     */
    @Override
    public int quedaY() {
        return topoY() - Math.max(20, config.getInt("regras.queda-abaixo", 40));
    }

    /**
     * O alcance da obra: o maior dos dois lados da grade, com folga.
     *
     * A grade não é quadrada — são duas fiadas de oito —, então o alcance é o
     * maior entre a largura das colunas e o quanto a plateia avança para fora
     * das fiadas. Pegar só a largura deixaria a plateia de fora da área que
     * {@code desfazer} apaga, e sobraria uma caixa de vidro no vazio.
     */
    @Override
    public int alcanceDaObra() {
        return Math.max(beiraDasColunas(), beiraDasPlateias()) + 6;
    }

    /** Quão longe do meio, no eixo das colunas, vai a borda do lote de fora. */
    public int beiraDasColunas() {
        return (int) Math.round((plotsPorFiada() - 1) / 2.0 * passo())
                + plotLado() / 2 + 1;
    }

    /** Quão longe do meio, no eixo das fiadas, vai o fundo da plateia. */
    public int beiraDasPlateias() {
        int fiadas = fiadas();
        return (int) Math.round((fiadas - 1) / 2.0 * passo())
                + (plotLado() - 1) / 2 + plateiaRecuo() + plateiaFundo();
    }

    public int fiadas() {
        return (int) Math.ceil(plots() / (double) plotsPorFiada());
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 4;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + Math.max(marcoAltura(), alturaDeObra()) + 10);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int segundosDeTema() {
        return Math.max(2, config.getInt("regras.segundos-de-tema", 6));
    }

    public int segundosDeObra() {
        return Math.max(30, config.getInt("regras.segundos-de-obra", 180));
    }

    public int segundosDePasseio() {
        return Math.max(1, config.getInt("regras.segundos-de-passeio", 3));
    }

    public int segundosDeVoto() {
        return Math.max(2, config.getInt("regras.segundos-de-voto", 8));
    }

    public int notas() {
        return Math.max(2, Math.min(9, config.getInt("regras.notas", 5)));
    }

    public List<String> temas() {
        List<String> lista = config.getStringList("temas");
        return lista.isEmpty() ? List.of("uma casa") : List.copyOf(lista);
    }

    /**
     * A paleta da obra, na ordem do yml.
     *
     * Cortada no tamanho do inventário: 36 blocos é o que cabe na mão do
     * jogador sem menu, sem busca e sem página. Nome desconhecido é pulado em
     * silêncio aqui e reclamado em {@link BuildBattleBuilder#conferirPaleta}.
     */
    public List<Material> blocos() {
        List<Material> saida = new ArrayList<>();
        for (String nome : config.getStringList("blocos")) {
            Material qual = Material.matchMaterial(nome.trim().toUpperCase());
            if (qual != null && qual.isBlock() && !saida.contains(qual)) {
                saida.add(qual);
            }
            if (saida.size() >= CABE_NO_INVENTARIO) {
                break;
            }
        }
        return List.copyOf(saida);
    }

    /** Quantos blocos cabem no inventário de um jogador. */
    public static final int CABE_NO_INVENTARIO = 36;

    /** Quanto tempo a partida inteira leva, com {@code quantos} construtores. */
    public int duracaoTotal(int quantos) {
        return duracaoTotal(segundosDeTema(), segundosDeObra(),
                segundosDePasseio(), segundosDeVoto(), fimSegundos(), quantos);
    }

    public static int duracaoTotal(int tema, int obra, int passeio, int voto,
                                   int fim, int quantos) {
        int lotes = Math.max(1, quantos);
        return tema + obra + (passeio + voto) * lotes + fim;
    }

    // ------------------------------------------------------------------
    //  A grade de lotes
    // ------------------------------------------------------------------

    public int[] centroDoPlot(int k) {
        return centroDoPlot(k, plots(), plotsPorFiada(), passo());
    }

    /** O meio do lote {@code k}, contando da fiada do norte para o sul. */
    public static int[] centroDoPlot(int k, int quantos, int porFiada,
                                     int passo) {
        if (k < 0 || k >= quantos) {
            return null;
        }
        int fiadas = (int) Math.ceil(quantos / (double) porFiada);
        int col = k % porFiada;
        int fiada = k / porFiada;
        int x = (int) Math.round((col - (porFiada - 1) / 2.0) * passo);
        int z = (int) Math.round((fiada - (fiadas - 1) / 2.0) * passo);
        return new int[]{x, z};
    }

    public int plotDoPonto(double x, double z) {
        return plotDoPonto(x, z, plots(), plotsPorFiada(), passo(), plotLado());
    }

    /**
     * De que lote é o bloco, ou -1.
     *
     * Só o chão de obra conta: a borda do lote fica de fora de propósito, e é
     * por isso que a borda existe. Ela é a faixa onde o construtor vê que
     * acabou o dele, e onde {@code permiteColocar} diz não sem que o jogador
     * precise adivinhar onde é a linha.
     */
    public static int plotDoPonto(double x, double z, int quantos,
                                  int porFiada, int passo, int lado) {
        int meio = (lado - 1) / 2;
        for (int k = 0; k < quantos; k++) {
            int[] centro = centroDoPlot(k, quantos, porFiada, passo);
            if (centro == null) {
                continue;
            }
            if (Math.abs(x - centro[0]) <= meio
                    && Math.abs(z - centro[1]) <= meio) {
                return k;
            }
        }
        return -1;
    }

    /** Os blocos do chão de obra de um lote. */
    public static List<int[]> blocosDoPlot(int[] centro, int lado) {
        List<int[]> saida = new ArrayList<>();
        int meio = (lado - 1) / 2;
        for (int dx = -meio; dx <= meio; dx++) {
            for (int dz = -meio; dz <= meio; dz++) {
                saida.add(new int[]{centro[0] + dx, centro[1] + dz});
            }
        }
        return saida;
    }

    /**
     * Se o construtor saiu da caixa do próprio lote.
     *
     * A caixa é a única maneira honesta de ninguém copiar o vizinho, e custa
     * zero a quem não ia copiar: {@code limite-do-lote} é maior que o meio
     * lote, então dá para recuar e olhar a própria obra de longe. Em pé é
     * larga, em altura é o teto da obra com folga, e para baixo é larga
     * também — porque fazer o fundo de uma obra voando por baixo dela é
     * trabalho legítimo.
     */
    public boolean foraDoLote(double x, double y, double z, int plot) {
        int[] centro = centroDoPlot(plot);
        if (centro == null) {
            return false;
        }
        return foraDoLote(x, y, z, centro, limiteDoLote(), topoY(),
                alturaDeObra());
    }

    public static boolean foraDoLote(double x, double y, double z,
                                     int[] centro, int limite, int topoY,
                                     int alturaDeObra) {
        if (Math.abs(x - centro[0]) > limite
                || Math.abs(z - centro[1]) > limite) {
            return true;
        }
        return y > topoY + alturaDeObra + 6 || y < topoY - 12;
    }

    /**
     * Para que lado a plateia do lote {@code k} fica: -1 norte, +1 sul.
     *
     * A fiada do norte é vista do norte e a do sul é vista do sul, as duas
     * plateias fora da grade. É o que faz as duas fiadas caberem: com a
     * plateia sempre ao sul, a da segunda fiada cairia dentro do lote da
     * primeira — ver {@link BuildBattleBuilder#conferirGrade}.
     */
    public int rumoDaPlateia(int k) {
        return rumoDaPlateia(k, plotsPorFiada(), fiadas());
    }

    public static int rumoDaPlateia(int k, int porFiada, int fiadas) {
        int fiada = Math.max(0, k) / Math.max(1, porFiada);
        return fiada * 2 < fiadas ? -1 : 1;
    }

    /** O meio da plateia do lote {@code k}, do lado de fora da grade. */
    public int[] pontoDaPlateia(int k) {
        int[] centro = centroDoPlot(k);
        if (centro == null) {
            return null;
        }
        int rumo = rumoDaPlateia(k);
        return new int[]{centro[0],
                centro[1] + rumo * ((plotLado() - 1) / 2 + plateiaRecuo())};
    }

    /** Para que lado a placa da plateia olha: para a grade. */
    public static BlockFace frenteDaPlaca(int rumo) {
        return rumo < 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    // ------------------------------------------------------------------
    //  Os ângulos da plateia
    // ------------------------------------------------------------------

    /**
     * O ângulo de subida até o topo da obra, em graus.
     *
     * Toda obra é vista do MESMO rumo e da MESMA distância, e é por isso que
     * os lotes são quadrados alinhados com os eixos em vez de girados num
     * anel: obra nenhuma ganha ou perde nota por ângulo de câmera.
     */
    public static double anguloDeSubida(int alturaDeObra, int recuo,
                                        int plateiaAltura) {
        return Math.toDegrees(Math.atan2(
                Math.max(0, alturaDeObra - plateiaAltura), Math.max(1, recuo)));
    }

    /** O ângulo de abertura: o lote inteiro cabe na tela? */
    public static double anguloDeAbertura(int lado, int recuo) {
        return Math.toDegrees(2 * Math.atan2(lado / 2.0, Math.max(1, recuo)));
    }

    // ------------------------------------------------------------------
    //  A conta da nota
    // ------------------------------------------------------------------

    /**
     * A cédula de um votante virada em ordem, de 0 (o pior dele) a 1 (o
     * melhor dele).
     *
     * Posto médio: notas iguais dividem o posto. É daí que sai a propriedade
     * que o jogo precisa — quem dá a mesma nota para todos produz uma cédula
     * de postos todos iguais, que soma a mesma constante para todo mundo e
     * não desempata nada. Não é castigo a quem vota mal: é que votar igual
     * para todos é, literalmente, não dizer nada sobre a ordem.
     *
     * <p>Com uma obra só para julgar não há contra o que ordenar, e a nota
     * crua é tudo o que existe.
     */
    public static Map<Integer, Double> ordemDaCedula(Map<Integer, Integer> cedula,
                                                     int notas) {
        Map<Integer, Double> saida = new LinkedHashMap<>();
        int quantas = cedula.size();
        if (quantas == 0) {
            return saida;
        }
        if (quantas == 1) {
            for (Map.Entry<Integer, Integer> voto : cedula.entrySet()) {
                saida.put(voto.getKey(), (voto.getValue() - 1)
                        / (double) Math.max(1, notas - 1));
            }
            return saida;
        }
        for (Map.Entry<Integer, Integer> voto : cedula.entrySet()) {
            int menores = 0;
            int iguais = 0;
            for (int outro : cedula.values()) {
                if (outro < voto.getValue()) {
                    menores++;
                } else if (outro == voto.getValue()) {
                    iguais++;
                }
            }
            double posto = menores + (iguais + 1) / 2.0;
            saida.put(voto.getKey(), (posto - 1) / (double) (quantas - 1));
        }
        return saida;
    }

    /**
     * Se a cédula diz alguma coisa sobre a ordem das obras.
     *
     * Uma cédula de nota única — "meh em todos" — não diz nada: ela não
     * aponta nenhuma obra como melhor que outra.
     */
    public static boolean temOrdem(Map<Integer, Integer> cedula) {
        return new java.util.HashSet<>(cedula.values()).size() > 1;
    }

    /**
     * A nota final de cada lote, de 0 a 1: a média das ordens.
     *
     * <h3>A cédula sem ordem nenhuma é DESCARTADA, e por que isso importa</h3>
     *
     * Converter a cédula em ordem já tira a força do voto baixo, mas sobrava
     * uma brecha pequena e real. A cédula de nota única virava 0,5 em todos os
     * lotes MENOS no do próprio votante — ninguém vota em si mesmo —, e 0,5
     * em todos os outros COMPRIME as notas deles para o meio sem tocar na
     * dele. Com quinze votantes isso valia uns 7% da escala; não decide uma
     * partida, mas é vantagem por não ter opinião, e vantagem por não ter
     * opinião é exatamente o que este desenho existe para não dar.
     *
     * <p>Descartada, a cédula sem ordem vale ZERO exato: a conta fica
     * idêntica à que daria se aquele votante não tivesse votado — o que é a
     * verdade, porque ele não votou, ele só apertou o mesmo item em todo
     * mundo. {@code BuildBattleTest.aCedulaSemOrdemNaoEntraNaConta} prende a
     * igualdade.
     *
     * <p>Sobra um resíduo honesto: a cédula PARCIALMENTE chata (5, 5, 3)
     * ainda comprime um pouco as outras. É limitado por 1/(votantes-1) e não
     * serve de jogada, porque para usá-lo o votante abre mão de distinguir as
     * obras que ele quer distinguir.
     */
    public static Map<Integer, Double> notasFinais(
            Collection<Map<Integer, Integer>> cedulas, int notas) {
        Set<Integer> lotes = new LinkedHashSet<>();
        for (Map<Integer, Integer> cedula : cedulas) {
            lotes.addAll(cedula.keySet());
        }
        Map<Integer, Double> soma = new LinkedHashMap<>();
        Map<Integer, Integer> quantos = new LinkedHashMap<>();
        for (Map<Integer, Integer> cedula : cedulas) {
            if (!temOrdem(cedula)) {
                continue;
            }
            for (Map.Entry<Integer, Double> ordem
                    : ordemDaCedula(cedula, notas).entrySet()) {
                soma.merge(ordem.getKey(), ordem.getValue(), Double::sum);
                quantos.merge(ordem.getKey(), 1, Integer::sum);
            }
        }
        Map<Integer, Double> saida = new LinkedHashMap<>();
        for (int lote : lotes) {
            int votantes = quantos.getOrDefault(lote, 0);
            saida.put(lote, votantes == 0 ? 0.5
                    : soma.getOrDefault(lote, 0.0) / votantes);
        }
        return saida;
    }

    /**
     * A média crua das notas de um lote.
     *
     * É o que o jogador vê, porque "nota 4,2 de 5" é o que ele entende. A
     * ordem normalizada decide a COLOCAÇÃO e não aparece em lugar nenhum.
     */
    public static double mediaCrua(Collection<Map<Integer, Integer>> cedulas,
                                   int plot) {
        int soma = 0;
        int quantos = 0;
        for (Map<Integer, Integer> cedula : cedulas) {
            Integer voto = cedula.get(plot);
            if (voto != null) {
                soma += voto;
                quantos++;
            }
        }
        return quantos == 0 ? 0 : soma / (double) quantos;
    }

    /** O lote campeão: a maior nota final, com a média crua no desempate. */
    public static int campeao(Collection<Map<Integer, Integer>> cedulas,
                              int notas) {
        Map<Integer, Double> finais = notasFinais(cedulas, notas);
        int melhor = -1;
        double maior = -1;
        double maiorCrua = -1;
        for (Map.Entry<Integer, Double> item : finais.entrySet()) {
            double nota = item.getValue();
            double crua = mediaCrua(cedulas, item.getKey());
            if (nota > maior || (nota == maior && crua > maiorCrua)) {
                maior = nota;
                maiorCrua = crua;
                melhor = item.getKey();
            }
        }
        return melhor;
    }

    /** A ordem de colocação dos lotes, do primeiro ao último. */
    public static List<Integer> colocacao(
            Collection<Map<Integer, Integer>> cedulas, int notas) {
        Map<Integer, Double> finais = notasFinais(cedulas, notas);
        List<Integer> lotes = new ArrayList<>(finais.keySet());
        lotes.sort((a, b) -> {
            int cmp = Double.compare(finais.get(b), finais.get(a));
            if (cmp != 0) {
                return cmp;
            }
            cmp = Double.compare(mediaCrua(cedulas, b), mediaCrua(cedulas, a));
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        return List.copyOf(lotes);
    }
}
