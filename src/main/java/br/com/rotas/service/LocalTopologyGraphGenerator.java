package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.RoadSnapData;
import br.com.rotas.integration.RoutingService;
import br.com.rotas.integration.SnappedRouteTableData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LocalTopologyGraphGenerator {
  static final String SOURCE = "LOCAL_VERIFIED_TOPOLOGY_FALLBACK";
  static final String RADIAL_SOURCE = "OSRM_RADIAL_DYNAMIC";
  private static final int POINT_LIMIT = 8;
  private static final int MAX_SECTORS = 16;
  private static final double TARGET_ARC_KM = 5.5;
  private static final double MAX_SNAP_METERS = 500;
  private static final int MIN_POINT_ACCESS_DEGREE = 2;
  private final GraphService graphs;
  private final RoutingService routing;
  private final AppProperties properties;

  public LocalTopologyGraphGenerator(GraphService graphs, RoutingService routing,
                                     AppProperties properties) {
    this.graphs = graphs;
    this.routing = routing;
    this.properties = properties;
  }

  public boolean covers(Point origin) {
    return properties.region().contains(origin.latitude(), origin.longitude());
  }

  public GeneratedGraphSnapshot generate(String id, Point requestedOrigin, double radiusKm) {
    if (!covers(requestedOrigin)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OUTSIDE_LOCAL_TOPOLOGY",
          "A origem está fora da cobertura topológica local do projeto.");
    }
    List<TargetPoint> targets = radialTargets(requestedOrigin, radiusKm);
    SnappedRouteTableData table = routing.snappedDistanceTable(
        targets.stream().map(TargetPoint::point).toList());
    if (!table.fallback()) {
      GeneratedGraphSnapshot radial = radialSnapshot(id, requestedOrigin, radiusKm, targets, table);
      if (radial != null) return radial;
    }
    return verifiedFallback(id, requestedOrigin, radiusKm);
  }

  private GeneratedGraphSnapshot radialSnapshot(String id, Point requestedOrigin, double radiusKm,
                                                 List<TargetPoint> targets,
                                                 SnappedRouteTableData table) {
    if (table.snappedPoints().size() != targets.size()) return null;
    RoadSnapData snappedOrigin = table.snappedPoints().getFirst();
    if (snappedOrigin.distanceMeters() > MAX_SNAP_METERS) return null;
    Point origin = new Point("ORIGIN", requestedOrigin.name(), requestedOrigin.address(),
        snappedOrigin.latitude(), snappedOrigin.longitude(), PointType.STORE);
    List<Point> points = new ArrayList<>();
    List<RadialPoint> radial = new ArrayList<>();
    points.add(origin);
    for (int sourceIndex = 1; sourceIndex < targets.size(); sourceIndex++) {
      TargetPoint target = targets.get(sourceIndex);
      RoadSnapData snap = table.snappedPoints().get(sourceIndex);
      if (snap.distanceMeters() > MAX_SNAP_METERS) continue;
      Point candidate = new Point(String.format(Locale.ROOT, "P%02d", radial.size() + 1),
          snap.roadName().isBlank() ? "Ponto viário " + (radial.size() + 1) : snap.roadName(),
          String.format(Locale.ROOT, "Ponto viário gerado pelo OSRM — anel %s, setor %d",
              target.ring() == 0 ? "interno" : "externo", target.sector() + 1),
          snap.latitude(), snap.longitude(), PointType.INTERMEDIATE);
      if (points.stream().anyMatch(existing -> GraphService.haversine(existing, candidate) < .12))
        continue;
      points.add(candidate);
      radial.add(new RadialPoint(candidate, target.ring(), target.sector(), sourceIndex,
          target.sectorCount(), !snap.roadName().isBlank()));
    }
    double[][] distances = table.distancesKm();
    List<RadialPoint> allCandidates = List.copyOf(radial);
    List<RadialPoint> accessible = allCandidates.stream()
        .filter(point -> hasSufficientRoadAccess(point, allCandidates, distances, radiusKm))
        .toList();
    List<RadialPoint> namedRoads = accessible.stream().filter(RadialPoint::namedRoad).toList();
    if (namedRoads.size() >= properties.graphGeneration().minimumPoints()) accessible = namedRoads;
    if (accessible.size() != radial.size()) {
      radial = new ArrayList<>(accessible);
      points = new ArrayList<>();
      points.add(origin);
      for (RadialPoint point : radial) points.add(point.point());
    }
    if (radial.size() < properties.graphGeneration().minimumPoints()) return null;

    Map<String, Edge> edges = new LinkedHashMap<>();
    List<RadialPoint> inner = radial.stream().filter(point -> point.ring() == 0)
        .sorted(Comparator.comparingInt(RadialPoint::sector)).toList();
    List<RadialPoint> outer = radial.stream().filter(point -> point.ring() == 1)
        .sorted(Comparator.comparingInt(RadialPoint::sector)).toList();
    for (RadialPoint point : inner) addPair(edges, origin, 0, point, distances, radiusKm);
    addRing(edges, inner, distances, radiusKm);
    addRing(edges, outer, distances, radiusKm);
    for (RadialPoint point : inner) {
      outer.stream().min(Comparator.comparingInt(other -> sectorDistance(point.sector(),
          other.sector(), point.sectorCount()))).ifPresent(other ->
          addPair(edges, point, other, distances, radiusKm));
    }
    addNearestAlternatives(points, radial, edges, distances, radiusKm);
    Graph graph = new Graph(points, List.copyOf(edges.values()));
    if (!allReachable(graph)) return null;

    Instant generatedAt = Instant.now();
    return new GeneratedGraphSnapshot(id, graph, origin, radiusKm, circle(origin, radiusKm),
        generatedAt, generatedAt.plusMillis(properties.graphGeneration().refreshIntervalMillis()),
        false, false, RADIAL_SOURCE);
  }

  private void addRing(Map<String, Edge> edges, List<RadialPoint> ring,
                       double[][] distances, double radiusKm) {
    if (ring.size() < 2) return;
    for (int index = 0; index < ring.size(); index++)
      addPair(edges, ring.get(index), ring.get((index + 1) % ring.size()), distances, radiusKm);
  }

  private void addNearestAlternatives(List<Point> points, List<RadialPoint> radial,
                                      Map<String, Edge> edges, double[][] distances,
                                      double radiusKm) {
    List<IndexedPoint> indexed = new ArrayList<>();
    indexed.add(new IndexedPoint(points.getFirst(), 0));
    radial.forEach(point -> indexed.add(new IndexedPoint(point.point(), point.sourceIndex())));
    for (IndexedPoint from : indexed) {
      int minimum = from.sourceIndex() == 0 ? 6 : 4;
      indexed.stream().filter(to -> to.sourceIndex() != from.sourceIndex())
          .sorted(Comparator.comparingDouble(to -> distances[from.sourceIndex()][to.sourceIndex()]))
          .forEach(to -> {
            if (outDegree(from.point().id(), edges) < minimum)
              addDirected(edges, from, to, distances, radiusKm);
          });
    }
  }

  private void addPair(Map<String, Edge> edges, Point origin, int originIndex,
                       RadialPoint point, double[][] distances, double radiusKm) {
    addDirected(edges, new IndexedPoint(origin, originIndex),
        new IndexedPoint(point.point(), point.sourceIndex()), distances, radiusKm);
    addDirected(edges, new IndexedPoint(point.point(), point.sourceIndex()),
        new IndexedPoint(origin, originIndex), distances, radiusKm);
  }

  private void addPair(Map<String, Edge> edges, RadialPoint first, RadialPoint second,
                       double[][] distances, double radiusKm) {
    addDirected(edges, new IndexedPoint(first.point(), first.sourceIndex()),
        new IndexedPoint(second.point(), second.sourceIndex()), distances, radiusKm);
    addDirected(edges, new IndexedPoint(second.point(), second.sourceIndex()),
        new IndexedPoint(first.point(), first.sourceIndex()), distances, radiusKm);
  }

  private void addDirected(Map<String, Edge> edges, IndexedPoint from, IndexedPoint to,
                           double[][] distances, double radiusKm) {
    double road = distances[from.sourceIndex()][to.sourceIndex()];
    double geographic = GraphService.haversine(from.point(), to.point());
    if (!validRoadEdge(road, geographic, radiusKm)) return;
    add(edges, new Edge(from.point().id(), to.point().id(), road, false, List.of()));
  }

  private boolean hasSufficientRoadAccess(RadialPoint point, List<RadialPoint> candidates,
                                          double[][] distances, double radiusKm) {
    long outgoing = candidates.stream().filter(other -> other != point)
        .filter(other -> validRoadEdge(distances[point.sourceIndex()][other.sourceIndex()],
            GraphService.haversine(point.point(), other.point()), radiusKm))
        .limit(MIN_POINT_ACCESS_DEGREE).count();
    long incoming = candidates.stream().filter(other -> other != point)
        .filter(other -> validRoadEdge(distances[other.sourceIndex()][point.sourceIndex()],
            GraphService.haversine(other.point(), point.point()), radiusKm))
        .limit(MIN_POINT_ACCESS_DEGREE).count();
    return outgoing >= MIN_POINT_ACCESS_DEGREE && incoming >= MIN_POINT_ACCESS_DEGREE;
  }

  private boolean validRoadEdge(double road, double geographic, double radiusKm) {
    return Double.isFinite(road) && road > 0 && road <= radiusKm * 1.5
        && (geographic < .4
        || road / geographic <= properties.graphGeneration().maxDetourRatio());
  }

  private GeneratedGraphSnapshot verifiedFallback(String id, Point requestedOrigin,
                                                   double radiusKm) {
    Point origin = new Point("ORIGIN", requestedOrigin.name(), requestedOrigin.address(),
        requestedOrigin.latitude(), requestedOrigin.longitude(), PointType.STORE);
    List<Point> selected = graphs.baseGraph().points().stream()
        .filter(point -> point.type() == PointType.INTERMEDIATE)
        .sorted(Comparator.comparingDouble(point -> GraphService.haversine(origin, point)))
        .limit(POINT_LIMIT).toList();
    if (selected.size() < 3) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_LOCAL_TOPOLOGY",
          "A topologia local não possui pontos suficientes para a geração.");
    }
    List<Point> points = new ArrayList<>();points.add(origin);points.addAll(selected);
    Map<String, Edge> edges = new LinkedHashMap<>();
    graphs.baseGraph().edges().stream()
        .filter(edge -> contains(selected, edge.from()) && contains(selected, edge.to()))
        .forEach(edge -> add(edges, edge));
    for (Point point : selected.stream().limit(3).toList()) addLocalPair(edges, origin, point);
    for (int index = 0; index < selected.size() - 1; index++)
      addLocalPair(edges, selected.get(index), selected.get(index + 1));
    for (Point point : selected) selected.stream()
        .filter(other -> !other.id().equals(point.id()))
        .sorted(Comparator.comparingDouble(other -> GraphService.haversine(point, other)))
        .limit(2).forEach(other -> addLocalPair(edges, point, other));
    Instant generatedAt = Instant.now();
    return new GeneratedGraphSnapshot(id, new Graph(points, List.copyOf(edges.values())), origin,
        radiusKm, circle(origin, radiusKm), generatedAt,
        generatedAt.plusMillis(properties.graphGeneration().refreshIntervalMillis()),
        true, false, SOURCE);
  }

  private void addLocalPair(Map<String, Edge> edges, Point first, Point second) {
    double distance = Math.max(.05, GraphService.haversine(first, second) * 1.28);
    add(edges, new Edge(first.id(), second.id(), distance, true, List.of()));
    add(edges, new Edge(second.id(), first.id(), distance, true, List.of()));
  }

  private List<TargetPoint> radialTargets(Point origin, double radiusKm) {
    List<TargetPoint> targets = new ArrayList<>();
    targets.add(new TargetPoint(new Point("TARGET_ORIGIN", origin.name(), origin.address(),
        origin.latitude(), origin.longitude(), PointType.STORE), -1, -1, 0));
    int sectorCount = sectorCount(radiusKm);
    for (int ring = 0; ring < 2; ring++) {
      double ratio = ring == 0 ? .35 : .70;
      for (int sector = 0; sector < sectorCount; sector++) {
        double sectorSize = 360.0 / sectorCount;
        double angle = Math.toRadians(sector * sectorSize + sectorSize / 2);
        double latitude = origin.latitude() + (radiusKm * ratio / 110.574) * Math.cos(angle);
        double longitude = origin.longitude() + (radiusKm * ratio
            / (111.320 * Math.max(.1, Math.cos(Math.toRadians(origin.latitude())))))
            * Math.sin(angle);
        targets.add(new TargetPoint(new Point("TARGET_" + ring + '_' + sector, "", "",
            latitude, longitude, PointType.INTERMEDIATE), ring, sector, sectorCount));
      }
    }
    return List.copyOf(targets);
  }

  private int sectorCount(double radiusKm) {
    int configured = properties.graphGeneration().sectors();
    int required = (int) Math.ceil(2 * Math.PI * radiusKm * .70 / TARGET_ARC_KM);
    return Math.min(MAX_SECTORS, Math.max(configured, required));
  }

  private static boolean allReachable(Graph graph) {
    Set<String> reached = new LinkedHashSet<>();ArrayDeque<String> queue = new ArrayDeque<>();
    reached.add("ORIGIN");queue.add("ORIGIN");
    while (!queue.isEmpty()) {
      String current = queue.remove();
      graph.edges().stream().filter(edge -> edge.from().equals(current)).forEach(edge -> {
        if (reached.add(edge.to())) queue.add(edge.to());
      });
    }
    return reached.size() == graph.points().size();
  }

  private static long outDegree(String id, Map<String, Edge> edges) {
    return edges.values().stream().filter(edge -> edge.from().equals(id)).count();
  }

  private static int sectorDistance(int first, int second, int sectorCount) {
    int difference = Math.abs(first - second);
    return Math.min(difference, sectorCount - difference);
  }

  private static void add(Map<String, Edge> edges, Edge edge) {
    edges.putIfAbsent(edge.from() + '>' + edge.to(), edge);
  }

  private static boolean contains(List<Point> points, String id) {
    return points.stream().anyMatch(point -> point.id().equals(id));
  }

  private static List<AppProperties.BoundaryPoint> circle(Point origin, double radiusKm) {
    List<AppProperties.BoundaryPoint> result = new ArrayList<>();
    for (int degrees = 0; degrees < 360; degrees += 10) {
      double angle = Math.toRadians(degrees);
      double latitude = origin.latitude() + (radiusKm / 110.574) * Math.cos(angle);
      double longitude = origin.longitude() + (radiusKm
          / (111.320 * Math.max(.1, Math.cos(Math.toRadians(origin.latitude())))))
          * Math.sin(angle);
      result.add(new AppProperties.BoundaryPoint(latitude, longitude));
    }
    return List.copyOf(result);
  }

  private record TargetPoint(Point point, int ring, int sector, int sectorCount) {}
  private record RadialPoint(Point point, int ring, int sector, int sourceIndex,
                             int sectorCount, boolean namedRoad) {}
  private record IndexedPoint(Point point, int sourceIndex) {}
}
