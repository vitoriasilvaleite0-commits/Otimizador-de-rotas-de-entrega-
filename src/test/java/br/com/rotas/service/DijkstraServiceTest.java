package br.com.rotas.service;
import br.com.rotas.domain.*;import org.junit.jupiter.api.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class DijkstraServiceTest {
  DijkstraService service=new DijkstraService();
  private AdjacencyMatrix m(String... ids){return new AdjacencyMatrix(List.of(ids));}
  @Test void directPath(){var m=m("A","B");m.set("A","B",2);assertEquals(List.of("A","B"),service.solve(m,"A","B").path());}
  @Test void intermediatePath(){var m=m("A","B","C");m.set("A","B",2);m.set("B","C",3);m.set("A","C",9);assertEquals(5,service.solve(m,"A","C").totalDistanceKm());}
  @Test void updatesKnownDistance(){var m=m("A","B","C");m.set("A","C",10);m.set("A","B",2);m.set("B","C",1);var r=service.solve(m,"A","C");assertEquals(3,r.totalDistanceKm());assertTrue(r.steps().stream().flatMap(s->s.analyzedNeighbors().stream()).anyMatch(x->x.neighbor().equals("C")&&x.updated()&&x.oldDistance()==10));}
  @Test void unreachable(){var r=service.solve(m("A","B"),"A","B");assertFalse(r.reachable());assertTrue(Double.isInfinite(r.totalDistanceKm()));}
  @Test void sameOriginAndTarget(){var r=service.solve(m("A"),"A","A");assertEquals(List.of("A"),r.path());assertEquals(0,r.totalDistanceKm());}
  @Test void asymmetricMatrix(){var m=m("A","B");m.set("A","B",1);assertTrue(service.solve(m,"A","B").reachable());assertFalse(service.solve(m,"B","A").reachable());}
  @Test void decimalWeights(){var m=m("A","B","C");m.set("A","B",1.25);m.set("B","C",.55);assertEquals(1.8,service.solve(m,"A","C").totalDistanceKm(),1e-9);}
  @Test void tieUsesStableVertexOrder(){var m=m("A","B","C","D");m.set("A","B",1);m.set("A","C",1);m.set("B","D",1);m.set("C","D",1);assertEquals(List.of("A","B","D"),service.solve(m,"A","D").path());}
  @Test void rejectsNegativeWeight(){var m=m("A","B");assertThrows(IllegalArgumentException.class,()->m.set("A","B",-1));}
  @Test void reconstructsPredecessors(){var m=m("A","B","C","D");m.set("A","B",1);m.set("B","C",1);m.set("C","D",1);var r=service.solve(m,"A","D");assertEquals(List.of("A","B","C","D"),r.path());assertEquals("C",r.steps().getLast().predecessors().get("D"));}
}
