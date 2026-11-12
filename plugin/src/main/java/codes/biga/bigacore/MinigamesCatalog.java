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
 * A planta do lobby dos minigames, lida do minigames.yml.
 *
 * <h2>Por que o lobby é um claustro e não um disco</h2>
 *
 * O hub é redondo porque tem três caminhos: num círculo de três braços cada
 * braço sai num ponto cardeal e ninguém precisa de placa. Aqui são VINTE
 * portais, e vinte nichos espalhados num círculo cairiam quase todos na
 * diagonal — e parede diagonal no Minecraft é escada, não parede. Reto e
 * carimbado de um bloco, um emblema de onze por onze vira um borrão de
 * degraus.
 *
 * Então a planta é um quadrado: quatro galerias retas de verdade, cinco
 * nichos em cada uma. Cada galeria tem uma parede de 107 blocos e um
 * emblema por nicho que sai do jeito que foi desenhado.
 *
 * <h2>Geometria local: ao longo e para fora</h2>
 *
 * Nada aqui é escrito em x e z. Cada ponto da galeria é (aoLongo, paraFora):
 * quanto ele anda ao lado da galeria e quanto ele se afasta do centro. O
 * {@link Lado} converte isso em x/z com uma rotação de 90 graus, e por isso
 * o mesmo código constrói as quatro galerias sem um único {@code if} de
 * direção. Foi o que fez o hub não ter quatro cópias do portão.
 */
public final class MinigamesCatalog {

    /** Altura do piso quando ninguém disse outra. O gerador do mundo usa isto. */
    public static final int Y_PADRAO = 100;

    /**
     * De que galeria um portal faz parte.
     *
     * <p>Cada lado carrega dois vetores: {@code u} anda ao longo da galeria e
     * {@code v} aponta para fora do centro. Os quatro são a mesma rotação
     * aplicada de novo — {@code u = (-v.z, v.x)} —, o que mantém a ordem dos
     * cinco nichos girando sempre no mesmo sentido. Sem isso, o nicho da
     * esquerda de uma galeria seria o da direita da seguinte.
     */
    public enum Lado {
        NORTE(0, -1), LESTE(1, 0), SUL(0, 1), OESTE(-1, 0);

        private final int vx;
        private final int vz;

        Lado(int vx, int vz) {
            this.vx = vx;
            this.vz = vz;
        }

        public int vx() {
            return vx;
        }

        public int vz() {
            return vz;
        }

        /** O vetor que anda ao longo da galeria: v girado 90 graus. */
        public int ux() {
            return -vz;
        }

        public int uz() {
            return vx;
        }

        public int x(int aoLongo, int paraFora) {
            return ux() * aoLongo + vx * paraFora;
        }

        public int z(int aoLongo, int paraFora) {
            return uz() * aoLongo + vz * paraFora;
        }

        /**
         * Yaw de quem está na galeria e olha para o centro da praça.
         *
         * <p>É a direção {@code -v}. Serve para virar o jogador depois do
         * teleporte e, somado a 180, para virar os letreiros.
         */
        public float yawParaDentro() {
            return switch (this) {
                case NORTE -> 0f;
                case LESTE -> 90f;
                case SUL -> 180f;
                case OESTE -> 270f;
            };
        }

        /** Yaw de quem está no pátio e olha para a fachada desta galeria. */
        public float yawParaFora() {
            return (yawParaDentro() + 180f) % 360f;
        }
    }

    /**
     * Um dos vinte jogos.
     *
     * @param vao qual dos cinco nichos da galeria, de -2 a +2, da esquerda
     *            para a direita de quem olha a fachada.
     */
    public record Portal(String id, Lado lado, int vao, String titulo, String descricao,
                         String detalhe, String mundo, String cor,
                         Material moldura, Material destaque, String escultura) {

        /** Portal sem mundo é portal em obra: existe, brilha e avisa. */
        public boolean aberto() {
            return mundo != null && !mundo.isBlank();
        }
    }

    /** O portal do meio, que devolve o jogador ao hub. */
    public record Volta(String titulo, String descricao, String cor,
                        Material moldura, Material destaque) {
    }

    private final BigaCore plugin;
    private YamlConfiguration config;

    public MinigamesCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "minigames.yml"));
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int pisoY() {
        return config.getInt("praca.y", Y_PADRAO);
    }

    /** Meia largura do pátio quadrado do meio. */
    public int patio() {
        return config.getInt("praca.patio", 43);
    }

    public int galeriaProfundidade() {
        return config.getInt("praca.galeria-profundidade", 10);
    }

    public int galeriaAltura() {
        return config.getInt("praca.galeria-altura", 20);
    }

    /** Altura em que se ANDA na passarela de cima, acima do piso. */
    public int galeriaSuperior() {
        return config.getInt("praca.galeria-superior", 21);
    }

    /** Onde a galeria termina, contado do centro. */
    public int galeriaFim() {
        return patio() + galeriaProfundidade();
    }

    public int bordaExterna() {
        return config.getInt("praca.borda-externa", 6);
    }

    /** Os deslocamentos dos nichos ao longo de cada galeria. */
    public List<Integer> vaos() {
        List<Integer> lidos = config.getIntegerList("praca.vaos");
        return lidos.isEmpty() ? List.of(-28, -14, 0, 14, 28) : lidos;
    }

    /** Converte o número do nicho (-2 a +2) no deslocamento na galeria. */
    public int centroDoVao(int vao) {
        List<Integer> lista = vaos();
        int indice = vao + lista.size() / 2;
        if (indice < 0 || indice >= lista.size()) {
            return 0;
        }
        return lista.get(indice);
    }

    /** Meia largura do arco por onde se passa. */
    public int vaoLargura() {
        return config.getInt("praca.vao-largura", 3);
    }

    public int vaoAltura() {
        return config.getInt("praca.vao-altura", 11);
    }

    public int nichoPilar() {
        return config.getInt("praca.nicho-pilar", 3);
    }

    /** Meia largura do nicho inteiro: o arco mais os dois montantes. */
    public int nichoMeiaLargura() {
        return vaoLargura() + nichoPilar();
    }

    /**
     * Meia largura da câmara de dentro.
     *
     * <p>É UM MENOR que o nicho de propósito: a câmara é mais larga que o
     * arco. Quem passa pelo arco de sete entra num salão de onze e só então
     * vê o emblema inteiro. Se a câmara tivesse a largura do arco, as duas
     * colunas de fora de cada emblema ficariam escondidas atrás do montante
     * e todos os vinte desenhos sairiam cortados.
     */
    public int camaraMeiaLargura() {
        return nichoMeiaLargura() - 1;
    }

    public int torreCentro() {
        return config.getInt("praca.torre-centro", 36);
    }

    /** Meia largura da torre. O lado é sempre ímpar, para ela ter um meio. */
    public int torreMeia() {
        return Math.max(3, config.getInt("praca.torre-lado", 13) / 2);
    }

    public int torreAltura() {
        return config.getInt("praca.torre-altura", 30);
    }

    public int monumentoRaio() {
        return config.getInt("praca.monumento-raio", 11);
    }

    public int monumentoAltura() {
        return config.getInt("praca.monumento-altura", 22);
    }

    public int anelAltura() {
        return config.getInt("praca.anel-altura", 34);
    }

    public int anelRaio() {
        return config.getInt("praca.anel-raio", 24);
    }

    /** Quanto a obra sobe acima do piso. Manda no desfazer e na conferência. */
    public int alturaDaObra() {
        return Math.max(anelAltura(), torreAltura()) + 8;
    }

    /**
     * O quadrado que a obra inteira ocupa, em distância a partir do centro.
     *
     * <p>Generoso de propósito: é com ele que o construtor confere se o lugar
     * está vazio e é ele que o desfazer apaga. Um desfazer que deixa pedaço
     * para trás é pior do que um que limpa ar demais num mundo que é só ar.
     */
    public int alcanceDaObra() {
        return galeriaFim() + bordaExterna() + 4;
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
            plugin.getLogger().warning("[lobby] Material desconhecido em paleta." + chave
                    + ": " + nome + " — usando " + padrao + ".");
            return padrao;
        }
        return material;
    }

    // ------------------------------------------------------------------
    //  Portais
    // ------------------------------------------------------------------

    public Volta volta() {
        return new Volta(
                config.getString("volta.titulo", "VOLTAR AO HUB"),
                config.getString("volta.descricao", "a praça dos mundos"),
                config.getString("volta.cor", "#8FD3FF"),
                material(config.getString("volta.moldura", "QUARTZ_BLOCK"),
                        Material.QUARTZ_BLOCK),
                material(config.getString("volta.destaque", "LIGHT_BLUE_CONCRETE"),
                        Material.LIGHT_BLUE_CONCRETE));
    }

    public List<Portal> portais() {
        List<Portal> portais = new ArrayList<>();
        List<Map<?, ?>> cru = config.getMapList("portais");

        for (Map<?, ?> item : cru) {
            String id = texto(item, "id", "");
            if (id.isBlank()) {
                continue;
            }

            Lado lado;
            try {
                lado = Lado.valueOf(texto(item, "lado", "NORTE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException erro) {
                plugin.getLogger().warning("[lobby] Lado desconhecido no portal " + id
                        + " — usando NORTE.");
                lado = Lado.NORTE;
            }

            int vao;
            try {
                vao = Integer.parseInt(texto(item, "vao", "0").trim());
            } catch (NumberFormatException erro) {
                plugin.getLogger().warning("[lobby] Vão inválido no portal " + id
                        + " — usando 0.");
                vao = 0;
            }

            portais.add(new Portal(id, lado, vao,
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
        if (nome == null) {
            return padrao;
        }
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

    /** Os desenhos do minigames.yml, do topo para a base. */
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

    public int resgateY() {
        return config.getInt("regras.resgate-y", 60);
    }

    public int esperaSegundos() {
        return config.getInt("regras.espera-segundos", 3);
    }

    // ------------------------------------------------------------------
    //  Conferências — estáticas e puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Dois portais no mesmo nicho se constroem um por cima do outro.
     *
     * <p>Com vinte entradas escritas à mão num arquivo, trocar um LESTE por
     * um OESTE e deixar dois no mesmo lugar é o erro mais fácil de cometer e
     * o mais difícil de ver: o segundo simplesmente apaga o primeiro, sem
     * nenhum aviso no console.
     */
    public static boolean vagasSaoUnicas(List<Portal> portais) {
        List<String> vistas = new ArrayList<>();
        for (Portal portal : portais) {
            String vaga = portal.lado().name() + ":" + portal.vao();
            if (vistas.contains(vaga)) {
                return false;
            }
            vistas.add(vaga);
        }
        return true;
    }

    /** Qual nicho está vazio, se algum. Útil para o relatório do /biga lobby. */
    public static List<String> vagasLivres(List<Portal> portais, List<Integer> vaos) {
        List<String> livres = new ArrayList<>();
        int meio = vaos.size() / 2;
        for (Lado lado : Lado.values()) {
            for (int i = 0; i < vaos.size(); i++) {
                int vao = i - meio;
                boolean ocupada = false;
                for (Portal portal : portais) {
                    if (portal.lado() == lado && portal.vao() == vao) {
                        ocupada = true;
                        break;
                    }
                }
                if (!ocupada) {
                    livres.add(lado.name() + ":" + vao);
                }
            }
        }
        return livres;
    }

    /**
     * Um desenho mais largo que a câmara sai cortado nas pontas.
     *
     * <p>A conta é a mesma do construtor, e está aqui para o teste poder
     * fazê-la sem servidor: carimbar e ver depois custa subir o servidor,
     * entrar no mundo e andar até o nicho.
     */
    public static boolean esculturaCabe(List<String> desenho, int camaraMeiaLargura,
                                        int alturaDisponivel) {
        if (!HubCatalog.desenhoEhRetangular(desenho)) {
            return false;
        }
        return desenho.get(0).length() <= camaraMeiaLargura * 2 + 1
                && desenho.size() <= alturaDisponivel;
    }

    /**
     * O nicho em que um deslocamento cai, ou {@link Integer#MIN_VALUE} se ele
     * cair no maciço entre dois nichos.
     *
     * <p>É a conta que decide se um ponto da galeria é parede ou passagem, e
     * é por isso que ela é pública e estática: o construtor usa para carimbar
     * e o {@code LobbyPortais} usa para saber em que portal o jogador está.
     */
    public static int vaoEm(int aoLongo, List<Integer> vaos, int meiaLargura) {
        int meio = vaos.size() / 2;
        for (int i = 0; i < vaos.size(); i++) {
            if (Math.abs(aoLongo - vaos.get(i)) <= meiaLargura) {
                return i - meio;
            }
        }
        return Integer.MIN_VALUE;
    }
}
