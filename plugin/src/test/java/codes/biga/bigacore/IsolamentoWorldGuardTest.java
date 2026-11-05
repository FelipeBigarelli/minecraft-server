package codes.biga.bigacore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garante que as classes do WorldGuard fiquem presas numa classe so.
 *
 * Por que isto existe: em 05/11/2026 o BigaCore parou de habilitar em servidor
 * sem WorldGuard. O onEnable construia a ponte sem checar, e ligar a ponte faz
 * a JVM resolver com/sk89q/... — NoClassDefFoundError dentro do onEnable
 * derruba o plugin INTEIRO, levando economia, lojas e painel de ajuda junto. O
 * smoke test do CI pegou, mas so depois de quatro commits vermelhos.
 *
 * A defesa tem duas camadas. O CI boota um servidor sem WorldGuard e exige que
 * o BigaCore suba. Este teste cuida da outra metade: se uma classe nova
 * importar com.sk89q, o blast radius cresce sem ninguem notar, e a proxima
 * instanciacao descuidada derruba tudo de novo.
 *
 * Le os .class compilados em vez do fonte de proposito: o que importa e o que
 * a JVM vai ter de resolver, e isso mora no constant pool, nao nos imports.
 */
final class IsolamentoWorldGuardTest {

    /** A unica classe do plugin autorizada a falar com o WorldGuard. */
    private static final String PONTE = "WorldGuardBridge";

    private static final byte[] AGULHA = "com/sk89q".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static Path raizDasClasses() throws URISyntaxException {
        return Path.of(BigaCore.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
    }

    private static boolean contemAgulha(byte[] bytes) {
        for (int i = 0; i + AGULHA.length <= bytes.length; i++) {
            int j = 0;
            while (j < AGULHA.length && bytes[i + j] == AGULHA[j]) {
                j++;
            }
            if (j == AGULHA.length) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("so o WorldGuardBridge referencia classes do WorldGuard")
    void sk89qFicaNumaClasseSo() throws IOException, URISyntaxException {
        Path raiz = raizDasClasses();
        assertTrue(Files.isDirectory(raiz),
                "esperava o diretorio de classes compiladas, achei " + raiz);

        List<String> culpadas = new ArrayList<>();
        int vistas = 0;

        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path p : arquivos.filter(p -> p.toString().endsWith(".class")).toList()) {
                vistas++;
                if (!contemAgulha(Files.readAllBytes(p))) {
                    continue;
                }
                String nome = p.getFileName().toString().replace(".class", "");
                // Classes internas e lambdas aparecem como Externa$1; o que
                // vale e a classe de fora.
                String externa = nome.contains("$") ? nome.substring(0, nome.indexOf('$')) : nome;
                if (!PONTE.equals(externa)) {
                    culpadas.add(p.getFileName().toString());
                }
            }
        }

        assertTrue(vistas > 10, "achei so " + vistas + " .class — o teste nao estaria olhando nada");
        assertEquals(List.of(), culpadas,
                "estas classes referenciam com.sk89q e nao deveriam. Tudo que fala com o "
                + "WorldGuard mora no " + PONTE + ", e so ele pode ser instanciado sob "
                + "checagem de presenca do plugin. Veja BigaCore.onEnable.");
    }

    @Test
    @DisplayName("o record Terreno circula sem arrastar o WorldGuard")
    void terrenoNaoDependeDoWorldGuard() throws IOException, URISyntaxException {
        Path alvo = raizDasClasses().resolve("codes/biga/bigacore/Terreno.class");
        assertTrue(Files.exists(alvo), "Terreno tem de ser uma classe de primeira classe, "
                + "nao um record aninhado na ponte");
        assertTrue(!contemAgulha(Files.readAllBytes(alvo)),
                "Terreno passou a referenciar com.sk89q — usar o record voltaria a ligar a ponte");
    }
}
