# Storico prezzi carburanti MIMIT - Raspberry Pi

Collector giornaliero dei prezzi carburanti per stazioni locali (PD/VI), con backfill storico e query CLI.

## Requisiti

- Python 3.8+ (solo standard library per il job giornaliero)
- Per il backfill e le query grafiche: `pip3 install matplotlib` (opzionale, solo per `query.py grafico`)

## File

- `raccogli.py` - job giornaliero (stdlib, streaming)
- `carica_archivio.py` - backfill storico da archivio trimestrale (stdlib, streaming)
- `query.py` - CLI di consultazione (stdlib + matplotlib opzionale)
- `carburanti.db` - database SQLite (creato al primo run)

## Primo run manuale

1. Copia i file sul Pi nella cartella scelta (es. `/home/pi/carburanti`)
2. Esegui il job giornaliero:

```bash
python3 raccogli.py
```

Output atteso:
```
Impianti locali: <N>
Nuovi eventi prezzo: <M>
```

## Backfill storico (una tantum)

Per caricare lo storico dall'archivio trimestrale (default: ultimi 2 anni):

```bash
python3 carica_archivio.py
```

Per estendere lo storico completo (2015-oggi), modifica in `carca_archivio.py`:
```python
ANNI = range(2015, date.today().year + 1)
```

Il backfill è idempotente: rilanciarlo non crea duplicati.

## Sync verso Turso (cloud)

`sync_turso.py` pusha incrementalmente gli eventi nuovi su un DB Turso (SQLite-as-a-service) per renderli accessibili dall'app Android.

Configurazione: crea `/home/pi/carburanti/.env` con:

```
TURSO_DATABASE_URL=libsql://...
TURSO_AUTH_TOKEN=...   # token di scrittura
```

Permessi: `chmod 600 .env`.

Run manuale:
```bash
python3 sync_turso.py
```

Output:
```
Upsert impianti: <N>
Spinti <M> eventi su Turso (watermark id=<X>)
```

Idempotente: il watermark in `sync_state` impedisce duplicati.

## Crontab

Esegui giornaliero alle 13:00 (raccolta + sync):

```bash
crontab -e
```

Aggiungi questa riga (sostituisci `/home/pi/carburanti` con il percorso reale):

```
0 13 * * * cd /home/pi/carburanti && /usr/bin/python3 raccogli.py >> log.txt 2>&1 && /usr/bin/python3 sync_turso.py >> log.txt 2>&1
```

Il sync parte solo se la raccolta è andata a buon fine (`&&`).

Verifica con:
```bash
crontab -l
```

## Log e DB

- `log.txt` - stdout/stderr del job giornaliero (append)
- `carburanti.db` - database SQLite locale (sul Pi)
- Turso - copia cloud, accessibile via API HTTP con token di sola lettura per l'app

## Query CLI

Prezzo più basso oggi (default GPL self):
```bash
python3 query.py minimo --carburante GPL
```

Per i prezzi serviti:
```bash
python3 query.py minimo --carburante GPL --servito
```

Grafico andamento storico (richiede matplotlib):
```bash
python3 query.py grafico --impianto <id> --carburante Benzina
```

Output PNG: `andamento_<id>_<carburante>.png`

## Fonte dati

Ministero delle Imprese e del Made in Italy (MIMIT) - Open Data, licenza IODL 2.0 (attribuzione al MIMIT).
