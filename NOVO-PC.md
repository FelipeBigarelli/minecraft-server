# 🆕 Primeiro boot em um PC novo

Este é o caminho recomendado para reconstruir o servidor **do zero** em outra
máquina sem depender do estado do PC antigo.

O fluxo é:

```text
Minecraft Launcher
    ↓
Git + clone do repositório
    ↓
setup.sh
    ↓
restaurar mundo (opcional)
    ↓
doctor.sh
    ↓
start.sh
    ↓
entrar pelo Minecraft Java 26.2
```

> O servidor continua sendo iniciado manualmente. Não instale systemd, não use
> `screen` e não suba uma segunda instância enquanto estiver configurando o PC.

---

## 1. Instalar o Minecraft Launcher

Use somente a página oficial:

- https://www.minecraft.net/pt-br/download
- suporte oficial: https://help.minecraft.net/hc/pt-br/articles/23907917790093

No Linux baseado em Debian/Ubuntu, escolha **Debian + baseado em Debian**.

Depois de instalar:

1. entre com a mesma conta Microsoft que possui o Minecraft;
2. abra **Minecraft: Java Edition**;
3. crie/selecione uma instalação da versão **26.2**;
4. não dependa de `Latest Release`: o servidor deste projeto está fixado em
   Minecraft/Paper 26.2.

O Java usado pelo Launcher é independente do JDK 25 do servidor.

---

## 2. Instalar Git e clonar o projeto

Em Ubuntu/Debian:

```bash
sudo apt update
sudo apt install -y git
```

Clone a branch principal:

```bash
cd ~
git clone https://github.com/FelipeBigarelli/minecraft-server.git
cd minecraft-server
git branch --show-current
```

Esperado:

```text
main
```

Não é obrigatório clonar em `~/Desktop`. O `setup.sh` descobre a raiz do
projeto automaticamente.

---

## 3. Instalar o servidor

O setup instala/valida Java 25, Maven e dependências, baixa o Paper verificando
SHA256, copia configs/scripts e compila o BigaCore.

Padrão de 4 GB:

```bash
bash server/scripts/setup.sh
```

Com outro limite de RAM:

```bash
RAM=8G bash server/scripts/setup.sh
```

Para já definir um operador durante a instalação:

```bash
MC_OP=SeuNick bash server/scripts/setup.sh
```

Também pode combinar:

```bash
RAM=8G MC_OP=SeuNick bash server/scripts/setup.sh
```

O runtime fica por padrão em:

```text
~/minecraft/
```

Os parâmetros usados ficam persistidos em:

```text
~/minecraft/scripts/server.env
```

---

## 4. Recuperar o mundo

### Opção A — recomendada: snapshot novo do mundo

Se você ainda tiver acesso ao PC antigo, primeiro atualize o repositório nele e
sincronize os scripts:

```bash
cd /caminho/do/minecraft-server
git pull
bash server/scripts/setup.sh
```

Com o servidor antigo **desligado**, gere:

```bash
cd ~/minecraft
bash scripts/export-world.sh
```

O arquivo `mc-world-*.tar.gz` contém somente os mundos. Transfira esse arquivo
para o PC novo por pendrive, nuvem privada ou GitHub Release.

No PC novo:

```bash
bash ~/minecraft/scripts/restore-world.sh ~/Downloads/mc-world-AAAA-MM-DD_HHMMSS.tar.gz
```

O script se recusa a restaurar com o servidor ligado e não toca em plugins,
configs, ops, whitelist ou credenciais.

### Opção B — fallback: release legada de 04/08/2026

Se o PC antigo não estiver disponível, existe a release legada:

```text
mundo-2026-08-04
```

Use especificamente o backup **Paper**:

```text
mc-backup-2026-08-04_142421.tar.gz
```

Não use `PRE-PAPER-2026-08-03_195957.tar.gz` para continuar no Paper.

Baixe o arquivo pela aba **Releases** do GitHub. Depois execute:

```bash
bash ~/minecraft/scripts/restore-world.sh ~/Downloads/mc-backup-2026-08-04_142421.tar.gz
```

Embora o arquivo legado contenha configs antigos, `restore-world.sh` extrai
**somente** `world/`, `world_nether/` e `world_the_end/`. O PC novo continua
usando os configs limpos da `main`.

> A release antiga não deve ser usada como modelo para backups futuros. Novos
> compartilhamentos devem usar apenas `export-world.sh`.

---

## 5. Validar tudo sem iniciar o servidor

Antes do primeiro boot:

```bash
cd ~/minecraft
bash scripts/doctor.sh
```

Ele verifica sem modificar nada:

- Java 25+;
- Java usado pelo Maven;
- Paper 26.2 build 92;
- JAR do BigaCore;
- EULA;
- `online-mode=true`;
- situação do RCON;
- presença do mundo;
- se já existe outro Paper/Spigot rodando.

O resultado ideal termina com:

```text
Diagnóstico aprovado.
```

Se não restaurar mundo nenhum, o doctor mostrará apenas um aviso: isso é normal.
O Paper cria um mundo novo no primeiro boot.

---

## 6. Primeiro boot

Somente depois do doctor:

```bash
cd ~/minecraft
bash scripts/start.sh
```

Espere aparecer algo equivalente a:

```text
[BigaCore] BigaCore habilitado.
Done (...)! For help, type "help"
```

Não abra outro terminal para executar outro `start.sh`.

Para desligar:

```text
stop
```

Nunca use `kill -9`.

---

## 7. Entrar no jogo

No Launcher:

1. abra Minecraft **Java Edition 26.2**;
2. Multiplayer;
3. para jogar no mesmo PC do servidor, use:

```text
localhost
```

Dentro do servidor teste:

```text
/biga info
/biga
```

Se não tiver usado `MC_OP` no setup, no console do servidor:

```text
op SeuNick
```

Depois teste:

```text
/biga voar
/biga reload
```

---

## 8. Checklist final

Antes de considerar a migração concluída:

- [ ] Minecraft Launcher oficial instalado
- [ ] Minecraft Java 26.2 abre normalmente
- [ ] repositório clonado na `main`
- [ ] `setup.sh` terminou sem erro
- [ ] mundo restaurado ou decisão consciente de começar mundo novo
- [ ] `doctor.sh` aprovado
- [ ] Paper iniciou uma única vez
- [ ] `[BigaCore] BigaCore habilitado.` apareceu
- [ ] entrou em `localhost`
- [ ] `/biga info` funciona
- [ ] Nether/End conferidos se o mundo antigo foi restaurado

Se qualquer etapa falhar, **pare nessa etapa**. Não tente corrigir iniciando o
servidor várias vezes nem apagando mundo/config aleatoriamente.
