#!/usr/bin/env python3
"""Restore only RAG keys/config; retain freshly generated local DB credentials."""
import argparse,hashlib,json,os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--backup-dir',required=True);p.add_argument('--env-file',required=True);a=p.parse_args()
b=Path(a.backup_dir).resolve();envfile=Path(a.env_file).resolve();root=envfile.parent
m=json.loads((b/'SHA256.json').read_text())
for name,h in m.items():
 if Path(name).name!=name:raise SystemExit('Unsafe backup filename')
 if hashlib.sha256((b/name).read_bytes()).hexdigest()!=h:raise SystemExit('Bad backup checksum: '+name)
meta=json.loads((b/'metadata.json').read_text())
if not str(meta['source_mysql_version']).startswith('8.0.45'):raise SystemExit('Source MySQL version changed; review target compatibility')
if meta['charset'].split()!=['utf8mb4','utf8mb4_general_ci']:raise SystemExit('Source collation changed; review target configuration')
old=json.loads((b/'weknora-secrets.json').read_text());runtime=json.loads((b/'roadagent-rag.json').read_text())
for k in ['SYSTEM_AES_KEY','TENANT_AES_KEY','JWT_SECRET','BGE_API_KEY']:
 if not old.get(k):raise SystemExit('Missing key '+k)
for k,v in old.items():
 if '\n' in v or '\r' in v:raise SystemExit('Multiline secret requires manual env-file escaping review')
# Compose env_file raw format is deliberately not assumed across Compose versions.
# Single-quoted dotenv values avoid $ interpolation; reject embedded quote until reviewed.
if any("'" in v for v in old.values()):raise SystemExit('Quoted secret requires manual dotenv escaping review')
text='\n'.join(k+"='"+v+"'" for k,v in old.items())+'\n'
(root/'weknora.env').write_text(text);os.chmod(root/'weknora.env',0o600)
(root/'weknora-config.yaml').write_bytes((b/'weknora-config.yaml').read_bytes())
lines=envfile.read_text().splitlines();replace=dict(runtime,BGE_API_KEY=old['BGE_API_KEY'])
for k,v in replace.items():
 if '\n' in v or "'" in v:raise SystemExit('Review secret quoting before restore')
envfile.write_text('\n'.join(k+"='"+replace[k]+"'" if '=' in line and (k:=line.split('=',1)[0]) in replace else line for line in lines)+'\n');os.chmod(envfile,0o600)
print('Restored RAG encryption/auth configuration; source MySQL credentials were not imported.')
