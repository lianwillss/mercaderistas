#!/usr/bin/env python3
"""Regenera tools/locales-rutas.json desde el rutero de Google Drive.

Cruza RUTERO + COD KPI ONE de la hoja RUTA RUTERO y deja un mapa
codigo -> ["EMU-3", ...] que stock-sync.mjs inserta en stock.json.

Uso:  python3 tools/rutas-sync.py
"""

import collections
import json
import subprocess
import sys
import urllib.request

RUTERO_URL = (
    "https://docs.google.com/spreadsheets/d/18PejQq99XzNyTDf0fFku0hHwMs2sG1X5/export?format=xlsx"
)
SALIDA = "tools/locales-rutas.json"
HOJA = "RUTA RUTERO"
DESTINO = "/tmp/rutero.xlsx"


def descargar():
    # curl usa los certificados del sistema; urllib en python.org suele fallar.
    try:
        subprocess.run(
            ["curl", "-sSL", "--fail", "-o", DESTINO, RUTERO_URL],
            check=True,
            timeout=120,
        )
    except (OSError, subprocess.SubprocessError):
        with urllib.request.urlopen(RUTERO_URL, timeout=60) as respuesta:
            with open(DESTINO, "wb") as archivo:
                archivo.write(respuesta.read())


def main() -> int:
    try:
        import openpyxl
    except ImportError:
        print("Falta openpyxl: pip3 install openpyxl", file=sys.stderr)
        return 1

    print("Descargando rutero…")
    descargar()

    libro = openpyxl.load_workbook(DESTINO, read_only=True)
    if HOJA not in libro.sheetnames:
        print("No encuentro la hoja " + HOJA, file=sys.stderr)
        return 1

    hoja = libro[HOJA]
    filas = hoja.iter_rows(values_only=True)
    cabecera = [str(c).strip() if c is not None else "" for c in next(filas)]
    indice = {nombre: i for i, nombre in enumerate(cabecera)}

    def valor(fila, columna):
        i = indice.get(columna, -1)
        return fila[i] if 0 <= i < len(fila) else None

    mapa = collections.defaultdict(set)
    for fila in filas:
        if not any(c not in (None, "") for c in fila):
            continue
        if "RM" not in str(valor(fila, "REGION") or "").upper():
            continue
        codigo = str(valor(fila, "COD KPI ONE") or "").strip()
        ruta = str(valor(fila, "RUTERO") or "").strip()
        if codigo and ruta:
            mapa[codigo].add(ruta)

    salida = {codigo: sorted(rutas) for codigo, rutas in sorted(mapa.items())}
    with open(SALIDA, "w", encoding="utf-8") as archivo:
        json.dump(salida, archivo, ensure_ascii=False, indent=0)

    rutas = {r for rs in salida.values() for r in rs}
    print("Listo: " + SALIDA)
    print("  locales: " + str(len(salida)) + " | rutas: " + str(len(rutas)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
