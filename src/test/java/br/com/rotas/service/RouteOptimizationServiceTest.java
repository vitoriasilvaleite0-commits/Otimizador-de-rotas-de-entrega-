package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.DijkstraResult;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.RouteData;
import br.com.rotas.integration.RoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteOptimizationServiceTest {
  @Test void dijkstraChoosesPathAndSegmentsFollowItExactly() {
    GraphService graphs = graphs();
    CountingDijkstra dijkstra = new CountingDijkstra();
    var demo = graphs.demo();
    Point customer = new Point("CLIENT", demo.name, demo.address, demo.latitude,
        demo.longitude, PointType.CUSTOMER);
    var result = new RouteOptimizationService(graphs, dijkstra).optimize(customer);
    assertTrue(dijkstra.called);
    assertEquals(result.dijkstra().path().size() - 1, result.segments().size());
    double sum = 0;
    for (int index = 0; index < result.segments().size(); index++) {
      Edge edge = result.segments().get(index);
      assertEquals(result.dijkstra().path().get(index), edge.from());
      assertEquals(result.dijkstra().path().get(index + 1), edge.to());
      sum += edge.distanceKm();
    }
    assertEquals(result.dijkstra().totalDistanceKm(), sum, 1e-9);
  }

  @Test void matrixContainsOnlyEdgeDistancesInKilometers() {
    GraphService graphs = graphs();
    var demo = graphs.demo();
    Graph graph = graphs.connectCustomer(new Point("CLIENT", demo.name, demo.address,
        demo.latitude, demo.longitude, PointType.CUSTOMER));
    AdjacencyMatrix matrix = AdjacencyMatrix.from(graph);
    for (Edge edge : graph.edges())
      assertEquals(edge.distanceKm(), matrix.get(edge.from(), edge.to()), 1e-9);
  }

  @Test void interlagosClientHasAlternativesAndDijkstraChoosesTheShortest() {
    GraphService graphs = graphs();
    var demo = graphs.demo();
    var result = new RouteOptimizationService(graphs, new DijkstraService()).optimize(
        new Point("CLIENT", demo.name, demo.address, demo.latitude, demo.longitude,
            PointType.CUSTOMER));
    assertEquals(List.of("STORE", "SHOPPING", "JABAQUARA", "CAMPO_GRANDE",
        "INTERLAGOS", "CLIENT"), result.dijkstra().path());
    assertEquals(20.77, result.dijkstra().totalDistanceKm(), 1e-9);
  }

  @Test void roadGeometryDoesNotChangeTheMatrixOrDijkstraPath() {
    AppProperties properties = properties();
    List<List<String>> orders = new ArrayList<>();
    RoutingService routing = new RoutingService(properties, new ObjectMapper()) {
      @Override public RouteData routeThrough(List<Point> points, double fallbackKm) {
        orders.add(points.stream().map(Point::id).toList());
        Point first = points.getFirst(), last = points.getLast();
        return new RouteData(999, 100, List.of(
            new double[]{first.latitude(), first.longitude()},
            new double[]{last.latitude(), last.longitude()}), false);
      }
    };
    GraphService graphs = new GraphService(new ObjectMapper(), routing, properties);
    var demo = graphs.demo();
    var result = new RouteOptimizationService(graphs, new DijkstraService()).optimize(
        new Point("CLIENT", demo.name, demo.address, demo.latitude, demo.longitude,
            PointType.CUSTOMER));
    assertEquals(List.of("STORE", "SHOPPING", "JABAQUARA", "CAMPO_GRANDE",
        "INTERLAGOS", "CLIENT"), result.dijkstra().path());
    assertEquals(result.dijkstra().path(), orders.getFirst());
    assertEquals(List.of("STORE", "CLIENT"), orders.get(1));
    assertTrue(result.segments().stream().noneMatch(edge -> edge.distanceKm() == 999));
  }

  @Test void rejectsAContinuousGeometryWithExcessiveDetour() {
    AppProperties properties = properties();
    RoutingService routing = new RoutingService(properties, new ObjectMapper()) {
      @Override public RouteData routeThrough(List<Point> points, double fallbackKm) {
        return new RouteData(points.size() == 2 ? 10 : 14, 100, List.of(
            new double[]{points.getFirst().latitude(), points.getFirst().longitude()},
            new double[]{points.getLast().latitude(), points.getLast().longitude()}), false);
      }
    };
    GraphService graphs = new GraphService(new ObjectMapper(), routing, properties);
    var demo = graphs.demo();
    ApiException error = assertThrows(ApiException.class,
        () -> new RouteOptimizationService(graphs, new DijkstraService()).optimize(
            new Point("CLIENT", demo.name, demo.address, demo.latitude, demo.longitude,
                PointType.CUSTOMER)));
    assertEquals("EXCESSIVE_ROUTE_DETOUR", error.code());
  }

  private AppProperties properties() {
    return new AppProperties(new AppProperties.Store("STORE", "Loja", "End", -23.61562,
        -46.63918), new AppProperties.Region(-23.78, -23.55, -46.75, -46.58, "x", List.of()),
        new AppProperties.External(new AppProperties.External.Nominatim("http://test", "test", 1, 0),
            new AppProperties.External.Osrm("http://test", 1),
            new AppProperties.External.Overpass("http://test", 1)), true,
        AppProperties.GraphGeneration.defaults());
  }

  private GraphService graphs() {
    AppProperties properties = properties();
    RoutingService routing = new RoutingService(properties, new ObjectMapper()) {
      @Override public RouteData route(Point first, Point second, double fallbackKm) {
        return new RouteData(fallbackKm, 100, List.of(), true);
      }
      @Override public RouteData routeThrough(List<Point> points, double fallbackKm) {
        return new RouteData(fallbackKm, 100, List.of(), true);
      }
    };
    return new GraphService(new ObjectMapper(), routing, properties);
  }

  static class CountingDijkstra extends DijkstraService {
    boolean called;
    @Override public DijkstraResult solve(AdjacencyMatrix matrix, String source, String target) {
      called = true;
      return super.solve(matrix, source, target);
    }
  }
}
