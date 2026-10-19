# Status do projeto

**Etapa atual:** correção de retornos físicos e alternativas ineficientes validada em 19/10/2026.

## Concluído

- Inspeção da pasta: repositório vazio, sem arquivos do usuário a preservar.
- Plano e critérios de validação registrados.
- Estrutura Maven/Spring Boot iniciada para Java 21.
- Domínio, matriz dirigida, Dijkstra manual e snapshots completos.
- Grafo com loja, nove pontos intermediários reais e fallback versionado.
- Clientes Nominatim/OSRM com timeout, cache, limitação e respostas simuladas nos testes.
- Quatro endpoints, validação e erros padronizados.
- Interface responsiva com Leaflet local, busca/ajuste, matriz, rota e animação.
- Documentação técnica e operacional.
- Fluxo offline validado no navegador: seleção, rota, matriz e primeiro passo da animação.
- Auditoria independente do fluxo Dijkstra → segmentos → Leaflet registrada em `docs/AUDIT.md`.
- Coordenadas confrontadas com Nominatim/OSM; Santa Cruz corrigida e os pontos Plaza Sul/Jabaquara convertidos em âncoras de corredor viário público.
- Descrição do ponto Ibirapuera corrigida após reverse geocoding não confirmar “Portão 10”.
- Montagem de segmentos agora exige par dirigido e peso idênticos aos usados pela matriz.
- Grafo ampliado pelo corredor sul e, posteriormente, complementado com três vértices territoriais distintos das estações correspondentes.
- Região atendida estendida até latitude `-23.78`.
- Cliente offline movido para Interlagos e conectado a três vértices locais em ambos os sentidos.
- Pesos dirigidos OSRM e demonstração de três alternativas registrados em `docs/SOUTHERN_EXPANSION.md`.
- Geometria final consolidada em uma única chamada OSRM, usando exatamente a ordem de vértices devolvida pelo Dijkstra.
- Layout da matriz isolado em um contêiner rolável; a tabela larga não desloca mais a seção 4 da simulação para fora da tela.
- Vértices territoriais `VELEIROS`, `INTERLAGOS` e `CIDADE_DUTRA` adicionados; estações separadas nos IDs `ESTACAO_SOCORRO`, `AUTODROMO` e `PRIMAVERA_INTERLAGOS`.
- Caixa retangular ampla substituída por polígono configurável de sete pontos, com validação ponto-no-polígono no backend e camada translúcida correspondente no Leaflet.
- Origem deixou de ser exclusivamente fixa: o entregador pode pesquisar, ajustar e confirmar um novo ponto de partida.
- Geração determinística com oito setores, dois anéis e até dezesseis cruzamentos reais obtidos pelo Overpass.
- Consulta Overpass limitada às 16 janelas das células-alvo, reduzindo volume e carga sem inventar coordenadas.
- Filtro de vias dirigíveis exclui acessos privados, estacionamento, `parking_aisle`, `driveway` e tipos incompatíveis.
- OSRM Nearest ajusta a origem; OSRM Table produz pesos dirigidos em quilômetros; OSRM Route continua restrito aos segmentos do cliente e à geometria final.
- Ligações dinâmicas são esparsas, rejeitam desvios excessivos, oferecem graus alternativos e passam por verificação de alcançabilidade.
- Cache por origem/raio com identificador determinístico, limite configurável e renovação diária atômica.
- Em falhas periódicas, a última versão válida permanece ativa; o `graph.json` continua como fallback da loja configurada.
- Interface adaptada para origem, raio, geração, metadados da nova matriz e busca de cliente no grafo selecionado.
- Contratos anteriores preservados: grafo fixo e otimização sem `graphId` continuam funcionais.
- Correção intermediária eliminou a dependência do Overpass no modo acadêmico e estabeleceu o fallback com oito pontos reais versionados.
- Origens distintas selecionam vizinhanças distintas; a origem dinâmica recebe três conexões bidirecionais e os intermediários mantêm alternativas.
- Em modo ao vivo, falhas externas dentro do polígono configurado acionam automaticamente a mesma topologia local.
- No modo acadêmico, origens fora da cobertura retornam erro imediato e compreensível, sem iniciar chamadas externas demoradas.
- Conexão do cliente e geometria final em topologia local não fazem novas chamadas externas; o mapa identifica a rota sem geometria como esquemática e tracejada.
- Correção posterior substituiu o reaproveitamento obrigatório desses oito pontos: o modo padrão projeta ao menos 16 alvos em dois anéis e publica o subconjunto válido ajustado a vias pelo OSRM Table.
- Uma única resposta OSRM fornece coordenadas viárias, nomes e pesos dirigidos; o `graph.json` permanece como fallback automático, e não como resultado normal da geração.
- A topologia radial possui ligações de anel, ligações radiais e vizinhos alternativos e só é publicada quando todos os pontos são alcançáveis.
- A resolução radial agora cresce de 8 até 16 setores conforme o raio; com 20 km são avaliados 32 candidatos antes da deduplicação e da filtragem viária, reduzindo quadrantes excessivamente vazios.
- Cada intermediário radial precisa de pelo menos duas entradas e duas saídas rodoviárias, e a geração adiciona atalhos próximos para ampliar as alternativas comparadas pelo Dijkstra.
- O cliente pré-seleciona oito candidatos, usa a OSRM Table para comparar o custo completo desde a origem e conecta três alternativas rodoviárias boas e direcionalmente diversas.
- A geometria final é comparada a uma referência direta; desvio superior a 35% gera erro explícito em vez de desenhar uma volta anormal.
- Pontos radiais sem nome de via são descartados quando há quantidade suficiente de vias nomeadas, evitando paradas em acessos anônimos ou ramais locais.
- A geometria detecta retorno superior a 120 m entre trechos consecutivos; o ponto responsável é removido e o Dijkstra é executado novamente sobre uma matriz reconstruída.
- Quando menos de duas opções intermediárias ficam a até 15% da distância direta, uma aresta direta de segurança entra na matriz para ser comparada pelo próprio Dijkstra.

## Decisões

- Grafo pequeno e direcionado, carregado de JSON; infinito é serializado como `null` na API.
- Serviços públicos são opcionais; o fluxo acadêmico sempre possui fallback local.
- OSRM fornece pesos e geometrias, nunca o menor caminho global.
- Overpass fornece somente candidatos topológicos; a aplicação seleciona os vértices e arestas do grafo reduzido.
- A topologia dinâmica não contém coordenadas inventadas e nunca substitui a versão ativa antes de concluir todas as validações.
- Coordenadas radiais calculadas são apenas alvos da consulta; os vértices exibidos usam exclusivamente os `sources` ajustados pelo OSRM, ou coordenadas previamente verificadas do fallback.

## Testes executados

- Validação inicial, antes da auditoria: 23 testes, 0 falhas, 0 erros, 0 ignorados.
- `./mvnw clean package`: sucesso; JAR executável gerado.
- Suíte atual: 10 testes de Dijkstra, 12 de grafo, 7 de otimização, 12 de integrações externas, 4 de controllers, 8 do gerador radial/local, 3 do serviço dinâmico, 3 do gerador híbrido, 4 da região e 2 de contexto Spring (65 no total).
- `node --check`: JavaScript sem erros de sintaxe.
- Servidor: iniciou na porta 8080 sem erros; página e `/api/status` responderam 200.
- Otimização offline real: `STORE → SAUDE → CLIENT`, 3,65 km, 6 passos.
- Após auditoria: `./mvnw test` executou 25 testes, 0 falhas, 0 erros, 0 ignorados.
- Após expansão sul: 29 testes executados, 29 aprovados, sem falhas ou testes ignorados (`./mvnw clean package`, 29/09/2026).
- API real em execução: cliente do Autódromo conectado a Interlagos, Estação Autódromo e Cidade Dutra; caminho `STORE → SHOPPING → JABAQUARA → CAMPO_GRANDE → INTERLAGOS → CLIENT`, total atualizado de 20,77 km.
- Navegador: 22 vértices fixos, matriz expandida e rota contínua.
- Correção visual da Estação Autódromo: segmentos fixos deixaram de usar retas sólidas; a rota escolhida recebe geometria viária OSRM após o Dijkstra, sem alterar pesos ou caminho. Fallback geométrico agora é vazio e aparece como ligação tracejada/esquemática.
- Após a correção geométrica: 30 testes executados e aprovados; o teste adicional comprova que uma geometria cuja resposta contém outra distância não modifica a matriz nem o resultado do Dijkstra.
- Após corrigir o acesso do Shopping Interlagos: 31 testes executados e aprovados; o novo teste fixa o vértice `CAMPO_GRANDE` no acesso viário verificado.
- Após eliminar voltas em acessos laterais: `./mvnw test` executou 32 testes, todos aprovados; a rota contínua do OSRM mediu 21,425 km, coerente com os 21,43 km somados pela matriz.
- Após a correção responsiva: largura do documento conferida no navegador (`scrollWidth = clientWidth = 842 px`), seção “Simulação do Dijkstra” visível sem rolagem horizontal; 32 testes aprovados novamente.
- Após separar regiões e estações: 32 testes aprovados; o teste de grafo verifica explicitamente a coexistência dos seis IDs territoriais/ferroviários.
- Após implantar a área poligonal: `./mvnw clean package` executou 37 testes, todos aprovados; loja e 22 vértices fixos foram verificados dentro da nova área.
- Validação final da geração dinâmica: `./mvnw test` e `./mvnw clean package` executaram 52 testes, com 0 falhas, 0 erros e 0 ignorados; JAR executável gerado.
- `node --check src/main/resources/static/js/app.js`: JavaScript válido. O projeto não possui ferramenta adicional de lint ou verificação de tipos configurada.
- Na validação intermediária do fallback: `./mvnw clean package` executou 57 testes, todos aprovados; cinco testes cobriam geração local, mudança de vizinhança, cache, rejeição imediata fora da cobertura e otimização sem rede.
- Nessa mesma etapa intermediária, Interlagos respondeu HTTP 200 com 9 vértices e 42 arestas; esse comportamento foi posteriormente substituído pelo gerador radial descrito abaixo.
- `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` e o JAR final: aplicação iniciou sem erros; `/` e `/api/status` responderam HTTP 200, e raio fora da configuração respondeu HTTP 400 com `INVALID_GRAPH_RADIUS`.
- Navegador automatizado: fluxo offline calculou `STORE → SHOPPING → JABAQUARA → CAMPO_GRANDE → INTERLAGOS → CLIENT` (20,77 km), iniciou a simulação, exibiu a matriz e não apresentou erros no console nem rolagem horizontal (`scrollWidth = clientWidth = 842 px`).
- Após gerar pontos realmente novos: `./mvnw test` e `./mvnw clean package` executaram 59 testes, com 0 falhas, 0 erros e 0 ignorados.
- API real na porta 8081: origem `-23.6500,-46.6500`, raio 20 km, gerou `ORIGIN + P01...P16`, 64 arestas, `graphSource=OSRM_RADIAL_DYNAMIC` e `fallbackUsed=false` em 1,136 s.
- Otimização real para `-23.7200,-46.5900`: cliente conectado em ambos os sentidos a `P04`, `P03` e `P12`; Dijkstra escolheu `ORIGIN → P04 → CLIENT`, 14,6298 km, com geometria contínua de 702 pontos e sem fallback.
- Navegador final: 16 marcadores novos com nomes viários, matriz 17 × 17, mensagem explícita de sucesso e console sem erros ou avisos.
- Correção dos desvios: `./mvnw test` e `./mvnw clean package` executaram 62 testes, com 0 falhas, 0 erros e 0 ignorados; `node --check` foi aprovado.
- Cenário real reportado, origem da loja e raio de 20 km: o grafo passou a ter 32 intermediários e 144 arestas. Para a Rua de Conde Linhares, o Dijkstra escolheu `ORIGIN → P10 → CLIENT`, com 14,2438 km na matriz contra 13,9426 km na referência direta, desvio de 2,16%. Antes, a topologia esparsa forçava 17,1406 km, cerca de 23% acima da referência.
- Navegador automatizado confirmou geração, busca do endereço, otimização, matriz e resultado na interface usando dados reais, sem fallback.
- Correção final do cenário Rua Capibaribe → Rua de Conde Linhares: `./mvnw clean package` executou 65 testes, sem falhas, erros ou testes ignorados. A API real gerou 14 intermediários nomeados e 63 arestas; o Dijkstra escolheu `ORIGIN → CLIENT`, 11,2886 km, contra os 11,2886 km da referência direta, sem desvio.
- Navegador interno confirmou mapa com mosaicos Leaflet, resultado de 11,29 km, matriz, primeiro passo da simulação e ausência de rolagem horizontal em 842 px. Um clique tardio no mapa revelou acesso nulo ao marcador; o estado de edição e as guardas dos marcadores foram corrigidos e revalidados.

## Problemas encontrados

- Maven não está instalado globalmente; o projeto inclui wrapper autossuficiente.
- Sandbox bloqueou cache Maven global e bind de portas em testes; wrapper usa cache local e mocks não usam rede.
- A inicialização revelou ambiguidade de construtores; corrigida com injeção explícita e coberta por teste de contexto.
- Mockito/Byte Buddy era incompatível com o mecanismo de attach do JDK 25 neste ambiente; removido, pois os testes usam dublês tipados em memória.
- Auditoria encontrou três coordenadas nominais divergentes e uma descrição geográfica imprecisa; todas corrigidas com base no Nominatim.
- A montagem da rota podia omitir silenciosamente uma aresta ausente; agora falha explicitamente e possui teste de correspondência exata.
- A rota sólida podia atravessar a Estação Autódromo porque arestas fixas sem geometria eram desenhadas como retas entre os vértices; corrigido com geometria viária pós-Dijkstra e sinalização explícita do fallback esquemático.
- O centro do Shopping Interlagos fazia o OSRM entrar no estacionamento e retornar à rota. O vértice foi convertido em acesso viário público e seus oito pesos dirigidos foram recalculados.
- Geometrias obtidas par a par podiam formar emendas e alças; a consulta visual agora é contínua. Plaza Sul e Jabaquara também foram deslocados de acessos laterais para corredores viários públicos verificados.
- Na validação externa final, uma consulta leve ao servidor público `overpass-api.de` expirou após 30 segundos sem resposta. Os testes simulados comprovam o contrato, a consulta de produção foi reduzida a 16 janelas amostrais e o fallback manteve o sistema funcional; disponibilidade do servidor público continua fora do controle da aplicação.
- Testes manuais confirmaram indisponibilidade/timeout dos servidores públicos de Overpass; esse era o motivo de o botão carregar e preservar o mesmo grafo. O caminho local removeu essa dependência no modo acadêmico.
- A primeira correção de confiabilidade apenas remontava o grafo com oito pontos antigos, portanto a matriz mudava mas não surgiam vértices novos. O gerador radial OSRM corrige essa limitação sem perder o fallback offline.
- Em raios grandes, oito setores deixavam até 11 km de arco entre pontos do anel externo. O Dijkstra estava correto, porém só podia escolher as arestas disponíveis e era obrigado a visitar um ponto fora do corredor. Resolução adaptativa, conectores avaliados por custo completo e validação final corrigiram a causa estrutural.
- No cenário reportado, o desvio total antigo era pequeno demais para o limite global de 35%, mas um ponto anônimo estava fisicamente num ramal e a topologia não oferecia alternativa direta competitiva. A filtragem de vias, a detecção local de retorno e a aresta condicional de segurança tratam separadamente essas duas causas.

## Pendências reais

- Nenhuma pendência funcional confirmada. Para uso contínuo, permanecem as evoluções operacionais já documentadas no README: hospedar ou contratar instâncias próprias das APIs, persistir o cache e monitorar disponibilidade.
