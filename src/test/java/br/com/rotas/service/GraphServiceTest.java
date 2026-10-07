package br.com.rotas.service;
import br.com.rotas.config.AppProperties;import br.com.rotas.domain.*;import br.com.rotas.exception.ApiException;import br.com.rotas.integration.*;import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class GraphServiceTest {
  AppProperties props(){return new AppProperties(new AppProperties.Store("STORE","Loja","End",-23.61562,-46.63918),new AppProperties.Region(-23.78,-23.55,-46.75,-46.58,"-46.75,-23.55,-46.58,-23.78",List.of()),new AppProperties.External(new AppProperties.External.Nominatim("http://localhost","test",1,0),new AppProperties.External.Osrm("http://localhost",1),new AppProperties.External.Overpass("http://localhost",1)),true,AppProperties.GraphGeneration.defaults());}
  GraphService service(){RoutingService r=new RoutingService(props(),new ObjectMapper()){@Override public RouteData route(Point a,Point b,double km){return new RouteData(km,100,java.util.List.of(new double[]{a.latitude(),a.longitude()},new double[]{b.latitude(),b.longitude()}),true);}};return new GraphService(new ObjectMapper(),r,props());}
  @Test void buildsMatrixWithInfinityAndDiagonal(){var g=service().baseGraph();var m=AdjacencyMatrix.from(g);assertEquals(0,m.get("STORE","STORE"));assertTrue(Double.isInfinite(m.get("STORE","BROOKLIN")));assertEquals(.45,m.get("STORE","SAUDE"));}
  @Test void addsCustomerAndSixDirectedEdges(){var s=service();var d=s.demo();var g=s.connectCustomer(new Point("CLIENT",d.name,d.address,d.latitude,d.longitude,PointType.CUSTOMER));assertEquals(s.baseGraph().points().size()+1,g.points().size());assertEquals(s.baseGraph().edges().size()+6,g.edges().size());}
  @Test void selectsThreeNearestIntermediates(){var s=service();var d=s.demo();var p=new Point("C","C","C",d.latitude,d.longitude,PointType.CUSTOMER);var nearest=s.nearest(p,3);assertEquals(List.of("INTERLAGOS","AUTODROMO","CIDADE_DUTRA"),nearest.stream().map(Point::id).toList());}
  @Test void generatedGraphCanReachDemoCustomer(){var s=service();var d=s.demo();var graph=s.connectCustomer(new Point("CLIENT",d.name,d.address,d.latitude,d.longitude,PointType.CUSTOMER));assertTrue(new DijkstraService().solve(AdjacencyMatrix.from(graph),"STORE","CLIENT").reachable());}
  @Test void graphSeparatesNeighborhoodsFromStations(){var graph=service().baseGraph();var ids=graph.points().stream().map(Point::id).toList();assertEquals(22,ids.size());assertTrue(ids.containsAll(List.of("ESTACAO_SOCORRO","VELEIROS","JURUBATUBA","CAMPO_GRANDE","INTERLAGOS","AUTODROMO","CIDADE_DUTRA","PRIMAVERA_INTERLAGOS","CAPELA_SOCORRO","GRAJAU")));assertEquals("Interlagos",point(graph,"INTERLAGOS").name());assertEquals("Estação Autódromo",point(graph,"AUTODROMO").name());assertEquals("Cidade Dutra",point(graph,"CIDADE_DUTRA").name());assertEquals("Estação Primavera-Interlagos",point(graph,"PRIMAVERA_INTERLAGOS").name());assertEquals("Veleiros",point(graph,"VELEIROS").name());assertEquals("Estação Socorro",point(graph,"ESTACAO_SOCORRO").name());}
  @Test void southernCustomerConnectsToThreeLocalVertices(){var s=service();var customer=new Point("C","C","Interlagos",-23.70182,-46.69692,PointType.CUSTOMER);var nearest=s.nearest(customer,3);assertEquals(List.of("INTERLAGOS","AUTODROMO","CIDADE_DUTRA"),nearest.stream().map(Point::id).toList());assertTrue(nearest.stream().allMatch(p->GraphService.haversine(customer,p)<3));var graph=s.connectCustomer(customer);assertEquals(6,graph.edges().stream().filter(e->e.from().equals("C")||e.to().equals("C")).count());}
  @Test void campoGrandeUsesVerifiedRoadAccessInsteadOfShoppingCentroid(){var point=service().baseGraph().points().stream().filter(p->p.id().equals("CAMPO_GRANDE")).findFirst().orElseThrow();assertEquals("Acesso viário ao Shopping Interlagos",point.name());assertEquals(-23.6780783,point.latitude(),1e-9);assertEquals(-46.6761105,point.longitude(),1e-9);}
  @Test void plazaSulAndJabaquaraUseVerifiedPublicRoadCorridors(){var graph=service().baseGraph();var shopping=graph.points().stream().filter(p->p.id().equals("SHOPPING")).findFirst().orElseThrow();var jabaquara=graph.points().stream().filter(p->p.id().equals("JABAQUARA")).findFirst().orElseThrow();assertEquals("Corredor viário próximo ao Shopping Plaza Sul",shopping.name());assertEquals(-23.6205364,shopping.latitude(),1e-9);assertEquals(-46.6280997,shopping.longitude(),1e-9);assertEquals("Corredor viário próximo ao Terminal Jabaquara",jabaquara.name());assertEquals(-23.6459641,jabaquara.latitude(),1e-9);assertEquals(-46.6407524,jabaquara.longitude(),1e-9);}
  @Test void dijkstraComparesTwoSouthernAlternativesAndChoosesShorter(){var graph=service().baseGraph();var matrix=AdjacencyMatrix.from(graph);var result=new DijkstraService().solve(matrix,"STORE","INTERLAGOS");assertEquals(List.of("STORE","SHOPPING","JABAQUARA","CAMPO_GRANDE","INTERLAGOS"),result.path());assertEquals(19.87,result.totalDistanceKm(),1e-9);double alternative=distance(matrix,List.of("STORE","SHOPPING","MOEMA","BROOKLIN","SANTO_AMARO","ESTACAO_SOCORRO","JURUBATUBA","INTERLAGOS"));assertEquals(29.20,alternative,1e-9);assertTrue(result.totalDistanceKm()<alternative);}
  @Test void dynamicGraphValidatesItsOwnRadiusAndConnectsThreeNearbyPoints(){var origin=new Point("ORIGIN","Origem","End",-23.61,-46.64,PointType.STORE);var points=List.of(origin,new Point("A","A","A",-23.62,-46.64,PointType.INTERMEDIATE),new Point("B","B","B",-23.61,-46.65,PointType.INTERMEDIATE),new Point("C","C","C",-23.60,-46.64,PointType.INTERMEDIATE));var graph=new Graph(points,List.of(new Edge("ORIGIN","A",1,true,List.of()),new Edge("ORIGIN","B",1,true,List.of()),new Edge("ORIGIN","C",1,true,List.of())));var snapshot=new GeneratedGraphSnapshot("g",graph,origin,5,List.of(),java.time.Instant.now(),java.time.Instant.now().plusSeconds(60),true,false,"TEST");var connected=service().connectCustomer(snapshot,new Point("CLIENT","Cliente","End",-23.615,-46.645,PointType.CUSTOMER));assertEquals(graph.edges().size()+6,connected.edges().size());ApiException error=assertThrows(ApiException.class,()->service().connectCustomer(snapshot,new Point("FAR","Cliente","End",-23.8,-46.8,PointType.CUSTOMER)));assertEquals("OUTSIDE_DYNAMIC_REGION",error.code());}
  @Test void dynamicCustomerConnectionsRejectAnExcessiveNearbyDetour(){
    AppProperties properties=props();
    RoutingService routing=new RoutingService(properties,new ObjectMapper()){
      @Override public RouteTableData distanceTable(List<Point> points){
        double[][] values=new double[points.size()][points.size()];
        for(int i=0;i<values.length;i++)for(int j=0;j<values.length;j++)values[i][j]=i==j?0:4;
        values[1][0]=10;
        for(int index=2;index<values.length;index++){
          values[1][index]=index==2?12:index;
          values[index][0]=index==2?5:Math.max(1,10-index);
          values[0][index]=values[index][0]+.2;
          values[index][1]=values[1][index]+.2;
        }
        return new RouteTableData(values,false);
      }
    };
    GraphService service=new GraphService(new ObjectMapper(),routing,properties);
    Point origin=new Point("ORIGIN","Origem","End",-23.65,-46.65,PointType.STORE);
    List<Point> points=new java.util.ArrayList<>();points.add(origin);
    for(int index=0;index<8;index++)points.add(new Point("P"+index,"P"+index,"Rua",-23.651-index*.002,-46.65+(index%2==0?.001:-.001),PointType.INTERMEDIATE));
    Graph graph=new Graph(points,List.of());java.time.Instant now=java.time.Instant.now();
    GeneratedGraphSnapshot snapshot=new GeneratedGraphSnapshot("quality",graph,origin,20,List.of(),now,now.plusSeconds(60),false,false,"OSRM_RADIAL_DYNAMIC");

    Graph connected=service.connectCustomer(snapshot,new Point("CLIENT","Cliente","End",-23.65,-46.651,PointType.CUSTOMER));

    List<String> incoming=connected.edges().stream().filter(edge->edge.to().equals("CLIENT"))
        .map(Edge::from).toList();
    assertEquals(3,incoming.size());assertFalse(incoming.contains("P0"));
    assertEquals(6,connected.edges().stream().filter(edge->edge.from().equals("CLIENT")||edge.to().equals("CLIENT")).count());
  }
  @Test void addsDirectSafetyConnectionWhenIntermediateAlternativesAreAllInefficient(){
    AppProperties properties=props();
    RoutingService routing=new RoutingService(properties,new ObjectMapper()){
      @Override public RouteTableData distanceTable(List<Point> points){
        double[][] values=new double[points.size()][points.size()];
        for(int i=0;i<values.length;i++)for(int j=0;j<values.length;j++)values[i][j]=i==j?0:8;
        values[1][0]=10;values[0][1]=10.5;
        for(int index=2;index<values.length;index++){
          values[1][index]=6+index*.1;values[index][0]=6.5;
          values[0][index]=6.6;values[index][1]=6.2;
        }
        return new RouteTableData(values,false);
      }
    };
    GraphService service=new GraphService(new ObjectMapper(),routing,properties);
    Point origin=new Point("ORIGIN","Origem","End",-23.65,-46.65,PointType.STORE);
    List<Point> points=new java.util.ArrayList<>();points.add(origin);
    for(int index=0;index<8;index++)points.add(new Point("P"+index,"P"+index,"Rua",
        -23.651-index*.002,-46.65+(index%2==0?.001:-.001),PointType.INTERMEDIATE));
    Graph graph=new Graph(points,List.of());java.time.Instant now=java.time.Instant.now();
    GeneratedGraphSnapshot snapshot=new GeneratedGraphSnapshot("direct-safety",graph,origin,20,
        List.of(),now,now.plusSeconds(60),false,false,"OSRM_RADIAL_DYNAMIC");

    Graph connected=service.connectCustomer(snapshot,
        new Point("CLIENT","Cliente","End",-23.65,-46.651,PointType.CUSTOMER));

    assertEquals(10,AdjacencyMatrix.from(connected).get("ORIGIN","CLIENT"),1e-9);
    assertEquals(List.of("ORIGIN","CLIENT"),new DijkstraService()
        .solve(AdjacencyMatrix.from(connected),"ORIGIN","CLIENT").path());
  }
  private Point point(Graph graph,String id){return graph.points().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();}
  private double distance(AdjacencyMatrix matrix,List<String> path){double total=0;for(int i=0;i<path.size()-1;i++)total+=matrix.get(path.get(i),path.get(i+1));return total;}
}
