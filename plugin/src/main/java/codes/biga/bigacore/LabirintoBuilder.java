package codes.biga.bigacore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Levanta o labirinto do dia.
 *
 * <h2>O que se confere antes de pôr um bloco</h2>
 *
 * Um labirinto gerado tem um jeito de sair errado que nenhum olho pega: ser
 * IMPOSSÍVEL. A grade pode dizer que existe passagem entre duas células e o
 * mapa de blocos tapar aquela passagem; uma célula pode nascer ilhada; a
 * porta pode cair num dia em que ela está a cinco passos da sala. Nada disso
 * aparece numa foto da arena — aparece quando dezesseis pessoas estão dentro.
 * Por isso a conferência percorre o mapa de blocos de verdade, em largura, a
 * partir da porta, e exige chegar a cada casa de piso.
 *
 * <h2>Por que o labirinto é refeito a cada rodada</h2>
 *
 * Não é para limpar o giz — isso é consequência. É que o mapa muda à
 * meia-noite, e um servidor que fica dias de pé passaria a mostrar o
 * labirinto de terça para quem joga na quinta. Refazer custa catorze mil
 * blocos, que é menos do que custa a arena do Spleef, e paga pela promessa.
 */
public final class LabirintoBuilder implements ConstrutorDeArena {

    public static final String TAG_PAINEL = "bigacore_labirinto_painel";

    private final BigaCore plugin;
    private int contador;

    public LabirintoBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Entrada
    // ------------------------------------------------------------------

    @Override
    public Resultado construir(World mundo, boolean aplicar) {
        LabirintoCatalog planta = plugin.labirintoCatalog();

        String problema = conferirNumeros(planta.celulas(), planta.celula(),
                planta.parede(), planta.paredeAltura(), planta.sala(),
                planta.saidaLargura(), planta.saidaFundo());
        if (problema == null) {
            problema = conferirMapa(planta.celulas(), planta.celula(),
                    planta.parede(), planta.ligacoes(), planta.sala(),
                    planta.saida());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". Labirinto de " + planta.celulas() + "x" + planta.celulas()
                    + " células (" + planta.lado() + " blocos de lado) na fiada "
                    + planta.topoY() + ". Dia " + planta.diaDeHoje()
                    + ", semente " + planta.semente() + ": a porta é a "
                    + nomeDoLado(planta.saida()[2]) + " e fica a "
                    + planta.passosAteASaida() + " passos da sala. Saguão em y "
                    + planta.saguaoY() + ".", 0);
        }

        int ocupados = contarOcupados(mundo, planta);
        if (ocupados > 0) {
            return new Resultado(false, "Há " + ocupados + " bloco(s) dentro da área. "
                    + "Rode /biga labirinto desfazer CONFIRMAR antes.", 0);
        }

        contador = 0;
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        obra(mundo, planta);
        contador += SaguaoPadrao.construir(plugin, mundo, planta, TAG_PAINEL);

        Location pouso = SaguaoPadrao.pontoDoSaguao(mundo, planta);
        mundo.setSpawnLocation(pouso.getBlockX(), pouso.getBlockY(), pouso.getBlockZ());

        return new Resultado(true, "Labirinto levantado: " + contador
                + " blocos. A porta de hoje é a " + nomeDoLado(planta.saida()[2])
                + ", a " + planta.passosAteASaida() + " passos da sala. O spawn "
                + "do mundo foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        LabirintoCatalog planta = plugin.labirintoCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    Block bloco = mundo.getBlockAt(x, y, z);
                    if (!bloco.getType().isAir()) {
                        bloco.setType(Material.AIR, false);
                        contador++;
                    }
                }
            }
        }
        return new Resultado(true, "Labirinto apagado: " + contador + " blocos.",
                contador);
    }

    @Override
    public Resultado textos(World mundo) {
        LabirintoCatalog planta = plugin.labirintoCatalog();
        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        SaguaoPadrao.escreverTextos(plugin, mundo, planta, TAG_PAINEL);
        return new Resultado(true, "Letreiros refeitos.", 0);
    }

    /** Refaz só o labirinto, com o mapa de hoje. Usado no começo da rodada. */
    public int refazerLabirinto(World mundo) {
        LabirintoCatalog planta = plugin.labirintoCatalog();
        contador = 0;
        obra(mundo, planta);
        return contador;
    }

    private void obra(World mundo, LabirintoCatalog planta) {
        campo(mundo, planta);
        coracao(mundo, planta);
        patamar(mundo, planta);
        torres(mundo, planta);
    }

    public static String nomeDoLado(int lado) {
        return switch (lado) {
            case LabirintoCatalog.NORTE -> "NORTE";
            case LabirintoCatalog.SUL -> "SUL";
            case LabirintoCatalog.OESTE -> "OESTE";
            default -> "LESTE";
        };
    }

    // ------------------------------------------------------------------
    //  Conferência — pura, para o teste rodar sem servidor
    // ------------------------------------------------------------------

    /**
     * Os números do yml.
     *
     * As três exigências de PARIDADE existem por um motivo só e é geométrico:
     * com células, corredor e muro ímpares a célula do meio cai exatamente
     * sobre o (0,0) do mundo, que é onde o saguão fica e onde todo mundo
     * larga. Com qualquer um deles par a sala sai meio bloco torta, e meio
     * bloco torto num labirinto é um corredor que não fecha.
     */
    static String conferirNumeros(int celulas, int celula, int parede,
                                  int paredeAltura, int sala, int saidaLargura,
                                  int saidaFundo) {
        if (celulas < 7) {
            return "O labirinto tem " + celulas + " células de lado: menos de "
                    + "sete não dá labirinto, dá corredor.";
        }
        if (celulas % 2 == 0) {
            return "'celulas' é " + celulas + ", que é par: a sala do meio não "
                    + "cairia sobre o (0,0), que é onde fica o saguão e onde "
                    + "todo mundo larga. Use um número ímpar.";
        }
        if (celula % 2 == 0) {
            return "'celula' é " + celula + ", que é par: o corredor ficaria "
                    + "meio bloco torto em relação ao meio do mundo.";
        }
        if (parede % 2 == 0) {
            return "'parede' é " + parede + ", que é par: a grade sairia "
                    + "deslocada meio bloco do meio do mundo.";
        }
        if (paredeAltura < 3) {
            return "O muro tem " + paredeAltura + " bloco(s) de altura: com "
                    + "menos de três um jogador pula em cima dele e o "
                    + "labirinto deixa de existir.";
        }
        int meio = celulas / 2;
        if (meio - sala < 2) {
            return "A sala do meio tem raio " + sala + " num labirinto de "
                    + celulas + " células: sobram " + (meio - sala) + " anel(éis) "
                    + "de labirinto em volta dela, e com menos de dois a porta "
                    + "fica à vista da largada.";
        }
        if (saidaLargura < celula) {
            return "O patamar da porta tem " + saidaLargura + " de largura e o "
                    + "corredor tem " + celula + ": o patamar não pode ser mais "
                    + "estreito que o vão que ele recebe.";
        }
        if (saidaLargura % 2 == 0) {
            return "'saida-largura' é " + saidaLargura + ", que é par: o "
                    + "patamar não ficaria centrado no vão da porta.";
        }
        if (saidaFundo < 2) {
            return "O patamar da porta tem " + saidaFundo + " de fundo: com "
                    + "menos de dois não cabe o farol nem dá para pisar fora "
                    + "do vão.";
        }
        return null;
    }

    /**
     * O labirinto gerado.
     *
     * Duas conferências, e a segunda é a que importa. A primeira anda a GRADE
     * e exige que nenhuma célula fique ilhada. A segunda anda o MAPA DE
     * BLOCOS, em largura, a partir da porta, e exige chegar a cada casa de
     * piso — é ela que pega o caso em que a grade diz que passa e o mapa não
     * abriu o vão, que é um labirinto impossível sem nenhum erro na grade.
     */
    static String conferirMapa(int celulas, int celula, int parede,
                               boolean[] ligado, int sala, int[] saida) {
        int[] dist = LabirintoCatalog.distancias(celulas, ligado, sala);
        for (int j = 0; j < celulas; j++) {
            for (int i = 0; i < celulas; i++) {
                if (dist[LabirintoCatalog.indice(celulas, i, j)] < 0) {
                    return "A célula (" + i + "," + j + ") ficou ilhada: não "
                            + "há caminho dela até a sala do meio.";
                }
            }
        }

        int lado = LabirintoCatalog.lado(celulas, celula, parede);
        int[] mapa = LabirintoCatalog.mapaDeBlocos(celulas, celula, parede,
                ligado, sala, saida);

        int[] vao = LabirintoCatalog.vaoDaPorta(celulas, celula, parede, saida);
        int aberturas = 0;
        for (int u = 0; u < lado; u++) {
            for (int v = 0; v < lado; v++) {
                boolean naBorda = u < parede || v < parede
                        || u >= lado - parede || v >= lado - parede;
                if (naBorda && mapa[v * lado + u] == LabirintoCatalog.PISO) {
                    aberturas++;
                }
            }
        }
        int esperado = vao[1] * vao[3];
        if (aberturas != esperado) {
            return "O muro da borda tem " + aberturas + " bloco(s) de vão e a "
                    + "porta pede " + esperado + ": ou sobrou buraco na borda "
                    + "ou a porta não abriu.";
        }

        // Em largura pelo mapa de blocos, a partir do vão da porta.
        boolean[] visto = new boolean[lado * lado];
        Deque<int[]> fila = new ArrayDeque<>();
        int u0 = vao[0];
        int v0 = vao[2];
        visto[v0 * lado + u0] = true;
        fila.add(new int[]{u0, v0});
        int achados = 1;
        while (!fila.isEmpty()) {
            int[] onde = fila.poll();
            for (int[] rumo : LabirintoCatalog.RUMOS) {
                int u = onde[0] + rumo[0];
                int v = onde[1] + rumo[1];
                if (u < 0 || v < 0 || u >= lado || v >= lado) {
                    continue;
                }
                if (visto[v * lado + u] || mapa[v * lado + u] != LabirintoCatalog.PISO) {
                    continue;
                }
                visto[v * lado + u] = true;
                achados++;
                fila.add(new int[]{u, v});
            }
        }
        int pisos = 0;
        for (int casa : mapa) {
            if (casa == LabirintoCatalog.PISO) {
                pisos++;
            }
        }
        if (achados != pisos) {
            return "Andando a pé pelo mapa de blocos chega-se a " + achados
                    + " casas de piso das " + pisos + " que existem: "
                    + (pisos - achados) + " casa(s) de corredor ficaram "
                    + "sem ligação com a porta.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  A obra
    // ------------------------------------------------------------------

    /**
     * O piso e os muros, numa passada só.
     *
     * Uma passada que escreve TODA a coluna — o piso, o muro e o ar acima
     * dele. Limpar antes e construir depois seria duas passadas e o dobro de
     * blocos, e deixaria a arena meio feita por um instante entre as duas.
     */
    private void campo(World mundo, LabirintoCatalog planta) {
        int[] mapa = planta.mapa();
        int lado = planta.lado();
        int meio = planta.meio();
        int y = planta.topoY();
        int altura = planta.paredeAltura();
        int passo = planta.passo();
        int parede = planta.parede();

        Material piso = planta.cor("piso", Material.STONE_BRICKS);
        Material xadrez = planta.cor("piso-xadrez", Material.ANDESITE);
        Material muro = planta.cor("muro", Material.PRISMARINE_BRICKS);
        Material base = planta.cor("muro-base", Material.PRISMARINE);
        Material topo = planta.cor("muro-topo", Material.DARK_PRISMARINE);

        for (int v = 0; v < lado; v++) {
            for (int u = 0; u < lado; u++) {
                int x = u - meio;
                int z = v - meio;
                boolean eMuro = mapa[v * lado + u] == LabirintoCatalog.MURO;
                // O xadrez sai da célula a que a coluna PERTENCE, muro
                // incluído: um tabuleiro de quatro em quatro que dá ao
                // jogador uma régua para contar esquinas de longe.
                int ci = Math.max(0, (u - parede)) / passo;
                int cj = Math.max(0, (v - parede)) / passo;
                por(mundo, x, y, z, eMuro ? base
                        : ((ci + cj) % 2 == 0 ? piso : xadrez));
                for (int alto = 1; alto <= altura + 1; alto++) {
                    Material qual = Material.AIR;
                    if (eMuro && alto <= altura) {
                        qual = alto == altura ? topo : muro;
                    }
                    por(mundo, x, y + alto, z, qual);
                }
            }
        }
    }

    /**
     * O coração: a marca de ouro no meio da sala, e as colunas do salão.
     *
     * As quatro colunas não são enfeite posto de fora: são os mourões que
     * sobram onde as faixas de muro da sala se cruzam, e que a abertura de
     * passagem nunca derruba. Ganham material próprio aqui porque, de pé no
     * meio do salão, elas é que dizem ao jogador que aquilo é um lugar e não
     * um corredor largo.
     */
    private void coracao(World mundo, LabirintoCatalog planta) {
        Material ouro = planta.cor("coracao", Material.GOLD_BLOCK);
        Material beira = planta.cor("coracao-beira", Material.CHISELED_STONE_BRICKS);
        Material coluna = planta.cor("coracao-coluna", Material.POLISHED_DIORITE);
        int y = planta.topoY();
        int passo = planta.passo();
        int meiaFaixa = (planta.celula() - 1) / 2 + 1;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean dentro = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                boolean mourao = Math.abs(x) >= meiaFaixa && Math.abs(z) >= meiaFaixa;
                if (!mourao) {
                    por(mundo, x, y, z, dentro ? ouro : beira);
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dx = 0; dx < planta.parede(); dx++) {
                    for (int dz = 0; dz < planta.parede(); dz++) {
                        int x = sx * (meiaFaixa + dx);
                        int z = sz * (meiaFaixa + dz);
                        for (int alto = 1; alto <= planta.paredeAltura(); alto++) {
                            por(mundo, x, y + alto, z, coluna);
                        }
                    }
                }
            }
        }
    }

    /**
     * O patamar da porta, com o farol.
     *
     * O facho é a única informação que o jogo dá de graça, e é de propósito:
     * sem ele, achar a porta seria sorteio — dezesseis pessoas vasculhando
     * uma borda de duzentos blocos. Com ele, a direção é de todos e o que
     * está em jogo é o caminho, que é o que o labirinto tem para oferecer.
     */
    private void patamar(World mundo, LabirintoCatalog planta) {
        int[] porta = pontoDaPorta(planta);
        int[] rumo = LabirintoCatalog.RUMOS[planta.saida()[2]];
        int[] lado = tangente(rumo);
        int fundo = planta.saidaFundo();
        int meiaLargura = (planta.saidaLargura() - 1) / 2;
        int y = planta.topoY();
        int altura = planta.paredeAltura();

        Material piso = planta.cor("patamar", Material.POLISHED_DIORITE);
        Material beira = planta.cor("patamar-beira", Material.DARK_PRISMARINE);
        Material metal = planta.cor("farol-base", Material.IRON_BLOCK);

        for (int p = 1; p <= fundo + 1; p++) {
            for (int q = -meiaLargura - 1; q <= meiaLargura + 1; q++) {
                int x = porta[0] + rumo[0] * p + lado[0] * q;
                int z = porta[1] + rumo[1] * p + lado[1] * q;
                boolean parede = Math.abs(q) > meiaLargura || p > fundo;
                por(mundo, x, y, z, parede ? beira : piso);
                for (int alto = 1; alto <= altura + 1; alto++) {
                    Material qual = Material.AIR;
                    if (parede && alto <= altura) {
                        qual = alto == altura ? piso : beira;
                    }
                    por(mundo, x, y + alto, z, qual);
                }
            }
        }

        int pFarol = (fundo + 1) / 2;
        for (int p = pFarol - 1; p <= pFarol + 1; p++) {
            for (int q = -1; q <= 1; q++) {
                por(mundo, porta[0] + rumo[0] * p + lado[0] * q, y,
                        porta[1] + rumo[1] * p + lado[1] * q, metal);
            }
        }
        por(mundo, porta[0] + rumo[0] * pFarol, y + 1,
                porta[1] + rumo[1] * pFarol, Material.BEACON);
    }

    /** As quatro torres de canto: é por elas que o labirinto tem escala. */
    private void torres(World mundo, LabirintoCatalog planta) {
        Material torre = planta.cor("torre", Material.DARK_PRISMARINE);
        Material luz = planta.cor("torre-luz", Material.SEA_LANTERN);
        Material rod = planta.cor("luz-fria", Material.END_ROD);
        int meio = planta.meio();
        int y = planta.topoY();
        int altura = planta.marcoAltura();

        for (int[] canto : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int x = canto[0] * meio;
            int z = canto[1] * meio;
            for (int alto = 1; alto <= altura; alto++) {
                por(mundo, x, y + alto, z, alto % 5 == 0 ? luz : torre);
            }
            for (int alto = 1; alto <= 3; alto++) {
                por(mundo, x, y + altura + alto, z, rod);
            }
        }
    }

    private int contarOcupados(World mundo, LabirintoCatalog planta) {
        int alcance = planta.alcanceDaObra();
        int ocupados = 0;
        for (int x = -alcance; x <= alcance; x++) {
            for (int z = -alcance; z <= alcance; z++) {
                for (int y = planta.fundoDaObra(); y <= planta.tetoDaObra(); y++) {
                    if (!mundo.getBlockAt(x, y, z).getType().isAir()) {
                        ocupados++;
                    }
                }
            }
        }
        return ocupados;
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

    /** A direção perpendicular a um rumo cardeal. */
    public static int[] tangente(int[] rumo) {
        return new int[]{-rumo[1], rumo[0]};
    }

    /** O bloco do meio do vão da porta, na borda: {x, z}. */
    public static int[] pontoDaPorta(LabirintoCatalog planta) {
        int[] saida = planta.saida();
        int meio = planta.meio();
        return switch (saida[2]) {
            case LabirintoCatalog.NORTE ->
                    new int[]{planta.centroDaCelula(saida[0]), -meio};
            case LabirintoCatalog.SUL ->
                    new int[]{planta.centroDaCelula(saida[0]), meio};
            case LabirintoCatalog.OESTE ->
                    new int[]{-meio, planta.centroDaCelula(saida[1])};
            default -> new int[]{meio, planta.centroDaCelula(saida[1])};
        };
    }

    /**
     * Está no patamar da porta — isto é, ganhou?
     *
     * A conta é em cima do rumo da porta, e não em x e z: assim ela é a mesma
     * para os quatro lados, e a porta do dia pode cair em qualquer um deles
     * sem um 'if' por lado. O vão em si (p = 0) não conta: é preciso ATRAVESSAR.
     */
    public static boolean naPorta(LabirintoCatalog planta, double x, double y,
                                  double z) {
        int[] porta = pontoDaPorta(planta);
        int[] rumo = LabirintoCatalog.RUMOS[planta.saida()[2]];
        int[] lado = tangente(rumo);
        double dx = x - (porta[0] + 0.5);
        double dz = z - (porta[1] + 0.5);
        double p = dx * rumo[0] + dz * rumo[1];
        double q = dx * lado[0] + dz * lado[1];
        double meiaLargura = (planta.saidaLargura() - 1) / 2.0;
        return p >= 0.5 && p <= planta.saidaFundo() + 0.5
                && Math.abs(q) <= meiaLargura + 0.5
                && y >= planta.topoY() && y <= planta.topoY() + 4;
    }

    @Override
    public Location pontoDeEntrada(World mundo) {
        return SaguaoPadrao.pontoDoSaguao(mundo, plugin.labirintoCatalog());
    }

    /** Onde um jogador nasce: na sala do meio, de costas para o coração. */
    public static Location pontoDaLargada(World mundo, LabirintoCatalog planta,
                                          int[] xz) {
        Location pouso = new Location(mundo, xz[0] + 0.5, planta.topoY() + 1,
                xz[1] + 0.5);
        pouso.setYaw(ArenaComum.yawParaOCentro(xz[0], xz[1]) + 180f);
        pouso.setPitch(0f);
        return pouso;
    }

    /** De onde se assiste: de cima do labirinto, que é como ele se lê. */
    public static Location pontoDaPlateia(World mundo, LabirintoCatalog planta) {
        Location onde = new Location(mundo, 0.5,
                planta.topoY() + planta.marcoAltura() + 20, 0.5);
        onde.setYaw(180f);
        onde.setPitch(70f);
        return onde;
    }

    /** O meio do patamar da porta, para o som e a partícula. */
    public static Location pontoDoFarol(World mundo, LabirintoCatalog planta) {
        int[] porta = pontoDaPorta(planta);
        int[] rumo = LabirintoCatalog.RUMOS[planta.saida()[2]];
        int p = (planta.saidaFundo() + 1) / 2;
        return new Location(mundo, porta[0] + rumo[0] * p + 0.5,
                planta.topoY() + 2, porta[1] + rumo[1] * p + 0.5);
    }
}
