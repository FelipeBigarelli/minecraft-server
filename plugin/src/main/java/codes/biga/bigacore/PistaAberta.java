package codes.biga.bigacore;

import java.util.ArrayList;
import java.util.List;

/**
 * A geometria de um percurso ABERTO, lido de um mapa pronto.
 *
 * <h2>Por que não serve a {@link PistaFechada}</h2>
 *
 * <p>A pista gerada é um anel: a largada é também a chegada, o eixo dá a
 * volta e o índice seguinte ao último é o primeiro. Uma descida de montanha
 * não fecha — começa no alto e acaba embaixo, e o ponto depois do último não
 * existe. Reamostrar um caminho aberto como se fechado inventa um segmento
 * voltando da chegada à largada, e a tangente das duas pontas aponta para o
 * lugar errado, que é justamente onde ficam a grade e o pórtico.
 *
 * <h2>E a montanha tem a terceira dimensão</h2>
 *
 * <p>O anel é plano: um y só para a pista inteira, e o jogo confere a altura
 * contra ele. A estrada do Monte Akina cai de 221 a 87 — cento e trinta e
 * quatro fiadas — e passa POR CIMA DE SI MESMA nas curvas de grampo. Altura
 * única ali não é imprecisão, é portão que conta duas vezes: quem desce o
 * trecho de baixo cruza o plano do trecho de cima, que tem o mesmo x e o
 * mesmo z. Por isso o eixo carrega um y por ponto, e o portão é conferido
 * contra a altura DELE.
 *
 * <h2>O portão é o mesmo objeto</h2>
 *
 * <p>Reaproveita {@link PistaFechada.Portao}: a conta de "o segmento que o
 * jogador andou atravessou este plano, no sentido da corrida, dentro da
 * meia-largura?" é a mesma numa pista aberta e numa fechada, e é uma conta
 * que já foi testada. O que muda é de onde vêm os planos, não o que eles são.
 *
 * <p>Nada aqui toca em bloco, em jogador ou em mundo: o percurso de verdade
 * tem mil e seiscentos blocos de descida, e a pergunta "este portão é
 * cruzável descendo?" tem de ter resposta antes de alguém gastar dois minutos
 * descobrindo que não.
 */
public final class PistaAberta {

    /** Teto de pontos de um percurso, para um arquivo torto não comer a RAM. */
    public static final int MAXIMO_DE_PONTOS = 20_000;

    /** Quantos pontos para cada lado a mediana do filtro de picos olha. */
    public static final int JANELA_DO_PICO = 4;

    private PistaAberta() {
    }

    // ------------------------------------------------------------------
    //  A leitura
    // ------------------------------------------------------------------

    /**
     * Os pontos de um arquivo de percurso: três números por linha.
     *
     * <p>Linha vazia e linha de comentário são puladas; linha com menos de
     * três números, ou com número que não é número, também — e de propósito,
     * sem erro: um percurso é um arquivo de milhares de linhas lido de uma
     * pasta que o dono mexe à mão, e derrubar o jogo inteiro por causa de uma
     * linha ruim no meio seria trocar um problema pequeno por um grande.
     * Quem decide se sobrou percurso é {@link #serve}.
     */
    public static List<int[]> ler(List<String> linhas) {
        List<int[]> saida = new ArrayList<>();
        for (String linha : linhas) {
            if (saida.size() >= MAXIMO_DE_PONTOS) {
                break;
            }
            if (linha == null) {
                continue;
            }
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) {
                continue;
            }
            String[] partes = limpa.split("\\s+");
            if (partes.length < 3) {
                continue;
            }
            try {
                saida.add(new int[]{
                        (int) Math.round(Double.parseDouble(partes[0])),
                        (int) Math.round(Double.parseDouble(partes[1])),
                        (int) Math.round(Double.parseDouble(partes[2]))});
            } catch (NumberFormatException naoEhNumero) {
                // linha torta: pulada, ver o javadoc
            }
        }
        return saida;
    }

    /**
     * Tira PICOS de altura: ponto cuja fiada discorda dos dois vizinhos.
     *
     * <h3>O caso que deu origem a isto</h3>
     *
     * <p>No primeiro trecho do Monte Akina, dois pontos dos quatrocentos
     * saíram em y 100 entre vizinhos em y 221 — o traçado seguiu o gelo e o
     * gelo tinha um lago congelado cento e vinte fiadas ABAIXO da estrada,
     * na mesma coluna. O x e o z continuavam certos; só a fiada pulava e
     * voltava.
     *
     * <p>O estrago não era visual: o eixo reamostrado passava a ter
     * quatrocentos blocos de queda e subida, o percurso media mil e
     * quinhentos blocos em vez de mil e duzentos, e um dos dez portões
     * nascia no fundo do lago — portão que ninguém cruza, corrida que não
     * acaba. Um pico some aqui, em três linhas, e não vira meia hora de
     * alguém descendo a montanha para descobrir por quê.
     *
     * <h3>Mediana de uma janela, e não comparação com o vizinho</h3>
     *
     * <p>Comparar com os dois vizinhos imediatos deixa passar o pico de DOIS
     * pontos seguidos: cada um serve de álibi para o outro. A mediana de nove
     * pontos não tem esse problema — dois pontos errados não viram maioria —
     * e tem outra virtude que importa mais: a mediana de uma janela SIMÉTRICA
     * de uma rampa é o ponto do meio dela, exatamente. Por isso o filtro não
     * aplaina a montanha, por mais íngreme que ela seja; perto das pontas a
     * janela encolhe para continuar simétrica, pelo mesmo motivo.
     *
     * @param tolerancia quantas fiadas de desacordo ainda são estrada
     */
    public static List<int[]> tirarPicos(List<int[]> bruto, int tolerancia) {
        if (bruto == null || bruto.size() < 3) {
            return bruto;
        }
        int n = bruto.size();
        List<int[]> saida = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int meia = Math.min(JANELA_DO_PICO, Math.min(i, n - 1 - i));
            int[] janela = new int[2 * meia + 1];
            for (int j = 0; j < janela.length; j++) {
                janela[j] = bruto.get(i - meia + j)[1];
            }
            java.util.Arrays.sort(janela);
            int mediana = janela[meia];
            int[] ponto = bruto.get(i);
            saida.add(Math.abs(ponto[1] - mediana) > tolerancia
                    ? new int[]{ponto[0], mediana, ponto[2]}
                    : ponto);
        }
        return saida;
    }

    /** Um percurso serve se dá para pôr grade, portões e pórtico nele. */
    public static boolean serve(List<double[]> eixo) {
        return eixo != null && eixo.size() >= 16;
    }

    // ------------------------------------------------------------------
    //  O eixo
    // ------------------------------------------------------------------

    /**
     * Reamostra o caminho de tantos em tantos blocos de distância real.
     *
     * <p>Aberto: não há segmento do fim para o começo. Daqui para a frente
     * índice é distância, e toda conta de trecho vira uma subtração — é o que
     * permite espalhar portões por DISTÂNCIA e não por número de pontos, que
     * numa estrada traçada à mão ficariam amontoados nas curvas.
     *
     * <p>A distância é medida em três dimensões porque a descida é íngreme:
     * medida só no plano, um trecho de rampa pareceria mais curto do que é e
     * levaria portão a menos.
     */
    public static List<double[]> reamostrar(List<int[]> bruto, double passo) {
        List<double[]> saida = new ArrayList<>();
        if (bruto == null || bruto.isEmpty()) {
            return saida;
        }
        double usado = Math.max(0.25, passo);
        int[] primeiro = bruto.get(0);
        saida.add(new double[]{primeiro[0], primeiro[1], primeiro[2]});
        double sobra = 0;
        for (int i = 1; i < bruto.size(); i++) {
            int[] a = bruto.get(i - 1);
            int[] b = bruto.get(i);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double dz = b[2] - a[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= 0) {
                continue;
            }
            double anda = usado - sobra;
            while (anda <= d && saida.size() < MAXIMO_DE_PONTOS) {
                double f = anda / d;
                saida.add(new double[]{a[0] + dx * f, a[1] + dy * f,
                        a[2] + dz * f});
                anda += usado;
            }
            sobra = (sobra + d) % usado;
        }
        int[] ultimo = bruto.get(bruto.size() - 1);
        double[] fim = saida.get(saida.size() - 1);
        if (Math.abs(fim[0] - ultimo[0]) > 0.5 || Math.abs(fim[2] - ultimo[2]) > 0.5) {
            saida.add(new double[]{ultimo[0], ultimo[1], ultimo[2]});
        }
        return saida;
    }

    /** O comprimento do percurso, em blocos de verdade. */
    public static double comprimento(List<double[]> eixo) {
        double soma = 0;
        for (int i = 1; i < eixo.size(); i++) {
            double[] a = eixo.get(i - 1);
            double[] b = eixo.get(i);
            soma += Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2)
                    + Math.pow(b[2] - a[2], 2));
        }
        return soma;
    }

    /**
     * A direção da corrida no ponto i, no plano, já normalizada.
     *
     * <p>Olha uma janela para trás e para frente em vez dos vizinhos
     * imediatos: a estrada veio de um traçado bloco a bloco, e dois pontos
     * seguidos de uma escada de blocos apontam na diagonal mesmo quando a
     * pista segue reta. Portão torto é portão que o jogador cruza de lado e
     * que mede errado a meia-largura.
     *
     * <p>Nas pontas a janela encolhe em vez de dar a volta: na largada e na
     * chegada é exatamente onde a direção precisa estar certa, porque é por
     * ela que a grade e o pórtico se orientam.
     */
    public static double[] tangente(List<double[]> eixo, int i, int janela) {
        int n = eixo.size();
        int meio = Math.max(1, janela);
        int a = Math.max(0, i - meio);
        int b = Math.min(n - 1, i + meio);
        if (a == b) {
            return new double[]{1, 0};
        }
        double dx = eixo.get(b)[0] - eixo.get(a)[0];
        double dz = eixo.get(b)[2] - eixo.get(a)[2];
        double comp = Math.hypot(dx, dz);
        if (comp < 1e-9) {
            return new double[]{1, 0};
        }
        return new double[]{dx / comp, dz / comp};
    }

    // ------------------------------------------------------------------
    //  Os portões
    // ------------------------------------------------------------------

    /**
     * Os índices dos portões: espalhados pelo percurso, o último na chegada.
     *
     * <p>A largada NÃO é portão. Num anel ela é, porque é a mesma linha da
     * chegada e é ela que fecha a volta; numa descida o jogador começa EM
     * CIMA da linha de largada, e um portão ali seria cruzado no primeiro
     * tique — ou não seria cruzado nunca, porque ele já está do lado de lá.
     *
     * <p>O último portão é a chegada — mas um pouco ANTES da ponta do eixo,
     * e isto não é detalhe. O percurso acaba onde a estrada traçada acaba; se
     * a bandeirada caísse no último ponto, o jogador teria de ULTRAPASSAR o
     * fim da estrada para cruzá-la, e o portão é conferido pelo SEGMENTO
     * andado: quem para em cima do plano não o atravessou. Numa estrada sem
     * continuação, a corrida simplesmente não acabaria — ninguém entenderia
     * por quê, e o tempo limite é que daria o vencedor. Com o recuo sobra
     * pista para a bandeirada cair e para o barco frear depois dela.
     */
    public static int[] portoes(int quantos, int n) {
        return portoes(quantos, n, 0);
    }

    /**
     * Os mesmos portões, começando DEPOIS de um ponto do eixo.
     *
     * <h3>Por que a largada manda nos portões</h3>
     *
     * <p>A grade não nasce no começo do traçado: ela é posta no primeiro
     * pedaço de estrada largo o bastante para duas colunas de barco, e num
     * dos cinco trechos do Akina isso é só no ponto 45 de 397. Com os portões
     * espalhados pelo eixo inteiro, o primeiro deles caía ATRÁS da grade: o
     * pelotão largava já passado dele, nunca o cruzava, e a corrida ficava
     * parada no portão 1 de 10 com todo mundo descendo a montanha.
     *
     * <p>Então os portões passam a ser espalhados entre a linha de largada e
     * a chegada, que é onde a corrida realmente acontece.
     *
     * @param de o índice da linha de largada; 0 para o eixo inteiro
     */
    public static int[] portoes(int quantos, int n, int de) {
        int usados = Math.max(1, quantos);
        int fim = Math.max(1, n - 1);
        int ultimo = Math.max(1, fim - Math.min(4, fim / 4));
        int comeco = Math.max(0, Math.min(ultimo - usados, de));
        int[] saida = new int[usados];
        for (int k = 0; k < usados; k++) {
            saida[k] = comeco + (int) Math.round(
                    (k + 1.0) * (ultimo - comeco) / usados);
            if (k > 0 && saida[k] <= saida[k - 1]) {
                saida[k] = Math.min(ultimo, saida[k - 1] + 1);
            }
        }
        saida[usados - 1] = ultimo;
        return saida;
    }

    /** Um plano por portão, na direção da descida. */
    public static List<List<PistaFechada.Portao>> planos(List<double[]> eixo,
                                                         int[] portoes,
                                                         double meiaLargura,
                                                         int janela) {
        List<List<PistaFechada.Portao>> saida = new ArrayList<>(portoes.length);
        for (int i : portoes) {
            double[] p = eixo.get(Math.max(0, Math.min(eixo.size() - 1, i)));
            double[] t = tangente(eixo, i, janela);
            saida.add(List.of(new PistaFechada.Portao(p[0], p[2], t[0], t[1],
                    meiaLargura)));
        }
        return saida;
    }

    /** A fiada de cada portão: é contra ela que o jogo confere a altura. */
    public static int[] alturas(List<double[]> eixo, int[] portoes) {
        int[] saida = new int[portoes.length];
        for (int k = 0; k < portoes.length; k++) {
            int i = Math.max(0, Math.min(eixo.size() - 1, portoes[k]));
            saida[k] = (int) Math.round(eixo.get(i)[1]);
        }
        return saida;
    }

    // ------------------------------------------------------------------
    //  Conferências
    // ------------------------------------------------------------------

    /**
     * Se os portões saem na ordem de quem desce o percurso ponto por ponto.
     *
     * <p>É a conferência que o teste roda sem servidor: anda o eixo inteiro
     * como se fosse o jogador e confere que cruzou todos os planos, na
     * sequência. Um portão torto o bastante para ser cruzado de lado, ou
     * posto onde a estrada dobra sobre si mesma, falha aqui — e não em jogo,
     * dois minutos montanha abaixo.
     *
     * @return o índice do primeiro portão que não saiu na ordem, ou -1
     */
    public static int conferirAOrdem(List<double[]> eixo,
                                     List<List<PistaFechada.Portao>> planos) {
        int alvo = 0;
        for (int i = 1; i < eixo.size() && alvo < planos.size(); i++) {
            double[] a = eixo.get(i - 1);
            double[] b = eixo.get(i);
            for (PistaFechada.Portao portao : planos.get(alvo)) {
                if (portao.cruzouParaFrente(a[0], a[2], b[0], b[2])) {
                    alvo++;
                    break;
                }
            }
        }
        return alvo == planos.size() ? -1 : alvo;
    }

    /** A maior queda de altura entre dois portões seguidos. */
    public static int maiorDegrau(int[] alturas) {
        int maior = 0;
        for (int k = 1; k < alturas.length; k++) {
            maior = Math.max(maior, Math.abs(alturas[k] - alturas[k - 1]));
        }
        return maior;
    }
}
