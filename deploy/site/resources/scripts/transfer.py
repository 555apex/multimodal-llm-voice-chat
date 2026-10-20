#!/usr/bin/env python3
"""Binary-safe SSH receive and deterministic split/join; standard library only."""
import argparse,hashlib,json,os,re,shlex,subprocess
from pathlib import Path
CHUNK=8*1024*1024
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(CHUNK),b''):h.update(b)
 return h.hexdigest()
def complete(part,target,expected):
 actual=sha(part)
 if actual!=expected:raise RuntimeError('SHA256 mismatch; keep partial for diagnosis')
 if target.exists():raise RuntimeError('Destination exists')
 part.rename(target);print(json.dumps({'file':str(target),'bytes':target.stat().st_size,'sha256':actual}))
def main():
 p=argparse.ArgumentParser();s=p.add_subparsers(dest='mode',required=True)
 r=s.add_parser('receive');r.add_argument('--host',required=True);r.add_argument('--ssh-config');r.add_argument('--remote-path',required=True);r.add_argument('--destination',required=True);r.add_argument('--sha256')
 x=s.add_parser('split');x.add_argument('--file',required=True);x.add_argument('--directory',required=True);x.add_argument('--part-mib',type=int,default=1900)
 j=s.add_parser('join');j.add_argument('--manifest',required=True);j.add_argument('--destination',required=True)
 v=s.add_parser('verify');v.add_argument('--file',required=True);v.add_argument('--sha256',required=True)
 a=p.parse_args()
 if a.mode=='verify':
  if sha(a.file)!=a.sha256.lower():raise SystemExit('SHA256 mismatch')
  print('VERIFIED');return
 if a.mode=='receive':
  if not a.remote_path.startswith('/') or '\n' in a.remote_path:raise SystemExit('Use an absolute remote path')
  base=['ssh']+(['-F',a.ssh_config] if a.ssh_config else [])+['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=15',a.host]
  expected=a.sha256 or subprocess.check_output(base+['sha256sum -- '+shlex.quote(a.remote_path)],text=True).split()[0]
  if not re.fullmatch('[a-fA-F0-9]{64}',expected):raise SystemExit('Invalid SHA256')
  dest=Path(a.destination).resolve();dest.parent.mkdir(parents=True,exist_ok=True);part=dest.with_name(dest.name+'.partial')
  if dest.exists() or part.exists():raise SystemExit('Destination/partial already exists; verify or explicitly remove failed partial before retry')
  with part.open('xb') as f:subprocess.run(base+['cat -- '+shlex.quote(a.remote_path)],stdout=f,check=True)
  complete(part,dest,expected.lower());return
 if a.mode=='split':
  src=Path(a.file).resolve();out=Path(a.directory).resolve()
  if out.exists():raise SystemExit('Split directory must be new')
  if a.part_mib<1:raise SystemExit('part-mib must be positive')
  out.mkdir(parents=True);parts=[]
  with src.open('rb') as f:
   i=0
   while True:
    first=f.read(min(CHUNK,a.part_mib*1024**2))
    if not first:break
    name=f'part-{i:05}.bin';i+=1;remain=a.part_mib*1024**2-len(first)
    with (out/name).open('xb') as g:
     g.write(first)
     while remain:
      b=f.read(min(CHUNK,remain))
      if not b:break
      g.write(b);remain-=len(b)
    parts.append({'name':name,'bytes':(out/name).stat().st_size,'sha256':sha(out/name)})
  (out/'parts.json').write_text(json.dumps({'filename':src.name,'bytes':src.stat().st_size,'sha256':sha(src),'parts':parts},indent=2))
  print('Parts:',len(parts));return
 manifest=Path(a.manifest).resolve();m=json.loads(manifest.read_text());dest=Path(a.destination).resolve();part=dest.with_name(dest.name+'.partial')
 if dest.exists() or part.exists():raise SystemExit('Destination/partial exists')
 dest.parent.mkdir(parents=True,exist_ok=True)
 with part.open('xb') as f:
  for item in m['parts']:
   if Path(item['name']).name!=item['name']:raise SystemExit('Unsafe part name')
   src=manifest.parent/item['name']
   if src.stat().st_size!=item['bytes'] or sha(src)!=item['sha256']:raise SystemExit('Bad/missing part '+item['name'])
   with src.open('rb') as g:
    for b in iter(lambda:g.read(CHUNK),b''):f.write(b)
 if part.stat().st_size!=m['bytes']:raise SystemExit('Size mismatch')
 complete(part,dest,m['sha256'])
if __name__=='__main__':main()
