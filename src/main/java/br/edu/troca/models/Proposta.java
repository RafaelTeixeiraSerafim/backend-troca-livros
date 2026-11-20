package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "propostas")
public class Proposta {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @ManyToOne(optional = false)
  @JoinColumn(name = "proponente_id")
  public Usuario proponente;

  @ManyToOne(optional = false)
  @JoinColumn(name = "destinatario_id")
  public Usuario destinatario;

  @Column(length = 300)
  public String mensagem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Status status = Status.PENDENTE;
}
