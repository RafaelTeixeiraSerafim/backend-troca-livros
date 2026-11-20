package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface CategoriaRepository
    extends JpaRepository<Categoria, java.util.UUID>, JpaSpecificationExecutor<Categoria> {}
