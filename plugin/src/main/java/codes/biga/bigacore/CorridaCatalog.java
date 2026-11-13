package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A planta da Corrida — e a pista, que é GERADA aqui.
 *
 * <h2>Uma pista, calculada num lugar só</h2>
 *
 * O construtor põe blocos no eixo, o jogo testa portões contra o eixo, o
 * teste anda uma volta pelo eixo e o mímico desenha o eixo. Se cada um
 * calculasse o seu, meio bloco de diferença bastaria para o portão ficar
 * fora da pista — e portão fora da pista é uma volta que nunca fecha.
 *
 * <p>Por isso o eixo e os portões ficam guardados: a conta é determinística,
 * mas é uma reamostragem de 2880 pontos, e refazê-la trinta vezes por segundo
 * por jogador seria desperdício. O cache morre no {@link #aoRecarregar()},
 * que é exatamente quando a planta pode ter mudado.
 */
public final class CorridaCatalog extends CatalogoDeArena {

    private List<double[]> eixo;
    private int[] portoes;
    private int[][] atalhos;
    private List<List<PistaFechada.Portao>> planos;

    /** Cada percurso de mapa pronto já lido, pelo nome do arquivo. */
    private final java.util.Map<String, Percurso> lidos = new java.util.HashMap<>();

    /** Os arquivos que não deram percurso: não se tenta de novo por rodada. */
    private final java.util.Set<String> ruins = new java.util.HashSet<>();

    /** O percurso desta rodada, quando o sorteio já escolheu um. */
    private String escolhido;

    /**
     * O percurso da rodada já resolvido, e se a busca já foi feita.
     *
     * <p>Sem este par, {@code percursoDaRodada()} varreria a lista do yml e
     * perguntaria pela configuração A CADA CHAMADA — e quem chama é topoY(),
     * que é chamado pelo limite de laço da obra. Foi assim que o refazer das
     * vinte arenas travou o servidor por quinze segundos; o defeito não vai
     * voltar por uma porta diferente.
     */
    private boolean jaResolveu;
    private Percurso atual;

    public CorridaCatalog(BigaCore plugin) {
        super(plugin, "corrida.yml", "corrida", "corrida");
    }

    @Override
    protected void aoRecarregar() {
        eixo = null;
        portoes = null;
        atalhos = null;
        planos = null;
        lidos.clear();
        ruins.clear();
        escolhido = null;
        jaResolveu = false;
        atual = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(16, config.getInt("arena.raio", 36));
    }

    public int onda() {
        return Math.max(0, config.getInt("arena.onda", 9));
    }

    public int voltasDaOnda() {
        return Math.max(2, config.getInt("arena.voltas-da-onda", 3));
    }

    /** A largura da faixa. Sempre ímpar: a pista tem eixo, e eixo é um bloco. */
    public int largura() {
        int pedido = Math.max(3, config.getInt("arena.largura", 7));
        return pedido % 2 == 0 ? pedido + 1 : pedido;
    }

    /** Quantos blocos de pista para cada lado do eixo. */
    public int meiaLargura() {
        return largura() / 2;
    }

    public int muroAltura() {
        return Math.max(1, config.getInt("arena.muro-altura", 2));
    }

    public int quantosPortoes() {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            return pronto.quantosPortoes();
        }
        return Math.max(4, config.getInt("arena.portoes", 8));
    }

    public int atalhoAbertura() {
        return Math.max(20, Math.min(120, config.getInt("arena.atalho-abertura", 72)));
    }

    public int atalhoLargura() {
        return Math.max(1, config.getInt("arena.atalho-largura", 2));
    }

    public int torreRecuo() {
        return Math.max(5, config.getInt("arena.torre-recuo", 8));
    }

    public int torreAltura() {
        return Math.max(8, config.getInt("arena.torre-altura", 18));
    }

    /**
     * A fiada abaixo da qual se caiu.
     *
     * <p>Com mapa pronto ela existe só como porão: a estrada desce cento e
     * trinta fiadas, e uma altura única para a montanha inteira ou perdoaria
     * uma queda no alto ou acusaria queda em quem está correndo embaixo. Quem
     * decide de verdade é {@link #quedaDoPortao(int)}, que mede contra a
     * altura do portão em que o jogador está.
     */
    @Override
    public int quedaY() {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            int fundo = pronto.alturaDaLargada();
            for (int k = 0; k < pronto.quantosPortoes(); k++) {
                fundo = Math.min(fundo, pronto.alturaDoPortao(k));
            }
            return fundo - quedaAbaixoDoMapa();
        }
        return topoY() - Math.max(3, config.getInt("arena.queda-abaixo", 6));
    }

    /** Abaixo disto, quem está indo para o portão k caiu da estrada. */
    public int quedaDoPortao(int k) {
        Percurso pronto = percursoDaRodada();
        if (pronto == null) {
            return quedaY();
        }
        return pronto.alturaDoPortao(k) - quedaAbaixoDoMapa();
    }

    /** O raio máximo que a pista alcança, já com muro. */
    public int alcanceDaPista() {
        return raio() + onda() + meiaLargura() + 1;
    }

    /**
     * A caixa que a obra pode apagar — e, com mapa pronto, SÓ o saguão.
     *
     * <h3>Isto é uma trava de segurança, não um ajuste</h3>
     *
     * <p>{@code desfazer} apaga a caixa inteira. Com a pista gerada isso é o
     * certo: tudo que está lá dentro foi o plugin que pôs. Com um mapa
     * pronto, não: lá dentro está a MONTANHA, quatrocentos megabytes de mundo
     * que o dono baixou e que não se refaz com um comando. Por isso, com mapa
     * pronto, a caixa encolhe até o disco do saguão, e a decoração da largada
     * e da chegada — que fica longe, estrada abaixo — é desfeita bloco a
     * bloco por {@link CorridaBuilder#limparDecoracoes}, que sabe exatamente
     * quais casas pôs.
     */
    @Override
    public int alcanceDaObra() {
        if (querMapaPronto()) {
            return saguaoRaio() + 6;
        }
        return alcanceDaPista() + torreRecuo() + 4;
    }

    @Override
    public int fundoDaObra() {
        if (querMapaPronto()) {
            return saguaoY() - 10;
        }
        return topoY() - 8;
    }

    @Override
    public int tetoDaObra() {
        if (querMapaPronto()) {
            return saguaoY() + saguaoParapeito() + 4;
        }
        return Math.max(saguaoY() + saguaoParapeito() + 4, topoY() + torreAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    /**
     * Quantas voltas tem a corrida — e com mapa pronto é sempre UMA.
     *
     * <p>Uma descida de montanha não dá volta: o último portão é a chegada, e
     * mandar o jogador fazer a segunda seria teleportá-lo de volta ao alto, o
     * que não é corrida, é fila de teleférico.
     */
    public int voltas() {
        if (querMapaPronto()) {
            return 1;
        }
        return Math.max(1, config.getInt("regras.voltas", 2));
    }

    public int velocidade() {
        return Math.max(0, Math.min(3, config.getInt("regras.velocidade", 2)));
    }

    /** Os materiais que o construtor põe no chão da pista — e só eles. */
    public Set<Material> materiaisDaPista() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("pista", Material.LIGHT_GRAY_CONCRETE));
        saida.add(cor("pista-eixo", Material.WHITE_CONCRETE));
        saida.add(cor("guia-clara", Material.WHITE_CONCRETE));
        saida.add(cor("guia-escura", Material.RED_CONCRETE));
        saida.add(cor("largada-clara", Material.WHITE_CONCRETE));
        saida.add(cor("largada-escura", Material.BLACK_CONCRETE));
        saida.add(cor("ponte", Material.POLISHED_BLACKSTONE));
        saida.add(cor("ponte-borda", Material.GOLD_BLOCK));
        return saida;
    }

    // ------------------------------------------------------------------
    //  A pista
    // ------------------------------------------------------------------

    public List<double[]> eixo() {
        if (eixo == null) {
            eixo = PistaFechada.eixo(raio(), onda(), voltasDaOnda());
        }
        return eixo;
    }

    public int quantosBlocosDePista() {
        return eixo().size();
    }

    public int[] portoes() {
        if (portoes == null) {
            portoes = PistaFechada.portoes(quantosPortoes(), eixo().size());
        }
        return portoes;
    }

    public int[][] atalhos() {
        if (atalhos == null) {
            atalhos = PistaFechada.atalhos(eixo(), voltasDaOnda(), atalhoAbertura());
        }
        return atalhos;
    }

    /**
     * Os planos de cada portão, com meia-largura folgada de propósito.
     *
     * Na pista, meia-largura é a faixa inteira mais um e meio: o portão tem de
     * pegar quem passa raspando no muro, porque um portão estreito deixa o
     * jogador rente à parede atravessar sem contar. Na ponte, a largura dela
     * mais um.
     */
    public List<List<PistaFechada.Portao>> planos() {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            return pronto.planos();
        }
        if (planos == null) {
            planos = PistaFechada.planos(eixo(), portoes(), atalhos(),
                    meiaLargura() + 1.5, atalhoLargura() / 2.0 + 1.0);
        }
        return planos;
    }


    // ------------------------------------------------------------------
    //  O mapa pronto: percursos lidos de arquivo
    // ------------------------------------------------------------------

    /**
     * Um percurso de mapa pronto, já medido.
     *
     * <p>Guardado inteiro porque a conta não é de graça: um arquivo de mil e
     * novecentas linhas vira um eixo reamostrado de oitocentos pontos, e
     * refazer isso trinta vezes por segundo por jogador — que é o que o
     * {@code aCadaTique} faria — seria desperdício puro. Morre no
     * {@code aoRecarregar()}, que é quando a pasta pode ter mudado.
     */
    public record Percurso(String nome, String arquivo, List<double[]> eixo,
                           int[] portoes, int[] alturas,
                           List<List<PistaFechada.Portao>> planos) {

        public double[] ponto(int i) {
            return eixo.get(Math.max(0, Math.min(eixo.size() - 1, i)));
        }

        public int indiceDoPortao(int k) {
            return portoes[Math.max(0, Math.min(portoes.length - 1, k))];
        }

        public int alturaDoPortao(int k) {
            return alturas[Math.max(0, Math.min(alturas.length - 1, k))];
        }

        public int quantosPortoes() {
            return portoes.length;
        }

        public double comprimento() {
            return PistaAberta.comprimento(eixo);
        }

        /** A altura da largada: a grade nasce nela. */
        public int alturaDaLargada() {
            return (int) Math.round(ponto(0)[1]);
        }
    }

    public boolean querMapaPronto() {
        return config.getBoolean("mapa-pronto.ativo", false);
    }

    public String pastaDosPercursos() {
        return config.getString("mapa-pronto.pasta", "mapas/corrida");
    }

    /** Se cada rodada sorteia um percurso diferente. */
    public boolean rodizio() {
        return config.getBoolean("mapa-pronto.rodizio", true);
    }

    public int portoesDoMapa() {
        return Math.max(2, Math.min(60, config.getInt("mapa-pronto.portoes", 10)));
    }

    /**
     * A meia-largura do portão numa estrada feita à mão.
     *
     * <p>Folgada de propósito, e mais do que na pista gerada: o eixo veio de
     * um caminho traçado bloco a bloco pelo gelo, e nas curvas de grampo ele
     * corta por dentro. Portão estreito num eixo que corta por dentro é
     * portão que quem passa por fora não cruza — e portão não cruzado trava a
     * corrida sem dar erro.
     */
    public double meiaLarguraDoMapa() {
        return Math.max(3, Math.min(48, config.getInt("mapa-pronto.meia-largura", 14)));
    }

    /** A folga de altura em que um portão da montanha ainda conta. */
    public int faixaDeAltura() {
        return Math.max(4, Math.min(64, config.getInt("mapa-pronto.faixa-de-altura", 12)));
    }

    /** Quantas fiadas de desacordo com os vizinhos ainda são estrada. */
    public int picoDoMapa() {
        return Math.max(2, Math.min(64, config.getInt("mapa-pronto.pico", 8)));
    }

    /** Quantas fiadas abaixo do portão já é queda. */
    public int quedaAbaixoDoMapa() {
        return Math.max(4, Math.min(80, config.getInt("mapa-pronto.queda-abaixo", 14)));
    }

    public double passoDoEixo() {
        return Math.max(0.5, Math.min(8.0, config.getDouble("mapa-pronto.passo", 2.0)));
    }

    public int janelaDaTangente() {
        return Math.max(1, Math.min(40, config.getInt("mapa-pronto.janela-da-tangente", 6)));
    }

    public Material pisoDaPista() {
        return cor("mapa-piso", Material.PACKED_ICE);
    }

    public Material linhaDaPista() {
        return cor("mapa-linha", Material.BLUE_ICE);
    }

    /** Os arquivos do rodízio, na ordem do yml e sem repetido. */
    public List<String> arquivosDoRodizio() {
        java.util.LinkedHashSet<String> limpos = new java.util.LinkedHashSet<>();
        for (java.util.Map<?, ?> entrada
                : config.getMapList("mapa-pronto.percursos")) {
            Object arquivo = entrada.get("arquivo");
            if (arquivo != null && !arquivo.toString().isBlank()) {
                limpos.add(arquivo.toString().trim());
            }
        }
        return List.copyOf(limpos);
    }

    /** O nome bonito de um arquivo, para anunciar na rodada. */
    public String nomeDoPercurso(String arquivo) {
        for (java.util.Map<?, ?> entrada
                : config.getMapList("mapa-pronto.percursos")) {
            Object nome = entrada.get("nome");
            if (arquivo.equals(String.valueOf(entrada.get("arquivo")))
                    && nome != null) {
                return nome.toString();
            }
        }
        return arquivo;
    }

    /** O arquivo do percurso desta rodada: o sorteado, ou o primeiro do yml. */
    public String arquivoDaRodada() {
        if (escolhido != null) {
            return escolhido;
        }
        List<String> todos = arquivosDoRodizio();
        return todos.isEmpty() ? null : todos.get(0);
    }

    /** O percurso desta rodada, ou nulo quando não há mapa pronto. */
    public Percurso percursoDaRodada() {
        if (jaResolveu) {
            return atual;
        }
        jaResolveu = true;
        if (!querMapaPronto()) {
            atual = null;
            return null;
        }
        atual = abrir(arquivoDaRodada());
        if (atual == null && escolhido != null) {
            escolhido = null;
            atual = abrir(arquivoDaRodada());
        }
        return atual;
    }

    /**
     * Sorteia o percurso da rodada.
     *
     * <p>Nunca o mesmo da rodada passada, enquanto houver outro: sorteio
     * ingênuo repete, e com cinco percursos uma rodada em cada cinco não
     * trocaria nada — o que o jogador lê como "o rodízio não funciona".
     */
    public Percurso sortearPercurso(java.util.Random sorteio) {
        if (!querMapaPronto()) {
            return null;
        }
        if (!rodizio()) {
            return percursoDaRodada();
        }
        List<String> candidatos = new ArrayList<>(arquivosDoRodizio());
        candidatos.removeAll(ruins);
        String deuUltima = arquivoDaRodada();
        if (candidatos.size() > 1) {
            candidatos.remove(deuUltima);
        }
        if (candidatos.isEmpty()) {
            return percursoDaRodada();
        }
        String escolha = candidatos.get(sorteio.nextInt(candidatos.size()));
        Percurso novo = abrir(escolha);
        if (novo == null) {
            return percursoDaRodada();
        }
        escolhido = escolha;
        atual = novo;
        jaResolveu = true;
        return novo;
    }

    /**
     * Um percurso pelo nome do arquivo, para quem precisa de TODOS eles.
     *
     * <p>Quem precisa é o desfazer da decoração: a rodada passada pode ter
     * decorado outro percurso do rodízio, e limpar só o da rodada deixaria o
     * pórtico da anterior plantado na montanha para sempre.
     */
    public Percurso percursoDoArquivo(String arquivo) {
        return abrir(arquivo);
    }

    /** Lê um percurso do disco, uma vez só, e guarda o que deu certo. */
    private Percurso abrir(String arquivo) {
        if (arquivo == null || ruins.contains(arquivo)) {
            return null;
        }
        if (lidos.containsKey(arquivo)) {
            return lidos.get(arquivo);
        }
        java.io.File onde = new java.io.File(
                new java.io.File(plugin.getDataFolder(), pastaDosPercursos()),
                arquivo);
        if (!onde.isFile()) {
            ruins.add(arquivo);
            plugin.getLogger().warning("[corrida] o percurso '" + arquivo
                    + "' não está em " + pastaDosPercursos() + ": a pista "
                    + "gerada vai no lugar.");
            return null;
        }
        try {
            List<int[]> bruto = PistaAberta.tirarPicos(PistaAberta.ler(
                    java.nio.file.Files.readAllLines(onde.toPath(),
                            java.nio.charset.StandardCharsets.UTF_8)),
                    picoDoMapa());
            List<double[]> linha = PistaAberta.reamostrar(bruto, passoDoEixo());
            if (!PistaAberta.serve(linha)) {
                ruins.add(arquivo);
                plugin.getLogger().warning("[corrida] o percurso '" + arquivo
                        + "' tem só " + linha.size() + " ponto(s): não dá "
                        + "corrida.");
                return null;
            }
            int[] ps = PistaAberta.portoes(portoesDoMapa(), linha.size());
            Percurso feito = new Percurso(nomeDoPercurso(arquivo), arquivo,
                    linha, ps, PistaAberta.alturas(linha, ps),
                    PistaAberta.planos(linha, ps, meiaLarguraDoMapa(),
                            janelaDaTangente()));
            lidos.put(arquivo, feito);
            plugin.getLogger().info("[corrida] percurso '" + feito.nome()
                    + "': " + Math.round(feito.comprimento()) + " blocos, "
                    + linha.size() + " pontos, " + ps.length + " portões, y "
                    + feito.alturaDaLargada() + " → "
                    + feito.alturaDoPortao(ps.length - 1) + ".");
            return feito;
        } catch (java.io.IOException naoAbriu) {
            ruins.add(arquivo);
            plugin.getLogger().warning("[corrida] o percurso '" + arquivo
                    + "' não abriu (" + naoAbriu.getMessage() + ").");
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  O que muda quando há mapa pronto
    // ------------------------------------------------------------------

    /**
     * A distância, no plano, de um ponto até o portão k.
     *
     * <p>Existe para o jogo não precisar saber qual geometria está em uso: o
     * eixo gerado é {x, z} e o do mapa pronto é {x, y, z}, e deixar a
     * classificação escolher o índice certo seria um "se" novo a cada vez que
     * alguém mexesse num dos dois.
     */
    public double distanciaAoPortao(int k, double x, double z) {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            double[] p = pronto.ponto(pronto.indiceDoPortao(k));
            return Math.hypot(p[0] - x, p[2] - z);
        }
        double[] p = eixo().get(indiceDoPortao(k));
        return Math.hypot(p[0] - x, p[1] - z);
    }

    /** O índice do eixo onde fica o portão número k. */
    public int indiceDoPortao(int k) {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            return pronto.indiceDoPortao(k);
        }
        int[] ps = portoes();
        return ps[Math.floorMod(k, ps.length)];
    }

    /** A fiada do portão k: é contra ela que o jogo mede altura e queda. */
    public int alturaDoPortao(int k) {
        Percurso pronto = percursoDaRodada();
        if (pronto != null) {
            return pronto.alturaDoPortao(k);
        }
        return topoY();
    }
}
