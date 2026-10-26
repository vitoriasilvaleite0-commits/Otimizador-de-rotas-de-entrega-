package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicGraphService {
  private final HybridGraphGenerator generator;
  private final LocalTopologyGraphGenerator localGenerator;
  private final AppProperties properties;
  private final Map<String, GeneratedGraphSnapshot> snapshots = new ConcurrentHashMap<>();
  private final Map<String, Definition> definitions = new ConcurrentHashMap<>();

  public DynamicGraphService(HybridGraphGenerator generator,
                             LocalTopologyGraphGenerator localGenerator,
                             AppProperties properties) {
    this.generator = generator;
    this.localGenerator = localGenerator;
    this.properties = properties;
  }

  public GeneratedGraphSnapshot generate(Point requestedOrigin, double radiusKm, boolean forceRefresh) {
    validate(requestedOrigin, radiusKm);
    String id = identifier(requestedOrigin, radiusKm);
    Definition definition = new Definition(requestedOrigin, radiusKm);
    definitions.put(id, definition);
    GeneratedGraphSnapshot current = snapshots.get(id);
    if (!forceRefresh && current != null && current.refreshAfter().isAfter(Instant.now())) return current;
    try {
      GeneratedGraphSnapshot generated = create(id, definition);
      put(id, generated);
      return generated;
    } catch (RuntimeException failure) {
      if (current != null) return current;
      throw failure;
    }
  }

  public GeneratedGraphSnapshot get(String id) {
    GeneratedGraphSnapshot snapshot = snapshots.get(id);
    if (snapshot == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "GRAPH_NOT_FOUND",
          "O grafo dinâmico não está mais disponível. Gere-o novamente.");
    return snapshot;
  }

  @Scheduled(fixedDelayString = "${app.graph-generation.refresh-interval-millis:86400000}")
  public void refreshActiveGraphs() {
    if (!properties.graphGeneration().enabled()) return;
    for (Map.Entry<String, Definition> entry : List.copyOf(definitions.entrySet())) {
      try {
        GeneratedGraphSnapshot refreshed = create(entry.getKey(), entry.getValue());
        snapshots.put(entry.getKey(), refreshed);
      } catch (RuntimeException ignored) {
        // A última versão válida permanece ativa; a indisponibilidade é exposta no próximo pedido manual.
      }
    }
  }

  public int activeGraphs() { return snapshots.size(); }

  private GeneratedGraphSnapshot create(String id, Definition definition) {
    if (properties.demoMode()) {
      return localGenerator.generate(id, definition.origin, definition.radiusKm);
    }
    try {
      return generator.generate(id, definition.origin, definition.radiusKm);
    } catch (RuntimeException externalFailure) {
      if (localGenerator.covers(definition.origin)) {
        return localGenerator.generate(id, definition.origin, definition.radiusKm);
      }
      throw externalFailure;
    }
  }

  private void validate(Point origin, double radiusKm) {
    if (!properties.graphGeneration().enabled())
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DYNAMIC_GRAPH_DISABLED",
          "A geração dinâmica está desativada na configuração.");
    if (origin.type() != PointType.STORE)
      throw new IllegalArgumentException("A origem deve possuir o tipo STORE");
    if (!Double.isFinite(radiusKm) || radiusKm < properties.graphGeneration().minRadiusKm()
        || radiusKm > properties.graphGeneration().maxRadiusKm())
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRAPH_RADIUS",
          "O raio deve estar entre " + properties.graphGeneration().minRadiusKm() + " e "
              + properties.graphGeneration().maxRadiusKm() + " quilômetros.");
  }

  private void put(String id, GeneratedGraphSnapshot snapshot) {
    if (snapshots.size() >= properties.graphGeneration().cacheMaxEntries()
        && !snapshots.containsKey(id)) {
      snapshots.values().stream().min(Comparator.comparing(GeneratedGraphSnapshot::generatedAt))
          .ifPresent(oldest -> { snapshots.remove(oldest.id()); definitions.remove(oldest.id()); });
    }
    snapshots.put(id, snapshot);
  }

  private String identifier(Point origin, double radiusKm) {
    String value = String.format(Locale.ROOT, "%.5f|%.5f|%.2f|v1",
        origin.latitude(), origin.longitude(), radiusKm);
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return "graph-" + HexFormat.of().formatHex(hash, 0, 8);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 indisponível", e);
    }
  }

  private record Definition(Point origin, double radiusKm) {}
}
