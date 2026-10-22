import json, re, zipfile
APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
nd = b'com/itextpdf/text/'
z = zipfile.ZipFile(APK)
rows = []
for n in [x for x in z.namelist() if x.endswith('.dex')]:
    d = z.read(n)
    for m in re.finditer(re.escape(nd), d):
        i = m.start()
        prev = d[i-1:i]
        if prev == b'L':
            continue
        seg = d[max(0, i-40):i+60]
        rows.append({'dex': n, 'off': i, 'prev_byte': prev.hex() or 'none',
                     'ctx_ascii': re.sub(rb'[^ -~]', b'.', seg).decode()})
print('non-anchored-only occurrences:', len(rows))
for r in rows:
    print(r['dex'], hex(r['off']), r['prev_byte'], '|', r['ctx_ascii'])
json.dump(rows, open('C:/tmp/itextg_ctx.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
