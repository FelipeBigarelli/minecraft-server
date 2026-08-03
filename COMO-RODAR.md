# 🎮 Guia rápido — Servidor Minecraft

Referência do meu setup. Se esqueci de algo, está aqui.

---

## ⚡ TL;DR — subir o servidor

```bash
cd ~/minecraft
bash scripts/start.sh
```

Pronto quando aparecer `Done (X.XXXs)! For help, type "help"`.

Pra desligar, digite `stop` no console. **Nunca Ctrl+C.**

---

## 📍 Onde está cada coisa

Duas pastas, propósitos diferentes:

| Caminho | O que é | Versionar no Git? |
|---|---|---|
| `~/Desktop/minecraft-server/` | 📝 Código-fonte, scripts, configs originais | ✅ sim |
| `~/minecraft/` | 🎮 O servidor rodando — mundo, logs, jars | ❌ nunca |

### Dentro do projeto

```
~/Desktop/minecraft-server/
├── server/
│   ├── scripts/
│   │   ├── setup.sh          # roda 1x — compila o Spigot
│   │   ├── start.sh          # sobe o servidor
│   │   ├── backup.sh         # backup do mundo
│   │   └── minecraft.service # unit systemd (não usado ainda)
│   └── config/
│       └── server.properties # cópia original
└── plugin/                   # projeto Maven do BigaCore
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java        # ciclo de vida do plugin
        │   ├── BigaCommand.java     # /biga
        │   └── JogadorListener.java # eventos de join/quit
        └── resources/
            ├── plugin.yml
            └── config.yml
```

### Dentro do servidor

```
~/minecraft/
├── spigot-26.2.jar    # o servidor compilado
├── scripts/           # cópia dos scripts
├── plugins/           # .jar dos plugins vão aqui
├── world/             # overworld
├── world_nether/
├── world_the_end/
├── server.properties  # ← editar AQUI, não no projeto
├── bukkit.yml         # gerado no 1º boot
├── spigot.yml         # gerado no 1º boot
├── ops.json           # quem é admin
└── logs/latest.log
```

⚠️ **Importante:** editar `server.properties` no projeto **não faz nada**. O servidor lê a cópia em `~/minecraft/`. Se quiser versionar uma mudança, edite nos dois lugares.

---

## 🔢 Versões — não misturar

| Componente | Versão | Por quê importa |
|---|---|---|
| Minecraft / Spigot | **26.2** | Cliente tem que ser exatamente essa |
| Java (servidor e build) | **25** | A 26.x não roda em Java < 25 |
| Java do launcher | 21 | Instalado junto, não conflita |
| Maven | 3.8.7 | ok |

💡 Desde 2026 a Mojang usa `ano.drop.patch`. `26.2` = segundo drop de 2026. O formato `1.21.x` ficou pra trás — qualquer tutorial antigo vai falar em `1.x` e está desatualizado.

---

## 🎯 Comandos que eu vou usar sempre

### Servidor

```bash
cd ~/minecraft
bash scripts/start.sh              # subir (4G de RAM, padrão)
RAM=2G bash scripts/start.sh       # subir com menos RAM
bash scripts/backup.sh             # backup manual
tail -f ~/minecraft/logs/latest.log  # ver log de outro terminal
```

### No console do servidor (o prompt `>`)

```
op MeuNick             # virar admin
deop MeuNick
stop                   # desligar (SEMPRE assim)
save-all               # forçar save
list                   # quem está online
whitelist on           # travar acesso
whitelist add Fulano
reload confirm         # recarrega plugins (evitar — prefira reiniciar)
tps                    # ver performance
```

---

## 🔁 Ciclo de desenvolvimento do plugin

Esses 4 passos, sempre nessa ordem:

```bash
# 1. editar o código no VS Code

# 2. compilar e instalar
cd ~/Desktop/minecraft-server/plugin
mvn clean package && cp target/bigacore-1.0.0.jar ~/minecraft/plugins/

# 3. no console do servidor:
stop

# 4. subir de novo
cd ~/minecraft && bash scripts/start.sh
```

Confirmar no log: `[BigaCore] BigaCore habilitado.`

⚠️ **Não usar `/reload confirm`.** Funciona, mas deixa classes antigas penduradas na memória e gera bugs fantasma difíceis de rastrear. Reiniciar leva ~1 segundo.

### 🪤 A armadilha do config.yml — dois arquivos, não um

| Arquivo | Papel | Quando é lido |
|---|---|---|
| `plugin/src/main/resources/config.yml` | 📦 Template embutido no `.jar` | Só na **primeira vez** que o plugin roda |
| `~/minecraft/plugins/BigaCore/config.yml` | ⚙️ O que o servidor realmente usa | Sempre |

O `saveDefaultConfig()` copia o template pra pasta do servidor **apenas se o arquivo ainda não existir**. Recompilar o plugin **não** sobrescreve o config já criado.

**Então:**

- Quer testar uma mudança de config? Edite `~/minecraft/plugins/BigaCore/config.yml` e rode `/biga reload`. Sem recompilar.
- Mudou o template e quer que ele valha? Apague o arquivo do servidor primeiro:
  ```bash
  rm ~/minecraft/plugins/BigaCore/config.yml
  ```
  Ele é recriado no próximo boot.
- Adicionou uma chave nova no template? Ela **não** aparece sozinha num config já existente. Ou adicione na mão, ou apague e deixe recriar.

💡 Mesmo padrão do `server.properties`: o projeto guarda o original, o servidor usa a cópia.

---

## 🔌 Como conectar

| De onde | O que digitar |
|---|---|
| Minha máquina | `localhost` |
| Outro PC na minha rede | resultado de `hostname -I` |

Pra usar `biga.server` em vez de IP (só nesta máquina):

```bash
echo "127.0.0.1    biga.server" | sudo tee -a /etc/hosts
```

**Ainda não resolvido:** jogar com amigos remotos. Radmin VPN não tem cliente Linux. Opções a avaliar: Tailscale (dá hostname real de brinde), playit.gg, ou mover pra VPS.

---

## 🐛 Problemas que já aconteceram

| Sintoma | Causa | Solução |
|---|---|---|
| `java -version` mostra 17 | JDK 17 antigo com prioridade | `sudo update-alternatives --config java` + `javac`, escolher o 25 |
| `mvn -version` mostra Java errado | Maven ignora alternatives, lê `JAVA_HOME` | Já resolvido no `~/.zshrc` |
| `Unsupported file .deb` | Arquivo não existe com esse nome | `ls ~/Downloads/*.deb` |
| BuildTools falha | Espaço no nome da pasta | Nunca usar espaço em path do projeto |
| `Outdated client/server` | Perfil do launcher ≠ 26.2 | Criar installation na 26.2 |
| `UnsupportedClassVersionError` | Java < 25 | Ver primeira linha desta tabela |
| Dump gigante do Watchdog no `stop` | `sync-chunk-writes=true` fazendo fsync em cada chunk | Já resolvido: `sync-chunk-writes=false` + `timeout-time: 300` no `spigot.yml` |
| Mudei o config do plugin e nada mudou | Editei o template em vez do arquivo do servidor | Ver "armadilha do config.yml" acima |

### Se algo quebrar feio

```bash
# ver o que aconteceu
tail -100 ~/minecraft/logs/latest.log

# recompilar o Spigot do zero
rm ~/minecraft/spigot-26.2.jar
cd ~/Desktop/minecraft-server && bash server/scripts/setup.sh
```

O mundo fica em `~/minecraft/world*/` — apagar o jar não afeta o mundo.

---

## ⚙️ Ajustes de performance

Os que mais rendem, em ordem:

**`server.properties`** (em `~/minecraft/`):
- `simulation-distance` — maior impacto. Controla quantos chunks realmente processam mobs e redstone. Está em 6.
- `view-distance` — pesa em CPU e banda. Está em 8.

**`spigot.yml`** → `world-settings.default`:
```yaml
mob-spawn-range: 6
entity-activation-range:
  animals: 16
  monsters: 24
merge-radius:
  item: 3.5
```

Pra descobrir o que está causando lag de verdade, instalar o **spark** e rodar `/spark profiler start`. Muito melhor que chutar.

---

## 🔒 Regras que não quero quebrar

- ✅ `online-mode=true` sempre. Com `false`, qualquer um entra com qualquer nick.
- ✅ `stop` no console, nunca matar o processo.
- ✅ Backup antes de mexer em plugin ou atualizar versão.
- ⚠️ Plugin de terceiro só de SpigotMC, Modrinth ou Hangar. Já houve malware distribuído via contas comprometidas de autores.
- ⚠️ Não abrir a porta 25575 (RCON) pra internet.

---

## 💡 Ideias pro BigaCore

**Mecânicas**
- Item que teleporta pro último ponto de morte
- Sistema de vidas: morreu 3x → espectador até alguém reviver
- Bloco que só quebra com dois jogadores minerando junto

**Eventos de mundo**
- Chuva de meteoros aleatória dropando minério raro
- Boss custom com fases
- Ciclo dia/noite acelerado durante eventos

**Integração externa**
- Webhook pro Discord (morte, conquista, join)
- Dashboard web com stats em tempo real
- NPC ou comando `/pergunta` usando a API do Claude — `HttpClient` do Java 25 + async task pra não travar o tick

⚠️ **Regra de ouro do plugin:** nunca fazer I/O (rede, arquivo, banco) na thread principal. Use `Bukkit.getScheduler().runTaskAsynchronously()`. Bloquear o tick = servidor travado pra todo mundo.
