package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Recompra do servidor — o único lugar do BigaCore que cria moeda.
 *
 * <h2>Por que existe um teto</h2>
 *
 * A Biga Market virou loja do servidor a pedido do dono. O lado da VENDA
 * (servidor vende, jogador paga) é seguro: tira moeda de circulação, é um sink
 * puro, e roda em placas ChestShop comuns.
 *
 * O lado da COMPRA é o perigoso. Em Minecraft quase tudo é automatizável: uma
 * farm de cobblestone ligada a noite inteira transforma uma recompra ilimitada
 * numa impressora de dinheiro, e o mercado entre jogadores morre porque ninguém
 * precisa negociar com ninguém.
 *
 * Por isso a recompra NÃO usa placa de ChestShop — o ChestShop não sabe aplicar
 * teto por jogador. Ela passa por aqui, onde os limites que já estavam escritos
 * no economy.yml (admin-buyback-daily-cap / weekly-cap) finalmente valem de
 * verdade.
 *
 * A vazão máxima de moeda nova no servidor passa a ser previsível:
 * <pre>jogadores × teto diário</pre>
 * contra os sinks de 4% de imposto, 50 B$ por loja criada e a venda no varejo.
 */
public final class ServerBuyback {

    private static final String FILE_NAME = "buyback.yml";

    private final BigaCore plugin;
    private YamlConfiguration dados;

    public ServerBuyback(BigaCore plugin) {
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

    /**
     * Registra uma venda feita FORA do /biga eco vender.
     *
     * As placas do Admin Shop passam pelo ChestShop, não por este comando, mas
     * o teto é o mesmo bolso: sem contabilizar aqui, o jogador teria dois tetos
     * independentes e o limite diário valeria o dobro.
     */
    public void registrarVendaExterna(Player jogador, int valor) {
        if (valor <= 0) {
            return;
        }
        registrar(jogador.getUniqueId(), valor);
    }

    /** Quanto o jogador ainda pode receber hoje, já cruzando o teto semanal. */
    public int restanteHoje(Player jogador) {
        EconomyCatalog catalog = plugin.economyCatalog();
        int tetoDia = catalog.adminBuybackDailyCap();
        int tetoSemana = catalog.adminBuybackWeeklyCap();

        int usadoDia = usado(jogador.getUniqueId(), "dia", hoje());
        int usadoSemana = usado(jogador.getUniqueId(), "semana", semana());

        int sobraDia = Math.max(0, tetoDia - usadoDia);
        int sobraSemana = Math.max(0, tetoSemana - usadoSemana);
        return Math.min(sobraDia, sobraSemana);
    }

    public int tetoDiario() {
        return plugin.economyCatalog().adminBuybackDailyCap();
    }

    // ------------------------------------------------------------------
    //  Venda
    // ------------------------------------------------------------------

    /**
     * Compra do jogador o item que está na mão.
     *
     * Vende só a mão, nunca o inventário inteiro: o projeto decidiu não ter
     * /sellall, e um comando que esvazia a mochila é a mesma coisa com outro
     * nome.
     */
    public Resultado comprar(Player jogador, int pedido) {
        if (!plugin.economyCatalog().adminBuybackEnabled()) {
            return Resultado.recusado("A recompra do servidor está desligada.");
        }
        if (!plugin.economyBridge().available()) {
            return Resultado.recusado("Economia indisponível. Confirme VaultUnlocked + EternalEconomy.");
        }

        ItemStack mao = jogador.getInventory().getItemInMainHand();
        if (mao.getType().isAir() || mao.getAmount() <= 0) {
            return Resultado.recusado("Segure na mão o item que quer vender.");
        }
        if (temDadosExtras(mao)) {
            return Resultado.recusado(
                    "Itens com encantamento, nome ou dano não têm preço fixo. Venda no mercado entre jogadores.");
        }

        String chave = mao.getType().name().toLowerCase(Locale.ROOT);
        Optional<EconomyCatalog.PriceEntry> encontrado = plugin.economyCatalog().find(chave);
        if (encontrado.isEmpty() || encontrado.get().serverBuy() == null) {
            return Resultado.recusado(
                    "O servidor não compra " + chave + ". Esse item fica no mercado entre jogadores.");
        }

        EconomyCatalog.PriceEntry entrada = encontrado.get();
        int disponivel = Math.min(pedido, mao.getAmount());
        if (disponivel <= 0) {
            return Resultado.recusado("Quantidade inválida.");
        }

        int restante = restanteHoje(jogador);
        if (restante <= 0) {
            return Resultado.recusado(
                    "Você já atingiu o teto de recompra. Ele reinicia amanhã — até lá, venda para outros jogadores.");
        }

        // Preço é por LOTE. Divisão inteira de propósito: nunca paga mais do
        // que a tabela, e sobra fica com o jogador em vez de virar moeda nova.
        int lote = Math.max(1, entrada.lot());
        int precoLote = entrada.serverBuy();

        int quantidade = Math.min(disponivel, quantidadeQueCabeNoTeto(restante, lote, precoLote));
        int pagamento = pagamentoPor(quantidade, lote, precoLote);

        if (quantidade <= 0 || pagamento <= 0) {
            int minimo = (int) Math.ceil((double) lote / precoLote);
            return Resultado.recusado(
                    "Quantidade pequena demais para pagar 1 " + plugin.economyCatalog().currencySymbol()
                            + ". O lote de referência é " + lote + " (mínimo ~" + minimo + ").");
        }

        // Debita o item ANTES de creditar: se o depósito falhar, devolvemos.
        // O caminho inverso criaria item e dinheiro ao mesmo tempo.
        ItemStack cobrado = mao.clone();
        cobrado.setAmount(mao.getAmount() - quantidade);
        if (cobrado.getAmount() <= 0) {
            jogador.getInventory().setItemInMainHand(null);
        } else {
            jogador.getInventory().setItemInMainHand(cobrado);
        }

        if (!plugin.economyBridge().deposit(jogador, pagamento)) {
            jogador.getInventory().setItemInMainHand(mao);
            return Resultado.recusado("O depósito falhou. Nada foi vendido.");
        }

        registrar(jogador.getUniqueId(), pagamento);

        plugin.getLogger().info("[buyback] " + jogador.getName() + " vendeu " + quantidade
                + "x " + chave + " por " + pagamento + " (restante hoje: "
                + restanteHoje(jogador) + ")");

        return Resultado.aceito(quantidade, chave, pagamento, restanteHoje(jogador));
    }

    /**
     * Quantos itens cabem no que sobrou do teto.
     *
     * Estático e visível ao teste de propósito: é a aritmética que decide
     * quanta moeda nova entra no servidor, e um erro aqui não aparece em
     * nenhum lugar até a economia já estar inflacionada.
     */
    static int quantidadeQueCabeNoTeto(int restante, int lote, int precoLote) {
        if (precoLote <= 0 || lote <= 0 || restante <= 0) {
            return 0;
        }
        // pagamento = precoLote * q / lote  <=  restante
        return (int) Math.min(Integer.MAX_VALUE, ((long) restante * lote) / precoLote);
    }

    /** Pagamento por {@code quantidade} itens, dado o preço do lote. */
    static int pagamentoPor(int quantidade, int lote, int precoLote) {
        if (quantidade <= 0 || lote <= 0 || precoLote <= 0) {
            return 0;
        }
        return (int) (((long) precoLote * quantidade) / lote);
    }

    /**
     * Item "puro" ou não.
     *
     * Uma espada encantada e uma espada comum são o mesmo Material. Pagar preço
     * de tabela por item encantado seria vender Mending por preço de ferro.
     */
    private boolean temDadosExtras(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
            return true;
        }
        return meta instanceof org.bukkit.inventory.meta.Damageable damageable
                && damageable.hasDamage();
    }

    // ------------------------------------------------------------------
    //  Persistência dos tetos
    // ------------------------------------------------------------------

    private void registrar(UUID jogador, int valor) {
        acumular(jogador, "dia", hoje(), valor);
        acumular(jogador, "semana", semana(), valor);
        salvar();
    }

    private void acumular(UUID jogador, String escopo, String periodo, int valor) {
        String base = "jogadores." + jogador + "." + escopo;
        int atual = usado(jogador, escopo, periodo);
        dados.set(base + ".periodo", periodo);
        dados.set(base + ".total", atual + valor);
    }

    /** Total gasto no período corrente; zera sozinho quando o período vira. */
    private int usado(UUID jogador, String escopo, String periodo) {
        String base = "jogadores." + jogador + "." + escopo;
        String gravado = dados.getString(base + ".periodo", "");
        if (!periodo.equals(gravado)) {
            return 0;
        }
        return dados.getInt(base + ".total", 0);
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
            // Falhar em silêncio aqui deixaria o teto sem memória entre reinícios,
            // então isso precisa aparecer no console.
            plugin.getLogger().severe("Falha ao gravar " + FILE_NAME + ": " + erro.getMessage()
                    + " — o teto de recompra pode reiniciar sozinho.");
        }
    }

    private static String hoje() {
        return LocalDate.now().toString();
    }

    private static String semana() {
        LocalDate agora = LocalDate.now();
        WeekFields iso = WeekFields.ISO;
        return agora.get(iso.weekBasedYear()) + "-W" + agora.get(iso.weekOfWeekBasedYear());
    }

    public record Resultado(
            boolean sucesso,
            String mensagem,
            int quantidade,
            String item,
            int pagamento,
            int restanteHoje
    ) {
        private static Resultado recusado(String mensagem) {
            return new Resultado(false, mensagem, 0, "", 0, 0);
        }

        private static Resultado aceito(int quantidade, String item, int pagamento, int restante) {
            return new Resultado(true, "", quantidade, item, pagamento, restante);
        }
    }
}
