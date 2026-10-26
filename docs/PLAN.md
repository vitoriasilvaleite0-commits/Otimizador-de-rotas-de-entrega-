# Plano de implementação

## Etapas e critérios de validação

1. **Estrutura** — Maven Wrapper executa e o projeto compila com Java 21.
2. **Domínio, matriz e Dijkstra** — matriz valida pesos e o algoritmo cobre os dez cenários matemáticos obrigatórios.
3. **Grafo e fallback** — JSON versionado carrega loja, oito locais reais, arestas direcionadas e cliente demonstrativo.
4. **Integrações** — Nominatim e OSRM têm timeout, cache, limitação de frequência/falhas e testes sem internet.
5. **API** — endpoints de grafo, geocodificação, otimização e status validam entradas e padronizam erros.
6. **Frontend** — página responsiva servida pelo Spring exibe Leaflet, busca, matriz, estados e animação passo a passo.
7. **Qualidade** — testes unitários/de integração passam; pacote é gerado; servidor inicia; fluxo é verificado no navegador.
8. **Documentação** — README e documentos técnicos explicam execução, matemática, arquitetura, offline e limitações.
9. **Origem e topologia dinâmicas** — busca ampla de origem, geração híbrida determinística, Overpass, OSRM Table/Nearest, cache versionado e renovação agendada preservam o modo fixo.

Após cada bloco, executar os testes relacionados e registrar o resultado em `docs/STATUS.md`.
