#!/usr/bin/env python3
"""Exercise the test driver at its Gradle process boundary."""

import importlib.util
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SCRIPTS = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('verification_cache', SCRIPTS / 'verification_cache.py')
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class VerificationCacheTest(unittest.TestCase):
    def test_driver_cache_switches(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            scripts = root / 'agent-scripts'
            shutil.copytree(SCRIPTS, scripts)
            gradle = root / 'gradlew'
            gradle.write_text('#!/bin/sh\nprintf "%s\\n" "$@" >> args\n')
            gradle.chmod(0o755)
            temporary_outputs = root / 'tmp'
            temporary_outputs.mkdir()
            env = dict(os.environ, SNAKT_VERIFICATION_CACHE=str(root / 'cache'),
                       SNAKT_TEST_DUMP_DIR=str(root / 'dumps'), TMPDIR=str(temporary_outputs))

            def run(*flags, environment=env):
                (root / 'args').unlink(missing_ok=True)
                result = subprocess.run(['bash', str(scripts / 'test.sh'), *flags],
                                        env=environment, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                return (root / 'args').read_text().splitlines()

            warm = run('--verify')
            self.assertIn(f'-Pformver.verificationCacheDirectory={root / "cache"}', warm)
            self.assertTrue(any(arg.startswith('-Pformver.verificationCacheReport=') for arg in warm))
            for args in (run('--verify', '--cold'),
                         run('--verify', environment=dict(env, SNAKT_VERIFICATION_CACHE='off')),
                         run()):
                self.assertFalse(any(arg.startswith('-Pformver.verificationCache') for arg in args))

    def test_prune_preserves_active_entries_and_batches_maintenance(self):
        with tempfile.TemporaryDirectory() as temporary:
            store = pathlib.Path(temporary)
            shard = store / 'ab'
            shard.mkdir()
            old, active = shard / 'old.success', shard / 'active.success'
            now = 40 * 86400
            for entry, tick in ((old, 0), (active, now - 86400)):
                entry.touch()
                os.utime(entry, (tick, tick))
            cache.prune(store, now)
            self.assertFalse(old.exists())
            self.assertTrue(active.exists())
            old.touch()
            os.utime(old, (0, 0))
            os.utime(store / '.pruned', (now, now))
            cache.prune(store, now + 1)
            self.assertTrue(old.exists())
            cache.prune(store, now + 86400)
            self.assertFalse(old.exists())


if __name__ == '__main__':
    unittest.main()
