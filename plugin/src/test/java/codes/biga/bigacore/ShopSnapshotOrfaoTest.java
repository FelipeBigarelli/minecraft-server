package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O impasse: o mundo de terrenos é recriado, o UUID muda, e aí
 * {@code /biga loja criar} recusa porque já existe snapshot enquanto
 * {@code /biga loja desfazer} recusa porque o UUID não bate. Sem saída dentro
 * do jogo — aconteceu em 12/08/2026 e de novo em 11/11/2026, e nas duas foi
 * preciso renomear o arquivo pelo terminal.
 *
 * O conserto arquiva sozinho o snapshot órfão. Como isso MOVE o único arquivo
 * capaz de desfazer uma construção, a decisão é testada nos dois sentidos: o
 * falso-positivo custa um rollback.
 */
class ShopSnapshotOrfaoTest {

    private static final UUID A = UUID.fromString("7a6cb263-aee3-407a-9821-ff0a0f3e22ee");
    private static final UUID B = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    @DisplayName("mundo recriado com outro UUID: e orfao")
    void mundoRecriado() {
        assertTrue(ShopSnapshot.snapshotOrfao("plots", A.toString(), B));
    }

    @Test
    @DisplayName("mesmo mundo, mesmo UUID: NAO e orfao")
    void mesmoMundo() {
        assertFalse(ShopSnapshot.snapshotOrfao("plots", A.toString(), A));
    }

    @Test
    @DisplayName("mundo apenas descarregado: NAO e orfao, pode voltar igual")
    void mundoDescarregado() {
        // /cp unload plots nao destroi nada. O mundo volta com o mesmo UUID e
        // o snapshot continua valendo — arquivar aqui perderia o rollback.
        assertFalse(ShopSnapshot.snapshotOrfao("plots", A.toString(), null));
    }

    @Test
    @DisplayName("snapshot sem identificacao de mundo: nao se julga sozinho")
    void semIdentificacao() {
        assertFalse(ShopSnapshot.snapshotOrfao("", A.toString(), B));
        assertFalse(ShopSnapshot.snapshotOrfao("plots", "", B));
        assertFalse(ShopSnapshot.snapshotOrfao(null, null, B));
    }

    @Test
    @DisplayName("UUID gravado ilegivel com o mundo carregado: e orfao")
    void uuidCorrompido() {
        // O restore recusaria para sempre, entao o arquivo so atrapalha.
        assertTrue(ShopSnapshot.snapshotOrfao("plots", "nao-e-uuid", B));
    }
}
