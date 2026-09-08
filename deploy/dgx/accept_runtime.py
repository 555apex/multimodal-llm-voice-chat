#!/usr/bin/env python3
"""Verify final runtime before opening either ingress."""
import hashlib,json,re,time
from release_ops import ROOT,RELEASE,CONTAINERS,AUTH,run,guard,write_json,inventory_check,query,counts,FLOWS

guard(ROOT)
baseline=json.loads((RELEASE/'final-backup/ready.json').read_text())
containers=json.loads(run(['docker','inspect',*CONTAINERS,'model-serving-qwen'],text=True))
states={c['Name'].lstrip('/'):c for c in containers}
assert states['model-serving-qwen']['Image']==baseline['images']['model-serving-qwen']
assert hashlib.sha256(AUTH.read_bytes()).hexdigest()==baseline['auth_sha256']
for name in CONTAINERS[:2]:
    c=states[name]; assert c['State']['Running'] and c['State']['Health']['Status']=='healthy',name
    env=dict(item.split('=',1) for item in c['Config']['Env'])
    assert re.search(r'/road_agent\?',env['ROADAGENT_DB_URL'])
    assert env['ROADAGENT_MODEL_ENABLE_THINKING']=='false'
    assert env['ROADAGENT_EVENT_CLASSIFICATION_ENABLED']==str(name=='road-agent-dgx-public-backend').lower()
    assert c['Image']==run(['docker','image','inspect','road-agent-dgx-backend:merge-20260908','--format','{{.Id}}'],text=True).strip()
speech=states['road-agent-dgx-speech']
assert speech['State']['Health']['Status']=='healthy'
assert speech['Image']==run(['docker','image','inspect','road-agent-dgx-speech:merge-20260908','--format','{{.Id}}'],text=True).strip()
mounts=[m for m in speech['Mounts'] if m['Type']=='bind']
assert len(mounts)==2 and all(not m['RW'] and m['Source'].startswith('/home/whtc/models/') for m in mounts)
for container in CONTAINERS[:2]:
    checks=json.loads((RELEASE/('live-readonly-'+container+'.json')).read_text())
    assert checks and all(c['passed'] for c in checks)
inventory_check('road_agent')
assert all(line.endswith('\t0') for line in counts('road_agent',FLOWS).splitlines())
write_json(RELEASE/'live-acceptance.json',{'passed':True,'time':time.time(),
    'containers':{n:{'image':states[n]['Image'],'running':states[n]['State']['Running']} for n in states},
    'local_models':True,'single_classifier':True,'auth_preserved':True,'inventory_conserved':True})
print('Runtime, models, credentials, both backends and inventory passed')
