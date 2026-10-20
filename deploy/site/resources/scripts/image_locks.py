#!/usr/bin/env python3
"""Record loaded image IDs. This does not approve the NPU profile."""
import argparse,json,subprocess,platform
from pathlib import Path
from importlib.util import spec_from_file_location,module_from_spec
s=spec_from_file_location('gate',Path(__file__).with_name('04_runtime_gate.py'));m=module_from_spec(s);s.loader.exec_module(m)
p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--output',required=True);a=p.parse_args()
env=m.env_read(a.env_file);locks={}
for key,ref in env.items():
 if key.endswith('_IMAGE') and ref:
  im=json.loads(subprocess.check_output(['docker','image','inspect',ref]))[0]
  if im['Architecture']!={'aarch64':'arm64','x86_64':'amd64'}.get(platform.machine()):raise SystemExit('Wrong architecture: '+key)
  locks[key]={'ref':ref,'id':im['Id'],'repo_digests':im.get('RepoDigests',[])}
path=Path(a.output)
with path.open('x') as f:json.dump(locks,f,indent=2)
print('Recorded IDs; reviewer must compare provenance and merge into target-profile.json images.')
