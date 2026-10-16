package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.Edge;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Graph;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.OverpassService;
import br.com.rotas.integration.RouteData;
import br.com.rotas.integration.RoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicGraphServiceTest {
  @Test void reusesFreshGraphAndRefreshesActiveTopologyAtomically() {
    Fixture fixture=new Fixture();
    Point origin=new Point("O","Origem","End",-23.61562,-46.63918,PointType.STORE);
    GeneratedGraphSnapshot first=fixture.service.generate(origin,10,false);
    GeneratedGraphSnapshot cached=fixture.service.generate(origin,10,false);
    assertSame(first,cached);assertEquals(1,fixture.generator.calls);
    fixture.service.refreshActiveGraphs();
    assertEquals(2,fixture.generator.calls);
    assertNotEquals(first.generatedAt(),fixture.service.get(first.id()).generatedAt());
  }

  @Test void keepsLastValidSnapshotWhenPeriodicRefreshFails() {
    Fixture fixture=new Fixture();Point origin=new Point("O","Origem","End",-23.61562,-46.63918,PointType.STORE);
    GeneratedGraphSnapshot first=fixture.service.generate(origin,10,false);fixture.generator.fail=true;
    fixture.service.refreshActiveGraphs();
    assertEquals(first.generatedAt(),fixture.service.get(first.id()).generatedAt());
  }

  @Test void validatesConfiguredRadius() {
    Fixture fixture=new Fixture();Point origin=new Point("O","Origem","End",-23.61562,-46.63918,PointType.STORE);
    ApiException error=assertThrows(ApiException.class,()->fixture.service.generate(origin,30,false));
    assertEquals("INVALID_GRAPH_RADIUS",error.code());
  }

  static class Fixture {
    final AppProperties properties=properties();final CountingGenerator generator;final DynamicGraphService service;
    Fixture(){ObjectMapper mapper=new ObjectMapper();RoutingService routing=new RoutingService(properties,mapper){@Override public RouteData route(Point a,Point b,double km){return new RouteData(km,1,List.of(),true);}};GraphService fixed=new GraphService(mapper,routing,properties);generator=new CountingGenerator(properties);LocalTopologyGraphGenerator local=new LocalTopologyGraphGenerator(fixed,routing,properties){@Override public boolean covers(Point origin){return false;}};service=new DynamicGraphService(generator,local,properties);}
  }

  static class CountingGenerator extends HybridGraphGenerator {
    int calls;boolean fail;final AppProperties properties;
    CountingGenerator(AppProperties properties){super(new OverpassService(properties,new ObjectMapper()),new RoutingService(properties,new ObjectMapper()),properties);this.properties=properties;}
    @Override public GeneratedGraphSnapshot generate(String id,Point requestedOrigin,double radiusKm){calls++;if(fail)throw new IllegalStateException("falha simulada");Point origin=new Point("ORIGIN",requestedOrigin.name(),requestedOrigin.address(),requestedOrigin.latitude(),requestedOrigin.longitude(),PointType.STORE);Point intermediate=new Point("P01","Ponto","Rua",origin.latitude()+.01,origin.longitude(),PointType.INTERMEDIATE);Instant now=Instant.now().plusNanos(calls);return new GeneratedGraphSnapshot(id,new Graph(List.of(origin,intermediate),List.of(new Edge("ORIGIN","P01",1,false,List.of()),new Edge("P01","ORIGIN",1.1,false,List.of()))),origin,radiusKm,List.of(),now,now.plusMillis(properties.graphGeneration().refreshIntervalMillis()),false,false,"TEST");}
  }

  static AppProperties properties(){return new AppProperties(new AppProperties.Store("STORE","Loja","End",-23.61562,-46.63918),new AppProperties.Region(-90,90,-180,180,"x",List.of()),new AppProperties.External(new AppProperties.External.Nominatim("http://test","test",1,0),new AppProperties.External.Osrm("http://test",1),new AppProperties.External.Overpass("http://test",1)),false,AppProperties.GraphGeneration.defaults());}
}
