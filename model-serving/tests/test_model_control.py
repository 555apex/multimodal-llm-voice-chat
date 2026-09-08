import hashlib
import importlib.util
import io
import json
import sqlite3
import subprocess
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import model_control as control
from download_snapshot import expected_sha, download_one


class ModelControlTest(unittest.TestCase):
    def test_webui_database_update_preserves_other_settings_and_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            db, backup = Path(directory) / 'webui.db', Path(directory) / 'before.db'
            with sqlite3.connect(db) as connection:
                connection.execute('CREATE TABLE config (key TEXT PRIMARY KEY, value TEXT)')
                connection.executemany('INSERT INTO config VALUES (?, ?)', [
                    ('ui.model_order_list', json.dumps(['old', 'gpt-oss'])),
                    ('unrelated.secret', json.dumps('preserve-this'))])
            connection.close()
            script = Path(control.__file__).with_name('sync-webui-model.py')
            subprocess.run([sys.executable, str(script), '--database', str(db),
                            '--backup', str(backup), '--old', 'old', '--new', 'new'],
                           check=True, capture_output=True)
            with sqlite3.connect(db) as connection:
                values = dict(connection.execute('SELECT key,value FROM config'))
                self.assertEqual(json.loads(values['ui.model_order_list']), ['new', 'gpt-oss'])
                self.assertEqual(json.loads(values['unrelated.secret']), 'preserve-this')
            connection.close()
            with sqlite3.connect(backup) as connection:
                old = connection.execute('SELECT value FROM config WHERE key=?',
                                         ('ui.model_order_list',)).fetchone()[0]
                self.assertEqual(json.loads(old), ['old', 'gpt-oss'])
            connection.close()

    def test_empty_metadata_file_download(self):
        with tempfile.TemporaryDirectory() as directory:
            response = io.BytesIO(b'')
            response.status = 200
            with patch('download_snapshot.urllib.request.urlopen', return_value=response):
                result = download_one('https://example.invalid', 'Qwen/test', 'fixed',
                                      Path(directory), {'rfilename': 'empty.txt', 'size': 0})
            self.assertEqual(result['size'], 0)
            self.assertTrue((Path(directory) / 'empty.txt').exists())

    def test_webui_only_changes_exact_model_ids(self):
        value = {'model_ids': ['old', 'gpt-oss'], 'pinned': 'old,gpt-oss',
                 'unrelated': 'old-other', 'enabled': True}
        updated = control.replace_model_id(value, 'old', 'new')
        self.assertEqual(updated['model_ids'], ['new', 'gpt-oss'])
        self.assertEqual(updated['pinned'], 'new,gpt-oss')
        self.assertEqual(updated['unrelated'], 'old-other')
        self.assertTrue(updated['enabled'])

    def test_dense_and_moe_flags_are_separate(self):
        new, old = control.resolve('qwen38'), control.resolve('qwen36')
        self.assertNotIn('--moe-backend', control.command(new))
        self.assertNotIn('--speculative-config', control.command(new))
        self.assertIn('--language-model-only', control.command(new))
        self.assertIn('marlin', control.command(old))
        self.assertIn('--speculative-config', control.command(old))

    def test_lfs_hash_variants(self):
        digest = 'a' * 64
        self.assertEqual(digest, expected_sha({'lfs': {'sha256': digest}}))
        self.assertEqual(digest, expected_sha({'lfs': {'oid': 'sha256:' + digest}}))
        with self.assertRaises(ValueError):
            expected_sha({'lfs': {'sha256': 'invalid'}})

    def test_env_update_preserves_unrelated_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            env = Path(directory) / '.env'
            env.write_text('# settings\nPASSWORD=never-print\nQWEN_SERVED_MODEL_NAME=old\n')
            with patch.object(control, 'ROOT', Path(directory)), patch.object(control, 'ENV_FILE', env):
                control.write_env({'QWEN_SERVED_MODEL_NAME': 'new', 'QWEN_HOST_BIND': '127.0.0.1'})
            self.assertIn('PASSWORD=never-print', env.read_text())
            self.assertIn('QWEN_SERVED_MODEL_NAME=new', env.read_text())
            self.assertNotIn('QWEN_SERVED_MODEL_NAME=old', env.read_text())

    def test_corrupted_weights_and_partial_download_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            weight = root / 'model.safetensors'
            weight.write_bytes(b'good')
            manifest = {'repo_id': 'Qwen/test', 'revision': 'fixed', 'verified': True,
                        'files': [{'name': weight.name, 'size': 4,
                                   'sha256': hashlib.sha256(b'good').hexdigest()}]}
            (root / '.deployment-manifest.json').write_text(json.dumps(manifest))
            profile = {'directory': directory, 'source': 'Qwen/test', 'revision': 'fixed',
                       'quantization': 'FP8', 'name': 'test'}
            self.assertEqual(control.verify(profile)['files'], 1)
            weight.write_bytes(b'evil')
            with self.assertRaisesRegex(RuntimeError, 'SHA-256'):
                control.verify(profile)
            weight.write_bytes(b'good')
            (root / 'unfinished.part').touch()
            with self.assertRaisesRegex(RuntimeError, 'part'):
                control.verify(profile)

    def test_smoke_payload_has_selected_model_and_no_key(self):
        path = Path(__file__).resolve().parents[2] / 'multimodal-llm-voice-chat-dgx/deploy/dgx/smoke.py'
        if not path.exists():
            path = Path(__file__).resolve().parents[2] / 'road-agent-dgx/deploy/dgx/smoke.py'
        if not path.exists():
            self.skipTest('Road Agent checkout not adjacent on this host')
        spec = importlib.util.spec_from_file_location('road_smoke', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        data = module.qwen_payload('qwen3.8-27b-fp8', structured=True)
        self.assertEqual(data['model'], 'qwen3.8-27b-fp8')
        self.assertEqual(data['chat_template_kwargs'], {'enable_thinking': False})
        self.assertEqual(data['response_format'], {'type': 'json_object'})
        self.assertNotIn('api_key', data)


if __name__ == '__main__':
    unittest.main()
