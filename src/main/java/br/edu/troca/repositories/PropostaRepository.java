package br.edu.troca.repositories;

import br.edu.troca.models.*;
import org.springframework.data.jpa.repository.*;

public interface PropostaRepository
    extends JpaRepository<Proposta, java.util.UUID>, JpaSpecificationExecutor<Proposta> {}
