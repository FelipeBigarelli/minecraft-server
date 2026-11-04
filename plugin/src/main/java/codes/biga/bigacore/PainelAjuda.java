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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** {@code <terreno-preco-16>} — o 16 é o lado do quadrado, não o preço. */
    private static final Pattern PRECO_TERRENO =
            Pattern.compile("<terreno-preco-(\\d{1,4})>");

    /**
     * Quanto vale um pixel da fonte em blocos, num display de escala 1.
     *
     * <p>Uma linha de texto ocupa 0.25 bloco de altura e a fonte avança
     * 10 px por linha (8 do glifo + 2 de respiro), então 1 px = 0.025
     * bloco. É daqui que sai a largura de um painel, e sem isso o
     * espaçamento entre colunas vira chute — foi o que aconteceu na
     * primeira versão, com painéis de ~7 blocos postos a cada 4.
     */
    private static final double BLOCO_POR_PIXEL = 0.025;

    /** Altura de uma linha de texto, em blocos, na escala 1. */
    private static final double ALTURA_LINHA = 0.25;

    /**
     * Largura de avanço de cada caractere na fonte padrão, em pixels.
     *
     * <p>A fonte do Minecraft não é monoespaçada: um {@code i} ocupa 2 px
     * e um {@code m}, 6. Medir tudo por 6 incharia a parede sem motivo,
     * ainda mais num texto cheio de barra e pontuação como este.
     */
    private static int larguraPixel(char c) {
        return switch (c) {
            case '!', '.', ',', ':', ';', 'i', '|', '\'', '`' -> 2;
            case 'l' -> 3;
            case ' ', 't', 'I', '[', ']', '(', ')', '{', '}', '"', '<', '>', '*' -> 4;
            case 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

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
            return new Relatorio(false, 0, 0, 0, 0,
                    "ajuda.yml não tem nenhum painel em 'paineis'.");
        }

        World mundo = jogador.getWorld();
        Location base = jogador.getLocation();

        ConfigurationSection l = config.getConfigurationSection("layout");
        double distancia = valor(l, "distancia", 6.0);
        double altura = valor(l, "altura", 2.4);
        float escala = (float) valor(l, "escala", 1.0);
        double margem = valor(l, "margem", 1.0);

        // Espaçamento 0 (ou ausente) significa "calcule você": o painel
        // mais largo manda na largura da coluna, o mais alto manda na
        // altura da fileira. Deixar número fixo aqui quebra toda vez que
        // alguém reescreve uma linha mais longa no ajuda.yml, e o
        // sintoma é texto sobreposto, não um erro.
        double espacamento = valor(l, "espacamento", 0.0);
        if (espacamento <= 0) {
            espacamento = larguraMaxima(paineis, escala) + margem;
        }
        double espacamentoV = valor(l, "espacamento_vertical", 0.0);
        if (espacamentoV <= 0) {
            espacamentoV = alturaMaxima(paineis, escala) + margem;
        }
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

        return new Relatorio(true, criados, removidos, espacamento, espacamentoV, null);
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
        TerrenoCatalog terreno = plugin.terrenoCatalog();

        String saida = texto
                .replace("<moeda-inicial>", String.valueOf(eco.startingBalance()))
                .replace("<lojas-limite>", String.valueOf(eco.initialShopLimit()))
                .replace("<taxa-loja>", String.valueOf(eco.chestShopTaxPercent()))
                .replace("<simbolo>", eco.currencySymbol())
                .replace("<terreno-bloco>", String.valueOf(terreno.precoPorBloco()))
                .replace("<terreno-min>", String.valueOf(terreno.tamanhoMinimo()))
                .replace("<terreno-max>", String.valueOf(terreno.tamanhoMaximo()))
                .replace("<terreno-limite>", String.valueOf(terreno.limitePorJogador()))
                .replace("<terreno-reembolso>", String.valueOf(terreno.reembolsoPercent()));

        List<Integer> precos = eco.plotPrices();
        for (int i = 0; i < 3; i++) {
            String marcador = "<preco-terreno-" + (i + 1) + ">";
            String valor = i < precos.size() ? String.valueOf(precos.get(i)) : "—";
            saida = saida.replace(marcador, valor);
        }

        return precosDeTerreno(saida, terreno);
    }

    /**
     * Resolve {@code <terreno-preco-16>} e companhia, calculando na hora.
     *
     * Qualquer tamanho serve: o número no marcador vira o lado do quadrado e o
     * painel mostra o que /terreno comprar cobraria por ele hoje.
     *
     * Existe pelo mesmo motivo dos outros marcadores — um exemplo de preço
     * digitado à mão no ajuda.yml vira mentira no dia em que alguém mexer no
     * preco-por-bloco do terrenos.yml, e ninguém lembra de voltar aqui. Com o
     * cálculo na hora, a parede e a cobrança nunca discordam.
     *
     * O multiplicador de quantidade fica de fora de propósito: a parede fala
     * com quem acabou de entrar, e para essa pessoa o primeiro terreno é o
     * único preço que existe.
     */
    private String precosDeTerreno(String texto, TerrenoCatalog terreno) {
        Matcher achado = PRECO_TERRENO.matcher(texto);
        StringBuilder saida = new StringBuilder();

        while (achado.find()) {
            long preco;
            try {
                int lado = Integer.parseInt(achado.group(1));
                preco = TerrenoCatalog.preco(lado * lado, terreno.precoPorBloco(), 1.0);
            } catch (NumberFormatException erro) {
                // Lado absurdo no ajuda.yml não derruba a parede inteira.
                preco = 0L;
            }
            achado.appendReplacement(saida, Matcher.quoteReplacement(String.valueOf(preco)));
        }
        achado.appendTail(saida);
        return saida.toString();
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

    /**
     * Largura, em blocos, do painel mais largo do conjunto.
     *
     * <p>Mede o texto depois de resolver os preços e de tirar as tags do
     * MiniMessage: {@code <gray>} não ocupa pixel nenhum na tela, mas
     * ocupa oito caracteres no arquivo, e medir o texto cru daria uma
     * parede larga demais — num painel bem colorido, o dobro do
     * necessário.
     */
    private double larguraMaxima(List<Painel> paineis, float escala) {
        int pixels = 0;
        for (Painel painel : paineis) {
            pixels = Math.max(pixels, larguraPixel(painel.titulo()));
            for (String linha : painel.linhas()) {
                pixels = Math.max(pixels, larguraPixel(linha));
            }
        }
        return pixels * BLOCO_POR_PIXEL * escala;
    }

    private int larguraPixel(String comTags) {
        String limpo = plugin.mini().stripTags(resolver(comTags));
        int total = 0;
        for (int i = 0; i < limpo.length(); i++) {
            total += larguraPixel(limpo.charAt(i));
        }
        return total;
    }

    /** Altura, em blocos, do painel com mais linhas (título e o branco incluídos). */
    private double alturaMaxima(List<Painel> paineis, float escala) {
        int linhas = 0;
        for (Painel painel : paineis) {
            linhas = Math.max(linhas, painel.linhas().size() + 2);
        }
        return linhas * ALTURA_LINHA * escala;
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

    /**
     * Resultado de uma construção.
     *
     * <p>Carrega o espaçamento usado de propósito: quando ele é
     * calculado, o número só existe em memória, e sem devolvê-lo não há
     * como conferir no jogo se a conta bateu com o que apareceu na
     * parede.
     */
    public record Relatorio(boolean ok, int criados, int removidos,
                            double espacamento, double espacamentoVertical,
                            String erro) {
    }
}
