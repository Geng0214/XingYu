#!/usr/bin/env python
"""写 evidence/M0/licence-texts/sources.json：本轮取回的许可正文清单。

字节数、SHA-256、行数、取回时间全部就地量得（取回时间用文件 mtime），
只有 fetched_via / honest_negatives 是叙述性的，与 THIRD-PARTY-NOTICES.md 第 9 节的口径一致。
"""
import hashlib
import json
import os
import time

HERE = os.path.dirname(os.path.abspath(__file__))
TXT = os.path.normpath(os.path.join(HERE, "..", "licence-texts"))

URLS = {
    "SPDX-AGPL-3.0-only.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/AGPL-3.0-only.txt",
    "ASF-Apache-2.0.txt": "https://www.apache.org/licenses/LICENSE-2.0.txt",
    "SPDX-BSD-2-Clause.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/BSD-2-Clause.txt",
    "SPDX-BSD-3-Clause.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/BSD-3-Clause.txt",
    "OWNER-BouncyCastle.txt": "https://www.bouncycastle.org/license.html",
    "OWNER-EDL-1.0.txt": "https://www.eclipse.org/org/documents/edl-v10.php",
    "SPDX-EPL-1.0.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/EPL-1.0.txt",
    "FSF-GPL-2.0.txt": "https://www.gnu.org/licenses/gpl-2.0.txt",
    "FSF-GPL-3.0.txt": "https://www.gnu.org/licenses/gpl-3.0.txt",
    "SPDX-ISC.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/ISC.txt",
    "FSF-LGPL-3.0.txt": "https://www.gnu.org/licenses/lgpl-3.0.txt",
    "SPDX-MIT.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/MIT.txt",
    "SPDX-OLDAP-2.8.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/OLDAP-2.8.txt",
    "UPSTREAM-junrar-LICENSE.txt": "https://raw.githubusercontent.com/junrar/junrar/v7.5.5/LICENSE",
    "SPDX-Unicode-3.0.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Unicode-3.0.txt",
    "SPDX-Unlicense.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Unlicense.txt",
    "SPDX-Zlib.txt": "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Zlib.txt",
}

CHANNEL = {
    "OWNER-BouncyCastle.txt": "权利方页面 HTML 还原为纯文本，只取该页给出的条款段落；原始 HTML 保留在 raw_licence.html",
    "OWNER-EDL-1.0.txt": "权利方页面 HTML 还原为纯文本；原始 HTML 保留在 raw_edl-v10.php",
    "UPSTREAM-junrar-LICENSE.txt": "上游仓库 tag v7.5.5（即被分发版本）的 LICENSE 原文；与 master 上同名文件逐字节相同，且该文件在 master 的唯一提交为 2020-07-18，早于 7.5.5，故对被分发版本有效。tag 真伪经 git ls-remote --tags 实测（dabca2849b463847…）。构件本体（junrar-7.5.5.jar，118 条目）内无任何许可文本，这正是必须回到上游 tag 取的原因。",
    "ASF-Apache-2.0.txt": "Apache 官方纯文本",
    "FSF-LGPL-3.0.txt": "FSF 官方纯文本",
    "FSF-GPL-3.0.txt": "FSF 官方纯文本",
    "FSF-GPL-2.0.txt": "FSF 官方纯文本",
}

SPDX = {
    "SPDX-AGPL-3.0-only.txt": "AGPL-3.0-only",
    "ASF-Apache-2.0.txt": "Apache-2.0",
    "SPDX-BSD-2-Clause.txt": "BSD-2-Clause",
    "SPDX-BSD-3-Clause.txt": "BSD-3-Clause",
    "OWNER-BouncyCastle.txt": "BouncyCastle",
    "OWNER-EDL-1.0.txt": "EDL-1.0",
    "SPDX-EPL-1.0.txt": "EPL-1.0",
    "FSF-GPL-2.0.txt": "GPL-2.0-only",
    "FSF-GPL-3.0.txt": "GPL-3.0-only",
    "SPDX-ISC.txt": "ISC",
    "FSF-LGPL-3.0.txt": "LGPL-3.0-only",
    "SPDX-MIT.txt": "MIT",
    "SPDX-OLDAP-2.8.txt": "OLDAP-2.8",
    "UPSTREAM-junrar-LICENSE.txt": "UnRAR",
    "SPDX-Unicode-3.0.txt": "Unicode-3.0",
    "SPDX-Unlicense.txt": "Unlicense",
    "SPDX-Zlib.txt": "Zlib",
}

on_disk = {f for f in os.listdir(TXT) if f.endswith(".txt")}
missing = set(URLS) ^ on_disk
if missing:
    raise SystemExit(f"licence-texts/ 与本脚本清单不一致：{sorted(missing)}")

entries = []
for name in sorted(URLS):
    path = os.path.join(TXT, name)
    raw = open(path, "rb").read()
    try:
        raw.decode("utf-8", errors="strict")
        encoding = "utf-8"
    except UnicodeDecodeError:
        encoding = "not-utf8"
    entries.append({
        "file": name,
        "spdx_id": SPDX[name],
        "url": URLS[name],
        "channel": CHANNEL.get(name, "上游/SPDX 纯文本直取，未做改写"),
        "bytes": len(raw),
        "sha256_16": hashlib.sha256(raw).hexdigest()[:16],
        "lines": raw.count(b"\n"),
        "encoding": encoding,
        "retrieved_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(os.path.getmtime(path))),
        "embedded_in": "THIRD-PARTY-NOTICES.md 第 9 节逐字附出",
    })

out = {
    "purpose": "记录 THIRD-PARTY-NOTICES.md 第 9 节所附许可正文的取用来源与指纹；正文只用字节级比对校验，未做任何行级改写。",
    "method": "curl（经 http://127.0.0.1:7897 代理）取回 → 就地计算 SHA-256 与字节数 → 生成脚本 evidence/M0/scripts/licence_texts_sources.py 重写本文件。",
    "texts": entries,
    "raw_html_kept": [
        {"file": "raw_edl-v10.php", "note": "Eclipse 官方页面原文，OWNER-EDL-1.0.txt 由它还原而来，保留以便复核还原过程。"},
        {"file": "raw_licence.html", "note": "bouncycastle.org/license.html 原文，OWNER-BouncyCastle.txt 由它还原而来。"},
    ],
    "honest_negatives": [
        {
            "item": "AGPL-3.0-only 未从 FSF 原始地址取回",
            "detail": "https://www.gnu.org/licenses/agpl-3.0.txt 在本机经代理三次 curl 均返回状态 000；"
                      "改用 SPDX 镜像 spdx/license-list-data 的 text/AGPL-3.0-only.txt。"
                      "该镜像含 Preamble 与 How to Apply These Terms 全部段落，共 235 行。"
                      "若要求以 FSF 原始页面为准，需换网络重取并逐字节比对。",
        },
        {
            "item": "EDL-1.0 与 BouncyCastle 不在 SPDX 许可列表内",
            "detail": "spdx/license-list-data 的 licenses.json 实测 740 项，无 `EDL-1.0` 与 `BouncyCastle` 这两个 id，"
                      "所以 SPDX 的 text/ 路径必然 404；改取权利方页面。"
                      "Eclipse 官方在 EDL-1.0 页面上把其 SPDX 标识标为 `BSD-3-Clause`。",
        },
        {
            "item": "ObjectBox Binary Licence 正文未取得，且不可取",
            "detail": "POM 的 url 字段只指向 objectbox.io 站点根。站点公开可下载的是《ObjectBox Terms Of Use》PDF"
                      "（本轮取回后核验：其 §2 明说产品条款另在下载地址提供，即该 PDF 本身不是 Binary Licence），"
                      "且其 §5 禁止再制该文档内容 ⇒ 即便拿到了也不得复制进本仓库。该缺口保留在第 2 节未闭合项。",
        },
    ],
    "boundary": "本清单只证明“这些文本在这些地址上取到、SHA-256 与字节数如此”。"
                "它不主张某个构件确实按该条款分发——那一项逐条写在 THIRD-PARTY-NOTICES.md 第 2/4/8 节。",
}

dst = os.path.join(TXT, "sources.json")
with open(dst, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(out, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print(f"wrote {dst}: {len(out['texts'])} texts, {len(out['honest_negatives'])} negatives, "
      f"{os.path.getsize(dst):,} bytes")
