# Sprints do Routify Acadêmico

## Equipe

- João Victor
- Eduardo Dias
- Vitória Silva

## Sprint 1 — Fundação e matemática

**Período:** 29/09/2026 a 02/10/2026

Objetivos:

- estruturar o projeto Java/Spring Boot;
- modelar vértices, arestas e matriz;
- implementar manualmente o Dijkstra;
- criar o primeiro grafo fixo;
- integrar Nominatim e OSRM no backend.

Resultado: backend funcional e 26 testes aprovados.

## Sprint 2 — Interface e versão fixa

**Período:** 03/10/2026 a 07/10/2026

Objetivos:

- criar o mapa com Leaflet/OpenStreetMap;
- exibir a matriz e os passos do Dijkstra;
- ampliar o grafo para o extremo sul;
- adicionar mais de uma alternativa de caminho;
- diferenciar bairros e estações.

Resultado: `v1.0.0`, seguida da expansão da cobertura e de 38 testes aprovados.

## Sprint 3 — Auditoria e correções

**Período:** 08/10/2026 a 13/10/2026

Objetivos:

- auditar a responsabilidade do Dijkstra;
- garantir pesos somente em quilômetros;
- fazer a geometria seguir a sequência escolhida;
- corrigir pontos em acessos e estacionamentos;
- validar desvios e melhorar a documentação matemática.

Resultado: `v1.1.0`, com 43 testes aprovados no estado reconstruído da versão fixa.

## Sprint 4 — Grafo dinâmico

**Período:** 14/10/2026 a 18/10/2026

Objetivos:

- escolher uma origem diferente da loja;
- definir o raio da região;
- gerar pontos e ligações dinamicamente;
- atualizar a matriz;
- preservar cache e fallback;
- adaptar o frontend para o novo fluxo.

Resultado: topologia dinâmica funcional no backend e no frontend.

## Sprint 5 — V2 Alpha e confiabilidade

**Período:** 19/10/2026

Objetivos:

- melhorar a densidade e as alternativas do grafo;
- rejeitar pontos viários inadequados;
- detectar retorno sobre o mesmo trecho;
- comparar a rota com uma referência direta;
- executar novamente o Dijkstra após remover um ponto ruim;
- finalizar os testes e a documentação.

Resultado: `v2.0.0-alpha.1`, pacote executável e 65 testes aprovados.

## Distribuição de autoria no histórico

Os commits foram distribuídos entre os integrantes de acordo com blocos de trabalho da reconstrução:

- João Victor: estrutura, integração geral, geração dinâmica e consolidação;
- Eduardo Dias: backend geográfico, otimização e correções de rota;
- Vitória Silva: modelo matemático, interface, expansão territorial e documentação.

A divisão serve para representar a colaboração da equipe; o projeto e suas decisões foram desenvolvidos coletivamente.
