# Expansão do grafo para o extremo sul

Expansão verificada em 29/09/2026. O conjunto continua sendo uma modelagem acadêmica reduzida: coordenadas vêm do OpenStreetMap por meio do Nominatim e os pesos foram medidos no OSRM público, mas ficam versionados apenas como **fallback acadêmico**, não como dado oficial ou garantia de trânsito atual.

## Novos vértices

| ID | Referência real verificada | Latitude | Longitude | Objeto Nominatim/OSM |
|---|---|---:|---:|---|
| SANTO_AMARO | Estação Santo Amaro | -23.65613 | -46.71923 | `building/transportation` |
| LARGO_TREZE | Metrô Largo Treze | -23.65446 | -46.71017 | `railway/station` |
| ESTACAO_SOCORRO | Estação Socorro | -23.66282 | -46.71119 | `building/transportation` |
| JURUBATUBA | Estação Jurubatuba | -23.67740 | -46.70231 | `building/transportation` |
| VELEIROS | Veleiros, âncora na Av. Cel. Octaviano de Freitas Costa | -23.6796950 | -46.7090264 | `highway/tertiary`, via OSM 922108053 |
| CAMPO_GRANDE | Acesso viário ao Shopping Interlagos | -23.6780783 | -46.6761105 | `highway/tertiary`, via OSM 83838308 |
| INTERLAGOS | Interlagos, âncora na Avenida Interlagos | -23.7014463 | -46.7014703 | `highway/primary`, via OSM 942619437 |
| AUTODROMO | Estação Autódromo | -23.70561 | -46.68817 | `building/train_station` |
| CIDADE_DUTRA | Cidade Dutra, âncora na Av. Sen. Teotônio Vilela | -23.7166982 | -46.6993112 | `highway/primary`, via OSM 244035464 |
| PRIMAVERA_INTERLAGOS | Estação Primavera-Interlagos | -23.72342 | -46.69174 | `building/train_station` |
| CAPELA_SOCORRO | Prefeitura Regional Capela do Socorro | -23.72052 | -46.70178 | `amenity/townhall` |
| GRAJAU | Estação Grajaú | -23.73638 | -46.69708 | `building/train_station` |

Consultas de busca utilizaram `addressdetails=1`, `countrycodes=br` e recorte da Zona Sul. Durante a expansão, a antiga caixa retangular chegou a usar latitude mínima `-23.78`. Ela foi posteriormente substituída pelo polígono documentado em [`SERVICE_AREA.md`](SERVICE_AREA.md), que acompanha melhor os vértices extremos.

## Ligações dirigidas e pesos OSRM preservados

Cada direção foi consultada separadamente com perfil `driving`; os valores estão em quilômetros, arredondados para duas casas.

| Ligação | km | Ligação inversa | km |
|---|---:|---|---:|
| BROOKLIN → SANTO_AMARO | 8,48 | SANTO_AMARO → BROOKLIN | 8,09 |
| JABAQUARA → CAMPO_GRANDE | 8,39 | CAMPO_GRANDE → JABAQUARA | 8,36 |
| SANTO_AMARO → LARGO_TREZE | 4,58 | LARGO_TREZE → SANTO_AMARO | 6,19 |
| LARGO_TREZE → ESTACAO_SOCORRO | 2,72 | ESTACAO_SOCORRO → LARGO_TREZE | 3,46 |
| SANTO_AMARO → ESTACAO_SOCORRO | 1,12 | ESTACAO_SOCORRO → SANTO_AMARO | 5,99 |
| ESTACAO_SOCORRO → JURUBATUBA | 2,19 | JURUBATUBA → ESTACAO_SOCORRO | 4,33 |
| ESTACAO_SOCORRO → VELEIROS | 3,69 | VELEIROS → ESTACAO_SOCORRO | 4,36 |
| JURUBATUBA → VELEIROS | 4,39 | VELEIROS → JURUBATUBA | 4,34 |
| JURUBATUBA → CAMPO_GRANDE | 3,82 | CAMPO_GRANDE → JURUBATUBA | 3,90 |
| VELEIROS → INTERLAGOS | 3,87 | INTERLAGOS → VELEIROS | 2,92 |
| JURUBATUBA → INTERLAGOS | 4,79 | INTERLAGOS → JURUBATUBA | 3,90 |
| CAMPO_GRANDE → INTERLAGOS | 5,20 | INTERLAGOS → CAMPO_GRANDE | 4,19 |
| CAMPO_GRANDE → AUTODROMO | 5,47 | AUTODROMO → CAMPO_GRANDE | 5,35 |
| INTERLAGOS → AUTODROMO | 2,38 | AUTODROMO → INTERLAGOS | 2,24 |
| INTERLAGOS → CIDADE_DUTRA | 2,06 | CIDADE_DUTRA → INTERLAGOS | 2,01 |
| AUTODROMO → CIDADE_DUTRA | 2,75 | CIDADE_DUTRA → AUTODROMO | 2,73 |
| CIDADE_DUTRA → PRIMAVERA_INTERLAGOS | 1,28 | PRIMAVERA_INTERLAGOS → CIDADE_DUTRA | 1,69 |
| CIDADE_DUTRA → CAPELA_SOCORRO | 0,77 | CAPELA_SOCORRO → CIDADE_DUTRA | 1,25 |
| AUTODROMO → PRIMAVERA_INTERLAGOS | 2,35 | PRIMAVERA_INTERLAGOS → AUTODROMO | 2,38 |
| CAPELA_SOCORRO → GRAJAU | 2,68 | GRAJAU → CAPELA_SOCORRO | 3,46 |
| PRIMAVERA_INTERLAGOS → GRAJAU | 1,67 | GRAJAU → PRIMAVERA_INTERLAGOS | 2,07 |

A assimetria é intencional e reflete o resultado dirigido do roteador, acessos às estações e sentidos viários.

## Cliente demonstrativo e três vizinhos locais

O cliente offline agora é o Autódromo José Carlos Pace (`-23.70182, -46.69692`), verificado como `leisure/sports_centre`. A seleção Haversine encontra:

1. `INTERLAGOS`, o vértice territorial na Avenida Interlagos;
2. `AUTODROMO`, a Estação Autódromo;
3. `CIDADE_DUTRA`, o vértice territorial na Avenida Senador Teotônio Vilela.

Todos ficam a menos de 3 km em linha reta. O cliente recebe seis arestas, três em cada sentido, com pesos OSRM dirigidos versionados. Jabaquara e Saúde não são candidatos diretos.

## Demonstração de alternativas

Para o cliente do Autódromo, o Dijkstra compara, entre outras, estas rotas:

1. **Vencedora — 20,77 km:** `STORE → SHOPPING → JABAQUARA → CAMPO_GRANDE → INTERLAGOS → CLIENT`.
2. **Via Estação Autódromo — 21,43 km:** `STORE → SHOPPING → JABAQUARA → CAMPO_GRANDE → AUTODROMO → CLIENT`.
3. **Via Cidade Dutra — 24,14 km:** `STORE → SHOPPING → JABAQUARA → CAMPO_GRANDE → INTERLAGOS → CIDADE_DUTRA → CLIENT`.
4. **Via Santo Amaro — 30,10 km:** `STORE → SHOPPING → MOEMA → BROOKLIN → SANTO_AMARO → ESTACAO_SOCORRO → JURUBATUBA → INTERLAGOS → CLIENT`.

O Dijkstra escolhe a primeira porque `20,77 < 21,43 < 24,14 < 30,10`. O teste `interlagosClientHasAlternativesAndDijkstraChoosesTheShortest` recalcula essas somas diretamente da matriz e verifica o resultado do algoritmo.

Em 29/09/2026, a coordenada original de `CAMPO_GRANDE` foi substituída pelo acesso viário verificado na Rua do Shopping Interlagos. O centro geométrico do shopping fazia o OSRM entrar no estacionamento para visitar o vértice e retornar à via. Os oito pesos dirigidos incidentes foram recalculados após essa correção. O shopping permanece como referência geográfica, mas o vértice representa explicitamente o acesso pela via pública.

Na mesma revisão, `SHOPPING` e `JABAQUARA` deixaram de representar entradas laterais/centroides e passaram a ser âncoras verificadas nos corredores Avenida Professor Abraão de Morais (`highway/primary`, via OSM 920583055) e Avenida Engenheiro Armando de Arruda Pereira (`highway/primary`, via OSM 942380240). Os pesos incidentes foram novamente medidos no OSRM e versionados como fallback acadêmico.

Em seguida, os nomes territoriais foram separados dos nomes das estações. Os antigos IDs `SOCORRO`, `INTERLAGOS` e `CIDADE_DUTRA`, que na prática apontavam para estações, passaram a `ESTACAO_SOCORRO`, `AUTODROMO` e `PRIMAVERA_INTERLAGOS`. Novos vértices `VELEIROS`, `INTERLAGOS` e `CIDADE_DUTRA` foram adicionados em vias públicas verificadas. Assim, por exemplo, o grafo agora possui simultaneamente o bairro/região `INTERLAGOS` e a `Estação Autódromo`.
