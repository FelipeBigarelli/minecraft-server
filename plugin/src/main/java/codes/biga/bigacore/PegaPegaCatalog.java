package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A planta do Pega-Pega: o pátio de dois andares.
 *
 * <h2>Por que o pátio tem dois andares</h2>
 *
 * Num disco plano, perseguição é geometria: o perseguido corre em círculo
 * grande, o perseguidor corre em círculo pequeno pelo meio, e vence quem fez
 * a conta certa. Com dois andares ligados por quatro escadas e por quedas, a
 * perseguição passa a ter DECISÕES — subir ou não, pular o fosso ou dar a
 * volta, cair pelo buraco e perder dois segundos ou continuar na galeria com
 * alguém colado. É a mesma diferença entre um corredor e um prédio.
 *
 * <h2>De cima para baixo é fácil, de baixo para cima custa</h2>
 *
 * De qualquer lugar da galeria dá para cair no piso; do piso só se sobe por
 * uma das quatro escadas. A assimetria é de propósito: quem está com a bomba
 * ganha a decisão de empurrar o outro para o andar de baixo, e quem está sem
 * ela paga pela fuga mais fácil com o caminho de volta mais longo.
 *
 * <h2>O mapa é uma função pura</h2>
 *
 * {@link #celulas()} devolve o pátio inteiro como mapa de célula para tipo,
 * sem tocar em mundo nenhum. É o que deixa o teste ANDAR pelo pátio sem
 * servidor: conferir que a galeria é um anel fechado, que nenhuma escada tem
 * degrau de dois, que o fosso é saltável e que nada flutua sobre o nada.
 */
public final class PegaPegaCatalog extends CatalogoDeArena {

    /** Os tipos de célula do pátio. */
    public static final int PISO = 0;
    public static final int PISO_MARCA = 1;
    public static final int MURO = 2;
    public static final int MURO_FAIXA = 3;
    public static final int ANEL = 4;
    public static final int ANEL_BEIRA = 5;
    public static final int PLATO = 6;
    public static final int PLATO_BEIRA = 7;
    public static final int ESCADA = 8;
    public static final int COLUNA = 9;
    public static final int COLUNA_TOPO = 10;
    public static final int NUCLEO = 11;

    private Map<Long, Integer> celulas;
    private List<int[]> largadas;

    public PegaPegaCatalog(BigaCore plugin) {
        super(plugin, "pega-pega.yml", "pega_pega", "pega");
    }

    @Override
    protected void aoRecarregar() {
        celulas = null;
        largadas = null;
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int raio() {
        return Math.max(12, config.getInt("arena.raio", 20));
    }

    public int muroEspessura() {
        return Math.max(1, config.getInt("arena.muro-espessura", 2));
    }

    public int muroAltura() {
        return Math.max(anelAltura() + 3, config.getInt("arena.muro-altura", 7));
    }

    public int anelDentro() {
        return Math.max(4, config.getInt("arena.anel-dentro", 12));
    }

    public int anelFora() {
        return Math.max(anelDentro() + 2, config.getInt("arena.anel-fora", 16));
    }

    public int anelAltura() {
        return Math.max(3, config.getInt("arena.anel-altura", 4));
    }

    public int platoRaio() {
        return Math.max(3, config.getInt("arena.plato-raio", 9));
    }

    public int escadas() {
        return Math.max(1, config.getInt("arena.escadas", 4));
    }

    public int escadaLargura() {
        return Math.max(1, config.getInt("arena.escada-largura", 3));
    }

    public int buracos() {
        return Math.max(0, config.getInt("arena.buracos", 4));
    }

    public int buracoTamanho() {
        return Math.max(1, config.getInt("arena.buraco-tamanho", 2));
    }

    public int colunas() {
        return Math.max(0, config.getInt("arena.colunas", 4));
    }

    public int colunaRaio() {
        return Math.max(anelFora() + 1, config.getInt("arena.coluna-raio", 18));
    }

    public int colunaAltura() {
        return Math.max(2, config.getInt("arena.coluna-altura", 3));
    }

    public int largadaRaio() {
        return Math.max(anelFora() + 2, config.getInt("arena.largada-raio", 19));
    }

    public int marcoRecuo() {
        return Math.max(4, config.getInt("arena.marco-recuo", 8));
    }

    public int marcoAltura() {
        return Math.max(10, config.getInt("arena.marco-altura", 20));
    }

    public int marcoDistancia() {
        return raio() + muroEspessura() + marcoRecuo();
    }

    /** A fiada da galeria e do plató. */
    public int galeriaY() {
        return topoY() + anelAltura();
    }

    @Override
    public int quedaY() {
        return topoY() - Math.max(4, config.getInt("regras.queda-abaixo", 6));
    }

    @Override
    public int alcanceDaObra() {
        return marcoDistancia() + 6;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 10;
    }

    @Override
    public int tetoDaObra() {
        return Math.max(saguaoY() + saguaoParapeito() + 4,
                topoY() + marcoAltura() + 6);
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    public int pavioSegundos() {
        return Math.max(5, config.getInt("regras.pavio-segundos", 25));
    }

    public int pavioDesconto() {
        return Math.max(0, config.getInt("regras.pavio-desconto", 3));
    }

    public int pavioMinimo() {
        return Math.max(5, Math.min(pavioSegundos(),
                config.getInt("regras.pavio-minimo", 10)));
    }

    public int jogadoresPorBomba() {
        return Math.max(2, config.getInt("regras.jogadores-por-bomba", 4));
    }

    public double alcance() {
        return Math.max(0.8, Math.min(4.0, config.getDouble("regras.alcance", 1.7)));
    }

    public int carenciaTiques() {
        return Math.max(0, config.getInt("regras.carencia-tiques", 40));
    }

    public int velocidadeExtra() {
        return Math.max(0, Math.min(4, config.getInt("regras.velocidade-extra", 1)));
    }

    /** O pavio da rodada número {@code rodada}, contando de 1. */
    public int pavioDaRodada(int rodada) {
        int pavio = pavioSegundos() - pavioDesconto() * Math.max(0, rodada - 1);
        return Math.max(pavioMinimo(), pavio);
    }

    /**
     * Quantas bombas numa rodada com tantos vivos.
     *
     * O teto de {@code vivos - 1} é o que impede o fim em que TODO MUNDO
     * explode junto e a partida termina sem vencedor: com dois vivos e a
     * conta arredondando para dois, as duas bombas acabariam ao mesmo tempo.
     */
    public static int quantasBombas(int vivos, int porBomba) {
        if (vivos <= 1) {
            return 0;
        }
        int conta = (int) Math.round(vivos / (double) Math.max(2, porBomba));
        return Math.max(1, Math.min(vivos - 1, conta));
    }

    /** O material de cada tipo de célula. */
    public Material material(int tipo) {
        return switch (tipo) {
            case PISO -> cor("piso", Material.SMOOTH_SANDSTONE);
            case PISO_MARCA -> cor("piso-marca", Material.YELLOW_TERRACOTTA);
            case MURO -> cor("muro", Material.CUT_SANDSTONE);
            case MURO_FAIXA -> cor("muro-faixa", Material.YELLOW_CONCRETE);
            case ANEL -> cor("anel", Material.CUT_SANDSTONE);
            case ANEL_BEIRA -> cor("anel-beira", Material.YELLOW_CONCRETE);
            case PLATO -> cor("plato", Material.SMOOTH_QUARTZ);
            case PLATO_BEIRA -> cor("plato-beira", Material.YELLOW_CONCRETE);
            case ESCADA -> cor("escada", Material.SANDSTONE);
            case COLUNA -> cor("coluna", Material.CUT_SANDSTONE);
            case COLUNA_TOPO -> cor("coluna-topo", Material.YELLOW_CONCRETE);
            default -> cor("nucleo", Material.GOLD_BLOCK);
        };
    }

    // ------------------------------------------------------------------
    //  O pátio
    // ------------------------------------------------------------------

    public Map<Long, Integer> celulas() {
        if (celulas == null) {
            celulas = patio(raio(), muroEspessura(), muroAltura(), anelDentro(),
                    anelFora(), anelAltura(), platoRaio(), escadas(),
                    escadaLargura(), buracos(), buracoTamanho(), colunas(),
                    colunaRaio(), colunaAltura());
        }
        return celulas;
    }

    /**
     * O pátio inteiro, em coordenadas relativas ao centro e à fiada zero.
     *
     * A fiada zero é o piso de baixo; a galeria sai em {@code anelAltura}. O
     * mapa é devolvido com as chaves de {@link ArenaComum#chave} e o
     * construtor só soma a fiada de verdade — do jeito que o teste precisa
     * para andar pelo pátio sem servidor.
     */
    public static Map<Long, Integer> patio(int raio, int muroEspessura,
                                           int muroAltura, int anelDentro,
                                           int anelFora, int anelAltura,
                                           int platoRaio, int escadas,
                                           int escadaLargura, int buracos,
                                           int buracoTamanho, int colunas,
                                           int colunaRaio, int colunaAltura) {
        Map<Long, Integer> mapa = new HashMap<>();
        int fora = raio + muroEspessura;

        // O piso, que vai até debaixo do muro para o muro não flutuar.
        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora)) {
                    continue;
                }
                mapa.put(ArenaComum.chave(x, 0, z), ehMarca(x, z) ? PISO_MARCA : PISO);
            }
        }

        // O muro.
        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!ArenaComum.noDisco(x, z, fora) || ArenaComum.noDisco(x, z, raio)) {
                    continue;
                }
                for (int alto = 1; alto <= muroAltura; alto++) {
                    mapa.put(ArenaComum.chave(x, alto, z),
                            alto == muroAltura ? MURO_FAIXA : MURO);
                }
            }
        }

        // A galeria: um anel na altura da galeria, com buracos de queda.
        Set<Long> vazios = buracosDaGaleria(buracos, buracoTamanho,
                (anelDentro + anelFora) / 2);
        Set<Long> galeria = new java.util.HashSet<>();
        for (int x = -anelFora; x <= anelFora; x++) {
            for (int z = -anelFora; z <= anelFora; z++) {
                if (!noAnel(x, z, anelDentro, anelFora)) {
                    continue;
                }
                if (vazios.contains(ArenaComum.chave(x, 0, z))) {
                    continue;
                }
                galeria.add(ArenaComum.chave(x, 0, z));
            }
        }
        podarFarpas(galeria);
        for (long casa : galeria) {
            int x = ArenaComum.daChave(casa, 40);
            int z = ArenaComum.daChave(casa, 0);
            boolean beira = !noAnel(x, z, anelDentro + 1, anelFora - 1);
            mapa.put(ArenaComum.chave(x, anelAltura, z), beira ? ANEL_BEIRA : ANEL);
        }

        // O plató do meio, na mesma altura.
        for (int x = -platoRaio; x <= platoRaio; x++) {
            for (int z = -platoRaio; z <= platoRaio; z++) {
                if (!noPlato(x, z, platoRaio)) {
                    continue;
                }
                boolean beira = !noPlato(x, z, platoRaio - 1);
                mapa.put(ArenaComum.chave(x, anelAltura, z),
                        beira ? PLATO_BEIRA : PLATO);
            }
        }
        mapa.put(ArenaComum.chave(0, anelAltura, 0), NUCLEO);

        // As escadas, nas diagonais.
        for (int[] passo : escada(escadas, escadaLargura, raio, anelAltura)) {
            mapa.put(ArenaComum.chave(passo[0], passo[1], passo[2]), ESCADA);
        }

        // As colunas de cobertura, nos eixos.
        for (int i = 0; i < colunas; i++) {
            double angulo = Math.PI / 4 + 2 * Math.PI * i / colunas;
            int cx = (int) Math.round(Math.cos(angulo) * colunaRaio);
            int cz = (int) Math.round(Math.sin(angulo) * colunaRaio);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!ArenaComum.noDisco(dx, dz, 1)) {
                        continue;
                    }
                    for (int alto = 1; alto <= colunaAltura; alto++) {
                        mapa.put(ArenaComum.chave(cx + dx, alto, cz + dz),
                                alto == colunaAltura ? COLUNA_TOPO : COLUNA);
                    }
                }
            }
        }
        return mapa;
    }

    /**
     * Os degraus de todas as escadas: {x, fiada, z}.
     *
     * As escadas ficam nos EIXOS, e não nas diagonais. Não é gosto: na
     * diagonal, dois raios inteiros seguidos arredondam para a mesma casa —
     * {@code round(0,707 * 19)} e {@code round(0,707 * 18)} dão os dois
     * (13, 13) — e a escada sai com dois degraus empilhados no mesmo lugar,
     * um dentro do outro. No eixo, raio e casa são a mesma coisa.
     *
     * <p>Cada escada sobe um degrau por bloco, de fora para dentro, e o
     * último degrau cai EM CIMA da galeria, substituindo uma casa dela. É o
     * que garante a emenda: escada que termina ao lado da galeria termina a
     * meio bloco dela em algumas direções, por causa do recorte do disco em
     * células, e aí não leva a lugar nenhum.
     *
     * <p>Ela começa uma casa antes do muro, e não em cima dele: os degraus
     * têm largura, e a casa de ponta de um degrau largo na beira do disco já
     * cai dentro da faixa do muro.
     */
    public static List<int[]> escada(int quantas, int largura, int raio,
                                     int anelAltura) {
        List<int[]> passos = new ArrayList<>();
        double meia = (largura - 1) / 2.0;
        for (int i = 0; i < Math.max(1, quantas); i++) {
            double angulo = 2 * Math.PI * i / Math.max(1, quantas);
            double ux = Math.cos(angulo);
            double uz = Math.sin(angulo);
            for (int degrau = 1; degrau <= anelAltura; degrau++) {
                porDegrau(passos, ux, uz, raio - degrau, degrau, largura, meia);
            }
        }
        return passos;
    }

    private static void porDegrau(List<int[]> passos, double ux, double uz,
                                  int r, int fiada, int largura, double meia) {
        for (int j = 0; j < largura; j++) {
            double lado = j - meia;
            int x = (int) Math.round(ux * r - uz * lado);
            int z = (int) Math.round(uz * r + ux * lado);
            passos.add(new int[]{x, fiada, z});
        }
    }

    /**
     * Tira da galeria as casas com menos de dois vizinhos.
     *
     * Um buraco de queda perto da borda de dentro deixa, do outro lado dele,
     * uma casa com um vizinho só — e casa com um vizinho só é a quina em que
     * um jogador é encurralado sem ter errado nada. A poda é repetida porque
     * tirar uma farpa pode criar a seguinte.
     *
     * <p>Podar e não só reclamar: a posição dos buracos sai de seno e
     * cosseno, e exigir que ela nunca produza farpa seria exigir que quem
     * mexe no yml resolva geometria de cabeça.
     */
    static void podarFarpas(Set<Long> galeria) {
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (long casa : new java.util.ArrayList<>(galeria)) {
                int x = ArenaComum.daChave(casa, 40);
                int z = ArenaComum.daChave(casa, 0);
                int vizinhos = 0;
                for (int[] passo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (galeria.contains(ArenaComum.chave(x + passo[0], 0,
                            z + passo[1]))) {
                        vizinhos++;
                    }
                }
                if (vizinhos < 2) {
                    galeria.remove(casa);
                    mudou = true;
                }
            }
        }
    }

    /** As células que a galeria NÃO tem, para se cair de propósito. */
    static Set<Long> buracosDaGaleria(int quantos, int tamanho, int raio) {
        Set<Long> vazios = new java.util.HashSet<>();
        for (int i = 0; i < quantos; i++) {
            double angulo = Math.PI / 4 + 2 * Math.PI * i / Math.max(1, quantos);
            int cx = (int) Math.round(Math.cos(angulo) * raio);
            int cz = (int) Math.round(Math.sin(angulo) * raio);
            for (int dx = 0; dx < tamanho; dx++) {
                for (int dz = 0; dz < tamanho; dz++) {
                    vazios.add(ArenaComum.chave(cx + dx, 0, cz + dz));
                }
            }
        }
        return vazios;
    }

    /** A célula está na galeria? Bordas suaves, sem farpa de um vizinho só. */
    public static boolean noAnel(int x, int z, int dentro, int fora) {
        return ArenaComum.noDiscoMeio(x, z, fora)
                && !ArenaComum.noDiscoMeio(x, z, dentro - 1);
    }

    /** A célula está no plató do meio? */
    public static boolean noPlato(int x, int z, int raio) {
        return ArenaComum.noDiscoMeio(x, z, raio);
    }

    /** A marca do piso: um xadrez de quatro em quatro, para dar escala. */
    static boolean ehMarca(int x, int z) {
        return ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) & 1) == 0;
    }

    // ------------------------------------------------------------------
    //  Onde dá para ficar de pé
    // ------------------------------------------------------------------

    /**
     * Dá para ficar de pé em cima da célula {@code (x, fiada, z)}?
     *
     * Bloco embaixo, e duas fiadas de ar acima: um jogador tem 1,8 de altura.
     * Uma fiada só de ar passaria por "chão" e seria chão em que ninguém
     * cabe — o caso do piso debaixo de um degrau de escada.
     */
    public static boolean daParaFicar(Map<Long, Integer> mapa, int x, int fiada,
                                      int z) {
        return mapa.containsKey(ArenaComum.chave(x, fiada, z))
                && !mapa.containsKey(ArenaComum.chave(x, fiada + 1, z))
                && !mapa.containsKey(ArenaComum.chave(x, fiada + 2, z));
    }

    /** Todas as células em que dá para ficar de pé: {x, fiada, z}. */
    public static List<int[]> ondeDaParaFicar(Map<Long, Integer> mapa, int alcance,
                                              int tetoDaBusca) {
        List<int[]> saida = new ArrayList<>();
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int fiada = 0; fiada <= tetoDaBusca; fiada++) {
                    if (daParaFicar(mapa, x, fiada, z)) {
                        saida.add(new int[]{x, fiada, z});
                    }
                }
            }
        }
        return saida;
    }

    // ------------------------------------------------------------------
    //  A largada
    // ------------------------------------------------------------------

    public List<int[]> largadas(int quantos) {
        if (largadas == null || largadas.size() != Math.max(1, quantos)) {
            largadas = largadas(celulas(), Math.max(1, quantos), largadaRaio(),
                    anelFora() + 2);
        }
        return largadas;
    }

    /**
     * As largadas: ângulos espaçados, cada um ENCAIXADO na casa de piso mais
     * próxima em que dá para ficar de pé.
     *
     * O encaixe é o que faz isto não quebrar quando a planta muda. Um anel de
     * ângulos fixos passa por cima de escada, de coluna e de muro, e o
     * jogador nasce com a cabeça dentro de um degrau — ou pior, nasce certo
     * hoje e errado depois de alguém mexer num número do yml. Aqui o ângulo é
     * só uma intenção: a casa sai da conferência.
     */
    public static List<int[]> largadas(Map<Long, Integer> mapa, int quantos,
                                       int raioDaLargada, int raioMinimo) {
        List<int[]> saida = new ArrayList<>();
        int total = Math.max(1, quantos);
        for (int i = 0; i < total; i++) {
            double angulo = 2 * Math.PI * (i + 0.5) / total;
            int[] onde = acharLargada(mapa, angulo, raioDaLargada, raioMinimo, saida);
            if (onde != null) {
                saida.add(onde);
            }
        }
        return saida;
    }

    private static int[] acharLargada(Map<Long, Integer> mapa, double angulo,
                                      int raioDaLargada, int raioMinimo,
                                      List<int[]> jaPostas) {
        double ux = Math.cos(angulo);
        double uz = Math.sin(angulo);
        for (int r = raioDaLargada; r >= raioMinimo; r--) {
            for (int passo = 0; passo <= 8; passo++) {
                for (int sinal = 1; sinal >= -1; sinal -= 2) {
                    double lado = passo * sinal;
                    int x = (int) Math.round(ux * r - uz * lado);
                    int z = (int) Math.round(uz * r + ux * lado);
                    if (!daParaFicar(mapa, x, 0, z)) {
                        continue;
                    }
                    if (pertoDeOutra(jaPostas, x, z)) {
                        continue;
                    }
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }

    private static boolean pertoDeOutra(List<int[]> jaPostas, int x, int z) {
        for (int[] outra : jaPostas) {
            if (Math.hypot(x - outra[0], z - outra[1]) < 4) {
                return true;
            }
        }
        return false;
    }
}
