package br.edu.troca.services;

import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevData implements CommandLineRunner {
  private final UsuarioRepository users;
  private final LivroRepository books;
  private final CategoriaRepository categories;
  private final PasswordEncoder passwords;
  private final String password;

  public DevData(
      UsuarioRepository u,
      LivroRepository i,
      CategoriaRepository c,
      PasswordEncoder p,
      @Value("${DEMO_PASSWORD}") String password) {
    users = u;
    books = i;
    categories = c;
    passwords = p;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(String... args) {
    int bytes = password.getBytes(StandardCharsets.UTF_8).length;
    if (bytes < 8 || bytes > 72)
      throw new IllegalArgumentException("DEMO_PASSWORD deve ter entre 8 e 72 bytes");
    for (int n = 1; n <= 2; n++) {
      String email = n == 1 ? "ana@universidade.edu" : "bruno@universidade.edu";
      if (users.findByEmail(email).isPresent()) continue;
      var u = new Usuario();
      u.id = UUID.fromString("20000000-0000-0000-0000-00000000000" + n);
      u.nome = n == 1 ? "Ana" : "Bruno";
      u.email = email;
      u.senha = passwords.encode(password);
      u.curso = "Computação";
      users.save(u);
      var i = new Livro();
      i.id = UUID.fromString("30000000-0000-0000-0000-00000000000" + n);
      i.titulo = n == 1 ? "Cálculo I" : "Álgebra Linear";
      i.autor = n == 1 ? "James Stewart" : "Gilbert Strang";
      i.condicao = Condicao.USADO;
      i.dono = u;
      i.categoria =
          categories
              .findById(UUID.fromString("10000000-0000-0000-0000-000000000001"))
              .orElseThrow();
      books.save(i);
    }
  }
}
