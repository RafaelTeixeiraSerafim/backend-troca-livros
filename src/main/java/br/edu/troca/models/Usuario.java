package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class Usuario {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @Column(nullable = false, length = 100)
  public String nome;

  @Column(nullable = false, length = 150, unique = true)
  public String email;

  @Column(nullable = false, length = 100)
  public String senha;

  @Column(length = 100)
  public String curso;
}
