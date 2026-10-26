package br.com.rotas.integration;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.Point;
import br.com.rotas.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OverpassService {
  private static final Set<String> DRIVABLE = Set.of(
      "trunk", "primary", "secondary", "tertiary", "unclassified",
      "residential", "living_street");
  private static final Map<String, Integer> ROAD_CLASS = Map.of(
      "trunk", 5, "primary", 5, "secondary", 4, "tertiary", 3,
      "unclassified", 2, "residential", 2, "living_street", 1);

  private final AppProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;

  @Autowired
  public OverpassService(AppProperties properties, ObjectMapper mapper) {
    this(properties, mapper, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(properties.external().overpass().timeoutSeconds()))
        .build());
  }

  OverpassService(AppProperties properties, ObjectMapper mapper, HttpClient client) {
    this.properties = properties;
    this.mapper = mapper;
    this.client = client;
  }

  public List<RoadCandidate> intersections(Point origin, double radiusKm) {
    String query = buildQuery(origin, radiusKm);
    var cfg = properties.external().overpass();
    HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.baseUrl()))
        .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
        .header("User-Agent", properties.external().nominatim().userAgent())
        .header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .POST(HttpRequest.BodyPublishers.ofString("data="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)))
        .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw unavailable("A malha viária não pôde ser consultada no Overpass.");
      return parse(response.body());
    } catch (ApiException e) {
      throw e;
    } catch (HttpTimeoutException e) {
      throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "OVERPASS_TIMEOUT",
          "A geração do grafo demorou demais. O grafo anterior continua disponível.");
    } catch (Exception e) {
      throw unavailable("Não foi possível consultar a topologia do OpenStreetMap.");
    }
  }

  String buildQuery(Point origin, double radiusKm) {
    int sectors = properties.graphGeneration().sectors();
    double sampleRadiusKm = Math.max(.7, radiusKm * .15);
    StringBuilder query = new StringBuilder("[out:json][timeout:")
        .append(properties.external().overpass().timeoutSeconds()).append("];")
        .append('(');
    for (double ringRatio : List.of(.35, .70)) {
      for (int sector = 0; sector < sectors; sector++) {
        double bearing = (sector + .5) * (360.0 / sectors);
        NodeCoordinate target = project(origin.latitude(), origin.longitude(),
            radiusKm * ringRatio, bearing);
        BoundingBox box = BoundingBox.around(target.latitude, target.longitude, sampleRadiusKm);
        query.append("way[\"highway\"~\"")
            .append("^(trunk|primary|secondary|tertiary|unclassified|residential|living_street)$")
            .append("\"](").append(box.south).append(',').append(box.west).append(',')
            .append(box.north).append(',').append(box.east).append(");");
      }
    }
    return query.append(");(._;>;);out body;").toString();
  }

  List<RoadCandidate> parse(String body) throws Exception {
    JsonNode elements = mapper.readTree(body).path("elements");
    Map<Long, NodeCoordinate> nodes = new HashMap<>();
    List<Way> ways = new ArrayList<>();
    for (JsonNode element : elements) {
      String type = element.path("type").asText();
      if ("node".equals(type)) {
        nodes.put(element.path("id").asLong(), new NodeCoordinate(
            element.path("lat").asDouble(), element.path("lon").asDouble()));
      } else if ("way".equals(type)) {
        JsonNode tags = element.path("tags");
        if (!isDrivable(tags)) continue;
        List<Long> nodeIds = new ArrayList<>();
        element.path("nodes").forEach(node -> nodeIds.add(node.asLong()));
        ways.add(new Way(element.path("id").asLong(), nodeIds,
            tags.path("name").asText(""), tags.path("highway").asText()));
      }
    }

    Map<Long, Junction> junctions = new HashMap<>();
    for (Way way : ways) {
      for (Long nodeId : way.nodes) {
        Junction junction = junctions.computeIfAbsent(nodeId, ignored -> new Junction());
        junction.wayIds.add(way.id);
        if (!way.name.isBlank()) junction.names.add(way.name);
        junction.roadClassScore = Math.max(junction.roadClassScore,
            ROAD_CLASS.getOrDefault(way.highway, 1));
      }
    }

    List<RoadCandidate> result = new ArrayList<>();
    for (Map.Entry<Long, Junction> entry : junctions.entrySet()) {
      Junction junction = entry.getValue();
      if (junction.wayIds.size() < 2
          || (junction.wayIds.size() < 3 && junction.names.size() < 2)) continue;
      NodeCoordinate coordinate = nodes.get(entry.getKey());
      if (coordinate == null) continue;
      result.add(new RoadCandidate(entry.getKey(), coordinate.latitude, coordinate.longitude,
          junction.names.stream().sorted().toList(), junction.wayIds.size(),
          junction.roadClassScore));
    }
    result.sort(java.util.Comparator.comparingLong(RoadCandidate::osmId));
    return List.copyOf(result);
  }

  private boolean isDrivable(JsonNode tags) {
    String highway = tags.path("highway").asText();
    if (!DRIVABLE.contains(highway)) return false;
    String access = tags.path("access").asText();
    String motorVehicle = tags.path("motor_vehicle").asText();
    String service = tags.path("service").asText();
    return !Set.of("private", "no").contains(access)
        && !Set.of("private", "no").contains(motorVehicle)
        && !Set.of("parking_aisle", "driveway").contains(service);
  }

  private ApiException unavailable(String message) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OVERPASS_UNAVAILABLE", message);
  }

  private static NodeCoordinate project(double latitude, double longitude,
                                        double distanceKm, double bearingDegrees) {
    double angle = Math.toRadians(bearingDegrees);
    double targetLatitude = latitude + (distanceKm / 110.574) * Math.cos(angle);
    double targetLongitude = longitude + (distanceKm
        / (111.320 * Math.max(.1, Math.cos(Math.toRadians(latitude))))) * Math.sin(angle);
    return new NodeCoordinate(targetLatitude, targetLongitude);
  }

  private static final class Junction {
    private final Set<Long> wayIds = new LinkedHashSet<>();
    private final Set<String> names = new LinkedHashSet<>();
    private int roadClassScore;
  }

  private record NodeCoordinate(double latitude, double longitude) {}
  private record Way(long id, List<Long> nodes, String name, String highway) {}

  private record BoundingBox(double south, double west, double north, double east) {
    static BoundingBox around(double latitude, double longitude, double radiusKm) {
      double latitudeDelta = radiusKm / 110.574;
      double longitudeDelta = radiusKm / (111.320 * Math.max(.1,
          Math.cos(Math.toRadians(latitude))));
      return new BoundingBox(latitude - latitudeDelta, longitude - longitudeDelta,
          latitude + latitudeDelta, longitude + longitudeDelta);
    }
  }
}
