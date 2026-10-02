package br.com.rotas.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppPropertiesRegionTest {
  private final AppProperties.Region region = new AppProperties.Region(
      -10, 10, -10, 10, "-10,10,10,-10",
      List.of(
          new AppProperties.BoundaryPoint(-1, -1),
          new AppProperties.BoundaryPoint(-1, 1),
          new AppProperties.BoundaryPoint(1, 1),
          new AppProperties.BoundaryPoint(1, -1)));

  @Test void acceptsPointInsidePolygon() {
    assertTrue(region.contains(0, 0));
  }

  @Test void acceptsPointOnPolygonBoundary() {
    assertTrue(region.contains(1, 0));
  }

  @Test void rejectsPointOutsidePolygonEvenWhenInsideBoundingBox() {
    assertFalse(region.contains(5, 5));
  }

  @Test void rejectsPointOutsideBoundingBox() {
    assertFalse(region.contains(20, 0));
  }
}
