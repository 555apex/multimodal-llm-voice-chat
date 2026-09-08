#!/usr/bin/env python3
"""DGX 2026-09-08 release operations. Credentials stay on the DGX host."""
import argparse, hashlib, json, os, pathlib, re, shutil, subprocess, tarfile, time

ROOT = pathlib.Path('/home/whtc/workspace/projects/road-agent-dgx')
RELEASE = ROOT / '.releases/merge-20260908-61e64d4'
SOURCE = RELEASE / 'source'
LOGS = RELEASE / 'logs'
QA_SCHEMA = 'road_agent_merge_20260908'
IMAGE = 'dockerproxy.net/library/mysql:8.4.11-oraclelinux9'
ENV = ROOT / 'deploy/dgx/.env'
PUBLIC_ENV = pathlib.Path('/home/whtc/.config/road-agent/public-original-db.env')
AUTH = pathlib.Path('/home/whtc/.config/road-agent/public.htpasswd')
FLOWS = ['w_emergency_resource_allocation', 'w_emergency_dispatch_action_log',
         'w_emergency_command_decision', 'w_emergency_professional_review',
         'w_emergency_dispatch_workflow', 'w_emergency_dispatch_order']
TOUCHED = FLOWS + ['w_emergency_resource', 'w_lw_incident', 'w_emergency_event_classification']
CONTAINERS = ['road-agent-dgx-backend', 'road-agent-dgx-public-backend',
              'road-agent-dgx-frontend', 'road-agent-dgx-public-frontend', 'road-agent-dgx-speech']

def run(args, **kw):
    p = subprocess.run(args, capture_output=True, **kw)
    if p.returncode: raise RuntimeError(p.stderr.decode(errors='replace') if isinstance(p.stderr, bytes) else p.stderr)
    return p.stdout

def values(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.startswith('#'))

def schema_url(schema):
    original = values(ENV)['ROADAGENT_DB_URL']
    return re.sub(r'^(jdbc:mysql://[^/]+/)[^?]+', lambda m: m[1] + schema, original)

def db_args(schema='road_agent'):
    assert re.fullmatch(r'[a-zA-Z0-9_]+', schema)
    return ['docker', 'run', '--rm', '-i', '--network', 'host', '--env-file', str(ENV),
            '-e', 'ROADAGENT_DB_URL=' + schema_url(schema),
            '-v', str(ROOT/'deploy/dgx/mysql-client-entrypoint') + ':/db-tool:ro',
            '--entrypoint', '/db-tool', IMAGE]

def query(sql, schema='road_agent'):
    return run(db_args(schema) + ['query', sql], text=True).strip()

def tables(schema='road_agent'):
    return query('SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE=\'BASE TABLE\' ORDER BY TABLE_NAME', schema).splitlines()

def counts(schema, names):
    return query(' UNION ALL '.join(f"SELECT '{t}',COUNT(*) FROM `{t}`" for t in names), schema)

def dump(path, schema, names):
    with path.open('wb') as out:
        p = subprocess.run(db_args(schema) + ['dump', *names], stdout=out, stderr=subprocess.PIPE)
    if p.returncode: raise RuntimeError(p.stderr.decode())
    path.chmod(0o600)

def restore(path, schema):
    with path.open('rb') as inp:
        p = subprocess.run(db_args(schema) + ['restore', '/dev/stdin'], stdin=inp, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode: raise RuntimeError(p.stderr.decode())

def write_json(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False))
    path.chmod(0o600)

def file_sha256(path):
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(4*1024*1024),b''): digest.update(chunk)
    return digest.hexdigest()

def inventory_check(schema):
    invalid = query('SELECT COUNT(*) FROM w_emergency_resource WHERE available_quantity<0 OR reserved_quantity<0 OR dispatched_quantity<0 OR available_quantity+reserved_quantity+dispatched_quantity>total_quantity', schema)
    assert invalid == '0', 'invalid inventory: ' + invalid
    mismatches = query('''SELECT COUNT(*) FROM w_emergency_resource r LEFT JOIN
      (SELECT resource_id,SUM(IF(allocation_status=0,allocated_quantity,0)) AS reserved,
       SUM(IF(allocation_status=1,allocated_quantity,0)) AS dispatched
       FROM w_emergency_resource_allocation GROUP BY resource_id) a ON a.resource_id=r.resource_id
       WHERE r.reserved_quantity<>COALESCE(a.reserved,0) OR r.dispatched_quantity<>COALESCE(a.dispatched,0)''', schema)
    assert mismatches == '0', 'inventory/allocation mismatch: ' + mismatches

def reset_flows(schema):
    inventory_check(schema)
    before = query('SELECT SUM(available_quantity+reserved_quantity+dispatched_quantity),SUM(total_quantity) FROM w_emergency_resource', schema)
    # The preceding check ensures every occupied quantity belongs to the removed application ledger.
    sql = '''START TRANSACTION;
      UPDATE w_emergency_resource r JOIN
      (SELECT resource_id,SUM(IF(allocation_status=0,allocated_quantity,0)) AS reserved,
       SUM(IF(allocation_status=1,allocated_quantity,0)) AS dispatched
       FROM w_emergency_resource_allocation GROUP BY resource_id) a ON a.resource_id=r.resource_id
      SET r.available_quantity=r.available_quantity+a.reserved+a.dispatched,
          r.reserved_quantity=r.reserved_quantity-a.reserved,
          r.dispatched_quantity=r.dispatched_quantity-a.dispatched,
          r.lock_version=r.lock_version+1,r.update_time=CURRENT_TIMESTAMP(6);
    ''' + '\n'.join('DELETE FROM '+t+';' for t in FLOWS) + '\nCOMMIT;'
    query(sql, schema)
    inventory_check(schema)
    assert before == query('SELECT SUM(available_quantity+reserved_quantity+dispatched_quantity),SUM(total_quantity) FROM w_emergency_resource', schema)
    assert all(line.endswith('\t0') for line in counts(schema, FLOWS).splitlines())
    print('Flow reset and inventory conservation verified:', schema)

def prepare():
    backup = RELEASE/'preflight-backup'
    backup.mkdir(exist_ok=True, mode=0o700)
    if (backup/'ready.json').exists(): print('Preflight backup already verified'); return
    exists = query(f"SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{QA_SCHEMA}'")
    assert exists == '0', 'QA schema already exists; inspect before retry'
    names = tables()
    snapshot = counts('road_agent', names)
    dump(backup/'full.sql', 'road_agent', names)
    query(f'CREATE DATABASE `{QA_SCHEMA}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci')
    restore(backup/'full.sql', QA_SCHEMA)
    assert snapshot == counts(QA_SCHEMA, names), 'backup restore counts differ'
    reset_flows(QA_SCHEMA)
    env = values(ENV)
    env.update(ROADAGENT_DB_URL=schema_url(QA_SCHEMA), ROADAGENT_EVENT_CLASSIFICATION_ENABLED='false',
               ROADAGENT_MODEL_ENDPOINT='http://qwen:8000/v1/chat/completions',
               ROADAGENT_MODEL_NAME='qwen3.6-35b-a3b-nvfp4', ROADAGENT_MODEL_AUTH_ENABLED='false',
               ROADAGENT_MODEL_ENABLE_THINKING='false', ROADAGENT_MODEL_TIMEOUT_SECONDS='180',
               ROADAGENT_DISPATCH_STALE_GENERATING_SECONDS='600', ROADAGENT_SPEECH_ENABLED='true',
               ROADAGENT_SPEECH_SERVICE_URL='http://speech-service:8091')
    test_env = RELEASE/'qa.env'
    test_env.write_text('\n'.join(k+'='+v for k,v in env.items())+'\n'); test_env.chmod(0o600)
    write_json(backup/'ready.json', {'tables': names, 'counts': snapshot, 'sha256': hashlib.sha256((backup/'full.sql').read_bytes()).hexdigest()})
    print('Restored and verified isolated database:', QA_SCHEMA, 'tables:', len(names))

def start_qa():
    run(['docker','run','-d','--name','road-agent-merge-qa','--env-file',str(RELEASE/'qa.env'),
         '--network','dgx-ai','-p','127.0.0.1:32768:8080','road-agent-dgx-backend:merge-20260908'])
    run(['docker','network','connect','road-agent-dgx_speech-private','road-agent-merge-qa'])
    print(run(['docker','port','road-agent-merge-qa','8080'],text=True))

def guard(source=SOURCE):
    args=['docker','compose','--env-file',str(source/'deploy/dgx/.env'),'--project-directory',str(source),
          '-f',str(source/'deploy/dgx/compose.yaml'),'-f',str(source/'deploy/dgx/compose.public.yaml'),
          '--profile','public','config','--format','json']
    config=json.loads(run(args,text=True))
    for name in ['backend','public-backend']:
        env=config['services'][name]['environment']
        assert env['ROADAGENT_MODEL_ENDPOINT']=='http://qwen:8000/v1/chat/completions'
        assert env['ROADAGENT_MODEL_NAME']=='qwen3.6-35b-a3b-nvfp4'
        assert str(env['ROADAGENT_MODEL_ENABLE_THINKING']).lower()=='false'
        assert env['ROADAGENT_SPEECH_SERVICE_URL']=='http://speech-service:8091'
        assert str(env['ROADAGENT_EVENT_CLASSIFICATION_ENABLED']).lower()==str(name=='public-backend').lower()
    speech=config['services']['speech-service']
    assert speech['environment']['SPEECH_ASR_MODEL_PATH']=='/models/Systran--faster-whisper-small'
    assert speech['environment']['SPEECH_TTS_MODEL_PATH']=='/models/Qwen--Qwen3-TTS-12Hz-0.6B-CustomVoice'
    assert str(speech['environment']['HF_HUB_OFFLINE'])=='1'
    assert all(v.get('read_only') for v in speech['volumes'] if v['type']=='bind')
    print('DGX local model deployment guard passed')

def stopped():
    states=json.loads(run(['docker','inspect',*CONTAINERS[:-1]],text=True))
    assert not any(c['State']['Running'] for c in states), 'stop both private and public applications first'

def final_backup():
    assert (RELEASE/'acceptance.json').exists(), 'isolated acceptance must pass before maintenance'
    backup=RELEASE/'final-backup'
    assert not backup.exists(), 'final backup exists; inspect release state before retry'
    backup.mkdir(mode=0o700)
    # Stop ingress before the two backends, keeping the shared model services running.
    run(['docker','stop',*CONTAINERS[2:4],*CONTAINERS[:2]])
    run(['docker','stop','road-agent-merge-qa'])
    stopped()
    names=tables()
    dump(backup/'full.sql','road_agent',names)
    dump(backup/'affected.sql','road_agent',TOUCHED)
    final_counts=counts('road_agent',names)
    # Reuse the isolated schema for a full final restore rehearsal after all test runs.
    restore(backup/'full.sql',QA_SCHEMA)
    assert final_counts==counts(QA_SCHEMA,names), 'final restore verification failed'
    (backup/'counts.tsv').write_text(final_counts)
    username=values(PUBLIC_ENV)['ROADAGENT_DB_USERNAME']
    assert re.fullmatch(r'[a-zA-Z0-9_]+',username)
    grants=query(f"SHOW GRANTS FOR '{username}'@'%'")
    (backup/'public-grants.sql').write_text(grants.replace('\n',';\n')+';\n')
    for name,p in [('runtime.env',ENV),('public-db.env',PUBLIC_ENV),('public.htpasswd',AUTH)]:
        shutil.copy2(p,backup/name); (backup/name).chmod(0o600)
    with (backup/'source.tar.gz').open('wb') as out:
        p=subprocess.run(['tar','--exclude=./.releases','--exclude=./outputs','--exclude=.env',
          '--exclude=.env.db-admin','--exclude=api-test.env','--exclude=api-test.ps1','--exclude=node_modules',
          '--exclude=target','--exclude=dist','--exclude=__pycache__','-czf','-','-C',str(ROOT),'.'],stdout=out,stderr=subprocess.PIPE)
        assert p.returncode==0,p.stderr.decode()
    containers=json.loads(run(['docker','inspect',*CONTAINERS,'model-serving-qwen'],text=True))
    images={c['Name'].lstrip('/'):c['Image'] for c in containers}
    for service in ['backend','frontend','speech']:
        run(['docker','tag',images['road-agent-dgx-'+service],'road-agent-dgx-'+service+':before-merge-20260908'])
    manifest={'counts':final_counts,'images':images,'auth_sha256':hashlib.sha256(AUTH.read_bytes()).hexdigest(),
              'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in backup.iterdir() if p.is_file()}}
    write_json(backup/'ready.json',manifest)
    print('Final backup and restore verified; maintenance active')

def protected(relative):
    return any(x in {'.git','.releases','outputs','target','node_modules','dist','__pycache__'} for x in relative.parts) or relative.name in {'.env','.env.db-admin','api-test.env','api-test.ps1'}

def sync_source(source):
    governed=['road-agent-domain','road-agent-application','road-agent-core','road-agent-adapters',
              'road-agent-interface','road-agent-boot','frontend','speech-service','contracts','configs','deploy']
    # Delete only obsolete source files, never runtime credentials, caches, or backups.
    for name in governed:
        for p in (ROOT/name).rglob('*'):
            rel=p.relative_to(ROOT)
            if p.is_file() and not protected(rel) and not (source/rel).exists(): p.unlink()
    for p in source.rglob('*'):
        rel=p.relative_to(source)
        if not p.is_file() or protected(rel): continue
        dest=ROOT/rel
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,dest)

def compose(*args):
    return run(['docker','compose','--env-file',str(ENV),'--project-directory',str(ROOT),
                '-f',str(ROOT/'deploy/dgx/compose.yaml'),'-f',str(ROOT/'deploy/dgx/compose.public.yaml'),
                '--profile','public',*args],text=True)

def cutover():
    stopped()
    backup=RELEASE/'final-backup'
    assert (backup/'ready.json').exists(), 'verified final backup required'
    assert not (RELEASE/'cutover-started.json').exists(), 'cutover already attempted; inspect before retry'
    guard()
    write_json(RELEASE/'cutover-started.json',{'time':time.time()})
    reset_flows('road_agent')
    user=values(PUBLIC_ENV)['ROADAGENT_DB_USERNAME']
    assert re.fullmatch(r'[a-zA-Z0-9_]+',user)
    grants=[('SELECT, UPDATE','w_lw_incident'),('SELECT, INSERT','w_emergency_event_classification'),
            ('SELECT','w_emergency_response_plan'),('SELECT','w_festival_data'),
            ('SELECT, UPDATE (status, remark)','w_realtime_abnormal')]
    for privileges,table in grants: query(f"GRANT {privileges} ON road_agent.{table} TO '{user}'@'%'")
    sync_source(SOURCE)
    env=values(ENV)
    for service in ['BACKEND','FRONTEND','SPEECH']:
        env['ROADAGENT_'+service+'_IMAGE']='road-agent-dgx-'+service.lower()+':merge-20260908'
    ENV.write_text('\n'.join(k+'='+v for k,v in env.items())+'\n'); ENV.chmod(0o600)
    guard(ROOT)
    # Keep ingress stopped until model and business checks have passed.
    print(compose('up','-d','--no-build','speech-service','backend','public-backend'))
    print('New backends started; ingress remains stopped for verification')

def open_ingress():
    assert (RELEASE/'live-acceptance.json').exists(), 'live private checks required'
    baseline=json.loads((RELEASE/'final-backup/ready.json').read_text())
    assert hashlib.sha256(AUTH.read_bytes()).hexdigest()==baseline['auth_sha256']
    print(compose('up','-d','--no-build','--no-deps','--force-recreate','frontend','public-frontend'))
    write_json(RELEASE/'opened.json',{'time':time.time()})
    print('Ingress opened with original authentication and Funnel address')

def rollback():
    backup=RELEASE/'final-backup'
    manifest=json.loads((backup/'ready.json').read_text())
    for filename,digest in manifest['files'].items():
        assert file_sha256(backup/filename)==digest,filename
    image_ids=[manifest['images']['road-agent-dgx-'+s] for s in ['backend','frontend','speech']]
    available=subprocess.run(['docker','image','inspect',*image_ids],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if available.returncode:
        assert (backup/'images.tar').exists(), 'old images missing and no offline archive is available'
        run(['docker','image','load','--input',str(backup/'images.tar')])
    run(['docker','stop',*CONTAINERS[2:4],*CONTAINERS[:2]])
    stopped()
    restore(backup/'affected.sql','road_agent')
    user=values(PUBLIC_ENV)['ROADAGENT_DB_USERNAME']
    query(f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM '{user}'@'%';\n"+(backup/'public-grants.sql').read_text())
    old=RELEASE/'rollback-source'
    old.mkdir(exist_ok=True)
    with tarfile.open(backup/'source.tar.gz') as archive: archive.extractall(old,filter='data')
    sync_source(old)
    shutil.copy2(backup/'runtime.env',ENV)
    for service in ['backend','frontend','speech']:
        run(['docker','tag',manifest['images']['road-agent-dgx-'+service],'road-agent-dgx-'+service+':local'])
    print(compose('up','-d','--no-build','--force-recreate','speech-service','backend','public-backend','frontend','public-frontend'))
    write_json(RELEASE/'rolled-back.json',{'time':time.time()})
    print('Original source, affected database, grants and images restored')

def archive_images():
    backup=RELEASE/'final-backup'
    manifest=json.loads((backup/'ready.json').read_text())
    names=['road-agent-dgx-'+s+':before-merge-20260908' for s in ['backend','frontend','speech']]
    images=json.loads(run(['docker','image','inspect',*names],text=True))
    assert shutil.disk_usage(backup).free > sum(image['Size'] for image in images)*2
    archive=backup/'images.tar'
    if not archive.exists():
        partial=backup/'images.tar.partial'
        assert not partial.exists(), 'inspect interrupted image archive before retry'
        run(['docker','image','save','--output',str(partial),*names])
        partial.rename(archive); archive.chmod(0o600)
    manifest['files'][archive.name]=file_sha256(archive)
    manifest['offline_image_archive_bytes']=archive.stat().st_size
    write_json(backup/'ready.json',manifest)
    print('Offline old application images archived and SHA-256 verified:',archive.stat().st_size)

if __name__ == '__main__':
    os.umask(0o077)
    parser=argparse.ArgumentParser()
    parser.add_argument('mode',choices=['prepare','start-qa','guard','guard-live','reset-qa','backup','cutover','open','rollback','archive-images'])
    mode=parser.parse_args().mode
    {'prepare':prepare,'start-qa':start_qa,'guard':guard,'guard-live':lambda:guard(ROOT),
     'reset-qa':lambda:reset_flows(QA_SCHEMA),'backup':final_backup,'cutover':cutover,
     'open':open_ingress,'rollback':rollback,'archive-images':archive_images}[mode]()
