package br.com.rotas.domain;
import java.util.List;
public record Graph(List<Point> points, List<Edge> edges) {
  public Graph { points = List.copyOf(points); edges = List.copyOf(edges); }
}
