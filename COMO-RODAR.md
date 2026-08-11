# 🎮 Guia rápido — Servidor Minecraft

Referência operacional do servidor. Para decisões, histórico e armadilhas, a
fonte principal continua sendo o [HANDOFF.md](HANDOFF.md).

---

## ⚡ TL;DR

Subir:

```bash
cd ~/minecraft
bash scripts/start.sh
```

Pronto quando aparecer `Done (X.XXXs)! For help, type "help"`.

Desligar: digite `stop` no console. **Não use `/reload confirm` e não mate o
processo à força.**

---

## 📍 Onde está cada coisa

| Caminho | O que é | Git? |
|---|---|---|
| `~/Desktop/minecraft-server/` | código, scripts e templates | ✅ |
| `~/minecraft/` | runtime: mundo, logs, jars e configs reais | ❌ |
| `~/minecraft-backups/` | backups privados de disaster recovery | ❌ |
| `~/minecraft-exports/` | snapshots somente do mundo para compartilhar | ❌ |

### Projeto

```text
minecraft-server/
├── server/
│   ├── scripts/
│   │   ├── setup.sh
│   │   ├── start.sh
│   │   ├── backup.sh         # PRIVADO: mundo + configs + plugins
│   │   ├── export-world.sh   # PUBLICÁVEL: somente mundo
│   │   └── minecraft.service # opcional; não instalado
│   └── config/
└── plugin/
    ├── pom.xml
    └── src/main/
```

### Runtime

```text
~/minecraft/
├── paper-26.2-92.jar
├── spigot-26.2.jar
├── scripts/
│   ├── start.sh
│   ├── backup.sh
│   ├── export-world.sh
│   └── server.env       # defaults persistentes do setup
├── plugins/
├── world/
├── config/
├── server.properties
├── bukkit.yml
├── spigot.yml
├── ops.json
└── logs/latest.log
```

O Paper migrou as dimensões para dentro de `world/dimensions/`. Voltar ao
Spigot exige restaurar o backup pré-Paper; não basta trocar o jar.

---

## 🔢 Versões atuais

| Componente | Versão |
|---|---|
| Minecraft / Paper | **26.2 build 92** |
| Java | **25** |
| Maven | 3.8.7 no ambiente validado |
| `paper-api` | `26.2.build.92-stable` |
| BigaCore | 1.0.0 |

A versão da API fica fixa e deve andar junto com a build do servidor.

---

## 🚀 Instalar do zero

Na raiz do clone:

```bash
bash server/scripts/setup.sh
```

Opções:

```bash
MC_OP=SeuNick bash server/scripts/setup.sh
RAM=8G bash server/scripts/setup.sh
SERVER_DIR=~/mc bash server/scripts/setup.sh
FORCE_CONFIG=1 bash server/scripts/setup.sh
```

`RAM`, `SERVER_DIR`, versão e build usados no setup são persistidos em
`scripts/server.env` no runtime. Variáveis passadas diretamente no `start.sh`
têm prioridade temporária:

```bash
RAM=2G bash scripts/start.sh
```

`FORCE_CONFIG=1` também permite atualizar os defaults persistidos quando você
rodar o setup novamente de forma deliberada.

---

## 💾 Backup privado × snapshot público

Esta distinção é obrigatória.

### Backup privado completo

```bash
cd ~/minecraft
bash scripts/backup.sh
```

Ele inclui mundo e arquivos do runtime como plugins, configs, `ops.json`,
whitelist e bans. No futuro esses diretórios podem conter senhas, tokens ou
bancos.

**Nunca publique `mc-backup-*.tar.gz` em GitHub Release pública.**

Se o servidor estiver ligado, o arquivo recebe `-quente`. Prefira backup com o
servidor desligado quando for fazer uma operação importante.

### Snapshot somente do mundo

Com o servidor desligado:

```bash
cd ~/minecraft
bash scripts/export-world.sh
```

O script se recusa a executar com o servidor ligado e gera:

```text
~/minecraft-exports/mc-world-AAAA-MM-DD_HHMMSS.tar.gz
```

Esse arquivo contém **somente os mundos** e é o artefato correto para GitHub
Release.

Exemplo:

```bash
cd ~/minecraft
ARQUIVO=$(bash scripts/export-world.sh | tail -1)
gh release create mundo-AAAA-MM-DD "$ARQUIVO" \
  -R FelipeBigarelli/minecraft-server \
  --title "Mundo — DD/MM/AAAA"
```

A release `mundo-2026-08-04` é **legada**: foi criada antes dessa separação e
contém `mc-backup-*.tar.gz`. Não use esse formato em releases novas.

---

## 🔁 Desenvolvimento do BigaCore

```bash
cd ~/Desktop/minecraft-server/plugin
mvn clean package && cp target/bigacore-1.0.0.jar ~/minecraft/plugins/
```

Depois:

1. no console do servidor: `stop`;
2. suba novamente com `bash scripts/start.sh`;
3. confirme no log: `[BigaCore] BigaCore habilitado.`

### Não use `/reload confirm`

Reload de plugins pode deixar classes e estado antigos em memória. Para
alteração de código, reinicie o servidor.

Para **somente** recarregar o `config.yml` do BigaCore existe:

```text
/biga reload
```

---

## 🪤 Os dois configs

O projeto guarda templates; o runtime guarda os arquivos realmente lidos.

| Template versionado | Runtime real |
|---|---|
| `server/config/server.properties` | `~/minecraft/server.properties` |
| `plugin/src/main/resources/config.yml` | `~/minecraft/plugins/BigaCore/config.yml` |

Mudar só o template não muda o servidor que já está instalado.

O `saveDefaultConfig()` do BigaCore só cria o config se ele ainda não existir.

---

## 🎯 Comandos úteis

### Terminal

```bash
cd ~/minecraft && bash scripts/start.sh
RAM=2G bash scripts/start.sh
tail -f ~/minecraft/logs/latest.log
bash scripts/backup.sh
bash scripts/export-world.sh
```

### Console Minecraft

```text
op SeuNick
stop
save-all
list
tps
whitelist on
whitelist add Fulano
/spark profiler start
/spark profiler stop
/spark tps
/spark health
```

### BigaCore

```text
/biga info
/biga reload
/biga voar
```

---

## 🔌 Conexão

Local:

```text
localhost
```

Outro computador na mesma rede: use o IP local retornado por:

```bash
hostname -I
```

Acesso remoto para amigos ainda não faz parte do setup atual. Avaliar isso
separadamente antes de abrir o servidor para a internet.

---

## ♻️ Restaurar backup

Sempre com o servidor desligado.

Backup Paper atual:

```bash
cd ~/minecraft
tar xzf ~/minecraft-backups/mc-backup-AAAA-MM-DD_HHMMSS.tar.gz
```

Para voltar ao Spigot, use o backup pré-Paper porque a estrutura dos mundos é
diferente:

```bash
cd ~/minecraft
rm -rf world
tar xzf ~/minecraft-backups/PRE-PAPER-2026-08-03_195957.tar.gz
SERVER_FLAVOR=spigot bash scripts/start.sh
```

Não faça isso sem backup novo antes.

---

## ⚙️ Performance

Os principais knobs atuais ficam em `~/minecraft/server.properties`:

```properties
view-distance=8
simulation-distance=6
```

Não faça "otimização" no escuro. Use o spark para descobrir a causa:

```text
/spark profiler start
/spark profiler stop
/spark tps
/spark health
```

---

## 🔒 Regras de segurança

- `online-mode=true` sempre.
- RCON permanece desligado até haver motivo real para ativar.
- Nunca abrir a porta 25575 diretamente para a internet.
- API key do narrador nunca entra no Git.
- Plugin de terceiro somente de fonte oficial/confiável e com versão conferida.
- `mc-backup-*.tar.gz` é **privado**.
- Release pública de mundo usa `export-world.sh`.
- Backup antes de atualização de versão, migração ou mudança destrutiva.

---

## 🌌 Próximo objetivo

O roadmap completo está em [PLANO-EXECUCAO.md](PLANO-EXECUCAO.md). A ideia de
longo prazo é o narrador vivo, mas o desenvolvimento permanece faseado: primeiro
infraestrutura e memória do mundo; IA somente depois que a coleta estiver sólida.
