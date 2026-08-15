package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Garante que todo jogador comece com o saldo inicial da política.
 *
 * <h2>Por que isto existe</h2>
 *
 * O `economy.yml` promete 250 B$ para conta nova, e o
 * `plugins/EternalEconomy/config.yml` traz `defaultBalance: '250'`. Mesmo assim
 * a primeira conta criada no servidor real nasceu com saldo zero:
 *
 * <pre>(conta_nova, saldo = 0)</pre>
 *
 * Saldo zero. A promessa estava escrita em dois arquivos e não valia em lugar
 * nenhum, porque dependia inteiramente do comportamento de um plugin de
 * terceiro na hora exata em que a conta nasce — e a conta pode nascer por um
 * caminho que ignora esse default (uma consulta de saldo via Vault, por
 * exemplo, costuma criar a conta zerada).
 *
 * A política é do BigaCore, então quem a aplica passa a ser o BigaCore.
 *
 * <h2>Como não vira presente infinito</h2>
 *
 * Duas travas independentes:
 *
 * <ol>
 *   <li>o UUID é marcado como já contemplado num arquivo nosso;</li>
 *   <li>o crédito é um <b>complemento até</b> o saldo inicial, nunca uma soma:
 *       quem já tem 250 ou mais não recebe nada.</li>
 * </ol>
 *
 * Assim, apagar o arquivo de controle por acidente não distribui dinheiro para
 * quem já jogou.
 */
public final class StartingBalance {

    private static final String FILE_NAME = "saldo-inicial.yml";

    private final BigaCore plugin;
    private YamlConfiguration dados;

    public StartingBalance(BigaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.dados = YamlConfiguration.loadConfiguration(arquivo());
    }

    private File arquivo() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    public boolean jaRecebeu(UUID jogador) {
        return dados.getBoolean("recebido." + jogador, false);
    }

    /**
     * Concede o que faltar para o jogador chegar ao saldo inicial.
     *
     * Roda na main thread, um tick depois do join. O EternalEconomy mantém os
     * saldos em cache e enfileira a escrita, então o custo aqui é desprezível —
     * mas se um dia a economia virar banco remoto, este é o ponto que precisa
     * sair da main thread (regra do HANDOFF §9).
     */
    public void concederSeNecessario(Player jogador) {
        int alvo = plugin.economyCatalog().startingBalance();
        if (alvo <= 0) {
            return;
        }

        UUID id = jogador.getUniqueId();
        if (jaRecebeu(id)) {
            return;
        }

        if (!plugin.economyBridge().available()) {
            plugin.getLogger().warning("[saldo-inicial] Economia indisponível; "
                    + jogador.getName() + " não recebeu o saldo inicial ainda.");
            return;
        }

        OptionalDouble saldo = plugin.economyBridge().balance(jogador);
        if (saldo.isEmpty()) {
            plugin.getLogger().warning("[saldo-inicial] Não foi possível ler o saldo de "
                    + jogador.getName() + "; tentaremos no próximo login.");
            return;
        }

        double atual = saldo.getAsDouble();
        double faltando = alvo - atual;

        if (faltando <= 0) {
            // Já tem o suficiente: marca como resolvido e não credita nada.
            marcar(id);
            return;
        }

        if (!plugin.economyBridge().deposit(jogador, faltando)) {
            plugin.getLogger().warning("[saldo-inicial] Depósito falhou para "
                    + jogador.getName() + "; tentaremos no próximo login.");
            return;
        }

        marcar(id);
        plugin.getLogger().info("[saldo-inicial] " + jogador.getName() + " recebeu "
                + (int) faltando + " (saldo era " + (int) atual + ", alvo " + alvo + ").");

        EconomyCatalog catalog = plugin.economyCatalog();
        jogador.sendMessage(plugin.mini().deserialize(
                "<gray>Saldo inicial creditado: <gold><valor></gold>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "valor", catalog.money(alvo))
        ));
    }

    private void marcar(UUID jogador) {
        dados.set("recebido." + jogador, true);
        try {
            File destino = arquivo();
            File pasta = destino.getParentFile();
            if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
                plugin.getLogger().warning("Não foi possível criar a pasta de " + FILE_NAME + ".");
                return;
            }
            dados.save(destino);
        } catch (IOException erro) {
            // Sem o arquivo, o jogador seria reavaliado no próximo login. A
            // trava de "complementar até o alvo" impede pagamento duplicado,
            // mas isso ainda precisa aparecer no console.
            plugin.getLogger().severe("Falha ao gravar " + FILE_NAME + ": " + erro.getMessage());
        }
    }
}
