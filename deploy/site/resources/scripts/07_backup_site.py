#!/usr/bin/env python3
"""Target-only encrypted recovery point. Stop writers before calling this tool."""
import argparse,datetime,hashlib,json,os,shutil,subprocess,tarfile
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);p.add_argument('--passphrase-file',required=True);p.add_argument('--out-directory',required=True);a=p.parse_args()
 if Path('/home/whtc/workspace/projects/road-agent-dgx').exists():raise SystemExit('Target-only script')
 os.umask(0o077);env=Path(a.env_file).resolve();site=env.parent;package=Path(__file__).resolve().parents[2]
 from local_guard import require_local
 require_local(env)
 values=dict(s.split('=',1) for s in env.read_text().splitlines() if '=' in s and not s.startswith('#'))
 data=Path(values['DATA_ROOT']).resolve();out=Path(a.out_directory).resolve();out.mkdir(parents=True,exist_ok=True)
 phrase=Path(a.passphrase_file)
 if not phrase.is_file() or phrase.stat().st_mode&0o077:raise SystemExit('Passphrase file must be mode 600')
 stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');work=out/('site-recovery-'+stamp);work.mkdir(mode=0o700)
 dc=['docker','compose','--project-name','roadagent-fz-pilot','--env-file',str(env),'-f',str(package/'resources/configs/compose.site.yaml')]
 running=set(subprocess.check_output(dc+['ps','--services','--status','running'],text=True).split())
 if running-{'mysql','postgres','redis'}:raise SystemExit('Stop all application/model writers; keep mysql/postgres/redis running')
 def query(sql):return subprocess.check_output(dc+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -e "$1"','sh',sql],text=True).strip()
 with (work/'mysql.sql').open('wb') as f:subprocess.run(dc+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF road_agent'],stdout=f,check=True)
 with (work/'weknora.dump').open('wb') as f:subprocess.run(dc+['exec','-T','postgres','pg_dump','-U','weknora','-d','weknora','-Fc','--no-owner','--no-acl'],stdout=f,check=True)
 with tarfile.open(work/'knowledge-files.tar','w') as t:
  for f in (data/'files').iterdir():t.add(f,arcname=f.name)
 for name in ['site.env','weknora.env','weknora-config.yaml','target-profile.json','compose.npu.yaml']:
  if (site/name).is_file():shutil.copyfile(site/name,work/name)
 meta={'source_mysql_version':query('SELECT VERSION()'),'charset':query('SELECT @@character_set_database,@@collation_database'),'table_names':query('SHOW TABLES FROM road_agent').splitlines()}
 (work/'metadata.json').write_text(json.dumps(meta,indent=2))
 records={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in work.iterdir() if f.is_file()}
 (work/'SHA256.json').write_text(json.dumps(records,indent=2))
 dest=out/(work.name+'.tar.gpg');partial=dest.with_name(dest.name+'.partial')
 proc=subprocess.Popen(['gpg','--batch','--pinentry-mode','loopback','--passphrase-file',str(phrase),'--symmetric','--cipher-algo','AES256','--output',str(partial)],stdin=subprocess.PIPE)
 try:
  with tarfile.open(fileobj=proc.stdin,mode='w|') as t:
   for f in work.iterdir():t.add(f,arcname=f.name,recursive=False)
 finally:proc.stdin.close()
 if proc.wait():raise SystemExit('Encryption failed; protected target staging retained')
 partial.rename(dest);assert work.parent==out and work.name.startswith('site-recovery-');shutil.rmtree(work)
 print('Encrypted recovery point:',dest,'bytes',dest.stat().st_size)
 print('sha256',hashlib.sha256(dest.read_bytes()).hexdigest())
if __name__=='__main__':main()
