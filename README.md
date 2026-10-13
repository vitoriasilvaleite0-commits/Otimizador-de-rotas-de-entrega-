# Routify Acadêmico

Protótipo acadêmico em Java para estudar grafos, matriz de adjacência ponderada e o algoritmo de Dijkstra aplicado a rotas de entrega.

## Equipe

- João Victor
- Eduardo Dias
- Vitória Silva

## Versão 1 — grafo fixo

Nesta versão, a loja é a origem fixa e os pontos intermediários ficam versionados em `src/main/resources/data/graph.json`. O cliente é incluído depois da confirmação do endereço.

O OSRM fornece os pesos rodoviários das ligações e a geometria visual. O menor caminho global é escolhido pelo algoritmo de Dijkstra implementado manualmente em Java.

```bash
./mvnw spring-boot:run
```

A aplicação fica disponível em `http://localhost:8080`.

## Testes

```bash
./mvnw test
```
