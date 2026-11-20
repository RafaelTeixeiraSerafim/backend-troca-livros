package br.edu.troca.services;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LivroService {
  private final LivroRepository books;
  private final CategoriaRepository categories;
  private final PropostaLivroRepository links;
  private final UsuarioService users;
  private final MutationLock lock;

  public LivroService(
      LivroRepository i,
      CategoriaRepository c,
      PropostaLivroRepository p,
      UsuarioService u,
      MutationLock l) {
    books = i;
    categories = c;
    links = p;
    users = u;
    lock = l;
  }

  public Livro get(UUID id) {
    return books.findById(id).orElseThrow(ApiException::missing);
  }

  public LivroSaida consultar(UUID id) {
    return LivroSaida.of(get(id));
  }

  private void fill(Livro i, LivroEntrada in) {
    i.titulo = in.titulo().strip();
    i.autor = in.autor().strip();
    i.isbn = in.isbn();
    i.descricao = in.descricao();
    i.condicao = in.condicao();
    i.categoria = categories.findById(in.categoriaId()).orElseThrow(ApiException::missing);
    if (in.disponivel() != null) i.disponivel = in.disponivel();
  }

  @Transactional
  public LivroSaida criar(UUID user, LivroEntrada in) {
    lock.acquire();
    var i = new Livro();
    i.dono = users.get(user);
    fill(i, in);
    return LivroSaida.of(books.save(i));
  }

  @Transactional
  public LivroSaida atualizar(UUID user, UUID id, LivroEntrada in) {
    lock.acquire();
    var i = get(id);
    owner(i, user);
    if (links.reservado(id)) throw ApiException.conflict("Livro reservado por troca aceita");
    fill(i, in);
    return LivroSaida.of(i);
  }

  @Transactional
  public void excluir(UUID user, UUID id) {
    lock.acquire();
    var i = get(id);
    owner(i, user);
    if (links.existsByLivroId(id))
      throw ApiException.conflict("Livro possui histórico de propostas");
    books.delete(i);
  }

  private void owner(Livro i, UUID user) {
    if (!i.dono.id.equals(user)) throw ApiException.forbidden();
  }

  public Pagina<LivroSaida> listar(
      UUID categoria,
      Condicao condicao,
      Boolean disponivel,
      UUID dono,
      String titulo,
      Pageable page) {
    Specification<Livro> spec =
        (r, q, b) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          if (categoria != null) p.add(b.equal(r.get("categoria").get("id"), categoria));
          if (condicao != null) p.add(b.equal(r.get("condicao"), condicao));
          if (disponivel != null) p.add(b.equal(r.get("disponivel"), disponivel));
          if (dono != null) p.add(b.equal(r.get("dono").get("id"), dono));
          if (titulo != null)
            p.add(
                b.like(
                    b.lower(r.get("titulo")),
                    "%" + escape(titulo.toLowerCase(Locale.ROOT)) + "%",
                    '\\'));
          return b.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return Pagina.of(books.findAll(spec, page).map(LivroSaida::of));
  }

  static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  public Pagina<CategoriaSaida> categorias(String nome, Pageable page) {
    return Pagina.of(
        categories
            .findAll(
                (r, q, b) ->
                    nome == null
                        ? b.conjunction()
                        : b.like(
                            b.lower(r.get("nome")),
                            "%" + escape(nome.toLowerCase(Locale.ROOT)) + "%",
                            '\\'),
                page)
            .map(CategoriaSaida::of));
  }
}
