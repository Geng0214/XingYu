import io
import json

lic = json.load(io.open('C:/tmp/rust_licenses.json', encoding='utf-8'))
sets = json.load(io.open('C:/tmp/rust_tree_sets.json', encoding='utf-8'))
so = json.load(io.open('C:/tmp/rust_so_tokens.json', encoding='utf-8'))

emb = {}
for k, v in so['path_hits'].items():
    d = k.split('|')[1]
    emb[d] = emb.get(d, 0) + v

runtime = {(p['crate'], p['version']): p['kind'] for p in sets['android_runtime']}
notruntime = {(p['crate'], p['version']): p['kind'] for p in sets['resolved_not_runtime']}

rows = []
for p in lic['packages']:
    key = (p['crate'], p['version'])
    if key in runtime:
        role = 'android-runtime' if runtime[key] == 'lib' else 'build-time-proc-macro'
    elif key in notruntime:
        role = 'host-build-only'
    else:
        role = 'lock-only-not-resolved'
    files = p.get('licence_files') or []
    top = files[0] if files else None
    rows.append({
        'crate': p['crate'],
        'version': p['version'],
        'declared': p.get('licence_declared'),
        'declared_source': p.get('licence_from', 'published Cargo.toml' if p.get('src_present') else None),
        'role_for_android_target': role,
        'licence_file': (top['path'] if top else None),
        'licence_file_sha256_16': (top['sha256_16'] if top else None),
        'licence_file_bytes': (top['bytes'] if top else None),
        'licence_file_text_class': (top['text_class'] if top else None),
        'licence_files_total': len(files),
        'source_path_embedded_in_shipped_so': emb.get('%s-%s' % key, 0),
    })

rows.sort(key=lambda r: (r['crate'], r['version']))
out = {
    'method': 'Cargo.lock 48 包逐个解析；declared 取本机 crates.io 缓存里已发布构件的 Cargo.toml `license` 字段；许可文本取该构件源码目录顶层 LICENSE|LICENCE|COPYING|NOTICE 首个文件，按 SHA-256 前 16 位登记指纹；role_for_android_target 由 `cargo tree --offline --locked --target aarch64-linux-android -e normal` 与 `-e normal,build` 的差集判定；source_path_embedded_in_shipped_so 为在随包 .so 中实测到的 `<crate>-<version>` 源码路径出现次数。',
    'commands': [
        'CARGO_HOME=C:/Users/15942/dev/rust/cargo cargo tree --offline --locked --target aarch64-linux-android -e normal',
        'CARGO_HOME=C:/Users/15942/dev/rust/cargo cargo tree --offline --locked --target aarch64-linux-android -e normal,build',
    ],
    'counts': {
        'lock_packages': len(rows),
        'android_runtime_libs': sum(1 for r in rows if r['role_for_android_target'] == 'android-runtime'),
        'build_time_proc_macro': sum(1 for r in rows if r['role_for_android_target'] == 'build-time-proc-macro'),
        'host_build_only': sum(1 for r in rows if r['role_for_android_target'] == 'host-build-only'),
        'lock_only_not_resolved': sum(1 for r in rows if r['role_for_android_target'] == 'lock-only-not-resolved'),
        'with_embedded_source_path_in_so': sum(1 for r in rows if r['source_path_embedded_in_shipped_so']),
    },
    'packages': rows,
}
io.open('C:/Users/15942/Desktop/Work/operit/evidence/M0/rust-crate-licenses.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
print(json.dumps(out['counts'], ensure_ascii=False))
for r in rows:
    print('%-22s %-9s %-32s %-24s %-8s %-16s %6s %s' % (
        r['crate'], r['version'], str(r['declared']), r['role_for_android_target'],
        str(r['source_path_embedded_in_shipped_so']), str(r['licence_file_text_class']),
        r['licence_file_bytes'] if r['licence_file_bytes'] else '-', r['licence_file_sha256_16'] or ''))
