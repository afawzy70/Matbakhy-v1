"""Bounded load for the local staging rehearsal (public read endpoints only; synthetic data; not a capacity claim).

usage: load.py <preview-domain> <seconds> <threads>
Prints one JSON line: requests, errors, status counts and latency percentiles per path.
"""
import json
import os
import ssl
import statistics
import sys
import threading
import time
import urllib.request

domain, seconds, threads = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
paths = [f"https://app.{domain}/", f"https://api.{domain}/api/v1/configuration/public", f"https://api.{domain}/api/v1/meals?limit=5",
         f"https://api.{domain}/api/v1/zones", f"https://id.{domain}/realms/matbakhy/.well-known/openid-configuration"]
ctx = ssl.create_default_context()  # certificate and hostname verification stay ON (SEC-003)
ca_file = os.environ.get("REHEARSAL_CA_FILE")
if ca_file:
    ctx.load_verify_locations(cafile=ca_file)  # an explicitly scoped test CA, e.g. the local rehearsal's Caddy root
if os.environ.get("REHEARSAL_INSECURE_LOCAL_TLS") == "1":
    # Local rehearsal only: the proxy there uses a throwaway internal CA. Refused for any non-.localhost domain.
    if not domain.endswith(".localhost"):
        sys.exit("REHEARSAL_INSECURE_LOCAL_TLS is only allowed for *.localhost rehearsal domains")
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
results = {p: [] for p in paths}
errors = {p: 0 for p in paths}
status = {}
lock = threading.Lock()
stop = time.time() + seconds


def worker(n):
    i = n
    while time.time() < stop:
        path = paths[i % len(paths)]
        i += 1
        t0 = time.perf_counter()
        code = 0
        try:
            with urllib.request.urlopen(urllib.request.Request(path, headers={"Accept-Language": "ar"}), context=ctx, timeout=20) as r:
                r.read()
                code = r.status
        except urllib.error.HTTPError as e:
            code = e.code
        except Exception:
            code = -1
        ms = (time.perf_counter() - t0) * 1000
        with lock:
            results[path].append(ms)
            status[code] = status.get(code, 0) + 1
            if code != 200:
                errors[path] += 1


pool = [threading.Thread(target=worker, args=(n,)) for n in range(threads)]
for t in pool:
    t.start()
for t in pool:
    t.join()


def pct(values, q):
    values = sorted(values)
    return round(values[min(len(values) - 1, int(len(values) * q))], 1) if values else None


out = {"seconds": seconds, "threads": threads, "requests": sum(len(v) for v in results.values()), "status": status,
       "paths": {p.split(domain)[1] or "/": {"n": len(v), "errors": errors[p], "p50_ms": pct(v, 0.5), "p95_ms": pct(v, 0.95),
                                           "max_ms": round(max(v), 1) if v else None} for p, v in results.items()}}
print(json.dumps(out))
sys.exit(0 if all(e == 0 for e in errors.values()) else 1)
