#!/usr/bin/env python3
"""Run ONLY on DGX. Export consistent logical DB backups and encrypt on DGX."""
import argparse,datetime,hashlib,json,os,shutil,subprocess,sys,tarfile
from pathlib import Path
ROOT=Path('/home/whtc/workspace/projects/road-agent-dgx')
def run(args,**kw):return subprocess.run(args,check=True,**kw)
def capture(args):return subprocess.check_output(args)
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb') as f:
  for b in iter(lambda:f.read(8*1024**2),b''):h.update(b)
 return h.hexdigest()
def inspect(name):return json.loads(capture(['docker','inspect',name]))[0]
def main():
 p=argparse.ArgumentParser();p.add_argument('--out-root',required=True);p.add_argument('--passphrase-file',required=True);p.add_argument('--knowledge-writes-paused',action='store_true');a=p.parse_args()
 if not a.knowledge_writes_paused:raise SystemExit('Pause KB ingestion/deletion, wait for parse jobs to finish, then pass --knowledge-writes-paused')
 if not ROOT.is_dir():raise SystemExit('Run only on source DGX')
 phrase=Path(a.passphrase_file).resolve()
 if not phrase.is_file() or phrase.stat().st_mode&0o077:raise SystemExit('Passphrase file must exist with mode 600')
 os.umask(0o077);out=Path(a.out_root).resolve()
 if len(out.parts)<4 or str(out).startswith(str(ROOT)):raise SystemExit('Use a separate backup directory, outside source tree')
 out.mkdir(parents=True,exist_ok=True);stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
 work=out/('site-data-'+stamp);work.mkdir(mode=0o700)
 sys.path.insert(0,str(ROOT/'deploy/dgx'));from release_ops import query,db_args
 engines=query('SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE=\'BASE TABLE\' GROUP BY ENGINE')
 if engines.strip()!='InnoDB':raise SystemExit('Non-InnoDB tables: coordinate a vendor-approved consistent snapshot')
 metadata={'source_mysql_version':query('SELECT VERSION()'),'charset':query('SELECT @@character_set_database,@@collation_database'),'table_names':query('SHOW TABLES').splitlines(),'captured_utc':stamp}
 # Add routines/events to the existing credential-safe client, in this export directory only.
 text=(ROOT/'deploy/dgx/mysql-client-entrypoint').read_text()
 needle='--triggers --hex-blob'
 if needle not in text:raise SystemExit('Source DB client changed; review exporter')
 client=work/'mysql-client';client.write_text(text.replace(needle,'--routines --events --triggers --hex-blob'));client.chmod(0o700)
 args=db_args();i=args.index('-v');args[i+1]=str(client)+':/db-tool:ro'
 with (work/'mysql.sql').open('wb') as f:run(args+['dump']+metadata['table_names'],stdout=f)
 client.unlink()
 app='roadagent-rag-candidate-weknora-app-1';pg='roadagent-rag-candidate-postgres-1'
 with (work/'weknora.dump').open('wb') as f:run(['docker','exec',pg,'pg_dump','-U','weknora','-d','weknora','-Fc','--no-owner','--no-acl'],stdout=f)
 with (work/'knowledge-files.tar').open('wb') as f:run(['docker','exec',app,'tar','-cf','-','-C','/data/files','.'],stdout=f)
 appinfo=inspect(app);env=dict(s.split('=',1) for s in appinfo['Config']['Env'] if '=' in s)
 keys=['JWT_SECRET','SYSTEM_AES_KEY','TENANT_AES_KEY','BGE_API_KEY','STORAGE_TYPE','RETRIEVE_DRIVER','MAX_FILE_SIZE_MB']
 safe={k:env[k] for k in keys if k in env}
 for k in ['SYSTEM_AES_KEY','TENANT_AES_KEY','JWT_SECRET','BGE_API_KEY']:
  if not safe.get(k):raise SystemExit('Missing source WeKnora key '+k+'; do not restore with new keys')
 backend=inspect('road-agent-dgx-backend');be=dict(s.split('=',1) for s in backend['Config']['Env'] if '=' in s)
 runtime={k:be[k] for k in ['ROADAGENT_RAG_API_KEY','ROADAGENT_RAG_KB_IDS'] if k in be}
 if not runtime.get('ROADAGENT_RAG_API_KEY'):raise SystemExit('Missing source RoadAgent RAG key')
 (work/'weknora-secrets.json').write_text(json.dumps(safe))
 (work/'roadagent-rag.json').write_text(json.dumps(runtime))
 mount=next(m for m in appinfo['Mounts'] if m['Destination']=='/app/config/config.yaml')
 shutil.copyfile(mount['Source'],work/'weknora-config.yaml')
 metadata['postgres_image_id']=inspect(pg)['Image']
 metadata['knowledge_files_bytes']=int(capture(['docker','exec',app,'du','-sb','/data/files']).split()[0])
 (work/'metadata.json').write_text(json.dumps(metadata,indent=2))
 records={p.name:sha(p) for p in work.iterdir() if p.is_file()}
 (work/'SHA256.json').write_text(json.dumps(records,indent=2))
 encrypted=out/(work.name+'.tar.gpg');partial=encrypted.with_name(encrypted.name+'.partial')
 # Plaintext stays in the protected DGX staging directory; only ciphertext is transportable.
 proc=subprocess.Popen(['gpg','--batch','--yes','--pinentry-mode','loopback','--passphrase-file',str(phrase),'--symmetric','--cipher-algo','AES256','--output',str(partial)],stdin=subprocess.PIPE)
 try:
  with tarfile.open(fileobj=proc.stdin,mode='w|') as t:
   for f in work.iterdir():t.add(f,arcname=f.name,recursive=False)
 finally:proc.stdin.close()
 if proc.wait()!=0:raise SystemExit('Encryption failed; protected staging retained on DGX')
 partial.rename(encrypted)
 # This is the freshly created task staging path, never the source data path.
 assert work.parent==out and work.name.startswith('site-data-')
 shutil.rmtree(work)
 print(json.dumps({'encrypted_path':str(encrypted),'bytes':encrypted.stat().st_size,'sha256':sha(encrypted)},indent=2))
 print('Resume KB edits after recording this export. Passphrase is handed over separately.')
if __name__=='__main__':main()
