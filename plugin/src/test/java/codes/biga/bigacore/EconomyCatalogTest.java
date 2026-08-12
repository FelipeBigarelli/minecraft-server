package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão do catálogo econômico.
 *
 * <h2>Por que este teste existe</h2>
 *
 * O catálogo é indexado pelo nome do {@link Material}: quem segura um item e
 * roda /biga eco vender faz o BigaCore procurar {@code material.name()} em
 * minúsculas. Uma chave que não corresponde a nenhum Material não dá erro —
 * ela simplesmente nunca é encontrada, e o servidor responde "não compro isso"
 * para um item que estava na tabela de preços o tempo todo.
 *
 * Foi o que aconteceu com {@code nether_quartz}: o Material é {@code QUARTZ}.
 * O preço existia, tinha server-buy, e mesmo assim era inalcançável.
 *
 * O teste lê o economy.yml empacotado, sem servidor.
 */
class EconomyCatalogTest {

    private static YamlConfiguration economia;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = EconomyCatalogTest.class.getResourceAsStream("/economy.yml")) {
            assertNotNull(in, "economy.yml não foi empacotado");
            economia = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection catalogo() {
        ConfigurationSection secao = economia.getConfigurationSection("catalog");
        assertNotNull(secao, "economy.yml sem seção catalog");
        return secao;
    }

    @Test
    void todaChaveDoCatalogoEUmMaterialReal() {
        List<String> invalidas = new ArrayList<>();

        for (String chave : catalogo().getKeys(false)) {
            if (Material.matchMaterial(chave) == null) {
                invalidas.add(chave);
            }
        }

        assertTrue(invalidas.isEmpty(),
                "chaves que nenhum item do jogo casa (preço inalcançável): " + invalidas);
    }

    @Test
    void todoItemTemLoteEPrecoP2PPositivos() {
        for (String chave : catalogo().getKeys(false)) {
            ConfigurationSection item = catalogo().getConfigurationSection(chave);
            assertNotNull(item, chave + " não é uma seção");

            assertTrue(item.getInt("lot", 0) > 0, chave + " está sem lote válido");
            assertTrue(item.getInt("p2p", 0) > 0, chave + " está sem referência p2p");
        }
    }

    @Test
    void servidorNuncaCompraMaisCaroDoQueVende() {
        // Se server-buy passasse de server-sell, dava para comprar do servidor
        // e revender ao servidor num laço infinito de lucro.
        for (String chave : catalogo().getKeys(false)) {
            ConfigurationSection item = catalogo().getConfigurationSection(chave);
            if (item == null || !item.contains("server-buy") || !item.contains("server-sell")) {
                continue;
            }

            int compra = item.getInt("server-buy");
            int venda = item.getInt("server-sell");
            assertTrue(compra < venda,
                    chave + ": servidor compra por " + compra + " e vende por " + venda
                            + " — isso é lucro infinito");
        }
    }

    @Test
    void paredeDaLojaSoUsaItemVendavel() {
        List<String> problemas = new ArrayList<>();

        for (String chave : economia.getStringList("admin-shop.wall")) {
            ConfigurationSection item = catalogo().getConfigurationSection(chave);
            if (item == null) {
                problemas.add(chave + " (fora do catálogo)");
                continue;
            }
            if (!item.contains("server-sell")) {
                problemas.add(chave + " (sem server-sell)");
            }
        }

        assertTrue(problemas.isEmpty(), "parede do Admin Shop inconsistente: " + problemas);
    }

    @Test
    void paredeCabeNasVagasDisponiveis() {
        int vagas = SpawnShopBuilder.adminShopSlotCount();
        int pedidos = economia.getStringList("admin-shop.wall").size();

        assertTrue(pedidos <= vagas,
                "a parede tem " + vagas + " vagas e o economy.yml pede " + pedidos);
    }

    @Test
    void servidorNaoVendeItemDeProgressao() {
        // Regra do ECONOMIA.md: progressão é conquistada ou negociada entre
        // jogadores, nunca comprada do servidor.
        List<String> proibidos = List.of(
                "elytra", "netherite_ingot", "netherite_scrap", "ancient_debris",
                "totem_of_undying", "shulker_shell", "nether_star", "beacon",
                "mace", "heavy_core", "dragon_egg", "trident", "diamond");

        List<String> vazando = new ArrayList<>();
        for (String chave : economia.getStringList("admin-shop.wall")) {
            if (proibidos.contains(chave.toLowerCase(Locale.ROOT))) {
                vazando.add(chave);
            }
        }

        assertTrue(vazando.isEmpty(),
                "item de progressão na loja do servidor: " + vazando);
    }

    @Test
    void placaDeDuasViasTemSpreadQueDesencorajaArbitragem() {
        // Com a placa comprando e vendendo no mesmo lugar, um spread pequeno
        // vira máquina de lavar: compra 64, vende 64, repete. O servidor
        // precisa comprar bem mais barato do que vende.
        if (!economia.getBoolean("admin-shop.placa-compra", false)) {
            return;
        }

        List<String> apertados = new ArrayList<>();
        for (String chave : economia.getStringList("admin-shop.wall")) {
            ConfigurationSection item = catalogo().getConfigurationSection(chave);
            if (item == null || !item.contains("server-buy")) {
                continue;
            }
            double compra = item.getInt("server-buy");
            double venda = item.getInt("server-sell");
            if (compra > venda * 0.6) {
                apertados.add(chave + " (compra " + (int) compra + " / venda " + (int) venda + ")");
            }
        }

        assertTrue(apertados.isEmpty(),
                "spread abaixo de 40% permite arbitragem no balcão: " + apertados);
    }

    @Test
    void tetoDiarioDeRecompraExisteEEhMenorQueOSemanal() {
        int diario = economia.getInt("policy.admin-buyback-daily-cap", 0);
        int semanal = economia.getInt("policy.admin-buyback-weekly-cap", 0);

        assertTrue(diario > 0, "sem teto diário, a recompra vira impressora de dinheiro");
        assertTrue(semanal >= diario, "teto semanal menor que o diário não faz sentido");
        assertFalse(economia.getStringList("admin-shop.wall").isEmpty(),
                "admin-shop ligado mas sem nenhum item na parede");
    }
}
