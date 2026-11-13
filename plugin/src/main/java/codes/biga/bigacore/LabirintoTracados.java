package codes.biga.bigacore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Os cinco traçados do LABIRINTO — cinco algoritmos, cinco texturas.
 *
 * <h2>Por que cinco, e não vinte</h2>
 *
 * A reclamação que deu origem a este arquivo é justa e é de quem joga: um
 * labirinto só acaba decorado. A resposta errada seria desenhar vinte
 * labirintos à mão — seriam decorados em vinte dias, custariam vinte vezes a
 * manutenção, e os vinte teriam a mesma CARA, porque mapa diferente não é a
 * mesma coisa que labirinto diferente.
 *
 * <p>O que muda a cara de um labirinto é o ALGORITMO que o cava. Cada um
 * tem uma assinatura estatística própria e reconhecível, e essa assinatura é
 * o que o jogador sente antes de saber nomear:
 *
 * <ul>
 *   <li><b>{@link #FUNDO}</b> (cavar em profundidade, sempre pelo galho mais
 *       novo) — corredores longos e sinuosos, pouquíssimos becos: cerca de um
 *       décimo das casas. É o labirinto de se perder andando, e era o único
 *       que este jogo tinha.</li>
 *   <li><b>{@link #GALHOS}</b> (Prim aleatório) — o oposto exato: mais de um
 *       terço das casas são becos curtos. É o labirinto de se perder
 *       ESCOLHENDO, onde cada esquina oferece três portas e duas não levam a
 *       nada.</li>
 *   <li><b>{@link #VENTANIA}</b> (sidewinder) — cava fiada por fiada, de
 *       leste a oeste. Dá corredores retos e compridos, uma avenida aberta
 *       de ponta a ponta na fiada do norte, e uma propriedade que o jogador
 *       descobre jogando e nunca esquece: <b>nenhum beco abre para o
 *       sul</b>. Quem souber disso anda o labirinto de cabeça.</li>
 *   <li><b>{@link #JUSTO}</b> (Wilson, passeio aleatório com laço apagado) —
 *       o labirinto sem viés nenhum. Não é o mais bonito nem o mais difícil:
 *       é o único em que não há truque a aprender, porque ele é sorteado com
 *       igual probabilidade entre TODOS os labirintos possíveis daquele
 *       tamanho. Está aqui por isso.</li>
 *   <li><b>{@link #SALOES}</b> (divisão recursiva) — não cava corredor:
 *       parte o terreno ao meio com um muro reto e abre uma porta. Sai
 *       arquitetura em vez de toca — salas, muros compridos e portas. É o
 *       único que não parece labirinto de jogo, e parece prédio.</li>
 * </ul>
 *
 * <p>Um sexto algoritmo caberia — Kruskal, por exemplo — e não entrou de
 * propósito: a textura dele é a do {@link #GALHOS} com outro nome, e o
 * jogador sentiria a repetição. Cinco traçados que se distinguem valem mais
 * que vinte que não se distinguem, e é exatamente o mesmo argumento contra
 * os vinte labirintos desenhados à mão.
 *
 * <h2>Todos devolvem a mesma coisa</h2>
 *
 * Um vetor de {@code celulas*celulas*2} passagens, idêntico ao que o resto do
 * jogo já consumia. Nada a jusante sabe qual algoritmo correu: o mapa de
 * blocos, a conta de distância, a porta e a conferência de andabilidade são
 * os mesmos para os cinco. É o que torna acrescentar um traçado uma mudança
 * de um arquivo só.
 */
public final class LabirintoTracados {

    public static final int FUNDO = 0;
    public static final int GALHOS = 1;
    public static final int VENTANIA = 2;
    public static final int JUSTO = 3;
    public static final int SALOES = 4;

    /** Os nomes como o jogador lê, na ordem das constantes. */
    public static final List<String> NOMES =
            List.of("FUNDO", "GALHOS", "VENTANIA", "JUSTO", "SALÕES");

    /** O que avisar ao jogador sobre o traçado da rodada. */
    public static final List<String> CARATER = List.of(
            "corredores longos e sinuosos, quase sem beco",
            "um beco a cada três esquinas: escolher é o jogo",
            "avenidas retas de leste a oeste, e nenhum beco abre ao sul",
            "sorteado sem viés nenhum: não há truque a aprender",
            "salas e muros compridos: é prédio, não é toca");

    private LabirintoTracados() {
    }

    public static int quantos() {
        return NOMES.size();
    }

    public static String nome(int tracado) {
        return NOMES.get(Math.max(0, Math.min(NOMES.size() - 1, tracado)));
    }

    public static String carater(int tracado) {
        return CARATER.get(Math.max(0, Math.min(CARATER.size() - 1, tracado)));
    }

    /**
     * O traçado de nome {@code nome}, ou -1 se não houver.
     *
     * Sem acento e sem caixa: "SALÕES", "saloes" e "Salões" são o mesmo
     * traçado. O nome que o jogador lê tem til e o yml é escrito à mão num
     * teclado qualquer — exigir o til ali seria um erro de digitação que
     * desliga um traçado dos cinco e não explica por quê.
     */
    public static int porNome(String nome) {
        String liso = semAcento(nome);
        for (int k = 0; k < NOMES.size(); k++) {
            if (semAcento(NOMES.get(k)).equals(liso)) {
                return k;
            }
        }
        return -1;
    }

    private static String semAcento(String texto) {
        return java.text.Normalizer.normalize(texto.trim(),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------
    //  A porta de entrada
    // ------------------------------------------------------------------

    /**
     * Cava o labirinto pelo traçado pedido, abre as voltas e abre a sala.
     *
     * As três etapas são sempre as mesmas e nesta ordem. Abrir as voltas
     * DEPOIS de cavar é o que garante que elas sejam voltas de verdade: cada
     * parede aberta num labirinto já perfeito é um ciclo, e ciclo é rota
     * alternativa. Abrir antes seria só cavar com outro nome.
     *
     * @param tracado uma das cinco constantes desta classe
     * @param celulas o lado da grade, em células
     * @param semente a semente; a mesma semente dá sempre o mesmo labirinto
     * @param voltas  quantas paredes internas abrir depois de cavar
     * @param sala    o raio, em células, da sala do meio
     */
    public static boolean[] cavar(int tracado, int celulas, long semente,
                                  int voltas, int sala) {
        Random sorte = new Random(semente);
        boolean[] ligado = switch (tracado) {
            case GALHOS -> galhos(celulas, sorte);
            case VENTANIA -> ventania(celulas, sorte);
            case JUSTO -> justo(celulas, sorte);
            case SALOES -> saloes(celulas, sorte);
            default -> fundo(celulas, sorte);
        };
        abrirAsVoltas(ligado, celulas, voltas, sorte);
        abrirASala(ligado, celulas, sala);
        return ligado;
    }

    // ------------------------------------------------------------------
    //  Os cinco
    // ------------------------------------------------------------------

    /**
     * FUNDO: em profundidade, sempre pelo galho mais novo.
     *
     * Empilha a casa visitada e só desempilha quando ela não tem mais vizinho
     * virgem. É essa teimosia em seguir em frente que dá o corredor comprido:
     * o algoritmo só volta atrás quando bate em parede de verdade, e o
     * resultado tem um décimo de becos contra um terço dos outros.
     */
    static boolean[] fundo(int celulas, Random sorte) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        boolean[] visto = new boolean[celulas * celulas];
        int meio = celulas / 2;

        Deque<int[]> pilha = new ArrayDeque<>();
        pilha.push(new int[]{meio, meio});
        visto[LabirintoCatalog.indice(celulas, meio, meio)] = true;

        List<int[]> livres = new ArrayList<>(4);
        while (!pilha.isEmpty()) {
            int[] onde = pilha.peek();
            livres.clear();
            for (int[] rumo : LabirintoCatalog.RUMOS) {
                int ni = onde[0] + rumo[0];
                int nj = onde[1] + rumo[1];
                if (LabirintoCatalog.dentro(celulas, ni, nj)
                        && !visto[LabirintoCatalog.indice(celulas, ni, nj)]) {
                    livres.add(new int[]{ni, nj});
                }
            }
            if (livres.isEmpty()) {
                pilha.pop();
                continue;
            }
            int[] escolhido = livres.get(sorte.nextInt(livres.size()));
            abrir(ligado, celulas, onde[0], onde[1], escolhido[0], escolhido[1]);
            visto[LabirintoCatalog.indice(celulas, escolhido[0], escolhido[1])] = true;
            pilha.push(escolhido);
        }
        return ligado;
    }

    /**
     * GALHOS: Prim aleatório, crescendo em todas as direções ao mesmo tempo.
     *
     * Guarda uma FRONTEIRA de casas vizinhas ao que já existe e sorteia
     * sempre nela — nunca continua de onde parou. Por isso o labirinto cresce
     * como um arbusto, em vez de como um túnel, e por isso mais de um terço
     * das casas acabam em beco curto. É o avesso exato do {@link #fundo}, e
     * é o contraste que faz as duas rodadas parecerem jogos diferentes.
     */
    static boolean[] galhos(int celulas, Random sorte) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        boolean[] dentro = new boolean[celulas * celulas];
        boolean[] naFronteira = new boolean[celulas * celulas];
        List<int[]> fronteira = new ArrayList<>();
        int meio = celulas / 2;

        dentro[LabirintoCatalog.indice(celulas, meio, meio)] = true;
        juntarVizinhos(celulas, meio, meio, dentro, naFronteira, fronteira);

        List<int[]> presos = new ArrayList<>(4);
        while (!fronteira.isEmpty()) {
            int[] onde = fronteira.remove(sorte.nextInt(fronteira.size()));
            int i = onde[0];
            int j = onde[1];
            if (dentro[LabirintoCatalog.indice(celulas, i, j)]) {
                continue;
            }
            presos.clear();
            for (int[] rumo : LabirintoCatalog.RUMOS) {
                int ni = i + rumo[0];
                int nj = j + rumo[1];
                if (LabirintoCatalog.dentro(celulas, ni, nj)
                        && dentro[LabirintoCatalog.indice(celulas, ni, nj)]) {
                    presos.add(new int[]{ni, nj});
                }
            }
            if (presos.isEmpty()) {
                continue;
            }
            int[] pai = presos.get(sorte.nextInt(presos.size()));
            abrir(ligado, celulas, i, j, pai[0], pai[1]);
            dentro[LabirintoCatalog.indice(celulas, i, j)] = true;
            juntarVizinhos(celulas, i, j, dentro, naFronteira, fronteira);
        }
        return ligado;
    }

    private static void juntarVizinhos(int celulas, int i, int j,
                                       boolean[] dentro, boolean[] naFronteira,
                                       List<int[]> fronteira) {
        for (int[] rumo : LabirintoCatalog.RUMOS) {
            int ni = i + rumo[0];
            int nj = j + rumo[1];
            if (!LabirintoCatalog.dentro(celulas, ni, nj)) {
                continue;
            }
            int onde = LabirintoCatalog.indice(celulas, ni, nj);
            if (dentro[onde] || naFronteira[onde]) {
                continue;
            }
            naFronteira[onde] = true;
            fronteira.add(new int[]{ni, nj});
        }
    }

    /**
     * VENTANIA: sidewinder, fiada por fiada, de leste a oeste.
     *
     * Cada fiada é percorrida juntando casas numa CORRIDA; a cada passo, ou a
     * corrida cresce para o leste, ou ela fecha e uma casa sorteada dela
     * sobe para o norte. A fiada do norte não tem para onde subir, então sai
     * inteira aberta: é a avenida que atravessa o labirinto de ponta a ponta.
     *
     * <p>E daí vem a propriedade que o jogador aprende jogando: como toda
     * casa pertence a uma corrida que subiu, <b>nenhum beco abre para o
     * sul</b>. Seguir sempre para o norte sempre leva a algum lugar. Não é
     * defeito: é o tipo de regra que transforma reconhecer o traçado em
     * vantagem, e é de graça para quem presta atenção.
     */
    static boolean[] ventania(int celulas, Random sorte) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        for (int i = 0; i + 1 < celulas; i++) {
            ligado[LabirintoCatalog.ligacao(celulas, i, 0, 0)] = true;
        }
        List<Integer> corrida = new ArrayList<>();
        for (int j = 1; j < celulas; j++) {
            corrida.clear();
            for (int i = 0; i < celulas; i++) {
                corrida.add(i);
                boolean fecha = i == celulas - 1 || sorte.nextBoolean();
                if (fecha) {
                    int qual = corrida.get(sorte.nextInt(corrida.size()));
                    ligado[LabirintoCatalog.ligacao(celulas, qual, j - 1, 1)] = true;
                    corrida.clear();
                } else {
                    ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)] = true;
                }
            }
        }
        return ligado;
    }

    /**
     * JUSTO: Wilson, passeio aleatório com laço apagado.
     *
     * Sorteia uma casa de fora, passeia ao acaso até esbarrar no que já
     * existe — apagando os laços que o passeio fizer no caminho — e só então
     * grava o trajeto. Parece desperdício e é o contrário: é o único
     * procedimento aqui que sorteia com igual probabilidade entre TODOS os
     * labirintos possíveis da grade. Os outros quatro têm cada um a sua
     * mania; este não tem nenhuma, e é esse o ponto dele.
     *
     * <p>O passeio é limitado por um teto de passos. Não porque ele não
     * termine — termina, com probabilidade 1 — mas porque "com probabilidade
     * 1" não é promessa que se faça a um servidor com dezesseis jogadores
     * esperando: se estourar, o que resta é cavado em profundidade e a
     * rodada acontece.
     */
    static boolean[] justo(int celulas, Random sorte) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        boolean[] dentro = new boolean[celulas * celulas];
        int total = celulas * celulas;
        int meio = celulas / 2;
        dentro[LabirintoCatalog.indice(celulas, meio, meio)] = true;
        int faltam = total - 1;

        List<int[]> forasteiras = new ArrayList<>();
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (!dentro[LabirintoCatalog.indice(celulas, i, j)]) {
                    forasteiras.add(new int[]{i, j});
                }
            }
        }
        Collections.shuffle(forasteiras, sorte);

        long teto = 400L * total;
        long passos = 0;
        Map<Integer, int[]> rumoDe = new HashMap<>();
        for (int[] inicio : forasteiras) {
            if (faltam <= 0 || dentro[LabirintoCatalog.indice(celulas,
                    inicio[0], inicio[1])]) {
                continue;
            }
            rumoDe.clear();
            int ci = inicio[0];
            int cj = inicio[1];
            while (!dentro[LabirintoCatalog.indice(celulas, ci, cj)]) {
                if (++passos > teto) {
                    return completarEmProfundidade(ligado, dentro, celulas, sorte);
                }
                int[] rumo = LabirintoCatalog.RUMOS[sorte.nextInt(4)];
                int ni = ci + rumo[0];
                int nj = cj + rumo[1];
                if (!LabirintoCatalog.dentro(celulas, ni, nj)) {
                    continue;
                }
                rumoDe.put(LabirintoCatalog.indice(celulas, ci, cj),
                        new int[]{ni, nj});
                ci = ni;
                cj = nj;
            }
            ci = inicio[0];
            cj = inicio[1];
            while (!dentro[LabirintoCatalog.indice(celulas, ci, cj)]) {
                int[] adiante = rumoDe.get(LabirintoCatalog.indice(celulas, ci, cj));
                abrir(ligado, celulas, ci, cj, adiante[0], adiante[1]);
                dentro[LabirintoCatalog.indice(celulas, ci, cj)] = true;
                faltam--;
                ci = adiante[0];
                cj = adiante[1];
            }
        }
        return ligado;
    }

    /** O desempate do {@link #justo}: liga o que sobrou, e a rodada acontece. */
    private static boolean[] completarEmProfundidade(boolean[] ligado,
                                                     boolean[] dentro,
                                                     int celulas, Random sorte) {
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (dentro[LabirintoCatalog.indice(celulas, i, j)]) {
                    continue;
                }
                List<int[]> presos = new ArrayList<>(4);
                for (int[] rumo : LabirintoCatalog.RUMOS) {
                    int ni = i + rumo[0];
                    int nj = j + rumo[1];
                    if (LabirintoCatalog.dentro(celulas, ni, nj)
                            && dentro[LabirintoCatalog.indice(celulas, ni, nj)]) {
                        presos.add(new int[]{ni, nj});
                    }
                }
                if (presos.isEmpty()) {
                    continue;
                }
                int[] pai = presos.get(sorte.nextInt(presos.size()));
                abrir(ligado, celulas, i, j, pai[0], pai[1]);
                dentro[LabirintoCatalog.indice(celulas, i, j)] = true;
            }
        }
        // Segunda passada ao contrário, para as casas que ainda não tinham
        // vizinho ligado quando a primeira passou por elas.
        for (int j = celulas - 1; j >= 0; j--) {
            for (int i = celulas - 1; i >= 0; i--) {
                if (dentro[LabirintoCatalog.indice(celulas, i, j)]) {
                    continue;
                }
                for (int[] rumo : LabirintoCatalog.RUMOS) {
                    int ni = i + rumo[0];
                    int nj = j + rumo[1];
                    if (LabirintoCatalog.dentro(celulas, ni, nj)
                            && dentro[LabirintoCatalog.indice(celulas, ni, nj)]) {
                        abrir(ligado, celulas, i, j, ni, nj);
                        dentro[LabirintoCatalog.indice(celulas, i, j)] = true;
                        break;
                    }
                }
            }
        }
        return ligado;
    }

    /**
     * SALÕES: divisão recursiva — o único que não cava, e sim constrói.
     *
     * Começa com o terreno TODO aberto, um descampado, e vai partindo ao meio
     * com um muro reto que tem uma porta só. Depois parte cada metade do
     * mesmo jeito, até não caber mais muro. O resultado não tem nada da toca
     * dos outros quatro: tem salas de tamanhos diferentes, muros compridos
     * que se veem de longe, e portas. Num jogo em que o jogador corre olhando
     * para frente, essa diferença é a mais visível das cinco.
     */
    static boolean[] saloes(int celulas, Random sorte) {
        boolean[] ligado = new boolean[celulas * celulas * 2];
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (i + 1 < celulas) {
                    ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)] = true;
                }
                if (j + 1 < celulas) {
                    ligado[LabirintoCatalog.ligacao(celulas, i, j, 1)] = true;
                }
            }
        }
        dividir(ligado, celulas, 0, celulas - 1, 0, celulas - 1, sorte);
        return ligado;
    }

    private static void dividir(boolean[] ligado, int celulas, int i0, int i1,
                                int j0, int j1, Random sorte) {
        int largura = i1 - i0 + 1;
        int altura = j1 - j0 + 1;
        if (largura < 2 && altura < 2) {
            return;
        }
        boolean emPe = largura == altura ? sorte.nextBoolean() : largura > altura;
        if (emPe && largura >= 2) {
            int coluna = i0 + sorte.nextInt(largura - 1);
            int porta = j0 + sorte.nextInt(altura);
            for (int j = j0; j <= j1; j++) {
                if (j != porta) {
                    ligado[LabirintoCatalog.ligacao(celulas, coluna, j, 0)] = false;
                }
            }
            dividir(ligado, celulas, i0, coluna, j0, j1, sorte);
            dividir(ligado, celulas, coluna + 1, i1, j0, j1, sorte);
        } else if (altura >= 2) {
            int fiada = j0 + sorte.nextInt(altura - 1);
            int porta = i0 + sorte.nextInt(largura);
            for (int i = i0; i <= i1; i++) {
                if (i != porta) {
                    ligado[LabirintoCatalog.ligacao(celulas, i, fiada, 1)] = false;
                }
            }
            dividir(ligado, celulas, i0, i1, j0, fiada, sorte);
            dividir(ligado, celulas, i0, i1, fiada + 1, j1, sorte);
        }
    }

    // ------------------------------------------------------------------
    //  O que é igual nos cinco
    // ------------------------------------------------------------------

    /**
     * As voltas: paredes internas ainda fechadas, sorteadas sem repetir.
     *
     * O labirinto perfeito tem um caminho e só um entre dois pontos
     * quaisquer. Isso é bonito no papel e ruim numa corrida de dezesseis:
     * errar a curva custa a partida, e os quinze de trás não têm o que fazer
     * além de seguir o primeiro. Cada parede aberta aqui é um ciclo, cada
     * ciclo é uma rota, e é a rota que transforma seguir em disputar.
     */
    static void abrirAsVoltas(boolean[] ligado, int celulas, int voltas,
                              Random sorte) {
        if (voltas <= 0) {
            return;
        }
        List<int[]> fechadas = new ArrayList<>();
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (i + 1 < celulas
                        && !ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)]) {
                    fechadas.add(new int[]{i, j, 0});
                }
                if (j + 1 < celulas
                        && !ligado[LabirintoCatalog.ligacao(celulas, i, j, 1)]) {
                    fechadas.add(new int[]{i, j, 1});
                }
            }
        }
        Collections.shuffle(fechadas, sorte);
        for (int k = 0; k < Math.min(voltas, fechadas.size()); k++) {
            int[] qual = fechadas.get(k);
            ligado[LabirintoCatalog.ligacao(celulas, qual[0], qual[1], qual[2])] = true;
        }
    }

    /** A sala do meio: todas as paredes internas dela caem. */
    static void abrirASala(boolean[] ligado, int celulas, int sala) {
        int meio = celulas / 2;
        for (int j = meio - sala; j <= meio + sala; j++) {
            for (int i = meio - sala; i <= meio + sala; i++) {
                if (i + 1 <= meio + sala) {
                    ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)] = true;
                }
                if (j + 1 <= meio + sala) {
                    ligado[LabirintoCatalog.ligacao(celulas, i, j, 1)] = true;
                }
            }
        }
    }

    static void abrir(boolean[] ligado, int celulas, int i, int j, int ni,
                      int nj) {
        if (ni > i) {
            ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)] = true;
        } else if (ni < i) {
            ligado[LabirintoCatalog.ligacao(celulas, ni, nj, 0)] = true;
        } else if (nj > j) {
            ligado[LabirintoCatalog.ligacao(celulas, i, j, 1)] = true;
        } else {
            ligado[LabirintoCatalog.ligacao(celulas, ni, nj, 1)] = true;
        }
    }

    // ------------------------------------------------------------------
    //  Medir a textura — é com isto que o teste prova que os cinco diferem
    // ------------------------------------------------------------------

    /** Quantas saídas tem cada célula: 1 é beco, 2 é corredor, 3+ é esquina. */
    public static int[] graus(int celulas, boolean[] ligado) {
        int[] saida = new int[celulas * celulas];
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                int quantas = 0;
                for (int[] rumo : LabirintoCatalog.RUMOS) {
                    int ni = i + rumo[0];
                    int nj = j + rumo[1];
                    if (LabirintoCatalog.dentro(celulas, ni, nj)
                            && LabirintoCatalog.passa(ligado, celulas, i, j, ni, nj)) {
                        quantas++;
                    }
                }
                saida[LabirintoCatalog.indice(celulas, i, j)] = quantas;
            }
        }
        return saida;
    }

    /** Quantas casas são beco (uma saída só). */
    public static int becos(int celulas, boolean[] ligado) {
        int quantos = 0;
        for (int grau : graus(celulas, ligado)) {
            if (grau == 1) {
                quantos++;
            }
        }
        return quantos;
    }

    /**
     * Quantos becos abrem para o rumo pedido.
     *
     * Existe por causa do {@link #VENTANIA}: nele este número é ZERO para o
     * sul, sempre, e é a assinatura que o teste usa para provar que o
     * traçado é mesmo o que diz ser.
     */
    public static int becosPara(int celulas, boolean[] ligado, int rumo) {
        int quantos = 0;
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                int abertas = 0;
                int unico = -1;
                for (int k = 0; k < LabirintoCatalog.RUMOS.length; k++) {
                    int ni = i + LabirintoCatalog.RUMOS[k][0];
                    int nj = j + LabirintoCatalog.RUMOS[k][1];
                    if (LabirintoCatalog.dentro(celulas, ni, nj)
                            && LabirintoCatalog.passa(ligado, celulas, i, j, ni, nj)) {
                        abertas++;
                        unico = k;
                    }
                }
                if (abertas == 1 && unico == rumo) {
                    quantos++;
                }
            }
        }
        return quantos;
    }

    /** O maior muro reto, em células: a assinatura do {@link #SALOES}. */
    public static int maiorMuroReto(int celulas, boolean[] ligado) {
        int maior = 0;
        for (int i = 0; i + 1 < celulas; i++) {
            int corrida = 0;
            for (int j = 0; j < celulas; j++) {
                corrida = ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)]
                        ? 0 : corrida + 1;
                maior = Math.max(maior, corrida);
            }
        }
        for (int j = 0; j + 1 < celulas; j++) {
            int corrida = 0;
            for (int i = 0; i < celulas; i++) {
                corrida = ligado[LabirintoCatalog.ligacao(celulas, i, j, 1)]
                        ? 0 : corrida + 1;
                maior = Math.max(maior, corrida);
            }
        }
        return maior;
    }

    /** A maior reta andável de leste a oeste: a assinatura do {@link #VENTANIA}. */
    public static int maiorCorridaDeitada(int celulas, boolean[] ligado) {
        int maior = 0;
        for (int j = 0; j < celulas; j++) {
            int corrida = 1;
            for (int i = 0; i + 1 < celulas; i++) {
                if (ligado[LabirintoCatalog.ligacao(celulas, i, j, 0)]) {
                    corrida++;
                    maior = Math.max(maior, corrida);
                } else {
                    corrida = 1;
                }
            }
        }
        return maior;
    }
}
