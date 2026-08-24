Crie um sistema acadêmico chamado “Otimizador de Rotas de Entrega”, o núcleo do sistema deve ser desenvolvido em Java, utilizando Maven para organizar o back-end. A interface deve ser web, desenvolvida com HTML, CSS e JavaScript, comunicando-se com o Java por meio de uma API REST em formato JSON.

O sistema deve representar uma rede de entregas como um grafo não direcionado e ponderado. Cada vértice representa um local de entrega, cada aresta representa uma rota entre dois locais e o peso da aresta representa a distância em quilômetros.

Utilize os seguintes pontos:

•⁠  ⁠CD — Centro de Distribuição
•⁠  ⁠VM — Vila Mariana
•⁠  ⁠MO — Moema
•⁠  ⁠IP — Ipiranga
•⁠  ⁠SA — Santo Amaro
•⁠  ⁠JP — Jabaquara
•⁠  ⁠IN — Interlagos
•⁠  ⁠SO — Socorro

Utilize as seguintes conexões e distâncias:

•⁠  ⁠CD–VM: 4 km
•⁠  ⁠CD–IP: 7 km
•⁠  ⁠VM–MO: 3 km
•⁠  ⁠VM–IP: 5 km
•⁠  ⁠MO–SA: 6 km
•⁠  ⁠MO–SO: 9 km
•⁠  ⁠IP–SA: 4 km
•⁠  ⁠IP–JP: 6 km
•⁠  ⁠SA–JP: 3 km
•⁠  ⁠SA–IN: 5 km
•⁠  ⁠SA–SO: 7 km
•⁠  ⁠JP–IN: 4 km
•⁠  ⁠IN–SO: 6 km

Represente matematicamente o grafo por meio de uma matriz de adjacência ponderada. Na matriz:

•⁠  ⁠a linha representa o ponto de origem;
•⁠  ⁠a coluna representa o ponto de destino;
•⁠  ⁠0 representa o ponto comparado com ele mesmo;
•⁠  ⁠um número representa a distância entre dois pontos diretamente conectados;
•⁠  ⁠infinito representa a ausência de conexão direta.

Implemente os algoritmos BFS, DFS, Dijkstra e A*:

•⁠  ⁠BFS deve encontrar um caminho com o menor número de conexões;
•⁠  ⁠DFS deve explorar o grafo em profundidade, sem garantir a menor distância;
•⁠  ⁠Dijkstra deve encontrar o caminho com o menor custo total;
•⁠  ⁠A* deve utilizar a fórmula f(n) = g(n) + h(n), em que g(n) representa o custo acumulado e h(n) representa uma estimativa até o destino.

As estimativas do A* podem ser armazenadas em uma matriz heurística contendo valores admissíveis entre os pontos, sem ultrapassar o custo real do menor caminho.

O sistema também deve possuir um preço por quilômetro, informado pelo usuário antes do cálculo da rota. Esse valor deve ser representado em reais e aceitar casas decimais.

Calcule o preço da entrega utilizando:

Preço da entrega = distância total da rota × preço por quilômetro

Por exemplo, se a rota possuir 16 km e o preço informado for R$ 2,50 por quilômetro:

Preço da entrega = 16 × 2,50 = R$ 40,00

A interface deve permitir:

•⁠  ⁠selecionar a origem;
•⁠  ⁠selecionar o destino;
•⁠  ⁠selecionar o algoritmo;
•⁠  ⁠informar o preço por quilômetro;
•⁠  ⁠calcular a rota;
•⁠  ⁠visualizar o grafo;
•⁠  ⁠destacar a rota encontrada;
•⁠  ⁠exibir a distância total em quilômetros;
•⁠  ⁠exibir o preço informado por quilômetro;
•⁠  ⁠exibir o preço total da entrega em reais;
•⁠  ⁠exibir somente os nós pertencentes ao caminho final no campo “Nós visitados”;
•⁠  ⁠visualizar a matriz de adjacência ponderada;
•⁠  ⁠consultar a ordem em que os nós foram analisados pelo algoritmo em um campo separado;
•⁠  ⁠quando o A* estiver selecionado, mostrar os valores g(n), h(n) e f(n).

O campo de preço por quilômetro deve ser obrigatório, aceitar somente números positivos e apresentar uma mensagem de erro quando estiver vazio, possuir texto inválido ou receber um valor igual ou menor que zero.

O ResultadoRota deve armazenar:

•⁠  ⁠caminho final;
•⁠  ⁠distância total;
•⁠  ⁠preço por quilômetro;
•⁠  ⁠preço total da entrega;
•⁠  ⁠nós analisados pelo algoritmo.

Organize o projeto nos seguintes pacotes:

•⁠  ⁠model: Ponto, Rota e ResultadoRota;
•⁠  ⁠graph: Grafo e MatrizAdjacencia;
•⁠  ⁠algorithm: BFS, DFS, Dijkstra, AStar e AlgoritmoRota;
•⁠  ⁠service: OtimizadorRotas;
•⁠  ⁠controller: MainController;
•⁠  ⁠data: MapaExemplo;
•⁠  ⁠ui: MainApp;
•⁠  ⁠resources: arquivos FXML e CSS;
•⁠  ⁠test: testes dos algoritmos.

O caminho final deve ser reconstruído utilizando os predecessores registrados durante a execução do algoritmo.

Crie testes comparando os quatro algoritmos e confirme que, na rota entre Centro de Distribuição e Interlagos, Dijkstra e A* encontram o caminho CD → IP → SA → IN, com distância total de 16 km.

Também crie um teste de preço utilizando R$ 2,50 por quilômetro. Para uma rota de 16 km, o sistema deve apresentar o preço total de R$ 40,00.

Os componentes matemáticos utilizados no projeto devem incluir:

•⁠  ⁠teoria dos grafos;
•⁠  ⁠vértices e arestas;
•⁠  ⁠grafos ponderados;
•⁠  ⁠matriz de adjacência ponderada;
•⁠  ⁠álgebra linear;
•⁠  ⁠soma dos pesos das arestas;
•⁠  ⁠busca em largura e profundidade;
•⁠  ⁠cálculo de caminho mínimo;
•⁠  ⁠função heurística do A*;
•⁠  ⁠relações e conjuntos;
•⁠  ⁠otimização de rotas;
•⁠  ⁠multiplicação de números decimais;
•⁠  ⁠proporcionalidade entre distância e preço;
•⁠  ⁠cálculo do preço total da entrega.
