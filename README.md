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

## Crontab

Esegui il job giornaliero alle 13:00 (l'orario non è critico: lo script è idempotente):

```bash
crontab -e
```

Aggiungi questa riga (sostituisci `/home/pi/carburanti` con il percorso reale):

```
0 13 * * * cd /home/pi/carburanti && /usr/bin/python3 raccogli.py >> log.txt 2>&1
```

Verifica con:
```bash
crontab -l
```

## Log e DB

- `log.txt` - stdout/stderr del job giornaliero (append)
- `carburanti.db` - database SQLite (nella cartella del progetto)

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
