package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface LivroRepository
    extends JpaRepository<Livro, java.util.UUID>, JpaSpecificationExecutor<Livro> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select i from Livro i where i.id in :ids order by i.id")
  java.util.List<Livro> lockAll(
      @org.springframework.data.repository.query.Param("ids")
          java.util.Collection<java.util.UUID> ids);
}
