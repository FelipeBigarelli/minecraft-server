package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * Levanta o campo de tiro do ARCO E FLECHA.
 *
 * <h2>As duas conferências que não são geométricas</h2>
 *
 * {@link #conferirForca} confere um número do yml contra a física do arco do
 * Minecraft: a força mínima tem de RECUSAR 18 tiques de arco armado e ACEITAR
 * 19. O limite de baixo é o que mata o tiro sem mira; o de cima é o que
 * impede o arco de "às vezes não atirar" quando o cliente e o servidor
 * discordam em um tique. Sem as duas, o número no yml parece razoável e o
 * jogo fica errado de um dos dois jeitos.
 *
 * <p>{@link #conferirCobertura} confere a proporção de muro no chão. Pouca
 * cobertura e os dezesseis se eliminam em dez segundos por flecha perdida;
 * muita e ninguém se acerta nunca. É a única conferência deste jogo que mede
 * a arena como um todo em vez de medir uma peça dela.
 */
public final class ArcoBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_arco_painel";

    /** O painel do tiro, refeito quando alguém é eliminado. */
    public static final String TAG_TIRO = "bigacore_arco_tiro";

    /** Quanto do chão a cobertura tem de ocupar, no mínimo e no máximo. */
    private static final double COBERTURA_MINIMA = 0.03;
    private static final double COBERTURA_MAXIMA = 0.20;

    /** Que folga tem de sobrar entre dois muros do mesmo anel. */
    private static final int VAO_DO_ANEL = 3;

    private final BigaCore plugin;
    private int contador;

    public ArcoBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        ArcoCatalog planta = plugin.arcoCatalog();

        String problema = conferirForca(planta.forcaMinima());
        if (problema == null) {
            problema = conferirCobertura(planta.aneis(), planta.murosPorAnel(),
                    planta.muroComprimento(), planta.muroAltura(),
                    planta.paredeAltura(), planta.raio());
        }
        if (problema == null) {
            problema = conferirRelogio(planta.gracaSegundos(),
                    planta.tempoLimite(), planta.raio(), planta.aneis());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            int cobertura = planta.blocosDaCobertura().size();
            int chao = ArcoCatalog.blocosDoChao(planta.raio());
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Campo de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + ", com " + cobertura
                    + " blocos de cobertura em " + chao + " de chão ("
                    + Math.round(cobertura * 1000.0 / chao) / 10.0
                    + "%), muros de " + planta.muroAltura()
                    + " fiadas nos anéis " + planta.aneis()
                    + ". Força mínima " + planta.forcaMinima() + " = "
                    + ArcoCatalog.tiquesParaForca(planta.forcaMinima())
                    + " tiques de arco. Saguão em y " + planta.saguaoY() + ".",
                    0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga arco desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_TIRO);
        chao(mundo, planta);
        muroDeFora(mundo, planta);
        cobertura(mundo, planta);
        alvos(mundo, planta);
        torres(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);
        placasDoCampo(mundo, planta);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "ARCO E FLECHA levantado: " + contador
                + " blocos e " + planta.blocosDaCobertura().size()
                + " blocos de cobertura. O spawn do mundo foi posto no "
                + "saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        ArcoCatalog planta = plugin.arcoCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_TIRO);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "ARCO E FLECHA apagado: " + contador
                + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        ArcoCatalog planta = plugin.arcoCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        ArenaComum.limparPaineis(mundo, TAG_TIRO);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        placasDoCampo(mundo, planta);
        return new Resultado(true, "Letreiros e placas do campo refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * A força mínima recusa meia puxada e aceita a puxada cheia.
     *
     * As duas pontas vêm da física: {@code forcaDe(18) = 0,87} é meia puxada
     * em tempo mas quase tudo em força, e tem de ser recusada;
     * {@code forcaDe(19) = 0,93} é a puxada cheia que o servidor às vezes vê
     * um tique antes do cliente, e tem de ser aceita.
     */
    static String conferirForca(double forcaMinima) {
        if (forcaMinima <= 0.1 || forcaMinima > 1.0) {
            return "A força mínima é " + forcaMinima + " e tem de ficar entre "
                    + "0,1 e 1,0.";
        }
        double meia = ArcoCatalog.forcaDe(ArcoCatalog.TIQUES_DO_ARCO - 2);
        if (forcaMinima <= meia) {
            return "A força mínima é " + forcaMinima + " e um tiro de "
                    + (ArcoCatalog.TIQUES_DO_ARCO - 2) + " tiques de arco já "
                    + "tem força " + Math.round(meia * 100) / 100.0
                    + ": o tiro sem mira passaria, e a jogada certa voltaria "
                    + "a ser encher o ar de flechas.";
        }
        double quaseCheia = ArcoCatalog.forcaDe(ArcoCatalog.TIQUES_DO_ARCO - 1);
        if (forcaMinima > quaseCheia) {
            return "A força mínima é " + forcaMinima + " e a puxada de "
                    + (ArcoCatalog.TIQUES_DO_ARCO - 1) + " tiques dá só "
                    + Math.round(quaseCheia * 1000) / 1000.0 + ": o arco "
                    + "chega ao máximo um tique antes de vez em quando, por "
                    + "diferença entre cliente e servidor, e o jogador veria "
                    + "o arco \"às vezes não atirar\" sem entender por quê.";
        }
        return null;
    }

    /**
     * A cobertura cobre, e deixa linha de tiro.
     *
     * Duas pontas de um mesmo número: sem muro, um jogo de um acerto elimina
     * acaba em dez segundos por flecha perdida; com muro demais, ninguém se
     * acerta e a partida vai no relógio.
     */
    static String conferirCobertura(List<Integer> aneis,
                                    List<Integer> murosPorAnel,
                                    int comprimento, int muroAltura,
                                    int paredeAltura, int raio) {
        if (aneis.isEmpty()) {
            return "Não há anel de cobertura nenhum: o campo é uma mesa e a "
                    + "partida acaba na primeira flecha perdida.";
        }
        if (murosPorAnel.size() != aneis.size()) {
            return "Há " + aneis.size() + " anéis e " + murosPorAnel.size()
                    + " contagens de muro: o yml não diz quantos muros cada "
                    + "anel tem.";
        }
        if (comprimento % 2 == 0) {
            return "O muro tem " + comprimento + " blocos de comprimento e "
                    + "precisa de número ímpar, senão ele não fica centrado "
                    + "na direção do anel.";
        }
        if (muroAltura + 2 > paredeAltura) {
            return "Os muros têm " + muroAltura + " fiadas e o muro de fora "
                    + paredeAltura + ": de cima de um muro de cobertura daria "
                    + "para pular o muro de fora.";
        }
        int anterior = 0;
        for (int k = 0; k < aneis.size(); k++) {
            int anel = aneis.get(k);
            int quantos = murosPorAnel.get(k);
            if (quantos < 1) {
                return "O anel " + (k + 1) + " tem " + quantos + " muros.";
            }
            if (anel <= anterior + 4) {
                return "O anel " + (k + 1) + " está no raio " + anel + " e o "
                        + "anterior no " + anterior + ": com menos de cinco "
                        + "blocos entre eles não dá para andar de um muro "
                        + "para o outro.";
            }
            if (anel > raio - 4) {
                return "O anel " + (k + 1) + " está no raio " + anel
                        + " e o campo tem raio " + raio + ": o muro ficaria "
                        + "encostado no muro de fora e viraria escada.";
            }
            double arco = 2 * Math.PI * anel / quantos;
            if (arco - comprimento < VAO_DO_ANEL) {
                return "No anel " + (k + 1) + " (raio " + anel + ") cabem "
                        + quantos + " muros de " + comprimento + " com "
                        + Math.round(arco - comprimento) + " bloco(s) de vão "
                        + "entre eles, e o mínimo é " + VAO_DO_ANEL
                        + ": o anel viraria uma parede fechada.";
            }
            anterior = anel;
        }

        int cobertura = ArcoCatalog.blocosDaCobertura(aneis, murosPorAnel,
                comprimento).size();
        int chao = ArcoCatalog.blocosDoChao(raio);
        double parte = cobertura / (double) Math.max(1, chao);
        if (parte < COBERTURA_MINIMA) {
            return "A cobertura ocupa " + Math.round(parte * 1000) / 10.0
                    + "% do chão e o mínimo é " + (COBERTURA_MINIMA * 100)
                    + "%: com tão pouco muro, um jogo de um acerto elimina "
                    + "acaba em dez segundos por flecha perdida.";
        }
        if (parte > COBERTURA_MAXIMA) {
            return "A cobertura ocupa " + Math.round(parte * 1000) / 10.0
                    + "% do chão e o máximo é " + (COBERTURA_MAXIMA * 100)
                    + "%: com tanto muro ninguém se acerta e a partida vai "
                    + "sempre no relógio.";
        }
        return null;
    }

    /** A graça dá para chegar a um muro, e não é a partida. */
    static String conferirRelogio(int graca, int tempoLimite, int raio,
                                  List<Integer> aneis) {
        int maisDeFora = aneis.isEmpty() ? 0 : aneis.get(aneis.size() - 1);
        // Um jogador andando faz uns 4,3 blocos por segundo.
        int correr = (int) Math.ceil(Math.max(1, raio - 3 - maisDeFora) / 4.3);
        if (graca < correr) {
            return "A graça é de " + graca + "s e correr da largada até o "
                    + "muro mais de fora leva " + correr + "s: alguém seria "
                    + "eliminado antes de ter onde se cobrir.";
        }
        if (graca > tempoLimite / 10) {
            return "A graça é de " + graca + "s num limite de " + tempoLimite
                    + "s: flecha que não mata por um décimo da partida vira "
                    + "treino de tiro, não partida.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    private void chao(World mundo, ArcoCatalog planta) {
        Material piso = planta.cor("piso", Material.MOSS_BLOCK);
        Material xadrez = planta.cor("piso-xadrez", Material.GRASS_BLOCK);
        Material beira = planta.cor("beira", Material.PACKED_MUD);
        int raio = planta.raio();
        int y = planta.topoY();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)) {
                    continue;
                }
                boolean naBeira = !ArenaComum.noDiscoMeio(x, z, raio - 1);
                por(mundo, x, y, z, naBeira ? beira
                        : (((x >> 3) + (z >> 3)) % 2 == 0 ? piso : xadrez));
            }
        }
    }

    /**
     * O muro de fora: segura a flecha perdida.
     *
     * Sem ele, toda flecha que erra vai para o vazio e o campo fica cercado
     * de nada — e quem está na beira só pode ser acertado de um lado, o que é
     * cobertura de graça para quem encosta nele. Com ele, a beira é o pior
     * lugar do campo: a flecha que erra volta de ricochete nenhuma, mas
     * também não há para onde recuar.
     */
    private void muroDeFora(World mundo, ArcoCatalog planta) {
        Material parede = planta.cor("parede", Material.PACKED_MUD);
        Material topo = planta.cor("parede-topo", Material.MUD_BRICKS);
        int raio = planta.raio();
        int y = planta.topoY();
        int alto = planta.paredeAltura();

        for (int x = -raio; x <= raio; x++) {
            for (int z = -raio; z <= raio; z++) {
                if (!ArenaComum.noDiscoMeio(x, z, raio)
                        || ArenaComum.noDiscoMeio(x, z, raio - 1)) {
                    continue;
                }
                for (int fiada = 1; fiada <= alto; fiada++) {
                    por(mundo, x, y + fiada, z, fiada == alto ? topo : parede);
                }
            }
        }
    }

    private void cobertura(World mundo, ArcoCatalog planta) {
        Material muro = planta.cor("cobertura", Material.MUD_BRICKS);
        Material capa = planta.cor("cobertura-topo", Material.MUD_BRICK_WALL);
        int y = planta.topoY();
        int alto = planta.muroAltura();

        for (int[] onde : planta.blocosDaCobertura()) {
            for (int fiada = 1; fiada <= alto; fiada++) {
                por(mundo, onde[0], y + fiada, onde[1],
                        fiada == alto ? capa : muro);
            }
        }
    }

    /** Refaz a cobertura inteira: as flechas da rodada anterior a comeram. */
    public int refazerCobertura(World mundo) {
        ArcoCatalog planta = plugin.arcoCatalog();
        contador = 0;
        cobertura(mundo, planta);
        return contador;
    }

    /**
     * A flecha comeu um bloco de cobertura?
     *
     * Só come cobertura: chão, muro de fora e alvo ficam. Deixar a flecha
     * comer o chão faria deste o quarto jogo de tirar chão do servidor, e
     * deixar comer o muro de fora abriria o campo para o vazio.
     */
    public boolean comerCobertura(World mundo, Block bloco) {
        ArcoCatalog planta = plugin.arcoCatalog();
        int y = planta.topoY();
        if (bloco.getY() <= y || bloco.getY() > y + planta.muroAltura()) {
            return false;
        }
        Material muro = planta.cor("cobertura", Material.MUD_BRICKS);
        Material capa = planta.cor("cobertura-topo", Material.MUD_BRICK_WALL);
        if (bloco.getType() != muro && bloco.getType() != capa) {
            return false;
        }
        bloco.setType(Material.AIR, false);
        return true;
    }

    /** Alvos de enfeite pendurados no muro de fora. */
    private void alvos(World mundo, ArcoCatalog planta) {
        Material alvo = planta.cor("alvo", Material.TARGET);
        Material moldura = planta.cor("alvo-moldura", Material.HAY_BLOCK);
        Material folha = planta.cor("folhagem",
                Material.FLOWERING_AZALEA_LEAVES);
        int y = planta.topoY() + 2;

        for (int[] onde : planta.pontosDosAlvos()) {
            BlockFace frente = ArcoCatalog.frenteDaPlaca(onde[0], onde[1]);
            int dx = -frente.getModX();
            int dz = -frente.getModZ();
            for (int lado = -1; lado <= 1; lado++) {
                int x = onde[0] + (dz == 0 ? 0 : lado);
                int z = onde[1] + (dx == 0 ? 0 : lado);
                for (int fiada = -1; fiada <= 1; fiada++) {
                    boolean meio = lado == 0 && fiada == 0;
                    por(mundo, x + dx, y + fiada, z + dz,
                            meio ? alvo : moldura);
                }
            }
            por(mundo, onde[0] + dx * 2, y - 2, onde[1] + dz * 2, folha);
        }
    }

    /** As placas do campo: as regras do arco, nos quatro rumos do meio. */
    public void placasDoCampo(World mundo, ArcoCatalog planta) {
        int y = planta.topoY() + 2;
        int longe = planta.aneis().isEmpty() ? 6
                : Math.max(4, planta.aneis().get(0) - 5);
        String cor = planta.corDoTexto();
        List<List<Component>> textos = List.of(
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>ARCO</b></color>"),
                        plugin.mini().deserialize("<white>flecha infinita</white>"),
                        plugin.mini().deserialize("<white>um acerto</white>"),
                        plugin.mini().deserialize("<gray>elimina</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>PUXE TUDO</b></color>"),
                        plugin.mini().deserialize("<white>um segundo</white>"),
                        plugin.mini().deserialize("<gray>tiro fraco é</gray>"),
                        plugin.mini().deserialize("<gray>recusado</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>OS MUROS</b></color>"),
                        plugin.mini().deserialize("<white>a flecha come</white>"),
                        plugin.mini().deserialize("<white>o muro</white>"),
                        plugin.mini().deserialize("<gray>não acampe</gray>")),
                List.of(plugin.mini().deserialize("<color:" + cor
                                + "><b>A GRAÇA</b></color>"),
                        plugin.mini().deserialize("<white>os primeiros</white>"),
                        plugin.mini().deserialize("<white>segundos</white>"),
                        plugin.mini().deserialize("<gray>não matam</gray>")));

        int[][] lados = {{0, -longe}, {longe, 0}, {0, longe}, {-longe, 0}};
        for (int k = 0; k < lados.length; k++) {
            int x = lados[k][0];
            int z = lados[k][1];
            Material poste = planta.cor("poste", Material.SPRUCE_FENCE);
            por(mundo, x, planta.topoY() + 1, z, poste);
            por(mundo, x, planta.topoY() + 3, z,
                    planta.cor("poste-luz", Material.LANTERN));
            BlockFace frente = ArcoCatalog.frenteDaPlaca(x, z);
            ArenaComum.escreverPlaca(mundo,
                    new int[]{x + frente.getModX(), y,
                            z + frente.getModZ()},
                    planta.materialDaPlaca(), frente, textos.get(k));
        }
    }

    private void torres(World mundo, ArcoCatalog planta) {
        Material torre = planta.cor("torre", Material.PACKED_MUD);
        Material luz = planta.cor("torre-luz", Material.LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int y = planta.topoY();
        int raio = planta.raio() + 5;

        for (int k = 0; k < 4; k++) {
            double angulo = Math.PI / 4 + k * Math.PI / 2;
            int x = (int) Math.round(Math.cos(angulo) * raio);
            int z = (int) Math.round(Math.sin(angulo) * raio);
            for (int alto = 0; alto <= planta.marcoAltura(); alto++) {
                por(mundo, x, y + alto, z, alto % 4 == 0 && alto > 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + planta.marcoAltura() + alto, z, rod);
            }
        }
    }

    /** O painel do tiro, no alto do meio do campo. */
    public void painelDoTiro(World mundo, ArcoCatalog planta,
                             Component texto) {
        ArenaComum.limparPaineis(mundo, TAG_TIRO);
        Location alvo = new Location(mundo, 0.5,
                planta.topoY() + planta.painelAltura(), 0.5);
        ArenaComum.painelVirado(mundo, alvo, texto,
                (float) planta.escalaTitulo(), planta.fundoDoTexto(), TAG_TIRO);
    }

    private int contarOcupados(World mundo, ArcoCatalog planta) {
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        Block bloco = mundo.getBlockAt(x, y, z);
        if (bloco.getType() != material) {
            bloco.setType(material, false);
            contador++;
        }
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.arcoCatalog());
    }

    public static Location pontoDaLargada(World mundo, ArcoCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    public static Location pontoDaPlateia(World mundo, ArcoCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 10, 0.5);
        onde.setYaw(180f);
        onde.setPitch(65f);
        return onde;
    }
}
