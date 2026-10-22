"""两份自有 LICENSE 与 FSF 官方 LGPL-3.0 全文的字节级比对。

官方文本来源：https://www.gnu.org/licenses/lgpl-3.0.txt （需代理，见 README「重跑前提」）。
若 C:/tmp/lgpl-3.0.txt 已存在则直接复用，不重复下载。
"""
import hashlib
import io
import json
import os
import urllib.request

REPO = 'C:/Users/15942/Desktop/Work/operit'
CANON_URL = 'https://www.gnu.org/licenses/lgpl-3.0.txt'
CANON_LOCAL = 'C:/tmp/lgpl-3.0.txt'
PROXY = 'http://127.0.0.1:7897'
OUT = 'C:/tmp/own_license_compare.json'


def sha16(b):
    return hashlib.sha256(b).hexdigest()[:16]


def canonical():
    if os.path.exists(CANON_LOCAL):
        return open(CANON_LOCAL, 'rb').read(), 'local copy'
    handler = urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY})
    with urllib.request.build_opener(handler).open(CANON_URL, timeout=60) as r:
        return r.read(), 'fetched'


def lines_lf(b):
    return b.decode('utf-8', 'replace').replace('\r\n', '\n').splitlines()


def first_aligned_offset(canon_l, file_l, title):
    for i, l in enumerate(file_l):
        if l.strip() == title:
            return i
    return None


def token_diff(canon_b, file_b):
    """词级比对。行级比对在许可文本上没有意义：一个空行的偏移会把后面每一行都算成不同。

    判据：官方文本的词序列是目标文件的**按序子序列**，即目标没有删改任何条款词。
    """
    import difflib
    import re
    ct = re.findall(r'\S+', canon_b.decode('utf-8', 'replace'))
    ft = re.findall(r'\S+', file_b.decode('utf-8', 'replace'))
    sm = difflib.SequenceMatcher(None, ct, ft, autojunk=False)
    groups = [{'tag': t, 'canonical': ' '.join(ct[i1:i2])[:200], 'file': ' '.join(ft[j1:j2])[:200]}
              for t, i1, i2, j1, j2 in sm.get_opcodes() if t != 'equal']
    it = iter(ft)
    return {'canonical_tokens': len(ct), 'file_tokens': len(ft),
            'canonical_is_in_order_subsequence': all(any(c == t for t in it) for c in ct),
            'replaced_or_deleted_groups': [g for g in groups if g['tag'] in ('replace', 'delete')],
            'inserted_groups': [g['file'] for g in groups if g['tag'] == 'insert']}


def align(canon_l, file_l, off):
    match, mism = 0, []
    for k in range(len(canon_l)):
        a = canon_l[k].rstrip()
        b = file_l[off + k].rstrip() if off + k < len(file_l) else '<EOF>'
        if a == b:
            match += 1
        elif len(mism) < 4:
            mism.append({'canonical_line': k, 'canonical': a, 'file': b})
    return match, mism


canon = None
try:
    canon, canon_src = canonical()
except Exception as e:
    canon_src = 'fetch failed: %r' % e

report = {'canonical_source': canon_src, 'files': {}}
if canon:
    cl = lines_lf(canon)
    report['canonical'] = {
        'url': CANON_URL, 'bytes': len(canon), 'lines': len(cl), 'sha256_16': sha16(canon),
        'crlf': canon.count(b'\r\n'),
        'embeds_gpl_full_text': 'The GNU General Public License is a free, copyleft license' in canon.decode('utf-8', 'replace'),
        'last_line': cl[-1].strip(),
    }

for name, rel in (('operit_root', 'LICENSE'), ('operit_terminal', 'terminal/LICENSE')):
    p = os.path.join(REPO, rel)
    b = open(p, 'rb').read()
    fl = lines_lf(b)
    entry = {'path': rel, 'bytes': len(b), 'lines': len(fl), 'sha256_16': sha16(b),
             'crlf': b.count(b'\r\n'), 'nonascii_bytes': sum(1 for c in b if c > 127)}
    if canon:
        entry['byte_equal_canonical_lf_normalised'] = b.replace(b'\r\n', b'\n') == canon
        entry['byte_equal_canonical_as_is'] = b == canon
        off = first_aligned_offset(cl, fl, cl[0].strip())
        entry['canonical_body_starts_at_line'] = off
        if off is not None:
            m, mism = align(cl, fl, off)
            entry['canonical_lines_aligned'] = m
            entry['canonical_lines_total'] = len(cl)
            entry['first_mismatches'] = mism
            entry['lines_after_canonical_span'] = len(fl) - off - len(cl)
        entry['contains_gpl_preamble_opening'] = (
            'The GNU General Public License is a free, copyleft license' in '\n'.join(fl))
        entry['states_verbatim_only'] = 'but changing it is not allowed' in '\n'.join(fl)
        entry['token_diff'] = token_diff(canon, b)
        entry['warning_line_alignment_is_not_the_right_measure'] = (
            'canonical_lines_aligned counts positional matches and collapses when a single blank '
            'line shifts the rest of the file; read token_diff instead')
    report['files'][name] = entry

io.open(OUT, 'w', encoding='utf-8').write(json.dumps(report, ensure_ascii=False, indent=1))
for k, v in report['files'].items():
    td = v.get('token_diff', {})
    print('%-14s bytes=%d sha256_16=%s line_endings_CRLF=%d identical_to_canonical_after_LF=%s '
          'canonical_token_subsequence=%s replace/delete_groups=%d insert_groups=%d' % (
              k, v['bytes'], v['sha256_16'], v['crlf'],
              v.get('byte_equal_canonical_lf_normalised'), td.get('canonical_is_in_order_subsequence'),
              len(td.get('replaced_or_deleted_groups', [])), len(td.get('inserted_groups', []))))
print('wrote', OUT)
