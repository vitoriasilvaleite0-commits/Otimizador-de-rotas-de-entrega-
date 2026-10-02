package br.com.rotas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Store store, Region region, External external, boolean demoMode,
                            GraphGeneration graphGeneration) {
  public AppProperties {
    graphGeneration = graphGeneration == null ? GraphGeneration.defaults() : graphGeneration;
  }

  public record Store(String id, String name, String address, double latitude, double longitude) {}
  public record Region(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude, String viewbox, List<BoundaryPoint> boundary) {
    public Region {
      boundary = boundary == null ? List.of() : List.copyOf(boundary);
    }

    public boolean contains(double lat, double lon) {
      if (lat < minLatitude || lat > maxLatitude || lon < minLongitude || lon > maxLongitude) return false;
      if (boundary.size() < 3) return true;
      boolean inside = false;
      for (int i = 0, j = boundary.size() - 1; i < boundary.size(); j = i++) {
        BoundaryPoint a = boundary.get(j), b = boundary.get(i);
        if (onSegment(lat, lon, a, b)) return true;
        boolean crosses = (a.latitude() > lat) != (b.latitude() > lat)
            && lon < (b.longitude() - a.longitude()) * (lat - a.latitude())
            / (b.latitude() - a.latitude()) + a.longitude();
        if (crosses) inside = !inside;
      }
      return inside;
    }

    private static boolean onSegment(double lat, double lon, BoundaryPoint a, BoundaryPoint b) {
      double cross = (lat - a.latitude()) * (b.longitude() - a.longitude())
          - (lon - a.longitude()) * (b.latitude() - a.latitude());
      if (Math.abs(cross) > 1e-10) return false;
      return lat >= Math.min(a.latitude(), b.latitude()) - 1e-10
          && lat <= Math.max(a.latitude(), b.latitude()) + 1e-10
          && lon >= Math.min(a.longitude(), b.longitude()) - 1e-10
          && lon <= Math.max(a.longitude(), b.longitude()) + 1e-10;
    }
  }
  public record BoundaryPoint(double latitude, double longitude) {}
  public record External(Nominatim nominatim, Osrm osrm, Overpass overpass) {
    public record Nominatim(String baseUrl, String userAgent, int timeoutSeconds, long minIntervalMillis) {}
    public record Osrm(String baseUrl, int timeoutSeconds) {}
    public record Overpass(String baseUrl, int timeoutSeconds) {}
  }

  public record GraphGeneration(boolean enabled, double defaultRadiusKm, double minRadiusKm,
                                double maxRadiusKm, int sectors, int rings, int minimumPoints,
                                double minimumSpacingKm, double maxDetourRatio,
                                long refreshIntervalMillis, int cacheMaxEntries) {
    public GraphGeneration {
      if (defaultRadiusKm <= 0 || minRadiusKm <= 0 || maxRadiusKm < minRadiusKm)
        throw new IllegalArgumentException("Raios da geração do grafo são inválidos");
      if (sectors < 4 || rings != 2 || minimumPoints < 4)
        throw new IllegalArgumentException("Configuração de setores, anéis ou pontos é inválida");
      if (minimumSpacingKm < 0 || maxDetourRatio <= 1 || refreshIntervalMillis < 60_000 || cacheMaxEntries < 1)
        throw new IllegalArgumentException("Configuração da geração dinâmica é inválida");
    }

    public static GraphGeneration defaults() {
      return new GraphGeneration(true, 10, 3, 20, 8, 2, 8, .6, 3.5,
          86_400_000, 12);
    }
  }
}
