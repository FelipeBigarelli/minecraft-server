# 🎮 Servidor Minecraft Paper 26.2 + BigaCore

Servidor Minecraft com plugin próprio em Java. Clone, rode um script, jogue.

O objetivo de longo prazo não é "ter um servidor" — é construir um mundo com um
**narrador vivo**, alimentado pela API do Claude, que reage ao que os jogadores
de fato fazem. Ver [HANDOFF.md](HANDOFF.md), seção 8.

---

## 🚀 Instalação

Em qualquer Linux com `sudo`. Leva ~1 minuto.

```bash
git clone <url-do-repo> minecraft-server
cd minecraft-server
bash server/scripts/setup.sh
```

Pronto. Para subir:

```bash
cd ~/minecraft && bash scripts/start.sh
```

Conecte em `localhost`. Para desligar, digite `stop` no console — **nunca Ctrl+C**.

### 🌍 Levar o mundo junto (opcional)

O mundo **não** está no Git — são 17 MB de binário que mudam a cada save, e o
histórico do Git guardaria uma cópia nova a cada commit, para sempre. Ele vive
em **GitHub Releases**, fora do histórico:

```bash
# depois do setup.sh, com o servidor DESLIGADO:
cd ~/minecraft
gh release download mundo-2026-08-04 -p "mc-backup-*.tar.gz" \
   -R FelipeBigarelli/minecraft-server
tar xzf mc-backup-*.tar.gz && rm mc-backup-*.tar.gz
bash scripts/start.sh
```

Sem isso, o `setup.sh` gera um mundo novo do zero — restaurar é opcional.

Para publicar um snapshot novo depois de jogar:

```bash
cd ~/minecraft && bash scripts/backup.sh        # com o servidor desligado
gh release create mundo-AAAA-MM-DD ~/minecraft-backups/mc-backup-*.tar.gz \
   --title "Mundo — DD/MM/AAAA"
```

⚠️ Se o nome do backup terminar em `-quente`, ele foi feito com o servidor no
ar e pode ter um chunk capturado no meio da escrita. Prefira publicar um feito
com o servidor desligado.

### Variáveis opcionais

```bash
MC_OP=SeuNick bash server/scripts/setup.sh   # já te deixa operador
RAM=8G        bash server/scripts/setup.sh   # padrão é 4G
SERVER_DIR=~/mc bash server/scripts/setup.sh # instalar em outro lugar
FORCE_CONFIG=1  bash server/scripts/setup.sh # sobrescrever configs existentes
```

### O que o setup faz

1. Instala o que faltar: `curl`, `tar`, `screen`, `maven`, **OpenJDK 25**
2. Confere que o `JAVA_HOME` aponta para o JDK 25 (o Maven ignora o
   `update-alternatives` e obedece só a essa variável)
3. Baixa o **Paper 26.2 build 92** e **verifica o SHA256** — descarta o arquivo
   se não bater
4. Aceita a EULA da Mojang
5. Instala os configs — **sem sobrescrever** nada que já exista
6. Compila o BigaCore e instala em `plugins/`
7. Opcionalmente resolve seu UUID na Mojang e te define como operador

É **idempotente**: rodar de novo não destrói mundo, config editado nem nada.

---

## 📁 Estrutura

Duas pastas, propósitos distintos. **Este repositório é só o código-fonte.**

| Caminho | O que é | Versionado |
|---|---|---|
| `minecraft-server/` (este repo) | Código, scripts, templates de config | ✅ |
| `~/minecraft/` | O servidor rodando: mundo, logs, jars | ❌ nunca |

```
minecraft-server/
├── server/
│   ├── scripts/
│   │   ├── setup.sh          # instala tudo do zero (idempotente)
│   │   ├── start.sh          # sobe o servidor com Aikar's flags
│   │   ├── backup.sh         # backup rotativo, com verificação
│   │   └── minecraft.service # unit systemd (opcional)
│   └── config/               # templates — o servidor usa cópias
│       ├── server.properties
│       ├── bukkit.yml
│       ├── spigot.yml        # timeout-time e restart-script corrigidos
│       ├── commands.yml
│       └── paper/
│           ├── paper-global.yml
│           └── paper-world-defaults.yml
└── plugin/                   # projeto Maven do BigaCore
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java        # ciclo de vida do plugin
        │   ├── BigaCommand.java     # /biga info|reload|voar + tab complete
        │   └── JogadorListener.java # eventos de join/quit
        └── resources/
            ├── plugin.yml
            └── config.yml
```

### 🪤 A armadilha dos dois configs

Os arquivos de config existem **em duplicata**: o projeto guarda o template, o
servidor usa a cópia.

| Editar isto | Efeito |
|---|---|
| `server/config/server.properties` | ❌ nenhum no servidor rodando |
| `~/minecraft/server.properties` | ✅ é o que vale |
| `plugin/src/main/resources/config.yml` | ❌ só vale em instalação nova |
| `~/minecraft/plugins/BigaCore/config.yml` | ✅ é o que vale |

`saveDefaultConfig()` só copia o template **se o arquivo ainda não existir**.
Recompilar não sobrescreve.

**Ao mudar config, mude nos dois lugares** — no servidor para valer agora, no
projeto para não se perder.

---

## 🔢 Versões

Desde 2026 a Mojang abandonou o `1.x.y` e adotou **`ano.drop.patch`**:
`26.2` é o segundo drop de 2026, lançado em 16/06/2026 (*Chaos Cubed*).

| Componente | Versão | Por que importa |
|---|---|---|
| Minecraft / Paper | **26.2 build 92** | O cliente tem que ser exatamente essa |
| Java | **25** | A 26.x não sobe em Java < 25 |
| `api-version` no plugin.yml | `'26.2'` | Sem camada de compatibilidade legada |
| `maven.compiler.release` | `25` | A API vem em bytecode 25 |
| `paper-api` no pom | `26.2.build.92-stable` | **Fixa**, casada com o jar |

⚠️ Qualquer código que faça parsing de versão assumindo prefixo `"1."` quebra.

⚠️ Tutoriais falando em `1.21.x` como "mais recente" estão desatualizados.

### Atualizar a build do Paper

Dois lugares, no mesmo commit:

```bash
# 1. server/scripts/setup.sh  → PAPER_BUILD e PAPER_SHA256_DEFAULT
# 2. server/scripts/start.sh  → PAPER_BUILD
# 3. plugin/pom.xml           → <paper.version>

# Descobrir a build mais recente:
curl -s -H "User-Agent: biga-mc-server/1.0" \
  https://fill.papermc.io/v3/projects/paper/versions/26.2/builds | jq '.[0]'
```

---

## 🔧 Desenvolver o plugin

```bash
cd plugin
mvn clean package && cp target/bigacore-1.0.0.jar ~/minecraft/plugins/
# no console do servidor: stop
cd ~/minecraft && bash scripts/start.sh
```

Confirmar no log: `[BigaCore] BigaCore habilitado.`

⚠️ **Não use `/reload confirm`.** Funciona, mas deixa classes antigas na memória
e gera bugs fantasma difíceis de rastrear. Reiniciar leva ~1 segundo.

### Comandos

```
/biga info      versão do plugin e do servidor (com hover e clique)
/biga reload    recarrega o config.yml           (precisa de OP)
/biga voar      alterna voo                      (precisa de OP)
```

### Decisões de API que valem entender

**`paper-api`, não `spigot-api`.** O `paper-api` é um *superset*: todo
`org.bukkit.*` continua lá, mais Adventure, Brigadier e as APIs do Paper.
Custo: o plugin passa a exigir Paper assim que usar a primeira API exclusiva.

**Versão fixa, nunca range.** Um `[26.2.build,)` parece prático e tem três
defeitos: build não reprodutível, pega pré-release, e quando sair a 26.3 sobe
sozinho para a API de outra versão do Minecraft — compila liso e quebra em
runtime.

**`scope=provided`.** A API já existe no servidor. Embutir no jar causa conflito
de classloader: duas cópias da mesma classe, e o Java trata como tipos
diferentes.

**Adventure em vez de `ChatColor`.** `ChatColor.AQUA + "texto"` é uma String com
códigos de controle costurados dentro — 16 cores, nada de interatividade, e está
deprecado. Um `Component` é um objeto: RGB completo, `hoverEvent`, `clickEvent`,
inspecionável. É a diferença entre montar HTML concatenando strings e montar uma
árvore DOM.

**MiniMessage nos configs.** Marcação em texto, com tags que abrem e fecham:

```yaml
mensagem: "<gradient:dark_purple:gold>A profecia</gradient> <hover:show_text:'12/07'>se cumpre</hover>"
```

Decisivo para o narrador: o Claude pode gerar a marcação **junto** com a prosa,
e você renderiza direto — sem pós-processar em Java para colorir.

**Placeholders por `Placeholder.unparsed()`, não `String.replace`.** O valor
entra como texto literal e nunca é interpretado como marcação. Nick de Minecraft
só aceita `[a-zA-Z0-9_]`, então hoje o risco é teórico — mas no dia em que um
placeholder vier de texto gerado por IA, deixa de ser.

**`EventPriority.MONITOR`** é para *observar* sem alterar; roda por último. Para
cancelar ou modificar um evento, use `NORMAL` ou `HIGH`.

### 🔴 Regras inegociáveis para o narrador com IA

- **Nunca fazer I/O na thread principal.** HTTP, arquivo, banco: tudo em
  `Bukkit.getScheduler().runTaskAsynchronously()`. Bloquear o tick congela o
  servidor para todos. É o erro nº1 em plugin que chama API externa.
- **Voltar à thread principal antes de tocar no mundo.** A API do Bukkit não é
  thread-safe. Padrão: async para buscar → `runTask()` para aplicar.
- **API key nunca no código nem no Git.** Variável de ambiente ou arquivo fora
  do repo. O `.gitignore` já cobre `.env`, `*.key`, `*.pem`, `credentials*`.
- **Rate limit e cache.** Uma morte não pode virar uma chamada de API por morte.
- **Degradar com elegância.** API fora do ar não pode derrubar o servidor.

---

## 🛠️ Operação

```bash
cd ~/minecraft && bash scripts/start.sh          # subir
SERVER_FLAVOR=spigot bash scripts/start.sh       # rollback pro Spigot*
RAM=2G bash scripts/start.sh                     # menos RAM
bash scripts/backup.sh                           # backup manual
tail -f ~/minecraft/logs/latest.log              # log ao vivo
```

\* O rollback exige restaurar um backup pré-Paper: o Paper migrou a estrutura de
pastas do mundo para o formato vanilla (`world/dimensions/...`), e o Spigot
espera `world_nether/` e `world_the_end/` separados. Ver HANDOFF, seção 5.

No console (prompt `>`):

```
op SeuNick          virar admin           list       quem está online
stop                desligar (SEMPRE)     tps        performance
save-all            forçar save           restart    testa o restart-script
/spark profiler start   profiler — já vem embutido no Paper
```

### Rodar em background

```bash
screen -dmS minecraft bash scripts/start.sh
screen -r minecraft     # entrar no console; Ctrl+A depois D para sair
```

Ou via systemd (reinicia sozinho, sobe no boot):

```bash
sudo cp ~/minecraft/scripts/minecraft.service /etc/systemd/system/
sudo nano /etc/systemd/system/minecraft.service   # ajuste User= e os paths
sudo systemctl daemon-reload && sudo systemctl enable --now minecraft
```

### Backup no cron

```bash
crontab -e
0 4 * * * /bin/bash $HOME/minecraft/scripts/backup.sh >> $HOME/minecraft/backup.log 2>&1
```

O `backup.sh` monta a lista de alvos dinamicamente (a estrutura de pastas do
mundo difere entre Paper e Spigot), verifica o `.tar.gz` com `gzip -t` e **falha
ruidosamente** se algo der errado — um backup pela metade não pode parecer
completo.

---

## 🔒 Segurança

- ✅ `online-mode=true` sempre. Com `false`, qualquer um entra com qualquer nick.
- ✅ `stop` no console, nunca `kill -9` — corrompe chunk no meio da escrita.
- ✅ Backup antes de mexer em mundo ou versão.
- ⚠️ Não abrir a porta 25575 (RCON) para a internet.
- ⚠️ Plugin de terceiro só de SpigotMC, Modrinth ou Hangar. Já houve malware
  distribuído via contas comprometidas de autores. Prefira open-source.

### 🔴 Dois campos que nunca podem ser preenchidos neste repositório

Os templates em `server/config/` **estão no Git**. Dois campos aceitam segredo e
hoje estão vazios de propósito:

| Arquivo | Campo | Onde preencher, se precisar |
|---|---|---|
| `server/config/server.properties` | `rcon.password=` | só em `~/minecraft/server.properties` |
| `server/config/paper/paper-global.yml` | `velocity.secret` | só em `~/minecraft/config/paper-global.yml` |

Um segredo commitado fica no histórico do Git **para sempre** — apagar num
commit seguinte não resolve, é preciso reescrever o histórico e rotacionar a
credencial. O runtime não é versionado justamente para ser o lugar deles.

O mesmo vale para a **API key do Claude** quando o narrador entrar: variável de
ambiente ou arquivo fora do repo. O `.gitignore` já bloqueia `.env`, `*.key`,
`*.pem`, `*.p12`, `*.jks`, `secrets.*` e `credentials*.json`.

---

## 🐛 Problemas comuns

| Sintoma | Causa | Solução |
|---|---|---|
| `UnsupportedClassVersionError` | Java < 25 | `sudo update-alternatives --config java` |
| `mvn` usa o JDK errado | Maven ignora o alternatives, lê `JAVA_HOME` | `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` |
| `session.lock: already locked` | Já há um servidor rodando | `pgrep -af paper` e desligue o outro com `stop` |
| Servidor fecha na hora | `eula.txt` sem `eula=true` | Rode o `setup.sh` |
| Mudei o config e nada mudou | Editou o template, não o do servidor | Ver "armadilha dos dois configs" |
| Tags MiniMessage aparecem como texto | Config em sintaxe antiga (`&b`, `{jogador}`) | Migrar para `<aqua>`, `<jogador>` |
| Lag spikes periódicos | Autosave ou GC | `/spark profiler start` e meça |

---

## 📚 Documentação

| Arquivo | Para quê |
|---|---|
| [HANDOFF.md](HANDOFF.md) | Estado completo do projeto, decisões, armadilhas, o plano do narrador |
| [PLANO-EXECUCAO.md](PLANO-EXECUCAO.md) | Plano faseado: infra → coletor de memória → narrador → conteúdo |
| [COMO-RODAR.md](COMO-RODAR.md) | Guia operacional do dia a dia |
| [docs/sessoes/](docs/sessoes/) | Transcripts das sessões de trabalho + **prompt pronto para retomar em outra máquina** |

### 🔄 Retomando o trabalho em outro PC

```bash
git clone https://github.com/FelipeBigarelli/minecraft-server.git
cd minecraft-server && bash server/scripts/setup.sh
```

Depois abra o Claude Code na pasta e cole o prompt de retomada que está em
[docs/sessoes/README.md](docs/sessoes/README.md) — ele diz o que ler, em que
ordem, e quais regras valem sempre.
