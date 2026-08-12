# 🎮 Servidor Minecraft Paper 26.2 + BigaCore

Servidor Minecraft com plugin próprio em Java. Clone, rode um script, jogue.

O objetivo de longo prazo não é "ter um servidor" — é construir um mundo com um
**narrador vivo**, alimentado pela API do Claude, que reage ao que os jogadores
de fato fazem. Ver [HANDOFF.md](HANDOFF.md), seção 8.

> ## 🆕 Mudando de computador?
>
> Comece por **[NOVO-PC.md](NOVO-PC.md)**. Ele cobre Minecraft Launcher, Git,
> clone, setup, recuperação do mundo, diagnóstico e primeiro boot na ordem certa.

---

## 🚀 Instalação

O fluxo automático é pensado principalmente para Ubuntu/Debian com `sudo`.
Em outras distribuições, o script reconhece alguns gerenciadores de pacotes para
as dependências básicas, mas o JDK 25 pode precisar ser instalado manualmente.

```bash
git clone https://github.com/FelipeBigarelli/minecraft-server.git
cd minecraft-server
bash server/scripts/setup.sh
```

Antes do primeiro boot, valide sem iniciar o servidor:

```bash
cd ~/minecraft
bash scripts/doctor.sh
```

Para subir:

```bash
cd ~/minecraft && bash scripts/start.sh
```

Conecte em `localhost`. Para desligar, digite `stop` no console — **nunca `kill -9`**.

### 🌍 Levar o mundo junto

O mundo **não** está no Git — são arquivos binários que mudam a cada save, e o
histórico do Git guardaria cópias novas a cada commit. Snapshots compartilháveis
do mundo podem viver em **GitHub Releases**, fora do histórico.

Há dois artefatos diferentes e eles **não podem ser confundidos**:

| Comando | Conteúdo | Pode ir para Release pública? |
|---|---|---|
| `bash scripts/backup.sh` | mundo + plugins + configs + listas administrativas | ❌ **NÃO — backup privado** |
| `bash scripts/export-world.sh` | somente os mundos | ✅ com revisão de privacidade |

O `backup.sh` pode conter futuramente senha de RCON, segredo de proxy, banco de
plugin ou outras credenciais do runtime. **Nunca publique `mc-backup-*.tar.gz`.**

O próprio mundo também pode conter UUIDs, inventários e posições de jogadores;
por isso `export-world.sh` é compartilhável do ponto de vista de configs/segredos,
mas uma publicação realmente pública ainda exige decisão consciente de privacidade.

Para gerar um snapshot novo, com o servidor desligado:

```bash
cd ~/minecraft
bash scripts/export-world.sh
```

Para restaurar qualquer snapshot novo no PC de destino:

```bash
bash ~/minecraft/scripts/restore-world.sh ~/Downloads/mc-world-AAAA-MM-DD_HHMMSS.tar.gz
```

`restore-world.sh` também aceita o backup legado da release `mundo-2026-08-04`,
mas extrai **somente os mundos**, ignorando plugins, configs, ops, whitelist e
credenciais que possam existir no arquivo antigo. Veja [NOVO-PC.md](NOVO-PC.md).

Sem snapshot, o **primeiro boot do Paper** gera um mundo novo do zero.

### Variáveis opcionais

```bash
MC_OP=SeuNick bash server/scripts/setup.sh   # já te deixa operador
RAM=8G        bash server/scripts/setup.sh   # padrão persistente passa a ser 8G
SERVER_DIR=~/mc bash server/scripts/setup.sh # instala e lembra esse diretório
FORCE_CONFIG=1 bash server/scripts/setup.sh  # sobrescreve configs/defaults existentes
```

O setup grava os defaults operacionais em `~/minecraft/scripts/server.env` (ou
no `SERVER_DIR` escolhido). Esse arquivo guarda somente diretório, versão, build,
RAM e flavor — **nenhum segredo**. Na hora de iniciar, uma variável passada no
comando ainda tem prioridade, por exemplo `RAM=2G bash scripts/start.sh`.

### O que o setup faz

1. Instala o que faltar: `curl`, `tar`, `screen`, `maven`, **OpenJDK 25**
2. Confere que o `JAVA_HOME` aponta para o JDK 25
3. Baixa o **Paper 26.2 build 92** e **verifica o SHA256**
4. Aceita a EULA da Mojang ao executar o setup
5. Instala os configs — **sem sobrescrever** nada que já exista
6. Instala scripts de start, backup, export, restore e diagnóstico
7. Grava os defaults persistentes em `scripts/server.env`
8. Compila o BigaCore e instala em `plugins/`
9. Opcionalmente resolve seu UUID na Mojang e te define como operador

É **idempotente**: rodar de novo não destrói mundo nem config editado.

---

## 📁 Estrutura

Duas pastas, propósitos distintos. **Este repositório é só o código-fonte.**

| Caminho | O que é | Versionado |
|---|---|---|
| `minecraft-server/` (este repo) | Código, scripts, templates de config | ✅ |
| `~/minecraft/` | O servidor rodando: mundo, logs, jars | ❌ nunca |

```text
minecraft-server/
├── NOVO-PC.md                # caminho completo para uma máquina nova
├── server/
│   ├── scripts/
│   │   ├── setup.sh          # instala tudo do zero (idempotente)
│   │   ├── start.sh          # sobe o servidor com Aikar's flags
│   │   ├── doctor.sh         # valida o ambiente sem iniciar o servidor
│   │   ├── restore-world.sh  # restaura SOMENTE os mundos de um tar.gz
│   │   ├── backup.sh         # backup PRIVADO completo, rotativo e verificado
│   │   ├── export-world.sh   # snapshot SOMENTE dos mundos
│   │   └── minecraft.service # unit systemd opcional; não instalada
│   └── config/
│       ├── server.properties
│       ├── bukkit.yml
│       ├── spigot.yml
│       ├── commands.yml
│       └── paper/
│           ├── paper-global.yml
│           └── paper-world-defaults.yml
└── plugin/
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java          # ponto de entrada
        │   ├── BigaCommand.java       # /biga e subcomandos
        │   ├── JogadorListener.java   # join/quit
        │   ├── EconomyCatalog.java    # lê economy.yml
        │   ├── VaultEconomyBridge.java# ponte Vault por reflexão
        │   ├── StartingBalance.java   # aplica o saldo inicial
        │   ├── ServerBuyback.java     # recompra com teto diário
        │   ├── SpawnShopBuilder.java  # analisa terreno e constrói a loja
        │   ├── ShopPreview.java       # preview por partículas
        │   ├── ShopSnapshot.java      # snapshot e rollback
        │   └── ShopDisplays.java      # item flutuante sobre as bancas
        └── resources/
            ├── plugin.yml
            ├── config.yml
            └── economy.yml
```

No runtime, `scripts/server.env` é gerado pelo setup e guarda os defaults usados
por `start.sh`, `backup.sh`, `export-world.sh`, `restore-world.sh` e `doctor.sh`.
Ele não é um arquivo de segredos.

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

Desde 2026 a Mojang abandonou o `1.x.y` e adotou **`ano.drop.patch`**.

| Componente | Versão | Por que importa |
|---|---|---|
| Minecraft / Paper | **26.2 build 92** | O cliente deve usar 26.2 |
| Java | **25** | A 26.x não sobe em Java < 25 |
| `api-version` no plugin.yml | `'26.2'` | Sem camada de compatibilidade legada |
| `maven.compiler.release` | `25` | A API vem em bytecode 25 |
| `paper-api` no pom | `26.2.build.92-stable` | **Fixa**, casada com o jar |

⚠️ Qualquer código que faça parsing de versão assumindo prefixo `"1."` quebra.

### Atualizar a build do Paper

Três lugares, no mesmo commit:

```bash
# 1. server/scripts/setup.sh  → PAPER_BUILD e PAPER_SHA256_DEFAULT
# 2. server/scripts/start.sh  → fallback de DEFAULT_PAPER_BUILD
# 3. plugin/pom.xml           → <paper.version>

# Depois, FORCE_CONFIG=1 no setup atualiza scripts/server.env no runtime.
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

⚠️ **Não use `/reload confirm`.** Prefira reiniciar o servidor.

### Comandos

```text
/biga info                      versão do plugin e do servidor
/biga eco status|saldo|regras   política econômica e seu saldo
/biga eco preco <item>          consulta o catálogo
/biga eco vender [qtd]          vende ao servidor o item da mão (com teto diário)
/biga loja local|validar        onde a Biga Market ficará e se a área é segura
/biga loja preview              desenha a loja com partículas, sem tocar em bloco
/biga loja criar CONFIRMAR      constrói                          (precisa de OP)
/biga loja desfazer CONFIRMAR   reverte a última construção       (precisa de OP)
/biga reload                    recarrega config.yml e economy.yml (precisa de OP)
/biga voar                      alterna voo                        (precisa de OP)
```

Detalhes da economia em [ECONOMIA.md](ECONOMIA.md) e da loja em
[LOJA-SPAWN.md](LOJA-SPAWN.md).

### Decisões de API

- **`paper-api`, não `spigot-api`**: o projeto decidiu usar APIs atuais do Paper.
- **Versão fixa, nunca range**: build reprodutível e casada com o servidor.
- **`scope=provided`**: a API já existe no runtime.
- **Adventure/MiniMessage** no lugar de `ChatColor`.
- **`Placeholder.unparsed()`** para dados externos.
- Texto futuro vindo da IA deve ser tratado como **não confiável** e nunca ganhar
  tags arbitrárias como `click:run_command`.

### 🔴 Regras inegociáveis para o narrador com IA

- **Nunca fazer I/O na thread principal.**
- **Voltar à thread principal antes de tocar no mundo.**
- **API key nunca no código nem no Git.**
- **Rate limit e cache desde o início.**
- **API fora do ar não pode derrubar o servidor.**

---

## 🛠️ Operação

```bash
cd ~/minecraft && bash scripts/doctor.sh         # diagnóstico sem boot
cd ~/minecraft && bash scripts/start.sh          # subir
RAM=2G bash scripts/start.sh                     # override temporário da RAM
bash scripts/restore-world.sh arquivo.tar.gz     # restaura só os mundos
bash scripts/backup.sh                           # backup PRIVADO completo
bash scripts/export-world.sh                     # snapshot dos mundos (server off)
tail -f ~/minecraft/logs/latest.log              # log ao vivo
```

### Backup no cron

```bash
crontab -e
0 4 * * * /bin/bash $HOME/minecraft/scripts/backup.sh >> $HOME/minecraft/backup.log 2>&1
```

O backup completo é **privado**. `export-world.sh` existe justamente para não
confundir disaster recovery com compartilhamento do mundo.

---

## 🔒 Segurança

- ✅ `online-mode=true` sempre.
- ✅ `stop` no console, nunca `kill -9`.
- ✅ Backup antes de mexer em mundo ou versão.
- ✅ Restore de arquivo legado passa por `restore-world.sh` e não repõe configs.
- ⚠️ Não abrir a porta 25575 (RCON) para a internet.
- ⚠️ Plugin de terceiro só de fonte oficial.

### 🔴 Campos secretos nunca entram no repositório

| Arquivo | Campo | Onde preencher, se precisar |
|---|---|---|
| `server/config/server.properties` | `rcon.password=` | só no runtime |
| `server/config/paper/paper-global.yml` | `velocity.secret` | só no runtime |

O mesmo vale para a API key do Claude. E **não publique `mc-backup-*.tar.gz`**:
o `.gitignore` não protege arquivos enviados manualmente como assets de Release.

---

## 🐛 Problemas comuns

| Sintoma | Causa | Solução |
|---|---|---|
| `UnsupportedClassVersionError` | Java < 25 | rode `setup.sh`/corrija Java |
| Maven usa JDK errado | `JAVA_HOME` incorreto | confira `mvn -version` |
| `session.lock: already locked` | servidor já está rodando | não abra segunda instância |
| Mundo não aparece após migrar | arquivo não restaurado | use `restore-world.sh` |
| Tags MiniMessage aparecem como texto | config antiga | use sintaxe MiniMessage |
| Dúvida se PC novo está pronto | — | rode `scripts/doctor.sh` |

---

## 📚 Documentação

| Arquivo | Para quê |
|---|---|
| **[NOVO-PC.md](NOVO-PC.md)** | instalação do Launcher até entrar no servidor em máquina nova |
| [HANDOFF.md](HANDOFF.md) | estado completo do projeto, decisões e armadilhas; leia primeiro ao desenvolver |
| [PLANO-EXECUCAO.md](PLANO-EXECUCAO.md) | infra → memória → narrador → conteúdo |
| [COMO-RODAR.md](COMO-RODAR.md) | guia operacional do dia a dia |
| [ECONOMIA.md](ECONOMIA.md) | moeda, catálogo, Admin Shop e recompra |
| [LOJA-SPAWN.md](LOJA-SPAWN.md) | a Biga Market: detecção de terreno, preview e rollback |
| [docs/sessoes/](docs/sessoes/) | histórico resumido das sessões |
| [rodadas/](rodadas/) | canal de trabalho entre Felipe, ChatGPT e Claude Code |

### 🔄 Retomando desenvolvimento em outro PC

Primeiro conclua [NOVO-PC.md](NOVO-PC.md). Depois abra o Claude Code na pasta do
repositório e use o prompt de retomada em [docs/sessoes/README.md](docs/sessoes/README.md).
