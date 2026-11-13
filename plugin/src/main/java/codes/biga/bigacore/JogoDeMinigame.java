package codes.biga.bigacore;

/**
 * O que o comando precisa saber de um minigame, qualquer que seja ele.
 *
 * Nasceu para o {@code /biga <jogo>} valer para os vinte sem um "se for o
 * jogo tal" dentro. Os dois primeiros jogos — TNT Run e Spleef — nasceram
 * antes do {@link JogoDeArena} e implementam isto à mão; todos os seguintes
 * ganham de graça, por herança.
 */
public interface JogoDeMinigame {

    /** Larga a rodada agora, mesmo sem o mínimo de gente. */
    String comecarAgora();

    /** Cancela a rodada e devolve todos ao saguão. */
    String pararRodada();

    /** O estado, em palavra, para o info do comando. */
    String estadoEscrito();

    int quantosNaArena();

    int quantosProntos();

    int quantosVivos();

    /**
     * Os parágrafos de instrução, que o SAGUÃO escreve nas paredes da rua.
     *
     * Eles viviam no bate-papo, ditos um a um quando o jogador entrava na
     * arena. Era a coisa errada por dois motivos: o bate-papo empurra texto
     * para cima — chegar com a rodada anterior acabando significava ler as
     * regras entremeadas de "fulano caiu" —, e uma parede de texto cinza é a
     * última coisa que alguém lê quando acabou de chegar num lugar novo.
     *
     * <p>Aqui é só a FONTE do texto. Quem desenha é o {@link SaguaoPadrao}, e
     * ele desenha nas fachadas das casas, na altura do olho de quem anda pela
     * avenida: ler passa a ser andar, e cada parágrafo tem a sua placa.
     */
    default java.util.List<String> instrucoes() {
        return java.util.List.of();
    }

    void start();

    void parar();
}
