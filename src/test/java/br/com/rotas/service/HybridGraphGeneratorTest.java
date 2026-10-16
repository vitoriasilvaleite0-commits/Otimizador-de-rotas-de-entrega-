package br.com.rotas.service;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.AdjacencyMatrix;
import br.com.rotas.domain.GeneratedGraphSnapshot;
import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.exception.ApiException;
import br.com.rotas.integration.OverpassService;
import br.com.rotas.integration.RoadCandidate;
import br.com.rotas.integration.RoadSnapData;
import br.com.rotas.integration.RouteTableData;
import br.com.rotas.integration.RoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridGraphGeneratorTest {
  private final Point origin=new Point("ORIGIN","Origem","Endereço",-23.61,-46.64,PointType.STORE);

  @Test void selectsOneRealIntersectionPerSectorAndRingDeterministically() {
    HybridGraphGenerator generator=generator(candidates(16));
    var first=generator.select(origin,10,candidates(16));
    var second=generator.select(origin,10,candidates(16));
    assertEquals(16,first.size());
    assertEquals(first.stream().map(HybridGraphGenerator.SelectedPoint::osmId).toList(),second.stream().map(HybridGraphGenerator.SelectedPoint::osmId).toList());
    assertEquals(8,first.stream().filter(point->point.ring()==0).count());
    assertEquals(8,first.stream().filter(point->point.ring()==1).count());
    assertEquals(16,first.stream().map(point->point.sector()+":"+point.ring()).distinct().count());
  }

  @Test void generatedGraphIsSparseDirectedAndOffersAlternatives() {
    GeneratedGraphSnapshot snapshot=generator(candidates(16)).generate("graph-test",origin,10);
    assertEquals(17,snapshot.graph().points().size());
    assertTrue(snapshot.graph().edges().size()<17*16);
    assertEquals("OVERPASS_OSRM",snapshot.source());
    for(Point point:snapshot.graph().points().stream().filter(p->p.type()==PointType.INTERMEDIATE).toList()) {
      assertTrue(snapshot.graph().edges().stream().filter(edge->edge.from().equals(point.id())).count()>=2);
      assertTrue(snapshot.graph().edges().stream().filter(edge->edge.to().equals(point.id())).count()>=2);
      assertTrue(new DijkstraService().solve(AdjacencyMatrix.from(snapshot.graph()),"ORIGIN",point.id()).reachable());
    }
    assertTrue(snapshot.graph().edges().stream().anyMatch(edge-> {
      double reverse=snapshot.graph().edges().stream().filter(other->other.from().equals(edge.to())&&other.to().equals(edge.from())).mapToDouble(br.com.rotas.domain.Edge::distanceKm).findFirst().orElse(edge.distanceKm());
      return Math.abs(reverse-edge.distanceKm())>.001;
    }));
  }

  @Test void refusesToInventPointsWhenRoadNetworkIsInsufficient() {
    ApiException error=assertThrows(ApiException.class,()->generator(candidates(7)).generate("graph-test",origin,10));
    assertEquals("INSUFFICIENT_ROAD_POINTS",error.code());
  }

  private HybridGraphGenerator generator(List<RoadCandidate> candidates) {
    AppProperties properties=properties();ObjectMapper mapper=new ObjectMapper();
    OverpassService overpass=new OverpassService(properties,mapper){@Override public List<RoadCandidate> intersections(Point point,double radiusKm){return candidates;}};
    RoutingService routing=new RoutingService(properties,mapper){
      @Override public RoadSnapData snap(Point point){return new RoadSnapData(point.latitude(),point.longitude(),4,"Rua da origem",false);}
      @Override public RouteTableData distanceTable(List<Point> points){double[][] values=new double[points.size()][points.size()];for(int i=0;i<points.size();i++)for(int j=0;j<points.size();j++)values[i][j]=i==j?0:GraphService.haversine(points.get(i),points.get(j))*1.20+(i<j?.02:.06);return new RouteTableData(values,false);}
    };
    return new HybridGraphGenerator(overpass,routing,properties);
  }

  private List<RoadCandidate> candidates(int amount) {
    List<RoadCandidate> result=new ArrayList<>();long id=100;
    for(int ring=0;ring<2&&result.size()<amount;ring++)for(int sector=0;sector<8&&result.size()<amount;sector++){
      double distance=ring==0?3.5:7.0,bearing=sector*45+22.5;
      double angle=Math.toRadians(bearing),lat=origin.latitude()+(distance/110.574)*Math.cos(angle),lon=origin.longitude()+(distance/(111.320*Math.cos(Math.toRadians(origin.latitude()))))*Math.sin(angle);
      result.add(new RoadCandidate(id++,lat,lon,List.of("Avenida "+sector,"Rua "+ring),4,4));
    }
    return result;
  }

  private AppProperties properties(){return new AppProperties(new AppProperties.Store("STORE","Loja","End",origin.latitude(),origin.longitude()),new AppProperties.Region(-90,90,-180,180,"x",List.of()),new AppProperties.External(new AppProperties.External.Nominatim("http://test","test",1,0),new AppProperties.External.Osrm("http://test",1),new AppProperties.External.Overpass("http://test",1)),true,AppProperties.GraphGeneration.defaults());}
}
