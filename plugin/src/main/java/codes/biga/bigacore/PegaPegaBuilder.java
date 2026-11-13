package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Levanta o pátio do Pega-Pega.
 *
 * <h2>O que este construtor confere antes de pôr um bloco</h2>
 *
 * Um pátio de dois andares tem quatro jeitos de sair errado sem lançar
 * exceção nenhuma: a galeria virar um arco em vez de um anel (e aí há um lado
 * do pátio em que ninguém dá a volta); uma escada com degrau de dois (e aí ela
 * não sobe); o fosso ficar largo demais (e aí o plató do meio é decoração) ou
 * estreito demais (e aí não é fosso, é rachadura); e uma ilha de chão sem
 * ligação com o resto (e aí quem cai nela está preso).
 *
 * <p>Todas as quatro são conferidas aqui, no mapa, antes da obra — e é o mapa
 * que o teste também anda.
 */
public final class PegaPegaBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_pega_painel";

    private final BigaCore plugin;
    private int contador;

    public PegaPegaBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        PegaPegaCatalog planta = plugin.pegaCatalog();
        Map<Long, Integer> mapa = planta.celulas();

        String problema = conferirPatio(mapa, planta);
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Pátio: piso de raio " + planta.raio() + " na fiada "
                    + planta.topoY() + ", galeria de " + planta.anelDentro()
                    + " a " + planta.anelFora() + " na fiada " + planta.galeriaY()
                    + ", plató de raio " + planta.platoRaio() + ", "
                    + planta.escadas() + " escadas e " + planta.buracos()
                    + " buracos. Pavio da primeira rodada: "
                    + planta.pavioDaRodada(1) + "s. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga pega desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);

        for (Map.Entry<Long, Integer> celula : mapa.entrySet()) {
            long chave = celula.getKey();
            por(mundo, ArenaComum.daChave(chave, 40),
                    planta.topoY() + ArenaComum.daChave(chave, 20),
                    ArenaComum.daChave(chave, 0),
                    planta.material(celula.getValue()));
        }
        luzes(mundo, planta);
        marcos(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Pátio levantado: " + contador + " blocos, "
                + planta.escadas() + " escadas e " + planta.buracos()
                + " buracos de queda. O spawn do mundo foi posto no saguão.",
                contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        PegaPegaCatalog planta = plugin.pegaCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
        return new Resultado(true, "Pátio apagado: " + contador + " blocos.", contador);
    }

    @Override
    public Resultado textos(World mundo) {
        PegaPegaCatalog planta = plugin.pegaCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    // ------------------------------------------------------------------
    //  Conferências — puras, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /** Roda as quatro conferências e devolve a primeira reclamação. */
    static String conferirPatio(Map<Long, Integer> mapa, PegaPegaCatalog planta) {
        String problema = conferirGaleria(mapa, planta.anelDentro(),
                planta.anelFora(), planta.anelAltura());
        if (problema == null) {
            problema = conferirEscadas(mapa, planta.escadas(),
                    planta.escadaLargura(), planta.raio(), planta.anelAltura());
        }
        if (problema == null) {
            problema = conferirFosso(mapa, planta.platoRaio(), planta.anelDentro(),
                    planta.anelFora(), planta.anelAltura());
        }
        if (problema == null) {
            problema = conferirLigacao(mapa, planta.raio(), planta.muroAltura());
        }
        return problema;
    }

    /**
     * A galeria é um anel FECHADO.
     *
     * Dois pedidos: ela é toda ligada por vizinhos de quatro (dá para andar de
     * qualquer ponto a qualquer ponto sem sair dela) e nenhuma célula tem
     * menos de dois vizinhos (não há ponta de corredor em que alguém seja
     * encurralado). Um anel com um corte em algum lugar passa no primeiro
     * pedido e falha no segundo, e é o defeito mais fácil de produzir: basta
     * um buraco de queda largo demais.
     */
    static String conferirGaleria(Map<Long, Integer> mapa, int dentro, int fora,
                                  int altura) {
        List<long[]> anel = new ArrayList<>();
        Set<Long> doAnel = new HashSet<>();
        for (int x = -fora; x <= fora; x++) {
            for (int z = -fora; z <= fora; z++) {
                if (!PegaPegaCatalog.noAnel(x, z, dentro, fora)) {
                    continue;
                }
                if (!PegaPegaCatalog.daParaFicar(mapa, x, altura, z)) {
                    continue;
                }
                anel.add(new long[]{x, z});
                doAnel.add(ArenaComum.chave(x, 0, z));
            }
        }
        if (anel.isEmpty()) {
            return "A galeria saiu vazia: confira anel-dentro e anel-fora.";
        }
        for (long[] casa : anel) {
            int vizinhos = 0;
            for (int[] passo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (doAnel.contains(ArenaComum.chave((int) casa[0] + passo[0], 0,
                        (int) casa[1] + passo[1]))) {
                    vizinhos++;
                }
            }
            if (vizinhos < 2) {
                return "A galeria tem uma ponta sem saída em (" + casa[0] + ","
                        + casa[1] + "): quem for encurralado ali não tem para "
                        + "onde ir. Diminua buraco-tamanho ou alargue o anel.";
            }
        }
        int alcancadas = andarPorPerto(doAnel, (int) anel.get(0)[0],
                (int) anel.get(0)[1]);
        if (alcancadas != doAnel.size()) {
            return "A galeria ficou partida em pedaços: só " + alcancadas
                    + " das " + doAnel.size() + " casas se alcançam andando.";
        }
        return null;
    }

    /** Quantas casas se alcançam andando por vizinhos de quatro. */
    private static int andarPorPerto(Set<Long> casas, int x0, int z0) {
        Set<Long> vistas = new HashSet<>();
        Deque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[]{x0, z0});
        vistas.add(ArenaComum.chave(x0, 0, z0));
        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            for (int[] passo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = onde[0] + passo[0];
                int z = onde[1] + passo[1];
                long chave = ArenaComum.chave(x, 0, z);
                if (casas.contains(chave) && vistas.add(chave)) {
                    fila.add(new int[]{x, z});
                }
            }
        }
        return vistas.size();
    }

    /**
     * Cada escada sobe um degrau por vez, começa no piso e chega na galeria.
     *
     * Degrau de dois não sobe: o jogador fica batendo na quina. E escada que
     * sobe certinho mas termina a uma casa da galeria não leva a lugar nenhum
     * — é o defeito que o recorte do disco em células produz sozinho, e que o
     * patamar do último degrau existe para tapar.
     */
    static String conferirEscadas(Map<Long, Integer> mapa, int quantas,
                                  int largura, int raio, int anelAltura) {
        List<int[]> passos = PegaPegaCatalog.escada(quantas, largura, raio,
                anelAltura);
        Set<Long> degraus = new HashSet<>();
        for (int[] passo : passos) {
            degraus.add(ArenaComum.chave(passo[0], passo[1], passo[2]));
        }
        int deCima = 0;
        int deBaixo = 0;
        for (int[] passo : passos) {
            int x = passo[0];
            int fiada = passo[1];
            int z = passo[2];
            if (!PegaPegaCatalog.daParaFicar(mapa, x, fiada, z)) {
                return "O degrau em (" + x + "," + fiada + "," + z + ") não tem "
                        + "pé direito: tem bloco em cima dele.";
            }
            boolean ligado = false;
            for (int[] lado : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int sobe = -1; sobe <= 1; sobe++) {
                    if (PegaPegaCatalog.daParaFicar(mapa, x + lado[0],
                            fiada + sobe, z + lado[1])) {
                        ligado = true;
                    }
                }
            }
            if (!ligado) {
                return "O degrau em (" + x + "," + fiada + "," + z + ") não "
                        + "encosta em nada que se possa pisar: a escada está "
                        + "solta no ar.";
            }
            if (fiada == 1) {
                deBaixo++;
            }
            if (fiada == anelAltura) {
                deCima++;
            }
        }
        if (deBaixo < quantas) {
            return "Só " + deBaixo + " degrau(s) começam na primeira fiada, e "
                    + "as escadas são " + quantas + ".";
        }
        if (deCima < quantas) {
            return "Só " + deCima + " degrau(s) chegam na fiada da galeria.";
        }
        return null;
    }

    /**
     * O fosso em volta do plató é saltável, e é mesmo um fosso.
     *
     * Dois limites, e os dois importam. Largo demais e o plató do meio é
     * enfeite: ninguém sobe nele, e um pedaço do pátio deixa de existir.
     * Estreito demais e não é fosso: dá para atravessar andando, e a decisão
     * "arrisco o salto ou dou a volta?" desaparece.
     */
    static String conferirFosso(Map<Long, Integer> mapa, int platoRaio,
                                int anelDentro, int anelFora, int altura) {
        List<int[]> beiraDoPlato = new ArrayList<>();
        for (int x = -platoRaio; x <= platoRaio; x++) {
            for (int z = -platoRaio; z <= platoRaio; z++) {
                if (!PegaPegaCatalog.noPlato(x, z, platoRaio)
                        || PegaPegaCatalog.noPlato(x, z, platoRaio - 1)) {
                    continue;
                }
                beiraDoPlato.add(new int[]{x, z});
            }
        }
        List<int[]> beiraDoAnel = new ArrayList<>();
        for (int x = -anelFora; x <= anelFora; x++) {
            for (int z = -anelFora; z <= anelFora; z++) {
                if (PegaPegaCatalog.noAnel(x, z, anelDentro, anelDentro)
                        && PegaPegaCatalog.daParaFicar(mapa, x, altura, z)) {
                    beiraDoAnel.add(new int[]{x, z});
                }
            }
        }
        if (beiraDoPlato.isEmpty() || beiraDoAnel.isEmpty()) {
            return "O plató ou a beira de dentro da galeria saíram vazios.";
        }
        double maior = 0;
        double menor = Double.MAX_VALUE;
        for (int[] daqui : beiraDoPlato) {
            double perto = Double.MAX_VALUE;
            for (int[] dali : beiraDoAnel) {
                perto = Math.min(perto, Math.hypot(daqui[0] - dali[0],
                        daqui[1] - dali[1]));
            }
            maior = Math.max(maior, perto);
            menor = Math.min(menor, perto);
        }
        if (maior > 3.5) {
            return "O fosso chega a " + String.format(java.util.Locale.ROOT,
                    "%.2f", maior) + " blocos num ponto: não se salta, e o "
                    + "plató do meio fica sendo enfeite. Suba plato-raio ou "
                    + "baixe anel-dentro.";
        }
        if (menor < 2.0) {
            return "O fosso tem só " + String.format(java.util.Locale.ROOT,
                    "%.2f", menor) + " blocos num ponto: atravessa-se andando, "
                    + "e aí não é fosso.";
        }
        return null;
    }

    /**
     * Tudo em que se pode pisar se alcança a partir do piso.
     *
     * Anda pelo pátio com as regras do jogador: passo para o lado subindo ou
     * descendo um, queda de qualquer altura e salto de até três blocos e meio
     * com um de subida. O que não for alcançado é ilha — e ilha num jogo de
     * perseguição é ou esconderijo perfeito, ou prisão.
     */
    static String conferirLigacao(Map<Long, Integer> mapa, int raioJogavel,
                                  int teto) {
        // Só o que está DENTRO do pátio entra na conta. O topo do muro é
        // chão em que dá para ficar de pé e em que ninguém consegue chegar —
        // de propósito, que é o que faz dele muro.
        List<int[]> casas = new ArrayList<>();
        for (int[] casa : PegaPegaCatalog.ondeDaParaFicar(mapa, raioJogavel, teto)) {
            if (ArenaComum.noDisco(casa[0], casa[2], raioJogavel)) {
                casas.add(casa);
            }
        }
        Set<Long> todas = new HashSet<>();
        for (int[] casa : casas) {
            todas.add(ArenaComum.chave(casa[0], casa[1], casa[2]));
        }
        if (todas.isEmpty()) {
            return "Não há uma única casa em que se possa ficar de pé.";
        }
        int[] inicio = casas.get(0);
        Set<Long> vistas = new HashSet<>();
        Deque<int[]> fila = new ArrayDeque<>();
        fila.add(inicio);
        vistas.add(ArenaComum.chave(inicio[0], inicio[1], inicio[2]));

        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    double longe = Math.hypot(dx, dz);
                    if (longe > 3.5) {
                        continue;
                    }
                    // Sobe no máximo um; desce o quanto for, porque cair é
                    // sempre possível e é meio caminho do pátio.
                    for (int dy = -teto; dy <= 1; dy++) {
                        long destino = ArenaComum.chave(onde[0] + dx,
                                onde[1] + dy, onde[2] + dz);
                        if (!todas.contains(destino) || vistas.contains(destino)) {
                            continue;
                        }
                        vistas.add(destino);
                        fila.add(new int[]{onde[0] + dx, onde[1] + dy, onde[2] + dz});
                    }
                }
            }
        }
        if (vistas.size() != todas.size()) {
            return "Ficaram " + (todas.size() - vistas.size()) + " casa(s) de "
                    + todas.size() + " sem ligação com o resto do pátio: quem "
                    + "cair lá fica preso.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Enfeite
    // ------------------------------------------------------------------

    /**
     * As luzes do pátio.
     *
     * Embaixo da galeria o piso é um corredor coberto, e corredor coberto sem
     * luz é corredor em que bicho nasce e o jogador não vê quem vem. As luzes
     * ficam no TETO daquele corredor — a barriga da galeria — e não no chão,
     * onde seriam tropeço.
     */
    private void luzes(World mundo, PegaPegaCatalog planta) {
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        int meio = (planta.anelDentro() + planta.anelFora()) / 2;
        int quantas = 12;
        for (int i = 0; i < quantas; i++) {
            double angulo = 2 * Math.PI * i / quantas;
            int x = (int) Math.round(Math.cos(angulo) * meio);
            int z = (int) Math.round(Math.sin(angulo) * meio);
            Block barriga = mundo.getBlockAt(x, planta.galeriaY(), z);
            if (barriga.getType().isAir()) {
                continue;
            }
            por(mundo, x, planta.galeriaY(), z, luz);
        }
        por(mundo, 0, planta.galeriaY(), 0,
                planta.cor("nucleo", Material.GOLD_BLOCK));
        for (int alto = 1; alto <= 3; alto++) {
            por(mundo, 0, planta.galeriaY() + alto, 0,
                    planta.cor("luz-fria", Material.END_ROD));
        }
    }

    /** Quatro marcos em volta do muro, para o pátio ter escala. */
    private void marcos(World mundo, PegaPegaCatalog planta) {
        Material estrutura = planta.cor("estrutura", Material.SMOOTH_QUARTZ);
        Material vidro = planta.cor("vidro", Material.YELLOW_STAINED_GLASS);
        Material luz = planta.cor("luz", Material.SHROOMLIGHT);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int d = planta.marcoDistancia();
        int altura = planta.marcoAltura();
        int y = planta.topoY();

        for (int[] eixo : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int cx = eixo[0] * d;
            int cz = eixo[1] * d;
            for (int alto = -4; alto <= altura; alto++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean miolo = dx == 0 && dz == 0;
                        Material qual = miolo && alto % 5 == 0 ? luz
                                : (miolo ? vidro : estrutura);
                        por(mundo, cx + dx, y + alto, cz + dz, qual);
                    }
                }
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, cx, y + altura + alto, cz, rod);
            }
        }
    }

    private int contarOcupados(World mundo, PegaPegaCatalog planta) {
        return ArenaComum.contarOcupados(mundo, planta.alcanceDaObra(),
                planta.fundoDaObra(), planta.tetoDaObra());
    }

    private void por(World mundo, int x, int y, int z, Material material) {
        mundo.getBlockAt(x, y, z).setType(material, false);
        contador++;
    }

    // ------------------------------------------------------------------
    //  Pontos
    // ------------------------------------------------------------------

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.pegaCatalog());
    }

    /** Onde um jogador nasce: em cima do piso, olhando para o meio. */
    public static Location pontoDaLargada(World mundo, PegaPegaCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]));
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde quem explodiu assiste: no alto do meio, olhando para baixo. */
    public static Location pontoDaPlateia(World mundo, PegaPegaCatalog planta) {
        Location onde = new Location(mundo, 0.5, planta.galeriaY() + 14, 0.5);
        onde.setYaw(180f);
        onde.setPitch(55f);
        return onde;
    }
}
