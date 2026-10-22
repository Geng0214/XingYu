import io
import json

lic = json.load(io.open('C:/tmp/rust_licenses2.json', encoding='utf-8'))['packages']
sets = json.load(io.open('C:/tmp/rust_tree_sets.json', encoding='utf-8'))
so = json.load(io.open('C:/tmp/rust_so_tokens.json', encoding='utf-8'))

emb = {}
for k, v in so['path_hits'].items():
    d = k.split('|')[1]
    emb[d] = emb.get(d, 0) + v

runtime = {(p['crate'], p['version']): p['kind'] for p in sets['android_runtime']}
notruntime = {(p['crate'], p['version']): p['kind'] for p in sets['resolved_not_runtime']}

rows = []
for p in lic:
    key = (p['crate'], p['version'])
    if key in runtime:
        role = 'android-runtime' if runtime[key] == 'lib' else 'build-time-proc-macro'
    elif key in notruntime:
        role = 'host-build-only'
    else:
        role = 'lock-only-not-resolved'
    classes = []
    for f in p['licence_files']:
        for c in f['text_class'].split(' + '):
            if c not in classes:
                classes.append(c)
    rows.append({
        'crate': p['crate'],
        'version': p['version'],
        'source': p['source'],
        'declared_in_published_manifest': p['declared'],
        'text_classes': classes,
        'licence_files': p['licence_files'],
        'role_for_aarch64_linux_android': role,
        'crate_source_path_occurrences_in_shipped_so': emb.get('%s-%s' % key, 0),
    })

rows.sort(key=lambda r: (r['crate'], r['version']))
counts = {
    'lock_packages': len(rows),
    'android_runtime_libs': sum(1 for r in rows if r['role_for_aarch64_linux_android'] == 'android-runtime'),
    'build_time_proc_macro': sum(1 for r in rows if r['role_for_aarch64_linux_android'] == 'build-time-proc-macro'),
    'host_build_only': sum(1 for r in rows if r['role_for_aarch64_linux_android'] == 'host-build-only'),
    'lock_only_not_resolved': sum(1 for r in rows if r['role_for_aarch64_linux_android'] == 'lock-only-not-resolved'),
    'with_embedded_crate_path_in_shipped_so': sum(1 for r in rows if r['crate_source_path_occurrences_in_shipped_so']),
    'copyleft': sum(1 for r in rows if any('GPL' in c for c in r['text_classes'])),
    'no_licence_text_locally': sum(1 for r in rows if not r['licence_files']),
}
by_class = {}
for r in rows:
    k = ' | '.join(r['text_classes']) if r['text_classes'] else 'NONE'
    by_class[k] = by_class.get(k, 0) + 1

out = {
    'measuredAt': '2026-09-19',
    'scope': 'tools/native_ripgrep/Cargo.lock 的全部包 —— 即随包 liboperit_ripgrep.so 的 Rust 依赖层',
    'method': [
        'Cargo.lock 逐 [[package]] 解析（48 条，含 workspace 根包 operit_ripgrep 0.1.0）。',
        'declared_in_published_manifest 取本机注册表缓存里已发布构件 `registry/src/index.crates.io-1949cf8c6b5b557f/<crate>-<ver>/Cargo.toml` 的 license 字段（这是发布时被规范化的值，不读上游仓库工作区）。',
        'licence_files 为该构件目录内文件名以 license/licence/copying/copyright/notice/unlicense 开头的全部文件（跳过 target/.git/tests/examples/benches/fixtures/test_data），逐个记 SHA-256 前 16 位、字节数与按首 1200 字节判出的文本类别。',
        'role_for_aarch64_linux_android 由 cargo tree 的两个集合判定：-e normal（Android 运行期）与 -e normal,build（含宿主构建期）之差即 host-build-only；两处都没出现的即 lock-only-not-resolved。',
        'crate_source_path_occurrences_in_shipped_so 是在随包 .so 的字节里实测 `<registry>/<hash>/<crate>-<ver>/` 路径的出现次数，作为"该 crate 的代码确在二进制里"的正向证据（反向不成立：未出现不代表没链入，只有带 file!()/ panic 串的源文件才会留路径）。',
    ],
    'commands': [
        "CARGO_HOME='C:/Users/15942/dev/rust/cargo' cargo tree --offline --locked --target aarch64-linux-android -e normal",
        "CARGO_HOME='C:/Users/15942/dev/rust/cargo' cargo tree --offline --locked --target aarch64-linux-android -e normal,build",
    ],
    'counts': counts,
    'by_text_class': by_class,
    'packages': rows,
}
io.open('C:/Users/15942/Desktop/Work/operit/evidence/M0/rust-crate-licenses.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
print(json.dumps(counts, ensure_ascii=False, indent=1))
print(json.dumps(by_class, ensure_ascii=False, indent=1))
for r in rows:
    print('%-24s %-9s %-38s %-26s %s' % (r['crate'], r['version'], str(r['declared_in_published_manifest']),
                                          r['role_for_aarch64_linux_android'],
                                          ' | '.join(r['text_classes']) or 'NO-TEXT'))
