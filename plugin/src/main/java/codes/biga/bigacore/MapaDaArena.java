package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Um mapa pronto já medido e encaixado na arena.
 *
 * <h2>O que esta classe resolve</h2>
 *
 * {@link EstruturaDeMapa} lê o arquivo; esta aqui responde as perguntas que o
 * jogo faz. Onde nasce o jogador 3? Quais baús são da ilha dele? Que blocos
 * somem quando o cerco derruba essa ilha? Num arena gerada, as três respostas
 * vêm de uma conta de trigonometria. Num mapa feito à mão, elas têm de ser
 * MEDIDAS — e é essa medição que mora aqui.
 *
 * <h2>As jaulas mandam</h2>
 *
 * Tudo se apoia nos marcadores de jaula. O centro do mapa é o centro delas; o
 * anel é a distância média delas ao centro; a ordem das ilhas é a ordem
 * angular delas, começando no norte, que é a mesma ordem da arena gerada — o
 * resto do jogo não percebe a troca. E cada bloco e cada baú pertencem à
 * jaula mais perto, o que dá o recorte das ilhas sem precisar de ninguém
 * desenhando contorno à mão.
 *
 * <h2>O recorte é por dono mais perto, com um teto</h2>
 *
 * Um bloco a trinta blocos de toda jaula não é de ilha nenhuma — é do meio,
 * ou é a ponte decorativa, ou é a torre do canto. {@link #raioDeRecorte} é
 * esse teto, e ele é medido: metade da distância entre duas jaulas vizinhas,
 * que é o ponto onde uma ilha deixa de ser mais perto que a outra. Sem o
 * teto, derrubar a primeira ilha levaria junto metade do meio.
 */
public final class MapaDaArena {

    private final String nome;
    private final EstruturaDeMapa estrutura;
    private final int[] desloca;
    private final List<int[]> jaulas = new ArrayList<>();
    private final List<List<int[]>> bausDaIlha = new ArrayList<>();
    private final List<int[]> bausDoMeio = new ArrayList<>();
    private final List<List<EstruturaDeMapa.Bloco>> blocosDaIlha =
            new ArrayList<>();
    private final List<EstruturaDeMapa.Bloco> blocosSoltos = new ArrayList<>();
    private final int raioDeRecorte;
    private int naoTraduzidos;

    /**
     * Monta o mapa já posicionado.
     *
     * @param yDaJaula      em que fiada do mundo a jaula tem de cair
     * @param etiquetaJaula a etiqueta dos marcadores de nascimento
     * @param etiquetasBau  as etiquetas de baú de ilha, em ordem de preferência
     * @param etiquetasMeio as etiquetas de baú do meio
     */
    public MapaDaArena(String nome, EstruturaDeMapa estrutura, int yDaJaula,
                       String etiquetaJaula, List<String> etiquetasBau,
                       List<String> etiquetasMeio) {
        this.nome = nome;
        this.estrutura = estrutura;
        int[] centro = estrutura.centroPelosMarcos(etiquetaJaula);
        this.desloca = EstruturaDeMapa.deslocamentoParaArena(centro, yDaJaula);

        for (EstruturaDeMapa.Marco marco : estrutura.comEtiqueta(etiquetaJaula)) {
            jaulas.add(pontoDoMarco(marco));
        }
        emOrdemDoNorte(jaulas);

        List<int[]> todosOsBaus = new ArrayList<>();
        for (String etiqueta : etiquetasBau) {
            for (EstruturaDeMapa.Marco marco : estrutura.comEtiqueta(etiqueta)) {
                todosOsBaus.add(pontoDoMarco(marco));
            }
        }
        for (String etiqueta : etiquetasMeio) {
            for (EstruturaDeMapa.Marco marco : estrutura.comEtiqueta(etiqueta)) {
                bausDoMeio.add(pontoDoMarco(marco));
            }
        }

        this.raioDeRecorte = raioDeRecorte(jaulas);
        for (int k = 0; k < jaulas.size(); k++) {
            bausDaIlha.add(new ArrayList<>());
            blocosDaIlha.add(new ArrayList<>());
        }
        for (int[] bau : todosOsBaus) {
            int dono = ilhaMaisPerto(bau[0], bau[2], jaulas, raioDeRecorte);
            if (dono >= 0) {
                bausDaIlha.get(dono).add(bau);
            } else {
                bausDoMeio.add(bau);
            }
        }
        for (EstruturaDeMapa.Bloco bloco : estrutura.blocos()) {
            EstruturaDeMapa.Bloco posto = new EstruturaDeMapa.Bloco(
                    bloco.x() + desloca[0], bloco.y() + desloca[1],
                    bloco.z() + desloca[2], bloco.estado());
            int dono = ilhaMaisPerto(posto.x(), posto.z(), jaulas,
                    raioDeRecorte);
            if (dono >= 0) {
                blocosDaIlha.get(dono).add(posto);
            } else {
                blocosSoltos.add(posto);
            }
        }
    }

    private int[] pontoDoMarco(EstruturaDeMapa.Marco marco) {
        return new int[]{
                (int) Math.floor(marco.x()) + desloca[0],
                (int) Math.floor(marco.y()) + desloca[1],
                (int) Math.floor(marco.z()) + desloca[2]};
    }

    // ------------------------------------------------------------------
    //  As medidas, puras e testáveis
    // ------------------------------------------------------------------

    /**
     * Põe as jaulas na ordem da arena gerada: norte primeiro, daí no sentido
     * do relógio.
     *
     * <h2>Por que não basta ordenar pelo ângulo</h2>
     *
     * Porque o norte é onde o ângulo dá a volta, e ninguém constrói um mapa
     * com a jaula exatamente no eixo. A do mapa baixado está a um décimo de
     * bloco a oeste do norte — ângulo 6,28 em vez de 0,00 —, e uma ordenação
     * crua a manda para o FIM da lista. A arena passaria a numerar as ilhas
     * começando na vizinha, e a ordem de queda do cerco, o lugar de cada
     * jogador e o número nas mensagens sairiam todos girados de uma casa.
     *
     * <p>Então ordena-se pelo ângulo e depois GIRA-SE a lista até a jaula
     * mais perto do norte ficar na frente. A ordem relativa, que é a que o
     * cerco usa, não muda; só o ponto de partida.
     */
    static void emOrdemDoNorte(List<int[]> jaulas) {
        if (jaulas.size() < 2) {
            return;
        }
        jaulas.sort(Comparator.comparingDouble(
                onde -> ArenaComum.anguloDoNorte(onde[0], onde[2])));
        int primeira = 0;
        double menor = Double.MAX_VALUE;
        for (int k = 0; k < jaulas.size(); k++) {
            double angulo = ArenaComum.anguloDoNorte(jaulas.get(k)[0],
                    jaulas.get(k)[2]);
            double doNorte = Math.min(angulo, 2 * Math.PI - angulo);
            if (doNorte < menor) {
                menor = doNorte;
                primeira = k;
            }
        }
        if (primeira == 0) {
            return;
        }
        List<int[]> girada = new ArrayList<>(jaulas.size());
        for (int k = 0; k < jaulas.size(); k++) {
            girada.add(jaulas.get((primeira + k) % jaulas.size()));
        }
        jaulas.clear();
        jaulas.addAll(girada);
    }

    /**
     * Até onde um bloco ainda conta como sendo de uma ilha.
     *
     * <p>É metade da menor distância entre duas jaulas: o ponto em que uma
     * ilha deixa de ser a mais perto. Com uma jaula só — mapa estranho, mas
     * possível — não há vizinha para medir, e o teto vira a distância ao
     * centro, que é o que sobra de sensato.
     */
    public static int raioDeRecorte(List<int[]> jaulas) {
        if (jaulas.size() < 2) {
            if (jaulas.isEmpty()) {
                return 0;
            }
            int[] so = jaulas.get(0);
            return Math.max(4, (int) Math.round(
                    Math.hypot(so[0], so[2]) / 2.0));
        }
        double menor = Double.MAX_VALUE;
        for (int a = 0; a < jaulas.size(); a++) {
            for (int b = a + 1; b < jaulas.size(); b++) {
                menor = Math.min(menor, Math.hypot(
                        jaulas.get(a)[0] - jaulas.get(b)[0],
                        jaulas.get(a)[2] - jaulas.get(b)[2]));
            }
        }
        return Math.max(4, (int) Math.floor(menor / 2.0));
    }

    /** Qual jaula é a dona deste ponto, ou -1 se nenhuma alcança. */
    public static int ilhaMaisPerto(int x, int z, List<int[]> jaulas,
                                    int raioMaximo) {
        int dono = -1;
        double melhor = Double.MAX_VALUE;
        for (int k = 0; k < jaulas.size(); k++) {
            double distancia = Math.hypot(x - jaulas.get(k)[0],
                    z - jaulas.get(k)[2]);
            if (distancia < melhor) {
                melhor = distancia;
                dono = k;
            }
        }
        return melhor <= raioMaximo ? dono : -1;
    }

    /** A distância média das jaulas ao centro: o anel do mapa. */
    public int anel() {
        if (jaulas.isEmpty()) {
            return 0;
        }
        double soma = 0;
        for (int[] jaula : jaulas) {
            soma += Math.hypot(jaula[0], jaula[2]);
        }
        return Math.max(1, (int) Math.round(soma / jaulas.size()));
    }

    /** O maior alcance de uma ilha a partir da jaula dela. */
    public int raioDaIlha() {
        int maior = 0;
        for (int k = 0; k < jaulas.size(); k++) {
            int[] jaula = jaulas.get(k);
            for (EstruturaDeMapa.Bloco bloco : blocosDaIlha.get(k)) {
                maior = Math.max(maior, (int) Math.ceil(Math.hypot(
                        bloco.x() - jaula[0], bloco.z() - jaula[2])));
            }
        }
        return Math.max(1, maior);
    }

    /** O maior alcance do que não é ilha: o meio e o que o cerca. */
    public int raioDoMeio() {
        int maior = 0;
        for (EstruturaDeMapa.Bloco bloco : blocosSoltos) {
            maior = Math.max(maior, (int) Math.ceil(
                    Math.hypot(bloco.x(), bloco.z())));
        }
        return Math.max(1, maior);
    }

    /** A fiada em que o jogador nasce. */
    public int yDaJaula() {
        return jaulas.isEmpty() ? 0 : jaulas.get(0)[1];
    }

    /** A fiada mais alta e a mais baixa que o mapa ocupa, já posicionadas. */
    public int[] alturas() {
        int baixo = Integer.MAX_VALUE;
        int alto = Integer.MIN_VALUE;
        for (List<EstruturaDeMapa.Bloco> lista : blocosDaIlha) {
            for (EstruturaDeMapa.Bloco bloco : lista) {
                baixo = Math.min(baixo, bloco.y());
                alto = Math.max(alto, bloco.y());
            }
        }
        for (EstruturaDeMapa.Bloco bloco : blocosSoltos) {
            baixo = Math.min(baixo, bloco.y());
            alto = Math.max(alto, bloco.y());
        }
        if (baixo > alto) {
            return new int[]{yDaJaula(), yDaJaula()};
        }
        return new int[]{baixo, alto};
    }

    /** O raio horizontal que o mapa inteiro ocupa. */
    public int alcance() {
        int maior = 0;
        for (List<EstruturaDeMapa.Bloco> lista : blocosDaIlha) {
            for (EstruturaDeMapa.Bloco bloco : lista) {
                maior = Math.max(maior, Math.max(Math.abs(bloco.x()),
                        Math.abs(bloco.z())));
            }
        }
        for (EstruturaDeMapa.Bloco bloco : blocosSoltos) {
            maior = Math.max(maior, Math.max(Math.abs(bloco.x()),
                    Math.abs(bloco.z())));
        }
        return maior;
    }

    // ------------------------------------------------------------------
    //  O que o jogo pergunta
    // ------------------------------------------------------------------

    public String nome() {
        return nome;
    }

    public int ilhas() {
        return jaulas.size();
    }

    public boolean serve() {
        return jaulas.size() >= 2 && !estrutura.vazio();
    }

    /** O {x, y, z} da jaula k, já no mundo. */
    public int[] jaula(int k) {
        return k >= 0 && k < jaulas.size() ? jaulas.get(k).clone() : null;
    }

    /** Os baús da ilha k, cada um {x, y, z}. */
    public List<int[]> bausDaIlha(int k) {
        return k >= 0 && k < bausDaIlha.size()
                ? List.copyOf(bausDaIlha.get(k)) : List.of();
    }

    /** Os baús que não são de ilha nenhuma: o meio e o semi-meio. */
    public List<int[]> bausDoMeio() {
        return List.copyOf(bausDoMeio);
    }

    public int quantosBlocos() {
        return estrutura.blocos().size();
    }

    /** Quantos blocos o recorte deu para a ilha k. Para o teste conferir. */
    int blocosDaIlhaParaTeste(int k) {
        return k >= 0 && k < blocosDaIlha.size()
                ? blocosDaIlha.get(k).size() : 0;
    }

    public int naoTraduzidos() {
        return naoTraduzidos;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /** Põe o mapa inteiro no mundo. */
    public int colar(World mundo, Logger diario) {
        Map<String, BlockData> traduzidos = new HashMap<>();
        naoTraduzidos = 0;
        int postos = 0;
        for (List<EstruturaDeMapa.Bloco> lista : blocosDaIlha) {
            postos += colarLista(mundo, lista, traduzidos, diario);
        }
        postos += colarLista(mundo, blocosSoltos, traduzidos, diario);
        if (naoTraduzidos > 0 && diario != null) {
            diario.warning("o mapa '" + nome + "' tem " + naoTraduzidos
                    + " bloco(s) que este Minecraft não conhece — eles "
                    + "ficaram de fora e o resto do mapa subiu");
        }
        return postos;
    }

    /** Apaga os blocos da ilha k: é isso que o cerco faz. */
    public int apagarIlha(World mundo, int k) {
        if (k < 0 || k >= blocosDaIlha.size()) {
            return 0;
        }
        int tirados = 0;
        for (EstruturaDeMapa.Bloco bloco : blocosDaIlha.get(k)) {
            Block onde = mundo.getBlockAt(bloco.x(), bloco.y(), bloco.z());
            if (!onde.getType().isAir()) {
                onde.setType(Material.AIR, false);
                tirados++;
            }
        }
        return tirados;
    }

    /** Levanta de volta a ilha k, para a rodada seguinte. */
    public int refazerIlha(World mundo, int k, Logger diario) {
        if (k < 0 || k >= blocosDaIlha.size()) {
            return 0;
        }
        return colarLista(mundo, blocosDaIlha.get(k), new HashMap<>(), diario);
    }

    private int colarLista(World mundo, List<EstruturaDeMapa.Bloco> lista,
                           Map<String, BlockData> traduzidos, Logger diario) {
        int postos = 0;
        for (EstruturaDeMapa.Bloco bloco : lista) {
            BlockData dado;
            if (traduzidos.containsKey(bloco.estado())) {
                dado = traduzidos.get(bloco.estado());
            } else {
                dado = traduzir(bloco.estado(), diario);
                traduzidos.put(bloco.estado(), dado);
            }
            if (dado == null) {
                naoTraduzidos++;
                continue;
            }
            mundo.getBlockAt(bloco.x(), bloco.y(), bloco.z())
                    .setBlockData(dado, false);
            postos++;
        }
        return postos;
    }

    /**
     * O texto do estado virado bloco de verdade.
     *
     * <p>Um mapa feito numa versão mais velha pode ter bloco ou propriedade
     * que esta versão não tem mais. O que não traduz é PULADO, e não é
     * acidente: um mapa com três blocos desconhecidos e tudo o resto no lugar
     * é uma arena jogável; um mapa que se recusa a subir por causa deles é
     * uma arena que não existe.
     */
    static BlockData traduzir(String estado, Logger diario) {
        try {
            return Bukkit.createBlockData(estado);
        } catch (IllegalArgumentException naoExiste) {
            int colchete = estado.indexOf('[');
            if (colchete > 0) {
                try {
                    return Bukkit.createBlockData(estado.substring(0, colchete));
                } catch (IllegalArgumentException nemAssim) {
                    // cai para o aviso
                }
            }
            if (diario != null) {
                diario.log(Level.FINE, "bloco desconhecido no mapa: " + estado);
            }
            return null;
        }
    }

    // ------------------------------------------------------------------

    /** Lê e monta num passo só. */
    public static MapaDaArena abrir(File pasta, String nome, int grade,
                                    int passo, int yDaJaula,
                                    String etiquetaJaula,
                                    List<String> etiquetasBau,
                                    List<String> etiquetasMeio)
            throws IOException {
        return new MapaDaArena(nome, EstruturaDeMapa.ler(pasta, nome, grade,
                passo), yDaJaula, etiquetaJaula, etiquetasBau, etiquetasMeio);
    }
}
