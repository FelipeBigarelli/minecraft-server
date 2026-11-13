package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * O que todo minigame de arena precisa, e que nenhum deles precisa reescrever.
 *
 * <h2>Por que isto existe</h2>
 *
 * São vinte jogos. Cada um tem a sua planta, a sua regra e o seu desenho — e
 * nenhum deles tem opinião própria sobre o que é um disco, como se escreve
 * uma placa ou de quanto em quanto a contagem é anunciada. Copiar essas
 * respostas de jogo em jogo daria vinte cópias que começam iguais e vão
 * divergindo em silêncio: o terceiro jogo anuncia a contagem de outro jeito,
 * o sétimo esquece de encerar a placa, e cada defeito precisa ser achado
 * vinte vezes.
 *
 * <p>Aqui mora só o que é mesmo comum: conta de geometria, formato de texto e
 * as duas operações de mundo que todo saguão faz (escrever placa, pendurar
 * letreiro). Regra de jogo NÃO mora aqui. A linha é essa, e ela importa:
 * quando uma função daqui começar a precisar de um "se for o TNT Run", ela
 * não era comum.
 *
 * <p>As funções de conta são estáticas e puras de propósito — é o que deixa o
 * teste medir a arena inteira sem servidor nenhum de pé.
 */
public final class ArenaComum {

    private ArenaComum() {
    }

    // ------------------------------------------------------------------
    //  Geometria do disco
    // ------------------------------------------------------------------

    /**
     * O disco: a arena é redonda, e isso não é gosto.
     *
     * Num quadrado as quinas são armadilha e a diagonal é atalho — quem corre
     * na diagonal cobre 1,41 de chão por bloco gasto, e quem é encurralado na
     * quina não tinha para onde ir desde o começo. No disco toda direção
     * custa igual.
     */
    public static boolean noDisco(int x, int z, int raio) {
        return x * x + z * z <= raio * raio;
    }

    /**
     * O disco de raio {@code raio + 0.5}, em contas de inteiro.
     *
     * O disco cru deixa FARPAS: na direção do eixo, a última casa fica
     * sozinha — os vizinhos de lado dela caem fora por meio bloco. Numa
     * arena de piso isso não se nota; num ANEL de andar de cima é uma ponta
     * de corredor com um vizinho só, em que um jogador é encurralado sem ter
     * errado nada. Meio bloco a mais tira todas as farpas, e a forma continua
     * sendo um círculo.
     */
    public static boolean noDiscoMeio(int x, int z, int raio) {
        long dobro = 2L * raio + 1;
        return 4L * (x * (long) x + z * (long) z) <= dobro * dobro;
    }

    /**
     * Se a célula está nos anéis de fora.
     *
     * Conta em quadrado do raio para não precisar de raiz: um anel de
     * espessura n por fora é tudo que está no disco e não cabe no de raio-n.
     */
    public static boolean naBeira(int x, int z, int raio, int aneis) {
        return noDisco(x, z, raio) && !noDisco(x, z, raio - aneis);
    }

    /**
     * As posições de largada: {@code quantos} pontos num anel, espaçados.
     *
     * Largada espalhada e não queda em monte. Dezesseis pessoas soltas do
     * mesmo ponto chegam em cima uma da outra e o primeiro segundo da partida
     * vira empurrão; no anel todos têm a mesma folga e a mesma distância da
     * beira, que é o que faz a partida ser decidida correndo.
     *
     * @return pares {x, z} em blocos, a partir do centro.
     */
    public static List<int[]> largadas(int quantos, int raio) {
        List<int[]> pontos = new ArrayList<>();
        if (quantos <= 0) {
            return pontos;
        }
        if (quantos == 1) {
            pontos.add(new int[]{0, 0});
            return pontos;
        }
        int anel = Math.max(2, raio - 5);
        for (int i = 0; i < quantos; i++) {
            double angulo = 2 * Math.PI * i / quantos;
            pontos.add(new int[]{
                    (int) Math.round(Math.cos(angulo) * anel),
                    (int) Math.round(Math.sin(angulo) * anel)});
        }
        return pontos;
    }

    /**
     * Três inteiros numa chave só, para mapa de posição.
     *
     * Um mapa de três inteiros por jogador por tique é alocação à toa trinta
     * vezes por segundo. Vinte bits por eixo dão de sobra para qualquer arena
     * — e uma arena que precise de mais que um milhão de blocos num eixo tem
     * problema maior do que a chave.
     */
    public static long chave(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /**
     * Um dos três inteiros de volta da chave empacotada.
     *
     * Os vinte bits são COM SINAL, e é por isso que a conta tem a correção do
     * último bit: sem ela, uma coordenada negativa volta como um número
     * enorme e positivo, e a célula vai parar do outro lado do mundo.
     *
     * @param deslocamento 40 para x, 20 para a fiada, 0 para z.
     */
    public static int daChave(long chave, int deslocamento) {
        int bruto = (int) ((chave >> deslocamento) & 0xFFFFF);
        return bruto >= (1 << 19) ? bruto - (1 << 20) : bruto;
    }

    /**
     * O yaw de quem olha para a direção (dx, dz).
     *
     * Yaw zero é o sul (z crescente) e cresce para o oeste, que é o contrário
     * da intuição de quem já fez isto em qualquer outro lugar. Daí a conta
     * ter um sinal invertido que parece erro e não é.
     */
    public static float yawDaDirecao(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Yaw de quem está em (x, z) e olha para o centro.
     *
     * Yaw do Bukkit: 0 olha para +Z, 90 para -X, 180 para -Z, 270 para +X. A
     * direção do centro é (-x, -z), que dá {@code atan2(x, -z)}. A conta
     * trocada — {@code atan2(-x, z)} — é a mesma virada ao contrário, e
     * largaria todo mundo de costas para a arena: a primeira reação de meia
     * arena seria correr para fora dela.
     */
    public static float yawParaOCentro(int x, int z) {
        return (float) Math.toDegrees(Math.atan2(x, -z));
    }

    /**
     * O ângulo de um ponto contado a partir do NORTE, girando como o relógio.
     *
     * <p>É a conta inversa da que distribui ilhas num anel: lá, a ilha k nasce
     * no ângulo {@code -PI/2 + 2*PI*k/quantas}, que começa no norte e segue
     * para o leste. Aqui, dado o ponto, volta-se o k — e é isso que permite
     * ORDENAR pontos medidos de um mapa pronto na mesma ordem em que a arena
     * gerada numera as ilhas dela. Sem essa ordem comum, trocar uma arena
     * gerada por um mapa feito à mão embaralharia quem nasce onde.
     *
     * @return de 0 (norte) a 2π, crescendo para o leste
     */
    public static double anguloDoNorte(double x, double z) {
        double bruto = Math.atan2(z, x) + Math.PI / 2;
        double volta = 2 * Math.PI;
        return ((bruto % volta) + volta) % volta;
    }

    // ------------------------------------------------------------------
    //  Regras de rodada que não são de nenhum jogo em particular
    // ------------------------------------------------------------------

    /**
     * O raio da arena depois de tantos segundos de aperto.
     *
     * Toda arena de disco precisa acabar antes do relógio, senão a rodada em
     * que os dois últimos se escondem dura até o tempo limite e termina em
     * empate — que não é o fim que ninguém quer ver. O aperto é o que obriga
     * o encontro: a partir de certo segundo, o disco come um anel por vez.
     */
    public static int raioDoAperto(int segundos, int raio, int apertoSegundos,
                                   int apertoIntervalo) {
        if (segundos < apertoSegundos) {
            return raio;
        }
        int passos = 1 + (segundos - apertoSegundos) / Math.max(1, apertoIntervalo);
        return Math.max(1, raio - passos);
    }

    /** A casa está no disco original mas já fora do disco apertado? */
    public static boolean foraDoApertoAtual(int x, int z, int raioAtual, int raio) {
        return noDisco(x, z, raio) && !noDisco(x, z, raioAtual);
    }

    /**
     * Se a rodada acabou.
     *
     * Sozinho o critério MUDA: um só de pé é o fim normal de uma partida de
     * verdade, mas é o começo de uma rodada de treino. Sem este desvio o jogo
     * seria impossível de testar — a rodada acabaria no mesmo tique em que
     * começasse, com o único jogador declarado vencedor sem ter jogado.
     */
    public static boolean rodadaAcabou(int vivos, int comecouCom, boolean treinoSozinho) {
        if (comecouCom <= 1 && treinoSozinho) {
            return vivos == 0;
        }
        return vivos <= 1;
    }

    /**
     * Em que segundos a contagem é anunciada.
     *
     * Todos os dez últimos, e de dez em dez antes disso — a convenção dos
     * minigames de servidor, e ela existe por um motivo: avisar de cinco em
     * cinco no fim deixa o jogador sem saber se faltam três ou seis, que é
     * justo quando ele decide se dá tempo de ir ler a placa.
     */
    public static boolean anunciaContagem(int segundos) {
        if (segundos < 1) {
            return false;
        }
        return segundos <= 10 || segundos % 10 == 0;
    }

    /** "1:07" a partir de tiques, para o tempo de quem sobreviveu. */
    public static String tempo(long ticks) {
        long total = Math.max(0, ticks) / 20;
        return (total / 60) + ":" + (total % 60 < 10 ? "0" : "") + (total % 60);
    }

    /** Prende um valor de barra entre 0 e 1 — inclusive quando ele é NaN. */
    public static float limite(float valor) {
        if (Float.isNaN(valor)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, valor));
    }

    /**
     * Se o jogador ficou parado de um segundo para o outro.
     *
     * Agachado conta como parado mesmo tendo andado: agachar na beira é
     * justamente a pose de quem está acampando sem cair. É a convenção do
     * plugin de referência e ela fecha o buraco óbvio da regra.
     */
    public static boolean ficouParado(double x, double z, double antesX, double antesZ,
                                      boolean agachado) {
        return agachado || (Math.abs(x - antesX) < 1.0 && Math.abs(z - antesZ) < 1.0);
    }

    // ------------------------------------------------------------------
    //  Placas
    // ------------------------------------------------------------------

    /**
     * Escreve — e coloca, se preciso — uma placa de parede do saguão.
     *
     * Coloca se preciso porque isto roda tanto na obra quanto no laço do jogo:
     * se a placa sumir por qualquer motivo, ela volta no segundo seguinte em
     * vez de o jogo virar um saguão sem porta de entrada.
     *
     * <p>A placa nasce ENCERADA. Não é enfeite: placa não encerada abre a tela
     * de edição no clique direito, e aí clicar nela não entra na fila — edita
     * o texto dela.
     */
    public static void escreverPlaca(World mundo, int[] onde, Material material,
                                     BlockFace frente, List<Component> linhas) {
        Block bloco = mundo.getBlockAt(onde[0], onde[1], onde[2]);

        if (bloco.getType() != material) {
            bloco.setType(material, false);
        }
        if (bloco.getBlockData() instanceof Directional dados
                && dados.getFaces().contains(frente)
                && dados.getFacing() != frente) {
            dados.setFacing(frente);
            bloco.setBlockData(dados, false);
        }
        if (!(bloco.getState() instanceof Sign placa)) {
            return;
        }
        SignSide lado = placa.getSide(Side.FRONT);
        for (int i = 0; i < 4; i++) {
            lado.line(i, i < linhas.size() ? linhas.get(i) : Component.empty());
        }
        placa.setWaxed(true);
        placa.update(true, false);
    }

    /** Se o bloco é exatamente aquela coordenada. */
    public static boolean mesmoBloco(Block bloco, int[] onde) {
        return bloco.getX() == onde[0] && bloco.getY() == onde[1]
                && bloco.getZ() == onde[2];
    }

    // ------------------------------------------------------------------
    //  Letreiros
    // ------------------------------------------------------------------

    /**
     * Pendura um letreiro de texto, marcado com a etiqueta do jogo.
     *
     * A etiqueta é o que permite refazer os letreiros sem deixar cópia para
     * trás: o desfazer apaga por etiqueta, e não por "tudo que for texto
     * perto daqui" — que apagaria letreiro de outro jogo se dois dividissem
     * mundo um dia.
     */
    public static void painel(World mundo, Location alvo, Component texto,
                              float escala, int fundoAlpha, String etiqueta) {
        alvo.setYaw(0f);
        alvo.setPitch(0f);
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(Display.Billboard.FIXED);
            novo.setAlignment(TextDisplay.TextAlignment.CENTER);
            novo.setBackgroundColor(Color.fromARGB(fundoAlpha, 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(220);
            novo.setViewRange(1.4f);
            novo.setPersistent(true);
            novo.addScoreboardTag(etiqueta);

            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(escala, escala, escala), t.getRightRotation()));
        });
    }

    /**
     * Pendura um letreiro que acompanha quem olha.
     *
     * A diferença com {@link #painel} é uma linha — {@code Billboard.CENTER}
     * em vez de {@code FIXED} — e ela existe para as arenas que são lidas de
     * várias direções ao mesmo tempo. O painel de instrução do saguão é lido
     * de frente e fica FIXO, que é mais bonito e não tremelica; o painel no
     * meio de uma arena em anel, com dezesseis pessoas em volta, tem de
     * virar, ou é ilegível para quinze delas.
     */
    public static void painelVirado(World mundo, Location alvo, Component texto,
                                    float escala, int fundoAlpha,
                                    String etiqueta) {
        alvo.setYaw(0f);
        alvo.setPitch(0f);
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(Display.Billboard.CENTER);
            novo.setAlignment(TextDisplay.TextAlignment.CENTER);
            novo.setBackgroundColor(Color.fromARGB(fundoAlpha, 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(260);
            novo.setViewRange(2.0f);
            novo.setPersistent(false);
            novo.addScoreboardTag(etiqueta);

            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(escala, escala, escala), t.getRightRotation()));
        });
    }

    /**
     * Um painel de PAREDE: ele gira para encarar quem lê, mas só no eixo de
     * pé.
     *
     * A diferença para o {@link #painelVirado} é essa, e ela importa: o
     * billboard CENTER também se inclina com o olhar, então um painel
     * pendurado na fachada de uma casa deita para trás quando o jogador olha
     * para cima e atravessa a parede. O VERTICAL só roda em volta do próprio
     * mastro — fica sempre de pé, sempre legível, e nunca entra no bloco de
     * trás.
     *
     * <p>A largura de linha é menor que a do letreiro do fundo da rua porque
     * estes ficam entre duas casas: uma linha larga passaria por dentro da
     * parede de frente.
     */
    public static void painelDeParede(World mundo, Location alvo,
                                      Component texto, float escala,
                                      int fundoAlpha, int largura,
                                      String etiqueta) {
        alvo.setYaw(0f);
        alvo.setPitch(0f);
        mundo.spawn(alvo, TextDisplay.class, novo -> {
            novo.text(texto);
            novo.setBillboard(Display.Billboard.VERTICAL);
            novo.setAlignment(TextDisplay.TextAlignment.LEFT);
            novo.setBackgroundColor(Color.fromARGB(fundoAlpha, 0, 0, 0));
            novo.setShadowed(true);
            novo.setBrightness(new Display.Brightness(15, 15));
            novo.setLineWidth(largura);
            novo.setViewRange(1.2f);
            novo.setPersistent(false);
            novo.addScoreboardTag(etiqueta);

            Transformation t = novo.getTransformation();
            novo.setTransformation(new Transformation(
                    t.getTranslation(), t.getLeftRotation(),
                    new Vector3f(escala, escala, escala), t.getRightRotation()));
        });
    }

    public static void limparPaineis(World mundo, String etiqueta) {
        for (Entity entidade : mundo.getEntities()) {
            if (entidade.getScoreboardTags().contains(etiqueta)) {
                entidade.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    //  O empurrão do golpe
    // ------------------------------------------------------------------

    /**
     * A velocidade que o alvo passa a ter depois de levar um golpe.
     *
     * A conta do Minecraft, escrita aqui para o teste medir sem servidor: a
     * velocidade de agora vale METADE, o empurrão soma por cima na direção
     * de fuga, e a subida tem TETO.
     *
     * <p>Os três pedaços são necessários. Sem a metade, golpes seguidos
     * somariam velocidade sem limite. Sem o teto na subida, dois golpes
     * jogam o jogador por cima do mapa em vez de para o lado — que é o
     * contrário do que empurrar quer dizer. E a direção é horizontal pura:
     * o eixo vertical vem do {@code alto}, não da posição de quem bateu, ou
     * um golpe vindo de baixo pregaria o alvo no chão.
     *
     * @param dx    quanto o alvo está a LESTE de quem bateu
     * @param dz    quanto o alvo está ao SUL de quem bateu
     * @param vx    a velocidade que o alvo já tinha
     * @param forca o empurrão horizontal, em blocos por tique
     * @param alto  o empurrão para cima
     * @param teto  o máximo que a parte de cima pode valer
     * @return {x, y, z} da velocidade nova
     */
    /**
     * Em quantas pilhas LEGAIS um punhado de itens se parte.
     *
     * <h2>Por que isto existe</h2>
     *
     * A MINERAÇÃO entregava dois sonares numa pilha só. O sonar é uma
     * LUNETA, e luneta no Minecraft empilha até UM. O que chegava ao jogador
     * não era uma pilha de dois: era uma pilha ilegal, e o CreativePlots
     * deste servidor tem proteção contra isso ligada — ele via o item,
     * escrevia "[Exploit Protection] Illegal stack detected and removed" no
     * bate-papo e APAGAVA o sonar. A partida começava sem a ferramenta que o
     * jogo inteiro é construído em volta, e nada no plugin dava erro: do
     * nosso lado o item tinha sido entregue.
     *
     * <p>É o mesmo tipo de armadilha do {@code setUnbreakable}: um item
     * perfeitamente válido para a API do Bukkit e inválido para o
     * anti-exploit que roda ao lado. Quem escreve o kit não tem como lembrar
     * de cabeça que luneta empilha 1, bola de neve 16 e flecha 64.
     *
     * <p>Recebe o máximo como NÚMERO em vez de perguntar ao material porque
     * {@code Material.getMaxStackSize()} precisa do registro vivo do
     * servidor e não roda em teste. Assim a conta — que é a parte que erra —
     * é testável, e só a leitura do máximo fica do lado do servidor.
     *
     * @return os tamanhos de cada pilha, em ordem; vazio se não há nada.
     */
    public static int[] fatias(int quantos, int maximo) {
        if (quantos <= 0) {
            return new int[0];
        }
        int teto = Math.max(1, maximo);
        int cheias = quantos / teto;
        int resto = quantos % teto;
        int[] saida = new int[cheias + (resto > 0 ? 1 : 0)];
        java.util.Arrays.fill(saida, 0, cheias, teto);
        if (resto > 0) {
            saida[cheias] = resto;
        }
        return saida;
    }

    /**
     * Põe o item na mão do jogador SEM montar uma pilha ilegal.
     *
     * Quando o punhado não cabe numa pilha só, ele escorre para os espaços
     * seguintes. É o que a MINERAÇÃO precisa: dois sonares são duas lunetas
     * em dois espaços, porque luneta empilha até uma.
     *
     * <p>Aqui e não no {@code JogoDeArena} porque o TNT RUN e o SPLEEF ficam
     * FORA do arcabouço, e já foram esquecidos três vezes por isso.
     *
     * @return quantos espaços foram ocupados, a partir de {@code espaco}.
     */
    public static int entregar(org.bukkit.entity.Player jogador, int espaco,
                               org.bukkit.inventory.ItemStack modelo,
                               int quantos) {
        int[] pilhas = fatias(quantos, modelo.getType().getMaxStackSize());
        for (int k = 0; k < pilhas.length; k++) {
            org.bukkit.inventory.ItemStack copia = modelo.clone();
            copia.setAmount(pilhas[k]);
            jogador.getInventory().setItem(espaco + k, copia);
        }
        return pilhas.length;
    }

    /**
     * Quantos desses itens cabem numa pilha, no máximo.
     *
     * Para o kit cujo desenho PRECISA de uma pilha só num espaço marcado — a
     * bola de neve do Spleef e da Guerra de Neve fica no espaço zero e o jogo
     * conta por ela. Aí não dá para escorrer para o espaço seguinte: o que dá
     * é entregar o que cabe. Um yml que peça 32 bolas passa a receber 16 em
     * vez de receber ZERO, que é o que o anti-exploit devolvia.
     */
    public static int cabemNaPilha(org.bukkit.Material qual, int quantos) {
        return Math.min(Math.max(0, quantos), qual.getMaxStackSize());
    }

    public static double[] empurrao(double dx, double dz, double vx, double vy,
                                    double vz, double forca, double alto,
                                    double teto) {
        double longe = Math.sqrt(dx * dx + dz * dz);
        double ux;
        double uz;
        if (longe < 1.0E-6) {
            // Em cima um do outro: empurra para o norte, que é melhor do
            // que dividir por zero e melhor do que não empurrar.
            ux = 0.0;
            uz = -1.0;
        } else {
            ux = dx / longe;
            uz = dz / longe;
        }
        return new double[]{
                vx / 2 + ux * forca,
                Math.min(teto, vy / 2 + alto),
                vz / 2 + uz * forca};
    }
}
