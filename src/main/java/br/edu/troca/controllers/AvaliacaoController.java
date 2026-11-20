package br.edu.troca.controllers;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.services.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class AvaliacaoController {
  private final AvaliacaoService reviews;

  public AvaliacaoController(AvaliacaoService r) {
    reviews = r;
  }

  @PostMapping("/propostas/{id}/avaliacoes")
  public ResponseEntity<AvaliacaoSaida> avaliar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AvaliacaoEntrada in) {
    var a = reviews.avaliar(UUID.fromString(jwt.getSubject()), id, in);
    return ResponseEntity.created(URI.create("/usuarios/" + a.avaliadoId() + "/avaliacoes"))
        .body(a);
  }

  @GetMapping("/usuarios/{id}/avaliacoes")
  public AvaliacoesRecebidas recebidas(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return reviews.recebidas(id, Pages.of(page, size, sort, "criadoEm,desc", "criadoEm", "nota"));
  }
}
