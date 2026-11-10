package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Le o encomendas.yml e responde as perguntas de regra.
 *
 * Separado do {@link EncomendaLivro} pelo mesmo motivo que o
 * {@link TerrenoCatalog} e separado das regioes: regra e dado tem ciclo de
 * vida diferente. A regra e escrita pelo dono do servidor e quase nunca muda;
 * o dado muda a cada pedido.
 */
public final class EncomendaCatalog {

    static final String ARQUIVO = "encomendas.yml";

    private final BigaCore plugin;
    private YamlConfiguration dados;

    public EncomendaCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.dados = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), ARQUIVO));
    }

    public boolean ativo() {
        return dados.getBoolean("ativo", true);
    }

    public int limitePorJogador() {
        return Math.max(1, dados.getInt("limite-por-jogador", 5));
    }

    public long precoMinimo() {
        return Math.max(1L, dados.getLong("preco-minimo", 10L));
    }

    public long precoMaximo() {
        return Math.max(precoMinimo(), dados.getLong("preco-maximo", 100000L));
    }

    public int quantidadeMaxima() {
        return Math.max(1, dados.getInt("quantidade-maxima", 1728));
    }

    /**
     * Por que uma encomenda foi recusada.
     *
     * Enum em vez de boolean porque a mensagem para o jogador precisa dizer
     * QUAL limite ele furou. "Nao deu" manda a pessoa adivinhar.
     */
    public enum Recusa {
        OK,
        PRECO_BAIXO,
        PRECO_ALTO,
        QUANTIDADE_BAIXA,
        QUANTIDADE_ALTA,
        LIMITE_DE_PEDIDOS
    }

    public Recusa validar(long preco, int quantidade, int abertasDoJogador) {
        return validar(preco, quantidade, abertasDoJogador,
                precoMinimo(), precoMaximo(), quantidadeMaxima(), limitePorJogador());
    }

    /**
     * A regra pura, sem servidor nem arquivo no caminho.
     *
     * Static e com todos os limites como parametro de proposito: e o que
     * permite testar a tabela inteira de casos sem subir um Paper. A versao de
     * instancia acima e so um atalho que busca os limites no YAML.
     */
    static Recusa validar(long preco, int quantidade, int abertasDoJogador,
                          long precoMinimo, long precoMaximo,
                          int quantidadeMaxima, int limitePorJogador) {

        if (quantidade < 1) {
            return Recusa.QUANTIDADE_BAIXA;
        }
        if (quantidade > quantidadeMaxima) {
            return Recusa.QUANTIDADE_ALTA;
        }
        if (preco < precoMinimo) {
            return Recusa.PRECO_BAIXO;
        }
        if (preco > precoMaximo) {
            return Recusa.PRECO_ALTO;
        }
        if (abertasDoJogador >= limitePorJogador) {
            return Recusa.LIMITE_DE_PEDIDOS;
        }
        return Recusa.OK;
    }
}
