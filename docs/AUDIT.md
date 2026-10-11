# Auditoria final de algoritmos, pesos e geodados

Auditoria realizada em **29/09/2026**, inicialmente sem alterações nos arquivos. A correção só começou depois da inspeção do fluxo, da execução das consultas geográficas e do registro das divergências abaixo.

## Conclusões do fluxo matemático

### Quem escolhe o caminho

`RouteOptimizationService` monta `AdjacencyMatrix` e chama diretamente `DijkstraService.solve(matrix, origin, customer.id())`. `DijkstraService` implementa manualmente seleção do menor vértice não visitado, relaxamento, predecessores, parada e reconstrução. Não há biblioteca de shortest path nem chamada OSRM depois da matriz para decidir a sequência.

O teste `RouteOptimizationServiceTest.dijkstraChoosesPathAndSegmentsFollowItExactly` usa um Dijkstra instrumentado e comprova que ele foi chamado pelo otimizador.

### Unidade e variável da matriz

`AdjacencyMatrix.from` copia exclusivamente `Edge.distanceKm`. Para respostas OSRM, `RouteData.metersToKilometers` divide metros por 1.000 antes da criação da aresta. `durationSeconds` permanece apenas como metadado em `RouteData`; não participa da matriz, relaxamento, comparação ou soma.

O teste `matrixContainsOnlyEdgeDistancesInKilometers` compara toda aresta à célula dirigida correspondente.

### Limite do papel do OSRM

`RoutingService` chama `/route/v1/driving` para medir ligações dirigidas e lê distância, duração e geometria. A distância em km torna-se o peso; a duração não é otimizada. Depois de o Dijkstra decidir, uma chamada separada recebe a sequência completa apenas para produzir uma geometria contínua; a distância dessa resposta é descartada. O serviço não recebe nem escolhe o caminho global do grafo.

No modo demonstrativo, ligações do cliente usam os pesos locais. As arestas fixas do grafo também usam os `fallbackKm` versionados; elas não são atualizadas automaticamente pelo OSRM a cada inicialização, evitando dezenas de consultas ao servidor público. Portanto, devem ser apresentadas como pesos acadêmicos de fallback, não como medição viária em tempo real.

### Correspondência entre caminho e desenho

O backend percorre pares consecutivos de `DijkstraResult.path` e procura uma aresta com mesma origem, destino e peso usado pela matriz. Antes da auditoria, uma aresta ausente era simplesmente ignorada por `ifPresent`, o que poderia produzir uma linha incompleta sem erro. Isso foi corrigido: a ausência agora interrompe a operação com erro interno, e os segmentos retornados são imutáveis.

O frontend recebe `data.segments` como prova dos pares escolhidos e `data.routeGeometry` para o desenho. Depois que o Dijkstra define a sequência, o backend envia todos os vértices, na mesma ordem, em uma única consulta geométrica ao OSRM, sem trocar o peso já utilizado pela matriz. Quando há geometria, uma linha sólida contínua segue as vias; quando ela está indisponível, as ligações entre vértices são tracejadas, recebem tooltip de trecho esquemático e não são apresentadas como rota rodoviária. O teste `roadGeometryDoesNotChangeWeightsOrPathChosenByDijkstra` injeta uma distância OSRM fictícia de 999 km e comprova que ela não altera matriz, segmentos nem caminho.

## Verificação das coordenadas

Foram feitas consultas ao endpoint público oficial do [Nominatim](https://nominatim.openstreetmap.org/) com User-Agent identificável e intervalo superior a um segundo. Usaram-se reverse geocoding no `zoom=18` e busca nominal limitada à região. Distâncias abaixo são aproximações Haversine entre o valor anterior e o objeto nominal retornado.

| ID | Resultado da auditoria | Decisão |
|---|---|---|
| SAUDE | Estação Saúde-Ultrafarma a ~14 m | Mantido |
| PRACA_ARVORE | estação Praça da Árvore a ~8 m | Mantido |
| SANTA_CRUZ | coordenada anterior estava ~81 m do nó ferroviário Santa Cruz | Corrigido para `-23.59853, -46.63723` |
| VILA_MARIANA | estação Vila Mariana a ~13 m | Mantido |
| IBIRAPUERA | reverse identifica via interna próxima à Rua Antônio de Queiroga, não “Portão 10” | Coordenada mantida; nome/endereço corrigidos para não alegar um portão não confirmado |
| MOEMA | estação Moema a ~16 m | Mantido |
| SHOPPING | entradas/centro do Plaza Sul forçavam desvios laterais | Âncora movida para a Av. Prof. Abraão de Morais, `-23.6205364, -46.6280997`, via OSM 920583055 |
| JABAQUARA | terminal/ruas laterais produziam uma volta apenas para visitar o marco | Âncora movida para a Av. Eng. Armando de Arruda Pereira, `-23.6459641, -46.6407524`, via OSM 942380240 |
| BROOKLIN | estação Brooklin a ~22 m | Mantido |
| DEMO_CLIENT | Autódromo José Carlos Pace, em Interlagos | Mantido; conectado a três vértices locais |

Consultas reproduzíveis:

- Reverse: `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1&zoom=18&lat={LAT}&lon={LON}`
- Busca: `https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=3&countrycodes=br&q={NOME}`

Os resultados do Nominatim representam o estado do OpenStreetMap na data da auditoria. Centroides de edifícios, plataformas, paradas e acessos podem diferir por dezenas de metros sem que o local esteja incorreto.

## Problemas confirmados e correções

1. Três coordenadas nominais divergentes: corrigidas.
2. Descrição não confirmada “Portão 10”: substituída por descrição compatível com reverse geocoding.
3. Omissão silenciosa de segmento: substituída por validação estrita de par dirigido e peso.
4. Falta de teste ponta a ponta entre Dijkstra, matriz e segmentos: adicionados dois testes.
5. Segmentos fixos eram desenhados como retas sólidas entre coordenadas de marcos geográficos, fazendo a linha atravessar visualmente a Estação Autódromo. Agora a geometria viária é obtida após o Dijkstra, e o fallback sem geometria é explicitamente esquemático e tracejado.
6. O vértice `CAMPO_GRANDE` estava no centro geométrico do Shopping Interlagos; o OSRM entrava no estacionamento para visitar esse ponto e depois retornava à via. Ele foi movido para um acesso verificado da Rua do Shopping Interlagos (`highway/tertiary`, via OSM 83838308), e todos os oito pesos dirigidos incidentes foram recalculados.
7. Mesmo com geometrias rodoviárias válidas, calcular cada par separadamente permitia encaixes incompatíveis e pequenas voltas entre chegada e partida. O desenho agora vem de uma única consulta OSRM com a sequência completa do Dijkstra.
8. `SHOPPING` e `JABAQUARA` ainda ficavam em acessos laterais que obrigavam desvios. Foram convertidos em âncoras sobre corredores `highway/primary`; os pesos dirigidos incidentes foram recalculados e os testes passaram a fixar essas coordenadas.

Não foram encontradas misturas de duração/custo na matriz, uso do OSRM como shortest path global ou desenho de uma sequência diferente daquela retornada pelo Dijkstra.
