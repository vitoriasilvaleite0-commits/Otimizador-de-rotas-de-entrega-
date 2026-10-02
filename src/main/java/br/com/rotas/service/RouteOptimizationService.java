package br.com.rotas.service;

import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.DijkstraResult;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.RouteData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteOptimizationService {
  private final GraphService graphs;
  private final DijkstraService dijkstra;

  public RouteOptimizationService(GraphService graphs, DijkstraService dijkstra) {
    this.graphs = graphs;
    this.dijkstra = dijkstra;
  }

  public OptimizationResult optimize(Point customer) {
    Graph graph = graphs.connectCustomer(customer);
    AdjacencyMatrix matrix = AdjacencyMatrix.from(graph);
    String origin = graph.points().getFirst().id();
    DijkstraResult result = dijkstra.solve(matrix, origin, customer.id());
    if (!result.reachable()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_UNREACHABLE",
          "O cliente não pode ser alcançado pelo grafo atual.");
    }
    List<Edge> segments = selectedSegments(graph, matrix, result.path());
    RouteData visualRoute = graphs.roadGeometryForPath(result.path(), graph,
        result.totalDistanceKm());
    boolean fallback = segments.stream().anyMatch(Edge::fallback) || visualRoute.fallback();
    return new OptimizationResult(graph, serialize(matrix), origin, customer.id(), result,
        segments, visualRoute.geometry(), fallback);
  }

  private static List<Edge> selectedSegments(Graph graph, AdjacencyMatrix matrix,
                                             List<String> path) {
    List<Edge> segments = new ArrayList<>();
    for (int index = 0; index < path.size() - 1; index++) {
      String from = path.get(index), to = path.get(index + 1);
      double weight = matrix.get(from, to);
      Edge edge = graph.edges().stream()
          .filter(candidate -> candidate.from().equals(from) && candidate.to().equals(to)
              && Double.compare(candidate.distanceKm(), weight) == 0)
          .findFirst().orElseThrow();
      segments.add(edge);
    }
    return List.copyOf(segments);
  }

  public static List<List<Double>> serialize(AdjacencyMatrix matrix) {
    List<List<Double>> serialized = new ArrayList<>();
    for (double[] row : matrix.copyValues()) {
      List<Double> values = new ArrayList<>();
      for (double value : row) values.add(Double.isFinite(value) ? value : null);
      serialized.add(values);
    }
    return serialized;
  }

  public record OptimizationResult(Graph graph, List<List<Double>> matrix, String origin,
                                   String destination, DijkstraResult dijkstra,
                                   List<Edge> segments, List<double[]> routeGeometry,
                                   boolean fallbackUsed) {}
}
