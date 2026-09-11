#!/usr/bin/env python3
"""Publish or roll back the voice repair without changing models, ingress or data."""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import time
import urllib.request

ROOT = pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx')
RELEASE = ROOT / '.releases/speech-20260910'
SERVICES = ['speech-service', 'backend', 'public-backend', 'frontend', 'public-frontend']
CONTAINERS = ['road-agent-dgx-speech', 'road-agent-dgx-backend', 'road-agent-dgx-public-backend', 'road-agent-dgx-frontend', 'road-agent-dgx-public-frontend']

def run(*args):
    return subprocess.check_output(args, text=True)

def compose(*args):
    return run('docker', 'compose', '--env-file', str(ROOT/'deploy/dgx/.env'), '--project-directory', str(ROOT),
               '-f', str(ROOT/'deploy/dgx/compose.yaml'), '-f', str(ROOT/'deploy/dgx/compose.public.yaml'), '--profile', 'public', *args)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def manifest():
    rows = json.loads((RELEASE/'changed-files.json').read_text())
    for name, digest in rows:
        target = (ROOT/name).resolve()
        assert target.is_relative_to(ROOT) and name != 'deploy/dgx/.env'
        assert sha(RELEASE/'source'/name) == digest, 'Source differs from verified manifest'
    return rows

def health():
    for name in CONTAINERS[:3]:
        for _ in range(100):
            try:
                if name == CONTAINERS[0]:
                    code = "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8091/health/ready').read().decode())"
                    caps = json.loads(run('docker','exec',name,'python','-c',code))
                else:
                    caps = json.loads(run('docker','exec',name,'curl','-fsS','http://127.0.0.1:8080/api/v1/speech/capabilities'))['data']
                if caps['asrAvailable'] and caps['ttsAvailable']:break
            except (subprocess.CalledProcessError, ValueError, KeyError):pass
            time.sleep(2)
        else:raise RuntimeError('Voice readiness failed: '+name)

def deploy():
    gate = json.loads((RELEASE/'qa-gate.json').read_text())
    assert gate['inputPassed'] and gate['cancellationPassed'] and gate['browserPassed']
    rows = manifest()
    assert sha(ROOT/'deploy/dgx/.env') == sha(RELEASE/'baseline/runtime.env'), 'Runtime configuration changed since backup'
    baseline = {c['Name'][1:]:c for c in json.loads((RELEASE/'baseline/containers.json').read_text())}
    for c in json.loads(run('docker','inspect',*CONTAINERS,'model-serving-qwen')):
        assert c['Image']==baseline[c['Name'][1:]]['Image'], 'Deployment baseline changed'
    for kind in ['backend','frontend','speech']:
        assert (RELEASE/'logs'/(kind+'-build.status')).read_text().strip()=='0'
        assert json.loads(run('docker','image','inspect','road-agent-dgx-'+kind+':speech-20260910'))[0]['Id']==gate['images'][kind]
    assert sha(pathlib.Path('/home/whtc/.config/road-agent/public.htpasswd'))==sha(RELEASE/'baseline/public.htpasswd')
    # No database operation and no model-serving container recreation.
    compose('stop','frontend','public-frontend','backend','public-backend','speech-service')
    for name,_ in rows:
        target=ROOT/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(RELEASE/'source'/name,target)
    envpath=ROOT/'deploy/dgx/.env'
    env=dict(line.split('=',1) for line in envpath.read_text().splitlines() if '=' in line and not line.startswith('#'))
    for kind in ['BACKEND','FRONTEND','SPEECH']:env['ROADAGENT_'+kind+'_IMAGE']='road-agent-dgx-'+kind.lower()+':speech-20260910'
    envpath.write_text('\n'.join(key+'='+value for key,value in env.items())+'\n');envpath.chmod(0o600)
    compose('up','-d','--no-deps','--no-build','--force-recreate','speech-service','backend','public-backend')
    health()
    compose('up','-d','--no-deps','--no-build','--force-recreate','frontend','public-frontend')
    assert json.loads(run('docker','inspect','model-serving-qwen'))[0]['Image']==baseline['model-serving-qwen']['Image']
    (RELEASE/'deployed.json').write_text(json.dumps({'time':time.time(),'images':gate['images'],'modelChanged':False,'databaseChanged':False,'observationMinutes':0}))
    print('Voice repair deployed; perform one frontend functional check',flush=True)

def rollback():
    rows=manifest()
    compose('stop',*SERVICES)
    restore=RELEASE/'rollback-source';restore.mkdir(exist_ok=True)
    run('tar','-xzf',str(RELEASE/'baseline/source.tar.gz'),'-C',str(restore))
    for name,_ in rows:
        source=restore/name;target=ROOT/name
        if source.is_file():shutil.copy2(source,target)
        elif target.is_file():target.unlink()
    shutil.copy2(RELEASE/'baseline/runtime.env',ROOT/'deploy/dgx/.env')
    # The baseline tags are immutable for this release even if older daily tags change.
    envpath=ROOT/'deploy/dgx/.env';env=dict(line.split('=',1) for line in envpath.read_text().splitlines() if '=' in line and not line.startswith('#'))
    for kind in ['BACKEND','FRONTEND','SPEECH']:env['ROADAGENT_'+kind+'_IMAGE']='road-agent-dgx-'+kind.lower()+':before-speech-20260910'
    envpath.write_text('\n'.join(key+'='+value for key,value in env.items())+'\n');envpath.chmod(0o600)
    compose('up','-d','--no-deps','--no-build','--force-recreate','speech-service','backend','public-backend');health()
    compose('up','-d','--no-deps','--no-build','--force-recreate','frontend','public-frontend')
    print('Voice rollback complete; business data preserved')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['deploy','rollback'])
    {'deploy':deploy,'rollback':rollback}[parser.parse_args().action]()
