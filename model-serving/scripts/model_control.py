#!/usr/bin/env python3
"""Pinned Qwen download, offline verification, candidate validation and switching.

Requires python3-yaml (already installed on DGX). No application/database writes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

import yaml
from download_snapshot import sha256_file

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / '.env'
ARTIFACTS = ROOT / 'artifacts' / 'model-control'
CANDIDATE = 'model-serving-qwen-candidate'


def read_env(path=ENV_FILE):
    result = {}
    if path.exists():
        for line in path.read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                result[key.strip()] = value.strip().strip('"').strip("'")
    return result


def write_env(updates):
    """Atomically edit only named values; retain image pins, comments and secrets."""
    lines = ENV_FILE.read_text(encoding='utf-8').splitlines() if ENV_FILE.exists() else []
    remaining = dict(updates)
    output = []
    for line in lines:
        key = line.split('=', 1)[0].strip() if '=' in line else ''
        if key in updates:
            if key in remaining:
                output.append(f'{key}={remaining.pop(key)}')
        else:
            output.append(line)
    output.extend(f'{key}={value}' for key, value in remaining.items())
    fd, name = tempfile.mkstemp(prefix='.env.', dir=ROOT)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write('\n'.join(output) + '\n')
    os.chmod(name, 0o600)
    os.replace(name, ENV_FILE)


def catalog():
    return yaml.safe_load((ROOT / 'configs/models.yaml').read_text(encoding='utf-8'))


def resolve(name='daily'):
    env = read_env()
    # Missing old deployment keys mean the previously deployed Qwen3.6, not an
    # unvalidated Qwen3.8 activation. switch qwen38 explicitly promotes it.
    if name == 'daily':
        name = env.get('QWEN_SERVED_MODEL_NAME', 'qwen3.6-35b-a3b-nvfp4')
    data = catalog()
    for key, profile in data['models'].items():
        if name == key or name in profile.get('aliases', []):
            if 'vllm_args' not in profile:
                raise ValueError(f'{name} is not a Qwen profile')
            directory = profile['local_dir']
            if env.get('QWEN_SERVED_MODEL_NAME') == key:
                directory = env.get('QWEN_MODEL_DIR', directory)
            return dict(profile, name=key, directory=directory,
                        image=env.get('VLLM_IMAGE', data['runtime']['image']))
    raise ValueError(f'unknown model profile: {name}')


def command(profile):
    return ['/models/qwen', '--served-model-name', profile['name'],
            '--host', '0.0.0.0', '--port', '8000', *profile['vllm_args']]


def run(args, *, capture=False, check=True):
    return subprocess.run(args, text=True, check=check,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None)


def inspect(name):
    result = run(['docker', 'inspect', name], capture=True, check=False)
    return json.loads(result.stdout)[0] if result.returncode == 0 else None


def verify(profile, *, full=True):
    directory = Path(profile['directory']).resolve()
    manifest_path = directory / '.deployment-manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if (manifest.get('revision') != profile['revision'] or
            manifest.get('repo_id') != profile['source'] or not manifest.get('verified')):
        raise RuntimeError('snapshot source/revision/verification does not match registry')
    if list(directory.rglob('*.part')):
        raise RuntimeError('incomplete .part files remain in model directory')
    files = manifest.get('files', [])
    if not files or not any(item['name'].endswith('.safetensors') for item in files):
        raise RuntimeError('manifest has no model weights')
    for item in files:
        path = (directory / item['name']).resolve()
        if not path.is_relative_to(directory) or not path.is_file():
            raise RuntimeError(f'invalid snapshot path: {item["name"]}')
        if path.stat().st_size != item['size']:
            raise RuntimeError(f'file size mismatch: {item["name"]}')
        digest = item.get('sha256')
        if profile['quantization'] == 'FP8' and path.suffix == '.safetensors' and not digest:
            raise RuntimeError('weight hash missing; rerun the corrected download script')
        if full and digest and sha256_file(path) != digest:
            raise RuntimeError(f'SHA-256 mismatch: {item["name"]}')
    return {'model': profile['name'], 'revision': profile['revision'],
            'files': len(files), 'bytes': sum(item['size'] for item in files),
            'manifest_sha256': sha256_file(manifest_path), 'full_hash_check': full}


def request(url, payload=None):
    body = None if payload is None else json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(url, data=body, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=600) as response:
        body = response.read()
        return json.loads(body) if body else {}


def smoke(profile, base, iterations=3):
    started = time.monotonic()
    ids = [item['id'] for item in request(base + '/v1/models')['data']]
    if profile['name'] not in ids:
        raise RuntimeError(f'model ID mismatch: {ids}')
    payload = {'model': profile['name'], 'messages': [{'role': 'user',
        'content': 'Return JSON only: {"answer":204}'}], 'max_tokens': 64,
        'temperature': 0, 'chat_template_kwargs': {'enable_thinking': False}}

    def one(structured=True):
        data = dict(payload)
        if structured:
            data['response_format'] = {'type': 'json_object'}
        result = request(base + '/v1/chat/completions', data)
        message = result['choices'][0]['message']
        content = message.get('content') or ''
        if '<think>' in content or message.get('reasoning') or message.get('reasoning_content'):
            raise RuntimeError('unexpected thinking output in non-thinking request')
        if '204' not in content or (structured and str(json.loads(content)['answer']) != '204'):
            raise RuntimeError(f'unexpected generation: {content}')
        return result.get('usage', {})

    one(False)
    for index in range(iterations):
        one()
        if (index + 1) % 10 == 0:
            print(f'structured requests: {index + 1}/{iterations}', flush=True)
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(lambda _: one(), range(2)))
    stream_payload = dict(payload, stream=True)
    req = urllib.request.Request(base + '/v1/chat/completions',
        data=json.dumps(stream_payload).encode(), headers={'Content-Type': 'application/json'})
    parts, done = [], False
    with urllib.request.urlopen(req, timeout=600) as response:
        for line in response:
            text = line.decode().strip()
            if not text.startswith('data:'):
                continue
            text = text[5:].strip()
            if text == '[DONE]':
                done = True
                break
            for choice in json.loads(text).get('choices', []):
                delta = choice.get('delta', {})
                if delta.get('reasoning') or delta.get('reasoning_content'):
                    raise RuntimeError('unexpected streamed reasoning')
                parts.append(delta.get('content') or '')
    if not done or '204' not in ''.join(parts) or '<think>' in ''.join(parts):
        raise RuntimeError('invalid/incomplete non-thinking SSE response')
    return {'model': profile['name'], 'ordinary': True, 'structured_iterations': iterations,
            'concurrency': 2, 'sse': True, 'seconds': round(time.monotonic() - started, 2)}


def wait_healthy(name, base):
    deadline = time.monotonic() + 1800
    while time.monotonic() < deadline:
        state = inspect(name)
        if not state or not state['State']['Running']:
            run(['docker', 'logs', '--tail', '100', name], check=False)
            raise RuntimeError(f'{name} exited during startup')
        try:
            request(base + '/health')
            return
        except Exception:
            time.sleep(10)
    raise RuntimeError(f'{name} readiness timed out; inspect docker logs')


def fingerprint(profile):
    values = dict(verify(profile, full=False), image=profile['image'], args=command(profile),
                  directory=profile['directory'])
    return hashlib.sha256(json.dumps(values, sort_keys=True).encode()).hexdigest()


def receipt_path(profile):
    return ARTIFACTS / (profile['name'] + '-candidate.json')


def stop_candidate():
    if inspect(CANDIDATE):
        run(['docker', 'stop', '--time', '120', CANDIDATE])
        run(['docker', 'rm', CANDIDATE])


def candidate(profile):
    print(json.dumps(verify(profile), indent=2), flush=True)
    for name in ('model-serving-qwen', 'model-serving-gpt-oss', CANDIDATE):
        state = inspect(name)
        if state and state['State']['Running']:
            raise RuntimeError(f'{name} is running; stop heavy model explicitly before candidate')
    stop_candidate()
    if run(['docker', 'network', 'inspect', 'model-serving-candidate'], capture=True,
           check=False).returncode:
        run(['docker', 'network', 'create', 'model-serving-candidate'])
    run(['docker', 'run', '-d', '--name', CANDIDATE, '--restart=no', '--gpus', 'all',
         '--ipc', 'host', '--network', 'model-serving-candidate',
         '--security-opt', 'no-new-privileges:true', '--ulimit', 'memlock=-1',
         '--ulimit', 'stack=67108864', '-p', '127.0.0.1:18001:8000',
         '-v', profile['directory'] + ':/models/qwen:ro',
         '-e', 'HF_HUB_OFFLINE=1', '-e', 'TRANSFORMERS_OFFLINE=1',
         '-e', 'HF_HUB_DISABLE_TELEMETRY=1', '--entrypoint', 'vllm',
         profile['image'], 'serve', *command(profile)])
    wait_healthy(CANDIDATE, 'http://127.0.0.1:18001')
    report = smoke(profile, 'http://127.0.0.1:18001')
    report.update(fingerprint=fingerprint(profile), verified_at=datetime.now(timezone.utc).isoformat())
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    receipt_path(profile).write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2), flush=True)


def compose_qwen(profile, *args):
    # Compose replaces command arrays, selecting the matching dense/MoE flags.
    env = dict(os.environ, QWEN_MODEL_DIR=profile['directory'],
               QWEN_SERVED_MODEL_NAME=profile['name'], QWEN_HOST_BIND='127.0.0.1')
    with tempfile.TemporaryDirectory(prefix='qwen-compose-') as tmp:
        override = Path(tmp) / 'profile.json'
        override.write_text(json.dumps({'services': {'qwen': {'command': command(profile)}}}))
        cmd = ['docker', 'compose', '--project-directory', str(ROOT)]
        if ENV_FILE.exists():
            cmd += ['--env-file', str(ENV_FILE)]
        cmd += ['-f', str(ROOT / 'docker/compose.yaml'), '-f', str(override), '--profile', 'daily', *args]
        subprocess.run(cmd, env=env, check=True)


def replace_model_id(value, old, new):
    if isinstance(value, dict):
        return {key: replace_model_id(child, old, new) for key, child in value.items()}
    if isinstance(value, list):
        return [replace_model_id(child, old, new) for child in value]
    if isinstance(value, str):
        return ','.join(new if part == old else part for part in value.split(','))
    return value


def sync_webui(old, new, backup):
    """Persistent settings override env vars in Open WebUI; update only model IDs."""
    database = ROOT / 'artifacts/open-webui/webui.db'
    if not database.exists() or old == new:
        return
    if not os.access(database, os.W_OK) or not os.access(database.parent, os.W_OK):
        # Open WebUI owns these files as root. Use its existing pinned image,
        # with no network and only the configuration directory mounted writable.
        webui = inspect('model-serving-open-webui')
        if not webui:
            raise RuntimeError('Open WebUI container/image not available for config sync')
        run(['docker', 'run', '--rm', '--network', 'none', '--user', '0',
             '--entrypoint', 'python3',
             '-v', str(database.parent) + ':/data:rw',
             '-v', str(backup) + ':/backup:rw',
             '-v', str(ROOT / 'scripts/sync-webui-model.py') + ':/sync.py:ro',
             webui['Image'], '/sync.py', '--database', '/data/webui.db',
             '--backup', '/backup/open-webui.db', '--old', old, '--new', new])
        return
    with sqlite3.connect(database) as connection:
        columns = {row[1] for row in connection.execute('PRAGMA table_info(config)')}
        if not {'key', 'value'}.issubset(columns):
            raise RuntimeError('unsupported Open WebUI config schema; review before model-ID update')
        saved_path = backup / 'open-webui.db'
        with sqlite3.connect(saved_path) as saved:
            connection.backup(saved)
        saved_path.chmod(0o600)
        changed = []
        for key in ['openai.api_configs', 'ui.default_models', 'ui.default_pinned_models',
                    'ui.model_order_list', 'task.model.default', 'task.model.external']:
            row = connection.execute('SELECT value FROM config WHERE key=?', (key,)).fetchone()
            if not row:
                continue
            value = json.loads(row[0])
            updated = replace_model_id(value, old, new)
            if updated != value:
                connection.execute('UPDATE config SET value=? WHERE key=?',
                                   (json.dumps(updated), key))
                changed.append(key)
    print('Open WebUI model settings updated: ' + ', '.join(changed), flush=True)


def switch(profile):
    print(json.dumps(verify(profile), indent=2), flush=True)
    if profile['quantization'] == 'FP8':
        receipt = json.loads(receipt_path(profile).read_text())
        if receipt['fingerprint'] != fingerprint(profile):
            raise RuntimeError('candidate validation is stale; run candidate again')
    gpt = inspect('model-serving-gpt-oss')
    if gpt and gpt['State']['Running']:
        raise RuntimeError('GPT-OSS is running; stop it explicitly before switching Qwen')
    previous = resolve()
    timestamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = ARTIFACTS / ('switch-' + timestamp)
    backup.mkdir(parents=True, mode=0o700)
    if ENV_FILE.exists():
        (backup / 'model-serving.env').write_bytes(ENV_FILE.read_bytes())
        (backup / 'model-serving.env').chmod(0o600)
    before = inspect('model-serving-qwen')
    (backup / 'qwen-before.json').write_text(json.dumps(before, indent=2))
    stop_candidate()
    if before and before['State']['Running']:
        run(['docker', 'stop', '--time', '120', 'model-serving-qwen'])
    try:
        compose_qwen(profile, 'up', '-d', '--no-deps', '--force-recreate', 'qwen')
        wait_healthy('model-serving-qwen', 'http://127.0.0.1:8001')
        report = smoke(profile, 'http://127.0.0.1:8001')
    except Exception:
        run(['docker', 'stop', '--time', '120', 'model-serving-qwen'], check=False)
        if previous['name'] != profile['name']:
            print('New model failed; restoring previous Qwen profile.', flush=True)
            compose_qwen(previous, 'up', '-d', '--no-deps', '--force-recreate', 'qwen')
            wait_healthy('model-serving-qwen', 'http://127.0.0.1:8001')
        raise
    write_env({'QWEN_MODEL_DIR': profile['directory'], 'QWEN_SERVED_MODEL_NAME': profile['name'],
               'QWEN_HOST_BIND': '127.0.0.1'})
    try:
        sync_webui(previous['name'], profile['name'], backup)
    except (OSError, sqlite3.Error, subprocess.CalledProcessError) as error:
        # The model is already healthy. A UI configuration permission issue
        # must not misreport the inference cutover as failed or restart Qwen.
        report['webui_sync_pending'] = type(error).__name__
        print('Open WebUI sync pending; retry model-stack webui-sync.', flush=True)
    webui = inspect('model-serving-open-webui')
    if webui and webui['State']['Running']:
        compose_qwen(profile, 'up', '-d', '--no-deps', 'open-webui')
    print(json.dumps(dict(report, backup=str(backup)), indent=2), flush=True)
    print('Next: set ROADAGENT_MODEL_NAME and run dgx-stack model-reload.', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['download', 'verify', 'candidate', 'candidate-stop',
                                         'switch', 'start', 'smoke', 'config', 'webui-sync'])
    parser.add_argument('model', nargs='?', default='daily')
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--quick', action='store_true')
    parser.add_argument('--base', default='http://127.0.0.1:8001')
    parser.add_argument('--iterations', type=int, default=3)
    args = parser.parse_args()
    if args.action == 'candidate-stop':
        stop_candidate()
        return
    profile = resolve(args.model)
    if args.action == 'config':
        print(json.dumps(profile, indent=2))
    elif args.action == 'webui-sync':
        backup = ARTIFACTS / ('webui-sync-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
        backup.mkdir(parents=True, mode=0o700)
        for name, entry in catalog()['models'].items():
            if 'vllm_args' in entry and name != profile['name']:
                sync_webui(name, profile['name'], backup)
    elif args.action == 'download':
        endpoint = os.getenv('HF_ENDPOINT', read_env().get('HF_ENDPOINT', 'https://hf-mirror.com'))
        cmd = [sys.executable, str(ROOT / 'scripts/download_snapshot.py'), '--repo',
               profile['source'], '--revision', profile['revision'], '--target',
               profile['directory'], '--endpoint', endpoint, '--workers', '3']
        run(cmd + (['--dry-run'] if args.dry_run else []))
    elif args.action == 'verify':
        print(json.dumps(verify(profile, full=not args.quick), indent=2))
    elif args.action == 'candidate':
        candidate(profile)
    elif args.action == 'smoke':
        print(json.dumps(smoke(profile, args.base.rstrip('/'), args.iterations), indent=2))
    elif args.action == 'start':
        state = inspect('model-serving-qwen')
        if state and state['State']['Running']:
            print(json.dumps(smoke(profile, 'http://127.0.0.1:8001'), indent=2))
        else:
            switch(profile)
    else:
        switch(profile)


if __name__ == '__main__':
    main()
