#!/usr/bin/env python3
"""
Sync incrementale del DB locale SQLite verso Turso (SQLite-as-a-service).

Pusha solo gli eventi nuovi usando un watermark locale (ultimo prezzi.id spinto).
Idempotente: rilanciarlo non crea duplicati grazie a INSERT OR IGNORE su UNIQUE.

Dipendenze: libsql (pip install libsql).
Token e URL letti da variabili d'ambiente TURSO_DATABASE_URL e TURSO_AUTH_TOKEN.
"""

import os
import sqlite3

from raccogli import DB_PATH

SCHEMA = """
CREATE TABLE IF NOT EXISTS impianti(
  id INTEGER PRIMARY KEY, gestore TEXT, bandiera TEXT, tipo TEXT, nome TEXT,
  indirizzo TEXT, comune TEXT, provincia TEXT, lat REAL, lon REAL, updated TEXT);
CREATE TABLE IF NOT EXISTS prezzi(
  id INTEGER PRIMARY KEY, impianto_id INTEGER, carburante TEXT, self INTEGER,
  prezzo REAL, dt_comu TEXT, first_seen TEXT,
  UNIQUE(impianto_id, carburante, self, dt_comu, prezzo));
CREATE INDEX IF NOT EXISTS idx_prezzi_lookup
  ON prezzi(impianto_id, carburante, self, dt_comu);
"""


def main():
    url = os.environ.get("TURSO_DATABASE_URL")
    token = os.environ.get("TURSO_AUTH_TOKEN")
    if not url or not token:
        print("Errore: TURSO_DATABASE_URL e TURSO_AUTH_TOKEN devono essere nelle env vars.",
              file=__import__("sys").stderr)
        return 1

    try:
        import libsql
    except ImportError:
        print("Errore: libsql non installato. Installa con: pip install libsql",
              file=__import__("sys").stderr)
        return 2

    loc = sqlite3.connect(DB_PATH)
    loc.execute("CREATE TABLE IF NOT EXISTS sync_state(target TEXT PRIMARY KEY, last_id INTEGER)")

    r = loc.execute("SELECT last_id FROM sync_state WHERE target='turso'").fetchone()
    last_id = r[0] if r else 0

    rem = libsql.connect(database=url, auth_token=token)
    rem.executescript(SCHEMA)

    # Upsert impianti (tabella piccola: carica tutto)
    impianti = loc.execute("SELECT id,gestore,bandiera,tipo,nome,indirizzo,comune,provincia,lat,lon,updated "
                           "FROM impianti").fetchall()
    for row in impianti:
        rem.execute("""INSERT INTO impianti(id,gestore,bandiera,tipo,nome,indirizzo,comune,provincia,lat,lon,updated)
                      VALUES(?,?,?,?,?,?,?,?,?,?,?)
                      ON CONFLICT(id) DO UPDATE SET
                      gestore=excluded.gestore, bandiera=excluded.bandiera,
                      nome=excluded.nome, indirizzo=excluded.indirizzo,
                      comune=excluded.comune, provincia=excluded.provincia,
                      lat=excluded.lat, lon=excluded.lon, updated=excluded.updated""", row)
    rem.commit()
    print(f"Upsert impianti: {len(impianti)}")

    # Push incrementale prezzi (watermark)
    nuovi = 0
    for row in loc.execute("""SELECT id,impianto_id,carburante,self,prezzo,dt_comu,first_seen
                              FROM prezzi WHERE id > ? ORDER BY id""", (last_id,)):
        rem.execute("""INSERT OR IGNORE INTO prezzi
            (id,impianto_id,carburante,self,prezzo,dt_comu,first_seen) VALUES(?,?,?,?,?,?,?)""", row)
        last_id = row[0]
        nuovi += 1
    rem.commit()

    loc.execute("INSERT OR REPLACE INTO sync_state VALUES('turso', ?)", (last_id,))
    loc.commit()
    loc.close()
    rem.close()

    print(f"Spinti {nuovi} eventi su Turso (watermark id={last_id})")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
