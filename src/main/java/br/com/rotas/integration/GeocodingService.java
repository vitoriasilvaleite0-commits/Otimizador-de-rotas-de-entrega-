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
    String key=(broad?"broad:":"region:")+normalize(query); return cache.computeIfAbsent(key,k->fetch(query,broad));
  }
  private List<GeocodingResult> fetch(String query,boolean broad){
    rateLimit(); var cfg=properties.external().nominatim();
    String encoded=java.net.URLEncoder.encode(query,StandardCharsets.UTF_8);
    String area=broad?"&countrycodes=br":"&bounded=1&viewbox="+properties.region().viewbox();
    URI uri=URI.create(cfg.baseUrl()+"/search?format=jsonv2&addressdetails=1&limit=5"+area+"&q="+encoded);
    var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(cfg.timeoutSeconds())).header("User-Agent",cfg.userAgent()).header("Accept","application/json").GET().build();
    try {
      var response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GEOCODING_UNAVAILABLE","A geocodificação está indisponível; use o endereço de demonstração.");
      JsonNode root=mapper.readTree(response.body()); List<GeocodingResult> results=new ArrayList<>();
      for(JsonNode n:root){double lat=n.path("lat").asDouble();double lon=n.path("lon").asDouble();if(!broad&&!properties.region().contains(lat,lon))continue;JsonNode a=n.path("address");results.add(new GeocodingResult(n.path("display_name").asText(),text(a,"road",text(a,"pedestrian","")),text(a,"house_number",""),text(a,"suburb",text(a,"neighbourhood","")),text(a,"city",text(a,"town","São Paulo")),text(a,"state",""),text(a,"postcode",""),lat,lon,false));}
      if(results.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"ADDRESS_NOT_FOUND",broad?"Nenhum endereço foi encontrado no Brasil.":"Nenhum endereço foi encontrado dentro da região atendida."); return List.copyOf(results);
    } catch(ApiException e){throw e;} catch(HttpTimeoutException e){throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,"GEOCODING_TIMEOUT","A busca demorou demais; tente novamente ou use a demonstração.");} catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"GEOCODING_UNAVAILABLE","Não foi possível consultar endereços; use a demonstração.");}
  }
  private synchronized void rateLimit(){long wait=properties.external().nominatim().minIntervalMillis()-(System.currentTimeMillis()-lastRequestAt);if(wait>0)try{Thread.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();}lastRequestAt=System.currentTimeMillis();}
  static String normalize(String s){return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
  private static String text(JsonNode n,String key,String fallback){String v=n.path(key).asText();return v.isBlank()?fallback:v;}
  public int cacheSize(){return cache.size();}
}
