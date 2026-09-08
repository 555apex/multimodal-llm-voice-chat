#!/usr/bin/env python3
"""Bounded DGX coexistence observation; records state without restarting services."""
import argparse
import json
from pathlib import Path
import subprocess
import time
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument('--seconds', type=int, default=1800)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
names = ['model-serving-qwen', 'road-agent-dgx-backend', 'road-agent-dgx-public-backend',
         'road-agent-dgx-frontend', 'road-agent-dgx-public-frontend', 'road-agent-dgx-speech']
started = time.monotonic()
baseline = None
samples, failures = [], []
args.output.parent.mkdir(parents=True, exist_ok=True)
while True:
    items = json.loads(subprocess.check_output(['docker', 'inspect', *names], text=True))
    states = {}
    for item in items:
        states[item['Name'].lstrip('/')] = {
            'id': item['Id'], 'started': item['State']['StartedAt'],
            'running': item['State']['Running'], 'health': item['State'].get('Health', {}).get('Status'),
            'restarts': item['RestartCount'], 'oom': item['State'].get('OOMKilled', False)}
    if baseline is None:
        baseline = states
    for name, state in states.items():
        if not state['running'] or state['health'] != 'healthy' or state['oom']:
            failures.append({'second': round(time.monotonic() - started), 'container': name, 'state': state})
        if any(state[key] != baseline[name][key] for key in ['id', 'started', 'restarts']):
            failures.append({'second': round(time.monotonic() - started), 'container': name, 'changed': True})
    memory = {}
    for line in Path('/proc/meminfo').read_text().splitlines():
        key, rest = line.split(':', 1)
        if key in ('MemTotal', 'MemAvailable', 'SwapFree', 'SwapTotal'):
            memory[key + '_KiB'] = int(rest.split()[0])
    elapsed = time.monotonic() - started
    sample = {'utc': datetime.now(timezone.utc).isoformat(), 'elapsed_seconds': round(elapsed, 2),
              'memory': memory, 'containers': states}
    samples.append(sample)
    report = {'required_seconds': args.seconds, 'elapsed_seconds': round(elapsed, 2),
              'complete': elapsed >= args.seconds, 'passed': elapsed >= args.seconds and not failures,
              'samples': samples, 'failures': failures}
    args.output.write_text(json.dumps(report, indent=2))
    print(json.dumps({'elapsed_seconds': round(elapsed), 'available_GiB': round(memory['MemAvailable_KiB'] / 1048576, 2),
                      'failures': len(failures)}), flush=True)
    if elapsed >= args.seconds:
        break
    time.sleep(min(30, args.seconds - elapsed))
raise SystemExit(0 if report['passed'] else 1)
