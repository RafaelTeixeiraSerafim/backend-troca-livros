#!/usr/bin/env python3
"""Executa o fluxo HTTP completo contra uma instância local, sem dependências extras."""
import json
import os
import urllib.request
import uuid

BASE = os.getenv("BASE_URL", "http://localhost:8080")
PASSWORD = "teste-" + uuid.uuid4().hex

def call(method, path, payload=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        assert response.status == expected, (path, response.status)
        if expected == 201:
            assert response.headers.get("Location"), path
        content = response.read()
        return json.loads(content) if content else None

def user(name):
    email = f"{name}.{uuid.uuid4().hex}@universidade.edu"
    registered = call("POST", "/usuarios", {"nome": name, "email": email, "senha": PASSWORD}, expected=201)
    assert "senha" not in registered
    login = call("POST", "/auth/login", {"email": email, "senha": PASSWORD})
    return registered["id"], login["accessToken"]

ana, ta = user("Ana")
bruno, tb = user("Bruno")
category = call("GET", "/categorias", token=ta)["content"][0]["id"]

def livro(title, token):
    return call("POST", "/livros", {"titulo": title, "autor": "Autor de demonstração", "condicao": "USADO", "categoriaId": category}, token, 201)["id"]

x, y = livro("Cálculo I", ta), livro("Álgebra Linear", tb)
p = call("POST", "/propostas", {"destinatarioId": bruno, "livros": [
    {"livroId": x, "lado": "OFERECIDO"}, {"livroId": y, "lado": "SOLICITADO"}]}, ta, 201)["id"]
accepted = call("PATCH", f"/propostas/{p}/aceitar", token=tb)
assert accepted["status"] == "ACEITA"
assert call("GET", f"/livros/{x}", token=ta)["disponivel"] is False
completed = call("PATCH", f"/propostas/{p}/concluir", token=ta)
assert completed["status"] == "CONCLUIDA"
for livro_id, owner in [(x, bruno), (y, ana)]:
    transferred = call("GET", f"/livros/{livro_id}", token=ta)
    assert transferred["donoId"] == owner and transferred["disponivel"] is True
for token, rating in [(ta, 5), (tb, 4)]:
    call("POST", f"/propostas/{p}/avaliacoes", {"nota": rating, "comentario": "Troca concluída"}, token, 201)
received = call("GET", f"/usuarios/{bruno}/avaliacoes", token=ta)
assert received["media"] == 5 and received["quantidadeTotal"] == 1
print(json.dumps({"resultado": "Fluxo completo validado", "propostaId": p, "anaId": ana, "brunoId": bruno}, ensure_ascii=False, indent=2))
