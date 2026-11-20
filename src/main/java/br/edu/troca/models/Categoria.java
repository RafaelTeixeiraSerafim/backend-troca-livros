package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categorias")
public class Categoria {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @Column(nullable = false, length = 60)
  public String nome;
}
