# Storico prezzi carburanti per distributore — Progetto MVP

> Documento self-contained, implementation-ready. Pensato per essere ripreso direttamente da uno sviluppatore o da un agente AI senza contesto esterno.
> Target hardware: **Raspberry Pi 2** (ARMv7, 1 GB RAM, no GPU). Vincolo: **costo zero**, solo software, Python.

---

## 1. Obiettivo

Costruire e accumulare nel tempo la **serie storica dei prezzi dei carburanti per singolo distributore** della zona di interesse (Padova / Vicenza), partendo dagli open data del MIMIT.

**Il punto chiave (il "moat"):** il MIMIT pubblica ogni giorno solo lo *snapshot del momento* — il prezzo di oggi. Non espone la serie storica per impianto in forma consultabile (l'archivio esiste ma è in `.tar.gz` enormi e scomodi). Il valore del progetto non è il dato di oggi, **è la serie temporale pulita che accumuli tu** e che nessun altro conserva in forma usabile: quando conviene fare il pieno *qui*, pattern settimanali, chi ritocca i prezzi per primo.

Utilità personale diretta: confronto reale dei prezzi GPL/benzina nei distributori che usi davvero.

---

## 2. Fonte dati

| | |
|---|---|
| **Ente** | Ministero delle Imprese e del Made in Italy (MIMIT) |
| **Licenza** | IODL 2.0 — riuso libero **con obbligo di attribuzione** al MIMIT |
| **Formato** | CSV |
| **Separatore** | `|` (pipe) — **cambiato dal 10/02/2026** (prima era `;`) |
| **Cadenza** | Quotidiana. Il file contiene i dati in vigore alle **ore 8 del giorno precedente** alla pubblicazione |
| **Pagina dataset** | `https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti` |

Due file da scaricare:

- **Prezzi** → `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv`
- **Anagrafica impianti** → `https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv`

> ⚠️ **Robustezza:** non dare per scontato lo schema. I due file iniziano con una riga di intestazione non-CSV (es. `Estrazione del : 18/06/2026 ...`) seguita dalla riga di header vera. **Leggi i nomi delle colonne a runtime dall'header**, non per posizione fissa: così il codice sopravvive a piccole variazioni di schema. **Il separatore va rilevato automaticamente**: è `|` nei file correnti (dal 10/02/2026) ma `;` in tutto lo storico dell'archivio (vedi §9). Il parser di riferimento lo deduce contando i separatori nella riga di header.

---

## 3. Schema dei file (atteso)

Da confermare a runtime leggendo l'header. Schema storicamente stabile:

**`anagrafica_impianti_attivi.csv`**
```
idImpianto | Gestore | Bandiera | Tipo Impianto | Nome Impianto | Indirizzo | Comune | Provincia | Latitudine | Longitudine
```

**`prezzo_alle_8.csv`**
```
idImpianto | descCarburante | prezzo | isSelf | dtComu
```

Note sui campi:
- `prezzo` → decimale **con la virgola** (es. `1,879`). Va normalizzato a `.` prima del `float`.
- `isSelf` → `1` (self service) / `0` (servito). Lo stesso impianto pubblica prezzi distinti self/servito.
- `dtComu` → data/ora in cui il gestore ha **comunicato** quel prezzo. Formato `DD/MM/YYYY HH:MM:SS`. **Campo cruciale** (vedi §5).
- `descCarburante` → testo libero (`Benzina`, `Gasolio`, `GPL`, `Metano`, e varianti commerciali tipo `HiQ Diesel`, `Gasolio Premium`...). Per il GPL filtra per sottostringa `GPL`, non per uguaglianza esatta.
- `Provincia` → sigla (`PD`, `VI`, ...).

---

## 4. Architettura MVP

Flusso giornaliero, idempotente:

```
[cron 1×/giorno]
   → scarica anagrafica + prezzi
   → individua le stazioni LOCALI (per provincia o per raggio da casa)
   → upsert anagrafica delle stazioni locali
   → per ogni prezzo di una stazione locale: INSERT OR IGNORE (vedi §5)
   → log della run (data, n. record)
```

Principi:
- **Streaming, niente pandas.** Il file prezzi ha centinaia di migliaia di righe (~20–40 MB). Su 1 GB di RAM si elabora riga per riga con il modulo `csv`, mai caricandolo tutto in memoria.
- **Zero dipendenze esterne.** Tutto con la standard library (`urllib`, `csv`, `sqlite3`). Niente da installare, niente da rompere sull'ARM.
- **Idempotente.** Rilanciarlo due volte non crea duplicati (vedi §5). Quindi l'orario esatto del cron è irrilevante.

---

## 5. Il cuore del progetto: storicizzazione senza duplicati

Problema: il file è uno *snapshot giornaliero*. Se salvassi tutto ogni giorno, accumuleresti la stessa riga centinaia di volte.

**Soluzione elegante (event-sourcing gratis):** il campo `dtComu` cambia **solo quando il gestore ritocca il prezzo**. Finché il prezzo resta fermo, `dtComu` resta identico giorno dopo giorno.

Quindi definisci un vincolo `UNIQUE(impianto_id, carburante, self, dt_comu, prezzo)` e usa `INSERT OR IGNORE`. Risultato:
- ogni giorno tenti di inserire lo snapshot, ma
- entra una riga nuova **solo quando il prezzo è davvero cambiato**.

Ottieni così una storia a *eventi di variazione*, leggerissima da archiviare (perfetta per il Pi) e perfetta da graficare. Aggiungi un campo `first_seen` (la data della run in cui hai osservato per la prima volta quell'evento) per conoscere la finestra di osservazione.

---

## 6. Schema database (SQLite)

```sql
CREATE TABLE IF NOT EXISTS impianti (
    id        INTEGER PRIMARY KEY,        -- idImpianto MIMIT
    gestore   TEXT, bandiera TEXT, tipo TEXT, nome TEXT,
    indirizzo TEXT, comune TEXT, provincia TEXT,
    lat REAL, lon REAL,
    updated   TEXT                          -- data ultimo aggiornamento anagrafica
);

CREATE TABLE IF NOT EXISTS prezzi (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    impianto_id INTEGER NOT NULL REFERENCES impianti(id),
    carburante  TEXT NOT NULL,
    self        INTEGER NOT NULL,           -- 1 self / 0 servito
    prezzo      REAL NOT NULL,
    dt_comu     TEXT NOT NULL,              -- ISO 8601
    first_seen  TEXT NOT NULL,              -- data run di prima osservazione
    UNIQUE(impianto_id, carburante, self, dt_comu, prezzo)
);

CREATE INDEX IF NOT EXISTS idx_prezzi_lookup
    ON prezzi(impianto_id, carburante, self, dt_comu);

CREATE TABLE IF NOT EXISTS runs (                -- audit opzionale
    data        TEXT PRIMARY KEY,
    estrazione  TEXT,
    n_nuovi     INTEGER
);
```

---

## 7. Filtro geografico (scegli una strategia)

L'anagrafica contiene `Provincia` e `Latitudine`/`Longitudine`. Due opzioni:

- **Semplice (MVP):** filtra per provincia → `PROVINCE = {"PD", "VI"}`.
- **Precisa:** filtra per **raggio** (es. 15 km) da casa con la formula di Haversine sulle coordinate dell'anagrafica.

Consiglio: parti per provincia (zero parametri da tarare), passa al raggio quando vuoi restringere ai distributori che usi davvero.

---

## 8. Implementazione di riferimento

Script unico, standard library, streaming. Da `cron` una volta al giorno.

```python
#!/usr/bin/env python3
"""
Raccoglitore storico prezzi carburanti MIMIT — MVP.
Scarica lo snapshot giornaliero, filtra le stazioni locali, accumula la serie storica in SQLite.
Dipendenze: solo standard library. Streaming, niente pandas (adatto a 1 GB RAM su ARM).
Fonte: MIMIT Open Data — licenza IODL 2.0 (attribuzione al Ministero delle Imprese e del Made in Italy).
"""

import csv, sqlite3, urllib.request
from datetime import datetime, date
from math import radians, sin, cos, asin, sqrt

PREZZI_URL     = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
ANAGRAFICA_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
DB_PATH = "carburanti.db"
# Nota: il separatore (| dal 10/02/2026, ; nello storico) è rilevato in automatico da leggi_csv().

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
```

**Cron** (una volta al giorno, es. alle 13:00 — i dati pubblicati sono dell'8:00 del giorno prima, l'orario non è critico perché lo script è idempotente):

```cron
0 13 * * *  cd /home/pi/carburanti && /usr/bin/python3 raccogli.py >> log.txt 2>&1
```

---

## 9. Backfill: caricare lo storico dall'archivio (tar.gz)

Il MIMIT pubblica anche l'**archivio storico** dei dataset, da marzo 2015, **raggruppato per trimestre** e compresso in `.tar.gz`. Caricandolo una volta riempi il database con anni di prezzi passati, invece di aspettare che la serie si accumuli da zero.

**Host e pattern URL** (regolari, generabili in automatico):
```
Prezzi      : https://opendatacarburanti.mise.gov.it/categorized/prezzo_alle_8/{ANNO}/{ANNO}_{Q}_tr.tar.gz
Anagrafica  : https://opendatacarburanti.mise.gov.it/categorized/anagrafica_impianti_attivi/{ANNO}/{ANNO}_{Q}_tr.tar.gz
```
con `{ANNO}` = 2015 … anno corrente e `{Q}` = 1..4 (trimestre). Esempio: `.../prezzo_alle_8/2025/2025_3_tr.tar.gz`. Dimensioni indicative per trimestre: ~45–65 MB il file prezzi, ~80–110 MB l'anagrafica (compressi).

**Cosa c'è dentro:** ogni `.tar.gz` contiene i CSV giornalieri del trimestre, stesso schema dei file correnti ma con **separatore `;`** (il `|` esiste solo dal 10/02/2026). Il parser `leggi_csv()` rileva il separatore da solo, quindi lo riusi identico.

**Vincoli sul Pi 2 (importante):**
- I tar.gz si leggono **in streaming** con `tarfile`, un membro (file giornaliero) alla volta. Il picco di RAM resta ≈ a un singolo file giornaliero (decine di MB): non si srotola l'intero trimestre né su disco né in memoria.
- È un'operazione **CPU-bound e lunga**: ogni trimestre sono milioni di righe da parsare, anche se dopo il filtro locale + dedup ne resti pochissime. Trattalo come **job batch da lanciare una volta**, magari di notte, un trimestre alla volta.
- **Idempotente** come il job giornaliero: lo stesso `INSERT OR IGNORE` su `dtComu` fa sì che ricaricare un trimestre già importato non crei duplicati. Lo puoi interrompere e riprendere senza paura (commit per-file).

**Stazioni locali nello storico:** di default il backfill usa l'insieme di stazioni locali già presente in `impianti` (popolato dal job giornaliero o da una prima esecuzione). Copre tutte le stazioni locali *tuttora esistenti* — il caso che interessa. Se vuoi anche le stazioni locali ormai chiuse, attiva `INCLUDI_ANAGRAFICA_STORICA = True`: il loader scaricherà anche l'anagrafica di ogni trimestre per scoprire gli id locali dell'epoca (più download, copertura completa).

**Reference script** (`carica_archivio.py`, riusa il modulo giornaliero `raccogli.py`):

```python
#!/usr/bin/env python3
"""
Backfill storico prezzi carburanti dall'archivio MIMIT (.tar.gz per trimestre).
Riusa lo stesso DB e lo stesso parser dello script giornaliero.
Streaming dei tar.gz: picco RAM ~ un file giornaliero. Lanciare come job batch.
Fonte: MIMIT Open Data — licenza IODL 2.0 (attribuzione al MIMIT).
"""

import sqlite3, tarfile, tempfile, urllib.request
from datetime import datetime, date

# Riusa dal job giornaliero (import sicuro: main() è sotto __main__ in raccogli.py)
from raccogli import leggi_csv, f, init_db, locale, DB_PATH

BASE      = "https://opendatacarburanti.mise.gov.it/categorized"
ANNI      = range(2023, date.today().year + 1)   # quali anni caricare
TRIMESTRI = (1, 2, 3, 4)                          # quali trimestri
INCLUDI_ANAGRAFICA_STORICA = False               # True = scopre anche stazioni locali chiuse


def url(categoria, anno, q):
    return f"{BASE}/{categoria}/{anno}/{anno}_{q}_tr.tar.gz"


def scarica_tar(u):
    """Scarica il .tar.gz in un file temporaneo; restituisce il path o None."""
    try:
        req = urllib.request.Request(u, headers={"User-Agent": "carburanti-mvp/1.0"})
        with urllib.request.urlopen(req, timeout=300) as resp:
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".tar.gz")
            while chunk := resp.read(1 << 20):     # 1 MB alla volta
                tmp.write(chunk)
            tmp.close()
            return tmp.name
    except Exception as e:
        print(f"  ! download fallito: {e}")
        return None


def righe_membro(tar, membro):
    """Righe decodificate di un CSV dentro il tar (utf-8, fallback latin-1)."""
    fobj = tar.extractfile(membro)
    if fobj is None:
        return []
    data = fobj.read()
    try:
        return data.decode("utf-8").splitlines()
    except UnicodeDecodeError:
        return data.decode("latin-1").splitlines()


def ids_locali_da_anagrafica(path):
    """Id delle stazioni locali da un tar di anagrafica storica."""
    ids = set()
    with tarfile.open(path, "r:gz") as tar:
        for m in tar:
            if not m.isfile():
                continue
            for rec, _ in leggi_csv(righe_membro(tar, m)):
                if locale(rec):
                    ids.add(rec["idImpianto"].strip())
    return ids


def carica_prezzi(con, path, locali):
    """Inserisce gli eventi-prezzo delle stazioni locali da un tar di prezzi."""
    oggi, nuovi = date.today().isoformat(), 0
    with tarfile.open(path, "r:gz") as tar:
        for m in tar:
            if not m.isfile():
                continue
            for rec, _ in leggi_csv(righe_membro(tar, m)):
                iid = rec.get("idImpianto", "").strip()
                if iid not in locali:
                    continue
                prezzo = f(rec.get("prezzo"))
                if prezzo is None:
                    continue
                try:
                    dt = datetime.strptime(rec["dtComu"].strip(),
                                           "%d/%m/%Y %H:%M:%S").isoformat()
                except (ValueError, KeyError):
                    dt = rec.get("dtComu", "").strip()
                cur = con.execute("""INSERT OR IGNORE INTO prezzi
                                  (impianto_id,carburante,self,prezzo,dt_comu,first_seen)
                                  VALUES(?,?,?,?,?,?)""",
                                  (int(iid), rec.get("descCarburante","").strip(),
                                   1 if rec.get("isSelf","").strip()=="1" else 0,
                                   prezzo, dt, oggi))
                nuovi += cur.rowcount
            con.commit()      # commit per-file giornaliero: ripartenza sicura
    return nuovi


def main():
    con = sqlite3.connect(DB_PATH)
    init_db(con)
    locali = {str(r[0]) for r in con.execute("SELECT id FROM impianti")}
    print(f"Stazioni locali note nel DB: {len(locali)}")

    for anno in ANNI:
        for q in TRIMESTRI:
            print(f"== {anno} Q{q} ==")
            if INCLUDI_ANAGRAFICA_STORICA:
                pa = scarica_tar(url("anagrafica_impianti_attivi", anno, q))
                if pa:
                    locali |= ids_locali_da_anagrafica(pa)
                    print(f"  stazioni locali (cumulate): {len(locali)}")
            pp = scarica_tar(url("prezzo_alle_8", anno, q))
            if not pp:
                continue
            print(f"  nuovi eventi prezzo: {carica_prezzi(con, pp, locali)}")
    con.close()


if __name__ == "__main__":
    main()
```

> Il job giornaliero (§8) e il backfill scrivono nella **stessa** tabella `prezzi` con lo stesso vincolo di unicità: convivono senza conflitti né duplicati. Strategia consigliata: **carichi lo storico una volta** con `carica_archivio.py`, poi lasci girare solo il cron giornaliero che aggiunge gli eventi nuovi.

---

## 10. Query utili (una volta che lo storico cresce)

```sql
-- Prezzo GPL self più basso oggi tra le mie stazioni (ultimo prezzo noto per impianto)
SELECT i.nome, i.comune, p.prezzo, p.dt_comu
FROM prezzi p
JOIN impianti i ON i.id = p.impianto_id
WHERE p.carburante LIKE '%GPL%' AND p.self = 1
  AND p.dt_comu = (SELECT MAX(dt_comu) FROM prezzi
                   WHERE impianto_id=p.impianto_id AND carburante=p.carburante AND self=p.self)
ORDER BY p.prezzo ASC;

-- Andamento storico di un singolo distributore (per il grafico)
SELECT dt_comu, prezzo FROM prezzi
WHERE impianto_id = ? AND carburante LIKE '%Benzina%' AND self = 1
ORDER BY dt_comu;

-- Chi ritocca per primo: ultime variazioni in ordine di tempo
SELECT i.nome, p.carburante, p.prezzo, p.dt_comu
FROM prezzi p JOIN impianti i ON i.id=p.impianto_id
ORDER BY p.dt_comu DESC LIMIT 50;
```

---

## 11. Scope MVP vs Fase 2

**MVP (questo documento):**
- download + parsing robusto + storicizzazione a eventi in SQLite
- filtro locale (provincia o raggio)
- **backfill una tantum dello storico dall'archivio trimestrale** (§9)
- query a riga di comando per "prezzo più basso oggi"

**Fase 2 (quando il dato si è accumulato):**
- grafici di andamento (matplotlib statico, o un piccolo dashboard)
- rilevazione di pattern settimanali / "giorno migliore per il pieno"
- alert quando un distributore che segui scende sotto una soglia
- export/integrazione (eventuale) verso strumenti di visualizzazione

---

## 12. Architettura ed evoluzione (Fase 2)

L'MVP è "single-box": il Pi raccoglie, salva in SQLite locale e si interroga via CLI. La Fase 2 porta i dati in cloud e su un'app personale (telefono + Android Auto). Schema end-to-end:

```
[Pi] collector stdlib (§8) + backfill (§9)
        │  scrive
        ▼
   SQLite locale  ──push incrementale──►  [Turso]  (SQLite-as-a-service, cloud)
   (buffer autorevole)                    source of truth, free tier
                                              │  lettura
                                              ▼
                                   [App Android]
                                   • telefono: mappa + lista + storico
                                   • Android Auto: superficie POI (template)
```

Vedi il documento separato **`app-android-carburanti.md`** per la parte app/Android Auto (è l'ultima fase a essere implementata).

### 12.1 DB hostato: Turso

- **Perché Turso:** resti nel modello mentale SQLite ma con accesso da fuori casa, e il free tier è ampio (≈ 3 db attivi, 1 GB, 500M righe lette/mese) — più che sufficiente per dati locali.
- **Il Pi resta autorevole:** SQLite locale rimane il buffer primario. Se Turso o la rete cadono, la raccolta non si ferma; il push recupera al run successivo.
- **Nota naming:** `libSQL` è il fork di SQLite con le *embedded replica*; `Turso` è la riscrittura in Rust che il team ora raccomanda per la sincronizzazione. Per le scelte concrete segui la documentazione Turso corrente.

### 12.2 Sync Pi → Turso (push incrementale)

Strategia: una tabella di stato locale tiene il **watermark** dell'ultimo `prezzi.id` spinto. A ogni run pushi solo le righe con `id` maggiore; lo **stesso vincolo `UNIQUE`** replicato su Turso rende il push ripetibile senza creare duplicati. Il collector (§8) resta a sola stdlib: il push è un passo **separato** che può usare il client Turso.

```python
# sync_turso.py — push incrementale degli eventi nuovi (Fase 2).
# Dipendenza: client libSQL/Turso per Python (NON stdlib) — verifica il nome
# del pacchetto sulla doc Turso corrente. Tenuto separato dal collector.
import sqlite3
from raccogli import DB_PATH
# import del client Turso, es.: import libsql_experimental as libsql

TURSO_URL   = "libsql://<tuo-db>.turso.io"
TURSO_TOKEN = "<auth-token-di-scrittura>"

SCHEMA = """CREATE TABLE IF NOT EXISTS prezzi(
  id INTEGER PRIMARY KEY, impianto_id INTEGER, carburante TEXT, self INTEGER,
  prezzo REAL, dt_comu TEXT, first_seen TEXT,
  UNIQUE(impianto_id,carburante,self,dt_comu,prezzo))"""

def main():
    loc = sqlite3.connect(DB_PATH)
    loc.execute("CREATE TABLE IF NOT EXISTS sync_state(target TEXT PRIMARY KEY, last_id INTEGER)")
    r = loc.execute("SELECT last_id FROM sync_state WHERE target='turso'").fetchone()
    last_id = r[0] if r else 0

    rem = libsql.connect(database=TURSO_URL, auth_token=TURSO_TOKEN)   # client Turso
    rem.execute(SCHEMA)
    # impianti: tabella piccola -> upsert dell'intera anagrafica locale a ogni run (omesso qui)

    nuovi = 0
    for row in loc.execute("""SELECT id,impianto_id,carburante,self,prezzo,dt_comu,first_seen
                              FROM prezzi WHERE id > ? ORDER BY id""", (last_id,)):
        rem.execute("""INSERT OR IGNORE INTO prezzi
            (id,impianto_id,carburante,self,prezzo,dt_comu,first_seen) VALUES(?,?,?,?,?,?,?)""", row)
        last_id = row[0]; nuovi += 1
    rem.commit()
    loc.execute("INSERT OR REPLACE INTO sync_state VALUES('turso', ?)", (last_id,))
    loc.commit()
    print(f"Spinti {nuovi} eventi su Turso (watermark id={last_id})")

if __name__ == "__main__":
    main()
```

> `impianti` è piccola (poche stazioni locali): la sincronizzi facendo un upsert dell'intera tabella a ogni run, senza watermark. Il watermark serve solo per `prezzi`, che cresce nel tempo.

### 12.3 Letture dall'app: due opzioni

- **Embedded replica** — copia SQLite locale sul telefono che si sincronizza da Turso. Ideale per l'uso in auto: letture istantanee e funzionamento offline. SDK Android ufficiale ma **in technical preview**.
- **Lettura HTTP remota + cache Room** — più convenzionale e robusta. È il piano B se la preview dà problemi.

### 12.4 Caveat che decidono la fattibilità (dettagli nel doc Android)

- **Android Auto + sideload:** l'opzione "fonti sconosciute" di Android Auto **non** copre le app Car App Library. Si sviluppa/collauda sul DHU (emulatore); per l'auto vera serve quasi certamente una traccia di test interno sul Play Store (account sviluppatore una tantum).
- **SDK Turso Android in preview:** ottimo per imparare, ma tieni pronto il fallback HTTP.

---

## 13. Note legali ed etiche

- **Licenza IODL 2.0:** riuso libero, anche per ridistribuzione, **con attribuzione** al Ministero delle Imprese e del Made in Italy.
- **Carico sul server:** un solo `GET` al giorno per file statico nel job quotidiano. Il backfill scarica i `.tar.gz` una sola volta (operazione una tantum). Impatto trascurabile, comportamento corretto. Mantieni uno `User-Agent` identificativo.
- Uso personale/non commerciale: nessun problema. In caso di pubblicazione dei dati elaborati, cita la fonte e la licenza.

---

## 14. Checklist di avvio

- [ ] Confermare a runtime header e separatore dei due file (stampa la prima riga header).
- [ ] Scegliere strategia filtro: provincia (`PD/VI`) o raggio da `HOME`.
- [ ] Primo run manuale del job giornaliero: verificare numero impianti locali e numero eventi inseriti.
- [ ] Secondo run lo stesso giorno: verificare che `nuovi == 0` (idempotenza OK).
- [ ] **Backfill:** lanciare `carica_archivio.py` su un singolo trimestre di prova (es. ultimo) e verificare che gli eventi entrino; poi estendere il range `ANNI`/`TRIMESTRI`.
- [ ] **Backfill:** rilanciare lo stesso trimestre e verificare `nuovi == 0` (idempotenza del backfill).
- [ ] Inserire la riga in `crontab` per la raccolta quotidiana.
- [ ] Validare la serie storica con una query di andamento su un distributore noto.
