package br.edu.troca.dtos;

import br.edu.troca.models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;

public final class Dtos {
  private Dtos() {}

  public record Cadastro(
      @NotBlank @Size(max = 100) String nome,
      @NotBlank @Email @Size(max = 150) String email,
      @NotBlank @Senha String senha,
      @Size(max = 100) String curso) {}

  public record Login(
      @NotBlank @Email @Size(max = 150) String email, @NotBlank @Senha String senha) {}

  public record Token(String accessToken, String tokenType, long expiresIn) {}

  public record UsuarioSaida(UUID id, String nome, String email, String curso) {
    public static UsuarioSaida of(Usuario u) {
      return new UsuarioSaida(u.id, u.nome, u.email, u.curso);
    }
  }

  public record LivroEntrada(
      @NotBlank @Size(max = 120) String titulo,
      @NotBlank @Size(max = 150) String autor,
      @Size(max = 20) String isbn,
      @Size(max = 1000) String descricao,
      @NotNull Condicao condicao,
      @NotNull UUID categoriaId,
      Boolean disponivel) {}

  public record LivroSaida(
      UUID id,
      String titulo,
      String autor,
      String isbn,
      String descricao,
      Condicao condicao,
      boolean disponivel,
      UUID donoId,
      UUID categoriaId,
      Instant criadoEm) {
    public static LivroSaida of(Livro i) {
      return new LivroSaida(
          i.id,
          i.titulo,
          i.autor,
          i.isbn,
          i.descricao,
          i.condicao,
          i.disponivel,
          i.dono.id,
          i.categoria.id,
          i.criadoEm);
    }
  }

  public record CategoriaSaida(UUID id, String nome) {
    public static CategoriaSaida of(Categoria c) {
      return new CategoriaSaida(c.id, c.nome);
    }
  }

  public record LivroPropostaEntrada(@NotNull UUID livroId, @NotNull Lado lado) {}

  public record PropostaEntrada(
      @NotNull UUID destinatarioId,
      @Size(max = 300) String mensagem,
      @NotEmpty @Size(max = 100) List<@NotNull @Valid LivroPropostaEntrada> livros) {}

  public record LivroPropostaSaida(UUID livroId, Lado lado) {}

  public record PropostaSaida(
      UUID id,
      UUID proponenteId,
      UUID destinatarioId,
      String mensagem,
      Status status,
      Instant criadoEm,
      List<LivroPropostaSaida> livros) {}

  public record AvaliacaoEntrada(
      @NotNull @Min(1) @Max(5) Integer nota, @Size(max = 300) String comentario) {}

  public record AvaliacaoSaida(
      UUID id,
      UUID propostaId,
      UUID avaliadorId,
      UUID avaliadoId,
      int nota,
      String comentario,
      Instant criadoEm) {
    public static AvaliacaoSaida of(Avaliacao a) {
      return new AvaliacaoSaida(
          a.id, a.proposta.id, a.avaliador.id, a.avaliado.id, a.nota, a.comentario, a.criadoEm);
    }
  }

  public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> Pagina<T> of(Page<T> p) {
      return new Pagina<>(
          p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
  }

  public record AvaliacoesRecebidas(
      Pagina<AvaliacaoSaida> avaliacoes, Double media, long quantidadeTotal) {}

  public enum Participacao {
    ENVIADAS,
    RECEBIDAS
  }
}
