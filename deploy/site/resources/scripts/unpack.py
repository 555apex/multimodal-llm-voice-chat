#!/usr/bin/env python3
"""Verify all paths before extracting data/model archives. Destination must be empty."""
import argparse,tarfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--archive',required=True);p.add_argument('--destination',required=True);a=p.parse_args()
dest=Path(a.destination).resolve()
if len(dest.parts)<3 or dest==Path('/'):raise SystemExit('Use a dedicated directory')
if dest.exists() and any(dest.iterdir()):raise SystemExit('Destination must be empty')
with tarfile.open(a.archive,'r:*') as t:
 members=t.getmembers()
 for m in members:
  target=(dest/m.name).resolve()
  if not target.is_relative_to(dest) or m.issym() or m.islnk() or not (m.isfile() or m.isdir()):raise SystemExit('Unsafe archive member')
 dest.mkdir(parents=True,exist_ok=True)
 t.extractall(dest,members=members,filter='data')
print('Unpacked into',dest,'; permissions/ownership must match the target containers')
