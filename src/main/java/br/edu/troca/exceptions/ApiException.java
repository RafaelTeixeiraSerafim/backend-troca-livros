package br.edu.troca.exceptions;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;

  public ApiException(int status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public static ApiException missing() {
    return new ApiException(404, "RECURSO_INEXISTENTE", "Recurso não encontrado");
  }

  public static ApiException conflict(String detail) {
    return new ApiException(409, "CONFLITO", detail);
  }

  public static ApiException forbidden() {
    return new ApiException(403, "ACESSO_NEGADO", "Operação não permitida para este usuário");
  }
}
