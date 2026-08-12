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

    // ------------------------------------------------------------------
    //  Biga Market como loja do servidor
    // ------------------------------------------------------------------

    public boolean adminShopEnabled() {
        return config.getBoolean("admin-shop.enabled", false);
    }

    /** Recompra ligada. Separado de adminShopEnabled: dá para vender sem comprar. */
    public boolean adminBuybackEnabled() {
        return config.getBoolean("admin-shop.buyback-enabled", false);
    }

    /**
     * Nome do dono nas placas.
     *
     * Precisa bater com ADMIN_SHOP_NAME do ChestShop. Se divergir, a placa vira
     * loja de um jogador inexistente e nenhuma compra funciona.
     */
    public String adminShopSignOwner() {
        return config.getString("admin-shop.sign-owner", "Admin Shop");
    }

    /** A placa do balcão também compra do jogador. */
    public boolean adminShopSignBuysBack() {
        return config.getBoolean("admin-shop.placa-compra", false);
    }

    /** Usa nomes em português na linha do item da placa. */
    public boolean adminShopPortugueseNames() {
        return config.getBoolean("admin-shop.nomes-em-portugues", false);
    }

    /**
     * Nome em português do item, ou a própria chave quando não houver.
     *
     * Cai para a chave de propósito: um item sem tradução aparece em inglês,
     * mas a loja continua funcionando.
     */
    public String displayName(String key) {
        return config.getString("nomes." + key, key.toUpperCase(Locale.ROOT));
    }

    /** Todos os pares chave -> nome em português, para gerar o itemAliases.yml. */
    public java.util.Map<String, String> allDisplayNames() {
        ConfigurationSection nomes = config.getConfigurationSection("nomes");
        if (nomes == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> mapa = new java.util.LinkedHashMap<>();
        for (String chave : nomes.getKeys(false)) {
            String valor = nomes.getString(chave);
            if (valor != null && !valor.isBlank()) {
                mapa.put(chave, valor);
            }
        }
        return java.util.Map.copyOf(mapa);
    }

    /** Itens da parede de venda, já filtrados: só entra quem tem server-sell. */
    public List<PriceEntry> adminShopWall(int maxSlots) {
        List<PriceEntry> vitrine = new ArrayList<>();

        for (String chave : config.getStringList("admin-shop.wall")) {
            Optional<PriceEntry> entrada = find(chave);
            if (entrada.isEmpty()) {
                plugin.getLogger().warning(
                        "[admin-shop] '" + chave + "' não está no catalog e foi ignorado.");
                continue;
            }
            if (entrada.get().serverSell() == null) {
                plugin.getLogger().warning(
                        "[admin-shop] '" + chave + "' não tem server-sell e foi ignorado.");
                continue;
            }
            if (vitrine.size() >= maxSlots) {
                plugin.getLogger().warning("[admin-shop] A parede tem " + maxSlots
                        + " vagas; '" + chave + "' e os seguintes ficaram de fora.");
                break;
            }
            vitrine.add(entrada.get());
        }

        return List.copyOf(vitrine);
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
