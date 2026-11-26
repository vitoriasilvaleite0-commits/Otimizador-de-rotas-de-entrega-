package br.com.rotas.controller;
import br.com.rotas.exception.ApiException;import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.MissingServletRequestParameterException;import org.springframework.web.bind.annotation.*;import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;import org.springframework.web.servlet.resource.NoResourceFoundException;import java.time.Instant;
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)ResponseEntity<ErrorResponse> api(ApiException e,HttpServletRequest r){return ResponseEntity.status(e.status()).body(new ErrorResponse(Instant.now(),e.status().value(),e.code(),e.getMessage(),r.getRequestURI()));}
  // Dados errados do cliente: campo inválido, JSON quebrado, parâmetro faltando ou com texto no lugar de número.
  @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class,HttpMessageNotReadableException.class,
      MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
  ResponseEntity<ErrorResponse> validation(Exception e,HttpServletRequest r){return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(),400,"INVALID_INPUT","Verifique os dados informados.",r.getRequestURI()));}
  @ExceptionHandler(NoResourceFoundException.class)ResponseEntity<ErrorResponse> notFound(NoResourceFoundException e,HttpServletRequest r){return ResponseEntity.status(404).body(new ErrorResponse(Instant.now(),404,"NOT_FOUND","Endereço não encontrado.",r.getRequestURI()));}
  // Demais erros do próprio Spring (método HTTP errado, tipo de conteúdo errado...) mantêm o código que o Spring definiu.
  @ExceptionHandler(Exception.class)ResponseEntity<ErrorResponse> unexpected(Exception e,HttpServletRequest r){
    if(e instanceof org.springframework.web.ErrorResponse er){int code=er.getStatusCode().value();return ResponseEntity.status(code).body(new ErrorResponse(Instant.now(),code,"REQUEST_ERROR","Não foi possível atender a esse pedido.",r.getRequestURI()));}
    return ResponseEntity.status(500).body(new ErrorResponse(Instant.now(),500,"INTERNAL_ERROR","Não foi possível concluir a operação.",r.getRequestURI()));}
  record ErrorResponse(Instant timestamp,int status,String code,String message,String path){}
}
