package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A praça de portais, conferida sem servidor.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * O hub é construído por um comando, de uma vez, com dezenas de milhares de
 * blocos. Erro de planta aqui não vira exceção: vira obra torta que só se
 * descobre andando nela, e desfazer e refazer custa minutos de servidor
 * parado. Tudo que dá para conferir na planta é conferido aqui.
 */
class HubTest {

    private static YamlConfiguration hub;

    @BeforeAll
    static void carregar() throws Exception {
        try (InputStream in = HubTest.class.getResourceAsStream("/hub.yml")) {
            assertNotNull(in, "hub.yml não foi empacotado");
            hub = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("toda escultura e um retangulo, linha por linha")
    void esculturasSaoRetangulares() {
        ConfigurationSection secao = hub.getConfigurationSection("esculturas");
        assertNotNull(secao, "hub.yml sem seção 'esculturas'");
        assertFalse(secao.getKeys(false).isEmpty(), "nenhuma escultura desenhada");

        for (String nome : secao.getKeys(false)) {
            List<String> desenho = secao.getStringList(nome);
            assertTrue(HubCatalog.desenhoEhRetangular(desenho),
                    "o desenho '" + nome + "' tem linhas de tamanhos diferentes — "
                            + "a escultura sai entortada e ninguém descobre o porquê");
        }
    }

    @Test
    @DisplayName("a escultura cabe no terraco em que ela e montada")
    void esculturaCabeNoTerraco() {
        int terraco = hub.getInt("praca.terraco-raio", 11);
        ConfigurationSection secao = hub.getConfigurationSection("esculturas");

        for (String nome : secao.getKeys(false)) {
            List<String> desenho = secao.getStringList(nome);
            int largura = desenho.get(0).length();
            assertTrue(largura <= terraco * 2,
                    "a escultura '" + nome + "' tem " + largura
                            + " de largura e o terraço só tem " + (terraco * 2));
            assertEquals(1, largura % 2,
                    "a escultura '" + nome + "' tem largura par e nasce fora do eixo");
        }
    }

    @Test
    @DisplayName("todo portal aponta para uma escultura que existe")
    void portalUsaEsculturaExistente() {
        ConfigurationSection secao = hub.getConfigurationSection("esculturas");

        for (Map<?, ?> portal : hub.getMapList("portais")) {
            Object escultura = portal.get("escultura");
            assertNotNull(escultura, "portal sem escultura: " + portal.get("id"));
            assertTrue(secao.contains(String.valueOf(escultura)),
                    "o portal " + portal.get("id") + " pede a escultura '"
                            + escultura + "', que não está desenhada");
        }
    }

    @Test
    @DisplayName("dois portais nunca saem pela mesma face")
    void facesNaoSeRepetem() {
        List<HubCatalog.Portal> portais = new ArrayList<>();
        for (Map<?, ?> item : hub.getMapList("portais")) {
            portais.add(new HubCatalog.Portal(
                    String.valueOf(item.get("id")),
                    HubCatalog.Face.valueOf(String.valueOf(item.get("face"))
                            .toUpperCase(Locale.ROOT)),
                    "", "", "", "", "#FFFFFF",
                    Material.QUARTZ_BLOCK, Material.QUARTZ_BLOCK, ""));
        }
        assertTrue(HubCatalog.facesSaoUnicas(portais),
                "dois portais na mesma face: um seria construído por cima do outro");
    }

    @Test
    @DisplayName("todo material do hub.yml existe de verdade")
    void materiaisExistem() {
        List<String> suspeitos = new ArrayList<>();

        ConfigurationSection paleta = hub.getConfigurationSection("paleta");
        for (String chave : paleta.getKeys(false)) {
            String nome = paleta.getString(chave, "");
            if (Material.matchMaterial(nome.toUpperCase(Locale.ROOT)) == null) {
                suspeitos.add("paleta." + chave + " = " + nome);
            }
        }

        for (Map<?, ?> portal : hub.getMapList("portais")) {
            for (String chave : List.of("moldura", "destaque")) {
                String nome = String.valueOf(portal.get(chave));
                if (Material.matchMaterial(nome.toUpperCase(Locale.ROOT)) == null) {
                    suspeitos.add(portal.get("id") + "." + chave + " = " + nome);
                }
            }
        }

        // Material errado não derruba a obra: ele cai no padrão e a praça
        // sai de outra cor, com um aviso no console que ninguém lê.
        assertTrue(suspeitos.isEmpty(), "material inexistente no hub.yml: " + suspeitos);
    }

    @Test
    @DisplayName("a caixa do portal pega quem entra no arco, nas quatro faces")
    void caixaDoPortalFuncionaEmTodasAsFaces() {
        int distancia = 33;
        int piso = 100;

        for (HubCatalog.Face face : HubCatalog.Face.values()) {
            double x = face.du() * distancia + 0.5;
            double z = face.dv() * distancia + 0.5;

            assertTrue(HubPortais.dentroDaCaixa(x, piso + 1, z, face, distancia, piso),
                    face + ": quem está no vão do arco não foi detectado");

            // Dois passos antes do arco, ainda na passarela: NÃO pode pegar,
            // senão o jogador é teleportado antes de decidir.
            assertFalse(HubPortais.dentroDaCaixa(
                            face.du() * (distancia - 4) + 0.5, piso + 1,
                            face.dv() * (distancia - 4) + 0.5, face, distancia, piso),
                    face + ": pegou o jogador ainda na passarela");

            // Do lado de fora do arco, passando ao largo.
            assertFalse(HubPortais.dentroDaCaixa(
                            x + face.lu() * 5, piso + 1, z + face.lv() * 5,
                            face, distancia, piso),
                    face + ": pegou quem passou ao lado do arco");

            // Voando por cima do arco.
            assertFalse(HubPortais.dentroDaCaixa(x, piso + 12, z, face, distancia, piso),
                    face + ": pegou quem voou por cima");
        }
    }

    @Test
    @DisplayName("a torre afina de baixo para cima, nunca ao contrario")
    void torreAfina() {
        int altura = 34;
        int anterior = Integer.MAX_VALUE;
        for (int y = 0; y <= altura; y++) {
            int raio = HubBuilder.raioDaTorre(y, altura);
            assertTrue(raio <= anterior, "a torre engordou na altura " + y);
            assertTrue(raio >= 1, "a torre sumiu na altura " + y);
            anterior = raio;
        }
    }

    @Test
    @DisplayName("texto do hub.yml nao vira formatacao no painel")
    void tituloNaoInjetaTag() {
        assertEquals("redeu", HubBuilder.escapar("<red>eu"));
        assertEquals("", HubBuilder.escapar(null));
        assertEquals("SURVIVAL", HubBuilder.escapar("SURVIVAL"));
    }

    @Test
    @DisplayName("a cor da particula sai do hexadecimal, e cor errada nao derruba nada")
    void corDaParticula() {
        assertEquals(org.bukkit.Color.fromRGB(0x5B, 0xE3, 0x7D), HubPortais.corDe("#5BE37D"));
        assertEquals(org.bukkit.Color.fromRGB(0x5B, 0xE3, 0x7D), HubPortais.corDe("5BE37D"));
        assertEquals(org.bukkit.Color.WHITE, HubPortais.corDe("laranja"));
        assertEquals(org.bukkit.Color.WHITE, HubPortais.corDe(null));
    }

    @Test
    @DisplayName("o resgate do void acontece acima do fundo do mundo")
    void resgateAconteceAntesDaMorte() {
        int resgate = hub.getInt("regras.resgate-y", 60);
        int piso = hub.getInt("praca.y", 100);

        // Abaixo de -64 o jogador já está tomando dano de void; o resgate
        // tem de ser bem antes disso, e bem abaixo do piso para não pescar
        // quem só pulou de uma passarela para outra.
        assertTrue(resgate > -64, "o resgate em y=" + resgate + " é tarde demais");
        assertTrue(resgate < piso - 10,
                "o resgate em y=" + resgate + " está perto demais do piso " + piso);
    }
}
