# Routify Acadêmico

Aplicação web acadêmica para estudar grafos dirigidos, matriz de adjacência ponderada e o algoritmo de Dijkstra em rotas de entrega. O entregador pode usar a loja configurada ou pesquisar uma nova origem, escolher o raio de atendimento e gerar um grafo reduzido a partir da malha viária real do OpenStreetMap.

O projeto preserva o grafo versionado da Zona Sul de São Paulo como demonstração e fallback. Os grafos dinâmicos são modelos didáticos reduzidos: não constituem reprodução completa ou oficial da malha urbana.

## Tecnologias e responsabilidades

- **Java 21 e Spring Boot:** domínio, validação, cache, atualização agendada e API interna.
- **Maven Wrapper:** compilação e testes reprodutíveis.
- **Jackson:** leitura do `graph.json`, Overpass, Nominatim e OSRM.
- **Java HttpClient:** integrações externas com timeout e cache.
- **Nominatim:** pesquisa explícita da origem e do cliente; nunca autocomplete.
- **Overpass API:** ruas, nós e cruzamentos dirigíveis usados para gerar a topologia.
- **OSRM Nearest:** ajusta a origem à rede dirigível mais próxima.
- **OSRM Table:** ajusta os alvos radiais a vias dirigíveis e fornece, na mesma resposta, as distâncias dirigidas entre os vértices selecionados.
- **OSRM Route:** fornece a geometria visual final e uma referência direta usada somente para detectar desvios anormais.
- **Dijkstra manual:** escolhe o menor caminho sobre a matriz do projeto.
- **Leaflet e OpenStreetMap:** mapa, marcadores, área atendida, arestas e rota.
- **JUnit e MockMvc:** testes sem dependência dos servidores públicos.

O OSRM não escolhe o caminho acadêmico final. Ele fornece dados locais de cada ligação; o Java monta a matriz e o `DijkstraService` escolhe a sequência de vértices.

## Geração confiável do grafo

O botão **Gerar grafo da região** possui três caminhos explícitos:

- com `app.demo-mode: true` (padrão acadêmico), cria alvos determinísticos em dois anéis. O número de setores cresce de 8 até 16 conforme o raio, evitando vazios muito grandes em áreas amplas. Uma única consulta OSRM Table ajusta os alvos às vias dirigíveis e devolve os pesos dirigidos. As coordenadas e nomes publicados são os pontos viários ajustados pelo OSRM, não os alvos geométricos brutos;
- se o OSRM estiver indisponível, devolver poucos pontos válidos ou produzir uma topologia desconectada, usa imediatamente oito pontos reais já verificados e versionados em `graph.json`;
- com `app.demo-mode: false`, tenta a geração completa por Overpass descrita abaixo e, se uma API falhar dentro da região configurada, recorre ao mesmo gerador radial/fallback verificado.

Origens fora do polígono configurado são recusadas antes de qualquer consulta externa. No fallback versionado, a origem escolhida vira `ORIGIN`, é conectada em ambos os sentidos aos três pontos mais próximos e os intermediários recebem múltiplas ligações. Ligações complementares usam `Haversine × 1,28`; esses pesos são identificados como aproximações acadêmicas, não como dados oficiais.

### Caminho radial padrão

1. cria alvos a `0,35R` e `0,70R`; usa oito setores nos raios usuais e aumenta gradualmente até dezesseis para manter cerca de 5,5 km de arco entre alvos externos;
2. envia origem e os alvos em uma única consulta OSRM Table;
3. lê `sources` para obter o ponto viário ajustado, nome da via e distância do ajuste;
4. descarta ajustes superiores a 500 m e pontos separados por menos de 120 m;
5. quando há pelo menos oito opções, prefere pontos cujo `source` identifica uma via com nome, evitando publicar acessos anônimos, estacionamentos e ramais locais como paradas obrigatórias;
6. monta anéis, ligações radiais e alternativas dirigidas usando somente distâncias OSRM em quilômetros;
7. exige pelo menos duas entradas e duas saídas viárias por ponto, adiciona alternativas próximas e valida desvios e alcançabilidade a partir da origem;
8. publica a matriz somente depois de validar toda a topologia.

As projeções radiais servem apenas como consultas de busca. Elas nunca são publicadas como se fossem coordenadas viárias confirmadas. Quando o ajuste não pode ser comprovado, o sistema usa o fallback versionado.

### Caminho ao vivo

Para uma origem e um raio, o gerador aplica uma regra determinística:

1. ajusta a origem a uma via dirigível com OSRM Nearest;
2. consulta pelo Overpass as vias dirigíveis em 16 janelas amostrais centradas nas células dos anéis, evitando baixar toda a malha da caixa do raio;
3. rejeita acesso privado, estacionamento, `parking_aisle`, `driveway` e vias não automotivas;
4. divide a área em oito setores de 45 graus e dois anéis;
5. considera o anel interno entre 20% e 50% do raio e o externo entre 50% e 90%;
6. escolhe no máximo um cruzamento real por célula, até 16 intermediários;
7. pontua conectividade, posição, classe viária e presença de nomes;
8. exige espaçamento mínimo e desempata pelo identificador OSM;
9. cria ligações com vizinhos do mesmo anel, do outro anel e com a origem;
10. usa OSRM Table para validar alcance e obter pesos em quilômetros;
11. rejeita ligações com desvio excessivo e garante alternativas de entrada e saída;
12. valida que todos os vértices são alcançáveis a partir da origem.

Nenhuma coordenada intermediária é inventada: cada ponto desse caminho corresponde a um nó de cruzamento devolvido pelo Overpass. Se não houver pelo menos oito pontos válidos, o grafo anterior é preservado e, dentro da cobertura local, o gerador tenta o caminho radial OSRM e depois os pontos verificados do `graph.json`.

## Atualização recorrente

Grafos dinâmicos ficam em cache por origem e raio. No modo ao vivo, uma tarefa Spring renovará diariamente todos os grafos ativos:

```text
Overpass → nova topologia → seleção híbrida → OSRM → nova matriz → validação → troca atômica
```

A versão nova somente substitui a anterior depois de ser completamente construída e validada. Em timeout ou indisponibilidade, a última versão válida continua ativa. Dentro do polígono configurado, o `graph.json` fornece uma topologia local confiável mesmo quando ainda não existe versão anterior.

O intervalo padrão é de 24 horas para respeitar o uso leve das APIs públicas. Não há consultas periódicas ao Nominatim.

## Modelo matemático

A matriz `D = [dij]` guarda na linha `i` e coluna `j` a distância em quilômetros da ligação dirigida `i → j`:

- `dii = 0`;
- `dij = Double.POSITIVE_INFINITY` quando a ligação não existe;
- `dij ≥ 0` para toda aresta;
- `dij` pode ser diferente de `dji`;
- duração e geometria não entram no peso.

No JSON da API, infinito é serializado como `null`. O Dijkstra inicializa a origem com zero, escolhe o vértice não visitado de menor distância, relaxa as arestas, registra predecessores e reconstrói o caminho. Veja [docs/MATHEMATICAL_MODEL.md](docs/MATHEMATICAL_MODEL.md).

## Como baixar e executar

### 1. Instale os pré-requisitos

É necessário ter:

- **Git** para baixar o repositório;
- **JDK 21 ou superior** para compilar e executar a aplicação;
- conexão com a internet na primeira execução, pois o Maven Wrapper baixa o Maven 3.9.11 automaticamente.

Confirme as instalações em um terminal:

```text
git --version
java -version
```

O comando `java -version` deve informar a versão 21 ou uma versão posterior. Não é necessário instalar o Maven separadamente.

### 2. Baixe o projeto

No terminal, execute:

```bash
git clone https://github.com/vitoriasilvaleite0-commits/Otimizador-de-rotas-de-entrega-.git
cd Otimizador-de-rotas-de-entrega-
```

Se preferir usar **Code → Download ZIP** no GitHub, extraia o arquivo e abra o terminal dentro da pasta extraída.

Antes de continuar, confirme que a pasta aberta contém `pom.xml`, `mvnw` e `mvnw.cmd`. No Linux, macOS ou Git Bash, use:

```bash
ls pom.xml mvnw mvnw.cmd
```

No Windows PowerShell, use:

```powershell
Get-ChildItem pom.xml,mvnw,mvnw.cmd
```

No Prompt de Comando do Windows, use:

```bat
dir pom.xml mvnw mvnw.cmd
```

Se o terminal informar que `./mvnw` ou `mvnw.cmd` não existe, entre na pasta extraída ou clonada com `cd` antes de tentar novamente.

### 3. Teste, compile e inicie

#### Linux, macOS ou Git Bash

```bash
./mvnw test
./mvnw clean package
./mvnw spring-boot:run
```

Se o sistema negar permissão para executar o arquivo, rode uma vez `chmod +x mvnw` e repita os comandos.

#### Windows PowerShell

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

#### Prompt de Comando do Windows (CMD)

```bat
mvnw.cmd test
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

Os comandos devem ser executados na ordem apresentada. Quando `spring-boot:run` indicar que a aplicação foi iniciada, abra `http://localhost:8080` no navegador. Mantenha esse terminal aberto enquanto estiver usando o sistema e pressione `Ctrl + C` para encerrar o servidor.

## Fluxo principal

1. Pesquise ou selecione a origem padrão.
2. Ajuste o marcador, se necessário.
3. Escolha um raio entre 3 e 20 km.
4. Clique em **Gerar grafo da região**.
5. Confira os pontos, ligações, área e matriz gerados.
6. Pesquise e confirme o cliente dentro do raio.
7. Execute a otimização.
8. Acompanhe cada passo do Dijkstra.

Ao inserir o cliente, o backend pré-seleciona oito candidatos por proximidade geográfica, compara os custos rodoviários completos `origem → candidato → cliente` em uma OSRM Table e conecta os três melhores, procurando também diversidade de direção. Se menos de duas alternativas intermediárias ficarem a até 15% da distância direta, a matriz recebe também uma aresta rodoviária direta de segurança; ela é apenas mais um peso e o Dijkstra continua sendo quem escolhe o caminho.

Depois da escolha, a geometria contínua serve como validação física. Se dois trechos consecutivos refizerem mais de 120 m da mesma rua em sentidos opostos, o ponto intermediário responsável é removido, a matriz é reconstruída e o Dijkstra é executado novamente. No máximo três pontos podem ser rejeitados dessa forma. Por fim, uma referência direta rejeita trajetos ainda superiores a 35% da distância normal. Essas verificações não substituem o algoritmo: elas corrigem a topologia e o executam de novo.

Enquanto nenhum grafo dinâmico for gerado, o fluxo anterior da Zona Sul continua disponível. O botão **Usar cliente de demonstração** executa o cenário offline do `graph.json`.

## Configuração

`src/main/resources/application.yml` contém:

- loja de demonstração;
- região fixa e seu polígono;
- URLs e timeouts de Nominatim, OSRM e Overpass;
- raio dinâmico padrão, mínimo e máximo;
- quantidade de setores e anéis;
- espaçamento mínimo;
- razão máxima de desvio;
- intervalo de atualização;
- limite do cache de grafos.

`src/main/resources/data/graph.json` continua armazenando o corredor fixo da Zona Sul e seus pesos acadêmicos de fallback.

## API interna

- `GET /api/geocoding/search?query=...&scope=service`
- `GET /api/geocoding/search?query=...&scope=origin`
- `GET /api/graph`
- `POST /api/graphs/generate`
- `POST /api/routes/optimize`
- `GET /api/status`

`POST /api/graphs/generate` recebe nome, endereço, latitude, longitude, raio e a opção de forçar renovação. A resposta contém grafo, matriz, área, origem dos dados, identificador e horários de geração/renovação. O identificador deve acompanhar a otimização do cliente.

## Modo offline e falhas

- o grafo fixo e o cliente demonstrativo funcionam sem Nominatim, Overpass ou OSRM;
- no modo acadêmico padrão, **Gerar grafo da região** projeta de 16 a 32 candidatos conforme o raio e usa o OSRM para convertê-los em pontos viários; duplicatas, ajustes ruins e acessos anônimos podem reduzir com segurança o total publicado, sem depender do Overpass;
- sem OSRM, o mesmo botão continua funcional para origens dentro do polígono configurado: escolhe oito coordenadas reais versionadas, monta ligações alternativas e marca seus pesos como aproximações acadêmicas;
- falhas do OSRM durante uma tabela dinâmica usam aproximações Haversine claramente marcadas como fallback;
- falha do Overpass preserva o último grafo válido ou ativa a topologia local, quando houver cobertura;
- para uma origem fora da cobertura local e sem versão anterior, o modo ao vivo informa a indisponibilidade em vez de inventar pontos;
- sem mosaicos OSM, o cálculo continua, mas o fundo cartográfico não aparece;
- geometrias indisponíveis aparecem como ligações tracejadas e esquemáticas.

## Limitações

Os servidores públicos de Nominatim, Overpass, OSRM e mosaicos OSM não possuem garantia de disponibilidade e devem ser usados apenas em demonstrações leves. O OSRM público não fornece trânsito em tempo real. A tabela de distâncias contém a extensão da rota considerada mais rápida pelo perfil de direção do OSRM.

O cache atual é em memória e se perde ao reiniciar. Uma implantação com uso contínuo deve usar instâncias próprias ou provedores apropriados, cache persistente e políticas operacionais compatíveis.

## Estrutura

```text
src/main/java/br/com/rotas/
├── config/       propriedades
├── controller/   endpoints e erros
├── domain/       grafo, snapshots, matriz e passos
├── integration/  Nominatim, Overpass e OSRM
└── service/      geração híbrida, cache, Dijkstra e otimização
src/main/resources/
├── data/graph.json
├── static/       página, estilos, JavaScript e Leaflet
└── application.yml
src/test/java/    testes unitários, integração simulada e web
docs/             arquitetura, matemática, auditorias e status
```
