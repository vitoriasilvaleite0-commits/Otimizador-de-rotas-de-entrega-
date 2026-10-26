package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.OverpassService;
import br.com.rotas.integration.RoadCandidate;
import br.com.rotas.integration.RoadSnapData;
import br.com.rotas.integration.RouteTableData;
import br.com.rotas.integration.RoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HybridGraphGenerator {
  private final OverpassService overpass;
  private final RoutingService routing;
  private final AppProperties properties;

  public HybridGraphGenerator(OverpassService overpass, RoutingService routing,
                              AppProperties properties) {
    this.overpass = overpass;
    this.routing = routing;
    this.properties = properties;
  }

  public GeneratedGraphSnapshot generate(String id, Point requestedOrigin, double radiusKm) {
    RoadSnapData snap = routing.snap(requestedOrigin);
    Point origin = new Point("ORIGIN", requestedOrigin.name(), requestedOrigin.address(),
        snap.latitude(), snap.longitude(), PointType.STORE);
    List<RoadCandidate> candidates = overpass.intersections(origin, radiusKm);
    List<SelectedPoint> selected = select(origin, radiusKm, candidates);
    if (selected.size() < properties.graphGeneration().minimumPoints()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_ROAD_POINTS",
          "A região não possui cruzamentos dirigíveis suficientes para gerar o grafo.");
    }

    List<Point> points = new ArrayList<>();
    points.add(origin);
    selected.stream().map(SelectedPoint::point).forEach(points::add);
    RouteTableData table = routing.distanceTable(points);
    List<Edge> edges = buildEdges(points, selected, table, radiusKm);
    ensureAlternativeDegrees(points, edges, table, radiusKm);
    ensureReachable(points, edges, table, radiusKm);

    Instant generatedAt = Instant.now();
    boolean fallback = snap.fallback() || table.fallback();
    return new GeneratedGraphSnapshot(id, new Graph(points, edges), origin, radiusKm,
        circle(origin, radiusKm), generatedAt,
        generatedAt.plusMillis(properties.graphGeneration().refreshIntervalMillis()),
        fallback, false, fallback ? "OVERPASS_COM_PESOS_FALLBACK" : "OVERPASS_OSRM");
  }

  List<SelectedPoint> select(Point origin, double radiusKm, List<RoadCandidate> candidates) {
    int sectors = properties.graphGeneration().sectors();
    List<CandidatePosition> positioned = candidates.stream()
        .map(candidate -> position(origin, radiusKm, sectors, candidate))
        .filter(position -> position.ring >= 0)
        .toList();
    List<SelectedPoint> selected = new ArrayList<>();
    Set<Long> used = new HashSet<>();
    double minimumSpacing = Math.max(properties.graphGeneration().minimumSpacingKm(), radiusKm * .06);

    for (int ring = 0; ring < 2; ring++) {
      for (int sector = 0; sector < sectors; sector++) {
        final int selectedRing = ring, selectedSector = sector;
        List<CandidatePosition> cell = positioned.stream()
            .filter(position -> position.ring == selectedRing && position.sector == selectedSector)
            .sorted(Comparator.comparingDouble(CandidatePosition::score).reversed()
                .thenComparingLong(position -> position.candidate.osmId()))
            .toList();
        CandidatePosition chosen = cell.stream()
            .filter(position -> !used.contains(position.candidate.osmId()))
            .filter(position -> selected.stream().allMatch(existing ->
                GraphService.haversine(existing.point, asPoint(position.candidate)) >= minimumSpacing))
            .findFirst().orElse(null);
        if (chosen == null) continue;
        used.add(chosen.candidate.osmId());
        String pointId = String.format(Locale.ROOT, "P%02d", selected.size() + 1);
        selected.add(new SelectedPoint(toPoint(pointId, chosen.candidate, ring, sector), ring, sector,
            chosen.candidate.osmId()));
      }
    }
    return List.copyOf(selected);
  }

  private CandidatePosition position(Point origin, double radiusKm, int sectors,
                                     RoadCandidate candidate) {
    Point point = asPoint(candidate);
    double distance = GraphService.haversine(origin, point);
    double ratio = distance / radiusKm;
    int ring = ratio >= .20 && ratio < .50 ? 0 : ratio >= .50 && ratio <= .90 ? 1 : -1;
    double bearing = bearing(origin, point);
    int sector = Math.min(sectors - 1, (int) Math.floor(bearing / (360.0 / sectors)));
    double targetRatio = ring == 0 ? .35 : .70;
    double radialScore = Math.max(0, 1 - Math.abs(ratio - targetRatio) / .20);
    double center = (sector + .5) * (360.0 / sectors);
    double angularDifference = Math.abs(bearing - center);
    angularDifference = Math.min(angularDifference, 360 - angularDifference);
    double angularScore = Math.max(0, 1 - angularDifference / (180.0 / sectors));
    double placement = (radialScore + angularScore) / 2;
    double connectivity = Math.min(1, candidate.connectedWays() / 5.0);
    double roadClass = candidate.roadClassScore() / 5.0;
    double named = candidate.roadNames().isEmpty() ? 0 : 1;
    double score = .40 * connectivity + .25 * placement + .20 * roadClass + .15 * named;
    return new CandidatePosition(candidate, ring, sector, score);
  }

  private List<Edge> buildEdges(List<Point> points, List<SelectedPoint> selected,
                                RouteTableData table, double radiusKm) {
    Set<DirectedPair> pairs = new LinkedHashSet<>();
    List<SelectedPoint> inner = selected.stream().filter(point -> point.ring == 0)
        .sorted(Comparator.comparingInt(SelectedPoint::sector)).toList();
    List<SelectedPoint> outer = selected.stream().filter(point -> point.ring == 1)
        .sorted(Comparator.comparingInt(SelectedPoint::sector)).toList();

    List<SelectedPoint> originConnections = inner.stream().filter(point -> point.sector % 2 == 0)
        .limit(4).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    for (SelectedPoint candidate : inner) {
      if (originConnections.size() >= Math.min(4, inner.size())) break;
      if (!originConnections.contains(candidate)) originConnections.add(candidate);
    }
    for (SelectedPoint point : originConnections) {
      int index = points.indexOf(point.point);
      pairs.add(new DirectedPair(0, index));
      pairs.add(new DirectedPair(index, 0));
    }
    addRingPairs(points, inner, pairs);
    addRingPairs(points, outer, pairs);
    for (SelectedPoint point : selected) {
      List<SelectedPoint> otherRing = point.ring == 0 ? outer : inner;
      otherRing.stream().min(Comparator.comparingInt(candidate ->
          circularSectorDistance(point.sector, candidate.sector,
              properties.graphGeneration().sectors()))).ifPresent(counterpart -> {
                int from = points.indexOf(point.point), to = points.indexOf(counterpart.point);
                pairs.add(new DirectedPair(from, to));
                pairs.add(new DirectedPair(to, from));
              });
    }
    return edgesForPairs(points, pairs, table, radiusKm);
  }

  private void addRingPairs(List<Point> points, List<SelectedPoint> ring,
                            Set<DirectedPair> pairs) {
    if (ring.size() < 2) return;
    for (int i = 0; i < ring.size(); i++) {
      int current = points.indexOf(ring.get(i).point);
      int previous = points.indexOf(ring.get((i - 1 + ring.size()) % ring.size()).point);
      int next = points.indexOf(ring.get((i + 1) % ring.size()).point);
      pairs.add(new DirectedPair(current, previous));
      pairs.add(new DirectedPair(current, next));
    }
  }

  private List<Edge> edgesForPairs(List<Point> points, Set<DirectedPair> pairs,
                                   RouteTableData table, double radiusKm) {
    double[][] distances = table.distancesKm();
    List<Edge> edges = new ArrayList<>();
    for (DirectedPair pair : pairs) {
      if (!validConnection(points, distances, pair.from, pair.to, radiusKm)) continue;
      edges.add(new Edge(points.get(pair.from).id(), points.get(pair.to).id(),
          distances[pair.from][pair.to], table.fallback(), List.of()));
    }
    return edges;
  }

  private void ensureReachable(List<Point> points, List<Edge> edges,
                               RouteTableData table, double radiusKm) {
    double[][] distances = table.distancesKm();
    Set<Integer> reachable = reachable(points, edges);
    while (reachable.size() < points.size()) {
      DirectedPair bridge = null;
      double best = Double.POSITIVE_INFINITY;
      for (int from : reachable) {
        for (int to = 1; to < points.size(); to++) {
          if (reachable.contains(to) || !validConnection(points, distances, from, to, radiusKm)) continue;
          if (distances[from][to] < best) {
            best = distances[from][to];
            bridge = new DirectedPair(from, to);
          }
        }
      }
      if (bridge == null) break;
      edges.add(new Edge(points.get(bridge.from).id(), points.get(bridge.to).id(), best,
          table.fallback(), List.of()));
      if (validConnection(points, distances, bridge.to, bridge.from, radiusKm)) {
        edges.add(new Edge(points.get(bridge.to).id(), points.get(bridge.from).id(),
            distances[bridge.to][bridge.from], table.fallback(), List.of()));
      }
      reachable = reachable(points, edges);
    }
    if (reachable.size() < points.size()) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GENERATED_GRAPH_DISCONNECTED",
          "A malha consultada não produziu um grafo alcançável a partir da origem.");
    }
  }

  private void ensureAlternativeDegrees(List<Point> points, List<Edge> edges,
                                        RouteTableData table, double radiusKm) {
    double[][] distances = table.distancesKm();
    for (int from = 0; from < points.size(); from++) {
      int minimum = from == 0 ? Math.min(3, points.size() - 1) : Math.min(2, points.size() - 1);
      while (outDegree(points.get(from).id(), edges) < minimum) {
        int candidate = bestMissingDestination(points, edges, distances, from, radiusKm);
        if (candidate < 0) break;
        edges.add(new Edge(points.get(from).id(), points.get(candidate).id(),
            distances[from][candidate], table.fallback(), List.of()));
      }
    }
    for (int to = 1; to < points.size(); to++) {
      while (inDegree(points.get(to).id(), edges) < Math.min(2, points.size() - 1)) {
        int candidate = bestMissingOrigin(points, edges, distances, to, radiusKm);
        if (candidate < 0) break;
        edges.add(new Edge(points.get(candidate).id(), points.get(to).id(),
            distances[candidate][to], table.fallback(), List.of()));
      }
    }
  }

  private int bestMissingDestination(List<Point> points, List<Edge> edges, double[][] distances,
                                     int from, double radiusKm) {
    int best = -1;double bestDistance = Double.POSITIVE_INFINITY;
    for (int to = 0; to < points.size(); to++) {
      if (from == to || hasEdge(points.get(from).id(), points.get(to).id(), edges)
          || !validConnection(points, distances, from, to, radiusKm)) continue;
      if (distances[from][to] < bestDistance) { bestDistance = distances[from][to];best = to; }
    }
    return best;
  }

  private int bestMissingOrigin(List<Point> points, List<Edge> edges, double[][] distances,
                                int to, double radiusKm) {
    int best = -1;double bestDistance = Double.POSITIVE_INFINITY;
    for (int from = 0; from < points.size(); from++) {
      if (from == to || hasEdge(points.get(from).id(), points.get(to).id(), edges)
          || !validConnection(points, distances, from, to, radiusKm)) continue;
      if (distances[from][to] < bestDistance) { bestDistance = distances[from][to];best = from; }
    }
    return best;
  }

  private static long outDegree(String id, List<Edge> edges) {
    return edges.stream().filter(edge -> edge.from().equals(id)).count();
  }

  private static long inDegree(String id, List<Edge> edges) {
    return edges.stream().filter(edge -> edge.to().equals(id)).count();
  }

  private static boolean hasEdge(String from, String to, List<Edge> edges) {
    return edges.stream().anyMatch(edge -> edge.from().equals(from) && edge.to().equals(to));
  }

  private Set<Integer> reachable(List<Point> points, List<Edge> edges) {
    Set<Integer> reached = new LinkedHashSet<>();
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    reached.add(0);queue.add(0);
    while (!queue.isEmpty()) {
      String from = points.get(queue.remove()).id();
      for (Edge edge : edges) {
        if (!edge.from().equals(from)) continue;
        int next = indexOf(points, edge.to());
        if (reached.add(next)) queue.add(next);
      }
    }
    return reached;
  }

  private boolean validConnection(List<Point> points, double[][] distances,
                                  int from, int to, double radiusKm) {
    double road = distances[from][to];
    if (!Double.isFinite(road) || road <= 0 || road > radiusKm * 1.15) return false;
    double geographic = GraphService.haversine(points.get(from), points.get(to));
    return geographic < .4 || road / geographic <= properties.graphGeneration().maxDetourRatio();
  }

  private List<AppProperties.BoundaryPoint> circle(Point origin, double radiusKm) {
    List<AppProperties.BoundaryPoint> result = new ArrayList<>();
    for (int degrees = 0; degrees < 360; degrees += 10) {
      double angle = Math.toRadians(degrees);
      double latitude = origin.latitude() + (radiusKm / 110.574) * Math.cos(angle);
      double longitude = origin.longitude() + (radiusKm
          / (111.320 * Math.max(.1, Math.cos(Math.toRadians(origin.latitude()))))) * Math.sin(angle);
      result.add(new AppProperties.BoundaryPoint(latitude, longitude));
    }
    return List.copyOf(result);
  }

  private Point toPoint(String id, RoadCandidate candidate, int ring, int sector) {
    String name = candidate.roadNames().isEmpty()
        ? String.format(Locale.ROOT, "Ponto %s %02d", ring == 0 ? "interno" : "externo", sector + 1)
        : String.join(" × ", candidate.roadNames().stream().limit(2).toList());
    return new Point(id, name, "Cruzamento viário obtido do OpenStreetMap",
        candidate.latitude(), candidate.longitude(), PointType.INTERMEDIATE);
  }

  private static Point asPoint(RoadCandidate candidate) {
    return new Point("OSM_" + candidate.osmId(), "", "", candidate.latitude(),
        candidate.longitude(), PointType.INTERMEDIATE);
  }

  private static int indexOf(List<Point> points, String id) {
    for (int i = 0; i < points.size(); i++) if (points.get(i).id().equals(id)) return i;
    return -1;
  }

  private static double bearing(Point from, Point to) {
    double latitude1 = Math.toRadians(from.latitude()), latitude2 = Math.toRadians(to.latitude());
    double longitude = Math.toRadians(to.longitude() - from.longitude());
    double y = Math.sin(longitude) * Math.cos(latitude2);
    double x = Math.cos(latitude1) * Math.sin(latitude2)
        - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longitude);
    return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
  }

  private static int circularSectorDistance(int first, int second, int sectors) {
    int difference = Math.abs(first - second);
    return Math.min(difference, sectors - difference);
  }

  record SelectedPoint(Point point, int ring, int sector, long osmId) {}
  private record CandidatePosition(RoadCandidate candidate, int ring, int sector, double score) {}
  private record DirectedPair(int from, int to) {}
}
