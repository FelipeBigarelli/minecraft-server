package codes.biga.bigacore;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A cerca de madeira em volta do terreno comprado.
 *
 * As particulas do /terreno ver duram dez segundos e somem. Isso responde
 * "onde fica a borda" na hora de comprar e nao responde mais nada: quem volta
 * no dia seguinte para construir nao sabe onde pode encostar a parede, e a
 * unica saida e ficar digitando /terreno ver a cada minuto. A cerca fica.
 *
 * <h2>O que ela nao pode fazer</h2>
 *
 * Apagar construcao de ninguem. Cada poste so entra onde ja existe ar ou mato
 * — nunca por cima de um bloco que alguem colocou. Numa coluna sem chao, como
 * a beirada de uma ilha flutuante, o poste e simplesmente pulado: cerca
 * boiando no vazio confundiria mais do que ajuda.
 *
 * <h2>Por que em lotes</h2>
 *
 * Um terreno de 64x64 tem 252 postes espalhados por ate 25 chunks. Ler bloco
 * de chunk descarregado forca o carregamento na hora, na thread do servidor —
 * fazer os 252 de uma vez e um engasgo visivel para todo mundo online. Em
 * lotes por tique o custo some, e de brinde a cerca aparece desenhando a
 * volta, o que mostra ao comprador o contorno do que ele acabou de comprar.
 */
public final class CercaTerreno {

    private final BigaCore plugin;

    public CercaTerreno(BigaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * @param postos  quantos blocos foram colocados ou tirados
     * @param pulados colunas sem chao, ou ja ocupadas por algo de alguem
     */
    public record Relatorio(int postos, int pulados) {
    }

    /**
     * Levanta a cerca na borda da caixa.
     *
     * {@code yReferencia} e a altura de quem comprou. A busca pelo chao comeca
     * perto dela e nao no ponto mais alto da coluna: num mundo de ilhas
     * empilhadas, o bloco mais alto de um x/z pode estar numa ilha trinta
     * blocos acima da que o jogador escolheu, e a cerca sairia no telhado da
     * ilha errada.
     */
    public void levantar(World mundo, Caixa caixa, int yReferencia,
                         Consumer<Relatorio> aoTerminar) {

        TerrenoCatalog cfg = plugin.terrenoCatalog();
        Material poste = cfg.cercaMaterial();
        Material canto = cfg.cercaCanto();
        Set<Caixa.Ponto> cantos = new HashSet<>(caixa.cantos());

        emLotes(caixa.perimetro(), cfg.cercaPorTique(), aoTerminar, ponto -> {
            Block apoio = procurarApoio(mundo, ponto.x(), ponto.z(), yReferencia, cfg);
            if (apoio == null) {
                return false;
            }

            Block alvo = apoio.getRelative(0, 1, 0);
            // Fisica ligada de proposito: e ela que faz uma cerca enxergar a
            // vizinha e se conectar. Sem isso sairiam 252 postes soltos.
            alvo.setType(cantos.contains(ponto) ? canto : poste, true);
            return true;
        });
    }

    /**
     * Tira a cerca.
     *
     * Remove na coluna da borda qualquer bloco que seja do material da cerca
     * dentro da faixa de altura procurada. Nao guarda em disco onde cada poste
     * foi posto, e isso e escolha: uma lista de 252 coordenadas por terreno
     * envelhece mal — se o terreno cresce, se o chao muda, se alguem quebra um
     * poste, a lista passa a descrever um mundo que nao existe mais. Procurar
     * o material na hora sempre descreve o mundo de agora.
     *
     * O preco dessa escolha: uma cerca de carvalho que o jogador tenha
     * construido exatamente em cima da linha da borda sai junto. Como quem
     * manda tirar a cerca esta vendendo ou redesenhando o terreno, e aceitavel.
     */
    public void derrubar(World mundo, Caixa caixa, int yReferencia,
                         Consumer<Relatorio> aoTerminar) {

        TerrenoCatalog cfg = plugin.terrenoCatalog();
        Set<Material> nossos = Set.of(cfg.cercaMaterial(), cfg.cercaCanto());

        int teto = Math.min(mundo.getMaxHeight() - 1, yReferencia + cfg.cercaBuscaAcima() + 1);
        int piso = Math.max(mundo.getMinHeight(), yReferencia - cfg.cercaBuscaAbaixo());

        emLotes(caixa.perimetro(), cfg.cercaPorTique(), aoTerminar, ponto -> {
            boolean tirou = false;
            for (int y = teto; y >= piso; y--) {
                Block bloco = mundo.getBlockAt(ponto.x(), y, ponto.z());
                if (nossos.contains(bloco.getType())) {
                    bloco.setType(Material.AIR, true);
                    tirou = true;
                }
            }
            return tirou;
        });
    }

    /**
     * O bloco de chao onde o poste se apoia, ou null se a coluna nao serve.
     *
     * Desce a partir da altura de referencia procurando o primeiro bloco
     * solido que tenha espaco livre em cima. "Espaco livre" inclui mato e
     * flor — isReplaceable() e o mesmo teste que o jogo usa quando voce
     * encosta um bloco num tufo de grama. Agua e lava NAO sao substituiveis
     * por esse teste, entao a cerca nao seca o lago de ninguem.
     */
    private Block procurarApoio(World mundo, int x, int z, int yReferencia,
                                TerrenoCatalog cfg) {

        int comeco = Math.min(mundo.getMaxHeight() - 2, yReferencia + cfg.cercaBuscaAcima());
        int fim = Math.max(mundo.getMinHeight(), yReferencia - cfg.cercaBuscaAbaixo());

        for (int y = comeco; y >= fim; y--) {
            Block bloco = mundo.getBlockAt(x, y, z);
            if (!bloco.isSolid()) {
                continue;
            }
            Block acima = bloco.getRelative(0, 1, 0);
            if (acima.isEmpty() || acima.isReplaceable()) {
                return bloco;
            }
            // Achou chao mas o lugar do poste esta ocupado por algo que nao e
            // mato: construcao, tronco de arvore, outra cerca. Nao insiste
            // mais embaixo — ali e dentro do chao.
            return null;
        }
        return null;
    }

    /**
     * Percorre os pontos alguns por tique, chamando {@code acao} em cada um.
     *
     * A acao devolve true quando mexeu em bloco, e e so isso que o relatorio
     * conta — colunas puladas nao viram "poste colocado".
     */
    private void emLotes(List<Caixa.Ponto> pontos, int porTique,
                         Consumer<Relatorio> aoTerminar,
                         java.util.function.Predicate<Caixa.Ponto> acao) {

        new BukkitRunnable() {
            private int indice;
            private int postos;
            private int pulados;

            @Override
            public void run() {
                int limite = Math.min(pontos.size(), indice + porTique);
                while (indice < limite) {
                    if (acao.test(pontos.get(indice))) {
                        postos++;
                    } else {
                        pulados++;
                    }
                    indice++;
                }

                if (indice >= pontos.size()) {
                    cancel();
                    if (aoTerminar != null) {
                        aoTerminar.accept(new Relatorio(postos, pulados));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
