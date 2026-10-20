#!/usr/bin/env python3
"""Stream selected non-secret resources directly into an explicit destination."""
import argparse,hashlib,json,subprocess,tarfile,shlex
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('--host',required=True,help='Authorized source SSH alias or user@host');p.add_argument('--ssh-config',help='Optional SSH config belonging to the operator');p.add_argument('--kind',choices=['images','models','bge'],required=True);p.add_argument('--destination',required=True);p.add_argument('--image',action='append',default=[]);a=p.parse_args()
 if a.kind=='images' and not a.image:raise SystemExit('Specify each --image explicitly; no automatic export of CUDA images')
 dest=Path(a.destination).resolve();part=dest.with_name(dest.name+'.partial')
 if dest.exists() or part.exists():raise SystemExit('Destination/partial exists')
 dest.parent.mkdir(parents=True,exist_ok=True)
 base=['ssh']+(['-F',a.ssh_config] if a.ssh_config else [])+['-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','UpdateHostKeys=no','-o','ConnectTimeout=15',a.host]
 if a.kind=='images':
  info=json.loads(subprocess.check_output(base+['docker image inspect '+shlex.join(a.image)]))
  if any(i['Architecture']!='arm64' for i in info):raise SystemExit('Non-ARM64 image selected')
  command='docker image save '+shlex.join(a.image)
  provenance=[dict(id=i['Id'],tags=i['RepoTags'],size_bytes=i['Size'],architecture=i['Architecture'],repo_digests=i.get('RepoDigests',[])) for i in info]
 elif a.kind=='models':
  command='tar -chf - -C /home/whtc/models Systran--faster-whisper-small Qwen--Qwen3-TTS-12Hz-1.7B-CustomVoice -C /home/whtc/workspace/projects/road-agent-rag/models bge-m3-ft-final bge-reranker-v2-m3-ft-final'
  provenance={'source':'four explicit ASR/TTS/BGE directories; no NVFP4 or unused model weights'}
 else:
  command='tar -chf - -C /home/whtc/workspace/projects/road-agent-rag/models bge-m3-ft-final bge-reranker-v2-m3-ft-final'
  provenance={'source':'two private fine-tuned BGE directories; preserve trained weights, not public substitutes'}
 with part.open('xb') as f:subprocess.run(base+[command],stdout=f,check=True)
 with tarfile.open(part,'r:') as t:
  for item in t:
   if item.name.startswith('/') or '..' in Path(item.name).parts or item.issym() or item.islnk():raise RuntimeError('Unsafe/non-dereferenced archive member')
 h=hashlib.sha256()
 with part.open('rb') as f:
  for b in iter(lambda:f.read(8*1024**2),b''):h.update(b)
 part.rename(dest)
 dest.with_name(dest.name+'.manifest.json').write_text(json.dumps(dict(file=dest.name,bytes=dest.stat().st_size,sha256=h.hexdigest(),provenance=provenance),indent=2))
 print('Exported',dest.stat().st_size,'bytes; SHA256',h.hexdigest())
if __name__=='__main__':main()
