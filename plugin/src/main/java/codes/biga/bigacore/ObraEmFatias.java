package codes.biga.bigacore;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongConsumer;

/**
 * Uma obra grande feita em fatias, com orçamento de tempo por tique.
 *
 * <h2>O defeito que isto conserta</h2>
 *
 * <p>{@code /biga refazer CONFIRMAR} apagava e levantava as vinte arenas
 * dentro de um tique só. Cada arena é uma caixa de meio milhão de blocos para
 * varrer; vinte delas são dez milhões de consultas de bloco sem respirar. O
 * servidor parava por dezenas de segundos e o vigia do Paper despejava o
 * rastro de pilha três vezes seguidas no console.
 *
 * <p>A conta não diminui — a obra é essa mesma. O que muda é que ela passa a
 * ser feita aos pedaços: a cada tique o plugin trabalha até gastar o
 * orçamento, guarda onde parou e devolve o tique ao servidor. O relógio do
 * mundo não para, ninguém cai da ponte, o vigia não late, e a obra leva
 * alguns segundos de relógio em vez de travar o jogo inteiro.
 *
 * <h2>O que é uma fatia</h2>
 *
 * <p>Uma {@link Fatia} é um pedaço de obra que sabe parar no meio: cada
 * chamada de {@link Fatia#passo()} faz um tanto pequeno e devolve
 * {@code false} quando não há mais o que fazer. A varredura de uma caixa, por
 * exemplo, faz uma COLUNA por passo — umas cinquenta casas, microssegundos —
 * e o orçamento é que decide quantas colunas cabem no tique.
 *
 * <p>Um passo que não sabe se dividir (levantar a arena, que é um método
 * fechado de cada construtor) entra como passo único: o orçamento é
 * conferido ANTES dele, então ele pode estourar o tique sozinho. É aceitável
 * porque o estouro de um é da ordem de um décimo de segundo, não de um
 * minuto, e porque dividir os vinte construtores seria reescrever a obra
 * inteira para ganhar o que a fatia da varredura já ganha.
 */
public final class ObraEmFatias implements Runnable {

    /** Um pedaço de obra que sabe parar no meio e continuar depois. */
    public interface Fatia {

        /** @return false quando esta fatia acabou */
        boolean passo();
    }

    private final BigaCore plugin;
    private final Deque<Fatia> fila = new ArrayDeque<>();
    private final long orcamento;
    private final LongConsumer aoAcabar;
    private long gastos;
    private BukkitTask tarefa;

    /**
     * @param orcamentoMs quanto de cada tique a obra pode tomar. Vinte
     *                    milissegundos é um tique inteiro do Minecraft: com
     *                    isso o servidor cai para uns dez quadros por segundo
     *                    durante a obra, que é lento mas vivo. O valor é
     *                    limitado entre 1 e 40 porque um orçamento de 200
     *                    seria o travamento de volta com outro nome.
     * @param aoAcabar     recebe o tempo total gasto, em milissegundos
     */
    public ObraEmFatias(BigaCore plugin, long orcamentoMs,
                        LongConsumer aoAcabar) {
        this.plugin = plugin;
        this.orcamento = Math.max(1, Math.min(40, orcamentoMs)) * 1_000_000L;
        this.aoAcabar = aoAcabar;
    }

    /** Junta uma fatia que sabe se dividir. */
    public ObraEmFatias juntar(Fatia fatia) {
        fila.add(fatia);
        return this;
    }

    /** Junta um passo que não sabe se dividir: roda inteiro, de uma vez. */
    public ObraEmFatias juntarPassoUnico(Runnable passo) {
        fila.add(() -> {
            passo.run();
            return false;
        });
        return this;
    }

    public boolean acabou() {
        return fila.isEmpty();
    }

    public void comecar() {
        if (tarefa != null) {
            return;
        }
        tarefa = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this, 1L, 1L);
    }

    /** Desiste da obra pelo meio: o que faltou não é feito. */
    public void desistir() {
        fila.clear();
        if (tarefa != null) {
            tarefa.cancel();
            tarefa = null;
        }
    }

    @Override
    public void run() {
        long comecou = System.nanoTime();
        long ate = comecou + orcamento;
        while (!fila.isEmpty() && System.nanoTime() < ate) {
            if (!fila.peek().passo()) {
                fila.poll();
            }
        }
        gastos += System.nanoTime() - comecou;
        if (fila.isEmpty()) {
            if (tarefa != null) {
                tarefa.cancel();
                tarefa = null;
            }
            aoAcabar.accept(gastos / 1_000_000L);
        }
    }

    // ------------------------------------------------------------------
    //  As fatias prontas
    // ------------------------------------------------------------------

    /**
     * A varredura de uma caixa de arena, uma coluna por passo.
     *
     * <p>Apaga esvaziando antes o que houver dentro: sem isso, varrer uma
     * arena de Sky Wars com os baús cheios cospe o saque todo no chão, que
     * foi um relato de verdade.
     *
     * @param soma o total de blocos apagados, para o recado do fim
     */
    public static Fatia varrerCaixa(World mundo, int alcance, int fundo,
                                    int teto, int[] soma) {
        int[] onde = {-alcance, -alcance};
        return () -> {
            if (onde[0] > alcance) {
                return false;
            }
            for (int y = fundo; y <= teto; y++) {
                soma[0] += MapaDaArena.apagarSemDerrubar(
                        mundo.getBlockAt(onde[0], y, onde[1]));
            }
            onde[1]++;
            if (onde[1] > alcance) {
                onde[1] = -alcance;
                onde[0]++;
            }
            return onde[0] <= alcance;
        };
    }

    /**
     * Quantas colunas uma caixa tem: o tamanho da obra, para o recado.
     *
     * <p>Separada e pura porque é ela que diz se a conta cabe num tique — e
     * a resposta, para uma arena de alcance 50, é que não cabe nem perto:
     * dez mil e duzentas colunas.
     */
    public static int colunasDaCaixa(int alcance) {
        long lado = 2L * Math.max(0, alcance) + 1;
        return (int) Math.min(Integer.MAX_VALUE, lado * lado);
    }
}
