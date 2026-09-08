#!/usr/bin/env python3
"""Read-only business and local speech checks against the isolated release backend."""
import json, os, pathlib, subprocess, time, urllib.request, uuid
from release_ops import RELEASE, run

container=os.environ.get('ACCEPT_LIVE_CONTAINER')
if container:
    assert container in ['road-agent-dgx-backend','road-agent-dgx-public-backend']
    base='http://127.0.0.1:8080'
else:
    port=run(['docker','port','road-agent-merge-qa','8080'],text=True).strip().split(':')[-1]
    base='http://127.0.0.1:'+port
results=[]
def transport(path, body=None, content_type='application/json', timeout=240):
    if container:
        args=['docker','exec','-i',container,'curl','--fail-with-body','--silent','--show-error',
              '--max-time',str(timeout),'-H','Content-Type: '+content_type]
        if body is not None: args+=['--data-binary','@-']
        return run(args+[base+path],input=body)
    req=urllib.request.Request(base+path,data=body,headers={'Content-Type':content_type})
    with urllib.request.urlopen(req,timeout=timeout) as response: return response.read()
def request(path, data=None, timeout=240):
    return transport(path,None if data is None else json.dumps(data).encode(),timeout=timeout)
for path in ['/api/v1/speech/capabilities','/api/v1/facility-alerts',
             '/api/v1/facility-alerts/health-report','/api/v1/facility-alerts/focus',
             '/api/v1/emergency-workflows/inbox?stage=LEVEL_1']:
    data=json.loads(request(path)); assert data['code']=='OK',data
    results.append({'endpoint':path,'passed':True})
for question in ['福建省目前整体交通态势如何？','福建省哪些路线接近通行瓶颈？',
                 '福州、厦门、泉州的跨区域交通联系如何？','福州的出行主要联系哪些城市？',
                 '福州、厦门、泉州的城市联系矩阵如何？','福州市24小时分车型出行规律如何？']:
    start=time.time()
    response=request('/api/v1/conversations/'+str(uuid.uuid4())+'/messages/stream',{'message':question}).decode()
    events=[line[6:].strip() for line in response.splitlines() if line.startswith('event:')]
    assert 'run.completed' in events and 'run.failed' not in events,(question,response)
    assert 'result.traffic' in events,(question,response)
    results.append({'question':question,'events':events,'seconds':round(time.time()-start,2),'passed':True})
    print(question,results[-1]['seconds'],flush=True)
audio=request('/api/v1/speech/syntheses',{'text':'福建省目前整体交通态势如何？'})
assert len(audio)>1000
(RELEASE/'qa-speech.mp3').write_bytes(audio)
wav=run(['docker','exec','-i','road-agent-dgx-speech','ffmpeg','-hide_banner','-loglevel','error',
         '-i','pipe:0','-ar','48000','-ac','1','-f','wav','pipe:1'],input=audio)
(RELEASE/'qa-microphone.wav').write_bytes(wav)
boundary='road-agent-acceptance'
body=(f'--{boundary}\r\nContent-Disposition: form-data; name="durationMs"\r\n\r\n5000\r\n'
      f'--{boundary}\r\nContent-Disposition: form-data; name="audio"; filename="test.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode()+wav+f'\r\n--{boundary}--\r\n'.encode()
data=json.loads(transport('/api/v1/speech/transcriptions',body,'multipart/form-data; boundary='+boundary,120))
assert data['code']=='OK' and '交通' in data['data']['text'],data
results.append({'speech_roundtrip':data['data']['text'],'audio_bytes':len(audio),'passed':True})
(RELEASE/('live-readonly-'+container+'.json' if container else 'readonly-acceptance.json')).write_text(json.dumps(results,ensure_ascii=False,indent=2))
print('Read-only business and local speech checks passed',flush=True)
