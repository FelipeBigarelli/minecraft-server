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
    @DisplayName("a escultura e a parede dela cabem no terraco, na distancia em que ficam")
    void esculturaCabeNoTerraco() {
        int terraco = hub.getInt("praca.terraco-raio", 15);
        int avancoDoPortao = hub.getInt("praca.portal-avanco", 5);
        int avancoDaPeca = hub.getInt("portao.escultura-avanco", 6);

        // O terraço é um quadrado de cantos arredondados, e a parede fica
        // longe do meio dele. Medir só contra o diâmetro dava "cabe" para uma
        // parede cujo canto, na verdade, nasce no void — e no void não dá
        // erro, só fica um buraco que ninguém vê de cima.
        int doCentro = avancoDoPortao + avancoDaPeca + 2;
        int disponivel = HubBuilder.meiaLarguraEm(doCentro, terraco, 4);

        ConfigurationSection secao = hub.getConfigurationSection("esculturas");
        for (String nome : secao.getKeys(false)) {
            List<String> desenho = secao.getStringList(nome);
            int largura = desenho.get(0).length();
            assertEquals(1, largura % 2,
                    "a escultura '" + nome + "' tem largura par e nasce fora do eixo");

            // A parede de fundo passa dois blocos de cada lado do desenho.
            int meia = largura / 2 + 2;
            assertTrue(meia <= disponivel,
                    "a parede da escultura '" + nome + "' precisa de " + meia
                            + " blocos para cada lado e o terraço só dá " + disponivel
                            + " a " + doCentro + " do meio");
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
    @DisplayName("a plataforma afina para baixo, degrau por degrau")
    void plataformaAfina() {
        int raio = hub.getInt("praca.raio", 21);
        int anterior = Integer.MAX_VALUE;

        for (int k = 0; k < HubBuilder.DEGRAUS.length; k++) {
            int r = HubBuilder.raioDaCamada(k, raio);
            assertTrue(r < anterior, "a camada " + k + " engordou em vez de encolher");
            anterior = r;
        }
        assertEquals(raio, HubBuilder.raioDaCamada(0, raio),
                "a primeira camada tem de ter a largura do piso");
        assertEquals(-1, HubBuilder.raioDaCamada(HubBuilder.DEGRAUS.length, raio),
                "camada inexistente tem de avisar, não devolver raio");
    }

    @Test
    @DisplayName("o arco sobe ate o anel e nunca abre no caminho de volta")
    void arcoChegaNoAnel() {
        int altura = hub.getInt("praca.anel-altura", 24);
        int raioAnel = hub.getInt("praca.anel-raio", 8);
        int raioBase = hub.getInt("praca.raio", 21) - 1;

        double anterior = Double.MAX_VALUE;
        for (int y = 1; y <= altura; y++) {
            double r = HubBuilder.raioDoArco(y, altura, raioAnel, raioBase);
            assertTrue(r <= anterior + 1.0e-9,
                    "o arco abriu para fora na altura " + y);
            anterior = r;
        }

        assertEquals(raioBase, HubBuilder.raioDoArco(1, altura, raioAnel, raioBase), 1.0e-6,
                "o pé do arco tem de nascer na praça");
        assertEquals(raioAnel, HubBuilder.raioDoArco(altura, altura, raioAnel, raioBase), 1.0e-6,
                "o topo do arco tem de encostar no anel, senão ele fica solto no ar");
    }

    @Test
    @DisplayName("o pe do arco nao cai em cima de nenhuma passarela")
    void arcoNaoFechaCaminho() {
        int raioBase = hub.getInt("praca.raio", 21) - 1;
        int meia = hub.getInt("praca.passarela-largura", 9) / 2;

        // O arco nasce na diagonal. Um bloco de cada lado por causa da seção
        // 3x3, senão o pé encosta na boca da passarela sem o teste ver.
        int napDiagonal = (int) Math.round(raioBase / Math.sqrt(2.0)) - 1;
        assertTrue(napDiagonal > meia,
                "o pé do arco fica a " + napDiagonal + " do eixo e a passarela "
                        + "ocupa até " + meia + " — ele nasceria no meio do caminho");
    }

    @Test
    @DisplayName("o monumento acompanha o tamanho do paredao e cabe na praca")
    void monumentoCabeNaPraca() {
        int raio = hub.getInt("praca.raio", 21);
        int minimo = hub.getInt("monumento.meia-largura-minima", 11);

        // Paredão pequeno: vale o mínimo do hub.yml.
        assertEquals(minimo, HubBuilder.meiaLarguraDoMonumento(4.0, minimo));
        // Paredão grande: a pedra cresce com ele, com folga dos dois lados.
        assertEquals(12, HubBuilder.meiaLarguraDoMonumento(20.0, minimo));
        assertEquals(17, HubBuilder.alturaDoMonumento(13.2, 14));

        // E o plinto dele ainda tem de deixar gente passar dos dois lados.
        int plinto = HubBuilder.meiaLarguraDoMonumento(20.0, minimo) + 3;
        assertTrue(raio - plinto >= 5,
                "sobram só " + (raio - plinto) + " blocos entre o plinto e a borda "
                        + "da praça: o caminho do norte fica intransitável");
    }

    @Test
    @DisplayName("o anel passa por cima do monumento, sem encostar nele")
    void anelPassaPorCimaDoMonumento() {
        int anel = hub.getInt("praca.anel-altura", 24);
        int altura = hub.getInt("monumento.altura-minima", 14);

        assertTrue(anel >= altura + 6,
                "o anel em " + anel + " raspa no monumento de " + altura
                        + " — e o feixe do farol sobe exatamente por ali");
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
    @DisplayName("Speed 3 no hub.yml vira amplificador 2 no jogo")
    void velocidadeViraAmplificador() {
        // Trocar nivel por amplificador deixaria o hub um nivel mais lento do
        // que o arquivo diz, calado. Ninguem abre a doc do Bukkit por causa
        // de "ta meio devagar".
        assertEquals(2, RegrasDoHub.amplificador(3));
        assertEquals(0, RegrasDoHub.amplificador(1));
        assertEquals(0, RegrasDoHub.amplificador(0), "nivel invalido nao pode virar negativo");
        assertEquals(9, RegrasDoHub.amplificador(99), "amplificador tem teto");

        assertTrue(RegrasDoHub.velocidadeLigada(hub.getInt("regras.velocidade", 3)));
        assertFalse(RegrasDoHub.velocidadeLigada(0), "zero tem de desligar a regra");
    }

    @Test
    @DisplayName("o que o admin poe a mao continua nascendo no hub")
    void spawnDeAdminPassa() {
        // Barrar TODO spawn apagaria o que o proprio servidor coloca de
        // proposito — e o sintoma seria um ovo que "nao funciona", sem erro.
        for (org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason motivo : List.of(
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)) {
            assertTrue(RegrasDoHub.nascimentoEhDeAdmin(motivo), motivo + " deveria passar");
        }
        assertFalse(RegrasDoHub.nascimentoEhDeAdmin(
                        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL),
                "bicho que nasce sozinho tem de ser barrado");
        assertFalse(RegrasDoHub.nascimentoEhDeAdmin(
                        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER),
                "spawner tambem nasce sozinho");
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
