#!/usr/bin/env python3
"""Verify final runtime before opening either ingress."""
import hashlib,json,time
from urllib.parse import urlsplit
from release_ops import ROOT,RELEASE,CONTAINERS,AUTH,ENV,run,guard,write_json,inventory_check,query,counts,FLOWS,values

guard(ROOT)
baseline=json.loads((RELEASE/'final-backup/ready.json').read_text())
containers=json.loads(run(['docker','inspect',*CONTAINERS,'model-serving-qwen'],text=True))
states={c['Name'].lstrip('/'):c for c in containers}
expected_db_url=values(ENV)['ROADAGENT_DB_URL']
configured_schema=urlsplit(expected_db_url.removeprefix('jdbc:')).path.lstrip('/')
assert configured_schema and configured_schema.replace('_','').isalnum()
assert states['model-serving-qwen']['Image']==baseline['images']['model-serving-qwen']
assert hashlib.sha256(AUTH.read_bytes()).hexdigest()==baseline['auth_sha256']
for name in CONTAINERS[:2]:
    c=states[name]; assert c['State']['Running'] and c['State']['Health']['Status']=='healthy',name
    env=dict(item.split('=',1) for item in c['Config']['Env'])
    assert env['ROADAGENT_DB_URL']==expected_db_url
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
inventory_check(configured_schema)
assert all(line.endswith('\t0') for line in counts(configured_schema,FLOWS).splitlines())
write_json(RELEASE/'live-acceptance.json',{'passed':True,'time':time.time(),
    'containers':{n:{'image':states[n]['Image'],'running':states[n]['State']['Running']} for n in states},
    'local_models':True,'single_classifier':True,'auth_preserved':True,'inventory_conserved':True})
print('Runtime, models, credentials, both backends and inventory passed')
