package br.com.rotas.domain;

public record Point(String id, String name, String address, double latitude, double longitude, PointType type) {
  public Point {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Identificador do ponto é obrigatório");
    if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90 || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
      throw new IllegalArgumentException("Coordenadas inválidas");
  }
}
