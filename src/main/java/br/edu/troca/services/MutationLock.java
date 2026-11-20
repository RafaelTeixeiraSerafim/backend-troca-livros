package br.edu.troca.services;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/**
 * Serializa mutações do catálogo/trocas entre instâncias da API. Lock liberado no commit/rollback.
 */
@Component
public class MutationLock {
  private final EntityManager em;

  public MutationLock(EntityManager em) {
    this.em = em;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void acquire() {
    em.createNativeQuery("set local lock_timeout = '10s'").executeUpdate();
    em.createNativeQuery("select pg_advisory_xact_lock(684216391)").getSingleResult();
  }
}
