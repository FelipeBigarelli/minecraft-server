package codes.biga.bigacore;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Os ymls do disco contra os ymls do jar.
 *
 * <h2>O defeito que isto conserta</h2>
 *
 * Era silencioso e custou noites de jogo. O plugin escreve cada yml no disco
 * na PRIMEIRA subida e nunca mais: {@code saveResource(nome, false)} não
 * sobrescreve, de propósito, para não apagar o que o dono do servidor editou.
 * A consequência não estava escrita em lugar nenhum — toda chave nova que o
 * plugin passou a entender ficou dentro do jar, e o servidor continuou lendo
 * o arquivo do primeiro dia.
 *
 * <p>Na prática: o Sky Wars ganhou ilhas afuniladas e cor de cogumelo, a
 * mineração ganhou tocha infinita, o quiz passou de 48 para 198 perguntas — e
 * nada disso apareceu no servidor, porque os arquivos do disco eram de três
 * semanas antes. O jogador reinstalava o jar, reiniciava, refazia as arenas, e
 * via a arena velha. Não havia aviso nenhum.
 *
 * <h2>Por que não basta comparar as CHAVES</h2>
 *
 * Porque as 150 perguntas novas do quiz entraram numa LISTA. O conjunto de
 * chaves do arquivo velho e do novo é idêntico — {@code perguntas} nos dois —
 * e uma comparação de chaves acharia que está tudo em dia. O que muda é o
 * conteúdo, e é o conteúdo que tem de ser comparado.
 *
 * <h2>Como ele sabe o que é edição do dono e o que é atraso</h2>
 *
 * Guardando duas impressões digitais por arquivo num caderninho ao lado:
 * a do PADRÃO que veio no jar na hora em que o arquivo foi escrito, e a do
 * que foi ESCRITO no disco. Daí cada subida responde sozinha:
 *
 * <ul>
 *   <li>o disco está igual ao que escrevemos e o padrão mudou → ninguém
 *       editou nada, o arquivo é nosso, ATUALIZA (com cópia de segurança);</li>
 *   <li>o disco mudou desde que escrevemos e o padrão também mudou → o dono
 *       editou, e aí ninguém mexe: AVISA, dizendo que chaves novas existem;</li>
 *   <li>o padrão não mudou → nada a fazer, em qualquer caso.</li>
 * </ul>
 *
 * <p>É a diferença entre um plugin que respeita a edição do dono e um que
 * esconde dele metade das próprias novidades.
 */
public final class ConfigsDoDisco {

    /** O caderninho das impressões digitais. */
    public static final String CADERNO = ".versoes-dos-configs.yml";

    private ConfigsDoDisco() {
    }

    /** O que fazer com um arquivo nesta subida. */
    public enum Acao {
        /** Nada: o padrão não mudou, ou o disco já é o padrão. */
        EM_DIA,
        /** O arquivo não existe: escrever o padrão. */
        ESCREVER,
        /** Ninguém editou e o padrão mudou: atualizar com cópia. */
        ATUALIZAR,
        /** O dono editou e o padrão mudou: avisar e não tocar. */
        AVISAR
    }

    /**
     * A decisão para um arquivo.
     *
     * @param shaPadrao       digital do yml que veio no jar agora
     * @param shaDisco        digital do yml que está no disco, ou nulo se não há
     * @param anotadoPadrao   digital do padrão na última vez que escrevemos
     * @param anotadoEscrito  digital do que escrevemos no disco
     */
    public static Acao decidir(String shaPadrao, String shaDisco,
                               String anotadoPadrao, String anotadoEscrito) {
        if (shaDisco == null) {
            return Acao.ESCREVER;
        }
        if (shaPadrao == null || shaPadrao.equals(shaDisco)) {
            return Acao.EM_DIA;
        }
        if (anotadoPadrao == null || anotadoEscrito == null) {
            // Primeira subida com o caderninho: não dá para saber se o
            // arquivo do disco é nosso ou do dono. Avisar é o lado seguro —
            // apagar a edição de alguém por falta de anotação seria o pior
            // dos dois erros possíveis.
            return Acao.AVISAR;
        }
        if (shaPadrao.equals(anotadoPadrao)) {
            return Acao.EM_DIA;
        }
        return shaDisco.equals(anotadoEscrito) ? Acao.ATUALIZAR : Acao.AVISAR;
    }

    /** As chaves que o padrão tem e o arquivo do disco não. */
    public static List<String> faltando(Set<String> padrao, Set<String> disco) {
        List<String> saida = new ArrayList<>();
        for (String chave : padrao) {
            if (!disco.contains(chave)) {
                saida.add(chave);
            }
        }
        return saida;
    }

    /** O recado do aviso, curto o bastante para caber num console. */
    public static String recado(String arquivo, List<String> faltando) {
        StringBuilder texto = new StringBuilder("[configs] o ")
                .append(arquivo).append(" do disco é de uma versão anterior "
                        + "do plugin e foi EDITADO à mão, então ele não foi "
                        + "tocado.");
        if (!faltando.isEmpty()) {
            texto.append(" Faltam ").append(faltando.size())
                    .append(" chave(s) nova(s): ");
            for (int k = 0; k < Math.min(6, faltando.size()); k++) {
                texto.append(k > 0 ? ", " : "").append(faltando.get(k));
            }
            if (faltando.size() > 6) {
                texto.append(", …");
            }
            texto.append('.');
        }
        return texto.append(" Rode /biga configs atualizar ").append(arquivo)
                .append(" CONFIRMAR para trocá-lo pelo novo (com cópia de "
                        + "segurança) ou copie as chaves à mão.").toString();
    }

    /** A digital de um punhado de bytes. */
    public static String digital(byte[] dados) {
        if (dados == null) {
            return null;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] cru = sha.digest(dados);
            StringBuilder texto = new StringBuilder(cru.length * 2);
            for (byte um : cru) {
                texto.append(Character.forDigit((um >> 4) & 0xF, 16));
                texto.append(Character.forDigit(um & 0xF, 16));
            }
            return texto.toString();
        } catch (NoSuchAlgorithmException semSha) {
            return null;
        }
    }

    /** O nome da cópia de segurança, com a data no nome. */
    public static String nomeDaCopia(String arquivo, Date quando) {
        return arquivo + ".bak-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(quando);
    }

    // ------------------------------------------------------------------
    //  A parte que mexe no disco
    // ------------------------------------------------------------------

    /** O que aconteceu com um arquivo, para o relatório. */
    public record Passagem(String arquivo, Acao acao, List<String> faltando) {
    }

    /**
     * Confere todos os ymls e aplica a decisão de cada um.
     *
     * @param forcar quando verdadeiro, ATUALIZA também o que foi editado —
     *               é o {@code /biga configs atualizar}, e nunca a subida
     */
    public static List<Passagem> sincronizar(BigaCore plugin,
                                             List<String> arquivos,
                                             boolean forcar,
                                             String soEste) {
        File pasta = plugin.getDataFolder();
        File caderno = new File(pasta, CADERNO);
        YamlConfiguration anotado = YamlConfiguration.loadConfiguration(caderno);
        List<Passagem> passagens = new ArrayList<>();
        Date agora = new Date();
        boolean mudouOCaderno = false;

        for (String arquivo : arquivos) {
            if (soEste != null && !soEste.equalsIgnoreCase(arquivo)) {
                continue;
            }
            byte[] doJar = lerDoJar(plugin, arquivo);
            if (doJar == null) {
                continue;
            }
            File noDisco = new File(pasta, arquivo);
            byte[] doDisco = lerDoDisco(noDisco);
            String shaPadrao = digital(doJar);
            String shaDisco = digital(doDisco);
            Acao acao = decidir(shaPadrao, shaDisco,
                    anotado.getString(chave(arquivo, "padrao")),
                    anotado.getString(chave(arquivo, "escrito")));
            if (forcar && acao == Acao.AVISAR) {
                acao = Acao.ATUALIZAR;
            }

            List<String> faltando = List.of();
            if (acao == Acao.AVISAR) {
                faltando = faltando(chavesDoJar(plugin, arquivo),
                        chavesDoDisco(noDisco));
            }
            if (acao == Acao.ESCREVER || acao == Acao.ATUALIZAR) {
                if (acao == Acao.ATUALIZAR && doDisco != null) {
                    copiar(noDisco, new File(pasta,
                            nomeDaCopia(arquivo, agora)), plugin);
                }
                if (escrever(noDisco, doJar, plugin)) {
                    anotado.set(chave(arquivo, "padrao"), shaPadrao);
                    anotado.set(chave(arquivo, "escrito"), shaPadrao);
                    mudouOCaderno = true;
                } else {
                    acao = Acao.AVISAR;
                }
            } else if (acao == Acao.EM_DIA
                    && anotado.getString(chave(arquivo, "padrao")) == null) {
                // Disco já igual ao padrão: anota, para a próxima subida
                // saber que este arquivo é nosso.
                anotado.set(chave(arquivo, "padrao"), shaPadrao);
                anotado.set(chave(arquivo, "escrito"), shaDisco);
                mudouOCaderno = true;
            }
            passagens.add(new Passagem(arquivo, acao, faltando));
        }

        if (mudouOCaderno) {
            try {
                anotado.options().setHeader(List.of(
                        "As impressões digitais dos ymls, para o plugin saber",
                        "o que é atraso e o que é edição sua. Não edite nem",
                        "apague: apagar faz o plugin perder a conta e passar a",
                        "avisar sobre arquivos que já estão em dia."));
                anotado.save(caderno);
            } catch (IOException naoSalvou) {
                plugin.getLogger().warning("[configs] não deu para escrever o "
                        + CADERNO + ": " + naoSalvou.getMessage());
            }
        }
        return passagens;
    }

    private static String chave(String arquivo, String qual) {
        return arquivo.replace('.', '_') + "." + qual;
    }

    private static byte[] lerDoJar(BigaCore plugin, String arquivo) {
        try (InputStream dentro = plugin.getResource(arquivo)) {
            return dentro == null ? null : dentro.readAllBytes();
        } catch (IOException naoLeu) {
            return null;
        }
    }

    private static byte[] lerDoDisco(File arquivo) {
        if (!arquivo.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(arquivo.toPath());
        } catch (IOException naoLeu) {
            return null;
        }
    }

    private static boolean escrever(File onde, byte[] conteudo,
                                    BigaCore plugin) {
        try {
            File pai = onde.getParentFile();
            if (pai != null && !pai.isDirectory() && !pai.mkdirs()) {
                return false;
            }
            Files.write(onde.toPath(), conteudo);
            return true;
        } catch (IOException naoEscreveu) {
            plugin.getLogger().warning("[configs] não deu para escrever o "
                    + onde.getName() + ": " + naoEscreveu.getMessage());
            return false;
        }
    }

    private static void copiar(File de, File para, BigaCore plugin) {
        try {
            Files.copy(de.toPath(), para.toPath());
        } catch (IOException naoCopiou) {
            plugin.getLogger().warning("[configs] não deu para copiar o "
                    + de.getName() + ": " + naoCopiou.getMessage());
        }
    }

    private static Set<String> chavesDoJar(BigaCore plugin, String arquivo) {
        try (InputStream dentro = plugin.getResource(arquivo)) {
            if (dentro == null) {
                return Set.of();
            }
            return new LinkedHashSet<>(YamlConfiguration.loadConfiguration(
                            new InputStreamReader(dentro, StandardCharsets.UTF_8))
                    .getKeys(true));
        } catch (IOException naoLeu) {
            return Set.of();
        }
    }

    private static Set<String> chavesDoDisco(File arquivo) {
        if (!arquivo.isFile()) {
            return Set.of();
        }
        return new LinkedHashSet<>(
                YamlConfiguration.loadConfiguration(arquivo).getKeys(true));
    }
}
