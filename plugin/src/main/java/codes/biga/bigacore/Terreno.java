package codes.biga.bigacore;

import java.util.Set;
import java.util.UUID;

/**
 * Dados de um terreno, em tipos que o resto do plugin entende.
 *
 * Os nomes dos donos nao vem aqui: o WorldGuard guarda UUID, e resolver nome a
 * partir de UUID e trabalho de quem vai mostrar na tela.
 *
 * Mora em arquivo proprio, e nao dentro do {@link WorldGuardBridge}, por um
 * motivo que custou um CI vermelho: tocar em qualquer coisa aninhada na ponte
 * obriga a JVM a ligar a ponte, e ligar a ponte obriga a resolver as classes do
 * WorldGuard. Num servidor sem WorldGuard isso e um NoClassDefFoundError que
 * derruba o plugin inteiro. Sendo um tipo de primeira classe, este record pode
 * circular pelo codigo sem arrastar o WorldGuard atras.
 */
public record Terreno(String id, Caixa caixa, Set<UUID> donos, Set<UUID> membros) {

    public boolean ehDono(UUID jogador) {
        return donos.contains(jogador);
    }
}
