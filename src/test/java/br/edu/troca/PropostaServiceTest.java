package br.edu.troca;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import br.edu.troca.services.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropostaServiceTest {
  PropostaRepository repository;
  PropostaLivroRepository links;
  LivroRepository books;
  PropostaService service;
  Proposta proposal;

  @BeforeEach
  void setup() {
    repository = mock(PropostaRepository.class);
    links = mock(PropostaLivroRepository.class);
    books = mock(LivroRepository.class);
    service =
        new PropostaService(
            repository, links, books, mock(UsuarioService.class), mock(MutationLock.class));
    proposal = new Proposta();
    proposal.proponente = new Usuario();
    proposal.destinatario = new Usuario();
    when(repository.findById(proposal.id)).thenReturn(Optional.of(proposal));
  }

  @Test
  void unauthorizedAcceptanceNeverReservesBooks() {
    assertThatThrownBy(
            () -> service.transicionar(proposal.proponente.id, proposal.id, Status.ACEITA))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status).isEqualTo(403));
    verifyNoInteractions(books, links);
    assertThat(proposal.status).isEqualTo(Status.PENDENTE);
  }

  @Test
  void repeatedAcceptanceFailsBeforeReadingBooks() {
    proposal.status = Status.ACEITA;
    assertThatThrownBy(
            () -> service.transicionar(proposal.destinatario.id, proposal.id, Status.ACEITA))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status).isEqualTo(409));
    verifyNoInteractions(books, links);
  }

  @Test
  void conclusionTransfersSeveralBooksToOppositeParticipants() {
    proposal.status = Status.ACEITA;
    var offered = new PropostaLivro();
    offered.livro = new Livro();
    offered.livro.dono = proposal.proponente;
    offered.livro.disponivel = false;
    offered.lado = Lado.OFERECIDO;
    var requested = new PropostaLivro();
    requested.livro = new Livro();
    requested.livro.dono = proposal.destinatario;
    requested.livro.disponivel = false;
    requested.lado = Lado.SOLICITADO;
    when(links.findByPropostaIdOrderById(proposal.id)).thenReturn(List.of(offered, requested));
    var result = service.transicionar(proposal.destinatario.id, proposal.id, Status.CONCLUIDA);
    assertThat(result.status()).isEqualTo(Status.CONCLUIDA);
    assertThat(offered.livro.dono).isSameAs(proposal.destinatario);
    assertThat(requested.livro.dono).isSameAs(proposal.proponente);
    assertThat(offered.livro.disponivel).isTrue();
    assertThat(requested.livro.disponivel).isTrue();
    assertThat(offered.lado).isEqualTo(Lado.OFERECIDO);
    assertThat(requested.lado).isEqualTo(Lado.SOLICITADO);
  }

  @Test
  void unimplementedTransitionCannotBeInvokedThroughService() {
    assertThatThrownBy(
            () -> service.transicionar(proposal.proponente.id, proposal.id, Status.PENDENTE))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status).isEqualTo(409));
    verifyNoInteractions(books, links);
  }
}
