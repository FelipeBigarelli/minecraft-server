package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O caderno do que havia antes da decoração, para o mapa voltar inteiro.
 *
 * <h2>Por que isto existe</h2>
 *
 * <p>A primeira decoração da Corrida foi escrita sem caderno nenhum: ela
 * pintava gelo compactado de gelo azul e, para desfazer, pintava gelo azul de
 * volta para compactado. A conta fecha num papel e não fechou na montanha —
 * <b>parte da estrada do Monte Akina JÁ É gelo azul</b>, a linha de corrida
 * rápida do mapa. Então a decoração não aparecia (azul sobre azul) e o
 * desfazer trocava o azul do mapa por compactado, deixando um xadrez de
 * remendo no meio da pista. Foi o "o final está feio e errado" do relato, e
 * não havia como consertar sem saber o que estava lá antes.
 *
 * <h2>Como funciona</h2>
 *
 * <p>Toda casa que a decoração escreve passa por {@link #por}, que anota o
 * material ANTERIOR na primeira vez que toca naquela casa — e só na primeira,
 * senão a segunda demão apagaria a memória da primeira. O caderno vai para
 * disco; {@link #restaurar} devolve casa por casa e rasga o caderno.
 *
 * <p>Com o caderno a decoração deixa de precisar ter medo: pode substituir
 * folha de árvore, grama e pedra para levantar o pórtico, em vez de só ocupar
 * o vazio e sair meio pórtico. Era o outro metade do mesmo relato.
 *
 * <h2>Um caderno por percurso</h2>
 *
 * <p>Porque o rodízio decora um percurso por rodada e tem de poder desfazer os
 * outros quatro sem tê-los decorado nesta subida do servidor. Em disco, e não
 * em memória, pelo mesmo motivo: o servidor reinicia entre uma rodada e a
 * próxima, e um caderno perdido é um pórtico plantado na montanha para sempre.
 */
public final class EnfeiteDaPista {

    /** Teto de casas anotadas, para um caderno torto não comer a memória. */
    public static final int MAXIMO_DE_CASAS = 20_000;

    private final File arquivo;
    private final Map<Long, Material> antes = new LinkedHashMap<>();
    private boolean lido;

    public EnfeiteDaPista(File pasta, String percurso) {
        this.arquivo = new File(pasta, percurso + ".enfeite");
    }

    /** Onde ficam os cadernos, dentro da pasta do plugin. */
    public static File pastaDosCadernos(BigaCore plugin) {
        return new File(plugin.getDataFolder(), "enfeites/corrida");
    }

    // ------------------------------------------------------------------
    //  Escrever
    // ------------------------------------------------------------------

    /**
     * Põe um bloco anotando o que havia ali.
     *
     * @return true se o bloco mudou de verdade
     */
    public boolean por(World mundo, int x, int y, int z, Material material) {
        Block onde = mundo.getBlockAt(x, y, z);
        Material tinha = onde.getType();
        if (tinha == material) {
            return false;
        }
        carregar();
        long chave = ArenaComum.chave(x, y, z);
        if (!antes.containsKey(chave) && antes.size() < MAXIMO_DE_CASAS) {
            antes.put(chave, tinha);
        }
        onde.setType(material, false);
        return true;
    }

    // ------------------------------------------------------------------
    //  Desfazer
    // ------------------------------------------------------------------

    /**
     * Devolve cada casa ao material anterior e rasga o caderno.
     *
     * <p>Devolve mesmo que o bloco de hoje não seja o que a decoração pôs: se
     * alguém mexeu ali no meio, o que estava no mapa continua sendo a resposta
     * certa — o caderno é a verdade sobre o MAPA, não sobre a decoração.
     */
    public int restaurar(World mundo) {
        carregar();
        int postos = 0;
        for (Map.Entry<Long, Material> casa : antes.entrySet()) {
            long chave = casa.getKey();
            Block onde = mundo.getBlockAt(ArenaComum.xDaChave(chave),
                    ArenaComum.yDaChave(chave), ArenaComum.zDaChave(chave));
            if (onde.getType() != casa.getValue()) {
                onde.setType(casa.getValue(), false);
                postos++;
            }
        }
        antes.clear();
        if (arquivo.isFile() && !arquivo.delete()) {
            arquivo.deleteOnExit();
        }
        return postos;
    }

    public int quantasCasas() {
        carregar();
        return antes.size();
    }

    // ------------------------------------------------------------------
    //  Disco
    // ------------------------------------------------------------------

    /** Lê o caderno do disco, uma vez por objeto. */
    public void carregar() {
        if (lido) {
            return;
        }
        lido = true;
        if (!arquivo.isFile()) {
            return;
        }
        try {
            for (String linha : Files.readAllLines(arquivo.toPath(),
                    StandardCharsets.UTF_8)) {
                long[] casa = lerLinha(linha);
                if (casa == null) {
                    continue;
                }
                Material qual = Material.matchMaterial(
                        linha.trim().split("\\s+")[3]);
                if (qual != null && antes.size() < MAXIMO_DE_CASAS) {
                    antes.put(ArenaComum.chave((int) casa[0], (int) casa[1],
                            (int) casa[2]), qual);
                }
            }
        } catch (IOException | RuntimeException naoDeu) {
            // Caderno ilegível é pior do que caderno nenhum só se derrubar o
            // servidor. Não derruba: o que acontece é a decoração não sair,
            // e isso aparece na hora.
            antes.clear();
        }
    }

    public void salvar() {
        try {
            File pasta = arquivo.getParentFile();
            if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
                return;
            }
            if (antes.isEmpty()) {
                if (arquivo.isFile() && !arquivo.delete()) {
                    arquivo.deleteOnExit();
                }
                return;
            }
            List<String> linhas = new ArrayList<>(antes.size() + 3);
            linhas.add("# o que havia nestas casas antes da decoração");
            linhas.add("# NÃO apague à mão: sem ele a montanha não volta");
            for (Map.Entry<Long, Material> casa : antes.entrySet()) {
                long chave = casa.getKey();
                linhas.add(ArenaComum.xDaChave(chave) + " "
                        + ArenaComum.yDaChave(chave) + " "
                        + ArenaComum.zDaChave(chave) + " "
                        + casa.getValue().name());
            }
            Files.write(arquivo.toPath(), linhas, StandardCharsets.UTF_8);
        } catch (IOException naoDeu) {
            // Mesmo raciocínio do carregar.
        }
    }

    /**
     * Os três números de uma linha do caderno, ou null se a linha não serve.
     *
     * <p>Separada e pura para o teste do formato não precisar de disco: um
     * caderno que não volta a ser lido é um pórtico eterno na montanha, e isso
     * só apareceria meses depois.
     */
    static long[] lerLinha(String linha) {
        if (linha == null) {
            return null;
        }
        String limpa = linha.trim();
        if (limpa.isEmpty() || limpa.startsWith("#")) {
            return null;
        }
        String[] partes = limpa.split("\\s+");
        if (partes.length < 4) {
            return null;
        }
        try {
            return new long[]{Long.parseLong(partes[0]),
                    Long.parseLong(partes[1]), Long.parseLong(partes[2])};
        } catch (NumberFormatException naoEhNumero) {
            return null;
        }
    }
}
