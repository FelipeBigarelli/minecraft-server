package codes.biga.bigacore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma construção estudada: paleta de blocos + grade de índices.
 *
 * O formato é o mesmo do .schem do WorldEdit (ordem YZX, índices em
 * varint), para que no futuro dê para exportar sem reescrever nada.
 *
 * Os índices não vão para o YAML como lista de números: 12 mil entradas
 * viram um arquivo de megabytes e a leitura fica lenta. Eles vão como um
 * bloco único em Base64.
 */
public final class Planta {

    /** Teto de segurança. Acima disso o estudo é recusado. */
    public static final int LIMITE_BLOCOS = 120_000;

    private final String nome;
    private final int largura;
    private final int altura;
    private final int comprimento;
    private final List<String> paleta;
    private final int[] blocos;

    /** Cache de BlockData. Montar a partir da string é caro para repetir. */
    private BlockData[] cache;

    private Planta(String nome, int largura, int altura, int comprimento,
                   List<String> paleta, int[] blocos) {
        this.nome = nome;
        this.largura = largura;
        this.altura = altura;
        this.comprimento = comprimento;
        this.paleta = paleta;
        this.blocos = blocos;
    }

    // ------------------------------------------------------------- estudo

    /**
     * Lê a região entre dois cantos (inclusive) e guarda tudo na memória.
     *
     * @throws IllegalArgumentException se os cantos estiverem em mundos
     *         diferentes ou a região passar do {@link #LIMITE_BLOCOS}
     */
    public static Planta estudar(String nome, Location a, Location b) {
        World mundo = a.getWorld();
        if (mundo == null || !mundo.equals(b.getWorld())) {
            throw new IllegalArgumentException("Os dois cantos precisam estar no mesmo mundo.");
        }

        int x1 = Math.min(a.getBlockX(), b.getBlockX());
        int y1 = Math.min(a.getBlockY(), b.getBlockY());
        int z1 = Math.min(a.getBlockZ(), b.getBlockZ());
        int largura = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
        int altura = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
        int comprimento = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;

        long volume = (long) largura * altura * comprimento;
        if (volume > LIMITE_BLOCOS) {
            throw new IllegalArgumentException(
                    "Região grande demais: " + volume + " blocos (limite " + LIMITE_BLOCOS + ").");
        }

        Map<String, Integer> indice = new HashMap<>();
        List<String> paleta = new ArrayList<>();
        int[] blocos = new int[(int) volume];

        for (int y = 0; y < altura; y++) {
            for (int z = 0; z < comprimento; z++) {
                for (int x = 0; x < largura; x++) {
                    String dados = mundo.getBlockAt(x1 + x, y1 + y, z1 + z)
                            .getBlockData().getAsString();
                    Integer id = indice.get(dados);
                    if (id == null) {
                        id = paleta.size();
                        indice.put(dados, id);
                        paleta.add(dados);
                    }
                    blocos[(y * comprimento + z) * largura + x] = id;
                }
            }
        }
        return new Planta(nome, largura, altura, comprimento, paleta, blocos);
    }

    // ------------------------------------------------------------ consulta

    public String nome() {
        return nome;
    }

    public int largura() {
        return largura;
    }

    public int altura() {
        return altura;
    }

    public int comprimento() {
        return comprimento;
    }

    public int total() {
        return blocos.length;
    }

    public int xDe(int cursor) {
        return cursor % largura;
    }

    public int yDe(int cursor) {
        return cursor / (largura * comprimento);
    }

    public int zDe(int cursor) {
        return (cursor / largura) % comprimento;
    }

    /** BlockData da posição linear informada, ou null se a string for inválida. */
    public BlockData dadosEm(int cursor) {
        if (cache == null) {
            cache = new BlockData[paleta.size()];
        }
        int id = blocos[cursor];
        BlockData pronto = cache[id];
        if (pronto == null) {
            try {
                pronto = Bukkit.createBlockData(paleta.get(id));
            } catch (IllegalArgumentException erro) {
                // Bloco de uma versão que o servidor não conhece mais.
                // Deixa como ar em vez de derrubar a obra inteira.
                pronto = Bukkit.createBlockData("minecraft:air");
            }
            cache[id] = pronto;
        }
        return pronto;
    }

    /**
     * Escreve a planta inteira de uma vez, sem animação.
     *
     * Usado para desfazer: aí não faz sentido ver a obra "desconstruindo".
     * applyPhysics fica desligado para a água não escorrer e a tocha não
     * cair enquanto a volta ainda está pela metade.
     */
    public void aplicar(World mundo, int ox, int oy, int oz) {
        for (int i = 0; i < blocos.length; i++) {
            mundo.getBlockAt(ox + xDe(i), oy + yDe(i), oz + zDe(i))
                    .setBlockData(dadosEm(i), false);
        }
    }

    // ------------------------------------------------------------- disco

    public void salvar(File arquivo) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("nome", nome);
        cfg.set("largura", largura);
        cfg.set("altura", altura);
        cfg.set("comprimento", comprimento);
        cfg.set("paleta", paleta);
        cfg.set("blocos", Base64.getEncoder().encodeToString(codificar(blocos)));
        cfg.save(arquivo);
    }

    public static Planta carregar(File arquivo) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(arquivo);
        int largura = cfg.getInt("largura");
        int altura = cfg.getInt("altura");
        int comprimento = cfg.getInt("comprimento");
        List<String> paleta = cfg.getStringList("paleta");
        String codificado = cfg.getString("blocos", "");

        int esperado = largura * altura * comprimento;
        int[] blocos = decodificar(Base64.getDecoder().decode(codificado), esperado);
        String nome = cfg.getString("nome", arquivo.getName().replace(".yml", ""));
        return new Planta(nome, largura, altura, comprimento, paleta, blocos);
    }

    // ------------------------------------------------------------ varint

    private static byte[] codificar(int[] valores) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream(valores.length);
        for (int valor : valores) {
            int n = valor;
            while (true) {
                if ((n & ~0x7F) == 0) {
                    saida.write(n);
                    break;
                }
                saida.write((n & 0x7F) | 0x80);
                n >>>= 7;
            }
        }
        return saida.toByteArray();
    }

    private static int[] decodificar(byte[] bruto, int quantos) {
        int[] saida = new int[quantos];
        int pos = 0;
        for (int i = 0; i < quantos && pos < bruto.length; i++) {
            int valor = 0;
            int desloc = 0;
            while (pos < bruto.length) {
                int b = bruto[pos++] & 0xFF;
                valor |= (b & 0x7F) << desloc;
                if ((b & 0x80) == 0) {
                    break;
                }
                desloc += 7;
            }
            saida[i] = valor;
        }
        return saida;
    }
}
