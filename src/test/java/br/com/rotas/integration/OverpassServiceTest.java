package br.com.rotas.integration;

import br.com.rotas.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class OverpassServiceTest {
  @Test void parsesOnlyRealDrivableIntersectionsAndKeepsRoadNames() throws Exception {
    String json="""
        {"elements":[
          {"type":"node","id":1,"lat":-23.61,"lon":-46.64},
          {"type":"node","id":2,"lat":-23.62,"lon":-46.65},
          {"type":"node","id":3,"lat":-23.63,"lon":-46.66},
          {"type":"way","id":10,"nodes":[1,2],"tags":{"highway":"primary","name":"Avenida A"}},
          {"type":"way","id":11,"nodes":[1,3],"tags":{"highway":"secondary","name":"Rua B"}},
          {"type":"way","id":12,"nodes":[2,3],"tags":{"highway":"service","service":"parking_aisle","name":"Estacionamento"}}
        ]}
        """;
    OverpassService service=new OverpassService(properties(),new ObjectMapper(),new StubClient(json,200));
    List<RoadCandidate> points=service.parse(json);
    assertEquals(1,points.size());
    assertEquals(1,points.getFirst().osmId());
    assertEquals(List.of("Avenida A","Rua B"),points.getFirst().roadNames());
    assertEquals(5,points.getFirst().roadClassScore());
  }

  @Test void sendsBoundedPostQueryWithApplicationUserAgent() {
    StubClient http=new StubClient("{\"elements\":[]}",200);
    OverpassService service=new OverpassService(properties(),new ObjectMapper(),http);
    service.intersections(new br.com.rotas.domain.Point("O","O","O",-23.61,-46.64,br.com.rotas.domain.PointType.STORE),10);
    assertEquals("POST",http.request.method());
    assertEquals("academic-test",http.request.headers().firstValue("User-Agent").orElseThrow());
    assertEquals("http://test",http.request.uri().toString());
    String query=service.buildQuery(new br.com.rotas.domain.Point("O","O","O",-23.61,-46.64,br.com.rotas.domain.PointType.STORE),10);
    assertEquals(16,occurrences(query,"way[\"highway\""));
    assertFalse(query.contains("(around:"));
  }

  private int occurrences(String value,String searched){int count=0,index=0;while((index=value.indexOf(searched,index))>=0){count++;index+=searched.length();}return count;}

  private AppProperties properties(){return new AppProperties(new AppProperties.Store("S","S","S",-23.61,-46.64),new AppProperties.Region(-90,90,-180,180,"x",List.of()),new AppProperties.External(new AppProperties.External.Nominatim("http://test","academic-test",2,0),new AppProperties.External.Osrm("http://test",2),new AppProperties.External.Overpass("http://test",2)),true,AppProperties.GraphGeneration.defaults());}

  static class StubClient extends HttpClient {
    final String body;final int status;HttpRequest request;
    StubClient(String body,int status){this.body=body;this.status=status;}
    public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler){this.request=request;@SuppressWarnings("unchecked")T cast=(T)body;return new StubResponse<>(request,status,cast);}
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){return CompletableFuture.completedFuture(send(r,h));}
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return sendAsync(r,h);}
    public Optional<CookieHandler> cookieHandler(){return Optional.empty();}public Optional<Duration> connectTimeout(){return Optional.of(Duration.ofSeconds(1));}public Redirect followRedirects(){return Redirect.NEVER;}public Optional<ProxySelector> proxy(){return Optional.empty();}public SSLContext sslContext(){try{return SSLContext.getDefault();}catch(Exception e){throw new RuntimeException(e);}}public SSLParameters sslParameters(){return new SSLParameters();}public Optional<Authenticator> authenticator(){return Optional.empty();}public Version version(){return Version.HTTP_1_1;}public Optional<java.util.concurrent.Executor> executor(){return Optional.empty();}
  }
  record StubResponse<T>(HttpRequest request,int statusCode,T body) implements HttpResponse<T>{public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}public Optional<javax.net.ssl.SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}}
}
