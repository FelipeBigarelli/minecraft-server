package codes.biga.bigacore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A conta de preco da venda de terreno.
 *
 * O teste que mais importa aqui e o {@link #venderNuncaDaLucro()}: um
 * reembolso maior que o preco de compra nao e um bug que aparece no log, e uma
 * impressora de dinheiro que um jogador descobre antes do dono.
 */
class TerrenoPrecoTest {

    private static final List<Double> TABELA = List.of(1.0, 2.0, 3.5);

    @Test
    @DisplayName("20x20 custa o mesmo que o primeiro terreno do criativo")
    void precoDeReferencia() {
        // 400 blocos x B$3 = B$1.200, igual ao primeiro valor de plot-prices
        // no economy.yml. A conta esta fixada aqui para a referencia nao se
        // perder num ajuste de preco distraido.
        assertEquals(1200L, TerrenoCatalog.preco(400, 3, 1.0));
    }

    @Test
    @DisplayName("a tabela de preco bate com o que o terrenos.yml promete")
    void tabelaDoArquivoDeConfig() {
        assertEquals(192L, TerrenoCatalog.preco(8 * 8, 3, 1.0));
        assertEquals(768L, TerrenoCatalog.preco(16 * 16, 3, 1.0));
        assertEquals(3072L, TerrenoCatalog.preco(32 * 32, 3, 1.0));
        assertEquals(6912L, TerrenoCatalog.preco(48 * 48, 3, 1.0));
        assertEquals(12288L, TerrenoCatalog.preco(64 * 64, 3, 1.0));
    }

    @Test
    @DisplayName("preco arredonda para cima, nunca para baixo")
    void arredondaParaCima() {
        // Com multiplicador fracionario o valor quase nunca e inteiro, e o
        // servidor nao usa centavo (decimals: 0). Arredondar para baixo
        // entregaria area de graca, um bloco por compra.
        assertEquals(225L, TerrenoCatalog.preco(64, 3, 1.17));
    }

    @Test
    @DisplayName("area ou preco invalido custa zero, nunca negativo")
    void nadaDePrecoNegativo() {
        assertEquals(0L, TerrenoCatalog.preco(0, 3, 1.0));
        assertEquals(0L, TerrenoCatalog.preco(-5, 3, 1.0));
        assertEquals(0L, TerrenoCatalog.preco(100, 0, 1.0));
        assertEquals(0L, TerrenoCatalog.preco(100, 3, 0.0));
        assertEquals(0L, TerrenoCatalog.preco(100, 3, -1.0));
    }

    @Test
    @DisplayName("o preco tem teto, para o multiplicador composto nao estourar")
    void precoTemTeto() {
        assertEquals(TerrenoCatalog.PRECO_MAXIMO,
                TerrenoCatalog.preco(4096, 1_000_000, 1000.0));
        assertTrue(TerrenoCatalog.preco(4096, 1_000_000, 1000.0) > 0,
                "estourar o long viraria preco negativo, que PAGA para comprar");
    }

    @Test
    @DisplayName("o multiplicador segue a tabela enquanto ela durar")
    void multiplicadorDentroDaTabela() {
        assertEquals(1.0, TerrenoCatalog.multiplicadorPara(TABELA, 0));
        assertEquals(2.0, TerrenoCatalog.multiplicadorPara(TABELA, 1));
        assertEquals(3.5, TerrenoCatalog.multiplicadorPara(TABELA, 2));
    }

    @Test
    @DisplayName("depois do fim da tabela o multiplicador continua subindo")
    void multiplicadorContinuaDepoisDaTabela() {
        // Sem isto, levantar limite-por-jogador de 3 para 10 faria o quarto
        // terreno em diante sair sempre pelo preco do terceiro.
        double quarto = TerrenoCatalog.multiplicadorPara(TABELA, 3);
        double quinto = TerrenoCatalog.multiplicadorPara(TABELA, 4);

        assertEquals(3.5 * (3.5 / 2.0), quarto, 1e-9);
        assertTrue(quinto > quarto, "o quinto precisa custar mais que o quarto");
    }

    @Test
    @DisplayName("tabela vazia, nula ou de um degrau nao quebra")
    void tabelaDegenerada() {
        assertEquals(1.0, TerrenoCatalog.multiplicadorPara(null, 5));
        assertEquals(1.0, TerrenoCatalog.multiplicadorPara(List.of(), 5));

        // Um degrau so: nao da para deduzir razao, entao usa o fator padrao.
        assertEquals(1.0, TerrenoCatalog.multiplicadorPara(List.of(1.0), 0));
        assertEquals(2.0, TerrenoCatalog.multiplicadorPara(List.of(1.0), 1));
    }

    @Test
    @DisplayName("indice negativo e tratado como o primeiro terreno")
    void indiceNegativo() {
        assertEquals(1.0, TerrenoCatalog.multiplicadorPara(TABELA, -3));
    }

    @Test
    @DisplayName("vender nunca da lucro, em nenhum tamanho nem posicao da tabela")
    void venderNuncaDaLucro() {
        int precoPorBloco = 3;
        int percent = 60;

        for (int lado = 8; lado <= 64; lado++) {
            int area = lado * lado;
            long volta = TerrenoCatalog.reembolso(area, precoPorBloco, percent);

            for (int slot = 0; slot < 8; slot++) {
                double multiplicador = TerrenoCatalog.multiplicadorPara(TABELA, slot);
                long pago = TerrenoCatalog.preco(area, precoPorBloco, multiplicador);

                assertTrue(volta < pago,
                        "comprar " + lado + "x" + lado + " como terreno " + (slot + 1)
                                + " custa " + pago + " e devolve " + volta
                                + " — isso fecha um ciclo com lucro");
            }
        }
    }

    @Test
    @DisplayName("reembolso ignora o multiplicador de quantidade")
    void reembolsoSaiDoPrecoBase() {
        // Se o reembolso seguisse o multiplicador, o terceiro terreno (x3.5)
        // devolveria 60% de 3.5 = 2.1 vezes o preco base. Como o primeiro
        // terreno custa 1.0, bastaria comprar o terceiro, vender, e repetir.
        long volta = TerrenoCatalog.reembolso(400, 3, 60);

        assertEquals(720L, volta, "60% de B$1.200, nao de B$4.200");
    }

    @Test
    @DisplayName("reembolso arredonda para baixo e nao vai abaixo de zero")
    void reembolsoNaoInventaDinheiro() {
        assertEquals(0L, TerrenoCatalog.reembolso(0, 3, 60));
        assertEquals(0L, TerrenoCatalog.reembolso(400, 3, 0));
        assertEquals(0L, TerrenoCatalog.reembolso(400, 3, -50));
        assertEquals(1L, TerrenoCatalog.reembolso(1, 3, 50), "1.5 vira 1, nao 2");
    }

    @Test
    @DisplayName("o slot do terreno sai do id da regiao")
    void slotVemDoId() {
        assertEquals(1, TerrenoVenda.slotDoId("t_zbigabiga_1"));
        assertEquals(12, TerrenoVenda.slotDoId("t_alguem_12"));
        assertEquals(0, TerrenoVenda.slotDoId("spawn"), "regiao administrativa nao tem slot");
        assertEquals(0, TerrenoVenda.slotDoId("t_nome_"));
        assertEquals(0, TerrenoVenda.slotDoId("t_nome_abc"));
        assertEquals(0, TerrenoVenda.slotDoId(null));
    }

    @Test
    @DisplayName("o proximo slot ocupa o buraco deixado por um terreno vendido")
    void proximoSlotPreencheBuraco() {
        // Quem tem os slots 1 e 3 e vendeu o 2 volta a pagar preco de segundo
        // terreno, nao de terceiro. E o que torna o preco previsivel.
        List<Terreno> meus = new ArrayList<>();
        meus.add(terreno("t_biga_1"));
        meus.add(terreno("t_biga_3"));

        assertEquals(2, TerrenoVenda.proximoSlot(meus, "t", "biga"));
    }

    @Test
    @DisplayName("sem terreno nenhum, o proximo slot e o 1")
    void primeiroSlot() {
        assertEquals(1, TerrenoVenda.proximoSlot(List.of(), "t", "biga"));
    }

    @Test
    @DisplayName("slots em sequencia dao o proximo numero")
    void slotSeguinte() {
        List<Terreno> meus = List.of(
                terreno("t_biga_1"), terreno("t_biga_2"), terreno("t_biga_3"));

        assertEquals(4, TerrenoVenda.proximoSlot(meus, "t", "biga"));
    }

    @Test
    @DisplayName("o id da regiao sai limpo mesmo com nome estranho")
    void idSempreValido() {
        // O WorldGuard recusa id com caractere fora da lista dele. Nome de
        // jogador do Minecraft e sempre [A-Za-z0-9_], mas plugin de apelido
        // pode entregar outra coisa.
        assertEquals("t_zbigabiga_1", TerrenoVenda.idPara("t", "zBigaBiga", 1));
        assertEquals("t_nome_2", TerrenoVenda.idPara("t", "No*me!", 2));
        assertEquals("t_jogador_1", TerrenoVenda.idPara("t", "***", 1));

        assertTrue(TerrenoVenda.idPara("t", "zBigaBiga", 1).matches("[A-Za-z0-9_,'\\-+/]+"),
                "id fora do padrao do WorldGuard faz o addRegion recusar");
    }

    @Test
    @DisplayName("a mensagem de borda troca o dono e aguenta texto vazio")
    void textoDeFlag() {
        assertEquals("&8| &7Terreno de &fBiga",
                TerrenoVenda.textoDeFlag("&8| &7Terreno de &f<dono>", "Biga"));
        assertEquals("", TerrenoVenda.textoDeFlag("", "Biga"));
        assertEquals("", TerrenoVenda.textoDeFlag(null, "Biga"));
    }

    private static Terreno terreno(String id) {
        return new Terreno(id,
                Caixa.centradaEm(0, 0, 16, -64, 319),
                Set.of(UUID.randomUUID()), Set.of());
    }
}
