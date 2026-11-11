package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le o terrenos.yml e faz a conta de preco.
 *
 * Fica separado do {@link EconomyCatalog} pelo mesmo motivo que o economy.yml
 * fica separado do config.yml: regra de terreno tem ciclo de ajuste proprio.
 * Mexer no preco por bloco nao deveria obrigar a reler um arquivo de 300
 * linhas de catalogo de itens.
 *
 * As contas sao estaticas e recebem tudo por parametro de proposito — assim o
 * teste roda sem servidor, sem arquivo e sem jogador. Preco errado aqui nao
 * da excecao: entrega terreno barato ou cobra o dobro, e ninguem percebe por
 * semanas.
 */
public final class TerrenoCatalog {

    static final String ARQUIVO = "terrenos.yml";

    /**
     * Teto do preco de um terreno.
     *
     * Existe porque o multiplicador e composto: com limite alto por jogador e
     * uma tabela agressiva, o preco do decimo terreno estoura o long e vira
     * negativo — e um preco negativo PAGA o jogador para comprar.
     */
    static final long PRECO_MAXIMO = 1_000_000_000L;

    /** Fator usado quando a tabela de multiplicadores nao permite deduzir um. */
    static final double FATOR_PADRAO = 2.0;

    private final BigaCore plugin;
    private YamlConfiguration dados;

    public TerrenoCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File arquivo = new File(plugin.getDataFolder(), ARQUIVO);
        this.dados = YamlConfiguration.loadConfiguration(arquivo);
    }

    /**
     * Mundos onde da para comprar.
     *
     * Lista branca, nao lista negra. Com lista negra, cada mundo novo criado
     * pelo Multiverse — um minigame, um criativo, um mapa de teste — entraria
     * vendendo terreno por padrao, e alguem compraria 64x64 no meio da arena.
     */
    public List<String> mundos() {
        List<String> lista = dados.getStringList("mundos");
        if (lista.isEmpty()) {
            return List.of("world");
        }
        List<String> limpa = new ArrayList<>(lista.size());
        for (String nome : lista) {
            if (nome != null && !nome.isBlank()) {
                limpa.add(nome.trim());
            }
        }
        return List.copyOf(limpa);
    }

    public boolean mundoPermitido(String nome) {
        for (String permitido : mundos()) {
            if (permitido.equalsIgnoreCase(nome)) {
                return true;
            }
        }
        return false;
    }

    public int precoPorBloco() {
        return Math.max(0, dados.getInt("preco-por-bloco", 3));
    }

    public int tamanhoMinimo() {
        return Math.max(1, dados.getInt("tamanho-minimo", 8));
    }

    public int tamanhoMaximo() {
        return Math.max(tamanhoMinimo(), dados.getInt("tamanho-maximo", 64));
    }

    public int limitePorJogador() {
        return Math.max(0, dados.getInt("limite-por-jogador", 3));
    }

    public List<Double> multiplicadores() {
        List<Double> lista = dados.getDoubleList("multiplicador");
        if (lista.isEmpty()) {
            return List.of(1.0);
        }
        return List.copyOf(lista);
    }

    public int reembolsoPercent() {
        return Math.clamp(dados.getInt("reembolso-percent", 60), 0, 100);
    }

    public int folgaDoSpawn() {
        return Math.max(0, dados.getInt("distancia-minima-do-spawn", 24));
    }

    /**
     * Segundos parado antes de o /terreno teleportar levar o jogador.
     *
     * Teto de 60 para nao existir configuracao que trava alguem esperando
     * eternamente por um comando que ele pode ter digitado sem querer.
     */
    public int teleporteEspera() {
        return Math.clamp(dados.getInt("teleporte.espera-segundos", 5), 0, 60);
    }

    public int folgaEntreTerrenos() {
        return Math.max(0, dados.getInt("folga-entre-terrenos", 0));
    }

    public boolean alturaTotal() {
        return dados.getBoolean("altura-total", true);
    }

    /**
     * Prefixo do id da regiao no WorldGuard.
     *
     * O WorldGuard aceita id com letra, numero, _ , - , ' , + , / e vírgula.
     * Qualquer outra coisa faz o addRegion recusar, entao o prefixo e filtrado
     * aqui em vez de explodir na hora da compra.
     */
    public String prefixo() {
        String bruto = dados.getString("prefixo", "t");
        String limpo = bruto == null ? "" : bruto.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return limpo.isBlank() ? "t" : limpo;
    }

    public String mensagemEntrada() {
        return dados.getString("mensagem-entrada", "");
    }

    public String mensagemSaida() {
        return dados.getString("mensagem-saida", "");
    }

    // ------------------------------------------------------------------
    // Cerca
    // ------------------------------------------------------------------

    public boolean cercaAtiva() {
        return dados.getBoolean("cerca.ativa", true);
    }

    public Material cercaMaterial() {
        return material("cerca.material", Material.OAK_FENCE);
    }

    public Material cercaCanto() {
        return material("cerca.canto", Material.OAK_LOG);
    }

    public int cercaBuscaAcima() {
        return Math.clamp(dados.getInt("cerca.busca-acima", 8), 0, 64);
    }

    public int cercaBuscaAbaixo() {
        return Math.clamp(dados.getInt("cerca.busca-abaixo", 24), 0, 128);
    }

    public int cercaPorTique() {
        return Math.clamp(dados.getInt("cerca.por-tique", 48), 1, 512);
    }

    /**
     * Le um material do arquivo, avisando no log quando o nome nao existe.
     *
     * Material.matchMaterial devolve null para nome errado. Sem este aviso,
     * trocar OAK_FENCE por "cerca de carvalho" no YAML nao daria erro nenhum:
     * a cerca simplesmente nunca apareceria, e o jogador acharia que o
     * comando esta quebrado.
     */
    private Material material(String chave, Material padrao) {
        String nome = dados.getString(chave);
        if (nome == null || nome.isBlank()) {
            return padrao;
        }

        Material achado = Material.matchMaterial(nome.trim().toUpperCase(Locale.ROOT));
        if (achado == null || !achado.isBlock()) {
            plugin.getLogger().warning("[terreno] " + chave + ": \"" + nome
                    + "\" nao e um bloco valido. Usando " + padrao + ".");
            return padrao;
        }
        return achado;
    }

    // ------------------------------------------------------------------
    // Conta de preco: estatica, pura, sem servidor.
    // ------------------------------------------------------------------

    /**
     * Multiplicador do proximo terreno de quem ja tem {@code jaTem}.
     *
     * Dentro da tabela, le direto. Depois do fim da tabela, continua subindo
     * pela razao entre os dois ultimos degraus — mesma ideia do
     * PlotSale.fatorDeEscalada. Sem isso, quem levantasse o limite por jogador
     * de 3 para 10 compraria do quarto em diante sempre pelo preco do
     * terceiro, e o limite deixaria de ser economico.
     */
    static double multiplicadorPara(List<Double> tabela, int jaTem) {
        if (tabela == null || tabela.isEmpty()) {
            return 1.0;
        }
        if (jaTem < 0) {
            jaTem = 0;
        }
        if (jaTem < tabela.size()) {
            return Math.max(0.0, tabela.get(jaTem));
        }

        double fator = FATOR_PADRAO;
        if (tabela.size() >= 2) {
            double ultimo = tabela.get(tabela.size() - 1);
            double penultimo = tabela.get(tabela.size() - 2);
            if (penultimo > 0 && ultimo > penultimo) {
                fator = ultimo / penultimo;
            }
        }

        double valor = Math.max(0.0, tabela.get(tabela.size() - 1));
        for (int degrau = tabela.size(); degrau <= jaTem; degrau++) {
            valor *= fator;
            if (valor >= PRECO_MAXIMO) {
                return PRECO_MAXIMO;
            }
        }
        return valor;
    }

    /**
     * Preco de uma area.
     *
     * Area de chao, nao volume. Cobrar por volume faria o preco depender da
     * altura do mundo — o mesmo 20x20 custaria o dobro num mundo com o teto
     * mais alto, e o jogador nao tem como adivinhar isso.
     *
     * Arredonda para cima: um terreno nunca sai mais barato do que a tabela
     * diz por causa de centavo, e o servidor nao usa centavo (decimals: 0).
     */
    static long preco(int area, int precoPorBloco, double multiplicador) {
        if (area <= 0 || precoPorBloco <= 0 || multiplicador <= 0) {
            return 0L;
        }
        double bruto = (double) area * precoPorBloco * multiplicador;
        if (bruto >= PRECO_MAXIMO) {
            return PRECO_MAXIMO;
        }
        return (long) Math.ceil(bruto);
    }

    /**
     * Quanto volta ao vender.
     *
     * Calculado sobre o preco BASE — sem o multiplicador de quantidade. Com o
     * multiplicador na conta, o terceiro terreno (x3.5) devolveria 60% de 3.5
     * = 2.1 vezes o preco base, e comprar-e-vender daria lucro. Seria uma
     * impressora de dinheiro aberta a quem tivesse paciencia de repetir.
     *
     * Do jeito que esta, vender sempre da prejuizo: 60% de 1.0 e menor que o
     * 1.0 que foi pago na melhor das hipoteses.
     */
    static long reembolso(int area, int precoPorBloco, int percent) {
        if (area <= 0 || precoPorBloco <= 0 || percent <= 0) {
            return 0L;
        }
        double bruto = (double) area * precoPorBloco * (percent / 100.0);
        return (long) Math.floor(bruto);
    }
}
