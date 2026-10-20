#!/usr/bin/env python3
"""Run inside rag-model; output contains only fixed synthetic test texts/vectors."""
import json,math,os,urllib.request
def post(path,data):
 req=urllib.request.Request('http://127.0.0.1:8001'+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+os.environ['BGE_API_KEY']})
 with urllib.request.urlopen(req,timeout=180) as r:return json.load(r)
texts=['公路养护工程应当按规定组织验收。','今天的天气晴朗。']
d=post('/v1/embeddings',{'model':'bge-m3-ft-final','input':texts})
v=[x['embedding'] for x in d['data']]
assert len(v)==2 and all(len(x)==1024 for x in v)
norms=[math.sqrt(sum(y*y for y in x)) for x in v]
assert all(abs(n-1)<.01 for n in norms)
r=post('/v1/rerank',{'model':'bge-reranker-v2-m3-ft-final','query':'养护工程如何验收','documents':texts,'top_n':2})
assert len(r['results'])==2
print(json.dumps({'dimension':1024,'norms':norms,'embeddings':v,'rerank':r},ensure_ascii=False))
