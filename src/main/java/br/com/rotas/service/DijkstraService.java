package br.com.rotas.service;

import br.com.rotas.domain.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DijkstraService {
  public DijkstraResult solve(AdjacencyMatrix matrix, String source, String target) {
    int n=matrix.size(), s=matrix.indexOf(source), t=matrix.indexOf(target);
    double[] dist=new double[n]; Arrays.fill(dist, Double.POSITIVE_INFINITY); dist[s]=0;
    boolean[] visited=new boolean[n]; int[] predecessor=new int[n]; Arrays.fill(predecessor,-1);
    List<DijkstraStep> steps=new ArrayList<>();
    if(s==t) { steps.add(snapshot(0,source,matrix,dist,visited,predecessor,List.of(),"A origem já é o destino; a distância é zero.")); return new DijkstraResult(List.of(source),0,true,steps); }
    for(int round=0;round<n;round++) {
      int u=-1; double best=Double.POSITIVE_INFINITY;
      for(int i=0;i<n;i++) if(!visited[i] && dist[i]<best){best=dist[i];u=i;}
      if(u<0) break;
      visited[u]=true; List<Relaxation> relaxations=new ArrayList<>();
      for(int v=0;v<n;v++) {
        double w=matrix.get(u,v); if(visited[v] || u==v || !Double.isFinite(w)) continue;
        double old=dist[v], candidate=dist[u]+w; boolean update=candidate<old;
        String oldPred=predecessor[v]<0?null:matrix.ids().get(predecessor[v]);
        if(update){dist[v]=candidate;predecessor[v]=u;}
        String reason=update ? (Double.isInfinite(old)?"A distância era infinita e foi descoberta.":"O novo caminho é menor.") : "O caminho existente é menor ou igual.";
        relaxations.add(new Relaxation(matrix.ids().get(v),old,w,candidate,dist[v],update,oldPred,predecessor[v]<0?null:matrix.ids().get(predecessor[v]),reason));
      }
      steps.add(snapshot(steps.size()+1,matrix.ids().get(u),matrix,dist,visited,predecessor,relaxations,"Selecionamos o vértice não visitado com menor distância temporária."));
      if(u==t) break;
    }
    if(!Double.isFinite(dist[t])) return new DijkstraResult(List.of(),Double.POSITIVE_INFINITY,false,steps);
    LinkedList<String> path=new LinkedList<>(); for(int at=t;at!=-1;at=predecessor[at]) path.addFirst(matrix.ids().get(at));
    return new DijkstraResult(path,dist[t],true,steps);
  }
  private DijkstraStep snapshot(int number,String selected,AdjacencyMatrix m,double[] d,boolean[] visited,int[] pred,List<Relaxation> rs,String msg){
    Map<String,Double> dm=new LinkedHashMap<>(); Set<String> vs=new LinkedHashSet<>(); Map<String,String> ps=new LinkedHashMap<>();
    for(int i=0;i<m.size();i++){dm.put(m.ids().get(i),d[i]);if(visited[i])vs.add(m.ids().get(i));if(pred[i]>=0)ps.put(m.ids().get(i),m.ids().get(pred[i]));}
    return new DijkstraStep(number,selected,dm,vs,ps,List.copyOf(rs),msg);
  }
}
