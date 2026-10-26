package br.com.rotas.service;

import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.DijkstraResult;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.RouteData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RouteOptimizationService {
  private static final double MAX_COMPLETE_ROUTE_RATIO = 1.35;
  private static final double MIN_RETRACED_DISTANCE_KM = .12;
  private static final double GEOMETRY_MATCH_TOLERANCE_KM = .035;
  private static final double WAYPOINT_MATCH_TOLERANCE_KM = .08;
  private static final int MAX_REJECTED_TRANSIT_POINTS = 3;

  private final GraphService graphs;
  private final DijkstraService dijkstra;
  private final DynamicGraphService dynamicGraphs;

  @Autowired
  public RouteOptimizationService(GraphService graphs, DijkstraService dijkstra,
                                  DynamicGraphService dynamicGraphs) {
    this.graphs = graphs;
    this.dijkstra = dijkstra;
    this.dynamicGraphs = dynamicGraphs;
  }

  public RouteOptimizationService(GraphService graphs, DijkstraService dijkstra) {
    this(graphs, dijkstra, null);
  }

  public OptimizationResult optimize(Point customer) {
    return optimize(customer, null);
  }

  public OptimizationResult optimize(Point customer, String graphId) {
    GeneratedGraphSnapshot snapshot = graphId == null || graphId.isBlank()
        ? null : dynamicGraphs.get(graphId);
    Graph graph = snapshot == null
        ? graphs.connectCustomer(customer) : graphs.connectCustomer(snapshot, customer);
    boolean localTopology = snapshot != null && snapshot.source() != null
        && snapshot.source().startsWith("LOCAL_");
    boolean validateBacktracking = snapshot != null && !localTopology;
    List<String> rejectedTransitPoints = new ArrayList<>();
    OptimizationState state = null;

    for (int attempt = 0; attempt <= MAX_REJECTED_TRANSIT_POINTS; attempt++) {
      AdjacencyMatrix matrix = AdjacencyMatrix.from(graph);
      String origin = graph.points().getFirst().id();
      DijkstraResult result = dijkstra.solve(matrix, origin, customer.id());
      if (!result.reachable()) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_UNREACHABLE",
            "O cliente não pode ser alcançado pelo grafo atual.");
      }
      List<Edge> segments = selectedSegments(graph, matrix, result.path());
      RouteData visualRoute = localTopology
          ? new RouteData(result.totalDistanceKm(), 0, List.of(), true)
          : graphs.roadGeometryForPath(result.path(), graph, result.totalDistanceKm());

      Optional<String> retraced = validateBacktracking && !visualRoute.fallback()
          ? retracedTransitPoint(result.path(), graph, visualRoute.geometry())
          : Optional.empty();
      if (retraced.isPresent() && attempt < MAX_REJECTED_TRANSIT_POINTS) {
        String pointId = retraced.get();
        rejectedTransitPoints.add(pointId);
        graph = withoutTransitPoint(graph, pointId);
        continue;
      }
      if (retraced.isPresent()) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCESSIVE_ROUTE_BACKTRACK",
            "A rota ainda refaz o mesmo trecho após remover pontos intermediários inadequados.");
      }
      state = new OptimizationState(graph, matrix, origin, result, segments, visualRoute);
      break;
    }
    if (state == null) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOMER_UNREACHABLE",
          "Não restou uma alternativa válida depois de remover pontos intermediários inadequados.");
    }

    RouteData directReference = localTopology
        ? new RouteData(state.result().totalDistanceKm(), 0, List.of(), true)
        : graphs.roadGeometryForPath(List.of(state.origin(), customer.id()), state.graph(),
            state.result().totalDistanceKm());
    double routeDistance = state.visualRoute().fallback()
        ? state.result().totalDistanceKm() : state.visualRoute().distanceKm();
    double directDistance = directReference.fallback()
        ? routeDistance : directReference.distanceKm();
    double detourRatio = directDistance > 0 ? routeDistance / directDistance : 1;
    if (detourRatio > MAX_COMPLETE_ROUTE_RATIO) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCESSIVE_ROUTE_DETOUR",
          "Os pontos atuais produziriam uma volta excessiva. Gere novamente o grafo ou escolha um raio menor.");
    }
    boolean fallback = (snapshot != null && snapshot.fallbackUsed())
        || state.segments().stream().anyMatch(Edge::fallback) || state.visualRoute().fallback();
    List<double[]> routeGeometry = state.visualRoute().fallback()
        ? List.of() : state.visualRoute().geometry();
    return new OptimizationResult(state.graph(), serialize(state.matrix()), state.origin(),
        customer.id(), state.result(), state.segments(), routeGeometry, fallback, graphId,
        snapshot == null ? "GRAPH_JSON" : snapshot.source(), routeDistance, directDistance,
        detourRatio, List.copyOf(rejectedTransitPoints),
        validateBacktracking && state.result().path().size() == 2);
  }

  private static List<Edge> selectedSegments(Graph graph, AdjacencyMatrix matrix,
                                             List<String> path) {
    List<Edge> segments = new ArrayList<>();
    for (int index = 0; index < path.size() - 1; index++) {
      String from = path.get(index), to = path.get(index + 1);
      double selectedWeight = matrix.get(from, to);
      Edge selected = graph.edges().stream()
          .filter(edge -> edge.from().equals(from) && edge.to().equals(to)
              && Double.compare(edge.distanceKm(), selectedWeight) == 0)
          .findFirst().orElseThrow(() -> new IllegalStateException(
              "Segmento do caminho ausente no grafo: " + from + " -> " + to));
      segments.add(selected);
    }
    return List.copyOf(segments);
  }

  private static Graph withoutTransitPoint(Graph graph, String pointId) {
    List<Point> points = graph.points().stream().filter(point -> !point.id().equals(pointId)).toList();
    List<Edge> edges = graph.edges().stream()
        .filter(edge -> !edge.from().equals(pointId) && !edge.to().equals(pointId)).toList();
    return new Graph(points, edges);
  }

  private static Optional<String> retracedTransitPoint(List<String> path, Graph graph,
                                                       List<double[]> geometry) {
    if (path.size() < 3 || geometry.size() < 4) return Optional.empty();
    Map<String, Point> points = new HashMap<>();
    graph.points().forEach(point -> points.put(point.id(), point));
    int[] waypointIndices = waypointIndices(path, points, geometry);
    if (waypointIndices == null) return Optional.empty();
    String rejected = null;
    double largestOverlap = MIN_RETRACED_DISTANCE_KM;
    for (int index = 1; index < path.size() - 1; index++) {
      double overlap = reversedOverlap(geometry, waypointIndices[index - 1],
          waypointIndices[index], waypointIndices[index + 1]);
      if (overlap > largestOverlap) {
        largestOverlap = overlap;
        rejected = path.get(index);
      }
    }
    return Optional.ofNullable(rejected);
  }

  private static int[] waypointIndices(List<String> path, Map<String, Point> points,
                                       List<double[]> geometry) {
    int[] indices = new int[path.size()];
    int start = 0;
    for (int pathIndex = 0; pathIndex < path.size(); pathIndex++) {
      Point waypoint = points.get(path.get(pathIndex));
      if (waypoint == null) return null;
      int bestIndex = -1;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int geometryIndex = start; geometryIndex < geometry.size(); geometryIndex++) {
        double distance = distanceKm(geometry.get(geometryIndex), waypoint);
        if (distance < bestDistance) {
          bestDistance = distance;
          bestIndex = geometryIndex;
        }
      }
      if (bestIndex < 0 || bestDistance > WAYPOINT_MATCH_TOLERANCE_KM) return null;
      indices[pathIndex] = bestIndex;
      start = bestIndex;
    }
    return indices;
  }

  private static double reversedOverlap(List<double[]> geometry, int start, int waypoint,
                                        int end) {
    if (waypoint <= start || waypoint >= end) return 0;
    int incomingCursor = waypoint - 1;
    double overlap = 0;
    double[] previous = geometry.get(waypoint);
    for (int outgoingCursor = waypoint + 1;
         outgoingCursor <= end && incomingCursor >= start; outgoingCursor++) {
      double[] current = geometry.get(outgoingCursor);
      double step = distanceKm(previous, current);
      previous = current;
      if (step < .003) continue;
      int match = -1;
      double matchDistance = Double.POSITIVE_INFINITY;
      for (int candidate = incomingCursor; candidate >= start; candidate--) {
        double distance = distanceKm(current, geometry.get(candidate));
        if (distance < matchDistance) {
          matchDistance = distance;
          match = candidate;
        }
      }
      if (match < 0 || matchDistance > GEOMETRY_MATCH_TOLERANCE_KM) break;
      overlap += step;
      incomingCursor = match - 1;
    }
    return overlap;
  }

  private static double distanceKm(double[] coordinate, Point point) {
    return distanceKm(coordinate, new double[]{point.latitude(), point.longitude()});
  }

  private static double distanceKm(double[] first, double[] second) {
    double radius = 6371;
    double deltaLatitude = Math.toRadians(second[0] - first[0]);
    double deltaLongitude = Math.toRadians(second[1] - first[1]);
    double value = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
        + Math.cos(Math.toRadians(first[0])) * Math.cos(Math.toRadians(second[0]))
        * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
    return 2 * radius * Math.asin(Math.sqrt(value));
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

  private record OptimizationState(Graph graph, AdjacencyMatrix matrix, String origin,
                                   DijkstraResult result, List<Edge> segments,
                                   RouteData visualRoute) {}

  public record OptimizationResult(Graph graph, List<List<Double>> matrix, String origin,
                                   String destination, DijkstraResult dijkstra,
                                   List<Edge> segments, List<double[]> routeGeometry,
                                   boolean fallbackUsed, String graphId, String graphSource,
                                   double routeDistanceKm, double directReferenceDistanceKm,
                                   double detourRatio, List<String> rejectedTransitPoints,
                                   boolean directSafetyConnectionUsed) {}
}
