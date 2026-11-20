package br.edu.troca.services;

import br.edu.troca.exceptions.ApiException;
import java.util.*;
import org.springframework.data.domain.*;

public final class Pages {
  private Pages() {}

  public static Pageable of(
      int page, int size, String sort, String defaultSort, String... allowed) {
    if (page < 0 || size < 1 || size > 100) throw bad();
    String[] parts = (sort == null ? defaultSort : sort).split(",", -1);
    if (parts.length != 2
        || !Set.of(allowed).contains(parts[0])
        || !(parts[1].equals("asc") || parts[1].equals("desc"))) throw bad();
    return PageRequest.of(
        page, size, Sort.by(Sort.Direction.fromString(parts[1]), parts[0]).and(Sort.by("id")));
  }

  private static ApiException bad() {
    return new ApiException(
        400,
        "PAGINACAO_INVALIDA",
        "page deve ser >= 0, size entre 1 e 100 e sort deve usar campo permitido,asc|desc");
  }
}
