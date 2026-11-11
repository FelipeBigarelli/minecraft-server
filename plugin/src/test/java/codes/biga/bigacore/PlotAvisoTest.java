package codes.biga.bigacore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O truque do {@link PlotAvisoListener} depende inteiramente de ORDEM.
 *
 * O listener de proteção do CreativePlots 5.1 registra os eventos de
 * construção em {@code priority = LOW} com {@code ignoreCancelled = true}.
 * Cancelar antes dele, em {@code LOWEST}, é o que faz a mensagem em inglês
 * não sair.
 *
 * Trocar {@code LOWEST} por {@code NORMAL} numa refatoração distraída não
 * quebra compilação, não quebra teste nenhum de lógica, e o sintoma aparece
 * só no jogo: duas mensagens em vez de uma, a nossa e a dele em inglês. Por
 * isso a anotação é verificada aqui.
 */
class PlotAvisoTest {

    private static List<Method> handlers() {
        List<Method> achados = new ArrayList<>();
        for (Method m : PlotAvisoListener.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(EventHandler.class)) {
                achados.add(m);
            }
        }
        return achados;
    }

    @Test
    @DisplayName("todo handler roda em LOWEST, antes do CreativePlots (LOW)")
    void prioridadeEhLowest() {
        List<Method> handlers = handlers();
        assertFalse(handlers.isEmpty(), "nenhum @EventHandler encontrado — o teste viraria vazio");

        for (Method m : handlers) {
            EventHandler anotacao = m.getAnnotation(EventHandler.class);
            assertEquals(EventPriority.LOWEST, anotacao.priority(),
                    m.getName() + " precisa rodar antes do listener do CreativePlots, que é LOW");
            assertTrue(anotacao.ignoreCancelled(),
                    m.getName() + " nao deve reagir a evento que outro plugin ja cancelou");
        }
    }

    @Test
    @DisplayName("cobre os quatro eventos de construcao que o CreativePlots recusa")
    void cobreOsEventosDeConstrucao() {
        List<String> tipos = handlers().stream()
                .map(m -> m.getParameterTypes()[0].getSimpleName())
                .sorted()
                .toList();

        assertEquals(List.of(
                "BlockBreakEvent",
                "BlockPlaceEvent",
                "PlayerBucketEmptyEvent",
                "PlayerBucketFillEvent"), tipos);
    }

    @Test
    @DisplayName("apelido com tag de MiniMessage nao vira marcacao na mensagem")
    void saneiaNomeDeJogador() {
        // online-mode=false: quem conecta escolhe o proprio apelido.
        //
        // Basta comer o "<": sem ele nenhuma tag ABRE, e o ">" que sobra e
        // texto literal. Tirar os dois mudaria apelido inocente a toa.
        assertEquals("red>Fulano", PlotAvisoListener.sanear("<red>Fulano"));
        assertEquals("click:run_command:/op eu>Fulano",
                PlotAvisoListener.sanear("<click:run_command:/op eu>Fulano"));
        assertEquals("Fulano", PlotAvisoListener.sanear("Fulano"));
        assertEquals("", PlotAvisoListener.sanear(null));
    }
}
