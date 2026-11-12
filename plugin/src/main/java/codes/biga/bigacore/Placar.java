package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O placar de um jogo de pontos.
 *
 * <h2>Por que isto é um serviço e não um {@code Map} dentro do jogo</h2>
 *
 * Não é pelo mapa — mapa de UUID para int qualquer jogo escreve numa linha. É
 * pelas três contas em volta dele, que parecem triviais e não são:
 *
 * <ul>
 *   <li>a ORDEM, que precisa de desempate ESTÁVEL. Ordenar só por pontos deixa
 *       dois empatados trocando de lugar a cada tique, e o jogador vê o
 *       segundo e o terceiro piscando sem nada ter acontecido;
 *   <li>a POSIÇÃO de um jogador, que não é o índice na lista: empatados
 *       dividem o mesmo lugar, senão dois com quarenta pontos aparecem como
 *       segundo e terceiro e um deles reclama com razão;
 *   <li>o LÍDER, que não existe quando há empate na frente. Um jogo que
 *       declara vencedor no empate premia quem entrou na partida primeiro.
 * </ul>
 *
 * As três estavam para ser escritas igual em seis jogos, e seis cópias iguais
 * divergem em silêncio — a quinta esqueceria o desempate estável e ninguém
 * saberia por que o placar pisca.
 *
 * <h2>O que este placar NÃO faz</h2>
 *
 * Soma. Só soma. Os jogos em que o número não é soma e sim marca pessoal — o
 * reinado mais longo do Rei, a menor distância do Labirinto — não cabem aqui,
 * e forçá-los a caber exigiria um parâmetro de "maior ou menor ganha" que
 * tornaria cada leitura deste arquivo uma pergunta. Quando o segundo jogo de
 * marca pessoal aparecer, ele ganha a classe dele.
 */
public final class Placar {

    private final Map<UUID, Integer> pontos = new HashMap<>();

    /** A ordem de entrada, que é o desempate estável. */
    private final Map<UUID, Integer> entrada = new HashMap<>();
    private int proximo;

    public void zerar() {
        pontos.clear();
        entrada.clear();
        proximo = 0;
    }

    /** Põe um jogador no placar com zero ponto. Repetir não zera o que tem. */
    public void entrar(UUID id) {
        pontos.putIfAbsent(id, 0);
        entrada.putIfAbsent(id, proximo++);
    }

    public void sair(UUID id) {
        pontos.remove(id);
        entrada.remove(id);
    }

    public void somar(UUID id, int quanto) {
        entrar(id);
        pontos.merge(id, quanto, Integer::sum);
    }

    public int de(UUID id) {
        return pontos.getOrDefault(id, 0);
    }

    public boolean vazio() {
        return pontos.isEmpty();
    }

    public int quantos() {
        return pontos.size();
    }

    /**
     * Todo mundo em ordem de pontos, do maior para o menor.
     *
     * O desempate é a ordem de ENTRADA, e não o UUID nem a ordem do mapa:
     * precisa ser estável entre duas chamadas, senão a lista que o jogador vê
     * reordena sozinha trinta vezes por segundo.
     */
    public List<UUID> ranking() {
        List<UUID> ordem = new ArrayList<>(pontos.keySet());
        ordem.sort(Comparator
                .comparingInt((UUID id) -> -de(id))
                .thenComparingInt(id -> entrada.getOrDefault(id, 0)));
        return ordem;
    }

    /**
     * Em que lugar está um jogador, contando empate como o mesmo lugar.
     *
     * Dois jogadores com quarenta pontos estão os dois em segundo, e o
     * seguinte está em quarto. O índice na lista diria segundo e terceiro, e
     * o terceiro reclamaria com razão.
     *
     * @return o lugar a partir de 1, ou 0 se o jogador não está no placar
     */
    public int posicaoDe(UUID id) {
        if (!pontos.containsKey(id)) {
            return 0;
        }
        int meus = de(id);
        int acima = 0;
        for (int outros : pontos.values()) {
            if (outros > meus) {
                acima++;
            }
        }
        return acima + 1;
    }

    /** Quem está na frente, ou null se há empate na frente ou ninguém. */
    public UUID lider() {
        UUID melhor = null;
        int maior = Integer.MIN_VALUE;
        boolean empate = false;
        for (Map.Entry<UUID, Integer> marca : pontos.entrySet()) {
            if (marca.getValue() > maior) {
                maior = marca.getValue();
                melhor = marca.getKey();
                empate = false;
            } else if (marca.getValue() == maior) {
                empate = true;
            }
        }
        return empate ? null : melhor;
    }

    public int pontosDoLider() {
        int maior = 0;
        for (int marca : pontos.values()) {
            maior = Math.max(maior, marca);
        }
        return maior;
    }

    /**
     * O nome de quem lidera, já escapado para o MiniMessage.
     *
     * @param semLider o que escrever quando há empate na frente
     */
    public String nomeDoLider(String semLider) {
        UUID quem = lider();
        if (quem == null) {
            return semLider;
        }
        Player jogador = Bukkit.getPlayer(quem);
        return jogador == null ? "alguém" : HubBuilder.escapar(jogador.getName());
    }
}
