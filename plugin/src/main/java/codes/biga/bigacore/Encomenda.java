package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Um pedido aberto no quadro de encomendas.
 *
 * O campo {@code preco} ja esta PAGO quando a encomenda existe: o dinheiro sai
 * da conta de quem pediu no instante da criacao e fica em custodia ate alguem
 * entregar ou o pedido ser cancelado. Nao existe encomenda sem lastro, e e
 * isso que permite prometer ao entregador que ele vai receber.
 *
 * Mora em arquivo proprio, e nao aninhado no {@link EncomendaLivro}, pela
 * mesma licao do {@link Terreno}: tocar num tipo aninhado obriga a JVM a ligar
 * a classe de fora, e a de fora costuma arrastar dependencia junto.
 */
public record Encomenda(
        int id,
        UUID dono,
        String donoNome,
        Material item,
        int quantidade,
        long preco,
        long criadaEm) {

    /** Quanto o servidor paga por unidade. So para exibir. */
    public double precoUnitario() {
        return quantidade <= 0 ? 0 : (double) preco / quantidade;
    }

    public boolean ehDono(UUID jogador) {
        return dono.equals(jogador);
    }
}
