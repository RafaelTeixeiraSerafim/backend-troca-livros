package br.edu.troca.services;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
  private final UsuarioRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder jwt;

  public UsuarioService(UsuarioRepository users, PasswordEncoder passwords, JwtEncoder jwt) {
    this.users = users;
    this.passwords = passwords;
    this.jwt = jwt;
  }

  public static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public UsuarioSaida cadastrar(Cadastro in) {
    var email = normalize(in.email());
    if (users.findByEmail(email).isPresent()) throw ApiException.conflict("Email já cadastrado");
    var u = new Usuario();
    u.nome = in.nome().strip();
    u.email = email;
    u.senha = passwords.encode(in.senha());
    u.curso = in.curso();
    return UsuarioSaida.of(users.saveAndFlush(u));
  }

  @Transactional(readOnly = true)
  public Token login(Login in) {
    var user = users.findByEmail(normalize(in.email())).orElse(null);
    // Hash válido usado somente para equalizar o custo de login de usuário inexistente.
    String hash =
        user == null ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" : user.senha;
    if (!passwords.matches(in.senha(), hash) || user == null)
      throw new ApiException(401, "CREDENCIAIS_INVALIDAS", "Email ou senha inválidos");
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("troca-livros")
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();
    return new Token(
        jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "Bearer",
        3600);
  }

  public Usuario get(UUID id) {
    return users.findById(id).orElseThrow(ApiException::missing);
  }
}
