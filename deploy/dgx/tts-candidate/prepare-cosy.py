import pathlib,urllib.request,json,subprocess,time,tarfile,io
r=pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx/.releases/speech-20260910')
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'RoadAgent-audit'}),timeout=25) as response:return response.read()
revision='074ca6dc9e80a2f424f1f74b48bdd7d3fea531cc';print('CosyVoice revision',revision,flush=True)
(r/'cosy-source-revision.json').write_text(json.dumps({'repository':'https://github.com/QwenAudio/CosyVoice','revision':revision}))
data=get('https://api.github.com/repos/QwenAudio/CosyVoice/tarball/'+revision)
target=r/'cosyvoice';target.mkdir(exist_ok=True)
with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as archive:
 for member in archive.getmembers():
  parts=pathlib.PurePosixPath(member.name).parts[1:]
  if not parts:continue
  member.name=str(pathlib.PurePosixPath(*parts));archive.extract(member,target,filter='data')
print((target/'requirements.txt').read_text(),flush=True)
print('Source downloaded',flush=True)
