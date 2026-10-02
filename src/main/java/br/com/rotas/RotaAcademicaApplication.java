package br.com.rotas;

import br.com.rotas.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class RotaAcademicaApplication {
  public static void main(String[] args) { SpringApplication.run(RotaAcademicaApplication.class, args); }
}
