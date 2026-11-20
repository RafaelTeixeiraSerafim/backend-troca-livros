package br.edu.troca.controllers;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.services.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UsuarioController {
  private final UsuarioService users;

  public UsuarioController(UsuarioService u) {
    users = u;
  }

  @PostMapping("/usuarios")
  @SecurityRequirements
  public ResponseEntity<UsuarioSaida> cadastrar(@Valid @RequestBody Cadastro in) {
    var u = users.cadastrar(in);
    return ResponseEntity.created(URI.create("/usuarios/" + u.id())).body(u);
  }

  @PostMapping("/auth/login")
  @SecurityRequirements
  public Token login(@Valid @RequestBody Login in) {
    return users.login(in);
  }
}
