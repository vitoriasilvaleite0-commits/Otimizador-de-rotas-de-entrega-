package br.com.rotas.service;

import br.com.rotas.config.AppProperties;import br.com.rotas.domain.*;import br.com.rotas.exception.ApiException;import br.com.rotas.integration.*;import com.fasterxml.jackson.databind.ObjectMapper;import org.springframework.core.io.ClassPathResource;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;
import java.io.IOException;import java.util.*;

@Service
public class GraphService {
  private static final int CUSTOMER_CANDIDATE_POOL = 8;
  private static final int CUSTOMER_CONNECTIONS = 3;
  private static final double MAX_CUSTOMER_PATH_RATIO = 1.35;
  private static final double MAX_PREFERRED_INTERMEDIATE_RATIO = 1.15;
  private static final double MIN_BEARING_SEPARATION_DEGREES = 25;
  private final Graph base;private final RoutingService routing;private final AppProperties props;private final DemoData demo;
  public GraphService(ObjectMapper mapper,RoutingService routing,AppProperties props){this.routing=routing;this.props=props;try{ConfigFile f=mapper.readValue(new ClassPathResource("data/graph.json").getInputStream(),ConfigFile.class);List<Point> pts=new ArrayList<>();pts.add(new Point(props.store().id(),props.store().name(),props.store().address(),props.store().latitude(),props.store().longitude(),PointType.STORE));for(NodeConfig n:f.points)pts.add(new Point(n.id,n.name,n.address,n.latitude,n.longitude,PointType.INTERMEDIATE));List<Edge> es=new ArrayList<>();for(NodeConfig n:f.points)for(LinkConfig l:n.links)es.add(new Edge(n.id,l.to,l.fallbackKm,true,List.of()));for(LinkConfig l:f.storeLinks)es.add(new Edge(props.store().id(),l.to,l.fallbackKm,true,List.of()));base=new Graph(pts,es);demo=f.demoCustomer;}catch(IOException|RuntimeException e){throw new IllegalStateException("Arquivo graph.json ausente ou inválido",e);}}
  public Graph baseGraph(){return base;}
  public DemoData demo(){return demo;}
  public Graph connectCustomer(Point customer){
    if(!props.region().contains(customer.latitude(),customer.longitude()))
      throw new ApiException(HttpStatus.BAD_REQUEST,"OUTSIDE_REGION","O cliente está fora da região atendida.");
    return connectCustomer(base,customer,true,false);
  }
  public Graph connectCustomer(GeneratedGraphSnapshot snapshot,Point customer){
    if(snapshot.fixedFallback())return connectCustomer(customer);
    if(haversine(snapshot.origin(),customer)>snapshot.radiusKm())
      throw new ApiException(HttpStatus.BAD_REQUEST,"OUTSIDE_DYNAMIC_REGION","O cliente está fora do raio do grafo gerado.");
    boolean forceLocal=snapshot.source()!=null&&snapshot.source().startsWith("LOCAL_");
    return connectCustomer(snapshot.graph(),customer,false,forceLocal);
  }
  private Graph connectCustomer(Graph source,Point customer,boolean allowDemo,boolean forceLocal){
    if(customer.type()!=PointType.CUSTOMER)throw new IllegalArgumentException("Ponto deve ser cliente");
    List<Point> candidates=source.points().stream().filter(p->p.type()==PointType.INTERMEDIATE)
        .sorted(Comparator.comparingDouble(p->haversine(customer,p)))
        .limit(CUSTOMER_CANDIDATE_POOL).toList();
    if(candidates.size()<2)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_CONNECTION_POINTS","O grafo não possui pontos suficientes para conectar o cliente.");
    List<Point> points=new ArrayList<>(source.points());points.add(customer);List<Edge> edges=new ArrayList<>(source.edges());
    boolean demoMatch=allowDemo&&Math.abs(customer.latitude()-demo.latitude)<.0001&&Math.abs(customer.longitude()-demo.longitude)<.0001;
    boolean useLocal=forceLocal||(demoMatch&&props.demoMode());
    if(useLocal){
      for(Point p:candidates.stream().limit(CUSTOMER_CONNECTIONS).toList()){
        double toFallback=demoMatch&&demo.connectionsToCustomer.containsKey(p.id())?demo.connectionsToCustomer.get(p.id()):Math.max(.2,haversine(customer,p)*1.28);
        double fromFallback=demoMatch&&demo.connectionsFromCustomer.containsKey(p.id())?demo.connectionsFromCustomer.get(p.id()):toFallback*1.05;
        edges.add(new Edge(p.id(),customer.id(),toFallback,true,List.of()));
        edges.add(new Edge(customer.id(),p.id(),fromFallback,true,List.of()));
      }
      return new Graph(points,edges);
    }

    Point origin=source.points().getFirst();
    List<Point> tablePoints=new ArrayList<>();tablePoints.add(customer);tablePoints.add(origin);tablePoints.addAll(candidates);
    RouteTableData table=routing.distanceTable(tablePoints);double[][] distances=table.distancesKm();
    List<CustomerConnection> ranked=new ArrayList<>();double direct=distances[1][0];
    for(int index=0;index<candidates.size();index++){
      Point candidate=candidates.get(index);int tableIndex=index+2;
      double originTo=distances[1][tableIndex],toCustomer=distances[tableIndex][0],fromCustomer=distances[0][tableIndex];
      if(!positiveFinite(originTo)||!positiveFinite(toCustomer)||!positiveFinite(fromCustomer))continue;
      double complete=originTo+toCustomer;
      double ratio=positiveFinite(direct)?complete/direct:1;
      ranked.add(new CustomerConnection(candidate,tableIndex,complete,ratio,bearing(customer,candidate)));
    }
    ranked.sort(Comparator.comparingDouble(CustomerConnection::completeDistanceKm));
    List<CustomerConnection> acceptable=ranked.stream()
        .filter(connection->connection.pathRatio()<=MAX_CUSTOMER_PATH_RATIO).toList();
    long efficientAlternatives=ranked.stream()
        .filter(connection->connection.pathRatio()<=MAX_PREFERRED_INTERMEDIATE_RATIO).count();
    List<CustomerConnection> selected=diverseConnections(
        acceptable.size()>=CUSTOMER_CONNECTIONS?acceptable:ranked,CUSTOMER_CONNECTIONS);
    if(selected.size()<2)throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INSUFFICIENT_ROAD_CONNECTIONS","Não foi possível criar ligações rodoviárias suficientes para o cliente.");
    for(CustomerConnection connection:selected){
      int index=connection.tableIndex();Point candidate=connection.point();
      upsert(edges,new Edge(candidate.id(),customer.id(),distances[index][0],table.fallback(),List.of()));
      upsert(edges,new Edge(customer.id(),candidate.id(),distances[0][index],table.fallback(),List.of()));
      if("ORIGIN".equals(origin.id())&&!table.fallback()){
        upsert(edges,new Edge(origin.id(),candidate.id(),distances[1][index],table.fallback(),List.of()));
        if(positiveFinite(distances[index][1]))
          upsert(edges,new Edge(candidate.id(),origin.id(),distances[index][1],table.fallback(),List.of()));
      }
    }
    if(!table.fallback()&&efficientAlternatives<2&&positiveFinite(direct)&&positiveFinite(distances[0][1])){
      upsert(edges,new Edge(origin.id(),customer.id(),direct,false,List.of()));
      upsert(edges,new Edge(customer.id(),origin.id(),distances[0][1],false,List.of()));
    }
    return new Graph(points,edges);
  }
  private static List<CustomerConnection> diverseConnections(List<CustomerConnection> ranked,int amount){
    List<CustomerConnection> selected=new ArrayList<>();
    for(CustomerConnection candidate:ranked){
      boolean separated=selected.stream().allMatch(existing->angularDistance(existing.bearingDegrees(),candidate.bearingDegrees())>=MIN_BEARING_SEPARATION_DEGREES);
      if(separated)selected.add(candidate);if(selected.size()==amount)return List.copyOf(selected);
    }
    for(CustomerConnection candidate:ranked){if(!selected.contains(candidate))selected.add(candidate);if(selected.size()==amount)break;}
    return List.copyOf(selected);
  }
  private static double bearing(Point from,Point to){double lat1=Math.toRadians(from.latitude()),lat2=Math.toRadians(to.latitude()),delta=Math.toRadians(to.longitude()-from.longitude());double y=Math.sin(delta)*Math.cos(lat2),x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(delta);return(Math.toDegrees(Math.atan2(y,x))+360)%360;}
  private static double angularDistance(double first,double second){double difference=Math.abs(first-second);return Math.min(difference,360-difference);}
  private static boolean positiveFinite(double value){return Double.isFinite(value)&&value>0;}
  private static void upsert(List<Edge> edges,Edge candidate){for(int index=0;index<edges.size();index++){Edge current=edges.get(index);if(current.from().equals(candidate.from())&&current.to().equals(candidate.to())){if(candidate.distanceKm()<current.distanceKm())edges.set(index,candidate);return;}}edges.add(candidate);}
  public RouteData roadGeometryForPath(List<String> path,Graph graph,double fallbackKm){Map<String,Point> points=new HashMap<>();for(Point point:graph.points())points.put(point.id(),point);List<Point> ordered=new ArrayList<>();for(String id:path){Point point=points.get(id);if(point==null)throw new IllegalArgumentException("Vértice ausente no caminho: "+id);ordered.add(point);}return routing.routeThrough(ordered,fallbackKm);}
  public List<Point> nearest(Point p,int amount){return base.points().stream().filter(x->x.type()==PointType.INTERMEDIATE).sorted(Comparator.comparingDouble(x->haversine(p,x))).limit(amount).toList();}
  static double haversine(Point a,Point b){double r=6371,dLat=Math.toRadians(b.latitude()-a.latitude()),dLon=Math.toRadians(b.longitude()-a.longitude());double x=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.sin(dLon/2)*Math.sin(dLon/2);return 2*r*Math.asin(Math.sqrt(x));}
  public static class ConfigFile{public Metadata metadata;public List<NodeConfig> points;public List<LinkConfig> storeLinks;public DemoData demoCustomer;}
  public static class Metadata{public String model,coordinateSource,weightSource,verifiedAt;}
  public static class NodeConfig{public String id,name,address;public double latitude,longitude;public List<LinkConfig> links;}
  public static class LinkConfig{public String to;public double fallbackKm;}
  public static class DemoData{public String name,address;public double latitude,longitude;public Map<String,Double> connectionsToCustomer,connectionsFromCustomer;}
  private record CustomerConnection(Point point,int tableIndex,double completeDistanceKm,
                                    double pathRatio,double bearingDegrees){}
}
