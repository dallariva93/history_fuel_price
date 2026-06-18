#!/usr/bin/env python3
"""
Sync incrementale del DB locale SQLite verso Turso (SQLite-as-a-service).

Pusha solo gli eventi nuovi usando un watermark locale (ultimo prezzi.id spinto).
Idempotente: rilanciarlo non crea duplicati grazie a INSERT OR IGNORE su UNIQUE.

Dipendenze: solo stdlib. Comunica col DB Turso via API HTTP /v2/pipeline.
URL e token letti da TURSO_DATABASE_URL e TURSO_AUTH_TOKEN (env o file .env).
"""

import json
import os
import sqlite3
import sys
import urllib.request

from raccogli import DB_PATH

BATCH = 500  # statements per HTTP request

SCHEMA = [
    """CREATE TABLE IF NOT EXISTS impianti(
         id INTEGER PRIMARY KEY, gestore TEXT, bandiera TEXT, tipo TEXT, nome TEXT,
         indirizzo TEXT, comune TEXT, provincia TEXT, lat REAL, lon REAL, updated TEXT)""",
    """CREATE TABLE IF NOT EXISTS prezzi(
         id INTEGER PRIMARY KEY, impianto_id INTEGER, carburante TEXT, self INTEGER,
         prezzo REAL, dt_comu TEXT, first_seen TEXT,
         UNIQUE(impianto_id, carburante, self, dt_comu, prezzo))""",
    "CREATE INDEX IF NOT EXISTS idx_prezzi_lookup ON prezzi(impianto_id, carburante, self, dt_comu)",
]


def carica_env(path=".env"):
    """Carica KEY=VALUE da un file .env (se esiste) nelle env vars del processo."""
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


def arg(value):
    """Converte un valore Python nel formato args richiesto dall'API Hrana."""
    if value is None:
        return {"type": "null", "value": None}
    if isinstance(value, bool):
        return {"type": "integer", "value": "1" if value else "0"}
    if isinstance(value, int):
        return {"type": "integer", "value": str(value)}
    if isinstance(value, float):
        return {"type": "float", "value": value}
    return {"type": "text", "value": str(value)}


def pipeline(endpoint, token, statements):
    """Esegue una pipeline di statements via POST /v2/pipeline.

    statements: lista di (sql, args_list).
    Ritorna la lista dei result objects (uno per statement).
    Solleva RuntimeError se uno statement fallisce.
    """
    requests = [
        {"type": "execute", "stmt": {"sql": sql, "args": [arg(a) for a in args]}}
        for sql, args in statements
    ]
    requests.append({"type": "close"})
    body = json.dumps({"requests": requests}).encode("utf-8")
    req = urllib.request.Request(
        endpoint,
        data=body,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        payload = json.loads(resp.read())
    results = payload.get("results", [])
    for i, r in enumerate(results[:-1]):  # ultimo è 'close'
        if r.get("type") != "ok":
            raise RuntimeError(f"Statement {i} fallito: {r}")
    return results


def esegui_batch(endpoint, token, statements):
    """Spezza la lista di statements in chunk e li invia in pipeline successive."""
    for i in range(0, len(statements), BATCH):
        pipeline(endpoint, token, statements[i:i + BATCH])


def main():
    carica_env()
    url = os.environ.get("TURSO_DATABASE_URL")
    token = os.environ.get("TURSO_AUTH_TOKEN")
    if not url or not token:
        print("Errore: TURSO_DATABASE_URL e TURSO_AUTH_TOKEN devono essere nelle env vars.",
              file=sys.stderr)
        return 1

    # libsql://host -> https://host/v2/pipeline
    endpoint = url.replace("libsql://", "https://", 1).rstrip("/") + "/v2/pipeline"

    loc = sqlite3.connect(DB_PATH)
    loc.execute("CREATE TABLE IF NOT EXISTS sync_state(target TEXT PRIMARY KEY, last_id INTEGER)")

    r = loc.execute("SELECT last_id FROM sync_state WHERE target='turso'").fetchone()
    last_id = r[0] if r else 0

    # Schema lato remoto (idempotente)
    pipeline(endpoint, token, [(s, []) for s in SCHEMA])

    # Upsert impianti (tabella piccola, ricaricata interamente)
    impianti = loc.execute(
        "SELECT id,gestore,bandiera,tipo,nome,indirizzo,comune,provincia,lat,lon,updated FROM impianti"
    ).fetchall()
    sql_imp = """INSERT INTO impianti(id,gestore,bandiera,tipo,nome,indirizzo,comune,provincia,lat,lon,updated)
                 VALUES(?,?,?,?,?,?,?,?,?,?,?)
                 ON CONFLICT(id) DO UPDATE SET
                   gestore=excluded.gestore, bandiera=excluded.bandiera,
                   nome=excluded.nome, indirizzo=excluded.indirizzo,
                   comune=excluded.comune, provincia=excluded.provincia,
                   lat=excluded.lat, lon=excluded.lon, updated=excluded.updated"""
    esegui_batch(endpoint, token, [(sql_imp, list(row)) for row in impianti])
    print(f"Upsert impianti: {len(impianti)}")

    # Push incrementale prezzi (watermark)
    sql_prz = """INSERT OR IGNORE INTO prezzi
                 (id,impianto_id,carburante,self,prezzo,dt_comu,first_seen)
                 VALUES(?,?,?,?,?,?,?)"""
    rows = loc.execute(
        "SELECT id,impianto_id,carburante,self,prezzo,dt_comu,first_seen "
        "FROM prezzi WHERE id > ? ORDER BY id", (last_id,)
    ).fetchall()
    if rows:
        esegui_batch(endpoint, token, [(sql_prz, list(r)) for r in rows])
        last_id = rows[-1][0]

    loc.execute("INSERT OR REPLACE INTO sync_state VALUES('turso', ?)", (last_id,))
    loc.commit()
    loc.close()

    print(f"Spinti {len(rows)} eventi su Turso (watermark id={last_id})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
