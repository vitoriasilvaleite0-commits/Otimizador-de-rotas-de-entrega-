package br.com.rotas.integration;

public record RoadSnapData(double latitude, double longitude, double distanceMeters,
                           String roadName, boolean fallback) {}
