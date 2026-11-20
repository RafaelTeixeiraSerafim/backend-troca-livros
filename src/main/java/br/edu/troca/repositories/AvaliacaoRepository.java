package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface AvaliacaoRepository
    extends JpaRepository<Avaliacao, java.util.UUID>, JpaSpecificationExecutor<Avaliacao> {
  boolean existsByPropostaIdAndAvaliadorId(java.util.UUID proposta, java.util.UUID avaliador);

  long countByAvaliadoId(java.util.UUID id);

  @org.springframework.data.jpa.repository.Query(
      "select avg(a.nota) from Avaliacao a where a.avaliado.id=:id")
  Double media(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
}
