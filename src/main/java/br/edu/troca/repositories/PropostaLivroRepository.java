package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface PropostaLivroRepository
    extends JpaRepository<PropostaLivro, java.util.UUID>, JpaSpecificationExecutor<PropostaLivro> {
  java.util.List<PropostaLivro> findByPropostaIdOrderById(java.util.UUID id);

  boolean existsByLivroId(java.util.UUID id);

  @org.springframework.data.jpa.repository.Query(
      "select count(pi)>0 from PropostaLivro pi where pi.livro.id=:id and"
          + " pi.proposta.status=br.edu.troca.models.Status.ACEITA")
  boolean reservado(@org.springframework.data.repository.query.Param("id") java.util.UUID id);

  @org.springframework.data.jpa.repository.Query(
      "select distinct pi.proposta from PropostaLivro pi where pi.livro.id in :ids and"
          + " pi.proposta.status=br.edu.troca.models.Status.PENDENTE")
  java.util.List<Proposta> pendentes(
      @org.springframework.data.repository.query.Param("ids")
          java.util.Collection<java.util.UUID> ids);
}
