package br.edu.troca.exceptions;

import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
  public static ProblemDetail problem(int status, String code, String detail) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setProperty("code", code);
    return p;
  }

  @ExceptionHandler(ApiException.class)
  ProblemDetail api(ApiException e) {
    return problem(e.status, e.code, e.getMessage());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ConcurrencyFailureException.class,
    jakarta.persistence.LockTimeoutException.class,
    jakarta.persistence.PessimisticLockException.class
  })
  ProblemDetail conflict(Exception e) {
    return problem(409, "CONFLITO", "Duplicidade, referência histórica ou conflito concorrente");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail type(Exception e) {
    return problem(400, "ENTRADA_INVALIDA", "Parâmetro inválido");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders h, HttpStatusCode s, WebRequest r) {
    var p = problem(400, "ENTRADA_INVALIDA", "Verifique os campos informados");
    p.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                e ->
                    Map.of(
                        "field",
                        e.getField(),
                        "message",
                        Objects.toString(e.getDefaultMessage(), "Inválido")))
            .toList());
    return handleExceptionInternal(ex, p, h, s, r);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e, HttpHeaders h, HttpStatusCode s, WebRequest r) {
    return handleExceptionInternal(
        e,
        problem(400, "ENTRADA_INVALIDA", "JSON inválido: confira UUIDs, enums e campos permitidos"),
        h,
        s,
        r);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception e, Object body, HttpHeaders h, HttpStatusCode s, WebRequest r) {
    if (body == null || body instanceof ProblemDetail p && p.getProperties() == null)
      body =
          problem(
              s.value(),
              "HTTP_" + s.value(),
              s.is4xxClientError() ? "Requisição inválida" : "Erro interno");
    return super.handleExceptionInternal(e, body, h, s, r);
  }
}
