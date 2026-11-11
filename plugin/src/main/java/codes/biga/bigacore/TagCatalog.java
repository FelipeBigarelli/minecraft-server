package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * As regras do tags.yml: preço, formato e o que NÃO pode virar tag.
 *
 * <h2>Por que a validação mora aqui, e estática</h2>
 *
 * A tag é a única coisa do servidor em que um jogador escreve na tela de
 * todos os outros. Uma regra frouxa não dá erro — ela publica. Por isso a
 * conferência é feita por métodos estáticos e puros, que o
 * {@code TagTest} roda sem servidor: cada caso que já passou errado vira
 * uma linha de teste em vez de uma correção na unha depois da reclamação.
 */
public final class TagCatalog {

    /** O que a conferência devolve: ou passa, ou diz exatamente por quê. */
    public enum Veredito {
        OK,
        CURTA,
        LONGA,
        CARACTERE,
        PROIBIDA,
        OFENSA,
        VAZIA
    }

    private final BigaCore plugin;
    private YamlConfiguration config;

    public TagCatalog(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "tags.yml"));
    }

    // ------------------------------------------------------------------
    //  Preço
    // ------------------------------------------------------------------

    public int preco() {
        return config.getInt("tag.preco", 2500);
    }

    public int precoDaTroca() {
        return config.getInt("tag.preco-troca", 800);
    }

    public int precoDaCor() {
        return config.getInt("tag.preco-cor", 1200);
    }

    public boolean vendendo() {
        return preco() > 0;
    }

    // ------------------------------------------------------------------
    //  Formato
    // ------------------------------------------------------------------

    public int minimo() {
        return config.getInt("tag.minimo", 3);
    }

    public int maximo() {
        return config.getInt("tag.maximo", 12);
    }

    public List<String> cores() {
        List<String> lista = config.getStringList("tag.cores");
        return lista.isEmpty() ? List.of("gold") : lista;
    }

    public String corPadrao() {
        return config.getString("tag.cor-padrao", "gold");
    }

    public boolean corPermitida(String cor) {
        if (cor == null) {
            return false;
        }
        String alvo = cor.toLowerCase(Locale.ROOT);
        for (String permitida : cores()) {
            if (permitida.toLowerCase(Locale.ROOT).equals(alvo)) {
                return true;
            }
        }
        return false;
    }

    private String caracteres() {
        return config.getString("tag.caracteres",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -_.");
    }

    public List<String> proibidas() {
        return config.getStringList("tag.proibidas");
    }

    public List<String> ofensas() {
        return config.getStringList("tag.ofensas");
    }

    // ------------------------------------------------------------------
    //  Conferência
    // ------------------------------------------------------------------

    /** Roda as regras do arquivo sobre um texto pedido por um jogador. */
    public Veredito conferir(String texto) {
        return conferir(texto, minimo(), maximo(), caracteres(), proibidas(), ofensas());
    }

    /**
     * A conferência de verdade — estática, para o teste chamar sem servidor.
     *
     * A ordem importa pouco para o resultado e muito para a mensagem: o
     * jogador precisa saber qual regra quebrou, e a primeira resposta é a que
     * ele lê.
     */
    static Veredito conferir(String texto, int minimo, int maximo, String caracteres,
                             List<String> proibidas, List<String> ofensas) {

        if (texto == null || texto.isBlank()) {
            return Veredito.VAZIA;
        }

        String limpo = texto.trim();
        if (limpo.length() < minimo) {
            return Veredito.CURTA;
        }
        if (limpo.length() > maximo) {
            return Veredito.LONGA;
        }

        for (int i = 0; i < limpo.length(); i++) {
            if (caracteres.indexOf(limpo.charAt(i)) < 0) {
                return Veredito.CARACTERE;
            }
        }

        // Espaço só separa palavra: "   " já caiu em VAZIA, mas "a   b" tem
        // tamanho suficiente e nenhuma letra no meio. Dois espaços seguidos
        // também servem para empurrar o nome do vizinho na lista.
        if (limpo.contains("  ")) {
            return Veredito.CARACTERE;
        }

        String achatado = normalizar(limpo);

        for (String proibida : proibidas) {
            if (!proibida.isBlank() && achatado.contains(normalizar(proibida))) {
                return Veredito.PROIBIDA;
            }
        }
        for (String ofensa : ofensas) {
            if (!ofensa.isBlank() && achatado.contains(normalizar(ofensa))) {
                return Veredito.OFENSA;
            }
        }

        return Veredito.OK;
    }

    /**
     * Achata o texto para comparar com a lista de palavras.
     *
     * Minúsculo, sem acento, sem espaço e com número virando a letra que ele
     * imita. Sem esta etapa, "4dm1n", "A D M I N" e "ádmin" passariam todos
     * por uma lista que tem "admin" — e passar é publicar.
     */
    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        StringBuilder saida = new StringBuilder(semAcento.length());
        for (char c : semAcento.toLowerCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case '4', '@' -> saida.append('a');
                case '3' -> saida.append('e');
                case '1', '!', '|' -> saida.append('i');
                case '0' -> saida.append('o');
                case '5', '$' -> saida.append('s');
                case '7' -> saida.append('t');
                case ' ', '-', '_', '.', '\'' -> { }
                default -> saida.append(c);
            }
        }
        return saida.toString();
    }

    /** A explicação que o jogador recebe quando a tag é recusada. */
    static String motivo(Veredito veredito, int minimo, int maximo) {
        return switch (veredito) {
            case OK -> "";
            case VAZIA -> "Escreva alguma coisa depois de /tag comprar.";
            case CURTA -> "Tag muito curta: mínimo de " + minimo + " caracteres.";
            case LONGA -> "Tag muito longa: máximo de " + maximo + " caracteres.";
            case CARACTERE -> "Só letra, número, espaço e - _ . são aceitos.";
            case PROIBIDA -> "Essa tag se passa por alguém da equipe.";
            case OFENSA -> "Essa tag não passa na moderação.";
        };
    }
}
