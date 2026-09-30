package br.com.rotas.domain;
public record Relaxation(String neighbor, double oldDistance, double edgeWeight, double candidateDistance, double newDistance, boolean updated, String previousPredecessor, String newPredecessor, String reason) {}
