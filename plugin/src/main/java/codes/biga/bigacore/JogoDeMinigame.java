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

    void start();

    void parar();
}
