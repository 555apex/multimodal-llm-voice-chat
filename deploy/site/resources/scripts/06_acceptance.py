#!/usr/bin/env python3
"""Read-only functional/latency probes. Write workflow acceptance is separate."""
import argparse,base64,concurrent.futures,json,statistics,time,uuid,urllib.request,wave
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:18080');p.add_argument('--output',required=True);p.add_argument('--audio');p.add_argument('--iterations',type=int,default=3);p.add_argument('--concurrency',type=int,default=2);a=p.parse_args()
 if not 1<=a.concurrency<=8 or a.iterations<1:raise SystemExit('Invalid workload')
 output=Path(a.output).resolve();output.mkdir(parents=True,exist_ok=True);results=[]
 def req(path,data=None,headers=None):
  body=None if data is None else (data if isinstance(data,bytes) else json.dumps(data,ensure_ascii=False).encode())
  h={'Content-Type':'application/json'};h.update(headers or {})
  return urllib.request.urlopen(urllib.request.Request(a.base.rstrip('/')+path,data=body,headers=h),timeout=240)
 def get(path):
  with req(path) as r:return json.load(r)
 def sse(path,body,tts=False):
  start=time.monotonic();first=None;events=[];pcm=0;rate=24000;data_records=[]
  with req(path,body,{'Accept':'text/event-stream'}) as r:
   event=''
   for b in r:
    line=b.decode('utf-8').strip()
    if line.startswith('event:'):event=line[6:].strip();events.append(event)
    if line.startswith('data:'):
     try:d=json.loads(line[5:])
     except json.JSONDecodeError:continue
     if event=='audio.start':rate=d.get('sampleRate',24000)
     if event=='audio.chunk':
      if first is None:first=time.monotonic()-start
      pcm+=len(base64.b64decode(d['pcm']))
     if event=='result.rag':data_records.append(d)
  total=time.monotonic()-start
  if tts:
   assert 'audio.completed' in events and 'audio.failed' not in events and pcm>0,events
   return {'seconds':total,'first_audio_seconds':first,'rtf':total/(pcm/(rate*2)),'sample_rate':rate,'pcm_bytes':pcm}
  assert 'run.completed' in events and 'run.failed' not in events,events
  return {'seconds':total,'events':events,'rag':data_records}
 def check(name,fn):
  try:result=fn();item={'check':name,'passed':True,'metrics':result}
  except Exception as e:item={'check':name,'passed':False,'error_type':type(e).__name__}
  results.append(item);(output/'acceptance.json').write_text(json.dumps({'checks':results,'deployment_accepted':False},ensure_ascii=False,indent=2));print(name,item['passed'],flush=True)
 for path in ['/api/v1/speech/capabilities','/api/v1/facility-alerts','/api/v1/facility-alerts/health-report','/api/v1/emergency-workflows/inbox?stage=LEVEL_1']:
  def health(path=path):
   d=get(path);assert d['code']=='OK';return {'code':d['code']}
  check(path,health)
 def conversation(message):return sse('/api/v1/conversations/'+str(uuid.uuid4())+'/messages/stream',{'message':message})
 def traffic():
  d=conversation('福建省目前整体交通态势如何？');assert 'result.traffic' in d['events'];return d
 def rag():
  d=conversation('根据《公路养护工程管理办法》，养护工程验收有哪些要求？');assert d['rag'] and any(x.get('hitCount',0)>0 and x.get('sources') for x in d['rag']);return d
 check('traffic_sse',traffic);check('rag_sources',rag)
 def tts():return sse('/api/v1/speech/syntheses/stream',{'text':'G205，K123+456，降雨31.495毫米，当前通行能力为百分之八十。'},True)
 check('tts_stream',tts)
 def audio_file():
  with req('/api/v1/speech/syntheses',{'text':'福州市目前整体交通态势如何？'}) as r:b=r.read()
  assert len(b)>1000;(output/'listen-test.mp3').write_bytes(b);return {'bytes':len(b)}
 check('tts_mp3',audio_file)
 if a.audio:
  def asr():
   f=Path(a.audio)
   with wave.open(str(f)) as w:duration=round(w.getnframes()/w.getframerate()*1000)
   assert 100<=duration<=60000
   boundary='site-'+uuid.uuid4().hex
   body=(f'--{boundary}\r\nContent-Disposition: form-data; name="durationMs"\r\n\r\n{duration}\r\n--{boundary}\r\nContent-Disposition: form-data; name="audio"; filename="traffic.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode()+f.read_bytes()+f'\r\n--{boundary}--\r\n'.encode()
   with req('/api/v1/speech/transcriptions',body,{'Content-Type':'multipart/form-data; boundary='+boundary}) as r:d=json.load(r)
   assert d['code']=='OK' and '福州' in d['data']['text'] and '交通' in d['data']['text'];return {'recognized':d['data']['text']}
  check('recorded_asr',asr)
 else:results.append({'check':'recorded_asr','passed':False,'status':'NOT_RUN; supply --audio'})
 measurements=[]
 for i in range(a.iterations):
  def mixed():
   with concurrent.futures.ThreadPoolExecutor(max_workers=a.concurrency+1) as pool:
    jobs=[pool.submit(tts) for _ in range(a.concurrency)]+[pool.submit(traffic)]
    values=[j.result() for j in jobs]
   measurements.extend(values[:-1]);return {'tts':values[:-1],'traffic_seconds':values[-1]['seconds']}
  check('mixed_'+str(i+1),mixed)
 if measurements:
  xs=sorted(x['first_audio_seconds'] for x in measurements)
  p95=xs[min(len(xs)-1,max(0,__import__('math').ceil(len(xs)*.95)-1))]
  (output/'latency-summary.json').write_text(json.dumps({'tts_first_audio_p95_s':p95,'tts_rtf_max':max(x['rtf'] for x in measurements),'requests':len(xs),'cold_start_excluded':'run warmup before measured workload'},indent=2))
 summary={'checks':results,'automated_checks_passed':all(x['passed'] for x in results),'deployment_accepted':False,'pending':['manual business answer quality','write workflows','TTS cancellation','baseline comparison','continuous observation','backup recovery']}
 (output/'acceptance.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
 if not summary['automated_checks_passed']:raise SystemExit(2)
if __name__=='__main__':main()
