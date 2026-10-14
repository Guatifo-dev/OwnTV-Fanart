#!/usr/bin/env python3
"""
emby_m3u.py — publica la biblioteca de Emby como una lista M3U para OwnTV (glcn).

Arranca un servidor HTTP mínimo en el Synology. OwnTV pide
    http://IP_DEL_NAS:8098/emby.m3u
y el script consulta a Emby y devuelve todas las películas y episodios:

  * Películas  → type="movie",  nombre "Título (Año)",               pestaña Películas
  * Episodios  → type="series", nombre "Serie S01E02 Título",        pestaña Series
  * Carátula   → la de Emby (tvg-logo)
  * Vídeo      → el archivo original, sin transcodificar (Emby /stream?static=true)

Solo usa la biblioteca estándar de Python 3 (sin instalar nada).

Configuración (variables de entorno o argumentos):
  EMBY_URL      dirección de Emby vista desde la SHIELD, p. ej. http://192.168.1.10:8096
  EMBY_API_KEY  clave creada en Emby → Panel → Avanzado → Claves de API
  M3U_PORT      puerto del servidor (por defecto 8098)
  M3U_CACHE     segundos que se guarda la lista antes de volver a preguntar a Emby (por defecto 600)

Uso:
  EMBY_URL=http://192.168.1.10:8096 EMBY_API_KEY=xxxx python3 emby_m3u.py
  python3 emby_m3u.py --url http://192.168.1.10:8096 --key xxxx [--port 8098]
  python3 emby_m3u.py --url ... --key ... --print      (solo imprime la lista y sale, para probar)
"""
import argparse
import json
import os
import sys
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FIELDS = "ProductionYear,ParentIndexNumber,IndexNumber,SeriesName,SeriesId,Container,Genres"


def emby_get(base, key, path, params):
    q = dict(params)
    q["api_key"] = key
    url = f"{base}/emby{path}?{urllib.parse.urlencode(q)}"
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def all_items(base, key, kind):
    items, start, page = [], 0, 1000
    while True:
        data = emby_get(base, key, "/Items", {
            "Recursive": "true",
            "IncludeItemTypes": kind,
            "Fields": FIELDS,
            "SortBy": "SortName" if kind == "Movie" else "SeriesSortName,ParentIndexNumber,IndexNumber",
            "StartIndex": start,
            "Limit": page,
            "IsMissing": "false",
        })
        batch = data.get("Items", [])
        items.extend(batch)
        start += len(batch)
        if not batch or start >= data.get("TotalRecordCount", 0):
            return items


def attr(v):
    # Comillas y saltos de línea romperían la línea #EXTINF.
    return str(v).replace('"', "'").replace("\n", " ").replace("\r", " ").strip()


def name(v):
    # El nombre va después de la coma; las comas dentro del nombre son válidas, los saltos no.
    return str(v).replace("\n", " ").replace("\r", " ").strip()


def build_m3u(base, key):
    lines = ["#EXTM3U"]
    image = lambda item_id: f"{base}/emby/Items/{item_id}/Images/Primary?maxWidth=500&api_key={key}"
    stream = lambda it: (
        f"{base}/emby/Videos/{it['Id']}/stream"
        + (f".{it['Container'].split(',')[0]}" if it.get("Container") else "")
        + f"?static=true&api_key={key}"
    )

    for m in all_items(base, key, "Movie"):
        title = m.get("Name", "")
        year = m.get("ProductionYear")
        label = f"{title} ({year})" if year else title
        group = (m.get("Genres") or ["Películas"])[0]
        lines.append(
            f'#EXTINF:-1 type="movie" tvg-logo="{attr(image(m["Id"]))}" group-title="{attr(group)}",{name(label)}'
        )
        lines.append(stream(m))

    for e in all_items(base, key, "Episode"):
        show = e.get("SeriesName") or "Serie"
        s = e.get("ParentIndexNumber")
        n = e.get("IndexNumber")
        if s is None or n is None:
            continue  # especiales sin numerar: OwnTV no sabría colocarlos
        title = e.get("Name", "")
        label = f"{show} S{s:02d}E{n:02d} {title}".strip()
        logo = image(e["SeriesId"]) if e.get("SeriesId") else image(e["Id"])
        lines.append(
            f'#EXTINF:-1 type="series" tvg-logo="{attr(logo)}" group-title="Series",{name(label)}'
        )
        lines.append(stream(e))

    return "\n".join(lines) + "\n"


class Cache:
    def __init__(self, base, key, ttl):
        self.base, self.key, self.ttl = base, key, ttl
        self.body, self.at = None, 0.0
        self.lock = threading.Lock()

    def get(self):
        with self.lock:
            if self.body is None or time.time() - self.at > self.ttl:
                self.body = build_m3u(self.base, self.key).encode("utf-8")
                self.at = time.time()
            return self.body


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--url", default=os.environ.get("EMBY_URL"))
    p.add_argument("--key", default=os.environ.get("EMBY_API_KEY"))
    p.add_argument("--port", type=int, default=int(os.environ.get("M3U_PORT", "8098")))
    p.add_argument("--cache", type=int, default=int(os.environ.get("M3U_CACHE", "600")))
    p.add_argument("--print", action="store_true", help="imprime la lista y sale")
    a = p.parse_args()
    if not a.url or not a.key:
        sys.exit("Falta EMBY_URL / EMBY_API_KEY (o --url / --key).")
    base = a.url.rstrip("/")

    if a.print:
        sys.stdout.write(build_m3u(base, a.key))
        return

    cache = Cache(base, a.key, a.cache)

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path.split("?")[0] not in ("/emby.m3u", "/"):
                self.send_error(404)
                return
            try:
                body = cache.get()
            except Exception as ex:  # Emby apagado, clave mala…
                self.send_error(502, f"Emby: {ex}")
                return
            self.send_response(200)
            self.send_header("Content-Type", "audio/x-mpegurl; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            pass

    print(f"Lista en http://<IP-del-NAS>:{a.port}/emby.m3u  (Emby: {base})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", a.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
