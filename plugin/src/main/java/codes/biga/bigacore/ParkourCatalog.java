package codes.biga.bigacore;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A planta do Parkour — e o percurso, que é GERADO aqui.
 *
 * <h2>O percurso é dado, não é obra</h2>
 *
 * {@link #percurso()} devolve a lista de plataformas. O construtor põe blocos
 * nela, o jogo lê checkpoint e chegada dela, o teste mede salto por salto
 * nela e o mímico anda por ela. Uma lista só, calculada de um jeito só: se o
 * construtor e o jogo calculassem cada um o seu, bastaria meio bloco de
 * diferença para o checkpoint ficar onde não há plataforma.
 *
 * <h2>Os limites de salto não são chute</h2>
 *
 * Saem da tabela do plugin de parkour procedural de referência, que é quem
 * tem de responder a mesma pergunta: até onde um jogador alcança. Subindo um
 * bloco, quatro de distância; no plano, cinco; descendo, cinco. Passar disso
 * é gerar um percurso impossível — e um percurso impossível não dá erro
 * nenhum, só fica lá, intransponível, parecendo que o jogador é ruim.
 *
 * <p>Por isso a geração CONFERE cada salto depois de arredondar para bloco
 * inteiro: o arredondamento mexe na distância real em até meio bloco para
 * cada lado, e meio bloco é a diferença entre o salto do jogo e o salto
 * impossível.
 */
public final class ParkourCatalog extends CatalogoDeArena {

    /** Os tipos de plataforma, na quarta posição de cada ponto do percurso. */
    public static final int LARGADA = 0;
    public static final int NORMAL = 1;
    public static final int CHECKPOINT = 2;
    public static final int CHEGADA = 3;

    public ParkourCatalog(BigaCore plugin) {
        super(plugin, "parkour.yml", "parkour", "parkour");
    }

    // ------------------------------------------------------------------
    //  Geometria
    // ------------------------------------------------------------------

    public int saltos() {
        return Math.max(4, config.getInt("arena.saltos", 32));
    }

    public int raio() {
        return Math.max(6, config.getInt("arena.raio", 11));
    }

    public int checkpointACada() {
        return Math.max(2, config.getInt("arena.checkpoint-a-cada", 6));
    }

    public int quedaAbaixo() {
        return Math.max(4, config.getInt("arena.queda-abaixo", 8));
    }

    @Override
    public int quedaY() {
        return topoY() - quedaAbaixo();
    }

    public int saguaoFolga() {
        return Math.max(6, config.getInt("arena.saguao-folga", 12));
    }

    /** A fiada da chegada: o cume do percurso. */
    public int cumeY() {
        List<int[]> pontos = percurso();
        return pontos.get(pontos.size() - 1)[1];
    }

    /**
     * O saguão fica acima do CUME, e não acima da largada.
     *
     * Esta é a diferença do parkour para os outros: a arena não é um disco
     * plano, é uma torre. Um saguão medido da base ficaria dentro do percurso,
     * e o jogador acabaria saltando dentro da sala de espera.
     */
    @Override
    public int saguaoY() {
        return cumeY() + saguaoFolga();
    }

    @Override
    public int alcanceDaObra() {
        return raio() + 14;
    }

    @Override
    public int fundoDaObra() {
        return topoY() - 14;
    }

    @Override
    public int tetoDaObra() {
        return saguaoY() + saguaoParapeito() + 6;
    }

    /** Os materiais que o construtor põe no caminho — e só eles. */
    public Set<Material> materiaisDoCaminho() {
        Set<Material> saida = EnumSet.noneOf(Material.class);
        saida.add(cor("salto", Material.MOSS_BLOCK));
        saida.add(cor("salto-duro", Material.LIME_TERRACOTTA));
        saida.add(cor("checkpoint", Material.LIME_CONCRETE));
        saida.add(cor("largada", Material.POLISHED_ANDESITE));
        saida.add(cor("chegada", Material.GOLD_BLOCK));
        return saida;
    }

    // ------------------------------------------------------------------
    //  O percurso
    // ------------------------------------------------------------------

    public List<int[]> percurso() {
        return percurso(saltos(), raio(), topoY(), checkpointACada());
    }

    /**
     * Gera o percurso inteiro: uma espiral de plataformas subindo a árvore.
     *
     * @return pontos {x, y, z, tipo}, começando na largada.
     */
    public static List<int[]> percurso(int saltos, int raio, int topoY,
                                       int checkpointACada) {
        List<int[]> pontos = new ArrayList<>();
        int x = raio;
        int y = topoY;
        int z = 0;
        double angulo = 0;
        pontos.add(new int[]{x, y, z, LARGADA});

        for (int i = 1; i <= saltos; i++) {
            int dy = subidaDoSalto(i, saltos);
            double corda = cordaDoSalto(i, saltos);
            double limite = alcanceMaximo(dy);

            // Procura, girando de pouco em pouco, a primeira casa inteira cuja
            // distância REAL fique na faixa pedida. O arredondamento para
            // bloco é o que obriga esta procura: o ângulo certo na conta vira
            // meio bloco a mais ou a menos no mundo.
            double melhorAngulo = angulo;
            int melhorX = x;
            int melhorZ = z;
            double melhorErro = Double.MAX_VALUE;
            double passoIdeal = 2 * Math.asin(Math.min(1, corda / (2.0 * raio)));

            for (int k = 0; k <= 60; k++) {
                double tentativa = angulo + passoIdeal * (0.6 + k * 0.03);
                int nx = (int) Math.round(raio * Math.cos(tentativa));
                int nz = (int) Math.round(raio * Math.sin(tentativa));
                double distancia = Math.hypot(nx - x, nz - z);
                if (distancia < 1.5 || distancia > limite) {
                    continue;
                }
                double erro = Math.abs(distancia - corda);
                if (erro < melhorErro) {
                    melhorErro = erro;
                    melhorAngulo = tentativa;
                    melhorX = nx;
                    melhorZ = nz;
                }
            }

            angulo = melhorAngulo;
            x = melhorX;
            z = melhorZ;
            y += dy;

            int tipo = i == saltos ? CHEGADA
                    : (i % checkpointACada == 0 ? CHECKPOINT : NORMAL);
            pontos.add(new int[]{x, y, z, tipo});
        }
        return pontos;
    }

    /**
     * Quanto sobe cada salto, com a dificuldade crescendo.
     *
     * O primeiro terço sobe sempre: é escada, qualquer um passa. O último
     * intercala plano com subida, que é onde o salto fica longo.
     */
    static int subidaDoSalto(int i, int saltos) {
        if (i <= saltos / 3) {
            return 1;
        }
        if (i <= 2 * saltos / 3) {
            return i % 2 == 0 ? 0 : 1;
        }
        return i % 3 == 0 ? 0 : 1;
    }

    /** A distância pedida para cada salto, também crescendo. */
    static double cordaDoSalto(int i, int saltos) {
        if (i <= saltos / 3) {
            return 2.2;
        }
        if (i <= 2 * saltos / 3) {
            return subidaDoSalto(i, saltos) == 0 ? 3.4 : 2.8;
        }
        return subidaDoSalto(i, saltos) == 0 ? 4.2 : 3.2;
    }

    /**
     * Até onde se alcança, por diferença de altura.
     *
     * Subindo um bloco o alcance encolhe — é o que a tabela do plugin de
     * referência diz, e é o que qualquer um sente no jogo: pular para cima
     * come distância.
     */
    static double alcanceMaximo(int dy) {
        if (dy >= 1) {
            return 4.0;
        }
        return 5.0;
    }
}
