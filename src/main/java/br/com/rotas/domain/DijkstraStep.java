package br.com.rotas.domain;
import java.util.*;
public record DijkstraStep(int number, String selectedVertex, Map<String,Double> distances, Set<String> visited, Map<String,String> predecessors, java.util.List<Relaxation> analyzedNeighbors, String message) {}
