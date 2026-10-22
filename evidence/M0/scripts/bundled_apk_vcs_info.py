"""M0-A05：随包 accessibility.apk / desktop.apk / shizuku.apk 的来源侧实测（零联网部分）。

三条独立证据，都只读分发物本身：
  1. META-INF/version-control-info.textproto —— AGP 写进产物的构建时 git 元数据。
     读得到 revision 就证明打包时 .git 在位；读到 NO_SUPPORTED_VCS_FOUND 就是"不在任何工作树里"的正向证据。
  2. aapt2 dump badging —— 随包真实的 versionCode/versionName/SDK。
  3. apksigner verify --print-certs —— 签名者 DN 与证书指纹。

用法：python bundled_apk_vcs_info.py
输出：stdout 摘要 + 同目录 bundled_apk_vcs_info.json
"""
import json
import os
import re
import subprocess

SDK = r"C:/Users/15942/dev/sdk/android-sdk"
BUILD_TOOLS = os.path.join(SDK, "build-tools", "36.0.0")
JAVA_HOME = r"C:/Users/15942/dev/jdk-21"
REPO = r"C:/Users/15942/Desktop/Work/operit"
APKS = ["accessibility.apk", "desktop.apk", "shizuku.apk"]
ASSETS = os.path.join(REPO, "app", "src", "main", "assets")


def run(cmd, env=None):
    p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", env=env, shell=False)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def unzip_show(apk, name):
    rc, out = run(["unzip", "-p", apk, name])
    return out if rc == 0 else None


def unzip_has(apk, pattern):
    rc, out = run(["unzip", "-Z1", apk, pattern])
    return rc == 0 and out.strip() != ""


def badging(apk):
    rc, out = run([os.path.join(BUILD_TOOLS, "aapt2.exe"), "dump", "badging", apk])
    m = re.search(r"package: name='([^']*)' versionCode='(\d+)' versionName='([^']*)'", out)
    t = re.search(r"targetSdkVersion:'(\d+)'", out)
    if not m:
        return {"parse_ok": False, "rc": rc}
    return {"parse_ok": True, "package": m.group(1),
            "versionCode": int(m.group(2)), "versionName": m.group(3),
            "targetSdk": t.group(1) if t else None}


def certs(apk):
    env = dict(os.environ, JAVA_HOME=JAVA_HOME)
    rc, out = run([os.path.join(BUILD_TOOLS, "apksigner.bat"),
                   "verify", "--print-certs", apk], env=env)
    dn = re.search(r"certificate DN: (.*)", out)
    sha = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", out)
    return {"dn": dn.group(1).strip() if dn else None,
            "cert_sha256": sha.group(1) if sha else None,
            "has_jar_signature_meta_inf_rsa": unzip_has(apk, "META-INF/*.RSA")}


def main():
    out = {}
    for name in APKS:
        apk = os.path.join(ASSETS, name)
        vci = unzip_show(apk, "META-INF/version-control-info.textproto")
        rec = {
            "bytes_on_disk": os.path.getsize(apk),
            "version_control_info_textproto": (vci or "").strip() or "(文件不存在)",
            "revision_in_textproto": (re.search(r'revision: "([0-9a-f]{40})"', vci or "").group(1)
                                      if vci and re.search(r'revision: "([0-9a-f]{40})"', vci) else None),
            "no_vcs_found": bool(vci and "NO_SUPPORTED_VCS_FOUND" in vci),
            "badging": badging(apk),
            "signature": certs(apk),
        }
        out[name] = rec
        print(name, json.dumps(rec, ensure_ascii=False, indent=1))
    here = os.path.dirname(os.path.abspath(__file__))
    json.dump(out, open(os.path.join(here, "bundled_apk_vcs_info.json"), "w", encoding="utf-8"),
              indent=1, ensure_ascii=False)


if __name__ == "__main__":
    main()
