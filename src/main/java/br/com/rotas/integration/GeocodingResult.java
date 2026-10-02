package br.com.rotas.integration;
public record GeocodingResult(String displayName,String road,String number,String neighborhood,String city,String state,String postcode,double latitude,double longitude,boolean fallback) {}
