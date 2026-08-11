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
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            return false;
        }
    }
}
