# Mundos separados com Multiverse

Instalado em 04/11/2026.

| Plugin | Versão | Para quê |
|---|---|---|
| `multiverse-core-5.8.2.jar` | 5.8.2 | criar e administrar mundos |
| `multiverse-inventories-5.3.6.jar` | 5.3.6 | inventário separado por mundo |

Os dois foram baixados do Modrinth e conferidos por SHA-512. Ambos declaram
suporte a 26.3.

## Por que o Inventories não é opcional

Sem ele o servidor tem **um** inventário por jogador, válido em todos os
mundos. Qualquer coisa pega no criativo caminha para o survival no bolso de
quem vai, e aí não existe mais item escasso — o `economy.yml`, o preço do
ChestShop e a loja do spawn viram enfeite, porque tudo que eles cobram pode
ser gerado de graça num mundo ao lado.

É o único jeito de ter criativo e economia no mesmo servidor.

## O que já estava separado antes do Multiverse

O `plots` do CreativePlots **já é um mundo Bukkit de verdade** — aparece no
boot como `World Settings For [minecraft:plots]` e o WorldGuard já mantém
região separada nele. O Multiverse não precisa criar nada para os plots, e
mexer neles por aqui só arriscaria quebrar o que funciona.

O que o Multiverse acrescenta é inventário e modo de jogo por mundo, além dos
mundos novos.

## Primeiro boot

Depois de reiniciar, no console:

```
/mv list
```

Se o `plots` aparecer na lista, está só sendo acompanhado — sem problema. Se o
console mostrar erro de carregar `plots` duas vezes (CreativePlots e Multiverse
disputando), tire ele do Multiverse com:

```
/mv remove plots
```

`remove` tira do controle do Multiverse. **Não apaga mundo nenhum** — quem apaga
é `/mv delete`, que não deve ser usado aqui.

## Criar os mundos

A sintaxe real da 5.8.2 (lida do jar, não de tutorial velho):

```
/mv create <nome> <environment> [--seed ... --generator ... --world-type ...
           --no-structures --no-adjust-spawn --biome ... --properties k=v,k=v]
```

### Criativo

```
/mv create criativo normal --world-type flat --no-structures
/mv modify criativo set gamemode creative
/mv modify criativo set difficulty peaceful
/mv modify criativo set allow-flight true
/mv modify criativo set alias "&6Criativo"
```

### Minigames

```
/mv create minigames normal --world-type flat --no-structures
/mv modify minigames set gamemode adventure
/mv modify minigames set difficulty easy
/mv modify minigames set alias "&bMinigames"
```

`gamemode` no Multiverse é obrigatório, não sugestão: ele troca o modo de quem
entra, e devolve na saída. Sem isso, um jogador em criativo que atravessasse
para o survival continuaria em criativo.

Para ver a lista inteira de propriedades: digite `/mv modify criativo set ` e
aperte Tab.

### Sobre o terreno dos mundos novos

Datapack é por mundo: fica em `<mundo>/datapacks/`. O FloatingIslands está
dentro de `world/datapacks/`, então **mundo criado pelo Multiverse nasce com
terreno normal**, não com ilhas flutuantes. É o que se quer para criativo e
minigames.

Se um dia quiser outro mundo de ilhas, copie o zip do datapack para
`<mundo>/datapacks/` **antes** de gerar — depois de gerado não converte.

Mundo totalmente vazio (void), do jeito que minigame costuma usar, precisa de um
plugin gerador. O Multiverse sozinho não faz void; `flat --no-structures` é o
mais perto disso sem instalar mais nada.

## Separar os inventários — a parte que protege a economia

> **Medido em 11/11/2026, e corrige o que estava escrito aqui.** A frase antiga
> era "instalar não separa nada". Não é bem assim: mundo que **não está em grupo
> nenhum** já fica isolado sozinho, porque o `config.yml` tem
> `default-ungrouped-worlds: false` ("any world not listed in a group will
> automatically be assigned to the 'default' group" — está desligado).
>
> Prova, não dedução: enquanto o `plots` esteve fora de grupo, às 10:55 o
> Multiverse-Inventories gravou `Multiverse-Inventories/worlds/plots/zBigaBiga.json`
> separado de `Multiverse-Inventories/groups/default/zBigaBiga.json`. Dois
> arquivos = dois inventários. Grupo isolado é `worlds/<mundo>/`, grupo
> compartilhado é `groups/<grupo>/`.
>
> (O `plots` entrou no grupo `default` na tarde do mesmo dia, pelo motivo da
> seção seguinte. O que vale aqui é o mecanismo, não o exemplo.)
>
> Criar o grupo mesmo assim continua valendo por ser explícito — o que não vale é
> acreditar que sem grupo está tudo misturado, ou o contrário, ligar criativo
> achando que depois dá para arrumar.

O grupo `default` de hoje junta `world`, `world_nether` e `world_the_end` com
`shares: all`. Mundo novo que você **quiser** compartilhando entra nele; mundo
que tem que ficar isolado é só não colocar em grupo nenhum.

A ideia: mundos no mesmo grupo compartilham; mundos em grupos diferentes ficam
isolados.

```
/mvinv group create survival inventory,armor,ender_chest,health,hunger,experience,level,potions world,world_nether,world_the_end
/mvinv group create criativo inventory,armor,ender_chest,health,hunger,experience,level,potions criativo
/mvinv group create minigames inventory,armor,ender_chest,health,hunger,experience,level,potions minigames
/mvinv group create plots     inventory,armor,ender_chest,health,hunger,experience,level,potions plots
```

Depois confira, e só então confie:

```
/mvinv info world
/mvinv list
```

### Não inclua `economy` nas shares

O Multiverse-Inventories sabe separar saldo (`economy`, `balance`, `money`) e
seria um desastre aqui: o servidor tem **uma** moeda, o B$, e o saldo vive no
EssentialsX. Separar por mundo daria a cada jogador uma carteira por mundo, as
lojas do spawn cobrariam de uma e o criativo pagaria de outra.

Por isso as shares acima são listadas uma por uma em vez de `everything`.
`everything` é cômodo e inclui coisas que a gente não quer.

### Teste antes de abrir para os outros

1. Entre no survival, deixe algo marcante no inventário.
2. `/mv tp criativo` — o inventário tem que estar **vazio**.
3. Pegue um bloco qualquer do criativo.
4. `/mv tp world` — o bloco do criativo **não** pode ter vindo, e o item do
   survival tem que estar de volta.
5. `/balance` nos dois mundos tem que dar o mesmo número.

Se o passo 4 falhar, pare e conserte o grupo antes de deixar alguém entrar —
depois de um jogador trazer uma pilha de diamante do criativo, não tem como
saber o que é legítimo.

## Portais e viagem

Não instalei o Multiverse-Portals: o servidor já usa **AdvancedPortals**, e dois
plugins de portal no mesmo bloco disputam o evento de entrada.

Para os jogadores viajarem sem ser op, o caminho é LuckPerms:

```
/lp group default permission set multiverse.teleport.self.criativo true
/lp group default permission set multiverse.teleport.self.minigames true
```

Ou, melhor para quem está começando: um portal do AdvancedPortals no spawn, que
não exige comando nenhum.

## Interação com o `/terreno`

A venda de terreno no survival usa lista branca de mundos (`mundos:` no
`terrenos.yml`). Mundo novo **não** entra vendendo terreno — é de propósito, para
ninguém comprar 64x64 no meio da arena de minigame.

Se um dia um mundo novo tiver que vender terreno, acrescente o nome lá e dê
`/biga reload`.

## A armadilha do `/cp delete` (11/11/2026)

O `plots` não é uma pasta de primeiro nível. O Paper guarda ele como **dimensão
aninhada**, em `world/dimensions/minecraft/plots` — por isso `ls` no servidor
não mostra pasta `plots/` nenhuma, e por isso o log escreve
`ServerLevel[world]/minecraft:plots`.

O CreativePlots procura a pasta em `<worldContainer>/plots`. Ela não existe.
Então `/cp delete plots` **não apaga nada** e o `/cp generate` seguinte carimba
plots novos por cima do mundo velho.

Como se vê que aconteceu: o cabeçalho de cada arquivo `.mca` guarda um timestamp
por chunk. Depois do `delete` + `generate` de 11/11 às 10:46, 713 chunks
continuavam com data de 04/11 15:51, todos na faixa `X -448..608, Z 256..560` —
terreno natural do mundo anterior, a 82 blocos da borda da grade de plots, bem
dentro do alcance de visão. É a areia que aparece ao longe.

```sh
# quantos chunks sobraram do mundo anterior
cd ~/minecraft/world/dimensions/minecraft/plots/region
python3 -I -c "
import struct,glob,os,time
corte=time.time()-3600
for p in sorted(glob.glob('r.*.mca')):
    b=open(p,'rb').read(8192)
    velhos=sum(1 for i in range(1024)
               if struct.unpack_from('>I',b,i*4)[0]>>8
               and struct.unpack_from('>I',b,4096+i*4)[0]<corte)
    if velhos: print(p, velhos)
"
```

**Recriar de verdade** pede o servidor parado e a pasta fora na mão:

```sh
# com o servidor PARADO
cd ~/minecraft
mv world/dimensions/minecraft/plots plots-antigo-$(date +%Y%m%d-%H%M%S)
# apague tambem a entrada do data.yml, senao o generate reclama de mundo ja registrado
```

Depois sobe o servidor e `/cp generate plots 324 48 6`.

## A borda não acompanha a grade

O `/cp generate` põe a borda em **centro (0,0)** com o tamanho que você passou.
A grade de plots, porém, nasce deslocada pelo `NW-Offset` do `config.yml`.

Com `324 48 6` e `NW-Offset: 41`, medido no `world_border.dat` e no `data.yml`:

| | de | até | centro |
|---|---:|---:|---:|
| borda | -162 | 162 | 0 |
| grade de plots | -142 | 174 | **16** |

Os plots passam 12 blocos da borda nos lados +X e +Z, e sobram 20 blocos de nada
nos lados negativos. É a "barreira comendo plot".

Conserto, dentro do mundo `plots`, sem regerar:

```
/worldborder center 16 16
```

A borda passa a ir de -146 a 178 e cobre a grade inteira com 4 blocos de folga.
Fica gravado em `world/dimensions/minecraft/plots/data/minecraft/world_border.dat`.

## O mundo de plots virou bairro de sobrevivência (11/11/2026)

De manhã ele foi configurado como mundo criativo isolado. À tarde a decisão
mudou: **o `plots` é um bairro em sobrevivência**, e é onde a Biga Market vai
morar. Criativo fica para um segundo mundo de plots, se um dia existir.

Isso inverte as duas chaves da manhã, e a inversão não é simétrica — uma
depende da outra.

### 1. `Force-Gamemode: false`

Essa chave do CreativePlots só sabe forçar **criativo**; não existe "forçar
sobrevivência". Com ela em `false` ninguém troca de modo ao atravessar, que é
exatamente o que se quer: o jogador chega no bairro com o modo que já tinha.

### 2. O `plots` entrou no grupo `default` do Multiverse-Inventories

```yaml
groups:
  default:
    worlds:
    - world
    - world_nether
    - world_the_end
    - plots          # <- 11/11/2026
    shares:
    - all
```

**Isto é obrigatório para a loja funcionar.** Com inventário separado, o
jogador chegaria no mercado de mãos vazias: não teria o que vender ao baú nem
onde guardar o que comprasse, e a compra ficaria presa naquele mundo. Loja com
inventário isolado não é loja.

Os arquivos `worlds/plots/*.json` que sobraram da manhã ficam inertes — o
Multiverse-Inventories só os lê enquanto o mundo está fora de grupo. Se um dia
o `plots` sair do grupo de novo, eles voltam sozinhos.

### A ordem para um mundo criativo futuro

1. tirar aquele mundo do grupo `default` (ou simplesmente nunca colocar)
2. **só então** ligar o `Force-Gamemode`

Nessa ordem, sempre. Criativo com inventário compartilhado é diamante de graça
entrando no survival, e a economia inteira depende disso não acontecer.

### Onde a Biga Market vai ficar

O `config.yml` do BigaCore já apontava para o terreno #1 do `plots`, com offset
21/21. **O offset estava velho**: ele vinha de quando o #1 ia de 6 a 37. Depois
das regerações o #1 passou a ir de **-34 a 12** nos dois eixos, e 21/21 caía no
terreno #3.

Recalculado para **-11/-11**, que é o centro do #1: a footprint 29x30 ocupa
X -25..3 e Z -25..4, com folga larga até a borda de polished_blackstone.

O offset é somado ao spawn **real** do mundo, lido em runtime. Por isso o
caminho continua sendo o que o próprio comentário do arquivo manda:

```
/biga loja local      # mostra onde cairia
/biga loja validar    # reprova se houver relevo, água ou construção
/biga loja criar CONFIRMAR
```

E antes disso, **reivindique o terreno #1**. Ele voltou como `available` depois
da regeração: um `/plot auto` de qualquer jogador pode pegar justamente o
terreno da loja. A trava `protegidos: - plots:1` no `plots.yml` do BigaCore já
impede o `/plot abandon`, mas não impede alguém de reivindicar um terreno livre.

### Armadilha: o CreativePlots reescreve o próprio config no boot

Comentários adicionados à mão em `plugins/CreativePlots/config.yml` **não
sobrevivem a um restart**. O `ConfigUpdater` dele regrava o arquivo, apaga o que
não é chave conhecida e ainda converte as quebras de linha para CRLF.

Descoberto na prática: o bloco de comentário escrito às 11:01 explicando o
`Force-Gamemode` tinha sumido às 14:00, depois de um boot. Por isso o raciocínio
daquela chave mora aqui, neste documento, e não lá.

