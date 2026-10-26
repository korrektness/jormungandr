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
        with tempfile.TemporaryDirectory() as temporary, tempfile.TemporaryDirectory() as artifacts:
            root = pathlib.Path(temporary)
            scripts = root / 'agent-scripts'
            shutil.copytree(SCRIPTS, scripts)
            gradle = root / 'gradlew'
            gradle.write_text('''#!/bin/sh
printf "%s\\n" "$@" >> args
printf "timings=%s\\n" "$SNAKT_VERIFICATION_TIMINGS" >> args
case "$1" in
    :formver.compiler-plugin:locality:*) module=formver.compiler-plugin/locality ;;
    *) module=formver.compiler-plugin ;;
esac
results="$module/build/test-results/test"
mkdir -p "$results"
find "$TMPDIR" -maxdepth 1 -type f -name 'tmp.*' -exec touch -d @1 {} +
printf '<testsuite><testcase classname="Fixture" name="passes"/></testsuite>' > "$results/TEST-Fixture.xml"
touch -d @2 "$results/TEST-Fixture.xml"
''')
            gradle.chmod(0o755)
            temporary_outputs = root / 'tmp'
            temporary_outputs.mkdir()
            # The fake gradlew writes no runner spec, so the driver runs Gradle's test tasks.
            env = dict(os.environ, SNAKT_TEST_RUNNER='off', SNAKT_VERIFICATION_CACHE=str(root / 'cache'),
                       SNAKT_TEST_DUMP_DIR=str(root / 'dumps'), TMPDIR=str(temporary_outputs),
                       SNAKT_VERIFICATION_TIMINGS=str(root / 'timings'))

            def run(*flags, environment=env, driver='test.sh'):
                (root / 'args').unlink(missing_ok=True)
                result = subprocess.run(['bash', str(scripts / driver), *flags],
                                        env=environment, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                return (root / 'args').read_text().splitlines()

            warm = run('--verify')
            self.assertIn(f'-Pformver.verificationCacheDirectory={root / "cache"}', warm)
            self.assertTrue(any(arg.startswith('-Pformver.verificationCacheReport=') for arg in warm))
            refreshed = run('--verify', '--timings', str(root / 'explicit-timings'), '--refresh-cache')
            self.assertIn('-Pformver.verificationCacheRefresh=true', refreshed)
            self.assertIn(f'timings={root / "explicit-timings"}', refreshed)
            for args in (run('--verify', '--cold'),
                         run('--verify', environment=dict(env, SNAKT_VERIFICATION_CACHE='off')),
                         run()):
                self.assertFalse(any(arg.startswith('-Pformver.verificationCache') for arg in args))

            for check in ('check-testdata.sh', 'check-gradle-consumer.sh'):
                (scripts / check).write_text('#!/bin/sh\nexit 0\n')
            binaries = root / 'bin'
            binaries.mkdir()
            git = binaries / 'git'
            diff = root / 'diff'
            git.write_text('#!/bin/sh\ncase "$1" in\n    merge-base) echo base-sha ;;\n'
                           f'    diff) cat {diff} ;;\n    *) echo candidate-sha ;;\nesac\n')
            git.chmod(0o755)
            golden = 'formver.compiler-plugin/testData/diagnostics/'
            diff.write_text(f'{golden}nested/non-local-returns.kt\n{golden}nested/non-local-returns.viper.txt\n'
                            f'{golden}assign_local.fir.diag.txt\n')
            for tool in ('java', 'z3'):
                binary = binaries / tool
                binary.write_text('#!/bin/sh\necho fixture-version\n')
                binary.chmod(0o755)
            gate_env = dict(env, PATH=str(binaries) + os.pathsep + env['PATH'],
                            SNAKT_GATE_ARTIFACTS=artifacts)
            gate = run(driver='gate-test.sh', environment=gate_env)
            verified = gate[gate.index(':formver.compiler-plugin:test') + 1:]
            self.assertEqual(['*.testAssign_local', '*.testNon_local_returns'],
                             [verified[i + 1] for i, arg in enumerate(verified) if arg == '--tests'])
            self.assertIn('-Pformver.verificationColdSample=candidate-sha', gate)
            self.assertIn(f'-Pformver.verificationCacheDirectory={root / "cache"}', gate)
            reports = list(pathlib.Path(artifacts).glob('*/gate.total.json'))
            self.assertEqual(1, len(reports))
            self.assertIn(f'-Pformver.verificationCacheReport={reports[0].parent / "cache"}', gate)
            cold_gate = run(driver='gate-test.sh', environment=dict(gate_env, SNAKT_VERIFICATION_CACHE='off'))
            self.assertFalse(any(arg.startswith('-Pformver.verification') for arg in cold_gate))
            self.assertIn(':formver.compiler-plugin:test', cold_gate)

            diff.write_text('')
            unchanged = run(driver='gate-test.sh', environment=gate_env)
            self.assertIn(':formver.compiler-plugin:untilConversion', unchanged)
            self.assertNotIn(':formver.compiler-plugin:test', unchanged)
            self.assertFalse(any(arg.startswith('-Pformver.verification') for arg in unchanged))

            diff.unlink()
            (root / 'args').unlink()
            failed = subprocess.run(['bash', str(scripts / 'gate-test.sh')], env=gate_env, capture_output=True)
            self.assertNotEqual(0, failed.returncode)
            self.assertFalse((root / 'args').exists())

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
