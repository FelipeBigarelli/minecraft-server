package codes.biga.bigacore;

/**
 * Um minigame registrado: a planta, a obra e o jogo.
 *
 * É o que o comando precisa para atender {@code /biga <jogo> ...} sem saber de
 * que jogo se trata. Vinte linhas de registro no lugar de vinte cópias de
 * comando.
 */
public record JogoRegistrado(CatalogoDeArena catalogo, ConstrutorDeArena construtor,
                             JogoDeMinigame jogo) {

    public String etiqueta() {
        return catalogo.etiqueta();
    }

    public String titulo() {
        return catalogo.titulo();
    }

    public String mundo() {
        return catalogo.mundo();
    }
}
