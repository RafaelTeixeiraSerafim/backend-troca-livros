package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "avaliacoes")
public class Avaliacao {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @ManyToOne(optional = false)
  @JoinColumn(name = "proposta_id")
  public Proposta proposta;

  @ManyToOne(optional = false)
  @JoinColumn(name = "avaliador_id")
  public Usuario avaliador;

  @ManyToOne(optional = false)
  @JoinColumn(name = "avaliado_id")
  public Usuario avaliado;

  @Column(nullable = false)
  public int nota;

  @Column(length = 300)
  public String comentario;
}
