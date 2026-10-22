#!/usr/bin/env python
"""把 APK 里的某个资源条目归属到具体构件：在 Gradle 缓存的全部 AAR/JAR 里按条目名检索。

用于 `THIRD-PARTY-NOTICES.md` §8.1 的 exoplayer 一支——"唯一命中"这句话要有可重跑的出处。
命中是正证据；未命中不是"没有该条目"的反证据（缓存里没有的构件、或构建期 transform 才生成的条目都扫不到）。
"""
import json
import os
import sys
import time
import zipfile

CACHE = os.environ.get("GRADLE_USER_HOME", "C:/Users/15942/Desktop/Work/.gradle-m0") + "/caches"
TARGETS = [
    "res/font/roboto_medium_numbers.ttf",
    "res/font/jetbrains_mono_nerd_font_regular.ttf",
]

if not os.path.isdir(CACHE):
    raise SystemExit(f"cache root missing: {CACHE}")

scanned, errors, hits = 0, 0, {t: [] for t in TARGETS}
for dp, _, fns in os.walk(CACHE):
    for fn in fns:
        if not fn.endswith((".aar", ".jar")):
            continue
        path = os.path.join(dp, fn)
        try:
            names = set(zipfile.ZipFile(path).namelist())
        except Exception:
            errors += 1
            continue
        scanned += 1
        for t in TARGETS:
            if t in names:
                hits[t].append(path)

out = {
    "measuredAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
    "cacheRoot": CACHE,
    "method": "遍历缓存下全部 .aar/.jar，用 zipfile.namelist() 对目标条目名做全等匹配（不是子串、不按文件名猜）",
    "archivesScanned": scanned,
    "archivesUnreadable": errors,
    "hits": hits,
    "caveat": "命中是正证据，未命中不是反证据：transform 期才生成的条目、以及不在本机缓存里的构件都扫不到。",
}
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "font-carrier-scan.json")
with open(dst, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(out, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
for t in TARGETS:
    print(f"{t}: {len(hits[t])} hit(s) in {scanned} archives")
    for h in hits[t]:
        print("   ", h)
