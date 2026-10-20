#!/usr/bin/env python3
import json,os,socket,subprocess,sys
from pathlib import Path
def require_local(envfile):
 if Path('/home/whtc/workspace/projects/road-agent-dgx').exists():raise SystemExit('Target-only operation; source DGX detected')
 marker=Path(envfile).resolve().parent/'site-only.marker'
 if not marker.is_file() or marker.read_text().strip()!=socket.gethostname():raise SystemExit('Initialize target site locally; marker missing or host mismatch')
 if os.getenv('DOCKER_HOST') or os.getenv('DOCKER_CONTEXT'):raise SystemExit('Unset remote Docker overrides')
 context=subprocess.check_output(['docker','context','show'],text=True).strip()
 info=json.loads(subprocess.check_output(['docker','context','inspect',context]))[0]
 if not info['Endpoints']['docker']['Host'].startswith('unix://'):raise SystemExit('Only local Unix Docker socket is allowed')
if __name__=='__main__':require_local(sys.argv[1])
