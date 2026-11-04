package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * O paredão de ajuda do spawn: painéis de texto flutuante explicando
 * comandos, economia, terrenos e lojas para quem acabou de entrar.
 *
 * <p>Por que TextDisplay e não placa: placa tem 4 linhas de 15
 * caracteres e nenhuma cor de fundo. Um painel de ajuda que cabe numa
 * placa não explica nada. O display entity aceita quantas linhas
 * quisermos, MiniMessage inteiro e um fundo semitransparente que
 * separa o texto da paisagem.
 *
 * <p>Por que não o FancyHolograms, que já está instalado: ele guarda
 * cada holograma num JSON próprio e os cria por comando, um de cada
 * vez. Um paredão de 6 painéis com 50 linhas no total viraria dezenas
 * de comandos digitados à mão, e nada disso sobreviveria a uma troca
 * de mundo. Aqui o conteúdo é arquivo versionado e a parede inteira
 * se refaz com um comando — que foi exatamente o que faltou quando o
 * mundo foi resetado em 04/11/2026.
 *
 * <p>Os preços nunca são escritos no texto: {@code <preco-terreno-1>} e
 * companhia são resolvidos a partir do economy.yml na hora de
 * construir. Assim a parede não passa a mentir quando a tabela de
 * preços mudar.
 */
public final class PainelAjuda {

    /**
     * Marca as entidades desta parede.
     *
     * <p>Diferente da {@link ShopDisplays#TAG}: as duas varrem o mundo
     * procurando display entities, e uma tag compartilhada faria a
     * limpeza de uma apagar a outra.
     */
    public static final String TAG = "bigacore_ajuda_painel";

    private final BigaCore plugin;
    private YamlConfiguration config;

    public PainelAjuda(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File arquivo = new File(plugin.getDataFolder(), "ajuda.yml");
        this.config = YamlConfiguration.loadConfiguration(arquivo);
    }

    /** Quantos painéis o ajuda.yml declara hoje. */
    public int totalPaineis() {
        List<?> lista = config.getList("paineis");
        return lista == null ? 0 : lista.size();
    }

    /**
     * Levanta a parede à frente de quem chamou.
     *
     * <p>Sempre limpa antes de construir. Sem isso, chamar o comando
     * duas vezes deixaria duas paredes sobrepostas no mesmo lugar —
     * ilegíveis, e com o dobro de entidades no chunk.
     *
     * @param aplicar false faz a simulação: conta e valida sem criar nada.
     */
    public Relatorio construir(Player jogador, boolean aplicar) {
        List<Painel> paineis = lerPaineis();
        if (paineis.isEmpty()) {
            return new Relatorio(false, 0, 0,
                    "ajuda.yml não tem nenhum painel em 'paineis'.");
        }

        World mundo = jogador.getWorld();
        Location base = jogador.getLocation();

        ConfigurationSection l = config.getConfigurationSection("layout");
        double distancia = valor(l, "distancia", 6.0);
        double espacamento = valor(l, "espacamento", 4.0);
        double altura = valor(l, "altura", 2.4);
        double espacamentoV = valor(l, "espacamento_vertical", 2.8);
        float escala = (float) valor(l, "escala", 1.0);
        float alcance = (float) valor(l, "alcance", 32.0);
        float giro = (float) valor(l, "giro", 180.0);
        int porFileira = l == null ? 3 : Math.max(1, l.getInt("por_fileira", 3));
        Color fundo = corDeFundo(l);

        int removidos = aplicar ? limpar(mundo) : contar(mundo);

        // Frente e lateral derivam do ângulo de quem digitou: você mira
        // para onde quer a parede e constrói. O Y é zerado porque olhar
        // para cima não deve inclinar a fileira.
        Vector frente = base.getDirection().setY(0);
        if (frente.lengthSquared() < 1.0e-6) {
            // Olhando reto para baixo ou para cima o vetor degenera.
            frente = new Vector(0, 0, 1);
        }
        frente = frente.normalize();
        Vector lateral = new Vector(-frente.getZ(), 0, frente.getX());

        int fileiras = (paineis.size() + porFileira - 1) / porFileira;
        int criados = 0;

        for (int i = 0; i < paineis.size(); i++) {
            int fileira = i / porFileira;
            int coluna = i % porFileira;
            int nestaFileira = Math.min(porFileira, paineis.size() - fileira * porFileira);

            // Centraliza a fileira no jogador: com 3 colunas os deslocamentos
            // ficam -1, 0, +1 vezes o espaçamento.
            double desvio = (coluna - (nestaFileira - 1) / 2.0) * espacamento;

            // A fileira 0 fica em cima, para a ordem de leitura do ajuda.yml
            // bater com a ordem de leitura na parede.
            double subida = altura + (fileiras - 1 - fileira) * espacamentoV;

            Location alvo = base.clone()
                    .add(frente.clone().multiply(distancia))
                    .add(lateral.clone().multiply(desvio));
            alvo.setY(base.getY() + subida);
            alvo.setYaw(base.getYaw() + giro);
            alvo.setPitch(0f);

            if (aplicar) {
                criar(mundo, alvo, paineis.get(i), escala, alcance, fundo);
            }
            criados++;
        }

        return new Relatorio(true, criados, removidos, null);
    }

    private void criar(World mundo, Location alvo, Painel painel,
                       float escala, float alcance, Color fundo) {

        Component texto = montar(painel);

        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);

            // FIXED, e não CENTER: num paredão o texto precisa ficar parado
            // na parede. Com CENTER cada painel giraria para acompanhar o
            // jogador e as colunas deixariam de formar uma parede.
            novo.setBillboard(Display.Billboard.FIXED);

            novo.setAlignment(TextDisplay.TextAlignment.LEFT);
            novo.setBackgroundColor(fundo);
            novo.setShadowed(true);

            // Sem isto o texto escurece à noite e dentro de sombra, que é
            // justo quando o jogador novo mais precisa ler.
            novo.setBrightness(new Display.Brightness(15, 15));

            novo.setViewRange(alcance / 64.0f);
            novo.setPersistent(true);
            novo.addScoreboardTag(TAG);

            if (Math.abs(escala - 1.0f) > 1.0e-4) {
                Transformation t = novo.getTransformation();
                novo.setTransformation(new Transformation(
                        t.getTranslation(),
                        t.getLeftRotation(),
                        new Vector3f(escala, escala, escala),
                        t.getRightRotation()));
            }
        });
    }

    /** Título, linha em branco e corpo, já com os placeholders resolvidos. */
    private Component montar(Painel painel) {
        StringBuilder bruto = new StringBuilder(painel.titulo());
        bruto.append('\n');
        for (String linha : painel.linhas()) {
            bruto.append('\n').append(linha);
        }
        return plugin.mini().deserialize(resolver(bruto.toString()));
    }

    /**
     * Troca os marcadores pelos números que valem hoje no economy.yml.
     *
     * <p>Feito antes do MiniMessage de propósito: o valor entra como
     * texto puro, então um preço nunca pode injetar tag de formatação.
     */
    private String resolver(String texto) {
        EconomyCatalog eco = plugin.economyCatalog();

        String saida = texto
                .replace("<moeda-inicial>", String.valueOf(eco.startingBalance()))
                .replace("<lojas-limite>", String.valueOf(eco.initialShopLimit()))
                .replace("<taxa-loja>", String.valueOf(eco.chestShopTaxPercent()))
                .replace("<simbolo>", eco.currencySymbol());

        List<Integer> precos = eco.plotPrices();
        for (int i = 0; i < 3; i++) {
            String marcador = "<preco-terreno-" + (i + 1) + ">";
            String valor = i < precos.size() ? String.valueOf(precos.get(i)) : "—";
            saida = saida.replace(marcador, valor);
        }
        return saida;
    }

    /**
     * Apaga a parede do mundo indicado.
     *
     * <p>Idempotente: pode rodar quantas vezes quiser, inclusive sem
     * nenhuma parede construída.
     */
    public int limpar(World mundo) {
        int removidos = 0;
        for (Entity entidade : mundo.getEntities()) {
            if (entidade instanceof TextDisplay
                    && entidade.getScoreboardTags().contains(TAG)) {
                entidade.remove();
                removidos++;
            }
        }
        return removidos;
    }

    /** Quantos painéis já existem no mundo, sem remover nada. */
    public int contar(World mundo) {
        int achados = 0;
        for (Entity entidade : mundo.getEntities()) {
            if (entidade instanceof TextDisplay
                    && entidade.getScoreboardTags().contains(TAG)) {
                achados++;
            }
        }
        return achados;
    }

    private List<Painel> lerPaineis() {
        List<Painel> saida = new ArrayList<>();
        List<?> cru = config.getList("paineis");
        if (cru == null) {
            return saida;
        }
        for (Object item : cru) {
            if (!(item instanceof java.util.Map<?, ?> mapa)) {
                continue;
            }
            Object titulo = mapa.get("titulo");
            Object linhas = mapa.get("linhas");
            if (titulo == null) {
                continue;
            }
            List<String> corpo = new ArrayList<>();
            if (linhas instanceof List<?> lista) {
                for (Object linha : lista) {
                    corpo.add(linha == null ? "" : String.valueOf(linha));
                }
            }
            saida.add(new Painel(String.valueOf(titulo), corpo));
        }
        return saida;
    }

    private static double valor(ConfigurationSection secao, String chave, double padrao) {
        return secao == null ? padrao : secao.getDouble(chave, padrao);
    }

    /**
     * Lê o fundo em ARGB hexadecimal.
     *
     * <p>Fica como texto no YAML, e não como número: {@code 90000000} sem
     * aspas estoura o int com sinal, e um fundo quase opaco viraria um
     * valor negativo sem explicação nenhuma para quem editou o arquivo.
     */
    private static Color corDeFundo(ConfigurationSection secao) {
        String texto = secao == null ? null : secao.getString("fundo", "90000000");
        try {
            long argb = Long.parseLong(texto == null ? "90000000" : texto.trim(), 16);
            return Color.fromARGB(
                    (int) ((argb >> 24) & 0xFF),
                    (int) ((argb >> 16) & 0xFF),
                    (int) ((argb >> 8) & 0xFF),
                    (int) (argb & 0xFF));
        } catch (NumberFormatException erro) {
            return Color.fromARGB(0x90, 0, 0, 0);
        }
    }

    public record Painel(String titulo, List<String> linhas) {
    }

    public record Relatorio(boolean ok, int criados, int removidos, String erro) {
    }
}
