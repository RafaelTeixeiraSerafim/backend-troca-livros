package br.edu.troca.services;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvaliacaoService {
  private final AvaliacaoRepository reviews;
  private final PropostaService proposals;
  private final UsuarioService users;
  private final MutationLock lock;

  public AvaliacaoService(
      AvaliacaoRepository r, PropostaService p, UsuarioService u, MutationLock l) {
    reviews = r;
    proposals = p;
    users = u;
    lock = l;
  }

  @Transactional
  public AvaliacaoSaida avaliar(UUID user, UUID id, AvaliacaoEntrada in) {
    lock.acquire();
    var p = proposals.get(id);
    proposals.participant(p, user);
    if (p.status != Status.CONCLUIDA)
      throw ApiException.conflict("Avaliação exige proposta concluída");
    if (reviews.existsByPropostaIdAndAvaliadorId(id, user))
      throw ApiException.conflict("Participante já avaliou a proposta");
    var a = new Avaliacao();
    a.proposta = p;
    a.avaliador = users.get(user);
    a.avaliado = p.proponente.id.equals(user) ? p.destinatario : p.proponente;
    a.nota = in.nota();
    a.comentario = in.comentario();
    return AvaliacaoSaida.of(reviews.saveAndFlush(a));
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AvaliacoesRecebidas recebidas(UUID user, Pageable page) {
    users.get(user);
    return new AvaliacoesRecebidas(
        Pagina.of(
            reviews
                .findAll((r, q, b) -> b.equal(r.get("avaliado").get("id"), user), page)
                .map(AvaliacaoSaida::of)),
        reviews.media(user),
        reviews.countByAvaliadoId(user));
  }
}
