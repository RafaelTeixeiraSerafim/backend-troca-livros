package br.edu.troca.controllers;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.models.Status;
import br.edu.troca.services.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropostaController {
  private final PropostaService proposals;

  public PropostaController(PropostaService p) {
    proposals = p;
  }

  private UUID user(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  @PostMapping("/propostas")
  public ResponseEntity<PropostaSaida> criar(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PropostaEntrada in) {
    var p = proposals.criar(user(jwt), in);
    return ResponseEntity.created(URI.create("/propostas/" + p.id())).body(p);
  }

  @GetMapping("/propostas/{id}")
  public PropostaSaida consultar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return proposals.consultar(user(jwt), id);
  }

  @GetMapping("/usuarios/{id}/propostas")
  public Pagina<PropostaSaida> listar(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Participacao participacao,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    return proposals.listar(
        user(jwt),
        id,
        status,
        participacao,
        Pages.of(page, size, sort, "criadoEm,desc", "criadoEm"));
  }

  @PatchMapping("/propostas/{id}/aceitar")
  public PropostaSaida aceitar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return proposals.transicionar(user(jwt), id, Status.ACEITA);
  }

  @PatchMapping("/propostas/{id}/recusar")
  public PropostaSaida recusar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return proposals.transicionar(user(jwt), id, Status.RECUSADA);
  }

  @PatchMapping("/propostas/{id}/cancelar")
  public PropostaSaida cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return proposals.transicionar(user(jwt), id, Status.CANCELADA);
  }

  @PatchMapping("/propostas/{id}/concluir")
  public PropostaSaida concluir(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return proposals.transicionar(user(jwt), id, Status.CONCLUIDA);
  }
}
