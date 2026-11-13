package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.List;

/**
 * O que todo minigame de arena tem na planta dele.
 *
 * <h2>Por que uma interface e não só uma classe por jogo</h2>
 *
 * São vinte jogos, e dezoito deles ainda nem existem. O saguão, a fila, as
 * placas, a contagem e a volta ao lobby são iguais nos vinte — o que muda é a
 * arena. Esta interface é o contrato mínimo que o {@link JogoDeArena} e o
 * {@link SaguaoPadrao} precisam para funcionar sem saber de que jogo se trata.
 *
 * <p>Repare no que NÃO está aqui: nada de piso, de vão, de raio da arena, de
 * tempo de derretimento. Isso é de cada jogo, mora no catálogo dele, e se
 * subisse para cá a interface viraria a união de vinte jogos — que é o mesmo
 * que não ter interface nenhuma.
 */
public interface PlantaDeArena {

    /** O nome do mundo próprio deste jogo. */
    String mundo();

    /** Para o log e as mensagens: "tntrun", "spleef". */
    String etiqueta();

    /** A fiada de referência da arena. Cada jogo decide o que ela significa. */
    int topoY();

    /** Abaixo desta altura o jogador está fora da rodada. */
    int quedaY();

    int saguaoY();

    int saguaoRaio();

    int saguaoParapeito();

    /** O raio da área que a obra ocupa — é o que o desfazer apaga. */
    int alcanceDaObra();

    int fundoDaObra();

    int tetoDaObra();

    Material cor(String chave, Material padrao);

    Material materialDaPlaca();

    int minimo();

    int maximo();

    int contagemSegundos();

    /**
     * Quantos segundos o jogador fica parado na arena antes de a rodada
     * abrir.
     *
     * Existe porque a largada põe o jogador num chão que em alguns jogos já
     * está sumindo: sem isso ele perde antes de ver onde está.
     */
    int preparoSegundos();

    int fimSegundos();

    /** Quantos segundos a rodada dura no máximo. */
    int tempoLimite();

    boolean treinoSozinho();

    String titulo();

    String corDoTexto();

    String subtitulo();

    List<String> regrasEscritas();

    double escalaTitulo();

    double escalaPlaca();

    int fundoDoTexto();

    String placa(String chave, String padrao);
}
