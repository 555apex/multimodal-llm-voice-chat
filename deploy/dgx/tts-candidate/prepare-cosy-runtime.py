import pathlib,urllib.request,json,subprocess,tarfile,io,os
r=pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx/.releases/speech-20260910');source=r/'cosyvoice'
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'RoadAgent-candidate'}),timeout=30) as response:return response.read()
revision=json.loads((r/'cosy-source-revision.json').read_text())['revision']
tree=json.loads(get('https://api.github.com/repos/QwenAudio/CosyVoice/git/trees/'+revision+'?recursive=1'))
matcha=next(x for x in tree['tree'] if x['path']=='third_party/Matcha-TTS')['sha']
with tarfile.open(fileobj=io.BytesIO(get('https://api.github.com/repos/shivammehta25/Matcha-TTS/tarball/'+matcha)),mode='r:gz') as archive:
 target=source/'third_party/Matcha-TTS';target.mkdir(parents=True,exist_ok=True)
 for member in archive.getmembers():
  if member.issym() or member.islnk():continue
  parts=pathlib.PurePosixPath(member.name).parts[1:]
  if parts:member.name=str(pathlib.PurePosixPath(*parts));archive.extract(member,target,filter='data')
print('Pinned Matcha',matcha,flush=True)
metadata=json.loads(get('https://hf-mirror.com/api/models/FunAudioLLM/Fun-CosyVoice3-0.5B-2512/revision/29e01c4e8d000f4bcd70751be16fa94bf3d85a18'))
model_revision=metadata['sha'];model=pathlib.Path('/home/whtc/models/FunAudioLLM--Fun-CosyVoice3-0.5B-2512');model.mkdir(exist_ok=True)
(r/'cosy-model-revision.json').write_text(json.dumps({'repository':'FunAudioLLM/Fun-CosyVoice3-0.5B-2512','revision':model_revision,'matchaRevision':matcha,'directory':str(model)}))
print('Pinned model',model_revision,'files',[x['rfilename'] for x in metadata['siblings']],flush=True)
base=json.loads(subprocess.check_output(['docker','image','inspect','road-agent-dgx-speech:before-speech-20260910']))[0]['Id']
download="from huggingface_hub import snapshot_download; snapshot_download('FunAudioLLM/Fun-CosyVoice3-0.5B-2512',revision="+repr(model_revision)+",local_dir='/download',max_workers=3,ignore_patterns=['*.zip','*.engine','*.plan'])"
subprocess.run(['docker','run','--rm','--name','road-agent-cosy-download','--memory','4g','--network','host','--user','0','-e','HF_ENDPOINT=https://hf-mirror.com','-e','HF_HUB_OFFLINE=0','-e','TRANSFORMERS_OFFLINE=0','-e','HF_HUB_DISABLE_XET=1','-v',str(model)+':/download','--entrypoint','python',base,'-c',download],check=True)
print('CosyVoice weights ready',flush=True)
