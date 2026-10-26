# Histórico de desenvolvimento do Routify Acadêmico

## Identificação

- Período registrado: 29/09/2026 a 19/10/2026
- Integrantes: João Victor, Eduardo Dias e Vitória Silva
- Versão mais recente do período: `v2.0.0-alpha.1`

Este histórico foi organizado a partir do código produzido, das conversas de desenvolvimento, dos testes executados e da documentação técnica. Os commits representam estados funcionais e verificáveis do projeto durante sua evolução.

## Evolução resumida

### 29/09 — estrutura inicial

Foi iniciada a aplicação Spring Boot com Java 21, Maven Wrapper e uma página servida pelo backend.

### 30/09 — modelo matemático

Foram implementados o grafo dirigido, a matriz de adjacência ponderada e o algoritmo de Dijkstra manual. Dez testes cobriram caminhos diretos, intermediários, assimetria, decimais, empates, pesos negativos e destino inalcançável.

### 02/10 e 03/10 — primeira versão funcional

O backend passou a carregar o grafo fixo, pesquisar endereços pelo Nominatim, obter pesos e geometrias pelo OSRM e executar o Dijkstra. O frontend passou a exibir o mapa, a matriz e a evolução passo a passo do algoritmo. Essa etapa recebeu a tag `v1.0.0`.

### 07/10 — expansão da cobertura

O grafo fixo foi ampliado pelo corredor sul de São Paulo, incluindo alternativas para Santo Amaro, Socorro, Campo Grande, Interlagos, Cidade Dutra e Grajaú. Regiões e estações passaram a ser representadas por vértices distintos.

### 11/10 — auditoria e correções de geometria

Foi confirmada a separação de responsabilidades: o OSRM fornece pesos e geometria; o Dijkstra escolhe a sequência global de vértices. A geometria passou a ser validada contra uma referência direta para impedir que um desenho inadequado fosse apresentado como resultado aceitável.

### 13/10 — consolidação da V1

O modelo matemático, a matriz e o percurso do Dijkstra foram documentados. A versão fixa ampliada e auditada recebeu a tag `v1.1.0`.

### 16/10 — início da topologia dinâmica

Foi adicionada a geração de um grafo regional a partir de uma origem escolhida e de um raio. A aplicação passou a montar pontos e ligações dirigidas, atualizar a matriz e manter fallback local quando serviços externos falham.

### 18/10 — experiência dinâmica no frontend

O entregador passou a pesquisar e ajustar a origem, escolher o raio, gerar a topologia e depois pesquisar o destino dentro do grafo selecionado.

### 19/10 — V2 Alpha e confiabilidade

Foram adicionadas verificações contra desvios exagerados, pontos em acessos inadequados e retornos sobre o mesmo trecho. Quando necessário, o ponto responsável é removido e o Dijkstra é executado novamente sobre a matriz reconstruída. A versão recebeu a tag `v2.0.0-alpha.1` porque o frontend e outras configurações ainda terão evoluções.

## Resultado ao final do período

- origem fixa preservada como demonstração e fallback;
- origem dinâmica e raio configurável;
- matriz atualizada para cada topologia;
- Dijkstra manual responsável pela decisão global;
- pesos exclusivamente em quilômetros;
- OSRM restrito a pesos e geometria;
- Nominatim restrito à geocodificação;
- 65 testes automatizados aprovados;
- aplicação empacotada e executável.
