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
     * Único ponto do BigaCore que CRIA moeda. Todo caminho até aqui precisa
     * passar pelo teto diário do {@link ServerBuyback} — é o que separa uma
     * recompra controlada de uma impressora de dinheiro ligada a uma farm AFK.
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
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return false;
        }
    }
}
