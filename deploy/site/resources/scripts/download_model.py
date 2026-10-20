#!/usr/bin/env python3
"""Download an explicitly reviewed model revision on the target, not the author PC."""
import argparse,hashlib,json,re,sys
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--repo',required=True);p.add_argument('--revision',required=True);p.add_argument('--directory',required=True);a=p.parse_args()
 if not re.fullmatch(r'[a-fA-F0-9]{40}',a.revision):raise SystemExit('Use the reviewed full 40-character model commit, not main/latest')
 root=Path(a.directory).resolve()
 if not root.is_relative_to(Path('/srv/roadagent-migration/models')) or root==Path('/srv/roadagent-migration/models'):raise SystemExit('Use a dedicated model subdirectory below /srv/roadagent-migration/models')
 marker=root/'.roadagent-download.json'
 identity={'repo':a.repo,'revision':a.revision}
 if root.exists() and any(root.iterdir()) and not marker.is_file():raise SystemExit('Nonempty unmarked directory; select a new model directory')
 if marker.is_file() and json.loads(marker.read_text())!=identity:raise SystemExit('Existing download is for another revision')
 try:from huggingface_hub import snapshot_download
 except ImportError:raise SystemExit('Install huggingface_hub in the separate download venv first')
 root.mkdir(parents=True,exist_ok=True);marker.write_text(json.dumps(identity),encoding='utf8')
 snapshot_download(repo_id=a.repo,revision=a.revision,local_dir=str(root))
 files={}
 for f in sorted(root.rglob('*')):
  rel=f.relative_to(root)
  if not f.is_file() or '.cache' in rel.parts or f.name in {'.roadagent-download.json','SHA256SUMS','download-receipt.json'}:continue
  h=hashlib.sha256()
  with f.open('rb') as stream:
   for block in iter(lambda:stream.read(8*1024**2),b''):h.update(block)
  files[rel.as_posix()]=h.hexdigest()
 if not files:raise SystemExit('No model files received')
 (root/'SHA256SUMS').write_text(''.join(h+'  '+n+'\n' for n,h in files.items()),encoding='utf8')
 (root/'download-receipt.json').write_text(json.dumps({**identity,'files':len(files),'bytes':sum((root/n).stat().st_size for n in files),'model_loaded':False},indent=2),encoding='utf8')
 print('Downloaded and locally hashed',len(files),'files. Validate publisher, license, model loading and APIs separately.')
if __name__=='__main__':main()
