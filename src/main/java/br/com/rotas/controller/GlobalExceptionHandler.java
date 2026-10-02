package br.com.rotas.controller;
import br.com.rotas.exception.ApiException;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import java.time.Instant;
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)ResponseEntity<ErrorResponse> api(ApiException e,HttpServletRequest r){return ResponseEntity.status(e.status()).body(new ErrorResponse(Instant.now(),e.status().value(),e.code(),e.getMessage(),r.getRequestURI()));}
  @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class})ResponseEntity<ErrorResponse> validation(Exception e,HttpServletRequest r){return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(),400,"INVALID_INPUT","Verifique os dados informados.",r.getRequestURI()));}
  @ExceptionHandler(Exception.class)ResponseEntity<ErrorResponse> unexpected(Exception e,HttpServletRequest r){return ResponseEntity.status(500).body(new ErrorResponse(Instant.now(),500,"INTERNAL_ERROR","Não foi possível concluir a operação.",r.getRequestURI()));}
  record ErrorResponse(Instant timestamp,int status,String code,String message,String path){}
}
