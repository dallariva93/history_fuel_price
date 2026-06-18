#!/usr/bin/env python3
"""
Backfill storico prezzi carburanti dall'archivio MIMIT (.tar.gz per trimestre).
Riusa parser e schema del job giornaliero (raccogli.py).
Streaming dei tar.gz: scaricato su file temporaneo, elaborato un membro alla volta.
Fonte: MIMIT Open Data - licenza IODL 2.0 (attribuzione al MIMIT).
"""

import os, sqlite3, tarfile, tempfile, urllib.request
from datetime import datetime, date

from raccogli import leggi_csv, f, init_db, locale, DB_PATH

BASE      = "https://opendatacarburanti.mise.gov.it/categorized"
ANNI      = range(date.today().year - 1, date.today().year + 1)  # default: ultimi 2 anni
TRIMESTRI = (1, 2, 3, 4)
INCLUDI_ANAGRAFICA_STORICA = False


def url(categoria, anno, q):
    return f"{BASE}/{categoria}/{anno}/{anno}_{q}_tr.tar.gz"


def scarica_tar(u):
    """Scarica il .tar.gz in un file temporaneo; restituisce il path o None."""
    try:
        req = urllib.request.Request(u, headers={"User-Agent": "carburanti-mvp/1.0"})
        with urllib.request.urlopen(req, timeout=300) as resp:
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".tar.gz")
            while chunk := resp.read(1 << 20):
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
                                  (int(iid), rec.get("descCarburante", "").strip(),
                                   1 if rec.get("isSelf", "").strip() == "1" else 0,
                                   prezzo, dt, oggi))
                nuovi += cur.rowcount
            con.commit()
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
                    os.unlink(pa)
            pp = scarica_tar(url("prezzo_alle_8", anno, q))
            if not pp:
                continue
            try:
                print(f"  nuovi eventi prezzo: {carica_prezzi(con, pp, locali)}")
            finally:
                os.unlink(pp)
    con.close()


if __name__ == "__main__":
    main()
