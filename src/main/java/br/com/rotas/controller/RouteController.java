package br.com.rotas.controller;

import br.com.rotas.domain.Point;
import br.com.rotas.domain.PointType;
import br.com.rotas.service.RouteOptimizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
  private final RouteOptimizationService service;

  public RouteController(RouteOptimizationService service) {
    this.service = service;
  }

  @PostMapping("/optimize")
  public RouteOptimizationService.OptimizationResult optimize(
      @Valid @RequestBody ClientRequest request) {
    return service.optimize(new Point("CLIENT", request.name(), request.address(),
        request.latitude(), request.longitude(), PointType.CUSTOMER));
  }

  public record ClientRequest(@NotBlank @Size(max = 120) String name,
                              @NotBlank @Size(max = 240) String address,
                              @DecimalMin("-90") @DecimalMax("90") double latitude,
                              @DecimalMin("-180") @DecimalMax("180") double longitude) {}
}
