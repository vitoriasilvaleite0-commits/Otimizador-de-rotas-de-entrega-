package br.com.rotas.domain;
import java.util.List;
public record DijkstraResult(List<String> path, double totalDistanceKm, boolean reachable, List<DijkstraStep> steps) {}
