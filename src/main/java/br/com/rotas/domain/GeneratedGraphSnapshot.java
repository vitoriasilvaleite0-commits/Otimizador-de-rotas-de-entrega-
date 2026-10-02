package br.com.rotas.domain;

import br.com.rotas.config.AppProperties;

import java.time.Instant;
import java.util.List;

public record GeneratedGraphSnapshot(String id, Graph graph, Point origin, double radiusKm,
                                     List<AppProperties.BoundaryPoint> serviceArea,
                                     Instant generatedAt, Instant refreshAfter,
                                     boolean fallbackUsed, boolean fixedFallback,
                                     String source) {
  public GeneratedGraphSnapshot {
    serviceArea = List.copyOf(serviceArea);
  }
}
