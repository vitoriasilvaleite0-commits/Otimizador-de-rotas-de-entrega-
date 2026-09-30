package br.com.rotas.domain;

import java.util.*;

public final class AdjacencyMatrix {
  private final List<String> ids;
  private final double[][] values;
  public AdjacencyMatrix(List<String> ids) {
    this.ids = List.copyOf(ids); this.values = new double[ids.size()][ids.size()];
    if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("IDs duplicados");
    for (int i=0;i<values.length;i++) { Arrays.fill(values[i], Double.POSITIVE_INFINITY); values[i][i]=0; }
  }
  public static AdjacencyMatrix from(Graph graph) {
    var m = new AdjacencyMatrix(graph.points().stream().map(Point::id).toList());
    graph.edges().forEach(e -> m.set(e.from(), e.to(), e.distanceKm())); return m;
  }
  public void set(String from, String to, double weight) {
    if (Double.isNaN(weight) || weight < 0) throw new IllegalArgumentException("Pesos negativos ou NaN não são permitidos");
    values[indexOf(from)][indexOf(to)] = weight;
  }
  public int indexOf(String id) { int i=ids.indexOf(id); if(i<0) throw new IllegalArgumentException("Vértice desconhecido: "+id); return i; }
  public double get(int i,int j){ return values[i][j]; }
  public double get(String a,String b){ return values[indexOf(a)][indexOf(b)]; }
  public int size(){ return ids.size(); }
  public List<String> ids(){ return ids; }
  public double[][] copyValues(){ return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new); }
}
