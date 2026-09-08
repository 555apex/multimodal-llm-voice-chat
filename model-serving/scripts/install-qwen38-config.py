#!/usr/bin/env python3
"""Apply only the model configuration delta to a potentially newer DGX release."""
import argparse
import hashlib
import json
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--stage', type=Path, required=True)
args = parser.parse_args()
root = Path('/home/whtc/workspace/projects')
backup = Path('/home/whtc/workspace/backups/qwen38-config') / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
backup.mkdir(parents=True, mode=0o700)
changes = []


def save(path, data):
    relative = path.relative_to(root)
    old = path.read_bytes() if path.exists() else None
    if old == data:
        return
    if old is not None:
        saved = backup / relative
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, saved)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    changes.append({'path': str(relative), 'before_sha256': hashlib.sha256(old).hexdigest() if old else None,
                    'after_sha256': hashlib.sha256(data).hexdigest()})


def replace(path, pairs):
    text = path.read_text(encoding='utf-8')
    for old, new in pairs:
        if old in text:
            if text.count(old) != 1:
                raise RuntimeError(f'ambiguous update in {path}')
            text = text.replace(old, new)
        elif new not in text:
            raise RuntimeError(f'expected model setting not found in {path}')
    save(path, text.encode('utf-8'))


for project in ('model-serving', 'road-agent-dgx'):
    env = root / project / ('.env' if project == 'model-serving' else 'deploy/dgx/.env')
    saved = backup / project / 'runtime.env'
    saved.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(env, saved)
    saved.chmod(0o600)

for name in ['configs/models.yaml', 'docker/compose.yaml', '.env.example',
             'scripts/download_snapshot.py', 'scripts/download-models', 'scripts/healthcheck',
             'scripts/model-stack', 'scripts/model_control.py', 'scripts/sync-webui-model.py',
             'scripts/observe-model.py', 'tests/test_model_control.py', 'tests/smoke.py']:
    source = args.stage / 'model-serving' / name
    target = root / 'model-serving' / name
    save(target, source.read_bytes().replace(b'\r\n', b'\n'))
    if name.startswith('scripts/'):
        target.chmod(0o755)

road = root / 'road-agent-dgx/deploy/dgx'
for name in ['compose.yaml', 'compose.public.yaml']:
    replace(road / name, [
        ('ROADAGENT_MODEL_ENDPOINT: http://qwen:8000/v1/chat/completions',
         'ROADAGENT_MODEL_ENDPOINT: ${ROADAGENT_MODEL_ENDPOINT:-http://qwen:8000/v1/chat/completions}'),
        ('ROADAGENT_MODEL_NAME: qwen3.6-35b-a3b-nvfp4',
         'ROADAGENT_MODEL_NAME: ${ROADAGENT_MODEL_NAME:?Set ROADAGENT_MODEL_NAME in deploy/dgx/.env}')])
replace(road / '.env.example', [('ROADAGENT_MODEL_NAME=qwen3.6-35b-a3b-nvfp4',
                               'ROADAGENT_MODEL_NAME=qwen3.8-27b-fp8')])
replace(road / 'dgx-stack', [
    ('QWEN_HEALTH_URL="${QWEN_HEALTH_URL:-http://100.119.145.78:8001}"',
     'QWEN_HEALTH_URL="${QWEN_HEALTH_URL:-http://127.0.0.1:8001}"'),
    ('  [[ -f "$MODEL_ROOT/NVIDIA--Qwen3.6-35B-A3B--NVFP4/.deployment-manifest.json" ]] || {\n'
     '    echo "validated Qwen snapshot is missing" >&2\n    exit 1\n  }',
     '  "$MODEL_STACK_DIR/scripts/model-stack" verify daily --quick'),
    ('case "${1:-status}" in\n  preflight)',
     'case "${1:-status}" in\n  model-reload)\n    python3 "$ROOT/model-reload.py" "${@:2}"\n    ;;\n  preflight)'),
    ('[preflight|download-models|build|up|status|smoke|',
     '[model-reload|preflight|download-models|build|up|status|smoke|')])
if (road / 'release_ops.py').exists():
    replace(road / 'release_ops.py', [
        ("assert env['ROADAGENT_MODEL_NAME']=='qwen3.6-35b-a3b-nvfp4'",
         "assert env['ROADAGENT_MODEL_NAME']==values(source/'deploy/dgx/.env')['ROADAGENT_MODEL_NAME']")])
smoke = road / 'smoke.py'
if hashlib.sha256(smoke.read_bytes()).hexdigest() not in {
        'bc870e5be1bee275151a12558466d245a473071bf80110517eedc6403b812336',
        hashlib.sha256((args.stage / 'multimodal-llm-voice-chat-dgx/deploy/dgx/smoke.py').read_bytes()).hexdigest()}:
    raise RuntimeError('remote smoke.py changed; review before replacing')
for name in ['smoke.py', 'model-reload.py']:
    save(road / name, (args.stage / 'multimodal-llm-voice-chat-dgx/deploy/dgx' / name).read_bytes())
    (road / name).chmod(0o755)
for script in [road / 'dgx-stack', root / 'model-serving/scripts/model-stack',
               root / 'model-serving/scripts/download-models']:
    subprocess.run(['bash', '-n', str(script)], check=True)
(backup / 'changes.json').write_text(json.dumps(changes, indent=2))
print(json.dumps({'backup': str(backup), 'updated_paths': [item['path'] for item in changes],
                  'runtime_model_not_switched': True}, indent=2))
