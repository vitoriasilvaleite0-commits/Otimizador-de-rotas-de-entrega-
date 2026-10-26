# Arquitetura

## Visão geral

O projeto possui dois modos compatíveis:

1. **fixo:** carrega `graph.json`, a loja e o polígono da Zona Sul;
2. **dinâmico radial:** recebe uma origem dentro da região configurada, gera de 16 a 32 alvos determinísticos conforme o raio e usa o OSRM para ajustá-los a vias reais;
3. **dinâmico ao vivo:** consulta a malha do OpenStreetMap e cria um grafo acadêmico reduzido, com fallback para o modo local.

Em ambos, `AdjacencyMatrix` materializa exclusivamente distâncias em quilômetros e `DijkstraService` escolhe o menor caminho. Serviços externos nunca substituem essa decisão.

## Componentes

### Controladores

- `GeocodingController`: busca regional ou ampla no Brasil.
- `GraphController`: publica o grafo fixo, gera grafos dinâmicos e informa o estado.
- `RouteController`: recebe o cliente e o identificador opcional do grafo.
- `GlobalExceptionHandler`: padroniza erros sem expor detalhes internos.

### Domínio

- `Point`, `Edge` e `Graph`: modelo imutável do grafo dirigido.
- `AdjacencyMatrix`: zero na diagonal, infinito na ausência de ligação e distância nas arestas.
- `GeneratedGraphSnapshot`: versão completa e imutável de um grafo dinâmico.
- `DijkstraResult`, `DijkstraStep` e `Relaxation`: resultado e evolução do algoritmo.

### Integrações

- `GeocodingService`: Nominatim com busca explícita, cache, User-Agent e limitação de frequência.
- `OverpassService`: obtém vias/nós, filtra vias dirigíveis e deriva cruzamentos reais.
- `RoutingService`: OSRM Route, Table e Nearest com cache, timeout e fallback.

### Serviços

- `GraphService`: carrega o grafo fixo, valida o cliente, avalia oito candidatos e conecta os três com melhor custo rodoviário completo e diversidade direcional.
- `LocalTopologyGraphGenerator`: gera dois anéis de pontos viários novos com OSRM Table e, se a consulta ou a validação falhar, seleciona oito intermediários verificados do `graph.json`.
- `HybridGraphGenerator`: aplica setores/anéis, seleciona cruzamentos, cria arestas e valida conectividade.
- `DynamicGraphService`: identifica, armazena, limita e renova snapshots.
- `DijkstraService`: implementação manual do menor caminho.
- `RouteOptimizationService`: seleciona o snapshot, atualiza a matriz com o cliente, chama o Dijkstra e monta a rota.

## Fluxo dinâmico padrão (radial com fallback confiável)

1. O frontend envia origem e raio ao backend.
2. `DynamicGraphService` reconhece o modo acadêmico e valida a cobertura poligonal.
3. `LocalTopologyGraphGenerator` projeta dois anéis a `0,35R` e `0,70R`. A resolução varia de 8 a 16 setores para que o arco externo não fique excessivamente largo; com raio de 20 km são 32 alvos.
4. Uma única consulta OSRM Table devolve tanto a matriz dirigida de distâncias quanto `sources`, com coordenadas ajustadas e nomes das vias.
5. Somente ajustes de até 500 m entram no grafo; duplicatas a menos de 120 m são removidas. Havendo ao menos oito opções com nome de via, acessos anônimos são descartados para reduzir paradas em ramais, estacionamentos ou ruas sem saída.
6. A origem, os anéis, os pares radiais, vizinhos alternativos e atalhos rodoviários formam uma topologia esparsa; cada intermediário precisa de pelo menos duas entradas e duas saídas válidas.
7. Ligações com distância inválida, desvio excessivo ou comprimento incompatível com o raio são descartadas.
8. A matriz é publicada com `graphSource=OSRM_RADIAL_DYNAMIC` somente se todos os vértices forem alcançáveis.
9. Em qualquer falha, oito pontos verificados do `graph.json` formam o snapshot `LOCAL_VERIFIED_TOPOLOGY_FALLBACK`.

Os alvos projetados são apenas entradas da consulta. Os pontos `P01...Pnn` exibidos e armazenados usam as coordenadas devolvidas pelo OSRM; são projetados de 16 a 32 alvos, mas o total publicado pode ser menor após deduplicação e filtragem de acessos anônimos. O fallback não depende de rede; nele, ligações complementares usam `Haversine × 1,28` e a ausência de geometria viária é indicada como traçado esquemático.

## Fluxo dinâmico ao vivo

1. O frontend pesquisa a origem pelo Nominatim.
2. O usuário confirma coordenadas e raio.
3. `GraphController` delega a `DynamicGraphService`.
4. OSRM Nearest ajusta a origem à rede dirigível.
5. Overpass retorna vias e nós de 16 janelas amostrais centradas nas células dos dois anéis; assim a consulta não precisa transferir toda a malha da caixa do raio.
6. `OverpassService` rejeita vias não dirigíveis, acessos privados e estacionamento.
7. `HybridGraphGenerator` divide o disco em oito setores e dois anéis.
8. Um cruzamento real é escolhido por célula com regra determinística.
9. Ligações esparsas são propostas entre vizinhos e anéis.
10. OSRM Table devolve a distância dirigida de cada par selecionado.
11. Ligações inalcançáveis ou com desvio excessivo são descartadas.
12. O gerador garante graus alternativos e alcance desde a origem.
13. A matriz é publicada com um `graphId`.
14. O cliente pré-seleciona oito intermediários por Haversine; uma OSRM Table ordena o custo dirigido completo desde a origem e o serviço conecta três opções boas e direcionalmente diversas.
15. Se menos de duas alternativas intermediárias custarem até 115% da ligação direta, a matriz recebe também a ligação direta OSRM como aresta de segurança.
16. O Dijkstra escolhe a sequência final entre todas as arestas presentes na matriz.
17. OSRM Route gera a geometria contínua nessa mesma sequência. Se dois trechos adjacentes se sobrepuserem em sentidos opostos por mais de 120 m, o vértice de trânsito é removido, a matriz é reconstruída e o Dijkstra é repetido, até três vezes.
18. Uma referência direta de sanidade rejeita a resposta se a sequência final ainda exceder a rota direta em mais de 35%.

## Seleção híbrida

O disco utiliza duas faixas normalizadas:

- interna: `0,20R ≤ distância < 0,50R`;
- externa: `0,50R ≤ distância ≤ 0,90R`.

Cada faixa possui oito setores de 45 graus. Candidatos são nós compartilhados por vias distintas. A pontuação usa:

- 40% conectividade;
- 25% proximidade do centro da célula;
- 20% classe viária;
- 15% presença de nomes de vias.

Há espaçamento mínimo entre escolhidos. Empates usam o menor ID OSM, garantindo reprodutibilidade para a mesma versão dos dados.

## Topologia e arestas

O grafo é propositalmente esparso. Cada intermediário tenta manter:

- vizinho anterior e posterior no mesmo anel;
- ligação radial com o outro anel;
- pelo menos duas entradas e duas saídas válidas.

A origem tenta conectar-se a quatro pontos internos. Se o padrão inicial não alcançar todos os vértices, o gerador adiciona a menor ponte válida entre a parte alcançável e a não alcançável. Se isso não for possível, a geração é rejeitada.

Arestas são validadas independentemente em cada sentido. Uma distância OSRM nula vira ausência de aresta. Também se rejeita uma ligação cujo comprimento exceda o limite relativo ao raio ou cuja razão entre distância rodoviária e Haversine seja excessiva.

## Cache e renovação

O identificador deriva de latitude, longitude, raio e versão do algoritmo. O cache usa `ConcurrentHashMap` e possui limite configurável.

`DynamicGraphService.refreshActiveGraphs()` é executado pelo agendador Spring no intervalo configurado. Cada versão é construída fora do mapa ativo e substitui a anterior em uma única operação. Falhas mantêm o snapshot anterior. Não há atualização parcial visível para requisições concorrentes.

O cache é apenas em memória. Após reinício, grafos dinâmicos são gerados novamente.

## OSRM versus Dijkstra

O OSRM responde perguntas locais:

- qual trecho dirigível fica mais próximo da origem;
- qual é o comprimento da rota considerada mais rápida entre dois pontos;
- qual geometria segue as ruas para a sequência informada.

O Dijkstra responde à pergunta global do projeto:

> Qual sequência de arestas do grafo acadêmico minimiza a soma das distâncias?

Depois do Dijkstra, a distância devolvida pela consulta visual não altera pesos, matriz ou caminho. Ela é comparada a uma referência direta apenas como validação: um desvio superior a 35% impede que uma rota claramente ruim seja desenhada, mas jamais troca a sequência escolhida pelo algoritmo.

## Compatibilidade e fallback

- `GET /api/graph` conserva o contrato do grafo fixo e adiciona metadados.
- `POST /api/routes/optimize` continua aceitando pedidos sem `graphId`.
- o cliente demonstrativo continua ligado ao `graph.json`.
- no modo acadêmico, a geração radial OSRM é a primeira escolha e possui apenas uma chamada de tabela; o fallback local assume em timeout ou resposta inválida;
- falha OSRM Table utiliza Haversine multiplicada por 1,28 e marca todas as arestas como fallback;
- falha Overpass mantém o snapshot anterior ou ativa a topologia local verificada dentro da região configurada.

## Segurança operacional

- URLs externas vêm somente de `application.yml`;
- entradas possuem limites e validação de coordenadas/raio;
- conteúdo externo é inserido no DOM com `textContent` ou escaping;
- Nominatim não é chamado pelo agendador;
- não há chaves, banco, login ou logs de endereço completo;
- testes externos usam clientes HTTP simulados.
