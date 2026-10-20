#!/usr/bin/env python3
import argparse,concurrent.futures,json,time,urllib.request,uuid
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:18080');p.add_argument('--output',required=True);a=p.parse_args()
rid=str(uuid.uuid4());start=time.monotonic()
def synth(text,request_id=None):
 h={'Content-Type':'application/json'}
 if request_id:h['X-Speech-Request-Id']=request_id
 req=urllib.request.Request(a.base+'/api/v1/speech/syntheses',data=json.dumps({'text':text}).encode(),headers=h)
 try:
  with urllib.request.urlopen(req,timeout=180) as r:return {'completed':True,'bytes':len(r.read()),'elapsed_s':time.monotonic()-start}
 except Exception as e:return {'completed':False,'error_type':type(e).__name__,'elapsed_s':time.monotonic()-start}
with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
 f=pool.submit(synth,'现场取消测试，请在播放结束前停止。'*20,rid)
 time.sleep(.5)
 with urllib.request.urlopen(urllib.request.Request(a.base+'/api/v1/speech/syntheses/'+rid,method='DELETE'),timeout=10) as r:status=r.status
 result=f.result()
recovery=synth('取消之后，语音服务应当能够继续正常使用。')
report={'delete_status':status,'cancelled_request':result,'recovery_request':recovery,'manual_log_check_required':True,'passed':status==204 and not result['completed'] and recovery['completed']}
Path(a.output).write_text(json.dumps(report,ensure_ascii=False,indent=2))
if not report['passed']:raise SystemExit('Cancellation failed/inconclusive; inspect logs and rerun with a sufficiently long request')
print('Cancellation API and subsequent synthesis passed; inspect backend work termination in logs.')
