# Manuale utente - Storico prezzi carburanti

Sistema per raccogliere e consultare i prezzi dei carburanti delle stazioni locali (PD/VI).

## Cos'è

Il sistema scarica ogni giorno i prezzi dal MIMIT, li filtra per le stazioni locali e li accumula in un database SQLite. Così hai la serie storica completa dei prezzi di ogni distributore.

## Dove gira

- Raspberry Pi (192.168.1.18), cartella `/home/pi/carburanti`
- Job giornaliero alle 13:00 (crontab)
- Log: `/home/pi/carburanti/log.txt`
- Database: `/home/pi/carburanti/carburanti.db`

## Accesso al Pi

```powershell
ssh -i "$env:USERPROFILE\.ssh\id_ed25519_pi" pi@192.168.1.18
cd ~/carburanti
```

## Operazioni comuni

### Consultare i prezzi più bassi oggi

```bash
python3 query.py minimo --carburante GPL
```

Output: classifica delle stazioni locali ordinate per prezzo.

Opzioni:
- `--carburante GPL` (default) o `Benzina`, `Gasolio`, ecc.
- `--servito` per i prezzi serviti (default è self)

### Grafico andamento storico di un distributore

```bash
python3 query.py grafico --impianto <id> --carburante Benzina
```

Output: file PNG `andamento_<id>_<carburante>.png`.

Richiede matplotlib (installato con `pip3 install matplotlib`).

### Lanciare il job giornaliero a mano

```bash
python3 raccogli.py
```

Output:
```
Impianti locali: <N>
Nuovi eventi prezzo: <M>
```

Idempotente: rilanciarlo non crea duplicati.

### Caricare lo storico (backfill)

```bash
python3 carca_archivio.py
```

Carica gli ultimi 2 anni di dati dall'archivio trimestrale del MIMIT. Per estendere al 2015-oggi, modifica `ANNI` nel file.

### Verificare il log

```bash
tail -f log.txt
```

## Troubleshooting

### Il job non parte

Controlla crontab:
```bash
crontab -l
```

Dovresti vedere:
```
0 13 * * * cd /home/pi/carburanti && /usr/bin/python3 raccogli.py >> log.txt 2>&1
```

### Nessun dato trovato

Verifica che il database esista:
```bash
ls -la carburanti.db
```

Se manca, lancia `python3 raccogli.py` a mano.

### Matplotlib non installato

```bash
pip3 install matplotlib
```

Oppure usa `apt install python3-matplotlib` (più veloce su ARMv7).

## Fonte dati

Ministero delle Imprese e del Made in Italy (MIMIT) - Open Data, licenza IODL 2.0 (attribuzione al MIMIT).
