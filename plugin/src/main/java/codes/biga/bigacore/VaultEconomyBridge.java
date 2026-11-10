package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.OptionalDouble;

/**
 * Ponte pequena e isolada para o contrato Vault.
 *
 * O BigaCore nao precisa empacotar outra copia da API Vault: em runtime a
 * classe vem do VaultUnlocked. Isso evita acoplar o build a uma biblioteca
 * que o servidor ja fornece e mantem a integracao opcional.
 */
public final class VaultEconomyBridge {

    private Object provider;
    private Method getBalance;
    private Method depositPlayer;
    private Method withdrawPlayer;

    public boolean available() {
        if (provider != null && getBalance != null) {
            return true;
        }
        return discover();
    }

    public OptionalDouble balance(OfflinePlayer player) {
        if (!available()) {
            return OptionalDouble.empty();
        }

        try {
            Object result = getBalance.invoke(provider, player);
            if (result instanceof Number number) {
                return OptionalDouble.of(number.doubleValue());
            }
            return OptionalDouble.empty();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Credita o jogador.
     *
     * Único ponto do BigaCore que CRIA moeda.
     *
     * Dois caminhos chegam aqui, e só um precisa de teto:
     *
     *  - {@link ServerBuyback}: passa pelo teto diário. Sem ele, uma farm AFK
     *    de qualquer item renovável vira impressora de dinheiro, porque o
     *    ciclo nunca fecha — o item volta a existir de graça.
     *  - {@link TerrenoVenda} no reembolso: NÃO passa por teto, de propósito.
     *    O valor é limitado pelo que o jogador pagou antes e é sempre menor
     *    (o reembolso sai do preço base, sem o multiplicador de quantidade),
     *    então comprar e vender em sequência dá prejuízo garantido. Não existe
     *    ciclo com lucro para automatizar, então não há o que capear.
     *
     *  - {@link EncomendaCommand}: NÃO passa por teto, e aqui está o porquê.
     *    Todo depósito de encomenda é precedido de um saque do MESMO valor na
     *    conta de quem pediu, no momento em que o pedido foi criado. O
     *    dinheiro não nasce: ele troca de dono. Entregar o próprio pedido é
     *    recusado, então também não existe ciclo de uma pessoa só.
     *
     * Antes de abrir o quarto caminho, a pergunta é essa: o jogador consegue
     * repetir isso de graça? Se consegue, precisa de teto.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0 || !available() || depositPlayer == null) {
            return false;
        }

        try {
            Object resposta = depositPlayer.invoke(provider, player, amount);
            return transacaoDeuCerto(resposta);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    /**
     * Debita o jogador.
     *
     * Contraparte do {@link #deposit}: é por aqui que a moeda SAI de
     * circulação. Usam a {@link PlotSale} (terreno no criativo) e a
     * {@link TerrenoVenda} (terreno no survival) — os dois ralos recorrentes
     * que o servidor tem.
     *
     * Devolve false quando o saque não acontece — inclusive quando o saldo não
     * cobre. Quem chama precisa tratar isso como "não cobrado", nunca como
     * "cobrado parcialmente": o Vault não faz débito parcial, e assumir que fez
     * entregaria o bem sem receber.
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0 || !available() || withdrawPlayer == null) {
            return false;
        }

        try {
            Object resposta = withdrawPlayer.invoke(provider, player, amount);
            return transacaoDeuCerto(resposta);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    /**
     * O Vault devolve um EconomyResponse. Como não compilamos contra o Vault,
     * o sucesso é lido por reflexão; se o formato mudar, o padrão é considerar
     * que NÃO deu certo, para nunca dar item por dinheiro que não entrou.
     */
    private boolean transacaoDeuCerto(Object resposta) {
        if (resposta == null) {
            return false;
        }

        try {
            Method sucesso = resposta.getClass().getMethod("transactionSuccess");
            Object valor = sucesso.invoke(resposta);
            return valor instanceof Boolean marcado && marcado;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean discover() {
        try {
            Class economyType = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider registration = Bukkit.getServicesManager()
                    .getRegistration(economyType);

            if (registration == null || registration.getProvider() == null) {
                return false;
            }

            this.provider = registration.getProvider();
            this.getBalance = economyType.getMethod("getBalance", OfflinePlayer.class);
            this.depositPlayer = economyType.getMethod(
                    "depositPlayer", OfflinePlayer.class, double.class);
            this.withdrawPlayer = economyType.getMethod(
                    "withdrawPlayer", OfflinePlayer.class, double.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return false;
        }
    }
}
