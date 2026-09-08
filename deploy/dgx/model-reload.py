#!/usr/bin/env python3
"""Reload model settings on running Backends, preserving their exact images."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import tempfile
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / 'deploy/dgx'
ENV_FILE = DEPLOY / '.env'
SERVICES = {'backend': 'road-agent-dgx-backend',
            'public-backend': 'road-agent-dgx-public-backend'}
PRESERVED = ['road-agent-dgx-frontend', 'road-agent-dgx-public-frontend',
             'road-agent-dgx-speech', 'model-serving-qwen']


def inspect(name):
    result = subprocess.run(['docker', 'inspect', name], capture_output=True, text=True)
    return json.loads(result.stdout)[0] if result.returncode == 0 else None


def snapshot(names):
    result = {}
    for name in names:
        item = inspect(name)
        if item:
            result[name] = {'id': item['Id'], 'image': item['Image'],
                'started': item['State']['StartedAt'], 'restarts': item['RestartCount'],
                'running': item['State']['Running'], 'oom': item['State'].get('OOMKilled'),
                'health': item['State'].get('Health', {}).get('Status')}
    return result


def environment():
    result = {}
    for line in ENV_FILE.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip().strip('"').strip("'")
    return result


def save_env(updates):
    lines, pending = [], dict(updates)
    for line in ENV_FILE.read_text(encoding='utf-8').splitlines():
        key = line.split('=', 1)[0].strip()
        if key in updates:
            if key in pending:
                lines.append(f'{key}={pending.pop(key)}')
        else:
            lines.append(line)
    lines.extend(f'{key}={value}' for key, value in pending.items())
    fd, temporary = tempfile.mkstemp(prefix='.model-env-', dir=DEPLOY)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write('\n'.join(lines) + '\n')
    os.chmod(temporary, 0o600)
    os.replace(temporary, ENV_FILE)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', help='served model ID; defaults to deploy/dgx/.env')
    args = parser.parse_args()
    model = args.model or environment().get('ROADAGENT_MODEL_NAME')
    with urllib.request.urlopen('http://127.0.0.1:8001/v1/models', timeout=15) as response:
        ids = [item['id'] for item in json.load(response)['data']]
    if not model or model not in ids:
        raise RuntimeError(f'configured model {model!r} is not served: {ids}')
    active = {}
    for service, name in SERVICES.items():
        item = inspect(name)
        if item and item['State']['Running']:
            active[service] = item
    if not active:
        raise RuntimeError('No running Backend found; use dgx-stack up for initial startup')
    before = snapshot([*SERVICES.values(), *PRESERVED])
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = Path('/home/whtc/workspace/backups/road-agent-model') / stamp
    backup.mkdir(parents=True, mode=0o700)
    (backup / 'roadagent.env').write_bytes(ENV_FILE.read_bytes())
    (backup / 'roadagent.env').chmod(0o600)
    (backup / 'before.json').write_text(json.dumps(before, indent=2))
    save_env({'ROADAGENT_MODEL_NAME': model,
              'ROADAGENT_MODEL_ENDPOINT': 'http://qwen:8000/v1/chat/completions',
              'ROADAGENT_MODEL_AUTH_ENABLED': 'false',
              'ROADAGENT_MODEL_ENABLE_THINKING': 'false'})
    # Pin the running immutable image IDs, even if the mutable :local tag moved.
    override = {'services': {service: {'image': item['Image']} for service, item in active.items()}}
    with tempfile.TemporaryDirectory(prefix='roadagent-model-') as temporary:
        path = Path(temporary) / 'images.json'
        path.write_text(json.dumps(override))
        cmd = ['docker', 'compose', '--env-file', str(ENV_FILE), '--project-directory',
               str(ROOT), '-f', str(DEPLOY / 'compose.yaml')]
        if 'public-backend' in active:
            cmd += ['-f', str(DEPLOY / 'compose.public.yaml')]
        cmd += ['-f', str(path), '--profile', 'public', 'up', '-d', '--no-deps',
                '--force-recreate', '--pull', 'never', '--no-build', *active]
        subprocess.run(cmd, check=True)
    deadline = time.monotonic() + 600
    while time.monotonic() < deadline:
        states = [inspect(SERVICES[service]) for service in active]
        if any(not state or not state['State']['Running'] for state in states):
            raise RuntimeError(f'Backend failed; environment backup: {backup}')
        if all(state['State'].get('Health', {}).get('Status') == 'healthy' for state in states):
            break
        time.sleep(5)
    else:
        raise RuntimeError(f'Backend readiness timed out; environment backup: {backup}')
    for service, old in active.items():
        new = inspect(SERVICES[service])
        if new['Image'] != old['Image']:
            raise RuntimeError('Backend image changed unexpectedly')
        env = dict(item.split('=', 1) for item in new['Config']['Env'] if '=' in item)
        if (env.get('ROADAGENT_MODEL_NAME') != model or
                env.get('ROADAGENT_MODEL_AUTH_ENABLED') != 'false' or
                env.get('ROADAGENT_MODEL_ENABLE_THINKING') != 'false'):
            raise RuntimeError('Backend did not load the requested model settings')
    # Nginx may cache the old Backend address; reload config without restarting.
    for name in ('road-agent-dgx-frontend', 'road-agent-dgx-public-frontend'):
        state = inspect(name)
        if state and state['State']['Running']:
            subprocess.run(['docker', 'exec', name, 'nginx', '-t'], check=True)
            subprocess.run(['docker', 'exec', name, 'nginx', '-s', 'reload'], check=True)
    after = snapshot([*SERVICES.values(), *PRESERVED])
    for name in PRESERVED:
        keys = ['id', 'image', 'started', 'restarts', 'running', 'oom']
        if any((before.get(name) or {}).get(key) != (after.get(name) or {}).get(key) for key in keys):
            raise RuntimeError(f'preserved container changed: {name}')
    (backup / 'after.json').write_text(json.dumps(after, indent=2))
    print(json.dumps({'model': model, 'reloaded': list(active), 'backup': str(backup),
                      'preserved_containers_unchanged': True}, indent=2))


if __name__ == '__main__':
    main()
