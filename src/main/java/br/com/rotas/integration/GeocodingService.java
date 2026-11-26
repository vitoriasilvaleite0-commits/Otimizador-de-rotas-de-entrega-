package br.com.rotas.integration;

import br.com.rotas.config.AppProperties;
import br.com.rotas.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodingService {
  // Limite simples para o cache não crescer sem fim num servidor com pouca memória.
  private static final int MAX_CACHE_ENTRIES=2000;
  private final AppProperties properties; private final HttpClient client; private final ObjectMapper mapper;
  private final Map<String,List<GeocodingResult>> cache=new ConcurrentHashMap<>(); private long lastRequestAt;
  @Autowired public GeocodingService(AppProperties properties,ObjectMapper mapper){this(properties,mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.external().nominatim().timeoutSeconds())).build());}
  GeocodingService(AppProperties properties,ObjectMapper mapper,HttpClient client){this.properties=properties;this.mapper=mapper;this.client=client;}
  public List<GeocodingResult> search(String query){
    return search(query,false);
  }
  public List<GeocodingResult> searchBroad(String query){
    return search(query,true);
  }
  private List<GeocodingResult> search(String query,boolean broad){
    if(query==null||query.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"EMPTY_ADDRESS","Informe um endereço.");
    if(query.length()>180) throw new ApiException(HttpStatus.BAD_REQUEST,"ADDRESS_TOO_LONG","O endereço deve ter no máximo 180 caracteres.");
    String key=(broad?"broad:":"region:")+normalize(query);
    if(cache.size()>=MAX_CACHE_ENTRIES&&!cache.containsKey(key)) cache.clear();
    return cache.computeIfAbsent(key,k->fetch(query,broad));
  }
  private List<GeocodingResult> fetch(String query,boolean broad){
    String encoded=java.net.URLEncoder.encode(query,StandardCharsets.UTF_8);
    String area=broad?"&countrycodes=br":"&bounded=1&viewbox="+properties.region().viewbox();
    JsonNode root=get("/search?format=jsonv2&addressdetails=1&limit=5"+area+"&q="+encoded); List<GeocodingResult> results=new ArrayList<>();
    for(JsonNode n:root){double lat=n.path("lat").asDouble();double lon=n.path("lon").asDouble();if(!broad&&!properties.region().contains(lat,lon))continue;results.add(toResult(n,lat,lon));}
    if(results.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"ADDRESS_NOT_FOUND",broad?"Nenhum endereço foi encontrado no Brasil.":"Nenhum endereço foi encontrado dentro da região atendida."); return List.copyOf(results);
  }
  // Endereço de um ponto clicado no mapa. As coordenadas devolvidas são as do clique, não as do endereço achado.
  public GeocodingResult reverse(double latitude,double longitude){
    if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)
      throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_COORDINATES","Coordenadas inválidas.");
    String key=String.format(Locale.ROOT,"reverse:%.5f,%.5f",latitude,longitude);
    if(cache.size()>=MAX_CACHE_ENTRIES&&!cache.containsKey(key)) cache.clear();
    return cache.computeIfAbsent(key,k->{
      JsonNode n=get(String.format(Locale.ROOT,"/reverse?format=jsonv2&addressdetails=1&zoom=18&lat=%.6f&lon=%.6f",latitude,longitude));
      if(n.has("error")||n.path("display_name").asText().isBlank()) throw new ApiException(HttpStatus.NOT_FOUND,"ADDRESS_NOT_FOUND","Nenhum endereço foi encontrado nesse ponto.");
      return List.of(toResult(n,latitude,longitude));
    }).getFirst();
  }
  private JsonNode get(String pathAndQuery){
    rateLimit(); var cfg=properties.external().nominatim();
    var request=HttpRequest.newBuilder(URI.create(cfg.baseUrl()+pathAndQuery)).timeout(Duration.ofSeconds(cfg.timeoutSeconds())).header("User-Agent",cfg.userAgent()).header("Accept","application/json").GET().build();
    try {
      var response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GEOCODING_UNAVAILABLE","A geocodificação está indisponível; use o endereço de demonstração.");
      return mapper.readTree(response.body());
    } catch(ApiException e){throw e;} catch(HttpTimeoutException e){throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,"GEOCODING_TIMEOUT","A busca demorou demais; tente novamente ou use a demonstração.");} catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GEOCODING_UNAVAILABLE","Não foi possível consultar endereços; use a demonstração.");}
  }
  private static GeocodingResult toResult(JsonNode n,double lat,double lon){JsonNode a=n.path("address");return new GeocodingResult(n.path("display_name").asText(),text(a,"road",text(a,"pedestrian","")),text(a,"house_number",""),text(a,"suburb",text(a,"neighbourhood","")),text(a,"city",text(a,"town","São Paulo")),text(a,"state",""),text(a,"postcode",""),lat,lon,false);}
  private synchronized void rateLimit(){long wait=properties.external().nominatim().minIntervalMillis()-(System.currentTimeMillis()-lastRequestAt);if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();}lastRequestAt=System.currentTimeMillis();}
  static String normalize(String s){return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
  private static String text(JsonNode n,String key,String fallback){String v=n.path(key).asText();return v.isBlank()?fallback:v;}
  public int cacheSize(){return cache.size();}
}
