"""Peak memory per container from `docker stats` samples (lines 'name,usage / limit'), compared with the ceilings.

usage: peaks.py <samples-file>
"""
import re
import sys

UNIT = {"b": 1, "kib": 1024, "mib": 1024 ** 2, "gib": 1024 ** 3, "kb": 1000, "mb": 1000 ** 2, "gb": 1000 ** 3}


def size(text):
    m = re.fullmatch(r"([\d.]+)\s*([A-Za-z]+)", text.strip())
    return float(m.group(1)) * UNIT[m.group(2).lower()]


peak, limit = {}, {}
for line in open(sys.argv[1], encoding="utf-8"):
    if "," not in line or "/" not in line:
        continue
    name, usage = line.strip().split(",", 1)
    used, lim = usage.split("/")
    name = re.sub(r"^.*?-(?=[a-z-]+-1$)", "", name).removesuffix("-1")
    peak[name] = max(peak.get(name, 0), size(used))
    limit[name] = size(lim)
total = 0
print(f"{'service':<16}{'peak MiB':>10}{'ceiling MiB':>13}{'share':>8}")
for name in sorted(peak):
    total += peak[name]
    print(f"{name:<16}{peak[name] / 1024 ** 2:>10.0f}{limit[name] / 1024 ** 2:>13.0f}{100 * peak[name] / limit[name]:>7.0f}%")
print(f"{'sum of peaks':<16}{total / 1024 ** 2:>10.0f}   (peaks need not coincide; the sum is an upper bound of use)")
