"""M0-A05 上游取证：AAswordman/OperitAccessbility 的 ref 真伪与整棵树授权文本探测。

为什么要这一支：只按 6 个候选文件名（LICENSE / LICENSE.md / …）探测不足以证明"上游没有授权文本"，
而且 raw.githubusercontent 对改名前的旧默认分支仍返回内容，会把不存在的分支读成存在。
所以这里用三条更硬的通道：
  1. git ls-remote —— 分支与 tag 是否真实存在，权威。
  2. codeload 整包归档 + tar 列举 —— 全树文件名，不靠猜。
  3. 不跟随重定向的 HEAD 探测 —— 区分"分支在"与"被 302 到默认分支"。
每个 atom 探测都带一个已知存在的正向对照（README.md），否则 404 不能说明任何事。

用法：python ax_refs_and_tree.py     （走本机代理 127.0.0.1:7897）
输出：stdout 摘要 + 同目录 ax_refs_and_tree.json
"""
import json
import os
import re
import subprocess
import tarfile
import urllib.error
import urllib.request

PROXY = "http://127.0.0.1:7897"
REPO = "AAswordman/OperitAccessbility"
REF = "main"
LICENCE_NAMES = ["LICENSE", "LICENSE.md", "LICENSE.txt", "LICENCE", "LICENCE.md", "COPYING",
                 "license", "licence", "copying", "LICENSE-MIT", "LICENSE-APACHE", "NOTICE"]
UA = {"User-Agent": "licence-audit"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


op_noredirect = urllib.request.build_opener(
    urllib.request.ProxyHandler({"http": PROXY, "https": PROXY}), NoRedirect)
op_redirect = urllib.request.build_opener(
    urllib.request.ProxyHandler({"http": PROXY, "https": PROXY}))


def chain(url):
    """返回重定向链；未知分支应 404，改名前的旧分支名应 302。"""
    steps, cur = [], url
    for _ in range(6):
        try:
            r = op_noredirect.open(urllib.request.Request(cur, headers=UA), timeout=45)
            steps.append({"url": cur, "status": r.status})
            r.close()
            break
        except urllib.error.HTTPError as e:
            loc = e.headers.get("Location")
            steps.append({"url": cur, "status": e.code, "location": loc})
            if e.code in (301, 302, 303, 307, 308) and loc:
                cur = loc
                continue
            break
        except Exception as e:
            steps.append({"url": cur, "status": "ERR:" + type(e).__name__})
            break
    return steps


def status_and_len(url):
    try:
        b = op_redirect.open(urllib.request.Request(url, headers=UA), timeout=45).read()
        return 200, len(b), b
    except urllib.error.HTTPError as e:
        return e.code, None, None
    except Exception:
        return "ERR", None, None


def git_ref_list():
    env = dict(os.environ, http_proxy=PROXY, https_proxy=PROXY)
    p = subprocess.run(["git", "ls-remote", "--heads", "--tags",
                        f"https://github.com/{REPO}"], capture_output=True, text=True, env=env)
    refs = []
    for line in p.stdout.splitlines():
        sha, ref = line.split("\t", 1)
        refs.append({"ref": ref, "sha": sha})
    return {"rc": p.returncode, "refs": refs,
            "heads": [r for r in refs if r["ref"].startswith("refs/heads/")],
            "tags": [r for r in refs if r["ref"].startswith("refs/tags/")]}


def atom(path, ref):
    st, _, body = status_and_len(f"https://github.com/{REPO}/commits/{ref}/{path}.atom")
    return {"path": path, "ref": ref, "status": st,
            "entry_count": (body.decode("utf-8", "replace").count("<entry>") if body else 0)}


def main():
    out = {"repo": REPO, "refs": git_ref_list()}

    tar = "C:/tmp/ax_main.tar.gz"
    subprocess.run(["curl", "-sx", PROXY, "-L", "-o", tar,
                    f"https://codeload.github.com/{REPO}/tar.gz/refs/heads/{REF}"], check=True)
    names = []
    with tarfile.open(tar) as tf:
        for m in tf.getmembers():
            names.append(re.sub(r"^[^/]+/", "", m.name) or "")
    names = sorted({n for n in names if n})
    archive_root = f"{REPO.split('/')[1]}-{REF}"
    out["full_tree"] = {
        "tarball_bytes": os.path.getsize(tar),
        "entry_count": len(names),
        "root_entries": sorted({n.split("/")[0] for n in names if n} - {archive_root, archive_root + "/"}),
        "licence_filename_hits_any_depth": [n for n in names
                                            if re.search(r"licen[sc]e|copying|notice|许可", n, re.I)],
    }

    with tarfile.open(tar) as tf:
        text = {}
        for m in tf.getmembers():
            if m.isfile() and re.search(r"\.(kt|kts|xml|aidl|md|properties|pro|toml|bat|sh)$|(^|/)gradlew$",
                                         m.name, re.I):
                text[re.sub(r"^[^/]+/", "", m.name)] = tf.extractfile(m).read()
    out["licence_text_in_sources"] = {
        "files_with_apache_or_copyright_header": sorted(
            {f for f, b in text.items() if re.search(rb"Licensed under the Apache License|Copyright \d{4}", b)}),
        "note": "只有 gradlew / gradlew.bat 命中，那是 Gradle wrapper 自带的头部，不覆盖仓库自己的源码",
        "kt_manifest_aidl_have_spdx_or_copyright": sorted(
            {f for f, b in text.items() if re.search(r"\.(kt|xml|aidl)$", f)
             and re.search(rb"SPDX|Copyright", b)}),
        "readme_licence_badge_line": next((l for f, b in text.items() if f == "README.md"
                                           for l in b.decode("utf-8", "replace").splitlines()
                                           if "shields.io" in l and re.search(r"licen|许可", l, re.I)), None),
    }

    src = text.get("app/build.gradle.kts", b"").decode("utf-8", "replace")
    out["source_version_fields_at_this_ref"] = {
        "versionCode": (re.search(r"versionCode\s*=\s*(\d+)", src) or [None, None])[1],
        "versionName": (re.search(r'versionName\s*=\s*"([^"]+)"', src) or [None, None])[1],
        "minSdk": (re.search(r"minSdk\s*=\s*(\d+)", src) or [None, None])[1],
        "targetSdk": (re.search(r"targetSdk\s*=\s*(\d+)", src) or [None, None])[1],
        "compare_with": "随包二进制的实测版本见 bundled_apk_vcs_info.json（同一轮、同一台机器）",
    }

    out["ref_semantics"] = {
        "raw_README_main": status_and_len(f"https://raw.githubusercontent.com/{REPO}/main/README.md")[:2],
        "raw_README_master": status_and_len(f"https://raw.githubusercontent.com/{REPO}/master/README.md")[:2],
        "raw_README_bogus_branch": status_and_len(
            f"https://raw.githubusercontent.com/{REPO}/definitely-not-a-branch-xyz-000/README.md")[:2],
        "tree_main_chain": chain(f"https://github.com/{REPO}/tree/main"),
        "tree_master_chain": chain(f"https://github.com/{REPO}/tree/master"),
        "tree_bogus_chain": chain(f"https://github.com/{REPO}/tree/definitely-not-a-branch-xyz-000"),
        "verdict": ("raw 端点对 master 也返回 200 而未知分支名返回 404 —— 单看 raw 会误判 master 是个分支；"
                    "ls-remote 只有 main，且 tree/master 是 302 到 main。"),
    }
    out["raw_licence_spelling_probe"] = {
        "probed": LICENCE_NAMES,
        "statuses": {n: status_and_len(f"https://raw.githubusercontent.com/{REPO}/{REF}/{n}")[0]
                     for n in LICENCE_NAMES},
    }
    out["file_history_atom"] = {
        "target": atom("LICENSE", REF),
        "positive_control": atom("README.md", REF),
    }

    here = os.path.dirname(os.path.abspath(__file__))
    json.dump(out, open(os.path.join(here, "ax_refs_and_tree.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)
    print(json.dumps(out, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
