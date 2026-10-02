package br.com.rotas.integration;

import java.util.Arrays;
import java.util.List;

public record SnappedRouteTableData(List<RoadSnapData> snappedPoints,
                                    double[][] distancesKm,
                                    boolean fallback) {
  public SnappedRouteTableData {
    snappedPoints = List.copyOf(snappedPoints);
    distancesKm = Arrays.stream(distancesKm).map(double[]::clone).toArray(double[][]::new);
  }

  @Override
  public double[][] distancesKm() {
    return Arrays.stream(distancesKm).map(double[]::clone).toArray(double[][]::new);
  }
}
