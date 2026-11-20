CREATE TABLE usuarios (
 id UUID PRIMARY KEY, nome VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE,
 senha VARCHAR(100) NOT NULL, curso VARCHAR(100), criado_em TIMESTAMPTZ NOT NULL,
 CONSTRAINT email_normalizado CHECK (email = lower(trim(email)))
);
CREATE TABLE categorias (
 id UUID PRIMARY KEY, nome VARCHAR(60) NOT NULL, criado_em TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX categorias_nome_unique ON categorias (lower(trim(nome)));
CREATE TABLE itens (
 id UUID PRIMARY KEY, titulo VARCHAR(120) NOT NULL, descricao VARCHAR(1000),
 condicao VARCHAR(20) NOT NULL CHECK (condicao IN ('NOVO','SEMINOVO','USADO')),
 disponivel BOOLEAN NOT NULL, dono_id UUID NOT NULL REFERENCES usuarios(id),
 categoria_id UUID NOT NULL REFERENCES categorias(id), criado_em TIMESTAMPTZ NOT NULL
);
CREATE TABLE propostas (
 id UUID PRIMARY KEY, proponente_id UUID NOT NULL REFERENCES usuarios(id),
 destinatario_id UUID NOT NULL REFERENCES usuarios(id), mensagem VARCHAR(300),
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE','ACEITA','RECUSADA','CANCELADA','CONCLUIDA')),
 criado_em TIMESTAMPTZ NOT NULL, CHECK (proponente_id <> destinatario_id)
);
CREATE TABLE proposta_itens (
 id UUID PRIMARY KEY, proposta_id UUID NOT NULL REFERENCES propostas(id),
 item_id UUID NOT NULL REFERENCES itens(id),
 lado VARCHAR(20) NOT NULL CHECK (lado IN ('OFERECIDO','SOLICITADO')),
 criado_em TIMESTAMPTZ NOT NULL, UNIQUE(proposta_id,item_id)
);
CREATE TABLE avaliacoes (
 id UUID PRIMARY KEY, proposta_id UUID NOT NULL REFERENCES propostas(id),
 avaliador_id UUID NOT NULL REFERENCES usuarios(id), avaliado_id UUID NOT NULL REFERENCES usuarios(id),
 nota INTEGER NOT NULL CHECK (nota BETWEEN 1 AND 5), comentario VARCHAR(300),
 criado_em TIMESTAMPTZ NOT NULL, UNIQUE(proposta_id,avaliador_id), CHECK(avaliador_id <> avaliado_id)
);
CREATE INDEX itens_dono_idx ON itens(dono_id);
CREATE INDEX itens_categoria_idx ON itens(categoria_id);
CREATE INDEX propostas_proponente_idx ON propostas(proponente_id,criado_em);
CREATE INDEX propostas_destinatario_idx ON propostas(destinatario_id,criado_em);
CREATE INDEX proposta_itens_item_idx ON proposta_itens(item_id);
CREATE INDEX avaliacoes_avaliado_idx ON avaliacoes(avaliado_id,criado_em);
INSERT INTO categorias (id,nome,criado_em) VALUES
 ('10000000-0000-0000-0000-000000000001','Livros',now()),
 ('10000000-0000-0000-0000-000000000002','Materiais de estudo',now()),
 ('10000000-0000-0000-0000-000000000003','Eletrônicos',now());
