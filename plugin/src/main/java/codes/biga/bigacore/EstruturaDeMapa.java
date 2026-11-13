package codes.biga.bigacore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Um mapa pronto, lido dos arquivos de estrutura em que ele foi salvo.
 *
 * <h2>Por que ler mapa de fora, se as vinte arenas são geradas</h2>
 *
 * Porque gerar e desenhar são coisas diferentes. O gerador acerta a
 * GEOMETRIA — o anel tem o raio que o relógio pede, o vão cabe nos blocos que
 * o baú garante, o baú não nasce na beira que vai cair. O que ele não faz é
 * o que um mapa feito à mão tem de sobra: a casinha torta no canto da ilha, a
 * ponte de corda, o moinho que não serve para nada e é a primeira coisa que
 * se olha. Com esta classe os dois convivem: o mapa dá o desenho, o nosso
 * código continua dando as regras.
 *
 * <h2>O formato</h2>
 *
 * É o do BLOCO DE ESTRUTURA do próprio jogo — o mesmo arquivo que o
 * {@code /structure save} escreve e que vem dentro de mapas baixados. Um
 * compound com três campos que importam:
 *
 * <ul>
 *   <li>{@code size}, as três medidas;</li>
 *   <li>{@code palette}, a lista de estados distintos, cada um com o nome do
 *       bloco e as propriedades dele;</li>
 *   <li>{@code blocks}, uma entrada por bloco, com a posição e o ÍNDICE na
 *       paleta — é o que faz um mapa de doze mil blocos caber em poucos
 *       quilobytes.</li>
 * </ul>
 *
 * <h2>Os marcadores são a parte que vale mais</h2>
 *
 * Um mapa de Sky Wars feito para datapack não guarda só os blocos: ele guarda
 * MARCADORES — entidades invisíveis — dizendo onde fica a jaula de cada
 * jogador, onde vai cada baú da ilha e onde vão os do meio. Sem eles, usar um
 * mapa de outra pessoa obrigaria a adivinhar no olho onde cada coisa fica;
 * com eles, a largada e os baús saem do próprio mapa e o nosso código só
 * precisa dizer o que vai DENTRO do baú.
 *
 * <h2>Peça por peça</h2>
 *
 * O bloco de estrutura do jogo não salva mais de 48 blocos de lado, então
 * mapa grande vem partido numa grade — nove pedaços de 48, em três por três,
 * encaixando de 47 em 47 (um de sobreposição em cada emenda). {@link
 * #deslocamentoDaPeca} é essa grade, e é o único lugar onde ela existe.
 *
 * <h2>Sem Bukkit aqui dentro</h2>
 *
 * Esta classe lê e mede; quem põe bloco no mundo é o construtor. É o que
 * permite testar a leitura inteira — a grade, a paleta, o estado com
 * propriedades, os marcadores, o centro — sem servidor nenhum em pé.
 */
public final class EstruturaDeMapa {

    /** Um bloco do mapa, já na coordenada da peça somada ao deslocamento. */
    public record Bloco(int x, int y, int z, String estado) {
    }

    /** Um marcador do mapa: onde, e com que etiquetas. */
    public record Marco(double x, double y, double z, Set<String> etiquetas) {

        public boolean tem(String etiqueta) {
            return etiquetas.contains(etiqueta);
        }
    }

    private final List<Bloco> blocos;
    private final List<Marco> marcos;

    public EstruturaDeMapa(List<Bloco> blocos, List<Marco> marcos) {
        this.blocos = List.copyOf(blocos);
        this.marcos = List.copyOf(marcos);
    }

    public List<Bloco> blocos() {
        return blocos;
    }

    public List<Marco> marcos() {
        return marcos;
    }

    public boolean vazio() {
        return blocos.isEmpty();
    }

    // ------------------------------------------------------------------
    //  A leitura
    // ------------------------------------------------------------------

    /**
     * Lê um mapa inteiro de {@code pasta}, juntando as peças da grade.
     *
     * <p>Os arquivos se chamam {@code <nome>-1.nbt} até
     * {@code <nome>-(grade*grade).nbt}, na ordem de leitura de um texto:
     * primeiro a fileira de trás, da esquerda para a direita.
     *
     * <p>Uma peça que falta não derruba a leitura — um mapa com um pedaço a
     * menos ainda é um mapa, e recusar o conjunto por causa de um arquivo
     * perdido deixaria o servidor sem arena nenhuma no lugar de deixá-lo com
     * uma arena com um buraco, que pelo menos se vê e se conserta.
     */
    public static EstruturaDeMapa ler(File pasta, String nome, int grade,
                                      int passo) throws IOException {
        List<Bloco> blocos = new ArrayList<>();
        List<Marco> marcos = new ArrayList<>();
        int pecas = Math.max(1, grade) * Math.max(1, grade);
        int lidas = 0;
        IOException ultimoErro = null;
        for (int k = 1; k <= pecas; k++) {
            File arquivo = new File(pasta, nome + "-" + k + ".nbt");
            if (!arquivo.isFile()) {
                if (pecas == 1) {
                    arquivo = new File(pasta, nome + ".nbt");
                }
                if (!arquivo.isFile()) {
                    continue;
                }
            }
            int[] desloca = deslocamentoDaPeca(k, grade, passo);
            try {
                lerPeca(Nbt.ler(arquivo), desloca[0], desloca[1], desloca[2],
                        blocos, marcos);
                lidas++;
            } catch (IOException falhou) {
                ultimoErro = new IOException("a peça " + arquivo.getName()
                        + " não abriu: " + falhou.getMessage(), falhou);
            }
        }
        if (lidas == 0) {
            if (ultimoErro != null) {
                throw ultimoErro;
            }
            throw new IOException("nenhuma peça de '" + nome + "' foi achada "
                    + "em " + pasta.getPath() + " (esperava " + nome
                    + "-1.nbt" + (pecas > 1 ? " até " + nome + "-" + pecas
                    + ".nbt" : "") + ")");
        }
        return new EstruturaDeMapa(blocos, marcos);
    }

    /**
     * O canto de uma peça da grade, em blocos, relativo ao centro.
     *
     * <p>A peça do meio fica no zero, e é por isso que a conta subtrai
     * {@code (grade - 1) / 2}: uma grade de três tem a peça 5 no centro, uma
     * de um tem a peça 1, e nos dois casos o mapa nasce em volta da origem em
     * vez de pendurado num canto dela.
     */
    public static int[] deslocamentoDaPeca(int indice, int grade, int passo) {
        int lado = Math.max(1, grade);
        int k = Math.max(0, indice - 1);
        int coluna = k % lado;
        int fileira = k / lado;
        int meio = (lado - 1) / 2;
        return new int[]{(coluna - meio) * passo, 0, (fileira - meio) * passo};
    }

    /** Despeja uma peça já lida nas listas do mapa. */
    static void lerPeca(Map<String, Object> raiz, int dx, int dy, int dz,
                        List<Bloco> blocos, List<Marco> marcos) {
        List<String> paleta = paleta(raiz);
        for (Object cru : Nbt.lista(raiz.get("blocks"))) {
            Map<String, Object> entrada = Nbt.composto(cru);
            int[] onde = Nbt.trio(entrada.get("pos"));
            if (onde == null) {
                continue;
            }
            int indice = Nbt.inteiro(entrada.get("state"), -1);
            if (indice < 0 || indice >= paleta.size()) {
                continue;
            }
            String estado = paleta.get(indice);
            if (estado == null || estado.isEmpty() || ehAr(estado)) {
                continue;
            }
            blocos.add(new Bloco(onde[0] + dx, onde[1] + dy, onde[2] + dz,
                    estado));
        }
        for (Object cru : Nbt.lista(raiz.get("entities"))) {
            Map<String, Object> entrada = Nbt.composto(cru);
            double[] onde = Nbt.trioDobro(entrada.get("pos"));
            if (onde == null) {
                continue;
            }
            Set<String> etiquetas = new LinkedHashSet<>();
            for (Object etiqueta : Nbt.lista(
                    Nbt.composto(entrada.get("nbt")).get("Tags"))) {
                String texto = Nbt.texto(etiqueta, null);
                if (texto != null && !texto.isBlank()) {
                    etiquetas.add(texto);
                }
            }
            if (etiquetas.isEmpty()) {
                continue;
            }
            marcos.add(new Marco(onde[0] + dx, onde[1] + dy, onde[2] + dz,
                    Set.copyOf(etiquetas)));
        }
    }

    /**
     * O ar não é guardado, e essa é a diferença entre caber e não caber.
     *
     * <p>Uma estrutura de 48 de lado tem 110 592 casas, e uma arena de Sky
     * Wars usa doze mil delas: as outras noventa e oito mil são ar, escritas
     * uma a uma no arquivo. Guardá-las custaria cem vezes a memória para
     * depois mandar o servidor pôr ar onde já há ar — num mundo vazio, que é
     * onde estas arenas moram, pôr ar é não fazer nada.
     */
    static boolean ehAr(String estado) {
        return estado.equals("minecraft:air")
                || estado.equals("minecraft:cave_air")
                || estado.equals("minecraft:void_air");
    }

    /**
     * A paleta em texto, pronta para virar bloco.
     *
     * <p>Um mapa salvo com variantes guarda {@code palettes} no plural, uma
     * por variante; a primeira é a que o jogo usa quando ninguém escolhe, e é
     * a que vale aqui — escolher variante é decisão de quem fez o mapa, não
     * de quem o empresta.
     */
    static List<String> paleta(Map<String, Object> raiz) {
        List<Object> cru = Nbt.lista(raiz.get("palette"));
        if (cru.isEmpty()) {
            List<Object> varias = Nbt.lista(raiz.get("palettes"));
            if (!varias.isEmpty()) {
                cru = Nbt.lista(varias.get(0));
            }
        }
        List<String> saida = new ArrayList<>(cru.size());
        for (Object entrada : cru) {
            saida.add(estadoDoPaleta(Nbt.composto(entrada)));
        }
        return saida;
    }

    /**
     * Uma entrada da paleta virada em texto de estado de bloco.
     *
     * <p>Sai no formato que o próprio jogo lê — {@code minecraft:oak_stairs
     * [facing=north,half=bottom]} —, com as propriedades em ordem alfabética.
     * A ordem não muda o bloco; ela existe para o texto ser o MESMO toda vez,
     * e um texto estável é o que permite guardar o estado já traduzido num
     * mapa e não traduzir o mesmo carvalho doze mil vezes.
     */
    static String estadoDoPaleta(Map<String, Object> entrada) {
        String nome = Nbt.texto(entrada.get("Name"), "").trim()
                .toLowerCase(Locale.ROOT);
        if (nome.isEmpty()) {
            return "";
        }
        if (!nome.contains(":")) {
            nome = "minecraft:" + nome;
        }
        Map<String, Object> cruas = Nbt.composto(entrada.get("Properties"));
        if (cruas.isEmpty()) {
            return nome;
        }
        Map<String, String> emOrdem = new TreeMap<>();
        for (Map.Entry<String, Object> par : cruas.entrySet()) {
            String valor = Nbt.texto(par.getValue(), null);
            if (valor != null) {
                emOrdem.put(par.getKey().toLowerCase(Locale.ROOT),
                        valor.toLowerCase(Locale.ROOT));
            }
        }
        if (emOrdem.isEmpty()) {
            return nome;
        }
        StringBuilder texto = new StringBuilder(nome).append('[');
        boolean primeiro = true;
        for (Map.Entry<String, String> par : emOrdem.entrySet()) {
            if (!primeiro) {
                texto.append(',');
            }
            primeiro = false;
            texto.append(par.getKey()).append('=').append(par.getValue());
        }
        return texto.append(']').toString();
    }

    // ------------------------------------------------------------------
    //  As medidas
    // ------------------------------------------------------------------

    /** Os marcadores com uma etiqueta, na ordem em que foram lidos. */
    public List<Marco> comEtiqueta(String etiqueta) {
        List<Marco> saida = new ArrayList<>();
        for (Marco marco : marcos) {
            if (marco.tem(etiqueta)) {
                saida.add(marco);
            }
        }
        return saida;
    }

    /**
     * O centro do mapa medido pelos marcadores de uma etiqueta.
     *
     * <p>Medir pelas JAULAS, e não pela caixa dos blocos, não é detalhe: a
     * caixa inclui a torre decorativa que alguém pôs num canto, e um mapa com
     * enfeite só de um lado nasceria torto na arena. As jaulas, essas, são
     * sempre um anel em volta do meio — o centro delas é o centro do jogo.
     *
     * @return {x, y, z} arredondado, ou nulo se não houver marcador
     */
    public int[] centroPelosMarcos(String etiqueta) {
        List<Marco> quais = comEtiqueta(etiqueta);
        if (quais.isEmpty()) {
            return null;
        }
        // Soma o BLOCO de cada marcador, e não a posição dele: o marcador
        // mora no meio do bloco, em x,5, e somar os meios desloca o centro
        // meio bloco para o canto. Meio bloco parece nada — e é o bastante
        // para a ilha do norte cair um passo a oeste do norte e trocar de
        // lugar com a última da volta quando a lista for ordenada.
        long x = 0;
        long y = 0;
        long z = 0;
        for (Marco marco : quais) {
            x += (long) Math.floor(marco.x());
            y += (long) Math.floor(marco.y());
            z += (long) Math.floor(marco.z());
        }
        int quantos = quais.size();
        return new int[]{(int) Math.round(x / (double) quantos),
                (int) Math.round(y / (double) quantos),
                (int) Math.round(z / (double) quantos)};
    }

    /** A caixa que envolve os blocos: {xMin,yMin,zMin,xMax,yMax,zMax}. */
    public int[] caixa() {
        if (blocos.isEmpty()) {
            return null;
        }
        int xMin = Integer.MAX_VALUE;
        int yMin = Integer.MAX_VALUE;
        int zMin = Integer.MAX_VALUE;
        int xMax = Integer.MIN_VALUE;
        int yMax = Integer.MIN_VALUE;
        int zMax = Integer.MIN_VALUE;
        for (Bloco bloco : blocos) {
            xMin = Math.min(xMin, bloco.x());
            yMin = Math.min(yMin, bloco.y());
            zMin = Math.min(zMin, bloco.z());
            xMax = Math.max(xMax, bloco.x());
            yMax = Math.max(yMax, bloco.y());
            zMax = Math.max(zMax, bloco.z());
        }
        return new int[]{xMin, yMin, zMin, xMax, yMax, zMax};
    }

    /**
     * O deslocamento que leva o mapa para a arena.
     *
     * <p>Em x e z, o centro medido vai para o centro da arena, que é a origem
     * do mundo do jogo. Em y, a altura das JAULAS vai para a fiada pedida: é
     * na jaula que o jogador nasce, e é ela, não o chão mais baixo do mapa,
     * que tem de cair onde o resto da arena espera encontrar gente.
     *
     * @param centro   o {x, y, z} de {@link #centroPelosMarcos}
     * @param yDaJaula em que fiada a jaula tem de ficar
     */
    public static int[] deslocamentoParaArena(int[] centro, int yDaJaula) {
        if (centro == null) {
            return new int[]{0, 0, 0};
        }
        return new int[]{-centro[0], yDaJaula - centro[1], -centro[2]};
    }
}
