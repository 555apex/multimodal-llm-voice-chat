#!/usr/bin/env python3
"""Target-native image pulls/builds; dry run unless --execute is explicit."""
import argparse,json,platform,subprocess
from pathlib import Path
from local_guard import require_local

def main():
 p=argparse.ArgumentParser();p.add_argument('--source-root',required=True);p.add_argument('--env-file',required=True);p.add_argument('--execute',action='store_true');a=p.parse_args()
 source=Path(a.source_root).resolve();envfile=Path(a.env_file).resolve()
 env=dict(line.split('=',1) for line in envfile.read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
 if not (source/'pom.xml').is_file():raise SystemExit('Source root must contain pom.xml')
 arch={'aarch64':'arm64','x86_64':'amd64'}.get(platform.machine())
 if not arch:raise SystemExit('Use a native Linux aarch64/x86_64 target')
 required=['MYSQL_IMAGE','POSTGRES_IMAGE','REDIS_IMAGE','WEKNORA_APP_IMAGE','WEKNORA_UI_IMAGE','WEKNORA_DOCREADER_IMAGE','LLM_IMAGE','TTS_IMAGE','BACKEND_IMAGE','FRONTEND_IMAGE','RAG_CPU_IMAGE','SPEECH_CPU_IMAGE']
 for key in required:
  if not env.get(key) or 'REPLACE' in env[key]:raise SystemExit('Select a reviewed image reference: '+key)
 commands=[['docker','pull','--platform','linux/'+arch,env[key]] for key in required[:8]]
 for key,dockerfile,context in [('BACKEND_IMAGE',source/'deploy/dgx/Dockerfile.backend',source),('FRONTEND_IMAGE',source/'deploy/dgx/Dockerfile.frontend',source),('SPEECH_CPU_IMAGE',Path(__file__).resolve().parents[1]/'configs/Dockerfile.speech-cpu',source),('RAG_CPU_IMAGE',Path(__file__).resolve().parents[1]/'configs/Dockerfile.bge-cpu',Path(__file__).resolve().parents[1]/'configs')]:
  commands.append(['docker','build','--platform','linux/'+arch,'-t',env[key],'-f',str(dockerfile),str(context)])
 print(json.dumps({'dry_run':not a.execute,'commands':commands},indent=2))
 if not a.execute:return
 require_local(envfile)
 for command in commands:subprocess.run(command,check=True)
 locks={}
 for key in required:
  info=json.loads(subprocess.check_output(['docker','image','inspect',env[key]]))[0]
  if info['Architecture']!=arch:raise SystemExit('Wrong image architecture: '+key)
  locks[key]={'ref':env[key],'id':info['Id'],'architecture':arch,'repo_digests':info.get('RepoDigests',[])}
 (envfile.parent/'online-image-receipt.json').write_text(json.dumps(locks,indent=2),encoding='utf8')
 print('Images available. Runtime/model compatibility is not accepted by this build step.')
if __name__=='__main__':main()
