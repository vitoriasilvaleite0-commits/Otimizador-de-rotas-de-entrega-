# Área atendida

A área atendida é um polígono acadêmico configurável que acompanha os vértices extremos do grafo com uma pequena margem. Ela não representa um limite administrativo oficial de bairros ou distritos.

## Fonte única de configuração

Os pontos ficam em `app.region.boundary`, no `application.yml`, na ordem do contorno. O backend aplica o algoritmo ponto-no-polígono para validar resultados do Nominatim e o cliente confirmado. O endpoint `GET /api/graph` publica os mesmos pontos no campo `serviceArea`; o Leaflet os desenha sem manter uma segunda cópia no JavaScript.

| Ordem | Latitude | Longitude | Referência aproximada |
|---:|---:|---:|---|
| 1 | -23.580 | -46.665 | margem norte/oeste de Ibirapuera |
| 2 | -23.582 | -46.625 | margem norte/leste de Vila Mariana |
| 3 | -23.650 | -46.625 | margem leste de Jabaquara |
| 4 | -23.743 | -46.688 | margem sudeste de Grajaú |
| 5 | -23.743 | -46.706 | margem sudoeste de Grajaú |
| 6 | -23.696 | -46.716 | margem oeste de Interlagos/Veleiros |
| 7 | -23.650 | -46.725 | margem oeste de Santo Amaro |

Uma caixa externa, também configurada em `app.region`, continua sendo enviada ao Nominatim com `bounded=1` para reduzir resultados irrelevantes. A aceitação final, porém, depende do polígono.

## Representação visual

O mapa usa preenchimento verde-limão com opacidade de 9%, contorno verde de 2 px e uma camada abaixo das ligações e da rota. A camada não captura cliques, portanto o ajuste do cliente no mapa continua funcionando normalmente.

## Alterando a área

Para alterar a cobertura, edite os pontos do `boundary` e ajuste a caixa externa para englobar todo o novo polígono. A loja e todos os vértices fixos devem permanecer dentro da área. Os testes verificam pontos internos, externos, sobre a borda e a inclusão de todos os vértices configurados.
