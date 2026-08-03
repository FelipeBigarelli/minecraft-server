# 🎮 Servidor Spigot 26.2 + Plugin Base

Kit completo para rodar um servidor Minecraft Spigot em Linux/VPS e desenvolver plugins pra ele.

---

## ⚠️ Leia primeiro: a versão do Minecraft mudou de formato

Desde 2026 a Mojang abandonou o `1.x.y`. O formato agora é **`ano.drop.patch`**:

| Versão | O que é |
|---|---|
| `26.1` | Primeiro drop de 2026 |
| `26.1.2` | Segundo hotfix desse drop |
| `26.2` | Segundo drop de 2026 — **atual**, lançado em 16/06/2026, codinome *Chaos Cubed* |

Consequências práticas:

- 🔴 **Java 25 é obrigatório.** As builds da 26.x são compiladas com JDK 25. Java 21 não sobe.
- 🔴 **Se você faz parsing de versão em código**, qualquer lógica que assume prefixo `"1."` quebra.
- 🟢 Não houve mudanças significativas de API entre a 26.1 e a 26.2 — plugins que usam só a API pública devem seguir funcionando.

---

## 📁 O que tem aqui

```
mc-server/
├── scripts/
│   ├── setup.sh            # roda 1x: instala JDK 25 + compila o Spigot
│   ├── start.sh            # sobe o servidor com GC otimizado
│   ├── backup.sh           # backup rotativo do mundo
│   └── minecraft.service   # unit systemd
├── config/
│   └── server.properties   # já ajustado para 26.2
└── plugin/                 # projeto Maven do plugin base
    ├── pom.xml
    └── src/main/
        ├── java/codes/biga/bigacore/
        │   ├── BigaCore.java         # ciclo de vida
        │   ├── BigaCommand.java      # /biga com subcomandos + tab complete
        │   └── JogadorListener.java  # eventos de join/quit
        └── resources/
            ├── plugin.yml
            └── config.yml
```

---

## 🚀 Parte 1 — Subir o servidor

### Requisitos da VPS

| Jogadores | RAM | vCPU |
|---|---|---|
| 1–5 | 2 GB | 1 |
| 5–20 | 4 GB | 2 |
| 20–50 | 8 GB | 4 |

Minecraft é **single-thread para o tick principal**. Clock alto por core importa muito mais que quantidade de cores — não adianta pegar 8 vCPU fracas.

### Instalação

```bash
# 1. Envie a pasta para a VPS
scp -r mc-server usuario@seu-ip:~/

# 2. Torne os scripts executáveis
cd ~/mc-server && chmod +x scripts/*.sh

# 3. Rode o setup (demora 5-15 min compilando o Spigot)
bash scripts/setup.sh

# 4. Copie a config e suba
cp config/server.properties ~/minecraft/
cp -r scripts ~/minecraft/
cd ~/minecraft && bash scripts/start.sh
```

> 💡 **Por que compilar?** A Spigot não distribui `.jar` pronto por questões de licença — o BuildTools baixa e monta localmente. É normal e é o jeito oficial.

### Rodar em background

Duas opções:

**`screen`** — simples, dá acesso ao console:
```bash
screen -S mc -dm bash scripts/start.sh
screen -r mc                # entrar no console
# Ctrl+A depois D para sair sem derrubar
```

**`systemd`** — reinicia sozinho se cair, sobe no boot:
```bash
sudo cp scripts/minecraft.service /etc/systemd/system/
sudo nano /etc/systemd/system/minecraft.service   # ajuste User= e os paths
sudo systemctl daemon-reload
sudo systemctl enable --now minecraft
sudo journalctl -u minecraft -f
```

### Firewall

```bash
sudo ufw allow 25565/tcp
sudo ufw allow OpenSSH
sudo ufw enable
```

Não abra a 25575 (RCON) para a internet.

---

## 🔧 Parte 2 — Desenvolver o plugin

### Compilar

```bash
cd plugin
mvn clean package
# saída: target/bigacore-1.0.0.jar
cp target/bigacore-1.0.0.jar ~/minecraft/plugins/
```

Precisa de **JDK 25** e Maven 3.9+ na máquina de desenvolvimento.

### Testar

No console do servidor:
```
/biga info      → versão do plugin e do servidor
/biga reload    → recarrega config.yml (precisa de OP)
/biga voar      → alterna voo (precisa de OP)
```

### Decisões de API que valem entender

**`api-version: '26.2'` no plugin.yml.** Os valores aceitos vão de `1.13` até `26.2`. Declarar a mais recente diz ao servidor para *não* aplicar camadas de compatibilidade legada. Se você omitir, o plugin carrega em modo legado e cospe um aviso no console.

**Spigot ≠ Paper.** Isso pega muita gente:

| Recurso | Spigot | Paper |
|---|---|---|
| `Component` / Adventure (texto rico) | ❌ | ✅ |
| `getPluginMeta()` | ❌ (use `getDescription()`) | ✅ |
| `ChatColor` + String | ✅ | ✅ (deprecado) |

Este plugin usa **só API Spigot pura**, então roda tanto em Spigot quanto em Paper. Código escrito para Paper não roda em Spigot.

**`scope=provided` no pom.xml.** A API já existe no servidor em runtime. Se você usar `compile`, ela é embutida no jar e você ganha conflitos de classloader.

**`EventPriority.MONITOR`** é para *observar* sem alterar — roda por último. Se você vai cancelar ou modificar um evento, use `NORMAL` ou `HIGH`.

---

## ⚡ Parte 3 — Performance

Depois do primeiro boot, o servidor gera `spigot.yml` e `bukkit.yml`. Os ajustes que mais rendem:

**`server.properties`** (já vem ajustado no kit):
- `simulation-distance=6` — este é o parâmetro de maior impacto. Controla quantos chunks realmente processam mobs e redstone. Baixar aqui alivia muito mais que baixar `view-distance`, e o jogador quase não percebe.
- `view-distance=8` — pesa em CPU e banda.

**`spigot.yml`** — em `world-settings.default`:
```yaml
mob-spawn-range: 6
entity-activation-range:
  animals: 16
  monsters: 24
  misc: 8
merge-radius:
  item: 3.5
  exp: 4.0
ticks-per:
  hopper-transfer: 8
  hopper-check: 8
```

Para diagnosticar lag de verdade, instale o **spark** (`/spark profiler start`). Ele mostra qual plugin ou qual chunk está consumindo tick — muito melhor que chutar.

---

## 🔒 Segurança

- ✅ `online-mode=true` — **nunca** deixe `false` em servidor público. Com `false`, qualquer pessoa entra usando qualquer nick, incluindo o seu.
- ✅ `enable-command-block=false` a menos que precise.
- ✅ Rode como usuário dedicado, nunca root.
- ✅ Backups testados. `backup.sh` no cron:
  ```bash
  0 4 * * * /bin/bash $HOME/minecraft/scripts/backup.sh >> $HOME/minecraft/backup.log 2>&1
  ```
- ⚠️ **Cuidado com plugins de fonte duvidosa.** Já houve casos de malware distribuído via contas comprometidas de autores no SpigotMC. Baixe só de SpigotMC, Modrinth ou Hangar, e prefira plugins com código aberto.

---

## ✅ O que eu validei e o que não

**Validado aqui:**
- ✔️ Sintaxe dos 3 scripts bash (`bash -n`)
- ✔️ `plugin.yml` e `config.yml` parseiam como YAML válido
- ✔️ `pom.xml` parseia como XML válido
- ✔️ Estrutura dos 3 arquivos Java: chaves balanceadas, nome de classe = nome do arquivo, package correto, zero resíduo de API Paper-only

**Não validado — você precisa conferir:**
- ❌ **`mvn clean package` não foi executado.** Não há JDK nem acesso ao Maven Central no meu ambiente. Um erro de tipo ou assinatura de método só aparece no seu primeiro build.
- ❌ **`setup.sh` não foi executado.** O nome do pacote `openjdk-25-jdk` pode não existir nos repositórios da sua distro — nesse caso use o [Adoptium Temurin 25](https://adoptium.net/temurin/releases/?version=25).
- ❌ Nada foi testado com o servidor de fato rodando.

Se o build reclamar de algo, me manda o erro que eu ajusto.

---

## 🐛 Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `UnsupportedClassVersionError` | Java < 25. Confira `java -version`. |
| Servidor fecha na hora | `eula.txt` sem `eula=true` |
| `Plugin is not marked as compatible` | `api-version` ausente ou inválido no plugin.yml |
| Ninguém consegue conectar | Firewall, ou `server-ip` preenchido quando devia estar vazio |
| BuildTools falha | Pouca RAM. Aumente o `-Xmx2G` no setup.sh |
| Lag spikes periódicos | Autosave ou GC. Instale o spark e meça. |
