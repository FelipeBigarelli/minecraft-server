package codes.biga.bigacore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Le a politica economica versionada em economy.yml.
 *
 * O catalogo e deliberadamente somente leitura: ChestShop continua sendo o
 * mercado real e os jogadores continuam livres para descobrir seus precos.
 */
public final class EconomyCatalog {

    private final BigaCore plugin;
    private YamlConfiguration config;

    public EconomyCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File arquivo = new File(plugin.getDataFolder(), "economy.yml");
        this.config = YamlConfiguration.loadConfiguration(arquivo);
    }

    public String currencyName() {
        return config.getString("currency.name", "Biga");
    }

    public String currencySymbol() {
        return config.getString("currency.symbol", "B$");
    }

    public int startingBalance() {
        return config.getInt("currency.starting-balance", 250);
    }

    public int chestShopTaxPercent() {
        return config.getInt("policy.chestshop-tax-percent", 4);
    }

    public int shopCreationPrice() {
        return config.getInt("policy.shop-creation-price", 50);
    }

    public int shopRefundPrice() {
        return config.getInt("policy.shop-refund-price", 10);
    }

    public int initialShopLimit() {
        return config.getInt("policy.player-shop-first-limit", 6);
    }

    public int adminBuybackDailyCap() {
        return config.getInt("policy.admin-buyback-daily-cap", 150);
    }

    public int adminBuybackWeeklyCap() {
        return config.getInt("policy.admin-buyback-weekly-cap", 900);
    }

    public boolean adminBuybackEnabledAtLaunch() {
        return config.getBoolean("policy.admin-buyback-enabled-at-launch", false);
    }

    public Optional<PriceEntry> find(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String key = normalize(input);
        ConfigurationSection section = config.getConfigurationSection("catalog." + key);
        if (section == null) {
            return Optional.empty();
        }

        String tier = section.getString("tier", "COMMON");
        int lot = section.getInt("lot", 1);
        int p2p = section.getInt("p2p", 0);
        Integer serverBuy = optionalInt(section, "server-buy");
        Integer serverSell = optionalInt(section, "server-sell");

        return Optional.of(new PriceEntry(key, tier, lot, p2p, serverBuy, serverSell));
    }

    public List<String> keys() {
        ConfigurationSection catalog = config.getConfigurationSection("catalog");
        if (catalog == null) {
            return List.of();
        }

        List<String> keys = new ArrayList<>(catalog.getKeys(false));
        Collections.sort(keys);
        return List.copyOf(keys);
    }

    public String money(Number value) {
        return currencySymbol() + " " + value;
    }

    private static Integer optionalInt(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            return null;
        }
        return section.getInt(path);
    }

    private static String normalize(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return normalized.replace(' ', '_');
    }

    public record PriceEntry(
            String key,
            String tier,
            int lot,
            int p2p,
            Integer serverBuy,
            Integer serverSell
    ) {
    }
}
