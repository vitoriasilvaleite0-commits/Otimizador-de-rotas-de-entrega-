package br.com.rotas.domain;
public record Edge(String from, String to, double distanceKm, boolean fallback, java.util.List<double[]> geometry) {
  public Edge { if (!Double.isFinite(distanceKm) || distanceKm < 0) throw new IllegalArgumentException("Peso deve ser não negativo e finito"); geometry = geometry == null ? java.util.List.of() : java.util.List.copyOf(geometry); }
}
