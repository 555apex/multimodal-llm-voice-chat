#!/usr/bin/env python3
import argparse,hashlib,json,subprocess,sys
from pathlib import Path
def env_read(path):
 return dict(line.split('=',1) for line in Path(path).read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
def main():
 p=argparse.ArgumentParser();p.add_argument('--env-file',required=True);a=p.parse_args()
 env=env_read(a.env_file);root=Path(a.env_file).resolve().parent
 profile=json.loads((root/'target-profile.json').read_text());fail=[]
 accelerator=profile.get('accelerator_type','ascend')
 def need(ok,msg):
  if not ok:fail.append(msg)
 need(profile.get('approved') is True,'profile not approved')
 need(accelerator in ['ascend','nvidia'],'unknown accelerator type')
 for k in ['reviewer','reviewed_at','os_id','os_version_id','driver_version','compatibility_evidence','tts_validation']+(['npu_model','cann_version'] if accelerator=='ascend' else ['gpu_model']):
  v=profile.get(k,'');need(bool(v) and 'REPLACE' not in v,'missing '+k)
 osinfo=env_read('/etc/os-release') if Path('/etc/os-release').exists() else {}
 need(osinfo.get('ID','').strip('"')==profile.get('os_id'),'OS ID mismatch')
 need(osinfo.get('VERSION_ID','').strip('"')==profile.get('os_version_id'),'OS version mismatch')
 need(subprocess.check_output(['uname','-m'],text=True).strip()==profile.get('architecture'),'architecture mismatch')
 need(profile.get('npu_count')==8 if accelerator=='ascend' else isinstance(profile.get('gpu_count'),int) and profile.get('gpu_count',0)>0,'expected reviewed accelerator count')
 try:
  smi=subprocess.run(['npu-smi','info'] if accelerator=='ascend' else ['nvidia-smi','-L'],capture_output=True,text=True)
  need(smi.returncode==0,'accelerator health command failed')
  if accelerator=='nvidia':need(sum(line.startswith('GPU ') for line in smi.stdout.splitlines())==profile.get('gpu_count'),'GPU count mismatch')
  # Exact count/model and firmware must be reviewed from saved vendor inventory.
 except FileNotFoundError:need(False,'accelerator health tool missing')
 comp=root/('compose.npu.yaml' if accelerator=='ascend' else 'compose.nvidia.yaml')
 if not comp.is_file():need(False,'missing reviewed compose.npu.yaml')
 else:
  content=comp.read_bytes();need(b'REPLACE' not in content,'NPU compose still contains placeholders')
  need(hashlib.sha256(content).hexdigest()==profile.get('accelerator_compose_sha256',profile.get('npu_compose_sha256')),'accelerator compose hash mismatch')
 locks=profile.get('images',{})
 for key in ['MYSQL_IMAGE','BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE','REDIS_IMAGE','WEKNORA_APP_IMAGE','WEKNORA_UI_IMAGE','WEKNORA_DOCREADER_IMAGE','RAG_CPU_IMAGE','SPEECH_CPU_IMAGE','LLM_IMAGE','TTS_IMAGE']:
  ref=env.get(key,'');lock=locks.get(key,{})
  need(bool(ref) and lock.get('ref')==ref,'missing image reference lock '+key)
  if not ref:continue
  image=subprocess.run(['docker','image','inspect',ref],capture_output=True,text=True)
  if image.returncode:need(False,'image unavailable '+key);continue
  im=json.loads(image.stdout)[0];need(im['Architecture']=={'aarch64':'arm64','x86_64':'amd64'}.get(profile.get('architecture')),'wrong image architecture '+key)
  need(im['Id']==lock.get('id'),'image ID mismatch '+key)
 weights=profile.get('llm_weights',{})
 need(weights.get('format') in ['BF16','FP16','vendor-validated-quantized'],'unsupported/unreviewed LLM format')
 need(bool(weights.get('revision')),'missing LLM revision')
 manifest=Path(weights.get('path',''))/'SHA256SUMS'
 if manifest.is_file():
  need(hashlib.sha256(manifest.read_bytes()).hexdigest()==weights.get('manifest_sha256'),'LLM manifest mismatch')
  if not fail:
   checked=subprocess.run(['sha256sum','--strict','--check',manifest.name],cwd=manifest.parent,capture_output=True)
   need(checked.returncode==0,'LLM weight checksum verification failed')
 else:need(False,'LLM SHA256SUMS missing')
 need(env.get('ROADAGENT_RAG_API_KEY','') not in ['', '__RESTORE__'],'restore RoadAgent RAG API key first')
 if fail:
  print('BLOCKED:');print('\n'.join('- '+s for s in fail));raise SystemExit(2)
 print('PROFILE PASSED. This is a configuration gate, not a performance acceptance result.')
if __name__=='__main__':main()
