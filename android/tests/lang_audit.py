#!/usr/bin/env python3
"""Wyciąga wszystkie literały z Lang.t("...")/Lang.f("...") i zapisuje jako JSON (jedna lista)."""
import re, sys, json, glob, os
src = sys.argv[1]
out = sys.argv[2]
pat = re.compile(r'Lang\.[tf]\(\s*"((?:[^"\\]|\\.)*)"')
found = {}
for fn in sorted(glob.glob(os.path.join(src, '*.java'))):
    if os.path.basename(fn) in ('Lang.java', 'LangTable.java'):
        continue
    text = open(fn, encoding='utf-8').read()
    for m in pat.finditer(text):
        raw = m.group(1)
        s = json.loads('"' + raw + '"')
        found.setdefault(s, []).append(os.path.basename(fn))
json.dump(found, open(out, 'w', encoding='utf-8'), ensure_ascii=True)
print(len(found), 'literałów')
