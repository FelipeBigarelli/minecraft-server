package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A planta do hub, lida do hub.yml.
 *
 * <h2>Geometria local</h2>
 *
 * Tudo aqui é (u, v, y) relativo ao centro da praça: u cresce para LESTE e v
 * para o SUL, que é como o x e o z do mundo crescem. Não há rotação — o hub
 * nasce alinhado aos pontos cardeais de propósito, porque é isso que faz a
 * pessoa dizer "o portal do survival é o do oeste" e acertar sempre.
 */
public final class HubCatalog {

    /** Altura do piso quando ninguém disse outra. O gerador do mundo usa isto. */
    public static final int Y_PADRAO = 100;

    /** Para que lado do centro um portal sai. */
    public enum Face {
        NORTE(0, -1), SUL(0, 1), LESTE(1, 0), OESTE(-1, 0);

        private final int du;
        private final int dv;

        Face(int du, int dv) {
            this.du = du;
            this.dv = dv;
        }

        public int du() {
            return du;
        }

        public int dv() {
            return dv;
        }

        /** Vetor perpendicular, usado para a largura da passarela e do arco. */
        public int lu() {
            return -dv;
        }

        public int lv() {
            return du;
        }

        public org.bukkit.block.BlockFace paraOCentro() {
            return switch (this) {
                case NORTE -> org.bukkit.block.BlockFace.SOUTH;
                case SUL -> org.bukkit.block.BlockFace.NORTH;
                case LESTE -> org.bukkit.block.BlockFace.WEST;
                case OESTE -> org.bukkit.block.BlockFace.EAST;
            };
        }

        /** Yaw de quem olha do portal para o centro. */
        public float yawParaOCentro() {
            return switch (this) {
                case NORTE -> 0f;
                case SUL -> 180f;
                case LESTE -> 90f;
                case OESTE -> 270f;
            };
        }
    }

    public record Portal(String id, Face face, String titulo, String descricao,
                         String detalhe, String mundo, String cor,
                         Material moldura, Material destaque, String escultura) {

        /** Portal sem mundo é portal em obra: existe, brilha e avisa. */
        public boolean aberto() {
            return mundo != null && !mundo.isBlank();
        }
    }

    private final BigaCore plugin;
    private YamlConfiguration config;

    public HubCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "hub.yml"));
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int pisoY() {
        return config.getInt("praca.y", Y_PADRAO);
    }

    public int raio() {
        return config.getInt("praca.raio", 21);
    }

    public int passarelaComprimento() {
        return config.getInt("praca.passarela-comprimento", 22);
    }

    public int passarelaLargura() {
        return config.getInt("praca.passarela-largura", 9);
    }

    public int terracoRaio() {
        return config.getInt("praca.terraco-raio", 15);
    }

    /** Altura do anel suspenso sobre o piso da praça. */
    public int anelAltura() {
        return config.getInt("praca.anel-altura", 24);
    }

    public int anelRaio() {
        return config.getInt("praca.anel-raio", 8);
    }

    /** Distância do centro até o meio do terraço de um portal. */
    public int distanciaDoTerraco() {
        return raio() + passarelaComprimento();
    }

    /** Quanto o portão fica adiante do meio do terraço. */
    public int portalAvanco() {
        return config.getInt("praca.portal-avanco", 5);
    }

    /** Onde o vão do portal fica, contado do centro da praça. */
    public int distanciaDoPortal() {
        return distanciaDoTerraco() + portalAvanco();
    }

    /** Meia largura do vão por onde se passa. */
    public int portaoVao() {
        return config.getInt("portao.vao", 3);
    }

    /** Espessura de cada montante, ao lado do vão. */
    public int portaoPilar() {
        return config.getInt("portao.pilar", 3);
    }

    public int portaoAltura() {
        return config.getInt("portao.altura", 14);
    }

    /** Quanto a escultura fica atrás do portão. */
    public int esculturaAvanco() {
        return config.getInt("portao.escultura-avanco", 6);
    }

    // ------------------------------------------------------------------
    //  Monumento
    // ------------------------------------------------------------------

    public int monumentoEspessura() {
        return config.getInt("monumento.espessura", 3);
    }

    public int monumentoMeiaLarguraMinima() {
        return config.getInt("monumento.meia-largura-minima", 11);
    }

    public int monumentoAlturaMinima() {
        return config.getInt("monumento.altura-minima", 14);
    }

    /** Quanto a obra sobe acima do piso. Manda no desfazer e na conferência. */
    public int alturaDaObra() {
        return anelAltura() + 10;
    }

    /**
     * O retângulo que a obra inteira ocupa, em raio a partir do centro.
     *
     * É com ele que o construtor confere se o lugar está vazio e é ele que o
     * desfazer apaga. Generoso de propósito: escultura e parapeito passam da
     * borda do terraço, e um desfazer que deixa pedaço para trás é pior do
     * que um que limpa ar demais num mundo que é só ar.
     */
    public int alcanceDaObra() {
        return distanciaDoTerraco() + terracoRaio() + 8;
    }

    // ------------------------------------------------------------------
    //  Paleta
    // ------------------------------------------------------------------

    public Material cor(String chave, Material padrao) {
        String nome = config.getString("paleta." + chave);
        if (nome == null) {
            return padrao;
        }
        Material material = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("[hub] Material desconhecido em paleta." + chave
                    + ": " + nome + " — usando " + padrao + ".");
            return padrao;
        }
        return material;
    }

    // ------------------------------------------------------------------
    //  Portais
    // ------------------------------------------------------------------

    public List<Portal> portais() {
        List<Portal> portais = new ArrayList<>();
        List<Map<?, ?>> cru = config.getMapList("portais");

        for (Map<?, ?> item : cru) {
            String id = texto(item, "id", "");
            if (id.isBlank()) {
                continue;
            }

            Face face;
            try {
                face = Face.valueOf(texto(item, "face", "NORTE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException erro) {
                plugin.getLogger().warning("[hub] Face desconhecida no portal " + id
                        + " — usando NORTE.");
                face = Face.NORTE;
            }

            portais.add(new Portal(id, face,
                    texto(item, "titulo", id.toUpperCase(Locale.ROOT)),
                    texto(item, "descricao", ""),
                    texto(item, "detalhe", ""),
                    texto(item, "mundo", ""),
                    texto(item, "cor", "#FFFFFF"),
                    material(texto(item, "moldura", "QUARTZ_BLOCK"), Material.QUARTZ_BLOCK),
                    material(texto(item, "destaque", "LIGHT_BLUE_CONCRETE"),
                            Material.LIGHT_BLUE_CONCRETE),
                    texto(item, "escultura", "")));
        }
        return portais;
    }

    private Material material(String nome, Material padrao) {
        Material achado = Material.matchMaterial(nome.toUpperCase(Locale.ROOT));
        return achado == null ? padrao : achado;
    }

    private static String texto(Map<?, ?> item, String chave, String padrao) {
        Object valor = item.get(chave);
        return valor == null ? padrao : String.valueOf(valor);
    }

    // ------------------------------------------------------------------
    //  Esculturas
    // ------------------------------------------------------------------

    /** Os desenhos do hub.yml, do topo para a base. */
    public Map<String, List<String>> esculturas() {
        Map<String, List<String>> saida = new LinkedHashMap<>();
        ConfigurationSection secao = config.getConfigurationSection("esculturas");
        if (secao == null) {
            return saida;
        }
        for (String chave : secao.getKeys(false)) {
            saida.put(chave, secao.getStringList(chave));
        }
        return saida;
    }

    public List<String> escultura(String nome) {
        return esculturas().getOrDefault(nome, List.of());
    }

    // ------------------------------------------------------------------
    //  Regras
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Letreiros
    // ------------------------------------------------------------------

    /**
     * Opacidade da caixa atrás do texto, de 0 a 255.
     *
     * Fica no arquivo porque é a coisa que mais se ajusta olhando, e olhar
     * custa entrar no jogo. Zero deixa o texto solto, com sombra; acima de
     * uns 120 a caixa cinza vira o elemento mais pesado de um hub branco.
     */
    public int fundoDoTexto() {
        return Math.max(0, Math.min(255, config.getInt("textos.fundo-alpha", 0)));
    }

    public double escalaTitulo() {
        return config.getDouble("textos.escala-titulo", 2.6);
    }

    public double escalaPlaca() {
        return config.getDouble("textos.escala-placa", 2.0);
    }

    public double escalaChegada() {
        return config.getDouble("textos.escala-chegada", 1.9);
    }

    /**
     * Nível da poção de velocidade no hub, como gente escreve: 3 é Speed III.
     * Zero desliga.
     */
    public int velocidade() {
        return config.getInt("regras.velocidade", 3);
    }

    public boolean semFome() {
        return config.getBoolean("regras.sem-fome", true);
    }

    public boolean semDano() {
        return config.getBoolean("regras.sem-dano", true);
    }

    public boolean semPvp() {
        return config.getBoolean("regras.sem-pvp", true);
    }

    public boolean semMobs() {
        return config.getBoolean("regras.sem-mobs", true);
    }

    /** Meio-dia travado e sem chuva: o hub foi desenhado para ser visto. */
    public boolean sempreDia() {
        return config.getBoolean("regras.sempre-dia", true);
    }

    public boolean spawnDeEntrada() {
        return config.getBoolean("regras.spawn-de-entrada", true);
    }

    public int resgateY() {
        return config.getInt("regras.resgate-y", 60);
    }

    public int esperaSegundos() {
        return config.getInt("regras.espera-segundos", 3);
    }

    // ------------------------------------------------------------------
    //  Conferências — estáticas e puras, para o HubTest rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Um desenho torto não dá erro: ele constrói uma escultura entortada.
     *
     * Linha mais curta que as outras vira buraco no lado direito da peça, e
     * como o desenho é lido de cima para baixo o erro aparece exatamente onde
     * ninguém olha duas vezes — no meio.
     */
    public static boolean desenhoEhRetangular(List<String> desenho) {
        if (desenho == null || desenho.isEmpty()) {
            return false;
        }
        int largura = desenho.get(0).length();
        for (String linha : desenho) {
            if (linha.length() != largura) {
                return false;
            }
        }
        return largura > 0;
    }

    /**
     * A face que sobrou: é nela que o jogador nasce.
     *
     * Com três portais sobra uma das quatro. Se um dia houver quatro portais
     * a chegada volta para o SUL e passa a dividir o terraço com ele — feio,
     * mas ninguém nasce dentro de parede, que é o que importa.
     */
    public static Face faceLivre(List<Portal> portais) {
        for (Face face : Face.values()) {
            boolean usada = false;
            for (Portal portal : portais) {
                if (portal.face() == face) {
                    usada = true;
                    break;
                }
            }
            if (!usada) {
                return face;
            }
        }
        return Face.SUL;
    }

    /** Dois portais na mesma face se constroem um por cima do outro. */
    public static boolean facesSaoUnicas(List<Portal> portais) {
        List<Face> vistas = new ArrayList<>();
        for (Portal portal : portais) {
            if (vistas.contains(portal.face())) {
                return false;
            }
            vistas.add(portal.face());
        }
        return true;
    }
}
