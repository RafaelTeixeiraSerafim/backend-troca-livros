package br.edu.troca.services;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PropostaService {
  private final PropostaRepository proposals;
  private final PropostaLivroRepository links;
  private final LivroRepository books;
  private final UsuarioService users;
  private final MutationLock lock;

  public PropostaService(
      PropostaRepository p,
      PropostaLivroRepository l,
      LivroRepository i,
      UsuarioService u,
      MutationLock m) {
    proposals = p;
    links = l;
    books = i;
    users = u;
    lock = m;
  }

  public Proposta get(UUID id) {
    return proposals.findById(id).orElseThrow(ApiException::missing);
  }

  public void participant(Proposta p, UUID user) {
    if (!p.proponente.id.equals(user) && !p.destinatario.id.equals(user))
      throw ApiException.forbidden();
  }

  private PropostaSaida output(Proposta p) {
    return new PropostaSaida(
        p.id,
        p.proponente.id,
        p.destinatario.id,
        p.mensagem,
        p.status,
        p.criadoEm,
        links.findByPropostaIdOrderById(p.id).stream()
            .map(l -> new LivroPropostaSaida(l.livro.id, l.lado))
            .toList());
  }

  public PropostaSaida consultar(UUID user, UUID id) {
    var p = get(id);
    participant(p, user);
    return output(p);
  }

  @Transactional
  public PropostaSaida criar(UUID user, PropostaEntrada in) {
    lock.acquire();
    if (user.equals(in.destinatarioId()))
      throw ApiException.conflict("Participantes devem ser distintos");
    var p = new Proposta();
    p.proponente = users.get(user);
    p.destinatario = users.get(in.destinatarioId());
    p.mensagem = in.mensagem();
    var ids = new HashSet<UUID>();
    var lados = EnumSet.noneOf(Lado.class);
    for (var entry : in.livros()) {
      if (!ids.add(entry.livroId())) throw ApiException.conflict("Livro repetido");
      lados.add(entry.lado());
    }
    if (lados.size() != 2)
      throw ApiException.conflict("Informe pelo menos um livro oferecido e um solicitado");
    var locked = books.lockAll(ids);
    if (locked.size() != ids.size()) throw ApiException.missing();
    var map = new HashMap<UUID, Livro>();
    locked.forEach(i -> map.put(i.id, i));
    for (var entry : in.livros()) validate(p, map.get(entry.livroId()), entry.lado());
    proposals.save(p);
    for (var entry : in.livros()) {
      var link = new PropostaLivro();
      link.proposta = p;
      link.livro = map.get(entry.livroId());
      link.lado = entry.lado();
      links.save(link);
    }
    return output(p);
  }

  private void validate(Proposta p, Livro livro, Lado lado) {
    if (!livro.disponivel) throw ApiException.conflict("Livro indisponível");
    var owner = lado == Lado.OFERECIDO ? p.proponente.id : p.destinatario.id;
    if (!livro.dono.id.equals(owner))
      throw ApiException.conflict("Proprietário do livro incompatível com o lado informado");
  }

  private void require(Proposta p, Status status) {
    if (p.status != status) throw ApiException.conflict("Estado incompatível: " + p.status);
  }

  @Transactional
  public PropostaSaida transicionar(UUID user, UUID id, Status target) {
    if (target == null || target == Status.PENDENTE)
      throw ApiException.conflict("Transição não permitida");
    lock.acquire();
    var p = get(id);
    participant(p, user);
    if (target == Status.ACEITA || target == Status.RECUSADA) {
      if (!p.destinatario.id.equals(user)) throw ApiException.forbidden();
    }
    if (target == Status.CANCELADA && !p.proponente.id.equals(user)) throw ApiException.forbidden();
    require(p, target == Status.CONCLUIDA ? Status.ACEITA : Status.PENDENTE);
    var entries = links.findByPropostaIdOrderById(id);
    if (target == Status.ACEITA || target == Status.CONCLUIDA) {
      var ids = entries.stream().map(l -> l.livro.id).toList();
      books.lockAll(ids);
      if (target == Status.ACEITA) {
        for (var entry : entries) validate(p, entry.livro, entry.lado);
        for (var other : links.pendentes(ids))
          if (!other.id.equals(p.id)) other.status = Status.CANCELADA;
        for (var entry : entries) entry.livro.disponivel = false;
      } else {
        for (var entry : entries) {
          entry.livro.dono = entry.lado == Lado.OFERECIDO ? p.destinatario : p.proponente;
          entry.livro.disponivel = true;
        }
      }
    }
    p.status = target;
    return output(p);
  }

  public Pagina<PropostaSaida> listar(
      UUID user, UUID routeUser, Status status, Participacao participacao, Pageable page) {
    if (!user.equals(routeUser)) throw ApiException.forbidden();
    return Pagina.of(
        proposals
            .findAll(
                (r, q, b) -> {
                  var sent = b.equal(r.get("proponente").get("id"), user);
                  var received = b.equal(r.get("destinatario").get("id"), user);
                  var participant =
                      participacao == null
                          ? b.or(sent, received)
                          : (participacao == Participacao.ENVIADAS ? sent : received);
                  return status == null
                      ? participant
                      : b.and(participant, b.equal(r.get("status"), status));
                },
                page)
            .map(this::output));
  }
}
