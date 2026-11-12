package codes.biga.bigacore;

import java.util.ArrayList;
import java.util.List;

/**
 * A geometria de uma pista fechada: eixo, portões e atalhos.
 *
 * <h2>Por que é uma classe só de contas</h2>
 *
 * Nada aqui toca em bloco, em jogador ou em mundo. É de propósito: a pergunta
 * "este portão é cruzável correndo pela faixa de dentro?" tem de ter resposta
 * ANTES de alguém perder uma rodada descobrindo que não. Sem servidor, o teste
 * ANDA uma volta inteira ponto por ponto e confere a sequência de portões —
 * coisa que no jogo custaria três minutos e dois jogadores.
 *
 * <h2>O eixo é reamostrado, e isso não é detalhe</h2>
 *
 * O anel sai de uma fórmula em ângulo, e ângulo igual não dá distância igual:
 * onde a onda abre, dois graus são três blocos; onde ela fecha, um. Portão a
 * cada tanto de ÂNGULO ficaria amontoado de um lado e ralo do outro, e o
 * atalho mediria errado o que economiza. Por isso o eixo é amostrado fino e
 * reamostrado de um em um bloco de distância real: daí para frente, índice é
 * distância, e toda conta de arco é uma subtração.
 *
 * <h2>Portão é plano, e o teste é de SEGMENTO</h2>
 *
 * Um portão é um plano vertical com meia-largura. O jogo não pergunta "o
 * jogador está no portão?" — pergunta "o caminho que ele andou neste tique
 * atravessou o plano?". A diferença aparece com Velocidade II: quem anda meio
 * bloco por tique passa por dentro de uma caixa de um bloco sem nunca estar
 * dentro dela em nenhum tique, e um portão perdido não dá erro: tranca a volta
 * para sempre. A ideia vem do plugin de corrida de barco de referência, que
 * resolve o mesmo problema do mesmo jeito.
 */
public final class PistaFechada {

    /** Quantas amostras finas antes de reamostrar. Fino basta: é uma vez só. */
    public static final int AMOSTRAS = 2880;

    private PistaFechada() {
    }

    // ------------------------------------------------------------------
    //  O eixo
    // ------------------------------------------------------------------

    /**
     * O anel ondulado, amostrado em ângulo.
     *
     * Menos cosseno em vez de mais seno porque assim o ângulo zero cai num
     * VALE da onda. É onde fica a largada, e o vale é o ponto mais longe dos
     * bojos — que é por onde os atalhos passam. Largada em cima de atalho
     * deixaria a linha de chegada contornável, e "primeiro a cruzar" não pode
     * admitir quem não cruzou.
     */
    public static List<double[]> anel(double raio, double onda, int voltas,
                                      int amostras) {
        List<double[]> saida = new ArrayList<>(amostras);
        for (int i = 0; i < amostras; i++) {
            double t = 2 * Math.PI * i / amostras;
            double r = raio - onda * Math.cos(voltas * t);
            saida.add(new double[]{r * Math.cos(t), r * Math.sin(t)});
        }
        return saida;
    }

    /** Reamostra um caminho fechado de tantos em tantos blocos de distância real. */
    public static List<double[]> reamostrar(List<double[]> bruto, double passo) {
        int n = bruto.size();
        double[] arco = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            arco[i] = arco[i - 1] + distancia(bruto.get(i - 1), bruto.get(i % n));
        }
        double total = arco[n];

        List<double[]> saida = new ArrayList<>((int) (total / passo) + 1);
        int i = 0;
        for (double alvo = 0; alvo < total; alvo += passo) {
            while (i + 1 <= n && arco[i + 1] < alvo) {
                i++;
            }
            double[] a = bruto.get(i % n);
            double[] b = bruto.get((i + 1) % n);
            double faixa = arco[i + 1] - arco[i];
            double f = faixa <= 0 ? 0 : (alvo - arco[i]) / faixa;
            saida.add(new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f});
        }
        return saida;
    }

    /** O eixo da pista, pronto: um ponto por bloco de pista. */
    public static List<double[]> eixo(double raio, double onda, int voltas) {
        return reamostrar(anel(raio, onda, voltas, AMOSTRAS), 1.0);
    }

    /**
     * A direção de quem corre no ponto i, tirada dos vizinhos de dois em dois.
     *
     * De dois e não de um porque o eixo foi reamostrado: dois pontos vizinhos
     * ficam a um bloco, e um bloco de base dá uma direção que tremelica.
     */
    public static double[] tangente(List<double[]> eixo, int i) {
        int n = eixo.size();
        double[] a = eixo.get(Math.floorMod(i - 2, n));
        double[] b = eixo.get(Math.floorMod(i + 2, n));
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        double d = Math.hypot(dx, dz);
        if (d < 1e-9) {
            return new double[]{1, 0};
        }
        return new double[]{dx / d, dz / d};
    }

    /** A direção que atravessa a pista no ponto i: positivo é para fora. */
    public static double[] lateral(List<double[]> eixo, int i) {
        double[] t = tangente(eixo, i);
        double[] fora = new double[]{-t[1], t[0]};
        double[] p = eixo.get(i);
        // Dois perpendiculares existem; o que aponta para fora é o que
        // se afasta do centro. Sem isto, "guia de fora" sai de dentro em
        // metade da pista e o muro nasce do lado errado.
        if (fora[0] * p[0] + fora[1] * p[1] < 0) {
            return new double[]{t[1], -t[0]};
        }
        return fora;
    }

    // ------------------------------------------------------------------
    //  Portões e atalhos
    // ------------------------------------------------------------------

    /** Os índices dos bojos: os pontos mais longe do centro, um por onda. */
    public static int[] bojos(List<double[]> eixo, int voltas) {
        int[] saida = new int[voltas];
        for (int k = 0; k < voltas; k++) {
            double alvo = Math.PI / voltas + k * 2 * Math.PI / voltas;
            int melhor = 0;
            double menor = Double.MAX_VALUE;
            for (int i = 0; i < eixo.size(); i++) {
                double[] p = eixo.get(i);
                double ang = Math.atan2(p[1], p[0]);
                double d = Math.abs(diferencaDeAngulo(ang, alvo));
                if (d < menor) {
                    menor = d;
                    melhor = i;
                }
            }
            saida[k] = melhor;
        }
        return saida;
    }

    private static double diferencaDeAngulo(double a, double b) {
        double d = (a - b) % (2 * Math.PI);
        if (d > Math.PI) {
            d -= 2 * Math.PI;
        }
        if (d < -Math.PI) {
            d += 2 * Math.PI;
        }
        return d;
    }

    /** Os índices dos portões: de tantos em tantos blocos de pista, o 0 na largada. */
    public static int[] portoes(int quantos, int n) {
        int[] saida = new int[quantos];
        for (int k = 0; k < quantos; k++) {
            saida[k] = (int) Math.round((double) k * n / quantos) % n;
        }
        return saida;
    }

    /**
     * Os atalhos: a corda que corta a ponta de cada bojo.
     *
     * @param abertura quantos graus de arco a corda corta.
     * @return pares {entrada, saída} em índice de eixo.
     */
    public static int[][] atalhos(List<double[]> eixo, int voltas, int abertura) {
        int n = eixo.size();
        int meio = (int) (n * abertura / 360.0 / 2);
        int[] tips = bojos(eixo, voltas);
        int[][] saida = new int[tips.length][2];
        for (int k = 0; k < tips.length; k++) {
            saida[k][0] = Math.floorMod(tips[k] - meio, n);
            saida[k][1] = Math.floorMod(tips[k] + meio, n);
        }
        return saida;
    }

    /** O índice i está no arco que vai de ia a ib andando para a frente? */
    public static boolean entre(int ia, int ib, int i) {
        if (ia <= ib) {
            return i > ia && i < ib;
        }
        return i > ia || i < ib;
    }

    /**
     * Um portão: plano vertical, centro, direção e meia-largura.
     *
     * A altura não está aqui porque a pista é plana: quem confere a altura é o
     * jogo, com uma faixa em volta da fiada da pista. Guardar um y por portão
     * seria guardar a mesma informação oito vezes e deixar sete delas
     * envelhecerem quando a planta mudasse.
     */
    public record Portao(double cx, double cz, double nx, double nz,
                         double meiaLargura) {

        /** De que lado do plano está este ponto. Negativo é "ainda não chegou". */
        public double lado(double x, double z) {
            return (x - cx) * nx + (z - cz) * nz;
        }

        /** O segmento de a para b atravessou este plano, em qualquer sentido? */
        public boolean cruzou(double ax, double az, double bx, double bz) {
            double d1 = lado(ax, az);
            double d2 = lado(bx, bz);
            if ((d1 > 0) == (d2 > 0)) {
                return false;
            }
            double t = d1 / (d1 - d2);
            double ix = ax + (bx - ax) * t;
            double iz = az + (bz - az) * t;
            double atravessado = (ix - cx) * -nz + (iz - cz) * nx;
            return Math.abs(atravessado) <= meiaLargura;
        }

        /**
         * Atravessou NO SENTIDO da corrida?
         *
         * A normal do plano é a direção de quem corre, então cruzar para
         * frente é vir do lado negativo. Sem esta conferência, quem anda de
         * ré por cima de um portão o cruza do mesmo jeito — e o jogo contaria
         * progresso para quem está voltando.
         */
        public boolean cruzouParaFrente(double ax, double az, double bx, double bz) {
            return lado(ax, az) <= 0 && cruzou(ax, az, bx, bz);
        }
    }

    /**
     * Os planos de cada portão lógico: um na pista e um em cada atalho.
     *
     * <p>Esta é a parte que faz atalho e portão conviverem. Um atalho pula um
     * pedaço da pista, e nesse pedaço pode haver portão: sem plano nenhum na
     * ponte, quem atalha nunca cruza aquele portão e fica travado no mesmo
     * número de portão até o tempo acabar — sem erro, sem aviso, parecendo que
     * o jogo não conta. Com um plano por portão pulado, posto em ORDEM ao
     * longo da ponte, quem atalha cruza os mesmos portões na mesma sequência
     * de quem foi pela pista.
     */
    public static List<List<Portao>> planos(List<double[]> eixo, int[] portoes,
                                            int[][] atalhos, double meiaPista,
                                            double meiaPonte) {
        List<List<Portao>> saida = new ArrayList<>(portoes.length);
        for (int i : portoes) {
            double[] p = eixo.get(i);
            double[] t = tangente(eixo, i);
            List<Portao> um = new ArrayList<>(2);
            um.add(new Portao(p[0], p[1], t[0], t[1], meiaPista));
            saida.add(um);
        }

        for (int[] atalho : atalhos) {
            List<Integer> pulados = new ArrayList<>();
            for (int k = 0; k < portoes.length; k++) {
                if (entre(atalho[0], atalho[1], portoes[k])) {
                    pulados.add(k);
                }
            }
            if (pulados.isEmpty()) {
                continue;
            }
            double[] a = eixo.get(atalho[0]);
            double[] b = eixo.get(atalho[1]);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double comp = Math.hypot(dx, dz);
            for (int ordem = 0; ordem < pulados.size(); ordem++) {
                double f = (ordem + 1.0) / (pulados.size() + 1.0);
                saida.get(pulados.get(ordem)).add(new Portao(
                        a[0] + dx * f, a[1] + dz * f, dx / comp, dz / comp, meiaPonte));
            }
        }
        return saida;
    }

    // ------------------------------------------------------------------
    //  Conferências
    // ------------------------------------------------------------------

    /**
     * O menor raio de curvatura do eixo.
     *
     * Serve para uma pergunta só: a faixa dobra sobre si mesma? Uma curva de
     * raio menor que a meia-largura da faixa faz a borda de dentro se cruzar,
     * e o que aparece em jogo é um pedaço de pista sem saída.
     */
    public static double curvaturaMinima(List<double[]> eixo) {
        int n = eixo.size();
        double pior = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double[] a = eixo.get(Math.floorMod(i - 1, n));
            double[] b = eixo.get(i);
            double[] c = eixo.get((i + 1) % n);
            double area = Math.abs((b[0] - a[0]) * (c[1] - a[1])
                    - (c[0] - a[0]) * (b[1] - a[1])) / 2;
            if (area < 1e-9) {
                continue;
            }
            double r = distancia(a, b) * distancia(b, c) * distancia(c, a) / (4 * area);
            pior = Math.min(pior, r);
        }
        return pior;
    }

    /**
     * A menor distância entre dois trechos NÃO vizinhos do eixo.
     *
     * É o que diz se a pista encosta nela mesma do outro lado da onda. Tem de
     * ser maior que a faixa mais os dois muros, senão duas retas diferentes
     * compartilham blocos e o jogador atravessa de uma para a outra.
     */
    public static double folgaEntreTracos(List<double[]> eixo, int janela) {
        int n = eixo.size();
        double pior = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = i + janela; j < n; j++) {
                if (n - j + i < janela) {
                    continue;
                }
                pior = Math.min(pior, distancia(eixo.get(i), eixo.get(j)));
            }
        }
        return pior;
    }

    /** O índice do eixo mais perto de um ponto. */
    public static int maisProximo(List<double[]> eixo, double x, double z) {
        int melhor = 0;
        double menor = Double.MAX_VALUE;
        for (int i = 0; i < eixo.size(); i++) {
            double[] p = eixo.get(i);
            double d = (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z);
            if (d < menor) {
                menor = d;
                melhor = i;
            }
        }
        return melhor;
    }

    private static double distancia(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
