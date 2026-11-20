package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposta_livros")
public class PropostaLivro {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @ManyToOne(optional = false)
  @JoinColumn(name = "proposta_id")
  public Proposta proposta;

  @ManyToOne(optional = false)
  @JoinColumn(name = "livro_id")
  public Livro livro;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Lado lado;
}
