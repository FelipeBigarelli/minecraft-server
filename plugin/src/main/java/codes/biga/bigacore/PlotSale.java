package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Venda de terreno — o primeiro RALO recorrente de moeda do servidor.
 *
 * <h2>Por que isto existe</h2>
 *
 * A auditoria de 30/10/2026 encontrou uma economia só com torneira e nenhum
 * ralo: {@code chestshop-tax-percent}, {@code shop-creation-price} e
 * {@code shop-refund-price} estavam todos em 0, enquanto o
 * {@link ServerBuyback} injeta até {@code admin-buyback-daily-cap} por jogador
 * por dia. Entra moeda todo dia e não sai nada.
 *
 * O outro sink é a venda no varejo do Admin Shop, mas ele é limitado pelo que o
 * jogador precisa comprar. O terreno é diferente: é um bem caro, desejado e que
 * não volta pro mercado. É o ralo que escala junto com a riqueza.
 *
 * <h2>Por que a cobrança não acontece dentro do claim</h2>
 *
 * O CreativePlots 5.1 não tem evento de claim e o {@code PlotAPI} só expõe
 * consultas. Não dá para cancelar um claim nem para reivindicar por código.
 *
 * Então a ordem é: o {@link PlotPurchaseListener} confere o saldo ANTES e
 * cancela se faltar, deixa o comando do CreativePlots rodar, e só cobra depois
 * de confirmar pela {@link PlotApiBridge} que o jogador virou dono de verdade.
 *
 * O inverso — cobrar antes e torcer — cobraria por claim recusado (terreno já
 * tomado, limite de terrenos atingido, mundo errado). Debitar só contra fato
 * consumado é a única ordem que não cria cobrança fantasma.
 *
 * <h2>Preço escalonado</h2>
 *
 * A tabela vem de {@code policy.plot-prices} no economy.yml. O primeiro terreno
 * é o da primeira posição; além da tabela o preço continua subindo pelo mesmo
 * fator do último salto, para que o ralo nunca sature.
 */
public final class PlotSale {

    private static final String FILE_NAME = "plots.yml";

    /**
     * Teto duro do preço escalonado.
     *
     * Sem isto, a multiplicação repetida estoura o int e um terreno distante
     * sairia por um preço NEGATIVO — que o Vault aceitaria como depósito.
     */
    static final int PRECO_MAXIMO = 1_000_000_000;

    /** Usado só quando a tabela tem um único degrau e não dá para inferir o salto. */
    static final int FATOR_PADRAO = 3;

    private final BigaCore plugin;
    private YamlConfiguration dados;

    public PlotSale(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.dados = YamlConfiguration.loadConfiguration(arquivo());
    }

    private File arquivo() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    // ------------------------------------------------------------------
    //  Consulta
    // ------------------------------------------------------------------

    /** Quantos terrenos este jogador já pagou. */
    public int pagosPor(UUID jogador) {
        return dados.getInt("jogadores." + jogador + ".pagos", 0);
    }

    /** O que o jogador pagaria no próximo terreno. */
    public int precoProximo(UUID jogador) {
        return precoDoProximo(plugin.economyCatalog().plotPrices(), pagosPor(jogador));
    }

    /** A tabela inteira, para exibir ao jogador. */
    public List<Integer> tabela() {
        return plugin.economyCatalog().plotPrices();
    }

    public boolean habilitado() {
        return !tabela().isEmpty() && plugin.economyBridge().available();
    }

    // ------------------------------------------------------------------
    //  Aritmética do preço — estática de propósito
    // ------------------------------------------------------------------

    /**
     * Preço do próximo terreno, dado quantos o jogador já pagou.
     *
     * Estático e visível ao teste porque é a conta que define o tamanho do
     * ralo. Tabela vazia significa terreno de graça, que é como o servidor
     * funcionava antes desta classe existir.
     */
    static int precoDoProximo(List<Integer> tabela, int jaPagos) {
        if (tabela == null || tabela.isEmpty()) {
            return 0;
        }

        int pagos = Math.max(0, jaPagos);
        if (pagos < tabela.size()) {
            return Math.max(0, tabela.get(pagos));
        }

        long preco = Math.max(0, tabela.get(tabela.size() - 1));
        int fator = fatorDeEscalada(tabela);

        for (int degrau = tabela.size(); degrau <= pagos; degrau++) {
            preco *= fator;
            if (preco >= PRECO_MAXIMO) {
                return PRECO_MAXIMO;
            }
        }
        return (int) preco;
    }

    /**
     * Quanto o preço multiplica a cada terreno além da tabela.
     *
     * Lido do último salto que o dono escreveu, em vez de fixado no código: se
     * ele reescrever a tabela com outra curva, a extrapolação acompanha.
     */
    static int fatorDeEscalada(List<Integer> tabela) {
        if (tabela == null || tabela.size() < 2) {
            return FATOR_PADRAO;
        }
        int ultimo = tabela.get(tabela.size() - 1);
        int penultimo = tabela.get(tabela.size() - 2);
        if (penultimo <= 0 || ultimo <= penultimo) {
            return FATOR_PADRAO;
        }
        return Math.max(2, ultimo / penultimo);
    }

    // ------------------------------------------------------------------
    //  Cobrança
    // ------------------------------------------------------------------

    /**
     * Cobra o terreno recém-reivindicado.
     *
     * Só deve ser chamado depois de confirmar que o jogador virou dono. Se o
     * saque falhar, devolvemos a recusa para quem chamou decidir — este método
     * não desfaz o claim, porque desfazer exigiria uma API de unclaim que o
     * CreativePlots não expõe.
     */
    public Resultado cobrar(Player jogador, int preco) {
        if (preco <= 0) {
            registrarCompra(jogador.getUniqueId(), 0);
            return Resultado.gratuito(pagosPor(jogador.getUniqueId()));
        }

        if (!plugin.economyBridge().withdraw(jogador, preco)) {
            return Resultado.recusado(
                    "O saque falhou. O terreno é seu, mas avise o administrador — nada foi cobrado.");
        }

        registrarCompra(jogador.getUniqueId(), preco);

        plugin.getLogger().info("[terreno] " + jogador.getName() + " pagou " + preco
                + " (terrenos pagos: " + pagosPor(jogador.getUniqueId()) + ")");

        return Resultado.cobrado(preco, pagosPor(jogador.getUniqueId()),
                precoProximo(jogador.getUniqueId()));
    }

    // ------------------------------------------------------------------
    //  Persistência
    // ------------------------------------------------------------------

    /**
     * Guardamos a contagem aqui em vez de perguntar ao CreativePlots.
     *
     * O {@code PlotAPI} não sabe contar terrenos de um jogador — só responde
     * quem é o dono de um terreno específico. Varrer a grade inteira a cada
     * claim seria caro e daria a resposta errada de qualquer jeito: o que
     * define o degrau do preço é quantos o jogador COMPROU, não quantos ele
     * tem. Terreno ganho pelo admin não deve encarecer o próximo.
     */
    private void registrarCompra(UUID jogador, int preco) {
        String base = "jogadores." + jogador;
        dados.set(base + ".pagos", dados.getInt(base + ".pagos", 0) + 1);
        dados.set(base + ".total-gasto", dados.getInt(base + ".total-gasto", 0) + preco);
        salvar();
    }

    private void salvar() {
        try {
            File destino = arquivo();
            File pasta = destino.getParentFile();
            if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
                plugin.getLogger().warning("Não foi possível criar a pasta de " + FILE_NAME + ".");
                return;
            }
            dados.save(destino);
        } catch (IOException erro) {
            // Perder este arquivo faria todo mundo voltar ao preço do primeiro
            // terreno, então a falha precisa aparecer no console.
            plugin.getLogger().severe("Falha ao gravar " + FILE_NAME + ": " + erro.getMessage()
                    + " — a contagem de terrenos pagos pode reiniciar sozinha.");
        }
    }

    public record Resultado(boolean sucesso, String mensagem, int preco, int pagos, int proximo) {

        private static Resultado recusado(String mensagem) {
            return new Resultado(false, mensagem, 0, 0, 0);
        }

        private static Resultado cobrado(int preco, int pagos, int proximo) {
            return new Resultado(true, "", preco, pagos, proximo);
        }

        private static Resultado gratuito(int pagos) {
            return new Resultado(true, "", 0, pagos, 0);
        }
    }
}
