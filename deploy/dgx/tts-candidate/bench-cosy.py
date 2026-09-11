import pathlib,subprocess
r=pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx/.releases/speech-20260910')
code='''import sys,time,json,torch,soundfile,numpy as np,pathlib
from cosyvoice.cli.cosyvoice import CosyVoice3
torch.set_num_threads(2)
out=pathlib.Path('/results');report={'model':'Fun-CosyVoice3-0.5B-2512','fp16':True,'samples':[]}
t=time.monotonic();model=CosyVoice3('/model',load_trt=False,load_vllm=False,fp16=True);report['loadSeconds']=time.monotonic()-t
prompt='You are a helpful assistant.<|endofprompt|>希望你以后能够做的比我还好呦。'
model.add_zero_shot_spk(prompt,'/opt/cosyvoice/asset/zero_shot_prompt.wav','road-agent')
report['cachedVoice']=True
def generate(text,name):
 t=time.monotonic();chunks=[];timings=[]
 for result in model.inference_zero_shot(text,'','',zero_shot_spk_id='road-agent',stream=True):
  chunks.append(result['tts_speech'].cpu().numpy().reshape(-1));timings.append(time.monotonic()-t)
 audio=np.concatenate(chunks);elapsed=time.monotonic()-t;soundfile.write(str(out/(name+'.wav')),audio,model.sample_rate)
 row={'text':text,'firstChunk':timings[0],'seconds':elapsed,'audioSeconds':len(audio)/model.sample_rate,'rtf':elapsed/(len(audio)/model.sample_rate),'chunks':timings,'rate':model.sample_rate};print(json.dumps(row,ensure_ascii=False),flush=True);return row
generate('路智通已准备好。','warmup')
for i,text in enumerate(['请查询福州道路的交通情况。','G104国道限速每小时六十公里。','当前福建省主要道路通行平稳。福州部分路段车流较大，请保持安全车距。G104国道限速每小时六十公里，经过施工区域时请减速慢行。前往泉州和厦门的车辆，可提前查看实时交通信息。如遇突发事故，请听从现场工作人员指挥，避免占用应急车道。预计未来三十分钟车流将逐步增加，请合理安排出行时间。']):
 report['samples'].append(generate(text,'candidate-'+str(i)))
 (out/'cosy-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
import concurrent.futures
with concurrent.futures.ThreadPoolExecutor(2) as pool:
 report['dual']=list(pool.map(lambda i:generate('G104国道限速每小时六十公里，请保持安全车距。','dual-'+str(i)),range(2)))
(out/'cosy-smoke.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
'''
(r/'bench-cosy-inner.py').write_text(code)
(r/'cosy-results').mkdir(exist_ok=True)
subprocess.run(['docker','run','--rm','--name','road-agent-cosy-bench','--gpus','all','--memory','16g','--ipc','host','--network','none','-e','HF_HUB_OFFLINE=1','-e','TRANSFORMERS_OFFLINE=1','-v','/home/whtc/models/FunAudioLLM--Fun-CosyVoice3-0.5B-2512:/model:ro','-v',str(r/'cosy-results')+':/results','-v',str(r/'bench-cosy-inner.py')+':/bench.py:ro','--entrypoint','python','road-agent-cosy:speech-20260910','/bench.py'],check=True)
