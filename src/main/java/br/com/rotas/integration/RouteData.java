package br.com.rotas.integration;
import java.util.List;
public record RouteData(double distanceKm,double durationSeconds,List<double[]> geometry,boolean fallback) {
  public static double metersToKilometers(double meters){ if(!Double.isFinite(meters)||meters<0) throw new IllegalArgumentException("Distância em metros inválida"); return meters/1000.0; }
}
