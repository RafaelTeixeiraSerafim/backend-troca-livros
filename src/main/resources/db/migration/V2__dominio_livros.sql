-- Preserva os UUIDs e o histórico da primeira versão da API.
ALTER TABLE itens RENAME TO livros;
ALTER TABLE proposta_itens RENAME TO proposta_livros;
ALTER TABLE proposta_livros RENAME COLUMN item_id TO livro_id;

ALTER TABLE livros ADD COLUMN autor VARCHAR(150);
UPDATE livros SET autor = 'Autor não informado' WHERE autor IS NULL;
ALTER TABLE livros ALTER COLUMN autor SET NOT NULL;
ALTER TABLE livros ADD COLUMN isbn VARCHAR(20);

ALTER INDEX itens_dono_idx RENAME TO livros_dono_idx;
ALTER INDEX itens_categoria_idx RENAME TO livros_categoria_idx;
ALTER INDEX proposta_itens_item_idx RENAME TO proposta_livros_livro_idx;

UPDATE categorias SET nome = 'Didáticos'
WHERE id = '10000000-0000-0000-0000-000000000001' AND nome = 'Livros';
UPDATE categorias SET nome = 'Literatura'
WHERE id = '10000000-0000-0000-0000-000000000002' AND nome = 'Materiais de estudo';
UPDATE categorias SET nome = 'Tecnologia'
WHERE id = '10000000-0000-0000-0000-000000000003' AND nome = 'Eletrônicos';
