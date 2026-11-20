package br.edu.troca;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.edu.troca.dtos.Dtos.*;
import br.edu.troca.exceptions.ApiException;
import br.edu.troca.models.*;
import br.edu.troca.repositories.*;
import br.edu.troca.services.*;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
    r.add("app.jwt-secret", () -> "chave-exclusiva-dos-testes-com-mais-de-32-bytes");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PropostaService proposals;
  @Autowired LivroRepository books;
  @Autowired PropostaRepository proposalRepository;
  @Autowired AvaliacaoService reviews;
  @Autowired UsuarioRepository users;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
  static final String CATEGORY = "10000000-0000-0000-0000-000000000001";

  record Account(UUID id, String token) {}

  record Setup(Account a, Account b, UUID x, UUID y, UUID proposal) {}

  @BeforeEach
  void clean() {
    jdbc.execute("TRUNCATE avaliacoes, proposta_livros, propostas, livros, usuarios CASCADE");
  }

  JsonNode call(MockHttpServletRequestBuilder req, Account user, Object body, int expected)
      throws Exception {
    if (user != null) req.header("Authorization", "Bearer " + user.token());
    if (body != null) req.contentType("application/json").content(json.writeValueAsBytes(body));
    var result = mvc.perform(req).andExpect(status().is(expected)).andReturn();
    String content = result.getResponse().getContentAsString();
    return content.isEmpty() ? json.nullNode() : json.readTree(content);
  }

  Account account(String name) throws Exception {
    var created =
        call(
            post("/usuarios"),
            null,
            Map.of("nome", name, "email", name + "@uni.edu", "senha", "senha1234"),
            201);
    assertThat(created.has("senha")).isFalse();
    var login =
        call(
            post("/auth/login"),
            null,
            Map.of("email", name + "@uni.edu", "senha", "senha1234"),
            200);
    assertThat(login.get("expiresIn").asLong()).isEqualTo(3600);
    assertThat(login.get("tokenType").asText()).isEqualTo("Bearer");
    return new Account(
        UUID.fromString(created.get("id").asText()), login.get("accessToken").asText());
  }

  Map<String, Object> livroInput(String title) {
    return Map.of(
        "titulo", title, "autor", "Autor de teste", "categoriaId", CATEGORY, "condicao", "USADO");
  }

  UUID livro(Account owner, String title) throws Exception {
    var res =
        mvc.perform(
                post("/livros")
                    .header("Authorization", "Bearer " + owner.token())
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(livroInput(title))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();
    return UUID.fromString(
        json.readTree(res.getResponse().getContentAsString()).get("id").asText());
  }

  Map<String, Object> proposalInput(Account recipient, UUID offered, UUID requested) {
    return Map.of(
        "destinatarioId",
        recipient.id(),
        "livros",
        List.of(
            Map.of("livroId", offered, "lado", "OFERECIDO"),
            Map.of("livroId", requested, "lado", "SOLICITADO")));
  }

  UUID proposal(Account sender, Account recipient, UUID offered, UUID requested) throws Exception {
    return UUID.fromString(
        call(post("/propostas"), sender, proposalInput(recipient, offered, requested), 201)
            .get("id")
            .asText());
  }

  Setup setup() throws Exception {
    var a = account("ana");
    var b = account("bruno");
    var x = livro(a, "Cálculo");
    var y = livro(b, "Álgebra");
    return new Setup(a, b, x, y, proposal(a, b, x, y));
  }

  void action(Setup s, String action, Account actor, int status) throws Exception {
    call(patch("/propostas/" + s.proposal() + "/" + action), actor, null, status);
  }

  @Test
  void expiredTokensAndTokensWithWrongIssuerAreRejected() throws Exception {
    var a = account("ana");
    for (boolean expired : List.of(true, false)) {
      var claims =
          org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
              .subject(a.id().toString())
              .issuer(expired ? "troca-livros" : "outro-emissor")
              .issuedAt(Instant.now().minusSeconds(7200))
              .expiresAt(Instant.now().plusSeconds(expired ? -3600 : 3600))
              .build();
      String token =
          jwtEncoder
              .encode(
                  org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                      org.springframework.security.oauth2.jwt.JwsHeader.with(
                              org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                          .build(),
                      claims))
              .getTokenValue();
      var error = call(get("/livros"), new Account(a.id(), token), null, 401);
      assertThat(error.get("code").asText()).isEqualTo("NAO_AUTENTICADO");
    }
    mvc.perform(post("/usuarios").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void bookMetadataIsRequiredValidatedAndReturned() throws Exception {
    var a = account("ana");
    var input = new HashMap<String, Object>(livroInput("Cálculo I"));
    input.remove("autor");
    var error = call(post("/livros"), a, input, 400);
    assertThat(error.get("errors").findValuesAsText("field")).contains("autor");
    input.put("autor", " ");
    call(post("/livros"), a, input, 400);
    input.put("autor", "a".repeat(151));
    call(post("/livros"), a, input, 400);
    input.put("autor", "James Stewart");
    input.put("isbn", "1".repeat(21));
    call(post("/livros"), a, input, 400);
    input.put("isbn", "978-85-221-3660-8");
    var created = call(post("/livros"), a, input, 201);
    assertThat(created.get("autor").asText()).isEqualTo("James Stewart");
    assertThat(created.get("isbn").asText()).isEqualTo(input.get("isbn"));
    UUID id = UUID.fromString(created.get("id").asText());
    var fetched = call(get("/livros/" + id), a, null, 200);
    assertThat(fetched.get("autor")).isEqualTo(created.get("autor"));
    assertThat(fetched.get("isbn")).isEqualTo(created.get("isbn"));
    input.put("autor", "Outro autor");
    input.remove("isbn");
    var updated = call(put("/livros/" + id), a, input, 200);
    assertThat(updated.get("autor").asText()).isEqualTo("Outro autor");
    assertThat(updated.get("isbn").isNull()).isTrue();
    var b = account("bruno");
    UUID y = livro(b, "Álgebra Linear");
    var legacy =
        Map.of(
            "destinatarioId",
            b.id(),
            "itens",
            List.of(
                Map.of("itemId", id, "lado", "OFERECIDO"),
                Map.of("itemId", y, "lado", "SOLICITADO")));
    call(post("/propostas"), a, legacy, 400);
  }

  @Test
  void upgradePreservesExistingBooksOwnersAndExchangeHistory() throws Exception {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    UUID a = UUID.randomUUID(),
        b = UUID.randomUUID(),
        x = UUID.randomUUID(),
        y = UUID.randomUUID(),
        p = UUID.randomUUID(),
        r = UUID.randomUUID();
    try {
      org.flywaydb.core.Flyway.configure()
          .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .target("1")
          .load()
          .migrate();
      try (var connection =
          java.sql.DriverManager.getConnection(
              DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
        connection.setSchema(schema);
        var old =
            new JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                    connection, true));
        for (UUID user : List.of(a, b))
          old.update(
              "INSERT INTO usuarios(id,nome,email,senha,criado_em) VALUES"
                  + " (?,'Usuário',?,'hash',now())",
              user,
              user + "@uni.edu");
        old.update(
            "INSERT INTO itens(id,titulo,condicao,disponivel,dono_id,categoria_id,criado_em) VALUES"
                + " (?,'Cálculo','USADO',true,?,?,now())",
            x,
            b,
            UUID.fromString(CATEGORY));
        old.update(
            "INSERT INTO itens(id,titulo,condicao,disponivel,dono_id,categoria_id,criado_em) VALUES"
                + " (?,'Álgebra','USADO',true,?,?,now())",
            y,
            a,
            UUID.fromString(CATEGORY));
        old.update(
            "INSERT INTO propostas(id,proponente_id,destinatario_id,status,criado_em) VALUES"
                + " (?,?,?,'CONCLUIDA',now())",
            p,
            a,
            b);
        old.update(
            "INSERT INTO proposta_itens(id,proposta_id,item_id,lado,criado_em) VALUES"
                + " (?,?,?,'OFERECIDO',now())",
            UUID.randomUUID(),
            p,
            x);
        old.update(
            "INSERT INTO proposta_itens(id,proposta_id,item_id,lado,criado_em) VALUES"
                + " (?,?,?,'SOLICITADO',now())",
            UUID.randomUUID(),
            p,
            y);
        old.update(
            "INSERT INTO avaliacoes(id,proposta_id,avaliador_id,avaliado_id,nota,criado_em) VALUES"
                + " (?,?,?,?,5,now())",
            r,
            p,
            a,
            b);
        org.flywaydb.core.Flyway.configure()
            .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .load()
            .migrate();
        assertThat(old.queryForObject("SELECT dono_id FROM livros WHERE id=?", UUID.class, x))
            .isEqualTo(b);
        assertThat(old.queryForObject("SELECT autor FROM livros WHERE id=?", String.class, x))
            .isEqualTo("Autor não informado");
        assertThat(
                old.queryForObject(
                    "SELECT count(*) FROM proposta_livros WHERE proposta_id=?", Long.class, p))
            .isEqualTo(2L);
        assertThat(
                old.queryForObject(
                    "SELECT livro_id FROM proposta_livros WHERE proposta_id=? AND lado='OFERECIDO'",
                    UUID.class,
                    p))
            .isEqualTo(x);
        assertThat(old.queryForObject("SELECT status FROM propostas WHERE id=?", String.class, p))
            .isEqualTo("CONCLUIDA");
        assertThat(old.queryForObject("SELECT nota FROM avaliacoes WHERE id=?", Integer.class, r))
            .isEqualTo(5);
        assertThat(
                old.queryForObject(
                    "SELECT nome FROM categorias WHERE id=?",
                    String.class,
                    UUID.fromString(CATEGORY)))
            .isEqualTo("Didáticos");
      }
    } finally {
      jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test
  void manualSqlCreatesTheSameBookColumnsAndCategoriesAsFlyway() throws Exception {
    String schema = "manual_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("CREATE SCHEMA " + schema);
    try (var connection =
        java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
      connection.setSchema(schema);
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          connection, new org.springframework.core.io.FileSystemResource("troca_livros.sql"));
      String columns =
          "SELECT table_name,column_name,data_type,is_nullable,character_maximum_length FROM"
              + " information_schema.columns WHERE table_schema=? AND table_name <>"
              + " 'flyway_schema_history' ORDER BY table_name,column_name";
      assertThat(jdbc.queryForList(columns, schema))
          .isEqualTo(jdbc.queryForList(columns, "public"));
      assertThat(jdbc.queryForList("SELECT id,nome FROM " + schema + ".categorias ORDER BY id"))
          .isEqualTo(jdbc.queryForList("SELECT id,nome FROM categorias ORDER BY id"));
    } finally {
      jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test
  void fullFlowTransfersBooksPreservesSidesAndAcceptsTwoReviews() throws Exception {
    var s = setup();
    call(post("/propostas/" + s.proposal() + "/avaliacoes"), s.a(), Map.of("nota", 5), 409);
    action(s, "aceitar", s.b(), 200);
    assertThat(books.findById(s.x()).orElseThrow().disponivel).isFalse();
    call(put("/livros/" + s.x()), s.a(), livroInput("Alterado"), 409);
    call(delete("/livros/" + s.x()), s.a(), null, 409);
    action(s, "aceitar", s.b(), 409);
    action(s, "concluir", s.a(), 200);
    assertThat(books.findById(s.x()).orElseThrow().dono.id).isEqualTo(s.b().id());
    assertThat(books.findById(s.y()).orElseThrow().dono.id).isEqualTo(s.a().id());
    assertThat(books.findById(s.x()).orElseThrow().disponivel).isTrue();
    var view = call(get("/propostas/" + s.proposal()), s.a(), null, 200);
    assertThat(view.get("livros").findValuesAsText("lado"))
        .containsExactlyInAnyOrder("OFERECIDO", "SOLICITADO");
    action(s, "concluir", s.b(), 409);
    var review =
        call(
            post("/propostas/" + s.proposal() + "/avaliacoes"),
            s.a(),
            Map.of("nota", 5, "comentario", "Ótima troca"),
            201);
    assertThat(review.get("avaliadoId").asText()).isEqualTo(s.b().id().toString());
    call(post("/propostas/" + s.proposal() + "/avaliacoes"), s.b(), Map.of("nota", 4), 201);
    call(post("/propostas/" + s.proposal() + "/avaliacoes"), s.a(), Map.of("nota", 3), 409);
    call(delete("/livros/" + s.x()), s.b(), null, 409);
    var received = call(get("/usuarios/" + s.b().id() + "/avaliacoes"), s.a(), null, 200);
    assertThat(received.get("media").asDouble()).isEqualTo(5.0);
    assertThat(received.get("quantidadeTotal").asLong()).isEqualTo(1);
  }

  @Test
  void permissionsAndAuthentication() throws Exception {
    call(get("/livros"), null, null, 401);
    call(get("/livros").header("Authorization", "Bearer invalido"), null, null, 401);
    call(get("/v3/api-docs"), null, null, 200);
    var s = setup();
    var stranger = account("carla");
    call(put("/livros/" + s.x()), stranger, livroInput("Novo"), 403);
    call(delete("/livros/" + s.x()), stranger, null, 403);
    call(get("/propostas/" + s.proposal()), stranger, null, 403);
    call(get("/usuarios/" + s.a().id() + "/propostas"), stranger, null, 403);
    action(s, "aceitar", s.a(), 403);
    action(s, "recusar", s.a(), 403);
    action(s, "cancelar", s.b(), 403);
    action(s, "concluir", stranger, 403);
    call(post("/propostas/" + s.proposal() + "/avaliacoes"), stranger, Map.of("nota", 5), 403);
    call(post("/auth/login"), null, Map.of("email", "ana@uni.edu", "senha", "incorreta"), 401);
    call(post("/auth/login"), null, Map.of("email", "ausente@uni.edu", "senha", "incorreta"), 401);
    assertThat(users.findByEmail("ana@uni.edu").orElseThrow().senha).startsWith("$2");
    call(get("/livros/" + UUID.randomUUID()), s.a(), null, 404);
  }

  @Test
  void inputValidationAndNormalizedEmail() throws Exception {
    call(post("/usuarios"), null, Map.of("nome", " ", "email", "invalido", "senha", "curta"), 400);
    call(
        post("/usuarios"),
        null,
        Map.of("nome", "Ana", "email", "ana@uni.edu", "senha", "á".repeat(37)),
        400);
    var a = account("ana");
    call(
        post("/usuarios"),
        null,
        Map.of("nome", "Ana", "email", "ANA@UNI.EDU", "senha", "senha1234"),
        409);
    call(
        post("/livros"),
        a,
        Map.of("titulo", "x", "autor", "Autor", "categoriaId", CATEGORY, "condicao", "VELHO"),
        400);
    call(
        post("/livros"),
        a,
        Map.of(
            "titulo",
            "x".repeat(121),
            "autor",
            "Autor",
            "categoriaId",
            CATEGORY,
            "condicao",
            "USADO"),
        400);
    call(
        post("/livros"),
        a,
        Map.of("titulo", "x", "autor", "Autor", "categoriaId", "abc", "condicao", "USADO"),
        400);
    call(
        post("/livros"),
        a,
        Map.of(
            "titulo",
            "x",
            "autor",
            "Autor",
            "categoriaId",
            CATEGORY,
            "condicao",
            "USADO",
            "donoId",
            a.id()),
        400);
    var b = account("bruno");
    var x = livro(a, "Livro");
    var y = livro(b, "Livro B");
    var p = proposal(a, b, x, y);
    call(post("/propostas/" + p + "/avaliacoes"), a, Map.of("nota", 0), 400);
    call(post("/propostas/" + p + "/avaliacoes"), a, Map.of("nota", 4.5), 400);
    call(
        post("/propostas/" + p + "/avaliacoes"),
        a,
        Map.of("nota", 6, "comentario", "a".repeat(301)),
        400);
    call(get("/livros/abc"), a, null, 400);
    var error = call(get("/livros?sort=senha,asc"), a, null, 400);
    assertThat(error.get("code").asText()).isEqualTo("PAGINACAO_INVALIDA");
  }

  @Test
  void proposalRulesAndRevalidation() throws Exception {
    var s = setup();
    call(post("/propostas"), s.a(), proposalInput(s.a(), s.x(), s.x()), 409);
    call(post("/propostas"), s.a(), proposalInput(s.b(), s.x(), s.x()), 409);
    call(post("/propostas"), s.a(), proposalInput(s.b(), s.y(), s.x()), 409);
    call(
        post("/propostas"),
        s.a(),
        Map.of(
            "destinatarioId",
            s.b().id(),
            "livros",
            List.of(Map.of("livroId", s.x(), "lado", "OFERECIDO"))),
        409);
    call(post("/propostas"), s.a(), proposalInput(s.b(), UUID.randomUUID(), s.y()), 404);
    var changed = new HashMap<>(livroInput("Cálculo"));
    changed.put("disponivel", false);
    call(put("/livros/" + s.x()), s.a(), changed, 200);
    action(s, "aceitar", s.b(), 409);
    assertThat(proposalRepository.findById(s.proposal()).orElseThrow().status)
        .isEqualTo(Status.PENDENTE);
    assertThat(books.findById(s.y()).orElseThrow().disponivel).isTrue();
    call(post("/propostas"), s.a(), proposalInput(s.b(), s.x(), s.y()), 409);
  }

  @Test
  void transitionsAndPhysicalDeletion() throws Exception {
    var s = setup();
    action(s, "concluir", s.a(), 409);
    action(s, "recusar", s.b(), 200);
    action(s, "aceitar", s.b(), 409);
    call(delete("/livros/" + s.x()), s.a(), null, 409);
    UUID second = proposal(s.a(), s.b(), s.x(), s.y());
    call(patch("/propostas/" + second + "/cancelar"), s.a(), null, 200);
    call(patch("/propostas/" + second + "/cancelar"), s.a(), null, 409);
    UUID fresh = livro(s.a(), "Sem histórico");
    call(delete("/livros/" + fresh), s.a(), null, 204);
    assertThat(books.existsById(fresh)).isFalse();
  }

  @Test
  void simultaneousAcceptancesReserveExactlyOneProposalWithoutPartialChanges() throws Exception {
    var s = setup();
    var c = account("carla");
    UUID z = livro(c, "Física");
    UUID other = proposal(c, s.b(), z, s.y());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      Callable<Integer> first =
          () -> {
            start.await();
            try {
              proposals.transicionar(s.b().id(), s.proposal(), Status.ACEITA);
              return 200;
            } catch (ApiException e) {
              return e.status;
            }
          };
      Callable<Integer> second =
          () -> {
            start.await();
            try {
              proposals.transicionar(s.b().id(), other, Status.ACEITA);
              return 200;
            } catch (ApiException e) {
              return e.status;
            }
          };
      var f = executor.submit(first);
      var g = executor.submit(second);
      start.countDown();
      assertThat(List.of(f.get(20, TimeUnit.SECONDS), g.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    var p = proposalRepository.findById(s.proposal()).orElseThrow();
    var q = proposalRepository.findById(other).orElseThrow();
    assertThat(List.of(p.status, q.status))
        .containsExactlyInAnyOrder(Status.ACEITA, Status.CANCELADA);
    assertThat(books.findById(s.y()).orElseThrow().disponivel).isFalse();
    assertThat(books.findById(s.x()).orElseThrow().disponivel).isEqualTo(p.status != Status.ACEITA);
    assertThat(books.findById(z).orElseThrow().disponivel).isEqualTo(q.status != Status.ACEITA);
    assertThat(books.findById(s.x()).orElseThrow().dono.id).isEqualTo(s.a().id());
    assertThat(books.findById(z).orElseThrow().dono.id).isEqualTo(c.id());
  }

  @Test
  void paginationCombinedFiltersAndStableSorting() throws Exception {
    var s = setup();
    UUID x2 = livro(s.a(), "Cálculo");
    livro(s.a(), "Outra matéria");
    String query =
        "/livros?categoriaId="
            + CATEGORY
            + "&condicao=USADO&disponivel=true&donoId="
            + s.a().id()
            + "&titulo=cálc&sort=titulo,asc&size=1";
    var first = call(get(query), s.a(), null, 200);
    var second = call(get(query + "&page=1"), s.a(), null, 200);
    assertThat(first.get("totalElements").asInt()).isEqualTo(2);
    assertThat(first.get("totalPages").asInt()).isEqualTo(2);
    assertThat(first.at("/content/0/id").asText())
        .isNotEqualTo(second.at("/content/0/id").asText());
    var sorted = List.of(s.x(), x2).stream().map(UUID::toString).sorted().toList();
    assertThat(first.at("/content/0/id").asText()).isEqualTo(sorted.getFirst());
    var literal = livro(s.a(), "100% livro");
    assertThat(
            call(get("/livros").param("titulo", "%"), s.a(), null, 200)
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    assertThat(
            call(get("/categorias?nome=DID&sort=nome,asc"), s.a(), null, 200)
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    var sent =
        call(
            get("/usuarios/" + s.a().id() + "/propostas?participacao=ENVIADAS&status=PENDENTE"),
            s.a(),
            null,
            200);
    assertThat(sent.get("totalElements").asInt()).isEqualTo(1);
    assertThat(
            call(
                    get("/usuarios/" + s.a().id() + "/propostas?participacao=RECEBIDAS"),
                    s.a(),
                    null,
                    200)
                .get("totalElements")
                .asInt())
        .isZero();
    for (String invalid :
        List.of(
            "size=101",
            "size=0",
            "page=-1",
            "sort=donoId,asc",
            "sort=titulo,up",
            "sort=titulo,asc,extra")) call(get("/livros?" + invalid), s.a(), null, 400);
    var empty = call(get("/usuarios/" + s.a().id() + "/avaliacoes"), s.a(), null, 200);
    assertThat(empty.get("media").isNull()).isTrue();
    assertThat(empty.get("quantidadeTotal").asInt()).isZero();
  }

  @Test
  void reviewAggregateIsIndependentOfPage() throws Exception {
    var s = setup();
    action(s, "aceitar", s.b(), 200);
    action(s, "concluir", s.b(), 200);
    reviews.avaliar(s.a().id(), s.proposal(), new AvaliacaoEntrada(5, null));
    UUID next = proposal(s.a(), s.b(), s.y(), s.x());
    proposals.transicionar(s.b().id(), next, Status.ACEITA);
    proposals.transicionar(s.a().id(), next, Status.CONCLUIDA);
    reviews.avaliar(s.a().id(), next, new AvaliacaoEntrada(3, null));
    var result =
        call(get("/usuarios/" + s.b().id() + "/avaliacoes?size=1&sort=nota,asc"), s.a(), null, 200);
    assertThat(result.get("media").asDouble()).isEqualTo(4.0);
    assertThat(result.get("quantidadeTotal").asInt()).isEqualTo(2);
    assertThat(result.at("/avaliacoes/content/0/nota").asInt()).isEqualTo(3);
    assertThat(result.at("/avaliacoes/totalElements").asInt()).isEqualTo(2);
  }

  @Test
  void databaseRejectsInvalidEnumsDuplicateCategoriesAndHistoricalDeletion() throws Exception {
    var s = setup();
    assertThatThrownBy(() -> jdbc.update("UPDATE livros SET condicao='INVALIDO' WHERE id=?", s.x()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO categorias(id,nome,criado_em) VALUES (?,'didáticos',now())",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM livros WHERE id=?", s.x()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE propostas SET destinatario_id=proponente_id WHERE id=?", s.proposal()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO proposta_livros(id,proposta_id,livro_id,lado,criado_em) VALUES"
                        + " (?,?,?,'OFERECIDO',now())",
                    UUID.randomUUID(),
                    s.proposal(),
                    s.x()))
        .isInstanceOf(DataIntegrityViolationException.class);
    action(s, "aceitar", s.b(), 200);
    action(s, "concluir", s.b(), 200);
    var a = reviews.avaliar(s.a().id(), s.proposal(), new AvaliacaoEntrada(4, null));
    assertThatThrownBy(() -> jdbc.update("UPDATE avaliacoes SET nota=6 WHERE id=?", a.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO avaliacoes(id,proposta_id,avaliador_id,avaliado_id,nota,criado_em)"
                        + " VALUES (?,?,?,?,5,now())",
                    UUID.randomUUID(),
                    s.proposal(),
                    s.a().id(),
                    s.b().id()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
