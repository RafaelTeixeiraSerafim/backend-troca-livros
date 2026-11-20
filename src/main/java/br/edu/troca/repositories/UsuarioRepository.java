package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface UsuarioRepository
    extends JpaRepository<Usuario, java.util.UUID>, JpaSpecificationExecutor<Usuario> {
  java.util.Optional<Usuario> findByEmail(String email);
}
