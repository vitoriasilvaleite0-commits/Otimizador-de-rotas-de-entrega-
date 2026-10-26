package br.com.rotas.integration;

import java.util.Arrays;

public record RouteTableData(double[][] distancesKm, boolean fallback) {
  public RouteTableData {
    distancesKm = Arrays.stream(distancesKm).map(double[]::clone).toArray(double[][]::new);
  }

  @Override public double[][] distancesKm() {
    return Arrays.stream(distancesKm).map(double[]::clone).toArray(double[][]::new);
  }
}
