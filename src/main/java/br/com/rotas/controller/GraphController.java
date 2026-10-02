package br.com.rotas.controller;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.service.GraphService;
import br.com.rotas.service.RouteOptimizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GraphController {
  private final GraphService graphs;
  private final AppProperties properties;

  public GraphController(GraphService graphs, AppProperties properties) {
    this.graphs = graphs;
    this.properties = properties;
  }

  @GetMapping("/graph")
  public GraphResponse graph() {
    Graph graph = graphs.baseGraph();
    return new GraphResponse(graph,
        RouteOptimizationService.serialize(AdjacencyMatrix.from(graph)),
        graph.points().stream().map(Point::id).toList(), graphs.demo(),
        properties.region().boundary());
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("demoMode", properties.demoMode());
    status.put("nominatimConfigured", true);
    status.put("osrmConfigured", true);
    status.put("dynamicGraphEnabled", false);
    status.put("message", "Grafo fixo carregado com fallback acadêmico.");
    return status;
  }

  public record GraphResponse(Graph graph, List<List<Double>> matrix,
                              List<String> vertexOrder, GraphService.DemoData demoCustomer,
                              List<AppProperties.BoundaryPoint> serviceArea) {}
}
