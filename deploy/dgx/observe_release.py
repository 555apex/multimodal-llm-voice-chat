#!/usr/bin/env python3
"""Observe the opened release for a full 15 minutes, retaining local evidence."""
import json,time,urllib.request,urllib.error
from release_ops import RELEASE,CONTAINERS,run,write_json

opened=json.loads((RELEASE/'opened.json').read_text())['time']
for attempt in range(45):
    initial=json.loads(run(['docker','inspect',*CONTAINERS,'model-serving-qwen'],text=True))
    assert all(c['State']['Running'] for c in initial), 'A released container stopped'
    if all(c['State'].get('Health',{}).get('Status') in (None,'healthy') for c in initial): break
    time.sleep(2)
else:
    raise RuntimeError('Containers did not become healthy after opening ingress')
observation_started=time.time()
samples=[]
def status(url):
    try:
        with urllib.request.urlopen(url,timeout=15) as response: return response.status
    except urllib.error.HTTPError as error: return error.code
    except Exception as error: return type(error).__name__

while True:
    containers=json.loads(run(['docker','inspect',*CONTAINERS,'model-serving-qwen'],text=True))
    states={c['Name'].lstrip('/'):{'running':c['State']['Running'],
       'health':c['State'].get('Health',{}).get('Status'),'restarts':c['RestartCount']} for c in containers}
    sample={'time':time.time(),'states':states,
       'private':status('http://127.0.0.1:18080/'),
       'public_health':status('https://spark-8a8d.taile1b178.ts.net/healthz'),
       'public_auth':status('https://spark-8a8d.taile1b178.ts.net/')}
    sample['passed']=(sample['private']==200 and sample['public_health']==200 and sample['public_auth']==401
       and all(s['running'] and s['health'] in (None,'healthy') and s['restarts']==0 for s in states.values()))
    samples.append(sample)
    write_json(RELEASE/'monitor-progress.json',samples)
    print('elapsed',round(time.time()-observation_started),'seconds','passed',sample['passed'],flush=True)
    if not sample['passed']: raise RuntimeError('Release observation failed; inspect monitor-progress.json')
    if time.time()-observation_started>=900: break
    time.sleep(min(60,max(1,900-(time.time()-observation_started))))

errors={}
for name in CONTAINERS[:2]+[CONTAINERS[4]]:
    p=__import__('subprocess').run(['docker','logs','--since',str(int(opened)),name],capture_output=True,text=True)
    text=p.stdout+p.stderr
    (RELEASE/'logs'/('observation-'+name+'.log')).write_text(text)
    errors[name]=[line for line in text.splitlines() if ' ERROR ' in line or 'Traceback (most recent call last)' in line]
assert not any(errors.values()),'Application error logs require review'
write_json(RELEASE/'monitor.json',{'passed':True,'opened':opened,'observation_started':observation_started,'ended':time.time(),'samples':samples,'errors':errors})
print('15-minute observation passed',flush=True)
