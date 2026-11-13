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
import java.util.Set;

/**
 * Levanta o labirinto desta rodada.
 *
 * <h2>O que se confere antes de pôr um bloco</h2>
 *
 * Um labirinto gerado tem um jeito de sair errado que nenhum olho pega: ser
 * IMPOSSÍVEL. A grade pode dizer que existe passagem entre duas células e o
 * mapa de blocos tapar aquela passagem; uma célula pode nascer ilhada; a
 * porta pode cair a cinco passos da sala. Nada disso aparece numa foto da
 * arena — aparece quando dezesseis pessoas estão dentro. Por isso a
 * conferência percorre o mapa de blocos de verdade, em largura, a partir da
 * porta, e exige chegar a cada casa de piso.
 *
 * <p>E não confere só o labirinto que saiu: confere TODOS os que podem sair.
 * Cinco traçados vezes cinco tamanhos são vinte e cinco labirintos, e um
 * traçado que só falhasse em vinte e cinco células apareceria na madrugada de
 * uma terça, numa rodada, para dezesseis pessoas presas dentro. Vinte e cinco
 * buscas em largura custam menos de um décimo de segundo no comando de obra —
 * é barato demais para não fazer.
 *
 * <h2>Por que o labirinto é refeito a cada rodada</h2>
 *
 * Porque ele é OUTRO a cada rodada: traçado, tamanho, estilo e voltas saem de
 * um sorteio novo em {@link LabirintoCatalog#sortearRodada()}. Limpar o giz é
 * consequência, não motivo.
 *
 * <p>Refazer varre a área do MAIOR labirinto possível e não a do que está
 * saindo. Tem de ser assim: depois de uma rodada de vinte e cinco células vem
 * uma de nove, e sem varrer o tamanho grande o labirinto anterior ficaria de
 * pé em volta do novo — com porta e com patamar, apontando para
 * uma saída que não existe mais.
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

        String problema = conferirSorteio(planta.tamanhos(),
                planta.tracadosEscritos(), planta.estilos(),
                planta.voltasPorCento());
        if (problema == null) {
            problema = conferirEstilos(planta.estilos(), coresDosEstilos(planta));
        }
        if (problema == null) {
            problema = conferirTodos(planta.tamanhos(), planta.tracadosLigados(),
                    planta.voltasPorCento(), planta.celula(), planta.parede(),
                    planta.paredeAltura(), planta.sala(),
                    planta.saidaLargura(), planta.saidaFundo());
        }
        if (problema != null) {
            return new Resultado(false, problema, 0);
        }

        if (!aplicar) {
            int ocupados = contarOcupados(mundo, planta);
            return new Resultado(true, "Área livre: "
                    + (ocupados == 0 ? "sim" : "NÃO, " + ocupados + " bloco(s) no caminho")
                    + ". O sorteio pode tirar " + planta.tracadosLigados().size()
                    + " traçado(s) x " + planta.tamanhos().size() + " tamanho(s) x "
                    + Math.max(1, planta.estilos().size()) + " estilo(s) x "
                    + planta.voltasPorCento().size() + " nível(eis) de volta = "
                    + combinacoes(planta) + " combinações, e a área da obra é a "
                    + "do maior (" + planta.celulasMaximo() + " células, "
                    + planta.ladoMaximo() + " blocos de lado). Esta rodada tirou "
                    + planta.nomeDoTracado() + " de " + planta.celulas() + "x"
                    + planta.celulas() + " células no estilo "
                    + planta.nomeDoEstilo() + ", com " + planta.voltas()
                    + " voltas, na fiada " + planta.topoY() + ": a porta é a "
                    + nomeDoLado(planta.saida()[2]) + ", fica a "
                    + planta.passosAteASaida() + " passos da sala e o relógio "
                    + "dela é de " + planta.tempoLimite() + "s. Saguão em y "
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
                + " blocos. " + planta.nomeDoTracado() + " de " + planta.celulas()
                + "x" + planta.celulas() + " no estilo " + planta.nomeDoEstilo()
                + "; a porta é a " + nomeDoLado(planta.saida()[2]) + ", a "
                + planta.passosAteASaida() + " passos da sala. O spawn do mundo "
                + "foi posto no saguão.", contador);
    }

    @Override
    public Resultado desfazer(World mundo) {
        LabirintoCatalog planta = plugin.labirintoCatalog();
        int alcance = planta.alcanceDaObra();
        contador = 0;

        ArenaComum.limparPaineis(mundo, TAG_PAINEL);
        contador += ArenaComum.apagarACaixa(mundo, alcance,
                planta.fundoDaObra(), planta.tetoDaObra());
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

    /** Quantos labirintos diferentes o sorteio pode tirar, sem contar o mapa. */
    public static int combinacoes(LabirintoCatalog planta) {
        return planta.tracadosLigados().size() * planta.tamanhos().size()
                * Math.max(1, planta.estilos().size())
                * planta.voltasPorCento().size();
    }

    /** Refaz só o labirinto, com o sorteio da rodada. Chamado ao largar. */
    public int refazerLabirinto(World mundo) {
        LabirintoCatalog planta = plugin.labirintoCatalog();
        contador = 0;
        obra(mundo, planta);
        return contador;
    }

    private void obra(World mundo, LabirintoCatalog planta) {
        campo(mundo, planta);
        limparTorres(mundo, planta);
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
     * As três chaves de paleta que distinguem um estilo, por estilo.
     *
     * Piso, muro e topo do muro: é o que o jogador vê correndo. Dois estilos
     * que concordem nas três são o mesmo estilo com dois nomes, e dois nomes
     * para a mesma coisa é a variação que o sorteio promete e não entrega.
     */
    public static List<List<Material>> coresDosEstilos(LabirintoCatalog planta) {
        List<List<Material>> cores = new ArrayList<>();
        for (int k = 0; k < planta.estilos().size(); k++) {
            cores.add(List.of(
                    planta.corDoEstilo(k, "piso", Material.STONE_BRICKS),
                    planta.corDoEstilo(k, "muro", Material.PRISMARINE_BRICKS),
                    planta.corDoEstilo(k, "muro-topo", Material.DARK_PRISMARINE)));
        }
        return cores;
    }

    /**
     * O sorteio: as listas de onde sai um labirinto.
     *
     * A conferência que importa é a ÚLTIMA, e ela é a resposta direta ao
     * pedido que gerou este jogo todo — "pelo menos umas vinte variações". O
     * número não é inventado: é o pedido. E é um produto e não quatro mínimos
     * de propósito, porque deixa o dono escolher COMO chegar lá — cinco
     * traçados e quatro tamanhos dá vinte, e um traçado só com cinco tamanhos
     * e quatro estilos também dá vinte. Quatro mínimos separados proibiriam a
     * segunda escolha sem motivo nenhum.
     *
     * <p>Para prender o jogo num labirinto só — torneio, gravação, evento —
     * existe 'sorteio-fixo', que é a maneira certa: ela prende sem mentir
     * sobre o tamanho do baralho.
     */
    static String conferirSorteio(List<Integer> tamanhos, List<String> tracados,
                                  List<String> estilos, List<Integer> voltas) {
        if (tamanhos.isEmpty()) {
            return "'arena.tamanhos' está vazia: o sorteio não tem tamanho "
                    + "nenhum para tirar.";
        }
        if (new HashSet<>(tamanhos).size() != tamanhos.size()) {
            return "'arena.tamanhos' tem tamanho repetido " + tamanhos + ": o "
                    + "repetido não acrescenta labirinto, só aumenta a chance "
                    + "de sair o mesmo de novo.";
        }
        Set<Integer> vistos = new HashSet<>();
        for (String nome : tracados) {
            int qual = LabirintoTracados.porNome(nome);
            if (qual < 0) {
                return "'arena.tracados' tem '" + nome + "', que não é traçado "
                        + "nenhum. Os que existem são " + LabirintoTracados.NOMES
                        + ".";
            }
            if (!vistos.add(qual)) {
                return "'arena.tracados' repete '" + nome + "': o traçado "
                        + "repetido só faz ele sair com o dobro da chance.";
            }
        }
        if (vistos.isEmpty()) {
            return "'arena.tracados' está vazia: sem traçado nenhum o sorteio "
                    + "não tem o que cavar.";
        }
        if (voltas.isEmpty()) {
            return "'arena.voltas-por-cento' está vazia: o sorteio não tem "
                    + "nível de volta para tirar.";
        }
        int anterior = -1;
        for (int pct : voltas) {
            if (pct < 0 || pct > 40) {
                return "'arena.voltas-por-cento' tem " + pct + "%: acima de "
                        + "quarenta por cento das casas sobra mais abertura que "
                        + "parede, e um labirinto sem parede é um descampado.";
            }
            if (pct <= anterior) {
                return "'arena.voltas-por-cento' não está em ordem crescente ("
                        + voltas + "): a planta chama o primeiro de 'poucas "
                        + "voltas' e o último de 'muitas voltas', e fora de "
                        + "ordem ela anuncia o contrário do que construiu.";
            }
            anterior = pct;
        }
        int quantas = vistos.size() * tamanhos.size()
                * Math.max(1, estilos.size()) * voltas.size();
        if (quantas < 20) {
            return "O sorteio só tem " + quantas + " combinação(ões) — "
                    + vistos.size() + " traçado(s) x " + tamanhos.size()
                    + " tamanho(s) x " + Math.max(1, estilos.size())
                    + " estilo(s) x " + voltas.size() + " nível(eis) de volta. "
                    + "Um labirinto que se decora é o problema que este jogo "
                    + "tinha; abaixo de vinte ele volta. Para prender o jogo "
                    + "num labirinto só use 'arena.sorteio-fixo'.";
        }
        return null;
    }

    /**
     * Os estilos: nomes e materiais.
     *
     * Duas coisas, e as duas já aconteceram em outras arenas. Piso da cor do
     * muro é um labirinto invisível — o jogador vê um chão liso e bate o nariz
     * na parede. Dois estilos iguais é um estilo copiado e meio editado, que
     * é como a quinta cópia de uma paleta sempre nasce.
     */
    static String conferirEstilos(List<String> nomes,
                                  List<List<Material>> cores) {
        if (nomes.size() < 2) {
            return "Há " + nomes.size() + " estilo(s) em 'estilos': o estilo é "
                    + "a variação mais barata que existe — só paleta, nenhuma "
                    + "regra — e é a que o jogador mais vê, porque ele corre "
                    + "olhando para o muro. Escreva pelo menos dois.";
        }
        for (int k = 0; k < cores.size(); k++) {
            List<Material> trio = cores.get(k);
            if (trio.get(0) == trio.get(1)) {
                return "O estilo '" + nomes.get(k) + "' tem o piso e o muro do "
                        + "mesmo material (" + trio.get(0) + "): o labirinto "
                        + "sairia invisível e o jogador só o descobriria "
                        + "batendo o nariz nele.";
            }
            for (int j = 0; j < k; j++) {
                if (cores.get(j).equals(trio)) {
                    return "Os estilos '" + nomes.get(j) + "' e '" + nomes.get(k)
                            + "' têm piso, muro e topo iguais: são o mesmo "
                            + "estilo com dois nomes, e o sorteio promete uma "
                            + "variação que não existe.";
                }
            }
        }
        return null;
    }

    /**
     * Todos os labirintos que podem sair, um por um.
     *
     * Traçado por tamanho, com o nível de volta do MEIO — que é o caso mais
     * difícil de propósito. O nível alto abre paredes e só facilita; o nível
     * baixo é o labirinto perfeito que cada algoritmo já devolve conectado por
     * construção. O do meio é o único em que a abertura de voltas chega a
     * mexer na estrutura sem garantir nada de graça.
     *
     * <p>A última exigência é sobre a CAMINHADA, e é ela que separa um
     * labirinto de um corredor: a porta tem de ficar a pelo menos meia grade
     * de distância da sala. Com a porta escolhida entre as mais longes isso
     * sobra em qualquer traçado — o pior medido foi 0,56 — mas é justamente
     * por sobrar que vale conferir: o dia em que alguém mexer no escolhedor da
     * porta, isto aqui avisa antes de dezesseis pessoas largarem a cinco
     * passos da saída.
     */
    static String conferirTodos(List<Integer> tamanhos, List<Integer> tracados,
                                List<Integer> voltas, int celula, int parede,
                                int paredeAltura, int sala, int saidaLargura,
                                int saidaFundo) {
        int pct = voltas.get(voltas.size() / 2);
        for (int quantas : tamanhos) {
            String problema = conferirNumeros(quantas, celula, parede,
                    paredeAltura, sala, saidaLargura, saidaFundo);
            if (problema != null) {
                return problema;
            }
            for (int tracado : tracados) {
                long semente = 7777L * quantas + 31L * tracado;
                boolean[] ligado = LabirintoTracados.cavar(tracado, quantas,
                        semente, LabirintoCatalog.voltasDe(quantas, pct), sala);
                int[] saida = LabirintoCatalog.portaLonge(quantas, ligado, sala,
                        semente);
                problema = conferirMapa(quantas, celula, parede, ligado, sala,
                        saida);
                if (problema != null) {
                    return LabirintoTracados.nome(tracado) + " de " + quantas
                            + " células: " + problema;
                }
                int passos = LabirintoCatalog.distancias(quantas, ligado, sala)
                        [LabirintoCatalog.indice(quantas, saida[0], saida[1])];
                if (passos * 2 < quantas) {
                    return LabirintoTracados.nome(tracado) + " de " + quantas
                            + " células: a porta ficou a " + passos + " passos "
                            + "da sala, menos de meia grade. Isso não é "
                            + "labirinto, é corredor — a porta tem de ser "
                            + "escolhida entre as mais longes do meio.";
                }
            }
        }
        return null;
    }

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
                    + "menos de dois não dá para pisar fora do vão e a "
                    + "chegada não se reconhece.";
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
     *
     * <p>E a passada é sobre a área do MAIOR labirinto que o sorteio pode
     * tirar, não a do que está saindo. Fora do labirinto desta rodada ela
     * escreve ar — é isso, e só isso, que apaga o labirinto de vinte e cinco
     * células quando a rodada seguinte tira nove. O preço é ler umas setenta
     * mil colunas em vez de catorze mil, e ler bloco igual não escreve nada:
     * a conta de blocos TROCADOS continua sendo a do labirinto que saiu.
     */
    private void campo(World mundo, LabirintoCatalog planta) {
        int[] mapa = planta.mapa();
        int lado = planta.lado();
        int meio = planta.meio();
        int alcance = planta.alcanceDaObra();
        int y = planta.topoY();
        int altura = planta.paredeAltura();
        int passo = planta.passo();
        int parede = planta.parede();

        Material piso = planta.corDoEstilo("piso", Material.STONE_BRICKS);
        Material xadrez = planta.corDoEstilo("piso-xadrez", Material.ANDESITE);
        Material muro = planta.corDoEstilo("muro", Material.PRISMARINE_BRICKS);
        Material base = planta.corDoEstilo("muro-base", Material.PRISMARINE);
        Material topo = planta.corDoEstilo("muro-topo", Material.DARK_PRISMARINE);

        for (int z = -alcance; z <= alcance; z++) {
            for (int x = -alcance; x <= alcance; x++) {
                if (Math.abs(x) > meio || Math.abs(z) > meio) {
                    for (int alto = 0; alto <= altura + 1; alto++) {
                        por(mundo, x, y + alto, z, Material.AIR);
                    }
                    continue;
                }
                int u = x + meio;
                int v = z + meio;
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
     * Derruba as torres de canto de TODOS os tamanhos antes de erguer as desta
     * rodada.
     *
     * A varredura do campo vai só até a altura do muro, que é onde o
     * labirinto mora. A torre tem vinte blocos e fica fora dele — no canto —,
     * então a varredura não a alcança: sem esta limpeza, a rodada de nove
     * células herdaria as quatro torres da rodada de vinte e cinco, de pé a
     * quarenta blocos do labirinto, marcando um canto que não existe mais.
     *
     * <p>Não precisa saber qual era o tamanho anterior: os cantos possíveis
     * são os dos tamanhos da lista, que são cinco. Vinte colunas limpas custam
     * nada e dispensam guardar estado entre rodadas.
     */
    private void limparTorres(World mundo, LabirintoCatalog planta) {
        int y = planta.topoY();
        int ate = planta.marcoAltura() + 3;
        for (int quantas : planta.tamanhos()) {
            int meio = (LabirintoCatalog.lado(quantas, planta.celula(),
                    planta.parede()) - 1) / 2;
            if (meio == planta.meio()) {
                continue;
            }
            for (int[] canto : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                for (int alto = 1; alto <= ate; alto++) {
                    por(mundo, canto[0] * meio, y + alto, canto[1] * meio,
                            Material.AIR);
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
        Material ouro = planta.corDoEstilo("coracao", Material.GOLD_BLOCK);
        Material beira = planta.corDoEstilo("coracao-beira",
                Material.CHISELED_STONE_BRICKS);
        Material coluna = planta.corDoEstilo("coracao-coluna",
                Material.POLISHED_DIORITE);
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
     * O patamar da porta.
     *
     * Sem farol, e isso foi uma correção: o facho de luz do beacon sobe até
     * o céu e se vê de qualquer ponto do labirinto, inclusive de cima do
     * muro. Ele não facilitava a busca — ele a APAGAVA, e a busca é o jogo.
     * O patamar continua existindo para a porta se reconhecer quando se
     * chega NELA, que é outra coisa.
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

        Material piso = planta.corDoEstilo("patamar", Material.POLISHED_DIORITE);
        Material beira = planta.corDoEstilo("patamar-beira",
                Material.DARK_PRISMARINE);
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

    }

    /** As quatro torres de canto: é por elas que o labirinto tem escala. */
    private void torres(World mundo, LabirintoCatalog planta) {
        Material torre = planta.corDoEstilo("torre", Material.DARK_PRISMARINE);
        Material luz = planta.corDoEstilo("torre-luz", Material.SEA_LANTERN);
        Material rod = planta.corDoEstilo("luz-fria", Material.END_ROD);
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

}
