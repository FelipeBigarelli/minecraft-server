package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * O que todo construtor de arena sabe fazer.
 *
 * <h2>Por que existe</h2>
 *
 * Os quatro comandos de administração — info, construir, desfazer, textos —
 * são idênticos nos vinte jogos. Sem este contrato, cada jogo novo traz cento
 * e cinquenta linhas de comando copiadas, e a décima cópia já responde
 * diferente das outras nove num detalhe que ninguém lembra de conferir.
 *
 * <p>Com ele, {@code /biga <jogo> construir CONFIRMAR} funciona em qualquer
 * jogo registrado, inclusive nos que ainda não existem.
 */
public interface ConstrutorDeArena {

    /** O que um comando de obra devolve: deu certo, o que dizer, e quanto. */
    record Resultado(boolean ok, String mensagem, int blocos) {}

    /**
     * @param aplicar false confere o terreno e descreve a obra sem pôr um
     *                bloco. É o {@code info}.
     */
    Resultado construir(World mundo, boolean aplicar);

    Resultado desfazer(World mundo);

    /** Refaz só os letreiros, sem tocar em bloco. */
    Resultado textos(World mundo);

    /**
     * Onde o {@code /biga <jogo> ir} deixa quem chega.
     *
     * É o saguão, não a arena: quem administra quer conferir os letreiros e
     * as placas, e cair no meio de uma rodada em andamento seria pior do que
     * não ter o comando.
     */
    Location pontoDeEntrada(World mundo);
}
