package br.com.rotas.controller;
import br.com.rotas.integration.*;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/geocoding")
public class GeocodingController {private final GeocodingService service;public GeocodingController(GeocodingService s){service=s;}@GetMapping("/search") public List<GeocodingResult> search(@RequestParam String query,@RequestParam(defaultValue="service") String scope){return switch(scope){case "origin","dynamic"->service.searchBroad(query);case "service"->service.search(query);default->throw new IllegalArgumentException("Escopo de busca inválido");};}}
