#!/usr/bin/env python3
"""Check a separately collected offline payload. Does not install or start services."""
import argparse,hashlib,json,re
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--artifacts-root',required=True)
    p.add_argument('--manifest',required=True)
    p.add_argument('--report')
    a=p.parse_args()
    root=Path(a.artifacts_root).resolve()
    spec=json.loads(Path(a.manifest).read_text(encoding='utf-8'))
    checks=[]
    review=spec.get('hardware_review',{})
    checks.append({'item':'hardware_review','passed':review.get('approved') is True and bool(review.get('evidence'))})
    required={'base-images','reviewed-extra-images','speech-retrieval-weights','llm-weights','encrypted-data'}
    present={entry.get('role') for entry in spec.get('artifacts',[])}
    checks.append({'item':'required_resource_roles','passed':required.issubset(present),'missing':sorted(required-present)})
    if not spec.get('artifacts'):checks.append({'item':'artifacts','passed':False,'reason':'empty manifest'})
    for entry in spec.get('artifacts',[]):
        item={'item':entry.get('name','unnamed'),'passed':False}
        name=entry.get('file','');path=(root/name).resolve()
        if not name or not path.is_relative_to(root) or not path.is_file():
            item['reason']='missing file or unsafe path'
        elif not re.fullmatch(r'[a-fA-F0-9]{64}',entry.get('sha256','') or ''):
            item['reason']='trusted expected SHA256 missing'
        else:
            h=hashlib.sha256()
            with path.open('rb') as f:
                for chunk in iter(lambda:f.read(8*1024**2),b''):h.update(chunk)
            item['passed']=h.hexdigest()==entry['sha256'].lower()
            item['reason']='verified' if item['passed'] else 'SHA256 mismatch'
        checks.append(item)
    report={'offline_payload_complete':all(x['passed'] for x in checks),'checks':checks,'onsite_deployment_accepted':False}
    if a.report:Path(a.report).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=True,indent=2))
    if not report['offline_payload_complete']:raise SystemExit(2)

if __name__=='__main__':main()
