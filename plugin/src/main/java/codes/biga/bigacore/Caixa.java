package codes.biga.bigacore;

/**
 * Uma caixa de blocos, inclusiva nas duas pontas.
 *
 * Existe separada do {@link WorldGuardBridge} de proposito: e geometria pura,
 * sem nenhuma classe do WorldGuard, do Bukkit ou do mundo. Isso deixa toda a
 * conta de tamanho, area e colisao sob teste unitario, sem servidor ligado —
 * e e justamente a conta que, errada por um bloco, vende ao jogador um
 * terreno de 19x19 cobrando por 20x20.
 *
 * "Inclusiva nas duas pontas" e a convencao do WorldGuard: uma regiao de
 * x=0 a x=19 tem 20 blocos de largura, nao 19. Metade dos erros de off-by-one
 * em plugin de terreno nasce de misturar essa convencao com a de intervalo
 * aberto.
 */
public record Caixa(int x1, int y1, int z1, int x2, int y2, int z2) {

    /**
     * Caixa quadrada em volta de um bloco, pegando a coluna inteira entre
     * {@code yMin} e {@code yMax}.
     *
     * Com tamanho impar o bloco informado fica no centro exato. Com tamanho
     * par nao existe centro exato — a caixa sobra um bloco para o lado
     * positivo, e quem construiu fica a (tamanho/2 - 1) blocos de uma borda e
     * a (tamanho/2) da outra. Isso e aritmetica, nao bug: 20 blocos nao tem
     * bloco do meio.
     */
    public static Caixa centradaEm(int x, int z, int tamanho, int yMin, int yMax) {
        int recuo = (tamanho - 1) / 2;
        int minX = x - recuo;
        int minZ = z - recuo;
        return new Caixa(minX, yMin, minZ,
                minX + tamanho - 1, yMax, minZ + tamanho - 1);
    }

    public int minX() {
        return Math.min(x1, x2);
    }

    public int maxX() {
        return Math.max(x1, x2);
    }

    public int minY() {
        return Math.min(y1, y2);
    }

    public int maxY() {
        return Math.max(y1, y2);
    }

    public int minZ() {
        return Math.min(z1, z2);
    }

    public int maxZ() {
        return Math.max(z1, z2);
    }

    public int larguraX() {
        return maxX() - minX() + 1;
    }

    public int larguraZ() {
        return maxZ() - minZ() + 1;
    }

    /** Area em blocos de chao. O preco do terreno sai daqui, nao do volume. */
    public int area() {
        return larguraX() * larguraZ();
    }

    /** Lado, quando a caixa e quadrada. Devolve o maior lado se nao for. */
    public int lado() {
        return Math.max(larguraX(), larguraZ());
    }

    public int centroX() {
        return minX() + (larguraX() - 1) / 2;
    }

    public int centroZ() {
        return minZ() + (larguraZ() - 1) / 2;
    }

    /**
     * Mesma caixa, aberta {@code blocos} para cada lado no plano horizontal.
     *
     * So em X e Z: inflar em Y nao faria sentido, porque o terreno ja pega a
     * coluna inteira do mundo. Usada para a folga entre terrenos — a checagem
     * de colisao roda contra a caixa inflada, entao dois terrenos vizinhos
     * ficam obrigados a manter esse espaco entre eles.
     */
    public Caixa inflada(int blocos) {
        if (blocos == 0) {
            return this;
        }
        return new Caixa(minX() - blocos, minY(), minZ() - blocos,
                maxX() + blocos, maxY(), maxZ() + blocos);
    }

    /** Caixa com a mesma area, presa entre dois limites de altura. */
    public Caixa comAltura(int yMin, int yMax) {
        return new Caixa(minX(), yMin, minZ(), maxX(), yMax, maxZ());
    }

    public boolean contemHorizontal(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    /**
     * Distancia horizontal de Chebyshev ate um ponto: quantos blocos faltam
     * para o ponto entrar na caixa, medidos pelo eixo mais folgado.
     *
     * Chebyshev e nao euclidiana porque a regra que isso serve ("nao vender
     * terreno a menos de N blocos do spawn") fala de um quadrado em volta do
     * spawn, igual ao que a protecao vanilla desenhava. Com distancia
     * euclidiana o canto do quadrado ficaria liberado.
     */
    public int distanciaAte(int x, int z) {
        int dx = Math.max(0, Math.max(minX() - x, x - maxX()));
        int dz = Math.max(0, Math.max(minZ() - z, z - maxZ()));
        return Math.max(dx, dz);
    }
}
