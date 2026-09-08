#!/usr/bin/env python3
"""Remove the old model-name constant from the newer deployment guard only."""
import shutil
from datetime import datetime, timezone
from pathlib import Path

path = Path('/home/whtc/workspace/projects/road-agent-dgx/deploy/dgx/release_ops.py')
old = "assert env['ROADAGENT_MODEL_NAME']=='qwen3.6-35b-a3b-nvfp4'"
new = "assert env['ROADAGENT_MODEL_NAME']==values(source/'deploy/dgx/.env')['ROADAGENT_MODEL_NAME']"
if path.exists():
    text = path.read_text()
    if old in text:
        assert text.count(old) == 1
        backup = Path('/home/whtc/workspace/backups/qwen38-config') / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        backup.mkdir(parents=True, mode=0o700)
        shutil.copy2(path, backup / 'release_ops.py')
        path.write_text(text.replace(old, new))
        print('Updated the model-name guard only; backup:', backup)
    else:
        assert new in text, 'guard changed; inspect before updating'
        print('Deployment model guard is already configuration-driven')
