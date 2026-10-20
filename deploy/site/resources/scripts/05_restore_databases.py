#!/usr/bin/env python3
import argparse,hashlib,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--backup-dir',required=True);p.add_argument('--env-file',required=True);p.add_argument('--confirm-project',required=True);a=p.parse_args()
if a.confirm_project!='roadagent-fz-pilot':raise SystemExit('Wrong confirmation project')
root=Path(__file__).resolve().parents[2];env=Path(a.env_file).resolve();b=Path(a.backup_dir).resolve()
from local_guard import require_local
require_local(env)
locks=json.loads((b/'SHA256.json').read_text())
for name,digest in locks.items():
 if Path(name).name!=name:raise SystemExit('Unsafe filename')
 if hashlib.sha256((b/name).read_bytes()).hexdigest()!=digest:raise SystemExit('Backup checksum mismatch')
meta=json.loads((b/'metadata.json').read_text())
dc=['docker','compose','--project-name','roadagent-fz-pilot','--env-file',str(env),'-f',str(root/'resources/configs/compose.site.yaml')]
def query(service,args):return subprocess.check_output(dc+['exec','-T',service]+args,text=True).strip()
sql=['sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names -e "$1"','sh']
v=query('mysql',sql+['SELECT VERSION()'])
if v!=meta['source_mysql_version']:raise SystemExit('Exact MySQL version mismatch: '+v)
n=query('mysql',sql+["SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='road_agent'"])
if n!='0':raise SystemExit('Target MySQL is not empty; refusing restore')
n=query('postgres',['psql','-U','weknora','-d','weknora','-Atc',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public'"])
if n!='0':raise SystemExit('Target PostgreSQL is not empty; refusing restore')
# Require both DB containers healthy and stop all application writers before invoking.
with (b/'mysql.sql').open('rb') as f:subprocess.run(dc+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot road_agent'],stdin=f,check=True)
with (b/'weknora.dump').open('rb') as f:subprocess.run(dc+['exec','-T','postgres','pg_restore','-U','weknora','-d','weknora','--no-owner','--no-acl','--exit-on-error'],stdin=f,check=True)
count=query('mysql',sql+["SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='road_agent'"])
if int(count)!=len(meta['table_names']):raise SystemExit('MySQL table count mismatch')
print('DB restore completed; compare business records and knowledge index metadata before app startup.')
