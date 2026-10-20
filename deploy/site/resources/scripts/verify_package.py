#!/usr/bin/env python3
import argparse,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--root',default=str(Path(__file__).resolve().parents[2]));a=p.parse_args()
root=Path(a.root).resolve();manifest=json.loads((root/'resources/manifests/package-sha256.json').read_text(encoding='utf-8'));bad=[]
for rel,h in manifest.items():
 path=(root/rel).resolve()
 if not path.is_relative_to(root) or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=h:bad.append(rel)
if bad:raise SystemExit('Missing/modified files: '+', '.join(bad))
print('PACKAGE FILES VERIFIED:',len(manifest),'This does not mean models/images are present or onsite deployment passed.')
