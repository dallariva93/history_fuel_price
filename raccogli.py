#!/usr/bin/env python3
"""
Raccoglitore storico prezzi carburanti MIMIT - MVP.
Scarica lo snapshot giornaliero, filtra le stazioni locali, accumula la serie storica in SQLite.
Dipendenze: solo standard library. Streaming, niente pandas (adatto a 1 GB RAM su ARM).
Fonte: MIMIT Open Data - licenza IODL 2.0 (attribuzione al Ministero delle Imprese e del Made in Italy).
"""

import csv, sqlite3, urllib.request
from datetime import datetime, date
from math import radians, sin, cos, asin, sqrt

PREZZI_URL     = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
ANAGRAFICA_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
DB_PATH = "carburanti.db"
# Nota: il separatore (| dal 10/02/2026, ; nello storico) e' rilevato in automatico da leggi_csv().

# --- Filtro geografico ---
USA_RAGGIO = False                 # False = per provincia, True = per distanza
PROVINCE   = {"PD", "VI"}
HOME       = (45.4064, 11.8768)    # Padova lat/lon
RAGGIO_KM  = 15.0


def scarica_righe(url):
    """Scarica e restituisce le righe decodificate (utf-8, fallback latin-1)."""
    req = urllib.request.Request(url, headers={"User-Agent": "carburanti-mvp/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
    try:
        testo = raw.decode("utf-8")
    except UnicodeDecodeError:
        testo = raw.decode("latin-1")
    return testo.splitlines()


def leggi_csv(righe):
    """Salta la riga 'Estrazione del...', individua l'header, RILEVA il separatore
    (| dal 10/02/2026, ; nello storico) e produce un dict per ogni riga."""
    start = next(i for i, r in enumerate(righe) if r.lower().startswith("idimpianto"))
    estrazione = righe[0] if start > 0 else ""
    sep = "|" if righe[start].count("|") >= righe[start].count(";") else ";"
    reader = csv.reader(righe[start:], delimiter=sep)
    header = [h.strip() for h in next(reader)]
    for campi in reader:
        if len(campi) == len(header):
            yield dict(zip(header, campi)), estrazione


def haversine(a, b):
    (la1, lo1), (la2, lo2) = a, b
    la1, lo1, la2, lo2 = map(radians, (la1, lo1, la2, lo2))
    h = sin((la2-la1)/2)**2 + cos(la1)*cos(la2)*sin((lo2-lo1)/2)**2
    return 2 * 6371 * asin(sqrt(h))


def f(v):
    """float robusto: virgola decimale, vuoto -> None."""
    v = (v or "").strip().replace(",", ".")
    try: return float(v)
    except ValueError: return None


def init_db(con):
    con.executescript("""
    CREATE TABLE IF NOT EXISTS impianti(
      id INTEGER PRIMARY KEY, gestore TEXT, bandiera TEXT, tipo TEXT, nome TEXT,
      indirizzo TEXT, comune TEXT, provincia TEXT, lat REAL, lon REAL, updated TEXT);
    CREATE TABLE IF NOT EXISTS prezzi(
      id INTEGER PRIMARY KEY AUTOINCREMENT, impianto_id INTEGER NOT NULL,
      carburante TEXT NOT NULL, self INTEGER NOT NULL, prezzo REAL NOT NULL,
      dt_comu TEXT NOT NULL, first_seen TEXT NOT NULL,
      UNIQUE(impianto_id, carburante, self, dt_comu, prezzo));
    CREATE INDEX IF NOT EXISTS idx_prezzi_lookup
      ON prezzi(impianto_id, carburante, self, dt_comu);
    CREATE TABLE IF NOT EXISTS runs(data TEXT PRIMARY KEY, estrazione TEXT, n_nuovi INTEGER);
    """)


def locale(rec):
    """True se l'impianto rientra nel filtro geografico scelto."""
    if USA_RAGGIO:
        lat, lon = f(rec.get("Latitudine")), f(rec.get("Longitudine"))
        if lat is None or lon is None: return False
        return haversine(HOME, (lat, lon)) <= RAGGIO_KM
    return rec.get("Provincia", "").strip().upper() in PROVINCE


def main():
    oggi = date.today().isoformat()
    con = sqlite3.connect(DB_PATH)
    init_db(con)

    # 1) Anagrafica -> set di id locali + upsert
    locali = set()
    for rec, _ in leggi_csv(scarica_righe(ANAGRAFICA_URL)):
        if not locale(rec): continue
        iid = rec["idImpianto"].strip()
        locali.add(iid)
        con.execute("""INSERT INTO impianti(id,gestore,bandiera,tipo,nome,indirizzo,
                       comune,provincia,lat,lon,updated)
                       VALUES(?,?,?,?,?,?,?,?,?,?,?)
                       ON CONFLICT(id) DO UPDATE SET
                       gestore=excluded.gestore, bandiera=excluded.bandiera,
                       nome=excluded.nome, indirizzo=excluded.indirizzo,
                       comune=excluded.comune, provincia=excluded.provincia,
                       lat=excluded.lat, lon=excluded.lon, updated=excluded.updated""",
                    (int(iid), rec.get("Gestore"), rec.get("Bandiera"),
                     rec.get("Tipo Impianto"), rec.get("Nome Impianto"),
                     rec.get("Indirizzo"), rec.get("Comune"), rec.get("Provincia"),
                     f(rec.get("Latitudine")), f(rec.get("Longitudine")), oggi))
    con.commit()
    print(f"Impianti locali: {len(locali)}")

    # 2) Prezzi -> INSERT OR IGNORE (storicizzazione a eventi)
    nuovi, estrazione = 0, ""
    for rec, estr in leggi_csv(scarica_righe(PREZZI_URL)):
        estrazione = estr
        iid = rec.get("idImpianto", "").strip()
        if iid not in locali: continue
        prezzo = f(rec.get("prezzo"))
        if prezzo is None: continue
        try:
            dt = datetime.strptime(rec["dtComu"].strip(), "%d/%m/%Y %H:%M:%S").isoformat()
        except (ValueError, KeyError):
            dt = rec.get("dtComu", "").strip()
        cur = con.execute("""INSERT OR IGNORE INTO prezzi
                          (impianto_id,carburante,self,prezzo,dt_comu,first_seen)
                          VALUES(?,?,?,?,?,?)""",
                          (int(iid), rec.get("descCarburante","").strip(),
                           1 if rec.get("isSelf","").strip()=="1" else 0,
                           prezzo, dt, oggi))
        nuovi += cur.rowcount
    con.execute("INSERT OR REPLACE INTO runs VALUES(?,?,?)", (oggi, estrazione, nuovi))
    con.commit(); con.close()
    print(f"Nuovi eventi prezzo: {nuovi}")


if __name__ == "__main__":
    main()
