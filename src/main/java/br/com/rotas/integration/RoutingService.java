package br.com.rotas.integration;

import br.com.rotas.config.AppProperties;
import br.com.rotas.domain.Point;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.URI;import java.net.http.*;import java.time.Duration;import java.util.*;import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoutingService {
  private final AppProperties props;private final ObjectMapper mapper;private final HttpClient client;private final Map<String,RouteData> cache=new ConcurrentHashMap<>();
  private final Map<String,RouteTableData> tableCache=new ConcurrentHashMap<>();
  private final Map<String,SnappedRouteTableData> snappedTableCache=new ConcurrentHashMap<>();
  private final Map<String,RoadSnapData> snapCache=new ConcurrentHashMap<>();
  @Autowired public RoutingService(AppProperties p,ObjectMapper m){this(p,m,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(p.external().osrm().timeoutSeconds())).build());}
  RoutingService(AppProperties p,ObjectMapper m,HttpClient c){props=p;mapper=m;client=c;}
  public RouteData route(Point from,Point to,double fallbackKm){return routeThrough(List.of(from,to),fallbackKm);}
  public RouteData routeThrough(List<Point> points,double fallbackKm){if(points==null||points.size()<2)throw new IllegalArgumentException("A rota exige ao menos dois pontos");String key=key(points);return cache.computeIfAbsent(key,k->fetch(points,fallbackKm));}
  public RouteTableData distanceTable(List<Point> points) {
    if(points==null||points.size()<2)throw new IllegalArgumentException("A matriz exige ao menos dois pontos");
    String key="table:"+key(points);return tableCache.computeIfAbsent(key,k->fetchTable(points));
  }
  public SnappedRouteTableData snappedDistanceTable(List<Point> points) {
    if(points==null||points.size()<2)throw new IllegalArgumentException("A matriz exige ao menos dois pontos");
    String key="snapped-table:"+key(points);return snappedTableCache.computeIfAbsent(key,k->fetchSnappedTable(points));
  }
  public RoadSnapData snap(Point point) {
    String key=String.format(Locale.ROOT,"snap:%.5f,%.5f",point.latitude(),point.longitude());
    return snapCache.computeIfAbsent(key,k->fetchSnap(point));
  }
  private RouteData fetch(List<Point> points,double fallback){var cfg=props.external().osrm();String coords=points.stream().map(p->p.longitude()+","+p.latitude()).collect(java.util.stream.Collectors.joining(";"));URI uri=URI.create(cfg.baseUrl()+"/route/v1/driving/"+coords+"?overview=full&geometries=geojson&steps=false");
    try{var req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(cfg.timeoutSeconds())).header("User-Agent",props.external().nominatim().userAgent()).GET().build();var res=client.send(req,HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200)return fallback(fallback);JsonNode r=mapper.readTree(res.body()).path("routes").path(0);if(r.isMissingNode())return fallback(fallback);List<double[]> geometry=new ArrayList<>();for(JsonNode c:r.path("geometry").path("coordinates"))geometry.add(new double[]{c.path(1).asDouble(),c.path(0).asDouble()});return new RouteData(RouteData.metersToKilometers(r.path("distance").asDouble()),r.path("duration").asDouble(),geometry,false);}catch(Exception e){return fallback(fallback);}}
  private RouteData fallback(double km){return new RouteData(km,km/25.0*3600,List.of(),true);}
  private RouteTableData fetchTable(List<Point> points) {
    var cfg=props.external().osrm();String coords=coordinates(points);
    URI uri=URI.create(cfg.baseUrl()+"/table/v1/driving/"+coords+"?annotations=distance");
    try {
      var req=request(uri);var res=client.send(req,HttpResponse.BodyHandlers.ofString());
      if(res.statusCode()!=200)return fallbackTable(points);
      JsonNode rows=mapper.readTree(res.body()).path("distances");
      if(!rows.isArray()||rows.size()!=points.size())return fallbackTable(points);
      double[][] values=new double[points.size()][points.size()];
      for(int i=0;i<points.size();i++){
        JsonNode row=rows.path(i);if(!row.isArray()||row.size()!=points.size())return fallbackTable(points);
        for(int j=0;j<points.size();j++)values[i][j]=row.path(j).isNull()?Double.POSITIVE_INFINITY:RouteData.metersToKilometers(row.path(j).asDouble());
      }
      return new RouteTableData(values,false);
    } catch(Exception e){return fallbackTable(points);}
  }
  private SnappedRouteTableData fetchSnappedTable(List<Point> points) {
    var cfg=props.external().osrm();String coords=coordinates(points);
    URI uri=URI.create(cfg.baseUrl()+"/table/v1/driving/"+coords+"?annotations=distance");
    try {
      var res=client.send(request(uri),HttpResponse.BodyHandlers.ofString());
      if(res.statusCode()!=200)return fallbackSnappedTable(points);
      JsonNode root=mapper.readTree(res.body()),rows=root.path("distances"),sources=root.path("sources");
      if(!rows.isArray()||rows.size()!=points.size()||!sources.isArray()||sources.size()!=points.size())return fallbackSnappedTable(points);
      double[][] values=new double[points.size()][points.size()];
      for(int i=0;i<points.size();i++){
        JsonNode row=rows.path(i);if(!row.isArray()||row.size()!=points.size())return fallbackSnappedTable(points);
        for(int j=0;j<points.size();j++)values[i][j]=row.path(j).isNull()?Double.POSITIVE_INFINITY:RouteData.metersToKilometers(row.path(j).asDouble());
      }
      List<RoadSnapData> snapped=new ArrayList<>();
      for(JsonNode source:sources){JsonNode location=source.path("location");if(!location.isArray()||location.size()<2)return fallbackSnappedTable(points);snapped.add(new RoadSnapData(location.path(1).asDouble(),location.path(0).asDouble(),source.path("distance").asDouble(),source.path("name").asText(""),false));}
      return new SnappedRouteTableData(snapped,values,false);
    } catch(Exception e){return fallbackSnappedTable(points);}
  }
  private RoadSnapData fetchSnap(Point point) {
    var cfg=props.external().osrm();URI uri=URI.create(cfg.baseUrl()+"/nearest/v1/driving/"+point.longitude()+","+point.latitude()+"?number=1");
    try {
      var res=client.send(request(uri),HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200)return fallbackSnap(point);
      JsonNode waypoint=mapper.readTree(res.body()).path("waypoints").path(0),location=waypoint.path("location");
      if(!location.isArray()||location.size()<2)return fallbackSnap(point);
      return new RoadSnapData(location.path(1).asDouble(),location.path(0).asDouble(),waypoint.path("distance").asDouble(),waypoint.path("name").asText(""),false);
    }catch(Exception e){return fallbackSnap(point);}
  }
  private HttpRequest request(URI uri){return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(props.external().osrm().timeoutSeconds())).header("User-Agent",props.external().nominatim().userAgent()).header("Accept","application/json").GET().build();}
  private RouteTableData fallbackTable(List<Point> points){double[][] values=new double[points.size()][points.size()];for(int i=0;i<points.size();i++)for(int j=0;j<points.size();j++)values[i][j]=i==j?0:haversine(points.get(i),points.get(j))*1.28;return new RouteTableData(values,true);}
  private SnappedRouteTableData fallbackSnappedTable(List<Point> points){RouteTableData table=fallbackTable(points);return new SnappedRouteTableData(points.stream().map(this::fallbackSnap).toList(),table.distancesKm(),true);}
  private RoadSnapData fallbackSnap(Point point){return new RoadSnapData(point.latitude(),point.longitude(),0,"",true);}
  private static double haversine(Point a,Point b){double r=6371,dLat=Math.toRadians(b.latitude()-a.latitude()),dLon=Math.toRadians(b.longitude()-a.longitude());double value=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.sin(dLon/2)*Math.sin(dLon/2);return 2*r*Math.asin(Math.sqrt(value));}
  private String coordinates(List<Point> points){return points.stream().map(p->p.longitude()+","+p.latitude()).collect(java.util.stream.Collectors.joining(";"));}
  private String key(List<Point> points){return points.stream().map(p->String.format(Locale.ROOT,"%.5f,%.5f",p.latitude(),p.longitude())).collect(java.util.stream.Collectors.joining(">"));}
  public int cacheSize(){return cache.size()+tableCache.size()+snappedTableCache.size()+snapCache.size();}
}
