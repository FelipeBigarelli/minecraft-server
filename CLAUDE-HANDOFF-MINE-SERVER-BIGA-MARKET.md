# HANDOFF PARA CLAUDE — MINE SERVER / BIGA MARKET

> ⚠️ **DOCUMENTO LEGADO — reflete o desenho ANTERIOR do projeto.** Foi o prompt
> da auditoria que originou a Biga Market e já foi superado em pontos-chave. Não
> use os números/decisões abaixo como verdade atual; a fonte corrente é
> [ECONOMIA.md](ECONOMIA.md), [LOJA-SPAWN.md](LOJA-SPAWN.md) e [README.md](README.md).
>
> O que mudou desde este documento:
> - **Moeda:** hoje é **Biga Coin (BC$)**, não "Biga (B$)".
> - **Biga Market:** os baús **passaram a ser Admin Shop** (venda infinita do
>   servidor, money sink). Este doc ainda afirma o contrário.
> - **Tamanho da loja:** footprint atual é **31×29**, não 21×17.
> - **Caminho do repositório:** o real é `~/Desktop/Projetos/minecraft-server`
>   (este doc aponta para `~/Desktop/minecraft-server`).

## Auditoria completa + continuação segura do servidor Minecraft

> **Leia este documento inteiro antes de alterar qualquer arquivo.**
>
> Repositório: `FelipeBigarelli/minecraft-server`
>
> Objetivo imediato:
> 1. auditar o estado real do repositório e do runtime;
> 2. manter o servidor em **4 GB de RAM**;
> 3. revisar e corrigir a Biga Market;
> 4. usar o spawn real restaurado como referência;
> 5. corrigir o cálculo de altura/terreno;
> 6. adicionar preview seguro;
> 7. somente depois permitir construção real.
>
> **NÃO execute `/biga loja criar CONFIRMAR` durante a auditoria.**

---

# 1. CONTEXTO E CAMINHOS

Repositório local esperado:

```text
/home/biga/Desktop/minecraft-server
```

Runtime real:

```text
/home/biga/minecraft
```

Branch principal esperada:

```text
main
```

O runtime **não é o Git**. Mundo e dados reais ficam fora do histórico.

Antes de editar:

```bash
cd /home/biga/Desktop/minecraft-server
git status
git branch --show-current
git log --oneline -20
git pull --ff-only
```

Depois inspecione a árvore REAL:

```bash
find . -maxdepth 4 -type f | sort
```

**Não invente arquivos, classes, pacotes ou caminhos.**

---

# 2. AMBIENTE ATUAL VALIDADO

Sistema:

```text
Ubuntu 20.04.6 LTS (Focal Fossa)
x86_64
```

Java:

```text
Eclipse Temurin 25.0.4 LTS
JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64
```

Maven:

```text
Apache Maven 3.6.3
```

Validar quando necessário:

```bash
java -version
javac -version
mvn -version
echo "$JAVA_HOME"
```

O Maven já foi corrigido para usar Java 25.

Havia um repositório `kali-rolling` misturado ao Ubuntu; ele foi desativado.
**Não reative.**

---

# 3. RAM — MANTER 4 GB

O usuário tentou 8 GB, o PC travou e precisou reiniciar.

Portanto:

```text
RAM runtime = 4G
```

Arquivo:

```text
/home/biga/minecraft/scripts/server.env
```

Deve conter:

```text
DEFAULT_RAM=4G
```

Conferir:

```bash
grep '^DEFAULT_RAM=' /home/biga/minecraft/scripts/server.env
```

Se necessário:

```bash
sed -i 's/^DEFAULT_RAM=.*/DEFAULT_RAM=4G/'   /home/biga/minecraft/scripts/server.env
```

O `server/scripts/start.sh` do repositório já usa `4G` como fallback.

**Não mude o default do projeto para 8G.**

---

# 4. BASELINE DO SERVIDOR

```text
Minecraft 26.2
Paper build 92
Java 25
BigaCore 1.0.0
```

O Paper avisou que existem builds mais recentes.

**Não atualize Paper nesta tarefa.**

A build 92 está fixada e foi validada com os plugins atuais.

---

# 5. ECONOMIA VALIDADA

Plugins:

```text
BigaCore 1.0.0
VaultUnlocked 2.20.2
EternalEconomy 1.0.1
ChestShop 3.13-pre-1
```

Boot validado:

```text
[ChestShop] Using EternalEconomy as the Economy provider now.
[ChestShop] Vault loaded!
[BigaCore] BigaCore habilitado — economia Vault conectada.
Done (...s)!
```

Configuração econômica relevante:

```text
Moeda: Biga (B$)
Saldo inicial: 250 B$
Taxa P2P ChestShop: 4%
Criar loja: 50 B$
Reembolso: 10 B$
```

Princípios:

- jogador ↔ jogador primeiro;
- sem dinheiro por login/AFK/mob/bloco;
- sem `/sellall`;
- admin buyback desativado no lançamento;
- itens raros/progressão principalmente P2P;
- baús da Biga Market NÃO são Admin Shops infinitas.

Não mude isso durante a tarefa da loja.

---

# 6. MUNDO RESTAURADO

Release:

```text
mundo-2026-08-04
```

Backup usado:

```text
mc-backup-2026-08-04_142421.tar.gz
```

SHA256 validado:

```text
57e6986895c2e893d47d01ef8254290f3d875e06f40824c17befa0e93941e0f5
```

Runtime:

```text
/home/biga/minecraft/world
```

O backup já está na estrutura Paper atual.

`doctor.sh` terminou com:

```text
Diagnóstico aprovado. 0 aviso(s).
```

---

# 7. SEGURANÇA OPERACIONAL

Iniciar:

```bash
cd /home/biga/minecraft
bash scripts/start.sh
```

Parar corretamente no console:

```text
stop
```

Nunca:

```text
kill -9
/reload confirm
```

Nunca suba uma segunda instância do Paper.

---

# 8. JOGADOR / PERMISSÃO

Nick observado:

```text
zBigaBigaa
```

Inicialmente `/biga loja local` retornou falta de permissão.

Depois o console tornou o jogador OP e o comando passou a funcionar.

---

# 9. SPAWN REAL OBTIDO NO JOGO

Comando:

```text
/biga loja local
```

Saída observada:

```text
Biga Market — referência do spawn

Spawn real:
world 176, 71, 64

Centro proposto:
world 204, 90, 64

Fachada olha para:
West
```

Config atual:

```yaml
loja-spawn:
  offset-x: 28
  offset-z: 0
  max-desnivel: 3
```

X/Z fazem sentido:

```text
176 + 28 = 204
64 + 0 = 64
```

Como a loja está a leste do spawn, a fachada voltada para `West` também faz
sentido.

---

# 10. ALERTA CRÍTICO — Y=90 É SUSPEITO

Spawn:

```text
Y=71
```

Centro proposto:

```text
Y=90
```

Diferença:

```text
+19 blocos
```

O print mostra **floresta densa e árvores altas** ao redor do spawn.

O código atual em `SpawnShopBuilder.analyze(...)` usa uma lógica equivalente a:

```java
int surfaceY = world.getHighestBlockYAt(x, z);
...
int floorY = maxSurfaceY;
```

Ele percorre a footprint e escolhe o MAIOR Y encontrado.

Isso pode fazer uma copa de árvore ou tronco alto virar a referência de piso.

**Prioridade máxima da auditoria: explicar exatamente por que o resultado virou
Y=90.**

Não aceite:

```text
world 204,90,64
```

como ponto de construção até corrigir/validar isso.

---

# 11. ARQUIVOS CENTRAIS

Leia integralmente antes de editar:

```text
plugin/src/main/java/codes/biga/bigacore/SpawnShopBuilder.java
plugin/src/main/java/codes/biga/bigacore/BigaCommand.java
plugin/src/main/java/codes/biga/bigacore/BigaCore.java
plugin/src/main/resources/config.yml
plugin/src/main/resources/plugin.yml
LOJA-SPAWN.md
ECONOMIA.md
server/scripts/start.sh
server/scripts/setup.sh
server/scripts/doctor.sh
```

Pesquisar também:

```bash
grep -Rni "loja-spawn\|SpawnShopBuilder\|loja validar\|loja criar" .
```

---

# 12. COMANDOS EXISTENTES

Fluxo atual:

```text
/biga loja local
/biga loja validar
/biga loja criar CONFIRMAR
```

`local`:

- spawn real;
- centro calculado;
- fachada.

`validar`:

- alturas;
- superfície suspeita;
- obstruções;
- SEGURA/BLOQUEADA.

`criar CONFIRMAR`:

- altera o mundo;
- revalida imediatamente antes;
- só deve funcionar com `safe=true`.

Preservar a revalidação antes do build.

---

# 13. IMPLEMENTAÇÃO VISUAL ATUAL

Já existe um gerador físico.

Footprint aproximada:

```text
21 x 17
```

Elementos:

- fundação;
- piso;
- paredes;
- telhado alto medieval/rústico;
- gable frontal;
- pequena chaminé;
- toldo lateral;
- barris;
- baús vazios;
- balcão;
- vegetação;
- lanternas;
- placa BIGA MARKET.

Paleta:

```text
SPRUCE_PLANKS
DARK_OAK_PLANKS
STRIPPED_DARK_OAK_LOG
STONE_BRICKS
COBBLESTONE
POLISHED_ANDESITE
ANDESITE
GLASS_PANE
LANTERN
IRON_BARS
BARREL
CHEST
BLUE_WOOL
WHITE_WOOL
FLOWERING_AZALEA_LEAVES
```

Os baús continuam vazios. Não criar Admin Shops infinitas.

---

# 14. PROBLEMA TÉCNICO CENTRAL

O algoritmo precisa diferenciar:

```text
solo real
```

de:

```text
folhas
árvore/tronco
vegetação
líquido
decoração
TileState
estrutura existente
```

Não basta usar `getHighestBlockYAt()` e chamar o resultado de solo.

---

# 15. ESTRATÉGIA DE DETECÇÃO DE GROUND

Primeiro confronte esta proposta com a API Paper REAL usada pelo projeto.

Para cada coluna X/Z da footprint:

1. descobrir o topo;
2. descer a coluna;
3. separar vegetação de chão;
4. achar um bloco realmente aceitável como chão;
5. registrar tudo que estiver acima do ground como ocupação/obstrução;
6. nunca usar copa de árvore como `floorY`.

Vegetação que não deve definir o piso:

```text
leaves
grass/tall grass
fern/large fern
vines
flores
snow superficial
```

Logs/troncos também não devem virar ground automaticamente.

Se houver árvores dentro da footprint, o comportamento mais seguro é:

```text
VALIDAÇÃO BLOQUEADA
```

e escolher outro offset ou limpar conscientemente a área.

**Não apague árvores automaticamente só porque é tecnicamente possível.**

---

# 16. NÃO CONFUNDIR MEDIÇÃO COM DESTRUIÇÃO

Separar:

```text
análise
preview
construção
```

Análise:

```text
não altera blocos
```

Preview:

```text
não altera blocos permanentes
```

Construção:

```text
somente confirmação explícita
```

---

# 17. IMPLEMENTAR PREVIEW

Preferencialmente adicionar:

```text
/biga loja preview
```

Sem blocos permanentes.

Sugestões:

- partículas nos quatro cantos;
- partículas no perímetro;
- marcador central;
- lado da fachada destacado;
- indicação da entrada;
- expirar automaticamente.

Mensagem exemplo:

```text
Biga Market — preview
Spawn: world 176,71,64
Centro X/Z: 204,64
Piso calculado: Y=...
Fachada: West
Footprint: 21x17
Status: SEGURA/BLOQUEADA
```

Se partículas forem inviáveis na API atual, melhore muito o diagnóstico textual,
mas preview visual é preferido.

---

# 18. AVALIAR ROLLBACK

O gerador atual não possui rollback conhecido.

Avaliar adicionar snapshot apenas do volume que será alterado.

Possível fluxo:

```text
/biga loja criar CONFIRMAR
/biga loja desfazer CONFIRMAR
```

Requisitos:

- nunca salvar mundo inteiro;
- salvar só blocos afetados;
- não sobrescrever snapshot silenciosamente;
- validar mundo e coordenadas;
- nunca restaurar automaticamente.

Se rollback inflar demais o escopo, documente a limitação e não execute a
construção durante a auditoria.

---

# 19. COORDENADAS DO PRINT NÃO DEVEM VIRAR HARD-CODE

Não fazer:

```java
new Location(world, 204, 90, 64)
```

Manter:

```text
spawn real + offset configurável
```

A informação real:

```text
spawn = 176,71,64
```

é a âncora.

O `204,90,64` é apenas o resultado do algoritmo atual.

---

# 20. O OFFSET +28 PODE NÃO SER O MELHOR

O print mostra região florestada.

Depois da correção:

1. `/biga loja local`
2. `/biga loja validar`
3. `/biga loja preview`
4. inspeção humana
5. alterar offset se necessário
6. repetir
7. só então construir

Não escolher automaticamente `+28,0` só porque é o default.

---

# 21. DESIGN DESEJADO

Estética:

```text
medieval / rústica / survival-friendly
```

Desejado:

- spruce;
- dark oak;
- pedra;
- telhado alto;
- gable;
- lanternas;
- barris;
- toldo/banca;
- vegetação;
- boa circulação;
- interior simples;
- integração visual com spawn;
- ChestShop P2P;
- fachada voltada ao spawn.

Na posição atual, a fachada deve ser:

```text
West
```

Não tente copiar um render conceitual pixel a pixel. Priorize uma construção
bonita e estável em blocos suportados pela versão atual.

---

# 22. NÃO USAR WORLD EDIT COMO DEPENDÊNCIA OBRIGATÓRIA

O gerador atual não depende de WorldEdit.

Manter assim, salvo autorização explícita e motivo forte.

---

# 23. PADRÕES DE CÓDIGO

## Não usar ternário aninhado

Evitar:

```java
a ? b : c ? d : e
```

Prefira fluxo legível com `if`.

Se encontrar ternário aninhado dentro do código alterado nesta tarefa, corrija.

## Não fazer refactor gigante

Só refatorar se melhorar:

- segurança;
- clareza;
- teste;
- ground detection;
- preview;
- rollback.

---

# 24. COMPATIBILIDADE COM PAPER API

Antes de usar qualquer `Material`, método ou classe:

1. confira a dependência real;
2. compile.

Já houve erro anterior com:

```text
Material.CHAIN
```

na API usada.

A solução foi:

```text
Material.IRON_BARS
```

Não adivinhar símbolos da API.

---

# 25. BUILD LOCAL

```bash
cd /home/biga/Desktop/minecraft-server/plugin
mvn clean package
```

Deve passar.

Warnings de `sun.misc.Unsafe` vindos do Maven/Guava em Java 25 já apareceram e
não representam falha do plugin por si só.

---

# 26. CI

Inspecione:

```text
.github/workflows/
```

Preservar no mínimo:

```text
Build BigaCore = verde
Economy Smoke = verde
```

Nunca use o mundo real do usuário em CI.

---

# 27. AUDITORIA ESPECÍFICA DO `SpawnShopBuilder`

Responda antes de concluir a implementação:

1. como o chão é encontrado?
2. folhas contam como chão?
3. logs contam como chão?
4. qual coluna provavelmente puxou `floorY` para 89?
5. árvores são diferenciadas do solo?
6. líquidos bloqueiam?
7. TileState bloqueia?
8. estruturas existentes bloqueiam?
9. `clearReplaceableVolume` pode apagar algo indevido?
10. a fundação pode criar paredão enorme?
11. footprint real bate com a documentação?
12. fachada West é transformada corretamente?
13. placas/baús têm orientação correta?
14. validação e construção usam exatamente a mesma geometria?
15. build revalida imediatamente antes de alterar?
16. limites de altura do mundo são respeitados?
17. existe rollback?
18. existe risco de construção parcial se ocorrer exceção?

---

# 28. MELHORAR O DIAGNÓSTICO

Queremos sair de algo genérico para algo como:

```text
Biga Market — validação
Spawn: world 176,71,64
Centro X/Z: 204,64
Ground min/max: 70..72
Top min/max: 71..89
Árvores: 14 colunas
Líquidos: 0
TileStates: 0
Estruturas suspeitas: 0
Piso recomendado: 72
Status: BLOQUEADA
Motivo: árvores ocupam a footprint.
```

Isso explica claramente por que `Y=90` era incorreto.

---

# 29. NÃO NIVELAR 19 BLOCOS PARA CIMA

Se o floor virar 90 por causa de árvore:

```text
é bug
```

Não construa uma plataforma enorme acima do spawn.

A solução correta é:

- solo real;
- outro offset;
- ou validação bloqueada.

---

# 30. CONFIG RUNTIME VS DEFAULT DO JAR

Adicionar chave em:

```text
plugin/src/main/resources/config.yml
```

não garante que um config runtime existente receba a chave automaticamente.

Config runtime:

```text
/home/biga/minecraft/plugins/BigaCore/config.yml
```

Se adicionar novas opções, documentar e garantir fallback seguro.

---

# 31. TESTE REAL APÓS ALTERAÇÕES

Com servidor parado, instalar JAR pelo fluxo já existente do projeto.

Depois:

```bash
bash /home/biga/minecraft/scripts/doctor.sh
```

Iniciar:

```bash
cd /home/biga/minecraft
bash scripts/start.sh
```

Dentro do jogo:

```text
/biga loja local
/biga loja validar
/biga loja preview
```

**PARE AÍ.**

Espere o usuário aprovar visualmente.

Nunca execute sozinho:

```text
/biga loja criar CONFIRMAR
```

---

# 32. O PRINT DO SPAWN

Resumo visual fornecido pelo usuário:

- plataforma clara no spawn;
- floresta de árvores altas ao redor;
- vegetação densa;
- comando da Biga Market funcionando após OP.

Texto concreto:

```text
Spawn real: world 176, 71, 64
Centro proposto: world 204, 90, 64
Fachada olha para: West
```

A imagem reforça que o `Y=90` precisa ser auditado.

---

# 33. CRITÉRIOS DE ACEITE

```text
[ ] RAM runtime = 4G
[ ] git status conhecido
[ ] arquivos reais auditados
[ ] origem do Y=90 explicada
[ ] folhas não viram chão
[ ] troncos não viram chão
[ ] árvores são detectadas como ocupação
[ ] líquidos bloqueiam
[ ] TileState bloqueia
[ ] estruturas existentes bloqueiam
[ ] fundação não cria paredão absurdo
[ ] construção não ocorre no startup
[ ] preview existe ou diagnóstico visual equivalente
[ ] /biga loja validar é claro
[ ] mvn clean package passa
[ ] CI continua verde
[ ] LOJA-SPAWN.md atualizado
[ ] construção real não foi executada sem autorização
```

---

# 34. ORDEM RECOMENDADA

## Fase A — segurança

```text
1. git status / branch / pull
2. RAM 4G
3. servidor parado quando houver troca de JAR
4. doctor
```

## Fase B — auditoria

```text
5. SpawnShopBuilder
6. BigaCommand
7. config/plugin.yml
8. explicar Y=90
```

## Fase C — correção

```text
9. separar ground de vegetação
10. melhorar Analysis
11. melhorar validar
12. adicionar preview
13. rollback se razoável
```

## Fase D — qualidade

```text
14. compile
15. testes
16. CI
17. docs
```

## Fase E — runtime

```text
18. instalar jar
19. doctor/start
20. /biga loja local
21. /biga loja validar
22. /biga loja preview
23. aguardar usuário
24. só depois criar
```

---

# 35. PREFERÊNCIA DE FLUXO DO USUÁRIO

O usuário quer processo passo a passo.

Após terminar código e auditoria, não mande 15 ações de runtime de uma vez.

Passe **uma etapa**, aguarde resultado, continue.

---

# 36. PRINCÍPIO FINAL

A coordenada confiável é:

```text
spawn real = world 176,71,64
```

A Biga Market deve continuar derivada do spawn real.

O centro atual:

```text
world 204,90,64
```

é apenas o resultado do offset + algoritmo de altura atual.

**Corrija e valide o cálculo antes de construir qualquer bloco.**

---

# COMEÇAR AGORA

1. audite o repositório real;
2. confirme `DEFAULT_RAM=4G`;
3. leia integralmente `SpawnShopBuilder.java`;
4. explique a origem provável/exata do Y=90;
5. corrija a detecção de ground;
6. implemente preview seguro;
7. compile e teste;
8. atualize documentação;
9. NÃO construa a loja;
10. pare e peça ao usuário para testar `/biga loja validar` e o preview.
