#!/usr/bin/env python3
"""Update only model-ID settings in Open WebUI's key/value config table."""
import argparse
import json
from pathlib import Path
import sqlite3


def replace(value, old, new):
    if isinstance(value, dict):
        return {key: replace(child, old, new) for key, child in value.items()}
    if isinstance(value, list):
        return [replace(child, old, new) for child in value]
    if isinstance(value, str):
        return ','.join(new if part == old else part for part in value.split(','))
    return value


parser = argparse.ArgumentParser()
parser.add_argument('--database', type=Path, required=True)
parser.add_argument('--backup', type=Path, required=True)
parser.add_argument('--old', required=True)
parser.add_argument('--new', required=True)
args = parser.parse_args()
with sqlite3.connect(args.database, timeout=30) as connection:
    columns = {row[1] for row in connection.execute('PRAGMA table_info(config)')}
    if not {'key', 'value'}.issubset(columns):
        raise RuntimeError('unsupported Open WebUI configuration schema')
    if not args.backup.exists():
        with sqlite3.connect(args.backup) as saved:
            connection.backup(saved)
        args.backup.chmod(0o600)
    changed = []
    for key in ['openai.api_configs', 'ui.default_models', 'ui.default_pinned_models',
                'ui.model_order_list', 'task.model.default', 'task.model.external']:
        row = connection.execute('SELECT value FROM config WHERE key=?', (key,)).fetchone()
        if row:
            value = json.loads(row[0])
            updated = replace(value, args.old, args.new)
            if value != updated:
                connection.execute('UPDATE config SET value=? WHERE key=?', (json.dumps(updated), key))
                changed.append(key)
print(json.dumps({'model': args.new, 'updated_keys': changed}))
