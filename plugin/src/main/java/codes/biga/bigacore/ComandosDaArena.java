package codes.biga.bigacore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * O que não se digita dentro de uma arena de minigame.
 *
 * <h2>Por que isto existe</h2>
 *
 * /speed transforma qualquer jogo de corrida numa piada, e nenhum dos vinte
 * tem defesa contra ele: o jogo mede quem chega primeiro, e o comando decide
 * a resposta antes de a partida começar. A mesma família — voo, vida, item,
 * modo e teleporte — faz o mesmo estrago nos outros.
 *
 * <h2>O que NÃO está na lista</h2>
 *
 * /spawn, /hub, /lobby, /home, /biga e o bate-papo ficam livres de propósito.
 * Um jogador trancado numa arena sem saída é um defeito pior do que um
 * jogador voando, e a lista sendo editável no yml é justamente onde alguém
 * se tranca sem perceber. {@link #SAIDAS} está aqui para a conferência do
 * teste poder recusar uma lista que fecha a porta.
 *
 * <h2>O prefixo</h2>
 *
 * O Essentials registra /essentials:speed além de /speed. Bloquear só o curto
 * deixa a porta dos fundos aberta, então a raiz do comando é tirada depois do
 * último dois-pontos.
 */
public final class ComandosDaArena {

    private ComandosDaArena() {
    }

    /** A chave do config.yml que manda nisto. */
    public static final String CHAVE = "minigames.comandos-bloqueados";

    /** O que é bloqueado quando o config.yml não diz nada. */
    public static final List<String> DE_FABRICA = List.of(
            "speed", "fly", "god", "heal", "feed", "ptime", "pweather",
            "vanish", "v", "gamemode", "gm", "gmc", "gms", "gma", "gmsp",
            "give", "i", "item", "more", "kit", "repair", "fix", "hat",
            "enderchest", "ec", "invsee", "workbench", "wb", "craft",
            "anvil", "smithingtable", "stonecutter", "loom", "grindstone",
            "cartographytable", "clearinventory", "ci", "powertool", "pt",
            "tp", "tpo", "tphere", "tpahere", "tppos", "top", "jump",
            "back", "butcher", "killall", "nuke", "sudo", "unlimited",
            "spawnmob", "tree", "bigtree", "effect", "enchant", "exp");

    /** As saídas que nunca podem entrar na lista: ninguém pode ficar preso. */
    public static final List<String> SAIDAS = List.of(
            "spawn", "hub", "lobby", "home", "biga", "msg", "r", "tell",
            "w", "help", "list", "spawnpoint");

    /**
     * O nome do comando: sem a barra, sem argumento e sem prefixo de plugin.
     */
    public static String raiz(String digitado) {
        String linha = digitado == null ? "" : digitado.trim();
        if (linha.startsWith("/")) {
            linha = linha.substring(1);
        }
        int espaco = linha.indexOf(' ');
        if (espaco >= 0) {
            linha = linha.substring(0, espaco);
        }
        int doisPontos = linha.lastIndexOf(':');
        if (doisPontos >= 0) {
            linha = linha.substring(doisPontos + 1);
        }
        return linha.toLowerCase(Locale.ROOT);
    }

    /** A lista escrita no yml, ou a de fábrica quando ela vier vazia. */
    public static Set<String> lista(BigaCore plugin) {
        return lista(plugin.getConfig().getStringList(CHAVE));
    }

    /** A mesma conta, pura, para o teste ler a lista que o servidor usa. */
    public static Set<String> lista(List<String> escrita) {
        List<String> fonte = escrita == null || escrita.isEmpty()
                ? DE_FABRICA : escrita;
        Set<String> saida = new LinkedHashSet<>();
        for (String nome : fonte) {
            String limpo = raiz(nome);
            if (!limpo.isEmpty()) {
                saida.add(limpo);
            }
        }
        return saida;
    }

    /** O comando digitado é dos que a arena recusa? */
    public static boolean bloqueado(BigaCore plugin, String digitado) {
        return lista(plugin).contains(raiz(digitado));
    }

    /**
     * A lista não tranca ninguém lá dentro?
     *
     * Devolve a primeira saída que a lista fecha, ou {@code null} se ela
     * estiver boa.
     */
    public static String saidaFechada(Set<String> lista) {
        for (String saida : SAIDAS) {
            if (lista.contains(saida)) {
                return saida;
            }
        }
        return null;
    }
}
