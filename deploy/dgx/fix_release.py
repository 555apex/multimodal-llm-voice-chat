#!/usr/bin/env python3
"""September 9 repair release: no production schema initialization or data reset."""
import argparse, hashlib, json, pathlib, shutil, subprocess, time
import urllib.request
import yaml

ROOT = pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx')
RELEASE = ROOT / '.releases/fix-20260909'
MODEL_ROOT = pathlib.Path('/home/whtc/workspace/projects/model-serving')
EXPECTED_MODEL = 'qwen3.6-35b-a3b-nvfp4'

def run(*args):
    p = subprocess.run(args, text=True, capture_output=True, check=True)
    return p.stdout

def read_json(name): return json.loads((RELEASE / name).read_text())
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def values(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.startswith('#'))
def write_env(path, data):
    path.write_text('\n'.join(k+'='+v for k,v in data.items())+'\n'); path.chmod(0o600)
def journal(stage):
    with (RELEASE/'release-journal.jsonl').open('a') as out:
        out.write(json.dumps({'time':time.time(),'stage':stage})+'\n')

def gate():
    perf = read_json('qa-emergency-benchmark.json')
    assert perf['singleP95'] <= 30 and perf['dualP95'] <= 45, 'Emergency performance gate failed'
    assert len([r for r in perf['results'] if r['label'].startswith('single')]) >= 20
    assert len([r for r in perf['results'] if r['label'].startswith('dual')]) >= 20
    assert read_json('qa-business.json')['passed'], 'Business acceptance missing'
    assert read_json('qa-workflows.json')['passed'], 'Workflow acceptance missing'
    image=json.loads(run('docker','image','inspect','road-agent-dgx-backend:fix-20260909'))[0]['Id']
    proof=read_json('emergency-image-equivalence.json')
    assert proof['passed'] and proof['releaseImage']==image and proof['benchmarkImage']==perf['backendImage']
    assert read_json('qa-business.json')['backendImage']==image
    assert (RELEASE/'logs/backend-build.status').read_text().strip() == '0'
    assert (RELEASE/'logs/frontend-build.status').read_text().strip() == '0'
    assert read_json('baseline/maintenance-db.json')['tables'], 'Maintenance backup missing'
    assert time.time()-read_json('baseline/maintenance-db.json')['time'] < 3600, 'Refresh final backup'

def model_compose(*args):
    return run('docker','compose','--env-file',str(MODEL_ROOT/'.env'),'--project-directory',str(MODEL_ROOT),
               '-f',str(MODEL_ROOT/'docker/compose.yaml'),'--profile','daily',*args)

def road_compose(*args):
    return run('docker','compose','--env-file',str(ROOT/'deploy/dgx/.env'),
               '--project-directory',str(ROOT),'-f',str(ROOT/'deploy/dgx/compose.yaml'),
               '-f',str(ROOT/'deploy/dgx/compose.public.yaml'),'--profile','public',*args)

def deploy_model():
    gate()
    assert not (RELEASE/'model-deployed.json').exists(), 'Inspect existing deployment before retry'
    for source, name in [(MODEL_ROOT/'configs/models.yaml','model-catalog.yaml'),
                         (MODEL_ROOT/'docker/compose.yaml','model-compose.yaml'),
                         (MODEL_ROOT/'.env','model-runtime.env')]:
        target=RELEASE/'baseline'/name
        if not target.exists(): shutil.copy2(source,target);target.chmod(0o600)
    candidate=json.loads(run('docker','inspect','road-agent-fix-qwen'))[0]
    command=candidate['Config']['Cmd']
    assert command[:1] == ['serve']
    command=command[1:]
    assert command[command.index('--served-model-name')+1] == EXPECTED_MODEL
    assert json.loads(command[command.index('--default-chat-template-kwargs')+1])['enable_thinking'] is False
    # Keep compiled kernels across the final container recreation.
    cache=RELEASE/'qwen-cache'
    if not cache.exists(): run('docker','cp','road-agent-fix-qwen:/root/.cache/vllm',str(cache))
    config=yaml.safe_load((MODEL_ROOT/'docker/compose.yaml').read_text())
    qwen=dict(config['services']['qwen'])
    qwen.update(command=command,image=candidate['Image'],pull_policy='never',mem_limit='72g',memswap_limit='72g')
    qwen['volumes']=[m['Source']+':'+m['Destination']+':ro' for m in candidate['Mounts'] if m['Type']=='bind']
    qwen['volumes'].append(str(cache)+':/root/.cache/vllm')
    config['services']['qwen']=qwen
    (MODEL_ROOT/'docker/compose.yaml').write_text(yaml.safe_dump(config,sort_keys=False,allow_unicode=True))
    catalog=yaml.safe_load((MODEL_ROOT/'configs/models.yaml').read_text())
    catalog['models'][EXPECTED_MODEL]['role']='daily'
    catalog['models']['qwen3.8-27b-fp8']['role']='rollback'
    model_args=catalog['models'][EXPECTED_MODEL]['vllm_args']
    if '--default-chat-template-kwargs' not in model_args:
        model_args+=['--default-chat-template-kwargs','{"enable_thinking":false}']
    (MODEL_ROOT/'configs/models.yaml').write_text(yaml.safe_dump(catalog,sort_keys=False,allow_unicode=True))
    env=values(MODEL_ROOT/'.env')
    env.update(QWEN_SERVED_MODEL_NAME=EXPECTED_MODEL,QWEN_MODEL_DIR='/home/whtc/models/NVIDIA--Qwen3.6-35B-A3B--NVFP4')
    write_env(MODEL_ROOT/'.env',env)
    run('docker','stop','-t','40','road-agent-fix-qwen')
    assert not json.loads(run('docker','inspect','model-serving-qwen'))[0]['State']['Running']
    journal('model-recreation-started')
    model_compose('up','-d','--no-deps','--no-build','--force-recreate','qwen')
    (RELEASE/'model-deployed.json').write_text(json.dumps({'time':time.time(),'model':EXPECTED_MODEL}))

def deploy_business():
    gate()
    model=json.loads(run('docker','inspect','model-serving-qwen'))[0]
    assert EXPECTED_MODEL in model['Config']['Cmd']
    for relative, digest in read_json('deployment-files.json'):
        rel=pathlib.PurePosixPath(relative)
        assert not rel.is_absolute() and '..' not in rel.parts
        assert not any(p in {'.git','.releases','node_modules','target','dist'} for p in rel.parts)
        assert rel.name != '.env'
        source=RELEASE/'source'/relative
        assert sha(source)==digest, 'Source changed after preparation: '+relative
        destination=ROOT/relative;destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,destination)
    env=values(ROOT/'deploy/dgx/.env')
    env.update(ROADAGENT_MODEL_NAME=EXPECTED_MODEL,ROADAGENT_MODEL_ENABLE_THINKING='false',
               ROADAGENT_MODEL_TIMEOUT_SECONDS='180',ROADAGENT_BACKEND_IMAGE='road-agent-dgx-backend:fix-20260909',
               ROADAGENT_FRONTEND_IMAGE='road-agent-dgx-frontend:fix-20260909')
    # Retain the original voice image: the accelerated candidate did not pass the two-user gate.
    write_env(ROOT/'deploy/dgx/.env',env)
    journal('business-recreation-started')
    road_compose('up','-d','--no-deps','--no-build','--force-recreate','backend','public-backend')

def open_ingress():
    assert read_json('live-acceptance.json')['passed']
    assert sha(pathlib.Path('/home/whtc/.config/road-agent/public.htpasswd'))==sha(RELEASE/'baseline/public.htpasswd')
    road_compose('up','-d','--no-deps','--no-build','--force-recreate','frontend','public-frontend')
    journal('ingress-opened')
    (RELEASE/'opened.json').write_text(json.dumps({'time':time.time()}))

def rollback():
    # Restore application artifacts and configuration only; preserve valid new business data.
    for name in ['road-agent-dgx-public-frontend','road-agent-dgx-frontend','road-agent-dgx-public-backend','road-agent-dgx-backend','model-serving-qwen']:
        run('docker','stop','-t','40',name)
    for name,target in [('model-catalog.yaml','configs/models.yaml'),('model-compose.yaml','docker/compose.yaml'),('model-runtime.env','.env')]:
        shutil.copy2(RELEASE/'baseline'/name,MODEL_ROOT/target)
    shutil.copy2(RELEASE/'baseline/runtime.env',ROOT/'deploy/dgx/.env')
    # The retained source archive contains the exact pre-repair tree. Extract to an isolated directory first.
    restore=RELEASE/'rollback-source';restore.mkdir(exist_ok=True)
    run('tar','-xzf',str(RELEASE/'baseline/source.tar.gz'),'-C',str(restore))
    for relative,_ in read_json('deployment-files.json'):
        source=restore/relative
        if source.is_file(): shutil.copy2(source,ROOT/relative)
    model_compose('up','-d','--no-deps','--no-build','--force-recreate','qwen')
    road_compose('up','-d','--no-deps','--no-build','--force-recreate','backend','public-backend')
    journal('rollback-services-started-check-health-before-opening')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['model','business','open','rollback'])
    {'model':deploy_model,'business':deploy_business,'open':open_ingress,'rollback':rollback}[parser.parse_args().action]()
