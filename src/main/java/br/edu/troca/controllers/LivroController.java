package br.edu.troca.controllers;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.models.Condicao;
import br.edu.troca.services.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class LivroController {
  private final LivroService books;

  public LivroController(LivroService i) {
    books = i;
  }

  @PostMapping("/livros")
  public ResponseEntity<LivroSaida> criar(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LivroEntrada in) {
    var i = books.criar(UUID.fromString(jwt.getSubject()), in);
    return ResponseEntity.created(URI.create("/livros/" + i.id())).body(i);
  }

  @GetMapping("/livros/{id}")
  public LivroSaida consultar(@PathVariable UUID id) {
    return books.consultar(id);
  }

  @PutMapping("/livros/{id}")
  public LivroSaida atualizar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody LivroEntrada in) {
    return books.atualizar(UUID.fromString(jwt.getSubject()), id, in);
  }

  @DeleteMapping("/livros/{id}")
  public ResponseEntity<Void> excluir(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    books.excluir(UUID.fromString(jwt.getSubject()), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/livros")
  public Pagina<LivroSaida> listar(
      @RequestParam(required = false) UUID categoriaId,
      @RequestParam(required = false) Condicao condicao,
      @RequestParam(required = false) Boolean disponivel,
      @RequestParam(required = false) UUID donoId,
      @RequestParam(required = false) String titulo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return books.listar(
        categoriaId,
        condicao,
        disponivel,
        donoId,
        titulo,
        Pages.of(page, size, sort, "criadoEm,desc", "criadoEm", "titulo"));
  }

  @GetMapping("/categorias")
  public Pagina<CategoriaSaida> categorias(
      @RequestParam(required = false) String nome,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return books.categorias(nome, Pages.of(page, size, sort, "nome,asc", "nome"));
  }
}
