package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.*;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteOptimizationServiceTest {
  @Test void dijkstraChoosesPathAndSegmentsFollowItExactly() {
    GraphService graphs=graphs(); CountingDijkstra dijkstra=new CountingDijkstra();
    var demo=graphs.demo(); Point customer=new Point("CLIENT",demo.name,demo.address,demo.latitude,demo.longitude,PointType.CUSTOMER);
    var result=new RouteOptimizationService(graphs,dijkstra).optimize(customer);
    assertTrue(dijkstra.called,"O serviço deve delegar a escolha do caminho ao Dijkstra manual");
    assertEquals(result.dijkstra().path().size()-1,result.segments().size());
    double sum=0;
    for(int i=0;i<result.segments().size();i++) {
      Edge edge=result.segments().get(i); String from=result.dijkstra().path().get(i),to=result.dijkstra().path().get(i+1);
      assertEquals(from,edge.from()); assertEquals(to,edge.to()); sum+=edge.distanceKm();
    }
    assertEquals(result.dijkstra().totalDistanceKm(),sum,1e-9);
  }
  @Test void matrixContainsOnlyEdgeDistancesInKilometers() {
    GraphService graphs=graphs();var demo=graphs.demo();Point customer=new Point("CLIENT",demo.name,demo.address,demo.latitude,demo.longitude,PointType.CUSTOMER);
    Graph graph=graphs.connectCustomer(customer);AdjacencyMatrix matrix=AdjacencyMatrix.from(graph);
    for(Edge edge:graph.edges())assertEquals(edge.distanceKm(),matrix.get(edge.from(),edge.to()),1e-9);
  }
  @Test void interlagosClientHasAlternativesAndDijkstraChoosesTheShortest() {
    GraphService graphs=graphs();var demo=graphs.demo();Point customer=new Point("CLIENT",demo.name,demo.address,demo.latitude,demo.longitude,PointType.CUSTOMER);
    var result=new RouteOptimizationService(graphs,new DijkstraService()).optimize(customer);
    assertEquals(List.of("STORE","SHOPPING","JABAQUARA","CAMPO_GRANDE","INTERLAGOS","CLIENT"),result.dijkstra().path());
    assertEquals(20.77,result.dijkstra().totalDistanceKm(),1e-9);
    AdjacencyMatrix matrix=AdjacencyMatrix.from(result.graph());
    double viaEstacaoAutodromo=pathDistance(matrix,List.of("STORE","SHOPPING","JABAQUARA","CAMPO_GRANDE","AUTODROMO","CLIENT"));
    double viaCidadeDutra=pathDistance(matrix,List.of("STORE","SHOPPING","JABAQUARA","CAMPO_GRANDE","INTERLAGOS","CIDADE_DUTRA","CLIENT"));
    double viaSantoAmaro=pathDistance(matrix,List.of("STORE","SHOPPING","MOEMA","BROOKLIN","SANTO_AMARO","ESTACAO_SOCORRO","JURUBATUBA","INTERLAGOS","CLIENT"));
    assertEquals(21.43,viaEstacaoAutodromo,1e-9);assertEquals(24.14,viaCidadeDutra,1e-9);assertEquals(30.10,viaSantoAmaro,1e-9);
    assertTrue(result.dijkstra().totalDistanceKm()<viaEstacaoAutodromo);assertTrue(result.dijkstra().totalDistanceKm()<viaCidadeDutra);assertTrue(result.dijkstra().totalDistanceKm()<viaSantoAmaro);
  }
  @Test void roadGeometryDoesNotChangeWeightsOrPathChosenByDijkstra() {
    AppProperties p=properties();
    List<List<String>> geometryOrders=new ArrayList<>();RoutingService routing=new RoutingService(p,new ObjectMapper()){@Override public RouteData routeThrough(List<Point> points,double km){geometryOrders.add(points.stream().map(Point::id).toList());Point a=points.getFirst(),b=points.getLast();double midLat=(a.latitude()+b.latitude())/2+.001,midLon=(a.longitude()+b.longitude())/2+.001;return new RouteData(999,100,List.of(new double[]{a.latitude(),a.longitude()},new double[]{midLat,midLon},new double[]{b.latitude(),b.longitude()}),false);}};
    GraphService graphs=new GraphService(new ObjectMapper(),routing,p);var demo=graphs.demo();Point customer=new Point("CLIENT",demo.name,demo.address,demo.latitude,demo.longitude,PointType.CUSTOMER);
    var result=new RouteOptimizationService(graphs,new DijkstraService()).optimize(customer);
    assertEquals(List.of("STORE","SHOPPING","JABAQUARA","CAMPO_GRANDE","INTERLAGOS","CLIENT"),result.dijkstra().path());assertEquals(20.77,result.dijkstra().totalDistanceKm(),1e-9);
    assertEquals(result.dijkstra().path(),geometryOrders.getFirst());assertEquals(List.of("STORE","CLIENT"),geometryOrders.get(1));assertEquals(3,result.routeGeometry().size());assertTrue(result.segments().stream().noneMatch(e->e.distanceKm()==999));assertEquals(1,result.detourRatio(),1e-9);
  }
  @Test void optimizesAgainstTheDynamicGraphIdentifiedByTheFrontend() {
    AppProperties p=properties();GraphService graphs=graphs();
    Point origin=new Point("ORIGIN","Origem dinâmica","End",-23.61562,-46.63918,PointType.STORE);
    Point first=new Point("P01","Ponto 1","Rua 1",-23.62562,-46.63918,PointType.INTERMEDIATE);
    Point second=new Point("P02","Ponto 2","Rua 2",-23.61562,-46.64918,PointType.INTERMEDIATE);
    Graph dynamicGraph=new Graph(List.of(origin,first,second),List.of(
        new Edge("ORIGIN","P01",1,false,List.of()),new Edge("P01","ORIGIN",1.1,false,List.of()),
        new Edge("ORIGIN","P02",2,false,List.of()),new Edge("P02","ORIGIN",2.1,false,List.of()),
        new Edge("P01","P02",1.4,false,List.of()),new Edge("P02","P01",1.5,false,List.of())));
    Instant now=Instant.now();GeneratedGraphSnapshot snapshot=new GeneratedGraphSnapshot(
        "graph-dynamic",dynamicGraph,origin,10,List.of(),now,now.plusSeconds(3600),false,false,"TEST_DYNAMIC");
    DynamicGraphService registry=new DynamicGraphService(null,null,p){@Override public GeneratedGraphSnapshot get(String id){assertEquals("graph-dynamic",id);return snapshot;}};
    CountingDijkstra dijkstra=new CountingDijkstra();
    Point customer=new Point("CLIENT","Cliente","End",-23.626,-46.63918,PointType.CUSTOMER);
    var result=new RouteOptimizationService(graphs,dijkstra,registry).optimize(customer,"graph-dynamic");
    assertTrue(dijkstra.called);assertEquals("graph-dynamic",result.graphId());
    assertEquals("TEST_DYNAMIC",result.graphSource());assertEquals("ORIGIN",result.origin());
    assertEquals("ORIGIN",result.dijkstra().path().getFirst());assertEquals("CLIENT",result.dijkstra().path().getLast());
  }
  @Test void rejectsAContinuousGeometryWithExcessiveDetour() {
    AppProperties properties=properties();
    RoutingService routing=new RoutingService(properties,new ObjectMapper()){
      @Override public RouteData routeThrough(List<Point> points,double fallbackKm){
        double distance=points.size()==2?10:14;
        Point first=points.getFirst(),last=points.getLast();
        return new RouteData(distance,100,List.of(new double[]{first.latitude(),first.longitude()},new double[]{last.latitude(),last.longitude()}),false);
      }
    };
    GraphService graphs=new GraphService(new ObjectMapper(),routing,properties);
    GraphService.DemoData demo=graphs.demo();
    Point customer=new Point("CLIENT",demo.name,demo.address,demo.latitude,demo.longitude,PointType.CUSTOMER);

    ApiException error=assertThrows(ApiException.class,
        ()->new RouteOptimizationService(graphs,new DijkstraService()).optimize(customer));

    assertEquals("EXCESSIVE_ROUTE_DETOUR",error.code());
  }
  @Test void removesATransitPointThatForcesRoadBacktrackingAndRunsDijkstraAgain() {
    AppProperties properties=properties();
    Point origin=new Point("ORIGIN","Origem","End",-23.6000,-46.6000,PointType.STORE);
    Point bad=new Point("BAD","Ramal sem saída","End",-23.6000,-46.5900,PointType.INTERMEDIATE);
    Point good=new Point("GOOD","Cruzamento","End",-23.5990,-46.5900,PointType.INTERMEDIATE);
    Point customer=new Point("CLIENT","Cliente","End",-23.6000,-46.5800,PointType.CUSTOMER);
    Graph connected=new Graph(List.of(origin,bad,good,customer),List.of(
        new Edge("ORIGIN","BAD",1,false,List.of()),new Edge("BAD","CLIENT",1,false,List.of()),
        new Edge("ORIGIN","GOOD",1.5,false,List.of()),new Edge("GOOD","CLIENT",1.5,false,List.of())));
    RoutingService routing=new RoutingService(properties,new ObjectMapper());
    GraphService graphs=new GraphService(new ObjectMapper(),routing,properties){
      @Override public Graph connectCustomer(GeneratedGraphSnapshot snapshot,Point point){return connected;}
      @Override public RouteData roadGeometryForPath(List<String> path,Graph graph,double fallbackKm){
        if(path.equals(List.of("ORIGIN","BAD","CLIENT")))return new RouteData(2,100,List.of(
            new double[]{-23.6000,-46.6000},new double[]{-23.6000,-46.5950},
            new double[]{-23.6000,-46.5900},new double[]{-23.6000,-46.5950},
            new double[]{-23.6000,-46.5800}),false);
        if(path.equals(List.of("ORIGIN","GOOD","CLIENT")))return new RouteData(3,100,List.of(
            new double[]{-23.6000,-46.6000},new double[]{-23.5990,-46.5900},
            new double[]{-23.6000,-46.5800}),false);
        return new RouteData(2.8,100,List.of(new double[]{-23.6000,-46.6000},
            new double[]{-23.6000,-46.5800}),false);
      }
    };
    Instant now=Instant.now();GeneratedGraphSnapshot snapshot=new GeneratedGraphSnapshot(
        "graph-backtrack",connected,origin,10,List.of(),now,now.plusSeconds(3600),false,false,"TEST_DYNAMIC");
    DynamicGraphService registry=new DynamicGraphService(null,null,properties){
      @Override public GeneratedGraphSnapshot get(String id){return snapshot;}
    };
    CountingDijkstra dijkstra=new CountingDijkstra();

    var result=new RouteOptimizationService(graphs,dijkstra,registry)
        .optimize(customer,"graph-backtrack");

    assertEquals(List.of("ORIGIN","GOOD","CLIENT"),result.dijkstra().path());
    assertEquals(List.of("BAD"),result.rejectedTransitPoints());
    assertTrue(result.graph().points().stream().noneMatch(point->point.id().equals("BAD")));
    assertEquals(2,dijkstra.calls);
  }
  private double pathDistance(AdjacencyMatrix matrix,List<String> path){double total=0;for(int i=0;i<path.size()-1;i++)total+=matrix.get(path.get(i),path.get(i+1));return total;}
  private AppProperties properties(){return new AppProperties(new AppProperties.Store("STORE","Loja","End",-23.61562,-46.63918),new AppProperties.Region(-23.78,-23.55,-46.75,-46.58,"x",List.of()),new AppProperties.External(new AppProperties.External.Nominatim("http://test","test",1,0),new AppProperties.External.Osrm("http://test",1),new AppProperties.External.Overpass("http://test",1)),true,AppProperties.GraphGeneration.defaults());}
  private GraphService graphs(){AppProperties p=properties();RoutingService r=new RoutingService(p,new ObjectMapper()){@Override public RouteData route(Point a,Point b,double km){return new RouteData(km,100,List.of(),true);}@Override public RouteData routeThrough(List<Point> points,double km){return new RouteData(km,100,List.of(),true);}};return new GraphService(new ObjectMapper(),r,p);}
  static class CountingDijkstra extends DijkstraService {boolean called;int calls;@Override public DijkstraResult solve(AdjacencyMatrix matrix,String source,String target){called=true;calls++;return super.solve(matrix,source,target);}}
}
