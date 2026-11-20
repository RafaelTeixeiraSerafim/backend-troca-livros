package br.edu.troca.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "livros")
public class Livro {
  @Id public UUID id = UUID.randomUUID();

  @Column(name = "criado_em", nullable = false, updatable = false)
  public Instant criadoEm = Instant.now();

  @Column(nullable = false, length = 120)
  public String titulo;

  @Column(nullable = false, length = 150)
  public String autor;

  @Column(length = 20)
  public String isbn;

  @Column(length = 1000)
  public String descricao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Condicao condicao;

  @Column(nullable = false)
  public boolean disponivel = true;

  @ManyToOne(optional = false)
  @JoinColumn(name = "dono_id")
  public Usuario dono;

  @ManyToOne(optional = false)
  @JoinColumn(name = "categoria_id")
  public Categoria categoria;
}
