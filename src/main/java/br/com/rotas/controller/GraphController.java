package br.com.rotas.controller;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.service.DynamicGraphService;
import br.com.rotas.service.GraphService;
import br.com.rotas.service.RouteOptimizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GraphController {
  private final GraphService graphs;
  private final DynamicGraphService dynamicGraphs;
  private final AppProperties props;

  @Autowired
  public GraphController(GraphService graphs, DynamicGraphService dynamicGraphs,
                         AppProperties props) {
    this.graphs = graphs;
    this.dynamicGraphs = dynamicGraphs;
    this.props = props;
  }

  public GraphController(GraphService graphs, AppProperties props) {
    this(graphs, null, props);
  }

  @GetMapping("/graph")
  public GraphResponse graph() {
    Graph graph = graphs.baseGraph();
    return response(graph, null, props.region().boundary(), "GRAPH_JSON", true,
        null, null);
  }

  @PostMapping("/graphs/generate")
  public GraphResponse generate(@Valid @RequestBody GenerateGraphRequest request) {
    if (dynamicGraphs == null) throw new IllegalStateException("Geração dinâmica indisponível");
    Point origin = new Point("ORIGIN", request.name(), request.address(), request.latitude(),
        request.longitude(), PointType.STORE);
    GeneratedGraphSnapshot snapshot = dynamicGraphs.generate(origin, request.radiusKm(),
        request.forceRefresh());
    return response(snapshot.graph(), snapshot.id(), snapshot.serviceArea(), snapshot.source(),
        snapshot.fallbackUsed(), snapshot.generatedAt(), snapshot.refreshAfter());
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("demoMode", props.demoMode());
    status.put("nominatimConfigured", true);
    status.put("osrmConfigured", true);
    status.put("overpassConfigured", true);
    status.put("dynamicGraphEnabled", props.graphGeneration().enabled());
    status.put("localTopologyFallback", true);
    status.put("activeDynamicGraphs", dynamicGraphs == null ? 0 : dynamicGraphs.activeGraphs());
    status.put("message", props.demoMode()
        ? "Geração radial disponível pelo OSRM; se o serviço falhar, a topologia local verificada assume."
        : "Geração híbrida disponível; a topologia local verificada é usada se as APIs falharem.");
    return status;
  }

  private GraphResponse response(Graph graph, String graphId,
                                 List<AppProperties.BoundaryPoint> serviceArea,
                                 String source, boolean fallbackUsed,
                                 Instant generatedAt, Instant refreshAfter) {
    return new GraphResponse(graph, RouteOptimizationService.serialize(AdjacencyMatrix.from(graph)),
        graph.points().stream().map(Point::id).toList(), graphs.demo(), serviceArea, graphId,
        source, fallbackUsed, generatedAt, refreshAfter);
  }

  public record GenerateGraphRequest(@NotBlank @Size(max = 120) String name,
                                     @NotBlank @Size(max = 240) String address,
                                     @DecimalMin("-90") @DecimalMax("90") double latitude,
                                     @DecimalMin("-180") @DecimalMax("180") double longitude,
                                     @Positive double radiusKm,
                                     boolean forceRefresh) {}

  public record GraphResponse(Graph graph, List<List<Double>> matrix,
                              List<String> vertexOrder, GraphService.DemoData demoCustomer,
                              List<AppProperties.BoundaryPoint> serviceArea, String graphId,
                              String graphSource, boolean fallbackUsed,
                              Instant generatedAt, Instant refreshAfter) {}
}
