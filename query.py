#!/usr/bin/env python3
"""
CLI di consultazione del DB carburanti (popolato da raccogli.py / carica_archivio.py).

Sottocomandi:
  minimo  : classifica delle stazioni locali per ultimo prezzo noto di un carburante.
  grafico : PNG dell'andamento storico del prezzo per un impianto + carburante.

Dipendenze: standard library + matplotlib (solo per il sottocomando grafico).
"""

import argparse
import sqlite3
import sys
from collections import defaultdict

from raccogli import DB_PATH


def cmd_minimo(args):
    con = sqlite3.connect(DB_PATH)
    # Ultimo prezzo noto per impianto/carburante/self (query della §10 della spec).
    # Match per sottostringa: 'GPL' copre anche varianti commerciali.
    sql = """
    SELECT i.nome, i.comune, p.prezzo, p.dt_comu
    FROM prezzi p
    JOIN impianti i ON i.id = p.impianto_id
    WHERE p.carburante LIKE ? AND p.self = ?
      AND p.dt_comu = (SELECT MAX(dt_comu) FROM prezzi
                       WHERE impianto_id = p.impianto_id
                         AND carburante  = p.carburante
                         AND self        = p.self)
    ORDER BY p.prezzo ASC
    """
    rows = con.execute(sql, (f"%{args.carburante}%", 1 if args.self else 0)).fetchall()
    con.close()

    modo = "self" if args.self else "servito"
    if not rows:
        print(f"Nessun prezzo trovato per carburante like '%{args.carburante}%' ({modo}).")
        return 1

    print(f"Classifica '{args.carburante}' ({modo}) - {len(rows)} stazioni:")
    print(f"{'Prezzo':>8}  {'Data':<19}  {'Comune':<20}  Nome")
    for nome, comune, prezzo, dt_comu in rows:
        print(f"{prezzo:>8.3f}  {dt_comu:<19}  {(comune or ''):<20}  {nome or ''}")
    return 0


def cmd_grafico(args):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.dates import DateFormatter, AutoDateLocator
    except ImportError:
        print("matplotlib non installato. Installa con: pip install matplotlib",
              file=sys.stderr)
        return 2

    from datetime import datetime

    con = sqlite3.connect(DB_PATH)
    info = con.execute("SELECT nome, comune FROM impianti WHERE id = ?",
                       (args.impianto,)).fetchone()
    if info is None:
        print(f"Impianto {args.impianto} non presente in DB.", file=sys.stderr)
        con.close()
        return 1
    nome, comune = info

    rows = con.execute("""
        SELECT dt_comu, prezzo, self, carburante
        FROM prezzi
        WHERE impianto_id = ? AND carburante LIKE ?
        ORDER BY dt_comu
    """, (args.impianto, f"%{args.carburante}%")).fetchall()
    con.close()

    if not rows:
        print(f"Nessun dato per impianto {args.impianto}, "
              f"carburante like '%{args.carburante}%'.", file=sys.stderr)
        return 1

    # Una serie per (descCarburante, self): self e servito hanno scale diverse, e
    # marche commerciali (es. 'HiQ Diesel' vs 'Gasolio') vanno tenute distinte.
    serie = defaultdict(lambda: ([], []))
    for dt_comu, prezzo, self_, carb in rows:
        try:
            x = datetime.fromisoformat(dt_comu)
        except ValueError:
            continue
        key = (carb, self_)
        serie[key][0].append(x)
        serie[key][1].append(prezzo)

    fig, ax = plt.subplots(figsize=(11, 5))
    for (carb, self_), (xs, ys) in sorted(serie.items()):
        label = f"{carb} ({'self' if self_ else 'servito'})"
        ax.step(xs, ys, where="post", marker=".", markersize=3, label=label)

    titolo_loc = f"{nome or 'impianto ' + str(args.impianto)}"
    if comune:
        titolo_loc += f" - {comune}"
    ax.set_title(f"Andamento prezzo: {titolo_loc}")
    ax.set_xlabel("Data comunicazione")
    ax.set_ylabel("Prezzo (EUR/litro)")
    ax.grid(True, alpha=0.3)
    ax.legend(loc="best", fontsize=9)
    ax.xaxis.set_major_locator(AutoDateLocator())
    ax.xaxis.set_major_formatter(DateFormatter("%Y-%m-%d"))
    fig.autofmt_xdate()
    fig.tight_layout()

    out = args.output or f"andamento_{args.impianto}_{args.carburante}.png"
    fig.savefig(out, dpi=120)
    plt.close(fig)
    print(f"Salvato: {out}  ({sum(len(xs) for xs,_ in serie.values())} punti, "
          f"{len(serie)} serie)")
    return 0


def main(argv=None):
    p = argparse.ArgumentParser(description="Query sul DB carburanti.")
    sub = p.add_subparsers(dest="cmd", required=True)

    pm = sub.add_parser("minimo",
                        help="Classifica stazioni locali per ultimo prezzo noto.")
    pm.add_argument("--carburante", default="GPL",
                    help="Sottostringa del carburante (default: GPL).")
    g = pm.add_mutually_exclusive_group()
    g.add_argument("--self", dest="self", action="store_true", default=True,
                   help="Prezzi self-service (default).")
    g.add_argument("--servito", dest="self", action="store_false",
                   help="Prezzi serviti invece dei self.")
    pm.set_defaults(func=cmd_minimo)

    pg = sub.add_parser("grafico",
                        help="PNG andamento storico per un impianto + carburante.")
    pg.add_argument("--impianto", type=int, required=True, help="idImpianto MIMIT.")
    pg.add_argument("--carburante", required=True,
                    help="Sottostringa del carburante (es. Benzina, GPL).")
    pg.add_argument("--output", help="Path PNG di output (default: derivato dagli argomenti).")
    pg.set_defaults(func=cmd_grafico)

    args = p.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
