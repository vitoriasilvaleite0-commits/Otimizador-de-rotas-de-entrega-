# Modelo matemático — versão com grafo fixo

## Grafo dirigido e ponderado

O projeto representa a rede acadêmica por `G = (V, E)`. O conjunto `V` contém a loja, os pontos intermediários e o cliente. O conjunto `E` contém as ligações permitidas entre eles.

Cada ligação dirigida `(i, j)` possui somente um peso: a distância rodoviária em quilômetros. A duração retornada pelo OSRM não participa da otimização.

## Matriz de adjacência

Para uma ordenação de `n` vértices, a matriz é `D = [dij]`, de tamanho `n × n`:

- `dii = 0`;
- `dij` recebe a distância da ligação direta de `i` para `j`;
- `dij = +∞` quando a ligação não existe;
- a matriz pode ser assimétrica, pois `dij` e `dji` podem ser diferentes.

## Dijkstra

O algoritmo implementado em Java mantém três estruturas principais:

- `distance[v]`: menor distância conhecida desde a origem;
- `predecessor[v]`: vértice anterior no melhor caminho conhecido;
- `visited[v]`: informa se a menor distância daquele vértice já foi fixada.

Em cada rodada, o algoritmo escolhe o vértice não visitado com a menor distância conhecida. Para cada vizinho `v` do vértice atual `u`, calcula:

`candidato = distance[u] + d(u,v)`

Se o candidato for menor que `distance[v]`, a distância e o predecessor são atualizados. Ao atingir o destino, os predecessores são percorridos ao contrário para reconstruir o caminho.

## Relação com o OSRM

O OSRM fornece distâncias rodoviárias e geometrias. A distância é convertida de metros para quilômetros e colocada na matriz. A geometria é usada somente para desenhar no Leaflet. A escolha global entre os vértices continua sendo realizada pelo Dijkstra do projeto.
