# Modelo matemático

## Grafo e matriz

Seja `G = (V, E)`, onde `V` contém loja, pontos intermediários e cliente, e `E` contém ligações permitidas. Para `n = |V|`, a matriz ponderada é `D = [dij]`, `n × n`:

- `dij ≥ 0` é a distância rodoviária em quilômetros de `i` até `j`;
- `dii = 0`;
- `dij = ∞` quando `(i,j) ∉ E`;
- não se exige `dij = dji`, portanto o grafo pode ser dirigido.

Pesos negativos invalidariam a propriedade gulosa do Dijkstra e são rejeitados. Distância é a única variável otimizada; duração é apenas metadado.

## Dijkstra

Mantemos `dist[v]`, `prev[v]` e o conjunto visitado `S`. Inicialmente, `dist[origem]=0`, todas as outras distâncias são infinitas e predecessores são vazios. Escolhemos `u ∉ S` com menor `dist[u]`. Para cada aresta `(u,v)`, calculamos o candidato:

`alternativa = dist[u] + duv`

Se `alternativa < dist[v]`, substituímos `dist[v]` e fazemos `prev[v]=u`. O processo para ao visitar o destino ou quando não resta vértice alcançável. Seguir `prev` do destino até a origem reconstrói o caminho.

## Exemplo completo

Considere `V = {A,B,C,D}` e a matriz em km:

| de/para | A | B | C | D |
|---|---:|---:|---:|---:|
| A | 0 | 4,0 | 10,0 | ∞ |
| B | ∞ | 0 | 3,0 | 8,0 |
| C | ∞ | ∞ | 0 | 2,0 |
| D | ∞ | ∞ | ∞ | 0 |

Origem `A`, destino `D`:

1. `dist=(0,∞,∞,∞)`. Visita A: B vira `0+4=4`; C vira `0+10=10`.
2. Visita B (4): C melhora para `4+3=7`; D vira `4+8=12`.
3. Visita C (7): D melhora para `7+2=9`.
4. Visita D (9) e termina.

Predecessores: `B←A`, `C←B`, `D←C`. Caminho: `A → B → C → D`; distância total: `9 km`. Este exemplo também demonstra por que uma distância conhecida pode ser atualizada.

## Grafo dinâmico e topologia recorrente

No modo acadêmico padrão, a origem `o` e o raio `R` definem dois anéis de alvos geométricos. O número de setores é adaptativo:

`m = min(16, max(8, ceil(2π × 0,70R / 5,5)))`

Para o setor `s ∈ {0,...,m-1}`, usa-se o ângulo central:

`θs = (s + 0,5) × 360°/m`

Há um alvo interno a `0,35R` e um externo a `0,70R` em cada setor. Assim, projetam-se de 16 a 32 candidatos; para `R=20 km`, `m=16` e são 32. Esses alvos não são vértices publicados: são apenas coordenadas de consulta. O OSRM Table ajusta cada alvo a uma via dirigível e devolve um ponto `p'`, seu nome e a matriz de distâncias rodoviárias dirigidas `r(i,j)`. Somente `p'` pode virar um vértice. Duplicatas, ajustes ruins e, quando restam pelo menos oito candidatos nomeados, acessos sem nome são removidos antes da publicação; portanto o número final pode ser menor.

A aresta `i → j` recebe diretamente:

`d(i,j) = r(i,j) / 1000`

pois o OSRM responde em metros e a matriz trabalha exclusivamente em quilômetros. A origem liga-se ao anel interno; cada anel liga vizinhos; pares radiais e vizinhos mais próximos fornecem alternativas. Cada sentido é validado separadamente e o Dijkstra continua sendo o responsável pela escolha global.

Se o OSRM falhar, se algum ajuste ficar a mais de 500 m do alvo, se restarem poucos pontos distintos ou se o grafo ficar desconectado, o snapshot não é parcialmente publicado. O fallback passa a usar os oito pontos versionados mais próximos da origem. A proximidade Haversine serve somente para selecionar e organizar esses candidatos. Para uma ligação complementar aplica-se a aproximação:

`d(i,j) = 1,28 × h(i,j)`

com mínimo de `0,05 km`. Esse valor é um fallback acadêmico e não uma distância rodoviária oficial. A origem recebe ligações nos dois sentidos com três candidatos, e cada intermediário é conectado nos dois sentidos a vizinhos próximos. A matriz continua dirigida e contém exclusivamente quilômetros.

Na inclusão do cliente, a distância Haversine pré-seleciona oito candidatos. Para cada candidato `c`, a OSRM Table fornece o escore rodoviário dirigido:

`score(c) = r(origem,c) + r(c,cliente)`

Se `r(origem,cliente)` existir, candidatos com `score(c) / r(origem,cliente) > 1,35` são descartados quando ainda restarem pelo menos três opções. Escolhem-se três conexões de menor escore, preferindo rumos separados por pelo menos 25 graus. Esses valores entram na matriz como arestas; o Haversine não entra no peso final.

Define-se uma alternativa intermediária eficiente por:

`score(c) / r(origem,cliente) ≤ 1,15`

Se houver menos de duas alternativas eficientes, acrescenta-se à matriz a aresta dirigida direta `origem → cliente`, com peso `r(origem,cliente)`. Isso não copia a resposta final do OSRM: a ligação direta passa a ser apenas mais uma aresta, e o Dijkstra compara seu peso com todos os demais caminhos.

Depois do Dijkstra, a geometria contínua na sequência escolhida tem comprimento `Lgrafo`, e uma consulta direta fornece `Ldireto`. A condição de sanidade é:

`Lgrafo / Ldireto ≤ 1,35`

Antes dessa comparação global, duas geometrias consecutivas são comparadas em sentidos opostos. Se o sufixo do trecho `a → c` coincidir com o prefixo invertido de `c → b` por mais de `0,12 km`, o vértice `c` representa um retorno físico relevante. O sistema remove `c` e suas arestas do snapshot da resposta, reconstrói `D` e executa o Dijkstra novamente, limitando a três correções por solicitação.

Essas comparações não otimizam nem substituem o Dijkstra. Elas validam ou corrigem o conjunto de vértices e arestas sobre o qual o algoritmo manual é novamente executado; a sequência nunca é trocada silenciosamente por uma rota pronta do OSRM.

Quando `app.demo-mode` está desativado, também fica disponível a seleção ao vivo por Overpass descrita a seguir.

Para uma origem `o` e raio de atendimento `R`, os candidatos intermediários são cruzamentos reais devolvidos pelo OpenStreetMap. Seja `h(o,p)` a distância Haversine entre a origem e um candidato `p`.

Os dois anéis são:

- interno: `0,20R ≤ h(o,p) < 0,50R`;
- externo: `0,50R ≤ h(o,p) ≤ 0,90R`.

O ângulo de rumo é dividido em oito setores de 45 graus. Assim, cada candidato pertence a no máximo uma das dezesseis células `(anel, setor)`. Pontos fora das faixas não são escolhidos.

Dentro de cada célula, a pontuação normalizada é:

`score(p) = 0,40C(p) + 0,25P(p) + 0,20V(p) + 0,15N(p)`

onde:

- `C(p)` mede quantas vias dirigíveis compartilham o nó;
- `P(p)` mede a proximidade do centro radial/angular da célula;
- `V(p)` representa a classe viária;
- `N(p)` vale um quando há nome de via e zero caso contrário.

Escolhe-se o candidato de maior pontuação que respeite o espaçamento mínimo. Empates são resolvidos pelo menor identificador OSM. Portanto, a seleção não utiliza aleatoriedade.

## Validação de uma aresta dinâmica

Para uma ligação candidata `i → j`, sejam:

- `r(i,j)` a distância rodoviária devolvida pelo OSRM, em quilômetros;
- `h(i,j)` a distância Haversine, em quilômetros;
- `q` a razão máxima de desvio configurada.

A aresta somente entra em `E` quando existe rota, o peso é positivo, está dentro do limite relativo ao raio e:

`r(i,j) / h(i,j) ≤ q`

A razão não é aplicada a pares extremamente próximos, nos quais pequenas restrições de conversão podem distorcer o quociente. Cada sentido é avaliado separadamente, preservando a assimetria.

Após a montagem, verifica-se alcançabilidade a partir da origem. Se houver vértices não alcançados, acrescenta-se a menor ponte dirigida válida. Se não existir ponte, a versão é rejeitada e a matriz anterior permanece ativa.

## Atualização da matriz

Cada snapshot produz uma nova ordenação de vértices e uma nova matriz `D(t)`. A atualização recorrente pode ser representada por:

`D(t anterior) → gerar e validar D(t novo) → troca atômica`

O Dijkstra sempre recebe uma matriz completa e imutável. Ele nunca observa metade da matriz antiga e metade da nova. Quando o cliente é confirmado, acrescenta-se mais um vértice e até seis arestas dirigidas antes da execução do algoritmo.
