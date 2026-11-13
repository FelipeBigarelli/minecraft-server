package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Garante que o mundo de um minigame existe, criando-o vazio se preciso.
 *
 * <h2>Por que não deixar isso para o /mv create</h2>
 *
 * O hub e o lobby foram criados à mão, uma vez cada, e isso foi razoável. Com
 * vinte jogos deixa de ser: seriam vinte comandos a digitar na ordem certa,
 * com a flag {@code -g BigaCore} que, esquecida, gera um mundo de PEDRA por
 * baixo da arena — e o construtor então se recusa a levantar nada, dizendo
 * que "há blocos no caminho", o que não explica nada a quem errou a flag.
 *
 * <p>Aqui o mundo nasce certo ou não nasce. Se a criação falhar, o aviso diz
 * o comando exato para fazer à mão, e o jogo simplesmente não roda até lá, em
 * vez de rodar errado.
 *
 * <h2>O Multiverse continua opcional</h2>
 *
 * Um mundo criado assim funciona para tudo o que o plugin faz — o portal usa
 * {@code Bukkit.getWorld(nome)} e acha. O que ele NÃO ganha é /mvtp e as
 * configurações por mundo do Multiverse; para isso basta um
 * {@code /mv import <nome> normal -g BigaCore} depois, uma vez.
 */
public final class MundoDeJogo {

    private MundoDeJogo() {
    }

    /**
     * O mundo de nome {@code nome}, criado vazio se ainda não existir.
     *
     * @return o mundo, ou null se não existia e não deu para criar.
     */
    public static World garantir(BigaCore plugin, String nome) {
        return garantir(plugin, nome, false);
    }

    /**
     * O mesmo, sabendo se o mundo é um MAPA PRONTO baixado.
     *
     * <h3>Por que a distinção importa</h3>
     *
     * <p>Dezenove dos vinte mundos de jogo nascem aqui, vazios, e precisam
     * continuar vazios: se um pedaço nas bordas da arena for gerado pelo
     * gerador normal, aparece montanha de pedra encostada no Sky Wars. Para
     * esses o gerador vazio é passado SEMPRE, mesmo com o mundo já em disco
     * — é o gerador deles desde o primeiro dia.
     *
     * <p>A corrida sobre o Monte Akina é o contrário: o mundo é um mapa
     * baixado de quatrocentos megabytes, com relevo de verdade, e impor o
     * gerador vazio a ele faria o servidor discordar do que está no próprio
     * mapa. Só esse passa {@code true}.
     *
     * @param mapaPronto se o mundo vem de um mapa baixado, com relevo próprio
     */
    public static World garantir(BigaCore plugin, String nome,
                                 boolean mapaPronto) {
        World achado = Bukkit.getWorld(nome);
        if (achado != null) {
            return achado;
        }

        // Um mundo que JÁ EXISTE em disco carrega com o gerador dele, nunca
        // com o nosso. A Corrida roda sobre um mapa baixado — uma montanha de
        // quatrocentos megabytes — e impor o gerador vazio a ele faria o
        // servidor gerar vazio em volta da estrada e, pior, discordar do que
        // está escrito no level.dat do próprio mapa. Só mundo que nasce aqui
        // nasce vazio.
        boolean jaEstaEmDisco = mapaPronto && estaEmDisco(nome);
        plugin.getLogger().info("[mundos] " + (jaEstaEmDisco
                ? "Carregando o mundo '" + nome + "' que já está em disco."
                : "Criando o mundo vazio '" + nome + "'."));
        try {
            WorldCreator receita = new WorldCreator(nome)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false);
            if (!jaEstaEmDisco) {
                receita.generator(new VazioGenerator());
            }
            World novo = receita.createWorld();
            if (novo == null) {
                plugin.getLogger().warning("[mundos] O servidor recusou criar '" + nome
                        + "'. Crie à mão: /mv create " + nome + " normal -g BigaCore");
                return null;
            }
            novo.setAutoSave(true);
            return novo;
        } catch (RuntimeException erro) {
            // Criar mundo mexe em disco e no registro de dimensões do servidor.
            // Se falhar, o que NÃO pode acontecer é derrubar o onEnable e levar
            // economia, lojas e painel junto — foi o que já aconteceu com a
            // ponte do WorldGuard.
            plugin.getLogger().warning("[mundos] Falhou criar '" + nome + "': "
                    + erro.getMessage() + ". Crie à mão: /mv create " + nome
                    + " normal -g BigaCore");
            return null;
        }
    }

    /**
     * Se o mundo de nome {@code nome} já tem dados em disco.
     *
     * <h3>Dois lugares, e o segundo não é o óbvio</h3>
     *
     * <p>A pasta clássica é {@code <container>/<nome>/level.dat}. Mas o Paper
     * moderno ARRUMA o servidor na subida: um mundo que não seja o principal
     * é mudado para {@code <mundo-principal>/dimensions/minecraft/<nome>}, e
     * lá dentro não há {@code level.dat} nenhum — só {@code region/},
     * {@code entities/} e {@code data/}, porque o level.dat do servidor
     * passou a ser um só.
     *
     * <p>Olhando só o primeiro lugar, o plugin anunciava
     * "Criando o mundo vazio 'corrida_akina'" com os quatrocentos megabytes
     * do Monte Akina intactos no segundo — e, pior, mandava o gerador vazio
     * junto. Hoje basta haver {@code region/} com alguma coisa dentro.
     */
    static boolean estaEmDisco(String nome) {
        java.io.File container = Bukkit.getWorldContainer();
        if (new java.io.File(new java.io.File(container, nome),
                "level.dat").isFile()) {
            return true;
        }
        java.util.List<World> mundos = Bukkit.getWorlds();
        if (mundos.isEmpty()) {
            return false;
        }
        java.io.File mudado = new java.io.File(
                mundos.get(0).getWorldFolder(),
                "dimensions/minecraft/" + nome + "/region");
        String[] regioes = mudado.list();
        return regioes != null && regioes.length > 0;
    }
}
