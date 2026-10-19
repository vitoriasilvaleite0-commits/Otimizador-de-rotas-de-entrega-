package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.RoadSnapData;
import br.com.rotas.integration.RouteData;
import br.com.rotas.integration.RoutingService;
import br.com.rotas.integration.SnappedRouteTableData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalTopologyGraphGeneratorTest {
  @Test
  void generatesSixteenNewRoadPointsAroundTheChosenOrigin() {
    Fixture fixture = new Fixture(false);
    Point origin = origin(-23.701167, -46.708291);

    GeneratedGraphSnapshot snapshot = fixture.generator.generate("graph-radial", origin, 5);

    assertEquals(LocalTopologyGraphGenerator.RADIAL_SOURCE, snapshot.source());
    assertFalse(snapshot.fallbackUsed());
    assertEquals(17, snapshot.graph().points().size());
    assertEquals("ORIGIN", snapshot.graph().points().getFirst().id());
    assertTrue(snapshot.graph().points().stream().skip(1)
        .allMatch(point -> point.id().matches("P\\d{2}")));
    assertTrue(snapshot.graph().points().stream().skip(1)
        .noneMatch(point -> fixture.graphs.baseGraph().points().stream()
            .anyMatch(fixed -> fixed.id().equals(point.id()))));
    assertEquals(1, fixture.tableCalls.get());

    AdjacencyMatrix matrix = AdjacencyMatrix.from(snapshot.graph());
    DijkstraService dijkstra = new DijkstraService();
    snapshot.graph().points().stream().skip(1).forEach(point ->
        assertTrue(dijkstra.solve(matrix, "ORIGIN", point.id()).reachable(), point.id()));
  }

  @Test
  void differentOriginsGenerateDifferentCoordinates() {
    Fixture fixture = new Fixture(false);
    GeneratedGraphSnapshot northern = fixture.generator.generate("north",
        origin(-23.5900, -46.6500), 5);
    GeneratedGraphSnapshot southern = fixture.generator.generate("south",
        origin(-23.7160, -46.6960), 5);

    Point northPoint = northern.graph().points().get(1);
    Point southPoint = southern.graph().points().get(1);
    assertNotEquals(northPoint.latitude(), southPoint.latitude());
    assertNotEquals(northPoint.longitude(), southPoint.longitude());
    assertTrue(GraphService.haversine(northern.origin(), northPoint) > 1);
    assertTrue(GraphService.haversine(southern.origin(), southPoint) > 1);
  }

  @Test
  void largeRadiusIncreasesAngularResolutionAndAddsRoadShortcuts() {
    Fixture fixture = new Fixture(false);

    GeneratedGraphSnapshot snapshot = fixture.generator.generate("large-radius",
        origin(-23.6500, -46.6500), 20);

    assertEquals(33, snapshot.graph().points().size());
    assertEquals(32, snapshot.graph().points().stream()
        .filter(point -> point.id().startsWith("P")).count());
    snapshot.graph().points().stream().filter(point -> point.type() == PointType.INTERMEDIATE)
        .forEach(point -> assertTrue(snapshot.graph().edges().stream()
            .filter(edge -> edge.from().equals(point.id())).count() >= 4, point.id()));
  }

  @Test
  void prefersNamedRoadPointsWhenEnoughAlternativesExist() {
    Fixture fixture = new Fixture(false, Set.of(5, 13));

    GeneratedGraphSnapshot snapshot = fixture.generator.generate("named-roads",
        origin(-23.6500, -46.6500), 10);

    assertEquals(LocalTopologyGraphGenerator.RADIAL_SOURCE, snapshot.source());
    assertEquals(15, snapshot.graph().points().size());
    assertTrue(snapshot.graph().points().stream().skip(1)
        .noneMatch(point -> point.name().startsWith("Ponto viário")));
  }

  @Test
  void dynamicServiceCachesTheGeneratedRadialGraph() {
    Fixture fixture = new Fixture(false);
    DynamicGraphService service = new DynamicGraphService(null, fixture.generator,
        fixture.properties);
    Point origin = origin(-23.701167, -46.708291);

    GeneratedGraphSnapshot snapshot = service.generate(origin, 5, false);

    assertEquals(LocalTopologyGraphGenerator.RADIAL_SOURCE, snapshot.source());
    assertSame(snapshot, service.generate(origin, 5, false));
    assertEquals(1, service.activeGraphs());
    assertEquals(1, fixture.tableCalls.get());
  }

  @Test
  void fallsBackToVerifiedVersionedPointsWhenOsrmIsUnavailable() {
    Fixture fixture = new Fixture(true);

    GeneratedGraphSnapshot snapshot = fixture.generator.generate("fallback",
        origin(-23.701167, -46.708291), 5);

    assertEquals(LocalTopologyGraphGenerator.SOURCE, snapshot.source());
    assertTrue(snapshot.fallbackUsed());
    assertEquals(9, snapshot.graph().points().size());
    assertTrue(snapshot.graph().points().stream().skip(1)
        .anyMatch(point -> point.id().equals("INTERLAGOS")));
  }

  @Test
  void demoModeRejectsUnsupportedOriginBeforeCallingOsrm() {
    Fixture fixture = new Fixture(false);
    DynamicGraphService service = new DynamicGraphService(null, fixture.generator,
        fixture.properties);

    ApiException error = assertThrows(ApiException.class,
        () -> service.generate(origin(-23.5505, -46.6333), 5, false));

    assertEquals("OUTSIDE_LOCAL_TOPOLOGY", error.code());
    assertEquals(0, fixture.tableCalls.get());
  }

  @Test
  void generatedGraphCanOptimizeAClientNearTheOuterRing() {
    Fixture fixture = new Fixture(false);
    DynamicGraphService service = new DynamicGraphService(null, fixture.generator,
        fixture.properties);
    Point origin = origin(-23.6500, -46.6500);
    GeneratedGraphSnapshot snapshot = service.generate(origin, 10, false);
    Point customer = new Point("CLIENT", "Cliente", "Endereço",
        -23.7040, -46.6100, PointType.CUSTOMER);

    RouteOptimizationService.OptimizationResult result = new RouteOptimizationService(
        fixture.graphs, new DijkstraService(), service).optimize(customer, snapshot.id());

    assertTrue(result.dijkstra().reachable());
    assertEquals("ORIGIN", result.dijkstra().path().getFirst());
    assertEquals("CLIENT", result.dijkstra().path().getLast());
    assertTrue(result.dijkstra().path().stream().anyMatch(id -> id.startsWith("P")));
  }

  private static Point origin(double latitude, double longitude) {
    return new Point("REQUESTED", "Origem", "Endereço", latitude, longitude, PointType.STORE);
  }

  private static class Fixture {
    final AtomicInteger tableCalls = new AtomicInteger();
    final AtomicInteger routeCalls = new AtomicInteger();
    final AppProperties properties = properties();
    final GraphService graphs;
    final LocalTopologyGraphGenerator generator;
    final Set<Integer> unnamedSourceIndices;

    Fixture(boolean tableFallback) {
      this(tableFallback, Set.of());
    }

    Fixture(boolean tableFallback, Set<Integer> unnamedSourceIndices) {
      this.unnamedSourceIndices = Set.copyOf(unnamedSourceIndices);
      ObjectMapper mapper = new ObjectMapper();
      RoutingService routing = new RoutingService(properties, mapper) {
        @Override
        public SnappedRouteTableData snappedDistanceTable(List<Point> points) {
          tableCalls.incrementAndGet();
          return snappedTable(points, tableFallback);
        }

        @Override
        public RouteData route(Point from, Point to, double fallbackKm) {
          routeCalls.incrementAndGet();
          return new RouteData(fallbackKm, 1, List.of(), true);
        }

        @Override
        public RouteData routeThrough(List<Point> points, double fallbackKm) {
          routeCalls.incrementAndGet();
          return new RouteData(fallbackKm, 1, List.of(), true);
        }
      };
      graphs = new GraphService(mapper, routing, properties);
      generator = new LocalTopologyGraphGenerator(graphs, routing, properties);
    }

    private SnappedRouteTableData snappedTable(List<Point> points, boolean fallback) {
      List<RoadSnapData> snaps = new ArrayList<>();
      for (int index = 0; index < points.size(); index++) {
        Point point = points.get(index);
        snaps.add(new RoadSnapData(point.latitude(), point.longitude(), 12,
            unnamedSourceIndices.contains(index) ? "" : "Rua gerada " + index, fallback));
      }
      double[][] values = new double[points.size()][points.size()];
      for (int from = 0; from < points.size(); from++) {
        for (int to = 0; to < points.size(); to++) {
          values[from][to] = from == to ? 0
              : GraphService.haversine(points.get(from), points.get(to)) * 1.18
              + (from < to ? .02 : .05);
        }
      }
      return new SnappedRouteTableData(snaps, values, fallback);
    }
  }

  private static AppProperties properties() {
    return new AppProperties(
        new AppProperties.Store("STORE", "Loja", "Endereço", -23.61562, -46.63918),
        new AppProperties.Region(-23.745, -23.578, -46.727, -46.580, "x", List.of()),
        new AppProperties.External(
            new AppProperties.External.Nominatim("http://test", "test", 1, 0),
            new AppProperties.External.Osrm("http://test", 1),
            new AppProperties.External.Overpass("http://test", 1)),
        true, AppProperties.GraphGeneration.defaults());
  }
}
