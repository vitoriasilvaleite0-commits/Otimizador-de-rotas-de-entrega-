package br.com.rotas;
import br.com.rotas.config.AppProperties;import br.com.rotas.service.GraphService;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ApplicationContextTest {
  @Autowired AppProperties properties;
  @Autowired GraphService graphs;
  @Test void contextLoads(){}
  @Test void configuredServiceAreaContainsStoreAndEveryFixedVertex(){assertTrue(properties.region().contains(properties.store().latitude(),properties.store().longitude()));assertTrue(graphs.baseGraph().points().stream().allMatch(p->properties.region().contains(p.latitude(),p.longitude())));}
}
