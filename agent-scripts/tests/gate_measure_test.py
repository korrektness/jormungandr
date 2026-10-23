#!/usr/bin/env python3
"""Guard command statuses and structured reporting with explicit clock ticks."""

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('gate_measure', Path(__file__).parents[1] / 'gate_measure.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateMeasureTest(unittest.TestCase):
    def test_command_status_and_artifact(self):
        for status in (0, 23):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as directory:
                command = [sys.executable, '-c', f'import sys; sys.exit({status})']
                stderr = io.StringIO()
                ticks = iter((100, 375))
                with patch.dict(os.environ, SNAKT_GATE_ARTIFACTS=directory), contextlib.redirect_stderr(stderr):
                    result = gate.main(['fixture', *command], clock=lambda: next(ticks))
                record = json.loads((Path(directory) / 'fixture.json').read_text())
                self.assertEqual(status, result)
                self.assertEqual(dict(stage='fixture', command=command, cwd=os.getcwd(),
                                      elapsed_ns=275, exit_status=status), record)
                self.assertEqual(record, json.loads(stderr.getvalue()))

    def test_signal_and_launch_failure_are_reported(self):
        for status in (-15, FileNotFoundError('missing executable')):
            records = []
            ticks = iter((1, 9))

            def run(command):
                if isinstance(status, Exception):
                    raise status
                return status

            result = gate.measure('fixture', ['missing'], run=run,
                                  clock=lambda: next(ticks), emit=records.append)
            self.assertEqual(143 if status == -15 else 127, result)
            self.assertEqual(result, records[0]['exit_status'])
            self.assertEqual(8, records[0]['elapsed_ns'])
            if isinstance(status, Exception):
                self.assertIn('error', records[0])

    def test_reporting_failure_preserves_command_failure_and_fails_success(self):
        def emit(record):
            raise OSError('artifact write failed')

        for status in (0, 23):
            ticks = iter((0, 10))
            with self.subTest(status=status), contextlib.redirect_stderr(io.StringIO()):
                if status:
                    self.assertEqual(status, gate.measure('fixture', ['command'], emit=emit,
                                     run=lambda command: status, clock=lambda: next(ticks)))
                else:
                    with self.assertRaises(OSError):
                        gate.measure('fixture', ['command'], emit=emit,
                                     run=lambda command: status, clock=lambda: next(ticks))


if __name__ == '__main__':
    unittest.main()
