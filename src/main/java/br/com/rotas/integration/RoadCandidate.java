package br.com.rotas.integration;

import java.util.List;

/** Cruzamento dirigível obtido diretamente dos nós e vias do OpenStreetMap. */
public record RoadCandidate(long osmId, double latitude, double longitude,
                            List<String> roadNames, int connectedWays,
                            int roadClassScore) {
  public RoadCandidate {
    roadNames = roadNames == null ? List.of() : List.copyOf(roadNames);
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || connectedWays < 2)
      throw new IllegalArgumentException("Candidato viário inválido");
  }
}
