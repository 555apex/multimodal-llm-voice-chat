#!/usr/bin/env python3
"""Create a new isolated site configuration; never overwrite an existing one."""
import argparse,json,os,re,secrets
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('--site-root',default='/opt/roadagent-migration');p.add_argument('--data-root',default='/srv/roadagent-migration');p.add_argument('--accelerator',choices=['ascend','nvidia'],default='ascend');a=p.parse_args()
 if Path('/home/whtc/workspace/projects/road-agent-dgx').exists():raise SystemExit('Refuse site initialization on source DGX')
 root=Path(a.site_root).resolve();data=Path(a.data_root).resolve()
 if root==Path('/') or data==Path('/') or len(root.parts)<3 or len(data.parts)<3:raise SystemExit('Use dedicated absolute directories')
 if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(root)+str(data)):raise SystemExit('Use ASCII directory names without spaces')
 root.mkdir(parents=True,exist_ok=True);os.chmod(root,0o700)
 env=root/'site.env'
 if env.exists():raise SystemExit('site.env exists; refusing overwrite')
 cfg=Path(__file__).resolve().parents[1]/'configs/site.env.example'
 s=cfg.read_text().replace('__SITE_ROOT__',str(root)).replace('__DATA_ROOT__',str(data))
 for k in ['MYSQL_ROOT_PASSWORD','MYSQL_PASSWORD','DB_PASSWORD','REDIS_PASSWORD','BGE_API_KEY']:
  s=s.replace(k+'=__GENERATE__',k+'='+secrets.token_hex(24))
 fd=os.open(env,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'w') as f:f.write(s)
 for n in ['mysql','postgres','files','docreader','models','evidence','backups']:(data/n).mkdir(parents=True,exist_ok=True)
 template=Path(__file__).resolve().parents[1]/'configs/target-profile.json.example'
 profile=json.loads(template.read_text());profile['accelerator_type']=a.accelerator
 profile['architecture']=__import__('platform').machine()
 if a.accelerator=='nvidia':profile.update(gpu_count=0,gpu_model='',accelerator_compose_sha256='')
 (root/'target-profile.json').write_text(json.dumps(profile,indent=2),encoding='utf8')
 (root/'weknora.env').write_text('# Replace by restore_config.py before rag-up\n');os.chmod(root/'weknora.env',0o600)
 (root/'site-only.marker').write_text(__import__('socket').gethostname())
 print('Created',env,'; fill image locks, restore keys and validate profile before startup.')
if __name__=='__main__':main()
